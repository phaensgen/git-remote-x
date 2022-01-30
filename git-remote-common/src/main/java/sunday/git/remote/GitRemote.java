package sunday.git.remote;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * This is the git remote helper implementation which does the communication with git and controls the actual storage
 * adapter. It will be called by the Git command-line commands (e.g. git push, git clone, git fetch, git pull and so on)
 * which send commands in the git-remote protocol syntax. Commands from git are received via std in, responses are
 * written to git via std out. Std err can be used for printing progress and status information.
 * 
 * @author Peter H&auml;nsgen
 */
public class GitRemote
{
    private static final int MAX_THREADS = 8;

    private Git git;
    private GitLogger logger;
    private Storage storage;

    private boolean firstPush;
    private String remoteHead;

    /**
     * A cache for the referenced object ids of remote refs, key is like "refs/heads/master".
     */
    private Map<String, SHA1> remoteRefs;

    /**
     * A cache for referenced objects that have been pushed, key is like "refs/heads/master".
     */
    private Map<String, SHA1> pushed;

    private Collection<SHA1> fetchTodo;
    private Collection<SHA1> fetchDone;

    private ExecutorService threadPool;

    /**
     * The constructor.
     */
    public GitRemote(Git git, Storage storage)
    {
        this.git = git;
        this.storage = storage;

        logger = new GitLogger();

        remoteRefs = new HashMap<>();
        pushed = new HashMap<>();

        fetchTodo = new HashSet<>();
        fetchDone = new HashSet<>();

        logger.debug("Using " + MAX_THREADS + " threads.");
        threadPool = Executors.newFixedThreadPool(MAX_THREADS);
    }

    /**
     * The main loop reading the commands that git sends from std in and executing them.
     */
    public void repl() throws IOException
    {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in)))
        {
            boolean hasPushed = false;

            while (true)
            {
                String line = in.readLine();
                if (line == null)
                {
                    if (hasPushed)
                    {
                        endPush();
                    }

                    break;
                }

                try
                {
                    if ("capabilities".equals(line))
                    {
                        capabilities();
                    }
                    else if ("list".equals(line))
                    {
                        list();
                    }
                    else if ("list for-push".equals(line))
                    {
                        listForPush();
                    }
                    else if (line.startsWith("push "))
                    {
                        push(line);
                        hasPushed = true;
                    }
                    else if (line.startsWith("fetch "))
                    {
                        fetch(line);
                    }
                    else if (line.startsWith("option"))
                    {
                        option(line);
                    }
                    else if (line.isEmpty())
                    {
                        System.out.println();
                    }
                    else
                    {
                        logger.error("Unexpected command: " + line);
                        System.exit(1);
                    }
                }
                catch (GitRemoteException ex)
                {
                    System.err.println(ex.getMessage());
                }
            }
        }

        System.exit(0);
    }

    /**
     * Responds with the capabilities that this remote backend has.
     */
    private void capabilities()
    {
        System.out.println("list");
        System.out.println("push");
        System.out.println("fetch");
        System.out.println("option");
        System.out.println();
    }

    /**
     * Sets options as defined in the git command. Currently, verbosity of log output can be
     * controlled.
     */
    private void option(String line)
    {
        String[] words = line.split(" ");
        if ("verbosity".equals(words[1]))
        {
            // Example:
            // option verbosity 1
            // 0 is silent, 1 is default, bigger is more output
            logger.setVerbosity(Integer.parseInt(words[2]));
            System.out.println("ok");
        }
        else
        {
            System.out.println("unsupported");
        }
    }

    /**
     * Lists the refs from the remote repository, one per line. This is used by git to calculate the difference between
     * the local and the remote repository.
     */
    private void list()
    {
        Collection<GitSHA1Reference> references = getRemoteRefs();
        for (GitSHA1Reference reference : references)
        {
            System.out.println(reference.toGit());
        }

        GitSymbolicReference head = readSymbolicRef("HEAD");
        if (head != null)
        {
            System.out.println(head.toGit());
        }

        System.out.println();
    }

    /**
     * Basically the same as list, except that the caller wants to use the result to prepare a push command.
     */
    private void listForPush()
    {
        Collection<GitSHA1Reference> references = getRemoteRefs();
        for (GitSHA1Reference reference : references)
        {
            System.out.println(reference.toGit());
        }

        System.out.println();
    }

    /**
     * Handles the fetch command, which provides a name and a hash.
     */
    private void fetch(String line)
    {
        String[] args = line.split(" ");
        SHA1 sha1 = new SHA1(args[1]);
        fetch(sha1);
    }

    /**
     * Recursively fetches the given object and its references from the remote
     * repository.
     */
    private void fetch(SHA1 sha1)
    {
        boolean changed = false;

        // the list of asynchronous fetch tasks that are currently scheduled
        Map<SHA1, Future<Collection<SHA1>>> fetchTasks = new HashMap<>();
        if (!fetchDone.contains(sha1))
        {
            fetchTasks.put(sha1, threadPool.submit(new FetchTask(sha1)));
            fetchTodo.add(sha1);
            changed = true;
        }

        while (!fetchTasks.isEmpty())
        {
            // get some scheduled task
            Entry<SHA1, Future<Collection<SHA1>>> entry = fetchTasks.entrySet().iterator().next();

            Future<Collection<SHA1>> fetchTask = entry.getValue();
            SHA1 sha = entry.getKey();

            try
            {
                // wait for its completion
                Collection<SHA1> references = fetchTask.get();

                // submit new tasks for resulting fetches
                for (SHA1 todo : references)
                {
                    if (!fetchDone.contains(todo) && !fetchTodo.contains(todo))
                    {
                        fetchTasks.put(todo, threadPool.submit(new FetchTask(todo)));
                        fetchTodo.add(todo);
                    }
                }

                fetchTasks.remove(sha);
                fetchDone.add(sha);

                int doneCount = fetchDone.size();
                int totalCount = fetchTodo.size() + doneCount;
                int percent = doneCount * 100 / totalCount;
                logger.progress("Fetching objects: " + percent + "% (" + doneCount + " / " + totalCount + ")");
            }
            catch (Exception ex)
            {
                System.out.println("error " + ex.getMessage());
            }
        }

        if (changed)
        {
            int doneCount = fetchDone.size();
            int totalCount = fetchTodo.size() + doneCount;
            logger.progress("Fetching objects: 100% (" + doneCount + " / " + totalCount + ")");
        }
    }

    /**
     * Handles a push command, which may look like:
     * 
     * <pre>
     * push refs/heads/master:refs/heads/master
     * push :refs/heads/master
     * </pre>
     */
    private void push(String line)
    {
        String[] args = line.split("[ :]");
        String src = args[1];
        String dst = args[2];

        if (src.isEmpty())
        {
            delete(dst);
        }
        else
        {
            push(src, dst);

            if (firstPush)
            {
                if ((remoteHead == null) || src.equals(git.getSymbolicRef("HEAD")))
                {
                    remoteHead = dst;
                }
            }
        }
    }

    /**
     * Writes the new HEAD ref after everything has been pushed.
     */
    private void endPush()
    {
        if (firstPush)
        {
            firstPush = false;
            writeSymbolicRef("HEAD", remoteHead);
        }
    }

    /**
     * Deletes the given ref from the remote.
     * 
     * @param ref a ref like "refs/heads/mybranch"
     */
    private void delete(String ref)
    {
        GitSymbolicReference head = readSymbolicRef("HEAD");
        if ((head != null) && head.getValue().equals(ref))
        {
            System.out.println("error " + ref + " Cannot delete the current branch.");
            return;
        }

        logger.debug("Deleting ref: " + ref);

        storage.deleteFile(refPath(ref));
        remoteRefs.remove(ref);
        pushed.remove(ref);

        logger.progress("Deleting refs: 100% (1 / 1)");

        System.out.println("ok " + ref);
    }

    /**
     * Pushes the given local src commit or branch to the remote branch described by dst.
     */
    private void push(String src, String dst)
    {
        logger.debug("Pushing from " + src + " to " + dst + "...");

        boolean force = false;
        if (src.startsWith("+"))
        {
            src = src.substring(1);
            force = true;
        }

        Collection<SHA1> excludes = new ArrayList<>();
        excludes.addAll(remoteRefs.values());
        excludes.addAll(pushed.values());

        List<SHA1> objects = git.listObjects(src, excludes);

        logger.debug("Found " + objects.size() + " objects, excluding " + excludes.size() + " remote refs.");

        Deque<Future<?>> tasks = new ArrayDeque<>();

        // before updating the ref, write all objects that are referenced
        // schedule upload tasks
        for (SHA1 sha1 : objects)
        {
            tasks.add(threadPool.submit(new UploadObject(sha1)));
        }

        int doneCount = 0;
        int totalCount = objects.size();

        // wait until all of them have finished
        while (!tasks.isEmpty())
        {
            try
            {
                Future<?> task = tasks.removeFirst();
                task.get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                throw new GitRemoteException(e);
            }

            int percent = doneCount * 100 / totalCount;
            logger.progress("Pushing objects: " + percent + "% (" + doneCount + " / " + totalCount + ")");

            doneCount++;
        }

        logger.progress("Pushing objects: 100% (" + doneCount + " / " + totalCount + ")");

        SHA1 sha1 = git.getRefValue(src);
        writeRemoteRef(dst, sha1, force);
        pushed.put(dst, sha1);

        System.out.println("ok " + dst);
    }

    /**
     * Returns the path for the given ref in the remote repository.
     */
    private Path refPath(String name)
    {
        if (!name.startsWith("refs/"))
        {
            throw new GitRemoteException("Invalid ref name: " + name);
        }

        return Path.of(name);
    }

    /**
     * Returns the path to the given object in the remote repository.
     */
    private Path objectPath(SHA1 sha1)
    {
        // split the path in 2 / 38 characters for an extra subdirectory level
        // similar to git in order to avoid huge number of objects in the same directory
        String name = sha1.toString();
        String prefix = name.substring(0, 2);
        String suffix = name.substring(2);
        return Path.of("objects", prefix, suffix);
    }

    /**
     * Converts a path into a name with forward slashes as separators.
     */
    private String pathToName(Path path)
    {
        // on Windows, the toString method returns the wrong separator
        return path.toString().replace('\\', '/');
    }

    /**
     * Checks if the object already exists at the remote repository.
     */
    private boolean objectExists(SHA1 sha1)
    {
        Path path = objectPath(sha1);
        boolean exists = storage.fileExists(path);

        if (exists)
        {
            logger.debug("Object already exists: " + sha1);
        }
        else
        {
            logger.debug("Object does not exist yet: " + sha1);
        }

        return exists;
    }

    /**
     * Uploads an object to the remote repository.
     */
    public void uploadObject(SHA1 sha1)
    {
        logger.debug("Uploading object: " + sha1);

        Path path = objectPath(sha1);

        GitObjectType type = git.getObjectType(sha1);
        String size = git.getObjectSize(sha1);

        // large files that don't fit into memory need special handling
        long length = Long.parseLong(size);
        if (length > 100 * 1024 * 1024)
        {
            logger.debug("Using large file handling: " + sha1 + " (" + length + " bytes)");

            File temp = encodeLargeObject(sha1, type, size);

            storage.uploadFile(path, temp);
            temp.delete();
        }
        else
        {
            byte[] content = encodeObject(sha1, type, size);
            storage.uploadFile(path, content);
        }
    }

    /**
     * Downloads an object from the remote repository and writes it into the local repository.
     */
    private void downloadObject(SHA1 sha1)
    {
        logger.debug("Downloading object: " + sha1);

        Path path = objectPath(sha1);
        InputStream in = storage.downloadStream(path);

        SHA1 computedSha1 = decodeObject(in);
        if (!computedSha1.equals(sha1))
        {
            throw new GitRemoteException("Provided and computed hashes do not match: " + sha1 + " != " + computedSha1);
        }
    }

    /**
     * Updates the given reference to point to the given object.
     */
    private void writeRemoteRef(String dst, SHA1 newSha1, boolean force)
    {
        Path path = refPath(dst);

        logger.debug("Uploading ref: " + path);

        if (!force)
        {
            SHA1 sha1 = remoteRefs.get(dst);
            if (sha1 != null)
            {
                if (!git.objectExists(sha1))
                {
                    throw new GitRemoteException("Object not found, fetch first.");
                }

                boolean isFastForward = git.isAncestor(sha1, newSha1);
                if (!isFastForward)
                {
                    throw new GitRemoteException("Not fast-forward, fetch first.");
                }
            }
        }

        storage.uploadFile(path, newSha1.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the refs that are present on the remote repository.
     */
    private Collection<GitSHA1Reference> getRemoteRefs()
    {
        logger.debug("Getting remote refs...");

        Collection<Path> files = storage.listFiles(Path.of("refs"));

        // something like:
        // refs/heads/master
        // 6bdbbdcda0bbbdc57fd83bf144954c3a9f218744

        if (files.isEmpty())
        {
            firstPush = true;

            logger.debug("No refs found, first push.");
            return Collections.emptyList();
        }

        List<GitSHA1Reference> refs = new ArrayList<>();
        for (Path path : files)
        {
            byte[] data = storage.downloadFile(path);
            String name = pathToName(path);
            SHA1 sha1 = new SHA1(new String(data, StandardCharsets.UTF_8).trim());
            GitSHA1Reference ref = new GitSHA1Reference(sha1, name);
            refs.add(ref);

            // cache for push check, like:
            // refs/heads/master -> 6bdbbdcda0bbbdc57fd83bf144954c3a9f218744
            remoteRefs.put(name, sha1);
        }

        logger.debug(refs.size() + " refs found.");

        return refs;
    }

    /**
     * Writes the given symbolic ref to the remote repository.
     * For example, like: HEAD -> ref: refs/heads/master
     */
    private void writeSymbolicRef(String path, String ref)
    {
        logger.debug("Uploading symbolic ref: " + path);

        String data = "ref: " + ref + "\n";
        byte[] content = data.getBytes(StandardCharsets.UTF_8);
        storage.uploadFile(Path.of(path), content);
    }

    /**
     * Returns the symbolic ref from the remote repository.
     */
    private GitSymbolicReference readSymbolicRef(String path)
    {
        logger.debug("Downloading symbolic ref: " + path);

        byte[] content = storage.downloadFile(Path.of(path));
        String ref = new String(content, StandardCharsets.UTF_8).substring("ref: ".length()).trim();
        return new GitSymbolicReference(path, ref);
    }

    /**
     * Returns the encoded contents of the object in the local repository.
     * The encoding is the same as the encoding that git uses for loose objects.
     */
    private byte[] encodeObject(SHA1 sha1, GitObjectType type, String size)
    {
        ByteArrayOutputStream data = new ByteArrayOutputStream();

        // git uses zlib compression
        try (DeflaterOutputStream out = new DeflaterOutputStream(data))
        {
            String header = type.toLowerName() + ' ' + size;
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(0);

            // read small files directly into memory
            byte[] contents = git.readObject(sha1, type);
            out.write(contents);
        }
        catch (IOException ex)
        {
            throw new GitRemoteException(ex);
        }

        return data.toByteArray();
    }

    /**
     * Returns a temporary file with the encoded contents of the object in the local repository.
     * The encoding is the same as the encoding that git uses for loose objects.
     */
    private File encodeLargeObject(SHA1 sha1, GitObjectType type, String size)
    {
        try
        {
            // prepare large uploads in a temporary file
            File temp = File.createTempFile("gitremotex", ".obj");

            // git uses zlib compression
            try (DeflaterOutputStream out = new DeflaterOutputStream(
                    new BufferedOutputStream(new FileOutputStream(temp))))
            {
                String header = type.toLowerName() + ' ' + size;
                out.write(header.getBytes(StandardCharsets.UTF_8));
                out.write(0);

                // append contents from git object
                git.copyObject(sha1, type, out);
            }

            return temp;
        }
        catch (IOException ex)
        {
            throw new GitRemoteException(ex);
        }
    }

    /**
     * Decodes the encoded object from the input stream and writes it to the local repository.
     * Returns the computed hash for the contents which represents the object id.
     */
    private SHA1 decodeObject(InputStream in)
    {
        ByteArrayOutputStream header = new ByteArrayOutputStream();

        // git uses zlib compression
        try (InflaterInputStream inf = new InflaterInputStream(in))
        {
            // collect header
            while (true)
            {
                int ch = inf.read();

                // 0 is the separator between header and content
                if (ch <= 0)
                {
                    break;
                }

                header.write(ch);
            }

            String headerString = new String(header.toByteArray(), StandardCharsets.UTF_8);
            String[] h = headerString.split(" ");
            GitObjectType type = GitObjectType.valueOf(h[0].toUpperCase());

            return git.writeObject(type, inf);
        }
        catch (IOException ex)
        {
            throw new GitRemoteException(ex);
        }
    }

    /**
     * Returns the objects that are directly referenced by the given object.
     */
    private Collection<SHA1> getReferencedObjects(SHA1 sha1)
    {
        GitObjectType type = git.getObjectType(sha1);
        if (GitObjectType.BLOB.equals(type))
        {
            // blob objects do not reference any other objects
            return Collections.emptyList();
        }

        byte[] content = git.readObject(sha1, null);
        String data = new String(content, StandardCharsets.UTF_8).trim();

        List<SHA1> objs = new ArrayList<>();
        if (GitObjectType.TAG.equals(type))
        {
            // tag objects reference a single object
            String[] lines = data.split("\n");
            String[] words = lines[0].split(" ");
            objs.add(new SHA1(words[1]));
        }
        else if (GitObjectType.COMMIT.equals(type))
        {
            // commit objects reference a tree and zero or more parents
            String[] lines = data.split("\n");
            String[] words = lines[0].split(" ");
            String tree = words[1];

            objs.add(new SHA1(tree));

            for (int i = 1; i < lines.length; i++)
            {
                String line = lines[i];
                if (line.startsWith("parent "))
                {
                    String[] w = line.split(" ");
                    objs.add(new SHA1(w[1]));
                }
                else
                {
                    break;
                }
            }
        }
        else if (GitObjectType.TREE.equals(type))
        {
            // tree objects reference zero or more trees and blobs, or submodules
            if (data == null)
            {
                // empty tree
                return Collections.emptyList();
            }
            String[] lines = data.split("\n");
            // submodules have the mode "160000" and the type "commit", we filter them out
            // because there is nothing to download
            for (String line : lines)
            {
                if (!line.startsWith("160000 commit "))
                {
                    String[] w = line.split("\\s");
                    objs.add(new SHA1(w[2]));
                }
            }
        }
        else
        {
            throw new GitRemoteException("Unexpected type: " + type);
        }

        return objs;
    }

    /**
     * A task for doing an upload in a concurrent thread.
     * 
     * @author Peter H&auml;nsgen
     */
    class UploadObject implements Runnable
    {
        private SHA1 sha1;

        /**
         * The constructor.
         */
        public UploadObject(SHA1 sha1)
        {
            this.sha1 = sha1;
        }

        @Override
        public void run()
        {
            // skip objects that may have been uploaded earlier in failed push
            if (!objectExists(sha1))
            {
                uploadObject(sha1);
            }
        }
    }

    /**
     * Performs a fetch for a single object in an asynchronous operation.
     * Returns the references of the object for subsequent retrieval.
     * 
     * @author Peter H&auml;nsgen
     */
    class FetchTask implements Callable<Collection<SHA1>>
    {
        private SHA1 sha1;

        /**
         * The constructor.
         */
        private FetchTask(SHA1 sha1)
        {
            this.sha1 = sha1;
        }

        @Override
        public Collection<SHA1> call()
        {
            Collection<SHA1> references = null;

            if (git.objectExists(sha1))
            {
                if (sha1.equals(SHA1.EMPTY_TREE_HASH))
                {
                    // git.objectExists() returns true for the empty
                    // tree hash even if it's not present in the object
                    // store. Everything will work fine in this situation,
                    // but "git fsck" will complain if it's not present, so
                    // we explicitly add it to avoid that.
                    git.writeObject(GitObjectType.TREE, new byte[0]);
                }

                if (!git.historyExists(sha1))
                {
                    // this can only happen in the case of aborted fetches
                    // that are resumed later
                    // resolve them too
                    references = getReferencedObjects(sha1);
                }
            }
            else
            {
                // new object, get it and resolve all its references
                downloadObject(sha1);

                references = getReferencedObjects(sha1);
            }

            return references != null ? references : Collections.emptyList();
        }
    }
}

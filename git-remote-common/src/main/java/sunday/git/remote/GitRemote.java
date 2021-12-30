package sunday.git.remote;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private Git git;
    private Storage storage;
    private boolean firstPush;
    private String remoteHead;
    private Map<String, SHA1> remoteRefs;

    /**
     * The constructor.
     */
    public GitRemote(Git git, Storage storage)
    {
        this.git = git;
        this.storage = storage;

        remoteRefs = new HashMap<>();
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
                else if (line.isEmpty())
                {
                    System.out.println();
                }
                else
                {
                    System.err.println("Unexpected command: " + line);
                    System.exit(1);
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
        System.out.println();
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
        Deque<SHA1> todo = new ArrayDeque<>();
        todo.push(sha1);

        Collection<SHA1> done = new HashSet<>();

        while (!todo.isEmpty())
        {
            SHA1 sha = todo.pop();
            if (done.contains(sha))
            {
                continue;
            }

            if (git.objectExists(sha))
            {
                if (sha.equals(SHA1.EMPTY_TREE_HASH))
                {
                    // git.objectExists() returns true for the empty
                    // tree hash even if it's not present in the object
                    // store. Everything will work fine in this situation,
                    // but "git fsck" will complain if it's not present, so
                    // we explicitly add it to avoid that.
                    git.writeObject(GitObjectType.TREE, new byte[0]);
                }

                if (!git.historyExists(sha))
                {
                    // this can only happen in the case of aborted fetches
                    // that are resumed later
                    // resolve them too
                    Collection<SHA1> refs = getReferencedObjects(sha);
                    todo.addAll(refs);
                }
            }
            else
            {
                // new object, get it and resolve all its references
                download(sha);

                Collection<SHA1> refs = getReferencedObjects(sha);
                todo.addAll(refs);
            }
            done.add(sha);
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
     */
    private void delete(String ref)
    {
        GitSymbolicReference head = readSymbolicRef("HEAD");
        if ((head != null) && head.getValue().equals(ref))
        {
            System.out.println("error " + ref + " Cannot delete the current branch.");
            return;
        }

        storage.deleteFile(refPath(ref));
        remoteRefs.remove(ref);

        System.out.println("ok " + ref);
    }

    /**
     * Pushes the given local src commit or branch to the remote branch described by dst.
     */
    private void push(String src, String dst)
    {
        boolean force = false;
        if (src.startsWith("+"))
        {
            src = src.substring(1);
            force = true;
        }

        List<SHA1> objects = git.listObjects(src, remoteRefs.values());

        // before updating the ref, write all objects that are referenced
        for (SHA1 sha1 : objects)
        {
            putObject(sha1);
        }

        SHA1 sha1 = git.getRefValue(src);
        writeRemoteRef(dst, sha1, force);

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
     * Uploads an object to the remote repository.
     */
    private void putObject(SHA1 sha1)
    {
        byte[] content = encodeObject(sha1);
        Path path = objectPath(sha1);
        storage.uploadFile(path, content);
    }

    /**
     * Downloads an object from the remote repository and writes it into the local repository.
     */
    private void download(SHA1 sha1)
    {
        Path path = objectPath(sha1);
        byte[] data = storage.downloadFile(path);

        SHA1 computedSha1 = decodeObject(data);
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
        Collection<Path> files = storage.listFiles(Path.of("refs"));

        // something like:
        // refs/heads/master
        // 6bdbbdcda0bbbdc57fd83bf144954c3a9f218744

        if (files.isEmpty())
        {
            firstPush = true;
            return Collections.emptyList();
        }

        List<GitSHA1Reference> refs = new ArrayList<>();
        for (Path path : files)
        {
            byte[] data = storage.downloadFile(path);
            String name = path.toString();
            SHA1 sha1 = new SHA1(new String(data, StandardCharsets.UTF_8).trim());
            GitSHA1Reference ref = new GitSHA1Reference(sha1, name);
            refs.add(ref);

            // cache for push check
            remoteRefs.put(name, sha1);
        }

        return refs;
    }

    /**
     * Writes the given symbolic ref to the remote repository.
     * For example, like: HEAD -> ref: refs/heads/master
     */
    private void writeSymbolicRef(String path, String ref)
    {
        String data = "ref: " + ref + "\n";
        byte[] content = data.getBytes(StandardCharsets.UTF_8);
        storage.uploadFile(Path.of(path), content);
    }

    /**
     * Returns the symbolic ref from the remote repository.
     */
    private GitSymbolicReference readSymbolicRef(String path)
    {
        byte[] content = storage.downloadFile(Path.of(path));
        String ref = new String(content, StandardCharsets.UTF_8).substring("ref: ".length()).trim();
        return new GitSymbolicReference(path, ref);
    }

    /**
     * Returns the encoded contents of the object in the local repository.
     * The encoding is the same as the encoding that git uses for loose objects.
     */
    private byte[] encodeObject(SHA1 sha1)
    {
        GitObjectType type = git.getObjectType(sha1);
        String size = git.getObjectSize(sha1);
        byte[] contents = git.readObject(sha1, type);

        ByteArrayOutputStream data = new ByteArrayOutputStream();

        // git uses zlib compression
        try (DeflaterOutputStream out = new DeflaterOutputStream(data))
        {
            String header = type.toLowerName() + ' ' + size;
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(0);
            out.write(contents);
        }
        catch (IOException ex)
        {
            throw new GitRemoteException(ex);
        }

        return data.toByteArray();
    }

    /**
     * Decodes the encoded object and writes it to the local repository.
     * Returns the computed hash for the contents which represents the object id.
     */
    private SHA1 decodeObject(byte[] data)
    {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        ByteArrayOutputStream content = new ByteArrayOutputStream();

        // git uses zlib compression
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data)))
        {
            // collect header
            while (true)
            {
                int ch = in.read();

                // 0 is the separator between header and content
                if (ch <= 0)
                {
                    break;
                }

                header.write(ch);
            }

            // collect content
            while (true)
            {
                int ch = in.read();
                if (ch < 0)
                {
                    break;
                }

                content.write(ch);
            }
        }
        catch (IOException ex)
        {
            throw new GitRemoteException(ex);
        }

        String headerString = new String(header.toByteArray(), StandardCharsets.UTF_8);
        String[] h = headerString.split(" ");
        GitObjectType type = GitObjectType.valueOf(h[0].toUpperCase());

        return git.writeObject(type, content.toByteArray());
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
}

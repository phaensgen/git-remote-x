package sunday.git.remote;

import java.io.BufferedReader;
import java.io.File;
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

/**
 * This is the git remote helper implementation which does the communication
 * with git and controls the actual storage adapter. It will be called by the
 * Git command-line tools using the git-remote protocol.
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
    public GitRemote(File workingDir, File gitDir, Storage storage)
    {
        this.storage = storage;

        remoteRefs = new HashMap<>();

        git = new Git(workingDir, gitDir);
    }

    /**
     * The main loop reading the commands that git sends from standard in and
     * executing them.
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
     * Lists the refs from the remote repository, one per line.
     */
    private void list()
    {
        Collection<SHA1Reference> references = getRemoteRefs();
        for (SHA1Reference reference : references)
        {
            System.out.println(reference.toGit());
        }

        SymbolicReference head = readSymbolicRef("HEAD");
        if (head != null)
        {
            System.out.println(head.toGit());
        }

        System.out.println();
    }

    /**
     * Basically the same as list, except that the caller wants to use the result to
     * prepare a push command.
     */
    private void listForPush()
    {
        Collection<SHA1Reference> references = getRemoteRefs();
        for (SHA1Reference reference : references)
        {
            System.out.println(reference.toGit());
        }

        System.out.println();
    }

    /**
     * Handle the fetch command.
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
                    // git.object_exists() returns True for the empty
                    // tree hash even if it's not present in the object
                    // store. Everything will work fine in this situation,
                    // but `git fsck` will complain if it's not present, so
                    // we explicitly add it to avoid that.
                    git.writeObject("tree", new byte[0]);
                }

                if (!git.historyExists(sha))
                {
                    // this can only happen in the case of aborted fetches
                    // that are resumed later
                    // self._trace('missing part of history from %s' % sha)
                    Collection<SHA1> refs = git.getReferencedObjects(sha);
                    todo.addAll(refs);
                }
            }
            else
            {
                download(sha);

                Collection<SHA1> refs = git.getReferencedObjects(sha);
                todo.addAll(refs);
            }
            done.add(sha);
        }
    }

    private void push(String line)
    {
        // push refs/heads/master:refs/heads/master
        // push :refs/heads/master
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

    private void endPush()
    {
        if (firstPush)
        {
            firstPush = false;
            writeSymbolicRef("HEAD", remoteHead);
        }
    }

    /**
     * Delete the ref from the remote.
     * 
     * @param dst the destination branch
     */
    private void delete(String ref)
    {
        SymbolicReference head = readSymbolicRef("HEAD");
        if ((head != null) && head.getValue().equals(ref))
        {
            System.out.println("error cannot delete the current branch " + ref);
            return;
        }

        storage.deleteFile(refPath(ref));
        remoteRefs.remove(ref);

        System.out.println("ok " + ref);
    }

    /**
     * Push src to dst on the remote.
     */
    private void push(String src, String dst)
    {
        // Pushes the given local <src> commit or branch to the remote branch described
        // by <dst>. A batch sequence of one or more push commands is terminated with a
        // blank line (if there is only one reference to push, a single push command is
        // followed by a blank line). For example, the following would be two batches of
        // push, the first asking the remote-helper to push the local ref master to the
        // remote ref master and the local HEAD to the remote branch, and the second
        // asking to push ref foo to ref bar (forced update requested by the +).

        // push +<src>:<dst>
        // push refs/heads/master:refs/heads/master
        // push HEAD:refs/heads/branch
        // \n
        // push +refs/heads/foo:refs/heads/bar
        // \n

        // When the push is complete, outputs one or more ok <dst> or error <dst> <why>?
        // lines to indicate success or failure of each pushed ref. The status report
        // output is terminated by a blank line. The option field <why> may be quoted in
        // a C style string if it contains an LF.

        // Similarly to fetch, push commands are sent in batches: The batch ends with a
        // blank line, and the remote handler outputs a blank line once the push
        // sequence is finished. It was originally published on
        // https://www.apriorit.com/

        // > push refs/heads/master:refs/heads/master
        // > push +HEAD:refs/heads/branch
        // >
        // < It was originally published on https://www.apriorit.com/

        // For the push command, the helper reads pairs of
        // local_reference:remote_reference. When the local reference is empty, Git
        // requests to delete the reference from the remote. Otherwise, the remote
        // helper has to push changes to the remote. It was originally published on
        // https://www.apriorit.com/

        // First of all, we have to figure out whether to allow the helper to execute
        // push if the local reference isn’t preceded with the + sign that means force
        // upload. The helper checks whether the object remote_reference points to
        // exists in the local database.

        // If this object doesn't exist in the database, somebody has already pushed
        // newer changes and the end user has to pull these changes first. In this case,
        // the remote helper notifies the end user about this via stderr and exits with
        // an error code. Also, we must check that the commit the remote reference
        // points to is an ancestor of the newer local commit to make sure that it’s a
        // fast-forward transition. It was originally published on
        // https://www.apriorit.com/

        // PUSH cases
        // 1) Local reference is empty
        // > push :refs/branch_to_remove
        // Action: remove the given reference on remote

        // 2) Force push
        // > push + refs/heads/master:refs/head/master
        // Action: upload objects referenced from the branch but missing on remote,
        // update remote reference without additional checks
        // 3) Regular push
        // > push refs/heads/master:refs/head/master
        // Action: perform fast-forward check: ensure that current remote reference
        // is an ancestor of the local reference, fail otherwise;
        // upload objects referenced from the branch but missing on remote,
        // update remote referenc It was originally published on
        // https://www.apriorit.com/

        // rev_list = system("git rev-list --objects %s" % src)
        // doc['type'] = system("git cat-file -t %s" % hash).strip("\n")
        // doc['content'] = system("git cat-file %s %s" % (doc['type'],

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
        writeRemoteRef(sha1, dst, force);

        System.out.println("ok " + dst);

    }

    /**
     * Return the path to the given ref on the remote.
     */
    private Path refPath(String name)
    {
        if (!name.startsWith("refs/"))
        {
            throw new GitRemoteException("Invalid ref path: " + name);
        }

        return Path.of(name);
    }

    /**
     * Return the path to the given object on the remote.
     */
    private Path objectPath(SHA1 sha1)
    {
        // splits the path in 2 / 38 subdirs as git does to avoid huge number of objects
        // in the same directory
        String name = sha1.toString();
        String prefix = name.substring(0, 2);
        String suffix = name.substring(2);
        return Path.of("objects", prefix, suffix);
    }

    /**
     * Upload an object to the remote.
     */
    private void putObject(SHA1 sha1)
    {
        byte[] content = git.encodeObject(sha1);
        Path path = objectPath(sha1);
        storage.uploadFile(path, content);
    }

    /**
     * Download files given in input_queue and push results to output_queue.
     */
    private void download(SHA1 sha1)
    {
        Path path = objectPath(sha1);
        byte[] data = storage.downloadFile(path);

        SHA1 computedSha1 = new SHA1(git.decodeObject(data));
        if (!computedSha1.equals(sha1))
        {
            throw new GitRemoteException("Provided and computed hashes do not match: " + sha1 + " != " + computedSha1);
        }
    }

    /**
     * Atomically update the given reference to point to the given object.
     * Return None if there is no error, otherwise return a description of the
     * error.
     */
    private void writeRemoteRef(SHA1 newSha1, String dst, boolean force)
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
                    throw new GitRemoteException("Non-fast forward, resolve this first.");
                }
            }
        }

        storage.uploadFile(path, newSha1.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Return the refs present on the remote.
     */
    private Collection<SHA1Reference> getRemoteRefs()
    {
        Collection<Path> files = storage.listFiles(Path.of("refs"));

        // like:
        // refs/heads/master
        // 6bdbbdcda0bbbdc57fd83bf144954c3a9f218744

        if (files.isEmpty())
        {
            firstPush = true;
            return Collections.emptyList();
        }

        List<SHA1Reference> refs = new ArrayList<>();
        for (Path path : files)
        {
            byte[] data = storage.downloadFile(path);
            String name = path.toString();
            SHA1 sha1 = new SHA1(new String(data, StandardCharsets.UTF_8).trim());
            SHA1Reference ref = new SHA1Reference(sha1, name);
            refs.add(ref);

            // cache for push check
            remoteRefs.put(name, sha1);
        }

        return refs;
    }

    /**
     * Write the given symbolic ref to the remote.
     * Perform a compare-and-swap (using previous revision rev) if specified,
     * otherwise perform a regular write.
     * Return a boolean indicating whether the write was successful.
     */
    // like: .git/HEAD ref: refs/heads/master
    // writeSymbolicRef("HEAD", remoteHead);
    private void writeSymbolicRef(String path, String ref)
    {
        String data = "ref: " + ref + "\n";
        byte[] content = data.getBytes(StandardCharsets.UTF_8);
        storage.uploadFile(Path.of(path), content);
    }

    /**
     * Return the revision number and content of a given symbolic ref on the remote.
     * Return a tuple (revision, content), or None if the symbolic ref does not
     * exist.
     */
    private SymbolicReference readSymbolicRef(String path)
    {
        byte[] content = storage.downloadFile(Path.of(path));
        String ref = new String(content, StandardCharsets.UTF_8).substring("ref: ".length()).trim();
        return new SymbolicReference(path, ref);
    }
}

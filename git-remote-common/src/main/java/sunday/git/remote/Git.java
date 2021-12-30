package sunday.git.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

/**
 * Helper for executing Git commands on the command-line.
 * 
 * @author Peter H&auml;nsgen
 */
public class Git
{
    private File workingDir;

    private File gitDir;

    /**
     * The constructor.
     */
    public Git(File workingDir, File gitDir)
    {
        this.workingDir = workingDir;
        this.gitDir = gitDir;
    }

    /**
     * Returns the git configuration value with the given name.
     */
    public String getConfig(String name)
    {
        return executeGitCommand("config", name).getFirstLine();
    }

    /**
     * Returns whether ancestor is an ancestor of ref.
     * This returns true when it is possible to fast-forward from ancestor to ref.
     */
    public boolean isAncestor(SHA1 ancestor, SHA1 ref)
    {
        return executeGitCommand("merge-base", "--is-ancestor", ancestor.toString(), ref.toString()).isOK();
    }

    /**
     * Returns whether the object exists in the repository.
     */
    public boolean objectExists(SHA1 sha1)
    {
        return executeGitCommand("cat-file", "-e", sha1.toString()).isOK();
    }

    /**
     * Return whether the object, along with its history, exists in the
     * repository.
     */
    public boolean historyExists(SHA1 sha1)
    {
        return executeGitCommand("rev-list", "--objects", sha1.toString()).isOK();
    }

    /**
     * Return the hash of the ref.
     */
    public SHA1 getRefValue(String ref)
    {
        return new SHA1(executeGitCommand("rev-parse", ref).getFirstLine());
    }

    /**
     * Returns the branch head to which the symbolic ref (e.g. HEAD) refers.
     */
    public String getSymbolicRef(String name)
    {
        return executeGitCommand("symbolic-ref", name).getFirstLine();
    }

    /**
     * Returns the type of the object.
     */
    public String getObjectKind(SHA1 sha1)
    {
        return executeGitCommand("cat-file", "-t", sha1.toString()).getFirstLine();
    }

    /**
     * Returns the size of the object.
     */
    public String getObjectSize(SHA1 sha1)
    {
        return executeGitCommand("cat-file", "-s", sha1.toString()).getFirstLine();
    }

    /**
     * Return the contents of the object.
     */
    public byte[] getObjectData(SHA1 sha1, String kind)
    {
        if (kind != null)
        {
            return executeGitCommand("cat-file", kind, sha1.toString()).getOutput();
        }
        else
        {
            // If kind is null, return a pretty-printed representation of the object.
            return executeGitCommand("cat-file", "-p", sha1.toString()).getOutput();
        }
    }

    /**
     * Return the encoded contents of the object.
     * The encoding is identical to the encoding git uses for loose objects.
     * This operation is the inverse of `decode_object`.
     */
    public byte[] encodeObject(SHA1 sha1)
    {
        String kind = getObjectKind(sha1);
        String size = getObjectSize(sha1);
        byte[] contents = getObjectData(sha1, kind);

        ByteArrayOutputStream data = new ByteArrayOutputStream();

        // git uses zlib compression
        try (DeflaterOutputStream out = new DeflaterOutputStream(data))
        {
            String header = kind + ' ' + size;
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
     * Decode the object, write it, and return the computed hash.
     * This operation is the inverse of `encode_object`.
     */
    public String decodeObject(byte[] data)
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
        String kind = h[0];

        return writeObject(kind, content.toByteArray());
    }

    /**
     * Writes an object.
     */
    public String writeObject(String kind, byte[] contents)
    {
        ByteArrayInputStream in = new ByteArrayInputStream(contents);
        GitResult result = executeGitCommand(in, "hash-object", "-w", "--stdin", "-t", kind);
        return result.getFirstLine();
    }

    /**
     * Return the objects reachable from ref excluding the objects reachable from
     * exclude.
     */
    public List<SHA1> listObjects(String ref, Collection<SHA1> exclude)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add("rev-list");
        cmd.add("--objects");
        cmd.add(ref);

        for (SHA1 ex : exclude)
        {
            if (objectExists(ex))
            {
                cmd.add("^" + ex);
            }
        }

        GitResult result = executeGitCommand(cmd.toArray(new String[cmd.size()]));
        List<SHA1> objects = new ArrayList<>();
        for (String line : result.getLines())
        {
            String s = line.split(" ")[0];
            objects.add(new SHA1(s));
        }
        return objects;
    }

    /**
     * Return the objects directly referenced by the object.
     */
    public Collection<SHA1> getReferencedObjects(SHA1 sha1)
    {
        String kind = getObjectKind(sha1);
        if (kind.equals("blob"))
        {
            // blob objects do not reference any other objects
            return Collections.emptyList();
        }

        byte[] content = getObjectData(sha1, null);
        String data = new String(content, StandardCharsets.UTF_8).trim();

        List<SHA1> objs = new ArrayList<>();
        if (kind.equals("tag"))
        {
            // tag objects reference a single object
            String[] lines = data.split("\n");
            String[] words = lines[0].split(" ");
            objs.add(new SHA1(words[1]));
        }
        else if (kind.equals("commit"))
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
        else if (kind.equals("tree"))
        {
            // tree objects reference zero or more trees and blobs, or submodules
            if (data == null)
            {
                // empty tree
                return Collections.emptyList();
            }
            String[] lines = data.split("\n");
            // submodules have the mode '160000' and the kind 'commit', we filter them out
            // because
            // there is nothing to download and this causes errors
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
            throw new GitRemoteException("Unexpected kind: " + kind);
        }

        return objs;
    }

    /**
     * Return the URL of the given remote.
     */
    public String getRemoteURL(String name)
    {
        return executeGitCommand("remote", "get-url", name).getFirstLine();
    }

    /**
     * Executes a git command by spawning a subprocess with the given args.
     */
    public GitResult executeGitCommand(String... args)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PumpStreamHandler streamHandler = new PumpStreamHandler(out, System.err);
        int exitValue = executeGitCommand(streamHandler, args);

        return new GitResult(exitValue, out.toByteArray());
    }

    /**
     * Executes a git command by spawning a subprocess with the given args, piping
     * from the input stream.
     */
    public GitResult executeGitCommand(InputStream in, String... args)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PumpStreamHandler streamHandler = new PumpStreamHandler(out, System.err, in);
        int exitValue = executeGitCommand(streamHandler, args);

        return new GitResult(exitValue, out.toByteArray());
    }

    /**
     * Executes a git command by spawning a subprocess with the given stream handler
     * and args.
     * 
     * @return the exit value of the subprocess
     */
    private int executeGitCommand(PumpStreamHandler streamHandler, String... args)
    {
        Map<String, String> environment = new HashMap<>();
        environment.put("GIT_DIR", gitDir.toString());

        // also set the user home directory because this may be needed to access the
        // global git configuration (like for git clone)
        environment.put("HOME", System.getProperty("user.home"));

        try
        {
            CommandLine commandLine = new CommandLine("git");
            commandLine.addArguments(args);

            DefaultExecutor executor = new DefaultExecutor();
            executor.setWorkingDirectory(workingDir);
            executor.setExitValues(null); // accept any without error
            executor.setStreamHandler(streamHandler);
            return executor.execute(commandLine, environment);
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }
}

package sunday.git.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

/**
 * Helper for executing Git commands on the command-line. The git executable
 * must be available on the path.
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
     * Checks whether an ancestor is really an ancestor of the descendant.
     * If this is the case, it is possible to fast-forward from ancestor to
     * descendant.
     */
    public boolean isAncestor(SHA1 ancestor, SHA1 descendant)
    {
        return executeGitCommand("merge-base", "--is-ancestor", ancestor.toString(), descendant.toString()).isOK();
    }

    /**
     * Checks whether the object with the given hash exists in the git repository.
     */
    public boolean objectExists(SHA1 sha1)
    {
        return executeGitCommand("cat-file", "-e", sha1.toString()).isOK();
    }

    /**
     * Checks whether the object, along with its history, exists in the
     * git repository.
     */
    public boolean historyExists(SHA1 sha1)
    {
        return executeGitCommand("rev-list", "--objects", sha1.toString()).isOK();
    }

    /**
     * Returns the hash value of the ref, e.g. the object the ref is pointing to.
     * Example: HEAD -> 46390aae1316b31427e1f480fcac1da0de7146f3
     */
    public SHA1 getRefValue(String ref)
    {
        return new SHA1(executeGitCommand("rev-parse", ref).getFirstLine());
    }

    /**
     * Returns the symbolic ref for the given name.
     * Example: HEAD -> refs/heads/master
     */
    public String getSymbolicRef(String name)
    {
        return executeGitCommand("symbolic-ref", name).getFirstLine();
    }

    /**
     * Returns the type of the object, e.g. one of blob, commit, tag or tree.
     */
    public GitObjectType getObjectType(SHA1 sha1)
    {
        String name = executeGitCommand("cat-file", "-t", sha1.toString()).getFirstLine();
        return GitObjectType.valueOf(name.toUpperCase());
    }

    /**
     * Returns the size of the object.
     */
    public String getObjectSize(SHA1 sha1)
    {
        return executeGitCommand("cat-file", "-s", sha1.toString()).getFirstLine();
    }

    /**
     * Returns the contents of the object.
     */
    public byte[] readObject(SHA1 sha1, GitObjectType type)
    {
        if (type != null)
        {
            return executeGitCommand("cat-file", type.toLowerName(), sha1.toString()).getOutput();
        }
        else
        {
            // if type is unknown return a pretty-printed representation of the object.
            return executeGitCommand("cat-file", "-p", sha1.toString()).getOutput();
        }
    }

    /**
     * Writes an object to the database and returns its content hash.
     */
    public SHA1 writeObject(GitObjectType type, byte[] contents)
    {
        ByteArrayInputStream in = new ByteArrayInputStream(contents);
        GitResult result = executeGitCommand(in, "hash-object", "-w", "--stdin", "-t", type.toLowerName());
        return new SHA1(result.getFirstLine());
    }

    /**
     * Returns the objects that are reachable from ref, excluding the objects
     * reachable from excludes.
     */
    public List<SHA1> listObjects(String ref, Collection<SHA1> excludes)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add("rev-list");
        cmd.add("--objects");
        cmd.add(ref);

        for (SHA1 ex : excludes)
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
            // looks like:
            // 27aa3f976fd2c30b2c00732f839ddf523a6dccfa README.md
            String s = line.split(" ")[0];
            objects.add(new SHA1(s));
        }

        return objects;
    }

    /**
     * Return the configured URL of the remote with the given name.
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

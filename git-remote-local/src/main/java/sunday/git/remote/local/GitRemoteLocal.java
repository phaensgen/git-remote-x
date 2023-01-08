package sunday.git.remote.local;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import sunday.git.remote.Git;
import sunday.git.remote.GitRemote;

/**
 * This git remote helper implementation stores contents in the local file
 * system. The folder where to store the contents must be configured in the
 * remote url, like:
 * 
 * <pre>
 * git remote add origin local:///path/to/dir/repo.git
 * </pre>
 * 
 * Note: The git-remote-file protocol is already taken by the native git
 * implementation, so we will call it git-remote-local instead.
 * 
 * @author Peter H&auml;nsgen
 */
public class GitRemoteLocal
{
    public static void main(String[] args) throws IOException
    {
        if (args.length != 3)
        {
            System.err.println("Invalid arguments: " + Arrays.toString(args));
            System.err.println("Usage: git-remote-local <remote> <url>");
            System.exit(1);
        }

        String url = args[2];
        if (!url.startsWith("local://"))
        {
            throw new IllegalArgumentException("Unsupported repository url: " + url);
        }

        // this is passed implicitly by git
        String gitDir = System.getenv("GIT_DIR");

        Path basePath = Path.of(url.substring("local://".length()));
        LocalStorage storage = new LocalStorage(basePath);

        Git git = new Git(new File("."), new File(gitDir));
        new GitRemote(git, storage).repl();
    }
}

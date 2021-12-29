package sunday.git.remote.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import sunday.git.remote.Git;

/**
 * Integration tests for the git remote local implementation.
 * 
 * @author Peter H&auml;nsgen
 */
public class GitRemoteLocalTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testGitRemoteLocal() throws IOException
    {
        // create a new empty git repo
        File testDir = temp.newFolder("test");
        File testGitDir = new File(testDir, ".git");
        testGitDir.mkdir();

        Git git = new Git(testDir, testGitDir);
        assertEquals(0, git.executeGitCommand("init").getExitValue());
        assertTrue(Files.exists(testDir.toPath().resolve(Path.of(".git"))));
        assertTrue(Files.exists(testDir.toPath().resolve(Path.of(".git", "HEAD"))));
        assertTrue(Files.exists(testDir.toPath().resolve(Path.of(".git", "config"))));

        // add something
        File readme = new File(testDir, "readme.txt");
        Files.writeString(readme.toPath(), "Hello World!");
        assertTrue(Files.exists(testDir.toPath().resolve(Path.of("readme.txt"))));

        // commit it
        assertEquals(0, git.executeGitCommand("add", "readme.txt").getExitValue());
        assertEquals(0, git.executeGitCommand("commit", "-m", "'Initial commit'").getExitValue());

        // create a new file storage
        File storageDir = temp.newFolder("storage");

        // configure test project to use it
        String url = "local://" + storageDir.getAbsolutePath() + "/test.git";
        assertEquals(0, git.executeGitCommand("remote", "add", "origin", url).getExitValue());

        // push the repo to the storage
        assertEquals(0, git.executeGitCommand("push", "--set-upstream", "origin", "master").getExitValue());
        assertTrue(Files.exists(storageDir.toPath().resolve(Path.of("test.git", "HEAD"))));
        assertTrue(Files.exists(storageDir.toPath().resolve(Path.of("test.git", "objects"))));
        assertTrue(Files.exists(storageDir.toPath().resolve(Path.of("test.git", "refs", "heads", "master"))));

        // clone the storage to another repo
        File cloneDir = temp.newFolder("clone");
        Git git2 = new Git(cloneDir.getParentFile(), cloneDir.getParentFile());
        assertEquals(0, git2.executeGitCommand("clone", url, "clone").getExitValue());

        // inspect checked out file
        File readme2 = new File(cloneDir, "readme.txt");
        String content = Files.readString(readme2.toPath());
        assertEquals("Hello World!", content);
    }
}

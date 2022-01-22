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
public class GitRemoteLocalIT
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /**
     * This test will fail on a CI system where no git-remote-local binary is
     * present in the path, therefore it will be excluded if maven profile is "ci".
     */
    @Test
    public void testGitRemoteLocal() throws IOException
    {
        // 1. initial commit and push
        // create a new empty git repo
        File test1Dir = temp.newFolder("test1");
        File test1GitDir = new File(test1Dir, ".git");
        test1GitDir.mkdir();

        Git git1 = new Git(test1Dir, test1GitDir);
        assertEquals(0, git1.executeGitCommand("init").getExitValue());
        assertTrue(Files.exists(test1Dir.toPath().resolve(Path.of(".git"))));
        assertTrue(Files.exists(test1Dir.toPath().resolve(Path.of(".git", "HEAD"))));
        assertTrue(Files.exists(test1Dir.toPath().resolve(Path.of(".git", "config"))));

        // set some test identity (only needed for CI builds)
        assertEquals(0, git1.executeGitCommand("config", "user.email", "test@example.com").getExitValue());
        assertEquals(0, git1.executeGitCommand("config", "user.name", "Test User").getExitValue());

        // add a small file
        File testFile11 = new File(test1Dir, "file1.txt");
        Files.writeString(testFile11.toPath(), "File1");

        // and a large file
        File testLargeFile1 = new File(test1Dir, "largeFile.txt");
        Files.write(testLargeFile1.toPath(), new byte[123 * 1024 * 1024]);

        // commit them
        assertEquals(0, git1.executeGitCommand("add", testFile11.getName(), testLargeFile1.getName()).getExitValue());
        assertEquals(0, git1.executeGitCommand("commit", "-m", "'Initial commit'").getExitValue());

        // create a new file storage
        File storageDir = temp.newFolder("storage");

        // configure test project to use it
        String url = "local://" + storageDir.getAbsolutePath() + "/test.git";
        assertEquals(0, git1.executeGitCommand("remote", "add", "origin", url).getExitValue());

        // push the repo to the storage
        assertEquals(0, git1.executeGitCommand("push", "-v", "--set-upstream", "origin", "master").getExitValue());
        assertTrue(Files.exists(storageDir.toPath().resolve(Path.of("test.git", "HEAD"))));
        assertTrue(Files.exists(storageDir.toPath().resolve(Path.of("test.git", "objects"))));
        assertTrue(Files.exists(storageDir.toPath().resolve(Path.of("test.git", "refs", "heads", "master"))));

        // 2. clone
        // clone the storage to another repo
        File test2Dir = temp.newFolder("test2");
        File test2GitDir = new File(test2Dir, ".git");

        // separate context for cloning because the target git project doesn't exist yet
        Git gitClone = new Git(test2Dir.getParentFile(), test2Dir.getParentFile());
        assertEquals(0, gitClone.executeGitCommand("clone", "-v", url, "test2").getExitValue());

        // now create context for running commands within the cloned repo
        Git git2 = new Git(test2Dir, test2GitDir);

        // inspect checked out files
        File testFile21 = new File(test2Dir, "file1.txt");
        assertFileContains("File1", testFile21);

        File testLargeFile2 = new File(test2Dir, "largeFile.txt");
        assertEquals(123 * 1024 * 1024, testLargeFile2.length());

        // shouldn't do anything, because we are up-to-date
        assertEquals(0, git2.executeGitCommand("push", "-v").getExitValue());

        // 3. commit, push and pull
        // change first file
        Files.writeString(testFile11.toPath(), "File1 Change1");

        // commit and push
        assertEquals(0, git1.executeGitCommand("add", testFile11.getName()).getExitValue());
        assertEquals(0, git1.executeGitCommand("commit", "-m", "'Second commit'").getExitValue());
        assertEquals(0, git1.executeGitCommand("push", "-v").getExitValue());

        // pull in second repository
        assertEquals(0, git2.executeGitCommand("pull", "-v").getExitValue());

        // verify
        assertFileContains("File1 Change1", testFile21);
        assertEquals(0, git2.executeGitCommand("fsck").getExitValue());

        // 4. concurrent commit and push, same file
        Files.writeString(testFile11.toPath(), "File1 Change2");
        assertEquals(0, git1.executeGitCommand("add", testFile11.getName()).getExitValue());
        assertEquals(0, git1.executeGitCommand("commit", "-m", "'Third commit'").getExitValue());
        assertEquals(0, git1.executeGitCommand("push", "-v").getExitValue());

        Files.writeString(testFile21.toPath(), "File1 Change3");
        assertEquals(0, git2.executeGitCommand("add", testFile21.getName()).getExitValue());
        assertEquals(0, git2.executeGitCommand("commit", "-m", "'Fourth commit'").getExitValue());

        // push should fail
        assertEquals(1, git2.executeGitCommand("push", "-v").getExitValue());
        assertEquals(0, git2.executeGitCommand("reset", "--hard", "origin/master").getExitValue());
        assertEquals(0, git2.executeGitCommand("pull", "-v").getExitValue());
        assertFileContains("File1 Change2", testFile21);

        // 5. concurrent commit and push, different files
        File testFile12 = new File(test1Dir, "file2.txt");
        File testFile13 = new File(test1Dir, "file3.txt");
        Files.writeString(testFile12.toPath(), "File2");
        assertEquals(0, git1.executeGitCommand("add", testFile12.getName()).getExitValue());
        assertEquals(0, git1.executeGitCommand("commit", "-m", "'Fifth commit'").getExitValue());
        assertEquals(0, git1.executeGitCommand("push", "-v").getExitValue());

        File testFile22 = new File(test2Dir, "file2.txt");
        File testFile23 = new File(test2Dir, "file3.txt");
        Files.writeString(testFile23.toPath(), "File3");
        assertEquals(0, git2.executeGitCommand("add", testFile23.getName()).getExitValue());
        assertEquals(0, git2.executeGitCommand("commit", "-m", "'Sixth commit'").getExitValue());

        // push should fail, fetch first
        assertEquals(1, git2.executeGitCommand("push", "-v").getExitValue());
        assertEquals(0, git2.executeGitCommand("config", "pull.rebase", "false").getExitValue());
        assertEquals(0, git2.executeGitCommand("pull", "-v").getExitValue());
        assertEquals(0, git2.executeGitCommand("push", "-v").getExitValue());

        // Finally: check consistency on all sides
        assertEquals(0, git1.executeGitCommand("pull", "-v").getExitValue());
        assertEquals(0, git1.executeGitCommand("fsck").getExitValue());
        assertFileContains("File1 Change2", testFile11);
        assertFileContains("File2", testFile12);
        assertFileContains("File3", testFile13);

        assertEquals(0, git2.executeGitCommand("pull", "-v").getExitValue());
        assertEquals(0, git2.executeGitCommand("fsck").getExitValue());
        assertFileContains("File1 Change2", testFile21);
        assertFileContains("File2", testFile22);
        assertFileContains("File3", testFile23);
    }

    private void assertFileContains(String expectedContent, File file) throws IOException
    {
        assertEquals(expectedContent, Files.readString(file.toPath()));
    }
}

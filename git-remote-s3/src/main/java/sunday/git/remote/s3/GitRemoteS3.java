package sunday.git.remote.s3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import sunday.git.remote.Git;
import sunday.git.remote.GitRemote;

/**
 * This git remote helper implementation stores contents in an AWS S3 bucket.
 * The location where to store the contents must be configured in the
 * remote url, like:
 * 
 * <pre>
 * git remote add origin s3://bucketName/path/to/dir/repo.git
 * </pre>
 * 
 * @author Peter H&auml;nsgen
 */
public class GitRemoteS3
{
    public static void main(String[] args) throws IOException
    {
        if (args.length != 3)
        {
            System.err.println("Usage: git-remote-s3 <remote> <url>");
            System.exit(1);
        }

        String url = args[2];
        if (!url.startsWith("s3://"))
        {
            throw new IllegalArgumentException("Unsupported repository url: " + url);
        }

        // this is passed implicitly by git
        String gitDir = System.getenv("GIT_DIR");

        S3Configuration configuration = new S3Configuration();

        String path = url.substring("s3://".length());
        int index = path.indexOf('/');
        if (index < 0)
        {
            configuration.setBucketName(path);
            configuration.setBaseDir(Path.of(""));
        }
        else
        {
            configuration.setBucketName(path.substring(0, index));
            configuration.setBaseDir(Path.of(path.substring(index + 1)));
        }

        Git git = new Git(new File("."), new File(gitDir));
        String accessKeyId = git.getConfig("s3.accesskeyid");
        String secretKey = git.getConfig("s3.secretkey");
        String region = git.getConfig("s3.region");

        configuration.setAccessKeyId(accessKeyId);
        configuration.setSecretKey(secretKey);
        configuration.setRegion(region);

        S3Storage storage = new S3Storage(configuration);

        new GitRemote(git, storage).repl();
    }
}

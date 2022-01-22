package sunday.git.remote.s3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.Collection;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Some tests for the S3 client. Require a configured S3 bucket, therefore disabled by default.
 * 
 * @author Peter H&auml;nsgen
 */
@Ignore
public class S3StorageTest
{
    private S3Configuration configuration;

    @Before
    public void before()
    {
        configuration = new S3Configuration();
        configuration.setAccessKeyId(System.getenv("ACCESS_KEY_ID"));
        configuration.setSecretKey(System.getenv("SECRET_KEY"));
        configuration.setRegion(System.getenv("REGION"));
        configuration.setBucketName(System.getenv("BUCKET_NAME"));
        configuration.setBaseDir(Path.of("S3StorageTest"));
    }

    @Test
    public void testS3()
    {
        Path testFile = Path.of("dir/test.txt");
        byte[] testContent = new byte[1024];

        S3Storage s3 = new S3Storage(configuration);
        assertFalse(s3.fileExists(testFile));

        s3.uploadFile(testFile, testContent);
        assertTrue(s3.fileExists(testFile));

        Collection<Path> allFiles = s3.listFiles(Path.of(""));
        assertEquals(1, allFiles.size());
        for (Path file : allFiles)
        {
            assertEquals(testFile, file);
        }

        Collection<Path> dirFiles = s3.listFiles(Path.of("dir"));
        assertEquals(1, dirFiles.size());
        for (Path file : dirFiles)
        {
            assertEquals(testFile, file);
        }

        s3.deleteFile(testFile);
        assertFalse(s3.fileExists(testFile));
    }

    @Test
    public void testS3Speed()
    {
        S3Storage s3 = new S3Storage(configuration);

        Path testFile = Path.of("dir/test.txt");
        byte[] testContent = new byte[123 * 1024 * 1024];

        long begin = System.currentTimeMillis();
        s3.uploadFile(testFile, testContent);
        long end = System.currentTimeMillis();

        System.out.println("Upload in " + (end - begin) + "ms");

        s3.deleteFile(testFile);
    }
}

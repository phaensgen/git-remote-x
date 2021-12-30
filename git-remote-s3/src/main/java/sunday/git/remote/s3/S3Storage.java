package sunday.git.remote.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.http.HttpStatus;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import sunday.git.remote.GitRemoteException;
import sunday.git.remote.Storage;

/**
 * Storage implementation that uses an AWS S3 bucket for storing files.
 *
 * @author Peter H&auml;nsgen
 */
public class S3Storage implements Storage
{
    private S3Configuration configuration;

    /**
     * The constructor.
     */
    public S3Storage(S3Configuration configuration)
    {
        this.configuration = configuration;
    }

    @Override
    public void uploadFile(Path path, byte[] content)
    {
        String bucket = configuration.getBucketName();
        String key = getKey(path);

        AmazonS3 s3 = createClient();

        ObjectMetadata metaData = new ObjectMetadata();
        metaData.setContentLength(content.length);

        s3.putObject(bucket, key, new ByteArrayInputStream(content), metaData);
    }

    @Override
    public byte[] downloadFile(Path path)
    {
        String bucketName = configuration.getBucketName();
        String key = getKey(path);

        try
        {
            AmazonS3 s3 = createClient();

            S3Object o = s3.getObject(bucketName, key);
            return o.getObjectContent().readAllBytes();
        }
        catch (AmazonS3Exception ex)
        {
            if (ex.getStatusCode() == HttpStatus.SC_NOT_FOUND)
            {
                throw new GitRemoteException("File not found: " + key);
            }

            throw new GitRemoteException(ex);
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }

    @Override
    public void deleteFile(Path path)
    {
        String bucketName = configuration.getBucketName();
        String key = getKey(path);

        AmazonS3 s3 = createClient();
        s3.deleteObject(bucketName, key);
    }

    @Override
    public Collection<Path> listFiles(Path path)
    {
        String bucketName = configuration.getBucketName();
        String key = getKey(path);

        List<Path> files = new ArrayList<>();

        AmazonS3 s3 = createClient();

        ListObjectsV2Request request = new ListObjectsV2Request();
        request.setBucketName(bucketName);
        request.setPrefix(key);

        ListObjectsV2Result result;
        do
        {
            result = s3.listObjectsV2(request);

            for (S3ObjectSummary o : result.getObjectSummaries())
            {
                String objectKey = o.getKey();
                Path objectPath = Paths.get(objectKey);

                files.add(configuration.getBaseDir().relativize(objectPath));
            }

            request.setContinuationToken(result.getNextContinuationToken());
        }
        while (result.isTruncated());

        return files;
    }

    private AmazonS3 createClient()
    {
        String region = configuration.getRegion();

        return AmazonS3ClientBuilder.standard() //
                .withCredentials(new S3CredentialsProvider(configuration)) //
                .withRegion(region) //
                .build();
    }

    private String getKey(Path path)
    {
        // replace backslashes in case this runs on Windows
        return configuration.getBaseDir().resolve(path).toString().replace('\\', '/');
    }
}

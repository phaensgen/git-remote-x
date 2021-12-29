package sunday.git.remote.s3;

import java.nio.file.Path;

/**
 * A configuration for an AWS S3 account.
 * 
 * @author Peter H&auml;nsgen
 */
public class S3Configuration
{
    private String accessKeyId;
    private String secretKey;
    private String region;
    private String bucketName;
    private Path baseDir;

    public void setAccessKeyId(String accessKeyId)
    {
        if ((accessKeyId == null) || accessKeyId.isBlank())
        {
            throw new IllegalArgumentException("Invalid access key id: " + accessKeyId);
        }

        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeyId()
    {
        return accessKeyId;
    }

    public void setSecretKey(String secretKey)
    {
        if ((secretKey == null) || secretKey.isBlank())
        {
            throw new IllegalArgumentException("Invalid secret key: " + secretKey);
        }

        this.secretKey = secretKey;
    }

    public String getSecretKey()
    {
        return secretKey;
    }

    public void setRegion(String region)
    {
        if ((region == null) || region.isBlank())
        {
            throw new IllegalArgumentException("Invalid region: " + region);
        }

        this.region = region;
    }

    public String getRegion()
    {
        return region;
    }

    public void setBucketName(String bucketName)
    {
        if ((bucketName == null) || bucketName.isBlank())
        {
            throw new IllegalArgumentException("Invalid bucket name: " + bucketName);
        }

        this.bucketName = bucketName;
    }

    public String getBucketName()
    {
        return bucketName;
    }

    public void setBaseDir(Path baseDir)
    {
        if (baseDir == null)
        {
            throw new IllegalArgumentException("Base dir is null.");
        }

        this.baseDir = baseDir;
    }

    public Path getBaseDir()
    {
        return baseDir;
    }
}

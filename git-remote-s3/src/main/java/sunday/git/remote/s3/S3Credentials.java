package sunday.git.remote.s3;

import com.amazonaws.auth.AWSCredentials;

/**
 * An AWS credential adapter.
 * 
 * @author Peter H&auml;nsgen
 */
public class S3Credentials implements AWSCredentials
{
    private String accessKeyId;

    private String secretKey;

    /**
     * The constructor.
     */
    public S3Credentials(String accessKeyId, String secretKey)
    {
        this.accessKeyId = accessKeyId;
        this.secretKey = secretKey;
    }

    @Override
    public String getAWSAccessKeyId()
    {
        return accessKeyId;
    }

    @Override
    public String getAWSSecretKey()
    {
        return secretKey;
    }
}

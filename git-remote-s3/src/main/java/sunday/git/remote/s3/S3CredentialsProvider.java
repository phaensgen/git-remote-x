package sunday.git.remote.s3;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;

/**
 * An AWS credential provider adapter.
 * 
 * @author Peter H&auml;nsgen
 */
public class S3CredentialsProvider implements AWSCredentialsProvider
{
    private S3Configuration configuration;

    /**
     * The constructor.
     */
    public S3CredentialsProvider(S3Configuration configuration)
    {
        this.configuration = configuration;
    }

    @Override
    public AWSCredentials getCredentials()
    {
        return new S3Credentials(configuration.getAccessKeyId(), configuration.getSecretKey());
    }

    @Override
    public void refresh()
    {
    }
}

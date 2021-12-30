package sunday.git.remote.s3enc;

import sunday.git.remote.s3.S3Configuration;

/**
 * A configuration for an encrypted AWS S3 account.
 * 
 * @author Peter H&auml;nsgen
 */
public class S3EncConfiguration extends S3Configuration
{
    private String encryptionKey;

    public void setEncryptionKey(String encryptionKey)
    {
        if ((encryptionKey == null) || encryptionKey.isBlank())
        {
            throw new IllegalArgumentException("Invalid encryption key: " + encryptionKey);
        }

        this.encryptionKey = encryptionKey;
    }

    public String getEncryptionKey()
    {
        return encryptionKey;
    }
}

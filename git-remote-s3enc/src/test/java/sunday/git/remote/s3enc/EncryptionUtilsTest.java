package sunday.git.remote.s3enc;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import javax.crypto.SecretKey;

import org.junit.Test;

/**
 * Unit tests for EncryptionUtils.
 * 
 * @author Peter H&auml;nsgen
 */
public class EncryptionUtilsTest
{
    @Test
    public void testEncryptDecrypt() throws Exception
    {
        EncryptionUtils encryptionUtils = new EncryptionUtils();
        SecretKey secretKey = encryptionUtils.generateKey();

        String originalText = "This is a test text.";
        byte[] encrypted = encryptionUtils.encrypt(originalText.getBytes("UTF-8"), secretKey);

        byte[] decrypted = encryptionUtils.decrypt(encrypted, secretKey);
        assertEquals(originalText, new String(decrypted, "UTF-8"));
    }

    @Test
    public void testEncryptDecryptStream() throws Exception
    {
        EncryptionUtils encryptionUtils = new EncryptionUtils();
        SecretKey secretKey = encryptionUtils.generateKey();

        String originalText = "This is a test text.";
        byte[] bytes = originalText.getBytes("UTF-8");

        try (InputStream encrypted = encryptionUtils.encrypt(new ByteArrayInputStream(bytes), secretKey);
                InputStream decrypted = encryptionUtils.decrypt(encrypted, secretKey))
        {
            byte[] encryptedDecrypted = decrypted.readAllBytes();
            assertEquals(originalText, new String(encryptedDecrypted, "UTF-8"));
        }
    }

    @Test
    public void testEncryptDecryptEmptyString() throws Exception
    {
        EncryptionUtils encryptionUtils = new EncryptionUtils();
        SecretKey secretKey = encryptionUtils.generateKey();

        String originalText = "";
        byte[] encrypted = encryptionUtils.encrypt(originalText.getBytes("UTF-8"), secretKey);

        byte[] decrypted = encryptionUtils.decrypt(encrypted, secretKey);
        assertEquals(originalText, new String(decrypted, "UTF-8"));
    }

    @Test
    public void testEncodeDecodeKey()
    {
        EncryptionUtils encryptionUtils = new EncryptionUtils();

        SecretKey secretKey = encryptionUtils.generateKey();
        String encodedKey = encryptionUtils.encodeKey(secretKey);
        SecretKey decodedKey = encryptionUtils.decodeKey(encodedKey);

        String secret = Arrays.toString(secretKey.getEncoded());
        String decoded = Arrays.toString(decodedKey.getEncoded());
        assertEquals(secret, decoded);
    }
}

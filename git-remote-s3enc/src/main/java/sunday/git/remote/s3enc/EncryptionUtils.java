package sunday.git.remote.s3enc;

import java.io.InputStream;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper methods for dealing with encryption.
 * 
 * @author Peter H&auml;nsgen
 */
public class EncryptionUtils
{
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * Encrypts a message using the given key.
     */
    public byte[] encrypt(byte[] input, SecretKey secretKey)
    {
        try
        {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            return cipher.doFinal(input);
        }
        catch (Exception ex)
        {
            throw new SecurityException(ex);
        }
    }

    /**
     * Encrypts an input stream using the given key.
     */
    public InputStream encrypt(InputStream in, SecretKey secretKey)
    {
        try
        {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            return new CipherInputStream(in, cipher);
        }
        catch (Exception ex)
        {
            throw new SecurityException(ex);
        }
    }

    /**
     * Decrypts a message using the given key.
     */
    public byte[] decrypt(byte[] encrypted, SecretKey secretKey)
    {
        try
        {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            return cipher.doFinal(encrypted);
        }
        catch (Exception ex)
        {
            throw new SecurityException(ex);
        }
    }

    /**
     * Decrypts an input stream using the given key.
     */
    public InputStream decrypt(InputStream in, SecretKey secretKey)
    {
        try
        {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            return new CipherInputStream(in, cipher);
        }
        catch (Exception ex)
        {
            throw new SecurityException(ex);
        }
    }

    /**
     * Generates a new AES key for symmetric encryption.
     */
    public SecretKey generateKey()
    {
        try
        {
            KeyGenerator symKeyGenerator = KeyGenerator.getInstance("AES");
            symKeyGenerator.init(256);
            return symKeyGenerator.generateKey();
        }
        catch (Exception ex)
        {
            throw new SecurityException(ex);
        }
    }

    /**
     * Encodes a secret key to Base64 url-encoded format so that it can be stored in
     * some configuration file.
     */
    public String encodeKey(SecretKey secretKey)
    {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(secretKey.getEncoded());
        byte[] bytes = x509EncodedKeySpec.getEncoded();

        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    /**
     * Decodes a secret key from Base64 format, for example it can be read from some
     * configuration file.
     */
    public SecretKey decodeKey(String encoded64)
    {
        byte[] encodedPrivateKey = Base64.getUrlDecoder().decode(encoded64);

        return new SecretKeySpec(encodedPrivateKey, "AES");
    }
}

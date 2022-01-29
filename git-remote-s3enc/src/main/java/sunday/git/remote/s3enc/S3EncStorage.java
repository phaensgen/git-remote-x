package sunday.git.remote.s3enc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import javax.crypto.SecretKey;

import com.amazonaws.util.IOUtils;

import sunday.git.remote.GitRemoteException;
import sunday.git.remote.s3.S3Storage;

/**
 * Storage implementation that uses an AWS S3 bucket for storing files.
 * File contents will be encrypted before upload and decrypted after download.
 * File names will be kept as they are in plain text, as most content in git is
 * represented by SHA1 hashes anyway and thus they are no secret.
 *
 * @author Peter H&auml;nsgen
 */
public class S3EncStorage extends S3Storage
{
    private EncryptionUtils encryptionUtils;
    private SecretKey secretKey;

    /**
     * The constructor.
     */
    public S3EncStorage(S3EncConfiguration configuration)
    {
        super(configuration);

        encryptionUtils = new EncryptionUtils();
        secretKey = encryptionUtils.decodeKey(configuration.getEncryptionKey());
    }

    /**
     * Uploads the given file after encryption.
     */
    @Override
    public void uploadFile(Path path, File file)
    {
        try
        {
            File encFile = File.createTempFile("gitremotex", ".s3enc");

            try (InputStream in = encryptionUtils.encrypt(new BufferedInputStream(new FileInputStream(file)),
                    secretKey); OutputStream out = new BufferedOutputStream(new FileOutputStream(encFile)))
            {
                IOUtils.copy(in, out);
            }

            super.uploadFile(path, encFile);

            encFile.delete();
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }

    @Override
    public void uploadFile(Path path, byte[] content)
    {
        byte[] encryptedContent = encryptionUtils.encrypt(content, secretKey);
        super.uploadFile(path, encryptedContent);
    }

    @Override
    public byte[] downloadFile(Path path)
    {
        byte[] encryptedContent = super.downloadFile(path);
        return encryptionUtils.decrypt(encryptedContent, secretKey);
    }

    @Override
    public InputStream downloadStream(Path path)
    {
        InputStream in = super.downloadStream(path);
        return encryptionUtils.decrypt(in, secretKey);
    }
}

package sunday.git.remote.local;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import sunday.git.remote.GitRemoteException;
import sunday.git.remote.Storage;

/**
 * Storage implementation that uses the local file system for storing files.
 * 
 * @author Peter H&auml;nsgen
 */
public class LocalStorage implements Storage
{
    private Path baseDir;

    /**
     * The constructor.
     */
    public LocalStorage(Path baseDir)
    {
        this.baseDir = baseDir;
    }

    @Override
    public boolean fileExists(Path path)
    {
        Path filePath = baseDir.resolve(path);
        return Files.exists(filePath);
    }

    @Override
    public void uploadFile(Path path, File file)
    {
        Path filePath = baseDir.resolve(path);

        try
        {
            Files.createDirectories(filePath.getParent());
            Files.copy(file.toPath(), filePath);
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }

    @Override
    public void uploadFile(Path path, byte[] content)
    {
        Path filePath = baseDir.resolve(path);

        try
        {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content);
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }

    @Override
    public byte[] downloadFile(Path path)
    {
        Path filePath = baseDir.resolve(path);

        try
        {
            return Files.readAllBytes(filePath);
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }

    @Override
    public InputStream downloadStream(Path path)
    {
        Path filePath = baseDir.resolve(path);

        try
        {
            return new BufferedInputStream(new FileInputStream(filePath.toFile()));
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }

    @Override
    public void deleteFile(Path path)
    {
        Path filePath = baseDir.resolve(path);

        try
        {
            Files.delete(filePath);
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }

    @Override
    public Collection<Path> listFiles(Path path)
    {
        Path dirPath = baseDir.resolve(path);
        if (!Files.isDirectory(dirPath))
        {
            return Collections.emptyList();
        }

        try
        {
            List<Path> files = new ArrayList<>();

            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    files.add(baseDir.relativize(file));
                    return FileVisitResult.CONTINUE;
                }
            });

            return files;
        }
        catch (IOException io)
        {
            throw new GitRemoteException(io);
        }
    }
}

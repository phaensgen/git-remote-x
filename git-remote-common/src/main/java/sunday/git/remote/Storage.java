package sunday.git.remote;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Common interface for all storage implementations.
 * 
 * @author Peter H&auml;nsgen
 */
public interface Storage
{
    void uploadFile(Path path, byte[] content);

    byte[] downloadFile(Path path);

    void deleteFile(Path path);

    Collection<Path> listFiles(Path dir);
}

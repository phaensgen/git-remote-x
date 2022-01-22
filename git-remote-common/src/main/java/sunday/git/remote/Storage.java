package sunday.git.remote;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Common interface for all storage implementations.
 * 
 * @author Peter H&auml;nsgen
 */
public interface Storage
{
    /**
     * Checks whether the file with the given path already exists in the repository.
     */
    boolean fileExists(Path path);

    /**
     * Upload the given file to the storage. Existing content with the same path is overwritten. The path is the
     * relative path within the repository, e.g. it may be appended to some base path as configured in the repository
     * URL.
     */
    void uploadFile(Path path, File file);

    /**
     * Upload new contents to the storage. Existing content with the same path is overwritten.The path is the relative
     * path within the repository, e.g. it may be appended to some base path as configured in the repository URL.
     */
    void uploadFile(Path path, byte[] contents);

    /**
     * Downloads an existing file from the storage. If it was not found, an
     * exception is thrown.
     */
    byte[] downloadFile(Path path);

    /**
     * Deletes the file with the given path from the storage.
     */
    void deleteFile(Path path);

    /**
     * List all files that are located within the given path and its subdirectories.
     */
    Collection<Path> listFiles(Path dir);
}

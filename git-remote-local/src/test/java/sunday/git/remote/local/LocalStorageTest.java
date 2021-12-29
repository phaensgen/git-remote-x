package sunday.git.remote.local;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit test for the local storage implementation.
 * 
 * @author Peter H&auml;nsgen
 */
public class LocalStorageTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testLocalStorage()
    {
        LocalStorage storage = new LocalStorage(temp.getRoot().toPath());

        // upload something
        Path objectPath = Path.of("objects", "6b", "dbbdcda0bbbdc57fd83bf144954c3a9f218744");
        Path refPath = Path.of("refs", "heads", "master");

        storage.uploadFile(objectPath, "test".getBytes());
        storage.uploadFile(refPath, "6bdbbdcda0bbbdc57fd83bf144954c3a9f218744".getBytes(StandardCharsets.UTF_8));

        // download again
        byte[] object = storage.downloadFile(objectPath);
        assertEquals("test", new String(object, StandardCharsets.UTF_8));

        byte[] ref = storage.downloadFile(refPath);
        assertEquals("6bdbbdcda0bbbdc57fd83bf144954c3a9f218744", new String(ref, StandardCharsets.UTF_8));

        // find files
        Collection<Path> refs = storage.listFiles(Path.of("refs"));
        assertEquals(1, refs.size());
        assertEquals("refs/heads/master", refs.iterator().next().toString());

        // delete and find again
        storage.deleteFile(refPath);
        Collection<Path> refs2 = storage.listFiles(Path.of("refs"));
        assertEquals(0, refs2.size());
    }
}

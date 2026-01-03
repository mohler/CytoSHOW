package ij.io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;

import static org.junit.Assert.*;

public class HoardManagerTest {

    private String originalUserHome;
    private Path tempHome;

    @Before
    public void setUp() throws IOException {
        // 1. Save the real user home so we can restore it later
        originalUserHome = System.getProperty("user.home");

        // 2. Create a temporary directory to act as "User Home" for this test
        tempHome = Files.createTempDirectory("CytoSHOW_Test_Env");
        
        // 3. Trick the system into thinking this temp folder is the user's home
        // This forces CacheLocation.getHoardLocation() to build paths inside our temp folder
        System.setProperty("user.home", tempHome.toAbsolutePath().toString());
    }

    @After
    public void tearDown() throws IOException {
        // 1. Restore the real user home
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }

        // 2. Nuke the temp directory (cleanup)
        Files.walk(tempHome)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    @Test
    public void testSanityCheckRemovesCorruptFiles() throws IOException {
        // Setup: Create the hoard directory manually
        Path hoard = CacheLocation.getHoardLocation();
        
        // Create a "Good" file (has content)
        Path goodFile = hoard.resolve("good.mp4");
        Files.write(goodFile, new byte[]{1, 2, 3});

        // Create a "Bad" file (0 bytes)
        Path zeroByteFile = hoard.resolve("corrupt.mp4");
        Files.createFile(zeroByteFile); 

        // Create a "Temp" file (valid content, but bad extension)
        Path tempFile = hoard.resolve("abandoned.tmp");
        Files.write(tempFile, new byte[]{1, 2, 3});

        // ACTION: Run Sanity Check
        HoardManager.performSanityCheck();

        // ASSERT: 
        assertTrue("Good file should remain", Files.exists(goodFile));
        assertFalse("0-byte file should be deleted", Files.exists(zeroByteFile));
        assertFalse(".tmp file should be deleted", Files.exists(tempFile));
    }

    @Test
    public void testGovernorEnforcesLimit() throws Exception {
        // Setup: Use reflection to set the MAX_HOARD_SIZE to a tiny limit (e.g., 50 bytes)
        setGovernorLimit(50L);

        Path hoard = CacheLocation.getHoardLocation();

        // 1. Create an "Old" file (30 bytes) - Time travel to yesterday
        Path oldFile = hoard.resolve("old_chunk.dat");
        Files.write(oldFile, new byte[30]); // 30 bytes
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minusSeconds(86400)));

        // 2. Create a "New" file (10 bytes) - Time is Now
        Path newFile = hoard.resolve("new_chunk.dat");
        Files.write(newFile, new byte[10]); // 10 bytes

        // Current Total: 40 bytes. Limit: 50 bytes. Safe.
        CacheGovernor.govern();
        assertTrue("Old file should still exist (under limit)", Files.exists(oldFile));

        // 3. Add a "Huge" file that breaks the limit (30 bytes)
        // New Total: 30 + 10 + 30 = 70 bytes. Limit: 50.
        // Expectation: The oldest file (30 bytes) should be deleted to make room.
        HoardManager.writeChunk("huge_chunk.dat", new byte[30]);

        // ASSERT
        assertFalse("Oldest file should have been evicted", Files.exists(oldFile));
        assertTrue("New file should remain", Files.exists(newFile));
        assertTrue("Huge file should remain", Files.exists(hoard.resolve("huge_chunk.dat")));
    }

    // --- Helper to hack the private MAX_HOARD_SIZE constant ---
    private void setGovernorLimit(long newLimit) throws Exception {
        Field field = CacheGovernor.class.getDeclaredField("maxHoardSize");
        field.setAccessible(true);
        
        // Remove 'final' modifier if necessary (usually not needed for static logic in tests, 
        // but strictly speaking, modifying static final fields is risky. 
        // Since the field is private static long, this usually works in standard JVMs).
        // If this fails, simply remove 'final' from the variable in CacheGovernor.java
        
        // Note: In newer Java versions (12+), modifying final static fields via reflection 
        // is blocked. If you are on Java 8 (CytoSHOW), this works perfectly.
        field.setLong(null, newLimit);
    }
}
package de.klausiiiii.mobArmyBattle.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldCleanupTest {

    @Test
    void deletesEmptyDirectory(@TempDir Path tmp) throws IOException {
        Path dir = Files.createDirectory(tmp.resolve("empty"));
        WorldCleanup.deleteRecursively(dir.toFile());
        assertFalse(Files.exists(dir));
    }

    @Test
    void deletesDirectoryWithFiles(@TempDir Path tmp) throws IOException {
        Path dir = Files.createDirectory(tmp.resolve("worlddata"));
        Files.writeString(dir.resolve("level.dat"), "binary content");
        Files.createDirectory(dir.resolve("region"));
        Files.writeString(dir.resolve("region").resolve("r.0.0.mca"), "chunkdata");

        WorldCleanup.deleteRecursively(dir.toFile());

        assertFalse(Files.exists(dir));
    }

    @Test
    void doesNothingIfDirectoryDoesNotExist(@TempDir Path tmp) {
        Path missing = tmp.resolve("doesnotexist");
        WorldCleanup.deleteRecursively(missing.toFile());
        assertFalse(Files.exists(missing));
    }
}

package de.klausiiiii.mobArmyBattle.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class WorldCleanup {

    private WorldCleanup() {
    }

    public static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        Path path = dir.toPath();
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + dir, e);
        }
    }
}

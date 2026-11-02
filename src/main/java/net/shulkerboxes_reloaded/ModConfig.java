package net.shulkerboxes_reloaded;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class ModConfig {
    private ModConfig() {}

    static synchronized boolean load(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "# Place shulker boxes only while sneaking. Use /shulkerboxes_reloaded reload to apply changes.\nsneak_place=true\n");
                return true;
            }
            return read(path);
        } catch (IOException | IllegalArgumentException exception) {
            ShulkerboxesReloaded.LOGGER.warn("[Shulkerboxes Reloaded] Cannot load configuration; using defaults.", exception);
        }
        return true;
    }

    static synchronized boolean read(Path path) throws IOException {
        return value(readProperties(path));
    }

    private static Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }

    private static boolean value(Properties properties) throws IOException {
        String value = properties.getProperty("sneak_place", "true").trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IOException("Invalid sneak_place value; expected true or false.");
        }
        return Boolean.parseBoolean(value);
    }

    static synchronized void save(Path path, boolean sneakPlace) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            properties = readProperties(path);
            value(properties); // Never overwrite an existing invalid file from the menu.
        }
        properties.setProperty("sneak_place", Boolean.toString(sneakPlace));
        StringWriter output = new StringWriter();
        properties.store(output, "Shulkerboxes Reloaded. Use /shulkerboxes_reloaded reload after manual edits.");
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".shulkerboxes_reloaded-", ".tmp");
        try {
            Files.writeString(temporary, output.toString());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

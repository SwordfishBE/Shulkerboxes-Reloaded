package net.shulkerboxes_reloaded;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class ModConfig {
    private ModConfig() {}

    static boolean load(Path path) {
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

    static boolean read(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        String value = properties.getProperty("sneak_place", "true").trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IOException("Invalid sneak_place value; expected true or false.");
        }
        return Boolean.parseBoolean(value);
    }
}

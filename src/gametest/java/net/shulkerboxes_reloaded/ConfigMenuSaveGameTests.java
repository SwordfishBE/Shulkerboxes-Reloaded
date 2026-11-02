package net.shulkerboxes_reloaded;

import java.nio.file.Files;
import java.util.Properties;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class ConfigMenuSaveGameTests {
    @GameTest
    public void menuSavePreservesOtherOptionsAndFailedSaves(GameTestHelper h) throws Exception {
        var path = ShulkerboxesReloaded.configPath();
        byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
        boolean previous = ShulkerboxesReloaded.sneakPlace;
        try {
            Files.writeString(path, "sneak_place=true\ncustom_option=keep me\n");
            var result = ShulkerboxesReloaded.saveEditedConfiguration(false);
            h.assertTrue(result.getString().startsWith("[Shulkerboxes Reloaded] Config saved"), "Save feedback prefix");
            h.assertFalse(ShulkerboxesReloaded.sneakPlace, "Successful save publishes the runtime setting");
            h.assertFalse(ShulkerboxesReloaded.configurationForEditing(), "Next editor reads the saved setting");
            Properties stored = new Properties();
            try (var reader = Files.newBufferedReader(path)) { stored.load(reader); }
            h.assertTrue(stored.getProperty("custom_option").equals("keep me"), "Unknown properties survive menu saves");

            for (String invalid : new String[] {"sneak_place=invalid\n", "sneak_place=\\uXXXX\n"}) {
                Files.writeString(path, invalid);
                result = ShulkerboxesReloaded.saveEditedConfiguration(true);
                h.assertTrue(result.getString().startsWith("[Shulkerboxes Reloaded] Config could not be saved"), "Save failure feedback");
                h.assertFalse(ShulkerboxesReloaded.sneakPlace, "Rejected save must keep previous runtime setting");
                h.assertTrue(Files.readString(path).equals(invalid), "Malformed file must remain byte-for-byte unchanged");
            }
            Files.delete(path);
            Files.createDirectory(path);
            ShulkerboxesReloaded.saveEditedConfiguration(true);
            h.assertFalse(ShulkerboxesReloaded.sneakPlace, "Write/read failure cannot publish a new setting");
            Files.delete(path);
            result = ShulkerboxesReloaded.saveEditedConfiguration(true);
            h.assertTrue(ShulkerboxesReloaded.sneakPlace && ModConfig.read(path), "A later valid save recovers");
            try (var files = Files.list(path.toAbsolutePath().getParent())) {
                h.assertFalse(files.anyMatch(file -> file.getFileName().toString().startsWith(".shulkerboxes_reloaded-")),
                        "Save leaves no temporary files");
            }
        } finally {
            ShulkerboxesReloaded.sneakPlace = previous;
            if (Files.isDirectory(path)) Files.delete(path);
            if (original == null) Files.deleteIfExists(path);
            else Files.write(path, original);
        }
        h.succeed();
    }
}

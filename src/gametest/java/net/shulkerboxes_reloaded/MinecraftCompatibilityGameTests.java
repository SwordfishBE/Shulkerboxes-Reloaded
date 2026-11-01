package net.shulkerboxes_reloaded;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import net.minecraft.gametest.framework.GameTestHelper;

public class MinecraftCompatibilityGameTests {
    @GameTest
    public void minecraftRangeIncludesSnapshotSixThroughFinalOnly(GameTestHelper h) throws Exception {
        var metadata = FabricLoader.getInstance().getModContainer(ShulkerboxesReloaded.ID).orElseThrow()
                .findPath("fabric.mod.json").orElseThrow();
        String range;
        try (var reader = Files.newBufferedReader(metadata)) {
            range = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("depends").get("minecraft").getAsString();
        }
        var predicate = VersionPredicate.parse(range);
        for (String id : new String[] {"26.4-snapshot-6", "26.4-snapshot-7", "26.4-snapshot-10",
                "26.4-pre-1", "26.4-rc-1", "26.4"}) {
            h.assertTrue(predicate.test(normalize(id)), "Metadata must accept " + id);
        }
        for (String id : new String[] {"26.3", "26.4-snapshot-1", "26.4-snapshot-5",
                "26.4.1-pre-1", "26.4.1", "26.5-snapshot-1", "26.5", "27.1"}) {
            h.assertFalse(predicate.test(normalize(id)), "Metadata must reject " + id);
        }
        h.succeed();
    }

    private static Version normalize(String id) throws Exception {
        // Match Fabric Loader's handling of modern Minecraft snapshot identifiers.
        return Version.parse(McVersionLookup.normalizeVersion(id, McVersionLookup.getRelease(id)));
    }
}

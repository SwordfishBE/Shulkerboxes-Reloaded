package net.shulkerboxes_reloaded;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;

public class ReloadCommandGameTests {
    @GameTest
    public void reloadPermissionsFeedbackAndFailureRetention(GameTestHelper h) throws Exception {
        var path = FabricLoader.getInstance().getConfigDir().resolve(ShulkerboxesReloaded.ID + ".properties");
        byte[] originalFile = Files.exists(path) ? Files.readAllBytes(path) : null;
        boolean originalSetting = ShulkerboxesReloaded.sneakPlace;
        List<String> feedback = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { feedback.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        var source = h.getLevel().getServer().createCommandSourceStack().withSource(capture)
                .withPermission(PermissionSet.ALL_PERMISSIONS);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        String command = "shulkerboxes_reloaded reload";
        try {
            Files.writeString(path, "sneak_place=false\n");
            h.assertTrue(dispatcher.execute(command, source) == 1 && !ShulkerboxesReloaded.sneakPlace,
                    "Actual registered reload command applies false");
            h.assertTrue(feedback.getLast().equals("[Shulkerboxes Reloaded] Config reloaded. sneak_place=false"),
                    "Success feedback includes prefix and effective setting");

            Files.writeString(path, "sneak_place=true\n");
            boolean denied = false;
            try { dispatcher.execute(command, source.withPermission(PermissionSet.NO_PERMISSIONS)); }
            catch (CommandSyntaxException expected) { denied = true; }
            h.assertTrue(denied && !ShulkerboxesReloaded.sneakPlace, "Non-operator cannot reload");
            h.assertTrue(dispatcher.execute(command, source) == 1 && ShulkerboxesReloaded.sneakPlace,
                    "Console/operator can reload the other setting");

            Files.writeString(path, "sneak_place=false\n");
            dispatcher.execute(command, source);
            for (String invalid : List.of("sneak_place=invalid\n", "sneak_place=\\uXXXX\n")) {
                Files.writeString(path, invalid);
                h.assertTrue(dispatcher.execute(command, source) == 0 && !ShulkerboxesReloaded.sneakPlace,
                        "Invalid reload retains the prior non-default setting");
                h.assertTrue(Files.readString(path).equals(invalid), "Failed reload must not rewrite the file");
                h.assertTrue(feedback.getLast().startsWith("[Shulkerboxes Reloaded] Config reload failed."),
                        "Failure feedback uses the requested prefix");
            }
            Files.delete(path);
            h.assertTrue(dispatcher.execute(command, source) == 0 && !ShulkerboxesReloaded.sneakPlace,
                    "Missing config retains active setting rather than silently resetting it");
            h.assertFalse(Files.exists(path), "Reload must not silently recreate a missing config");
            h.assertTrue(feedback.stream().allMatch(message -> message.startsWith("[Shulkerboxes Reloaded]")),
                    "All command feedback is prefixed");
        } finally {
            ShulkerboxesReloaded.sneakPlace = originalSetting;
            if (originalFile == null) Files.deleteIfExists(path);
            else Files.write(path, originalFile);
        }
        h.succeed();
    }
}

package net.shulkerboxes_reloaded;

import com.mojang.brigadier.CommandDispatcher;
import java.io.IOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

final class ReloadCommand {
    private ReloadCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(ShulkerboxesReloaded.ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        try {
            boolean sneakPlace;
            synchronized (ModConfig.class) {
                sneakPlace = ModConfig.read(ShulkerboxesReloaded.configPath());
                // Publish only after the complete file has been read and validated.
                ShulkerboxesReloaded.sneakPlace = sneakPlace;
            }
            final boolean applied = sneakPlace;
            source.sendSuccess(() -> Component.literal(
                    "[Shulkerboxes Reloaded] Config reloaded. sneak_place=" + applied), false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            ShulkerboxesReloaded.LOGGER.warn(
                    "[Shulkerboxes Reloaded] Config reload failed; previous settings remain active.", exception);
            source.sendFailure(Component.literal(
                    "[Shulkerboxes Reloaded] Config reload failed. Previous settings remain active. Check the server log."));
            return 0;
        }
    }
}

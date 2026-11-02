package net.shulkerboxes_reloaded;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ShulkerboxesConfigScreen {
    private ShulkerboxesConfigScreen() {}

    static Screen create(Screen parent) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer()) {
            return notice(parent, Component.literal("Multiplayer uses the server configuration. Edit the server's config file and run /shulkerboxes_reloaded reload on the server."));
        }
        final boolean initial;
        try {
            initial = ShulkerboxesReloaded.configurationForEditing();
        } catch (java.io.IOException | IllegalArgumentException exception) {
            return notice(parent, Component.literal("[Shulkerboxes Reloaded] Cannot open the configuration. The existing file has been preserved. Check config/shulkerboxes_reloaded.properties."));
        }
        boolean[] draft = {initial};
        var builder = ConfigBuilder.create().setParentScreen(parent)
                .setTitle(Component.literal("Shulkerboxes Reloaded Config"));
        builder.getOrCreateCategory(Component.literal("General")).addEntry(builder.entryBuilder()
                .startBooleanToggle(Component.literal("Sneak to place shulker boxes"), initial)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Enabled: right-click to open; sneak to place."),
                        Component.literal("Disabled: use normal placement on blocks. Applies immediately in singleplayer."))
                .setSaveConsumer(value -> draft[0] = value).build());
        builder.setSavingRunnable(() -> {
            var server = minecraft.getSingleplayerServer();
            if (minecraft.getConnection() != null && server == null) return;
            if (server != null) {
                server.execute(() -> {
                    if (server.isStopped() || minecraft.getSingleplayerServer() != server) return;
                    Component result = ShulkerboxesReloaded.saveEditedConfiguration(draft[0]);
                    minecraft.execute(() -> minecraft.setScreenAndShow(notice(parent, result)));
                });
            } else {
                Component result = ShulkerboxesReloaded.saveEditedConfiguration(draft[0]);
                // Cloth closes its own screen after the save callback returns.
                minecraft.schedule(() -> minecraft.setScreenAndShow(notice(parent, result)));
            }
        });
        return builder.build();
    }

    private static Screen notice(Screen parent, Component message) {
        return new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent),
                Component.literal("Shulkerboxes Reloaded"), message);
    }
}

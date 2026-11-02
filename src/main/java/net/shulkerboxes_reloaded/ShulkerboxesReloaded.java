package net.shulkerboxes_reloaded;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;

public final class ShulkerboxesReloaded implements ModInitializer {
    public static final String ID = "shulkerboxes_reloaded";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static volatile boolean sneakPlace = true;

    static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(ID + ".properties");
    }

    public static boolean configurationForEditing() throws IOException {
        synchronized (ModConfig.class) {
            return Files.exists(configPath()) ? ModConfig.read(configPath()) : sneakPlace;
        }
    }

    /** Caller schedules live singleplayer changes on the integrated server thread. */
    public static Component saveEditedConfiguration(boolean value) {
        synchronized (ModConfig.class) {
            try {
                ModConfig.save(configPath(), value);
                sneakPlace = value;
                return Component.literal("[Shulkerboxes Reloaded] Config saved and applied. sneak_place=" + value);
            } catch (IOException | IllegalArgumentException exception) {
                LOGGER.warn("[Shulkerboxes Reloaded] Cannot save config; previous settings remain active.", exception);
                return Component.literal("[Shulkerboxes Reloaded] Config could not be saved. Previous settings remain active. Check the log.");
            }
        }
    }

    @Override
    public void onInitialize() {
        sneakPlace = ModConfig.load(configPath());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ReloadCommand.register(dispatcher));
        String version = FabricLoader.getInstance().getModContainer(ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        LOGGER.info("[Shulkerboxes Reloaded] Mod initialized. Version: {}", version);
    }
}

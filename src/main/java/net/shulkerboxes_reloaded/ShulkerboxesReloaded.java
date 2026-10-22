package net.shulkerboxes_reloaded;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShulkerboxesReloaded implements ModInitializer {
    public static final String ID = "shulkerboxes_reloaded";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static volatile boolean sneakPlace = true;

    @Override
    public void onInitialize() {
        sneakPlace = ModConfig.load(FabricLoader.getInstance().getConfigDir().resolve(ID + ".properties"));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ReloadCommand.register(dispatcher));
        String version = FabricLoader.getInstance().getModContainer(ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        LOGGER.info("[Shulkerboxes Reloaded] Mod initialized. Version: {}", version);
    }
}

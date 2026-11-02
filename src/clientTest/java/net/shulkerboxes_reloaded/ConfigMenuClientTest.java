package net.shulkerboxes_reloaded;

import java.nio.file.Files;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;

public class ConfigMenuClientTest implements FabricClientGameTest {
    private ClothConfigScreen editor;

    private void open(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = new ShulkerboxesModMenu().getModConfigScreenFactory().create(new TitleScreen());
            if (!ShulkerboxesModMenu.hasCompatibleClothConfig()) {
                if (!(screen instanceof AlertScreen)) throw new AssertionError("Old Cloth must show a safe explanation");
                client.setScreenAndShow(screen);
                // The 26.3 widgets cannot render on 26.4. Test their prepared save
                // callbacks without attaching that incompatible screen to the client.
                editor = (ClothConfigScreen) ShulkerboxesConfigScreen.create(new TitleScreen());
            } else {
                if (!(screen instanceof ClothConfigScreen cloth)) throw new AssertionError("Expected Cloth editor");
                editor = cloth;
                client.setScreenAndShow(editor);
            }
        });
        context.waitTicks(3);
    }

    private void value(boolean value) {
        var entry = (BooleanListEntry) editor.getCategorizedEntries().values().iterator().next().getFirst();
        if (entry.getValue() != value) ((Button) entry.children().getFirst()).onPress(new KeyEvent(32, 0, 0));
        if (entry.getValue() != value) throw new AssertionError("Toggle did not change");
    }

    private void save(ClientGameTestContext context, boolean value) {
        open(context);
        context.runOnClient(client -> {
            value(value);
            editor.saveAll(true);
        });
        context.waitTicks(5);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var path = ShulkerboxesReloaded.configPath();
        boolean oldValue = ShulkerboxesReloaded.sneakPlace;
        byte[] oldBytes;
        try {
            oldBytes = Files.exists(path) ? Files.readAllBytes(path) : null;
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, "sneak_place=true\n");
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        try {
            open(context);
            context.takeScreenshot("shulkerboxes-config-availability");
            context.runOnClient(client -> value(false));
            context.setScreen(TitleScreen::new);
            if (!ShulkerboxesReloaded.configurationForEditing()) throw new AssertionError("Cancelled draft was saved");
            save(context, false);
            if (ShulkerboxesReloaded.configurationForEditing()) throw new AssertionError("Title-screen save did not persist");
            context.setScreen(TitleScreen::new);
            try (var world = context.worldBuilder().create()) {
                world.getServer().runOnServer(server -> {
                    if (ShulkerboxesReloaded.sneakPlace) throw new AssertionError("Title-screen setting did not apply to integrated world");
                });
                save(context, true);
                world.getServer().runOnServer(server -> {
                    if (!ShulkerboxesReloaded.sneakPlace) throw new AssertionError("In-world save did not apply immediately");
                });
                save(context, false);
                world.getServer().runOnServer(server -> {
                    if (ShulkerboxesReloaded.sneakPlace) throw new AssertionError("Second live toggle failed");
                });
            }
            Files.writeString(path, "sneak_place=invalid\n");
            context.runOnClient(client -> {
                if (!(ShulkerboxesConfigScreen.create(new TitleScreen()) instanceof AlertScreen))
                    throw new AssertionError("Invalid existing file needs a notice, not a writable editor");
            });
            if (!Files.readString(path).equals("sneak_place=invalid\n")) throw new AssertionError("Opening menu overwrote invalid config");
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        finally {
            context.setScreen(TitleScreen::new);
            ShulkerboxesReloaded.sneakPlace = oldValue;
            try {
                if (oldBytes == null) Files.deleteIfExists(path);
                else Files.write(path, oldBytes);
            } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        }
    }
}

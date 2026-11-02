package net.shulkerboxes_reloaded;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** No references to Cloth classes: verifies the actual optional-dependency boundary. */
public class MissingClothClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) throw new AssertionError("Cloth must be absent in this run");
        context.runOnClient(client -> {
            var screen = new ShulkerboxesModMenu().getModConfigScreenFactory().create(new TitleScreen());
            if (!(screen instanceof AlertScreen)) throw new AssertionError("Missing Cloth must show an explanation");
            client.setScreenAndShow(screen);
        });
        context.waitTicks(3);
        context.takeScreenshot("shulkerboxes-missing-cloth");
        context.setScreen(TitleScreen::new);
    }
}

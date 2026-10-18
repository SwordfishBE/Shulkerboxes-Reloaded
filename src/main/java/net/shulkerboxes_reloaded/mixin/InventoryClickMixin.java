package net.shulkerboxes_reloaded.mixin;

import net.shulkerboxes_reloaded.BoxOpening;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class InventoryClickMixin {
    @Shadow public ServerPlayer player;

    // Run after vanilla's thread handoff, before it suppresses menu updates.
    // Opening inside AbstractContainerMenu.clicked would leave the old menu suppressed
    // and apply the old inventory packet's prediction to the new shulker menu.
    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V",
            shift = At.Shift.AFTER), cancellable = true)
    private void shulkerboxesReloaded$inventoryOpen(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        var menu = player.containerMenu;
        int slot = packet.slotNum();
        if (menu != player.inventoryMenu || packet.containerId() != menu.containerId
                || player.isSpectator() || !player.isAlive()
                || packet.containerInput() != ContainerInput.PICKUP || packet.buttonNum() != 1
                || !menu.getCarried().isEmpty() || slot < 0 || slot >= menu.slots.size()) return;
        var target = menu.slots.get(slot);
        if (target.container == player.getInventory() && BoxOpening.isBox(target.getItem())) {
            if (!BoxOpening.open(player, target.getContainerSlot())) menu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}

package net.shulkerboxes_reloaded.mixin;

import net.shulkerboxes_reloaded.BoxOpening;
import net.shulkerboxes_reloaded.ShulkerboxesReloaded;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void shulkerboxesReloaded$useOn(UseOnContext context,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (!ShulkerboxesReloaded.sneakPlace || context.getPlayer() == null
                || context.getPlayer().isShiftKeyDown()) return;
        InteractionResult result = BoxOpening.use(context.getPlayer(), context.getHand());
        if (result != InteractionResult.PASS) cir.setReturnValue(result);
    }
}

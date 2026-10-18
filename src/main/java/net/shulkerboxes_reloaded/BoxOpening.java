package net.shulkerboxes_reloaded;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

public final class BoxOpening {
    private BoxOpening() {}

    public static boolean isBox(ItemStack stack) {
        return stack.getCount() == 1 && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof ShulkerBoxBlock;
    }

    public static InteractionResult use(Player player, InteractionHand hand) {
        if (player == null || player.isSpectator() || !isBox(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND);
        }
        return InteractionResult.SUCCESS;
    }

    public static boolean open(ServerPlayer player, int inventorySlot) {
        if (player.isSpectator() || !player.isAlive() || player.isRemoved() || player.hasDisconnected() || inventorySlot < 0
                || inventorySlot >= player.getInventory().getContainerSize()) {
            return false;
        }
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (!isBox(stack) || !hasSupportedContents(stack)) return false;
        var lock = stack.get(DataComponents.LOCK);
        if (lock != null && !lock.canUnlock(player)) {
            BaseContainerBlockEntity.sendChestLockedNotifications(player.position(), player, stack.getHoverName());
            return false;
        }
        // Close the previous menu before taking a snapshot of this box's contents.
        // This also flushes pending changes if another mod requests a reopen.
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        } else {
            // Inventory right-click can open while the 2x2 crafting grid is occupied.
            // Vanilla openMenu skips InventoryMenu.removed; return its inputs/cursor
            // before hiding that menu so they cannot be stranded outside saved storage.
            player.doCloseContainer();
        }
        // Menu close callbacks may change the inventory, including this very slot.
        if (player.getInventory().getItem(inventorySlot) != stack || !isBox(stack)
                || !hasSupportedContents(stack)) return false;
        lock = stack.get(DataComponents.LOCK);
        if (lock != null && !lock.canUnlock(player)) return false;
        return player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new PortableBoxMenu(id, inventory, inventorySlot), stack.getHoverName())).isPresent();
    }

    private static boolean hasSupportedContents(ItemStack stack) {
        var contents = stack.get(DataComponents.CONTAINER);
        // Never silently truncate extended storage or combine existing items with
        // an unresolved loot table. Leave such boxes untouched for their provider.
        return contents == null || (contents.size() <= 27
                && (!stack.has(DataComponents.CONTAINER_LOOT)
                    || contents.nonEmptyItemCopyStream().findAny().isEmpty()));
    }
}

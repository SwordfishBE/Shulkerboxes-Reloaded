package net.shulkerboxes_reloaded;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PortableBoxMenu extends ShulkerBoxMenu {
    private final BoxInventory contents;
    private final int sourceSlot;

    PortableBoxMenu(int id, Inventory inventory, int sourceSlot) {
        this(id, inventory, sourceSlot, new BoxInventory((ServerPlayer) inventory.player, sourceSlot));
    }

    private PortableBoxMenu(int id, Inventory inventory, int sourceSlot, BoxInventory contents) {
        super(id, inventory, contents);
        this.contents = contents;
        this.sourceSlot = sourceSlot;
        for (int i = 27; i < slots.size(); i++) {
            Slot old = slots.get(i);
            if (old.container == inventory && old.getContainerSlot() == sourceSlot) {
                Slot locked = new Slot(inventory, sourceSlot, old.x, old.y) {
                    @Override public boolean mayPickup(Player player) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                };
                locked.index = old.index;
                slots.set(i, locked);
            }
        }
    }

    private boolean isSource(int slot) {
        return slot >= 0 && slot < slots.size() && slots.get(slot).container instanceof Inventory
                && slots.get(slot).getContainerSlot() == sourceSlot;
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (!stillValid(player) || isSource(slot)
                || (input == ContainerInput.SWAP && button == sourceSlot)) return;
        contents.beginChanges();
        try {
            super.clicked(slot, button, input, player);
        } finally {
            contents.endChanges();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        if (!stillValid(player) || isSource(slot) || slot < 0 || slot >= slots.size()) return ItemStack.EMPTY;
        contents.beginChanges();
        try {
            return super.quickMoveStack(player, slot);
        } finally {
            contents.endChanges();
        }
    }

    @Override
    public void broadcastChanges() {
        if (contents != null) contents.flush();
        super.broadcastChanges();
    }

    @Override
    public void setSelectedBundleItemIndex(int slot, int selectedItem) {
        if (!contents.isCurrentSession()) return;
        super.setSelectedBundleItemIndex(slot, selectedItem);
        if (slot >= 0 && slot < 27) {
            contents.setChanged();
            contents.flush();
        }
    }
}

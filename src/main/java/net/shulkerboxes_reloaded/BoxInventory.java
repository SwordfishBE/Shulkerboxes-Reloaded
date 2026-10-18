package net.shulkerboxes_reloaded;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

final class BoxInventory extends SimpleContainer {
    private final ServerPlayer owner;
    private final int sourceSlot;
    private final ItemStack source;
    private ItemContainerContents savedContents;
    private boolean dirty;
    private boolean opened;
    private boolean conflicted;
    private boolean closed;
    private int changeDepth = 1;

    BoxInventory(ServerPlayer owner, int sourceSlot) {
        super(27);
        this.owner = owner;
        this.sourceSlot = sourceSlot;
        source = owner.getInventory().getItem(sourceSlot);
        savedContents = source.get(DataComponents.CONTAINER);
        if (savedContents != null) savedContents.copyInto(getItems());
        var loot = source.get(DataComponents.CONTAINER_LOOT);
        if (loot != null) {
            var table = owner.level().getServer().reloadableRegistries().getLootTable(loot.lootTable());
            var params = new LootParams.Builder(owner.level())
                    .withParameter(LootContextParams.ORIGIN, owner.position())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, owner).withLuck(owner.getLuck());
            table.fill(this, params.create(LootContextParamSets.CHEST), loot.seed());
            source.remove(DataComponents.CONTAINER_LOOT);
            dirty = true;
        }
        endChanges();
    }

    @Override
    public void setChanged() {
        dirty = true;
        if (changeDepth == 0) flush();
    }

    void beginChanges() {
        changeDepth++;
    }

    void endChanges() {
        if (--changeDepth == 0) flush();
    }

    void flush() {
        if (!dirty || changeDepth != 0 || closed) return;
        if (!contentsUnchanged() || owner.getInventory().getItem(sourceSlot) != source || source.getCount() != 1) {
            if (!conflicted) ShulkerboxesReloaded.LOGGER.warn(
                    "[Shulkerboxes Reloaded] Shulker contents changed outside the open menu; refusing to overwrite them.");
            conflicted = true;
            return;
        }
        savedContents = ItemContainerContents.fromItems(getItems());
        source.set(DataComponents.CONTAINER, savedContents);
        owner.getInventory().setChanged();
        dirty = false;
    }

    private boolean contentsUnchanged() {
        return !conflicted && java.util.Objects.equals(source.get(DataComponents.CONTAINER), savedContents)
                && !source.has(DataComponents.CONTAINER_LOOT);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = super.removeItemNoUpdate(slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public boolean stillValid(Player player) {
        // Identity prevents an identical replacement box from inheriting this session.
        return opened && !closed && player == owner && owner.isAlive() && !owner.isSpectator()
                && !owner.isRemoved() && !owner.hasDisconnected()
                && owner.getInventory().getItem(sourceSlot) == source && source.getCount() == 1
                && contentsUnchanged();
    }

    boolean isCurrentSession() {
        return stillValid(owner);
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (!opened && !closed) {
            opened = true;
            sound(SoundEvents.SHULKER_BOX_OPEN);
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        flush();
        closed = true;
        if (opened) {
            opened = false;
            sound(SoundEvents.SHULKER_BOX_CLOSE);
        }
    }

    private void sound(SoundEvent sound) {
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), sound,
                SoundSource.BLOCKS, 0.5F, 0.9F + owner.getRandom().nextFloat() * 0.1F);
    }
}

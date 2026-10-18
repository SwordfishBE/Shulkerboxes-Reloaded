package net.shulkerboxes_reloaded;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerboxesGameTests {
    private ServerPlayer player(GameTestHelper h) {
        return player(h, GameType.SURVIVAL);
    }

    private ServerPlayer player(GameTestHelper h, GameType mode) {
        ServerPlayer p = (ServerPlayer) h.makeMockServerPlayer(mode);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public boolean hasClientLoaded() { return true; }
        };
        return p;
    }

    private ItemStack box(ServerPlayer p, int slot) {
        ItemStack box = new ItemStack(Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.PURPLE));
        p.getInventory().setItem(slot, box);
        return box;
    }

    private PortableBoxMenu open(GameTestHelper h, ServerPlayer p, int slot) {
        h.assertTrue(BoxOpening.open(p, slot), "Box must open");
        h.assertTrue(p.containerMenu.getType() == MenuType.SHULKER_BOX, "Use vanilla client menu");
        return (PortableBoxMenu) p.containerMenu;
    }

    private int count(ItemStack box, net.minecraft.world.item.Item item) {
        return box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyItemCopyStream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest
    public void handUseMixinAndAllInventorySlots(GameTestHelper h) {
        for (int i = 0; i < 36; i++) {
            ServerPlayer p = player(h);
            box(p, i);
            var menu = open(h, p, i);
            h.assertTrue(menu.stillValid(p), "Valid inventory slot " + i);
            p.closeContainer();
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ServerPlayer p = player(h);
            ItemStack stack = new ItemStack(Items.SHULKER_BOX);
            p.setItemInHand(hand, stack);
            stack.getItem().use(h.getLevel(), p, hand);
            h.assertTrue(p.containerMenu instanceof PortableBoxMenu, "Item.use mixin must open " + hand);
            p.closeContainer();
        }
        h.succeed();
    }

    @GameTest
    public void contentsPersistAcrossClicksAndReopen(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("My supplies"));
        SimpleContainer initial = new SimpleContainer(27);
        initial.setItem(5, new ItemStack(Items.DIAMOND, 11));
        source.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(initial.getItems()));
        var menu = open(h, p, 0);
        h.assertTrue(menu.getSlot(5).getItem().getCount() == 11, "Load existing contents");
        menu.clicked(5, 0, ContainerInput.PICKUP, p);
        h.assertTrue(count(source, Items.DIAMOND) == 0 && menu.getCarried().getCount() == 11,
                "Pickup persists before close");
        menu.clicked(8, 0, ContainerInput.PICKUP, p);
        h.assertTrue(count(source, Items.DIAMOND) == 11, "Deposit persists before close");
        p.closeContainer();
        var reopened = open(h, p, 0);
        h.assertTrue(reopened.getSlot(8).getItem().getCount() == 11, "Reopen preserves new slot");
        h.assertTrue(source.getHoverName().getString().equals("My supplies"), "Keep custom name");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void sourceCannotMoveThroughAnyClickType(GameTestHelper h) {
        for (int sourceSlot : new int[] {0, 8, 9, 35, 40}) {
            ServerPlayer p = player(h);
            ItemStack source = box(p, sourceSlot);
            var menu = open(h, p, sourceSlot);
            int menuSlot = sourceSlot == 40 ? -1 : menu.findSlot(p.getInventory(), sourceSlot).orElseThrow();
            if (menuSlot >= 0) {
                for (ContainerInput input : ContainerInput.values()) {
                    menu.clicked(menuSlot, 0, input, p);
                    menu.clicked(menuSlot, 1, input, p);
                }
                h.assertTrue(menu.quickMoveStack(p, menuSlot).isEmpty(), "Direct quick move is blocked");
            }
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));
            menu.broadcastChanges();
            if (sourceSlot < 9 || sourceSlot == 40) menu.clicked(0, sourceSlot, ContainerInput.SWAP, p);
            h.assertTrue(p.getInventory().getItem(sourceSlot) == source, "Source must remain in slot " + sourceSlot);
            h.assertTrue(menu.getCarried().isEmpty(), "Source cannot enter cursor");
            h.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND), "Hotbar/offhand swap cannot replace source");
            p.closeContainer();
        }
        h.succeed();
    }

    @GameTest
    public void shiftTransfersAndNestedBoxes(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0);
        p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
        p.getInventory().setItem(10, new ItemStack(Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.BLUE)));
        var menu = open(h, p, 0);
        menu.clicked(27, 0, ContainerInput.QUICK_MOVE, p);
        h.assertTrue(count(source, Items.EMERALD) == 64, "Shift deposit persists");
        menu.clicked(28, 0, ContainerInput.QUICK_MOVE, p);
        h.assertTrue(count(source, Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.BLUE)) == 0, "Cannot nest through shift click");
        menu.setCarried(new ItemStack(Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.RED)));
        menu.clicked(1, 0, ContainerInput.PICKUP, p);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty(), "Cannot nest through cursor");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
        h.assertTrue(count(source, Items.EMERALD) == 0 && p.getInventory().countItem(Items.EMERALD) == 64,
                "Shift withdraw conserves items");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void dragAndHotbarSwapPersist(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0);
        p.getInventory().setItem(1, new ItemStack(Items.GOLD_INGOT, 5));
        var menu = open(h, p, 0);
        menu.clicked(2, 1, ContainerInput.SWAP, p);
        h.assertTrue(count(source, Items.GOLD_INGOT) == 5, "Other hotbar slots can swap");
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 12));
        menu.clicked(-999, 0, ContainerInput.QUICK_CRAFT, p);
        menu.clicked(0, 1, ContainerInput.QUICK_CRAFT, p);
        menu.clicked(1, 1, ContainerInput.QUICK_CRAFT, p);
        menu.clicked(-999, 2, ContainerInput.QUICK_CRAFT, p);
        h.assertTrue(count(source, Items.IRON_INGOT) == 12 && menu.getCarried().isEmpty(), "Drag persists all items");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void replacementIdentityAndSpectatorRejected(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0);
        var menu = open(h, p, 0);
        p.getInventory().setItem(0, source.copy());
        h.assertFalse(menu.stillValid(p), "Identical replacement must invalidate session");
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(0, 0, ContainerInput.PICKUP, p);
        h.assertTrue(count(source, Items.DIAMOND) == 0, "Invalid session cannot change contents");
        menu.setCarried(ItemStack.EMPTY);
        p.closeContainer();
        ServerPlayer spectator = player(h, GameType.SPECTATOR);
        box(spectator, 0);
        h.assertFalse(BoxOpening.open(spectator, 0), "Spectator cannot open portable box");
        p.getInventory().setItem(0, new ItemStack(Items.SHULKER_BOX, 2));
        h.assertFalse(BoxOpening.open(p, 0), "Nonstandard stacked box is rejected");
        h.succeed();
    }

    @GameTest
    public void inventoryClickPacketOpensWithoutPickingUp(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 9);
        var inventory = p.inventoryMenu;
        int slot = inventory.findSlot(p.getInventory(), 9).orElseThrow();
        var packet = new ServerboundContainerClickPacket(inventory.containerId, inventory.getStateId(),
                (short) slot, (byte) 1, ContainerInput.PICKUP, new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY);
        p.connection.handleContainerClick(packet);
        h.assertTrue(p.containerMenu instanceof PortableBoxMenu, "Packet mixin opens inventory box");
        h.assertTrue(p.getInventory().getItem(9) == source && p.containerMenu.getCarried().isEmpty(),
                "No phantom cursor or removed source");
        p.closeContainer();
        inventory.clicked(slot, 0, ContainerInput.PICKUP, p);
        h.assertTrue(BoxOpening.isBox(inventory.getCarried())
                && p.getInventory().getItem(9).isEmpty(), "Normal left click still moves the box after closing");
        inventory.clicked(slot, 0, ContainerInput.PICKUP, p);
        h.succeed();
    }

    @GameTest
    public void sneakPlacementAndConfig(GameTestHelper h) {
        ServerPlayer p = player(h);
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        boolean previous = ShulkerboxesReloaded.sneakPlace;
        try {
            ShulkerboxesReloaded.sneakPlace = true;
            ItemStack source = box(p, 0);
            source.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
            h.assertTrue(p.containerMenu instanceof PortableBoxMenu, "Use on block opens when standing");
            h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(), "Standing must not place");
            p.closeContainer();
            p.setShiftKeyDown(true);
            source.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
            h.assertTrue(h.getLevel().getBlockState(pos.above()).is(Blocks.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.PURPLE)), "Sneaking places");
            h.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            p.setShiftKeyDown(false);
            ShulkerboxesReloaded.sneakPlace = false;
            source = box(p, 0);
            source.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
            h.assertTrue(h.getLevel().getBlockState(pos.above()).is(Blocks.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.PURPLE)), "Config false allows normal placement");
        } finally {
            ShulkerboxesReloaded.sneakPlace = previous;
        }
        h.succeed();
    }

    @GameTest
    public void lazyLootGeneratedOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0);
        source.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(BuiltInLootTables.SPAWN_BONUS_CHEST, 1234));
        var menu = open(h, p, 0);
        h.assertFalse(source.has(DataComponents.CONTAINER_LOOT), "Loot seed removed");
        var generated = source.get(DataComponents.CONTAINER);
        h.assertTrue(generated != null && generated.nonEmptyItemCopyStream().findAny().isPresent(), "Loot generated with valid context");
        p.closeContainer();
        open(h, p, 0);
        h.assertTrue(generated.equals(source.get(DataComponents.CONTAINER)), "No reroll on reopen");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void everyColorAndLockedBox(GameTestHelper h) {
        for (var color : net.minecraft.world.item.DyeColor.values()) {
            ServerPlayer p = player(h);
            ItemStack source = new ItemStack(Items.DYED_SHULKER_BOX.pick(color));
            p.getInventory().setItem(0, source);
            open(h, p, 0);
            p.closeContainer();
            h.assertTrue(source.is(Items.DYED_SHULKER_BOX.pick(color)), "Keep color " + color);
        }
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0);
        var predicate = net.minecraft.advancements.predicates.ItemPredicate.Builder.item()
                .of(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM),
                        Items.TRIPWIRE_HOOK).build();
        source.set(DataComponents.LOCK, new net.minecraft.world.LockCode(predicate));
        h.assertFalse(BoxOpening.open(p, 0), "Locked box must reject missing key");
        h.assertTrue(p.containerMenu == p.inventoryMenu, "Rejected lock must leave inventory active");
        p.getInventory().setItem(1, new ItemStack(Items.TRIPWIRE_HOOK));
        p.getInventory().setSelectedSlot(1);
        open(h, p, 0);
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void dirtyBatchingAndIdleMenu(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0);
        BoxInventory contents = new BoxInventory(p, 0);
        var original = source.get(DataComponents.CONTAINER);
        contents.beginChanges();
        for (int i = 0; i < 27; i++) contents.setItem(i, new ItemStack(Items.DIAMOND, i + 1));
        h.assertTrue(source.get(DataComponents.CONTAINER) == original, "Slot changes are batched");
        contents.endChanges();
        h.assertTrue(count(source, Items.DIAMOND) == 378, "One flush saves all 27 slots");
        var saved = source.get(DataComponents.CONTAINER);
        var menu = open(h, p, 0);
        for (int i = 0; i < 100; i++) menu.broadcastChanges();
        p.closeContainer();
        h.assertTrue(source.get(DataComponents.CONTAINER) == saved, "Idle/close never rebuild unchanged contents");
        h.succeed();
    }
}



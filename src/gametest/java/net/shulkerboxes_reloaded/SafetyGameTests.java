package net.shulkerboxes_reloaded;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

public class SafetyGameTests {
    private ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public boolean hasClientLoaded() { return true; }
        };
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        return p;
    }

    private ItemStack box(ServerPlayer p, int slot, int diamonds) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
                java.util.List.of(new ItemStack(Items.DIAMOND, diamonds))));
        p.getInventory().setItem(slot, box);
        return box;
    }

    private int count(ItemStack box, Item item) {
        return box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyItemCopyStream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private void rightClickBox(ServerPlayer p, int inventorySlot) {
        var menu = p.inventoryMenu;
        int slot = menu.findSlot(p.getInventory(), inventorySlot).orElseThrow();
        p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                (short) slot, (byte) 1, ContainerInput.PICKUP, new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
    }

    @GameTest
    public void inventoryOpeningReturnsCraftingInputs(GameTestHelper h) {
        ServerPlayer p = player(h);
        box(p, 9, 8);
        p.inventoryMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 13));
        rightClickBox(p, 9);
        h.assertTrue(p.containerMenu instanceof PortableBoxMenu, "Open through inventory packet");
        h.assertTrue(p.inventoryMenu.getSlot(1).getItem().isEmpty()
                && p.getInventory().countItem(Items.EMERALD) == 13,
                "Crafting inputs must return to saved inventory before changing menus");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void externalContentsMustNotBeOverwritten(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 8);
        BoxOpening.open(p, 0);
        var menu = p.containerMenu;
        source.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(java.util.List.of(
                new ItemStack(Items.DIAMOND, 8), new ItemStack(Items.EMERALD, 17))));
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
        menu.clicked(2, 0, ContainerInput.PICKUP, p);
        h.assertTrue(count(source, Items.EMERALD) == 17, "External deposit must not be overwritten by stale menu");
        h.assertTrue(menu.getCarried().getCount() == 3, "Rejected deposit stays on cursor");
        p.closeContainer();
        h.assertTrue(count(source, Items.DIAMOND) == 8 && count(source, Items.EMERALD) == 17
                && p.getInventory().countItem(Items.IRON_INGOT) == 3, "Close preserves both external contents and cursor");
        h.succeed();
    }

    @GameTest
    public void oversizedContentsAreNeverTruncated(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 0);
        SimpleContainer large = new SimpleContainer(54);
        large.setItem(40, new ItemStack(Items.DIAMOND, 31));
        var original = ItemContainerContents.fromItems(large.getItems());
        source.set(DataComponents.CONTAINER, original);
        h.assertFalse(BoxOpening.open(p, 0), "A 27-slot menu must refuse larger existing contents");
        h.assertTrue(source.get(DataComponents.CONTAINER).equals(original), "All extra slots remain untouched");
        h.succeed();
    }

    @GameTest
    public void closingPreviousMenuRevalidatesSource(GameTestHelper h) {
        ServerPlayer p = player(h);
        box(p, 0, 8);
        p.containerMenu = new ShulkerBoxMenu(99, p.getInventory(), new SimpleContainer(27)) {
            @Override public void removed(net.minecraft.world.entity.player.Player ignored) {
                super.removed(ignored);
                p.getInventory().setItem(0, new ItemStack(Items.STONE));
            }
        };
        h.assertFalse(BoxOpening.open(p, 0), "Closing callbacks can replace source: opening must revalidate");
        h.assertTrue(p.containerMenu == p.inventoryMenu && p.getInventory().getItem(0).is(Items.STONE),
                "Never open a stale session over a replacement item");
        h.succeed();
    }

    @GameTest
    public void directSlotEditsPersistBeforePlayerSave(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 8);
        BoxOpening.open(p, 0);
        // Inventory integrations can use the exposed Container without menu.clicked.
        p.containerMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 19));
        h.assertTrue(count(source, Items.EMERALD) == 19, "Direct container edits must persist before the next broadcast/save");
        p.containerMenu.getSlot(1).container.removeItemNoUpdate(1);
        h.assertTrue(count(source, Items.EMERALD) == 0, "No-update removal must not resurrect removed contents");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void closedMenuCannotBeUsedAgain(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 8);
        BoxOpening.open(p, 0);
        var oldMenu = p.containerMenu;
        p.closeContainer();
        h.assertFalse(oldMenu.stillValid(p), "Closed session must be permanently invalid");
        oldMenu.clicked(0, 0, ContainerInput.PICKUP, p);
        h.assertTrue(count(source, Items.DIAMOND) == 8, "Stale callback cannot withdraw from a closed session");
        h.succeed();
    }

    private int units(ItemStack stack, Item item) {
        int result = stack.is(item) ? stack.getCount() : 0;
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null) result += container.nonEmptyItemCopyStream().mapToInt(s -> units(s, item)).sum();
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) result += bundle.itemCopies().mapToInt(s -> units(s, item)).sum();
        return result;
    }

    private int total(ServerPlayer p, Item item) {
        int total = 0;
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
            total += units(p.getInventory().getItem(slot), item);
        }
        total += units(p.containerMenu.getCarried(), item);
        for (var entity : p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                p.getBoundingBox().inflate(3))) total += units(entity.getItem(), item);
        return total;
    }

    @GameTest
    public void closeWithFullInventoryAndCursorConservesItems(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 23);
        for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        BoxOpening.open(p, 0);
        p.containerMenu.clicked(0, 0, ContainerInput.PICKUP, p);
        h.assertTrue(count(source, Items.DIAMOND) == 0, "Withdrawal persists");
        p.closeContainer();
        h.assertTrue(total(p, Items.DIAMOND) == 23, "Full inventory close must retain or drop all cursor items");
        h.succeed();
    }

    @GameTest
    public void disconnectWithCursorConservesItems(GameTestHelper h) {
        ServerPlayer p = player(h);
        box(p, 0, 23);
        BoxOpening.open(p, 0);
        p.containerMenu.clicked(0, 1, ContainerInput.PICKUP, p);
        p.disconnect();
        // Vanilla removes the player after saving inventory, and drops the cursor.
        p.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        h.assertTrue(total(p, Items.DIAMOND) == 23, "Disconnect and player removal conserve box plus cursor contents");
        h.succeed();
    }

    @GameTest
    public void deathWithOpenBoxConservesDrops(GameTestHelper h) {
        ServerPlayer p = player(h);
        box(p, 0, 23);
        BoxOpening.open(p, 0);
        p.containerMenu.clicked(0, 1, ContainerInput.PICKUP, p);
        var rules = h.getLevel().getGameRules();
        boolean keep = rules.get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY);
        try {
            rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY, false, h.getLevel().getServer());
            p.hurtServer(h.getLevel(), p.damageSources().generic(), 1000);
            h.assertFalse(p.isAlive(), "Test must execute actual death");
            h.assertTrue(total(p, Items.DIAMOND) == 23, "Death preserves total across dropped box and cursor");
        } finally {
            rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY, keep, h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest
    public void savedPlayerRoundTripPreservesContents(GameTestHelper h) {
        ServerPlayer p = player(h);
        box(p, 0, 23);
        BoxOpening.open(p, 0);
        p.containerMenu.clicked(0, 1, ContainerInput.PICKUP, p);
        p.containerMenu.clicked(8, 0, ContainerInput.PICKUP, p);
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        p.saveWithoutId(output);
        ServerPlayer restored = player(h);
        restored.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(count(restored.getInventory().getItem(0), Items.DIAMOND) == 23,
                "Player save/load while the menu is open retains full contents");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void randomizedSurvivalTransfersConserveItems(GameTestHelper h) {
        randomizedTransfers(h, 40);
    }

    @GameTest
    public void randomizedMainHandTransfersConserveItems(GameTestHelper h) {
        randomizedTransfers(h, 0);
    }

    @GameTest
    public void randomizedInventoryTransfersConserveItems(GameTestHelper h) {
        randomizedTransfers(h, 35);
    }

    private void packetClick(ServerPlayer p, int slot, int button, ContainerInput input) {
        var menu = p.containerMenu;
        p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                (short) slot, (byte) button, input, new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
    }

    private void randomizedTransfers(GameTestHelper h, int sourceSlot) {
        ServerPlayer p = player(h);
        box(p, sourceSlot, 64);
        p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
        p.getInventory().setItem(10, new ItemStack(Items.IRON_INGOT, 64));
        BoxOpening.open(p, sourceSlot);
        var menu = p.containerMenu;
        java.util.Random random = new java.util.Random(20261018L + sourceSlot);
        for (int step = 0; step < 2500; step++) {
            int slot = random.nextInt(menu.slots.size());
            switch (random.nextInt(6)) {
                case 0 -> packetClick(p, slot, random.nextInt(2), ContainerInput.PICKUP);
                case 1 -> packetClick(p, slot, 0, ContainerInput.QUICK_MOVE);
                case 2 -> packetClick(p, slot, random.nextBoolean() ? random.nextInt(9) : 40, ContainerInput.SWAP);
                case 3 -> packetClick(p, slot, random.nextInt(2), ContainerInput.THROW);
                case 4 -> packetClick(p, slot, 0, ContainerInput.PICKUP_ALL);
                case 5 -> {
                    packetClick(p, -999, 0, ContainerInput.QUICK_CRAFT);
                    packetClick(p, slot, 1, ContainerInput.QUICK_CRAFT);
                    packetClick(p, random.nextInt(menu.slots.size()), 1, ContainerInput.QUICK_CRAFT);
                    packetClick(p, -999, 2, ContainerInput.QUICK_CRAFT);
                }
            }
            for (Item item : new Item[] {Items.DIAMOND, Items.EMERALD, Items.IRON_INGOT}) {
                h.assertTrue(total(p, item) == 64, "Item conservation failed at random step " + step + " for " + item);
            }
        }
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void cursorAndFullCraftingGridReturnOnOpen(GameTestHelper h) {
        ServerPlayer p = player(h);
        box(p, 0, 5);
        for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        for (int i = 1; i <= 4; i++) p.inventoryMenu.getSlot(i).set(new ItemStack(Items.EMERALD, 10));
        p.inventoryMenu.setCarried(new ItemStack(Items.IRON_INGOT, 7));
        h.assertTrue(BoxOpening.open(p, 0), "Hand/API opening cleans up occupied inventory menu");
        h.assertTrue(total(p, Items.EMERALD) == 40 && total(p, Items.IRON_INGOT) == 7,
                "Full inventory returns or drops every crafting and cursor item");
        p.closeContainer();
        h.succeed();
    }

    @GameTest
    public void bundleInteractionsPersistContents(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 12);
        BoxOpening.open(p, 0);
        var menu = p.containerMenu;
        menu.setCarried(new ItemStack(Items.BUNDLE));
        menu.clicked(0, 1, ContainerInput.PICKUP, p);
        h.assertTrue(total(p, Items.DIAMOND) == 12, "Taking box contents into carried bundle conserves items");
        menu.clicked(1, 0, ContainerInput.PICKUP, p);
        menu.setCarried(new ItemStack(Items.EMERALD, 7));
        menu.clicked(1, 1, ContainerInput.PICKUP, p);
        h.assertTrue(total(p, Items.DIAMOND) == 12 && total(p, Items.EMERALD) == 7,
                "Adding to stored bundle conserves items");
        menu.setSelectedBundleItemIndex(1, 0);
        menu.clicked(1, 1, ContainerInput.PICKUP, p);
        h.assertTrue(total(p, Items.DIAMOND) == 12 && total(p, Items.EMERALD) == 7,
                "Removing selected bundle contents conserves items");
        p.closeContainer();
        h.assertTrue(total(p, Items.DIAMOND) == 12 && total(p, Items.EMERALD) == 7, "Bundle state survives close");
        h.succeed();
    }

    @GameTest
    public void mixedLootAndExistingContentsAreRefused(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 12);
        var original = source.copy();
        var loot = new net.minecraft.world.item.component.SeededContainerLoot(
                net.minecraft.world.level.storage.loot.BuiltInLootTables.SPAWN_BONUS_CHEST, 9);
        source.set(DataComponents.CONTAINER_LOOT, loot);
        h.assertFalse(BoxOpening.open(p, 0), "Ambiguous loot plus existing contents must be refused");
        h.assertTrue(count(source, Items.DIAMOND) == 12 && source.get(DataComponents.CONTAINER_LOOT).equals(loot),
                "Refused opening preserves items and loot seed");
        h.succeed();
    }

    @GameTest
    public void placeAfterPortableEditPreservesContents(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack source = box(p, 0, 23);
        BoxOpening.open(p, 0);
        p.containerMenu.clicked(0, 0, ContainerInput.PICKUP, p);
        p.containerMenu.clicked(8, 0, ContainerInput.PICKUP, p);
        p.closeContainer();
        var base = h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(base, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 3)));
        p.setShiftKeyDown(true);
        source.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(p,
                net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(base), net.minecraft.core.Direction.UP, base, false)));
        var placed = h.getLevel().getBlockEntity(base.above());
        h.assertTrue(placed instanceof net.minecraft.world.Container
                && ((net.minecraft.world.Container) placed).countItem(Items.DIAMOND) == 23,
                "Placing after portable edits transfers all contents into the block entity");
        h.succeed();
    }

}


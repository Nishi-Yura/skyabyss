package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.FLOOR;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

// 背の野営の道具 (CampBlocks) の自己テスト (SKYABYSS_SELFTEST=1。SKYABYSS_SELFTEST_ONLY=camp で単独に。岩鯨の背を建てたあとに流す)。
// 置けないブロックの手持ちが減らないこと (本線・オフハンド・二段・半ブロック)、置ける道具、仕掛けのそば、数の上限、
// 持ち主だけが外せて中身ごと戻ること、背中を出ると全部戻ること、オフラインの持ち主は次のログインで受け取れること
public final class CampSelfTest {
    private CampSelfTest() {
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.setPos(0.5, FLOOR + 1, -20.5);
        return p;
    }

    // 床の上 (pos の下が床) へ、手に持った品を置こうとする
    private static InteractionResult place(ServerLevel level, FakePlayer p, InteractionHand hand, BlockPos pos) {
        p.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
        ItemStack stack = p.getItemInHand(hand);
        BlockPos floor = pos.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        return stack.useOn(new UseOnContext(level, p, hand, stack, hit));
    }

    // ふつうの床の置き場所 (評価が通る所) を、広間から count 個 探す
    private static List<BlockPos> goodSpots(ServerLevel level, FakePlayer owner, int count, BlockState sample) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -8; x <= 8 && out.size() < count; x += 2) {
            for (int z = -31; z <= -13 && out.size() < count; z += 2) {
                BlockPos pos = new BlockPos(x, FLOOR + 1, z);
                BlockPos floor = pos.below();
                if (!level.getBlockState(pos).isAir() || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                    continue;
                }
                level.setBlock(pos, sample, 2);
                CampBlocks.Refusal why = CampBlocks.evaluate(owner, level, pos, sample);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                if (why == null) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            SkyAbyss.LOGGER.error("SELFTEST camp blocks FAILED: titan_back is missing");
            return;
        }
        TitanBackData built = TitanBackData.get(back);
        if (built.needsBuild()) {
            TitanBackBuilder.build(back, built);
            TitanBackTrials.installGates(back);
        }
        FakePlayer owner = player(back, "[camp_owner]");
        FakePlayer stranger = player(back, "[camp_stranger]");
        try {
            run(server, back, owner, stranger, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("camp selftest crashed", e);
            f.add("crashed: " + e);
        } finally {
            CampBlocks.returnAll(server, owner.getUUID(), owner, null);
            CampBlocks.returnAll(server, stranger.getUUID(), stranger, null);
            TitanMemoryData.get(server).takePending(owner.getUUID());
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST camp blocks PASSED (refused placement keeps item incl. offhand/door/slab, whitelist on floor, refused near mechanism/in arena/stacked, limit, owner break returns item+contents, stranger cannot break, leaving returns all with contents, offline owner gets them on next login)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST camp blocks FAILED: {}", m));
        }
    }

    private static int total(FakePlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void run(MinecraftServer server, ServerLevel back, FakePlayer owner, FakePlayer stranger, List<String> f) {
        List<BlockPos> spots = goodSpots(back, owner, 12, Blocks.CRAFTING_TABLE.defaultBlockState());
        if (spots.size() < 12) {
            f.add("could not find 12 free floor spots in the hub (" + spots.size() + ")");
            return;
        }
        // 1. 置けないブロックは、手持ちが減らず、ブロックも残らない
        BlockPos s0 = spots.get(0);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 5));
        place(back, owner, InteractionHand.MAIN_HAND, s0);
        if (owner.getMainHandItem().getCount() != 5 || !back.getBlockState(s0).isAir()) {
            f.add("a refused cobblestone changed the hand (count " + owner.getMainHandItem().getCount() + ", block " + back.getBlockState(s0) + ")");
        }
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 1));
        place(back, owner, InteractionHand.MAIN_HAND, s0);
        if (owner.getMainHandItem().getCount() != 1) {
            f.add("a refused single cobblestone vanished from the main hand");
        }
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 1));
        place(back, owner, InteractionHand.OFF_HAND, s0);
        if (owner.getOffhandItem().getCount() != 1 || !back.getBlockState(s0).isAir()) {
            f.add("a refused cobblestone in the offhand vanished or was placed");
        }
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_DOOR, 2));
        place(back, owner, InteractionHand.OFF_HAND, s0);
        if (owner.getOffhandItem().getCount() != 2 || !back.getBlockState(s0).isAir() || !back.getBlockState(s0.above()).isAir()) {
            f.add("a refused two-block door vanished or left a half (count " + owner.getOffhandItem().getCount() + ")");
        }
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE_SLAB, 3));
        place(back, owner, InteractionHand.OFF_HAND, s0);
        if (owner.getOffhandItem().getCount() != 3 || !back.getBlockState(s0).isAir()) {
            f.add("a refused slab vanished or was placed");
        }
        owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        // 2. 野営の道具は、ふつうの床に置ける
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRAFTING_TABLE, 1));
        place(back, owner, InteractionHand.MAIN_HAND, s0);
        if (!back.getBlockState(s0).is(Blocks.CRAFTING_TABLE) || CampBlocks.at(server, back.dimension(), s0) == null
                || !owner.getMainHandItem().isEmpty()) {
            f.add("a crafting table could not be placed on a normal floor (block " + back.getBlockState(s0) + ")");
        }
        // 道具の上には積めない (足場にしない)
        BlockPos stacked = s0.above();
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST, 1));
        BlockHitResult onTable = new BlockHitResult(Vec3.atCenterOf(s0).add(0, 0.5, 0), Direction.UP, s0, false);
        owner.setPos(s0.getX() + 0.5, s0.getY(), s0.getZ() + 2.5);
        owner.getMainHandItem().useOn(new UseOnContext(back, owner, InteractionHand.MAIN_HAND, owner.getMainHandItem(), onTable));
        if (!back.getBlockState(stacked).isAir() || owner.getMainHandItem().getCount() != 1) {
            f.add("a chest was stacked on a camp block or the item vanished");
        }
        back.setBlock(stacked, Blocks.AIR.defaultBlockState(), 3);

        // 3. 仕掛け (封印の扉) のそばには置けない。手持ちは減らない
        BlockPos nearGate = new BlockPos(0, FLOOR + 1, TitanBackLayout.GATE.get(0).getZ() + 1);
        if (!back.getBlockState(nearGate).isAir()) {
            f.add("test spot near the treasury gate is not free: " + back.getBlockState(nearGate));
        } else {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FURNACE, 1));
            place(back, owner, InteractionHand.MAIN_HAND, nearGate);
            if (!back.getBlockState(nearGate).isAir() || owner.getMainHandItem().getCount() != 1) {
                f.add("a furnace was placed within 2 blocks of the treasury seal or vanished (" + back.getBlockState(nearGate) + ")");
            }
        }
        // 戦いの部屋の中 (心臓の間) では置けない
        BlockPos arena = TitanBackLayout.HEART.aabb().getCenter() == null ? null : BlockPos.containing(TitanBackLayout.HEART.aabb().getCenter().x, FLOOR + 1, TitanBackLayout.HEART.aabb().getCenter().z);
        if (arena != null && CampBlocks.evaluate(owner, back, arena, Blocks.CRAFTING_TABLE.defaultBlockState()) != CampBlocks.Refusal.ARENA) {
            f.add("the heart arena did not refuse a camp block");
        }

        // 4. 数の上限 (一人 8 個)。最初の 1 個 + 7 個で 8、次は断られる
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH, 20));
        for (int i = 1; i < 8; i++) {
            place(back, owner, InteractionHand.MAIN_HAND, spots.get(i));
        }
        if (CampBlocks.count(server, owner.getUUID(), back.dimension()) != 8) {
            f.add("expected 8 camp blocks, found " + CampBlocks.count(server, owner.getUUID(), back.dimension()));
        }
        int torches = owner.getMainHandItem().getCount();
        place(back, owner, InteractionHand.MAIN_HAND, spots.get(8));
        if (!back.getBlockState(spots.get(8)).isAir() || owner.getMainHandItem().getCount() != torches
                || CampBlocks.count(server, owner.getUUID(), back.dimension()) != 8) {
            f.add("the ninth camp block was not refused by the limit");
        }

        // 5. 外す: 持ち主は一瞬で外せて、本体と中身が手もとへ。他人は外せない
        BlockPos chestPos = spots.get(1);
        back.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        // 7 番目の記録を使って、箱に差し替える (数は変わらない)
        if (!(back.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            f.add("no chest block entity at the test spot");
            return;
        }
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        chest.setItem(5, new ItemStack(Items.BREAD, 7));
        PlayerEvent.BreakSpeed speedOwner = new PlayerEvent.BreakSpeed(owner, back.getBlockState(chestPos), 1f, chestPos);
        NeoForge.EVENT_BUS.post(speedOwner);
        if (speedOwner.isCanceled() || speedOwner.getNewSpeed() < 1000f) {
            f.add("the owner's break speed was not made instant");
        }
        PlayerEvent.BreakSpeed speedOther = new PlayerEvent.BreakSpeed(stranger, back.getBlockState(chestPos), 1f, chestPos);
        NeoForge.EVENT_BUS.post(speedOther);
        if (!speedOther.isCanceled()) {
            f.add("a stranger's break speed was not cancelled");
        }
        BlockEvent.BreakEvent strangerBreak = new BlockEvent.BreakEvent(back, chestPos, back.getBlockState(chestPos), stranger);
        NeoForge.EVENT_BUS.post(strangerBreak);
        if (!back.getBlockState(chestPos).is(Blocks.CHEST) || CampBlocks.at(server, back.dimension(), chestPos) == null
                || total(stranger, Items.DIAMOND) != 0) {
            f.add("a stranger took down someone else's chest");
        }
        int chests = total(owner, Items.CHEST);
        BlockEvent.BreakEvent ownerBreak = new BlockEvent.BreakEvent(back, chestPos, back.getBlockState(chestPos), owner);
        NeoForge.EVENT_BUS.post(ownerBreak);
        if (!back.getBlockState(chestPos).isAir() || CampBlocks.at(server, back.dimension(), chestPos) != null
                || total(owner, Items.CHEST) != chests + 1 || total(owner, Items.DIAMOND) != 3 || total(owner, Items.BREAD) != 7) {
            f.add("the owner's break did not return the chest and its contents (chest " + total(owner, Items.CHEST) + ", diamond "
                    + total(owner, Items.DIAMOND) + ", bread " + total(owner, Items.BREAD) + ")");
        }

        // 6. 背中を出る (次元の移動・運び出し・倒れる): 残りの道具が中身ごと戻る
        BlockPos chest2 = spots.get(9);
        back.setBlock(chest2, Blocks.CHEST.defaultBlockState(), 3);
        CampBlocks.record(owner, back, chest2, back.getBlockState(chest2));
        if (back.getBlockEntity(chest2) instanceof ChestBlockEntity c2) {
            c2.setItem(2, new ItemStack(Items.GOLD_INGOT, 9));
        }
        int tableBefore = total(owner, Items.CRAFTING_TABLE);
        int placed = CampBlocks.count(server, owner.getUUID(), back.dimension());
        int returned = CampBlocks.returnAll(server, owner.getUUID(), owner, back.dimension());
        if (returned != placed || CampBlocks.count(server, owner.getUUID(), back.dimension()) != 0) {
            f.add("leaving returned " + returned + " of " + placed + " camp blocks");
        }
        if (total(owner, Items.CRAFTING_TABLE) != tableBefore + 1 || total(owner, Items.GOLD_INGOT) != 9
                || total(owner, Items.CHEST) != chests + 2) {
            f.add("leaving did not return items and chest contents (table " + total(owner, Items.CRAFTING_TABLE) + ", gold "
                    + total(owner, Items.GOLD_INGOT) + ")");
        }
        for (BlockPos p : spots.subList(0, 10)) {
            if (!back.getBlockState(p).isAir()) {
                f.add("camp block remains after leaving at " + p.toShortString() + ": " + back.getBlockState(p));
            }
        }

        // 7. オフラインの持ち主 (サーバーにいない) は、見回りで預かりへ。次のログインで受け取る
        FakePlayer away = player(back, "[camp_away]");
        BlockPos chest3 = spots.get(10);
        away.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST, 1));
        place(back, away, InteractionHand.MAIN_HAND, chest3);
        if (!back.getBlockState(chest3).is(Blocks.CHEST) || CampBlocks.at(server, back.dimension(), chest3) == null) {
            f.add("the away player's chest was not placed");
            return;
        }
        if (back.getBlockEntity(chest3) instanceof ChestBlockEntity c3) {
            c3.setItem(1, new ItemStack(Items.EMERALD, 4));
        }
        CampBlocks.sweep(server);
        if (!back.getBlockState(chest3).isAir() || CampBlocks.count(server, away.getUUID(), back.dimension()) != 0) {
            f.add("the sweep did not take back the offline owner's chest");
        }
        if (!TitanMemoryData.get(server).hasPending(away.getUUID())) {
            f.add("the offline owner's items did not go to the pending stash");
        }
        TitanLoot.deliver(away, true);
        if (total(away, Items.CHEST) != 1 || total(away, Items.EMERALD) != 4) {
            f.add("the offline owner did not get the chest and contents on next login (chest " + total(away, Items.CHEST) + ", emerald "
                    + total(away, Items.EMERALD) + ")");
        }
        // 後片付け
        for (BlockPos p : spots) {
            back.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}

package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.mob.BubblePuffer;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 西の試練 珊瑚の庭 (08 の 4.1節): 大貝から「潮の真珠」を取り出し、祭壇の潮鳴り石まで運んで据える。
// - 真珠は庭に一つ。持っている人は鈍足 I と、全身がうすく光る (イルカの好意は消す。8 章)
// - 頭まで水に入る (0.5 秒) と、潮に引かれて大貝へ戻る。地面に落ちて 15 秒、庭を出る、倒れる、どこにも見当たらない、でも戻る
// - 投げて仲間に渡せる (落とした真珠は誰でも拾える)
// - 庭には珊瑚憑き (5 体、人が一人増えるごとに +2。最大 6 人まで数える) と、水路の磯走りの群れ。満潮のあいだは台のまわりに泡吹き
// 庭は占有する部屋 (TideRooms.GARDEN)。真珠・大貝・敵・道の形は、いまの持ち主のグループ (パーティ、または一人) のもの。
// 真珠を取り上げる・戻すのは持ち主のグループの人だけ (ほかのグループの人の持ち物には触れない)。敵は持ち主のグループの印を持つ
public final class TideGarden {
    public static final String GARDEN_TAG = "skyabyss_garden_mob";
    public static final int DROPPED_RETURN = 15 * 20;
    public static final int HEAD_UNDER_TICKS = 10;

    enum Pearl {
        CLAM, OUT, PLACED
    }

    private static Pearl pearl = Pearl.CLAM;
    private static int missing;
    private static final Map<UUID, Integer> UNDER = new HashMap<>();
    private static boolean spawned;
    private static int spawnedFor;
    private static TideClock.Phase lastPhase;

    private TideGarden() {
    }

    public static BlockPos pearlBlock() {
        return new BlockPos(CLAM[0], GARDEN_FLOOR + 2, CLAM[1]);
    }

    public static boolean pearlOut() {
        return pearl == Pearl.OUT;
    }

    // 案内 (TideGuide) が読む真珠の状態
    static Pearl pearlState() {
        return pearl;
    }

    // 庭の中 (真珠を持っていてよい所)。祭壇の台と入口の段も含む
    static boolean inGarden(Entity e) {
        BlockPos p = e.blockPosition();
        return p.getX() >= GARDEN.x1() - 1 && p.getX() <= GARDEN.x2() + 1 && p.getZ() >= GARDEN.z1() - 1 && p.getZ() <= GARDEN.z2() + 1
                && p.getY() >= GARDEN_FLOOR - 2 && p.getY() <= GARDEN_WALL_TOP + 6;
    }

    static boolean holds(Player player) {
        return countPearls(player.getInventory()) > 0 || player.containerMenu.getCarried().is(TideBackSetup.TIDE_PEARL.get());
    }

    static int countPearls(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(TideBackSetup.TIDE_PEARL.get())) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    static void strip(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(TideBackSetup.TIDE_PEARL.get())) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (player.containerMenu.getCarried().is(TideBackSetup.TIDE_PEARL.get())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    // ---------------------------------------------------------------- 大貝と祭壇
    // 大貝の真珠に触れた
    public static boolean useClam(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!pos.equals(pearlBlock())) {
            return false;
        }
        UUID group = Groups.keyOf(player);
        TideBackState.Group state = TideBackState.of(level, group);
        if (state.rung(TideBackState.Trial.WEST)) {
            Notice.bar(player, ModText.hint(state.sleeping() == TideBackState.Trial.WEST
                    ? "message.skyabyss.tide.trial_sleeping" : "message.skyabyss.tide.clam_empty"));
            return true;
        }
        if (TideRooms.owner(TideRooms.GARDEN) == null) {
            // 部屋の持ち主がまだ決まっていない (入った直後): 触れた人のグループに持たせる
            TideRooms.claim(level, TideRooms.GARDEN, group);
        }
        if (!TideRooms.owns(TideRooms.GARDEN, player)) {
            Notice.bar(player, ModText.deny("message.skyabyss.room_busy"));
            return true;
        }
        if (pearl != Pearl.CLAM) {
            Notice.bar(player, ModText.hint("message.skyabyss.tide.clam_empty"));
            return true;
        }
        if (!player.getInventory().add(new ItemStack(TideBackSetup.TIDE_PEARL.get()))) {
            Notice.bar(player, ModText.deny("message.skyabyss.tide.pearl_full"));
            return true;
        }
        pearl = Pearl.OUT;
        missing = 0;
        level.setBlock(pos, Blocks.WHITE_TERRACOTTA.defaultBlockState(), 3);
        level.playSound(null, pos, TideBackSetup.PEARL_TAKE.get(), SoundSource.PLAYERS, 1.5f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
        Notice.narrate(player, "message.skyabyss.tide.pearl_taken");
        return true;
    }

    // 祭壇の潮鳴り石に、真珠を据える (祭壇の上の真珠の光は、鳴らしたグループにだけ見せる。TideBackTrials の見かけの上書き)
    static boolean placePearl(ServerLevel level, ServerPlayer player) {
        if (!holds(player) || !TideRooms.owns(TideRooms.GARDEN, player)) {
            Notice.bar(player, ModText.hint("message.skyabyss.tide.altar_hint"));
            return false;
        }
        strip(player);
        pearl = Pearl.PLACED;
        BlockPos top = STONE_WEST.above();
        level.sendParticles(ParticleTypes.END_ROD, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
        return true;
    }

    // 真珠を大貝へ戻す。取り上げるのは持ち主のグループの人の真珠だけ
    static void returnPearl(ServerLevel level, String reasonKey) {
        if (pearl != Pearl.OUT) {
            return;
        }
        UUID owner = TideRooms.owner(TideRooms.GARDEN);
        pearl = Pearl.CLAM;
        missing = 0;
        UNDER.clear();
        if (owner != null) {
            for (ServerPlayer p : Groups.online(level, owner)) {
                strip(p);
            }
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, GARDEN.aabb().inflate(30, 30, 30), e -> e.getItem().is(TideBackSetup.TIDE_PEARL.get()))) {
            e.discard();
        }
        BlockPos c = pearlBlock();
        level.setBlock(c, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), 3);
        level.playSound(null, c, TideBackSetup.PEARL_RETURN.get(), SoundSource.BLOCKS, 2f, 1f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.05);
        Component msg = ModText.deny(reasonKey);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, GARDEN.aabb().inflate(8, 10, 8))) {
            if (owner == null || Groups.isMember(p, owner)) {
                Notice.send(p, msg);
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick (持ち主がいるときだけ動く)
    static void tick(ServerLevel level, boolean peaceful) {
        UUID owner = TideRooms.owner(TideRooms.GARDEN);
        if (owner == null) {
            return;
        }
        TideBackState.Group state = TideBackState.of(level, owner);
        TideClock.Phase phase = TideClock.phase(level);
        boolean westDone = state.rung(TideBackState.Trial.WEST);
        if (pearl == Pearl.OUT) {
            tickPearl(level, owner);
        }
        if (phase != lastPhase) {
            if (!peaceful && !westDone) {
                onPhase(level, owner, phase);
            }
            lastPhase = phase;
        }
        if (level.getGameTime() % 20 != 0 || peaceful || westDone) {
            return;
        }
        // 人が庭に入ったら、試練の敵を置く (人数で数を変える)。あとから仲間が入ってきたら、その分を足す
        // (試遊 (2 人) で、先に入った一人の分しか湧かず、二人でも一人のときと同じ数だった)。
        // 数えるのは持ち主のグループの人だけ (最大 6 人 = パーティの上限)
        int people = Math.min(Parties.MAX_MEMBERS, Groups.inBox(level, owner, TideRooms.gardenBox()).size());
        if (!spawned && people > 0) {
            spawned = true;
            spawnedFor = people;
            spawnGuards(level, state, owner, people);
        } else if (spawned && people > spawnedFor) {
            spawnExtra(level, state, owner, people - spawnedFor);
            spawnedFor = people;
        }
    }

    private static void tickPearl(ServerLevel level, UUID owner) {
        boolean found = false;
        for (ServerPlayer p : Groups.online(level, owner)) {
            if (!holds(p)) {
                UNDER.remove(p.getUUID());
                continue;
            }
            found = true;
            if (!p.isAlive()) {
                continue;
            }
            if (!inGarden(p)) {
                returnPearl(level, "message.skyabyss.tide.pearl_left_garden");
                return;
            }
            // 頭まで水に入った (0.5 秒続く)
            if (p.isEyeInFluid(FluidTags.WATER)) {
                int t = UNDER.merge(p.getUUID(), 1, Integer::sum);
                if (t >= HEAD_UNDER_TICKS) {
                    returnPearl(level, "message.skyabyss.tide.pearl_pulled");
                    return;
                }
            } else {
                UNDER.remove(p.getUUID());
            }
            if (p.tickCount % 10 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, false));
                if (p.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    p.removeEffect(MobEffects.DOLPHINS_GRACE);
                }
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.3, 0.4, 0.3, 0.01);
            }
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, GARDEN.aabb().inflate(30, 30, 30), e -> e.getItem().is(TideBackSetup.TIDE_PEARL.get()))) {
            found = true;
            if (e.getAge() >= DROPPED_RETURN || !inGarden(e) || (e.isInWater() && e.isUnderWater())) {
                returnPearl(level, "message.skyabyss.tide.pearl_dropped");
                return;
            }
        }
        // どこにも見当たらない (ログアウト・箱の中など) が 1 秒続いたら戻す
        missing = found ? 0 : missing + 1;
        if (missing > 20) {
            returnPearl(level, "message.skyabyss.tide.pearl_lost");
        }
    }

    private static void onPhase(ServerLevel level, UUID owner, TideClock.Phase phase) {
        boolean people = !Groups.inBox(level, owner, TideRooms.gardenBox()).isEmpty();
        if (phase == TideClock.Phase.HIGH && people) {
            // 満潮: 台のまわりに泡吹き 2 体
            int[][] plats = {PLAT_A, PLAT_C, PLAT_B1, PLAT_B2, PLAT_D1, PLAT_D2};
            RandomSource r = level.random;
            for (int i = 0; i < 2; i++) {
                int[] c = plats[r.nextInt(plats.length)];
                Mob m = TideFlats.spawn(level, TideMobSetup.BUBBLE_PUFFER.get(), new BlockPos(c[0] + r.nextInt(7) - 3, PLATFORM_TOP + 3, c[1] + r.nextInt(7) - 3),
                        owner);
                if (m != null) {
                    m.addTag(GARDEN_TAG);
                }
            }
        }
        if (phase == TideClock.Phase.LOW) {
            for (Entity e : level.getEntitiesOfClass(BubblePuffer.class, GARDEN.aabb().inflate(4), e -> e.getTags().contains(GARDEN_TAG))) {
                level.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), e.getY(), e.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                e.discard();
            }
        }
    }

    // 珊瑚憑き (水路の曲がり角と、台のふもと) と、水路の磯走りの群れ
    private static void spawnGuards(ServerLevel level, TideBackState.Group state, UUID owner, int people) {
        List<BlockPos> spots = new ArrayList<>();
        List<Channel> open = new ArrayList<>(List.of(GARDEN_ROUTES[Math.floorMod(state.gardenRoute(), GARDEN_ROUTES.length)]));
        open.add(Channel.CLAM_A);
        for (Channel c : open) {
            // L 字の曲がり角
            spots.add(new BlockPos(c.from[0], GARDEN_FLOOR + 1, c.to[1]));
        }
        open.add(Channel.G0_C);
        for (Channel c : open) {
            List<BlockPos> cells = TideIslandBuilder.channelCells(c);
            BlockPos mid = cells.get(cells.size() / 2);
            spots.add(new BlockPos(mid.getX(), GARDEN_FLOOR + 1, mid.getZ()));
        }
        placeThralls(level, spots, 5 + 2 * (people - 1), owner);
        placePack(level, 3 + people - 1, owner);
    }

    // あとから入ってきた仲間の分 (一人につき珊瑚憑き 2、磯走り 1)
    private static void spawnExtra(ServerLevel level, TideBackState.Group state, UUID owner, int added) {
        List<BlockPos> spots = new ArrayList<>();
        for (Channel c : GARDEN_ROUTES[Math.floorMod(state.gardenRoute(), GARDEN_ROUTES.length)]) {
            List<BlockPos> cells = TideIslandBuilder.channelCells(c);
            BlockPos mid = cells.get(cells.size() / 2);
            spots.add(new BlockPos(mid.getX(), GARDEN_FLOOR + 1, mid.getZ()));
            spots.add(new BlockPos(c.from[0], GARDEN_FLOOR + 1, c.to[1]));
        }
        placeThralls(level, spots, 2 * added, owner);
        placePack(level, added, owner);
    }

    // 珊瑚憑きを置ける所 (空気か水) に、数がそろうまで置く。
    // 前は候補の先頭から数だけ試し、壁の中の候補を飛ばした分だけ少なかった (二人で 7 体のはずが 5 体)
    // 候補より数が多いとき (6 人のパーティで 15 体) は、二巡目から候補のとなりのマスにも置く
    private static void placeThralls(ServerLevel level, List<BlockPos> spots, int count, UUID owner) {
        java.util.Collections.shuffle(spots, new java.util.Random(level.random.nextLong()));
        int[][] near = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int placed = 0;
        for (int round = 0; round < near.length && placed < count; round++) {
            for (BlockPos spot : spots) {
                if (placed >= count) {
                    break;
                }
                BlockPos at = spot.offset(near[round][0], 0, near[round][1]);
                if (!level.getBlockState(at).isAir() && !level.getBlockState(at).is(Blocks.WATER)) {
                    continue;
                }
                Mob m = TideFlats.spawn(level, TideMobSetup.CORAL_THRALL.get(), at, owner);
                if (m != null) {
                    m.addTag(GARDEN_TAG);
                    placed++;
                }
            }
        }
    }

    // 水路の群れ (g0〜C の水路の奥)
    private static void placePack(ServerLevel level, int count, UUID owner) {
        List<BlockPos> g0c = TideIslandBuilder.channelCells(Channel.G0_C);
        BlockPos pack = g0c.get(g0c.size() * 2 / 3);
        for (int i = 0; i < count; i++) {
            Mob m = TideFlats.spawn(level, TideMobSetup.REEF_SCUTTLER.get(), new BlockPos(pack.getX(), GARDEN_FLOOR + 1, pack.getZ()), owner);
            if (m != null) {
                m.addTag(GARDEN_TAG);
            }
        }
    }

    // 自己テスト用: いまの庭の敵の数 (そのグループの印のもの)
    static int guards(ServerLevel level, UUID group) {
        return level.getEntitiesOfClass(Mob.class, GARDEN.aabb().inflate(8, 12, 8),
                e -> e.getTags().contains(GARDEN_TAG) && e instanceof jp.revati.skyabyss.titan.group.GroupOwned o && group.equals(o.groupId())).size();
    }

    // 自己テスト用: 人が入ったときの湧かせ方を、いま呼ぶ
    static void spawnForTest(ServerLevel level, UUID owner, int people) {
        spawned = true;
        spawnedFor = people;
        spawnGuards(level, TideBackState.of(level, owner), owner, people);
    }

    // ---------------------------------------------------------------- 倒れた・出た
    static void onDeath(ServerLevel level, ServerPlayer player) {
        if (holds(player)) {
            strip(player);
            if (TideRooms.owns(TideRooms.GARDEN, player)) {
                returnPearl(level, "message.skyabyss.tide.pearl_fallen");
            }
        }
    }

    // 真珠を持っている人が背中を出た・グループが変わった: その人の真珠を取り上げ、持ち主のグループの真珠なら大貝へ戻す
    static void dropOut(ServerLevel level, ServerPlayer player, boolean ownerGroup) {
        if (!holds(player)) {
            return;
        }
        strip(player);
        if (ownerGroup) {
            returnPearl(level, "message.skyabyss.tide.pearl_left_garden");
        }
    }

    // ---------------------------------------------------------------- 部屋を作り直す (道の形を変え、真珠を大貝へ、敵を消す)
    // 新しい持ち主のグループの種 (道の形) と進み (西の石) から。state が null なら最初の形 (建てたあと)。
    // 祭壇の石・抜け道の床・真珠の光は、ワールドではいつも「まだ鳴っていない」形 (鳴らしたグループには見かけの上書きで見せる)
    static void reset(ServerLevel level, @Nullable TideBackState.Group state) {
        TideKit kit = new TideKit(level, level.random);
        TideIslandBuilder.carveGardenRoute(kit, state == null ? 0 : state.gardenRoute(), false);
        altarFixtures(kit, false);
        for (Entity e : level.getEntitiesOfClass(Entity.class, GARDEN.aabb().inflate(8, 12, 8), e -> e.getTags().contains(GARDEN_TAG))) {
            e.discard();
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, GARDEN.aabb().inflate(30, 30, 30), e -> e.getItem().is(TideBackSetup.TIDE_PEARL.get()))) {
            e.discard();
        }
        if (state != null) {
            for (ServerPlayer p : Groups.online(level, state.id)) {
                strip(p);
            }
        }
        pearl = state != null && state.rung(TideBackState.Trial.WEST) ? Pearl.PLACED : Pearl.CLAM;
        level.setBlock(pearlBlock(), pearl == Pearl.CLAM ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState(), 3);
        spawned = false;
        spawnedFor = 0;
        missing = 0;
        UNDER.clear();
        lastPhase = null;
    }

    // 部屋を手放した (持ち主が出て 20 秒・リセット): 持ち主のグループの真珠と、庭の敵を片付け、大貝へ真珠を戻す
    static void release(ServerLevel level, UUID formerOwner) {
        for (ServerPlayer p : Groups.online(level, formerOwner)) {
            strip(p);
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, GARDEN.aabb().inflate(30, 30, 30), e -> e.getItem().is(TideBackSetup.TIDE_PEARL.get()))) {
            e.discard();
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, GARDEN.aabb().inflate(8, 12, 8), e -> e.getTags().contains(GARDEN_TAG))) {
            e.discard();
        }
        if (pearl != Pearl.CLAM && level.isLoaded(pearlBlock())) {
            level.setBlock(pearlBlock(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), 3);
        }
        clear();
    }

    // 祭壇の設え: 北の低い壁、潮鳴り石、抜け道の床、碑文 (台を建て直すたびに置き直す)
    static void altarFixtures(TideKit kit, boolean rung) {
        int ax = ALTAR[0];
        int az = ALTAR[1];
        for (int x = ax - 2; x <= ax + 2; x++) {
            for (int y = ALTAR_TOP; y <= ALTAR_TOP + 3; y++) {
                kit.set(x, y, az - 3, y == ALTAR_TOP + 3 ? TideKit.DARK : (x == ax ? TideKit.LAMP : TideKit.BRICK));
            }
        }
        kit.set(STONE_WEST.below(), Blocks.CHISELED_SANDSTONE.defaultBlockState());
        kit.set(STONE_WEST, TideBackSetup.CHIME_STONE.get().defaultBlockState().setValue(TideChimeStoneBlock.ACTIVE, rung));
        kit.set(STONE_WEST.above(), rung ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.AIR.defaultBlockState());
        kit.set(RETURN_WEST, rung ? TideKit.LAMP : TideKit.DARK);
        for (Inscription i : LORE_SIGNS) {
            if (i.key().equals("sign.skyabyss.lore_tide_west")) {
                kit.wallSign(i.pos(), i.facing(), i.key());
            }
        }
    }

    static void clear() {
        pearl = Pearl.CLAM;
        missing = 0;
        UNDER.clear();
        spawned = false;
        spawnedFor = 0;
        lastPhase = null;
    }

    static boolean clamHasPearl(ServerLevel level) {
        BlockState s = level.getBlockState(pearlBlock());
        return s.is(Blocks.PEARLESCENT_FROGLIGHT);
    }

    static AABB area() {
        return GARDEN.aabb();
    }
}

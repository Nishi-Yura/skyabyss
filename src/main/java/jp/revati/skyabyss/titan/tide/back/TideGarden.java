package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.tide.mob.BubblePuffer;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
// - 真珠は島に一つ。持っている人は鈍足 I と、全身がうすく光る (イルカの好意は消す。8 章)
// - 頭まで水に入る (0.5 秒) と、潮に引かれて大貝へ戻る。地面に落ちて 15 秒、庭を出る、倒れる、どこにも見当たらない、でも戻る
// - 投げて仲間に渡せる (落とした真珠は誰でも拾える)
// - 庭には珊瑚憑き (5 体、人が一人増えるごとに +2。最大 4 人まで数える) と、水路の磯走りの群れ。満潮のあいだは台のまわりに泡吹き
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
    private static TideClock.Phase lastPhase;

    private TideGarden() {
    }

    public static BlockPos pearlBlock() {
        return new BlockPos(CLAM[0], GARDEN_FLOOR + 2, CLAM[1]);
    }

    public static boolean pearlOut() {
        return pearl == Pearl.OUT;
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
        TideBackState state = TideBackState.get(level);
        if (state.rung(TideBackState.Trial.WEST)) {
            player.displayClientMessage(ModText.hint(state.sleeping() == TideBackState.Trial.WEST
                    ? "message.skyabyss.tide.trial_sleeping" : "message.skyabyss.tide.clam_empty"), true);
            return true;
        }
        if (pearl != Pearl.CLAM) {
            player.displayClientMessage(ModText.hint("message.skyabyss.tide.clam_empty"), true);
            return true;
        }
        if (!player.getInventory().add(new ItemStack(TideBackSetup.TIDE_PEARL.get()))) {
            player.displayClientMessage(ModText.deny("message.skyabyss.tide.pearl_full"), true);
            return true;
        }
        pearl = Pearl.OUT;
        missing = 0;
        level.setBlock(pos, Blocks.WHITE_TERRACOTTA.defaultBlockState(), 3);
        level.playSound(null, pos, TideBackSetup.PEARL_TAKE.get(), SoundSource.PLAYERS, 1.5f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
        player.sendSystemMessage(ModText.lore("message.skyabyss.tide.pearl_taken"));
        return true;
    }

    // 祭壇の潮鳴り石に、真珠を据える
    static boolean placePearl(ServerLevel level, ServerPlayer player) {
        if (!holds(player)) {
            player.displayClientMessage(ModText.hint("message.skyabyss.tide.altar_hint"), true);
            return false;
        }
        strip(player);
        pearl = Pearl.PLACED;
        BlockPos top = STONE_WEST.above();
        level.setBlock(top, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), 3);
        level.sendParticles(ParticleTypes.END_ROD, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
        return true;
    }

    // 真珠を大貝へ戻す
    static void returnPearl(ServerLevel level, String reasonKey) {
        if (pearl != Pearl.OUT) {
            return;
        }
        pearl = Pearl.CLAM;
        missing = 0;
        UNDER.clear();
        for (ServerPlayer p : level.players()) {
            strip(p);
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
            p.sendSystemMessage(msg);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    static void tick(ServerLevel level, TideBackState state, boolean peaceful) {
        TideClock.Phase phase = TideClock.phase(level);
        boolean westDone = state.rung(TideBackState.Trial.WEST);
        if (pearl == Pearl.OUT) {
            tickPearl(level, phase);
        }
        if (phase != lastPhase) {
            if (!peaceful && !westDone) {
                onPhase(level, phase);
            }
            lastPhase = phase;
        }
        if (level.getGameTime() % 20 != 0 || peaceful || westDone) {
            return;
        }
        // 人が庭に入ったら、試練の敵を置く (人数で数を変える)
        List<ServerPlayer> inside = level.getEntitiesOfClass(ServerPlayer.class, GARDEN.aabb().inflate(2, 6, 2), p -> p.isAlive() && !p.isSpectator());
        if (!spawned && !inside.isEmpty()) {
            spawned = true;
            spawnGuards(level, state, Math.min(4, inside.size()));
        }
    }

    private static void tickPearl(ServerLevel level, TideClock.Phase phase) {
        boolean found = false;
        for (ServerPlayer p : level.players()) {
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

    private static void onPhase(ServerLevel level, TideClock.Phase phase) {
        boolean people = !level.getEntitiesOfClass(ServerPlayer.class, GARDEN.aabb().inflate(2, 6, 2), p -> p.isAlive() && !p.isSpectator()).isEmpty();
        if (phase == TideClock.Phase.HIGH && people) {
            // 満潮: 台のまわりに泡吹き 2 体
            int[][] plats = {PLAT_A, PLAT_C, PLAT_B1, PLAT_B2, PLAT_D1, PLAT_D2};
            RandomSource r = level.random;
            for (int i = 0; i < 2; i++) {
                int[] c = plats[r.nextInt(plats.length)];
                Mob m = TideFlats.spawn(level, TideMobSetup.BUBBLE_PUFFER.get(), new BlockPos(c[0] + r.nextInt(7) - 3, PLATFORM_TOP + 3, c[1] + r.nextInt(7) - 3));
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
    private static void spawnGuards(ServerLevel level, TideBackState state, int people) {
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
        int thralls = 5 + 2 * (people - 1);
        RandomSource r = level.random;
        java.util.Collections.shuffle(spots, new java.util.Random(r.nextLong()));
        for (int i = 0; i < thralls && i < spots.size(); i++) {
            BlockPos at = spots.get(i);
            if (!level.getBlockState(at).isAir() && !level.getBlockState(at).is(Blocks.WATER)) {
                continue;
            }
            Mob m = TideFlats.spawn(level, TideMobSetup.CORAL_THRALL.get(), at);
            if (m != null) {
                m.addTag(GARDEN_TAG);
            }
        }
        // 水路の群れ (g0〜C の水路の奥)
        List<BlockPos> g0c = TideIslandBuilder.channelCells(Channel.G0_C);
        BlockPos pack = g0c.get(g0c.size() * 2 / 3);
        for (int i = 0; i < 3 + people - 1; i++) {
            Mob m = TideFlats.spawn(level, TideMobSetup.REEF_SCUTTLER.get(), new BlockPos(pack.getX(), GARDEN_FLOOR + 1, pack.getZ()));
            if (m != null) {
                m.addTag(GARDEN_TAG);
            }
        }
    }

    // ---------------------------------------------------------------- 倒れた・出た
    static void onDeath(ServerLevel level, ServerPlayer player) {
        if (holds(player)) {
            strip(player);
            returnPearl(level, "message.skyabyss.tide.pearl_fallen");
        }
    }

    // ---------------------------------------------------------------- リセット (道の形を変え、真珠を大貝へ、敵を消す)
    static void reset(ServerLevel level, TideBackState state) {
        TideKit kit = new TideKit(level, level.random);
        TideIslandBuilder.carveGardenRoute(kit, state.gardenRoute(), false);
        altarFixtures(kit, state.rung(TideBackState.Trial.WEST));
        for (Entity e : level.getEntitiesOfClass(Entity.class, GARDEN.aabb().inflate(8, 12, 8), e -> e.getTags().contains(GARDEN_TAG))) {
            e.discard();
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, GARDEN.aabb().inflate(30, 30, 30), e -> e.getItem().is(TideBackSetup.TIDE_PEARL.get()))) {
            e.discard();
        }
        for (ServerPlayer p : level.players()) {
            strip(p);
        }
        pearl = state.rung(TideBackState.Trial.WEST) ? Pearl.PLACED : Pearl.CLAM;
        level.setBlock(pearlBlock(), pearl == Pearl.CLAM ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.WHITE_TERRACOTTA.defaultBlockState(), 3);
        spawned = false;
        missing = 0;
        UNDER.clear();
        lastPhase = null;
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

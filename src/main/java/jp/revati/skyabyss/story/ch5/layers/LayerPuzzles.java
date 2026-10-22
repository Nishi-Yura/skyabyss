package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 層ごとの深淵術の仕掛けと拾う物 (docs/design/36 の 8 章。置き場は tools/abyss/layer_<層>.py)。その層で覚えた術を、その層ですぐ使う:
//   骨 (光の術): 灯皿の間 — 三つの灯皿 (brazier_bone_0..2) に光の術の灯りが同時に届くと、奥の骨の戸 (door_bone) が開く。
//                骨の広間の横の割れた壁 (hid_bone) は、光の術の灯りが当たると崩れ、隠し部屋 (hidden_bone) の箱に探掘隊の灯り
//   塩 (重さの術): 脆い床 (brittle_salt) — 重さの術なしで乗ると割れて浅い穴へ (8 秒で戻る)。乾いた井戸 (well_salt) — 術の中で底に立つと上の口まで浮く
//   雷 (鼓動の術): 三つの戸 (door_storm_0..2) — 奥で鼓動が鳴るのは一つだけ (DOOR_TRUE)。鼓動の術を戸の間で使うと、本物の戸に心臓の粒が見える。
//                違う戸の奥は待ち伏せと、前の私の影。雷の回廊 (bolt_storm_a → b) — 三つの区間に拍子で雷 (赤い予告)。術を使った人には拍の前に心臓の音
//   灰 (三つとも): 灰の迷路 (maze_ash) — 光の術の灯りの中でだけ、出口への燠の跡が見える。三つの術の封 (seal_ash) — 封の前で光・重さ・鼓動を 20 秒の内に
// 開いた戸はワールドに残す (LayerData.open)。建て直しで戻った戸は見回りでまた開ける
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LayerPuzzles {
    public static final int DOOR_TRUE = 2;
    static final int BRAZIER_LIGHT = 6;      // 灯皿に届いたとみなす灯りの強さ (lampAt)
    static final int HIDDEN_LIGHT = 4;
    static final int BRITTLE_DELAY = 8;      // 乗ってから割れるまで (tick)
    static final int BRITTLE_BACK = 160;     // 割れてから戻るまで
    static final double RELIEF_HOLD = 0.3;   // 割れない・浮く重さの緩み
    static final int HALL_BEAT = 60;
    static final int HALL_WARN = 30;
    static final float HALL_HIT = 7f;
    static final long SEAL_WINDOW = 400;     // 三つの術の封: 三つがこの間に
    static final long BEAT_SHOW = 160;       // 鼓動の術で本物の戸が見える長さ

    private static final Map<BlockPos, Long> CRACKED = new HashMap<>();
    private static final Map<BlockPos, Long> STEPPED = new HashMap<>();
    private static final Map<UUID, Long> HEARD = new HashMap<>();          // 鼓動の術を使った時刻 (雷の回廊の拍)
    private static final Map<UUID, Long> SAW_DOOR = new HashMap<>();       // 本物の戸が見える期限
    private static final long[] SEAL = new long[3];                         // 光・重さ・鼓動を封の前で使った時刻
    private static final Set<String> HINTED = new HashSet<>();             // 人と仕掛けごとの一言 (起動のあいだ)
    @Nullable
    private static List<BlockPos> mazeTrail;
    private static boolean listening;

    private LayerPuzzles() {
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty() || !AbyssBuild.built(server)) {
            return;
        }
        if (!listening) {
            listening = true;
            AbyssArts.ON_CAST.add(LayerPuzzles::onCast);
        }
        int t = server.getTickCount();
        long now = level.getGameTime();
        if (t % 2 == 0) {
            brittle(level, now);
            well(level);
        }
        if (t % 5 == 0) {
            braziers(level);
            hidden(level);
            seal(level, now);
        }
        if (t % 10 == 0) {
            maze(level);
            doorsGlow(level, now);
        }
        hall(level, now);
        if (t % 20 == 3) {
            hints(level);
        }
        if (t % 200 == 17) {
            LayerData d = LayerData.get(server);
            for (String k : List.of("door_bone", "hid_bone", "door_storm_0", "door_storm_1", "door_storm_2", "seal_ash")) {
                if (d.isOpen(k)) {
                    AbyssMarks.clearBox(level, k, null);
                }
            }
        }
    }

    static void onCast(ServerPlayer p, AbyssArt a) {
        if (!AbyssWorld.in(p)) {
            return;
        }
        long now = p.level().getGameTime();
        if (a == AbyssArt.HEARTBEAT) {
            HEARD.put(p.getUUID(), now);
            BlockPos room = AbyssBuild.mark("puzzle_storm");
            if (room != null && room.closerThan(p.blockPosition(), 16)) {
                SAW_DOOR.put(p.getUUID(), now + BEAT_SHOW);
                Notice.narrate(p, "message.skyabyss.abyss_v2.doors_heard");
            }
        }
        BlockPos seal = AbyssBuild.mark("seal_ash");
        if (seal != null && seal.closerThan(p.blockPosition(), 10)) {
            if (a == AbyssArt.HEARTBEAT) {
                SEAL[2] = now;
            }
            if (a == AbyssArt.LIGHT) {
                SEAL[0] = now;
            }
            if (a == AbyssArt.WEIGHT) {
                SEAL[1] = now;
            }
        }
    }

    // ---------------------------------------------------------------- 骨: 灯皿と隠し壁
    static void braziers(ServerLevel level) {
        LayerData d = LayerData.get(level.getServer());
        int lit = 0;
        for (int k = 0; k < 3; k++) {
            BlockPos b = AbyssBuild.mark("brazier_bone_" + k);
            if (b == null || !level.isLoaded(b)) {
                return;
            }
            boolean on = d.isOpen("door_bone") || AbyssLight.lampAt(level, b) >= BRAZIER_LIGHT;
            BlockState st = level.getBlockState(b);
            if (st.hasProperty(CampfireBlock.LIT) && st.getValue(CampfireBlock.LIT) != on) {
                level.setBlock(b, st.setValue(CampfireBlock.LIT, on), Block.UPDATE_ALL);
                if (on) {
                    level.playSound(null, b, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 0.7f);
                }
            }
            if (on) {
                lit++;
            }
        }
        if (lit == 3 && d.setOpen("door_bone")) {
            AbyssMarks.clearBox(level, "door_bone", null);
            BlockPos at = AbyssBuild.mark("puzzle_bone");
            level.playSound(null, at, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 2f, 0.5f);
            near(level, at, 24, "message.skyabyss.abyss_v2.door_bone_open");
        }
    }

    static void hidden(ServerLevel level) {
        LayerData d = LayerData.get(level.getServer());
        if (d.isOpen("hid_bone")) {
            return;
        }
        BlockPos a = AbyssBuild.mark("hid_bone_a");
        BlockPos b = AbyssBuild.mark("hid_bone_b");
        if (a == null || b == null || !level.isLoaded(a)) {
            return;
        }
        BlockPos c = new BlockPos((a.getX() + b.getX()) / 2, a.getY() + 1, (a.getZ() + b.getZ()) / 2);
        if (AbyssLight.lampAt(level, c) >= HIDDEN_LIGHT) {
            d.setOpen("hid_bone");
            AbyssMarks.clearBox(level, "hid_bone", null);
            level.playSound(null, c, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
            level.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 30, 1, 1, 1, 0.02);
            near(level, c, 16, "message.skyabyss.abyss_v2.hidden_open");
        }
    }

    // ---------------------------------------------------------------- 塩: 脆い床と井戸
    static void brittle(ServerLevel level, long now) {
        // 戻す
        CRACKED.entrySet().removeIf(e -> {
            if (e.getValue() <= now) {
                if (level.isLoaded(e.getKey()) && level.getBlockState(e.getKey()).isAir()) {
                    level.setBlock(e.getKey(), Blocks.WHITE_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
                }
                return true;
            }
            return false;
        });
        for (ServerPlayer p : List.copyOf(level.players())) {
            BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
            if (!p.onGround() || !Layers.inBox("brittle_salt", under) || !level.getBlockState(under).is(Blocks.WHITE_CONCRETE)) {
                continue;
            }
            if (AbyssArts.weightRelief(p) >= RELIEF_HOLD) {
                if (now % 10 == 0) {
                    level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.3, 0, 0.3, 0);
                }
                continue;
            }
            STEPPED.putIfAbsent(under, now);
        }
        STEPPED.entrySet().removeIf(e -> {
            if (now - e.getValue() < BRITTLE_DELAY) {
                if (now - e.getValue() == 2) {
                    level.playSound(null, e.getKey(), SoundEvents.CALCITE_HIT, SoundSource.BLOCKS, 1f, 0.6f);
                }
                return false;
            }
            for (BlockPos q : BlockPos.betweenClosed(e.getKey().offset(-1, 0, -1), e.getKey().offset(1, 0, 1))) {
                if (Layers.inBox("brittle_salt", q) && level.getBlockState(q).is(Blocks.WHITE_CONCRETE)) {
                    level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    CRACKED.put(q.immutable(), now + BRITTLE_BACK);
                    level.sendParticles(ParticleTypes.CLOUD, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.01);
                }
            }
            level.playSound(null, e.getKey(), SoundEvents.CALCITE_BREAK, SoundSource.BLOCKS, 1.4f, 0.5f);
            return true;
        });
    }

    static void well(ServerLevel level) {
        BlockPos bot = AbyssBuild.mark("well_salt");
        BlockPos top = AbyssBuild.mark("well_salt_top");
        if (bot == null || top == null) {
            return;
        }
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (Math.abs(p.getX() - (bot.getX() + 0.5)) > 2.6 || Math.abs(p.getZ() - (bot.getZ() + 0.5)) > 2.6 || p.getY() < bot.getY() - 0.5
                    || p.getY() > top.getY() + 1.5) {
                continue;
            }
            if (AbyssArts.weightRelief(p) < RELIEF_HOLD) {
                continue;
            }
            Vec3 v = p.getDeltaMovement();
            double up = p.getY() < top.getY() - 0.2 ? Math.max(v.y, 0.38) : v.y;
            // 上の口では、縁 (well_salt_top) の方へそっと押す
            double push = p.getY() >= top.getY() - 1.2 ? 0.12 : 0;
            double dx = Math.signum(top.getX() + 0.5 - p.getX()) * push, dz = Math.signum(top.getZ() + 0.5 - p.getZ()) * push;
            p.setDeltaMovement(v.x + dx, up, v.z + dz);
            p.hurtMarked = true;
            p.fallDistance = 0;
            if (level.getGameTime() % 6 == 0) {
                level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY(), p.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }

    // ---------------------------------------------------------------- 雷: 鼓動の戸と雷の回廊
    static void doorsGlow(ServerLevel level, long now) {
        BlockPos door = AbyssBuild.mark("door_storm_" + DOOR_TRUE);
        if (door == null) {
            return;
        }
        for (ServerPlayer p : List.copyOf(level.players())) {
            Long until = SAW_DOOR.get(p.getUUID());
            if (until != null && until > now) {
                level.sendParticles(p, ParticleTypes.HEART, true, door.getX() + 0.5, door.getY() + 0.4, door.getZ() + 0.5, 1, 0.4, 0.5, 0.4, 0);
                if (now % 20 == 0) {
                    p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.2f, 1f);
                }
            }
        }
    }

    // 鼓動の戸を開ける (右クリック)。違う戸なら奥から待ち伏せ (0) か前の私の影 (1)
    public static boolean openDoor(ServerPlayer p, int k) {
        ServerLevel level = p.serverLevel();
        LayerData d = LayerData.get(p.server);
        String key = "door_storm_" + k;
        if (!d.setOpen(key)) {
            return false;
        }
        AbyssMarks.clearBox(level, key, null);
        BlockPos at = AbyssBuild.mark(key);
        level.playSound(null, at, SoundEvents.COPPER_DOOR_OPEN, SoundSource.BLOCKS, 1.4f, 0.7f);
        if (k == DOOR_TRUE) {
            Notice.narrate(p, "message.skyabyss.abyss_v2.door_true");
            return true;
        }
        BlockPos closet = AbyssBuild.mark("closet_storm_" + k);
        if (closet == null) {
            return true;
        }
        if (k == 0) {
            Notice.alert(p, "message.skyabyss.abyss_v2.door_wrong");
            for (int i = 0; i < 4; i++) {
                AbyssMobSpawner.spawnAt(level, "charged_moth", closet.getX() + 0.5, closet.getY() + 1, closet.getZ() + 0.5, 1);
            }
        } else {
            LayerFights.shadow(p, closet);
        }
        // 違う戸は 30 秒で閉じる (もう一度開けると、また出る)
        LayerFights.later(level.getServer(), 600, () -> {
            d.close(key);
            BlockPos a = AbyssBuild.mark(key + "_a");
            BlockPos b = AbyssBuild.mark(key + "_b");
            if (a != null && b != null && level.isLoaded(a)) {
                for (BlockPos q : BlockPos.betweenClosed(a, b)) {
                    if (level.getBlockState(q).isAir() && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(q)).isEmpty()) {
                        level.setBlock(q, (q.getY() == a.getY() + 1 ? Blocks.CHISELED_COPPER : Blocks.WAXED_WEATHERED_CUT_COPPER).defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        });
        return true;
    }

    static void hall(ServerLevel level, long now) {
        BlockPos a = AbyssBuild.mark("bolt_storm_a");
        BlockPos b = AbyssBuild.mark("bolt_storm_b");
        if (a == null || b == null) {
            return;
        }
        long phase = now % HALL_BEAT;
        boolean anyone = false;
        Vec3 va = a.getBottomCenter(), vb = b.getBottomCenter();
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (distToSegment(p.position(), va, vb) < 6 && Math.abs(p.getY() - a.getY()) < 4) {
                anyone = true;
                // 鼓動の術を使った人 (40 秒) には、拍の前に心臓の音 (20 と 10 tick 前)
                Long heard = HEARD.get(p.getUUID());
                if (heard != null && now - heard < 800 && (phase == HALL_BEAT - HALL_WARN - 20 || phase == HALL_BEAT - HALL_WARN - 10)) {
                    p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.4f, 1.2f);
                }
            }
        }
        if (!anyone || phase != HALL_BEAT - HALL_WARN) {
            return;
        }
        int[] pattern = {0, 2, 1, 2, 0, 1};
        int s = pattern[(int) ((now / HALL_BEAT) % pattern.length)];
        Vec3 c = va.add(vb.subtract(va).scale(s / 2.0));
        double r = va.distanceTo(vb) / 6.0 + 0.5;
        Telegraph.in(level, TelegraphShape.circle(c.x, c.z, r)).floor(c.y).warn(HALL_WARN).danger().owner(null)
                .onImpact(lvl -> {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(lvl);
                    if (bolt != null) {
                        bolt.moveTo(c.x, c.y, c.z);
                        bolt.setVisualOnly(true);
                        lvl.addFreshEntity(bolt);
                    }
                })
                .onHit((lvl, p) -> p.hurt(lvl.damageSources().lightningBolt(), HALL_HIT))
                .start();
    }

    static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr())));
        Vec3 q = a.add(ab.scale(t));
        return Math.hypot(p.x - q.x, p.z - q.z);
    }

    // ---------------------------------------------------------------- 灰: 迷路の跡と三つの術の封
    static void maze(ServerLevel level) {
        BlockPos in = AbyssBuild.mark("maze_ash_in");
        if (in == null || !level.isLoaded(in)) {
            return;
        }
        List<BlockPos> trail = trail(level);
        if (trail.isEmpty()) {
            return;
        }
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (!Layers.inBox("maze_ash", p.blockPosition()) && !p.blockPosition().closerThan(in, 4)) {
                continue;
            }
            for (BlockPos q : trail) {
                if (q.closerThan(p.blockPosition(), 14) && AbyssLight.lampAt(level, q) >= 4) {
                    level.sendParticles(p, ParticleTypes.SMALL_FLAME, true, q.getX() + 0.5, q.getY() + 0.1, q.getZ() + 0.5, 1, 0.15, 0, 0.15, 0);
                }
            }
        }
    }

    // 迷路の入口から出口までの道 (置いたブロックから BFS。一度だけ求める)
    static List<BlockPos> trail(ServerLevel level) {
        if (mazeTrail != null) {
            return mazeTrail;
        }
        BlockPos in = AbyssBuild.mark("maze_ash_in");
        BlockPos out = AbyssBuild.mark("maze_ash_out");
        if (in == null || out == null) {
            return List.of();
        }
        Map<BlockPos, BlockPos> from = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(in);
        from.put(in, in);
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            if (c.equals(out)) {
                break;
            }
            for (BlockPos n : new BlockPos[] {c.north(), c.south(), c.east(), c.west()}) {
                if (!from.containsKey(n) && (Layers.inBox("maze_ash", n) || n.equals(out)) && level.getBlockState(n.above()).isAir()
                        && !level.getBlockState(n.above(2)).isSolid()) {
                    from.put(n, c);
                    q.add(n);
                }
            }
        }
        List<BlockPos> path = new ArrayList<>();
        if (!from.containsKey(out)) {
            mazeTrail = path;
            return path;
        }
        for (BlockPos c = out; !c.equals(in); c = from.get(c)) {
            path.add(c);
        }
        path.add(in);
        mazeTrail = path;
        return path;
    }

    public static int trailLength(ServerLevel level) {
        mazeTrail = null;
        return trail(level).size();
    }

    static void seal(ServerLevel level, long now) {
        BlockPos seal = AbyssBuild.mark("seal_ash");
        LayerData d = LayerData.get(level.getServer());
        if (seal == null || d.isOpen("seal_ash") || !level.isLoaded(seal)) {
            return;
        }
        // 重さ: 封の前で術の中にいる人。光: 封の前に灯り
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.blockPosition().closerThan(seal, 8) && AbyssArts.weightRelief(p) > 0) {
                SEAL[1] = now;
            }
        }
        if (AbyssLight.lampAt(level, seal) >= 4) {
            SEAL[0] = now;
        }
        BlockPos room = AbyssBuild.mark("puzzle_ash");
        int ok = 0;
        for (int k = 0; k < 3; k++) {
            boolean on = SEAL[k] > 0 && now - SEAL[k] <= SEAL_WINDOW;
            if (on) {
                ok++;
                if (room != null) {
                    // 三つの台 (光・重さ・鼓動) のうち、満ちたものの上に光
                    BlockPos ped = sealPedestal(k);
                    if (ped != null) {
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ped.getX() + 0.5, ped.getY() + 1.2, ped.getZ() + 0.5, 3, 0.15, 0.2, 0.15, 0.01);
                    }
                }
            }
        }
        if (ok == 3) {
            d.setOpen("seal_ash");
            AbyssMarks.clearBox(level, "seal_ash", null);
            level.playSound(null, seal, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 2f, 0.6f);
            near(level, seal, 24, "message.skyabyss.abyss_v2.seal_open");
        }
    }

    // 三つの台の場所 (光・重さ・鼓動。layer_ash.py の seal_ped_<k>)
    @Nullable
    static BlockPos sealPedestal(int k) {
        return AbyssBuild.mark("seal_ped_" + k);
    }

    public static void resetSeal() {
        java.util.Arrays.fill(SEAL, 0);
    }

    // ---------------------------------------------------------------- 一言 (仕掛けの部屋に初めて入ったとき)
    static void hints(ServerLevel level) {
        String[][] spots = {{"puzzle_bone", "bone", "8"}, {"puzzle_salt", "salt", "9"}, {"well_salt", "well", "4"}, {"puzzle_storm", "storm", "8"},
                {"bolt_storm_a", "hall", "5"}, {"maze_ash_in", "maze", "4"}, {"puzzle_ash", "ash", "8"}};
        for (ServerPlayer p : List.copyOf(level.players())) {
            for (String[] s : spots) {
                BlockPos m = AbyssBuild.mark(s[0]);
                if (m != null && m.closerThan(p.blockPosition(), Integer.parseInt(s[2])) && HINTED.add(p.getUUID() + s[1])) {
                    Notice.narrate(p, "message.skyabyss.abyss_v2.hint." + s[1]);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 拾う物 (箱を右クリック)
    // 印 → (記録、合図、品)。品は小さなもの (探掘隊の灯り・地図の切れ端)
    static final String[][] FINDS = {
            {"hidden_bone", "took:hidden_bone", "abyss_hidden_bone"},
            {"frag_salt_1", "took:frag_salt_1", "abyss_frag_salt_1"},
            {"frag_salt_2", "took:frag_salt_2", "abyss_frag_salt_2"},
            {"frag_salt_3", "took:frag_salt_3", "abyss_frag_salt_3"},
    };

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos at = event.getPos();
        for (String[] f : FINDS) {
            BlockPos m = AbyssBuild.mark(f[0]);
            if (m != null && at.closerThan(m, 1.8) && level(p).getBlockState(at).is(Blocks.CHEST)) {
                event.setCanceled(true);
                find(p, f);
                return;
            }
        }
        for (int k = 0; k < 3; k++) {
            if (Layers.inBox("door_storm_" + k, at)) {
                event.setCanceled(true);
                openDoor(p, k);
                return;
            }
        }
    }

    private static ServerLevel level(ServerPlayer p) {
        return p.serverLevel();
    }

    public static boolean find(ServerPlayer p, String[] f) {
        LayerData d = LayerData.get(p.server);
        if (!d.set(p.getUUID(), f[1])) {
            Notice.bar(p, Component.translatable("message.skyabyss.abyss_v2.find_empty"));
            return false;
        }
        Quests.signalWithParty(p.serverLevel(), List.of(p), f[2]);
        Notice.narrate(p, "message.skyabyss.abyss_v2.find." + f[0]);
        ItemStack gift = f[0].startsWith("frag") ? new ItemStack(Items.PAPER) : new ItemStack(Items.SOUL_LANTERN);
        gift.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable("item.skyabyss.abyss_v2." + (f[0].startsWith("frag") ? "fragment" : "party_lantern")));
        Quests.give(p, gift);
        return true;
    }

    static void near(ServerLevel level, @Nullable BlockPos at, double r, String key) {
        if (at == null) {
            return;
        }
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.blockPosition().closerThan(at, r)) {
                Notice.narrate(p, key);
            }
        }
    }

    // 開発用・自己テスト: 仕掛けの記録を戻す
    public static void resetForTest(MinecraftServer server) {
        CRACKED.clear();
        STEPPED.clear();
        HEARD.clear();
        SAW_DOOR.clear();
        HINTED.clear();
        resetSeal();
        mazeTrail = null;
    }
}

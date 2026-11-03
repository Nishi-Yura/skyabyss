package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.mount.MountEntity;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 早駆けの道 (2026-11-03 の案「早駆けの道」。docs/design/38 の 8 章)。土地ごとに一本、道の上を乗り物で駆ける道筋
// (data/skyabyss/continent/races.json。tools/gen/world/gen_races.py が町・都の門の外から道に沿って作る)。
//   標     始まりの関所の右、道の外に柱 (人が BUILD_RADIUS まで来たら建てる。SiteData の種類 KIND)
//   始まる 自分の乗り物 (地を駆けるもの) に乗って標の POST_RADIUS の中で HOLD_TICKS 止まると、3・2・1 の合図のあと走り出す (合図の前に動くとやめる)
//   走る   次の関所に光の輪 (その人にだけ)。GATE_RADIUS の中を通ると次へ。降りる・倒れる・関所から LOST_RADIUS 離れる・時間切れでやめる
//   印     終わりの時間で金・銀・銅 (道の長さ ÷ 乗り物の最上の速さ × PACE)。人ごとの自己最高と印を覚える (倒れても消えない)。
//          初めて届いた印ごとに鐘銭 COINS と経験値。すべての道で金なら鐘銭 ALL_GOLD_COINS
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaceCourses {
    public static final String KIND = "race";
    static final String BEST = "skyabyss_race_best";
    static final String MEDAL = "skyabyss_race_medal";
    static final int BUILD_RADIUS = 64;
    static final double NEAR_RADIUS = 14;
    static final double POST_RADIUS = 3.5;
    static final int HOLD_TICKS = 40;
    static final int COUNT_TICKS = 60;
    static final double GATE_RADIUS = 7.5;
    static final double LOST_RADIUS = 96;
    // 乗り物の最上の段 (速さ 0.44) の地上の速さ (ブロック/tick)。金・銀・銅はその割合で走りきる時間
    static final double TOP_SPEED = 0.88;
    static final double[] PACE = {0.80, 0.62, 0.42};
    // 初めて届いた印の鐘銭 (金・銀・銅) と経験値
    static final int[] COINS = {600, 300, 150};
    static final int[] XP = {400, 200, 100};
    static final int ALL_GOLD_COINS = 2000;
    public static final int NONE = 3;

    public record Course(int index, String id, String region, int postX, int postZ, List<int[]> gates, int length) {
        // 印の時間 (tick)。medal 0 = 金・1 = 銀・2 = 銅
        public int medalTicks(int medal) {
            return (int) Math.ceil(length / (TOP_SPEED * PACE[medal]));
        }

        public int limitTicks() {
            return medalTicks(2) * 2;
        }

        public Component name() {
            return Component.translatable("race.skyabyss." + id);
        }
    }

    // 走っている人。phase 0 = 合図の前 (数える)、1 = 走る
    static final class Run {
        final Course course;
        int phase;
        long t0;
        int next = 1;
        final boolean onFoot;
        final int[] groundY;

        Run(Course c, boolean onFoot) {
            this.course = c;
            this.onFoot = onFoot;
            this.groundY = new int[c.gates().size()];
            java.util.Arrays.fill(groundY, Integer.MIN_VALUE);
        }
    }

    private static List<Course> all;
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Integer> HOLD = new HashMap<>();
    private static final Set<String> HINTED = new HashSet<>();

    private RaceCourses() {
    }

    public static synchronized List<Course> all() {
        if (all == null) {
            List<Course> out = new ArrayList<>();
            try (InputStream in = RaceCourses.class.getResourceAsStream("/data/skyabyss/continent/races.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (JsonElement e : o.getAsJsonArray("courses")) {
                        JsonObject c = e.getAsJsonObject();
                        List<int[]> gates = new ArrayList<>();
                        for (JsonElement g : c.getAsJsonArray("checkpoints")) {
                            JsonArray a = g.getAsJsonArray();
                            gates.add(new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
                        }
                        JsonArray post = c.getAsJsonArray("post");
                        out.add(new Course(out.size(), c.get("id").getAsString(), c.get("region").getAsString(), post.get(0).getAsInt(), post.get(1).getAsInt(),
                                List.copyOf(gates), c.get("length").getAsInt()));
                    }
                }
            } catch (Exception e) {
                SkyAbyss.LOGGER.warn("Race courses could not be read: {}", e.toString());
            }
            all = List.copyOf(out);
        }
        return all;
    }

    // ---------------------------------------------------------------- 人ごとの記録
    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag tag(Player p, String key) {
        CompoundTag root = persisted(p);
        if (!root.contains(key)) {
            root.put(key, new CompoundTag());
        }
        return root.getCompound(key);
    }

    // 自己最高 (tick。まだなら 0)
    public static int best(Player p, Course c) {
        return tag(p, BEST).getInt(c.id());
    }

    // 届いた一番よい印 (0 金・1 銀・2 銅・NONE まだ)
    public static int medal(Player p, Course c) {
        CompoundTag t = tag(p, MEDAL);
        return t.contains(c.id()) ? t.getInt(c.id()) : NONE;
    }

    public static String clock(int ticks) {
        int tenths = ticks / 2;
        return String.format("%d:%02d.%d", tenths / 600, tenths / 10 % 60, tenths % 10);
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!RoadClearance.live()) {
            return;
        }
        ServerLevel over = server.overworld();
        long now = over.getGameTime();
        int t = server.getTickCount();
        for (ServerPlayer p : over.players()) {
            if (p.isSpectator()) {
                continue;
            }
            if (RUNS.containsKey(p.getUUID())) {
                step(p, now);
            } else if (t % 5 == 3) {
                watchPosts(p, over, t % 20 == 3);
            }
        }
    }

    // 標のまわり: 建てる・近づいた知らせ・止まって待つ
    static void watchPosts(ServerPlayer p, ServerLevel level, boolean slow) {
        for (Course c : all()) {
            double dx = p.getX() - (c.postX() + 0.5);
            double dz = p.getZ() - (c.postZ() + 0.5);
            double d2 = dx * dx + dz * dz;
            if (d2 > (double) BUILD_RADIUS * BUILD_RADIUS) {
                continue;
            }
            if (slow) {
                SiteData data = SiteData.get(level);
                SiteData.Entry e = entry(data, c.index());
                if (e == null || !e.built) {
                    build(level, data, c, e);
                }
            }
            if (d2 > NEAR_RADIUS * NEAR_RADIUS) {
                continue;
            }
            MountEntity m = p.getVehicle() instanceof MountEntity v && p.getUUID().equals(v.owner()) ? v : null;
            if (HINTED.add(p.getUUID() + "|" + c.id())) {
                Notice.bar(p, Component.translatable(m == null ? "message.skyabyss.race.on_foot" : "message.skyabyss.race.near", c.name()));
                showTimes(p, c);
            }
            if (m == null || d2 > POST_RADIUS * POST_RADIUS) {
                HOLD.remove(p.getUUID());
                continue;
            }
            if (m.look().flies()) {
                if (HINTED.add(p.getUUID() + "|flyer")) {
                    Notice.hint(p, "message.skyabyss.race.flyer");
                }
                continue;
            }
            int held = m.getDeltaMovement().horizontalDistanceSqr() < 0.0025 ? HOLD.merge(p.getUUID(), 5, Integer::sum) : 0;
            if (held == 0) {
                HOLD.remove(p.getUUID());
            }
            if (held >= HOLD_TICKS) {
                HOLD.remove(p.getUUID());
                begin(p, c, false);
            }
            return;
        }
    }

    static void showTimes(ServerPlayer p, Course c) {
        Component times = Component.translatable("message.skyabyss.race.times", clock(c.medalTicks(0)), clock(c.medalTicks(1)), clock(c.medalTicks(2)));
        int b = best(p, c);
        Notice.hint(p, "message.skyabyss.race.course", c.name(), times, b > 0 ? Component.translatable("message.skyabyss.race.best_is", clock(b)) : Component.empty());
    }

    // 合図を数え始める (onFoot = 試験・撮影。乗り物なしで走る)
    static Run begin(ServerPlayer p, Course c, boolean onFoot) {
        Run r = new Run(c, onFoot);
        r.t0 = p.serverLevel().getGameTime();
        RUNS.put(p.getUUID(), r);
        return r;
    }

    // 走っている人の一 tick
    static void step(ServerPlayer p, long now) {
        Run r = RUNS.get(p.getUUID());
        if (r == null) {
            return;
        }
        Course c = r.course;
        boolean mounted = r.onFoot || p.getVehicle() instanceof MountEntity v && p.getUUID().equals(v.owner());
        if (!p.isAlive() || !mounted) {
            stop(p, "message.skyabyss.race.off");
            return;
        }
        long t = now - r.t0;
        if (r.phase == 0) {
            if (!r.onFoot && (Math.hypot(p.getX() - (c.postX() + 0.5), p.getZ() - (c.postZ() + 0.5)) > POST_RADIUS + 2)) {
                stop(p, "message.skyabyss.race.false_start");
                return;
            }
            if (t % 20 == 0 && t < COUNT_TICKS) {
                Notice.bar(p, Component.translatable("message.skyabyss.race.count", 3 - t / 20));
                p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1f, 1.0f);
            }
            if (t >= COUNT_TICKS) {
                r.phase = 1;
                r.t0 = now;
                Notice.bar(p, Component.translatable("message.skyabyss.race.go"));
                p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.4f);
            }
            if (t % 4 == 0) {
                ring(p, r, 1);
            }
            return;
        }
        int[] g = c.gates().get(r.next);
        double dx = p.getX() - (g[0] + 0.5);
        double dz = p.getZ() - (g[1] + 0.5);
        double d2 = dx * dx + dz * dz;
        if (d2 <= GATE_RADIUS * GATE_RADIUS) {
            r.next++;
            if (r.next >= c.gates().size()) {
                finish(p, r, (int) t);
                return;
            }
            float pitch = 0.8f + 0.9f * r.next / c.gates().size();
            p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, pitch);
        } else if (d2 > LOST_RADIUS * LOST_RADIUS) {
            stop(p, "message.skyabyss.race.lost");
            return;
        }
        if (t > c.limitTicks()) {
            stop(p, "message.skyabyss.race.timeout");
            return;
        }
        if (t % 4 == 0) {
            Notice.bar(p, Component.translatable("message.skyabyss.race.bar", c.name(), clock((int) t), r.next - 1, c.gates().size() - 1));
            ring(p, r, r.next);
        }
    }

    // 関所 i の輪 (その人にだけ)。次の次は小さく薄く
    static void ring(ServerPlayer p, Run r, int i) {
        ServerLevel level = p.serverLevel();
        for (int k = 0; k < 2 && i + k < r.course.gates().size(); k++) {
            int gi = i + k;
            int[] g = r.course.gates().get(gi);
            if (r.groundY[gi] == Integer.MIN_VALUE) {
                if (!level.hasChunkAt(new BlockPos(g[0], 0, g[1]))) {
                    continue;
                }
                r.groundY[gi] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, g[0], g[1]);
            }
            boolean last = gi == r.course.gates().size() - 1;
            ParticleOptions dot = k == 0 ? (last ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD) : ParticleTypes.WAX_OFF;
            double rad = k == 0 ? 3.4 : 2.2;
            int n = k == 0 ? 30 : 14;
            double cy = r.groundY[gi] + 2.2;
            // 輪は道の向きに立てる (前の関所からの向きに直交)
            int[] prev = r.course.gates().get(Math.max(0, gi - 1));
            double fx = g[0] - prev[0];
            double fz = g[1] - prev[1];
            double fl = Math.max(1e-3, Math.hypot(fx, fz));
            double sx = -fz / fl;
            double sz = fx / fl;
            for (int j = 0; j < n; j++) {
                double a = Math.PI * 2 * j / n;
                level.sendParticles(p, dot, true, g[0] + 0.5 + sx * Math.cos(a) * rad, cy + Math.sin(a) * rad * 0.7, g[1] + 0.5 + sz * Math.cos(a) * rad,
                        1, 0, 0, 0, 0);
            }
            if (k == 0) {
                for (int y = 0; y < 16; y += 2) {
                    level.sendParticles(p, dot, true, g[0] + 0.5, cy + 3 + y, g[1] + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // 走りきった: 時間・印・自己最高・初めての印の報い
    static void finish(ServerPlayer p, Run r, int ticks) {
        RUNS.remove(p.getUUID());
        Course c = r.course;
        int medal = NONE;
        for (int m = 2; m >= 0; m--) {
            if (ticks <= c.medalTicks(m)) {
                medal = m;
            }
        }
        int before = best(p, c);
        boolean best = before == 0 || ticks < before;
        if (best) {
            tag(p, BEST).putInt(c.id(), ticks);
        }
        Notice.place(p, c.name(), Component.translatable("message.skyabyss.race.finish", clock(ticks), Component.translatable("race.skyabyss.medal." + medal)));
        if (best && before > 0) {
            Notice.gain(p, "message.skyabyss.race.new_best", clock(ticks), clock(before));
        }
        int had = medal(p, c);
        if (medal < had) {
            tag(p, MEDAL).putInt(c.id(), medal);
            int coins = 0;
            int xp = 0;
            for (int m = medal; m < had && m <= 2; m++) {
                coins += COINS[m];
                xp += XP[m];
            }
            Coins.give(p, coins);
            jp.revati.skyabyss.rpg.Growth.addXp(p, xp, "growth.skyabyss.reason.race");
            Notice.gain(p, "message.skyabyss.race.medal_coins", Component.translatable("race.skyabyss.medal." + medal), coins);
            if (medal == 0 && allGold(p) && !tag(p, MEDAL).getBoolean("all_gold")) {
                tag(p, MEDAL).putBoolean("all_gold", true);
                Coins.give(p, ALL_GOLD_COINS);
                Notice.unlock(p, "message.skyabyss.race.all_gold", ALL_GOLD_COINS);
            }
        }
        p.playNotifySound(medal == 0 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.0f);
        SkyAbyss.LOGGER.info("Race {}: {} finished in {} ({})", c.id(), p.getGameProfile().getName(), clock(ticks), medal);
    }

    static boolean allGold(Player p) {
        for (Course c : all()) {
            if (medal(p, c) != 0) {
                return false;
            }
        }
        return !all().isEmpty();
    }

    static void stop(ServerPlayer p, String key) {
        if (RUNS.remove(p.getUUID()) != null) {
            Notice.bar(p, Component.translatable(key));
            p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    public static boolean racing(Player p) {
        return RUNS.containsKey(p.getUUID());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        RUNS.remove(id);
        HOLD.remove(id);
        HINTED.removeIf(s -> s.startsWith(id + "|"));
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        RUNS.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- 標を建てる
    @Nullable
    static SiteData.Entry entry(SiteData data, int index) {
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.index == index) {
                return e;
            }
        }
        return null;
    }

    // 石の台に木の柱と灯り、道の側に赤い旗。チャンクがまだなら建てない
    static void build(ServerLevel level, SiteData data, Course c, @Nullable SiteData.Entry e) {
        if (!level.hasChunkAt(new BlockPos(c.postX(), 0, c.postZ()))) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.postX(), c.postZ());
        BlockPos base = new BlockPos(c.postX(), y, c.postZ());
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockPos g = base.offset(a, -1, b);
                level.setBlock(g, (a == 0 && b == 0) ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : (a + b) % 2 == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                BlockPos above = g.above();
                if (!level.getBlockState(above).isAir() && level.getBlockState(above).canBeReplaced()) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.setBlock(base, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(base.above(), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(base.above(2), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(base.above(3), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(base.above(4), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), Block.UPDATE_CLIENTS);
        // 旗: 柱の上の木口から、道の側へ赤い旗を垂らす
        int[] g0 = c.gates().get(0);
        net.minecraft.core.Direction toRoad = net.minecraft.core.Direction.getNearest(g0[0] - c.postX(), 0, g0[1] - c.postZ());
        level.setBlock(base.above(3).relative(toRoad), Blocks.RED_WALL_BANNER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, toRoad), Block.UPDATE_CLIENTS);
        if (e != null) {
            data.remove(e);
        }
        SiteData.Entry n = data.add(KIND, c.index(), base, 0);
        n.built = true;
        data.setDirty();
    }

    // ---------------------------------------------------------------- 開発・撮影・試験
    // /skyabyss_race go <道> : 始まりの関所へ行き、乗り物なしで走り出す (合図から)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("skyabyss_race").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("go")
                        .then(net.minecraft.commands.Commands.argument("course", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "course");
                            for (Course c : all()) {
                                if (c.id().equals(id)) {
                                    int[] g = c.gates().get(0);
                                    int[] g1 = c.gates().get(1);
                                    float yaw = (float) (Math.toDegrees(Math.atan2(g1[1] - g[1], g1[0] - g[0])) - 90);
                                    ServerLevel level = p.serverLevel();
                                    level.getChunk(g[0] >> 4, g[1] >> 4);
                                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, g[0], g[1]);
                                    p.teleportTo(level, g[0] + 0.5, y, g[1] + 0.5, yaw, 10f);
                                    begin(p, c, true);
                                    showTimes(p, c);
                                    return 1;
                                }
                            }
                            ctx.getSource().sendFailure(Component.literal("no course " + id));
                            return 0;
                        }))));
    }

    static void forgetForTest(Player p) {
        persisted(p).remove(BEST);
        persisted(p).remove(MEDAL);
        RUNS.remove(p.getUUID());
    }

    static Run runOf(Player p) {
        return RUNS.get(p.getUUID());
    }
}

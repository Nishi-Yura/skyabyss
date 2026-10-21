package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberRooms;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMoth;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 南の試練 火守り (13 の 4.3節): 谷の奥の火床の火 (0〜100) を、100 まで燃やす。
//   火は始め 20、1 秒に −0.5 で弱まる (人が増えると 1 人につき −0.2/秒)。0 になっても失敗にしない (20 から焚き直す)。
//   熾 (敵が落とす) を持って火床に右クリックすると +8。
//   灰運び (灰被り) が谷の手前から火床へ歩いてきて、着くと灰をかけて火が −15。人が近ければ戦う。
//   火が 60 を超えると火の粉蛾の群れが降りてきて、火床に触れるたびに −2。谷の中ほどに燠虫 2 体。噴気 10 本 (HearthVents)
//   100 になると南の熾り石を鳴らせる (EmberBackTrials.markReady)。灰運びと蛾は消える
// 火の強さはメモリだけに持つ (保存しない。リセットで 20)
public final class Hearth {
    // 火床の位置 (谷の奥、取っておく場所 (z 56〜65) の手前)。床 83 の上
    public static final BlockPos HEARTH = new BlockPos(0, EmberBackLayout.FLOOR_SOUTH + 1, 46);
    public static final BlockPos CARRIER_SPAWN = new BlockPos(0, EmberBackLayout.FLOOR_SOUTH + 1, 8);
    public static final BlockPos MOTH_SPAWN = new BlockPos(0, EmberBackLayout.FLOOR_SOUTH + 22, 34);
    public static final String CARRIER_TAG = "skyabyss_hearth_carrier";

    public static final float START = 20f;
    public static final float MAX = 100f;
    public static final float FEED = 8f;
    public static final float DOUSE = 15f;
    // 噴気に焼かれた灰運びが火床へ足す火 (灰が燃えて薪になる)
    public static final float JET_FEED = 6f;
    public static final float MOTH_DOUSE = 2f;
    public static final float DECAY_PER_SECOND = 0.5f;
    public static final float DECAY_PER_PLAYER = 0.2f;
    public static final float MOTH_FROM = 60f;
    public static final int CARRIER_INTERVAL = 160;
    public static final int MOTH_INTERVAL = 600;
    public static final double CARRIER_ARRIVE = 4.0;
    public static final double MOTH_TOUCH = 1.8;
    public static final int MOTH_TOUCH_GAP = 30;
    public static final float COAL_HEAT = 1f;

    // 静かな周 (13 の 10.2節): 火の弱まりは半分、灰運びは 2 倍ゆっくり一体ずつ、蛾と燠虫は出ない
    public static final float QUIET_DECAY = 0.5f;
    public static final int QUIET_CARRIER_SLOWER = 2;
    // 周回の長い火守り (32 の 4 章、R5): 火を 100 まで二度燃やす。一度目に 100 に届くと火が燃え上がって RELIGHT まで落ち、谷の奥から灰運びが続けて来る
    public static final int FLARES_LONG = 2;
    public static final float RELIGHT = 45f;
    private static float strength = START;
    // 100 に届いた回数 (長い周の途中)
    private static int flares;
    private static boolean done;
    private static boolean spawned;
    private static long lastCarrier;
    private static long lastMoths;
    private static final Map<UUID, Long> MOTH_TOUCHED = new HashMap<>();

    private Hearth() {
    }

    static void clearMemory() {
        strength = START;
        flares = 0;
        done = false;
        spawned = false;
        MOTH_TOUCHED.clear();
        HearthVents.clearMemory();
    }

    public static float strength() {
        return strength;
    }

    public static boolean done() {
        return done;
    }

    public static void setStrength(float v) {
        strength = Math.max(0f, Math.min(MAX, v));
    }

    // 弱まる速さ (1 秒に)。n は谷にいる人数 (パーティの上限 6 人まで数える)
    public static float decayPerSecond(int players) {
        return DECAY_PER_SECOND + DECAY_PER_PLAYER * (Math.max(1, Math.min(Parties.MAX_MEMBERS, players)) - 1);
    }

    // 灰運びの間隔 (tick)。人が増えるほど多く来る
    public static int carrierInterval(int players) {
        return Math.round(CARRIER_INTERVAL / (1f + 0.5f * (Math.max(1, Math.min(Parties.MAX_MEMBERS, players)) - 1)));
    }

    public static int carrierCap(int players) {
        return 2 + Math.max(1, Math.min(Parties.MAX_MEMBERS, players));
    }

    public static int level(float s) {
        return s >= MAX ? 4 : s >= 75 ? 3 : s >= 50 ? 2 : s >= 25 ? 1 : 0;
    }

    // ---------------------------------------------------------------- 火の出入り
    // 熾を火床へ。+8。100 に届いたら済む
    public static boolean feed(ServerLevel level, ServerPlayer player, ItemStack coal) {
        if (done) {
            EmberTrials.hint(player, EmberIds.LANG_SOUTH + "burning");
            return false;
        }
        coal.shrink(1);
        add(level, FEED);
        level.playSound(null, HEARTH, EmberTrialSetup.HEARTH_FEED.get(), SoundSource.BLOCKS, 1f, 0.9f + level.random.nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.FLAME, HEARTH.getX() + 0.5, HEARTH.getY() + 0.9, HEARTH.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.04);
        Notice.bar(player, ModText.hint(EmberIds.LANG_SOUTH + "fed", Math.round(strength)));
        return true;
    }

    // 灰をかけられた (灰運びが着いた)
    public static void douse(ServerLevel level) {
        if (done) {
            return;
        }
        add(level, -DOUSE);
        level.playSound(null, HEARTH, EmberTrialSetup.HEARTH_DOUSE.get(), SoundSource.BLOCKS, 1.2f, 1f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, HEARTH.getX() + 0.5, HEARTH.getY() + 0.9, HEARTH.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.03);
        for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.SOUTH)) {
            Notice.bar(p, ModText.deny(EmberIds.LANG_SOUTH + "doused"));
        }
    }

    // 灰運びが噴気に焼かれた: 灰が燃えて、火床へ火が足される。目に見える手応え (火の粉が灰運びから火床へ飛ぶ)
    public static void jetBurned(ServerLevel level, net.minecraft.world.entity.LivingEntity carrier) {
        if (done) {
            return;
        }
        add(level, JET_FEED);
        level.playSound(null, HEARTH, EmberTrialSetup.HEARTH_FEED.get(), SoundSource.BLOCKS, 1f, 1.3f);
        Vec3 from = carrier.position().add(0, 1.0, 0);
        Vec3 to = Vec3.atCenterOf(HEARTH).add(0, 0.8, 0);
        for (int i = 0; i <= 12; i++) {
            Vec3 p = from.lerp(to, i / 12.0).add(0, Math.sin(Math.PI * i / 12.0) * 2.0, 0);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
        }
        level.sendParticles(ParticleTypes.FLAME, HEARTH.getX() + 0.5, HEARTH.getY() + 0.9, HEARTH.getZ() + 0.5, 14, 0.25, 0.2, 0.25, 0.05);
        for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.SOUTH)) {
            Notice.bar(p, ModText.hint(EmberIds.LANG_SOUTH + "jet_burned", Math.round(strength)));
        }
    }

    // 蛾が火に触れた
    public static void mothTouch(ServerLevel level) {
        add(level, -MOTH_DOUSE);
    }

    private static void add(ServerLevel level, float amount) {
        if (done) {
            return;
        }
        strength = Math.min(MAX, strength + amount);
        if (strength <= 0f) {
            // 失敗にはしない: 20 から焚き直す
            strength = START;
            for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.SOUTH)) {
                Notice.narrate(p, EmberIds.LANG_SOUTH + "dwindled");
            }
        }
        show(level);
        if (strength >= MAX) {
            if (flares + 1 < flaresNeeded(level)) {
                flare(level);
            } else {
                complete(level);
            }
        }
    }

    // 何度 100 まで燃やすか (周回の持ち主のグループなら 2)
    static int flaresNeeded(ServerLevel level) {
        java.util.UUID owner = EmberRooms.owner(EmberBackState.Trial.SOUTH.id());
        boolean extended = owner != null ? jp.revati.skyabyss.trial.RouteLength.extended(level, owner)
                : Boolean.TRUE.equals(jp.revati.skyabyss.trial.RouteLength.force);
        return extended ? FLARES_LONG : 1;
    }

    public static int flares() {
        return flares;
    }

    // 一度目の 100: 火が燃え上がって落ち着く。灰運びはすぐ次が来る
    private static void flare(ServerLevel level) {
        flares++;
        strength = RELIGHT;
        lastCarrier = 0;
        level.playSound(null, HEARTH, EmberTrialSetup.HEARTH_FEED.get(), SoundSource.BLOCKS, 1.6f, 0.8f);
        level.sendParticles(ParticleTypes.FLAME, HEARTH.getX() + 0.5, HEARTH.getY() + 1.2, HEARTH.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.08);
        for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.SOUTH)) {
            Notice.narrate(p, EmberIds.LANG_SOUTH + "flare");
        }
        show(level);
    }

    private static void complete(ServerLevel level) {
        done = true;
        EmberTrials.clearMobs(level, EmberBackLayout.SOUTH);
        EmberTrials.markReady(level, EmberBackState.Trial.SOUTH);
        level.playSound(null, HEARTH, EmberTrialSetup.HEARTH_FEED.get(), SoundSource.BLOCKS, 2f, 0.6f);
        level.sendParticles(ParticleTypes.FLAME, HEARTH.getX() + 0.5, HEARTH.getY() + 1.2, HEARTH.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.1);
        for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.SOUTH)) {
            Notice.narrate(p, EmberIds.LANG_SOUTH + "burning_bright");
        }
    }

    // 火床の炎の高さと色
    static void show(ServerLevel level) {
        BlockState cur = level.getBlockState(HEARTH);
        if (!cur.is(EmberTrialSetup.EMBER_HEARTH.get())) {
            return;
        }
        BlockState want = cur.setValue(HearthBlock.LEVEL, level(strength));
        if (want != cur) {
            level.setBlock(HEARTH, want, Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<ServerPlayer> in = EmberTrials.playersIn(level, EmberBackLayout.SOUTH);
        if (!in.isEmpty() || now % 20 == 0) {
            HearthVents.tick(level, now, !done && !in.isEmpty());
        }
        if (in.isEmpty()) {
            return;
        }
        if (now % 20 == 0) {
            coalHeat(in);
            second(level, in);
        }
        if (done) {
            return;
        }
        if (now % 10 == 0) {
            carriers(level);
            mothsTouchHearth(level, now);
        }
        if (now % 20 == 10) {
            spawnWaves(level, in, now);
        }
    }

    // 1 秒ごと: 火が弱まる。近くの人へ火の強さを出す
    static void second(ServerLevel level, List<ServerPlayer> in) {
        if (!done) {
            add(level, -decayPerSecond(in.size()) * (EmberBackTrials.quiet(level, EmberIds.SOUTH) ? QUIET_DECAY : 1f));
        }
        for (ServerPlayer p : in) {
            if (p.distanceToSqr(Vec3.atCenterOf(HEARTH)) < 14 * 14) {
                Notice.bar(p, ModText.hint(EmberIds.LANG_SOUTH + "strength", Math.round(strength)));
            }
        }
    }

    // 熾を持っていると熱が 1 秒に +1
    private static void coalHeat(List<ServerPlayer> in) {
        for (ServerPlayer p : in) {
            if (EmberTrials.count(p, EmberTrialSetup.LIVE_COAL.get()) > 0) {
                EmberHeat.add(p, COAL_HEAT, EmberHeat.Reason.LIVE_COAL);
            }
        }
    }

    private static List<AshThrall> carriersAlive(ServerLevel level) {
        return level.getEntitiesOfClass(AshThrall.class, EmberBackLayout.SOUTH.aabb(), t -> t.isAlive() && t.getTags().contains(CARRIER_TAG));
    }

    // 灰運びは火床へ歩く。着いたら灰をかける。人が近ければそちらと戦う
    static void carriers(ServerLevel level) {
        Vec3 hearth = Vec3.atBottomCenterOf(HEARTH);
        Vec3 target = Vec3.atBottomCenterOf(HEARTH.north(2));
        for (AshThrall t : carriersAlive(level)) {
            if (t.distanceToSqr(hearth) < CARRIER_ARRIVE * CARRIER_ARRIVE) {
                t.removeTag(CARRIER_TAG);
                douse(level);
                continue;
            }
            LivingEntity victim = t.getTarget();
            if (victim == null || t.distanceToSqr(victim) > 36) {
                t.setTarget(null);
                t.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            }
        }
    }

    // 蛾が火床のそばに来たら、触れた火を −2 する (一匹 1.5 秒に一度)
    static void mothsTouchHearth(ServerLevel level, long now) {
        Vec3 c = Vec3.atCenterOf(HEARTH);
        for (EmberMoth m : level.getEntitiesOfClass(EmberMoth.class, EmberBackLayout.SOUTH.aabb())) {
            if (m.distanceToSqr(c) < MOTH_TOUCH * MOTH_TOUCH) {
                Long last = MOTH_TOUCHED.get(m.getUUID());
                if (last == null || now - last >= MOTH_TOUCH_GAP) {
                    MOTH_TOUCHED.put(m.getUUID(), now);
                    mothTouch(level);
                }
            }
        }
        if (now % 200 == 0) {
            MOTH_TOUCHED.values().removeIf(t -> now - t > 400);
        }
    }

    static void spawnWaves(ServerLevel level, List<ServerPlayer> in, long now) {
        int n = Math.max(1, Math.min(Parties.MAX_MEMBERS, in.size()));
        boolean quiet = EmberBackTrials.quiet(level, EmberIds.SOUTH);
        if (!spawned) {
            spawned = true;
            // 最初の灰運びは、人が谷に入ってすぐ来る (道を歩く灰運びと、噴気の上を通る様子を最初に見せる)
            lastCarrier = now - 100000;
            lastMoths = now;
            if (!quiet) {
                EmberTrials.spawn(level, EmberMobSetup.CINDER_ROLLER.get(), -10.5, EmberBackLayout.FLOOR_SOUTH + 1, 20.5);
                EmberTrials.spawn(level, EmberMobSetup.CINDER_ROLLER.get(), 10.5, EmberBackLayout.FLOOR_SOUTH + 1, 32.5);
            }
        }
        // 静かな周: 灰運びは一体ずつ・ゆっくり (熾の元なので 0 にはしない)。蛾と燠虫は出ない
        int interval = quiet ? carrierInterval(n) * QUIET_CARRIER_SLOWER : carrierInterval(n);
        int carrierMax = quiet ? 1 : carrierCap(n);
        if (now - lastCarrier >= interval && carriersAlive(level).size() < carrierMax) {
            lastCarrier = now;
            EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), CARRIER_SPAWN.getX() - 2 + level.random.nextInt(5) + 0.5,
                    CARRIER_SPAWN.getY(), CARRIER_SPAWN.getZ() + 0.5, CARRIER_TAG);
        }
        if (!quiet && strength > MOTH_FROM && now - lastMoths >= MOTH_INTERVAL) {
            lastMoths = now;
            int cap = EmberMoth.roomCap(n);
            int want = 2 + 2 * n;
            for (int i = 0; i < want && EmberMoth.countIn(level, EmberBackLayout.SOUTH.aabb(), EmberRooms.owner(EmberIds.SOUTH)) < cap; i++) {
                EmberTrials.spawn(level, EmberMobSetup.EMBER_MOTH.get(), MOTH_SPAWN.getX() - 2 + i, MOTH_SPAWN.getY(), MOTH_SPAWN.getZ() + (i % 3));
            }
        }
    }

    // 蛾の寄る熱の源 (火床。火が 60 を超えたとき)。EmberMothSetup から一度だけ登録する
    static void registerLure() {
        EmberMoth.addLureSource((level, near, range, out) -> {
            if (level.dimension() == EmberIds.BACK && !done && strength > MOTH_FROM) {
                Vec3 c = Vec3.atCenterOf(HEARTH).add(0, 1.0, 0);
                if (c.distanceToSqr(near) < range * range) {
                    out.add(new EmberMoth.Lure(c, 55));
                }
            }
        });
    }

    // ---------------------------------------------------------------- リセット (EmberBackRooms.onReset("south", ...))
    public static void reset(ServerLevel level, boolean quiet) {
        EmberTrials.clearMobs(level, EmberBackLayout.SOUTH);
        EmberTrials.clearItems(level, EmberBackLayout.SOUTH, EmberTrialSetup.LIVE_COAL.get());
        clearMemory();
        HearthRoom.buildInner(level);
    }
}

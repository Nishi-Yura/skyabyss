package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
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
    // 火床の位置 (谷の奥、取っておく場所 (z 56〜62) の手前)。床 78 の上
    public static final BlockPos HEARTH = new BlockPos(0, EmberBackLayout.FLOOR_SOUTH + 1, 52);
    public static final BlockPos CARRIER_SPAWN = new BlockPos(0, EmberBackLayout.FLOOR_SOUTH + 1, 8);
    public static final BlockPos MOTH_SPAWN = new BlockPos(0, EmberBackLayout.FLOOR_SOUTH + 22, 30);
    public static final String CARRIER_TAG = "skyabyss_hearth_carrier";

    public static final float START = 20f;
    public static final float MAX = 100f;
    public static final float FEED = 8f;
    public static final float DOUSE = 15f;
    public static final float MOTH_DOUSE = 2f;
    public static final float DECAY_PER_SECOND = 0.5f;
    public static final float DECAY_PER_PLAYER = 0.2f;
    public static final float MOTH_FROM = 60f;
    public static final int CARRIER_INTERVAL = 160;
    public static final int MOTH_INTERVAL = 600;
    public static final double CARRIER_ARRIVE = 3.0;
    public static final double MOTH_TOUCH = 1.8;
    public static final int MOTH_TOUCH_GAP = 30;
    public static final float COAL_HEAT = 1f;

    private static float strength = START;
    private static boolean done;
    private static boolean spawned;
    private static long lastCarrier;
    private static long lastMoths;
    private static boolean relit;
    private static final Map<UUID, Long> MOTH_TOUCHED = new HashMap<>();

    private Hearth() {
    }

    static void clearMemory() {
        strength = START;
        done = false;
        spawned = false;
        relit = false;
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

    // 弱まる速さ (1 秒に)。n は谷にいる人数 (最大 4 まで数える)
    public static float decayPerSecond(int players) {
        return DECAY_PER_SECOND + DECAY_PER_PLAYER * (Math.max(1, Math.min(4, players)) - 1);
    }

    // 灰運びの間隔 (tick)。人が増えるほど多く来る
    public static int carrierInterval(int players) {
        return Math.round(CARRIER_INTERVAL / (1f + 0.5f * (Math.max(1, Math.min(4, players)) - 1)));
    }

    public static int carrierCap(int players) {
        return 2 + Math.max(1, Math.min(4, players));
    }

    public static int level(float s) {
        return s >= MAX ? 4 : s >= 75 ? 3 : s >= 50 ? 2 : s >= 25 ? 1 : 0;
    }

    // ---------------------------------------------------------------- 火の出入り
    // 熾を火床へ。+8。100 に届いたら済む
    public static boolean feed(ServerLevel level, ServerPlayer player, ItemStack coal) {
        if (done || EmberBackTrials.sleeping(level, EmberIds.SOUTH)) {
            EmberTrials.hint(player, EmberIds.LANG_SOUTH + (done ? "burning" : "sleeping"));
            return false;
        }
        coal.shrink(1);
        add(level, FEED);
        level.playSound(null, HEARTH, EmberTrialSetup.HEARTH_FEED.get(), SoundSource.BLOCKS, 1f, 0.9f + level.random.nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.FLAME, HEARTH.getX() + 0.5, HEARTH.getY() + 0.9, HEARTH.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.04);
        player.displayClientMessage(ModText.hint(EmberIds.LANG_SOUTH + "fed", Math.round(strength)), true);
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
            p.displayClientMessage(ModText.deny(EmberIds.LANG_SOUTH + "doused"), true);
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
                p.sendSystemMessage(ModText.lore(EmberIds.LANG_SOUTH + "dwindled"));
            }
        }
        show(level);
        if (strength >= MAX) {
            complete(level);
        }
    }

    private static void complete(ServerLevel level) {
        done = true;
        EmberTrials.clearMobs(level, EmberBackLayout.SOUTH);
        EmberBackTrials.markReady(level, EmberBackState.Trial.SOUTH);
        level.playSound(null, HEARTH, EmberTrialSetup.HEARTH_FEED.get(), SoundSource.BLOCKS, 2f, 0.6f);
        level.sendParticles(ParticleTypes.FLAME, HEARTH.getX() + 0.5, HEARTH.getY() + 1.2, HEARTH.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.1);
        for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.SOUTH)) {
            p.sendSystemMessage(ModText.lore(EmberIds.LANG_SOUTH + "burning_bright"));
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
        boolean sleeping = EmberBackTrials.sleeping(level, EmberIds.SOUTH);
        if (!in.isEmpty() || now % 20 == 0) {
            HearthVents.tick(level, now, !done && !sleeping && !in.isEmpty());
        }
        if (in.isEmpty() || sleeping) {
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
    private static void second(ServerLevel level, List<ServerPlayer> in) {
        if (!done) {
            add(level, -decayPerSecond(in.size()));
        }
        for (ServerPlayer p : in) {
            if (p.distanceToSqr(Vec3.atCenterOf(HEARTH)) < 14 * 14) {
                p.displayClientMessage(ModText.hint(EmberIds.LANG_SOUTH + "strength", Math.round(strength)), true);
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
    private static void carriers(ServerLevel level) {
        Vec3 target = Vec3.atBottomCenterOf(HEARTH);
        for (AshThrall t : carriersAlive(level)) {
            if (t.distanceToSqr(target) < CARRIER_ARRIVE * CARRIER_ARRIVE) {
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
    private static void mothsTouchHearth(ServerLevel level, long now) {
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

    private static void spawnWaves(ServerLevel level, List<ServerPlayer> in, long now) {
        int n = Math.max(1, Math.min(4, in.size()));
        if (!spawned) {
            spawned = true;
            lastCarrier = now;
            lastMoths = now;
            EmberTrials.spawn(level, EmberMobSetup.CINDER_ROLLER.get(), -4.5, EmberBackLayout.FLOOR_SOUTH + 1, 28.5);
            EmberTrials.spawn(level, EmberMobSetup.CINDER_ROLLER.get(), 4.5, EmberBackLayout.FLOOR_SOUTH + 1, 32.5);
        }
        if (now - lastCarrier >= carrierInterval(n) && carriersAlive(level).size() < carrierCap(n)) {
            lastCarrier = now;
            EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), CARRIER_SPAWN.getX() - 3 + level.random.nextInt(7) + 0.5,
                    CARRIER_SPAWN.getY(), CARRIER_SPAWN.getZ() + 0.5, CARRIER_TAG);
        }
        if (strength > MOTH_FROM && now - lastMoths >= MOTH_INTERVAL) {
            lastMoths = now;
            int cap = EmberMoth.roomCap(n);
            int want = 2 + 2 * n;
            for (int i = 0; i < want && EmberMoth.countIn(level, EmberBackLayout.SOUTH.aabb()) < cap; i++) {
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
    public static void reset(ServerLevel level, boolean sleeping) {
        EmberTrials.clearMobs(level, EmberBackLayout.SOUTH);
        EmberTrials.clearItems(level, EmberBackLayout.SOUTH, EmberTrialSetup.LIVE_COAL.get());
        EmberTrials.stripAll(level, EmberTrialSetup.LIVE_COAL.get());
        clearMemory();
        HearthRoom.buildInner(level);
    }
}

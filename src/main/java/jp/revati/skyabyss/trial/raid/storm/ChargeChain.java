package jp.revati.skyabyss.trial.raid.storm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 闘 SR3「帯電の連鎖」: 嵐の敵は、湧いてから時間とともに帯電する (粒が増え、満ちると体が光る)。
//  - 光る (帯電が満ちた) 敵を倒すと、近くの敵 (最大 3 体) へ雷が跳び、傷を与える。跳んだ先が光る敵なら、その敵が倒れたときにまた跳ぶ (連鎖)
//  - 光る敵を放っておくと、いずれ過負荷で自分の足もとに赤い輪を出す (人に当たる。そのあと帯電は最初から)。光ったら集めて倒すのが早い
//  - 三つの波 (蛾 → 雷乞い混じり → 羽守混じり) のあと、帯電しきった精鋭の羽守。精鋭は連鎖の雷を倍で受け、ときどき光る雷蛾を従える
//  - 雷は敵にだけ跳ぶ (人には当たらない)
public final class ChargeChain extends RaidRoom {
    static final int HX = 14;
    static final int HZ = 14;
    static final int H = 10;
    public static final int FULL = 200;
    static final int OVERLOAD = 520;
    static final int WAVES = 3;
    static final int WAVE_GAP = 80;
    static final double CHAIN_RANGE = 6.5;
    static final int CHAIN_MAX = 3;
    static final float CHAIN_DAMAGE = 14f;
    static final float ELITE_FACTOR = 2f;
    static final double OVERLOAD_R = 3.4;
    static final float OVERLOAD_DAMAGE = 5f;
    static final int ADDS_EVERY = 240;

    private final Map<Mob, Integer> charge = new IdentityHashMap<>();
    private int wave;
    private int cleared;
    private long age;
    private long nextWaveAt;
    private Mob elite;
    private boolean eliteSpawned;
    private boolean done;
    private long nextAdds;
    private boolean started;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.STORM));
        BlockPos o = c.origin();
        BlockState gleam = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
        BlockState cut = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        // 床の輪 (中央の導体の輪。二重)
        for (int r = 3; r <= 7; r += 4) {
            for (int a = 0; a < 720; a++) {
                double ang = Math.toRadians(a / 2.0);
                int x = (int) Math.round(Math.cos(ang) * r);
                int z = (int) Math.round(Math.sin(ang) * r);
                RaidKit.set(level, o.offset(x, 0, z), r == 3 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : gleam);
            }
        }
        RaidKit.fill(level, o.offset(-1, 0, -1), o.offset(1, 0, 1), gleam);
        RaidKit.set(level, o, Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
        // 四隅の電極: 銅の柱の天辺に避雷針、根もとに蛙灯
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * (HX - 2);
                int z = sz * (HZ - 2);
                RaidKit.fill(level, o.offset(x, 1, z), o.offset(x + 1, H - 3, z + 1), cut);
                RaidKit.fill(level, o.offset(x, H - 2, z), o.offset(x + 1, H - 2, z + 1), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
                RaidKit.set(level, o.offset(x, H - 1, z), Blocks.LIGHTNING_ROD.defaultBlockState());
                RaidKit.set(level, o.offset(x, 1, z - sz), Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            }
        }
        // 低い遮りの壁 (跳んで越えられる高さ 1)。四つ、挑戦ごとに少し動く
        for (int i = 0; i < 4; i++) {
            int x = (i < 2 ? -1 : 1) * (5 + ctx.rng().nextInt(3));
            int z = (i % 2 == 0 ? -1 : 1) * (9 + ctx.rng().nextInt(3));
            RaidKit.fill(level, o.offset(x - 1, 1, z), o.offset(x + 1, 1, z), cut);
        }
        // 建築 (docs/design/39 の 4.3): 中央の吊りの避雷の尖塔・雲の床の輪・羽の段の壁・雲の天井
        StormRoomDeco.dressSR3(ctx);
    }

    @Override
    public void start(RaidCtx ctx) {
        started = true;
        age = 0;
        nextWaveAt = 60;
    }

    @Override
    public void tick(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        age++;
        // 倒れた敵: 光っていたら連鎖
        List<Mob> dead = new ArrayList<>();
        for (Map.Entry<Mob, Integer> e : charge.entrySet()) {
            Mob m = e.getKey();
            if (!m.isAlive() || m.isRemoved()) {
                dead.add(m);
            }
        }
        for (Mob m : dead) {
            int c = charge.remove(m);
            boolean discarded = m.isRemoved() && m.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED;
            if (c >= FULL && !discarded) {
                chain(ctx, m.position().add(0, m.getBbHeight() / 2, 0), m);
            }
        }
        // 帯電
        for (Mob m : new ArrayList<>(charge.keySet())) {
            int c = charge.get(m) + 1;
            if (m == elite) {
                c = Math.max(c, FULL);
            }
            charge.put(m, c);
            visual(level, m, c);
            if (c >= FULL && !m.isCurrentlyGlowing()) {
                m.setGlowingTag(true);
                level.playSound(null, m.blockPosition(), SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.8f);
            }
            if (c >= OVERLOAD && m != elite) {
                overload(ctx, m);
                charge.put(m, FULL - 140);
                m.setGlowingTag(false);
            }
        }
        // 波
        List<Mob> alive = ctx.aliveMobs();
        if (done) {
            return;
        }
        if (eliteSpawned) {
            if (elite != null && (!elite.isAlive() || elite.isRemoved())) {
                done = true;
                StormRoomDeco.exitLit(ctx, true);
                for (Mob m : new ArrayList<>(alive)) {
                    m.kill();
                }
                return;
            }
            if (age >= nextAdds && alive.size() < 6) {
                nextAdds = age + ADDS_EVERY;
                for (int i = 0; i < 2; i++) {
                    Vec3 e = elite.position();
                    Mob moth = spawn(ctx, StormMobSetup.STATIC_MOTH.get(), e.x + (i == 0 ? 3 : -3), e.y + 1, e.z + (i == 0 ? 1 : -1));
                    if (moth != null) {
                        charge.put(moth, FULL);
                        moth.setGlowingTag(true);
                    }
                }
            }
            return;
        }
        if (wave > 0 && alive.isEmpty()) {
            cleared = wave;
        }
        if (started && alive.isEmpty() && age >= nextWaveAt) {
            if (wave < WAVES) {
                wave++;
                spawnWave(ctx, wave);
                ctx.say(Component.translatable("message.skyabyss.raid.SR3.wave", wave));
                nextWaveAt = age + WAVE_GAP;
            } else {
                spawnElite(ctx);
            }
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public void reset(RaidCtx ctx) {
        charge.clear();
        wave = 0;
        cleared = 0;
        age = 0;
        elite = null;
        eliteSpawned = false;
        done = false;
        started = false;
        StormRoomDeco.exitLit(ctx, false);
    }

    @Override
    public void end(RaidCtx ctx) {
        charge.clear();
    }

    @Override
    public String objectiveKey() {
        return eliteSpawned ? "raid.skyabyss.SR3.elite" : "raid.skyabyss.SR3.chain";
    }

    @Override
    public int counter() {
        return done ? WAVES + 1 : eliteSpawned ? WAVES : cleared;
    }

    @Override
    public int counterMax() {
        return WAVES + 1;
    }

    // ---- 湧き
    private void spawnWave(RaidCtx ctx, int w) {
        int s = ctx.scale();
        int moths = w == 1 ? 3 + s : w == 2 ? 2 + s / 2 : 3;
        int pray = w == 1 ? 0 : w == 2 ? 2 + s / 2 : 2;
        int wardens = w == 3 ? 1 + s / 4 : 0;
        for (int i = 0; i < moths; i++) {
            enter(ctx, StormMobSetup.STATIC_MOTH.get());
        }
        for (int i = 0; i < pray; i++) {
            enter(ctx, StormMobSetup.THUNDER_SUPPLICANT.get());
        }
        for (int i = 0; i < wardens; i++) {
            enter(ctx, StormMobSetup.FEATHER_WARDEN.get());
        }
    }

    private <T extends Mob> void enter(RaidCtx ctx, net.minecraft.world.entity.EntityType<T> type) {
        BlockPos at = null;
        for (int k = 0; k < 16 && at == null; k++) {
            BlockPos p = RaidKit.randomSpot(ctx, 3);
            boolean near = false;
            for (ServerPlayer pl : ctx.players()) {
                if (pl.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 7 * 7) {
                    near = true;
                }
            }
            if (!near && Math.abs(p.getX() - ctx.origin().getX()) + Math.abs(p.getZ() - ctx.origin().getZ()) > 4) {
                at = p;
            }
        }
        if (at == null) {
            at = RaidKit.randomSpot(ctx, 3);
        }
        double y = type == StormMobSetup.STATIC_MOTH.get() ? at.getY() + 1.5 : at.getY();
        Mob m = spawn(ctx, type, at.getX() + 0.5, y, at.getZ() + 0.5);
        if (m != null) {
            // 湧いたあと、光るまでの間をばらつかせる
            charge.put(m, ctx.rng().nextInt(FULL / 2));
            ServerLevel level = ctx.level();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.getX(), m.getY() + 0.5, m.getZ(), 16, 0.4, 0.6, 0.4, 0.05);
        }
    }

    private void spawnElite(RaidCtx ctx) {
        eliteSpawned = true;
        BlockPos at = ctx.cell().floor(0, -HZ / 2);
        elite = spawn(ctx, StormMobSetup.FEATHER_WARDEN.get(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (elite == null) {
            done = true;
            StormRoomDeco.exitLit(ctx, true);
            return;
        }
        AttributeInstance hp = elite.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(hp.getBaseValue() * (1.0 + 0.3 * (ctx.scale() - 1)));
            elite.setHealth(elite.getMaxHealth());
        }
        charge.put(elite, FULL);
        elite.setGlowingTag(true);
        nextAdds = age + 120;
        ctx.say(Component.translatable("message.skyabyss.raid.SR3.elite"));
        StormRaid.bolt(ctx.level(), elite.position());
    }

    private <T extends Mob> Mob spawn(RaidCtx ctx, net.minecraft.world.entity.EntityType<T> type, double x, double y, double z) {
        return ctx.adopt(StormRaid.spawn(ctx.level(), type, ctx.group(), x, y, z));
    }

    // ---- 帯電の見た目と過負荷
    private void visual(ServerLevel level, Mob m, int c) {
        float f = Math.min(1f, c / (float) FULL);
        if (f >= 0.3f && (c % Math.max(2, (int) (10 - 8 * f))) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 1 + (int) (3 * f), m.getBbWidth() * 0.5, m.getBbHeight() * 0.4,
                    m.getBbWidth() * 0.5, 0.03);
        }
    }

    // 過負荷: 自分の足もとに赤い輪 (予告のあと人に当たる)
    private void overload(RaidCtx ctx, Mob m) {
        ServerLevel level = ctx.level();
        double cx = m.getX();
        double cz = m.getZ();
        Telegraph.in(level, TelegraphShape.circle(cx, cz, OVERLOAD_R))
                .floor(m.getY()).warn(26).danger().owner(m)
                .startSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f)
                .onImpact(lvl -> {
                    StormRaid.sparks(lvl, new Vec3(cx, m.getY() + 0.6, cz), 50, 1.2);
                    StormRaid.zap(lvl, BlockPos.containing(cx, m.getY(), cz), 1.0f, 1.5f);
                })
                .onHit((lvl, p) -> p.hurt(lvl.damageSources().lightningBolt(), OVERLOAD_DAMAGE))
                .start();
    }

    // ---- 連鎖: 光る敵が倒れた点から、近くの敵 (最大 CHAIN_MAX 体) へ
    public int chain(RaidCtx ctx, Vec3 from, Mob source) {
        ServerLevel level = ctx.level();
        List<Mob> near = new ArrayList<>();
        for (Mob m : ctx.aliveMobs()) {
            if (m != source && m.isAlive() && m.position().distanceTo(from) <= CHAIN_RANGE) {
                near.add(m);
            }
        }
        near.sort((a, b) -> Double.compare(a.position().distanceToSqr(from), b.position().distanceToSqr(from)));
        int hit = 0;
        for (Mob m : near) {
            if (hit >= CHAIN_MAX) {
                break;
            }
            float dmg = CHAIN_DAMAGE * (m == elite ? ELITE_FACTOR : 1f);
            Vec3 to = m.position().add(0, m.getBbHeight() / 2, 0);
            arc(level, from, to);
            m.hurt(level.damageSources().lightningBolt(), dmg);
            hit++;
        }
        StormRaid.zap(level, BlockPos.containing(from), 1.0f, hit > 0 ? 1.4f : 1.0f);
        StormRaid.sparks(level, from, 20, 0.4);
        return hit;
    }

    private static void arc(ServerLevel level, Vec3 a, Vec3 b) {
        int n = Math.max(2, (int) (a.distanceTo(b) * 3));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ---- 試験用
    public int chargeOf(Mob m) {
        return charge.getOrDefault(m, 0);
    }

    public void setCharge(Mob m, int c) {
        charge.put(m, c);
        m.setGlowingTag(c >= FULL);
    }

    public int wave() {
        return wave;
    }

    public Mob elite() {
        return elite;
    }

    public boolean eliteSpawned() {
        return eliteSpawned;
    }
}

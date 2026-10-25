package jp.revati.skyabyss.trial.raid.storm;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 闘 SR6「嵐の目へ」: 丸い銅の広間。中央から外へ吹く突風に逆らって進み、中央に立つ「嵐の目の守り手」(羽守の精鋭) を倒す。
//  - 突風は GUST_PERIOD tick ごと。始まる WARN tick 前から広間いっぱいに灰色の押しの印 (傷なし)・風の唸り・雲の流れ、吹くのは GUST tick の間。
//    吹いている間、広間の人は中央から外へ押される。柱の陰 (中央との間にブロックがある) は押されない
//  - 凪の間 (突風と突風の間) に柱から柱へ進む。突風が来るたびに、雷蛾が二体ずつ湧く (四体まで)。守り手と取り巻きの傷には、赤い予告が付く (敵の側)
//  - 守り手は中央の台のまわりから離れすぎない。倒すと越える。全滅・やり直しで敵は消え、突風は最初から
//  - 闘の試練では (32 の 9.5) 守り手は軽く (体力の倍率なし)、倒すと中央に雷獣が降りる (部屋に避雷の柱と栓が建つ)。
//    突風は雷獣との戦いのあいだも吹き続け (雷蛾も湧く)、柱の陰が身を守る所のまま
public final class EyeAdvance extends RaidRoom {
    static final int HX = 14;
    static final int HZ = 14;
    static final int H = 10;
    static final int GUST_PERIOD = 170;
    static final int FIRST_GUST = 110;
    static final int WARN = 40;
    static final int GUST = 56;
    static final double IMPULSE = 0.13;
    static final double TETHER = 9.0;
    static final int MOTH_CAP = 4;

    // tick = 始まってからの tick。突風の吹いている間か
    static boolean blowing(long age) {
        if (age < FIRST_GUST) {
            return false;
        }
        long t = (age - FIRST_GUST) % GUST_PERIOD;
        return t >= WARN && t < WARN + GUST;
    }

    // 突風の予告の間か
    static boolean warning(long age) {
        if (age < FIRST_GUST) {
            return false;
        }
        return (age - FIRST_GUST) % GUST_PERIOD < WARN;
    }

    // 次の突風が吹き始めるまでの tick (吹いている間は負)
    static int untilGust(long age) {
        if (age < FIRST_GUST) {
            return (int) (FIRST_GUST + WARN - age);
        }
        long t = (age - FIRST_GUST) % GUST_PERIOD;
        if (t < WARN) {
            return (int) (WARN - t);
        }
        if (t < WARN + GUST) {
            return -1;
        }
        return (int) (GUST_PERIOD - t + WARN);
    }

    // 中央 (cx, cz) から外へ向かう押しの向き (単位ベクトルに mag を掛ける)
    static double[] pushOf(double cx, double cz, double px, double pz, double mag) {
        double dx = px - cx;
        double dz = pz - cz;
        double d = Math.hypot(dx, dz);
        if (d < 1e-6) {
            return new double[] {0, 0};
        }
        return new double[] {dx / d * mag, dz / d * mag};
    }

    private final List<int[]> pillars = new ArrayList<>();
    private Mob keeper;
    private boolean keeperSpawned;
    private boolean done;
    private boolean started;
    private long age;
    private int generation;
    private final List<Telegraph> gusts = new ArrayList<>();

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.STORM));
        BlockPos o = c.origin();
        BlockState cut = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        BlockState gleam = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
        // 中央の台 (五×五の明るい銅の円と、その真ん中の灯り)。足場より低い (床と同じ高さ)
        RaidKit.fill(level, o.offset(-2, 0, -2), o.offset(2, 0, 2), gleam);
        RaidKit.fill(level, o.offset(-1, 0, -1), o.offset(1, 0, 1), Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        // 同心の筋 (突風の向きを床に見せる): 十字の四方へ、台から外へ矢の列
        for (int k = 4; k <= HX - 2; k += 3) {
            RaidKit.set(level, o.offset(k, 0, 0), gleam);
            RaidKit.set(level, o.offset(-k, 0, 0), gleam);
            RaidKit.set(level, o.offset(0, 0, -k), gleam);
        }
        // 柱: 内の輪 (半径 6) に六本、外の輪 (半径 10) に六本。位置は挑戦ごとに少し回る。戸の前 (南の中央) は空ける
        double rot = ctx.rng().nextDouble() * Math.PI / 6;
        for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? 6.0 : 10.0;
            for (int k = 0; k < 6; k++) {
                double a = rot + k * Math.PI / 3 + (ring == 1 ? Math.PI / 6 : 0);
                int x = (int) Math.round(Math.cos(a) * r);
                int z = (int) Math.round(Math.sin(a) * r);
                if (Math.abs(x) < 4 && z > 5 || Math.abs(x) > HX - 2 || Math.abs(z) > HZ - 2) {
                    continue;
                }
                pillars.add(new int[] {x, z});
                RaidKit.fill(level, o.offset(x, 1, z), o.offset(x + 1, H - 3, z + 1), cut);
                RaidKit.fill(level, o.offset(x, H - 2, z), o.offset(x + 1, H - 2, z + 1), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
                RaidKit.set(level, o.offset(x, H - 1, z), Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
        // 建築 (docs/design/39 の 4.3): 丸い広間・天井の嵐の目・羽の段の壁 (胸の高さの広間の中には足さない)
        StormRoomDeco.dressSR6(ctx);
    }

    @Override
    public void start(RaidCtx ctx) {
        started = true;
        age = 0;
        spawnKeeper(ctx);
    }

    private void spawnKeeper(RaidCtx ctx) {
        BlockPos at = ctx.cell().floor(0, 0);
        keeper = ctx.adopt(StormRaid.spawn(ctx.level(), StormMobSetup.FEATHER_WARDEN.get(), ctx.group(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
        keeperSpawned = true;
        if (keeper == null) {
            done = true;
            StormRoomDeco.exitLit(ctx, true);
            return;
        }
        AttributeInstance hp = keeper.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(hp.getBaseValue() * ((bossFollows ? 1.0 : 1.6) + 0.4 * (ctx.scale() - 1)));
            keeper.setHealth(keeper.getMaxHealth());
        }
        keeper.setGlowingTag(true);
        for (int i = 0; i < 1 + ctx.scale() / 2; i++) {
            double a = i * Math.PI;
            ctx.adopt(StormRaid.spawn(ctx.level(), StormMobSetup.THUNDER_SUPPLICANT.get(), ctx.group(), at.getX() + 0.5 + Math.cos(a) * 4, at.getY(), at.getZ() + 0.5 + Math.sin(a) * 4));
        }
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (!started) {
            return;
        }
        ServerLevel level = ctx.level();
        age++;
        // 突風: 予告の始まりに印と音
        if (age >= FIRST_GUST && (age - FIRST_GUST) % GUST_PERIOD == 0) {
            announce(ctx);
        }
        wind(ctx);
        // 守り手: 台から離れすぎたら戻る
        if (keeper != null && keeper.isAlive()) {
            BlockPos c = ctx.cell().floor(0, 0);
            if (age % 20 == 0 && Math.hypot(keeper.getX() - c.getX() - 0.5, keeper.getZ() - c.getZ() - 0.5) > TETHER) {
                keeper.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.2);
            }
        } else if (keeperSpawned && keeper != null) {
            done = true;
            if (!bossFollows) {
                StormRoomDeco.exitLit(ctx, true);
            }
        }
    }

    @Override
    public void bossBegin(RaidCtx ctx, Mob boss) {
        ctx.say(Component.translatable("message.skyabyss.raid.SR6.raiju"));
    }

    // 中ボスの段も突風は吹き続ける (柱の陰に隠れながら雷獣と戦う)
    @Override
    public void bossTick(RaidCtx ctx, Mob boss) {
        age++;
        if (age >= FIRST_GUST && (age - FIRST_GUST) % GUST_PERIOD == 0) {
            announce(ctx);
        }
        wind(ctx);
    }

    @Override
    public void bossDone(RaidCtx ctx) {
        for (Telegraph t : gusts) {
            t.cancel();
        }
        gusts.clear();
        StormRoomDeco.exitLit(ctx, true);
    }

    // 突風の予告: 灰色の押しの印 (広間いっぱい。傷なし)・唸り
    private void announce(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        Vec3 c = centre(ctx);
        gusts.removeIf(Telegraph::isDone);
        gusts.add(Telegraph.in(level, TelegraphShape.circle(c.x, c.z, HX + 1.0))
                .floor(ctx.origin().getY() + 1).warn(WARN).active(GUST).harmless()
                .startSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 0.8f, 0.5f)
                .victims(p -> false)
                .start());
        ctx.say(Component.translatable("message.skyabyss.raid.SR6.gust"));
        // 雷蛾が湧く (四体まで)
        int moths = 0;
        for (Mob m : ctx.aliveMobs()) {
            if (m.getType() == StormMobSetup.STATIC_MOTH.get()) {
                moths++;
            }
        }
        for (int i = 0; i < 2 && moths + i < MOTH_CAP; i++) {
            double a = ctx.rng().nextDouble() * Math.PI * 2;
            double x = c.x + Math.cos(a) * (HX - 3);
            double z = c.z + Math.sin(a) * (HZ - 3);
            ctx.adopt(StormRaid.spawn(level, StormMobSetup.STATIC_MOTH.get(), ctx.group(), x, ctx.origin().getY() + 2, z));
        }
    }

    // 風の見た目と押し
    private void wind(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        Vec3 c = centre(ctx);
        boolean blow = blowing(age);
        if (age % 2 == 0 && (blow || warning(age))) {
            for (int i = 0; i < 4; i++) {
                double a = ctx.rng().nextDouble() * Math.PI * 2;
                double r = 1.5 + ctx.rng().nextDouble() * (HX - 2);
                double y = ctx.origin().getY() + 1 + ctx.rng().nextInt(3);
                double[] v = pushOf(0, 0, Math.cos(a), Math.sin(a), blow ? 0.9 : 0.25);
                level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 0, v[0], 0, v[1], 1.0);
            }
        }
        if (blow && age % 10 == 0) {
            level.playSound(null, BlockPos.containing(c), SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 1.2f, 0.6f);
        }
        if (!blow) {
            return;
        }
        for (ServerPlayer p : ctx.standing()) {
            if (!ctx.cell().inArena(p.getX(), p.getZ()) || sheltered(ctx, p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            double[] v = pushOf(c.x, c.z, p.getX(), p.getZ(), IMPULSE);
            p.setDeltaMovement(p.getDeltaMovement().add(v[0], 0, v[1]));
            p.hurtMarked = true;
        }
    }

    Vec3 centre(RaidCtx ctx) {
        return new Vec3(ctx.origin().getX() + 0.5, ctx.origin().getY() + 1, ctx.origin().getZ() + 0.5);
    }

    // (px, py, pz) は柱の陰か: 中央から人への線 (胸の高さ) の途中にブロックがある。中央の台のそば (1.5 ブロックまで) は数えない
    boolean sheltered(RaidCtx ctx, double px, double py, double pz) {
        Vec3 c = centre(ctx);
        double y = py + 1.0;
        double dx = px - c.x;
        double dz = pz - c.z;
        double d = Math.hypot(dx, dz);
        for (double s = 1.5; s < d - 0.4; s += 0.35) {
            BlockPos at = BlockPos.containing(c.x + dx / d * s, y, c.z + dz / d * s);
            BlockState st = ctx.level().getBlockState(at);
            // 見えない灯り (minecraft:light) は陰にしない: 床の灯りが胸の高さに並んでも、通り道は開いたまま
            if (!st.isAir() && !st.is(Blocks.LIGHT)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        for (Telegraph t : gusts) {
            t.cancel();
        }
        gusts.clear();
        keeper = null;
        keeperSpawned = false;
        done = false;
        started = false;
        age = 0;
        StormRoomDeco.exitLit(ctx, false);
    }

    @Override
    public void end(RaidCtx ctx) {
        generation++;
        for (Telegraph t : gusts) {
            t.cancel();
        }
        gusts.clear();
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.SR6.advance";
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.SR6.keeper";
    }

    @Override
    public int secondary() {
        return keeper != null && keeper.isAlive() ? (int) Math.ceil(keeper.getHealth()) : 0;
    }

    @Override
    public int secondaryMax() {
        return keeper != null ? (int) keeper.getMaxHealth() : 0;
    }

    @Override
    public int timerTicks() {
        if (!started || done) {
            return -1;
        }
        return untilGust(age);
    }

    @Override
    public int bossTimerTicks() {
        return untilGust(age);
    }

    // ---- 試験用
    public long age() {
        return age;
    }

    public void setAge(long a) {
        age = a;
    }

    @Nullable
    public Mob keeper() {
        return keeper;
    }

    public List<int[]> pillars() {
        return pillars;
    }
}

package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.boss.BossLook;
import jp.revati.skyabyss.abyss.mobs.AbyssMob;
import jp.revati.skyabyss.abyss.mobs.AbyssMobB;
import jp.revati.skyabyss.abyss.mobs.AbyssMobKind;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.abyss.mobs.EmberKeeper;
import jp.revati.skyabyss.abyss.mobs.NamelessShape;
import jp.revati.skyabyss.abyss.mobs.PastShadow;
import jp.revati.skyabyss.abyss.mobs.SootChild;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 歪んだ王 (docs/design/36 の 9・王の階)。5 階ごと。深淵の敵の種を体にして、大きく歪めた一体の王。
//   王の型 5 種を順に回す (10 階の王は 2 番目 …)。一周するごとに体が大きく、技が一つ早く増える。
//   技 5 種 (SLAM 叩きつけ・CHARGE 突進・SUMMON 手下を呼ぶ・BOLTS 落ちる光・SWEEP 衝撃の輪)。型ごとに四つ持つ。
//   段 (体力の割合): 1 (66% 超) は二つ、2 (33% 超) は三つ、3 (33% 以下) は四つの技を使い、間隔が詰まる。
//   予告: 赤い粒だけ。予告の間、王はその場に立ち止まる (止まらない型は動いたまま)。当たりは予告の最後の一瞬。
//   予告の長さは 1.5〜2 秒。深く行くほど短くはなるが、1.2 秒を切らない。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthBoss {
    public static final String KING_TAG = "skyabyss_depth_king";
    public static final String ADD_TAG = "skyabyss_depth_add";
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1f, 0.1f, 0.1f), 1.3f);
    private static final DustParticleOptions RED_SMALL = new DustParticleOptions(new Vector3f(0.9f, 0.08f, 0.08f), 0.8f);

    public enum Move {
        SLAM, CHARGE, SUMMON, BOLTS, SWEEP
    }

    // 王の型。body は体にする敵の種、add は手下の種。moves は覚える順 (先の二つが最初から)
    public record King(String id, String body, String add, boolean walks, Move[] moves) {
    }

    public static final King[] KINGS = {
            new King("bone", AbyssMobKind.RIB_GUARD.id, AbyssMobKind.BONE_PICKER.id, true, new Move[] {Move.SLAM, Move.SUMMON, Move.CHARGE, Move.SWEEP}),
            new King("salt", AbyssMobKind.SALT_HUSK.id, AbyssMobKind.TIDE_HANDS.id, true, new Move[] {Move.CHARGE, Move.SLAM, Move.SUMMON, Move.BOLTS}),
            new King("storm", AbyssMobKind.LIGHTNING_IDOL.id, AbyssMobKind.CHARGED_MOTH.id, false, new Move[] {Move.BOLTS, Move.SUMMON, Move.SWEEP, Move.SLAM}),
            new King("ember", EmberKeeper.ID, SootChild.ID, true, new Move[] {Move.SLAM, Move.BOLTS, Move.SUMMON, Move.CHARGE}),
            new King("shadow", PastShadow.ID, NamelessShape.ID, true, new Move[] {Move.CHARGE, Move.SUMMON, Move.SWEEP, Move.BOLTS}),
    };

    private DepthBoss() {
    }

    // ---------------------------------------------------------------- 決まり (純粋な計算)
    public static int kingIndex(King def) {
        for (int k = 0; k < KINGS.length; k++) {
            if (KINGS[k] == def) {
                return k;
            }
        }
        return 0;
    }

    public static King kingOf(int bossNo) {
        return KINGS[Math.floorMod(bossNo - 1, KINGS.length)];
    }

    // 一周目 0、二周目 1 …
    public static int cycleOf(int bossNo) {
        return Math.max(0, (bossNo - 1) / KINGS.length);
    }

    // 段 (0〜2) での、使える技の数。深いほど (周が進むほど) 早く増える
    public static int movesAllowed(int bossNo, int phase) {
        return Math.min(4, 2 + phase + Math.min(2, cycleOf(bossNo)));
    }

    public static int phaseOf(double hpFrac) {
        return hpFrac > 0.66 ? 0 : hpFrac > 0.33 ? 1 : 2;
    }

    // 王の体力 (組の人数で増える)。深いほど増える
    public static double hp(int bossNo, int party) {
        return (220 + 45.0 * bossNo) * (1 + 0.55 * (Math.max(1, party) - 1));
    }

    // 技の傷
    public static double dmg(int bossNo) {
        return Math.min(24, 6 + 0.5 * bossNo);
    }

    public static double scale(int bossNo) {
        return Math.min(2.4, 1.6 + 0.04 * (bossNo - 1));
    }

    // 技と技のあいだ (tick)。段が進むほど短い。下限 30
    public static int cooldown(int bossNo, int phase) {
        return Math.max(30, 90 - 14 * phase - 2 * bossNo);
    }

    // 予告の長さ (tick)。下限 24 (1.2 秒)
    public static int telegraph(Move m, int bossNo) {
        int base = switch (m) {
            case SLAM -> 36;
            case CHARGE -> 38;
            case SUMMON -> 44;
            case BOLTS -> 44;
            case SWEEP -> 40;
        };
        return Math.max(24, base - bossNo / 2);
    }

    // ---------------------------------------------------------------- 戦い
    public static final class Fight {
        final UUID kingId;
        final int bossNo;
        final King def;
        final ServerBossEvent bar;
        final int floor;
        final List<UUID> adds = new ArrayList<>();
        int phase = 0;
        int cooldown = 80;
        Move active;
        int left;
        int total;
        Vec3 center = Vec3.ZERO;
        Vec3 dir = Vec3.ZERO;
        List<Vec3> points = new ArrayList<>();
        final List<Vec3> extra = new ArrayList<>();
        int dash;
        boolean dashHit;
        int empty;
        final Random rng;

        Fight(UUID kingId, int floor, King def, Component name, long seed) {
            this.kingId = kingId;
            this.floor = floor;
            this.bossNo = DepthPlan.bossNo(floor);
            this.def = def;
            this.bar = new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
            this.rng = new Random(seed);
        }
    }

    // 広間に人が踏み入ったとき、一度だけ王を呼ぶ
    static void start(ServerLevel level, DepthBuilder.Inst i, List<ServerPlayer> party) {
        if (i.boss != null || i.bossDeadAt >= 0) {
            return;
        }
        int bossNo = DepthPlan.bossNo(i.floor);
        King def = kingOf(bossNo);
        DepthFloor.Spot sp = i.plan.bossSpawn();
        BlockPos feet = i.feet(sp.x(), sp.z());
        Entity e = AbyssMobSpawner.spawnAt(level, def.body(), feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 3);
        if (e instanceof AbyssMob m) {
            m.setDeep(DepthPlan.stepsOf(i.floor));
        } else if (e instanceof AbyssMobB b) {
            b.setDeep(DepthPlan.stepsOf(i.floor));
        } else {
            SkyAbyss.LOGGER.warn("[abyss depth] king body {} did not spawn", def.body());
            return;
        }
        LivingEntity king = (LivingEntity) e;
        double hp = hp(bossNo, party.size());
        // 湧いた時の雑魚の下げ (MobTuning。体力を決める前の小さな体で付く) を外してから、王の体力を決める
        jp.revati.skyabyss.combat.MobTuning.untune(king);
        king.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        king.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(dmg(bossNo) * 0.8);
        if (king.getAttribute(Attributes.SCALE) != null) {
            king.getAttribute(Attributes.SCALE).setBaseValue(scale(bossNo));
        }
        king.setHealth((float) hp);
        Component name = Component.translatable("message.skyabyss.depths.king." + def.id());
        king.setCustomName(name);
        king.setCustomNameVisible(true);
        king.addTag(KING_TAG);
        king.addTag(DepthMobs.TAG);
        // 見た目: 型ごとの王の姿。周が進むと歪んだ姿 (BossLook)
        BossLook.set(king, BossLook.pack(BossLook.KING_BONE + kingIndex(def), cycleOf(bossNo), 0));
        if (king instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        Fight f = new Fight(king.getUUID(), i.floor, def, name, i.seed ^ 0xB055);
        i.boss = f;
        for (ServerPlayer p : party) {
            f.bar.addPlayer(p);
            Notice.alert(p, "message.skyabyss.depths.king.appear", name);
            // 王の正体を一行で (心の声)
            Notice.narrate(p, "message.skyabyss.depths.king.reveal." + def.id());
            Notice.objective(p, "message.skyabyss.depths.obj.king", name);
        }
        SkyAbyss.LOGGER.info("[abyss depth] king {} woke on floor {} (hp {}, party {})", def.id(), i.floor, hp, party.size());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        for (DepthBuilder.Inst i : List.copyOf(DepthBuilder.all())) {
            if (i.boss != null && i.ready) {
                tick(server, level, i, i.boss);
            }
        }
    }

    static List<ServerPlayer> party(ServerLevel level, DepthBuilder.Inst i) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, i.box())) {
            if (p.isAlive() && !p.isSpectator()) {
                out.add(p);
            }
        }
        return out;
    }

    static void tick(MinecraftServer server, ServerLevel level, DepthBuilder.Inst i, Fight f) {
        tick(server, level, i, f, party(level, i));
    }

    // party は階の中の生きている人 (自己テストは偽の人を渡す)
    static void tick(MinecraftServer server, ServerLevel level, DepthBuilder.Inst i, Fight f, List<ServerPlayer> party) {
        Entity ke = level.getEntity(f.kingId);
        if (!(ke instanceof LivingEntity king) || !king.isAlive()) {
            if (ke == null && f.empty < 400 && party.isEmpty()) {
                f.empty++;
                if (f.empty >= 400) {
                    clear(level, i, f);
                }
                return;
            }
            win(server, level, i, f, party);
            return;
        }
        if (party.isEmpty()) {
            if (++f.empty > 200) {
                clear(level, i, f);
            }
            return;
        }
        f.empty = 0;
        for (ServerPlayer p : party) {
            f.bar.addPlayer(p);
        }
        f.bar.setProgress(Math.max(0f, king.getHealth() / king.getMaxHealth()));
        // 段のボスバー (色で段。story/ch5/client/AbyssBossBarClient)
        jp.revati.skyabyss.story.ch5.AbyssBossBars.phase(f.bar, f.phase);
        int ph = phaseOf(king.getHealth() / king.getMaxHealth());
        if (ph > f.phase) {
            f.phase = ph;
            f.cooldown = Math.min(f.cooldown, 30);
            for (ServerPlayer p : party) {
                Notice.alert(p, "message.skyabyss.depths.king.phase" + ph, king.getCustomName());
            }
            if (f.active != null) {
                endMove(king, f);
            }
            BossLook.clear(king);
            BossLook.setPhase(king, ph);
        }
        double dmg = dmg(f.bossNo) * (1 + 0.08 * DepthPlan.stepsOf(f.floor));
        if (f.dash > 0) {
            dash(level, king, f, party, dmg);
            return;
        }
        if (f.active == null) {
            if (--f.cooldown <= 0) {
                begin(level, i, king, f, party);
            }
            return;
        }
        f.left--;
        drawTelegraph(level, king, f, party);
        if (f.left <= 0) {
            resolve(level, i, king, f, party, dmg);
        }
    }

    static Move choose(Fight f) {
        int n = movesAllowed(f.bossNo, f.phase);
        return f.def.moves()[f.rng.nextInt(n)];
    }

    static void begin(ServerLevel level, DepthBuilder.Inst i, LivingEntity king, Fight f, List<ServerPlayer> party) {
        Move m = choose(f);
        if (m == Move.CHARGE && !f.def.walks()) {
            m = Move.BOLTS;
        }
        if (m == Move.SUMMON && aliveAdds(level, f) >= 6 + f.phase) {
            m = Move.SLAM;
        }
        f.active = m;
        f.total = telegraph(m, f.bossNo);
        f.left = f.total;
        f.center = king.position();
        ServerPlayer target = nearest(party, king.position());
        switch (m) {
            case CHARGE -> {
                Vec3 d = target.position().subtract(king.position());
                d = new Vec3(d.x, 0, d.z);
                f.dir = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
            }
            case SUMMON -> {
                f.points.clear();
                int n = 2 + f.phase + Math.min(2, cycleOf(f.bossNo));
                for (int k = 0; k < n; k++) {
                    double a = f.rng.nextDouble() * Math.PI * 2;
                    double r = 4 + f.rng.nextDouble() * 5;
                    f.points.add(king.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                }
            }
            case BOLTS -> {
                f.points.clear();
                f.extra.clear();
                for (int k = 0; k < f.phase; k++) {
                    double a = f.rng.nextDouble() * Math.PI * 2;
                    double r = 3 + f.rng.nextDouble() * 9;
                    f.extra.add(king.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                }
            }
            default -> {
            }
        }
        if (f.def.walks() && m != Move.SUMMON) {
            freeze(king, true);
        }
        BossLook.windup(king, m.ordinal() + 1, f.total);
        for (ServerPlayer p : party) {
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.6f, 0.6f);
        }
    }

    static void freeze(LivingEntity king, boolean on) {
        if (king instanceof Mob m) {
            m.setNoAi(on);
        }
    }

    static void endMove(LivingEntity king, Fight f) {
        freeze(king, false);
        f.active = null;
        f.points.clear();
        f.cooldown = cooldown(f.bossNo, f.phase);
    }

    static ServerPlayer nearest(List<ServerPlayer> party, Vec3 from) {
        ServerPlayer best = party.get(0);
        for (ServerPlayer p : party) {
            if (p.position().distanceToSqr(from) < best.position().distanceToSqr(from)) {
                best = p;
            }
        }
        return best;
    }

    static int aliveAdds(ServerLevel level, Fight f) {
        int n = 0;
        for (UUID u : new ArrayList<>(f.adds)) {
            Entity e = level.getEntity(u);
            if (e != null && e.isAlive()) {
                n++;
            } else {
                f.adds.remove(u);
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 予告 (赤い粒だけ)
    static void ring(ServerLevel level, Vec3 c, double r, int n) {
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * k / n;
            level.sendParticles(RED, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    static void fill(ServerLevel level, Vec3 c, double r, int n, Random rng) {
        for (int k = 0; k < n; k++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rng.nextDouble()) * r;
            level.sendParticles(RED_SMALL, c.x + Math.cos(a) * d, c.y + 0.1, c.z + Math.sin(a) * d, 1, 0, 0, 0, 0);
        }
    }

    static void drawTelegraph(ServerLevel level, LivingEntity king, Fight f, List<ServerPlayer> party) {
        if (f.left % 2 != 0) {
            return;
        }
        double prog = 1.0 - (double) f.left / f.total;
        switch (f.active) {
            case SLAM -> {
                double r = 4.5 + 0.3 * f.phase;
                ring(level, f.center, r, 28);
                fill(level, f.center, r * prog, 10, f.rng);
            }
            case CHARGE -> {
                for (int d = 0; d <= 14; d += 1) {
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 p = f.center.add(f.dir.scale(d)).add(-f.dir.z * s * 1.0, 0, f.dir.x * s * 1.0);
                        level.sendParticles(RED_SMALL, p.x, p.y + 0.15, p.z, 1, 0, 0, 0, 0);
                    }
                }
                king.setYRot((float) (Math.toDegrees(Math.atan2(-f.dir.x, f.dir.z))));
                king.yHeadRot = king.getYRot();
            }
            case SUMMON -> {
                for (Vec3 p : f.points) {
                    ring(level, p, 1.2, 10);
                    fill(level, p, 1.2 * prog, 3, f.rng);
                }
            }
            case BOLTS -> {
                if (f.left > f.total * 0.4 || f.points.isEmpty()) {
                    // 最初の六割は人を追う。残りは位置を固める
                    f.points.clear();
                    for (ServerPlayer p : party) {
                        f.points.add(p.position());
                    }
                    f.points.addAll(f.extra);
                }
                for (Vec3 p : f.points) {
                    ring(level, p, 2.2, 14);
                    fill(level, p, 2.2 * prog, 5, f.rng);
                }
            }
            case SWEEP -> {
                ring(level, f.center, 4.0, 24);
                ring(level, f.center, 9.5, 48);
                for (int k = 0; k < 16; k++) {
                    double a = f.rng.nextDouble() * Math.PI * 2;
                    double d = 4.0 + f.rng.nextDouble() * 5.5 * prog;
                    level.sendParticles(RED_SMALL, f.center.x + Math.cos(a) * d, f.center.y + 0.1, f.center.z + Math.sin(a) * d, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 当たり
    // 当たった回数 (自己テストが見る。偽の人は傷を受けないので)
    static int hits;

    static void hurt(ServerLevel level, LivingEntity king, ServerPlayer p, double dmg) {
        hits++;
        p.hurt(level.damageSources().mobAttack(king), (float) dmg);
    }

    static void resolve(ServerLevel level, DepthBuilder.Inst i, LivingEntity king, Fight f, List<ServerPlayer> party, double dmg) {
        Move m = f.active;
        BossLook.strike(king, m.ordinal() + 1);
        switch (m) {
            case SLAM -> {
                double r = 4.5 + 0.3 * f.phase;
                ring(level, f.center, r, 40);
                for (ServerPlayer p : party) {
                    if (flat(p.position(), f.center) <= r && Math.abs(p.getY() - f.center.y) < 3) {
                        hurt(level, king, p, dmg);
                    }
                }
                level.playSound(null, BlockPos.containing(f.center), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.5f);
                endMove(king, f);
            }
            case CHARGE -> {
                f.dash = 10;
                f.dashHit = false;
                freeze(king, false);
                f.active = null;
                level.playSound(null, BlockPos.containing(f.center), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8f, 1.2f);
            }
            case SUMMON -> {
                int cap = 6 + f.phase;
                for (Vec3 p : f.points) {
                    if (aliveAdds(level, f) >= cap) {
                        break;
                    }
                    BlockPos feet = BlockPos.containing(p.x, i.feet(0, 0).getY(), p.z);
                    if (!level.getBlockState(feet).isAir() || !i.contains(feet)) {
                        continue;
                    }
                    Entity add = DepthMobs.spawnId(level, f.floor, feet, f.rng, f.def.add());
                    if (add instanceof LivingEntity le) {
                        DepthMobs.tuneAdd(le, 0.7);
                        le.addTag(ADD_TAG);
                        f.adds.add(le.getUUID());
                    }
                    ring(level, p, 1.2, 14);
                }
                endMove(king, f);
            }
            case BOLTS -> {
                for (Vec3 p : f.points) {
                    ring(level, p, 2.2, 28);
                    for (int h = 0; h < 8; h++) {
                        level.sendParticles(RED, p.x, p.y + 0.3 + h * 0.5, p.z, 2, 0.3, 0, 0.3, 0);
                    }
                    for (ServerPlayer pl : party) {
                        if (flat(pl.position(), p) <= 2.2 && Math.abs(pl.getY() - p.y) < 3) {
                            hurt(level, king, pl, dmg * 0.9);
                        }
                    }
                }
                level.playSound(null, BlockPos.containing(f.center), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.7f, 0.7f);
                endMove(king, f);
            }
            case SWEEP -> {
                ring(level, f.center, 4.0, 40);
                ring(level, f.center, 9.5, 70);
                for (ServerPlayer p : party) {
                    double d = flat(p.position(), f.center);
                    if (d >= 4.0 && d <= 9.5 && p.getY() < f.center.y + 1.0 + 0.6) {
                        hurt(level, king, p, dmg * 0.9);
                    }
                }
                level.playSound(null, BlockPos.containing(f.center), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.8f, 0.8f);
                endMove(king, f);
            }
        }
    }

    static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void dash(ServerLevel level, LivingEntity king, Fight f, List<ServerPlayer> party, double dmg) {
        f.dash--;
        king.setDeltaMovement(f.dir.x * 1.2, king.getDeltaMovement().y, f.dir.z * 1.2);
        king.hurtMarked = true;
        level.sendParticles(RED_SMALL, king.getX(), king.getY() + 0.5, king.getZ(), 4, 0.5, 0.4, 0.5, 0);
        if (!f.dashHit) {
            for (ServerPlayer p : party) {
                if (p.position().distanceTo(king.position()) < 2.3 * king.getScale()) {
                    hurt(level, king, p, dmg);
                    f.dashHit = true;
                    p.knockback(0.9, -f.dir.x, -f.dir.z);
                }
            }
        }
        if (f.dash <= 0) {
            f.cooldown = cooldown(f.bossNo, f.phase);
        }
    }

    // ---------------------------------------------------------------- 終わり
    static void clear(ServerLevel level, DepthBuilder.Inst i, Fight f) {
        Entity ke = level.getEntity(f.kingId);
        if (ke != null) {
            ke.discard();
        }
        for (UUID u : f.adds) {
            Entity e = level.getEntity(u);
            if (e != null) {
                e.discard();
            }
        }
        f.bar.removeAllPlayers();
        i.boss = null;
        SkyAbyss.LOGGER.info("[abyss depth] king reset on floor {}", i.floor);
    }

    // 王を倒した: 下り口を開く・褒美の箱・再開の階を進める
    static void win(MinecraftServer server, ServerLevel level, DepthBuilder.Inst i, Fight f, List<ServerPlayer> party) {
        for (UUID u : f.adds) {
            Entity e = level.getEntity(u);
            if (e != null) {
                e.discard();
            }
        }
        f.bar.removeAllPlayers();
        i.boss = null;
        i.bossDeadAt = server.getTickCount();
        openStairs(level, i);
        Random r = new Random(i.seed ^ 0x77);
        for (DepthFloor.Chest c : i.plan.chests()) {
            if (c.tier() != 3) {
                continue;
            }
            BlockPos at = i.pos(c.x(), DepthFloorBuild.FLOOR_YI + 1, c.z());
            level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(at) instanceof ChestBlockEntity cb) {
                DepthLoot.fill(cb, i.floor, 3, r);
                cb.setChanged();
            }
        }
        DepthRun.checkpointReached(server, i);
        for (ServerPlayer p : party) {
            Notice.narrate(p, "message.skyabyss.depths.king.down", Component.translatable("message.skyabyss.depths.king." + f.def.id()));
            Notice.objective(p, "message.skyabyss.depths.obj.stairs_open");
            Notice.hint(p, "message.skyabyss.depths.checkpoint", i.floor + 1);
            p.giveExperienceLevels(2 + f.bossNo / 2);
        }
        SkyAbyss.LOGGER.info("[abyss depth] king down on floor {}", i.floor);
    }

    static void openStairs(ServerLevel level, DepthBuilder.Inst i) {
        i.stairsOpen = true;
        DepthFloor.Spot s = i.plan.stairs();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(i.pos(s.x() + dx, DepthFloorBuild.FLOOR_YI, s.z() + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}

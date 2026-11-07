package jp.revati.skyabyss.dungeon.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.BossOpenings;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import jp.revati.skyabyss.entity.foe.RegionBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 地下のダンジョンの主の共通 (docs/design/55 の 2 章「予告と隙のある主 (47)。帯・技名の知らせ」)。
// 技の名前はいつも出す (名づきかどうかによらない)。体力の帯と人数の強さは dungeon/DungeonFloor が持つ。
// 掘り穴・柱など、主の間の印の場所は holes (DungeonFloor が湧かせたときに渡す。卵から出したときは空)
// つっかえ直し (2026-11-06 の声「ダンジョンのボスのムカデ、めっちゃブロックにつっかえる。他のダンジョンのボスもそうかも。体力高いだけのやつになってる」):
//   主の体は大きく (幅 2〜3.6)、道探しは広い体の通れる道を見つけられずに止まり、技は「地面にいて人が見える」ときしか出さないので、
//   柱の陰や段差でつっかえると殴られるだけになっていた。
//   - 段差: 1 ブロックまで歩いて上がる (STEP)
//   - 道が見つからない・STUCK_CHECK ごとに 0.6 も進まないときは、人へまっすぐ歩く
//   - それでも STUCK_LIMIT tick 進まない、または人が BLIND_LIMIT tick 見えないときは unstick: 人のそばの、体が収まって人の見える所へ
//     地を蹴って移る (土煙と音。主ごとに変えられる。大百足は潜る)。UNSTICK_GAP tick に一度まで
// 後半 (2026-11-07 の声「中ダンジョンのボス (ムカデ等々) もまだまだ手直しほしい」):
//   体力が PHASE2 を切ると、吠えて (まわりを押し返す輪。予兆つき) 手下を MINIONS 体呼び、後半に入る。
//   後半は技の間が PHASE2_COOLDOWN 倍になり、主ごとの後半の技が加わる (phase2)。
//   大技のあとは大きな隙 (opening: BossOpenings.open。白い火花と澄んだ音)
// 後半の場の技 (2026-11-08 の声「亀の第3形態がめっちゃ面白い。中ダンジョンのボスもそのくらい派手だと楽しい」):
//   潮殻の心臓の段階3 にならい、主の技に重ねて、部屋じゅうを使う技を HAZARD_GAP ごとに一つ出す (主が技の途中でも出る)。
//   降りそそぎ (人の足もとと、まわりに二つの円。真珠の雨の形) と、回る薙ぎ (主から二本の帯がまわる。月潮の大波の形) を交互に。
//   色と粒は主ごと (骨・潮・水晶・灰)。予告はどれも 1.5 秒以上
public abstract class DungeonBoss extends RegionBoss {
    public static final float PHASE2 = 0.5f;
    public static final int MINIONS = 2;
    public static final float PHASE2_COOLDOWN = 0.7f;
    public static final int RAGE_WARN = 24;
    public static final double RAGE_RADIUS = 7;
    public static final double STEP = 1.1;
    static final int STUCK_CHECK = 10;
    public static final int STUCK_LIMIT = 60;
    public static final int BLIND_LIMIT = 80;
    static final int UNSTICK_GAP = 160;
    // 後半の場の技 (combat/FieldHazards) の間 (tick)。荒ぶると短く
    public static final int HAZARD_GAP = 90;
    public static final int HAZARD_GAP_RAGE = 60;
    // 封じ (四体の主だけ。雷の大事件の大いなるもの (endgame/WorldEventFight) と同じ流れ。2026-11-08 の声
    // 「中ダンジョンのボスの攻撃、雷の大事件で出てくるデカい敵くらいの作り込みがいい」):
    //   体力が SEAL_AT を切ると守りを張り (受ける傷 ×SEALED)、主の間に封じの輪が三つ出る。輪の中に立つと満ちる (一人 1 秒に SEAL_FILL)。
    //   三つ満ちると守りが割れ、膝をつく (崩れ。KNEEL tick)。SEAL_LAST で満ちなければ守りは割れるが荒ぶる。封じのあいだは ADDS_EVERY ごとに手下が来る
    //   体力が RAGE_AT を切ると (または封じに間に合わないと) 荒ぶる: 足が速く、場の技の間が短い
    public static final float[] SEAL_AT = {0.66f, 0.33f};
    public static final float SEALED = 0.1f;
    public static final int SEAL_LAST = 45 * 20;
    public static final double SEAL_RADIUS = 2.6;
    public static final float SEAL_FILL = 0.25f / 20f;
    public static final int ADDS_EVERY = 12 * 20;
    public static final float RAGE_AT = 0.25f;
    public static final int KNEEL = 160;
    static final ResourceLocation RAGE_SPEED = ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID, "dungeon_boss_rage_speed");
    private final jp.revati.skyabyss.combat.FieldHazards field = new jp.revati.skyabyss.combat.FieldHazards(this, this::hazardBlock,
            (attack, ticks) -> cue(bossId(), attack, ticks), this::damage, this::attackSource, this::canHit);
    private final List<Vec3> seals = new ArrayList<>();
    private final List<Float> sealFill = new ArrayList<>();
    private int sealsDone;
    private int sealLeft;
    private int addsWait;
    private boolean enraged;
    private Vec3 home;

    protected final List<Vec3> holes = new ArrayList<>();
    // 後半に入ったか
    protected boolean phase2;
    private Vec3 lastPos;
    private int stuck;
    private int blind;
    private int unstickWait;

    protected DungeonBoss(EntityType<? extends Monster> type, Level level, String animPrefix, Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt,
            Supplier<SoundEvent> death, String... actions) {
        super(type, level, animPrefix, ambient, hurt, death, actions);
        setPersistenceRequired();
        var step = getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && step.getBaseValue() < STEP) {
            step.setBaseValue(STEP);
        }
    }

    @Override
    protected void cue(String boss, String attack, int ticks) {
        BossCue.send(this, boss, attack, ticks);
    }

    public void setHoles(List<Vec3> list) {
        holes.clear();
        holes.addAll(list);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // 体力の割合 (段の変わり目)
    protected float hp() {
        return getHealth() / getMaxHealth();
    }

    // 近づいて止まる距離 (これより遠ければ「寄りたい」)
    protected double reach() {
        return 4.0;
    }

    // 技の名前の知らせに使う主の名 (gui.skyabyss.attack.<boss>.<技>)
    protected abstract String bossId();

    // 後半に呼ぶ手下 (そのダンジョンの道中の敵)。null なら呼ばない
    @Nullable
    protected String minionId() {
        return null;
    }

    public boolean inPhase2() {
        return phase2;
    }

    // 大技のあとの大きな隙
    protected void opening() {
        BossOpenings.open(this, BossOpenings.BIG_OPEN_TICKS);
    }

    // 後半に入る: 吠えて押し返し、手下を呼ぶ
    protected void enterPhase2(LivingEntity target) {
        phase2 = true;
        cooldown = Math.max(cooldown, RAGE_WARN + 16);
        cue(bossId(), "rage", RAGE_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.55f);
        server().sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight(), getZ(), 10, getBbWidth() * 0.4, 0.4, getBbWidth() * 0.4, 0);
        Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), RAGE_RADIUS))
                .floor(getY()).warn(RAGE_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 6, RAGE_RADIUS * 0.3, 0.2, RAGE_RADIUS * 0.3, 0))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 0.5f);
                    Vec3 away = player.position().subtract(position()).normalize();
                    player.knockback(2.0, -away.x, -away.z);
                })
                .start();
        summonMinions(target);
    }

    // ---------------------------------------------------------------- 中ボスの新しい技の部品 (2026-11-08 の声「ミニボス手抜き。動きを作ったり、攻撃要素増やしたり」)
    // 投げ・降らせ: 人の足もとと、まわりに extra 個の円 (半径 radius、予告 warn)。当たると dmg 倍の傷と浮き
    protected void volley(LivingEntity target, String action, int warn, int extra, double radius, float dmg,
                          net.minecraft.world.level.block.state.BlockState look) {
        begin(target, action);
        cue(bossId(), action, warn);
        List<Vec3> spots = new ArrayList<>();
        spots.add(target.position());
        for (int i = 0; i < extra; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 2.5 + random.nextDouble() * 3.5;
            spots.add(target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        var opt = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, look);
        for (Vec3 c : spots) {
            Telegraph.in(server(), TelegraphShape.circle(c.x, c.z, radius)).floor(c.y).warn(warn).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
                        server.sendParticles(opt, c.x, c.y + 0.4, c.z, 24, radius * 0.4, 0.3, radius * 0.4, 0.15);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * dmg);
                        player.setDeltaMovement(player.getDeltaMovement().add(0, 0.5, 0));
                        player.hurtMarked = true;
                    })
                    .start();
        }
    }

    // 跳び込み: 人のいる所に円 (半径 radius、予告 warn)。落ちるときに主がそこへ移り、円の中を打つ。続けて外へ輪の衝撃 (半径 radius 〜 radius+4)
    protected void leapAt(LivingEntity target, String action, int warn, double radius, float dmg) {
        begin(target, action);
        cue(bossId(), action, warn);
        Vec3 at = target.position();
        Telegraph.in(server(), TelegraphShape.circle(at.x, at.z, radius)).floor(at.y).warn(warn).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    Vec3 to = freeSpotNear(target) != null && at.distanceTo(position()) > 30 ? freeSpotNear(target) : at;
                    teleportTo(to.x, to.y, to.z);
                    server.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
                    server.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y + 0.5, to.z, 4, radius * 0.3, 0.2, radius * 0.3, 0);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * dmg);
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.7, 0));
                    player.hurtMarked = true;
                })
                .start();
        Telegraph.in(server(), TelegraphShape.ring(at.x, at.z, radius, radius + 4)).floor(at.y).warn(warn + 8).danger().owner(this).target(target)
                .victims(this::canHit)
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * dmg * 0.6f);
                    Vec3 away = player.position().subtract(at).multiply(1, 0, 1).normalize();
                    player.knockback(1.2, -away.x, -away.z);
                })
                .start();
    }

    // 吹きつけ: 前の扇 (半径 range、開き halfDeg) を active tick のあいだ焼く。首を振る分、二枚を少しずらす
    protected void spray(LivingEntity target, String action, int warn, double range, double halfDeg, int active, float dmg,
                         net.minecraft.core.particles.ParticleOptions look) {
        begin(target, action);
        cue(bossId(), action, warn);
        double base = Telegraph.angleOf(forward());
        for (int k = -1; k <= 1; k += 2) {
            double ang = base + Math.toRadians(halfDeg * 0.5 * k);
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), ang, range, Math.toRadians(halfDeg * 0.6))).floor(getY()).warn(warn + (k > 0 ? 10 : 0))
                    .active(active).danger().owner(this).target(target).victims(this::canHit)
                    .onImpact(server -> {
                        for (double d = 1.5; d <= range; d += 1.2) {
                            server.sendParticles(look, getX() + Math.cos(ang) * d, getY() + 1.0, getZ() + Math.sin(ang) * d, 4, 0.5, 0.3, 0.5, 0.02);
                        }
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * dmg))
                    .start();
        }
    }

    // 回る: 主のまわりの輪 (半径 radius) を、間を空けて times 回打つ
    protected void spinRings(LivingEntity target, String action, int warn, double radius, int times, int gap, float dmg) {
        begin(target, action);
        cue(bossId(), action, warn);
        for (int i = 0; i < times; i++) {
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), radius)).floor(getY()).warn(warn + i * gap).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.0, getZ(), 8, radius * 0.5, 0.2, radius * 0.5, 0))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * dmg);
                        Vec3 away = player.position().subtract(position()).multiply(1, 0, 1).normalize();
                        player.knockback(1.0, -away.x, -away.z);
                    })
                    .start();
        }
    }

    // 手下を呼ぶ (そのダンジョンの道中の敵を MINIONS 体)
    protected void summonMinions(LivingEntity target) {
        String id = minionId();
        if (id == null) {
            return;
        }
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id)).orElse(null);
        for (int i = 0; type != null && i < MINIONS; i++) {
            Vec3 at = target.position();
            Vec3 free = freeSpotNear(target);
            if (free != null) {
                at = free;
            }
            if (type.create(server()) instanceof Mob m) {
                m.moveTo(at.x + (i - 0.5) * 2, at.y, at.z, random.nextFloat() * 360f, 0f);
                m.finalizeSpawn(server(), server().getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                m.addTag("skyabyss_boss_minion");
                if (server().addFreshEntity(m)) {
                    m.setTarget(target);
                    server().sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 16, 0.5, 0.5, 0.5, 0.05);
                }
            }
        }
    }

    @Override
    protected void finish() {
        super.finish();
        if (phase2) {
            cooldown = Math.round(cooldown * PHASE2_COOLDOWN);
        }
    }

    @Override
    protected void customServerAiStep() {
        LivingEntity rageAt = getTarget();
        if (!phase2 && !level().isClientSide && hp() < PHASE2 && rageAt != null && rageAt.isAlive() && !busy() && !isInvisible()) {
            enterPhase2(rageAt);
        }
        super.customServerAiStep();
        if (unstickWait > 0) {
            unstickWait--;
        }
        stepFight();
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || busy() || isInvisible()) {
            stuck = 0;
            blind = 0;
            lastPos = position();
            return;
        }
        if (tickCount % STUCK_CHECK != 0) {
            return;
        }
        boolean far = distanceTo(t) > reach() + 1.5;
        if (lastPos != null && far && position().distanceTo(lastPos) < 0.6) {
            stuck += STUCK_CHECK;
        } else {
            stuck = Math.max(0, stuck - STUCK_CHECK);
        }
        lastPos = position();
        blind = hasLineOfSight(t) ? 0 : blind + STUCK_CHECK;
        // 広い体の通れる道が見つからない・進めないときは、まっすぐ人へ
        if (far && (getNavigation().isDone() || stuck > 0)) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
        }
        if ((stuck >= STUCK_LIMIT || blind >= BLIND_LIMIT) && unstickWait == 0) {
            stuck = 0;
            blind = 0;
            unstickWait = UNSTICK_GAP;
            unstick(t);
        }
    }

    // ---------------------------------------------------------------- 後半の場の技・封じ・荒ぶり
    private void stepFight() {
        if (level().isClientSide || isInvisible() || !isAlive()) {
            return;
        }
        if (home == null) {
            home = position();
        }
        field.tick(phase2 && seals.isEmpty(), enraged ? HAZARD_GAP_RAGE : HAZARD_GAP);
        if (!major()) {
            return;
        }
        LivingEntity t = getTarget();
        if (seals.isEmpty() && sealsDone < SEAL_AT.length && hp() < SEAL_AT[sealsDone] && t != null && !busy()) {
            beginSeal();
        }
        if (!seals.isEmpty()) {
            stepSeal();
        }
        if (!enraged && hp() < RAGE_AT) {
            enrage();
        }
    }

    // 封じと荒ぶりを持つ主か (四体の主。中ボスは場の技だけ)
    protected boolean major() {
        return minionId() != null;
    }

    public boolean sealed() {
        return !seals.isEmpty();
    }

    public boolean enraged() {
        return enraged;
    }

    // 封じの間は、受ける傷が SEALED 倍
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!seals.isEmpty() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= SEALED;
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.6, 0.6, 0.6, 0.2);
            }
        }
        return super.hurt(source, amount);
    }

    void beginSeal() {
        sealLeft = SEAL_LAST;
        addsWait = ADDS_EVERY / 2;
        double turn = random.nextDouble() * Math.PI * 2;
        Vec3 c = home != null ? home : position();
        for (int i = 0; i < 3; i++) {
            double a = turn + i * Math.PI * 2 / 3;
            Vec3 at = sealSpot(c, a);
            seals.add(at);
            sealFill.add(0f);
        }
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.6f);
        server().sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.6, getZ(), 80, 2.0, 1.5, 2.0, 0.05);
        cue(bossId(), "seal", 40);
        for (ServerPlayer p : field.targetsNear()) {
            jp.revati.skyabyss.notice.Notice.alert(p, "message.skyabyss.world_event.seal", getDisplayName());
        }
    }

    // 輪の置き場: 主の間の真ん中から角度 a へ、立てる床のある所 (7〜11 先から近い方へ探す)
    Vec3 sealSpot(Vec3 c, double a) {
        for (double r = 10; r >= 4; r -= 1.5) {
            int x = (int) Math.floor(c.x + Math.cos(a) * r);
            int z = (int) Math.floor(c.z + Math.sin(a) * r);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos at = BlockPos.containing(x, c.y + dy, z);
                if (level().getBlockState(at).getCollisionShape(level(), at).isEmpty() && level().getBlockState(at.above()).getCollisionShape(level(), at.above()).isEmpty()
                        && !level().getBlockState(at.below()).getCollisionShape(level(), at.below()).isEmpty()) {
                    return new Vec3(x + 0.5, at.getY(), z + 0.5);
                }
            }
        }
        return new Vec3(c.x + Math.cos(a) * 5, c.y, c.z + Math.sin(a) * 5);
    }

    void stepSeal() {
        List<ServerPlayer> people = field.targetsNear();
        boolean all = true;
        for (int i = 0; i < seals.size(); i++) {
            Vec3 s = seals.get(i);
            float fill = sealFill.get(i);
            if (fill < 1f) {
                for (ServerPlayer p : people) {
                    if (Math.hypot(p.getX() - s.x, p.getZ() - s.z) <= SEAL_RADIUS && Math.abs(p.getY() - s.y) < 4) {
                        fill = Math.min(1f, fill + SEAL_FILL);
                    }
                }
                if (fill >= 1f) {
                    server().playSound(null, s.x, s.y, s.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2f, 1.2f);
                    server().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, s.x, s.y + 1, s.z, 40, 1.2, 1.0, 1.2, 0.2);
                } else if (tickCount % 10 == 0 && fill > sealFill.get(i)) {
                    server().playSound(null, s.x, s.y, s.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f + fill);
                }
                sealFill.set(i, fill);
            }
            all &= fill >= 1f;
            if (fill < 1f && tickCount % 4 == 0) {
                if (tickCount % 20 == 0) {
                    Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, SEAL_RADIUS)).floor(s.y).warn(0).active(22).safe().start();
                }
                for (double y = 0; y < 1 + 6 * fill; y += 0.5) {
                    server().sendParticles(ParticleTypes.END_ROD, s.x, s.y + y, s.z, 1, 0.05, 0, 0.05, 0);
                }
                Vec3 from = position().add(0, getBbHeight() * 0.6, 0);
                Vec3 d = s.add(0, 1, 0).subtract(from);
                for (int k = 1; k < 10; k++) {
                    Vec3 q = from.add(d.scale(k / 10.0));
                    server().sendParticles(ParticleTypes.ENCHANT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (tickCount % 20 == 0) {
            int full = 0;
            for (float f : sealFill) {
                full += f >= 1f ? 1 : 0;
            }
            for (ServerPlayer p : people) {
                jp.revati.skyabyss.notice.Notice.bar(p, net.minecraft.network.chat.Component.translatable("message.skyabyss.dungeon.seal_left", full, seals.size(), sealLeft / 20));
            }
        }
        if (--addsWait <= 0) {
            addsWait = ADDS_EVERY;
            LivingEntity t = getTarget();
            if (t != null) {
                summonMinions(t);
            }
        }
        if (all) {
            endSeal(true);
        } else if (--sealLeft <= 0) {
            endSeal(false);
        }
    }

    void endSeal(boolean solved) {
        seals.clear();
        sealFill.clear();
        sealsDone++;
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);
        server().sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        if (solved) {
            jp.revati.skyabyss.combat.BreakGauge.solved(this, KNEEL);
            if (!jp.revati.skyabyss.combat.BreakGauge.broken(this)) {
                jp.revati.skyabyss.combat.BreakGauge.add(this, jp.revati.skyabyss.combat.BreakGauge.size(this, jp.revati.skyabyss.combat.BreakGauge.breaks(this)));
            }
            for (ServerPlayer p : field.targetsNear()) {
                jp.revati.skyabyss.notice.Notice.alert(p, "message.skyabyss.world_event.seal_broken", getDisplayName());
            }
        } else {
            for (ServerPlayer p : field.targetsNear()) {
                jp.revati.skyabyss.notice.Notice.warn(p, "message.skyabyss.world_event.seal_failed", getDisplayName());
            }
            enrage();
        }
    }

    void enrage() {
        if (enraged) {
            return;
        }
        enraged = true;
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(RAGE_SPEED)) {
            speed.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RAGE_SPEED, 0.3,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.5f);
        server().sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight(), getZ(), 12, 1.2, 0.5, 1.2, 0);
        for (ServerPlayer p : field.targetsNear()) {
            jp.revati.skyabyss.notice.Notice.warn(p, "message.skyabyss.world_event.rage", getDisplayName());
        }
    }

    // 自己テスト: 場の技を三つとも出す
    void hazardsNow() {
        field.all();
    }

    // 主ごとの色の石 (粒と予告の見た目)
    protected net.minecraft.world.level.block.state.BlockState hazardBlock() {
        return switch (bossId()) {
            case "bone_centipede", "pit_foreman" -> net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState();
            case "shell_octopus", "bell_hermit" -> net.minecraft.world.level.block.Blocks.PRISMARINE.defaultBlockState();
            case "crystal_lizard", "geode_golem" -> net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState();
            case "forge_giant", "ox_smith" -> net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState();
            default -> net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        };
    }

    // つっかえたとき: 人のそばの、体が収まって人の見える所へ地を蹴って移る。見つからなければ何もしない
    protected void unstick(LivingEntity target) {
        Vec3 to = freeSpotNear(target);
        if (to == null) {
            return;
        }
        server().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 12, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.02);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.6f, 0.6f);
        teleportTo(to.x, to.y, to.z);
        getNavigation().stop();
        server().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, to.x, to.y + 0.5, to.z, 16, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.02);
        server().playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 0.7f);
    }

    // 人のまわり (3〜8) で、体が収まり・足もとが固く・人の見える所 (なければ null)
    @Nullable
    public Vec3 freeSpotNear(LivingEntity target) {
        double base = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
        for (double r : new double[] {reach() + 1.0, reach() + 3.0, 3.0, 8.0}) {
            for (int k = 0; k < 12; k++) {
                // 今いる側から順に試す
                double a = base + (k % 2 == 0 ? 1 : -1) * (k / 2) * Math.PI / 6;
                double x = target.getX() + Math.cos(a) * r;
                double z = target.getZ() + Math.sin(a) * r;
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos feet = BlockPos.containing(x, target.getY() + dy, z);
                    if (!level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), net.minecraft.core.Direction.UP)) {
                        continue;
                    }
                    Vec3 at = new Vec3(x, feet.getY(), z);
                    AABB box = getDimensions(getPose()).makeBoundingBox(at);
                    if (level().noCollision(this, box) && !level().containsAnyLiquid(box)
                            && level().clip(new net.minecraft.world.level.ClipContext(at.add(0, getEyeHeight(), 0), target.getEyePosition(),
                                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType()
                                    == net.minecraft.world.phys.HitResult.Type.MISS) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    // 自己テスト用: つっかえの数え
    public int stuckTicks() {
        return stuck;
    }
}

package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.prop.PropFx;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.BossIntro;
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
    // 後半の場の技 (combat/FieldHazards) の間 (tick)
    public static final int HAZARD_GAP = 90;
    // 解き放ち (四体の主だけ): 体力が SURGE_AT を切るたびに吠え、場の技を三つとも一度に放ち、そのあと大きな隙。
    // 封じ・荒ぶりは雷の大事件の大いなるもの固有の仕組みなので持たない (2026-11-08 の声「封じや荒ぶりは大事件の敵固有の物だから、
    // まるまるとってくるのは違う。ああいうくらいの攻撃だと楽しい、というだけ」)
    public static final float[] SURGE_AT = {0.66f, 0.33f};
    public static final int SURGE_WARN = 30;
    private final jp.revati.skyabyss.combat.FieldHazards field = new jp.revati.skyabyss.combat.FieldHazards(this, this::hazardBlock,
            (attack, ticks) -> cue(bossId(), attack, ticks), this::damage, this::attackSource, this::canHit);
    private int surges;
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

    // ---------------------------------------------------------------- 登場 (2026-11-11 の声「最初の登場のアニメーションや、画面にボスの名前表示がない」)
    // 人 (偽の人は数えない) が INTRO_WAKE まで寄ると一度だけ: 主は地の下に隠れ (見えず・傷を受けず・動かない)、画面に「〜が這い出る」と
    // INTRO_RISE tick の数え下ろし、地鳴りと土煙。数え終わると姿を見せて登場の動き (introAnim) と名前の札 (combat/BossIntro)。
    // 動きが終わる (introLength) と戦いが始まる。済んだかは保存する (読み込み直しで繰り返さない)
    public static final double INTRO_WAKE = 22;
    // これより近くに本物の人がいれば、登場までは地の下に潜んで待つ
    public static final double INTRO_LURK = 64;
    public static final int INTRO_RISE = 60;
    public static final int INTRO_CARD = 90;
    private int introTick = -1;
    private boolean introDone;

    // 登場の動き (主ごとに替える)。既定は吠える
    // ダンジョンの中ボス (坑の頭・鐘の隠者・晶洞の巨像・牛の鍛冶) は true: 名前の札は出すが、顔のアップはしない
    protected boolean midBoss() {
        return false;
    }

    // 出方 (combat/BossEmerge の style)。"dig" = 地を掘って這い出る (いつもの)。ほかは姿を見せる時から名前の札まで、その出方で現れる
    protected String entrance() {
        return "dig";
    }

    protected String introAnim() {
        return "roar";
    }

    // 登場の動きの長さ (tick)
    protected int introLength() {
        return 32;
    }

    // 登場の動きのうち、体が地から抜けきって吠える tick (名前の札と大きな土煙はここ)。既定は出た瞬間
    protected int introCardAt() {
        return 0;
    }

    public boolean introRunning() {
        return introTick >= 0;
    }

    // 試験・やり直し用: 登場を済んだことにする
    public void skipIntro() {
        if (introTick >= 0) {
            endIntro();
        }
        introDone = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || introDone || !isAlive()) {
            return;
        }
        if (introTick < 0) {
            // 本物の人 (偽の人・見物の人は数えない) が近いか。近くに誰もいない (自己テストの偽の人だけ) なら何もしない
            double nearest = Double.MAX_VALUE;
            for (ServerPlayer p : server().players()) {
                if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && !p.isSpectator() && p.isAlive()) {
                    nearest = Math.min(nearest, p.distanceToSqr(this));
                }
            }
            if (nearest < INTRO_WAKE * INTRO_WAKE) {
                startIntro();
            } else if (nearest < INTRO_LURK * INTRO_LURK) {
                // まだ遠い: 地の下に潜んで待つ (立ったまま見えていて、寄ると消えて出直すのを防ぐ)
                if (!isInvisible() || !isNoAi()) {
                    setInvisible(true);
                    setNoAi(true);
                    getNavigation().stop();
                }
            }
            return;
        }
        introTick++;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (introTick < INTRO_RISE) {
            if (!"dig".equals(entrance())) {
                return;
            }
            // 地鳴り: 足もとの土と骨が跳ね、低い音がだんだん大きく
            if (introTick % 4 == 0) {
                float k = introTick / (float) INTRO_RISE;
                server().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, hazardBlock()),
                        getX(), getY() + 0.2, getZ(), (int) (10 + 30 * k), 1.2 + 1.5 * k, 0.1, 1.2 + 1.5 * k, 0.15);
            }
            if (introTick % 20 == 0) {
                server().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.6f + introTick / 40f, 0.5f);
            }
            return;
        }
        if (introTick == INTRO_RISE) {
            setInvisible(false);
            // いちばん近い人の方を向いて出る
            ServerPlayer face = null;
            for (ServerPlayer p : server().players()) {
                if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && !p.isSpectator() && p.isAlive()
                        && (face == null || p.distanceToSqr(this) < face.distanceToSqr(this))) {
                    face = p;
                }
            }
            if (face != null) {
                float yaw = (float) (net.minecraft.util.Mth.atan2(face.getZ() - getZ(), face.getX() - getX()) * 57.2957763671875) - 90f;
                setYRot(yaw);
                setYBodyRot(yaw);
                setYHeadRot(yaw);
            }
            playAction(introAnim());
            if (!"dig".equals(entrance())) {
                jp.revati.skyabyss.combat.BossEmerge.visual(this, entrance(), Math.max(20, introCardAt()));
            }
            // 床が割れる
            server().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, hazardBlock()),
                    getX(), getY() + 0.6, getZ(), 60, 1.4, 0.5, 1.4, 0.2);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0f, 0.7f);
        }
        int out = INTRO_RISE + introCardAt();
        if (introTick > INTRO_RISE && introTick < out && introTick % 3 == 0 && "dig".equals(entrance())) {
            // 這い出る間: 体のまわりから土と骨が噴き、掘る音
            server().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, hazardBlock()),
                    getX(), getY() + 0.3, getZ(), 18, 1.6, 0.4, 1.6, 0.18);
            if (introTick % 12 == 0) {
                server().playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.8f, 0.5f);
            }
        }
        if (introTick == out) {
            // 体が抜けきって吠える: 名前の札
            BossIntro.send(this, BossIntro.NAME, bossId(), INTRO_CARD, !midBoss());
            server().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, hazardBlock()),
                    getX(), getY() + 1.0, getZ(), 120, 2.0, 1.2, 2.0, 0.25);
            server().sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 4, 1.5, 0.3, 1.5, 0);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.5f);
            return;
        }
        if (introTick <= out) {
            return;
        }
        if (introTick >= INTRO_RISE + introLength()) {
            endIntro();
            introDone = true;
        }
    }

    private void startIntro() {
        introTick = 0;
        setNoAi(true);
        setInvisible(true);
        setInvulnerable(true);
        getNavigation().stop();
        BossIntro.send(this, BossIntro.RISE, bossId(), INTRO_RISE);
    }

    private void endIntro() {
        introTick = -1;
        setNoAi(false);
        setInvisible(false);
        setInvulnerable(false);
        cooldown = Math.max(cooldown, 10);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("skyabyss_intro_done", introDone || introTick >= 0);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        introDone = tag.getBoolean("skyabyss_intro_done");
        if (introDone && isNoAi() && isInvisible()) {
            // 登場の途中で保存されたもの: 戦える姿へ戻す
            setNoAi(false);
            setInvisible(false);
            setInvulnerable(false);
        }
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
        Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), RAGE_RADIUS)).prop(PropFx.ring(spikeProp(), 10))
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
        // 技の形 (combat/prop): 投げる技は主の口元から弧を描いて飛び、降らせる技は空から落ち、噴き上げる技・雷は打つ瞬間に床から出る
        boolean toss = action.equals("spit") || action.equals("hurl") || action.equals("throw");
        boolean rise = action.equals("erupt") || action.equals("strike");
        String kind = action.equals("strike") ? "lightning" : rise ? jp.revati.skyabyss.combat.prop.AttackProps.spikeKind(look)
                : toss ? jp.revati.skyabyss.combat.prop.AttackProps.tossKind(look) : jp.revati.skyabyss.combat.prop.AttackProps.fallKind(look);
        Vec3 mouth = position().add(forward().scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.7, 0);
        for (Vec3 c : spots) {
            if (toss) {
                jp.revati.skyabyss.combat.prop.AttackProps.toss(server(), kind, this, mouth, c, warn, Math.min(warn, 16), 4f, 1.2f, 14);
            } else if (!rise) {
                jp.revati.skyabyss.combat.prop.AttackProps.fall(server(), kind, this, c, warn, 14, 11f, 1.3f, 14);
            }
            Telegraph.in(server(), TelegraphShape.circle(c.x, c.z, radius)).floor(c.y).warn(warn).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        if (rise) {
                            jp.revati.skyabyss.combat.prop.AttackProps.rise(server, kind, this, c, random.nextFloat() * 360f, kind.equals("lightning") ? 1.0f : 0.9f,
                                    kind.equals("lightning") ? 6 : 18);
                        }
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

    // 技の形 (combat/prop/PropFx) の、この主のダンジョンの属性の突き出る物 (骨・貝・晶・氷・熔岩)
    protected String spikeProp() {
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).toString();
        for (jp.revati.skyabyss.dungeon.Dungeon d : jp.revati.skyabyss.dungeon.Dungeon.values()) {
            if (id.equals(d.boss) || id.equals(d.midBoss)) {
                return switch (d.element) {
                    case TIDE -> "shell_spike";
                    case WIND -> d.id.contains("ice") ? "ice_spike" : "crystal_spike";
                    case ASH -> "lava_pillar";
                    default -> "bone_spike";
                };
            }
        }
        return "bone_spike";
    }

    // 吹きつけ (spray) の飛ぶ物: 火 → 火の玉、雷 → 風の刃 (梟) か晶の棘、墨 (海月の触手の鞭) → なし (触手は体の技)
    @javax.annotation.Nullable
    protected PropFx sprayProp(net.minecraft.core.particles.ParticleOptions look) {
        if (look == ParticleTypes.FLAME || look == ParticleTypes.SOUL_FIRE_FLAME) {
            return PropFx.shots("fireball", 3).size(0.6f);
        }
        if (look == ParticleTypes.ELECTRIC_SPARK) {
            return bossId().contains("owl") ? PropFx.shots("wind_blade", 3) : PropFx.shots("crystal_spike", 3).size(0.6f);
        }
        return null;
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
        Telegraph.in(server(), TelegraphShape.ring(at.x, at.z, radius, radius + 4)).prop(PropFx.ring(spikeProp(), 10)).floor(at.y).warn(warn + 8).danger().owner(this).target(target)
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
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), ang, range, Math.toRadians(halfDeg * 0.6))).prop(sprayProp(look)).floor(getY()).warn(warn + (k > 0 ? 10 : 0))
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
        field.tick(phase2, HAZARD_GAP);
        if (!major()) {
            return;
        }
        LivingEntity t = getTarget();
        if (surges < SURGE_AT.length && hp() < SURGE_AT[surges] && t != null && !busy()) {
            surge(t);
        }
    }

    // 解き放ち: 吠えて (予告 SURGE_WARN)、場の技を三つとも放つ。終わると大きな隙
    void surge(LivingEntity target) {
        surges++;
        cooldown = Math.max(cooldown, SURGE_WARN + 40);
        cue(bossId(), "rage", SURGE_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.6f);
        server().sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0, 0, 0, 0);
        field.all();
        BossOpenings.open(this, BossOpenings.BIG_OPEN_TICKS + SURGE_WARN);
    }

    // 解き放ちを持つ主か (四体の主。中ボスは場の技だけ)
    protected boolean major() {
        return minionId() != null;
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
            case "forge_giant", "ox_smith", "lava_wyrm" -> net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState();
            case "fort_warlord" -> net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState();
            case "temple_keeper" -> net.minecraft.world.level.block.Blocks.PRISMARINE.defaultBlockState();
            case "ice_lord" -> net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState();
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

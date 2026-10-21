package jp.revati.skyabyss.story.ch4.mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import jp.revati.skyabyss.story.ch4.Ch4Setup;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 古い灰被り (docs/design/21 の 6.1。名づき): 消えた炉の坑の主。HP 210 (人数で 1 + 0.6 × (人数 − 1) 倍)、火掻き 12、殻 40、背に煤の釜。
// 灰被り (AshThrall) の殻の決まり (殻のあいだは傷が半分) に、名づきの技 3 つ (どれも Telegraph の予告つき。名づきなので 1 秒以上):
//   火掻き   狙いへ向いた扇 (半径 4.2・左右 50 度) を 1.0 秒 → 振り下ろし 12
//   亀裂     8 秒ごと、部屋の人それぞれの足もとに円 (半径 1.6) が 1.5 秒脈を打つ → 噴いて 7 と火 2 秒 (灰の鍛え手の亀裂の予習)
//   熱の波   殻が割れた瞬間、自分のまわりの輪 (1.2〜5.5) を 1.0 秒 → 6 と押し返し。燃え上がっても燃え尽きない (名づき)
// 煤の砥石で研いだ刃は、殻へ 1.5 倍。落とし物は灰の護り糸 1 (旅の職人が余燼の鼓動守に編む) と煤鉄 2〜3
public class ElderThrall extends AshThrall {
    public static final float HEALTH = 210;
    public static final float DAMAGE = 12;
    public static final float SHELL = 40;
    public static final float FLARED = 15;
    public static final int RAKE_WARN = 20;
    public static final double RAKE_RANGE = 4.2;
    public static final double RAKE_HALF = Math.toRadians(50);
    public static final float RAKE_DAMAGE = 12;
    public static final int CRACK_EVERY = 160;
    public static final int CRACK_WARN = 30;
    public static final double CRACK_RADIUS = 1.6;
    public static final float CRACK_DAMAGE = 7;
    public static final int WAVE_WARN = 20;
    public static final double WAVE_INNER = 1.2;
    public static final double WAVE_OUTER = 5.5;
    public static final float WAVE_DAMAGE = 6;
    public static final double ROOM = 12;
    public static final int CRACK_COLOR = 0xFF5A20;

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.skyabyss.elder_thrall"), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_6);
    private boolean scaled;
    private int crackIn = 100;
    // 自己テスト用: 技の始まった数
    public int rakes;
    public int cracks;
    public int waves;

    public ElderThrall(EntityType<? extends Monster> type, Level level) {
        super(type, level, "elder_thrall", new String[] {"attack", "shell_break", "crack", "wave"});
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createElderAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    public float shellMax() {
        return SHELL;
    }

    @Override
    protected float flaredDamage() {
        return FLARED;
    }

    @Override
    protected int burnoutTicks() {
        return 0;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RakeGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isFlared() && shell() > 0) {
            amount *= Ch4Mobs.shellBonus(source);
        }
        return super.hurt(source, amount);
    }

    boolean canHit(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && canAttack(p);
    }

    // 部屋 (12 ブロック) の中の、狙える人
    List<ServerPlayer> roomPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        if (level() instanceof ServerLevel server) {
            for (ServerPlayer p : Players.snapshot(server.players())) {
                if (canHit(p) && !p.isCreative() && p.distanceToSqr(this) < ROOM * ROOM && Math.abs(p.getY() - getY()) < 5) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    // 人数に応じた体力 (最初の AI tick で。湧かせる側が groupId を入れたあと)
    void scaleToGroup() {
        scaled = true;
        int n;
        if (groupId() != null && level() instanceof ServerLevel server) {
            n = Groups.alive(server, groupId()).size();
        } else {
            n = (int) level().players().stream().filter(p -> !p.isSpectator() && p.distanceToSqr(this) < 24 * 24).count();
        }
        float scale = StoneWhaleHeart.scaleFor(Math.min(n, jp.revati.skyabyss.party.Parties.MAX_MEMBERS));
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && scale > 1f) {
            health.setBaseValue(Math.min(1024, HEALTH * scale));
            setHealth(getMaxHealth());
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!scaled) {
            scaleToGroup();
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) {
            for (ServerPlayer p : Players.snapshot(((ServerLevel) level()).players())) {
                if (p.distanceToSqr(this) < 32 * 32 && GroupOwned.visibleTo(this, p)) {
                    bossBar.addPlayer(p);
                } else {
                    bossBar.removePlayer(p);
                }
            }
        }
        if (getTarget() != null && --crackIn <= 0) {
            crackIn = CRACK_EVERY;
            startCracks();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    // ---- 亀裂: 部屋の人それぞれの足もとに円 1.5 秒 → 噴く ----
    public int startCracks() {
        if (!(level() instanceof ServerLevel server)) {
            return 0;
        }
        int n = 0;
        playAction("crack");
        server.playSound(null, getX(), getY(), getZ(), Ch4Setup.ELDER_CRACK.get(), SoundSource.HOSTILE, 1.4f, 0.8f);
        for (ServerPlayer p : roomPlayers()) {
            double cx = p.getX();
            double cz = p.getZ();
            double y = p.getY();
            if (Growth.discover(p, "ch4_elder_crack")) {
                Notice.narrate(p, "message.skyabyss.ch4g.pit.crack");
            }
            Telegraph.in(server, TelegraphShape.circle(cx, cz, CRACK_RADIUS)).floor(y).warn(CRACK_WARN).color(CRACK_COLOR).owner(this)
                    .victims(this::canHit)
                    .onImpact(lvl -> {
                        lvl.playSound(null, cx, y, cz, Ch4Setup.ELDER_BURST.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
                        lvl.sendParticles(ParticleTypes.LAVA, cx, y + 0.2, cz, 8, CRACK_RADIUS * 0.4, 0.1, CRACK_RADIUS * 0.4, 0.1);
                        lvl.sendParticles(ParticleTypes.FLAME, cx, y + 0.3, cz, 24, CRACK_RADIUS * 0.4, 0.4, CRACK_RADIUS * 0.4, 0.05);
                    })
                    .onHit((lvl, player) -> {
                        player.hurt(attackSource(), CRACK_DAMAGE);
                        player.igniteForSeconds(2);
                    })
                    .start();
            n++;
        }
        cracks++;
        return n;
    }

    // ---- 熱の波: 殻が割れた瞬間、まわりの輪 1.0 秒 → 押し返す ----
    @Override
    protected void onIgnite() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        waves++;
        playAction("wave");
        double cx = getX();
        double cz = getZ();
        double y = getY();
        for (ServerPlayer p : roomPlayers()) {
            Notice.narrate(p, "message.skyabyss.ch4g.pit.wave");
        }
        Telegraph.in(server, TelegraphShape.ring(cx, cz, WAVE_INNER, WAVE_OUTER)).floor(y).warn(WAVE_WARN).danger().owner(this)
                .startSound(Ch4Setup.ELDER_WAVE.get(), 1.4f, 1.0f)
                .victims(this::canHit)
                .onImpact(lvl -> lvl.sendParticles(ParticleTypes.FLAME, cx, y + 0.4, cz, 60, WAVE_OUTER * 0.5, 0.2, WAVE_OUTER * 0.5, 0.08))
                .onHit((lvl, player) -> {
                    player.hurt(attackSource(), WAVE_DAMAGE);
                    Vec3 away = player.position().subtract(cx, y, cz).multiply(1, 0, 1).normalize();
                    player.knockback(1.2, -away.x, -away.z);
                })
                .start();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, Ch4Setup.ASH_WARD_THREAD.get(), 1.0, 1, 1);
        Ch4Mobs.drop(level, this, source, Ch4Setup.SOOT_IRON.get(), 1.0, 2, 3);
    }

    // ---------------------------------------------------------------- 火掻き (扇の予告 1.0 秒 → 振り下ろし)
    static final class RakeGoal extends Goal {
        private final ElderThrall mob;
        private int act;
        private int cooldown;
        private int repath;

        RakeGoal(ElderThrall mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || act > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            act = 0;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (cooldown > 0) {
                cooldown--;
            }
            if (act > 0) {
                mob.getNavigation().stop();
                if (++act > RAKE_WARN + 8) {
                    act = 0;
                    cooldown = 30;
                }
                return;
            }
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            double d = mob.distanceTo(t) - mob.getBbWidth() / 2 - t.getBbWidth() / 2;
            if (d <= RAKE_RANGE - 1.0 && cooldown == 0 && mob.level() instanceof ServerLevel server) {
                mob.getNavigation().stop();
                act = 1;
                mob.rakes++;
                mob.playAction("attack");
                double angle = Telegraph.angleOf(t.getX() - mob.getX(), t.getZ() - mob.getZ());
                float yaw = (float) Math.toDegrees(angle) - 90f;
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                mob.setYHeadRot(yaw);
                double cx = mob.getX();
                double cz = mob.getZ();
                Telegraph.in(server, TelegraphShape.cone(cx, cz, angle, RAKE_RANGE, RAKE_HALF)).floor(mob.getY()).warn(RAKE_WARN).danger().owner(mob)
                        .target(t).startSound(Ch4Setup.ELDER_RAKE.get(), 1.2f, 1.0f)
                        .victims(mob::canHit)
                        .onImpact(lvl -> {
                            lvl.playSound(null, cx, mob.getY(), cz, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2f, 0.5f);
                            lvl.sendParticles(ParticleTypes.LAVA, cx + Math.cos(angle) * 2.5, mob.getY() + 0.2, cz + Math.sin(angle) * 2.5, 5, 0.5, 0.1, 0.5, 0);
                        })
                        .onHit((lvl, player) -> player.hurt(mob.attackSource(), RAKE_DAMAGE))
                        .start();
            } else if (--repath <= 0) {
                repath = 8;
                mob.getNavigation().moveTo(t, 1.0);
            }
        }
    }
}

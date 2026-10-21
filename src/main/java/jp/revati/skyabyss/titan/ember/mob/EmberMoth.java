package jp.revati.skyabyss.titan.ember.mob;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.titan.group.GroupOwned;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 火の粉蛾 (13 の 5.1節): 3〜6 体の群れで飛ぶ小さな蛾。**いちばん熱いものへ寄る**
// (熱の高い人、火の鍵を持つ人、冷え殻、火床など試練の担当が足した熱の源)。人なら触れに来て 3 の傷と熱 +8。
// 叩けば一撃 (体力 8、防御 0)。死ぬと火の粉が散って、近く (3 ブロック) の人に熱 +4。
// 一つの部屋に生きている蛾は 18 体まで (+4/人)。湧かせる側 (試練の担当) は roomCap と countIn で数える
public class EmberMoth extends EmberMob {
    public static final float HEALTH = 8;
    public static final float DAMAGE = 3;
    public static final float ARMOR = 0;
    public static final float TOUCH_HEAT = 8;
    public static final float SPARK_HEAT = 4;
    public static final double SPARK_RADIUS = 3;
    // 寄る範囲、熱の源の評価で距離 1 ブロックあたり引く値
    public static final double LURE_RANGE = 16;
    static final double DIST_COST = 1.5;
    // 火の鍵を持つ人の熱の上乗せ (焼き入れ前は熱い、焼き入れたあとも温かい)
    public static final float FIRE_KEY_HOT_BONUS = 60;
    public static final float FIRE_KEY_BONUS = 30;
    // 冷え殻の熱 (灰の道の殻の上にいる人より少し低い)
    public static final float COOLED_CRUST_HEAT = 45;
    // 触れたあとの間 (tick)。触れたら少し離れて、また来る
    static final int TOUCH_COOLDOWN = 34;
    // 一つの部屋の上限
    public static final int ROOM_CAP = 18;
    public static final int ROOM_CAP_PER_PLAYER = 4;

    // ---------------------------------------------------------------- 熱の源 (試練の担当が足す)
    // 寄る先の候補。heat は 0〜100 ほど (人の熱と同じ目盛り。高いほど寄る)
    public record Lure(Vec3 pos, float heat) {
    }

    // 熱の源を集める (near のまわり range ブロックの中の物を out へ)。火守りの火床 (C3) などがここに足す
    @FunctionalInterface
    public interface LureSource {
        void collect(ServerLevel level, Vec3 near, double range, List<Lure> out);
    }

    private static final List<LureSource> SOURCES = new CopyOnWriteArrayList<>();

    public static void addLureSource(LureSource source) {
        SOURCES.add(source);
    }

    // 部屋の蛾の上限 (一人で 18、一人増えるごとに +4)
    public static int roomCap(int players) {
        return ROOM_CAP + ROOM_CAP_PER_PLAYER * Math.max(0, players - 1);
    }

    // 部屋の中の生きている蛾の数
    public static int countIn(ServerLevel level, AABB room) {
        return level.getEntitiesOfClass(EmberMoth.class, room, EmberMoth::isAlive).size();
    }

    // 部屋の中の、そのグループの生きている蛾の数 (group が null なら全部)
    public static int countIn(ServerLevel level, AABB room, @javax.annotation.Nullable java.util.UUID group) {
        return level.getEntitiesOfClass(EmberMoth.class, room, m -> m.isAlive() && (group == null || group.equals(m.groupId()))).size();
    }

    private int touchCooldown;
    private Vec3 lure;
    private Player lurePlayer;
    private int rethink;
    // 群れの中で一体ずつ違う回り方 (同じ所に重ならない)
    private final float orbitPhase;

    public EmberMoth(EntityType<? extends Monster> type, Level level) {
        super(type, level, EmberIds.EMBER_MOTH, "attack");
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 2;
        this.orbitPhase = level.random.nextFloat() * Mth.TWO_PI;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.FOLLOW_RANGE, LURE_RANGE);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new SeekHeatGoal(this));
    }

    // ---------------------------------------------------------------- 熱の源を選ぶ

    // 人の熱 (寄る強さ)。火の鍵を持っていれば上乗せ
    public static float playerHeat(Player p) {
        float h = EmberHeat.get(p);
        if (holds(p, EmberIds.FIRE_KEY_HOT)) {
            h += FIRE_KEY_HOT_BONUS;
        } else if (holds(p, EmberIds.FIRE_KEY)) {
            h += FIRE_KEY_BONUS;
        }
        return h;
    }

    private static boolean holds(Player p, String itemId) {
        var item = BuiltInRegistries.ITEM.getOptional(EmberIds.id(itemId));
        if (item.isEmpty()) {
            return false;
        }
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item.get())) {
                return true;
            }
        }
        return p.getOffhandItem().is(item.get());
    }

    static boolean targetable(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    // いちばん熱いもの (熱 − 距離 × 1.5 がいちばん大きいもの)。人は熱が 0 でも候補になる。何もなければ null
    void chooseLure() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 here = position();
        double best = -Double.MAX_VALUE;
        Vec3 bestPos = null;
        Player bestPlayer = hottestPlayer(server.players().stream().filter(p -> GroupOwned.mayTarget(this, p)).toList(), here);
        if (bestPlayer != null) {
            best = score(bestPlayer, here);
            bestPos = bestPlayer.position().add(0, bestPlayer.getBbHeight() * 0.6, 0);
        }
        List<Lure> lures = new ArrayList<>();
        collectCrust(server, here, lures);
        for (LureSource s : SOURCES) {
            s.collect(server, here, LURE_RANGE, lures);
        }
        for (Lure l : lures) {
            double score = l.heat() - l.pos().distanceTo(here) * DIST_COST;
            if (score > best) {
                best = score;
                bestPos = l.pos();
                bestPlayer = null;
            }
        }
        lure = bestPos;
        lurePlayer = bestPlayer;
        setTarget(bestPlayer);
    }

    // 人の寄る強さ (熱 − 距離 × 1.5)
    static double score(Player p, Vec3 here) {
        return playerHeat(p) - p.position().distanceTo(here) * DIST_COST;
    }

    // 寄る先の人 (16 ブロック以内で、寄る強さがいちばん大きい人)。いなければ null
    public static Player hottestPlayer(Iterable<? extends Player> players, Vec3 here) {
        Player best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Player p : players) {
            if (!targetable(p) || p.position().distanceToSqr(here) > LURE_RANGE * LURE_RANGE) {
                continue;
            }
            double sc = score(p, here);
            if (sc > bestScore) {
                bestScore = sc;
                best = p;
            }
        }
        return best;
    }

    // 冷え殻 (灰の道。C1 が登録していれば) を近く 6 ブロックで探す
    private void collectCrust(ServerLevel level, Vec3 here, List<Lure> out) {
        var crust = BuiltInRegistries.BLOCK.getOptional(EmberIds.id(EmberIds.COOLED_CRUST));
        if (crust.isEmpty()) {
            return;
        }
        Block block = crust.get();
        BlockPos c = BlockPos.containing(here);
        if (!jp.revati.skyabyss.util.SectionScan.mayContain(level, c.offset(-6, -4, -6), c.offset(6, 2, 6), s -> s.is(block))) {
            return;
        }
        BlockPos nearest = null;
        double nd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-6, -4, -6), c.offset(6, 2, 6))) {
            BlockState st = level.getBlockState(p);
            if (st.is(block)) {
                double d = p.distSqr(c);
                if (d < nd) {
                    nd = d;
                    nearest = p.immutable();
                }
            }
        }
        if (nearest != null) {
            out.add(new Lure(Vec3.atCenterOf(nearest).add(0, 1.2, 0), COOLED_CRUST_HEAT));
        }
    }

    public Vec3 lure() {
        return lure;
    }

    public Player lurePlayer() {
        return lurePlayer;
    }

    // ---------------------------------------------------------------- 触れる・散る

    // 人に触れた: 3 の傷と熱 +8
    public void touch(Player p) {
        if (touchCooldown > 0 || !targetable(p) || !GroupOwned.mayTarget(this, p)) {
            return;
        }
        touchCooldown = TOUCH_COOLDOWN;
        p.hurt(damageSources().mobAttack(this), DAMAGE);
        EmberHeat.add(p, TOUCH_HEAT, EmberHeat.Reason.MOTH);
        playAction("attack");
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.25, 0.4, 0.25, 0.02);
        }
    }

    public int touchCooldown() {
        return touchCooldown;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            sparks(server);
        }
    }

    // 死ぬと火の粉が散る: 3 ブロック以内の人に熱 +4
    void sparks(ServerLevel server) {
        server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 5, 0.3, 0.2, 0.3, 0.05);
        server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 14, 0.5, 0.3, 0.5, 0.06);
        sparkPlayers(server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(SPARK_RADIUS), p -> GroupOwned.mayTarget(this, p)), position());
    }

    // 散った火の粉: at から 3 ブロック以内の人に熱 +4。熱を足した人数を返す
    public static int sparkPlayers(Iterable<? extends Player> players, Vec3 at) {
        int n = 0;
        for (Player p : players) {
            if (targetable(p) && p.position().distanceToSqr(at) <= SPARK_RADIUS * SPARK_RADIUS) {
                EmberHeat.add(p, SPARK_HEAT, EmberHeat.Reason.MOTH);
                n++;
            }
        }
        return n;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (touchCooldown > 0) {
            touchCooldown--;
        }
        if (level().isClientSide) {
            // 腹の熾から落ちる小さな火の粉 (見た目だけ)
            if (random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.3, getZ(), 0, -0.02, 0);
            }
        }
    }

    // ---------------------------------------------------------------- 飛び方

    private static final class SeekHeatGoal extends Goal {
        private final EmberMoth mob;
        private int retreat;

        SeekHeatGoal(EmberMoth mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (--mob.rethink <= 0) {
                mob.rethink = 10 + mob.getRandom().nextInt(6);
                mob.chooseLure();
            }
            Vec3 at = mob.lure;
            float t = (mob.tickCount + mob.getId() * 7) * 0.11f + mob.orbitPhase;
            if (at == null) {
                // 何もなければ、その場でふらふら浮く
                Vec3 here = mob.position();
                mob.getMoveControl().setWantedPosition(here.x + Mth.cos(t) * 1.5, here.y + Mth.sin(t * 0.7f) * 0.4, here.z + Mth.sin(t) * 1.5, 0.6);
                return;
            }
            Player p = mob.lurePlayer;
            if (p != null && p.isAlive()) {
                mob.getLookControl().setLookAt(p, 30f, 30f);
                if (retreat > 0) {
                    retreat--;
                    // 触れたあとは少し離れて回る
                    double r = 2.5;
                    mob.getMoveControl().setWantedPosition(at.x + Mth.cos(t) * r, at.y + 0.8 + Mth.sin(t * 1.3f) * 0.5, at.z + Mth.sin(t) * r, 1.0);
                    return;
                }
                mob.getMoveControl().setWantedPosition(at.x + Mth.cos(t) * 0.3, at.y + Mth.sin(t * 1.7f) * 0.2, at.z + Mth.sin(t) * 0.3, 1.2);
                if (mob.touchCooldown == 0 && mob.getBoundingBox().inflate(0.25).intersects(p.getBoundingBox())) {
                    mob.touch(p);
                    retreat = 20;
                }
                return;
            }
            // 人でない熱の源: そのまわりを回る (触れて傷はない)
            double r = 1.4 + 0.3 * Mth.sin(t * 0.5f);
            mob.getMoveControl().setWantedPosition(at.x + Mth.cos(t) * r, at.y + Mth.sin(t * 1.3f) * 0.4, at.z + Mth.sin(t) * r, 0.8);
        }
    }

    // ---------------------------------------------------------------- 飛ぶ者の決まり

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return EmberMobSetup.MOTH_FLUTTER.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EmberMobSetup.MOTH_DEATH.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EmberMobSetup.MOTH_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.5f;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    // 試練の担当が湧かせるときのための、近くの人の数 (部屋の上限を出す)
    public static int playersNear(ServerLevel level, AABB room) {
        int n = 0;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (targetable(p) && room.contains(p.position())) {
                n++;
            }
        }
        return n;
    }
}

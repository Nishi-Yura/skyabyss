package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 闘 TR7「本物の群れの母」: 群れの母 (大きな磯走り) が、そっくりの偽物を連れている。本物は一体だけ。見分けは足もと:
//  - 本物は足もとに暗い影を落とし、歩いたあとに濡れた足跡 (水色の絨毯。しばらくして消える) を残す。偽物にはどちらもない
//  - 偽物を叩くと弾けて消える (傷も湧きもない)。しばらくしてまた別の所に偽物が現れる。時間が無駄になるだけ
//  - 母を倒せば越える。母は時々、手下の泡吹きを呼ぶ (数に上限)。偽物は人を追うが傷は与えない
//  - 水・ダメージの仕掛けはない
//  - 闘の試練では (32 の 9.5) 母を倒すと、潮招きが砂から現れ、影の潮招きが二体混じる。見分けは母と同じ (本物だけ足もとの影と濡れた足跡)。
//    影は叩くと弾けて、しばらくしてまた別の所から現れる。技は出さない
public final class TideBroodRoom extends RaidRoom {
    static final int HX = 13;
    static final int HZ = 13;
    static final int H = 9;
    static final String DECOY_TAG = "skyabyss_tide_decoy";
    static final String MOTHER_TAG = "skyabyss_tide_mother";
    static final double DECOY_HEALTH = 1000;
    static final int DECOY_RESPAWN = 100;
    static final int PRINT_LIFE = 140;
    static final int MAX_PRINTS = 14;
    static final BlockState PRINT = Blocks.LIGHT_BLUE_CARPET.defaultBlockState();

    private BlockPos origin;
    private RaidKit.Cell cell;
    private Mob mother;
    private final List<Mob> decoys = new ArrayList<>();
    private final List<Long> respawnAt = new ArrayList<>();
    private final Deque<long[]> prints = new ArrayDeque<>();
    private final List<AABB> pillars = new ArrayList<>();
    private int decoyCount;
    private int popped;
    private boolean done;
    private long nextHatch;
    // 中ボスの段の本物の潮招き (null なら母の段)
    private Mob beckoner;
    static final int SHADOWS = 2;

    @Override
    public void build(RaidCtx ctx) {
        origin = ctx.origin();
        ServerLevel level = ctx.level();
        decoyCount = Math.min(6, 2 + ctx.scale() + 1);
        cell = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, TideBuild.PALETTE));
        BlockState[] coral = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
                Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};
        int tries = 0;
        while (pillars.size() < 6 && tries++ < 150) {
            int x = ctx.rng().nextInt(2 * HX - 5) - HX + 2;
            int z = ctx.rng().nextInt(2 * HZ - 13) - HZ + 4;
            if (Math.abs(x) < 4 && (z > HZ - 8 || z < -HZ + 6)) {
                continue;
            }
            AABB box = new AABB(x, 1, z, x + 2, 5, z + 2);
            boolean clash = false;
            for (AABB o : pillars) {
                if (o.inflate(3, 0, 3).intersects(box)) {
                    clash = true;
                }
            }
            if (clash) {
                continue;
            }
            pillars.add(box);
            RaidKit.fill(level, origin.offset(x, 1, z), origin.offset(x + 1, 4, z + 1), coral[ctx.rng().nextInt(coral.length)]);
            RaidKit.fill(level, origin.offset(x, 5, z), origin.offset(x + 1, 5, z + 1), Blocks.DARK_PRISMARINE.defaultBlockState());
        }
        dress(ctx);
    }

    // 飾り (docs/design/39 の 4.2「守る 群れ」): 群れの母の巣 = 北の壁の巻貝の断面 (出口の戸の上)。床は明るい砂岩のまま (水色の足跡が見える)。
    // 柱の頭は珊瑚の頭に。戦う床の真ん中には何も足さない
    private void dress(RaidCtx ctx) {
        TideBuild d = new TideBuild(ctx);
        d.keep(-10, 1, -10, 10, 4, 10);
        for (AABB b : pillars) {
            d.keep((int) b.minX - 1, 1, (int) b.minZ - 1, (int) b.maxX, 7, (int) b.maxZ);
        }
        d.keep(-5, 1, -HZ, 5, H, -HZ + 1);
        d.shell(2);
        d.spiral(true, 0, 6, 4, 2.2);
        for (AABB b : pillars) {
            d.coralHead((int) b.minX, 5, (int) b.minZ);
        }
        d.floorSpiral(0, -3, 7, 2.0, Blocks.CUT_SANDSTONE.defaultBlockState());
        d.hiddenLights(11, 4, false).ceilingCoral(10, 2, H - 2, 3).wallBranches(10, 5, 2).pools(2).shards(4);
        d.finish();
    }

    @Override
    public void start(RaidCtx ctx) {
        done = false;
        popped = 0;
        decoys.clear();
        respawnAt.clear();
        clearPrints(ctx);
        mother = spawnMother(ctx);
        for (int i = 0; i < decoyCount; i++) {
            Mob d = spawnDecoy(ctx);
            if (d != null) {
                decoys.add(d);
            }
        }
        nextHatch = ctx.now() + 240;
    }

    @Override
    public void reset(RaidCtx ctx) {
        ctx.clearMobs();
        decoys.clear();
        respawnAt.clear();
        mother = null;
        beckoner = null;
        clearPrints(ctx);
        done = false;
    }

    @Override
    public void end(RaidCtx ctx) {
        clearPrints(ctx);
    }

    private BlockPos spot(RaidCtx ctx, int zMin, int zMax) {
        for (int k = 0; k < 40; k++) {
            int x = ctx.rng().nextInt(2 * HX - 7) - HX + 4;
            int z = zMin + ctx.rng().nextInt(Math.max(1, zMax - zMin + 1));
            BlockPos at = cell.floor(x, z);
            ServerLevel level = ctx.level();
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.above(2)).isAir()
                    && !level.getBlockState(at.below()).isAir()) {
                return at;
            }
        }
        return cell.floor(0, (zMin + zMax) / 2);
    }

    private Mob spawnMother(RaidCtx ctx) {
        Mob m = TideRaid.spawn(ctx, TideMobSetup.REEF_SCUTTLER.get(), spot(ctx, -HZ + 3, -3));
        if (m != null) {
            m.addTag(MOTHER_TAG);
            m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60 + 30 * ctx.scale());
            m.setHealth(m.getMaxHealth());
        }
        return m;
    }

    private Mob spawnDecoy(RaidCtx ctx) {
        if (beckoner != null) {
            return spawnShadow(ctx);
        }
        Mob d = TideRaid.spawn(ctx, TideMobSetup.REEF_SCUTTLER.get(), spot(ctx, -HZ + 3, 2));
        if (d != null) {
            d.addTag(DECOY_TAG);
            d.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DECOY_HEALTH);
            d.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0);
            d.setHealth((float) DECOY_HEALTH);
        }
        return d;
    }

    // 影の潮招き (技なし・ボスバーなし。叩くと弾ける)
    private Mob spawnShadow(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        jp.revati.skyabyss.titan.tide.back.TideBeckoner b = jp.revati.skyabyss.titan.tide.back.TideBackSetup.TIDE_BECKONER.get().create(level);
        if (b == null) {
            return null;
        }
        BlockPos at = spot(ctx, -HZ + 3, HZ - 4);
        b.addTag(jp.revati.skyabyss.titan.tide.mob.TideMob.TRIAL_TAG);
        b.addTag(DECOY_TAG);
        b.setGroupId(ctx.group());
        b.makeShadow(ctx.standing());
        b.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DECOY_HEALTH);
        b.setHealth((float) DECOY_HEALTH);
        b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.rng().nextFloat() * 360f, 0);
        level.addFreshEntity(b);
        b.emergeAt(level, b.position());
        return ctx.adopt(b);
    }

    private void clearPrints(RaidCtx ctx) {
        for (long[] p : prints) {
            BlockPos at = BlockPos.of(p[0]);
            if (ctx.level().getBlockState(at).is(PRINT.getBlock())) {
                RaidKit.set(ctx.level(), at, Blocks.AIR.defaultBlockState());
            }
        }
        prints.clear();
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done || mother == null) {
            return;
        }
        ServerLevel level = ctx.level();
        long now = ctx.now();
        if (!mother.isAlive()) {
            if (mother.isRemoved() && mother.getHealth() > 0) {
                mother = spawnMother(ctx);
                return;
            }
            finish(ctx);
            return;
        }
        tendDecoys(ctx, now);
        markReal(ctx, mother, now);
        // 手下: 時々、泡吹きを呼ぶ (数に上限)
        if (now >= nextHatch) {
            nextHatch = now + 260;
            int helpers = 0;
            for (Mob m : ctx.aliveMobs()) {
                if (m != mother && !decoys.contains(m)) {
                    helpers++;
                }
            }
            if (helpers < 1 + ctx.scale()) {
                TideRaid.spawn(ctx, TideMobSetup.BUBBLE_PUFFER.get(), spot(ctx, -HZ + 3, 4).above());
            }
        }
        if (now % 40 == 0) {
            TideRaid.purgeStrays(ctx, cell.inner());
        }
    }

    // ---- 中ボスの段: 影の潮招きが二体混じる
    @Override
    public void bossBegin(RaidCtx ctx, Mob boss) {
        beckoner = boss;
        decoys.clear();
        respawnAt.clear();
        clearPrints(ctx);
        for (int i = 0; i < SHADOWS; i++) {
            Mob d = spawnShadow(ctx);
            if (d != null) {
                decoys.add(d);
            }
        }
        ctx.say(Component.translatable("message.skyabyss.raid.TR7.shadows"));
    }

    @Override
    public void bossTick(RaidCtx ctx, Mob boss) {
        beckoner = boss;
        long now = ctx.now();
        tendDecoys(ctx, now);
        markReal(ctx, boss, now);
        if (now % 40 == 0) {
            for (Mob d : decoys) {
                if (d instanceof jp.revati.skyabyss.titan.tide.back.TideBeckoner b) {
                    b.setDevFighters(ctx.standing());
                }
            }
        }
    }

    @Override
    public void bossDone(RaidCtx ctx) {
        for (Mob d : new ArrayList<>(decoys)) {
            if (!d.isRemoved()) {
                pop(ctx, d);
            }
        }
        decoys.clear();
        respawnAt.clear();
        clearPrints(ctx);
    }

    // 偽物: 傷を受けたら弾ける。ほかの理由で消えたものは数に入れなおす
    private void tendDecoys(RaidCtx ctx, long now) {
        ServerLevel level = ctx.level();
        for (int i = decoys.size() - 1; i >= 0; i--) {
            Mob d = decoys.get(i);
            if (d.isRemoved() || !d.isAlive()) {
                decoys.remove(i);
                respawnAt.add(now + DECOY_RESPAWN);
            } else if (d.getHealth() < d.getMaxHealth() - 0.01f) {
                pop(ctx, d);
                decoys.remove(i);
                respawnAt.add(now + DECOY_RESPAWN);
                popped++;
                ctx.say(Component.translatable("message.skyabyss.raid.TR7.decoy"));
            }
        }
        for (int i = respawnAt.size() - 1; i >= 0; i--) {
            if (now >= respawnAt.get(i)) {
                respawnAt.remove(i);
                Mob d = spawnDecoy(ctx);
                if (d != null) {
                    decoys.add(d);
                    level.sendParticles(ParticleTypes.POOF, d.getX(), d.getY() + 0.5, d.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }

    // 本物の印: 足もとの影 (4 tick おき)・足跡 (10 tick おき)
    private void markReal(RaidCtx ctx, Mob mother, long now) {
        ServerLevel level = ctx.level();
        double ring = mother.getBbWidth() > 1.5 ? mother.getBbWidth() * 0.6 : 0.8;
        if (now % 4 == 0) {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                level.sendParticles(TideRaid.dust(0.04f, 0.04f, 0.1f, 2.2f), mother.getX() + Math.cos(a) * ring, mother.getY() + 0.06, mother.getZ() + Math.sin(a) * ring, 1, 0.02, 0, 0.02, 0);
            }
        }
        if (now % 10 == 0) {
            BlockPos at = mother.blockPosition();
            if (mother.onGround() && level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
                RaidKit.set(level, at, PRINT);
                prints.add(new long[] {at.asLong(), now + PRINT_LIFE});
            } else if (level.getBlockState(at).is(PRINT.getBlock())) {
                // 立ち止まっているあいだは、同じ足跡の寿命を延ばすだけ
                for (long[] p : prints) {
                    if (p[0] == at.asLong()) {
                        p[1] = now + PRINT_LIFE;
                    }
                }
            }
        }
        while (!prints.isEmpty() && (prints.size() > MAX_PRINTS || prints.peekFirst()[1] <= now)) {
            long[] p = prints.pollFirst();
            BlockPos at = BlockPos.of(p[0]);
            if (level.getBlockState(at).is(PRINT.getBlock())) {
                RaidKit.set(level, at, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private void pop(RaidCtx ctx, Mob d) {
        ServerLevel level = ctx.level();
        level.sendParticles(ParticleTypes.POOF, d.getX(), d.getY() + 0.6, d.getZ(), 14, 0.4, 0.4, 0.4, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, d.getX(), d.getY() + 0.6, d.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, d.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.2f, 1.4f);
        d.discard();
    }

    private void finish(RaidCtx ctx) {
        done = true;
        for (Mob d : new ArrayList<>(decoys)) {
            if (!d.isRemoved()) {
                pop(ctx, d);
            }
        }
        decoys.clear();
        respawnAt.clear();
        clearPrints(ctx);
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.TR7.find";
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.TR7.mother";
    }

    @Override
    public int secondary() {
        return mother == null ? 0 : done ? 0 : Math.max(0, (int) Math.ceil(mother.getHealth()));
    }

    @Override
    public int secondaryMax() {
        return mother == null ? 0 : (int) mother.getMaxHealth();
    }

    // ---- 試験用
    public Mob mother() {
        return mother;
    }

    public List<Mob> decoys() {
        return decoys;
    }

    public int decoyTarget() {
        return decoyCount;
    }

    public int popped() {
        return popped;
    }

    public int pendingRespawns() {
        return respawnAt.size();
    }

    public int printCount() {
        return prints.size();
    }
}

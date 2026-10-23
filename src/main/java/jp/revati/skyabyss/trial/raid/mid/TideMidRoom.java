package jp.revati.skyabyss.trial.raid.mid;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.tide.back.TideBackSetup;
import jp.revati.skyabyss.titan.tide.back.TideBeckoner;
import jp.revati.skyabyss.titan.tide.heart.TideLoot;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 潮殻の中ボス「潮招き」の間 (元は群れの干潟。砂の床・岩の小島・招き潮で満ちる)。49 × 37、高さ 14。
// 準備のあと潮招きが真ん中の砂から現れる (巣を壊す前の段はない)。招き潮は間の砂の上だけを 8 秒 2 マス満たし (岩の上は乾いたまま)、
// 怒りの咆哮で磯走りが四隅から出る。体力は 360、挑む人数の倍率で受ける傷を割る (一人なら 1 倍)。倒すと越える
public final class TideMidRoom extends MidRoom implements TideBeckoner.Flats {
    public static final String ID = "TRM";
    static final int HX = 24;
    static final int HZ = 18;
    static final int HEIGHT = 14;
    static final int FLOOD = 2;
    // 岩の小島 (3 × 3、床の上 2。招き潮のあいだ立てる所)
    static final int[][] ROCKS = {{-14, -9}, {14, -9}, {-16, 6}, {16, 6}, {0, -13}, {-6, 11}, {7, 11}};
    // 元の値 (TideFlats の潮招きの報い)
    static final int XP = 130;
    static final int FIRST_BONUS = 60;
    static final String SIGNAL = "tide_beckoner_stilled";

    @Nullable
    private RaidCtx ctx;
    private int warn;
    private int flood;

    @Override
    public void build(RaidCtx ctx) {
        this.ctx = ctx;
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, HEIGHT, RaidKit.TIDE));
        ServerLevel level = ctx.level();
        BlockState sand = Blocks.SAND.defaultBlockState();
        for (int x = -HX + 1; x <= HX - 1; x++) {
            for (int z = -HZ + 1; z <= HZ - 1; z++) {
                RaidKit.set(level, ctx.local(x, 0, z), (x * 7 + z * 3) % 11 == 0 ? Blocks.SANDSTONE.defaultBlockState() : sand);
            }
        }
        for (int[] r : ROCKS) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 1; y <= FLOOD; y++) {
                        RaidKit.set(level, ctx.local(r[0] + dx, y, r[1] + dz), y == FLOOD ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                    }
                }
            }
        }
        RaidKit.restInArena(ctx, 0, HZ - 2);
    }

    @Override
    @Nullable
    protected Mob spawnBoss(RaidCtx ctx) {
        this.ctx = ctx;
        TideBeckoner boss = TideBackSetup.TIDE_BECKONER.get().create(ctx.level());
        if (boss == null) {
            return null;
        }
        boss.addTag(TideMob.TRIAL_TAG);
        boss.setGroupId(ctx.group());
        boss.setChallengers(Math.max(1, Math.min(Parties.MAX_MEMBERS, ctx.scale())));
        boss.bindFlats(this);
        Vec3 home = home(ctx);
        boss.moveTo(home.x, home.y, home.z, 180f, 0);
        ctx.level().addFreshEntity(boss);
        boss.emergeAt(ctx.level(), home);
        WhaleMidRoom.say(ctx, "raid.skyabyss.TRM.begin");
        return ctx.adopt(boss);
    }

    @Override
    protected Vec3 home(RaidCtx ctx) {
        return Vec3.atBottomCenterOf(ctx.cell().floor(0, -3));
    }

    @Override
    protected void tickFight(RaidCtx ctx) {
        if (warn > 0) {
            warn--;
        }
        if (flood > 0 && --flood == 0) {
            setWater(ctx, false);
        }
    }

    @Override
    protected void resetFixtures(RaidCtx ctx) {
        warn = 0;
        if (flood > 0) {
            flood = 0;
        }
        setWater(ctx, false);
    }

    // 招き潮の水 (砂の上の 2 マス。岩の上は乾いたまま)
    private void setWater(RaidCtx ctx, boolean on) {
        ServerLevel level = ctx.level();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -HX; x <= HX; x++) {
            for (int z = -HZ; z <= HZ; z++) {
                if (rock(x, z)) {
                    continue;
                }
                for (int y = 1; y <= FLOOD; y++) {
                    BlockPos p = ctx.local(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (on ? s.isAir() : s.is(Blocks.WATER)) {
                        level.setBlock(p, on ? water : air, 2);
                    }
                }
            }
        }
    }

    private static boolean rock(int x, int z) {
        for (int[] r : ROCKS) {
            if (Math.abs(x - r[0]) <= 1 && Math.abs(z - r[1]) <= 1) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 干潟の代わり (TideBeckoner.Flats)
    @Override
    public boolean beckoning() {
        return flood > 0;
    }

    @Override
    public boolean flooded(ServerLevel level) {
        return flood > 0;
    }

    @Override
    public void warnBeckon(ServerLevel level, int ticks) {
        warn = ticks;
        if (ctx != null) {
            WhaleMidRoom.say(ctx, "raid.skyabyss.TRM.beckon");
        }
    }

    @Override
    public void beckon(ServerLevel level, int ticks) {
        warn = 0;
        if (ctx != null) {
            flood = ticks;
            setWater(ctx, true);
        }
    }

    @Override
    public List<ServerPlayer> fighters(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        if (ctx == null) {
            return out;
        }
        AABB inner = ctx.cell().inner().inflate(0, 2, 0);
        for (ServerPlayer p : ctx.players()) {
            if (p.isAlive() && !p.isSpectator() && !Downed.isDowned(p) && inner.contains(p.position())) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public void roarSpawn(ServerLevel level, TideBeckoner boss) {
        if (ctx == null) {
            return;
        }
        int[][] corners = {{-HX + 3, -HZ + 3}, {HX - 3, -HZ + 3}, {-HX + 3, HZ - 3}, {HX - 3, HZ - 3}};
        for (int[] c : corners) {
            Mob m = TideMobSetup.REEF_SCUTTLER.get().create(level);
            if (m == null) {
                continue;
            }
            BlockPos at = ctx.cell().floor(c[0], c[1]);
            m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
            m.addTag(TideMob.TRIAL_TAG);
            if (m instanceof jp.revati.skyabyss.titan.group.GroupOwned owned) {
                owned.setGroupId(ctx.group());
            }
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
            level.addFreshEntity(m);
            ctx.adopt(m);
        }
    }

    @Override
    public void defeated(ServerLevel level, TideBeckoner boss) {
        if (ctx != null) {
            onBossDeath(ctx, boss);
        }
    }

    @Override
    public AABB arena() {
        return ctx == null ? new AABB(0, 0, 0, 0, 0, 0) : ctx.cell().inner().inflate(0, 4, 0);
    }

    // ---------------------------------------------------------------- 報い (元の干潟の潮招きと同じ)
    @Override
    protected void reward(RaidCtx ctx, Set<UUID> ids) {
        ServerLevel level = ctx.level();
        MinecraftServer server = level.getServer();
        RandomSource r = level.random;
        List<ServerPlayer> present = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            List<ItemStack> items = new ArrayList<>();
            if (r.nextFloat() < 0.60f) {
                items.add(new ItemStack(TideMobSetup.TIDE_SHELL_SHARD.get(), 2 + r.nextInt(3)));
            }
            if (r.nextFloat() < 0.35f) {
                ResourceLocation rl = ResourceLocation.tryParse(TideLoot.RESONANCE_SHARD);
                if (rl != null) {
                    BuiltInRegistries.ITEM.getOptional(rl).ifPresent(i -> items.add(new ItemStack(i, 1 + r.nextInt(2))));
                }
            }
            if (r.nextFloat() < 0.20f) {
                items.add(new ItemStack(TideMobSetup.TIDE_DRIED_SHELL.get(), 1 + r.nextInt(2)));
            }
            items.addAll(jp.revati.skyabyss.titan.tide.gear.TideGearLoot.rollBeckoner(server, id, r));
            int held = TitanLoot.handOver(server, id, p, items, 40);
            if (p != null) {
                present.add(p);
                int amount = (int) Math.round(XP * GrowthRules.levelFalloff(Growth.get(p).level, TideLoot.TIDE_LEVEL));
                if (Growth.discover(p, "first:" + SkyAbyss.MODID + ":tide_beckoner")) {
                    amount += FIRST_BONUS;
                }
                Growth.addXp(p, amount, "entity.skyabyss.tide_beckoner");
                jp.revati.skyabyss.quest.Quests.signal(p, SIGNAL);
                Notice.unlock(p, "message.skyabyss.tide.beckoner_defeated");
                if (!items.isEmpty()) {
                    Notice.send(p, TitanLoot.rewardLine(items));
                }
                TitanLoot.tellHeld(p, held);
            }
        }
        double rate = Parties.shareRate(server);
        for (ServerPlayer mate : Parties.absentMates(level, present)) {
            Growth.addXp(mate, (int) Math.round(XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, TideLoot.TIDE_LEVEL)),
                    "growth.skyabyss.reason.party");
        }
        jp.revati.skyabyss.titan.group.FightBadges.onVictory(level, boss, ids, 0);
    }

    @Override
    public String objectiveKey() {
        return flood > 0 || warn > 0 ? "raid.skyabyss.TRM.rocks" : "raid.skyabyss.TRM.defeat";
    }

    @Override
    public int timerTicks() {
        return flood > 0 ? flood : -1;
    }
}

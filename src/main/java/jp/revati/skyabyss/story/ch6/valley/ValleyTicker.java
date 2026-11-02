package jp.revati.skyabyss.story.ch6.valley;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.DustCrawler;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.ShadowEmbracer;
import jp.revati.skyabyss.story.ch6.mob.ShadowStitcher;
import jp.revati.skyabyss.story.ch6.mob.ShellHare;
import jp.revati.skyabyss.town.TownGuestEntity;
import jp.revati.skyabyss.town.TownSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 新月の谷の見張り (docs/design/46 の 4.3)。月の世界のサーバーの tick で:
//   10 tick ごと  星灯りの時間切れ・影の橋の足場 (星灯りの中と、位相の合わない脇の列は消える)
//   20 tick ごと  裂け目に落ちた人を野営へ戻す・敵を湧かす (人ごと)・落ちた星の穴の巣の兎・前のループの自分を立たせる・主の間の片付け
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ValleyTicker {
    public static final String PAST_SELF = "moon_past1";
    static final int CAP = 5;
    static final int FALL_Y = -44;
    static final double CAMP_SAFE = 16;
    private static List<BlockPos> bridges;
    private static long nestAt = -10000;

    private ValleyTicker() {
    }

    public static boolean built(ServerLevel level) {
        return MoonWorld.valleyBuilt(level.getServer());
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL || !built(level)) {
            return;
        }
        long t = level.getGameTime();
        if (t % 10 == 0) {
            StarLamps.tick(level);
            bridges(level);
        }
        if (t % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator()) {
                    continue;
                }
                if (p.getY() < FALL_Y) {
                    fell(p);
                    continue;
                }
                spawn(level, p);
                nest(level, p);
                pastSelf(level, p);
            }
            ValleyArena.tick(level);
        }
    }

    // ---------------------------------------------------------------- 影の橋
    static List<BlockPos> bridgeBlocks(ServerLevel level) {
        if (bridges == null) {
            List<BlockPos> out = new ArrayList<>();
            BlockPos n = MoonValley.mark("bridge_n");
            BlockPos s = MoonValley.mark("bridge_s");
            int y = MoonValley.mark("shard_bridge").getY() - 1;
            for (int x = n.getX() - 1; x <= n.getX() + 1; x++) {
                for (int z = Math.min(n.getZ(), s.getZ()); z <= Math.max(n.getZ(), s.getZ()); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).getBlock() instanceof ShadowBridgeBlock) {
                        out.add(p);
                    }
                }
            }
            bridges = out;
        }
        return bridges;
    }

    static void bridges(ServerLevel level) {
        if (level.getNearestPlayer(MoonValley.mark("shard_bridge").getX(), 0, MoonValley.mark("shard_bridge").getZ(), 64, false) == null) {
            return;
        }
        updateBridges(level);
    }

    // 人がいなくても決め直す (自己テスト)
    static void updateBridges(ServerLevel level) {
        boolean nm = MoonMob.newMoon(level);
        boolean faded = false;
        for (BlockPos p : bridgeBlocks(level)) {
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof ShadowBridgeBlock)) {
                continue;
            }
            boolean want = !StarLamps.inLight(level, p.above()) && (!st.getValue(ShadowBridgeBlock.WIDE) || nm);
            if (st.getValue(ShadowBridgeBlock.SOLID) != want) {
                level.setBlock(p, st.setValue(ShadowBridgeBlock.SOLID, want), 3);
                faded |= !want;
            }
        }
        if (faded) {
            level.playSound(null, MoonValley.mark("shard_bridge"), ValleyBlocks.SHADOW_BRIDGE_FADE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    // ---------------------------------------------------------------- 裂け目に落ちた
    static void fell(ServerPlayer p) {
        BlockPos camp = MoonValley.mark("camp");
        p.teleportTo(p.serverLevel(), camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5, p.getYRot(), 0f);
        p.fallDistance = 0;
        p.hurt(p.damageSources().fellOutOfWorld(), 4f);
        Notice.narrate(p, "message.skyabyss.moon.chasm_fall");
    }

    // ---------------------------------------------------------------- 湧き
    static boolean onFloor(BlockPos p) {
        return MoonValley.inside(p) && p.getY() < MoonValley.mark("arena_entry").getY() - 6;
    }

    static void spawn(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (!onFloor(at) || at.closerThan(MoonValley.mark("camp"), CAMP_SAFE) || level.getGameTime() % 40 != 0) {
            return;
        }
        int near = level.getEntitiesOfClass(MoonMob.class, p.getBoundingBox().inflate(40, 20, 40),
                e -> e.isAlive() && !(e instanceof ShadowEmbracer) && !e.getTags().contains(ValleyArena.TAG)).size();
        if (near >= CAP) {
            return;
        }
        RandomSource r = level.random;
        boolean byChasm = at.closerThan(MoonValley.mark("shard_bridge"), 20);
        float roll = r.nextFloat();
        EntityType<? extends MoonMob> type = byChasm ? (roll < 0.6f ? MoonMobsSetup.SHADOW_STITCHER.get() : MoonMobsSetup.DUST_CRAWLER.get())
                : roll < 0.5f ? MoonMobsSetup.DUST_CRAWLER.get() : roll < 0.75f ? MoonMobsSetup.SHADOW_STITCHER.get() : MoonMobsSetup.SHELL_HARE.get();
        BlockPos spot = spot(level, at, r, type == MoonMobsSetup.SHADOW_STITCHER.get());
        if (spot != null) {
            put(level, type, spot);
        }
    }

    static BlockPos spot(ServerLevel level, BlockPos near, RandomSource r, boolean dark) {
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 12 + r.nextDouble() * 12;
            int x = near.getX() + (int) Math.round(Math.cos(a) * d);
            int z = near.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int y = near.getY() + 6; y >= near.getY() - 10; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(),
                        net.minecraft.core.Direction.UP)) {
                    if (!onFloor(p) || p.closerThan(MoonValley.mark("camp"), CAMP_SAFE)) {
                        break;
                    }
                    if (dark && (StarLamps.inLight(level, p) || level.getBrightness(LightLayer.BLOCK, p) >= 8)) {
                        break;
                    }
                    return p;
                }
            }
        }
        return null;
    }

    static MoonMob put(ServerLevel level, EntityType<? extends MoonMob> type, BlockPos at) {
        MoonMob m = type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        boolean nm = MoonMob.newMoon(level);
        if (m instanceof DustCrawler) {
            m.tuneMoon(0, nm, false, DustCrawler.HP, DustCrawler.DMG);
        } else if (m instanceof ShellHare) {
            m.tuneMoon(0, nm, false, ShellHare.HP, ShellHare.DMG);
        } else if (m instanceof ShadowStitcher) {
            m.tuneMoon(0, nm, false, ShadowStitcher.HP, ShadowStitcher.DMG);
        }
        level.addFreshEntity(m);
        if (m instanceof ShellHare h) {
            HARES.add(h);
        }
        return m;
    }

    // 谷で湧かせた兎 (落ちた星の穴の巣の見張り。区画の見え方に依らず数える)
    static final List<ShellHare> HARES = new ArrayList<>();

    static boolean hareNear(ServerLevel level, BlockPos at, double r) {
        HARES.removeIf(h -> h.isRemoved() || !h.isAlive());
        for (ShellHare h : HARES) {
            if (h.level() == level && h.position().distanceToSqr(at.getCenter()) <= r * r) {
                return true;
            }
        }
        return false;
    }

    // 落ちた星の穴の巣: 近くに来た人がいて、兎が少なく、払われてから 30 秒たっていれば、巣の印に兎を戻す
    static void nest(ServerLevel level, ServerPlayer p) {
        BlockPos hole = MoonValley.mark("shard_hole");
        if (p.blockPosition().distSqr(new BlockPos(hole.getX(), p.getBlockY(), hole.getZ())) > 26 * 26) {
            return;
        }
        int hares = level.getEntitiesOfClass(ShellHare.class, new AABB(hole).inflate(20, 30, 20), e -> e.isAlive()).size();
        if (hares > 0) {
            nestAt = level.getGameTime();
            return;
        }
        if (level.getGameTime() - nestAt < 600) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            put(level, MoonMobsSetup.SHELL_HARE.get(), MoonValley.mark("nest_" + i));
        }
        nestAt = level.getGameTime();
    }

    // ---------------------------------------------------------------- 前のループの自分 (野営。頼みが済むまで)
    static void pastSelf(ServerLevel level, ServerPlayer p) {
        BlockPos at = MoonValley.mark("past_self");
        if (!p.blockPosition().closerThan(at, 48)) {
            return;
        }
        List<TownGuestEntity> near = level.getEntities(TownSetup.GUEST.get(), new AABB(at).inflate(24), e -> e.isAlive() && PAST_SELF.equals(e.guestId()));
        boolean gone = level.players().stream().filter(q -> q.blockPosition().closerThan(at, 48))
                .allMatch(q -> ValleyPuzzles.stage(q) >= ValleyPuzzles.stageIndex("promise") + 1);
        if (gone) {
            near.forEach(TownGuestEntity::discard);
            return;
        }
        if (!near.isEmpty()) {
            for (int i = 1; i < near.size(); i++) {
                near.get(i).discard();
            }
            return;
        }
        TownGuestEntity e = TownSetup.GUEST.get().create(level);
        if (e == null) {
            return;
        }
        e.setGuest(PAST_SELF);
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 140f, 0f);
        e.setHome(at);
        level.addFreshEntity(e);
    }
}

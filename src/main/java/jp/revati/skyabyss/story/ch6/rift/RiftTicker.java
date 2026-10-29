package jp.revati.skyabyss.story.ch6.rift;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.RiftArmor;
import jp.revati.skyabyss.story.ch6.mob.RiftSerpent;
import jp.revati.skyabyss.story.ch6.mob.RiftWing;
import jp.revati.skyabyss.story.ch6.mob.WaningBlade;
import jp.revati.skyabyss.story.ch6.valley.ShadowBridgeBlock;
import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import jp.revati.skyabyss.town.TownGuestEntity;
import jp.revati.skyabyss.town.TownSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 下弦の淵の見張り (docs/design/46 の 4.6)。月の世界のサーバーの tick で:
//   毎 tick       敵の残像 (RiftRemnants)
//   2 tick ごと   残像の板 (人か自分の残像が立つと踏まれる) と二つの板の門 (二枚とも踏まれると開き、離れても 3 秒は開いたまま)
//   4 tick ごと   影の橋 (星灯りの 6 の内は消える)
//   20 tick ごと  淵へ落ちた人を戻す・門を通らずに主の間に入った人を戻す・敵を湧かす (人ごと)・残り影の溜まり・前のループの自分 (四人目)・
//                 主の間の片付け・鏡の片付け
//   落ちる傷      淵の中では、どこから落ちても傷 4 まで
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RiftTicker {
    public static final String PAST_SELF = "moon_past4";
    public static final String POOL_TAG = "skyabyss_rift_pool_";
    static final int CAP = 5;
    static final double CAMP_SAFE = 18;
    static final float FALL_CAP = 4f;
    static final int DOOR_HOLD = 60;
    static final double POOL_REACH = 12;
    static final int POOL_EVERY = 120;
    private static long doorUntil;
    private static boolean doorOpen;

    private RiftTicker() {
    }

    public static boolean built(ServerLevel level) {
        return MoonWorld.riftBuilt(level.getServer());
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL || !built(level)) {
            return;
        }
        RiftRemnants.tick(level);
        long gt = level.getGameTime();
        if (gt % 2 == 0) {
            plates(level);
        }
        if (gt % 4 == 0) {
            bridges(level, false);
        }
        if (gt % 20 != 0) {
            return;
        }
        RiftEcho.sweep(level);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !MoonRift.inside(p.blockPosition())) {
                continue;
            }
            if (fell(p)) {
                continue;
            }
            if (guardArena(p)) {
                continue;
            }
            spawn(level, p);
            pastSelf(level, p);
        }
        pools(level);
        RiftArena.tick(level);
    }

    // ---------------------------------------------------------------- 落ちる傷 (淵の中では 4 まで)
    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != MoonWorld.LEVEL || !MoonRift.inside(p.blockPosition())) {
            return;
        }
        double safe = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        if (event.getDistance() > safe + FALL_CAP) {
            event.setDistance((float) (safe + FALL_CAP));
            event.setDamageMultiplier(1.0f);
        }
    }

    // ---------------------------------------------------------------- 落ちた人を戻す
    //   淵へ落ちた (帯の上で縁より 8 低い。回廊の中は棚より 6 低い): 一番近い場所の入口へ (傷 4)。覚えている残像はそこで覚え終わる
    //   主の間に下りた人 (札あり) が床から落ちた (床より 6 低い): 主の間の入口 (門を通れない人は門の前)
    static boolean fell(ServerPlayer p) {
        Vec3 at = p.position();
        BlockPos to = null;
        if (p.getTags().contains(RiftArena.TAG)) {
            BlockPos c = MoonRift.mark("arena_center");
            if (p.getY() < c.getY() - 6) {
                p.removeTag(RiftArena.TAG);
                to = RiftPuzzles.mayCross(p) ? MoonRift.mark("arena_entry") : MoonRift.mark("gate_front");
            }
        } else if (MoonRift.inHall(at)) {
            if (p.getY() < MoonRift.mark("hall_floor").getY() - 6) {
                to = MoonRift.mark("hall_entry");
            }
        } else if (MoonRift.overChasm(at) && p.getY() < MoonRift.FALL_Y && !onBridge(at) && !MoonRift.inArenaZone(at)) {
            to = MoonRift.nearestEntry(at);
        }
        if (to == null) {
            return false;
        }
        RiftEcho.stop(p, "message.skyabyss.moon.echo.stored_fall");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.fallDistance = 0;
        p.hurt(p.damageSources().fellOutOfWorld(), FALL_CAP);
        Notice.narrate(p, "message.skyabyss.moon.rift_fall");
        return true;
    }

    // 門から主の間へ下りる石の段の橋の上
    static boolean onBridge(Vec3 at) {
        BlockPos g = MoonRift.mark("rift_gate");
        BlockPos e = MoonRift.mark("arena_entry");
        return Math.abs(at.z - (g.getZ() + 0.5)) < 3 && at.x > g.getX() - 1 && at.x < e.getX() + 1 && at.y > e.getY() - 4;
    }

    // 主の間に、門を通らずに入った人 (縁から月の跳びで跳び下りた人) は門の前へ戻す。true = 戻した
    public static boolean guardArena(ServerPlayer p) {
        BlockPos c = MoonRift.mark("arena_center");
        if (!MoonRift.inArenaZone(p.position()) || p.getY() < c.getY() - 1.5 || p.getY() > c.getY() + 8) {
            return false;
        }
        if (RiftPuzzles.mayCross(p)) {
            p.addTag(RiftArena.TAG);
            return false;
        }
        BlockPos to = MoonRift.mark("gate_front");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 90f, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.fallDistance = 0;
        Notice.narrate(p, "message.skyabyss.moon.rift_barred");
        return true;
    }

    // ---------------------------------------------------------------- 残像の板と二つの板の門
    public static boolean pressed(ServerLevel level, BlockPos plate) {
        AABB box = new AABB(plate.getX() + 0.1, plate.getY(), plate.getZ() + 0.1, plate.getX() + 0.9, plate.getY() + 0.7, plate.getZ() + 0.9);
        if (!level.getEntitiesOfClass(ServerPlayer.class, box, e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
            return true;
        }
        return !level.getEntitiesOfClass(EchoShade.class, box, e -> e.isAlive() && !e.chasing()).isEmpty();
    }

    static void plates(ServerLevel level) {
        boolean a = false;
        boolean b = false;
        for (String k : List.of("camp_plate", "door_plate_a", "door_plate_b")) {
            BlockPos pos = MoonRift.mark(k);
            BlockState st = level.getBlockState(pos);
            if (!(st.getBlock() instanceof EchoPlateBlock)) {
                continue;
            }
            boolean on = pressed(level, pos);
            if (st.getValue(EchoPlateBlock.PRESSED) != on) {
                level.setBlock(pos, st.setValue(EchoPlateBlock.PRESSED, on), 3);
                if (on) {
                    level.playSound(null, pos, RiftBlocks.PLATE_ON.get(), SoundSource.BLOCKS, 0.8f, 1.2f);
                }
            }
            a |= on && k.equals("door_plate_a");
            b |= on && k.equals("door_plate_b");
        }
        long gt = level.getGameTime();
        if (a && b) {
            doorUntil = gt + DOOR_HOLD;
        }
        boolean want = gt < doorUntil;
        if (want != doorOpen) {
            door(level, want);
        }
    }

    public static boolean doorOpen() {
        return doorOpen;
    }

    // 格子の門 (窪の口、幅 5・高さ 4 の鉄の格子)
    static void door(ServerLevel level, boolean open) {
        doorOpen = open;
        BlockPos g = MoonRift.mark("door_gate");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                BlockPos p = g.offset(dx, dy, 0);
                BlockState want = open ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState();
                if (level.getBlockState(p).getBlock() != want.getBlock()) {
                    level.setBlock(p, want, 3);
                }
            }
        }
        level.playSound(null, g, open ? RiftBlocks.DOOR_OPEN.get() : RiftBlocks.DOOR_CLOSE.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
    }

    // ---------------------------------------------------------------- 影の橋 (灯りの回廊。星灯りの 6 の内は消える)
    static void bridges(ServerLevel level, boolean force) {
        BlockPos f = MoonRift.mark("hall_floor");
        if (!force && level.getNearestPlayer(f.getX(), f.getY(), f.getZ(), 64, false) == null) {
            return;
        }
        for (BlockPos p : MoonRift.shadow()) {
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof ShadowBridgeBlock)) {
                continue;
            }
            boolean want = !StarLamps.inLight(level, p.above());
            if (st.getValue(ShadowBridgeBlock.SOLID) != want) {
                level.setBlock(p, st.setValue(ShadowBridgeBlock.SOLID, want), 3);
            }
        }
    }

    // ---------------------------------------------------------------- 残り影の溜まり (回廊。人が 12 の内にいると 6 秒ごとに巡礼の残像)
    static void pools(ServerLevel level) {
        long gt = level.getGameTime();
        for (int i = 0; i < 4; i++) {
            if ((gt + i * 30) % POOL_EVERY != 0) {
                continue;
            }
            BlockPos at = MoonRift.mark("pool_" + i);
            if (level.getNearestPlayer(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, POOL_REACH, false) == null) {
                continue;
            }
            if (StarLamps.inLight(level, at.getCenter())) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
                continue;
            }
            String tag = POOL_TAG + i;
            if (!level.getEntitiesOfClass(RiftArmor.class, new AABB(at).inflate(24), e -> e.isAlive() && e.getTags().contains(tag)).isEmpty()) {
                continue;
            }
            RiftRemnants.mark(level, MoonMobsSetup.RIFT_ARMOR.get(), at.getBottomCenter(), tag);
        }
    }

    // ---------------------------------------------------------------- 湧き (内の台地と外の面。野営・淵の上・主の間は湧かない)
    static boolean spawnable(BlockPos p) {
        Vec3 c = p.getCenter();
        return MoonRift.inside(p) && !p.closerThan(MoonRift.mark("camp"), CAMP_SAFE) && !MoonRift.overChasm(c) && !MoonRift.inArenaZone(c)
                && !p.closerThan(MoonRift.mark("door_entry"), 8) && !p.closerThan(MoonRift.mark("leap_entry"), 8);
    }

    static void spawn(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (!spawnable(at) || level.getGameTime() % 40 != 0) {
            return;
        }
        int near = level.getEntitiesOfClass(MoonMob.class, p.getBoundingBox().inflate(40, 24, 40),
                e -> e.isAlive() && !(e instanceof WaningBlade) && !e.getTags().contains(RiftArena.TAG)).size();
        if (near >= CAP) {
            return;
        }
        RandomSource r = level.random;
        float roll = r.nextFloat();
        EntityType<? extends MoonMob> type = roll < 0.35f ? MoonMobsSetup.RIFT_WING.get() : roll < 0.7f ? MoonMobsSetup.RIFT_ARMOR.get()
                : MoonMobsSetup.RIFT_SERPENT.get();
        BlockPos spot = spot(level, at, r);
        if (spot != null) {
            put(level, type, spot);
        }
    }

    static BlockPos spot(ServerLevel level, BlockPos near, RandomSource r) {
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 14 + r.nextDouble() * 12;
            int x = near.getX() + (int) Math.round(Math.cos(a) * d);
            int z = near.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int y = near.getY() + 6; y >= near.getY() - 8; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                    if (!spawnable(p)) {
                        break;
                    }
                    return p;
                }
            }
        }
        return null;
    }

    public static MoonMob put(ServerLevel level, EntityType<? extends MoonMob> type, BlockPos at) {
        MoonMob m = type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY() + (m instanceof RiftWing ? 4.0 : 0), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        boolean matched = MoonMob.waningMoon(level);
        if (m instanceof RiftWing) {
            m.tuneMoon(3, matched, false, RiftWing.HP, RiftWing.DMG);
        } else if (m instanceof RiftArmor) {
            m.tuneMoon(3, matched, false, RiftArmor.HP, RiftArmor.DMG);
        } else if (m instanceof RiftSerpent) {
            m.tuneMoon(3, matched, false, RiftSerpent.HP, RiftSerpent.DMG);
        }
        level.addFreshEntity(m);
        return m;
    }

    // ---------------------------------------------------------------- 前のループの自分 (四人目。野営。頼みが済むまで)
    static void pastSelf(ServerLevel level, ServerPlayer p) {
        BlockPos at = MoonRift.mark("past_self");
        if (!p.blockPosition().closerThan(at, 48)) {
            return;
        }
        List<TownGuestEntity> near = level.getEntities(TownSetup.GUEST.get(), new AABB(at).inflate(24), e -> e.isAlive() && PAST_SELF.equals(e.guestId()));
        boolean gone = level.players().stream().filter(q -> q.blockPosition().closerThan(at, 48))
                .allMatch(q -> RiftPuzzles.stage(q) >= RiftPuzzles.stageIndex("promise") + 1);
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
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90f, 0f);
        e.setHome(at);
        level.addFreshEntity(e);
    }
}

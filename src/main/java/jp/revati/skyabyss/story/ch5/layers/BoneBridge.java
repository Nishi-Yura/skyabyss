package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 骨の層の「肋の橋と崩れた道」(docs/design/36 の 8 章)。設計図の印は tools/abyss/layer_bone.py。
//   1. 橋の上に立つと、橋の両端から骨拾いの群れが来る (人ごとに一度ずつ。少し間をおいて、もう一度来る)
//   2. 橋の向こうの巣 (den_bone) へ入ると、肋の番が出て、巣の戸の口が封じられる (barrier)。倒すと戸が開く
//   3. 倒したら一度だけ DEN_CLEARED の係へ知らせる (鎖の近道を下ろす係。近道は layer_common / 本線の仕事。ここは知らせるだけ)
//   状態 (番を出した・倒した) は世界に残す。番がいなくなっても、倒されていない限り戸は開かない (死んだ・消えた番を数える)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BoneBridge {
    public static final String DEN_GUARD_TAG = "skyabyss_den_guard";
    static final int PACK = 3;               // 一度に両端から来る数 (端ごと)
    static final long PACK_GAP_TICKS = 900;  // 同じ人に次の群れが来るまで (45 秒)
    static final int FORM = 1;
    static final int SEAL_NEAR_K = 20;       // 戸の口の封じ: 原点からの半径 20〜22 の三層
    static final int SEAL_FAR_K = 22;
    static final double DEN_REACH = 8;       // 巣の中へ入ったとみなす距離
    static final double BRIDGE_REACH = 2.5;  // 橋の上とみなす距離 (橋の線から)
    static final int CHECK_EVERY = 10;

    // 倒した・出したことを、ほかの係へ知らせる (一度だけ、倒したとき)。近道の係はここへ入る
    private static final List<Consumer<ServerLevel>> DEN_CLEARED = new ArrayList<>();
    private static final Map<UUID, Long> LAST_PACK = new HashMap<>();

    private BoneBridge() {
    }

    public static void onDenCleared(Consumer<ServerLevel> listener) {
        DEN_CLEARED.add(listener);
    }

    // ---------------------------------------------------------------- 橋の群れ

    // 橋の線の上 (両端の間で、線から近い) にいるか
    public static boolean onBridge(BlockPos p) {
        BlockPos a = AbyssBuild.mark("bridge_bone_a");
        BlockPos b = AbyssBuild.mark("bridge_bone_b");
        if (a == null || b == null) {
            return false;
        }
        return distToSegment(p.getX() + 0.5, p.getZ() + 0.5, a, b) <= BRIDGE_REACH && !atEnd(p, a, b);
    }

    // 橋の端から二マス以内 (ここでは群れを呼ばない。端の所は足場)
    static boolean atEnd(BlockPos p, BlockPos a, BlockPos b) {
        return Math.min(p.distSqr(a), p.distSqr(b)) < 9;
    }

    // 点から線分までの水平の距離
    static double distToSegment(double px, double pz, BlockPos a, BlockPos b) {
        double ax = a.getX() + 0.5, az = a.getZ() + 0.5;
        double bx = b.getX() + 0.5, bz = b.getZ() + 0.5;
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
        double qx = ax + t * dx - px, qz = az + t * dz - pz;
        return Math.sqrt(qx * qx + qz * qz);
    }

    // 橋の両端から群れを出す (人の今いる所には出さない。橋の端の床に立てるところ)
    public static void packFromBothEnds(ServerLevel level) {
        BlockPos a = AbyssBuild.mark("bridge_bone_a");
        BlockPos b = AbyssBuild.mark("bridge_bone_b");
        if (a == null || b == null) {
            return;
        }
        spawnPack(level, a);
        spawnPack(level, b);
        LayerNotes.near(level, a, 40, "message.skyabyss.abyss_layers.bone_pack");
    }

    static void spawnPack(ServerLevel level, BlockPos end) {
        for (int i = 0; i < PACK; i++) {
            double ox = (i - 1) * 1.2;
            AbyssMobSpawner.spawnAt(level, "bone_picker", end.getX() + 0.5 + ox, end.getY(), end.getZ() + 0.5, FORM);
        }
    }

    // ---------------------------------------------------------------- 巣の門

    // 巣の戸の口 (room() と同じ向き: 原点から巣へ向かう方向に、半径 20〜22、幅 3、高さ 3)
    public static List<BlockPos> sealCells(BlockPos den) {
        double len = Math.sqrt((double) den.getX() * den.getX() + (double) den.getZ() * den.getZ());
        double ox = den.getX() / len, oz = den.getZ() / len;
        List<BlockPos> out = new ArrayList<>();
        for (int k = SEAL_NEAR_K; k <= SEAL_FAR_K; k++) {
            for (int w = -1; w <= 1; w++) {
                int px = (int) Math.round(ox * k - oz * w);
                int pz = (int) Math.round(oz * k + ox * w);
                for (int y = 0; y < 3; y++) {
                    out.add(new BlockPos(px, den.getY() + y, pz));
                }
            }
        }
        return out;
    }

    // 戸を封じる (on) または開ける (off)。封じるのは空気だけ。開けるのは封じた barrier だけ
    public static void seal(ServerLevel level, boolean on) {
        BlockPos den = AbyssBuild.mark("den_bone");
        if (den == null) {
            return;
        }
        for (BlockPos p : sealCells(den)) {
            if (on && level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            } else if (!on && level.getBlockState(p).is(Blocks.BARRIER)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    // 巣の中にいる人 (巣の印から水平に 8 以内、原点から 22 より外 = 戸の口より奥)
    static boolean inDen(BlockPos p, BlockPos den) {
        double dx = p.getX() - den.getX(), dz = p.getZ() - den.getZ();
        double r = Math.sqrt((double) p.getX() * p.getX() + (double) p.getZ() * p.getZ());
        return dx * dx + dz * dz <= DEN_REACH * DEN_REACH && r > SEAL_FAR_K && Math.abs(p.getY() - den.getY()) <= 4;
    }

    static boolean guardAlive(ServerLevel level, BlockPos den) {
        AABB box = new AABB(den).inflate(40);
        return !level.getEntitiesOfClass(Entity.class, box, e -> e.getTags().contains(DEN_GUARD_TAG) && e.isAlive()).isEmpty();
    }

    // 肋の番が死んだとき (LivingDeathEvent から)
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().getTags().contains(DEN_GUARD_TAG) || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!AbyssWorld.LEVEL.equals(level.dimension())) {
            return;
        }
        Data d = Data.get(level.getServer());
        if (d.defeated) {
            return;
        }
        d.defeated = true;
        d.setDirty();
        seal(level, false);
        LayerNotes.near(level, AbyssBuild.mark("den_bone"), 60, "message.skyabyss.abyss_layers.den_open");
        SkyAbyss.LOGGER.info("[abyss] the rib guard of the bone den is down");
        for (Consumer<ServerLevel> c : DEN_CLEARED) {
            c.accept(level);
        }
    }

    // ---------------------------------------------------------------- 毎 tick

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 0 || !AbyssBuild.built(server)) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            if (onBridge(p.blockPosition())) {
                Long last = LAST_PACK.get(p.getUUID());
                if (last == null || now - last >= PACK_GAP_TICKS) {
                    LAST_PACK.put(p.getUUID(), now);
                    packFromBothEnds(level);
                }
            }
        }
        denGate(level);
    }

    // 巣の門: 人が中へ入ったら番を出して戸を封じる。番が死んだら、死亡の係 (onDeath) が開ける
    static void denGate(ServerLevel level) {
        Data d = Data.get(level.getServer());
        if (d.defeated) {
            return;
        }
        BlockPos den = AbyssBuild.mark("den_bone");
        if (den == null) {
            return;
        }
        if (!d.spawned) {
            for (ServerPlayer p : level.players()) {
                if (p.isAlive() && inDen(p.blockPosition(), den)) {
                    Entity g = AbyssMobSpawner.spawnAt(level, "rib_guard", den.getX() + 0.5, den.getY(), den.getZ() + 0.5, FORM);
                    if (g != null) {
                        g.addTag(DEN_GUARD_TAG);
                        d.spawned = true;
                        d.setDirty();
                        seal(level, true);
                        LayerNotes.near(level, den, 40, "message.skyabyss.abyss_layers.den_seal");
                    }
                    break;
                }
            }
        } else if (!guardAlive(level, den)) {
            // 番がいないのに倒されていない (消えた・再起動で失くした): 人がいれば次の人の入りで出し直す
            d.spawned = false;
            d.setDirty();
            seal(level, false);
        } else {
            seal(level, true);
        }
    }

    // 巣の戸を封じたか (自己テスト用)
    public static boolean sealed(ServerLevel level) {
        BlockPos den = AbyssBuild.mark("den_bone");
        if (den == null) {
            return false;
        }
        for (BlockPos p : sealCells(den)) {
            if (level.getBlockState(p).is(Blocks.BARRIER)) {
                return true;
            }
        }
        return false;
    }

    // 倒した状態 (自己テスト用・画面の印)
    public static boolean defeated(MinecraftServer server) {
        return Data.get(server).defeated;
    }

    // 世界に一つ (番を出したか・倒したか)
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        boolean spawned;
        boolean defeated;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_bone_den");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.spawned = tag.getBoolean("Spawned");
            d.defeated = tag.getBoolean("Defeated");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("Spawned", spawned);
            tag.putBoolean("Defeated", defeated);
            return tag;
        }
    }
}

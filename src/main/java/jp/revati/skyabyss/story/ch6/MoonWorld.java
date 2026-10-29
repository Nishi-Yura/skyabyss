package jp.revati.skyabyss.story.ch6;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

// 六章の月 (docs/design/46)。別のディメンション skyabyss:moon (data/skyabyss/dimension*/moon.json、バイオーム skyabyss:moon)。
//   地面    平らな月の砂 (薄灰の粉。上面 y 11)。着く所のまわり 72 に、くぼみと縁の盛り上がったクレーターを一度だけ掘る (MoonWorld.build)
//   空      黒く、星と、空に浮かぶ地上の大陸 (client/MoonClient)。時刻は夜に止める。月の位相は MoonClock
//   重さ    軽い (MoonGravity)
//   行き来  都の大鐘楼の上の門 (MoonGate)。着く所の帰りの台 (天然磁石) を右クリックで都へ
//   谷      着く所の南に区画 1「新月の谷」(valley.MoonValley。版 2 で一度だけ流し込む)
//   崖      着く所の東に区画 2「上弦の崖」(cliff.MoonCliff。版 4 で一度だけ流し込む)
//   野      着く所の西に区画 3「満月の野」(field.MoonField。版 6)
//   淵      着く所の南東に区画 4「下弦の淵」(rift.MoonRift。版 7)
//   核      着く所の北に核の門、月の奥に核の間 (core.MoonCore。版 8)
public final class MoonWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "moon"));
    public static final int SURFACE = 11;
    public static final BlockPos ARRIVAL = new BlockPos(0, SURFACE + 1, 0);
    // 1 = 着く所とクレーター、2 = 新月の谷 (valley.MoonValley)、3 = 谷の踏み跡・星の溜まりの十字 (谷を流し込み直す)、
    // 4 = 上弦の崖 (cliff.MoonCliff。着く所の東)、5 = 崖の岩山を尾根と谷筋のある形に (崖を流し込み直す)、6 = 満月の野 (field.MoonField。着く所の西)、
    // 7 = 下弦の淵 (rift.MoonRift。着く所の南東)、8 = 月の核 (core.MoonCore。着く所の北の門と、月の奥の核の間)、
    // 9 = 月の暦 (クリア後の位相の挑戦の入口。着く所の南。MoonTrial)、10 = 記憶の石 (区画ごとに一つ。MoonMemories)、
    // 11 = 谷の作り込みの二回目 (入口の段々の下り・崩れた斜面と石柱・崖の岩棚・主の間の立ち石の輪。谷を流し込み直し、記憶の石を置き直す)
    static final int VERSION = 11;

    private MoonWorld() {
    }

    public static boolean in(ServerPlayer p) {
        return p.level().dimension() == LEVEL;
    }

    // 帰りの台 (着く所の北 3)
    public static BlockPos returnStone() {
        return ARRIVAL.offset(0, 0, -3);
    }

    public static boolean enter(ServerPlayer p) {
        ServerLevel level = p.server.getLevel(LEVEL);
        if (level == null) {
            return false;
        }
        level.getChunk(ARRIVAL);
        build(level);
        p.teleportTo(level, ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 2.5, 180f, -10f);
        return true;
    }

    // 地上へ戻す: 門 (鐘楼の上) があればそこ、なければ起きる所
    public static void leave(ServerPlayer p) {
        ServerLevel over = p.server.overworld();
        BlockPos gate = MoonGate.gatePos(p.server);
        BlockPos at = gate != null ? gate.offset(0, 0, 2)
                : p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition().above() : over.getSharedSpawnPos().above();
        p.teleportTo(over, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
    }

    // ---------------------------------------------------------------- 着く所 (一度だけ)
    public static void build(ServerLevel level) {
        Data d = Data.get(level.getServer());
        if (d.version >= VERSION) {
            return;
        }
        if (d.version < 1) {
            landing(level);
        }
        if (d.version < 3) {
            jp.revati.skyabyss.story.ch6.valley.MoonValley.build(level);
        }
        if (d.version < 5) {
            jp.revati.skyabyss.story.ch6.cliff.MoonCliff.build(level);
        }
        if (d.version < 6) {
            jp.revati.skyabyss.story.ch6.field.MoonField.build(level);
        }
        if (d.version < 7) {
            jp.revati.skyabyss.story.ch6.rift.MoonRift.build(level);
        }
        if (d.version < 8) {
            jp.revati.skyabyss.story.ch6.core.MoonCore.build(level);
        }
        if (d.version < 9) {
            calendar(level);
        }
        if (d.version >= 3 && d.version < 11) {
            jp.revati.skyabyss.story.ch6.valley.MoonValley.build(level);
        }
        if (d.version < 11) {
            MoonMemories.place(level);
        }
        d.version = VERSION;
        d.setDirty();
    }

    // 新月の谷ができているか
    public static boolean valleyBuilt(MinecraftServer server) {
        return Data.get(server).version >= 2;
    }

    // 上弦の崖ができているか
    public static boolean cliffBuilt(MinecraftServer server) {
        return Data.get(server).version >= 4;
    }

    // 満月の野ができているか
    public static boolean fieldBuilt(MinecraftServer server) {
        return Data.get(server).version >= 6;
    }

    // 下弦の淵ができているか
    public static boolean riftBuilt(MinecraftServer server) {
        return Data.get(server).version >= 7;
    }

    // 月の核の間ができているか
    public static boolean coreBuilt(MinecraftServer server) {
        return Data.get(server).version >= 8;
    }

    // 月の暦 (版 9): 着く所の南 7 に、磨いた黒石の小さな台と暦
    static void calendar(ServerLevel level) {
        BlockPos c = MoonTrial.calendar();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlock(c.offset(x, -1, z), (x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
                for (int y = 0; y < 3; y++) {
                    level.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(c, jp.revati.skyabyss.story.ch6.rift.RiftBlocks.MOON_CALENDAR.get().defaultBlockState(), 3);
    }

    // 着く所とクレーター (版 1)
    static void landing(ServerLevel level) {
        RandomSource r = RandomSource.create(0x4D4F4F4EL);
        // クレーター: 着く所 (半径 10) を避けて、大小 26
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = 16 + r.nextDouble() * 56;
            int cx = (int) Math.round(Math.cos(a) * dist);
            int cz = (int) Math.round(Math.sin(a) * dist);
            int rad = 3 + r.nextInt(i < 4 ? 9 : 5);
            // 谷 (着く所の南 z 14〜182、x -46〜46) には掘らない
            if (cz + rad + 4 >= 14 && Math.abs(cx) - rad - 4 <= 46) {
                continue;
            }
            crater(level, cx, cz, rad, r);
        }
        // 着く所: 磨いた石の円い台、銀の縁、帰りの台 (天然磁石) と四つの灯り
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double d0 = Math.hypot(x, z);
                if (d0 <= 4.4) {
                    BlockState st = d0 > 3.5 ? Blocks.POLISHED_DIORITE.defaultBlockState() : (x + z) % 2 == 0 ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                            : Blocks.CALCITE.defaultBlockState();
                    level.setBlock(new BlockPos(x, SURFACE, z), st, 2);
                    for (int y = SURFACE + 1; y < SURFACE + 6; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        level.setBlock(returnStone(), Blocks.LODESTONE.defaultBlockState(), 3);
        for (int[] c : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {3, -3}}) {
            BlockPos p = new BlockPos(c[0], SURFACE + 1, c[1]);
            level.setBlock(p, Blocks.DIORITE_WALL.defaultBlockState(), 3);
            level.setBlock(p.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
        }
        SkyAbyss.LOGGER.info("[moon] landing site built");
    }

    // くぼみ (お椀) と、縁の盛り上がり。底は暗い玄武岩、縁は明るい砂
    static void crater(ServerLevel level, int cx, int cz, int rad, RandomSource r) {
        int depth = Math.max(2, rad / 2);
        for (int x = cx - rad - 3; x <= cx + rad + 3; x++) {
            for (int z = cz - rad - 3; z <= cz + rad + 3; z++) {
                double d = Math.hypot(x - cx, z - cz) / rad;
                if (Math.hypot(x, z) < 10) {
                    continue;
                }
                if (d <= 1.0) {
                    int dig = (int) Math.round(depth * (1 - d * d));
                    for (int y = SURFACE - dig + 1; y <= SURFACE; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(new BlockPos(x, SURFACE - dig, z), d < 0.55 ? Blocks.SMOOTH_BASALT.defaultBlockState()
                            : r.nextFloat() < 0.3 ? Blocks.TUFF.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState(), 2);
                } else if (d <= 1.35) {
                    int up = (int) Math.round((1.35 - d) / 0.35 * Mth.clamp(rad / 3.0, 1, 3));
                    for (int y = SURFACE + 1; y <= SURFACE + up; y++) {
                        level.setBlock(new BlockPos(x, y, z), y == SURFACE + up ? Blocks.WHITE_CONCRETE_POWDER.defaultBlockState()
                                : Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState(), 2);
                    }
                }
            }
        }
        // 縁の小岩
        for (int k = 0; k < rad; k++) {
            double a = r.nextDouble() * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * rad * 1.2);
            int z = cz + (int) Math.round(Math.sin(a) * rad * 1.2);
            if (Math.hypot(x, z) >= 10) {
                level.setBlock(new BlockPos(x, SURFACE + 1 + (int) Math.round(rad / 4.0), z), r.nextBoolean() ? Blocks.TUFF.defaultBlockState()
                        : Blocks.ANDESITE.defaultBlockState(), 2);
            }
        }
    }

    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        int version;
        // 位相の時計のずれ (開発用のコマンドで位相の頭へ進めるとき。ゲームの時刻は全ディメンションで共有なので動かさない)
        long clockOffset;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_moon_world");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.version = tag.getInt("Version");
            d.clockOffset = tag.getLong("ClockOffset");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Version", version);
            tag.putLong("ClockOffset", clockOffset);
            return tag;
        }
    }
}

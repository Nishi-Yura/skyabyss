package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 外れの隠し荷 (FrontierBand の ②。2026-11-06 の頁「大陸の外れ」の m_reasons = a)。外れの帯に、一人ずつ受け取る宝箱を多く置く。
//   置き場: 外れの帯の中を SPACING ごとに見て、道 (ROAD_CLEAR)・見どころ (POI_CLEAR)・外れの集落と奥のダンジョン (SPOT_CLEAR)・
//           隠れた発見 (SECRET_CLEAR) から離れ、海より上の平らな所。互いに MIN_GAP 離す (決まった順で選ぶので、どの世界でも同じ)
//   建てる: 人が BUILD_RADIUS まで近づいたら、土地の石の小さな囲い (岩 2 つと目印) と宝箱を置く。建った印は SiteData の KIND
//           (地上を作り直すと消え、建て直す)
//   中身: 鐘銭・糧・土地の粉 (I) と、封の箱 一つ。箱の格は土地の野の主の箱より一段上 (岩鯨・潮 = エピック、嵐・灰 = ヒロイック)、
//         帯は土地の上限 +3 の Lv の帯。宝箱は世界の宝箱と同じく一人ずつ受け取り、20 分で戻る (loot/SharedChests)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FrontierCaches {
    public static final String KIND = "far_cache";
    static final int SPACING = 48;
    static final double MIN_GAP = 170;
    static final double ROAD_CLEAR = 24;
    static final double POI_CLEAR = 60;
    static final double SPOT_CLEAR = 90;
    static final double SECRET_CLEAR = 40;
    static final int BUILD_RADIUS = 64;
    static final int EXTENT = 2600;

    public record Cache(int id, int region, int x, int z) {
    }

    private static List<Cache> all;

    private FrontierCaches() {
    }

    public static synchronized List<Cache> all() {
        if (all == null) {
            all = List.copyOf(find());
        }
        return all;
    }

    static List<Cache> find() {
        ContinentMap map = ContinentMap.get();
        ContinentLayout lay = ContinentLayout.get();
        List<int[]> picked = new ArrayList<>();
        List<Cache> out = new ArrayList<>();
        for (int z = -EXTENT; z <= EXTENT; z += SPACING) {
            for (int x = -EXTENT; x <= EXTENT; x += SPACING) {
                // 格子のままでは並んで見えるので、場所ごとに少しずらす
                int jx = x + (int) Math.round(ContinentMap.hash(x, 0, z ^ 0x3C1) * SPACING * 0.35);
                int jz = z + (int) Math.round(ContinentMap.hash(z, 0, x ^ 0x7A5) * SPACING * 0.35);
                if (!FrontierBand.far(jx, jz) || !flat(map, jx, jz)) {
                    continue;
                }
                if (near(picked, jx, jz, MIN_GAP) || roadNear(lay, jx, jz) || poiNear(jx, jz) || spotNear(lay, jx, jz) || secretNear(jx, jz)) {
                    continue;
                }
                picked.add(new int[] {jx, jz});
                out.add(new Cache(out.size(), map.region(jx, jz), jx, jz));
            }
        }
        return out;
    }

    // 海より上で、まわり ±3 の高さの差が 2 まで
    static boolean flat(ContinentMap map, int x, int z) {
        int c = map.surface(x, z);
        if (c < ContinentChunkGenerator.SEA_LEVEL + 2) {
            return false;
        }
        for (int[] d : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            if (Math.abs(map.surface(x + d[0], z + d[1]) - c) > 2) {
                return false;
            }
        }
        return true;
    }

    static boolean near(List<int[]> pts, int x, int z, double r) {
        for (int[] p : pts) {
            if (Math.hypot(p[0] - x, p[1] - z) < r) {
                return true;
            }
        }
        return false;
    }

    static boolean roadNear(ContinentLayout lay, int x, int z) {
        return roadNear(lay, x, z, ROAD_CLEAR);
    }

    static boolean roadNear(ContinentLayout lay, int x, int z, double clear) {
        for (List<BlockPos> line : lay.roads()) {
            for (int i = 1; i < line.size(); i++) {
                BlockPos a = line.get(i - 1);
                BlockPos b = line.get(i);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                double l2 = dx * dx + dz * dz;
                double u = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.getX()) * dx + (z - a.getZ()) * dz) / l2));
                if (Math.hypot(a.getX() + u * dx - x, a.getZ() + u * dz - z) < clear) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean poiNear(int x, int z) {
        for (ContinentPois.Poi p : ContinentPois.all()) {
            if (Math.hypot(p.x() - x, p.z() - z) < POI_CLEAR) {
                return true;
            }
        }
        return false;
    }

    static boolean spotNear(ContinentLayout lay, int x, int z) {
        for (ContinentLayout.Frontier f : lay.frontier()) {
            if (Math.hypot(f.pos().getX() - x, f.pos().getZ() - z) < SPOT_CLEAR) {
                return true;
            }
        }
        return false;
    }

    static boolean secretNear(int x, int z) {
        for (ContinentSecrets.Secret s : ContinentSecrets.all()) {
            if (Math.hypot(s.x() - x, s.z() - z) < SECRET_CLEAR) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 中身
    // 箱の格: 土地の野の主の箱 (item/wild/WildLoot) より一段上
    public static GearTier boxTier(int region) {
        return region == ContinentMap.STORM || region == ContinentMap.EMBER ? GearTier.HEROIC : GearTier.EPIC;
    }

    // 箱の帯: 土地の上限 +3 の Lv の帯
    public static int boxBand(int region) {
        int lv = FrontierBand.landLevel(region) + FrontierBand.LEVEL_PLUS;
        return lv <= 10 ? 1 : lv <= 20 ? 2 : lv <= 30 ? 3 : lv <= 40 ? 4 : 5;
    }

    static void fill(ServerLevel level, BlockPos at, Cache c) {
        if (!(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
            return;
        }
        var r = level.random;
        chest.setItem(4, new ItemStack(jp.revati.skyabyss.facility.FacilitySetup.BELL_COIN.get(), 12 + r.nextInt(14)));
        chest.setItem(10, new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.TOWN_BREAD.get(), 2 + r.nextInt(3)));
        BoxKind kind = BoxKind.rarity(boxTier(c.region()), boxBand(c.region()));
        var item = kind == null ? null : jp.revati.skyabyss.loot.box.LootBoxSetup.item(kind);
        if (item != null) {
            chest.setItem(13, new ItemStack(item));
        }
        jp.revati.skyabyss.item.extra.Element e = switch (c.region()) {
            case ContinentMap.WHALE -> jp.revati.skyabyss.item.extra.Element.STONE;
            case ContinentMap.TIDE -> jp.revati.skyabyss.item.extra.Element.TIDE;
            case ContinentMap.STORM -> jp.revati.skyabyss.item.extra.Element.WIND;
            default -> jp.revati.skyabyss.item.extra.Element.ASH;
        };
        chest.setItem(16, new ItemStack(jp.revati.skyabyss.registry.ModItems.socketShard(e).get()));
        chest.setChanged();
    }

    // ---------------------------------------------------------------- 見回り (1 秒ごと)
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7 || !RoadClearance.live()) {
            return;
        }
        ServerLevel over = server.overworld();
        SiteData data = null;
        for (ServerPlayer p : over.players()) {
            if (p.isSpectator() || !FrontierBand.far(p.getX(), p.getZ())) {
                continue;
            }
            for (Cache c : all()) {
                double dx = p.getX() - c.x();
                double dz = p.getZ() - c.z();
                if (dx * dx + dz * dz > (double) BUILD_RADIUS * BUILD_RADIUS) {
                    continue;
                }
                if (data == null) {
                    data = SiteData.get(over);
                }
                SiteData.Entry e = entry(data, c.id());
                if (e == null || !e.built) {
                    build(over, data, c, e);
                }
            }
        }
    }

    static SiteData.Entry entry(SiteData data, int id) {
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.index == id) {
                return e;
            }
        }
        return null;
    }

    // 建てる。チャンクがまだなら null
    public static SiteData.Entry build(ServerLevel level, SiteData data, Cache c, SiteData.Entry e) {
        if (!level.hasChunkAt(new BlockPos(c.x(), 0, c.z()))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.x(), c.z());
        BlockPos base = new BlockPos(c.x(), y, c.z());
        Direction facing = Direction.from2DDataValue(Math.floorMod(c.id() * 5 + c.z(), 4));
        place(level, c, base, facing);
        if (e != null) {
            data.remove(e);
        }
        e = data.add(KIND, c.id(), base, 0);
        e.built = true;
        data.setDirty();
        return e;
    }

    // 土地の石: {地面, 岩, 目印}
    static BlockState[] stones(int region) {
        return switch (region) {
            case ContinentMap.WHALE -> new BlockState[] {Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.BONE_BLOCK.defaultBlockState(),
                    Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState()};
            case ContinentMap.TIDE -> new BlockState[] {Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
                    Blocks.SANDSTONE_WALL.defaultBlockState()};
            case ContinentMap.STORM -> new BlockState[] {Blocks.PACKED_ICE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(),
                    Blocks.STONE_BRICK_WALL.defaultBlockState()};
            default -> new BlockState[] {Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE_WALL.defaultBlockState()};
        };
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    // 形: 3x3 の土地の石の床、後ろの両角に岩 (2 段)、宝箱は真ん中で正面を向く、右の角に目印の柱と灯り
    static void place(ServerLevel level, Cache c, BlockPos base, Direction facing) {
        BlockState[] st = stones(c.region());
        Direction side = facing.getClockWise();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockPos g = base.relative(side, a).relative(facing, b);
                set(level, g.below(), st[0]);
                BlockState above = level.getBlockState(g);
                if (!above.isAir() && above.canBeReplaced()) {
                    set(level, g, Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos back = base.relative(facing.getOpposite());
        for (int a : new int[] {-1, 1}) {
            set(level, back.relative(side, a), st[1]);
            set(level, back.relative(side, a).above(), st[1]);
        }
        set(level, back, st[1]);
        BlockPos post = base.relative(side, 1).relative(facing, 1);
        set(level, post, st[2]);
        set(level, post.above(), Blocks.LANTERN.defaultBlockState());
        set(level, base, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        fill(level, base, c);
    }
}

package jp.revati.skyabyss.titan.light;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 歩く道の灯り (建築の監査 LIGHT_DARK への答え)。道のどの立ち位置も光度 4 以上になるように、壁・天井の石を、巨神ごとの灯りのブロックに置き換える。
// 灯りの場所は、建てた背を RouteLightDev (/skyabyss dev routelight <tide|storm|ember>) が測って選び、
// data/skyabyss/<tide_back|storm_back|ember_back>/route_lamps.json に [x,y,z] の並びで書く。背を建てたあと (部屋・試練の中身より後) に place が置く。
//  潮殻: 海のランタン (sea_lantern)、雷翼: 銅の電球 (copper_bulb、点灯)、余燼: 茂み光 (shroomlight)
// 置き換えるのは、石・レンガ・玄武岩・凝灰岩などの丸ごとのブロックだけ (羊毛・ガラス・仕掛け・しるしのブロックは触らない)
public final class RouteLamps {
    private RouteLamps() {
    }

    public static BlockState lampOf(String titan) {
        return switch (titan) {
            case "tide" -> Blocks.SEA_LANTERN.defaultBlockState();
            case "storm" -> Blocks.COPPER_BULB.defaultBlockState().setValue(net.minecraft.world.level.block.CopperBulbBlock.LIT, true);
            default -> Blocks.SHROOMLIGHT.defaultBlockState();
        };
    }

    public static boolean replaceable(BlockState s) {
        boolean stone = s.is(Blocks.STONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS)
                || s.is(Blocks.DEEPSLATE) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.DEEPSLATE_BRICKS) || s.is(Blocks.DEEPSLATE_TILES)
                || s.is(Blocks.POLISHED_DEEPSLATE) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.POLISHED_BLACKSTONE) || s.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                || s.is(Blocks.BASALT) || s.is(Blocks.POLISHED_BASALT) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.TUFF) || s.is(Blocks.TUFF_BRICKS)
                || s.is(Blocks.POLISHED_TUFF) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.PRISMARINE) || s.is(Blocks.PRISMARINE_BRICKS) || s.is(Blocks.DARK_PRISMARINE)
                || s.is(Blocks.PACKED_MUD) || s.is(Blocks.MUD_BRICKS) || s.is(Blocks.BRICKS) || s.is(Blocks.NETHER_BRICKS) || s.is(Blocks.ANDESITE)
                || s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.POLISHED_DIORITE)
                || s.is(Blocks.RED_NETHER_BRICKS) || s.is(Blocks.NETHER_WART_BLOCK) || s.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
                || s.is(Blocks.CHISELED_POLISHED_BLACKSTONE) || s.is(Blocks.RED_TERRACOTTA) || s.is(Blocks.LIGHT_GRAY_TERRACOTTA)
                || s.is(Blocks.ORANGE_TERRACOTTA);
        return stone && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static List<BlockPos> load(String titan) {
        List<BlockPos> out = new ArrayList<>();
        String path = "/data/" + SkyAbyss.MODID + "/" + titan + "_back/route_lamps.json";
        try (var in = RouteLamps.class.getResourceAsStream(path)) {
            if (in == null) {
                return out;
            }
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonArray a = e.getAsJsonArray();
                out.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
        } catch (Exception ex) {
            SkyAbyss.LOGGER.error("Could not read {}", path, ex);
        }
        return out;
    }

    // 背を建てたあとに呼ぶ。置いた数を返す
    public static int place(ServerLevel level, String titan) {
        BlockState lamp = lampOf(titan);
        int n = 0;
        for (BlockPos p : load(titan)) {
            BlockState cur = level.getBlockState(p);
            if (cur != lamp && replaceable(cur)) {
                level.setBlock(p, lamp, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                n++;
            }
        }
        if (n > 0) {
            SkyAbyss.LOGGER.info("Route lamps ({}): placed {}", titan, n);
        }
        return n;
    }
}

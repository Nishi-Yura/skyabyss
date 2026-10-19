package jp.revati.skyabyss.titan.tide.back;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の「場所ごとの材質」(26 の 9 章)。体の中がどこも同じ海の石のレンガにならないよう、場所ごとに壁・床・柱・灯りを替える。
// TideKit が今の場所 (kit.zone) を持ち、壁・灯り・部屋の部品はそれを使う。壁の石は座標だけから決まる (乱数なし)。
public enum TideZone {
    // 既定: 潮の民の石組み (海の石のレンガ・暗い海の石・方解石の筋)
    SHELL("shell", 5, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.CALCITE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
            Blocks.DARK_PRISMARINE, Blocks.CALCITE, Blocks.SEA_LANTERN),
    // 潮門・浜: 砂岩と貝の白 (方解石)、流木の梁
    BEACH("beach", 4, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.CALCITE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE,
            Blocks.CHISELED_SANDSTONE, Blocks.CALCITE, Blocks.SEA_LANTERN),
    // 殻の大広間 (広間・回廊): 方解石と磨いた閃緑岩の白に、暗い海の石の帯
    VAULT("vault", 6, Blocks.CALCITE, Blocks.POLISHED_DIORITE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.POLISHED_DIORITE, Blocks.SMOOTH_QUARTZ,
            Blocks.DARK_PRISMARINE, Blocks.CALCITE, Blocks.SEA_LANTERN),
    // 潮見の間: 緑青の銅と海の石 (水路・弁の工房)
    GAUGE("gauge", 3, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.PRISMARINE_BRICKS, Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.DARK_PRISMARINE,
            Blocks.WAXED_OXIDIZED_COPPER, Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.SEA_LANTERN),
    // 首の廊下 (背骨の回廊): 骨と方解石、磨いた安山岩
    NECK("neck", 5, Blocks.CALCITE, Blocks.POLISHED_ANDESITE, Blocks.BONE_BLOCK, Blocks.SMOOTH_BASALT, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS,
            Blocks.BONE_BLOCK, Blocks.BONE_BLOCK, Blocks.SEA_LANTERN),
    // ひれの道: 凝灰岩のレンガと、貝殻 (フジツボ) の白い塊
    FIN("fin", 4, Blocks.TUFF_BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.DARK_PRISMARINE, Blocks.CALCITE, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS,
            Blocks.CHISELED_TUFF_BRICKS, Blocks.CALCITE, Blocks.SEA_LANTERN),
    // 冠 (上の台地): 方解石・白い土器・骨 (フジツボの冠)
    CROWN("crown", 5, Blocks.CALCITE, Blocks.WHITE_TERRACOTTA, Blocks.BONE_BLOCK, Blocks.POLISHED_DIORITE, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_DIORITE,
            Blocks.CHISELED_QUARTZ_BLOCK, Blocks.BONE_BLOCK, Blocks.SEA_LANTERN),
    // 珊瑚の庭の入口と段 (庭の外の部品): 土器の桃色と砂岩
    CORAL("coral", 4, Blocks.TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
            Blocks.CHISELED_SANDSTONE, Blocks.CALCITE, Blocks.SEA_LANTERN),
    // 干潟の孵り場: 泥レンガ・固めた泥・砂岩・骨 (鯨の骨が埋まっている)
    HATCH("hatch", 4, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.SMOOTH_SANDSTONE, Blocks.BONE_BLOCK, Blocks.SMOOTH_SANDSTONE, Blocks.PACKED_MUD,
            Blocks.CHISELED_SANDSTONE, Blocks.BONE_BLOCK, Blocks.SEA_LANTERN);

    public final String id;
    // 壁の石の混ぜ方 (主・副・暗・筋・床・床の別・柱頭・柱・灯り)
    public final BlockState main;
    public final BlockState alt;
    public final BlockState dark;
    public final BlockState vein;
    public final BlockState floor;
    public final BlockState floorAlt;
    public final BlockState cap;
    public final BlockState pillar;
    public final BlockState lamp;
    // 暗い帯の高さの周期
    private final int bandEvery;

    TideZone(String id, int bandEvery, Block main, Block alt, Block dark, Block vein, Block floor, Block floorAlt, Block cap, Block pillar, Block lamp) {
        this.id = id;
        this.bandEvery = bandEvery;
        this.main = main.defaultBlockState();
        this.alt = alt.defaultBlockState();
        this.dark = dark.defaultBlockState();
        this.vein = vein.defaultBlockState();
        this.floor = floor.defaultBlockState();
        this.floorAlt = floorAlt.defaultBlockState();
        this.cap = cap.defaultBlockState();
        this.pillar = pillar.defaultBlockState();
        this.lamp = lamp.defaultBlockState();
    }

    // 壁の石: 主の石に、ところどころ副・筋・暗い帯
    public BlockState wall(int x, int y, int z) {
        int h = Math.floorMod(x * 7 + y * 13 + z * 5, 23);
        if (h == 0) {
            return vein;
        }
        if (h < 4) {
            return alt;
        }
        return (y % bandEvery == 0) ? dark : main;
    }

    // 床の石: 主の床に、ところどころ別の石 (市松の乱れ)
    public BlockState floorAt(int x, int z) {
        int h = Math.floorMod(x * 7 + z * 3, 9);
        return h < 6 ? floor : h < 8 ? floorAlt : vein;
    }
}

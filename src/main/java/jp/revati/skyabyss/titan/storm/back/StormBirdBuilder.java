package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.BackShape;
import jp.revati.skyabyss.titan.storm.back.StormKit.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

// 雷翼の背の鳥を建てる (12 の 3.2〜3.3節・26 の 3.3)。サーバーの起動のときに一度だけ動く。
// 今建てるのは鳥の形 (BackShape storm_back/bird.bin。中は詰まっている) と、羽根の門を閉じる継ぎ当て (titan/BackSeal)、縦穴の埋めだけ。
// 尾羽の上の待機場所は trial/ArrivalDeck、心臓の間は trial/raid/InstanceArenas。以下は前の版の順番 (記録):
// 順番: 鳥の形 → 尾羽の上の到着・羽軸の道・羽の広場 → 羽根の門と腰の回廊 → 尾の間 →
//       風の広間 (昇り風・凪の塔・上の回廊・天窓・竜骨の門・羽軸の柱・行き先の線) → 西の骨・東の骨 (StormWingBuilder) → 首・冠の間・眼の間 (StormHeadBuilder) →
//       竜骨の下の道と前室と竜骨の間 → 掘った所の内壁を風の民の石に (skin) → 休み場・碑文・石
// 拍で動く所 (昇り風、横風、落雷、骨の戸の開け閉め、鏡の向き) は試練の側 (StormBackTrials ほか) が置く
public final class StormBirdBuilder {
    public static final BackShape BIRD = new BackShape("/data/skyabyss/storm_back/bird.bin");

    private StormBirdBuilder() {
    }

    public static void build(StormKit kit) {
        long t0 = System.currentTimeMillis();
        if (BIRD.exists()) {
            int placed = BIRD.place(kit.level());
            SkyAbyss.LOGGER.info("Placed the storm bird: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
        } else {
            // 形がまだ無い (取り込みの前)。部屋だけを空に建てる
            SkyAbyss.LOGGER.warn("storm_back/bird.bin is missing; building the rooms without the bird");
        }
        buildRest(kit);
    }

    // 起動時: 鳥の体を何 tick かに分けて置く仕事 (BackBuildJobs)。形が無ければ空の仕事
    public static jp.revati.skyabyss.titan.BackBuildJobs.Job job(StormKit kit) {
        return BIRD.exists() ? jp.revati.skyabyss.titan.BackBuildJobs.afterAsync(BIRD::preload, () -> BIRD.job(kit.level()))
                : jp.revati.skyabyss.titan.BackBuildJobs.empty();
    }

    // 鳥の体を置いたあと (部屋を掘る・中身を建てる)
    public static void buildRest(StormKit kit) {
        jp.revati.skyabyss.titan.BackBuildJobs.Steps s = new jp.revati.skyabyss.titan.BackBuildJobs.Steps("storm back bird");
        restSteps(s, kit);
        while (!s.step(Long.MAX_VALUE)) {
            // 最後まで
        }
    }

    // buildRest の手順 (順は同じ。tick に分けて進められる)
    public static void restSteps(jp.revati.skyabyss.titan.BackBuildJobs.Steps s, StormKit kit) {
        long[] t0 = new long[1];
        int[] n = new int[3];
        // 仕上げが触る範囲 (背の監査の範囲) のチャンクを先に読み込む (読みに行くと、その場で tick が止まる。道・広場・甲板で 0.5〜0.9 秒)
        s.thenJob("load area", () -> jp.revati.skyabyss.titan.BackBuildJobs.loadArea(kit.level(), -330, -190, 330, 270));
        s.then("seal", () -> {
            t0[0] = System.currentTimeMillis();
            // 体の中も外の道も建てない (2026-11-07「もともとの建築は消して、完全に閉じて」)。尾羽の上の待機場所は trial/ArrivalDeck、
            // 形に残る羽根の門の輪は継ぎ当てで閉じる (titan/BackSeal)
            jp.revati.skyabyss.titan.BackSeal.apply(kit.level(), "storm_back");
        });
        // 鳥の背・翼の上の小さな縦穴 (落ちると出られない) を埋める (詰み監査)
        s.then("wells", () -> {
            if (BIRD.exists()) {
                jp.revati.skyabyss.titan.WellFiller.fill(kit.level(), -330, 330, -190, 200, BIRD::top, 140);
            }
            SkyAbyss.LOGGER.info("Dressed the storm bird's outside in {} ms", System.currentTimeMillis() - t0[0]);
        });
    }

    // 鳥の上 (いちばん上のブロック)。形が無ければ到着の高さ
    public static int top(int x, int z) {
        if (!BIRD.exists()) {
            return ARRIVAL.getY() - 1;
        }
        int t = BIRD.top(x, z);
        return t == Integer.MIN_VALUE ? ARRIVAL.getY() - 1 : t;
    }

    // 昇り風の穴 (3x3 の銅の格子と、その下の灯り)
    static void vent(StormKit kit, BlockPos c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.put(c.getX() + dx, c.getY(), c.getZ() + dz, StormKit.GRATE);
            }
        }
        kit.put(c.getX(), c.getY() - 1, c.getZ(), StormKit.LAMP);
    }

    // 避雷の柱 (根元 base、高さ 6)。専用のブロック: 根元の台、段の電球 3 つ、軸、先の針 (12 の 120)
    static void rod(StormKit kit, BlockPos base, int charge) {
        kit.put(base.getX(), base.getY(), base.getZ(), StormBackSetup.ROD_BASE.get().defaultBlockState());
        for (int i = 0; i < 3; i++) {
            kit.put(base.getX(), base.getY() + 1 + i, base.getZ(), StormBackSetup.ROD_BULB.get().defaultBlockState()
                    .setValue(StormBlocks.RodBulb.LIT, charge > i));
        }
        kit.put(base.getX(), base.getY() + 4, base.getZ(), StormBackSetup.ROD_SHAFT.get().defaultBlockState());
        kit.put(base.getX(), base.getY() + 5, base.getZ(), StormBackSetup.ROD_TIP.get().defaultBlockState());
    }

    // 体の外の封じた竜骨の間 (trial/raid/InstanceArenas。版 34): 竜骨の材質で詰めた箱 → 前室 (休み場 heart) → 入口 → 竜骨の間 → 柱・風穴・灯り →
    // 心臓の担当の形 (StormHeartArena.buildShape) → 大門 → 焚き火と碑文
    public static void buildHeartInstance(net.minecraft.server.level.ServerLevel level) {
        heartFill(level).runAll();
        buildHeartInstance(level, false);
    }

    // 竜骨の間のまわりを竜骨の石で詰める箱 (tick に分けて詰められる。trial/raid/InstanceArenas.addHeartSteps)
    public static jp.revati.skyabyss.trial.raid.InstanceArenas.Fill heartFill(net.minecraft.server.level.ServerLevel level) {
        StormKit kit = new StormKit(level);
        int r0 = HEART_ROOM_RADIUS + 4;
        return new jp.revati.skyabyss.trial.raid.InstanceArenas.Fill(HEART_ROOM.getX() - r0, HEART_ROOM.getX() + r0, KEEL_ANTE.z1() - 4, HEART_ROOM.getZ() + r0, HEART_ROOM.getY() - 4,
                HEART_ROOM_CEILING + 3, (x, y, z) -> kit.put(x, y, z, StormKit.wall(Style.KEEL, x, y, z)));
    }

    // 詰めた箱の中に、元の形を掘って建てる (withFill = 先に箱を詰める)
    public static void buildHeartInstance(net.minecraft.server.level.ServerLevel level, boolean withFill) {
        if (withFill) {
            heartFill(level).runAll();
        }
        StormKit kit = new StormKit(level);
        kit.room(KEEL_ANTE, Style.KEEL, 3);
        int hx = HEART_ROOM.getX();
        // 竜骨の間の入口 (前室の南の口)
        for (int x = hx - 1; x <= hx + 1; x++) {
            for (int y = KEEL_ANTE.floor() + 1; y <= KEEL_ANTE.floor() + 3; y++) {
                kit.air(x, y, KEEL_ANTE.z2() + 1);
            }
        }
        // 竜骨の間 (円形、床 117、空気は 130 まで)。細かい形 (柱・風穴・帯電の床) は心臓の担当が建て直してよい
        int r = HEART_ROOM_RADIUS;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = HEART_ROOM.getX() + dx;
                int z = HEART_ROOM.getZ() + dz;
                if (d <= r + 0.5) {
                    // 舟の底の形 (中央の 1 段低い帯)
                    boolean keelLine = Math.abs(dx) <= 2;
                    kit.put(x, HEART_ROOM.getY(), z, keelLine ? StormKit.SMOOTH : StormKit.floor(Style.KEEL, x, z));
                    if (!keelLine) {
                        kit.put(x, HEART_ROOM.getY() + 1, z, StormKit.floor(Style.KEEL, x, z));
                    }
                    for (int y = HEART_ROOM.getY() + (keelLine ? 1 : 2); y <= HEART_ROOM_CEILING; y++) {
                        kit.air(x, y, z);
                    }
                    kit.put(x, HEART_ROOM_CEILING + 1, z, StormKit.wall(Style.KEEL, x, 0, z));
                } else if (d <= r + 1.6) {
                    for (int y = HEART_ROOM.getY(); y <= HEART_ROOM_CEILING + 1; y++) {
                        kit.solid(x, y, z, StormKit.wall(Style.KEEL, x, y, z));
                    }
                }
            }
        }
        // 前室の床 (118) と竜骨の間の外の床 (118) をつなぐ: 入口の下
        for (int x = hx - 1; x <= hx + 1; x++) {
            kit.put(x, KEEL_ANTE.floor(), KEEL_ANTE.z2() + 1, StormKit.floor(Style.KEEL, x, 0));
        }
        for (BlockPos p : HEART_RODS) {
            rod(kit, p.above(), 0);
        }
        for (BlockPos v : HEART_VENTS) {
            vent(kit, v.above());
        }
        // 肋の灯り
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            kit.solid(HEART_ROOM.getX() + (int) Math.round(Math.cos(a) * (r + 1)), HEART_ROOM.getY() + 4,
                    HEART_ROOM.getZ() + (int) Math.round(Math.sin(a) * (r + 1)), StormKit.LAMP);
        }
        jp.revati.skyabyss.titan.storm.heart.StormHeartArena.buildShape(level);
        StormBossGates.heart(level);
        for (StormBackLayout.Rest rest : RESTS) {
            if (rest.id().equals("heart")) {
                kit.campfire(rest.campfire());
            }
        }
    }

}

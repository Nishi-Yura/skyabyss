package jp.revati.skyabyss.trial.raid;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.BackBuildJobs;
import jp.revati.skyabyss.trial.TrialSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 巨神の心臓の間 (最終ボスの部屋) を、体の中ではなく、背の次元の区画 0 番の枠 (TrialSlot の番号 1 からの枠の手前。順路の部屋はここを使わない) に
// 一つだけ建てる封じた部屋 (docs/design/32 の 9 章)。心臓の戦いはグループごとの上書き (GroupOwned・RoomLocks) で同じ部屋を分け合うので、
// 部屋は背ごとに一つ。各巨神の心臓の配置 (TitanBackLayout・TideHeartArena・StormBackLayout・EmberBackLayout) は、元の座標をここへずらしてある。
//  - 心臓の間の中心の水平の位置は (HEART_X, HEART_Z)。床の高さは元のまま (巨神ごとに違う)
//  - 部屋のまわりは元の体の材質で詰めた箱 (水密)。そこへ元の部屋の掘り方・飾りをそのまま当てる。入口の外に、倒れた人が目を覚ます小さな前室 (休み場 heart) と大門
//  - 順路の段 4 (心臓) へ運ばれる人は、前室ではなく部屋の中の入口の側へ直に着く (heartEntry)。集合の 5 秒のあと封じて心臓が現れる
public final class InstanceArenas {
    private InstanceArenas() {
    }

    // 心臓の間の中心 (区画 0 番の枠。区画 1 番の枠は x = ORIGIN_X + STRIDE から)
    public static final int HEART_X = TrialSlot.ORIGIN_X + 256;
    public static final int HEART_Z = TrialSlot.ORIGIN_Z;
    // 建てるときに先に読み込む範囲 (中心から)
    static final int LOAD_R = 64;

    // 背を建てる手順に、心臓の間を建てる手順を足す (各巨神の背の建設から一行で呼ぶ)
    public static void addHeartSteps(BackBuildJobs.Steps s, ServerLevel level, String titan) {
        s.thenJob("heart arena area", () -> BackBuildJobs.loadArea(level, HEART_X - LOAD_R, HEART_Z - LOAD_R, HEART_X + LOAD_R, HEART_Z + LOAD_R));
        s.then("heart arena", () -> buildHeart(level, titan));
    }

    // 心臓の間をその場で建てる (背の建設・開発のコマンド・自己テスト)
    public static void buildHeart(ServerLevel level, String titan) {
        long t0 = System.nanoTime();
        switch (titan) {
            case Titans.STONE_WHALE -> jp.revati.skyabyss.titan.dungeon.HeartArenaBuilder.buildInstance(level);
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.heart.TideHeartArena.buildInstance(level);
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBirdBuilder.buildHeartInstance(level);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBodyBuilder.buildHeartInstance(level);
            default -> {
                return;
            }
        }
        SkyAbyss.LOGGER.info("Built the {} heart arena in the instance area in {} ms", titan, (System.nanoTime() - t0) / 1_000_000L);
    }

    // 心臓の間の戦いの箱 (技と狙いの範囲)
    public static AABB heartArena(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> jp.revati.skyabyss.titan.dungeon.TitanBackTrials.heartArena();
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.heart.TideHeartArena.arena();
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.heart.StormHeartArena.arena();
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.heart.EmberHeartArena.arena();
            default -> new AABB(BlockPos.ZERO);
        };
    }

    // 順路の段 4 で運ばれる所 (足の位置のブロック): 心臓の間の中の、入口の内側の床の上。なければ null
    @Nullable
    public static BlockPos heartEntry(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> new BlockPos(jp.revati.skyabyss.titan.dungeon.TitanBackLayout.HEART_CX, jp.revati.skyabyss.titan.dungeon.TitanBackLayout.FLOOR + 1,
                    jp.revati.skyabyss.titan.dungeon.TitanBackLayout.HEART_GATE_Z - 5);
            case Titans.TIDE_SHELL -> new BlockPos(jp.revati.skyabyss.titan.tide.heart.TideHeartArena.CX, jp.revati.skyabyss.titan.tide.heart.TideHeartArena.FLOOR,
                    jp.revati.skyabyss.titan.tide.heart.TideHeartArena.CZ + 10);
            case Titans.THUNDER_WING -> new BlockPos(jp.revati.skyabyss.titan.storm.heart.StormHeartArena.CX + 3, jp.revati.skyabyss.titan.storm.heart.StormHeartArena.FLOOR,
                    jp.revati.skyabyss.titan.storm.heart.StormHeartArena.CZ - 11);
            case Titans.EMBER -> BlockPos.containing(jp.revati.skyabyss.titan.ember.heart.EmberHeartArena.insideSpawn(jp.revati.skyabyss.titan.ember.heart.EmberHeartArena.CX + 0.5));
            default -> null;
        };
    }

    // 入口の内側から心臓の居場所への向き (yaw)
    public static float heartEntryYaw(String titan) {
        BlockPos p = heartEntry(titan);
        if (p == null) {
            return 0f;
        }
        Vec3 at = Vec3.atBottomCenterOf(p);
        Vec3 c = heartArena(titan).getCenter();
        return (float) (Math.toDegrees(Math.atan2(c.z - at.z, c.x - at.x)) - 90.0);
    }
}

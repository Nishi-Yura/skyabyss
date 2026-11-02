package jp.revati.skyabyss.story.ch5.layers;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssEntrance;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

// 深淵の四つの層の決まり (docs/design/36 の 8 章。設計図は データを直接直す（生成スクリプトはない）)。
//   層 = 章の区切り: 五章の一「骨の層」… 五章の四「灰の層」、五章の終わり「心臓」(section 0〜4)
//   層ごとに 野営 (camp_<層>) → 三本の道 → 印 (mark_<層>_<1..3>) → 門 (gate_<層>) → 主の間 (arena_<層>) → 下りの鎖 (descend_<層>) → 次の野営 (arrive_<次>)
//   メインの段 (main_abyss): <層>_camp (生き残りと話す) → <層>_marks (印を三つ) → <層>_lord (主を倒す)。灰の次は descend (心臓の間へ)
public final class Layers {
    public static final List<String> ORDER = List.of("bone", "salt", "storm", "ash");
    public static final int HEART = 4;
    // 層の帯 (y)。AbyssScaling.layerOf と同じ境
    static final int[] TOP = {256, 120, 0, -120};
    static final int ARENA_R = 14;

    private Layers() {
    }

    // 立っている所の区切り (0〜3 = 層、4 = 心臓。深淵の外は呼ばない)
    public static int sectionAt(BlockPos p) {
        int y = p.getY();
        if (y >= 120) {
            return 0;
        }
        if (y >= 0) {
            return 1;
        }
        if (y >= -120) {
            return 2;
        }
        if (y >= -240) {
            return 3;
        }
        return HEART;
    }

    public static int index(String layer) {
        return ORDER.indexOf(layer);
    }

    @Nullable
    public static String next(String layer) {
        int i = index(layer);
        return i >= 0 && i + 1 < ORDER.size() ? ORDER.get(i + 1) : null;
    }

    @Nullable
    public static String prev(String layer) {
        int i = index(layer);
        return i > 0 ? ORDER.get(i - 1) : null;
    }

    // 主の間の中 (中心の印から半径 ARENA_R + 1、床から 11)
    public static boolean inArena(String layer, BlockPos p) {
        BlockPos c = AbyssBuild.mark("arena_" + layer);
        if (c == null) {
            return false;
        }
        double dx = p.getX() + 0.5 - (c.getX() + 0.5), dz = p.getZ() + 0.5 - (c.getZ() + 0.5);
        return dx * dx + dz * dz <= (ARENA_R + 1) * (ARENA_R + 1) && p.getY() >= c.getY() - 1 && p.getY() <= c.getY() + 11;
    }

    public static AABB arenaBox(String layer) {
        BlockPos c = AbyssBuild.mark("arena_" + layer);
        if (c == null) {
            return new AABB(0, 0, 0, 0, 0, 0);
        }
        return new AABB(c).inflate(ARENA_R + 1, 0, ARENA_R + 1).expandTowards(0, 11, 0);
    }

    // 野営の部屋の中 (中心から 8 以内、床から 8)
    public static boolean inCamp(String layer, BlockPos p) {
        BlockPos c = AbyssBuild.mark("camp_" + layer);
        return c != null && Math.abs(p.getX() - c.getX()) <= 8 && Math.abs(p.getZ() - c.getZ()) <= 8 && p.getY() >= c.getY() - 1 && p.getY() <= c.getY() + 8;
    }

    // 箱の印 (<名前>_a と <名前>_b) の中
    public static boolean inBox(String name, BlockPos p) {
        BlockPos a = AbyssBuild.mark(name + "_a");
        BlockPos b = AbyssBuild.mark(name + "_b");
        return a != null && b != null && p.getX() >= a.getX() && p.getX() <= b.getX() && p.getY() >= a.getY() && p.getY() <= b.getY()
                && p.getZ() >= a.getZ() && p.getZ() <= b.getZ();
    }

    // ---------------------------------------------------------------- メインの段
    public static String stage(ServerPlayer p) {
        String s = TownGuests.stageOf(p, AbyssEntrance.QUEST);
        return s == null ? "" : s;
    }

    // メインの段の並び (段の名前 → 何番目か)。段のない人は -1、終えた人は大きい数
    static final List<String> STAGES = List.of("gate", "bone_camp", "bone_marks", "bone_lord", "salt_camp", "salt_marks", "salt_lord",
            "storm_camp", "storm_marks", "storm_lord", "ash_camp", "ash_marks", "ash_lord", "descend", "warden", "heart", "report");

    public static List<String> stagesForTest() {
        return STAGES;
    }

    public static int stageIndex(ServerPlayer p) {
        String s = stage(p);
        int i = STAGES.indexOf(s);
        if (i >= 0) {
            return i;
        }
        jp.revati.skyabyss.quest.QuestLog.Entry e = jp.revati.skyabyss.quest.QuestLog.of(p).entry(AbyssEntrance.QUEST);
        return e != null && e.done ? STAGES.size() : -1;
    }

    // その層の段を越えたか (印の段・主の段)
    public static boolean pastMarks(ServerPlayer p, String layer) {
        return stageIndex(p) > STAGES.indexOf(layer + "_marks");
    }

    public static boolean pastLord(ServerPlayer p, String layer) {
        return stageIndex(p) > STAGES.indexOf(layer + "_lord");
    }
}

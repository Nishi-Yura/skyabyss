package jp.revati.skyabyss.story.ch6;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

// 前の自分の記憶の断片 (docs/design/46 の 4.9)。伝承の頁 ch6/memory_1〜12 (data/skyabyss/lore/entry/ch6/)。区画ごとに 3 つ:
//   一つ目  前の自分と初めて話し終えた (会話の節 ch6_<区画>_meet3。Quests.onTalk から)
//   二つ目  区画の隠れた所の「記憶の石」に触れた (MoonMemoryStoneBlock。月の世界の版 10 で置く)
//   三つ目  区画の主を倒した (ふだんでも位相の挑戦でも。MoonTrial.finish から)
// 十二がそろうと ch6/watcher_truth (見る者の正体)、月の核を止めて語りを見終えると ch6/core_reason (核が時間を閉じた理由)。
// 位相の挑戦の段 5 を初めて越えると、その主の名の入った記念の銀の札 ch6/keepsake_<主>
public final class MoonMemories {
    public static final List<String> SECTIONS = List.of("valley", "cliff", "field", "rift");
    public static final String TRUTH = "ch6/watcher_truth";
    public static final String CORE = "ch6/core_reason";
    public static final int COUNT = 12;
    // 記憶の石を置く所の近くの印 (区画の決まりの道から少し外れた所)
    static final String[] STONE_NEAR = {"hole_bottom", "ledge_a", "monolith_4", "pillar_3"};

    private MoonMemories() {
    }

    // 区画 section (0〜3) の kind 番目 (0 = 前の自分・1 = 記憶の石・2 = 主)
    public static String id(int section, int kind) {
        return "ch6/memory_" + (section * 3 + kind + 1);
    }

    public static int count(ServerPlayer p) {
        int n = 0;
        for (int i = 1; i <= COUNT; i++) {
            if (Lore.state(p).has("ch6/memory_" + i)) {
                n++;
            }
        }
        return n;
    }

    static void give(ServerPlayer p, String id) {
        if (!Lore.unlock(p, id, true)) {
            return;
        }
        int n = count(p);
        Notice.narrate(p, "message.skyabyss.moon.memory", n, COUNT);
        if (n >= COUNT && Lore.unlock(p, TRUTH, true)) {
            Notice.narrate(p, "message.skyabyss.moon.memory_all");
        }
    }

    // ---------------------------------------------------------------- きっかけ
    public static void onTalk(ServerPlayer p, String node) {
        for (int s = 0; s < SECTIONS.size(); s++) {
            if (node.equals("ch6_" + SECTIONS.get(s) + "_meet3")) {
                give(p, id(s, 0));
            }
        }
    }

    public static void onStone(ServerPlayer p, int section) {
        give(p, id(section, 1));
    }

    public static void onLord(List<ServerPlayer> players, String lord) {
        int s = SECTIONS.indexOf(lord);
        if (s < 0) {
            return;
        }
        for (ServerPlayer p : players) {
            give(p, id(s, 2));
        }
    }

    public static void onCore(ServerPlayer p) {
        Lore.unlock(p, CORE, true);
    }

    public static void onKeepsake(ServerPlayer p, String lord) {
        if (Lore.unlock(p, "ch6/keepsake_" + lord, true)) {
            Notice.narrate(p, "message.skyabyss.moon_trial.keepsake", net.minecraft.network.chat.Component.translatable("moon_trial.skyabyss." + lord));
        }
    }

    // ---------------------------------------------------------------- 記憶の石を置く (月の世界の版 10)
    static BlockPos near(int section) {
        String k = STONE_NEAR[section];
        return switch (section) {
            case 0 -> jp.revati.skyabyss.story.ch6.valley.MoonValley.mark(k);
            case 1 -> jp.revati.skyabyss.story.ch6.cliff.MoonCliff.mark(k);
            case 2 -> jp.revati.skyabyss.story.ch6.field.MoonField.mark(k);
            default -> jp.revati.skyabyss.story.ch6.rift.MoonRift.mark(k);
        };
    }

    static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP);
    }

    // 印のまわりで、石を置けて、その横に人が立てる所 (近い順)
    @Nullable
    static BlockPos spot(ServerLevel level, int section) {
        BlockPos c = near(section);
        for (int r = 2; r <= 7; r++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos p = c.offset(dx, dy, dz);
                        if (standable(level, p) && standable(level, p.relative(net.minecraft.core.Direction.NORTH))) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    static void place(ServerLevel level) {
        for (int s = 0; s < SECTIONS.size(); s++) {
            // 流し直さなかった区画には前の石が残っている (二つ目を置かない)
            if (stoneAt(level, s) != null) {
                continue;
            }
            BlockPos p = spot(level, s);
            if (p != null) {
                level.setBlock(p, jp.revati.skyabyss.story.ch6.rift.RiftBlocks.MEMORY_STONE.get().defaultBlockState()
                        .setValue(MoonMemoryStoneBlock.SECTION, s), 3);
            }
        }
    }

    // 置いた記憶の石の所 (自己テスト)
    @Nullable
    public static BlockPos stoneAt(ServerLevel level, int section) {
        BlockPos c = near(section);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -4, -8), c.offset(8, 4, 8))) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof MoonMemoryStoneBlock && st.getValue(MoonMemoryStoneBlock.SECTION) == section) {
                return p.immutable();
            }
        }
        return null;
    }
}

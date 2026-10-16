package jp.revati.skyabyss.npc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

// 好感度 (プレイヤー × NPC の役)。-100〜100。段階で呼び方を変え、会話の条件 affinity で使う。
//   〜-1 よそよそしい / 0〜9 見知らぬ / 10〜29 顔見知り / 30〜59 打ち解けた / 60〜 信頼
public final class Affinity {
    public static final int MIN = -100;
    public static final int MAX = 100;
    // 一日の最初の会話で上がる量
    public static final int DAILY_TALK = 1;
    // 手を上げられたときに下がる量 (5 秒に一度まで)
    public static final int STRUCK = -3;

    private Affinity() {
    }

    public static int get(Player player, String role) {
        return NpcBonds.of(player).affinity(role);
    }

    public static int add(ServerPlayer player, String role, int amount) {
        NpcBonds.Bond bond = NpcBonds.of(player).bond(role);
        bond.affinity = Mth.clamp(bond.affinity + amount, MIN, MAX);
        NpcBonds.sync(player);
        return bond.affinity;
    }

    public static void set(ServerPlayer player, String role, int value) {
        NpcBonds.of(player).bond(role).affinity = Mth.clamp(value, MIN, MAX);
        NpcBonds.sync(player);
    }

    // 段階 (0〜4)。画面の呼び方 gui.skyabyss.npc.tier.<段階> と、印の数に使う
    public static int tier(int affinity) {
        if (affinity < 0) {
            return 0;
        }
        if (affinity < 10) {
            return 1;
        }
        if (affinity < 30) {
            return 2;
        }
        if (affinity < 60) {
            return 3;
        }
        return 4;
    }
}

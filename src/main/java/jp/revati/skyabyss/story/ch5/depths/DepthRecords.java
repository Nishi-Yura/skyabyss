package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// 深みの記録 (docs/design/36 の 9・decision 260)。心臓の下の深さ (beyond) の一番深い数字を、組ごと・一人ごとに永久に残す。
//   節目   MILESTONES の深さ (心臓の下の 100・250・500…) を初めて越えたとき、組の人と越えた人に一度だけ報酬 (経験値の段)
//   順位   順位の石 (心臓の間) の右クリックで、組と一人の上位五つを出す (文字だけ。画面は後で)
//   数字   心臓の下の深さ。表示の深さは DepthPlan.totalDepth (心臓の間の底から数えた数字)
public final class DepthRecords {
    public static final int[] MILESTONES = {100, 250, 500, 1000, 2000, 4000, 8000};
    public static final int TOP = 5;

    private DepthRecords() {
    }

    // 心臓の下の深さに届いた。記録を更新し、新しく越えた節目を返す (報酬は配る)
    public static List<Integer> reach(MinecraftServer server, ServerPlayer p, int beyond) {
        DepthData d = DepthData.get(server);
        UUID group = Groups.keyOf(p);
        boolean dirty = false;
        if (beyond > d.groupBest.getOrDefault(group, 0)) {
            d.groupBest.put(group, beyond);
            dirty = true;
        }
        if (beyond > d.playerBest.getOrDefault(p.getUUID(), 0)) {
            d.playerBest.put(p.getUUID(), beyond);
            dirty = true;
        }
        List<Integer> out = new ArrayList<>();
        int gm = d.groupMile.getOrDefault(group, 0);
        int gmNew = milestoneCount(gm, beyond);
        for (int i = gm; i < gmNew; i++) {
            out.add(MILESTONES[i]);
            dirty = true;
            d.groupMile.put(group, i + 1);
            for (ServerPlayer m : Groups.online(p.serverLevel(), group)) {
                reward(m, i + 1, true);
            }
        }
        int pm = d.playerMile.getOrDefault(p.getUUID(), 0);
        int pmNew = milestoneCount(pm, beyond);
        for (int i = pm; i < pmNew; i++) {
            dirty = true;
            d.playerMile.put(p.getUUID(), i + 1);
            reward(p, i + 1, false);
        }
        if (dirty) {
            d.setDirty();
        }
        return out;
    }

    // 節目の報酬 (経験値の段。組の人は一段少なく)
    static void reward(ServerPlayer p, int milestone, boolean group) {
        int levels = 2 + 2 * milestone - (group ? 1 : 0);
        p.giveExperienceLevels(Math.max(1, levels));
        Notice.gain(p, Component.translatable(group ? "message.skyabyss.depths.milestone_group" : "message.skyabyss.depths.milestone", MILESTONES[milestone - 1]));
    }

    // 節目を受け取った数 (have まで受け取った人が、深さ beyond に届いたあと)。節目は昇順、越えた分は全部数える
    public static int milestoneCount(int have, int beyond) {
        int n = have;
        while (n < MILESTONES.length && beyond >= MILESTONES[n]) {
            n++;
        }
        return n;
    }

    public static int groupBest(MinecraftServer server, UUID group) {
        return DepthData.get(server).groupBest.getOrDefault(group, 0);
    }

    public static int playerBest(MinecraftServer server, UUID player) {
        return DepthData.get(server).playerBest.getOrDefault(player, 0);
    }

    // 順位の石の文字 (組と一人の上位。見る人に送る)
    public static void show(MinecraftServer server, ServerPlayer p) {
        DepthData d = DepthData.get(server);
        p.sendSystemMessage(Component.translatable("message.skyabyss.depths.rank_head"));
        p.sendSystemMessage(Component.translatable("message.skyabyss.depths.rank_group"));
        int i = 1;
        for (Map.Entry<UUID, Integer> e : top(d.groupBest)) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.depths.rank_line", i++, Component.literal(short_(e.getKey())),
                    DepthPlan.totalDepth(e.getValue())));
        }
        if (d.groupBest.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.depths.rank_none"));
        }
        p.sendSystemMessage(Component.translatable("message.skyabyss.depths.rank_player"));
        i = 1;
        for (Map.Entry<UUID, Integer> e : top(d.playerBest)) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.depths.rank_line", i++, Component.literal(nameOf(server, e.getKey())),
                    DepthPlan.totalDepth(e.getValue())));
        }
        if (d.playerBest.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.depths.rank_none"));
        }
    }

    // 上位 TOP。同じ深さは先に記録した方を上にする (値の大きい順、同じなら鍵の文字の順)
    static List<Map.Entry<UUID, Integer>> top(Map<UUID, Integer> m) {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : a.getKey().toString().compareTo(b.getKey().toString());
        });
        return list.subList(0, Math.min(TOP, list.size()));
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null ? p.getGameProfile().getName() : short_(id);
    }

    private static String short_(UUID id) {
        return id.toString().substring(0, 8);
    }
}

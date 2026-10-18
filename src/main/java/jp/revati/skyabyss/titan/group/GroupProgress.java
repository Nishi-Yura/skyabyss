package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// グループごとの進み (BackGroupData.Record) を引く・戻す・時間で手放す、ための小さな道具。
//  - いる間: そのグループの誰かが背中にいるあいだ、10 秒ごとに「最後にいた時刻」を更新する (GroupEvents)
//  - 放置のリセット: そのグループの誰も背中にいないまま 3 分 (IDLE_TICKS) たち、進みがあれば、idleGroups に出る。
//    各背中は、そのグループの試練を戻す (自分の reset を呼ぶ)
//  - 世界のリセット (背中に誰もいなくなって久しいとき): 進みのない古い記録を捨てる (prune)。進みのあるグループの記録は残す
public final class GroupProgress {
    // 背中に誰もいなくなってから、そのグループの試練を戻すまで (3 分)
    public static final int IDLE_TICKS = 20 * 60 * 3;
    // 進みのない記録を捨てるまで (10 分)
    public static final int PRUNE_TICKS = 20 * 60 * 10;

    private GroupProgress() {
    }

    public static BackGroupData.Record of(ServerLevel level, UUID group) {
        return BackGroupData.get(level).of(group);
    }

    public static BackGroupData.Record of(ServerLevel level, ServerPlayer player) {
        return of(level, Groups.keyOf(player));
    }

    public static int epoch(ServerLevel level, UUID group) {
        return of(level, group).epoch();
    }

    // 進みを最初に戻す: 番号を進め、進みと扉の上書きを消し、グループの人の画面を直す
    public static void reset(ServerLevel level, UUID group) {
        of(level, group).resetProgress();
        GroupOverlays.refresh(level, group);
    }

    // そのグループの動いている人 (観戦者と、長く動いていない人を除く) がその背中にいるか
    public static boolean present(ServerLevel level, UUID group) {
        for (ServerPlayer player : Groups.online(level, group)) {
            if (!player.isSpectator() && Activity.active(player, Activity.PRESENT_TICKS)) {
                return true;
            }
        }
        return false;
    }

    // そのグループの誰かが背中にいない (動いている人がその背中にいない) まま IDLE_TICKS たち、進みがあるグループ
    public static List<UUID> idleGroups(ServerLevel level) {
        List<UUID> idle = new ArrayList<>();
        long now = level.getGameTime();
        BackGroupData data = BackGroupData.get(level);
        for (UUID id : data.ids()) {
            BackGroupData.Record r = data.peek(id);
            if (r != null && r.lastSeen() == 0) {
                // まだ一度も背中で見ていない記録 (できたばかり・コマンドや自己テストで進めた): ここから数える。0 のままだと、すぐ放置と見なされる
                r.touch(now);
                continue;
            }
            if (r != null && r.hasProgress() && now - r.lastSeen() >= IDLE_TICKS && !present(level, id)) {
                idle.add(id);
            }
        }
        return idle;
    }

    // いま背中の中で続けているグループの数 (巨神の目覚めを待たせる)。数えるのは
    //  - 動いている人 (観戦者と、長く動いていない人を除く) が背中にいるグループ
    //  - 試練が進んでいて、心臓をまだ止めていないグループ (全員が背中を離れていても、放置のリセット (3 分) までは続けている扱い。ログアウト・再起動をまたぐ)
    // 心臓を止めたグループ (運び出されたあと) と、進みのないグループは数えない
    public static int activeInside(ServerLevel back) {
        java.util.Set<UUID> groups = new java.util.HashSet<>();
        for (ServerPlayer player : back.players()) {
            if (!player.isSpectator() && Activity.active(player, Activity.PRESENT_TICKS)) {
                groups.add(Groups.keyOf(player));
            }
        }
        BackGroupData data = BackGroupData.get(back);
        for (UUID id : data.ids()) {
            BackGroupData.Record r = data.peek(id);
            if (r != null && r.hasProgress() && !r.isHeartDefeated()) {
                groups.add(id);
            }
        }
        return groups.size();
    }

    // 背中にいる人のグループの「最後にいた時刻」を更新する (10 秒ごとに GroupEvents が呼ぶ)
    static void touch(ServerLevel level) {
        long now = level.getGameTime();
        BackGroupData data = BackGroupData.get(level);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && Activity.active(player, Activity.PRESENT_TICKS)) {
                data.of(Groups.keyOf(player)).touch(now);
            }
        }
    }

    // 進みのない古い記録を捨てる
    static void prune(ServerLevel level) {
        long now = level.getGameTime();
        BackGroupData data = BackGroupData.get(level);
        for (UUID id : data.ids()) {
            BackGroupData.Record r = data.peek(id);
            if (r != null && !r.hasProgress() && now - r.lastSeen() >= PRUNE_TICKS && Groups.online(level, id).isEmpty()) {
                data.remove(id);
            }
        }
    }
}

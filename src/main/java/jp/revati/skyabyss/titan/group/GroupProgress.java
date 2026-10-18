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

    // そのグループの誰かが背中にいない (オンラインでその背中にいる人がいない) まま IDLE_TICKS たち、進みがあるグループ
    public static List<UUID> idleGroups(ServerLevel level) {
        List<UUID> idle = new ArrayList<>();
        long now = level.getGameTime();
        BackGroupData data = BackGroupData.get(level);
        for (UUID id : data.ids()) {
            BackGroupData.Record r = data.peek(id);
            if (r != null && r.hasProgress() && now - r.lastSeen() >= IDLE_TICKS && Groups.online(level, id).isEmpty()) {
                idle.add(id);
            }
        }
        return idle;
    }

    // 背中にいる人のグループの「最後にいた時刻」を更新する (10 秒ごとに GroupEvents が呼ぶ)
    static void touch(ServerLevel level) {
        long now = level.getGameTime();
        BackGroupData data = BackGroupData.get(level);
        for (ServerPlayer player : level.players()) {
            data.of(Groups.keyOf(player)).touch(now);
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

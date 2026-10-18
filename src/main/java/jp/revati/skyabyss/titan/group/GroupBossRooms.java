package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 一つのボスの部屋 (心臓の間・中ボスの広間など) の、グループごとの GroupBossRoom をまとめる。
// 部屋に入ったグループは自分の GroupBossRoom を持ち、同じ空間で別々に戦う。10 tick ごとに tick を呼ぶ
public final class GroupBossRooms {
    private final Map<UUID, GroupBossRoom> rooms = new LinkedHashMap<>();

    public GroupBossRoom of(UUID group) {
        return rooms.computeIfAbsent(group, GroupBossRoom::new);
    }

    @Nullable
    public GroupBossRoom peek(UUID group) {
        return rooms.get(group);
    }

    public List<GroupBossRoom> all() {
        return new ArrayList<>(rooms.values());
    }

    public boolean anyActive() {
        for (GroupBossRoom room : rooms.values()) {
            if (room.active()) {
                return true;
            }
        }
        return false;
    }

    // このボス (エンティティの UUID) を戦わせている部屋
    @Nullable
    public GroupBossRoom byBoss(UUID boss) {
        for (GroupBossRoom room : rooms.values()) {
            if (boss.equals(room.bossId())) {
                return room;
            }
        }
        return null;
    }

    // 戦っているグループと、いま部屋の中にいる人のグループを順に動かす。使い終わった (戦っていない・集合していない・誰もいない) 部屋は手放す
    public void tick(ServerLevel level, GroupBossRoom.Host host) {
        Set<UUID> groups = new LinkedHashSet<>(rooms.keySet());
        for (ServerPlayer player : host.playersInside(level)) {
            groups.add(Groups.keyOf(player));
        }
        for (UUID group : groups) {
            GroupBossRoom room = of(group);
            room.tick(level, host);
        }
        rooms.values().removeIf(r -> !r.active() && !r.gathering() && Groups.alive(level, r.group).isEmpty());
    }

    // すべての戦いを (告知なしで) 止めて片付ける (ピースフル・リセット)
    public void resetAll(ServerLevel level, GroupBossRoom.Host host, boolean announce) {
        for (GroupBossRoom room : all()) {
            room.reset(level, host, announce);
        }
    }

    public void clear() {
        for (GroupBossRoom room : rooms.values()) {
            room.clear();
        }
        rooms.clear();
    }
}

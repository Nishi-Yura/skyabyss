package jp.revati.skyabyss.titan.group;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// 背中の「ボスの部屋」の、グループ (Groups) ごとの戦い (旧 titan.BossRoom を、グループごとに持てるようにしたもの)。
// 一つのグループの一つの戦いが 1 インスタンス。同じ部屋 (空間) で、グループごとに別のボスが同時に戦える
// (ほかのグループには、そのボスは見えず・狙われず・傷を受けない: GroupOwned)。
// 水・溶岩・仕掛けの床など、部屋の物理が戦いで変わる心臓の間は、RoomLocks で一度に一グループにする (呼ぶ側で)。
//
// 決まり (旧 BossRoom と同じ):
//  - 部屋の内側にグループの人が入ると 5 秒の集合。閉じるとき、戸口と前室の人 (そのグループだけ) も中へ入れる
//  - 入口をそのグループにだけ封じ、ボスを湧かせる。挑む人数で強さが決まる
//  - 戦いの途中で倒れて前室から戻った参加者は、扉に触れると中へ通す
//  - ボスが部屋の外へ出たら中央へ戻す
//  - 内側にグループの人がいなくなって 3 秒たつと放棄としてボスを消し入口を開ける。撃破の報告がないまま消えたときも戻す (5 秒は湧かせ直さない)
// 参加者は「ボスが倒れる瞬間に部屋にいた、またはその前に部屋で戦い続けて倒れた人」(M6)。一瞬いただけの人は数えない:
// 戦いが始まったあとに入った人は、1.5 秒 (30 tick) 以上いたときだけ参加者になる。切断した人は、最後に部屋にいた記録で数える
// tick は 10 tick ごとに呼ぶ
public final class GroupBossRoom {
    public static final int GATHER_TICKS = 100;
    public static final int ABANDON_DELAY = 60;
    public static final int RETRY_COOLDOWN = 100;
    // 戦いの途中から入った人が参加者になるまでの、部屋にいた時間 (tick)
    public static final int JOIN_TICKS = 30;
    private static final int STEP = 10;

    // 部屋ごとに違うところ。group は戦っているグループの鍵
    public interface Host {
        // 戦いの部屋の内側にいる (生きていて観戦者でない) 人 (どのグループの人も)。グループの絞り込みは GroupBossRoom がする
        List<ServerPlayer> playersInside(ServerLevel level);

        // 入口をそのグループにだけ閉じる・開ける
        void seal(ServerLevel level, UUID group);

        void open(ServerLevel level, UUID group);

        // 閉じるとき: 戸口と前室にいる、そのグループの人を中へ入れる
        void pullIn(ServerLevel level, UUID group);

        // 戦いの途中: 扉に触れた、そのグループの参加者を中へ通す
        void letBack(ServerLevel level, UUID group, Set<UUID> participants);

        // ボスを湧かせる (失敗したら null)。湧かせたボスには GroupOwned.setGroupId(group) を addFreshEntity の前に入れること
        @Nullable
        Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers);

        // ボスの居場所と、離れてよい距離
        Vec3 home();

        double leash();

        // そのグループの人へ知らせる
        void broadcast(ServerLevel level, UUID group, Component message);

        // 残りの秒を、そのグループの人の画面に出す (1 秒ごと)
        void countdown(ServerLevel level, UUID group, int secondsLeft);

        // 放棄・やり直しで、そのグループの戦いの仕掛け (水など) を片付ける
        void cleanup(ServerLevel level, UUID group);

        // いま戦いを始められないか (ピースフルなど)
        boolean asleep(ServerLevel level);

        // このグループはもうこの部屋のボスを倒したか。倒していれば、人が部屋に残っていても (運び出しの前や、光の柱を待つあいだ)
        // 新しく集合・湧かせ直しをしない (倒したあとに心臓がまた出て、ボスゲージだけ残る不具合の元だった)。試練が戻って記録が消えれば、また始められる
        default boolean defeated(ServerLevel level, UUID group) {
            return false;
        }
    }

    // 戦っている部屋 (プレイヤーの死を知らせるため)
    private static final List<GroupBossRoom> ACTIVE = new java.util.ArrayList<>();

    // 部屋の中にいた参加者が倒れた (LivingDeathEvent から)。戦っている部屋のうち、その人を最後に中で見た部屋に数える
    public static void noteDeath(UUID player) {
        for (GroupBossRoom room : ACTIVE) {
            if (room.lastInside.contains(player)) {
                room.died.add(player);
            }
        }
    }

    public final UUID group;
    private boolean active;
    @Nullable
    private UUID bossId;
    private final Set<UUID> participants = new LinkedHashSet<>();
    // 戦いが始まったあとに部屋に入った人が、部屋にいた tick
    private final java.util.Map<UUID, Integer> joining = new java.util.HashMap<>();
    // 最後に部屋にいた人 (切断した人も参加者に数えるため)
    private final Set<UUID> lastInside = new LinkedHashSet<>();
    // 戦いの最中に部屋の中で倒れた人 (倒れて前室から戻っても参加者に数える)
    private final Set<UUID> died = new LinkedHashSet<>();
    private int empty;
    private int gather = -1;
    private int cooldown;
    private int challengers;

    public GroupBossRoom(UUID group) {
        this.group = group;
    }

    public boolean active() {
        return active;
    }

    public boolean gathering() {
        return gather >= 0;
    }

    @Nullable
    public UUID bossId() {
        return bossId;
    }

    public Set<UUID> participants() {
        return participants;
    }

    public int challengers() {
        return challengers;
    }

    private List<ServerPlayer> mine(ServerLevel level, Host host) {
        return host.playersInside(level).stream().filter(p -> Groups.isMember(p, group)).toList();
    }

    // 10 tick ごとに呼ぶ
    public void tick(ServerLevel level, Host host) {
        List<ServerPlayer> inside = mine(level, host);
        if (!active) {
            if (cooldown > 0) {
                cooldown -= STEP;
                return;
            }
            if (host.asleep(level) || host.defeated(level, group)) {
                gather = -1;
                return;
            }
            if (gather < 0) {
                if (!inside.isEmpty()) {
                    gather = GATHER_TICKS;
                    host.broadcast(level, group, ModText.battle("message.skyabyss.gate_gathering", GATHER_TICKS / 20));
                }
                return;
            }
            if (inside.isEmpty()) {
                gather = -1;
                return;
            }
            gather -= STEP;
            if (gather > 0) {
                if (gather % 20 == 0) {
                    host.countdown(level, group, gather / 20);
                }
                return;
            }
            gather = -1;
            host.pullIn(level, group);
            start(level, host, mine(level, host));
            return;
        }
        Entity boss = bossId == null ? null : level.getEntity(bossId);
        if (boss instanceof LivingEntity dying && dying.isDeadOrDying() && !dying.isRemoved()) {
            // 倒れる演出の最中 (撃破の報告は演出が終わってから出る)。部屋は閉じたまま待つ
            return;
        }
        if (boss == null || !boss.isAlive()) {
            // 撃破の報告がないまま消えた (ピースフルで消えた、コマンドで消したなど)。最初に戻し、すぐには湧かせ直さない
            reset(level, host, true);
            cooldown = RETRY_COOLDOWN;
            return;
        }
        Vec3 home = host.home();
        if (boss.distanceToSqr(home) > host.leash() * host.leash()) {
            boss.moveTo(home.x, home.y, home.z, boss.getYRot(), 0f);
        }
        lastInside.clear();
        for (ServerPlayer player : inside) {
            lastInside.add(player.getUUID());
        }
        if (inside.isEmpty()) {
            empty += STEP;
            if (empty >= ABANDON_DELAY) {
                reset(level, host, true);
            }
            return;
        }
        empty = 0;
        for (ServerPlayer player : inside) {
            UUID id = player.getUUID();
            if (participants.contains(id)) {
                continue;
            }
            // 戦いの途中から入った人は、少しの間いたときだけ参加者
            int t = joining.merge(id, STEP, Integer::sum);
            if (t >= JOIN_TICKS) {
                participants.add(id);
                joining.remove(id);
            }
        }
        host.letBack(level, group, participants);
    }

    // 戦いを始める (集合の終わり、またはテスト用のコマンドから)
    public boolean start(ServerLevel level, Host host, List<ServerPlayer> inside) {
        if (active) {
            return false;
        }
        Mob boss = host.spawnBoss(level, group, inside);
        if (boss == null) {
            return false;
        }
        active = true;
        ACTIVE.add(this);
        empty = 0;
        gather = -1;
        challengers = Math.max(1, inside.size());
        participants.clear();
        joining.clear();
        died.clear();
        lastInside.clear();
        for (ServerPlayer player : inside) {
            participants.add(player.getUUID());
            lastInside.add(player.getUUID());
        }
        bossId = boss.getUUID();
        host.seal(level, group);
        return true;
    }

    // ボスを消して入口を開ける (放棄・やり直し)
    public void reset(ServerLevel level, Host host, boolean announce) {
        boolean wasActive = active;
        if (bossId != null) {
            Entity boss = level.getEntity(bossId);
            if (boss != null) {
                boss.discard();
            }
        }
        active = false;
        ACTIVE.remove(this);
        bossId = null;
        participants.clear();
        joining.clear();
        died.clear();
        lastInside.clear();
        empty = 0;
        gather = -1;
        host.cleanup(level, group);
        host.open(level, group);
        if (announce && wasActive) {
            host.broadcast(level, group, ModText.deny("message.skyabyss.heart_abandoned"));
        }
    }

    // ボスが倒れた。いま戦っているボスなら参加者を返して終える。違えば null。
    // 参加者 = 戦いの参加者 + 倒れる瞬間に部屋にいた人 + 直前まで部屋にいて切断した人
    @Nullable
    public Set<UUID> finish(ServerLevel level, Host host, UUID boss) {
        if (!active || !boss.equals(bossId)) {
            return null;
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (ServerPlayer player : mine(level, host)) {
            ids.add(player.getUUID());
        }
        for (UUID id : died) {
            ids.add(id);
        }
        for (UUID id : lastInside) {
            if (level.getServer().getPlayerList().getPlayer(id) == null) {
                ids.add(id);
            }
        }
        active = false;
        ACTIVE.remove(this);
        bossId = null;
        participants.clear();
        joining.clear();
        died.clear();
        lastInside.clear();
        empty = 0;
        gather = -1;
        host.cleanup(level, group);
        host.open(level, group);
        return ids;
    }

    // サーバーが止まったとき (状態はメモリにだけある)
    public void clear() {
        active = false;
        ACTIVE.remove(this);
        bossId = null;
        participants.clear();
        joining.clear();
        died.clear();
        lastInside.clear();
        empty = 0;
        gather = -1;
        cooldown = 0;
        challengers = 0;
    }
}

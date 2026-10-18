package jp.revati.skyabyss.titan;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// 背中の「ボスの部屋」の共通の進み方 (08 の 10.2節の TitanBackBattleRoom / TitanBackBossRoom の、心臓の間の分)。
// 岩鯨の心臓の間 (TitanBackTrials.tickHeart) と同じ決まりを、巨神ごとの部屋 (Host) に使い回せる形にした:
//  - 部屋の内側に人が入ると、5 秒の集合 (残りを画面に出す)。閉じるとき、戸口と前室の人も中へ入れる
//  - 入口を封じてボスを湧かせる。挑む人数で強さが決まる
//  - 戦いの途中で倒れて前室から戻った参加者は、扉に触れると中へ通す
//  - ボスが部屋の外へ出たら中央へ戻す
//  - 内側に誰もいなくなって 3 秒たつと、放棄としてボスを消し入口を開ける。撃破の報告がないまま消えたときも戻す (5 秒は湧かせ直さない)
// 岩鯨の心臓の間はまだ TitanBackTrials のままで、この部品を使うのは潮殻の心臓から (岩鯨の動きは変えていない)。
// tick は 10 tick ごとに呼ぶ
public final class BossRoom {
    public static final int GATHER_TICKS = 100;
    public static final int ABANDON_DELAY = 60;
    public static final int RETRY_COOLDOWN = 100;
    private static final int STEP = 10;

    // 部屋ごとに違うところ
    public interface Host {
        // 戦いの部屋の内側にいる (生きていて観戦者でない) 人
        List<ServerPlayer> playersInside(ServerLevel level);

        // 入口を閉じる・開ける
        void seal(ServerLevel level);

        void open(ServerLevel level);

        // 閉じるとき: 戸口と前室の人を中へ入れる
        void pullIn(ServerLevel level);

        // 戦いの途中: 扉に触れた参加者を中へ通す
        void letBack(ServerLevel level, Set<UUID> participants);

        // ボスを湧かせる (失敗したら null)
        @Nullable
        Mob spawnBoss(ServerLevel level, List<ServerPlayer> challengers);

        // ボスの居場所と、離れてよい距離
        Vec3 home();

        double leash();

        // 部屋の近くの人へ知らせる
        void broadcast(ServerLevel level, Component message);

        // 残りの秒を画面に出す (1 秒ごと)
        void countdown(ServerLevel level, int secondsLeft);

        // 放棄・やり直しで、部屋の仕掛け (水など) を片付ける
        void cleanup(ServerLevel level);

        // いま戦いを始められないか (ピースフルなど)
        boolean asleep(ServerLevel level);
    }

    private boolean active;
    @Nullable
    private UUID bossId;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private int empty;
    private int gather = -1;
    private int cooldown;
    private int challengers;

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

    // 10 tick ごとに呼ぶ
    public void tick(ServerLevel level, Host host) {
        List<ServerPlayer> inside = host.playersInside(level);
        if (!active) {
            if (cooldown > 0) {
                cooldown -= STEP;
                return;
            }
            if (host.asleep(level)) {
                gather = -1;
                return;
            }
            if (gather < 0) {
                if (!inside.isEmpty()) {
                    gather = GATHER_TICKS;
                    host.broadcast(level, ModText.battle("message.skyabyss.gate_gathering", GATHER_TICKS / 20));
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
                    host.countdown(level, gather / 20);
                }
                return;
            }
            gather = -1;
            host.pullIn(level);
            start(level, host, host.playersInside(level));
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
        if (inside.isEmpty()) {
            empty += STEP;
            if (empty >= ABANDON_DELAY) {
                reset(level, host, true);
            }
            return;
        }
        empty = 0;
        for (ServerPlayer player : inside) {
            participants.add(player.getUUID());
        }
        host.letBack(level, participants);
    }

    // 戦いを始める (集合の終わり、またはテスト用のコマンドから)
    public boolean start(ServerLevel level, Host host, List<ServerPlayer> inside) {
        if (active) {
            return false;
        }
        Mob boss = host.spawnBoss(level, inside);
        if (boss == null) {
            return false;
        }
        active = true;
        empty = 0;
        gather = -1;
        challengers = Math.max(1, inside.size());
        participants.clear();
        for (ServerPlayer player : inside) {
            participants.add(player.getUUID());
        }
        bossId = boss.getUUID();
        host.seal(level);
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
        bossId = null;
        participants.clear();
        empty = 0;
        gather = -1;
        host.cleanup(level);
        host.open(level);
        if (announce && wasActive) {
            host.broadcast(level, ModText.deny("message.skyabyss.heart_abandoned"));
        }
    }

    // ボスが倒れた。いま戦っているボスなら参加者 (部屋にいる人を足して) を返して終える。違えば null
    @Nullable
    public Set<UUID> finish(ServerLevel level, Host host, UUID boss) {
        if (!active || !boss.equals(bossId)) {
            return null;
        }
        Set<UUID> ids = new LinkedHashSet<>(participants);
        for (ServerPlayer player : host.playersInside(level)) {
            ids.add(player.getUUID());
        }
        active = false;
        bossId = null;
        participants.clear();
        empty = 0;
        gather = -1;
        host.cleanup(level);
        host.open(level);
        return ids;
    }

    // サーバーが止まったとき (状態はメモリにだけある)
    public void clear() {
        active = false;
        bossId = null;
        participants.clear();
        empty = 0;
        gather = -1;
        cooldown = 0;
        challengers = 0;
    }
}

package jp.revati.skyabyss.entity;

import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

// 心臓の登場と倒れる演出の始まり・終わりを知らせるイベント (NeoForge.EVENT_BUS。サーバー側で投げる)。
// 演出のカメラの仕組みは、これを受けて近くの人の画面を動かす。長さ (tick) は kind が INTRO_* なら HeartBoss.INTRO_TICKS、DEATH_* なら DEATH_TICKS。
// 演出のあいだの位置は boss.focusPoint()、進み具合は boss.cinematicProgress(partialTick) (どちらも両側で読める) で取る。
// 倒れる演出の終わり (DEATH_END) の直後に心臓は消える。撃破の報酬や扉の動きは、この直前に出る。
public class HeartCinematicEvent extends Event {
    public enum Kind { INTRO_START, INTRO_END, DEATH_START, DEATH_END }

    private final HeartBoss boss;
    private final Kind kind;
    private final int durationTicks;

    public HeartCinematicEvent(HeartBoss boss, Kind kind, int durationTicks) {
        this.boss = boss;
        this.kind = kind;
        this.durationTicks = durationTicks;
    }

    public HeartBoss boss() {
        return boss;
    }

    public Kind kind() {
        return kind;
    }

    // 演出の長さ (tick)
    public int durationTicks() {
        return durationTicks;
    }

    // カメラが狙う点 (イベントの時点の核のあたり)
    public Vec3 focusPoint() {
        return boss.focusPoint();
    }
}

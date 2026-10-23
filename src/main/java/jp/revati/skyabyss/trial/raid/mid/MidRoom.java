package jp.revati.skyabyss.trial.raid.mid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 順路の中ボスの部屋 (docs/design/32 の 2.1 の段 3.5)。区画の段 4 の場所 (RaidRun.originOf(slot, 4)) に建てる閉じた間で、
// 三つ目の部屋を越えると参加者がそのまま間の中へ運ばれる (前室を歩かない)。準備 3 秒のあと中ボスが現れ、倒すと越える
// (RaidRun が TrialRoute.onMidBossDefeated を呼び、心臓へ運ぶ)。全員が倒れると間の中の休み場へ戻り、中ボスを湧かせ直す。
// 中ボスの技と体力の決まりは背の中の元の戦いのまま。座標だけ部屋へずらす (各巨神の部屋が中ボスに部屋を結びつける)。
// 一人でも勝てる: 体力の倍率は挑む人数 (1 人なら 1 倍)
public abstract class MidRoom extends RaidRoom {
    @Nullable
    protected Mob boss;
    protected boolean defeated;
    // 倒れた瞬間に間にいた人 (報酬を渡す相手)
    protected final Set<UUID> fighters = new LinkedHashSet<>();

    // 中ボスを湧かせる (ctx.adopt してから返す)。失敗したら null
    @Nullable
    protected abstract Mob spawnBoss(RaidCtx ctx);

    // 倒したときの報酬 (参加者 ids)
    protected abstract void reward(RaidCtx ctx, Set<UUID> ids);

    // 中ボスの居場所 (間から出たら戻す所)
    protected abstract Vec3 home(RaidCtx ctx);

    // 全滅・やり直しで部屋の仕掛けを戻す (任意)
    protected void resetFixtures(RaidCtx ctx) {
    }

    // 戦いの最中の毎 tick (任意。仕掛けの水・柱の見た目など)
    protected void tickFight(RaidCtx ctx) {
    }

    // 中ボスの前の波 (任意。番兵長は前触れの番兵の波のあとに出る)。start で呼ぶ
    protected void startFight(RaidCtx ctx) {
    }

    // 中ボスを湧かせてよいか (前の波が残っているあいだは false)
    protected boolean bossReady(RaidCtx ctx) {
        return true;
    }

    @Override
    public void start(RaidCtx ctx) {
        defeated = false;
        fighters.clear();
        boss = null;
        startFight(ctx);
        if (!bossReady(ctx)) {
            return;
        }
        boss = spawnBoss(ctx);
        if (boss == null) {
            SkyAbyss.LOGGER.warn("Raid mid room {}: the boss could not be spawned", ctx.roomId());
        }
    }

    // 中ボスが倒れた (各中ボスの die から)。間にいる人を参加者に
    protected void onBossDeath(RaidCtx ctx, Mob dead) {
        if (dead != boss || defeated) {
            return;
        }
        defeated = true;
        for (ServerPlayer p : ctx.players()) {
            fighters.add(p.getUUID());
        }
        sweep(ctx);
        try {
            reward(ctx, fighters);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.warn("Raid mid room {} reward failed: {}", ctx.roomId(), e.toString());
        }
        SkyAbyss.LOGGER.info("Raid mid room {}: boss defeated by {} participant(s)", ctx.roomId(), fighters.size());
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (defeated) {
            return;
        }
        Mob b = boss;
        if (b == null && !bossReady(ctx)) {
            for (ServerPlayer p : ctx.players()) {
                fighters.add(p.getUUID());
            }
            tickFight(ctx);
            return;
        }
        if (b == null || b.isRemoved() && !b.isDeadOrDying()) {
            // 倒されずに消えた (読み込みの外れ・コマンド): 湧かせ直す
            if (b != null) {
                ctx.aliveMobs();
            }
            boss = spawnBoss(ctx);
            return;
        }
        if (b.isDeadOrDying()) {
            // die のフックが呼ばれなかったとき (念のため)
            onBossDeath(ctx, b);
            return;
        }
        // 間の外へ出たら真ん中へ戻す
        AABB inner = ctx.cell().inner().inflate(0.5, 2, 0.5);
        if (!inner.contains(b.position())) {
            Vec3 h = home(ctx);
            b.moveTo(h.x, h.y, h.z);
        }
        for (ServerPlayer p : ctx.players()) {
            fighters.add(p.getUUID());
        }
        tickFight(ctx);
    }

    @Override
    public boolean isComplete() {
        return defeated;
    }

    @Override
    public void reset(RaidCtx ctx) {
        sweep(ctx);
        boss = null;
        defeated = false;
        fighters.clear();
        resetFixtures(ctx);
    }

    @Override
    public void end(RaidCtx ctx) {
        sweep(ctx);
        resetFixtures(ctx);
    }

    // 部屋の中の、このグループの敵 (中ボスが呼んだ仲間を含む) を片付ける
    protected static void sweep(RaidCtx ctx) {
        UUID group = ctx.group();
        AABB box = ctx.cell().bounds().inflate(2);
        List<Entity> gone = new ArrayList<>();
        for (Mob m : ctx.level().getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
            UUID g = Groups.keyOf(m);
            if (g == null || g.equals(group)) {
                gone.add(m);
            }
        }
        for (Entity e : gone) {
            e.discard();
        }
    }

    // 中ボスの体力 (帯の二つ目の数)
    @Override
    public String secondaryKey() {
        return boss != null && !defeated ? "raid.skyabyss.mid.hp" : "";
    }

    @Override
    public int secondary() {
        return boss instanceof LivingEntity l && !defeated ? (int) Math.ceil(l.getHealth()) : 0;
    }

    @Override
    public int secondaryMax() {
        return boss instanceof LivingEntity l && !defeated ? (int) Math.ceil(l.getMaxHealth()) : 0;
    }
}

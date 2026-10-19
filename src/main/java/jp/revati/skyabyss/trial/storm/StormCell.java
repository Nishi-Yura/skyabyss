package jp.revati.skyabyss.trial.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.storm.back.StormBackTrials;
import jp.revati.skyabyss.titan.storm.back.StormFx;
import jp.revati.skyabyss.titan.storm.mob.StormMob;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// 雷翼の背の試練の甲板 (StormDeck) のセルの共通部分 (docs/design/29)。
// 世界のブロックは常設で、グループごとの見かけは overlay・状態は Run.data・敵は GroupOwned (そのグループのもの) で動くので、
// 同じ甲板に二つのグループが同時にいても混ざらない (S09 だけは柱の雷が背に一つなので、部屋の占有がある)。
// 約束: どの拍 (凪・嵐) でも、全部のセルは解ける (拍は遊び方を変えるだけ。25 の原則)。一人でも解ける (人数で増えるのは敵の数・周期の短さ・窓の長さ)
abstract class StormCell implements TrialCell {
    protected final Def def;
    protected final Ports ports;

    protected StormCell(String id, Ports ports) {
        this.def = TrialCatalog.get(id);
        this.ports = ports;
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // 毎 tick の仕事 (昇り風・横風)。StormCellTicks が、中にいる人のいるセルへ呼ぶ
    interface PerTick {
        void perTick(ServerLevel back, Run run, List<ServerPlayer> inside, long now);
    }

    // ---------------------------------------------------------------- 人数・一行
    // 中にいる (生きている・観戦者でない) 人の数の下限 1
    static int count(List<ServerPlayer> inside) {
        return Math.max(1, inside.size());
    }

    // 人数による増え方: 2 人ごとに 1 (一人は 0)、上限 cap
    static int extra(int players, int cap) {
        return Math.min(cap, Math.max(0, (players - 1) / 2));
    }

    protected static void bar(List<ServerPlayer> ps, Component text) {
        TrialFx.bar(ps, text);
    }

    // ---------------------------------------------------------------- 落ちた
    // 足場から落ちたか (床の高さより 3 以上下)
    static boolean fell(ServerPlayer p, double floorY) {
        return p.getY() < floorY - 3.0;
    }

    // その人を休み場へ戻す (落ちた・足場を外れた)。傷は軽く (体力が 3 を超えていれば 2)
    protected void toRest(ServerLevel back, Run run, ServerPlayer p, String restId, String messageKey) {
        StormBackTrials.returnToRest(p, restId);
        if (p.getHealth() > 3f && !p.isCreative()) {
            p.hurt(p.damageSources().fall(), 2f);
        }
        Notice.bar(p, ModText.deny(messageKey));
    }

    // 立っている島 (水平) から、いちばん近い休み場の名前 (idA は始め・idB は中ほど。xFar より先なら中ほど)
    protected static String restByX(ServerPlayer p, String idA, String idB, double midX, boolean positive) {
        boolean far = positive ? p.getX() >= midX : p.getX() <= midX;
        return far ? idB : idA;
    }

    // ---------------------------------------------------------------- 済んだ
    protected void finish(ServerLevel back, Run run, List<ServerPlayer> inside, long now, String messageKey) {
        run.cleared = true;
        run.clearedAt = now;
        BlockPos stone = ports.stone();
        if (stone != null) {
            TrialFx.sound(back, run.group, stone, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
        }
        bar(inside, ModText.progress(messageKey));
        jp.revati.skyabyss.trial.TrialRuntime.refresh(back, run);
    }

    // ---------------------------------------------------------------- 敵 (そのグループのもの)
    // 雷翼の敵を一体湧かせる。グループの印・試練の印つき (ディスクに保存されず、グループのリセットで消える)
    @Nullable
    protected static <T extends Mob> T spawn(ServerLevel back, EntityType<T> type, UUID group, double x, double y, double z) {
        T m = type.create(back);
        if (m == null) {
            return null;
        }
        m.moveTo(x, y, z, back.random.nextFloat() * 360f, 0f);
        m.addTag(StormMob.TRIAL_TAG);
        if (m instanceof GroupOwned owned) {
            owned.setGroupId(group);
        }
        m.setPersistenceRequired();
        if (!back.addFreshEntity(m)) {
            return null;
        }
        return m;
    }

    protected static void discardAll(ServerLevel back, List<UUID> ids) {
        for (UUID id : ids) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        ids.clear();
    }

    // 生きている数 (死んだ・いなくなった個体は表から外す)
    protected static int alive(ServerLevel back, List<UUID> ids) {
        int n = 0;
        for (int i = ids.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(ids.get(i));
            if (e == null || !e.isAlive()) {
                ids.remove(i);
            } else {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 粒・音 (そのグループの人にだけ)
    protected static void puff(ServerLevel back, UUID group, ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz,
            double speed) {
        StormFx.to(group, () -> StormFx.forced(back, p, x, y, z, n, dx, dy, dz, speed));
    }

    protected static void sound(ServerLevel back, UUID group, Vec3 at, SoundEvent s, float vol, float pitch) {
        TrialFx.sound(back, group, BlockPos.containing(at), s, vol, pitch);
    }

    // 区間に分けて数えるための作業用: 中にいる人のうち条件を満たす人
    protected static List<ServerPlayer> where(List<ServerPlayer> inside, java.util.function.Predicate<ServerPlayer> test) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : inside) {
            if (test.test(p)) {
                out.add(p);
            }
        }
        return out;
    }
}

package jp.revati.skyabyss.rpg;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

// 職の熟練 (docs/design/54 の 6.2.1)。Lv 50 (上限) のあとに得た経験値は、いまの職の「熟練」にたまる (職ごと 10 段。GrowthData.mastery)。
//   段の境: 段 t へは NEED_BASE + NEED_STEP × (t − 1) (段 10 までの合計 150,000)
//   強さは変えない。見た目: 技を使うたびに、職の色の光が足もとに出る (段 1〜3 小さな火の粉、4〜6 色の輪、7〜9 輪と昇る光、10 は冠の光)
//   段 10 で称号「〜の極み」(RebirthTitles の mastery_<職>)。左上の Lv の表示は、Lv 50 では熟練の段とその進みを出す (GrowthHud)
public final class JobMastery {
    public static final int TIERS = 10;
    public static final int NEED_BASE = 6000;
    public static final int NEED_STEP = 2000;

    private JobMastery() {
    }

    // 段 t (1〜10) へ上がるのに要る量
    public static int need(int t) {
        return NEED_BASE + NEED_STEP * (t - 1);
    }

    public static int tier(long xp) {
        int t = 0;
        long left = xp;
        while (t < TIERS && left >= need(t + 1)) {
            left -= need(t + 1);
            t++;
        }
        return t;
    }

    // いまの段の中での進み (0〜1。段 10 では 1)
    public static float progress(long xp) {
        int t = tier(xp);
        if (t >= TIERS) {
            return 1f;
        }
        long left = xp;
        for (int i = 1; i <= t; i++) {
            left -= need(i);
        }
        return (float) left / need(t + 1);
    }

    public static long xp(GrowthData d, @Nullable Job job) {
        return job == null ? 0 : d.mastery.getOrDefault(job.id, 0L);
    }

    @Nullable
    static Job job(ServerPlayer p) {
        return jp.revati.skyabyss.story.finale.gear.DivineGear.job(p);
    }

    public static int tier(ServerPlayer p, Job job) {
        return tier(xp(Growth.get(p), job));
    }

    // 上限のあとの経験値を、いまの職の熟練へ (Growth.addXp から)
    public static void add(ServerPlayer p, long amount) {
        Job job = job(p);
        if (job == null || amount <= 0) {
            return;
        }
        GrowthData d = Growth.get(p);
        long before = xp(d, job);
        d.mastery.put(job.id, before + amount);
        int t0 = tier(before);
        int t1 = tier(before + amount);
        // 一度に何段も上がったときは、着いた段だけを知らせる
        if (t1 > t0) {
            Notice.levelUp(p, "message.skyabyss.mastery.up", Component.translatable(job.nameKey()), t1);
        }
        if (t1 >= TIERS && t0 < TIERS) {
            jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
        }
        Growth.sync(p);
    }

    // 称号の事実の印 ("mastery:<職>" = 段 10)
    public static java.util.Set<String> marks(ServerPlayer p) {
        java.util.Set<String> out = new java.util.HashSet<>();
        GrowthData d = Growth.get(p);
        for (Job j : Job.values()) {
            if (tier(xp(d, j)) >= TIERS) {
                out.add("mastery:" + j.id);
            }
        }
        return out;
    }

    // 職の光の色 (岩の砂色・熾の橙・星の青・骨の白緑・風の青緑・潮の水色)
    static Vector3f color(Job job) {
        return switch (job) {
            case WARRIOR -> new Vector3f(0.86f, 0.72f, 0.50f);
            case SMITH -> new Vector3f(1.00f, 0.52f, 0.20f);
            case MAGE -> new Vector3f(0.55f, 0.66f, 1.00f);
            case ARCHER -> new Vector3f(0.82f, 0.95f, 0.72f);
            case ASSASSIN -> new Vector3f(0.40f, 0.92f, 0.78f);
            case SHAMAN -> new Vector3f(0.45f, 0.86f, 1.00f);
        };
    }

    // 技を使ったときの熟練の光 (SkillActions.perform から。段 0 では出さない)
    public static void aura(ServerPlayer p) {
        Job job = job(p);
        if (job == null) {
            return;
        }
        int t = tier(p, job);
        if (t <= 0) {
            return;
        }
        ServerLevel level = p.serverLevel();
        double x = p.getX();
        double y = p.getY() + 0.1;
        double z = p.getZ();
        DustParticleOptions dust = new DustParticleOptions(color(job), 0.8f + 0.06f * t);
        if (t <= 3) {
            level.sendParticles(dust, x, y + 0.4, z, 3 + 2 * t, 0.35, 0.25, 0.35, 0.0);
            return;
        }
        int ring = 10 + 2 * t;
        double r = 0.9 + 0.05 * t;
        for (int i = 0; i < ring; i++) {
            double a = Math.PI * 2 * i / ring;
            level.sendParticles(dust, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (t >= 7) {
            level.sendParticles(dust, x, y + 0.6, z, 6 + t, 0.25, 0.6, 0.25, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, t - 5, 0.3, 0.3, 0.3, 0.02);
        }
        if (t >= TIERS) {
            double top = p.getY() + p.getBbHeight() + 0.35;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                level.sendParticles(dust, x + Math.cos(a) * 0.3, top, z + Math.sin(a) * 0.3, 1, 0, 0, 0, 0);
            }
        }
    }
}

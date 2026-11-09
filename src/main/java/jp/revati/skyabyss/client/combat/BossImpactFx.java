package jp.revati.skyabyss.client.combat;

import org.joml.Vector3f;

import jp.revati.skyabyss.client.entity.PaperCutLayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 心臓の後のボスの技が当たった瞬間の飾り (2026-11-08 の声「アニメーションが凝れていなかったり、攻撃が地味。もっと豪華にしよう」)。
// 床の印 (Telegraph) が当たったとき ImpactFx.burst が呼ぶ。印の持ち主 (Decal.owner) の見せ方 (PaperCutLayer.Style) ごとに、ふつうの破片の上へ重ねる:
//   深みの王 (KING)    : 二重の金の輪が地を走り、黒い花びらが舞い上がる。喪の鐘
//   心臓の番人 (WARDEN): 青緑の輪と、飛び散る錆の欠片、鍵の鳴る音
//   始祖 (FOUNDER)     : 白い羽根が舞い上がり、雲が広がる。鐘の残響
//   淵の主 (LORD)      : 巨神の色の輪と、昇る熾の粉
//   月の主 (MOON)      : 銀の輪と、星屑が昇る
//   塔の主・塔の手下 (tower_*) : 青白い星の輪、空から星の筋が降り、白い光が昇る
// 粒はクライアントだけで出す (サーバーからは送らない)
public final class BossImpactFx {
    private BossImpactFx() {
    }

    static void burst(ClientLevel level, TelegraphClient.Decal d, double cx, double y0, double cz, double radius) {
        if (d.owner < 0) {
            return;
        }
        Entity e = level.getEntity(d.owner);
        if (!(e instanceof LivingEntity le)) {
            return;
        }
        RandomSource r = level.random;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (path.startsWith("tower_")) {
            ring(level, cx, y0, cz, radius, new Vector3f(0.7f, 0.82f, 1f), 1.8f);
            ring(level, cx, y0, cz, radius * 0.55, new Vector3f(1f, 1f, 1f), 1.2f);
            int n = (int) Math.min(18, 4 + radius * 1.5);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d0 = Math.sqrt(r.nextDouble()) * radius;
                double x = cx + Math.cos(a) * d0;
                double z = cz + Math.sin(a) * d0;
                // 空から降る星の筋 (上から速く落ちる白い光)
                level.addParticle(ParticleTypes.FIREWORK, x, y0 + 6 + r.nextDouble() * 4, z, 0, -0.9, 0);
                level.addParticle(ParticleTypes.END_ROD, x, y0 + 0.2, z, 0, 0.12 + r.nextDouble() * 0.1, 0);
            }
            level.playLocalSound(cx, y0, cz, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.2f, 0.6f, false);
            return;
        }
        switch (PaperCutLayer.style(le)) {
            case KING -> {
                ring(level, cx, y0, cz, radius, new Vector3f(0.95f, 0.78f, 0.36f), 1.6f);
                ring(level, cx, y0, cz, radius * 0.6, new Vector3f(0.95f, 0.78f, 0.36f), 1.2f);
                puff(level, r, cx, y0, cz, radius, new Vector3f(0.12f, 0.03f, 0.14f), 2.0f, (int) (8 + radius * 3), 0.12);
                level.playLocalSound(cx, y0, cz, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0f, 0.6f, false);
            }
            case WARDEN -> {
                ring(level, cx, y0, cz, radius, new Vector3f(0.35f, 0.9f, 0.82f), 1.6f);
                puff(level, r, cx, y0, cz, radius, new Vector3f(0.45f, 0.22f, 0.12f), 1.4f, (int) (8 + radius * 3), 0.2);
                level.playLocalSound(cx, y0, cz, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f, false);
            }
            case FOUNDER -> {
                ring(level, cx, y0, cz, radius, new Vector3f(0.96f, 0.96f, 1f), 1.6f);
                puff(level, r, cx, y0, cz, radius, new Vector3f(0.98f, 0.98f, 1f), 2.0f, (int) (8 + radius * 3), 0.16);
                for (int i = 0; i < (int) (4 + radius * 2); i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    level.addParticle(ParticleTypes.CLOUD, cx + Math.cos(a) * radius * 0.7, y0 + 0.3, cz + Math.sin(a) * radius * 0.7, Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08);
                }
                level.playLocalSound(cx, y0, cz, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 0.8f, 1.3f, false);
            }
            case LORD, MOON -> {
                Vector3f c = PaperCutLayer.style(le) == PaperCutLayer.Style.MOON ? new Vector3f(0.86f, 0.92f, 1f) : lordColor(le);
                ring(level, cx, y0, cz, radius, c, 1.5f);
                puff(level, r, cx, y0, cz, radius, c, 1.1f, (int) (6 + radius * 2), 0.25);
            }
            default -> {
            }
        }
    }

    static Vector3f lordColor(LivingEntity e) {
        int rgb = PaperCutLayer.lordRgbOf(e);
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    // 地を走る輪 (外へ少し広がる)
    static void ring(ClientLevel level, double cx, double y, double cz, double radius, Vector3f c, float size) {
        DustParticleOptions dust = new DustParticleOptions(c, size);
        int n = (int) Math.min(48, 10 + radius * 5);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.addParticle(dust, cx + Math.cos(a) * radius, y + 0.2, cz + Math.sin(a) * radius, Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08);
        }
    }

    // 舞い上がるもの (花びら・錆・羽根・粉)
    static void puff(ClientLevel level, RandomSource r, double cx, double y, double cz, double radius, Vector3f c, float size, int n, double up) {
        DustParticleOptions dust = new DustParticleOptions(c, size);
        for (int i = 0; i < Math.min(n, 40); i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(r.nextDouble()) * radius;
            level.addParticle(dust, cx + Math.cos(a) * d, y + 0.3 + r.nextDouble() * 0.6, cz + Math.sin(a) * d,
                    (r.nextDouble() - 0.5) * 0.06, up + r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.06);
        }
    }
}

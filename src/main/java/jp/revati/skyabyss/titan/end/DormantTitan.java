package jp.revati.skyabyss.titan.end;

import net.minecraft.util.Mth;

// 眠る巨神の本体 (岩鯨・潮殻・雷翼・余燼) が実装する。restTicks は同期された値で、起きていれば -1、眠る姿が始まってからは tick (SYNC_CAP で止まる)。
// 眠る巨神は動かず・歌わず・光らない (描画は glowFactor で光を落とす。光は揺らぎながら消える)
public interface DormantTitan {
    // 光が消えきるまで (tick)
    int GLOW_FADE = 240;

    int restTicks();

    default boolean isDormantNow() {
        return restTicks() >= 0;
    }

    // 光る画素にかける強さ (起きていれば 1、眠って GLOW_FADE tick で 0)
    default float glowFactor(float partialTick) {
        int r = restTicks();
        if (r < 0) {
            return 1f;
        }
        float t = (r + partialTick) / GLOW_FADE;
        if (t >= 1f) {
            return 0f;
        }
        // 消えかけの灯りのように揺らぎながら落ちる
        float flicker = 0.8f + 0.2f * Mth.sin((r + partialTick) * 0.9f);
        return Mth.clamp((1f - t) * (1f - t) * flicker, 0f, 1f);
    }
}

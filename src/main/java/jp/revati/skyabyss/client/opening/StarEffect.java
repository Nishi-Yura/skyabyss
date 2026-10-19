package jp.revati.skyabyss.client.opening;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.Config.StarEffectVariant;

// 夜の歌の「星が欠ける」見せ方の選択。設定 starEffect か、撮影台本の stareffect が決める (台本のほうが優先)
public final class StarEffect {
    // 撮影用の上書き
    @Nullable
    public static StarEffectVariant override;

    // 新しい見せ方 (A〜C) で、帯 (影) が空を渡るのにかける tick。3〜5 秒で読めるよう、今のもの (600) より短い
    static final int NEW_LENGTH = 180;

    private StarEffect() {
    }

    static StarEffectVariant variant() {
        return override != null ? override : Config.STAR_EFFECT.get();
    }

    static int bandLength() {
        return variant() == StarEffectVariant.CURRENT ? OpeningClient.BAND_LENGTH : NEW_LENGTH;
    }
}

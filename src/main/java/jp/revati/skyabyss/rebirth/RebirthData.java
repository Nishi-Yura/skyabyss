package jp.revati.skyabyss.rebirth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// プレイヤーごとの転生の見え方 (Data Attachment。持ち主と、その周りで追っている人みんなへ同期する)。値は変えずに作り直す (record)。
//  count    : 転生の回数 (0〜20)。転生の本体 (別の担当) ができたら RebirthView の元をそちらへ切り替える
//  title    : 選んでいる称号の ID ("" = なし)
//  unlocked : 解けた称号の印 (RebirthTitles の番号の bit)
//  flawless : 死なずに転生した回数
//  hidden   : 順位表に載せない (断る)
public record RebirthData(int count, String title, long unlocked, int flawless, boolean hidden) {
    public static final RebirthData EMPTY = new RebirthData(0, "", 0L, 0, false);

    public static final Codec<RebirthData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("count", 0).forGetter(RebirthData::count),
            Codec.STRING.optionalFieldOf("title", "").forGetter(RebirthData::title),
            Codec.LONG.optionalFieldOf("unlocked", 0L).forGetter(RebirthData::unlocked),
            Codec.INT.optionalFieldOf("flawless", 0).forGetter(RebirthData::flawless),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(RebirthData::hidden)).apply(i, RebirthData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RebirthData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RebirthData::count,
            ByteBufCodecs.stringUtf8(32), RebirthData::title,
            ByteBufCodecs.VAR_LONG, RebirthData::unlocked,
            ByteBufCodecs.VAR_INT, RebirthData::flawless,
            ByteBufCodecs.BOOL, RebirthData::hidden,
            RebirthData::new);

    public RebirthData {
        count = RebirthTier.clamp(count);
        title = title == null ? "" : title;
    }

    public RebirthData withCount(int c) {
        return new RebirthData(c, title, unlocked, flawless, hidden);
    }

    public RebirthData withTitle(String t) {
        return new RebirthData(count, t, unlocked, flawless, hidden);
    }

    public RebirthData withUnlocked(long u) {
        return new RebirthData(count, title, u, flawless, hidden);
    }

    public RebirthData withFlawless(int f) {
        return new RebirthData(count, title, unlocked, f, hidden);
    }

    public RebirthData withHidden(boolean h) {
        return new RebirthData(count, title, unlocked, flawless, h);
    }
}

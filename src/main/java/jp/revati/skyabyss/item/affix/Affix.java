package jp.revati.skyabyss.item.affix;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// 装備に付いた特性ひとつ (種類と値)。種類は文字列で持ち、知らない種類 (将来消したものなど) は効かないだけにする
public record Affix(String type, double value) {
    public static final Codec<Affix> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(Affix::type),
            Codec.DOUBLE.fieldOf("value").forGetter(Affix::value)).apply(i, Affix::new));
    public static final StreamCodec<ByteBuf, Affix> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Affix::type,
            ByteBufCodecs.DOUBLE, Affix::value,
            Affix::new);

    // 装備ひとつの特性 (0〜4 個)
    public static final Codec<List<Affix>> LIST_CODEC = CODEC.listOf(0, 4);
    public static final StreamCodec<ByteBuf, List<Affix>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list(4));
    // 再鍛造の候補 (最大 3 組)
    public static final Codec<List<List<Affix>>> OFFERS_CODEC = LIST_CODEC.listOf(0, AffixRules.MAX_TRIALS);
    public static final StreamCodec<ByteBuf, List<List<Affix>>> OFFERS_STREAM_CODEC = LIST_STREAM_CODEC.apply(ByteBufCodecs.list(AffixRules.MAX_TRIALS));

    public Affix(AffixType type, double value) {
        this(type.id, value);
    }

    @Nullable
    public AffixType kind() {
        return AffixType.byId(type);
    }
}

package jp.revati.skyabyss.item.extra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// 穴に差したもの一つ。kind: "shard" (arg = 属性 id) / "rune_power" / "rune_cd" (arg = 技 id、milli = 倍率 ×1000)
public record Gem(String kind, String arg, int milli) {
    public static final String SHARD = "shard";
    public static final String RUNE_POWER = "rune_power";
    public static final String RUNE_COOLDOWN = "rune_cd";

    public static final Codec<Gem> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(Gem::kind),
            Codec.STRING.fieldOf("arg").forGetter(Gem::arg),
            Codec.INT.optionalFieldOf("milli", 0).forGetter(Gem::milli)).apply(i, Gem::new));

    public boolean isShard() {
        return SHARD.equals(kind);
    }

    public boolean isRune() {
        return RUNE_POWER.equals(kind) || RUNE_COOLDOWN.equals(kind);
    }

    public double multiplier() {
        return milli / 1000.0;
    }
}

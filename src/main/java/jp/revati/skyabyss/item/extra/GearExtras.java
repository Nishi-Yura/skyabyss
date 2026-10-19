package jp.revati.skyabyss.item.extra;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import jp.revati.skyabyss.item.affix.Affix;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// 装備に付く「特性の外側」の刻み (docs/design/31 の A02・A06・A07・B15)。装備のデータ skyabyss:gear_extras。
//  sockets   穴の数 0〜2 (付いていない装備は、手にしたときに一度だけ抽選する。GearFreedom.ensure)
//  gems      差したもの (欠片・ルーン)。数は sockets 以下
//  aspect    刻んだアスペクト (伝説の書き換え Rune の id。空 = なし)。1 装備に 1 つ
//  hollow    伝説の書き換えを抜いて、印だけ残した伝説 (その武器自身の書き換えは効かない)
//  generation  第 N 世代 (転生を越えて持ち越すたびに +1)
//  genStamp  最後に世代を上げたときの転生回数 (同じ転生で二度上げないため)
//  traits    世代 3 ごとに開く形見の特性 (小さな特性)
public record GearExtras(int sockets, List<Gem> gems, String aspect, boolean hollow, int generation, int genStamp, List<Affix> traits) {
    public static final int MAX_SOCKETS = 2;
    public static final int MAX_GENERATION = 12;
    public static final int GENERATIONS_PER_TRAIT = 3;

    public static final GearExtras EMPTY = new GearExtras(0, List.of(), "", false, 0, 0, List.of());

    public static final Codec<GearExtras> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, MAX_SOCKETS).optionalFieldOf("sockets", 0).forGetter(GearExtras::sockets),
            Gem.CODEC.listOf(0, MAX_SOCKETS).optionalFieldOf("gems", List.of()).forGetter(GearExtras::gems),
            Codec.STRING.optionalFieldOf("aspect", "").forGetter(GearExtras::aspect),
            Codec.BOOL.optionalFieldOf("hollow", false).forGetter(GearExtras::hollow),
            Codec.intRange(0, MAX_GENERATION).optionalFieldOf("generation", 0).forGetter(GearExtras::generation),
            Codec.INT.optionalFieldOf("gen_stamp", 0).forGetter(GearExtras::genStamp),
            Affix.CODEC.listOf(0, 4).optionalFieldOf("traits", List.of()).forGetter(GearExtras::traits)).apply(i, GearExtras::new));
    public static final StreamCodec<ByteBuf, GearExtras> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public GearExtras withSockets(int n) {
        return new GearExtras(n, gems, aspect, hollow, generation, genStamp, traits);
    }

    public GearExtras withGems(List<Gem> g) {
        return new GearExtras(sockets, List.copyOf(g), aspect, hollow, generation, genStamp, traits);
    }

    public GearExtras withAspect(String a) {
        return new GearExtras(sockets, gems, a, hollow, generation, genStamp, traits);
    }

    public GearExtras withHollow(boolean h) {
        return new GearExtras(sockets, gems, aspect, h, generation, genStamp, traits);
    }

    public GearExtras withGeneration(int gen, int stamp, List<Affix> t) {
        return new GearExtras(sockets, gems, aspect, hollow, gen, stamp, List.copyOf(t));
    }

    public int traitSlots() {
        return generation / GENERATIONS_PER_TRAIT;
    }
}

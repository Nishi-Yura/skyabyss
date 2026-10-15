package jp.revati.skyabyss.lore;

import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;

// 伝承の項目が書に記される条件の一つ。項目は条件のどれか一つを満たすと記される。
//   sky_shadow : 雲の上を通る影を、空の見える所で初めて見た
//   sign       : 看板の碑文を読んだ (右クリック、または近くで目を向けた)。target は看板の文のキー (末尾の .1 を除く)
//   dimension  : そのディメンションに入った。target はディメンションの ID
//   near_entity: その種類の生き物が radius ブロック以内にいた。target はエンティティの ID
//   kill       : その種類の生き物が倒れたとき、倒した人か radius ブロック以内にいた。target はエンティティの ID
//   item       : そのアイテムを持ち物に入れた。target はアイテムの ID
//   page       : 破れた頁を読んだ。target は頁の出どころ (village / titan_back)
//   manual     : コマンドでだけ記される (演出の途中で記す項目など)
public record LoreTrigger(String type, String target, double radius) {
    public static final Set<String> TYPES = Set.of("sky_shadow", "sign", "dimension", "near_entity", "kill", "item", "page", "manual");

    public static final Codec<LoreTrigger> CODEC = RecordCodecBuilder.<LoreTrigger>create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(LoreTrigger::type),
            Codec.STRING.optionalFieldOf("target", "").forGetter(LoreTrigger::target),
            Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(LoreTrigger::radius)).apply(i, LoreTrigger::new))
            .validate(t -> TYPES.contains(t.type()) ? DataResult.success(t) : DataResult.error(() -> "unknown lore trigger type: " + t.type()));

    public boolean is(String type, String target) {
        return this.type.equals(type) && this.target.equals(target);
    }

    void write(FriendlyByteBuf buf) {
        buf.writeUtf(type);
        buf.writeUtf(target);
        buf.writeDouble(radius);
    }

    static LoreTrigger read(FriendlyByteBuf buf) {
        return new LoreTrigger(buf.readUtf(), buf.readUtf(), buf.readDouble());
    }
}

package jp.revati.skyabyss.lore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;

// 伝承の書の章 (書の上のしおり一枚)。data/skyabyss/lore/chapter/<ID>.json から読む。
// 文のキー: lore.skyabyss.chapter.<ID>.tab (しおりの字) / .name (章の名。章の項目が一つでも記されるまでは伏せる)
public record LoreChapter(String id, int order) {
    public record Def(int order) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("order").forGetter(Def::order)).apply(i, Def::new));
    }

    public String tabKey() {
        return "lore.skyabyss.chapter." + id + ".tab";
    }

    public String nameKey() {
        return "lore.skyabyss.chapter." + id + ".name";
    }

    void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeVarInt(order);
    }

    static LoreChapter read(FriendlyByteBuf buf) {
        return new LoreChapter(buf.readUtf(), buf.readVarInt());
    }
}

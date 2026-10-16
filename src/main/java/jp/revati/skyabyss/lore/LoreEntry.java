package jp.revati.skyabyss.lore;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;

// 伝承の書の一項目。data/skyabyss/lore/entry/<章>/<名前>.json から読む (ID は "<章>/<名前>")。
// 文は lang に置き、キーは ID から決まる (lore.skyabyss.<章>.<名前>.title / .body / .inscription)。
//   chapter     : 章の ID (data/skyabyss/lore/chapter/<ID>.json)
//   order       : 章の中で並べる順 (小さいほど上)
//   inscription : true なら、本文の前に碑文の写し (青緑の字) を置く。文は .inscription のキー
//   inscription_sign : 看板の文のキー (末尾の .1〜.4 を除く)。あれば、その看板の 4 行をそのまま碑文の写しにする
//   placeholder : true なら、まだ書かれていない枠。記されることはなく、書では「？？？」のまま
//   unlock      : 記される条件 (どれか一つ)
//   related     : 関連する項目の ID。両方が記されていれば、書の中でたどれる
//   hint        : 伏せられているときに出す手がかりのキー (省略すると、最初の条件の種類から決まる)
public record LoreEntry(String id, String chapter, int order, boolean inscription, String inscriptionSign, boolean placeholder,
        List<LoreTrigger> unlock, List<String> related, String hint) {

    // JSON の中身 (ID はファイルの場所から付ける)
    public record Def(String chapter, int order, boolean inscription, String inscriptionSign, boolean placeholder,
            List<LoreTrigger> unlock, List<String> related, String hint) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("chapter").forGetter(Def::chapter),
                Codec.INT.optionalFieldOf("order", 0).forGetter(Def::order),
                Codec.BOOL.optionalFieldOf("inscription", false).forGetter(Def::inscription),
                Codec.STRING.optionalFieldOf("inscription_sign", "").forGetter(Def::inscriptionSign),
                Codec.BOOL.optionalFieldOf("placeholder", false).forGetter(Def::placeholder),
                LoreTrigger.CODEC.listOf().optionalFieldOf("unlock", List.of()).forGetter(Def::unlock),
                Codec.STRING.listOf().optionalFieldOf("related", List.of()).forGetter(Def::related),
                Codec.STRING.optionalFieldOf("hint", "").forGetter(Def::hint)).apply(i, Def::new));

        public LoreEntry withId(String id) {
            return new LoreEntry(id, chapter, order, inscription, inscriptionSign, placeholder, List.copyOf(unlock), List.copyOf(related), hint);
        }
    }

    public String titleKey() {
        return key() + ".title";
    }

    public String bodyKey() {
        return key() + ".body";
    }

    public String inscriptionKey() {
        return key() + ".inscription";
    }

    // 碑文の写しのキーの並び (看板なら 4 行、自前の文なら 1 つ。なければ空)
    public List<String> inscriptionKeys() {
        if (!inscriptionSign.isEmpty()) {
            return List.of(inscriptionSign + ".1", inscriptionSign + ".2", inscriptionSign + ".3", inscriptionSign + ".4");
        }
        return inscription ? List.of(inscriptionKey()) : List.of();
    }

    // 伏せられているときの手がかり。枠だけの項目には出さない
    public String hintKey() {
        if (!hint.isEmpty()) {
            return hint;
        }
        if (placeholder || unlock.isEmpty()) {
            return "gui.skyabyss.lore.hint.blank";
        }
        return "gui.skyabyss.lore.hint." + unlock.get(0).type();
    }

    private String key() {
        return "lore.skyabyss." + id.replace('/', '.');
    }

    // 記すことのできる項目か (枠だけのもの、条件のないものは記されない。コマンドの unlock all でも飛ばす)
    public boolean unlockable() {
        return !placeholder && !unlock.isEmpty();
    }

    void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(chapter);
        buf.writeVarInt(order);
        buf.writeBoolean(inscription);
        buf.writeUtf(inscriptionSign);
        buf.writeBoolean(placeholder);
        buf.writeVarInt(unlock.size());
        unlock.forEach(t -> t.write(buf));
        buf.writeVarInt(related.size());
        related.forEach(buf::writeUtf);
        buf.writeUtf(hint);
    }

    static LoreEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String chapter = buf.readUtf();
        int order = buf.readVarInt();
        boolean inscription = buf.readBoolean();
        String inscriptionSign = buf.readUtf();
        boolean placeholder = buf.readBoolean();
        int n = buf.readVarInt();
        List<LoreTrigger> unlock = new java.util.ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            unlock.add(LoreTrigger.read(buf));
        }
        int m = buf.readVarInt();
        List<String> related = new java.util.ArrayList<>(m);
        for (int k = 0; k < m; k++) {
            related.add(buf.readUtf());
        }
        return new LoreEntry(id, chapter, order, inscription, inscriptionSign, placeholder, List.copyOf(unlock), List.copyOf(related), buf.readUtf());
    }
}

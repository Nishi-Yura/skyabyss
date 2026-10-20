package jp.revati.skyabyss.relic;

import jp.revati.skyabyss.progress.Titans;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// 遺物ブロックの巨神ごとの様式。同じブロックの見た目だけが変わる (挙動は同じ)
//   WHALE = 鯨骨と深層岩 (青い光) / TIDE = 鱗と貝 (青緑と珊瑚) / STORM = 銅と羽 (淡い金) / EMBER = 玄武岩と琥珀
public enum RelicStyle implements StringRepresentable {
    WHALE("whale"),
    TIDE("tide"),
    STORM("storm"),
    EMBER("ember");

    public static final EnumProperty<RelicStyle> PROP = EnumProperty.create("titan", RelicStyle.class);

    private final String id;

    RelicStyle(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    // 巨神の名前 (Titans.STONE_WHALE など) から。分からなければ鯨
    public static RelicStyle ofTitan(String titan) {
        if (Titans.TIDE_SHELL.equals(titan)) {
            return TIDE;
        }
        if (Titans.THUNDER_WING.equals(titan)) {
            return STORM;
        }
        if (Titans.EMBER.equals(titan)) {
            return EMBER;
        }
        return WHALE;
    }
}

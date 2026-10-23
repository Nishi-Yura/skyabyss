package jp.revati.skyabyss.mount;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

// 乗り物の見た目 (docs/design/38)。角の荷獣ははじめから使え、あとの四つは章を越えると店に並ぶ (買うのは一度だけ)。
//   id      : 名前 (モデル geo/entity/mount_<id>.geo.json、絵 textures/entity/mount_<id>.png、翻訳 mount.skyabyss.look.<id>)
//   chapter : この章から店に並ぶ (人の章。Coins.chapter。2 = 岩鯨の心臓を止めたあと)
//   price   : 鐘銭の値段 (0 = 最初から持っている)
//   width / height : 当たりの大きさ。seat = 乗る人の座る高さ (足もとからのブロック数。鞍の上面 - 1/16)
//   legPitch / legSpread : 乗った人の脚の向き (度。前への回し・外への開き)。脚が体に入らない向きを生成の道具が選ぶ
//   (tools/gen/mobs/mounts_lp/build_all.py が低ポリのモデルから測って書き換える)
public enum MountLook {
    HORN_BEAST("horn_beast", 1, 0, 1.3f, 1.8f, 1.6, -70f, 30f),
    ASH_DEER("ash_deer", 1, 60, 1.2f, 2.0f, 1.75, -70f, 30f),
    GREAT_LIZARD("great_lizard", 1, 60, 1.5f, 1.2f, 1.2, -70f, 30f),
    WHALE_CALF("whale_calf", 2, 120, 1.4f, 1.6f, 1.45, -70f, 30f),
    SHELL_TORTOISE("shell_tortoise", 3, 200, 1.5f, 1.6f, 1.5, -70f, 30f),
    STORM_CHICK("storm_chick", 4, 280, 1.2f, 2.2f, 1.85, -70f, 30f),
    EMBER_FOX("ember_fox", 5, 360, 1.1f, 1.5f, 1.35, -70f, 30f);

    public final String id;
    public final int chapter;
    public final int price;
    public final float width;
    public final float height;
    public final double seat;
    public final float legPitch;
    public final float legSpread;

    MountLook(String id, int chapter, int price, float width, float height, double seat, float legPitch, float legSpread) {
        this.id = id;
        this.chapter = chapter;
        this.price = price;
        this.width = width;
        this.height = height;
        this.seat = seat;
        this.legPitch = legPitch;
        this.legSpread = legSpread;
    }

    public String model() {
        return "mount_" + id;
    }

    public String nameKey() {
        return "mount.skyabyss.look." + id;
    }

    @Nullable
    public static MountLook byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (MountLook l : values()) {
            if (l.id.equals(s)) {
                return l;
            }
        }
        return null;
    }

    public static MountLook byOrdinal(int i) {
        MountLook[] all = values();
        return i >= 0 && i < all.length ? all[i] : HORN_BEAST;
    }
}

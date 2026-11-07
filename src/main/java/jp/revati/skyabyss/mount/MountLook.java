package jp.revati.skyabyss.mount;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

// 乗り物の見た目 (docs/design/38)。角の荷獣ははじめから使え、あとの四つは章を越えると店に並ぶ (買うのは一度だけ)。
//   id      : 名前 (モデル geo/entity/mount_<id>.geo.json、絵 textures/entity/mount_<id>.png、翻訳 mount.skyabyss.look.<id>)
//   chapter : この章から店に並ぶ (MountService.chapter。2 = 岩鯨の心臓を止めたあと … 5 = 四つめ。6 = 五章、7 = 六章 (月)、8 = 終章を越えた)
//   price   : 鐘銭の値段 (0 = 最初から持っている)
//   width / height : 当たりの大きさ。seat = 乗る人の座る高さ (足もとからのブロック数。鞍の上面 - 1/16)
//   legPitch / legSpread : 乗った人の脚の向き (度。前への回し・外への開き)。脚が体に入らない向きを生成の道具が選ぶ
//   (tools/gen/mobs/mounts_lp/build_all.py が低ポリのモデルから測って書き換える)
//   maxSeats : 乗れる人数の最大 (持ち主を入れて。人数の段を上げてもこれまで)
//   flies   : 飛ぶ乗り物 (38 の 6 章。乗ると見ている向きへ飛ぶ。MountEntity.travel)
public enum MountLook {
    HORN_BEAST("horn_beast", 1, 0, 1.00f, 2.23f, 2.081, -30f, 52f, 2),
    ASH_DEER("ash_deer", 1, 30, 1.00f, 2.01f, 1.862, -30f, 44f, 2),
    GREAT_LIZARD("great_lizard", 1, 30, 1.00f, 1.77f, 1.623, -40f, 48f, 2),
    WHALE_CALF("whale_calf", 2, 60, 1.60f, 2.37f, 2.220, -70f, 40f, 3),
    SHELL_TORTOISE("shell_tortoise", 3, 100, 1.60f, 2.12f, 1.973, -40f, 44f, 3),
    STORM_CHICK("storm_chick", 4, 140, 1.43f, 2.19f, 2.037, -40f, 56f, 2),
    EMBER_FOX("ember_fox", 5, 180, 1.00f, 1.92f, 1.775, -30f, 44f, 2),
    // 2026-11-02 に選んだ三つ (00 の 346〜348)。五章・六章・終章のあと
    ABYSS_GECKO("abyss_gecko", 6, 220, 1.10f, 1.70f, 1.551, -30f, 56f, 2),
    STAR_LYNX("star_lynx", 7, 260, 1.00f, 1.99f, 1.840, -25f, 44f, 2),
    SKY_RAY("sky_ray", 8, 400, 1.60f, 1.94f, 1.792, -65f, 44f, 3),
    // 二頭立ての幌の荷車 (2026-11-07 の頁「二頭立ての車」の選定 A・最大 6 人・四つの心臓のあと。tools/gen/mobs/mounts_lp/carriage.py)。
    // 乗る人は腰掛けに座る (脚は前へ)。席の並びは MountEntity.CARRIAGE_SEATS
    CARRIAGE("carriage", 5, 240, 2.00f, 3.00f, 1.334, -80f, 16f, 6);

    // 飛ぶ見た目
    public boolean flies() {
        return this == SKY_RAY;
    }

    // 並ぶ前の札のキーと引数 (四つの心臓までは「心臓を n 体」、そのあとは章ごとの文)
    public String lockedKey(String prefix) {
        return chapter <= 5 ? prefix : prefix + ".ch" + chapter;
    }

    public final String id;
    public final int chapter;
    public final int price;
    public final float width;
    public final float height;
    public final double seat;
    public final float legPitch;
    public final float legSpread;
    public final int maxSeats;

    MountLook(String id, int chapter, int price, float width, float height, double seat, float legPitch, float legSpread, int maxSeats) {
        this.maxSeats = maxSeats;
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

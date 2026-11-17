package jp.revati.skyabyss.client.look;

// 自分の武器・防具・技の見せ方の試作 (2026-11-17「邪魔にならないがかっこよく見栄えのいいベストな表現」)。'0' = 今の描き方 (点の模型)。
// 5 回目から: 手で打ったドット絵 (tools/gen/pixel/art/*.txt) を部分ごとの厚みで立体に起こした模型 (tools/gen/pixel/pixel_extrude.py が lowpoly/look/a/ に書く)。
//   a = その模型。作り直しは武器から (祠の始めの武器 6 本、嵐の剣、鯨骨の杖、巨神断ち)
// 立方体の模型が無い品は今のまま。技の案のあいだは、独自の光の形と粒 (FxDraw・SkillFx) を出さない
// 台本では `gearlook weapon|armor|skill|all 0|a|b|c`
public final class GearLook {
    public static volatile char weapon = '0';
    public static volatile char armor = '0';
    public static volatile char skill = '0';

    private GearLook() {
    }

    public static boolean set(String what, String value) {
        char v = value.isEmpty() ? '0' : Character.toLowerCase(value.charAt(0));
        if ("0abc".indexOf(v) < 0) {
            return false;
        }
        switch (what) {
            case "weapon" -> weapon = v;
            case "armor" -> armor = v;
            case "skill" -> skill = v;
            case "all" -> {
                weapon = v;
                armor = v;
                skill = v;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // 自分の技の形か (敵の技の形は今のまま)
    public static boolean playerProp(String kind) {
        return kind.startsWith("ss_") || kind.startsWith("sk_") || kind.startsWith("my_") || kind.startsWith("pa_") || kind.startsWith("lg_")
                || kind.startsWith("gx_") || kind.startsWith("bow_spirit");
    }
}

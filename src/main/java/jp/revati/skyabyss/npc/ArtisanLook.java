package jp.revati.skyabyss.npc;

// 旅の職人の装い (NpcEntity.variant = 章 1〜4) から絵の名前 (textures/lowpoly/<名前>.png)。範囲外 (0 など) は一章。描画 (client/npc/NpcRenderer) と自己テストが使う
public final class ArtisanLook {
    private ArtisanLook() {
    }

    public static String texture(int variant) {
        return "artisan_ch" + (variant >= 1 && variant <= 4 ? variant : 1);
    }
}

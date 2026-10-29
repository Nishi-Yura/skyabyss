package jp.revati.skyabyss.story.ch6.gear.client;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.story.ch6.gear.MoonWalkerArmorItem;

// 月渡りの装束の 3D 描画。低ポリの共通部品 LowPolyArmorRenderer が lowpoly/armor/moon_walker/<部位>[_rare].json と
// textures/lowpoly/armor/moon_walker/<部位>_rare[_plus5|_plus10].png (+ 光る画素 _glowmask.png) を描く。
// 絵とメッシュは tools/install/install_moon_gear.py が入れる (ラフは tools/rough/lowpoly/gear_gaps/armor_moon.py)
public class MoonWalkerArmorRenderer extends LowPolyArmorRenderer<MoonWalkerArmorItem> {
    public MoonWalkerArmorRenderer() {
        super("moon_walker");
    }
}

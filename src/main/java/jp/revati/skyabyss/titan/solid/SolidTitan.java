package jp.revati.skyabyss.titan.solid;

import java.util.List;

import net.minecraft.world.entity.Entity;

// 体に入れない巨神 (岩鯨・潮殻・雷翼・余燼)。体積は TitanVolume (巨神の名前の表)、乗り場の通り道は corridors() が局所の箱で返す。
// 位置と向きは Entity のもの (サーバーでもクライアントでも同じ)
public interface SolidTitan {
    // TitanVolume の名前
    String volumeName();

    // 立入禁止の中でも入ってよい所 (鎖の下・潮門・昇り風・熱の口)。巨神の向きの局所 (横, 上, 前)
    List<Corridor> corridors();

    // 体 (立入禁止) に触れた人を、乗り込み口へ渡すか (渡したら true。押し出さない)。local は人の体の真ん中の局所 (横, 上, 前)。
    // 潮殻は甲羅のどこに触れても潮門へ、余燼は頭のまわりに触れれば口が開いているあいだ口へ (2026-10-21 の試遊の声: 当たりに押し返されて乗れない)
    default boolean boardOnTouch(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.phys.Vec3 local) {
        return false;
    }

    // いま体に入れないか。眠って沈んだ・消えかけの巨神は false
    default boolean solidActive() {
        return !((Entity) this).isRemoved();
    }

    // 局所の箱 (横 = 左が +, 上, 前)。単位はブロック
    record Corridor(double sideMin, double sideMax, double upMin, double upMax, double fwdMin, double fwdMax) {
        public boolean contains(double side, double up, double fwd) {
            return side >= sideMin && side <= sideMax && up >= upMin && up <= upMax && fwd >= fwdMin && fwd <= fwdMax;
        }
    }
}

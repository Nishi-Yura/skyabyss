package jp.revati.skyabyss.titan.travel;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// 人を乗せて運べる巨神 (巨神での移動)。岩鯨のほか、後の巨神 (潮殻・雷翼・余燼) もこれを実装する。
// 動き (向き・速さ・高さ) は旅の操縦 (Voyage) が外から決める。巨神の側が受け持つのは
// 乗り場の鎖の伸び縮み、座席の位置、旅の始まりと終わりの切り替えだけ
public interface TravelTitan {
    Entity asEntity();

    // 旅の操縦を受け付ける (自分の AI を止める)
    void beginVoyage();

    // 旅を終え、自分で泳ぐのに戻る。restFor > 0 なら、その場でその tick だけ休む (背中の鎖を垂らす)
    void endVoyage(int restFor);

    boolean inVoyage();

    // 乗り場の鎖を ground (地面の高さ) の少し上まで垂らす。NaN ならたたむ
    void setVoyageChain(double ground);

    // 乗り場の鎖が地面近くまで下りきっているか
    boolean isVoyageChainDown();

    boolean isChainFolded();

    // 乗り場の鎖の垂れ口の、巨神の中心からの位置 (向き yaw のとき)
    Vec3 boardingChainOffset(float yaw);

    // 垂れ口から座席の手前までの道すじ (世界の座標。垂れ口が先)
    List<Vec3> boardingPath();

    int seatCount();

    Vec3 seatPosition(int index);
}

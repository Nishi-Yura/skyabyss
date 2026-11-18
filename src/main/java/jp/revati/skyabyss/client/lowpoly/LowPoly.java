package jp.revati.skyabyss.client.lowpoly;

// 低ポリのメッシュ (点の模型 client/splat の元) まわりの開発用の切り替え。
// メッシュは描かない。体のモブ・防具・品・NPC の持ち物は、どれもメッシュから作った点の模型で描く (SplatGen / SplatDraw)
public final class LowPoly {
    // 巨神の重さの見せ方 (地面や海面の影・雲や水しぶきと土ぼこり。client/titan/TitanWeight・TitanFx) を使うか。台本の `titanweight on|off` (前後の比べ用)
    public static volatile boolean titanWeight = true;
    // 開発用: 武器のメッシュの階級を common|rare|legend に固定する (null = 品の階級)。台本の `weapontier <階級|off>`。品の階級は一つに決まっているので、その段のメッシュが無い品は品の階級で描く
    public static volatile String weaponTier = null;
    // 開発用: 装飾品のメッシュの階級を common|rare|legend に固定する (null = 品の階級)。台本の `acctier <階級|off>`
    public static volatile String accessoryTier = null;

    private LowPoly() {
    }
}

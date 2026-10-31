package jp.revati.skyabyss.item;

import net.minecraft.network.chat.TextColor;

// 装備の階級 (03 の 1.2節。2026-10-24 に 6 段へ、2026-11-01 に 7 段へ): コモン(白) / アンコモン(緑) / レア(青) / エピック(紫) / ヒロイック(橙) /
// アーティファクト(金。表示は「★ 伝説」) / 神器(紅紫。Wynncraft のミシックの立ち位置: いちばん上、終章の神器とあいだの塔の装備だけ)。
// 階級は品で決まる (セーブには書かない)。品の ID が変わらないので、前のセーブの装備は新しい階級をそのまま読む。
// 階級ごとの決まりはこの表だけに置く: 名前の色、特性の数と幅 (AffixRules)、穴の抽選 (GearFreedom)、分解 (ForgeRules)、
// 必要 Lv の差 (GearLevel)、立体の形の段 (meshTier: 手・地面・額縁・装飾の立体。絵は変えない)、アスペクトの芯を刻めるか。
// 旧「伝説」はアーティファクトに改名した (キーは artifact)。神器 (mythic) は 2026-11-01 の声「レア度神器は Wynncraft のミシックのような立ち位置」で足した
public enum GearTier {
    //        key          色        分解 特性   少ない側 幅広 穴 0/1/2        欠片 結晶 Lv差 形
    COMMON("common", 0xE4E4E4, 0, 0, 0, 0f, false, new int[]{100, 0, 0}, false, 0, 4, "common"),
    UNCOMMON("uncommon", 0x7BD66B, 1, 0, 0, 0f, false, new int[]{100, 0, 0}, false, 0, 4, "common"),
    RARE("rare", 0x5AA9FF, 3, 1, 2, 0.6f, false, new int[]{50, 38, 12}, false, 1, 4, "rare"),
    EPIC("epic", 0xB98CFF, 5, 1, 2, 0.35f, false, new int[]{40, 42, 18}, false, 1, 4, "rare"),
    HEROIC("heroic", 0xFF7A3D, 10, 2, 3, 0.6f, true, new int[]{30, 45, 25}, true, 2, 2, "legend"),
    ARTIFACT("artifact", 0xF2C94C, 10, 2, 3, 0.35f, true, new int[]{20, 45, 35}, true, 3, 2, "legend"),
    MYTHIC("mythic", 0xE040FB, 12, 3, 3, 0f, true, new int[]{10, 40, 50}, true, 4, 2, "legend");

    public final String key;
    public final TextColor color;
    // 共鳴の炉で分解したときの基準 (shardDismantle なら共鳴の欠片、そうでなければ由来の巨神の素材の数)
    public final int dismantleValue;
    // 特性 (Affix) の数。0 なら付かない
    public final int affixMin;
    public final int affixMax;
    // 少ないほうの数になる確率
    public final float fewerChance;
    // 特性の幅を 1.25 倍にするか (AffixType.min/max の wide)
    public final boolean wideAffixes;
    // 穴の数の重み (0 個・1 個・2 個)
    private final int[] socketOdds;
    // 分解で共鳴の欠片 (交換の通貨) が出るか。出ない階級は素材が戻る
    public final boolean shardDismantle;
    // 特性の付く品を分解したときの共鳴の結晶
    public final int dismantleCrystals;
    // 必要 Lv = 由来の推奨 Lv − この差
    public final int levelMargin;
    // 立体の形の段 (WeaponItemRenderer・AccessoryMeshes・LowPolyArmor のモデルの名前の後ろ)。common / rare / legend
    public final String meshTier;

    GearTier(String key, int rgb, int dismantleValue, int affixMin, int affixMax, float fewerChance, boolean wideAffixes,
            int[] socketOdds, boolean shardDismantle, int dismantleCrystals, int levelMargin, String meshTier) {
        this.key = key;
        this.color = TextColor.fromRgb(rgb);
        this.dismantleValue = dismantleValue;
        this.affixMin = affixMin;
        this.affixMax = affixMax;
        this.fewerChance = fewerChance;
        this.wideAffixes = wideAffixes;
        this.socketOdds = socketOdds;
        this.shardDismantle = shardDismantle;
        this.dismantleCrystals = dismantleCrystals;
        this.levelMargin = levelMargin;
        this.meshTier = meshTier;
    }

    public int rgb() {
        return color.getValue();
    }

    public int[] socketOdds() {
        return socketOdds.clone();
    }

    // 穴 (サブ効果の欠片・ルーンを差す所) を開けられる数の上限 (2026-10-26 の声)。抽選も打ち直しもこの数まで
    public int maxSockets() {
        return switch (this) {
            case COMMON, UNCOMMON -> 0;
            case RARE -> 1;
            case EPIC -> 2;
            case HEROIC, ARTIFACT, MYTHIC -> 3;
        };
    }

    public boolean atLeast(GearTier other) {
        return ordinal() >= other.ordinal();
    }

    // 特性 (と穴・アスペクト・世代) が付く階級か (レア以上。品の側が武器・防具・盾であることは AffixRules が見る)
    public boolean affixed() {
        return affixMax > 0;
    }

    // アスペクトの芯を刻める階級 (レア・エピック。ヒロイック以上は自分の書き換えを持つ)
    public boolean aspectHost() {
        return this == RARE || this == EPIC;
    }

    // 翻訳キー (gear.skyabyss.tier.<key>)
    public String translationKey() {
        return "gear.skyabyss.tier." + key;
    }
}

package jp.revati.skyabyss.story.ch1;

// 一章の地上の敵の差し込み口 (docs/design/21 の 3.1、P7 まで)。新しい敵 (岩殻虫・棘うさぎ・骨負い・老いた歩哨) は P7 がまだ作っていないので、
// 割れ殻の洞などの湧き台は、P7 の ID が登録されていればそれを、なければ今ある一章の敵 (背中の番兵たち) を使う。
// P7 の担当は、エンティティを登録するだけで洞の中身が入れ替わる (ID が下の定数のとおりなら。湧き台は GroupOwned (GuardMob の系統) を実装していること)
public final class Ch1Mobs {
    public static final String ROCK_SHELL_BEETLE = "skyabyss:rock_shell_beetle";
    public static final String SPINE_HARE = "skyabyss:spine_hare";
    public static final String BONE_HAULER = "skyabyss:bone_hauler";
    public static final String ELDER_SENTRY = "skyabyss:elder_sentry";
    // 今の代用 (背中の敵)
    public static final String STAND_IN_BEETLE = "skyabyss:moss_crawler";
    public static final String STAND_IN_HARE = "skyabyss:rib_archer";
    public static final String STAND_IN_ELDER = "skyabyss:bone_sentinel";

    private Ch1Mobs() {
    }

    // P7 の敵が登録されていればそれ、なければ代用
    public static String pick(String p7, String standIn) {
        return Ch1Prep.entityExists(p7) ? p7 : standIn;
    }

    // TODO(P7): ひとり歩きの湧き (guide/ChapterMobSpawner.register(new Wanderer(...)))
    //   岩殻虫 (rock_shell_beetle): Chapters.WHALE、丘・山・荒れ地、昼夜問わず (Time.ANY)
    //   棘うさぎ (spine_hare): Chapters.WHALE、草原・平野の夜 (Time.NIGHT)
    // 骨負いは湧き台なし (ChapterMobSpawner の対象外)。BoneCache の骨の山のまわりを夕方〜夜に歩かせる。
    // 今は新しい敵がないので何も登録しない (序盤に背中の敵を地上へ放たない)
    static void registerWanderers() {
    }
}

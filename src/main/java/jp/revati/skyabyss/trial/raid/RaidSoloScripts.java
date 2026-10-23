package jp.revati.skyabyss.trial.raid;

// 一人で越える台本 (RaidSolo) の登録の一覧。巨神ごとに一行
final class RaidSoloScripts {
    private RaidSoloScripts() {
    }

    private static boolean done;

    static void registerAll() {
        if (done) {
            return;
        }
        done = true;
        jp.revati.skyabyss.trial.raid.whale.WhaleSolo.register();
        jp.revati.skyabyss.trial.raid.tide.TideSolo.register();
        jp.revati.skyabyss.trial.raid.storm.StormSolo.register();
        jp.revati.skyabyss.trial.raid.ember.EmberSolo.register();
    }
}

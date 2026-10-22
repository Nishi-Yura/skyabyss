package jp.revati.skyabyss.trial.raid;

// レイドの部屋の登録の一覧。巨神ごとに一行 (最初に RaidRooms が使われるとき一度だけ呼ばれる)
final class RaidBootstrap {
    private RaidBootstrap() {
    }

    static void registerAll() {
        jp.revati.skyabyss.trial.raid.whale.WhaleRaid.register();
        jp.revati.skyabyss.trial.raid.ember.EmberRaid.register();
        jp.revati.skyabyss.trial.raid.storm.StormRaid.register();
        // 潮殻・雷翼・余燼は、それぞれの部屋ができたらここへ一行足す (trial/raid/tide|storm|ember の register())
    }
}

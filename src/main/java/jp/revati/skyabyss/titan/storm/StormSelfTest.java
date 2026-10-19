package jp.revati.skyabyss.titan.storm;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.storm.StormPerch.Peak;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

// 雷翼の巨神の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST storm PASSED / FAILED
// 夕立の刻の境目、止まり木の探し方、昇り風の幾何、一体だけの決まり、記録の保存と読み込み (岩鯨・潮殻が変わらないこと)、
// 戻る先、背中の結びつけと一覧
public final class StormSelfTest {
    // 岩鯨 (4000, 4000)・潮殻 (-4000, 4000) と重ならない所
    private static final int TEST_X = -6000;
    private static final int TEST_Z = -6000;
    private static final int FORCE_CHUNKS = 7;

    private StormSelfTest() {
    }

    // SelfTestRunner が呼ぶ: 読み込み (load) → 決まった tick 待つ → 試す (runSelfTest)
    public static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int dx = 0; dx <= FORCE_CHUNKS; dx++) {
            for (int dz = 0; dz <= FORCE_CHUNKS; dz++) {
                level.setChunkForced((TEST_X >> 4) + dx, (TEST_Z >> 4) + dz, true);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        try {
            run(level, failures);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm crashed", e);
            failures.add("crashed: " + e);
        }
        for (int dx = 0; dx <= FORCE_CHUNKS; dx++) {
            for (int dz = 0; dz <= FORCE_CHUNKS; dz++) {
                level.setChunkForced((TEST_X >> 4) + dx, (TEST_Z >> 4) + dz, false);
            }
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm PASSED (window, perch, gale column, one titan, record, leave, bind, back list)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST storm FAILED: {}", f));
        }
    }

    private static void run(ServerLevel level, List<String> failures) {
        checkWindow(failures);
        checkRules(failures);
        checkPerch(level, failures);
        checkGale(level, failures);
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.snapshot(level.registryAccess());
        try {
            checkKeeper(level, failures);
        } finally {
            StormWingEntity t = StormKeeper.KEEPER.current(level);
            if (t != null) {
                t.discard();
            }
            StormKeeper.KEEPER.releaseTicket(level);
            data.restoreSlot(StormKeeper.SLOT, saved, level.registryAccess());
        }
        // 岩鯨と潮殻の記録が変わっていない (雷翼の記録は別の場所)
        CompoundTag after = data.snapshot(level.registryAccess());
        for (String key : List.of("StoneWhaleSpawned", "WhaleId", "LastX", "State")) {
            if (!java.util.Objects.equals(saved.get(key), after.get(key))) {
                failures.add("the stone whale's record changed: " + key);
            }
        }
        checkLeave(level, failures);
    }

    // 夕立の刻: 7000〜10600 (一日の中の時刻)。刻の前後と、次の日も同じ
    private static void checkWindow(List<String> failures) {
        if (StormWingEntity.inWindow(6999) || !StormWingEntity.inWindow(7000) || !StormWingEntity.inWindow(10599)
                || StormWingEntity.inWindow(10600)) {
            failures.add("the storm window (7000..10600) is wrong");
        }
        if (!StormWingEntity.inWindow(24000L + 7000) || StormWingEntity.inWindow(24000L * 3 + 10600)) {
            failures.add("the storm window must repeat every day");
        }
    }

    // 止まり木: 平らな地面の上で、人の周り 24〜96 ブロックに地表が見つかる
    private static void checkPerch(ServerLevel level, List<String> failures) {
        Peak p = StormPerch.find(level, TEST_X + 8, TEST_Z + 8, 24, 96);
        if (p == null) {
            // 世界の種によっては、試す所が海 (乾いた地面が一つもない)。そのときは「見つからない」が正しい
            boolean anyLand = false;
            for (int dx = -96; dx <= 96 && !anyLand; dx += 8) {
                for (int dz = -96; dz <= 96 && !anyLand; dz += 8) {
                    double dist = Math.hypot(dx, dz);
                    anyLand = dist >= 24 && dist <= 96 && jp.revati.skyabyss.titan.tide.TideOcean.land(level, TEST_X + 8 + dx, TEST_Z + 8 + dz);
                }
            }
            if (anyLand) {
                failures.add("the perch finder found no dry ground around the test point");
            }
            return;
        }
        double d = Math.hypot(p.x() - (TEST_X + 8), p.z() - (TEST_Z + 8));
        if (d < 24 - 1 || d > 96 + 1 || Double.isNaN(p.y())) {
            failures.add("the perch is out of range: " + d);
        }
    }

    // 昇り風: 柱の中 (半径 2.5、地面から頂まで) と外
    private static void checkGale(ServerLevel level, List<String> failures) {
        GaleColumnEntity col = StormSetup.GALE_COLUMN.get().create(level);
        if (col == null) {
            failures.add("the gale column could not be created");
            return;
        }
        col.setup(java.util.UUID.randomUUID(), TEST_X + 20.5, TEST_Z + 20.5, 200);
        double base = col.position().y;
        boolean inside = col.inColumn(TEST_X + 20.5 + 3.5, base + 10, TEST_Z + 20.5);
        boolean outside = col.inColumn(TEST_X + 20.5 + 4.5, base + 10, TEST_Z + 20.5);
        boolean above = col.inColumn(TEST_X + 20.5, 300, TEST_Z + 20.5);
        if (!inside || outside || above) {
            failures.add("the gale column geometry is wrong (inside " + inside + ", outside " + outside + ", above " + above + ")");
        }
        // 見える柱 (半径 2.5・光の枠 4) のなかの点を、縁・複数の高さ・跳びこむ高さまで調べる: どこでも持ち上がる範囲に入っている
        double cx = TEST_X + 20.5;
        double cz = TEST_Z + 20.5;
        double topY = col.topY();
        int misses = 0;
        int samples = 0;
        for (double r : new double[] {0, 1, 2, 2.5, 3, 3.5, 3.9}) {
            for (int a = 0; a < 12; a++) {
                for (double h : new double[] {-2, -0.5, 0, 0.6, 1.25, 3, 10, (topY - base) / 2, topY - base - 3, topY - base - 0.5}) {
                    samples++;
                    if (!col.inColumn(cx + Math.cos(a * Math.PI / 6) * r, base + h, cz + Math.sin(a * Math.PI / 6) * r)) {
                        misses++;
                    }
                }
            }
        }
        if (misses > 0) {
            failures.add("the gale column misses " + misses + " of " + samples + " positions inside the visible column");
        }
        // 乗り心地: 強さは頂へ向けて段階的に弱まり (毎秒 8 → 3 ブロック)、最後まで上向き
        int far = GaleColumnEntity.levitationFor(20);
        int mid = GaleColumnEntity.levitationFor(5);
        int near = GaleColumnEntity.levitationFor(1);
        if (!(far > mid && mid > near && near >= 1) || (far + 1) * 0.05 * 20 < 7.9) {
            failures.add("the gale ride is not a smooth taper (" + far + ", " + mid + ", " + near + ")");
        }
        col.discard();
    }

    // 一体だけ: 二度目の呼び出しで前の個体が消える。前の個体は (読み込まれていれば) 本物でなくなる
    private static void checkKeeper(ServerLevel level, List<String> failures) {
        StormWingEntity first = StormKeeper.KEEPER.spawn(level, TEST_X + 40, TEST_Z + 40, null);
        StormWingEntity second = StormKeeper.KEEPER.spawn(level, TEST_X + 60, TEST_Z + 60, null);
        // 巨神は嵐の中心から 120〜200 離れた輪の上に置かれる (読み込んだチャンクの外のことがある) ので、記録の UUID で見る
        if (!second.getUUID().equals(StormKeeper.KEEPER.slot(level).id)) {
            failures.add("the second thunder wing is not the current one");
        }
        // 前の個体が読み込まれていないチャンクに置かれたときは、まだ見えていない (読み込まれると handleJoin が取り消す)。
        // 見えている前の個体が残っていたら失敗
        if (!first.isRemoved() && level.getEntity(first.getUUID()) == first) {
            failures.add("the first thunder wing was not removed when the second appeared");
        }
        if (!StormKeeper.KEEPER.slot(level).spawned) {
            failures.add("the thunder wing slot is not marked spawned");
        }
        second.discard();
    }

    // 戻る先: 巨神が無くても、有限の座標 (共有スポーン地点の上)
    private static void checkLeave(ServerLevel level, List<String> failures) {
        StormTravel.Landing landing = StormTravel.landing(level);
        Vec3 p = landing.pos();
        if (Double.isNaN(p.x) || Double.isNaN(p.y) || Double.isNaN(p.z)) {
            failures.add("the landing point is not finite");
        }
    }

    // 規則: 背中の一覧、背中と巨神の結びつけ
    private static void checkRules(List<String> failures) {
        if (!TitanBacks.isBack(StormTravel.STORM_BACK)) {
            failures.add("storm_back is not in the back list");
        }
        if (Titans.titanOfBack(StormTravel.STORM_BACK) == null
                || !Titans.THUNDER_WING.equals(Titans.titanOfBack(StormTravel.STORM_BACK))) {
            failures.add("titanOfBack(storm_back) must be thunder_wing");
        }
        if (!Titans.hasBack(Titans.THUNDER_WING)) {
            failures.add("the thunder wing back is not bound");
        }
    }
}

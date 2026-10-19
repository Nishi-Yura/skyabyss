package jp.revati.skyabyss.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.affix.AffixType;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

// 強さの曲線の自己テスト (SKYABYSS_SELFTEST=1)。結果はログに SELFTEST gear curve PASSED / FAILED (止めるのは TitanBackSelfTest)。
//  - 岩鯨の装備の実際の値 (品の既定の修飾子) が 03 の 9章の表どおりか
//  - 手に入れた直後 (+0) がダイヤの剣の前後か、+10 でもバニラの終盤 (ネザライトの剣 + 鋭さ V) を超えないか
//  - +10 と最良の特性を盛っても、岩鯨の心臓が殴り合いで勝てる作業にならないか (手ごたえ >= 1.3)
//  - 巨神ごとの目安が段ごとに伸び、前の巨神の +10 が次の巨神の +10 を超えないか
public final class GearCurveSelfTest {
    // 心臓 (02 の 5.3節): 体力 450、防御 8、強靭 6、技は 2.5 秒ほどに一度。殴っていられるのは 6 割とみる
    private static final double HEART_HP = 450;
    private static final double HEART_ARMOR = 8;
    private static final double HEART_TOUGHNESS = 6;
    private static final double HEART_INTERVAL = 2.5;
    private static final double UPTIME = 0.6;

    private GearCurveSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("gear curve selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST gear curve PASSED (tooltip story lines, item values, fresh gear vs diamond, +10 vs netherite, heart load, titan ladder)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST gear curve FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // 装備のツールチップの物語の一行 (assets/skyabyss_gear_story/lang。生成元 tools/gen_gear_story.py): 登録された装備のすべてに日英であり、短く、重複がない
    static void story(List<String> f) {
        java.util.Map<String, String> ja = lang("/assets/skyabyss_gear_story/lang/ja_jp.json", f);
        java.util.Map<String, String> en = lang("/assets/skyabyss_gear_story/lang/en_us.json", f);
        java.util.Set<String> seen = new java.util.HashSet<>();
        int gear = 0;
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (!(item instanceof ModGear) || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(SkyAbyss.MODID)) {
                continue;
            }
            gear++;
            String key = ModGear.storyKey(item);
            String j = ja.get(key);
            String e = en.get(key);
            check(f, j != null && e != null, "gear story line missing (ja/en): " + key);
            if (j == null || e == null) {
                continue;
            }
            check(f, j.codePointCount(0, j.length()) <= 28, "gear story line too long (" + j.codePointCount(0, j.length()) + "): " + key);
            check(f, e.length() <= 70, "gear story line too long in English (" + e.length() + "): " + key);
            check(f, j.startsWith("「") && j.endsWith("」") && !j.contains("\n"), "gear story line should be one quoted line: " + key);
            check(f, seen.add(j), "gear story line repeated: " + key);
        }
        check(f, gear == 48, "expected 48 mod gear items with a story line, found " + gear);
        check(f, ja.size() == 48 && en.size() == 48, "story line tables should hold 48 lines each: " + ja.size() + "/" + en.size());
    }

    private static java.util.Map<String, String> lang(String path, List<String> f) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        try (java.io.InputStream in = GearCurveSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("lang file missing: " + path);
                return map;
            }
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        } catch (java.io.IOException | RuntimeException e) {
            f.add("lang file unreadable: " + path + " " + e);
        }
        return map;
    }

    static void run(List<String> f) {
        story(f);
        // ---- 品の値
        Item sword = ModItems.WHALEBONE_GREATSWORD.get();
        Item pike = ModItems.CAPTAIN_PIKE.get();
        Item fang = ModItems.WATERWAY_FANG.get();
        check(f, Math.abs(GearEvents.weaponAttack(sword) - 10) < 1e-6, "greatsword attack " + GearEvents.weaponAttack(sword) + " (want 10)");
        check(f, Math.abs(GearEvents.weaponAttack(pike) - 8.5) < 1e-6, "pike attack " + GearEvents.weaponAttack(pike) + " (want 8.5)");
        check(f, Math.abs(GearEvents.weaponAttack(fang) - 5.5) < 1e-6, "fang attack " + GearEvents.weaponAttack(fang) + " (want 5.5)");
        check(f, Math.abs(GearEvents.enhanceAttack(sword, 10) - 3.0) < 1e-6, "greatsword +10 adds " + GearEvents.enhanceAttack(sword, 10) + " (want 3)");
        Item[] armor = {ModItems.STONE_WHALE_HELMET.get(), ModItems.STONE_WHALE_CHESTPLATE.get(), ModItems.STONE_WHALE_LEGGINGS.get(),
                ModItems.STONE_WHALE_BOOTS.get()};
        double defense = 0;
        double toughness = 0;
        for (Item a : armor) {
            defense += ((ArmorItem) a).getDefense();
            toughness += ((ArmorItem) a).getToughness();
        }
        GearCurve.Stage whale = GearCurve.STAGES.get(0);
        check(f, defense == whale.armor() && Math.abs(toughness - whale.toughness()) < 1e-6,
                "stone whale armor " + defense + " / " + toughness + " (want " + whale.armor() + " / " + whale.toughness() + ")");

        // ---- 一体への 1 秒あたり (+0 / +10)
        double diamond = GearCurve.diamondSwordDps();
        double netherite = GearCurve.netheriteSharpVDps();
        Item[] weapons = {sword, pike, fang};
        String[] names = {"greatsword", "pike", "fang"};
        double[] speeds = {1.0, 1.1, 2.2};
        for (int w = 0; w < 3; w++) {
            double[] dps = new double[2];
            int[] levels = {0, ModGear.MAX_ENHANCE};
            for (int i = 0; i < 2; i++) {
                double attack = GearEvents.weaponAttack(weapons[w]) + GearEvents.enhanceAttack(weapons[w], levels[i]);
                double proc = weapons[w] == pike ? GearCurve.pikeProc(levels[i]) : 1;
                dps[i] = GearCurve.rawDps(attack, speeds[w], proc);
            }
            SkyAbyss.LOGGER.info("[selftest] gear curve {}: {} per second at +0 ({}x diamond sword), {} at +10 ({}x netherite sharpness V)",
                    names[w], fmt(dps[0]), fmt(dps[0] / diamond), fmt(dps[1]), fmt(dps[1] / netherite));
            check(f, dps[0] >= diamond * 0.85 && dps[0] <= diamond * 1.15,
                    names[w] + " +0 is " + fmt(dps[0] / diamond) + "x a diamond sword (want 0.85-1.15)");
            check(f, dps[1] <= netherite * 0.92, names[w] + " +10 is " + fmt(dps[1] / netherite) + "x netherite sharpness V (want <= 0.92)");
            check(f, dps[1] / dps[0] <= 1.45, names[w] + " +10 grows x" + fmt(dps[1] / dps[0]) + " over +0 (want <= 1.45)");
        }

        // ---- 心臓の手ごたえ (Lv 12。筋力に全部振った場合も見る)
        double setMul = whale.setMultiplier();
        double fullStrength = 1 + 0.006 * Math.min(50, 3 * (whale.level() - 1));
        double legendarySharp = AffixType.SHARP.max(true);
        double legendarySwift = AffixType.SWIFT.max(true);
        double toughness10 = toughness + 4 * ModGear.MAX_ENHANCE * GearEvents.TOUGHNESS_PER_LEVEL;
        double health = GearCurve.maxHealth(whale.level());
        double plainTaken = whale.bossHit() * GearCurve.armorTaken(whale.bossHit(), defense, toughness) * setMul;
        double plain = GearCurve.load(
                GearCurve.dpsAgainst(GearEvents.weaponAttack(fang), 2.2, 1, GearCurve.fangBleed(0), GearCurve.strengthMultiplier(whale.level()), HEART_ARMOR, HEART_TOUGHNESS),
                HEART_HP, UPTIME, HEART_INTERVAL, health, plainTaken);
        double worst = Double.MAX_VALUE;
        for (int w = 0; w < 3; w++) {
            double attack = (GearEvents.weaponAttack(weapons[w]) + GearEvents.enhanceAttack(weapons[w], 10)) * (1 + legendarySharp);
            double proc = weapons[w] == pike ? GearCurve.pikeProc(10) : 1;
            double bleed = weapons[w] == fang ? GearCurve.fangBleed(10) : 0;
            double dps = GearCurve.dpsAgainst(attack, speeds[w] * (1 + legendarySwift), proc, bleed, fullStrength, HEART_ARMOR, HEART_TOUGHNESS);
            // 守りの最良: 4 部位とも 頑健 1.0 と 守り 3% (守りは上限 10%)
            double taken = whale.bossHit() * GearCurve.armorTaken(whale.bossHit(), defense, toughness10) * setMul * (1 - AffixRules.WARD_CAP);
            double load = GearCurve.load(dps, HEART_HP, UPTIME, HEART_INTERVAL, health + 4, taken);
            worst = Math.min(worst, load);
            SkyAbyss.LOGGER.info("[selftest] gear curve heart load with {} +10, best affixes, all strength: {} (fresh fang +0: {})",
                    names[w], fmt(load), fmt(plain));
        }
        check(f, worst >= 1.3, "maxed whale gear makes the heart load " + fmt(worst) + " (want >= 1.3: the heart must still be dodged)");
        check(f, plain >= 3.0, "fresh whale gear makes the heart load " + fmt(plain) + " (want >= 3.0)");

        // ---- 甲殻一式の耐性 I (03 の 10.1節): 強すぎないか。心臓の一撃で、ダイヤ + ダメージ軽減 I〜II の間に収まるか
        double heartHit = whale.bossHit();
        double base = heartHit * GearCurve.armorTaken(heartHit, defense, toughness);
        double withSet = health / (base * GearCurve.RESISTANCE_I);
        double protI = health / (base * GearCurve.protectionSet(1));
        double protII = health / (base * GearCurve.protectionSet(2));
        SkyAbyss.LOGGER.info("[selftest] gear curve resistance I: survives {} heart hits (no set effect {}, diamond + protection I {}, II {}); fresh load {} -> {} without it",
                fmt(withSet), fmt(health / base), fmt(protI), fmt(protII), fmt(plain), fmt(plain / GearCurve.RESISTANCE_I));
        check(f, withSet >= protI && withSet <= protII, "stone whale set with resistance I survives " + fmt(withSet)
                + " heart hits (want between diamond + protection I " + fmt(protI) + " and II " + fmt(protII) + ")");
        SkyAbyss.LOGGER.info("[selftest] gear curve resistance I: maxed load {} -> {} without it", fmt(worst), fmt(worst / GearCurve.RESISTANCE_I));

        // ---- 巨神ごとの目安
        for (int i = 1; i < GearCurve.STAGES.size(); i++) {
            GearCurve.Stage prev = GearCurve.STAGES.get(i - 1);
            GearCurve.Stage next = GearCurve.STAGES.get(i);
            check(f, next.level() > prev.level(), next.id() + " level does not rise");
            check(f, next.dps0() >= prev.dps0() * 1.1 && next.dps0() <= prev.dps10(), next.id() + " +0 should sit between "
                    + prev.id() + " +0 x1.1 and its +10");
            check(f, next.dps10() >= prev.dps10() * 1.1, next.id() + " +10 leaves too little room over " + prev.id());
            check(f, next.bossHp() > prev.bossHp() && next.bossHit() > prev.bossHit(), next.id() + " boss does not grow");
        }
        for (GearCurve.Stage s : GearCurve.STAGES) {
            double hp = GearCurve.maxHealth(s.level());
            double taken = s.bossHit() * GearCurve.armorTaken(s.bossHit(), s.armor(), s.toughness()) * s.setMultiplier();
            double seconds = s.bossHp() / (s.dps0() * GearCurve.strengthMultiplier(s.level()) * 0.8) / UPTIME;
            SkyAbyss.LOGGER.info("[selftest] gear curve stage {} (Lv {}): boss {} hp in {}s at +0, survives {} boss hits",
                    s.id(), s.level(), s.bossHp(), fmt(seconds), fmt(hp / taken));
        }
        check(f, GearCurve.STAGES.get(0).dps10() < netherite && GearCurve.STAGES.get(1).dps10() <= netherite * 1.05,
                "the tide shell (Lv 18) +10 should be the first to reach vanilla's end (netherite sharpness V)");
    }
}

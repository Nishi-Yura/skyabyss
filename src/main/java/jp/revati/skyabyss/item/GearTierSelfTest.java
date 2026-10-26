package jp.revati.skyabyss.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.AbyssBowItem;
import jp.revati.skyabyss.abyss.AbyssSetup;
import jp.revati.skyabyss.abyss.gear.AbyssGear;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.storm.StormGear;
import jp.revati.skyabyss.item.storm.StormGearSelfTest;
import jp.revati.skyabyss.titan.ember.gear.EmberGear;
import jp.revati.skyabyss.titan.ember.gear.EmberGearSelfTest;
import jp.revati.skyabyss.titan.tide.gear.TideGear;
import jp.revati.skyabyss.titan.tide.gear.TideGearSelfTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

// 階級の自己テスト (SKYABYSS_SELFTEST=1、SKYABYSS_SELFTEST_ONLY="gear tiers")。03 の 1.2節 (6 段) と 1.4節 (全装備の表)。
//  - 表が欠けていない: このModの装備 (ModGear) はすべて下の表にあり、品の階級が表と同じ。表の品はすべて登録されている
//  - 階級の決まりが上へ単調: 特性の数・幅、穴、分解の結晶、必要 Lv の差
//  - 同じ段 (巨神・章) の中で、武器の 1 秒あたりが階級の順に並ぶ (+0 と +10。逆転なし)。防具は旅装 (アンコモン) < 巨神の鎧 (レア)
//  - 落とし表が解ける: 図鑑の品は登録済み、専用の逸品はヒロイック・心臓の確率がほかの品より低い、深淵の心臓の品はアーティファクト
//  - 名前とツールチップに階級が出る (名前の色、一行目の階級のキーと色)、日英の階級の名前がある
public final class GearTierSelfTest {
    // 弓は離れて撃てるぶん、近接の目安の 0.85 倍に合わせてある (03 の 10.1節)。近接と比べるときは割り戻す
    private static final double RANGED = 0.85;
    // アーティファクトは、同じ段のヒロイックの +0 を 5% 以上上回る (段の差が手に感じられる)
    private static final double ARTIFACT_STEP = 1.05;

    public record Row(String id, GearTier tier, int chapter) {
    }

    // 全装備の表 (03 の 1.4節と同じ。章 = 由来の巨神の章。深淵は 5)
    public static final List<Row> TABLE = new ArrayList<>();

    private static void row(int chapter, GearTier tier, String... ids) {
        for (String id : ids) {
            TABLE.add(new Row(id, tier, chapter));
        }
    }

    static {
        GearTier C = GearTier.COMMON, U = GearTier.UNCOMMON, R = GearTier.RARE, E = GearTier.EPIC, H = GearTier.HEROIC, A = GearTier.ARTIFACT;
        // 一章 岩鯨
        row(1, C, "heartbeat_ward_stone_whale");
        // 島の祠の始めの武器 (rpg/skill/JobChoice。docs/design/43 の 4 節)
        row(1, C, "shrine_staff", "shrine_bell_staff", "shrine_dagger", "shrine_sword", "shrine_hammer", "shrine_bow");
        row(1, U, "whale_road_helmet", "whale_road_chestplate", "whale_road_leggings", "whale_road_boots");
        row(1, R, "stone_whale_helmet", "stone_whale_chestplate", "stone_whale_leggings", "stone_whale_boots", "moss_talisman");
        row(1, E, "whalebone_greatsword", "resonance_charm", "whale_staff", "whale_bell_staff", "whale_hammer", "whale_bow");
        row(1, H, "captain_pike", "waterway_fang", "stone_whale_plating");
        row(1, A, "abyss_greatsword", "abyss_whale_hammer", "abyss_whale_staff", "abyss_whale_bow", "abyss_whale_fang", "abyss_whale_bell_staff");
        // 二章 潮殻
        row(2, C, "heartbeat_ward_tide_shell");
        row(2, U, "tide_wader_helmet", "tide_wader_chestplate", "tide_wader_leggings", "tide_wader_boots");
        row(2, R, "tide_shell_helmet", "tide_shell_chestplate", "tide_shell_leggings", "tide_shell_boots");
        row(2, E, "tide_shell_trident", "coral_shield", "tide_chime_charm", "tide_staff", "tide_bell_staff", "tide_hammer", "tide_bow");
        row(2, H, "tidecaller_claw", "coral_harpoon", "moontide_pearl");
        row(2, A, "abyss_bell_staff", "abyss_tide_sword", "abyss_tide_hammer", "abyss_tide_staff", "abyss_tide_bow", "abyss_tide_dagger");
        // 三章 雷翼
        row(3, C, "heartbeat_ward_thunder_wing");
        row(3, U, "peak_crossing_helmet", "peak_crossing_chestplate", "peak_crossing_leggings", "peak_crossing_boots");
        row(3, R, "thunder_wing_helmet", "thunder_wing_chestplate", "thunder_wing_leggings", "thunder_wing_boots");
        row(3, E, "thunder_wing_bow", "storm_anklet", "storm_charm", "storm_staff", "storm_bell_staff", "storm_hammer");
        row(3, H, "raiju_claw", "warden_glaive", "storm_eye_feather");
        row(3, A, "abyss_storm_bow", "abyss_storm_sword", "abyss_storm_hammer", "abyss_storm_staff", "abyss_storm_dagger", "abyss_storm_bell_staff");
        // 四章 余燼
        row(4, C, "heartbeat_ward_ember");
        row(4, U, "ash_walking_helmet", "ash_walking_chestplate", "ash_walking_leggings", "ash_walking_boots");
        row(4, R, "ember_helmet", "ember_chestplate", "ember_leggings", "ember_boots");
        row(4, E, "ember_war_axe", "ember_mantle", "kindling_charm", "ember_staff", "ember_censer", "ember_greatsword", "ember_bow", "ember_dagger");
        row(4, H, "smith_hammer", "thrall_poker", "ember_lantern");
        row(4, A, "abyss_ash_scythe", "abyss_ember_sword", "abyss_ember_staff", "abyss_ember_bow", "abyss_ember_dagger", "abyss_ember_bell_staff");
        // 五章 深淵
        row(5, R, "abyss_diver_helmet", "abyss_diver_chestplate", "abyss_diver_leggings", "abyss_diver_boots");
        row(5, E, "abyss_lamp_sword", "abyss_pulse_listener", "abyss_lantern_charm", "abyss_lamp_staff", "abyss_depth_bow", "abyss_toll_bell_staff");
        row(5, H, "abyss_sinking_anchor", "abyss_pulse_dagger", "abyss_surveyor_plumb");
    }

    private GearTierSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            table(f, notes);
            rules(f);
            weapons(f, notes);
            armor(f);
            loot(f);
            display(f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("gear tiers selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("[selftest] gear tiers {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST gear tiers PASSED (tier table complete, rules rise with tier, no stat inversions, loot tables resolve, tooltips show the tier)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST gear tiers FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path));
    }

    // ---------------------------------------------------------------- 表
    private static void table(List<String> f, List<String> notes) {
        Map<String, Row> byId = new LinkedHashMap<>();
        for (Row r : TABLE) {
            check(f, byId.put(r.id(), r) == null, "tier table lists " + r.id() + " twice");
            Item it = item(r.id());
            check(f, it != net.minecraft.world.item.Items.AIR, "tier table item " + r.id() + " is not registered");
            check(f, it instanceof ModGear g && g.tier() == r.tier(), r.id() + " is " + (it instanceof ModGear g ? g.tier() : "not gear") + ", table says " + r.tier());
        }
        for (Item it : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(it);
            if (it instanceof ModGear && key.getNamespace().equals(SkyAbyss.MODID)) {
                check(f, byId.containsKey(key.getPath()), "gear " + key.getPath() + " is missing from the tier table (03 1.4)");
            }
        }
        // 章ごとの数 (03 の 1.4節の表の数)
        for (int ch = 1; ch <= 5; ch++) {
            Map<GearTier, Integer> count = new EnumMap<>(GearTier.class);
            for (Row r : TABLE) {
                if (r.chapter() == ch) {
                    count.merge(r.tier(), 1, Integer::sum);
                }
            }
            StringBuilder line = new StringBuilder("chapter " + ch + ":");
            for (GearTier t : GearTier.values()) {
                line.append(' ').append(t.key).append(' ').append(count.getOrDefault(t, 0));
            }
            notes.add(line.toString());
            // 巨神の章 (1〜4) は六つの階級がすべてそろう
            if (ch <= 4) {
                check(f, count.size() == GearTier.values().length, "chapter " + ch + " does not use every tier: " + count);
            }
        }
    }

    // ---------------------------------------------------------------- 決まりが上へ単調
    private static void rules(List<String> f) {
        GearTier[] t = GearTier.values();
        check(f, t.length == 6 && t[0] == GearTier.COMMON && t[5] == GearTier.ARTIFACT, "the ladder is not the six tiers");
        double prevSockets = -1;
        for (int i = 0; i < t.length; i++) {
            int[] odds = t[i].socketOdds();
            double total = odds[0] + odds[1] + odds[2];
            double mean = (odds[1] + 2 * odds[2]) / total;
            if (t[i].affixed()) {
                check(f, mean > prevSockets, t[i] + " has no more sockets than the tier below");
                prevSockets = mean;
            }
            if (i == 0) {
                continue;
            }
            GearTier lo = t[i - 1];
            GearTier hi = t[i];
            check(f, hi.affixMin >= lo.affixMin && hi.affixMax >= lo.affixMax, hi + " has fewer affixes than " + lo);
            // 平均の特性の数 (少ないほうの確率で重みをつけて) も下がらない
            double loMean = lo.affixMin * lo.fewerChance + lo.affixMax * (1 - lo.fewerChance);
            double hiMean = hi.affixMin * hi.fewerChance + hi.affixMax * (1 - hi.fewerChance);
            check(f, hiMean > loMean || !hi.affixed(), hi + " rolls no more affixes on average than " + lo);
            check(f, !lo.wideAffixes || hi.wideAffixes, hi + " has narrower affixes than " + lo);
            check(f, hi.dismantleCrystals >= lo.dismantleCrystals && hi.levelMargin <= lo.levelMargin, hi + " crystals / level margin out of order");
            check(f, !lo.shardDismantle || hi.shardDismantle, hi + " stops returning resonance shards");
            check(f, hi.color.getValue() != lo.color.getValue(), hi + " shares a colour with " + lo);
        }
        check(f, !GearTier.UNCOMMON.affixed() && GearTier.RARE.affixed(), "affixes should start at rare");
        check(f, GearTier.RARE.aspectHost() && GearTier.EPIC.aspectHost() && !GearTier.HEROIC.aspectHost() && !GearTier.ARTIFACT.aspectHost(), "aspect hosts are rare and epic");
        check(f, GearTier.RARE.meshTier.equals("rare") && GearTier.EPIC.meshTier.equals("rare") && GearTier.HEROIC.meshTier.equals("legend")
                && GearTier.ARTIFACT.meshTier.equals("legend") && GearTier.UNCOMMON.meshTier.equals("common"), "mesh tiers moved (models must keep their names)");
        // 分解: エピックは素材、ヒロイックは共鳴の欠片
        int[] epic = ForgeRules.dismantleYield(new ItemStack(item("whalebone_greatsword")));
        int[] heroic = ForgeRules.dismantleYield(new ItemStack(item("captain_pike")));
        check(f, epic[0] == 0 && epic[1] == GearTier.EPIC.dismantleValue && heroic[0] == GearTier.HEROIC.dismantleValue && heroic[1] == 0, "dismantle yields by tier");
    }

    // ---------------------------------------------------------------- 武器 (一体への 1 秒あたり)
    private static double speed(Item item) {
        double s = 4;
        for (ItemAttributeModifiers.Entry e : item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_SPEED) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                s += e.modifier().amount();
            }
        }
        return s;
    }

    private static double plain(String path, int enhance) {
        Item it = item(path);
        return (GearEvents.weaponAttack(it) + GearEvents.enhanceAttack(it, enhance)) * speed(it);
    }

    private static String ns(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    // 近接の目安にそろえた 1 秒あたり (弓は 0.85 で割り戻す)
    static double dps(String path, int e) {
        String id = ns(path);
        return switch (path) {
            case "captain_pike" -> plain(path, e) * GearCurve.pikeProc(e);
            case "waterway_fang" -> plain(path, e) + GearCurve.fangBleed(e);
            case "tide_shell_trident", "tidecaller_claw", "coral_harpoon" -> TideGearSelfTest.dps(id, e);
            case "thunder_wing_bow" -> StormGearSelfTest.dps(StormGear.BOW, e) / RANGED;
            case "abyss_storm_bow" -> AbyssBowItem.dps(e) / RANGED;
            case "raiju_claw", "warden_glaive" -> StormGearSelfTest.dps(id, e);
            case "ember_war_axe", "smith_hammer", "thrall_poker" -> EmberGearSelfTest.dps(id, e);
            case "abyss_lamp_sword" -> AbyssGear.swordDps(e);
            case "abyss_sinking_anchor" -> AbyssGear.anchorDps(e);
            case "abyss_pulse_dagger" -> AbyssGear.daggerDps(e);
            case "whale_bow", "tide_bow", "ember_bow", "shrine_bow" -> ((jp.revati.skyabyss.item.ChapterBowItem) item(path)).dps(e) / RANGED;
            default -> item(path) instanceof jp.revati.skyabyss.item.ChapterBowItem b ? b.dps(e) / RANGED : plain(path, e);
        };
    }

    private static boolean weapon(Item it) {
        return it instanceof net.minecraft.world.item.SwordItem || it instanceof net.minecraft.world.item.BowItem;
    }

    private static void weapons(List<String> f, List<String> notes) {
        check(f, TideGear.TRIDENT.endsWith("tide_shell_trident") && EmberGear.HAMMER.endsWith("smith_hammer"), "weapon ids moved");
        for (int ch = 1; ch <= 5; ch++) {
            Map<GearTier, double[]> lo = new EnumMap<>(GearTier.class);
            Map<GearTier, double[]> hi = new EnumMap<>(GearTier.class);
            StringBuilder line = new StringBuilder("chapter " + ch + " weapons (+0 / +10):");
            for (Row r : TABLE) {
                if (r.chapter() != ch || !weapon(item(r.id()))) {
                    continue;
                }
                double d0 = dps(r.id(), 0);
                double d10 = dps(r.id(), ModGear.MAX_ENHANCE);
                line.append(String.format(Locale.ROOT, " %s[%s] %.2f/%.2f", r.id(), r.tier().key, d0, d10));
                lo.merge(r.tier(), new double[]{d0, d10}, (a, b) -> new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1])});
                hi.merge(r.tier(), new double[]{d0, d10}, (a, b) -> new double[]{Math.max(a[0], b[0]), Math.max(a[1], b[1])});
            }
            notes.add(line.toString());
            // 下の階級のいちばん強い品を、上の階級のいちばん弱い品が下回らない (+0 と +10)
            GearTier prev = null;
            for (GearTier t : GearTier.values()) {
                if (!lo.containsKey(t)) {
                    continue;
                }
                if (prev != null) {
                    double step = t == GearTier.ARTIFACT ? ARTIFACT_STEP : 1.0;
                    check(f, lo.get(t)[0] >= hi.get(prev)[0] * step - 1e-6, "chapter " + ch + ": a " + t.key + " weapon +0 " + fmt(lo.get(t)[0])
                            + " is below the best " + prev.key + " " + fmt(hi.get(prev)[0]) + (step > 1 ? " x" + step : ""));
                    check(f, lo.get(t)[1] >= hi.get(prev)[1] - 1e-6, "chapter " + ch + ": a " + t.key + " weapon +10 " + fmt(lo.get(t)[1])
                            + " is below the best " + prev.key + " " + fmt(hi.get(prev)[1]));
                }
                prev = t;
            }
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ---------------------------------------------------------------- 防具: 旅装 (アンコモン) < 巨神の鎧 (レア)
    private static double armorSum(int chapter, GearTier tier) {
        double sum = 0;
        for (Row r : TABLE) {
            if (r.chapter() == chapter && r.tier() == tier && item(r.id()) instanceof ArmorItem a) {
                sum += a.getDefense() + a.getToughness();
            }
        }
        return sum;
    }

    private static void armor(List<String> f) {
        for (int ch = 1; ch <= 4; ch++) {
            double travel = armorSum(ch, GearTier.UNCOMMON);
            double titan = armorSum(ch, GearTier.RARE);
            check(f, travel > 0 && titan > travel, "chapter " + ch + ": travel armor " + travel + " is not below the titan armor " + titan);
        }
        check(f, armorSum(5, GearTier.RARE) > armorSum(4, GearTier.RARE), "abyss armor is not above the ember armor");
        // 巨神の鎧は章ごとに上がる (旅装も)
        StringBuilder line = new StringBuilder("armor sums by chapter (travel / titan):");
        for (int ch = 1; ch <= 5; ch++) {
            line.append(String.format(Locale.ROOT, " ch%d %.1f/%.1f", ch, armorSum(ch, GearTier.UNCOMMON), armorSum(ch, GearTier.RARE)));
            if (ch >= 2 && ch <= 4) {
                check(f, armorSum(ch, GearTier.UNCOMMON) > armorSum(ch - 1, GearTier.UNCOMMON), "chapter " + ch + " travel armor is not above chapter " + (ch - 1));
            }
        }
        // 巨神の鎧は 2 部位のセットの強靭 (+3) を入れた曲線 (GearCurve.STAGES) で上がる: 防御 + 強靭が章ごとに上がる
        for (int i = 1; i < GearCurve.STAGES.size(); i++) {
            GearCurve.Stage a0 = GearCurve.STAGES.get(i - 1);
            GearCurve.Stage a1 = GearCurve.STAGES.get(i);
            check(f, a1.armor() + a1.toughness() > a0.armor() + a0.toughness(), a1.id() + " armor curve is not above " + a0.id());
        }
        SkyAbyss.LOGGER.info("[selftest] gear tiers {}", line);
    }

    // ---------------------------------------------------------------- 落とし表
    private static void loot(List<String> f) {
        for (TitanGearSet set : TitanGearSets.all()) {
            float minPiece = 1;
            float maxExclusive = 0;
            for (TitanGearSet.Piece p : set.pieces()) {
                check(f, TitanGearSet.registered(p.id()), set.titan + " codex item " + p.id() + " is not registered");
                Item it = TitanGearSet.item(p.id()).orElse(null);
                GearTier tier = it instanceof ModGear g ? g.tier() : null;
                if (p.exclusive()) {
                    check(f, tier == GearTier.HEROIC, set.titan + " exclusive " + p.id() + " is " + tier + " (want heroic)");
                    maxExclusive = Math.max(maxExclusive, p.heartChance());
                } else {
                    check(f, tier == GearTier.RARE || tier == GearTier.EPIC, set.titan + " codex item " + p.id() + " is " + tier + " (want rare or epic)");
                    minPiece = Math.min(minPiece, p.heartChance());
                }
            }
            check(f, maxExclusive < minPiece, set.titan + ": an exclusive drops as often as a codex piece");
            check(f, set.exclusives().size() == 3, set.titan + " should have three exclusives");
        }
        for (String id : AbyssSetup.legends()) {
            check(f, item(id) instanceof ModGear g && g.tier() == GearTier.ARTIFACT, "abyss heart item " + id + " is not an artifact");
        }
        // 階級の高い品ほど要る Lv が近い (ヒロイック以上は推奨 −2、エピック以下は −4)
        check(f, GearLevel.required(item("captain_pike")) == GearLevel.required(item("whalebone_greatsword")) + 2, "heroic level margin");
        check(f, GearLevel.required(item("abyss_greatsword")) == GearLevel.required(item("captain_pike")), "artifact level margin");
    }

    // ---------------------------------------------------------------- 表示
    private static void display(List<String> f) {
        Map<String, String> ja = GearCurveSelfTest.lang("/assets/skyabyss/lang/ja_jp.json", f);
        Map<String, String> en = GearCurveSelfTest.lang("/assets/skyabyss/lang/en_us.json", f);
        String[] jaNames = {"コモン", "アンコモン", "レア", "エピック", "ヒロイック", "★ 伝説"};
        String[] enNames = {"Common", "Uncommon", "Rare", "Epic", "Heroic", "★ Legendary"};
        for (GearTier t : GearTier.values()) {
            check(f, jaNames[t.ordinal()].equals(ja.get(t.translationKey())), "ja tier name of " + t + " is " + ja.get(t.translationKey()));
            check(f, enNames[t.ordinal()].equals(en.get(t.translationKey())), "en tier name of " + t + " is " + en.get(t.translationKey()));
        }
        // 階級の名前の鍵は六つだけ (前の伝説・神器の鍵が残っていない)
        String prefix = GearTier.COMMON.translationKey().substring(0, GearTier.COMMON.translationKey().length() - GearTier.COMMON.key.length());
        for (Map<String, String> m : List.of(ja, en)) {
            for (String k : m.keySet()) {
                if (k.startsWith(prefix)) {
                    boolean known = false;
                    for (GearTier t : GearTier.values()) {
                        known |= t.translationKey().equals(k);
                    }
                    check(f, known, "stale tier key in the lang: " + k);
                }
            }
        }
        for (Row r : TABLE) {
            Item it = item(r.id());
            ItemStack stack = new ItemStack(it);
            Component name = it.getName(stack);
            check(f, name.getStyle().getColor() != null && name.getStyle().getColor().getValue() == r.tier().rgb(), r.id() + " name is not in the " + r.tier().key + " colour");
            List<Component> lines = new ArrayList<>();
            try {
                it.appendHoverText(stack, Item.TooltipContext.EMPTY, lines, net.minecraft.world.item.TooltipFlag.NORMAL);
            } catch (RuntimeException e) {
                f.add(r.id() + " tooltip crashed: " + e);
            }
            boolean shown = false;
            for (Component c : lines) {
                if (c.getContents() instanceof TranslatableContents tc && tc.getKey().equals(r.tier().translationKey())
                        && c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == r.tier().rgb()) {
                    shown = true;
                    break;
                }
            }
            check(f, shown, r.id() + " tooltip does not show its tier line");
            check(f, AffixRules.eligible(stack) == (r.tier().affixed() && (it instanceof net.minecraft.world.item.SwordItem || it instanceof ArmorItem
                    || it instanceof net.minecraft.world.item.ShieldItem)), r.id() + " affix eligibility does not follow its tier");
        }
    }
}

package jp.revati.skyabyss.endgame;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.facility.TownGuide;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentPois;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 土地の名声と旅帳 (docs/design/54 の 6.2.2・6.2.3)。
//   名声: 土地の点の合計 (RegionPoints.fame) で 5 段。段の境 STEPS。点は巡り路の印・大陸の大事件 (+4)・名のある強敵 (+1)・
//         言伝ての板の頼み (+2、週の頼み +5)・名所の発見 (+1) でたまる。段が上がると贈り物 (鐘銭 100 × 段・封の箱 1)、
//         その土地の町の店が値引き (DISCOUNT)、5 段で称号「〜の英雄」(四つの土地すべてで「大陸の英雄」)
//   旅帳: 土地ごとの名所 (ContinentPois の見どころ) をいくつ見つけたか。土地の名所をすべて見つけると鐘銭 500・名声 +10・称号「〜を歩き尽くした」
//   見る: 町の案内人の「名声と旅帳を見る」か /skyabyss_journal (チャットに土地ごとの段・次の段まで・名所の数)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RegionFame {
    public static final int[] STEPS = {10, 30, 70, 140, 250};
    // 段ごとの、その土地の町の店の値引き (％)
    public static final int[] DISCOUNT = {0, 5, 5, 10, 10, 15};
    // 旅帳と名声を数える土地 (都のまわり・岩鯨・潮殻・雷翼・余燼)。名声の段と称号は町のある四つだけ
    public static final int[] LANDS = {ContinentMap.MEADOW, ContinentMap.WHALE, ContinentMap.TIDE, ContinentMap.STORM, ContinentMap.EMBER};
    public static final String JOURNAL_DONE = "skyabyss_journal_done";
    public static final int JOURNAL_COINS = 500;
    public static final int JOURNAL_FAME = 10;

    private RegionFame() {
    }

    public static int tier(int points) {
        int t = 0;
        while (t < STEPS.length && points >= STEPS[t]) {
            t++;
        }
        return t;
    }

    public static int tier(ServerPlayer p, int region) {
        return tier(RegionPoints.fame(p, region));
    }

    // 町のある土地か (名声の段・称号・値引きがある)
    public static boolean townLand(int region) {
        return region >= ContinentMap.WHALE && region <= ContinentMap.EMBER;
    }

    public static String landId(int region) {
        return switch (region) {
            case ContinentMap.MEADOW -> "meadow";
            case ContinentMap.WHALE -> "whale";
            case ContinentMap.TIDE -> "tide";
            case ContinentMap.STORM -> "storm";
            case ContinentMap.EMBER -> "ember";
            default -> "island";
        };
    }

    public static Component landName(int region) {
        return Component.translatable("endgame.skyabyss.land." + landId(region));
    }

    // ---------------------------------------------------------------- 点が増えたとき (RegionPoints.add から)
    static void changed(ServerPlayer p, int region, int before, int after) {
        if (!townLand(region)) {
            return;
        }
        int t0 = tier(before);
        int t1 = tier(after);
        for (int t = t0 + 1; t <= t1; t++) {
            Notice.levelUp(p, "message.skyabyss.fame.up", landName(region), Component.translatable("endgame.skyabyss.fame.tier." + t), t);
            int coins = 100 * t;
            Coins.give(p, coins);
            int band = jp.revati.skyabyss.item.wild.WildGearTypes.band(Circuits.TOP_LEVEL[region]);
            Circuits.giveBox(p, BoxKind.rarity(t >= 4 ? GearTier.EPIC : GearTier.RARE, band));
            Notice.gain(p, "message.skyabyss.fame.gift", coins);
            if (DISCOUNT[t] > DISCOUNT[t - 1]) {
                Notice.hint(p, "message.skyabyss.fame.discount", landName(region), DISCOUNT[t]);
            }
        }
        if (t1 > t0) {
            jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
        }
    }

    // ---------------------------------------------------------------- 店の値引き (土地の町の中だけ)
    public static int discountHere(ServerPlayer p) {
        SiteData.Entry town = TownGuide.townAt(p.server, p.blockPosition());
        if (town == null) {
            return 0;
        }
        int region = Circuits.regionOfTown(town.index);
        int pct = DISCOUNT[tier(p, region)];
        // その土地の旗を持つ組合の人は、さらに引く (GuildWeek)
        if (GuildWeek.holdsFlag(p, region)) {
            pct = Math.min(GuildWeek.DISCOUNT_CAP, pct + GuildWeek.FLAG_DISCOUNT);
        }
        return pct;
    }

    // その土地の町の店での値引き (名声の段と、その土地の旗。町の中かどうかは見ない。旅の記録の画面に出す)
    public static int discountFor(ServerPlayer p, int region) {
        if (!townLand(region)) {
            return 0;
        }
        int pct = DISCOUNT[tier(p, region)];
        if (GuildWeek.holdsFlag(p, region)) {
            pct = Math.min(GuildWeek.DISCOUNT_CAP, pct + GuildWeek.FLAG_DISCOUNT);
        }
        return pct;
    }

    public static int price(ServerPlayer p, int coins) {
        int pct = discountHere(p);
        if (coins <= 0 || pct <= 0) {
            return coins;
        }
        return Math.max(1, coins - coins * pct / 100);
    }

    // ---------------------------------------------------------------- 旅帳
    public record Journal(int found, int total) {
        public boolean done() {
            return total > 0 && found >= total;
        }
    }

    public static Journal journal(ServerPlayer p, int region) {
        var found = ContinentPois.discovered(p);
        var all = ContinentPois.all();
        ContinentMap map = ContinentMap.get();
        int n = 0;
        int total = 0;
        for (int i = 0; i < all.size(); i++) {
            ContinentPois.Poi poi = all.get(i);
            if (poi.site() && map.region(poi.x(), poi.z()) == region) {
                total++;
                if (found.contains(i)) {
                    n++;
                }
            }
        }
        return new Journal(n, total);
    }

    public static boolean journalDone(ServerPlayer p, int region) {
        return (p.getPersistentData().getInt(JOURNAL_DONE) & (1 << region)) != 0;
    }

    // 名所を見つけたとき (ContinentPois.discover から)
    public static void discovered(ServerPlayer p, int region) {
        RegionPoints.add(p, region, 1);
        if (journalDone(p, region) || !journal(p, region).done()) {
            return;
        }
        p.getPersistentData().putInt(JOURNAL_DONE, p.getPersistentData().getInt(JOURNAL_DONE) | (1 << region));
        Notice.unlock(p, "message.skyabyss.journal.done", landName(region));
        Coins.give(p, JOURNAL_COINS);
        Notice.gain(p, "message.skyabyss.journal.gift", JOURNAL_COINS, JOURNAL_FAME);
        RegionPoints.add(p, region, JOURNAL_FAME);
        jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
    }

    // 称号の事実の印 (Rebirths.facts。"hero:<土地>"・"hero:all"・"walker:<土地>")
    public static java.util.Set<String> marks(ServerPlayer p) {
        java.util.Set<String> out = new java.util.HashSet<>();
        int heroes = 0;
        for (int r : LANDS) {
            if (townLand(r) && tier(p, r) >= STEPS.length) {
                out.add("hero:" + landId(r));
                heroes++;
            }
            if (journalDone(p, r)) {
                out.add("walker:" + landId(r));
            }
        }
        if (heroes >= 4) {
            out.add("hero:all");
        }
        return out;
    }

    // ---------------------------------------------------------------- 見る
    public static void show(ServerPlayer p) {
        p.sendSystemMessage(Component.translatable("message.skyabyss.journal.head").withStyle(ChatFormatting.GOLD));
        for (int r : LANDS) {
            Journal j = journal(p, r);
            Component sights = Component.translatable("message.skyabyss.journal.sights", j.found(), j.total());
            if (townLand(r)) {
                int pts = RegionPoints.fame(p, r);
                int t = tier(pts);
                Component next = t >= STEPS.length ? Component.translatable("message.skyabyss.fame.max")
                        : Component.translatable("message.skyabyss.fame.next", pts, STEPS[t]);
                p.sendSystemMessage(Component.translatable("message.skyabyss.journal.line", landName(r), Component.translatable("endgame.skyabyss.fame.tier." + t),
                        next, sights).withStyle(j.done() ? ChatFormatting.AQUA : ChatFormatting.WHITE));
            } else {
                p.sendSystemMessage(Component.translatable("message.skyabyss.journal.line_plain", landName(r), sights)
                        .withStyle(j.done() ? ChatFormatting.AQUA : ChatFormatting.WHITE));
            }
        }
        // 今週の土地の旗と塔の誉れ (GuildWeek)
        GuildWeek w = GuildWeek.get(p.server);
        for (int r : LANDS) {
            String g = townLand(r) ? w.flag(r) : null;
            if (g != null) {
                p.sendSystemMessage(Component.translatable("message.skyabyss.guild_week.flag_line", landName(r), w.name(g)).withStyle(ChatFormatting.YELLOW));
            }
        }
        p.sendSystemMessage(Component.translatable("message.skyabyss.journal.foot").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    static Integer landArg(String s) {
        for (int r : LANDS) {
            if (landId(r).equals(s)) {
                return r;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_journal").executes(c -> {
            show(c.getSource().getPlayerOrException());
            return 1;
        }));
        // 開発用: /skyabyss fame add <土地> <点>
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("fame").then(Commands.literal("add")
                        .then(Commands.argument("land", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000)).executes(c -> {
                                    Integer r = landArg(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "land"));
                                    if (r == null) {
                                        return 0;
                                    }
                                    RegionPoints.add(c.getSource().getPlayerOrException(), r, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                                    return 1;
                                }))))));
    }
}

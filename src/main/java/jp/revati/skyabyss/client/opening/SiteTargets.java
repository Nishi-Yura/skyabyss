package jp.revati.skyabyss.client.opening;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.quest.ObjectiveStrip;
import jp.revati.skyabyss.opening.site.SiteTargetsPayload;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// 構造物の場所 (サーバーから届く) を、目的の帯 (ObjectiveStrip.targets) の目印として使えるようにする口 (拍子の担当が使う)。
//   SiteTargets.bind("<クエストの id>", "camp")  : その目的のとき、帯に野営地の距離と方角を出す
//   キー: camp / notebook / shelter / bell / ribs / footprint / footprint1 / footprint2 / cairn0…
//   bind していない id も、id がそのままキーなら (例えば id "shelter") その場所を指す
//   位置を直接出したいとき (岩鯨など) は bindPos("<id>", () -> pos)
public final class SiteTargets {
    private static volatile Map<String, BlockPos> sites = Map.of();
    private static final Map<String, String> BINDINGS = new ConcurrentHashMap<>();
    private static final Map<String, java.util.function.Supplier<BlockPos>> DIRECT = new ConcurrentHashMap<>();

    private SiteTargets() {
    }

    public static void receive(SiteTargetsPayload payload) {
        sites = Map.copyOf(payload.sites());
    }

    public static void bind(String questId, String siteKey) {
        BINDINGS.put(questId, siteKey);
    }

    public static void bindPos(String questId, java.util.function.Supplier<BlockPos> pos) {
        DIRECT.put(questId, pos);
    }

    @Nullable
    public static BlockPos site(String key) {
        return sites.get(key);
    }

    // 巨神の位置 (サーバーが SiteNetwork で送る。キー titan.<巨神の名前>)。章のクエストの帯が方角と距離を出す
    public static final String TITAN_PREFIX = "titan.";

    // 章のクエストの段ごとの目印: "<クエストの id>/<段の id>"。地上 (オーバーワールド) のときだけ出す
    private static void bindChapterTargets() {
        bind("main_song/board", TITAN_PREFIX + "stone_whale");
        bind("main_tide/see", TITAN_PREFIX + "tide_shell");
        bind("main_tide/board", TITAN_PREFIX + "tide_shell");
        bind("main_thunder/see", TITAN_PREFIX + "thunder_wing");
        bind("main_thunder/board", TITAN_PREFIX + "thunder_wing");
        bind("main_ember/see", TITAN_PREFIX + "ember");
        // 一章の支度の段: 骨の坑の荷・割れ殻の洞の台座 (SiteNetwork.CHAPTER_TARGETS)
        bind("ch1/trail_iron/pit", "ch1_bone_pit");
        bind("ch1/trail_blade/cave", "ch1_cleft_shell_cave");
        // 三章の支度の段: 避雷の塔の跡 (台と戸)・焼けた見張り塔
        bind("ch3/lightning_pillars/raise", "storm_ruin");
        bind("ch3/lightning_pillars/vault", "storm_ruin");
        bind("ch3/scorched_watch/clear", "ch3_scorched_watch");
    }

    // 語り部・鯨追いなど、話す相手の居場所 (サーバーが SiteNetwork で送る。キー npc.<役の名前>)
    public static final String NPC_PREFIX = "npc.";

    // 位置はどれも地上のもの。背中・ほかの次元の中では出さない (距離が意味を持たない)
    public static boolean inOverworld() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level == null || mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
    }

    @Nullable
    public static BlockPos lookup(String questId) {
        return inOverworld() ? lookupAny(questId) : null;
    }

    // 次元を問わず場所があるか (背中の中でも「この目的には場所がある」と言うため)
    @Nullable
    public static BlockPos lookupAny(String questId) {
        java.util.function.Supplier<BlockPos> d = DIRECT.get(questId);
        if (d != null) {
            BlockPos p = d.get();
            if (p != null) {
                return p;
            }
        }
        String key = BINDINGS.get(questId);
        return sites.get(key != null ? key : questId);
    }

    // 話す相手 (クエストの対象 "skyabyss:storyteller" → npc.storyteller) の居場所。次元を問わない
    @Nullable
    public static BlockPos npc(String entityId) {
        int c = entityId.indexOf(':');
        return sites.get(NPC_PREFIX + (c >= 0 ? entityId.substring(c + 1) : entityId));
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Install {
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                bindChapterTargets();
                Function<String, BlockPos> previous = ObjectiveStrip.targets;
                ObjectiveStrip.targets = id -> {
                    BlockPos p = lookup(id);
                    return p != null || previous == null ? p : previous.apply(id);
                };
            });
        }
    }
}

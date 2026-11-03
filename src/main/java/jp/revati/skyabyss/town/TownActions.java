package jp.revati.skyabyss.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.map.MapMarks;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 町のクエストと裏通りの、会話の結果 {"type": "town", "act": ...} (quest/Action が呼ぶ)。
//   open_cellar   : 酒場の地下の奥の壁を開ける (TownCellar)
//   guide_rumor   : 案内人の噂。まだ見ていない跡地 (ChapterGrounds) と、まだ着いていない章の場所の、都からの方角とおおよその距離
public final class TownActions {
    public static final Set<String> ACTS = Set.of("open_cellar", "guide_rumor", "guide_draw", "accept_offer", "decline_offer", "island_cross", "inner_cross");
    // 噂に並べる数の上限
    static final int RUMOR_MAX = 3;

    private TownActions() {
    }

    public static void run(ServerPlayer player, @Nullable NpcEntity npc, String act) {
        switch (act) {
            case "open_cellar" -> TownCellar.open(player.server);
            case "guide_rumor" -> guideRumor(player);
            case "accept_offer" -> TownQuests.acceptOffer(player);
            case "decline_offer" -> TownQuests.declineOffer(player);
            case "island_cross" -> jp.revati.skyabyss.world.continent.IslandTrail.cross(player);
            case "inner_cross" -> jp.revati.skyabyss.world.continent.ContinentFerries.cross(player);
            default -> SkyAbyss.LOGGER.warn("[town] unknown act {}", act);
        }
    }

    // ---------------------------------------------------------------- 地図屋
    // 噂にする所。ground = 跡地 (描き足せる)、それ以外は章の場所 (噂だけ)
    public record Unseen(String nameKey, BlockPos pos, @Nullable SiteData.Ground ground) {
    }

    // まだ見ていない跡地 (その人の章までのもの) と、まだ着いていない章の場所。都に近い順
    public static List<Unseen> unseen(ServerPlayer player) {
        SiteData data = SiteData.get(player.server.overworld());
        Chapters now = Chapters.of(player);
        BlockPos town = OpeningSites.shelter(player.server).orElse(null);
        List<Unseen> out = new ArrayList<>();
        for (SiteData.Ground g : data.grounds()) {
            Chapters ch = Chapters.byId(g.chapter);
            if (ch == null || ch.ordinal() > now.ordinal() || g.seen.contains(player.getUUID())) {
                continue;
            }
            out.add(new Unseen("map.skyabyss.site." + MapMarks.groundName(ch), g.center, g));
        }
        for (SiteData.ChapterPlan plan : data.plans()) {
            if (!plan.players.contains(player.getUUID())) {
                continue;
            }
            for (SiteData.Entry e : plan.sites) {
                if (e.built && !e.arrived.contains(player.getUUID()) && !e.blueprint.equals("ch1_road_cairn")) {
                    out.add(new Unseen(MapMarks.siteKey(e.siteId), e.pos, null));
                }
            }
        }
        if (town != null) {
            out.sort(java.util.Comparator.comparingDouble(u -> u.pos.distSqr(town)));
        }
        return out;
    }

    // 16 方位の名前の鍵 (message.skyabyss.town.dir.<0..15>。0 = 北、時計回り)
    public static String dirKey(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        // 北 = -z。atan2(東, 北)
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floorMod(Math.round(deg / 22.5), 16);
        return "message.skyabyss.town.dir." + i;
    }

    // おおよその距離 (50 に丸める。50 より近ければ 50)
    public static int roughDistance(BlockPos from, BlockPos to) {
        double d = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        return (int) Math.max(50, Math.round(d / 50.0) * 50);
    }

    static void guideRumor(ServerPlayer player) {
        BlockPos town = OpeningSites.shelter(player.server).orElse(player.blockPosition());
        List<Unseen> list = unseen(player);
        if (list.isEmpty()) {
            Notice.hint(player, "message.skyabyss.town.guide.none");
            return;
        }
        for (int i = 0; i < Math.min(RUMOR_MAX, list.size()); i++) {
            Unseen u = list.get(i);
            Notice.hint(player, "message.skyabyss.town.guide.rumor", Component.translatable(u.nameKey),
                    Component.translatable(dirKey(town, u.pos)), roughDistance(town, u.pos));
        }
    }

}

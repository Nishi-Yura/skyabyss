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
//   mapmaker      : 地図屋の噂。まだ見ていない跡地 (ChapterGrounds) と、まだ着いていない章の場所の、都からの方角とおおよその距離
//   mapmaker_draw : 地図屋に描き足してもらう (鐘銭 DRAW_COST)。まだ見ていない跡地のうち都にいちばん近いものを、M の地図に「見た」で載せる
public final class TownActions {
    public static final Set<String> ACTS = Set.of("open_cellar", "mapmaker", "mapmaker_draw");
    public static final int DRAW_COST = 5;
    // 噂に並べる数の上限
    static final int RUMOR_MAX = 3;

    private TownActions() {
    }

    public static void run(ServerPlayer player, @Nullable NpcEntity npc, String act) {
        switch (act) {
            case "open_cellar" -> TownCellar.open(player.server);
            case "mapmaker" -> mapmakerRumor(player);
            case "mapmaker_draw" -> mapmakerDraw(player);
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

    static void mapmakerRumor(ServerPlayer player) {
        BlockPos town = OpeningSites.shelter(player.server).orElse(player.blockPosition());
        List<Unseen> list = unseen(player);
        if (list.isEmpty()) {
            Notice.narrate(player, "message.skyabyss.town.mapmaker.none");
            return;
        }
        for (int i = 0; i < Math.min(RUMOR_MAX, list.size()); i++) {
            Unseen u = list.get(i);
            Notice.narrate(player, "message.skyabyss.town.mapmaker.rumor", Component.translatable(u.nameKey),
                    Component.translatable(dirKey(town, u.pos)), roughDistance(town, u.pos));
        }
    }

    // 描き足したら true
    static boolean mapmakerDraw(ServerPlayer player) {
        SiteData.Ground g = null;
        String key = null;
        for (Unseen u : unseen(player)) {
            if (u.ground != null) {
                g = u.ground;
                key = u.nameKey;
                break;
            }
        }
        if (g == null) {
            Notice.hint(player, "message.skyabyss.town.mapmaker.nothing_to_draw");
            return false;
        }
        if (!Coins.take(player, DRAW_COST)) {
            Notice.hint(player, "message.skyabyss.town.mapmaker.cost", DRAW_COST);
            return false;
        }
        g.seen.add(player.getUUID());
        SiteData.get(player.server.overworld()).setDirty();
        Notice.hint(player, "message.skyabyss.town.mapmaker.drawn", Component.translatable(key));
        return true;
    }
}

package jp.revati.skyabyss.town;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.world.continent.ContinentLayout;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentPois;
import jp.revati.skyabyss.world.continent.ContinentTowns;
import jp.revati.skyabyss.world.continent.FrontierHamlets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

// 土地の人 (RegionFolk: 土地の町・野営・外れの集落) の頼みの行き先を、目的の帯と標 (client/quest/ObjectiveStrip) へ渡す。
// TownNetwork が都の頼みの場所と一緒に送る。鍵は「クエスト/段」(段の最初の目的) と「クエスト/段#番号」(目的ごと。済んでいない最初の目的を指す)。
//   poi:@<名>          → その名の見どころ (建っていれば建った所、まだなら置き場の地面)
//   poi:<種類>          → 頼んだ人にいちばん近い、その種類の見どころ
//   town_hand:guest_<人> → その人の立つ印 (町・集落・野営)
// 見つけていない見どころも指す (地図には出さない。行き先は標が案内する。docs/design/40 の 3.3)
public final class RegionErrandSpots {
    private RegionErrandSpots() {
    }

    public static void addTo(MinecraftServer server, Map<String, BlockPos> out) {
        ServerLevel over = server.overworld();
        if (over == null || !(over.getChunkSource().getGenerator() instanceof jp.revati.skyabyss.world.continent.ContinentChunkGenerator)) {
            return;
        }
        // 頼んだ人のいない頼み (言伝ての板の日替わり・週替わり): 倒す敵の出る土地の真ん中を、場所の決まっていない段の行き先に
        for (QuestDef def : QuestManager.all()) {
            if (def.giver() != null && !def.giver().isEmpty()) {
                continue;
            }
            BlockPos land = foeLand(over, def);
            if (land != null) {
                out.put(def.id() + "/giver", land);
            }
        }
        for (QuestDef def : QuestManager.all()) {
            if (!def.id().startsWith("town/") || def.giver() == null) {
                continue;
            }
            String giver = def.giver().startsWith(SkyAbyss.MODID + ":guest_") ? def.giver().substring(SkyAbyss.MODID.length() + 7) : null;
            RegionFolk.Folk folk = giver == null ? null : RegionFolk.byId(giver);
            if (folk == null) {
                continue;
            }
            BlockPos home = standOf(over, folk);
            // 頼んだ人の居場所 (場所の決まっていない段の標の行き先。client/quest/ObjectiveStrip)
            if (home != null) {
                out.put(def.id() + "/giver", home);
            }
            for (QuestDef.Stage st : def.stages()) {
                for (int i = 0; i < st.objectives().size(); i++) {
                    QuestDef.Objective ob = st.objectives().get(i);
                    if (!ob.type().equals("signal") || ob.targets().isEmpty()) {
                        continue;
                    }
                    BlockPos at = spotOf(over, ob.targets().get(0), home);
                    if (at != null) {
                        out.putIfAbsent(def.id() + "/" + st.id(), at);
                        out.put(def.id() + "/" + st.id() + "#" + i, at);
                    }
                }
            }
        }
    }

    // 倒す目的の敵が湧く土地 (湧きの表) の真ん中 (地図の土地の目安の札の所)。見つからなければ null
    @Nullable
    static BlockPos foeLand(ServerLevel over, QuestDef def) {
        List<List<jp.revati.skyabyss.world.continent.RegionSpawner.Entry>> table = jp.revati.skyabyss.world.continent.RegionSpawner.table();
        String[] names = {"meadow", "start", "whale", "tide", "storm", "ember"};
        for (QuestDef.Stage st : def.stages()) {
            for (QuestDef.Objective ob : st.objectives()) {
                if (!ob.type().equals("kill")) {
                    continue;
                }
                for (String target : ob.targets()) {
                    for (int r = 0; r < table.size() && r < names.length; r++) {
                        for (var e : table.get(r)) {
                            if (e.entity().equals(target)) {
                                var level = ContinentLayout.get().levels().get(names[r]);
                                if (level != null) {
                                    BlockPos c = level.pos();
                                    return new BlockPos(c.getX(), ContinentMap.get().surface(c.getX(), c.getZ()) + 1, c.getZ());
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public static BlockPos spotOf(ServerLevel over, String signal, @Nullable BlockPos home) {
        if (signal.startsWith("town_hand:guest_")) {
            RegionFolk.Folk f = RegionFolk.byId(signal.substring("town_hand:guest_".length()));
            return f == null ? null : standOf(over, f);
        }
        if (signal.startsWith("poi:@")) {
            String name = signal.substring(5);
            List<ContinentPois.Poi> all = ContinentPois.all();
            for (int i = 0; i < all.size(); i++) {
                if (name.equals(all.get(i).name())) {
                    return poiAt(over, i, all.get(i));
                }
            }
            return null;
        }
        if (signal.startsWith("poi:") && home != null) {
            String kind = signal.substring(4);
            List<ContinentPois.Poi> all = ContinentPois.all();
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < all.size(); i++) {
                ContinentPois.Poi p = all.get(i);
                if (p.kind().equals(kind)) {
                    double d = Math.hypot(p.x() - home.getX(), p.z() - home.getZ());
                    if (d < bd) {
                        bd = d;
                        best = i;
                    }
                }
            }
            return best < 0 ? null : poiAt(over, best, all.get(best));
        }
        return null;
    }

    static BlockPos poiAt(ServerLevel over, int index, ContinentPois.Poi poi) {
        for (SiteData.Entry e : SiteData.get(over).entries(ContinentPois.KIND)) {
            if (e.index == index && e.built) {
                return e.pos;
            }
        }
        return new BlockPos(poi.x(), ContinentMap.get().surface(poi.x(), poi.z()) + 1, poi.z());
    }

    // 土地の人の立つ所 (建っていなければ、集落は置き場の地面。町と野営は null)
    @Nullable
    public static BlockPos standOf(ServerLevel over, RegionFolk.Folk f) {
        SiteData data = SiteData.get(over);
        if (f.town().startsWith("hamlet_")) {
            String key = f.town().substring("hamlet_".length());
            List<ContinentLayout.Frontier> spots = FrontierHamlets.spots();
            for (int i = 0; i < spots.size(); i++) {
                if (spots.get(i).key().equals(key)) {
                    SiteData.Entry e = FrontierHamlets.entry(data, i);
                    BlockPos at = e != null && e.built ? e.extra(f.mark()) : null;
                    if (at != null) {
                        return at;
                    }
                    BlockPos c = spots.get(i).pos();
                    return new BlockPos(c.getX(), ContinentMap.get().surface(c.getX(), c.getZ()) + 1, c.getZ());
                }
            }
            return null;
        }
        if (f.town().equals(RegionFolk.CAMP)) {
            List<ContinentPois.Poi> all = ContinentPois.all();
            for (SiteData.Entry e : data.entries(ContinentPois.KIND)) {
                if (e.built && e.index >= 0 && e.index < all.size() && f.mark().equals(all.get(e.index).name())) {
                    return e.extra("folk");
                }
            }
            return null;
        }
        for (SiteData.Entry e : data.entries(ContinentTowns.KIND)) {
            if (e.built && f.town().equals(ContinentTowns.region(e.index))) {
                return e.extra(f.mark());
            }
        }
        return null;
    }
}

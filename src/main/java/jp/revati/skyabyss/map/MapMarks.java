package jp.revati.skyabyss.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.opening.OpeningBeat;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.opening.OpeningState;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.end.TitanDormancy;
import jp.revati.skyabyss.titan.travel.TitanCalls;
import jp.revati.skyabyss.titan.travel.TravelDest;
import jp.revati.skyabyss.titan.travel.TravelState;
import jp.revati.skyabyss.titan.travel.TravelWorldData;
import jp.revati.skyabyss.titan.travel.Voyages;
import jp.revati.skyabyss.titan.young.YoungReach;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 旅の地図 (M キー。docs/design/33 の 4 章) に載せる印を、サーバーで一人ぶん集める。**印の出どころはここ一か所**。
// 地図の画面 (client/map/TravelMapScreen) はこの一覧と、クライアントがもともと持っている自分・仲間・墓・目的の標だけを描く。
//
//   町 (鐘の宿り)   序盤の場所 shelter。語り部に会った (STORYTELLER) か近くに来たら「見た」、それまでは野営地からの方角だけの「噂」
//   野営地          camp。いつも見た (目覚めの場所)
//   巨大な足跡      footprint。足跡の拍子 (FOOTPRINTS) のあと
//   巨神の骨        ribs。骨の拍子 (BONES) のあと見た。イサナ (ISANA) のあとは町からの方角の噂
//   積み石          cairn と一章の道の積み石。町を見たあと、道しるべの線 (点) として
//   章の場所        ChapterSites の小要素。その人が使う拠点の分で、近くに着いた (arrived) ものだけ
//   碑              覚えた碑 (TravelState.waymarks)。壊された碑は出さない
//   巨神            出会った巨神の今の場所 (読み込まれていなければ最後に見た場所)。眠った巨神・まだ姿を見せない巨神は出さない
// すでに遊んでいた人 (OpeningState.legacy) には序盤の場所を全部「見た」で出す。新しい保存は足さない (記録はどれも既存のものを読む)。
//
// W2 (章の跡地を方位と距離で置く。33 の 3 章) と 32 の祠 (R3) ができたら、その場所はここへ足す (kind = SITE / SHRINE。噂と見たの二段は rumor で)。
// 言伝ての板の依頼の目印も同じ。画面の側は kind を知っていれば描ける (MapSprites)
public final class MapMarks {
    // 印の種類 (画面の絵の番号と同じ順。MapSprites)
    public enum Kind {
        TOWN, CAMP, FOOTPRINT, RIBS, WAYMARK, SITE, TITAN, SHRINE, CAIRN, SPAWN
    }

    // 一つの印。name は文の鍵の材料 (種類ごと。画面の MapText が文にする)、extra は付け足し (碑の土地の名・章の ID)。
    // rumor = 噂 (x, z はおおよその位置。画面は距離を出さず方角だけ)。color は縁取りの色 (0xRRGGBB。0 なら種類の既定)
    public record Mark(Kind kind, String id, String name, String extra, int x, int z, boolean rumor, int color) {
    }

    // 地図の中心 (町。まだ決まっていなければ世界のスポーン地点) と印
    public record Sheet(int centerX, int centerZ, boolean townKnown, List<Mark> marks) {
    }

    // 送る印の上限 (仲間・墓・目的はクライアントの分で、ここには入らない)
    public static final int MAX = 96;
    // 「近くに来た」とみなす距離 (町を見た)
    static final int SEEN_NEAR = 64;

    private MapMarks() {
    }

    // ---------------------------------------------------------------- 一人ぶん
    public static Sheet collect(ServerPlayer player) {
        ServerLevel over = player.server.overworld();
        SiteData data = SiteData.get(over);
        OpeningState opening = OpeningDirector.state(player);
        BlockPos here = player.level() == over ? player.blockPosition() : null;
        Knowledge k = Knowledge.of(opening, here);
        List<Mark> out = new ArrayList<>();
        List<Spot> spots = new ArrayList<>();
        if (data.planned()) {
            for (SiteData.Entry e : data.entries()) {
                spots.add(new Spot(e.kind, e.index, e.pos));
            }
        }
        BlockPos town = openingMarks(spots, k, out);
        chapterMarks(data, player.getUUID(), out);
        waymarkMarks(Voyages.state(player), TravelWorldData.get(player.server), out);
        titanMarks(player, over, out);
        if (data.first("camp") == null) {
            BlockPos s = over.getSharedSpawnPos();
            out.add(new Mark(Kind.SPAWN, "spawn", "spawn", "", s.getX(), s.getZ(), false, 0));
        }
        BlockPos center = town != null ? town : over.getSharedSpawnPos();
        boolean townKnown = out.stream().anyMatch(m -> m.kind() == Kind.TOWN && !m.rumor());
        return new Sheet(center.getX(), center.getZ(), townKnown, out.size() > MAX ? List.copyOf(out.subList(0, MAX)) : List.copyOf(out));
    }

    // 序盤の拍子から読む「知っていること」(純粋。自己テスト向けに分けてある)
    public record Knowledge(boolean legacy, Set<OpeningBeat> done, @Nullable BlockPos here) {
        static Knowledge of(OpeningState s, @Nullable BlockPos here) {
            return new Knowledge(s.legacy(), s.doneBeats(), here);
        }

        boolean did(OpeningBeat beat) {
            return legacy || done.contains(beat);
        }

        boolean near(BlockPos p) {
            return here != null && horizontal(here, p) <= SEEN_NEAR;
        }
    }

    // 序盤の場所の一つ (SiteData.Entry の読むところだけ)
    public record Spot(String kind, int index, BlockPos pos) {
    }

    // 序盤の場所の印を out に足し、町の位置 (なければ null) を返す
    @Nullable
    public static BlockPos openingMarks(List<Spot> entries, Knowledge k, List<Mark> out) {
        Spot shelter = first(entries, "shelter");
        Spot camp = first(entries, "camp");
        BlockPos town = shelter == null ? null : shelter.pos();
        boolean townSeen = town != null && (k.did(OpeningBeat.STORYTELLER) || k.near(town));
        if (camp != null) {
            out.add(new Mark(Kind.CAMP, "camp", "camp", "", camp.pos().getX(), camp.pos().getZ(), false, 0));
        }
        if (town != null) {
            if (townSeen) {
                out.add(new Mark(Kind.TOWN, "town", "town", "", town.getX(), town.getZ(), false, 0));
            } else {
                // 野営地から見た煙の方角だけ (野営地がなければ出さない)
                if (camp != null) {
                    BlockPos r = rumor(camp.pos(), town, "town".hashCode());
                    out.add(new Mark(Kind.TOWN, "town", "town", "", r.getX(), r.getZ(), true, 0));
                }
            }
        }
        if (townSeen) {
            for (Spot e : entries) {
                if (e.kind().equals("cairn")) {
                    out.add(new Mark(Kind.CAIRN, "cairn" + e.index(), "cairn", "", e.pos().getX(), e.pos().getZ(), false, 0));
                }
            }
        }
        if (k.did(OpeningBeat.FOOTPRINTS)) {
            for (Spot e : entries) {
                if (e.kind().equals("footprint")) {
                    out.add(new Mark(Kind.FOOTPRINT, "footprint" + e.index(), "footprint", "", e.pos().getX(), e.pos().getZ(), false, 0));
                }
            }
        }
        Spot ribsSpot = first(entries, "ribs");
        BlockPos ribs = ribsSpot == null ? null : ribsSpot.pos();
        if (ribs != null) {
            int whale = TravelDest.titanColor(Titans.STONE_WHALE);
            if (k.did(OpeningBeat.BONES) || k.near(ribs)) {
                out.add(new Mark(Kind.RIBS, "ribs", "ribs", "", ribs.getX(), ribs.getZ(), false, whale));
            } else if (k.did(OpeningBeat.ISANA) && town != null) {
                BlockPos r = rumor(town, ribs, "ribs".hashCode());
                out.add(new Mark(Kind.RIBS, "ribs", "ribs", "", r.getX(), r.getZ(), true, whale));
            }
        }
        return town;
    }

    // 章の小要素: その人の拠点の分で、着いたものだけ。一章の道の積み石は道しるべの点に
    static void chapterMarks(SiteData data, UUID player, List<Mark> out) {
        for (SiteData.ChapterPlan plan : data.plans()) {
            if (!plan.players.contains(player)) {
                continue;
            }
            Chapters ch = Chapters.byId(plan.chapter);
            int color = ch == null || ch.titan() == null ? 0 : TravelDest.titanColor(ch.titan());
            for (SiteData.Entry e : plan.sites) {
                if (!e.built || !e.arrived.contains(player)) {
                    continue;
                }
                boolean roadCairn = e.blueprint.equals("ch1_road_cairn");
                out.add(new Mark(roadCairn ? Kind.CAIRN : Kind.SITE, e.siteId + "#" + e.index + "@" + plan.anchor.asLong(), e.siteId, plan.chapter,
                        e.pos.getX(), e.pos.getZ(), false, color));
            }
        }
    }

    static void waymarkMarks(TravelState state, TravelWorldData world, List<Mark> out) {
        for (BlockPos pos : state.waymarks) {
            TravelWorldData.Waymark mark = world.waymark(pos);
            if (mark == null) {
                continue;
            }
            out.add(new Mark(Kind.WAYMARK, "w" + pos.asLong(), mark.name(), mark.biome(), pos.getX(), pos.getZ(), false, 0));
        }
    }

    static void titanMarks(ServerPlayer player, ServerLevel over, List<Mark> out) {
        for (String titan : Titans.all()) {
            if (!TitanCalls.known(titan) || !TitanCalls.met(player, titan) || TitanDormancy.isResting(player.server, titan)) {
                continue;
            }
            Vec3 at = YoungReach.adultPos(over, titan);
            if (at == null) {
                continue;
            }
            out.add(new Mark(Kind.TITAN, "t:" + titan, titan, "", (int) Math.floor(at.x), (int) Math.floor(at.z), false, TravelDest.titanColor(titan)));
        }
    }

    // ---------------------------------------------------------------- 噂の位置
    // from から見た to の方角を 16 方位に丸め、距離を 100 ブロックに丸めた点 (本当の位置は送らない)。salt で印ごとに少しだけずらす
    public static BlockPos rumor(BlockPos from, BlockPos to, int salt) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.max(100, Math.round(Math.hypot(dx, dz) / 100.0) * 100.0);
        double step = Math.PI / 8;
        double a = Math.round(Math.atan2(dz, dx) / step) * step;
        // 同じ方角の噂が重ならないよう、丸めた方角の中で ±1/4 だけ (印ごとに決まった量)
        a += ((Math.floorMod(salt, 7) - 3) / 12.0) * step;
        return new BlockPos(from.getX() + (int) Math.round(Math.cos(a) * dist), 0, from.getZ() + (int) Math.round(Math.sin(a) * dist));
    }

    static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    @Nullable
    private static Spot first(List<Spot> entries, String kind) {
        for (Spot e : entries) {
            if (e.kind().equals(kind)) {
                return e;
            }
        }
        return null;
    }

    // ChapterSites の要素の名前の鍵 (無ければ画面は章の名前で呼ぶ)
    public static String siteKey(String elementId) {
        return "map.skyabyss.site." + elementId.replace(ChapterSites.KIND_PREFIX, "");
    }
}

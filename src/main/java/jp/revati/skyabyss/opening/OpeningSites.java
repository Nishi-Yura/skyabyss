package jp.revati.skyabyss.opening;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

// 序盤の構造物 4 つ (docs/design/17 の 4 章) の場所を返す窓口。拍子 (opening/) と語りが、これだけを見ればよい。
//   野営地 CAMP        : 目覚めの場所。スポーン地点 (近い陸へ動かすこともある)。9x9 ほど
//   鐘の宿り SHELTER   : 語り部の家。野営地から 140〜220 ブロック。バニラの鐘がある (NpcSpawner が語り部を立たせる)
//   巨大な足跡 FOOTPRINT: 野営地〜宿りの道から 30〜60 ブロック外れた所に 3 つ (index 0 が野営地に一番近い)
//   巨神の骨 RIBS      : 野営地から 250〜350 ブロックの丘 (宿りと違う方角)
// どれもオーバーワールド。サーバーが始まったときに場所を決め (planned)、何 tick かに分けて建てる (built)。
// 決めたあと、建てるまでの間に、そこに人の建物があると場所を少しずらすことがある。建て終わった (built) 場所は動かない。
// 戻り値の BlockPos は「立つ場所」(地面の一つ上)。計画前は Optional.empty / 空のリスト。
public final class OpeningSites {
    public enum Kind {
        CAMP("camp", 12),
        SHELTER("shelter", 16),
        FOOTPRINT("footprint", 27),
        RIBS("ribs", 45);

        public final String id;
        // この距離 (水平) の中にいれば「その場所にいる」とみなす半径
        public final int radius;

        Kind(String id, int radius) {
            this.id = id;
            this.radius = radius;
        }
    }

    // 一つの場所。index は足跡の番号 (ほかは 0)
    public record Site(Kind kind, int index, BlockPos pos, boolean built) {
    }

    private OpeningSites() {
    }

    // ---------------------------------------------------------------- 場所を決めたか・建てたか
    public static boolean planned(MinecraftServer server) {
        return data(server).planned();
    }

    // 4 種類の場所が決まっていて、すべて建て終わっているか
    public static boolean allBuilt(MinecraftServer server) {
        SiteData d = data(server);
        if (!d.planned() || d.entries().isEmpty()) {
            return false;
        }
        for (SiteData.Entry e : d.entries()) {
            if (!e.built) {
                return false;
            }
        }
        return true;
    }

    public static boolean built(MinecraftServer server, Kind kind) {
        List<SiteData.Entry> list = data(server).entries(kind.id);
        if (list.isEmpty()) {
            return false;
        }
        for (SiteData.Entry e : list) {
            if (!e.built) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 場所
    public static Optional<BlockPos> camp(MinecraftServer server) {
        return pos(server, Kind.CAMP);
    }

    // 宿りの庭の入口に立つ場所 (鐘のそば)
    public static Optional<BlockPos> shelter(MinecraftServer server) {
        return pos(server, Kind.SHELTER);
    }

    // 野営地の、地面の古い帳面の場所 (拾う物の位置)
    public static Optional<BlockPos> notebook(MinecraftServer server) {
        return extra(server, Kind.CAMP, "notebook");
    }

    // 鐘のブロック (POI "meeting"。語り部が立つ基準)
    public static Optional<BlockPos> bell(MinecraftServer server) {
        return extra(server, Kind.SHELTER, "bell");
    }

    // 都の社の祭壇 (転生の台座。社が開いたあと)
    public static Optional<BlockPos> capitalAltar(MinecraftServer server) {
        return extra(server, Kind.SHELTER, "altar");
    }

    // 都の中の鯨追いの野営の場所 (都は舗装で自然の地面がないので、設計図が庭に用意する)
    public static Optional<BlockPos> chaserCamp(MinecraftServer server) {
        return extra(server, Kind.SHELTER, "chaser");
    }

    // 宿りの煙の出る焚き火 (遠くから見える目印。煙の柱の根もと)
    public static Optional<BlockPos> smoke(MinecraftServer server) {
        return extra(server, Kind.SHELTER, "smoke");
    }

    // 骨の足もとの石板 (一行の碑文)
    public static Optional<BlockPos> slab(MinecraftServer server) {
        return extra(server, Kind.RIBS, "slab");
    }

    // 巨大な足跡 3 つの中心 (野営地に近い順)
    public static List<BlockPos> footprints(MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        for (SiteData.Entry e : data(server).entries(Kind.FOOTPRINT.id)) {
            out.add(e.pos);
        }
        return out;
    }

    // pos (水平) が、巨大な足跡のどれかの形 (見える足の形) + margin ブロックの中か。円ではなく形で調べる
    public static boolean insideFootprint(MinecraftServer server, BlockPos pos, int margin) {
        SiteData d = data(server);
        for (SiteData.Entry e : d.entries(Kind.FOOTPRINT.id)) {
            if (jp.revati.skyabyss.opening.site.FootprintShape.contains(d, e, pos, margin)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<BlockPos> ribs(MinecraftServer server) {
        return pos(server, Kind.RIBS);
    }

    // 野営地から宿りへの道の積み石 (40 ブロックおき。野営地に近い順)
    public static List<BlockPos> cairns(MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        for (SiteData.Entry e : data(server).entries("cairn")) {
            out.add(e.pos);
        }
        return out;
    }

    // 全部 (自己テスト・開発コマンド・地図向け)
    public static List<Site> all(MinecraftServer server) {
        List<Site> out = new ArrayList<>();
        for (SiteData.Entry e : data(server).entries()) {
            Kind k = kind(e.kind);
            if (k != null) {
                out.add(new Site(k, e.index, e.pos, e.built));
            }
        }
        return out;
    }

    // pos (水平) がどの場所の中か (Kind.radius + extraMargin 以内。足跡は 3 つのどれか)。拍子の「足跡を見つけた」「骨の丘に着いた」の判定向け
    public static Optional<Site> siteAt(MinecraftServer server, BlockPos pos, int extraMargin) {
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (Site s : all(server)) {
            double d = horizontal(s.pos(), pos);
            if (d <= s.kind().radius + extraMargin && d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return Optional.ofNullable(best);
    }

    // pos から一番近い、その種類の場所
    public static Optional<Site> nearest(MinecraftServer server, BlockPos pos, Kind kind) {
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (Site s : all(server)) {
            if (s.kind() == kind) {
                double d = horizontal(s.pos(), pos);
                if (d < bestD) {
                    best = s;
                    bestD = d;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // 水平の距離 (ブロック)
    public static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // from から to の向き。0 = 南、90 = 西、180 = 北、270 = 東 (プレイヤーの yaw と同じ。目的の帯の方角向け)
    public static float yawTo(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (((Math.toDegrees(Math.atan2(-dx, dz)) % 360) + 360) % 360);
    }

    // ---------------------------------------------------------------- 内部
    @Nullable
    private static Kind kind(String id) {
        for (Kind k : Kind.values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    private static Optional<BlockPos> pos(MinecraftServer server, Kind kind) {
        SiteData.Entry e = data(server).first(kind.id);
        return e == null ? Optional.empty() : Optional.of(e.pos);
    }

    private static Optional<BlockPos> extra(MinecraftServer server, Kind kind, String name) {
        SiteData.Entry e = data(server).first(kind.id);
        return e == null ? Optional.empty() : Optional.ofNullable(e.extra(name));
    }

    private static SiteData data(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return SiteData.get(overworld);
    }
}

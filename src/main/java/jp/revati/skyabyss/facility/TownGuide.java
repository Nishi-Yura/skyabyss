package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.nav.LevelTerrain;
import jp.revati.skyabyss.guide.nav.PathFinder;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// 都の施設の案内 (docs/design/35 の 6.12。試遊 2026-10-23「施設の場所が分かりにくい」の案 C、屋根の旗柱なし)。
//   案内人 (AttendantRole.GUIDE。印 npc_guide_<所>: 四つの門の内と広場) に話すと、施設の一覧 (TownGuideNetwork.Menu → client/facility/TownGuideScreen)
//   一覧で選ぶと (Pick)、サーバーがその人の足もとから施設の戸の印までの歩ける道を探し (PathFinder。大通りの真ん中を好む)、その人にだけ送る (Path)。
//   灯りの描画・着いたら消す・時間で消す・帯の「〜へ向かおう」はクライアント (client/guide/TownGuideClient)。
// 印の位置は都の設計図 (capital.json) から、建てた向きと鐘の位置で世界の座標へ直す (前の版で建てた都でも、新しい印が使える)。
public final class TownGuide {
    // 案内できる都の中の範囲 (都の真ん中からの水平距離)。墓所 (西の門の外) まで入る
    public static final int RANGE = 190;
    // 探索の広さ (出発点からの四角の半径)・ノードの上限・道の長さの上限
    static final int SEARCH_RADIUS = 280;
    static final int MAX_NODES = 90_000;
    static final int MAX_PATH = 900;
    // 狭い所 (横に 3 マス先が塞がる)・屋根の下 の上乗せ
    static final float NARROW_COST = 1.3f;
    static final float ROOF_COST = 2.5f;

    // 施設の種類の色 (README の色分け。地面の灯り・一覧の札・杖の房で同じ)
    public enum Group {
        GOLD("gold", 0xF2C14E),
        BLUE("blue", 0x6FB6F2),
        RED("red", 0xEE6A52),
        VIOLET("violet", 0xBC92F0),
        GREY("grey", 0xB4B0A4);

        public final String id;
        public final int rgb;

        Group(String id, int rgb) {
            this.id = id;
            this.rgb = rgb;
        }

        public static Group of(int i) {
            return values()[Math.floorMod(i, values().length)];
        }
    }

    // 一覧の一行: 施設の ID (名前は gui.skyabyss.town_guide.place.<id>)、種類、戸の印 (設計図の印の名前)
    public record Place(String id, Group group, String mark) {
        public String nameKey() {
            return "gui.skyabyss.town_guide.place." + id;
        }

        public String toKey() {
            return nameKey() + ".to";
        }
    }

    // 並び = 一覧の並び (種類ごと)。裏通りの闇市・地図屋は自分で見つける所なので載せない (戸の上の名も出していない。ShelterBuilder.LABEL_HIDDEN)
    public static final List<Place> PLACES = List.of(
            new Place("tool_shop", Group.GOLD, "label_tool_shop"),
            new Place("storage", Group.GOLD, "label_storage"),
            new Place("trade", Group.GOLD, "label_trade"),
            new Place("apothecary", Group.GOLD, "label_apothecary"),
            new Place("inn", Group.BLUE, "label_inn"),
            new Place("stable", Group.BLUE, "label_stable"),
            new Place("perch", Group.BLUE, "label_perch"),
            new Place("guild", Group.RED, "open_guild"),
            new Place("smithy", Group.RED, "label_smithy"),
            new Place("training", Group.RED, "open_training"),
            new Place("shrine", Group.VIOLET, "open_shrine"),
            new Place("keep", Group.VIOLET, "open_keep"),
            new Place("hall", Group.VIOLET, "open_hall"),
            new Place("tavern", Group.GREY, "label_tavern"),
            new Place("graveyard", Group.GREY, "sign_graveyard"));

    private static final Map<java.util.UUID, Long> LAST_PICK = jp.revati.skyabyss.util.PlayerMaps.track(new java.util.concurrent.ConcurrentHashMap<>());

    public static final String GUIDE_MARK = AttendantRole.MARK_PREFIX + "guide_";

    private TownGuide() {
    }

    @Nullable
    public static Place place(String id) {
        for (Place p : PLACES) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 印 (設計図 → 世界)
    static Blueprint blueprint() {
        return Blueprint.get(System.getenv("SKYABYSS_SMALL_SHELTER") != null ? "shelter" : "capital");
    }

    // 建てた都の向きと原点 (設計図の (0, 0, 0) の世界の座標)。建てていなければ null
    private record Frame(int rot, boolean mirror, BlockPos origin) {
    }

    @Nullable
    private static Frame frame(MinecraftServer server, Blueprint bp) {
        SiteData.Entry e = SiteData.get(server.overworld()).first(OpeningSites.Kind.SHELTER.id);
        if (e == null || !e.built) {
            return null;
        }
        BlockPos bell = e.extra("bell");
        int[] bm = bp.marks.get("bell");
        if (bell == null || bm == null) {
            return null;
        }
        int rot = SiteFinder.rot(e.yaw);
        boolean mirror = SiteFinder.mirror(e.yaw);
        BlockPos rel = Blueprint.transform(bm[0], bm[1] + 1, bm[2], rot, mirror, BlockPos.ZERO);
        return new Frame(rot, mirror, bell.subtract(rel));
    }

    // 都の真ん中 (設計図の原点。広場の南の大通りの十字)。建てていなければ null
    @Nullable
    public static BlockPos center(MinecraftServer server) {
        Frame f = frame(server, blueprint());
        return f == null ? null : f.origin.above();
    }

    // 設計図の印のうち prefix で始まるものの、世界の座標 (建てた都がなければ空)。鐘の印と鐘の位置の差から都の原点を出す
    public static Map<String, BlockPos> worldMarks(MinecraftServer server, String prefix) {
        Map<String, BlockPos> out = new LinkedHashMap<>();
        Blueprint bp = blueprint();
        Frame f = frame(server, bp);
        if (f == null) {
            return out;
        }
        for (Map.Entry<String, int[]> m : bp.marks.entrySet()) {
            if (m.getKey().startsWith(prefix)) {
                int[] v = m.getValue();
                out.put(m.getKey(), Blueprint.transform(v[0], v[1] + 1, v[2], f.rot, f.mirror, f.origin));
            }
        }
        return out;
    }

    // 施設の戸の世界の座標 (施設 ID → 位置。印のない施設は載らない)
    public static Map<String, BlockPos> doors(MinecraftServer server) {
        Map<String, BlockPos> all = worldMarks(server, "");
        Map<String, BlockPos> out = new LinkedHashMap<>();
        for (Place p : PLACES) {
            BlockPos at = all.get(p.mark);
            if (at != null) {
                out.put(p.id, at);
            }
        }
        return out;
    }

    // 案内人の立つ所 (印 npc_guide_<所>)。Attendants の見回りが、前の版で建てた都にも案内人を立たせるのに使う
    public static Map<String, BlockPos> guideSpots(MinecraftServer server) {
        return worldMarks(server, GUIDE_MARK);
    }

    // ---------------------------------------------------------------- 土地の町 (2026-10-29 の試遊「最初の街以外、お店の場所教えてくれるやついない」)
    // 土地の町は都と同じ施設を持つ (係の人の印 npc_<役>)。広場の「町の案内人」(town/RegionFolk の town_guide_<町>) が同じ一覧を開き、
    // 係の人の立つ所まで灯りで案内する
    static final int TOWN_RANGE = 70;
    static final Map<String, String> TOWN_DOORS = Map.ofEntries(Map.entry("tool_shop", "npc_tool_merchant"), Map.entry("storage", "npc_storage_keeper"),
            Map.entry("trade", "npc_trade_keeper"), Map.entry("apothecary", "npc_apothecary"), Map.entry("inn", "npc_innkeeper"),
            Map.entry("stable", "npc_stable_keeper"), Map.entry("perch", "npc_perch_keeper"), Map.entry("guild", "npc_guild_clerk"),
            Map.entry("smithy", "npc_smith"), Map.entry("training", "npc_training_master"), Map.entry("shrine", "npc_shrine_priest"));

    @Nullable
    static SiteData.Entry townAt(MinecraftServer server, BlockPos at) {
        for (SiteData.Entry e : SiteData.get(server.overworld()).entries(jp.revati.skyabyss.world.continent.ContinentTowns.KIND)) {
            if (e.built && OpeningSites.horizontal(e.pos, at) <= TOWN_RANGE) {
                return e;
            }
        }
        return null;
    }

    // いる所 (都か土地の町) の施設の戸 (施設 ID → 位置)。どちらでもなければ空
    public static Map<String, BlockPos> doorsAt(MinecraftServer server, BlockPos at) {
        if (inTown(server, at)) {
            return doors(server);
        }
        Map<String, BlockPos> out = new LinkedHashMap<>();
        SiteData.Entry town = townAt(server, at);
        if (town != null) {
            for (Place p : PLACES) {
                String mark = TOWN_DOORS.get(p.id);
                BlockPos d = mark == null ? null : town.extra(mark);
                if (d != null) {
                    out.put(p.id, d);
                }
            }
        }
        return out;
    }

    public static boolean inTown(MinecraftServer server, BlockPos at) {
        BlockPos c = center(server);
        if (c == null) {
            return false;
        }
        double dx = at.getX() - c.getX();
        double dz = at.getZ() - c.getZ();
        return dx * dx + dz * dz <= (double) RANGE * RANGE;
    }

    // ---------------------------------------------------------------- 道
    // 大通りを好む地形: 横に 3 マス先 (四方) のどれかが頭の高さで塞がる所 (路地・壁ぎわ) と、屋根の下は手間を足す
    public static final class Streets implements PathFinder.Terrain {
        private final PathFinder.Terrain base;
        private final HashMap<Long, Float> extra = new HashMap<>();

        public Streets(PathFinder.Terrain base) {
            this.base = base;
        }

        @Override
        public PathFinder.Cell cell(int x, int y, int z) {
            return base.cell(x, y, z);
        }

        @Override
        public float extraCost(int x, int y, int z) {
            return extra.computeIfAbsent(BlockPos.asLong(x, y, z), k -> {
                float c = 0f;
                if (!open(x + 3, y + 1, z) || !open(x - 3, y + 1, z) || !open(x, y + 1, z + 3) || !open(x, y + 1, z - 3)) {
                    c += NARROW_COST;
                }
                for (int dy = 2; dy <= 7; dy++) {
                    if (base.cell(x, y + dy, z) == PathFinder.Cell.SOLID) {
                        c += ROOF_COST;
                        break;
                    }
                }
                return c;
            });
        }

        private boolean open(int x, int y, int z) {
            PathFinder.Cell c = base.cell(x, y, z);
            return c == PathFinder.Cell.AIR || c == PathFinder.Cell.WATER;
        }
    }

    // from から戸 door までの道 (先頭 = 出発点)。terrain は本物の世界 (LevelTerrain) か、自己テストの設計図
    public static PathFinder.Result route(PathFinder.Terrain terrain, BlockPos from, BlockPos door) {
        PathFinder pf = new PathFinder(new Streets(terrain), from, door, SEARCH_RADIUS, MAX_NODES, MAX_PATH);
        pf.step(Integer.MAX_VALUE);
        PathFinder.Result r = pf.result();
        return r == null ? PathFinder.Result.NONE : r;
    }

    // 選んだ施設への道の知らせ (送る形。点は間引いたもの)
    public static TownGuideNetwork.Path plan(PathFinder.Terrain terrain, BlockPos from, Place p, BlockPos door) {
        PathFinder.Result r = route(terrain, from, door);
        return new TownGuideNetwork.Path(p.id, p.group.ordinal(), door, r.reached(), simplify(r.nodes()));
    }

    // 道の点を間引く (向きが変わる所・高さが変わる所と、8 マスごとは残す)。送る量を減らす
    public static List<BlockPos> simplify(List<BlockPos> nodes) {
        if (nodes.size() <= 2) {
            return nodes;
        }
        List<BlockPos> out = new ArrayList<>();
        out.add(nodes.get(0));
        int since = 0;
        for (int i = 1; i + 1 < nodes.size(); i++) {
            BlockPos a = nodes.get(i - 1);
            BlockPos b = nodes.get(i);
            BlockPos c = nodes.get(i + 1);
            boolean turn = (b.getX() - a.getX()) != (c.getX() - b.getX()) || (b.getZ() - a.getZ()) != (c.getZ() - b.getZ());
            boolean step = b.getY() != a.getY() || c.getY() != b.getY();
            since++;
            if (turn || step || since >= 8) {
                out.add(b);
                since = 0;
            }
        }
        out.add(nodes.get(nodes.size() - 1));
        return out;
    }

    // ---------------------------------------------------------------- 灯り
    // 地面の灯りの間 (ブロック)
    public static final double LANTERN_SPACING = 3.0;

    // 灯り一つ: 位置 (床の上)、道の上の距離 (出発点から)
    public record Lantern(double x, double y, double z, double s) {
    }

    // 道の点 (間引いたマスの列) を、3 ブロックごとの灯りに。最後の灯りは戸の前 (道の最後の点)
    public static List<Lantern> lanterns(List<BlockPos> nodes) {
        List<Lantern> out = new ArrayList<>();
        if (nodes.isEmpty()) {
            return out;
        }
        double s = 0;
        double next = 1.5;
        for (int i = 0; i + 1 < nodes.size(); i++) {
            BlockPos a = nodes.get(i);
            BlockPos b = nodes.get(i + 1);
            double ax = a.getX() + 0.5;
            double az = a.getZ() + 0.5;
            double bx = b.getX() + 0.5;
            double bz = b.getZ() + 0.5;
            double len = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
            if (len < 1.0e-6) {
                continue;
            }
            while (next <= s + len) {
                double t = (next - s) / len;
                // 高さは段の上り下りの中ほどで切り替える (段の途中に浮かない)
                double y = t < 0.5 ? a.getY() : b.getY();
                out.add(new Lantern(Mth.lerp(t, ax, bx), y, Mth.lerp(t, az, bz), next));
                next += LANTERN_SPACING;
            }
            s += len;
        }
        BlockPos last = nodes.get(nodes.size() - 1);
        if (out.isEmpty() || s - out.get(out.size() - 1).s() > 1.2) {
            out.add(new Lantern(last.getX() + 0.5, last.getY(), last.getZ() + 0.5, s));
        }
        return out;
    }

    // ---------------------------------------------------------------- 案内人から
    // 案内人の「行き先を選ぶ」: 一覧を開く (都の中でなければ断る)
    public static void openMenu(ServerPlayer player) {
        MinecraftServer server = player.server;
        Map<String, BlockPos> doors = player.level().dimension() == Level.OVERWORLD ? doorsAt(server, player.blockPosition()) : Map.of();
        if (doors.isEmpty()) {
            Notice.hint(player, "message.skyabyss.town_guide.too_far");
            return;
        }
        List<TownGuideNetwork.Entry> list = new ArrayList<>();
        for (Map.Entry<String, BlockPos> d : doors.entrySet()) {
            Place p = place(d.getKey());
            if (p != null) {
                list.add(new TownGuideNetwork.Entry(p.id, p.group.ordinal(), d.getValue()));
            }
        }
        PacketDistributor.sendToPlayer(player, new TownGuideNetwork.Menu(list));
    }

    // 一覧で選んだ (id が空なら案内をやめる)。道を探して、その人にだけ送る。送った道 (探さなかったら null)
    @Nullable
    public static TownGuideNetwork.Path pick(ServerPlayer player, String id) {
        if (id.isEmpty()) {
            PacketDistributor.sendToPlayer(player, TownGuideNetwork.Path.CLEAR);
            return null;
        }
        Place p = place(id);
        MinecraftServer server = player.server;
        Map<String, BlockPos> doors = player.level().dimension() == Level.OVERWORLD ? doorsAt(server, player.blockPosition()) : Map.of();
        if (p == null || doors.isEmpty()) {
            Notice.hint(player, "message.skyabyss.town_guide.too_far");
            return null;
        }
        BlockPos door = doors.get(id);
        // 探索は重いので、一人 1 秒に一度まで (押し続けても積もらない)
        long now = server.overworld().getGameTime();
        Long last = LAST_PICK.get(player.getUUID());
        if (door == null || (last != null && now - last < 20 && now >= last)) {
            return null;
        }
        LAST_PICK.put(player.getUUID(), now);
        long t0 = System.nanoTime();
        TownGuideNetwork.Path path = plan(new LevelTerrain(player.level(), true), player.blockPosition(), p, door);
        SkyAbyss.LOGGER.debug("[town guide] {} -> {}: {} points, reached={}, {} ms", player.getScoreboardName(), id, path.nodes().size(), path.reached(),
                (System.nanoTime() - t0) / 1_000_000);
        PacketDistributor.sendToPlayer(player, path);
        if (!path.reached()) {
            Notice.hint(player, "message.skyabyss.town_guide.no_path", Component.translatable(p.toKey()));
        }
        return path;
    }
}

package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 序盤の構造物 (野営地・鐘の宿り・巨大な足跡・巨神の骨) の場所と、建て終わったかの記録 (オーバーワールドに保存)。
// 読む側は opening.OpeningSites を使うこと (この型は建てる側 opening.site の内部)
public final class SiteData extends SavedData {
    private static final String NAME = "skyabyss_opening_sites";
    private static final Factory<SiteData> FACTORY = new Factory<>(SiteData::new, SiteData::load);

    // 一つの場所。pos は「立つ場所の中心」(地面の一つ上)。extra は構造物ごとの付属の点 (名前 → 位置)
    public static final class Entry {
        public final String kind;
        public final int index;
        public BlockPos pos;
        public boolean built;
        // 建て始めたか (止まったあとの続きで、自分で置いた物を「人の建物」と見まちがえない)
        public boolean started;
        public int yaw;
        public final List<String> extraNames = new ArrayList<>();
        public final List<BlockPos> extraPos = new ArrayList<>();
        // 章の小要素 (ChapterSites) だけが使う欄。siteId = 登録した要素の ID、blueprint = 部品の名前、anchor = 拠点 (動かしても maxDist 以内)、
        // radius = ほかの場所との重なりを避ける半径 (0 なら種類ごとの既定)、arrived = 近くに着いた人
        public String siteId = "";
        public String blueprint = "";
        @Nullable
        public BlockPos anchor;
        public int maxDist;
        public int radius;
        public final Set<UUID> arrived = new LinkedHashSet<>();

        Entry(String kind, int index, BlockPos pos, int yaw) {
            this.kind = kind;
            this.index = index;
            this.pos = pos;
            this.yaw = yaw;
        }

        public Entry extra(String name, BlockPos at) {
            int i = extraNames.indexOf(name);
            if (i >= 0) {
                extraPos.set(i, at);
            } else {
                extraNames.add(name);
                extraPos.add(at);
            }
            return this;
        }

        @Nullable
        public BlockPos extra(String name) {
            int i = extraNames.indexOf(name);
            return i < 0 ? null : extraPos.get(i);
        }

        // 印を外す (自己テストで「前の版の世界」に戻すときなど)
        public void extraRemove(String name) {
            int i = extraNames.indexOf(name);
            if (i >= 0) {
                extraNames.remove(i);
                extraPos.remove(i);
            }
        }
    }

    // 章の小要素の置き場 (章が開いた人の拠点ごとに一つ)。序盤の 4 種の entries とは別に持つ (OpeningSites が数えない)
    public static final class ChapterPlan {
        public final String chapter;
        public final BlockPos anchor;
        // この拠点を共有する人 (同じ拠点なら一緒に使う)
        public final Set<UUID> players = new LinkedHashSet<>();
        // 置こうとした要素の ID (置けなかったものも入る。同じ要素を二度試さない)
        public final Set<String> attempted = new LinkedHashSet<>();
        public final List<Entry> sites = new ArrayList<>();
        // 拠点から 300 ブロック以上離れた人のために、進行方向へ置いた計画
        public boolean ahead;

        ChapterPlan(String chapter, BlockPos anchor) {
            this.chapter = chapter;
            this.anchor = anchor;
        }

        public String key() {
            return chapter + "@" + anchor.asLong();
        }
    }

    // 章の跡地。都から決まった方位と距離の帯に、章ごとに世界で一つ。ChapterGrounds が決めて整える
    public static final class Ground {
        public final String chapter;
        // 跡地の中心 (立つ場所。地面の一つ上)。章の小要素はここを拠点にして置く
        public BlockPos center;
        // 整える形 (ChapterGrounds.Shape の名前。NONE = 合う地形があった)、その中心と、測った地面の高さ
        public String shape = "NONE";
        public BlockPos shapeCenter = BlockPos.ZERO;
        public int baseY;
        // 整え終わったか (NONE なら最初から true)
        public boolean shaped;
        // 祠 (32 の 3.1) の台座の中心 (立つ場所)。ChapterGrounds.shrineSpot が返す
        @Nullable
        public BlockPos shrine;
        // 潮殻の休む場所 (二章だけ。掘った入り江なら決まった場所と向き = fixedRest)、海の向きの点
        @Nullable
        public BlockPos sea;
        public float restYaw;
        public boolean fixedRest;
        // 都から跡地への道しるべの点 (都に近い順)
        public final List<BlockPos> road = new ArrayList<>();
        // 跡地を見た人 (地図の「見た」)
        public final Set<UUID> seen = new LinkedHashSet<>();

        Ground(String chapter, BlockPos center) {
            this.chapter = chapter;
            this.center = center;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<ChapterPlan> chapterPlans = new ArrayList<>();
    private final List<Ground> grounds = new ArrayList<>();
    private boolean groundsPlanned;
    // 場所を決めたか (決めたあとは、建てている途中でも読める)
    private boolean planned;
    // 最初に決めたときの、世界の初めのスポーン地点と、動かしたか (17-11)
    private BlockPos originalSpawn = BlockPos.ZERO;
    private boolean spawnMoved;
    private long plannedAt = -1;

    public static SiteData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean planned() {
        return planned;
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<ChapterPlan> plans() {
        return chapterPlans;
    }

    public List<Ground> grounds() {
        return grounds;
    }

    @Nullable
    public Ground ground(String chapter) {
        for (Ground g : grounds) {
            if (g.chapter.equals(chapter)) {
                return g;
            }
        }
        return null;
    }

    public boolean groundsPlanned() {
        return groundsPlanned;
    }

    // 跡地を書き込む (前の分は捨てる)
    public void setGrounds(List<Ground> list) {
        grounds.clear();
        grounds.addAll(list);
        for (Ground g : list) {
            Set<UUID> was = carrySeen.remove(g.chapter);
            if (was != null) {
                g.seen.addAll(was);
            }
        }
        groundsPlanned = true;
        setDirty();
    }

    // 自己テスト・開発コマンド用: 跡地を捨てる (次の起動か ChapterGrounds.plan で決め直す)
    public void clearGrounds() {
        grounds.clear();
        groundsPlanned = false;
        setDirty();
    }

    static Ground newGround(String chapter, BlockPos center) {
        return new Ground(chapter, center);
    }

    @Nullable
    public ChapterPlan plan(String chapter, BlockPos anchor) {
        for (ChapterPlan p : chapterPlans) {
            if (p.chapter.equals(chapter) && p.anchor.equals(anchor)) {
                return p;
            }
        }
        return null;
    }

    public ChapterPlan addPlan(String chapter, BlockPos anchor, boolean ahead) {
        ChapterPlan p = new ChapterPlan(chapter, anchor);
        p.ahead = ahead;
        Set<UUID> was = carryPlayers.remove(chapter);
        if (was != null) {
            p.players.addAll(was);
        }
        chapterPlans.add(p);
        setDirty();
        return p;
    }

    // ---------------------------------------------------------------- 大陸の作り直し (upgrade/ContinentRegen)
    // 作り直しのあとに引き継ぐ、人ごとの記録 (跡地を見た人・章の小要素に着いた人・章の拠点の人)。置き場を決め直したら付け直す
    private final Map<String, Set<UUID>> carrySeen = new HashMap<>();
    private final Map<String, Set<UUID>> carryArrived = new HashMap<>();
    private final Map<String, Set<UUID>> carryPlayers = new HashMap<>();

    private static String arrivedKey(String chapter, String siteId, int index) {
        return chapter + "|" + siteId + "|" + index;
    }

    // 置き場をすべて捨てて、新しい世界と同じに決め直させる (2026-11-09 の声「途中からのところはおかしくなるのかも。全部新しくなるようにしてほしい」)。
    // 野営地 (はじまりの場所。スポーン地点と結びつく) だけは場所を残して建て直す。人ごとの記録は carry に移して、決め直した所へ付け直す。戻り値 = 捨てた数
    public int clearForRegen() {
        int n = entries.size();
        for (Ground g : grounds) {
            if (!g.seen.isEmpty()) {
                carrySeen.computeIfAbsent(g.chapter, k -> new LinkedHashSet<>()).addAll(g.seen);
            }
        }
        for (ChapterPlan p : chapterPlans) {
            n += p.sites.size();
            if (!p.players.isEmpty()) {
                carryPlayers.computeIfAbsent(p.chapter, k -> new LinkedHashSet<>()).addAll(p.players);
            }
            for (Entry e : p.sites) {
                if (!e.arrived.isEmpty()) {
                    carryArrived.computeIfAbsent(arrivedKey(p.chapter, e.siteId, e.index), k -> new LinkedHashSet<>()).addAll(e.arrived);
                }
            }
        }
        Entry camp = first("camp");
        entries.clear();
        if (camp != null) {
            camp.built = false;
            camp.started = false;
            entries.add(camp);
        }
        chapterPlans.clear();
        grounds.clear();
        groundsPlanned = false;
        planned = false;
        setDirty();
        return n;
    }

    // 自己テスト (upgrade/UpgradeSelfTest): 作り直しで置き場を捨て、野営地を残し、記録を決め直した所へ付け直すか。保存して読み直しても残るか
    public static List<String> regenCarryCheck(HolderLookup.Provider registries) {
        List<String> f = new ArrayList<>();
        UUID a = UUID.fromString("7a0b1d00-0000-4000-8000-00000000c0a1");
        SiteData d = new SiteData();
        d.add("camp", 0, new BlockPos(1, 70, 1), 0).built = true;
        d.add("ferry", 0, new BlockPos(9, 64, 9), 0).built = true;
        d.markPlanned(5, BlockPos.ZERO, false);
        Ground g = new Ground("whale", new BlockPos(100, 70, 0));
        g.seen.add(a);
        d.setGrounds(List.of(g));
        ChapterPlan p = d.addPlan("whale", new BlockPos(100, 70, 0), false);
        p.players.add(a);
        Entry e = new Entry("ch_hut", 2, new BlockPos(110, 70, 0), 0);
        e.siteId = "hut";
        e.arrived.add(a);
        p.sites.add(e);
        d.clearForRegen();
        if (d.entries().size() != 1 || !d.entries().get(0).kind.equals("camp") || d.entries().get(0).built || d.planned() || d.groundsPlanned()
                || !d.plans().isEmpty() || !d.grounds().isEmpty()) {
            f.add("clearForRegen should keep only the camp (unbuilt) and unplan everything");
        }
        SiteData r = load(d.save(new CompoundTag(), registries), registries);
        Ground g2 = new Ground("whale", new BlockPos(120, 70, 0));
        r.setGrounds(List.of(g2));
        ChapterPlan p2 = r.addPlan("whale", new BlockPos(120, 70, 0), false);
        Entry e2 = new Entry("ch_hut", 2, new BlockPos(130, 70, 0), 0);
        e2.siteId = "hut";
        r.adoptCarried(p2, e2);
        if (!g2.seen.contains(a) || !p2.players.contains(a) || !e2.arrived.contains(a)) {
            f.add("regen carry lost a record across save: seen " + g2.seen.contains(a) + " players " + p2.players.contains(a) + " arrived " + e2.arrived.contains(a));
        }
        return f;
    }

    // 決め直した章の小要素に、前の版で着いていた人を付け直す (ChapterSites が置き場を書くとき)
    public void adoptCarried(ChapterPlan plan, Entry e) {
        Set<UUID> was = carryArrived.remove(arrivedKey(plan.chapter, e.siteId, e.index));
        if (was != null) {
            e.arrived.addAll(was);
        }
    }

    // 序盤の場所と章の小要素の場所を合わせた一覧 (重なりを避けるとき用)
    public List<Entry> allEntries() {
        List<Entry> out = new ArrayList<>(entries);
        for (ChapterPlan p : chapterPlans) {
            out.addAll(p.sites);
        }
        return out;
    }

    // 場所を捨てる (建てるのをあきらめたとき)。序盤の場所でも章の小要素でも
    public void remove(Entry e) {
        entries.remove(e);
        for (ChapterPlan p : chapterPlans) {
            p.sites.remove(e);
        }
        setDirty();
    }

    public List<Entry> entries(String kind) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind.equals(kind)) {
                out.add(e);
            }
        }
        return out;
    }

    @Nullable
    public Entry first(String kind) {
        for (Entry e : entries) {
            if (e.kind.equals(kind)) {
                return e;
            }
        }
        return null;
    }

    public Entry add(String kind, int index, BlockPos pos, int yaw) {
        Entry e = new Entry(kind, index, pos, yaw);
        entries.add(e);
        setDirty();
        return e;
    }

    public void markPlanned(long gameTime, BlockPos originalSpawn, boolean spawnMoved) {
        this.planned = true;
        this.plannedAt = gameTime;
        this.originalSpawn = originalSpawn;
        this.spawnMoved = spawnMoved;
        setDirty();
    }

    public void setPlanned(boolean v) {
        planned = v;
        setDirty();
    }

    // 計画を捨てる (自己テストと開発コマンドの作り直し用)
    public void clear() {
        entries.clear();
        grounds.clear();
        groundsPlanned = false;
        planned = false;
        setDirty();
    }

    public long plannedAt() {
        return plannedAt;
    }

    public BlockPos originalSpawn() {
        return originalSpawn;
    }

    public boolean spawnMoved() {
        return spawnMoved;
    }

    private static CompoundTag saveCarry(Map<String, Set<UUID>> m) {
        CompoundTag c = new CompoundTag();
        for (Map.Entry<String, Set<UUID>> e : m.entrySet()) {
            ListTag l = new ListTag();
            e.getValue().forEach(u -> l.add(StringTag.valueOf(u.toString())));
            c.put(e.getKey(), l);
        }
        return c;
    }

    private static void loadCarry(CompoundTag c, Map<String, Set<UUID>> m) {
        for (String k : c.getAllKeys()) {
            Set<UUID> set = new LinkedHashSet<>();
            for (Tag u : c.getList(k, Tag.TAG_STRING)) {
                set.add(UUID.fromString(u.getAsString()));
            }
            m.put(k, set);
        }
    }

    static SiteData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiteData d = new SiteData();
        d.planned = tag.getBoolean("planned");
        d.plannedAt = tag.contains("plannedAt") ? tag.getLong("plannedAt") : -1;
        d.originalSpawn = BlockPos.of(tag.getLong("originalSpawn"));
        d.spawnMoved = tag.getBoolean("spawnMoved");
        for (Tag t : tag.getList("sites", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Entry e = new Entry(c.getString("kind"), c.getInt("index"), BlockPos.of(c.getLong("pos")), c.getInt("yaw"));
            e.built = c.getBoolean("built");
            e.started = c.getBoolean("started");
            CompoundTag x = c.getCompound("extra");
            for (String key : x.getAllKeys()) {
                e.extra(key, BlockPos.of(x.getLong(key)));
            }
            d.entries.add(e);
        }
        for (Tag t : tag.getList("chapterPlans", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ChapterPlan p = new ChapterPlan(c.getString("chapter"), BlockPos.of(c.getLong("anchor")));
            p.ahead = c.getBoolean("ahead");
            for (Tag u : c.getList("players", Tag.TAG_STRING)) {
                p.players.add(UUID.fromString(u.getAsString()));
            }
            for (Tag u : c.getList("attempted", Tag.TAG_STRING)) {
                p.attempted.add(u.getAsString());
            }
            for (Tag st : c.getList("sites", Tag.TAG_COMPOUND)) {
                CompoundTag sc = (CompoundTag) st;
                Entry e = new Entry(sc.getString("kind"), sc.getInt("index"), BlockPos.of(sc.getLong("pos")), sc.getInt("yaw"));
                e.built = sc.getBoolean("built");
                e.started = sc.getBoolean("started");
                e.siteId = sc.getString("siteId");
                e.blueprint = sc.getString("blueprint");
                e.anchor = p.anchor;
                e.maxDist = sc.getInt("maxDist");
                e.radius = sc.getInt("radius");
                for (Tag u : sc.getList("arrived", Tag.TAG_STRING)) {
                    e.arrived.add(UUID.fromString(u.getAsString()));
                }
                CompoundTag ex = sc.getCompound("extra");
                for (String key : ex.getAllKeys()) {
                    e.extra(key, BlockPos.of(ex.getLong(key)));
                }
                p.sites.add(e);
            }
            d.chapterPlans.add(p);
        }
        d.groundsPlanned = tag.getBoolean("groundsPlanned");
        loadCarry(tag.getCompound("carrySeen"), d.carrySeen);
        loadCarry(tag.getCompound("carryArrived"), d.carryArrived);
        loadCarry(tag.getCompound("carryPlayers"), d.carryPlayers);
        for (Tag t : tag.getList("grounds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Ground g = new Ground(c.getString("chapter"), BlockPos.of(c.getLong("center")));
            g.shape = c.getString("shape");
            g.shapeCenter = BlockPos.of(c.getLong("shapeCenter"));
            g.baseY = c.getInt("baseY");
            g.shaped = c.getBoolean("shaped");
            g.shrine = c.contains("shrine") ? BlockPos.of(c.getLong("shrine")) : null;
            g.sea = c.contains("sea") ? BlockPos.of(c.getLong("sea")) : null;
            g.restYaw = c.getFloat("restYaw");
            g.fixedRest = c.getBoolean("fixedRest");
            for (long l : c.getLongArray("road")) {
                g.road.add(BlockPos.of(l));
            }
            for (Tag u : c.getList("seen", Tag.TAG_STRING)) {
                g.seen.add(UUID.fromString(u.getAsString()));
            }
            d.grounds.add(g);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("planned", planned);
        tag.putLong("plannedAt", plannedAt);
        tag.putLong("originalSpawn", originalSpawn.asLong());
        tag.putBoolean("spawnMoved", spawnMoved);
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putString("kind", e.kind);
            c.putInt("index", e.index);
            c.putLong("pos", e.pos.asLong());
            c.putInt("yaw", e.yaw);
            c.putBoolean("built", e.built);
            c.putBoolean("started", e.started);
            CompoundTag x = new CompoundTag();
            for (int i = 0; i < e.extraNames.size(); i++) {
                x.putLong(e.extraNames.get(i), e.extraPos.get(i).asLong());
            }
            c.put("extra", x);
            list.add(c);
        }
        tag.put("sites", list);
        ListTag plans = new ListTag();
        for (ChapterPlan p : chapterPlans) {
            CompoundTag c = new CompoundTag();
            c.putString("chapter", p.chapter);
            c.putLong("anchor", p.anchor.asLong());
            c.putBoolean("ahead", p.ahead);
            ListTag pl = new ListTag();
            p.players.forEach(u -> pl.add(StringTag.valueOf(u.toString())));
            c.put("players", pl);
            ListTag at = new ListTag();
            p.attempted.forEach(a -> at.add(StringTag.valueOf(a)));
            c.put("attempted", at);
            ListTag sl = new ListTag();
            for (Entry e : p.sites) {
                CompoundTag sc = new CompoundTag();
                sc.putString("kind", e.kind);
                sc.putInt("index", e.index);
                sc.putLong("pos", e.pos.asLong());
                sc.putInt("yaw", e.yaw);
                sc.putBoolean("built", e.built);
                sc.putBoolean("started", e.started);
                sc.putString("siteId", e.siteId);
                sc.putString("blueprint", e.blueprint);
                sc.putInt("maxDist", e.maxDist);
                sc.putInt("radius", e.radius);
                ListTag ar = new ListTag();
                e.arrived.forEach(u -> ar.add(StringTag.valueOf(u.toString())));
                sc.put("arrived", ar);
                CompoundTag ex = new CompoundTag();
                for (int i = 0; i < e.extraNames.size(); i++) {
                    ex.putLong(e.extraNames.get(i), e.extraPos.get(i).asLong());
                }
                sc.put("extra", ex);
                sl.add(sc);
            }
            c.put("sites", sl);
            plans.add(c);
        }
        tag.put("chapterPlans", plans);
        tag.putBoolean("groundsPlanned", groundsPlanned);
        tag.put("carrySeen", saveCarry(carrySeen));
        tag.put("carryArrived", saveCarry(carryArrived));
        tag.put("carryPlayers", saveCarry(carryPlayers));
        ListTag gl = new ListTag();
        for (Ground g : grounds) {
            CompoundTag c = new CompoundTag();
            c.putString("chapter", g.chapter);
            c.putLong("center", g.center.asLong());
            c.putString("shape", g.shape);
            c.putLong("shapeCenter", g.shapeCenter.asLong());
            c.putInt("baseY", g.baseY);
            c.putBoolean("shaped", g.shaped);
            if (g.shrine != null) {
                c.putLong("shrine", g.shrine.asLong());
            }
            if (g.sea != null) {
                c.putLong("sea", g.sea.asLong());
            }
            c.putFloat("restYaw", g.restYaw);
            c.putBoolean("fixedRest", g.fixedRest);
            c.putLongArray("road", g.road.stream().mapToLong(BlockPos::asLong).toArray());
            ListTag sn = new ListTag();
            g.seen.forEach(u -> sn.add(StringTag.valueOf(u.toString())));
            c.put("seen", sn);
            gl.add(c);
        }
        tag.put("grounds", gl);
        return tag;
    }
}

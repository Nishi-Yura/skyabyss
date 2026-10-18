package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 章の小要素 (E1〜E12 など。docs/design/17 の 3.8、10 の 3.6) の置き場と登録の口。章ごとの担当 (P4 の各巨神) は、自分のパッケージから
// ここへ要素を登録するだけで、場所探し・建設・保存・二重に置かない仕組みを使える (この package のほかのファイルは触らなくてよい)。
//
// ---------------------------------------------------------------- 口 (P4 はここだけを見る)
//   登録     ChapterSites.element(String id, Chapters chapter, String blueprint)      → Builder
//              .dist(int min, int max)           拠点からの水平距離 (既定 40〜140。24〜150 に収まる。150 を超えては置かない)
//              .count(int n)                     同じ要素を n 個 (既定 1。歌石 5 つなど)。index 0..n-1
//              .prefer(Prefer.ANY|HIGH|SHORE)    高台 (見張りの丘) か水辺 (漂着の荷) を選ぶ。既定 ANY
//              .arriveRadius(int r)              「着いた」とみなす水平の半径 (既定 16)
//              .post(SiteJob.Post p)             建て終わりの仕上げ (箱の中身・看板・碑。CampBuilder と同じ。job.mark("名前") で部品の印の位置が引ける)
//              .onArrive(ChapterSites.OnArrive cb)  その章の人が初めて近くに着いたとき、人ごとに一度だけ (サーバースレッド。頁・クエストの開始・字幕など)
//              .register()                       → Element。同じ ID を二度登録すると IllegalStateException
//            部品は data/skyabyss/opening/<blueprint>.json (Blueprint と同じ形式。tools/rough/opening_sites.py と同じ)。
//            登録は FMLCommonSetupEvent の enqueueWork か、自分の Setup クラスの静的初期化で。サーバーが始まる前に済んでいればよい
//            (あとから足した要素も、その章にいる人の拠点に置かれる = 古いワールドでも、新しく足した要素が出る)。
//   読む     ChapterSites.sites(server, String elementId)                  全部の拠点の分 (Site(chapter, id, index, pos, built))。pos は立つ場所 (地面の一つ上)
//            ChapterSites.sites(server, Chapters chapter)
//            ChapterSites.sitesFor(server, UUID player, String elementId)  その人が使う拠点の分だけ (クエストの目印はこちら)
//            ChapterSites.nearest(server, UUID player, String elementId, BlockPos from)
//   手動     ChapterSites.open(ServerPlayer player, Chapters chapter)       章が開いたことを知らせる (下の「呼ばれる時機」)。何度呼んでも二重に置かない
//   型       record Site(String chapter, String id, int index, BlockPos pos, boolean built)
//            interface OnArrive { void arrive(ServerPlayer player, Site site); }
//            enum Prefer { ANY, HIGH, SHORE }
//
// ---------------------------------------------------------------- 決まり
//   呼ばれる時機  サーバーの tick から 5 秒おき (OPEN_EVERY) に、オーバーワールドにいる人の Chapters.of(player) を調べる。その章の要素が一つでも登録されていて、
//                その人がその章の拠点にまだ入っていなければ、その場で open する。この呼び出しは poll() の一か所だけ。章が変わる出来事 (イベント) ができたら、
//                そのイベントから ChapterSites.open(player, chapter) を呼ぶようにして、poll の open 側 (pollOpen) は消してよい (open は何度呼んでも安全)。
//   拠点          その人のベッド (リスポーン地点。オーバーワールドのもの)、なければ今いる位置。同じ章で、すでに 120 ブロック以内に拠点があれば、それを共有する
//                (マルチで同じ家を使う人は同じ場所を使う)。離れている人は自分の拠点 (自分の分が新しく置かれる)。
//   扇            拠点のまわりに 120 度おきの 3 方向。要素を順に 3 方向へ配り、方向ごとに 24〜150 ブロックの陸を探す。陸・群系・段差の条件は、
//                見つからなければ段階的にゆるめる (最後は群系を問わず、高さの差 80 まで)。どの地形でも、拠点から 150 ブロック以内に置く。
//                (保証: 登録された小要素が合わせて 2 つ以上なら、2 つ以上が拠点から 150 以内にある。1 つしか登録しない章はその 1 つ)
//   離れた人      章が開いたあと、拠点 (と置いた場所) のどれからも 300 ブロック以上離れた人 (OP の移動・長旅) には、進行方向の 60〜120 ブロック先に
//                要素を 1 つだけ追加する (要素は順に回す。1 人 1 章に 6 回まで)。その場所を先に建てる。
//   壊さない      SiteJob の既存の調べ (自然の地面か・水・人の建物。あればずらす、あきらめる) がそのまま効く。ずらしても拠点から 150 以内。
//   二重にしない  SiteData (オーバーワールド、skyabyss_opening_sites の chapterPlans) に、拠点ごとに「章・拠点・人・置こうとした要素の ID・置いた場所」を保存する。
//                再ログイン・再起動で同じ要素を二度置かない。置けなかった要素も記録するので、ログを荒らさない。
//   古いワールド  その人の今の章の分だけ置く。過去の章の分は置かない (Chapters.of がその人の今の章だけを返すため)。
//   建設          既存の SiteBuilds の予約 (1 tick の予算 6 ms) に乗る。場所探しは別スレッド (地形生成の高さだけを見る)。
//   序盤の 4 種    OpeningSites は章の小要素を数えない (別の置き場)。
//
// ---------------------------------------------------------------- 確かめ
//   SiteSelfTest の段 "chapter sites ..." (SKYABYSS_SELFTEST_ONLY="chapter sites")
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterSites {
    public enum Prefer {
        ANY, HIGH, SHORE
    }

    public record Site(String chapter, String id, int index, BlockPos pos, boolean built) {
    }

    @FunctionalInterface
    public interface OnArrive {
        void arrive(ServerPlayer player, Site site);
    }

    public record Rules(int minDist, int maxDist, int count, Prefer prefer, int arriveRadius) {
    }

    public record Element(String id, Chapters chapter, String blueprint, Rules rules, @Nullable SiteJob.Post post, @Nullable OnArrive onArrive) {
    }

    // 定数
    public static final int OPEN_EVERY = 100;
    public static final int ARRIVE_EVERY = 20;
    // 同じ章で、この距離以内の拠点は共有する
    public static final int SHARE_DIST = 120;
    // 拠点・置いた場所のどれからもこれだけ離れたら、進行方向の先に 1 つ足す
    public static final int AWAY_DIST = 300;
    public static final int MAX_AHEAD = 6;
    public static final String KIND_PREFIX = "ch:";

    private static final Map<String, Element> ELEMENTS = new ConcurrentHashMap<>();

    private ChapterSites() {
    }

    // ---------------------------------------------------------------- 登録
    public static Builder element(String id, Chapters chapter, String blueprint) {
        return new Builder(id, chapter, blueprint);
    }

    public static final class Builder {
        private final String id;
        private final Chapters chapter;
        private final String blueprint;
        private int min = 40;
        private int max = 140;
        private int count = 1;
        private Prefer prefer = Prefer.ANY;
        private int arrive = 16;
        private SiteJob.Post post;
        private OnArrive onArrive;

        private Builder(String id, Chapters chapter, String blueprint) {
            this.id = id;
            this.chapter = chapter;
            this.blueprint = blueprint;
        }

        public Builder dist(int min, int max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public Builder count(int n) {
            this.count = Math.max(1, n);
            return this;
        }

        public Builder prefer(Prefer p) {
            this.prefer = p;
            return this;
        }

        public Builder arriveRadius(int r) {
            this.arrive = Math.max(2, r);
            return this;
        }

        public Builder post(SiteJob.Post p) {
            this.post = p;
            return this;
        }

        public Builder onArrive(OnArrive cb) {
            this.onArrive = cb;
            return this;
        }

        public Element register() {
            Element e = new Element(id, chapter, blueprint, new Rules(min, max, count, prefer, arrive), post, onArrive);
            if (ELEMENTS.putIfAbsent(id, e) != null) {
                throw new IllegalStateException("chapter site element registered twice: " + id);
            }
            return e;
        }
    }

    // 自己テスト用
    static void unregister(String id) {
        ELEMENTS.remove(id);
    }

    public static List<Element> elements(Chapters chapter) {
        List<Element> out = new ArrayList<>();
        for (Element e : ELEMENTS.values()) {
            if (e.chapter() == chapter) {
                out.add(e);
            }
        }
        // 登録の順は読み込みの順で変わるので、ID で並べる (扇への配り方が毎回同じになる)
        out.sort((a, b) -> a.id().compareTo(b.id()));
        return out;
    }

    // ---------------------------------------------------------------- 読む
    public static List<Site> sites(MinecraftServer server, String elementId) {
        List<Site> out = new ArrayList<>();
        for (SiteData.ChapterPlan p : SiteData.get(server.overworld()).plans()) {
            for (SiteData.Entry e : p.sites) {
                if (e.siteId.equals(elementId)) {
                    out.add(site(p, e));
                }
            }
        }
        return out;
    }

    public static List<Site> sites(MinecraftServer server, Chapters chapter) {
        List<Site> out = new ArrayList<>();
        for (SiteData.ChapterPlan p : SiteData.get(server.overworld()).plans()) {
            if (p.chapter.equals(chapter.id())) {
                for (SiteData.Entry e : p.sites) {
                    out.add(site(p, e));
                }
            }
        }
        return out;
    }

    public static List<Site> sitesFor(MinecraftServer server, UUID player, String elementId) {
        List<Site> out = new ArrayList<>();
        for (SiteData.ChapterPlan p : SiteData.get(server.overworld()).plans()) {
            if (!p.players.contains(player)) {
                continue;
            }
            for (SiteData.Entry e : p.sites) {
                if (e.siteId.equals(elementId)) {
                    out.add(site(p, e));
                }
            }
        }
        return out;
    }

    @Nullable
    public static Site nearest(MinecraftServer server, UUID player, String elementId, BlockPos from) {
        Site best = null;
        double bd = Double.MAX_VALUE;
        for (Site s : sitesFor(server, player, elementId)) {
            double d = horizontal(s.pos(), from);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    private static Site site(SiteData.ChapterPlan p, SiteData.Entry e) {
        return new Site(p.chapter, e.siteId, e.index, e.pos, e.built);
    }

    static double horizontal(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    // ---------------------------------------------------------------- 計画の依頼
    // 場所探しの結果待ち (サーバースレッドだけが触る)
    private record Task(String key, String chapter, BlockPos anchor, boolean ahead, Set<UUID> players, List<String> ids,
            CompletableFuture<List<ChapterPlanner.Placed>> future) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    // 人ごとの前回の位置 (進行方向を出す。保存しない)
    private static final Map<UUID, BlockPos> LAST = new HashMap<>();

    // 章が開いた人の拠点に、その章の要素を置く (まだなら)。何度呼んでも二重に置かない。戻り値 = 何かを予約したか
    public static boolean open(ServerPlayer player, Chapters chapter) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        return request(server, player.getUUID(), chapter, baseOf(player));
    }

    static BlockPos baseOf(ServerPlayer p) {
        BlockPos bed = p.getRespawnPosition();
        if (bed != null && p.getRespawnDimension() == Level.OVERWORLD) {
            return bed;
        }
        return p.blockPosition();
    }

    static boolean request(MinecraftServer server, UUID player, Chapters chapter, BlockPos base) {
        List<Element> els = elements(chapter);
        if (els.isEmpty()) {
            return false;
        }
        SiteData data = SiteData.get(server.overworld());
        SiteData.ChapterPlan plan = nearestPlan(data, chapter.id(), base);
        if (plan == null) {
            String key = "n:" + player + ":" + chapter.id();
            if (IN_FLIGHT.contains(key)) {
                return false;
            }
            // 近くの人の拠点を探している最中なら、その結果を共有する (同じ場所に二つ作らない)
            for (Task t : TASKS) {
                if (!t.ahead() && t.key().startsWith("n:") && t.chapter().equals(chapter.id()) && horizontal(t.anchor(), base) <= SHARE_DIST) {
                    t.players().add(player);
                    IN_FLIGHT.add(key);
                    return false;
                }
            }
            schedule(server, data, key, chapter, base, false, Double.NaN, Set.of(player), els, null);
            return true;
        }
        if (plan.players.add(player)) {
            data.setDirty();
        }
        return topUp(server, data, plan, chapter);
    }

    // 登録のあとに足された要素を、すでにある拠点に足す
    private static boolean topUp(MinecraftServer server, SiteData data, SiteData.ChapterPlan plan, Chapters chapter) {
        if (plan.ahead) {
            return false;
        }
        List<Element> missing = new ArrayList<>();
        for (Element e : elements(chapter)) {
            if (!plan.attempted.contains(e.id())) {
                missing.add(e);
            }
        }
        String key = "t:" + plan.key();
        if (missing.isEmpty() || IN_FLIGHT.contains(key)) {
            return false;
        }
        schedule(server, data, key, chapter, plan.anchor, false, Double.NaN, Set.of(), missing, plan);
        return true;
    }

    // 進行方向の先に 1 つ足す。戻り値 = 予約したか
    static boolean requestAhead(MinecraftServer server, UUID player, Chapters chapter, BlockPos pos, double angle) {
        List<Element> els = elements(chapter);
        if (els.isEmpty()) {
            return false;
        }
        SiteData data = SiteData.get(server.overworld());
        String key = "a:" + player + ":" + chapter.id();
        if (IN_FLIGHT.contains(key)) {
            return false;
        }
        int made = 0;
        int total = 0;
        for (SiteData.ChapterPlan p : data.plans()) {
            if (p.chapter.equals(chapter.id()) && p.ahead) {
                total++;
                if (p.players.contains(player)) {
                    made++;
                }
            }
        }
        if (made >= MAX_AHEAD) {
            return false;
        }
        // 要素を順に回す (遠くへ行くたびに別の要素)
        Element pick = els.get(total % els.size());
        schedule(server, data, key, chapter, pos, true, angle, Set.of(player), List.of(pick), null);
        return true;
    }

    private static void schedule(MinecraftServer server, SiteData data, String key, Chapters chapter, BlockPos base, boolean ahead, double angle,
            Set<UUID> players, List<Element> els, @Nullable SiteData.ChapterPlan existing) {
        ServerLevel level = server.overworld();
        List<ChapterPlanner.Req> reqs = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Element e : els) {
            ids.add(e.id());
            int half;
            int radius;
            try {
                Blueprint bp = Blueprint.get(e.blueprint());
                half = Math.max(2, Math.max(bp.rx1 - bp.rx0, bp.rz1 - bp.rz0) / 2 + 1);
                radius = half + bp.ring;
            } catch (RuntimeException ex) {
                SkyAbyss.LOGGER.error("Chapter site {}: unusable blueprint {}", e.id(), e.blueprint(), ex);
                continue;
            }
            int n = ahead ? 1 : e.rules().count();
            for (int i = 0; i < n; i++) {
                reqs.add(new ChapterPlanner.Req(e.id(), i, half, radius, e.rules().minDist(), e.rules().maxDist(), e.rules().prefer()));
            }
        }
        List<ChapterPlanner.Obstacle> obstacles = new ArrayList<>();
        for (SiteData.Entry e : data.allEntries()) {
            obstacles.add(new ChapterPlanner.Obstacle(e.pos, SiteJob.radiusOf(e)));
        }
        SiteFinder.Probe probe = new SiteFinder.Probe(level);
        int salt = chapter.ordinal();
        BlockPos at = base.immutable();
        CompletableFuture<List<ChapterPlanner.Placed>> f = CompletableFuture.supplyAsync(() -> ChapterPlanner.plan(probe, at, reqs, obstacles, angle, salt),
                Util.backgroundExecutor());
        IN_FLIGHT.add(key);
        TASKS.add(new Task(key, chapter.id(), at, ahead, new java.util.LinkedHashSet<>(players), ids, f));
        SkyAbyss.LOGGER.info("Chapter sites: planning {} for {} near {} ({} sites{})", chapter.id(), players, base, reqs.size(), ahead ? ", ahead" : "");
    }

    // 同じ章で、base から SHARE_DIST 以内の (進行方向の先に置いたのではない) 拠点のうち、いちばん近いもの
    @Nullable
    static SiteData.ChapterPlan nearestPlan(SiteData data, String chapter, BlockPos base) {
        SiteData.ChapterPlan best = null;
        double bd = SHARE_DIST;
        for (SiteData.ChapterPlan p : data.plans()) {
            if (p.ahead || !p.chapter.equals(chapter)) {
                continue;
            }
            double d = horizontal(p.anchor, base);
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // pos が、その章の拠点と置いた場所のすべてから AWAY_DIST 以上離れているか (離れた人に進行方向の先へ足すか)
    static boolean farFromAll(Collection<BlockPos> points, BlockPos pos) {
        if (points.isEmpty()) {
            return false;
        }
        for (BlockPos p : points) {
            if (horizontal(p, pos) < AWAY_DIST) {
                return false;
            }
        }
        return true;
    }

    static List<BlockPos> pointsOf(SiteData data, String chapter) {
        List<BlockPos> pts = new ArrayList<>();
        for (SiteData.ChapterPlan p : data.plans()) {
            if (p.chapter.equals(chapter)) {
                pts.add(p.anchor);
                for (SiteData.Entry e : p.sites) {
                    pts.add(e.pos);
                }
            }
        }
        return pts;
    }

    // ---------------------------------------------------------------- 結果の反映 (tick の中)
    private static void applyTasks(MinecraftServer server) {
        if (TASKS.isEmpty()) {
            return;
        }
        SiteData data = SiteData.get(server.overworld());
        boolean changed = false;
        for (java.util.Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
            Task t = it.next();
            if (!t.future().isDone()) {
                continue;
            }
            it.remove();
            IN_FLIGHT.remove(t.key());
            for (UUID u : t.players()) {
                IN_FLIGHT.remove("n:" + u + ":" + t.chapter());
            }
            List<ChapterPlanner.Placed> placed;
            try {
                placed = t.future().join();
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("Chapter sites: the search failed for {}", t.key(), e);
                placed = List.of();
            }
            SiteData.ChapterPlan plan = data.plan(t.chapter(), t.anchor());
            if (plan == null) {
                plan = data.addPlan(t.chapter(), t.anchor(), t.ahead());
            }
            plan.players.addAll(t.players());
            plan.attempted.addAll(t.ids());
            for (ChapterPlanner.Placed p : placed) {
                SiteData.Entry e = new SiteData.Entry(KIND_PREFIX + p.id(), p.index(), p.pos(), p.yaw());
                e.siteId = p.id();
                Element el = ELEMENTS.get(p.id());
                e.blueprint = el == null ? "" : el.blueprint();
                e.anchor = plan.anchor;
                e.maxDist = ChapterPlanner.MAX_DIST;
                e.radius = p.radius();
                plan.sites.add(e);
            }
            data.setDirty();
            changed = true;
            SkyAbyss.LOGGER.info("Chapter sites: {} placed {} sites ({} elements) for {} at {}", t.chapter(), placed.size(), t.ids().size(), t.players(), t.anchor());
        }
        if (changed) {
            SiteBuilds.enqueueMissing(server);
        }
    }

    // SiteBuilds の予約から呼ばれる。要素が登録されていなければ null (建てない)
    @Nullable
    static SiteJob jobFor(ServerLevel level, SiteData data, SiteData.Entry e) {
        Element el = ELEMENTS.get(e.siteId);
        if (el == null) {
            return null;
        }
        Blueprint bp;
        try {
            bp = Blueprint.get(e.blueprint.isEmpty() ? el.blueprint() : e.blueprint);
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("Chapter site {}: unusable blueprint, dropped", e.siteId, ex);
            data.remove(e);
            return null;
        }
        SiteJob.Post post = el.post() == null ? job -> {
        } : el.post();
        return new SiteJob(level, data, e, bp, e.siteId + "#" + e.index, post);
    }

    // ---------------------------------------------------------------- 見張り
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!SiteBuilds.enabled(server)) {
            return;
        }
        applyTasks(server);
        int tick = server.getTickCount();
        if (tick % ARRIVE_EVERY == 0 && server.getPlayerCount() > 0) {
            poll(server, tick % OPEN_EVERY == 0);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
        IN_FLIGHT.clear();
        LAST.clear();
    }

    // 章が開いたことを見つける唯一の場所 (pollOpen)。着いたことの検出 (pollArrive) は 1 秒おき
    static void poll(MinecraftServer server, boolean open) {
        SiteData data = SiteData.get(server.overworld());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != Level.OVERWORLD || !p.isAlive()) {
                continue;
            }
            Chapters c = Chapters.of(p);
            if (open) {
                pollOpen(server, data, p, c);
            }
            pollArrive(data, p, c);
        }
    }

    private static void pollOpen(MinecraftServer server, SiteData data, ServerPlayer p, Chapters c) {
        BlockPos pos = p.blockPosition();
        BlockPos last = LAST.put(p.getUUID(), pos);
        if (elements(c).isEmpty()) {
            return;
        }
        boolean served = false;
        for (SiteData.ChapterPlan plan : data.plans()) {
            if (!plan.ahead && plan.chapter.equals(c.id()) && plan.players.contains(p.getUUID())) {
                served = true;
                topUp(server, data, plan, c);
            }
        }
        if (!served) {
            open(p, c);
            return;
        }
        if (farFromAll(pointsOf(data, c.id()), pos)) {
            double angle = last != null && horizontal(last, pos) > 6 ? Math.atan2(pos.getZ() - last.getZ(), pos.getX() - last.getX())
                    : ChapterPlanner.baseAngle(pos, 7);
            requestAhead(server, p.getUUID(), c, pos, angle);
        }
    }

    private static void pollArrive(SiteData data, ServerPlayer p, Chapters c) {
        arrive(data, p.getUUID(), p.blockPosition(), c, (el, site) -> el.onArrive().arrive(p, site));
    }

    // c の章の人が pos にいるとき、近くの built の場所で、まだ着いていない人なら一度だけ fire を呼ぶ
    static void arrive(SiteData data, UUID uuid, BlockPos pos, Chapters c, java.util.function.BiConsumer<Element, Site> fire) {
        for (SiteData.ChapterPlan plan : data.plans()) {
            if (!plan.chapter.equals(c.id())) {
                continue;
            }
            for (SiteData.Entry e : plan.sites) {
                if (!e.built || e.arrived.contains(uuid)) {
                    continue;
                }
                Element el = ELEMENTS.get(e.siteId);
                if (el == null || el.onArrive() == null) {
                    continue;
                }
                if (horizontal(e.pos, pos) <= el.rules().arriveRadius() && Math.abs(e.pos.getY() - pos.getY()) <= 30) {
                    e.arrived.add(uuid);
                    data.setDirty();
                    try {
                        fire.accept(el, site(plan, e));
                    } catch (RuntimeException ex) {
                        SkyAbyss.LOGGER.error("Chapter site {}: the arrive callback failed", e.siteId, ex);
                    }
                }
            }
        }
    }

    static boolean pending() {
        return !TASKS.isEmpty();
    }
}

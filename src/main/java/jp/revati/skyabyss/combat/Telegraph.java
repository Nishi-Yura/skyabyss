package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.titan.group.GroupOwned;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// ボス・中ボスの技の予告 (テレグラフ)。「当たる範囲」を床に粒で描き、時間が来たらその範囲の中にいる人だけに当てる。
//
//   Telegraph.in(level, TelegraphShape.circle(x, z, 4))      // 形: 円・輪・帯・扇・振り回す帯
//           .floor(y)                                        // 床の高さ (描く高さと、当たる高さの基準)
//           .warn(28)                                        // 予告の長さ tick。MIN_WARN_TICKS (16 = 0.8 秒) より短くはならない
//           .color(0xFF5A5A)                                 // 印の色 (近づくほど白くなる)
//           .owner(this)                                     // 持ち主。倒れる・消えると予告は黙って取り消される (傷は出ない)
//           .startSound(sound, 2f, 1f)                       // 予告の始まりの音 (省略可)
//           .onImpact(lvl -> { ... })                        // 当たる瞬間の見た目・音 (省略可)
//           .onHit((lvl, player) -> player.hurt(...))        // 範囲の中の人それぞれに一度ずつ (傷・ノックバック)
//           .start();
//
// 範囲の判定と印の描画は、同じ TelegraphShape から出る。印はクライアントの床の面 (decal): 開始パケット (TelegraphStartPayload) を
// 見える人へ送り、client/combat/TelegraphRenderer が満ちる面と縁を床の高さに沿って描く (形の刻みは TelegraphGeometry)。
// 旧来の点線の粒 (outline/edge) は、クライアントの telegraphStyle か、サーバー設定 telegraphServerParticles。
// 印が満ちる縁は範囲の内側にしか出ない。
// 振り回す帯 (sweep) は active(ticks) の間、いまの帯 (shape.at(進み具合)) の中の人に一度ずつ当てる。
// jumpClear(h): 床から h 以上跳んでいる人は当たらない (跳び越える技)。粒は 1 tick ごとの総数に上限がある (BUDGET)。
// 設計書: docs/design/02_stone_whale.md の 5.4 (予告の部品)。
public final class Telegraph {
    // 傷が出るまでの最短の予告 (0.8 秒)
    public static final int MIN_WARN_TICKS = 16;
    // 1 tick に送る予告の粒の数 (粒 × 見る人) の上限
    static final int BUDGET = 700;

    // 印の意味 (色に頼らない合図の違いもある: 危険 = 縁は実線・満ちると橙から赤から白、安全 = 青白の破線、無害 = 灰色の点)。
    // CUSTOM は color() で選んだ色 (旧来の見え方)。新しい技は danger() / safe() / harmless() を使う
    public enum Style {
        CUSTOM(0xFF5A5A),
        DANGER(0xFF8A2A),
        SAFE(0x5CE8FF),
        HARMLESS(0xB0B4BC);

        public final int rgb;

        Style(int rgb) {
            this.rgb = rgb;
        }

        public static Style byId(int id) {
            Style[] v = values();
            return id >= 0 && id < v.length ? v[id] : CUSTOM;
        }
    }

    @FunctionalInterface
    public interface Hit {
        void hit(ServerLevel level, ServerPlayer victim);
    }

    private static final Map<ServerLevel, List<Telegraph>> ACTIVE = new HashMap<>();
    private static int budget = BUDGET;
    private static int created;
    // 試験用: 傷のある予告を作った数と、印の判定を調べる点 (人の代わり。傷のある予告が一度でも当てた点が probeHit)
    private static int damagingCreated;
    private static List<Vec3> probes;
    private static boolean[] probeHit;
    // 試験用: 傷のある予告を作るたびに、その形を記録する (印と当たりが同じ形かを、実際の技で確かめるため)
    private static List<TelegraphShape> recorded;

    private final ServerLevel level;
    private final TelegraphShape shape;
    private final double floor;
    private final double reach;
    private final double jumpClear;
    private final int warn;
    private final int active;
    private final int repeat;
    private final int rgb;
    private final Style style;
    @Nullable
    private final UUID target;
    @Nullable
    private final Entity owner;
    private final Predicate<ServerPlayer> victims;
    @Nullable
    private final Consumer<ServerLevel> onImpact;
    @Nullable
    private final Hit onHit;
    private final Set<UUID> hit = new HashSet<>();
    // 床の印 (decal) のパケットを送った人。あとから近づいた人にも送る
    private final Set<UUID> notified = new HashSet<>();
    private final boolean decal;
    private static int decalsSent;
    private final int id = created++;
    private int age;
    private boolean done;

    private Telegraph(Builder b) {
        this.level = b.level;
        this.shape = b.shape;
        this.floor = b.floor;
        this.reach = b.reach;
        this.jumpClear = b.jumpClear;
        // 誓い (titan.vow) の「予告が短い」: 持ち主のグループの誓いで縮める。最短 MIN_WARN_TICKS は割れない
        this.warn = Math.max(MIN_WARN_TICKS, jp.revati.skyabyss.titan.vow.Vows.telegraphWarn(b.owner, b.warn));
        this.active = Math.max(0, b.active);
        this.repeat = Math.max(0, b.repeat);
        this.rgb = b.rgb;
        this.style = b.style;
        this.target = b.target;
        this.owner = b.owner;
        this.victims = b.victims;
        this.onImpact = b.onImpact;
        this.onHit = b.onHit;
        this.decal = b.shape.kind() != TelegraphShape.KIND_NONE;
        if (b.onHit != null) {
            damagingCreated++;
            if (recorded != null) {
                recorded.add(b.shape);
            }
        }
    }

    // ---- 試験用 ----

    public static void resetStats() {
        damagingCreated = 0;
    }

    // 試験用: 床の印のパケットを送った数 (人 × 予告)
    public static int decalsSent() {
        return decalsSent;
    }

    public static int damagingCreated() {
        return damagingCreated;
    }

    // 床の高さの点を並べて、傷のある予告が (どの瞬間でも) 当たる点を記録する。null で止める
    public static void probe(List<Vec3> points) {
        probes = points;
        probeHit = points == null ? null : new boolean[points.size()];
    }

    public static boolean[] probeHits() {
        return probeHit;
    }

    // 傷のある予告の形を sink に記録する。null で止める
    public static void record(List<TelegraphShape> sink) {
        recorded = sink;
    }

    // ---- 作る ----

    public static Builder in(ServerLevel level, TelegraphShape shape) {
        return new Builder(level, shape);
    }

    public static final class Builder {
        private final ServerLevel level;
        private final TelegraphShape shape;
        private double floor;
        private double reach = 3.0;
        private double jumpClear = 0;
        private int warn = 24;
        private int active = 0;
        private int repeat = 0;
        private int rgb = 0xFF5A5A;
        private Style style = Style.CUSTOM;
        @Nullable
        private UUID target;
        @Nullable
        private Entity owner;
        private Predicate<ServerPlayer> victims = Telegraph::defaultVictim;
        @Nullable
        private Consumer<ServerLevel> onImpact;
        @Nullable
        private Hit onHit;
        @Nullable
        private SoundEvent sound;
        private float soundVolume = 2f;
        private float soundPitch = 1f;

        private Builder(ServerLevel level, TelegraphShape shape) {
            this.level = level;
            this.shape = shape;
        }

        public Builder floor(double y) {
            this.floor = y;
            return this;
        }

        // 床からこの高さまでの人に当たる (既定 3)
        public Builder reach(double height) {
            this.reach = height;
            return this;
        }

        // 床から clearance 以上跳んでいる人は当たらない (0 なら跳び越えられない)
        public Builder jumpClear(double clearance) {
            this.jumpClear = clearance;
            return this;
        }

        public Builder warn(int ticks) {
            this.warn = ticks;
            return this;
        }

        // 当たりが続く tick 数 (振り回す技)。0 なら予告のあと一度だけ
        public Builder active(int ticks) {
            this.active = ticks;
            return this;
        }

        // active の間、同じ人に ticks ごとにもう一度当てる (床の帯電・爪の連打。印は消えずに残る)。0 なら一度だけ
        public Builder repeatEvery(int ticks) {
            this.repeat = ticks;
            return this;
        }

        // 特別な色 (旧来。意味の印にならない)
        public Builder color(int rgb) {
            this.rgb = rgb;
            this.style = Style.CUSTOM;
            return this;
        }

        // 当たると傷が出る範囲: 橙から赤へ満ち、当たる瞬間に白く光る。縁は実線
        public Builder danger() {
            this.style = Style.DANGER;
            this.rgb = Style.DANGER.rgb;
            return this;
        }

        // 安全な場所 (輪の抜ける筋など): 青白で、縁は破線
        public Builder safe() {
            this.style = Style.SAFE;
            this.rgb = Style.SAFE.rgb;
            return this;
        }

        // 傷はなく押すだけ・見た目だけの範囲: 灰色で、縁は点
        public Builder harmless() {
            this.style = Style.HARMLESS;
            this.rgb = Style.HARMLESS.rgb;
            return this;
        }

        // この技が狙っている人。狙われた本人の画面にだけ、帯の根もとに輪と矢印が出る
        public Builder target(@Nullable UUID player) {
            this.target = player;
            return this;
        }

        public Builder target(@Nullable Entity entity) {
            this.target = entity instanceof ServerPlayer ? entity.getUUID() : null;
            return this;
        }

        public Builder owner(@Nullable Entity owner) {
            this.owner = owner;
            return this;
        }

        // 当たる人の条件 (既定: 生きていて、観戦でない人)。ボスは自分の canReach を渡す
        public Builder victims(Predicate<ServerPlayer> victims) {
            this.victims = victims;
            return this;
        }

        public Builder startSound(SoundEvent sound, float volume, float pitch) {
            this.sound = sound;
            this.soundVolume = volume;
            this.soundPitch = pitch;
            return this;
        }

        public Builder onImpact(Consumer<ServerLevel> onImpact) {
            this.onImpact = onImpact;
            return this;
        }

        public Builder onHit(Hit onHit) {
            this.onHit = onHit;
            return this;
        }

        public Telegraph start() {
            Telegraph t = new Telegraph(this);
            ACTIVE.computeIfAbsent(level, l -> new ArrayList<>()).add(t);
            if (sound != null) {
                level.playSound(null, shape.centerX(), floor + 1, shape.centerZ(), sound, SoundSource.HOSTILE, soundVolume, soundPitch);
            }
            return t;
        }
    }

    private static boolean defaultVictim(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator();
    }

    // ---- 範囲の判定 (印と同じ形) ----

    // この人は、いまの形の中にいるか (高さも見る)
    public boolean inside(TelegraphShape current, Entity e) {
        return inside(current, floor, reach, jumpClear, e);
    }

    public static boolean inside(TelegraphShape shape, double floor, double reach, double jumpClear, Entity e) {
        double y = e.getY();
        if (y < floor - 1.5 || y > floor + reach) {
            return false;
        }
        if (jumpClear > 0 && y >= floor + jumpClear) {
            return false;
        }
        return shape.contains(e.getX(), e.getZ());
    }

    // 試験用 (読む人形): 点 (x, y, z) に、ticksAhead tick 後に傷が出る予告 (傷のある・まだ終わっていないもの) があるか。
    // 当たる瞬間 (予告が満ちる tick) と、振り回す・続く予告の active の間を見る。印と当たりは同じ形・同じ高さ判定
    public static boolean dangerAt(ServerLevel level, double x, double y, double z, int ticksAhead) {
        for (Telegraph t : ACTIVE.getOrDefault(level, List.of())) {
            if (t.done || t.onHit == null) {
                continue;
            }
            int a = t.age + ticksAhead;
            if (a < t.warn) {
                continue;
            }
            TelegraphShape now;
            if (t.active == 0) {
                if (a > t.warn) {
                    continue;
                }
                now = t.shape.at(1.0);
            } else {
                if (a > t.warn + t.active) {
                    continue;
                }
                now = t.shape.at(Math.min(1.0, (double) (a - t.warn) / t.active));
            }
            if (y < t.floor - 1.5 || y > t.floor + t.reach) {
                continue;
            }
            if (t.jumpClear > 0 && y >= t.floor + t.jumpClear) {
                continue;
            }
            if (now.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    // 向き (x, z のベクトル) の角度。Telegraph の角度の約束 (cos a, sin a) に合わせる
    public static double angleOf(double dx, double dz) {
        return Math.atan2(dz, dx);
    }

    public static double angleOf(Vec3 v) {
        return Math.atan2(v.z, v.x);
    }

    // ---- 状態 (自己テスト・呼び出し側用) ----

    public int age() {
        return age;
    }

    public int warnTicks() {
        return warn;
    }

    public boolean isDone() {
        return done;
    }

    public TelegraphShape shape() {
        return shape;
    }

    public void cancel() {
        kill();
    }

    // 取り消し: 床の印も消す (自然に当たり終えたときは送らない。クライアントが自分の時間で消す)
    private void kill() {
        if (!done) {
            done = true;
            if (!notified.isEmpty()) {
                TelegraphCancelPayload payload = new TelegraphCancelPayload(id);
                for (ServerPlayer p : Players.snapshot(level.players())) {
                    if (notified.contains(p.getUUID())) {
                        PacketDistributor.sendToPlayer(p, payload);
                    }
                }
            }
        }
    }

    public static int activeCount(ServerLevel level) {
        List<Telegraph> list = ACTIVE.get(level);
        return list == null ? 0 : list.size();
    }

    // 持ち主の予告をすべて取り消す (倒れたとき、段階が変わるとき)
    public static void cancelAll(Entity owner) {
        for (List<Telegraph> list : ACTIVE.values()) {
            for (Telegraph t : list) {
                if (t.owner == owner) {
                    t.kill();
                }
            }
        }
    }

    // 試験用: 1 tick 分進める (サーバーの tick に任せず、自己テストが手で進めるとき)
    public static void tickNow(ServerLevel level) {
        tickLevel(level);
    }

    // 試験用: 動いている予告の予告 tick 数の一覧
    public static List<Integer> activeWarns(ServerLevel level) {
        List<Integer> out = new ArrayList<>();
        for (Telegraph t : ACTIVE.getOrDefault(level, List.of())) {
            if (!t.done) {
                out.add(t.warn);
            }
        }
        return out;
    }

    static void clearAll() {
        ACTIVE.clear();
    }

    // ---- 毎 tick (TelegraphEvents から) ----

    static void tickLevel(ServerLevel level) {
        List<Telegraph> list = ACTIVE.get(level);
        if (list == null) {
            return;
        }
        budget = BUDGET;
        for (int i = 0; i < list.size(); i++) {
            Telegraph t = list.get(i);
            if (!t.done) {
                t.tick();
            }
        }
        list.removeIf(t -> t.done);
        if (list.isEmpty()) {
            ACTIVE.remove(level);
        }
    }

    private void tick() {
        if (owner != null && (owner.isRemoved() || (owner instanceof LivingEntity le && le.isDeadOrDying()))) {
            kill();
            return;
        }
        age++;
        if (decal && (age == 1 || age % 4 == 2) && age <= warn + active) {
            syncViewers();
        }
        if (age < warn) {
            drawWarning();
            return;
        }
        if (age == warn) {
            if (onImpact != null) {
                onImpact.accept(level);
            }
            if (active == 0) {
                strike(shape.at(1.0));
                done = true;
            } else {
                strike(shape.at(0.0));
            }
            return;
        }
        // 振り回し: 進み具合に合わせた帯の中の人に一度ずつ
        double progress = Math.min(1.0, (double) (age - warn) / active);
        TelegraphShape now = shape.at(progress);
        if (repeat > 0 && (age - warn) % repeat == 0) {
            hit.clear();
        }
        strike(now);
        if (age % 2 == 0 && serverParticles()) {
            draw(now::outline, 1.6, 1.0, 0.0, 1.5f);
        }
        if (age - warn >= active) {
            done = true;
        }
    }

    private void strike(TelegraphShape current) {
        if (onHit == null) {
            return;
        }
        if (probes != null) {
            for (int i = 0; i < probes.size(); i++) {
                Vec3 p = probes.get(i);
                if (!probeHit[i] && p.y >= floor - 0.5 && p.y <= floor + 0.5 && current.contains(p.x, p.z)) {
                    probeHit[i] = true;
                }
            }
        }
        double[] b = current.bounds();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(b[0] - 1, floor - 1.5, b[1] - 1, b[2] + 1, floor + reach + 1, b[3] + 1);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, victims)) {
            if (inside(current, p) && hit.add(p.getUUID())) {
                onHit.hit(level, p);
            }
        }
    }

    // ---- 印を描く ----

    // 床の印を、まだ送っていない見える人へ送る (始まりと、そのあと 4 tick ごと)
    private void syncViewers() {
        double cx = shape.centerX();
        double cz = shape.centerZ();
        double[] b = shape.bounds();
        double reachSq = Math.pow(96 + Math.hypot(b[2] - b[0], b[3] - b[1]) / 2, 2);
        TelegraphStartPayload payload = null;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p instanceof FakePlayer || notified.contains(p.getUUID())) {
                continue;
            }
            if (p.distanceToSqr(cx, floor, cz) < reachSq && GroupOwned.visibleTo(owner != null ? owner : p, p)) {
                if (payload == null) {
                    payload = TelegraphStartPayload.of(id, shape, floor, age, warn, active, rgb, style.ordinal(), target);
                }
                PacketDistributor.sendToPlayer(p, payload);
                notified.add(p.getUUID());
                decalsSent++;
            }
        }
    }

    // 旧来の点線の粒をサーバーからも送るか (床の印が出せない形は、いつも送る)
    private boolean serverParticles() {
        if (!decal) {
            return true;
        }
        try {
            return Config.TELEGRAPH_SERVER_PARTICLES.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void drawWarning() {
        if (!serverParticles()) {
            return;
        }
        double progress = (double) age / warn;
        // 輪郭: 序盤は 4 tick ごと、近づくと 2 tick ごと (id をずらして、同時に何本あっても粒が一度に集中しない)
        double[] b = shape.bounds();
        // 小さい形 (棘の円など) は粒が少ないぶん、間隔をあけて数を抑える
        boolean small = Math.max(b[2] - b[0], b[3] - b[1]) < 4.5;
        int period = (progress < 0.6 ? 4 : 2) * (small ? 2 : 1);
        if ((age + id) % period == 0) {
            draw(shape::outline, 0.9, progress, 0.0, 1.0f);
        }
        // 満ちていく縁: 2 tick ごと (小さい形は 4 tick ごと)
        if ((age + id) % (small ? 4 : 2) == 1) {
            draw((sink, spacing) -> shape.edge(progress, sink, spacing), 1.0, progress, 0.12, 1.2f);
        }
    }

    private interface Painter {
        void paint(TelegraphShape.PointSink sink, double spacing);
    }

    private void draw(Painter painter, double spacing, double progress, double lift, float sizeBoost) {
        List<ServerPlayer> viewers = new ArrayList<>();
        double cx = shape.centerX();
        double cz = shape.centerZ();
        double[] b = shape.bounds();
        double reachSq = Math.pow(64 + Math.hypot(b[2] - b[0], b[3] - b[1]) / 2, 2);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.distanceToSqr(cx, floor, cz) < reachSq && GroupOwned.visibleTo(owner != null ? owner : p, p)) {
                viewers.add(p);
            }
        }
        if (viewers.isEmpty()) {
            return;
        }
        double p2 = Math.max(0, Math.min(1, progress));
        int r = lerp((rgb >> 16) & 0xFF, 255, p2 * 0.55);
        int g = lerp((rgb >> 8) & 0xFF, 255, p2 * 0.55);
        int bl = lerp(rgb & 0xFF, 255, p2 * 0.55);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r / 255f, g / 255f, bl / 255f), (float) (1.0 + 0.5 * p2) * sizeBoost);
        // 粒が多すぎる形は、間隔を広げる
        double step = spacing;
        int[] counter = {0};
        painter.paint((x, z) -> counter[0]++, step);
        while (counter[0] > 64 && step < 4) {
            step *= 1.5;
            counter[0] = 0;
            final double s = step;
            painter.paint((x, z) -> counter[0]++, s);
        }
        if (counter[0] * viewers.size() > budget) {
            return;
        }
        budget -= counter[0] * viewers.size();
        double y = floor + 0.1 + lift;
        painter.paint((x, z) -> {
            for (ServerPlayer p : viewers) {
                level.sendParticles(p, dust, true, x, y, z, 1, 0, 0, 0, 0);
            }
        }, step);
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}

package jp.revati.skyabyss.titan.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

// 心臓の「領域」(docs/design/02 の 5.8 / 08 / 12 / 13 の「領域」の節)。体力が半分を切った心臓が、一度だけ部屋の姿を変える。
//  1. 宣言 (DECLARE_TICKS = 4 秒): 心臓は傷を受けない。画面は暗く、題字 (領域の名前) と天の声の一行、音の合図。技は止まる (出ている予告は消えない)
//  2. 領域: 部屋の見た目 (霧の色・床の紋・粒) が変わり、領域だけの決まり (rule) が回る。決まりの傷はすべて Telegraph (床に見える予告)。
//     心臓の通常の技は止まる。同時に出る数は人数 × 1〜2 本まで。心臓は傷を受けない (叩いても削れない)。
//     領域は「仕掛け」を解くまで続く (時間切れはない)。部屋に仕掛けが三つ立つ (NODES)。
//        岩鯨 (石の海):   共鳴する骨が三本。鳴る順を見て、同じ順に叩き返す
//        潮殻 (満ち潮):   潮の水門が三つ。三つを同時に開けておく (開けた水門は 45 秒保つ)
//        雷翼 (雷の檻):   避雷の柱が三本。順に溜まる雷を、溜まりきる前に叩いて地へ逃がす
//        余燼 (炉心):     熾の核が三つ。そばでしゃがみ続けて冷ます
//     仕掛けは、叩く (左クリック・右クリック) か、そばでしゃがむ。一人でも解ける速さ・猶予で、人数が多ければ分担で速くなる。
//     手がかり (光る柱・音・一行) は出し続け、間違えても傷は受けない (決まりの傷は領域の決まりの予告だけ)
//  3. 解いた: 予告は全部消え、領域は砕け、心臓は STUN_TICKS (5 秒) のあいだ傷を 1.5 倍受ける。
//     人がいなくなれば心臓ごと最初に戻る。安全網として SAFETY_TICKS (6 分) で静かに終わる。
// 領域は部屋ごとの subclass (StoneSeaDomain / RisingTideDomain / ThunderCageDomain / HearthCoreDomain) が rule() と仕掛けを書く。
// 画面の側は client/titan/domain/DomainClient (題字・色・床の紋・仕掛けの進み)。
public abstract class HeartDomain {
    public static final int DECLARE_TICKS = 80;
    // 安全網: 領域がこれだけ続いても解けないときは静かに終わる (部屋に誰かが居残っている詰みの保険。普通は届かない)
    public static final int SAFETY_TICKS = 20 * 60 * 6;
    public static final int STUN_TICKS = 100;
    public static final float STUN_TAKEN = 1.5f;
    public static final int NODES = 3;
    public static final String NODE_TAG = "skyabyss_domain_node";

    public enum Phase {
        IDLE, DECLARE, ACTIVE, DONE
    }

    // 仕掛けの一つ (骨・水門・柱・核)。位置・当たりの箱 (Interaction の生き物)・状態
    public static final class Node {
        public final int index;
        public final Vec3 pos;
        @Nullable
        UUID entity;
        // 済んだ (骨: 今の順で叩けた / 水門: 開いている / 柱: 地へ逃がした / 核: 冷めた)
        public boolean done;
        // 仕掛けごとの数字 (水門: 開いている残り tick / 核: しゃがんだ分)
        public int state;

        Node(int index, Vec3 pos) {
            this.index = index;
            this.pos = pos;
        }

        // 当たりの箱の生き物 (試験用)
        @Nullable
        public UUID entityId() {
            return entity;
        }
    }

    protected final Mob heart;
    protected final String id;
    protected final double cx;
    protected final double cz;
    protected final double floorY;
    protected final double radius;
    protected final Predicate<ServerPlayer> victims;
    protected final BiConsumer<ServerPlayer, Float> striker;
    protected final Supplier<Collection<ServerPlayer>> players;
    private final SoundEvent sting;
    protected final List<Node> nodes = new ArrayList<>();

    private Phase phase = Phase.IDLE;
    private int ticks;
    private int stun;
    private boolean solved;
    private int lastSolved = -1;
    private ServerLevel levelRef;

    protected HeartDomain(Mob heart, String id, double cx, double cz, double floorY, double radius, Predicate<ServerPlayer> victims,
            BiConsumer<ServerPlayer, Float> striker, Supplier<Collection<ServerPlayer>> players, SoundEvent sting) {
        this.heart = heart;
        this.id = id;
        this.cx = cx;
        this.cz = cz;
        this.floorY = floorY;
        this.radius = radius;
        this.victims = victims;
        this.striker = striker;
        this.players = players;
        this.sting = sting;
    }

    public String id() {
        return id;
    }

    public Phase phase() {
        return phase;
    }

    public boolean running() {
        return phase == Phase.DECLARE || phase == Phase.ACTIVE;
    }

    public boolean finished() {
        return phase == Phase.DONE;
    }

    // 仕掛けを解いて領域が砕けたか (時間切れ・心臓が倒れたときは false)
    public boolean broken() {
        return solved;
    }

    public boolean solved() {
        return solved;
    }

    public int ticks() {
        return ticks;
    }

    // 宣言のあいだも領域のあいだも、心臓は傷を受けない (領域は仕掛けで解く。叩いても削れない)
    public boolean invulnerable() {
        return phase == Phase.DECLARE || phase == Phase.ACTIVE;
    }

    // 領域を解いた直後の傷の倍率
    public float takenMultiplier() {
        return stun > 0 ? STUN_TAKEN : 1f;
    }

    public int stunTicks() {
        return stun;
    }

    // 解いた仕掛けの数 (0〜NODES)
    public abstract int solvedCount();

    public int percent() {
        return Math.min(100, Math.round(100f * solvedCount() / NODES));
    }

    public List<Node> nodes() {
        return nodes;
    }

    // 領域のあいだ心臓は傷を受けないので、削る傷は数えない (hurt 側の呼びは残してある)
    public void damaged(ServerLevel level, float lost) {
    }

    // ---- 流れ ----

    public void begin(ServerLevel level) {
        if (phase != Phase.IDLE) {
            return;
        }
        levelRef = level;
        phase = Phase.DECLARE;
        ticks = 0;
        solved = false;
        stun = 0;
        lastSolved = -1;
        send(level, DomainPayload.start(id, DECLARE_TICKS, 0, cx, cz, floorY, radius));
        level.playSound(null, cx, floorY + 4, cz, sting, SoundSource.MASTER, 4f, 1f);
        onDeclare(level);
    }

    // 毎 tick。領域が心臓の技を止めているあいだ true
    public boolean tick(ServerLevel level) {
        levelRef = level;
        if (stun > 0) {
            stun--;
        }
        if (phase == Phase.DECLARE) {
            ticks++;
            declareEffects(level, ticks);
            if (ticks >= DECLARE_TICKS) {
                phase = Phase.ACTIVE;
                ticks = 0;
                spawnNodes(level);
                puzzleBegin(level);
                tell(level, "message.skyabyss.domain." + id + ".goal");
                tell(level, "message.skyabyss.domain.invulnerable");
                send(level, DomainPayload.progress(id, percent(), solvedCount()));
            }
            return true;
        }
        if (phase == Phase.ACTIVE) {
            rule(level, ticks);
            if (ticks % 20 == 0) {
                keepNodes(level);
            }
            puzzleTick(level, ticks);
            if (ticks % 6 == 0) {
                ambient(level, ticks);
                markNodes(level, ticks);
            }
            ticks++;
            int solvedNow = solvedCount();
            if (solvedNow != lastSolved) {
                lastSolved = solvedNow;
                send(level, DomainPayload.progress(id, percent(), solvedNow));
            }
            if (complete()) {
                end(level, true);
            } else if (ticks >= SAFETY_TICKS) {
                end(level, false);
            }
            return true;
        }
        return false;
    }

    // 領域を終える (解いた / 安全網)。出ている予告は全部消し、解いたなら心臓は 5 秒傷を 1.5 倍受ける
    public void end(ServerLevel level, boolean shattered) {
        if (!running()) {
            return;
        }
        phase = Phase.DONE;
        solved = shattered;
        Telegraph.cancelAll(heart);
        removeNodes(level);
        if (shattered) {
            stun = STUN_TICKS;
        }
        send(level, DomainPayload.end(id, shattered));
        onEnd(level, shattered);
    }

    // 心臓が倒れた・消えた: 画面を戻す (演出なし)
    public void abort(ServerLevel level) {
        if (running()) {
            phase = Phase.DONE;
            Telegraph.cancelAll(heart);
            removeNodes(level);
            send(level, DomainPayload.end(id, false));
        }
    }

    // ---- 部屋ごと ----

    // 宣言の始まり (音・粒)
    protected void onDeclare(ServerLevel level) {
    }

    // 宣言のあいだの毎 tick の演出
    protected void declareEffects(ServerLevel level, int t) {
    }

    // 領域の決まり。t は領域の始めからの tick。傷は Telegraph で出す
    protected abstract void rule(ServerLevel level, int t);

    // 6 tick ごとの、部屋に漂う粒
    protected void ambient(ServerLevel level, int t) {
    }

    // 領域が終わった (解いたなら shattered)
    protected void onEnd(ServerLevel level, boolean shattered) {
    }

    // ---- 仕掛け ----

    // 仕掛けの置き場 (NODES 個)。既定は床の上、部屋の半径の 6 割の輪に 120 度おき。部屋の作りに合わせて上書きする
    protected List<Vec3> nodeSpots() {
        List<Vec3> out = new ArrayList<>();
        double base = heart.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < NODES; i++) {
            double a = base + Math.PI * 2 * i / NODES;
            out.add(new Vec3(cx + Math.cos(a) * radius * 0.62, floorY, cz + Math.sin(a) * radius * 0.62));
        }
        return out;
    }

    // 仕掛けの始まり (宣言が明けた瞬間。仕掛けの置き場はもう決まっている)
    protected void puzzleBegin(ServerLevel level) {
    }

    // 領域のあいだ毎 tick
    protected void puzzleTick(ServerLevel level, int t) {
    }

    // 仕掛けを叩いた (左クリック・右クリック) / そばでしゃがむ仕掛けは puzzleTick が数える。扱ったら true
    protected boolean strike(ServerLevel level, Node node, ServerPlayer player) {
        return false;
    }

    // 領域が解けたか
    protected abstract boolean complete();

    // 仕掛けの一行の手がかりの鍵 (gui の hud の二行目)
    public String goalKey() {
        return "gui.skyabyss.domain." + id + ".goal";
    }

    // 試験・event から: 仕掛け index を player が叩いた
    public boolean strikeNode(ServerLevel level, int index, ServerPlayer player) {
        if (phase != Phase.ACTIVE || index < 0 || index >= nodes.size()) {
            return false;
        }
        return strike(level, nodes.get(index), player);
    }

    private void spawnNodes(ServerLevel level) {
        removeNodes(level);
        nodes.clear();
        List<Vec3> spots = nodeSpots();
        for (int i = 0; i < NODES && i < spots.size(); i++) {
            Node n = new Node(i, snap(level, spots.get(i)));
            nodes.add(n);
            n.entity = spawnBox(level, n.pos);
        }
    }

    // 叩ける当たりの箱 (見えない Interaction)。幅 1.4・高さ 2.2。落ちたら保存されない (再読み込みでは DomainNodeEvents が片付ける)
    @Nullable
    private UUID spawnBox(ServerLevel level, Vec3 pos) {
        net.minecraft.world.entity.Interaction box = EntityType.INTERACTION.create(level);
        if (box == null) {
            return null;
        }
        CompoundTag tag = box.saveWithoutId(new CompoundTag());
        tag.putFloat("width", 1.4f);
        tag.putFloat("height", 2.2f);
        tag.putBoolean("response", true);
        box.load(tag);
        box.setPos(pos.x, pos.y, pos.z);
        box.addTag(NODE_TAG);
        level.addFreshEntity(box);
        NODE_OWNER.put(box.getUUID(), this);
        return box.getUUID();
    }

    // 箱が消えていたら置き直す (20 tick ごと)
    private void keepNodes(ServerLevel level) {
        for (Node n : nodes) {
            if (n.entity == null || level.getEntity(n.entity) == null) {
                if (n.entity != null) {
                    NODE_OWNER.remove(n.entity);
                }
                n.entity = spawnBox(level, n.pos);
            }
        }
    }

    private void removeNodes(ServerLevel level) {
        for (Node n : nodes) {
            if (n.entity != null) {
                NODE_OWNER.remove(n.entity);
                Entity e = level.getEntity(n.entity);
                if (e != null) {
                    e.discard();
                }
                n.entity = null;
            }
        }
    }

    // 位置を、箱が壁に入らない近くの床へ寄せる (水平 ±2 の中で、体の箱が空いていて足もとが立てるいちばん近い所)
    protected Vec3 snap(ServerLevel level, Vec3 want) {
        double bestD = Double.MAX_VALUE;
        Vec3 best = want;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    Vec3 p = new Vec3(want.x + dx, want.y + dy, want.z + dz);
                    AABB box = new AABB(p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 2.0, p.z + 0.5);
                    if (!level.noCollision(box)) {
                        continue;
                    }
                    BlockPos under = BlockPos.containing(p.x, p.y - 0.1, p.z);
                    if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
                        continue;
                    }
                    double d = dx * dx + dz * dz + dy * dy * 2.0;
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    // 予告の組み立て (持ち主は心臓。当たるのは部屋の人)
    protected Telegraph.Builder tg(ServerLevel level, jp.revati.skyabyss.combat.TelegraphShape shape, int warn) {
        return Telegraph.in(level, shape).floor(floorY).warn(warn).danger().owner(heart).victims(victims).reach(3.5);
    }

    // ---- 手がかりの演出 (部屋の人にだけ) ----

    protected List<ServerPlayer> inRoom() {
        return Players.snapshot(players.get());
    }

    // 仕掛けの上の粒の柱: 色 (rgb)・大きさ・本数
    protected void column(ServerLevel level, Node n, int rgb, float size, int count) {
        Vector3f c = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        DustParticleOptions dust = new DustParticleOptions(c, size);
        for (ServerPlayer p : inRoom()) {
            for (int i = 0; i < count; i++) {
                level.sendParticles(p, dust, true, n.pos.x, n.pos.y + 0.3 + i * (2.4 / Math.max(1, count)), n.pos.z, 1, 0.12, 0.05, 0.12, 0);
            }
        }
    }

    protected void burst(ServerLevel level, Node n, ParticleOptions particle, int count, double spread, double speed) {
        for (ServerPlayer p : inRoom()) {
            level.sendParticles(p, particle, true, n.pos.x, n.pos.y + 1.2, n.pos.z, count, spread, spread, spread, speed);
        }
    }

    // 輪 (半径 r の円周) の粒
    protected void ring(ServerLevel level, Vec3 center, double r, ParticleOptions particle, int count) {
        for (ServerPlayer p : inRoom()) {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                level.sendParticles(p, particle, true, center.x + Math.cos(a) * r, center.y + 0.15, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    protected void tone(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y + 1, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // 部屋の人に一行 (仕掛けの手がかり・進み)
    protected void tell(ServerLevel level, String key, Object... args) {
        for (ServerPlayer p : inRoom()) {
            if (!(p instanceof FakePlayer)) {
                Notice.bar(p, ModText.objective(key, args));
            }
        }
    }

    // 仕掛けの印 (6 tick ごと): 済みは緑、まだは白。仕掛けごとに markColor で替える
    private void markNodes(ServerLevel level, int t) {
        for (Node n : nodes) {
            column(level, n, markColor(n, t), 1.0f, 3);
        }
    }

    protected int markColor(Node n, int t) {
        return n.done ? 0x66E08A : 0xDDE6F0;
    }

    protected void send(ServerLevel level, DomainPayload payload) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p instanceof FakePlayer) {
                continue;
            }
            if (p.distanceToSqr(cx, floorY, cz) < 90 * 90 && GroupOwned.visibleTo(heart, p)) {
                PacketDistributor.sendToPlayer(p, payload);
            }
        }
        lastPayload = payload;
    }

    // 試験用: 最後に送った通信
    private static DomainPayload lastPayload;

    public static DomainPayload lastPayload() {
        return lastPayload;
    }

    protected static double angleOf(double dx, double dz) {
        return Math.atan2(dz, dx);
    }

    // ---- 叩きの受け口 (DomainNodeEvents から) ----

    // 当たりの箱の生き物 → その領域
    private static final Map<UUID, HeartDomain> NODE_OWNER = new HashMap<>();

    // target が領域の仕掛けの箱なら、player の叩きとして扱う。箱なら true (イベントは取り消す)
    static boolean onHit(ServerPlayer player, Entity target) {
        HeartDomain d = NODE_OWNER.get(target.getUUID());
        if (d == null) {
            return target.getTags().contains(NODE_TAG);
        }
        ServerLevel level = d.levelRef;
        if (level == null || d.phase != Phase.ACTIVE || player.isSpectator() || !player.isAlive()) {
            return true;
        }
        if (!d.victims.test(player)) {
            return true;
        }
        for (Node n : d.nodes) {
            if (target.getUUID().equals(n.entity)) {
                d.strike(level, n, player);
                break;
            }
        }
        return true;
    }

    static void forgetAll() {
        NODE_OWNER.clear();
    }
}

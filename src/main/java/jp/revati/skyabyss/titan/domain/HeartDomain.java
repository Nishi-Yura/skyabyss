package jp.revati.skyabyss.titan.domain;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.util.Players;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 心臓の「領域」(docs/design/02 の 5.8 / 08 / 12 / 13 の「領域」の節)。体力が半分を切った心臓が、一度だけ部屋の姿を変える。
//  1. 宣言 (DECLARE_TICKS = 4 秒): 心臓は傷を受けない。画面は暗く、題字 (領域の名前) と天の声の一行、音の合図。技は止まる (出ている予告は消えない)
//  2. 領域 (ACTIVE_TICKS = 35 秒): 部屋の見た目 (霧の色・床の紋・粒) が変わり、領域だけの決まりが回る。決まりの傷はすべて Telegraph (床に見える予告)。
//     心臓の通常の技は止まる。領域の決まりは必ず予告つきで、予告が満ちるまで 0.8 秒以上、人数が増えても同時に出る数は人数 × 1 〜 2 本まで
//  3. 破る・耐える: 領域のあいだに心臓へ与えた傷が BREAK_SHARE (最大体力の 6%) に届くと領域が砕け、予告は全部消え、心臓は STUN_TICKS (5 秒) のあいだ
//     傷を 1.5 倍受ける。届かなければ 35 秒で静かに終わる。体力の 6% は人数で変わらない (人数が多いほど硬くなる分は心臓の側の計算に入っている)
// 領域は部屋ごとの subclass (WhaleDomain 石の海 / TideDomain 満ち潮 / StormDomain 雷の檻 / EmberDomain 炉心) が rule() に決まりを書く。
// 画面の側は client/titan/domain/DomainClient (題字・色・床の紋・残りと砕く進み)。
public abstract class HeartDomain {
    public static final int DECLARE_TICKS = 80;
    public static final int ACTIVE_TICKS = 700;
    public static final int STUN_TICKS = 100;
    public static final float STUN_TAKEN = 1.5f;
    public static final float BREAK_SHARE = 0.06f;

    public enum Phase {
        IDLE, DECLARE, ACTIVE, DONE
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

    private Phase phase = Phase.IDLE;
    private int ticks;
    private float dealt;
    private int stun;
    private boolean broken;
    private int lastPercent = -1;

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

    public boolean broken() {
        return broken;
    }

    public int ticks() {
        return ticks;
    }

    // 宣言のあいだ心臓は傷を受けない
    public boolean invulnerable() {
        return phase == Phase.DECLARE;
    }

    // 領域が砕けた直後の傷の倍率
    public float takenMultiplier() {
        return stun > 0 ? STUN_TAKEN : 1f;
    }

    public int stunTicks() {
        return stun;
    }

    // 砕くのに要る傷 (心臓の体力の点。最大体力 × BREAK_SHARE)
    public float breakNeeded() {
        return heart instanceof net.minecraft.world.entity.LivingEntity le ? le.getMaxHealth() * BREAK_SHARE : 60f;
    }

    public float dealt() {
        return dealt;
    }

    // ---- 流れ ----

    public void begin(ServerLevel level) {
        if (phase != Phase.IDLE) {
            return;
        }
        phase = Phase.DECLARE;
        ticks = 0;
        dealt = 0;
        broken = false;
        stun = 0;
        lastPercent = -1;
        send(level, DomainPayload.start(id, DECLARE_TICKS, ACTIVE_TICKS, cx, cz, floorY, radius));
        level.playSound(null, cx, floorY + 4, cz, sting, SoundSource.MASTER, 4f, 1f);
        onDeclare(level);
    }

    // 毎 tick。領域が心臓の技を止めているあいだ true
    public boolean tick(ServerLevel level) {
        if (stun > 0) {
            stun--;
        }
        if (phase == Phase.DECLARE) {
            ticks++;
            declareEffects(level, ticks);
            if (ticks >= DECLARE_TICKS) {
                phase = Phase.ACTIVE;
                ticks = 0;
            }
            return true;
        }
        if (phase == Phase.ACTIVE) {
            rule(level, ticks);
            if (ticks % 6 == 0) {
                ambient(level, ticks);
            }
            ticks++;
            if (ticks % 10 == 0) {
                int percent = percent();
                if (percent != lastPercent) {
                    lastPercent = percent;
                    send(level, DomainPayload.progress(id, percent, ACTIVE_TICKS - ticks));
                }
            }
            if (ticks >= ACTIVE_TICKS) {
                end(level, false);
            }
            return true;
        }
        return false;
    }

    public int percent() {
        return Math.min(100, Math.round(100f * dealt / Math.max(1f, breakNeeded())));
    }

    // 心臓が失った体力 (点) を伝える。領域のあいだだけ数え、届いたら砕ける
    public void damaged(ServerLevel level, float lost) {
        if (phase != Phase.ACTIVE || lost <= 0) {
            return;
        }
        dealt += lost;
        if (dealt >= breakNeeded()) {
            broken = true;
            end(level, true);
        }
    }

    // 領域を終える (砕かれた / 時間切れ)。出ている予告は全部消し、砕けたなら心臓は 5 秒傷を 1.5 倍受ける
    public void end(ServerLevel level, boolean shattered) {
        if (!running()) {
            return;
        }
        phase = Phase.DONE;
        broken = shattered;
        Telegraph.cancelAll(heart);
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

    // 領域が終わった (砕かれたなら shattered)
    protected void onEnd(ServerLevel level, boolean shattered) {
    }

    // ---- 道具 ----

    protected List<ServerPlayer> inRoom() {
        return Players.snapshot(players.get());
    }

    // 予告の組み立て (持ち主は心臓。当たるのは部屋の人)
    protected Telegraph.Builder tg(ServerLevel level, jp.revati.skyabyss.combat.TelegraphShape shape, int warn) {
        return Telegraph.in(level, shape).floor(floorY).warn(warn).danger().owner(heart).victims(victims).reach(3.5);
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
}

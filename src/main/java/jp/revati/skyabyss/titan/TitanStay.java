package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 巨神が止まって乗れるようになってからの地上での滞在 (四体で共通)。20 分 = 24000 tick。
// 乗れる窓の長さはこの一つで決まる (岩鯨は鎖が降りてから、潮殻は潮門が開いてから、雷翼は止まり木に着いてから、余燼は浮いて眠ってから)。
// 途中で去るのは、心臓が止まって眠るとき (TitanDormancy)、遠くまで人がいなくなって呼び直されるとき (TitanKeeper)、
// 巨神での移動 (titan.travel) が始まるとき、そして「誰もいない」のが 3 分続いたとき (Vacancy。下)。残りは各巨神の保存データ (RestTicks / ForcedTicks) に入り、再起動しても続く。
public final class TitanStay {
    public static final int TICKS = 24000;
    // 去る前の知らせ: 残り 2 分と 30 秒
    public static final int WARN_TICKS = 2400;
    public static final int WARN_LAST = 600;
    private static final double RANGE = 200;
    // 早く去る (rpg_brushup B5): 巨神から LEAVE_RANGE ブロック以内に誰もいない状態が LEAVE_AFTER tick (3 分) 続いたら去る。
    // 戻ってくれば数え直し。そのグループが背中の中にいるあいだは数えない
    public static final double LEAVE_RANGE = 160;
    public static final int LEAVE_AFTER = 3 * 60 * 20;

    private TitanStay() {
    }

    // 知らせの境の幅 (残りの値が跳んでも、この幅の中で一度は見る)
    public static final int WARN_WINDOW = 20;

    // 前回の値を持たない版: 残りが境から WARN_WINDOW tick の中にあれば、近くの人に一行 (key = message.skyabyss.stay_ending.<titan>)。
    // 毎 tick 呼ぶ前提 (値が 1 ずつ減るときは境ちょうどの 1 回だけ。幅の中で値が減り続けるあいだは、境ちょうどの値のときだけ)
    public static void warnIfDue(ServerLevel level, Vec3 at, int left, String key) {
        if (boundary(left) > 0 && left == boundary(left)) {
            send(level, at, key);
        }
    }

    // left が境 (WARN_TICKS / WARN_LAST) の幅の中なら、その境。そうでなければ 0
    public static int boundary(int left) {
        for (int b : new int[] {WARN_LAST, WARN_TICKS}) {
            if (left <= b && left > b - WARN_WINDOW) {
                return b;
            }
        }
        return 0;
    }

    private static void send(ServerLevel level, Vec3 at, String key) {
        for (ServerPlayer p : level.players()) {
            if (TitanKeeper.eligible(p) && p.distanceToSqr(at.x, p.getY(), at.z) < RANGE * RANGE) {
                Notice.bar(p, ModText.lore(key));
            }
        }
    }

    // 誰かがいるか: 巨神 at から LEAVE_RANGE (水平) 以内に生きている人 (観戦者を除く) がいる、または巨神 titan の背中の中に人がいる
    public static boolean attended(ServerLevel level, Vec3 at, String titan) {
        for (ServerPlayer p : level.players()) {
            if (TitanKeeper.eligible(p) && p.distanceToSqr(at.x, p.getY(), at.z) < LEAVE_RANGE * LEAVE_RANGE) {
                return true;
            }
        }
        var back = jp.revati.skyabyss.progress.Titans.backOf(titan);
        ServerLevel inside = back == null ? null : level.getServer().getLevel(back);
        if (inside != null) {
            for (ServerPlayer p : inside.players()) {
                if (TitanKeeper.eligible(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 無人の連続を数える (巨神ごと・保存しない。再読み込みで数え直し)。毎 tick tick(attended) を呼び、true が返ったら去る
    public static final class Vacancy {
        private int vacant;

        // 戻りは「去ってよい (3 分続いた)」
        public boolean tick(boolean attended) {
            if (attended) {
                vacant = 0;
                return false;
            }
            if (++vacant >= LEAVE_AFTER) {
                vacant = 0;
                return true;
            }
            return false;
        }

        public int vacantTicks() {
            return vacant;
        }

        public void reset() {
            vacant = 0;
        }
    }

    // 巨神ごとの知らせ係。前回の残りを覚えて、残りが跳んで境をまたいだとき (旅の降り・WhaleHush の延長など) も一度だけ知らせる。
    // 保存はしない (再読み込みの直後は、境の幅の中にいるときだけ知らせる)
    public static final class Warner {
        private int last = -1;
        private final Vacancy vacancy = new Vacancy();

        // 毎 tick (滞在中だけ)。誰もいないのが 3 分続いたら true = 今すぐ去ってよい
        public boolean vacantTooLong(ServerLevel level, Vec3 at, String titan) {
            return vacancy.tick(attended(level, at, titan));
        }

        // 毎 tick、残りを渡す。またいだ境のうち一番小さいものを一度だけ知らせる。知らせたら true
        public boolean tick(ServerLevel level, Vec3 at, int left, String key) {
            int due = crossed(last, left);
            last = left;
            if (due > 0) {
                send(level, at, key);
                return true;
            }
            return false;
        }

        // 巨神が去る・やり直すとき。次の滞在で前回の値を持ち越さない
        public void reset() {
            last = -1;
            vacancy.reset();
        }
    }

    // prev から left へ減る間にまたいだ境の、一番小さいもの (またいでいなければ 0)。prev < 0 (前回なし) は幅の中だけ見る
    public static int crossed(int prev, int left) {
        if (left <= 0) {
            return 0;
        }
        if (prev < 0) {
            return boundary(left);
        }
        for (int b : new int[] {WARN_LAST, WARN_TICKS}) {
            if (prev > b && left <= b) {
                return b;
            }
        }
        return 0;
    }
}

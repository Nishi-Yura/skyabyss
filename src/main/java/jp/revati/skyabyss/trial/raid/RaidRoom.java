package jp.revati.skyabyss.trial.raid;

// レイドの部屋 (docs/design/32 の 7 章)。巨神ごとに歩・智・闘の三つ、決まった部屋を RaidRooms.register で登録する。
// 部屋は「区画 (TrialSlot) の遠い島に建てる、閉じた一室」で、巨神の体の中には作らない。
//
// ---- 部屋を書く側の約束 (一回の挑戦につきインスタンスが一つ。工場 Supplier<RaidRoom> が毎回新しく作る) ----
//  1. build(ctx)   区画に部屋を建てる。RaidKit.buildCell(ctx, spec) が床・壁・天井・入口の戸・出口の戸・休み場まで建てるので、
//                  中の飾りと仕掛け (台座・骨・柱など) をその部屋の中だけに足す。ctx.rng() は挑戦ごとの乱数 (配置・順・敵の数を少し変える)。
//                  まだ人は居ない。一度だけ呼ばれる
//  2. start(ctx)   入口の戸が開いて始まる。休み場の準備 (3 秒) のあと。リセットのあとにも呼ばれる (もう一度、最初から)
//  3. tick(ctx)    始まってから 1 tick ごと。敵の湧き・目標の判定・タイマーはここ。ctx.players() = 部屋にいる参加者
//  4. isComplete() 越えたら true。RaidRun が見て、出口の戸を開け、順路の既存の「越えた」(TrialRoute.onCleared) を呼び、
//                  4 秒後に次の段へ運ぶ。呼ぶのは部屋ではなく枠組み
//  5. reset(ctx)   全員が倒れた (または ctx.fail() を呼んだ) とき、「部屋の入口に戻って最初から」のために、動くものを全部戻す:
//                  敵 (ctx.clearMobs())・床に落ちた物・動かした仕掛け・進みの数。build 直後の姿へ。このあと start(ctx) がもう一度呼ばれる。
//                  部屋全部を建て直さなくてよい
//  5b. onUse(ctx, player, pos, attack)  (任意) 部屋のブロックを叩いた/使った。仕掛けの入力に使う (部屋の中のブロックは壊せない・置けない)
//  6. end(ctx)     (任意) 順路が終わる・次の段へ運ばれて部屋を片付けるとき。ctx.clearMobs() は枠組みがやる
//
//  目的の帯 (HUD): objectiveKey()/objectiveArgs() = 「いまやること」の文 (lang キー。毎回呼ばれるので、いまの進みで返してよい)、
//  counter()/counterMax() = 「2/3」の数 (counterMax <= 0 なら出さない)、secondary*() = 二つ目の数 (核の体力など)、
//  timerTicks() = 残り tick (負なら出さない)。部屋の名前は試練の名前 (trial.skyabyss.cell.<id>) が自動で出る。
//  赤だけの予告の決まり (combat/Telegraph) は敵・仕掛けの側で守る。一人でも解け、仲間で手分けすると早い作りにする
public abstract class RaidRoom {
    // 部屋を建てる。RaidKit.buildCell(ctx, ...) を呼んで ctx.cell() を得る
    public abstract void build(RaidCtx ctx);

    // 始まる (入口が開いた)。リセットのあとにも呼ばれる
    public abstract void start(RaidCtx ctx);

    // 始まってから 1 tick ごと
    public abstract void tick(RaidCtx ctx);

    // 越えたか
    public abstract boolean isComplete();

    // 動くものを build 直後の姿へ戻す (全滅・ctx.fail())
    public abstract void reset(RaidCtx ctx);

    // 目的の文の lang キー (例 "raid.skyabyss.WR1.carry")
    public abstract String objectiveKey();

    public Object[] objectiveArgs() {
        return new Object[0];
    }

    // 「2/3」の数。counterMax <= 0 なら出さない
    public int counter() {
        return 0;
    }

    public int counterMax() {
        return 0;
    }

    // 二つ目の数 (例: 核の体力)。ラベルの lang キーは secondaryKey()。secondaryMax <= 0 なら出さない
    public String secondaryKey() {
        return "";
    }

    public int secondary() {
        return 0;
    }

    public int secondaryMax() {
        return 0;
    }

    // 残り tick。負なら出さない
    public int timerTicks() {
        return -1;
    }

    // 行き先の印 (任意。運ぶ部屋の届け先・次に叩く仕掛けなど)。null なら出さない。
    // RaidRun が、ここに床から天井までの光の柱 (粒) を立て、目的の帯に矢印と距離 (「↗ 12m」) を出す。毎回呼ばれるので、いまの進みで返してよい
    @javax.annotation.Nullable
    public net.minecraft.core.BlockPos guideTarget(RaidCtx ctx) {
        return null;
    }

    // 部屋の中のブロックを叩いた・使った (任意。attack = 左クリック)。扱ったら true (元の動作を打ち消す)。部屋の中のブロックはどのみち壊せず置けない
    public boolean onUse(RaidCtx ctx, net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos, boolean attack) {
        return false;
    }

    // 片付け (任意)
    public void end(RaidCtx ctx) {
    }

    // ---- 闘の部屋の中ボス (mid/FightBossRoom、docs/design/32 の 9.5 の表)。元の部屋の仕掛けを中ボス戦にも効かせる (どれも任意)
    // 波のあとに中ボスが出る (闘の部屋として包まれたとき true。中ボスと重なる最後の波・精鋭を出さない部屋がある)
    protected boolean bossFollows;

    public void followBoss() {
        bossFollows = true;
    }

    // 中ボスの仕掛けを建てる前 (部屋の姿を控える前)。中ボスの間に部屋の形を渡す (潮招きの小島を苗の台に など)
    public void bossFit(RaidCtx ctx, jp.revati.skyabyss.trial.raid.mid.MidRoom mid) {
    }

    // 中ボスが出た直後
    public void bossBegin(RaidCtx ctx, net.minecraft.world.entity.Mob boss) {
    }

    // 中ボスの段の 1 tick ごと (中ボスの間の tick のあと)
    public void bossTick(RaidCtx ctx, net.minecraft.world.entity.Mob boss) {
    }

    // 中ボスを倒した (出口の灯りなど)
    public void bossDone(RaidCtx ctx) {
    }

    // 中ボスの段でも、帯の二つ目の数 (核・炉の体力など) を元の部屋のものにする
    public boolean bossKeepsSecondary() {
        return false;
    }

    // 中ボスの段の残り tick (突風までなど)。中ボスの間が残り tick を出さないときに出す。負なら出さない
    public int bossTimerTicks() {
        return -1;
    }
}

package jp.revati.skyabyss.titan.travel;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

// 巨神での移動の数値と、呼べるかどうかの決まり。数値は docs/design/09_titan_travel.md の表と同じにする
public final class TravelRules {
    // ---- 速さ (ブロック/tick) と高さ
    // 旅の速さ。エリトラで花火を使うのと同じくらい (28 ブロック/秒)。地形の生成もその程度に収まる
    public static final double TRAVEL_SPEED = 1.4;
    // 迎えに行くとき・降りていくときの、いちばん遅い速さ
    public static final double SLOW_SPEED = 0.12;
    // 旅の高さ: 雲 (y=192〜196) の上。地形がそれより高ければ、その 50 上 (建築の上限の 50 下まで)
    public static final double TRAVEL_Y = 212;
    public static final double TERRAIN_CLEARANCE = 50;
    // 迎え・降ろすときの高さ (巨神の中心が、鎖の下の地面の何ブロック上か)
    public static final double HOVER_CLEARANCE = 30;
    public static final float TURN_RATE = 1.2f;

    // ---- 距離
    // これより近い行き先へは運ばない
    public static final double MIN_DISTANCE = 64;
    // これより遠い旅は、雲の海へ潜って時間を縮める (雲の帳)
    public static final double VEIL_DISTANCE = 600;
    // 雲の帳から出る所 (行き先の手前)
    public static final double VEIL_EXIT = 220;
    // 迎えに行く岩鯨がこれより遠ければ、雲の向こうから呼び寄せる (この距離の所に現れて泳いでくる)
    public static final double CALL_FLY_LIMIT = 480;
    public static final double CALL_ENTRY = 260;
    // 呼ばれた岩鯨の鎖のそばにいれば、ほかの人の呼んだ巨神にも乗れる
    public static final double JOIN_RADIUS = 48;

    // ---- 時間 (tick)
    // 呼んでから鎖が下りるまでの上限 (超えたら、迎えの場所の上へ寄せる)
    public static final int COMING_LIMIT = 900;
    // 鎖が下りてから、呼んだ人が乗り場へ上がるまで待つ
    public static final int WAIT_FOR_LEADER = 1200;
    // 乗り場で、行き先を決めるまで待つ
    public static final int CHOOSE_LIMIT = 1800;
    // 行き先が決まってから身を起こすまで (ほかの人が乗る時間)。60 秒
    public static final int BOARDING_COUNTDOWN = 1200;
    // 近くの仲間がまだ乗っていないあいだ、最大でここまで延ばす (30 秒)
    public static final int BOARDING_EXTEND_MAX = 600;
    // 延ばしているあいだ、残りをこの tick 数に保つ
    public static final int BOARDING_EXTEND_HOLD = 100;
    // この距離内の仲間 (同じパーティ) は、乗るのを待つ。全員乗っていれば 5 秒で自動出発する
    public static final double BOARDING_PARTY_RADIUS = 64;
    // 見積もり (海図の「約〇秒」) に入れる出発の待ち。延ばす待ちは含めない
    public static final int BOARDING_ESTIMATE = 160;
    // 雲の海を泳ぐ時間 (雲の帳まで)。距離に応じて伸びる
    public static final int CRUISE_MIN = 240;
    public static final int CRUISE_MAX = 420;
    // 雲の帳。白くなりきってから、行き先の読み込みを待って移す
    public static final int VEIL_FADE = 20;
    public static final int VEIL_WAIT_LIMIT = 200;
    // 着いてから、その場で背中の鎖を垂らして休む (20 分。TitanStay)
    public static final int REST_AFTER = jp.revati.skyabyss.titan.TitanStay.TICKS;
    // 呼んだ人のクールダウン (着いたあと)。呼んだが乗らなかった・取りやめたときは短い方
    public static final int COOLDOWN = 3600;
    public static final int COOLDOWN_CANCELLED = 600;
    // 鎖を上る・下りる速さと上限
    public static final double CLIMB_UP = 0.85;
    public static final double CLIMB_ALONG = 0.7;
    public static final double LOWER_DOWN = 0.6;
    public static final int CLIMB_LIMIT = 260;
    public static final int LOWER_LIMIT = 400;
    // 戦いの最中とみなす時間 (敵に傷つけられた・敵を傷つけてから)
    public static final int COMBAT_HURT = 200;
    public static final int COMBAT_ATTACK = 100;
    public static final double COMBAT_RADIUS = 16;

    private TravelRules() {
    }

    // 雲の海を泳ぐ時間 (tick)
    public static int cruiseTicks(double distance) {
        return (int) Math.max(CRUISE_MIN, Math.min(CRUISE_MAX, 200 + distance / 30.0));
    }

    // 出発の待ちの上限を、毎 tick 更新する。近くの仲間がまだ乗っていなければ、残りが HOLD になるまで延ばし、上限で止める
    public static int boardingLimit(int limit, int phaseTicks, boolean partyApproaching) {
        if (!partyApproaching) {
            return limit;
        }
        int cap = BOARDING_COUNTDOWN + BOARDING_EXTEND_MAX;
        return Math.max(limit, Math.min(cap, phaseTicks + BOARDING_EXTEND_HOLD));
    }

    // 近くの仲間が全員乗ってから出発するまで (5 秒)。笛をもう一度吹いて早く出す操作はない (自動出発)
    public static final int AUTO_DEPART = 100;

    // 出発の待ちの上限に、自動出発を重ねる。allAboardSince は全員乗った時刻 (phaseTicks)、乗っていない人がいれば負の値
    public static int autoDepartLimit(int limit, int allAboardSince, int phaseTicks) {
        if (allAboardSince < 0) {
            return limit;
        }
        return Math.min(limit, allAboardSince + AUTO_DEPART);
    }

    // 乗り場で行き先を決めてから降ろされるまでの、おおよその秒数 (画面に出す。実際は地形や向きで少し変わる)
    public static int estimateSeconds(double distance) {
        double ticks = BOARDING_ESTIMATE + 40 /* 鎖をたたむ */ + 160 /* 身を起こす */;
        if (distance > VEIL_DISTANCE) {
            ticks += cruiseTicks(distance) + VEIL_FADE * 2 + 40 + VEIL_EXIT / TRAVEL_SPEED * 1.3;
        } else {
            ticks += distance / TRAVEL_SPEED * 1.15;
        }
        // 降りていく (向きを合わせて真上へ寄る) と、鎖で降ろす。試遊で測った 1,433 ブロックの旅 (64 秒) に合わせた
        ticks += 260 + 160;
        return (int) Math.round(ticks / 20.0);
    }

    // ---- 呼べるかどうか (プレイヤーの側の条件)
    public enum Refusal {
        NOT_RECOGNIZED("not_recognized"),
        WRONG_DIMENSION("wrong_dimension"),
        INDOORS("indoors"),
        UNDERWATER("underwater"),
        IN_COMBAT("in_combat"),
        RIDING("riding"),
        COOLDOWN("cooldown");

        public final String key;

        Refusal(String key) {
            this.key = "message.skyabyss.travel.refuse." + key;
        }
    }

    // 呼べなければその理由。巨神の側の都合 (ほかの人を運んでいる等) は Voyages が見る
    public static Refusal check(ServerPlayer player, TitanKind kind, boolean bypass) {
        if (!bypass && !Voyages.isUnlocked(player, kind)) {
            return Refusal.NOT_RECOGNIZED;
        }
        if (player.level().dimension() != Level.OVERWORLD) {
            return Refusal.WRONG_DIMENSION;
        }
        if (player.isPassenger()) {
            return Refusal.RIDING;
        }
        if (bypass) {
            return null;
        }
        if (cooldownLeft(player) > 0) {
            return Refusal.COOLDOWN;
        }
        if (player.isUnderWater()) {
            return Refusal.UNDERWATER;
        }
        if (!openSky(player)) {
            return Refusal.INDOORS;
        }
        if (inCombat(player)) {
            return Refusal.IN_COMBAT;
        }
        return null;
    }

    // 空が見えるか (屋根の下・洞窟では巨神に見つけてもらえない)。頭の上の一番高いブロックより上にいるか、で見る
    public static boolean openSky(ServerPlayer player) {
        Level level = player.level();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
        return player.getEyeY() >= top && level.canSeeSky(player.blockPosition().above());
    }

    public static boolean inCombat(ServerPlayer player) {
        if (player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < COMBAT_HURT) {
            return true;
        }
        if (player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < COMBAT_ATTACK) {
            return true;
        }
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(COMBAT_RADIUS),
                mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() == player);
        return !mobs.isEmpty();
    }

    // 残りのクールダウン (tick)
    public static long cooldownLeft(ServerPlayer player) {
        long now = player.server.overworld().getGameTime();
        return Math.max(0, Voyages.state(player).readyAt - now);
    }
}

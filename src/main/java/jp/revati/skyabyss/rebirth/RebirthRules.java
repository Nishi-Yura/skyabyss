package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// 転生の数字 (docs/design/31)。ゲームの状態に触れない計算だけを置く (自己テストで直接たしかめる)。
// 決定: 回数の上限 20 (rebirth_6 = b)、代価は結晶 5 (rebirth_7 = b)、伸びは 10 回まで線形・そのあと半分・20 回目以降は 1/4 (rebirth_4 = a)、
// 力は小さく (rebirth_8 = b: 10 回で全ダメージ約 +8%)。色は白 → 青緑 → 金 → プリズム (rebirth_5 = a)
public final class RebirthRules {
    public static final int MAX_REBIRTHS = 20;
    public static final int MIN_LEVEL = 50;
    public static final int COST_CRYSTALS = 5;
    // 必要経験値: 転生ごとに −6%、下限 −40% (B03)
    public static final double XP_STEP = 0.06;
    public static final double XP_FLOOR_CUT = 0.40;
    // 要の節 (10・20 番目の節。ふつうの節の 3 倍)
    public static final int KEY_EVERY = 10;
    public static final double KEY_MULT = 3.0;
    // 逓減 (B06): 10 番目の節まで 1、そのあと 1/2、20 番目を超えたら 1/4
    public static final int FULL_UNTIL = 10;
    public static final int HALF_UNTIL = 20;
    // 祖の力 (B10): 5 回ごとに全ダメージ +1%、上限 +4%
    public static final int ANCESTOR_EVERY = 5;
    public static final double ANCESTOR_STEP = 0.01;
    public static final double ANCESTOR_CAP = 0.04;
    // 全ダメージの合計の安全な上限 (魂盤・祖の力・恩恵を合わせて)
    public static final double DAMAGE_CAP = 0.25;
    // ポーチの余白の枠 (B13): 転生 2・6・12 回で 1 つずつ (最大 3)
    public static final int[] SPARE_AT = {2, 6, 12};
    // 若い運び手との絆 (B14): 転生 3・8・15 回
    public static final int[] BOND_AT = {3, 8, 15};
    public static final double[] BOND_SPEED = {1.08, 1.15, 1.25};
    public static final double[] BOND_RANGE = {8, 16, 24};
    // 導きの欠片 (B05): 5 個で魂盤の節 1 つ (一生で 6 つまで)。1 日 3 回まで・同じ人は 1 日 1 回まで
    public static final int SHARDS_PER_NODE = 5;
    public static final int MAX_SHARD_NODES = 6;
    public static final int GUIDE_PER_DAY = 3;
    // 共闘のレベル合わせ (B21): この差までは何も変わらず、超えた 1 レベルごとに傷の倍率が 1.2% 下がる (最低 0.6)
    public static final int SYNC_GRACE = 3;
    public static final double SYNC_STEP = 0.012;
    public static final double SYNC_FLOOR = 0.6;

    private RebirthRules() {
    }

    public static int clampCount(int n) {
        return Math.max(0, Math.min(MAX_REBIRTHS, n));
    }

    // ---------------------------------------------------------------- 経験値 (B03)
    public static double xpScale(int rebirths) {
        return 1.0 - Math.min(XP_FLOOR_CUT, XP_STEP * Math.max(0, rebirths));
    }

    public static int scaledXp(int base, int rebirths) {
        if (base <= 0 || rebirths <= 0) {
            return base;
        }
        return Math.max(1, (int) Math.round(base * xpScale(rebirths)));
    }

    // ---------------------------------------------------------------- 逓減 (B06)
    // ordinal 番目 (1 から) の節・恩恵の値の倍率
    public static double scale(int ordinal) {
        if (ordinal <= FULL_UNTIL) {
            return 1.0;
        }
        return ordinal <= HALF_UNTIL ? 0.5 : 0.25;
    }

    public static boolean isKey(int ordinal) {
        return ordinal > 0 && ordinal % KEY_EVERY == 0;
    }

    // 祖の力 (B10)
    public static double ancestor(int rebirths) {
        return Math.min(ANCESTOR_CAP, ANCESTOR_STEP * (Math.max(0, rebirths) / ANCESTOR_EVERY));
    }

    // ---------------------------------------------------------------- 色と記号 (C01・C02 に渡す)
    // 段 0 白、1 青緑 (1〜4)、2 金 (5〜14)、3 プリズム (15 以上)
    public static int tier(int rebirths) {
        return rebirths >= 15 ? 3 : rebirths >= 5 ? 2 : rebirths >= 1 ? 1 : 0;
    }

    public static final int COLOR_WHITE = 0xFFFFFF;
    public static final int COLOR_TEAL = 0x4FD1C5;
    public static final int COLOR_GOLD = 0xE6B84A;
    public static final int COLOR_PRISM = 0xF0A8FF;

    public static int tierColor(int rebirths) {
        return switch (tier(rebirths)) {
            case 0 -> COLOR_WHITE;
            case 1 -> COLOR_TEAL;
            case 2 -> COLOR_GOLD;
            default -> COLOR_PRISM;
        };
    }

    // プリズムは時間で色相が回る (ms)。ほかの段は固定色
    public static int tierColor(int rebirths, long millis) {
        if (tier(rebirths) < 3) {
            return tierColor(rebirths);
        }
        float hue = (millis % 6000L) / 6000f;
        return net.minecraft.util.Mth.hsvToRgb(hue, 0.45f, 1f) & 0xFFFFFF;
    }

    // ✦ (1〜4) → ✧ (5 ごと) に束ね、20 で ✺
    public static String symbol(int rebirths) {
        if (rebirths <= 0) {
            return "";
        }
        if (rebirths >= MAX_REBIRTHS) {
            return "✺";
        }
        return "✧".repeat(rebirths / 5) + "✦".repeat(rebirths % 5);
    }

    // 「Lv 50+n」。レベルを上げ直している間は「Lv 12 ✦✦」
    public static String levelLabel(int level, int rebirths) {
        if (rebirths <= 0) {
            return "Lv " + level;
        }
        if (level >= MIN_LEVEL) {
            return "Lv " + MIN_LEVEL + "+" + rebirths;
        }
        return "Lv " + level + " " + symbol(rebirths);
    }

    // ---------------------------------------------------------------- 魂盤の効き目
    public record Effects(double damage, double projectile, double reduction, double cooldown, double moveSpeed, double attackSpeed,
            double maxMana, double maxHealth, double pickup, double crystal, double xp) {
        public static final Effects NONE = new Effects(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    // 節の並び (腕の id が転生の順) と恩恵の並び (id) と回数から、いまの効き目をまとめる
    public static Effects effects(List<String> nodes, List<String> boons, int rebirths) {
        double damage = ancestor(rebirths);
        double projectile = 0;
        double reduction = 0;
        double cooldown = 0;
        double move = 0;
        double attack = 0;
        double mana = 0;
        double health = 0;
        double pickup = 0;
        double crystal = 0;
        double xp = 0;
        for (int i = 0; i < nodes.size(); i++) {
            SoulArm arm = SoulArm.byId(nodes.get(i));
            if (arm == null) {
                continue;
            }
            int ordinal = i + 1;
            double k = scale(ordinal) * (isKey(ordinal) ? KEY_MULT : 1.0);
            damage += arm.damage * k;
            projectile += arm.projectile * k;
            reduction += arm.reduction * k;
            cooldown += arm.cooldown * k;
            move += arm.move * k;
            mana += arm.mana * k;
            health += arm.health * k;
            pickup += arm.pickup * k;
            crystal += arm.crystal * k;
        }
        for (int i = 0; i < boons.size(); i++) {
            Boon b = Boon.byId(boons.get(i));
            if (b == null) {
                continue;
            }
            double k = scale(i + 1);
            damage += b.damage * k;
            reduction += b.reduction * k;
            cooldown += b.cooldown * k;
            move += b.move * k;
            attack += b.attack * k;
            mana += b.mana * k;
            health += b.health * k;
            pickup += b.pickup * k;
            crystal += b.crystal * k;
            xp += b.xp * k;
        }
        return new Effects(Math.min(DAMAGE_CAP, damage), projectile, Math.min(0.25, reduction), Math.min(0.20, cooldown), move, attack,
                mana, health, Math.min(4.0, pickup), Math.min(0.5, crystal), xp);
    }

    // ---------------------------------------------------------------- 3 択の恩恵 (B07)
    // 毎回違う候補 3 枚。人と転生の回数から決める (ログインし直しても、つなぎ直しても同じ。選ぶまで変わらない)
    public static List<Boon> offer(long seed, int rebirthsDone) {
        List<Boon> pool = new ArrayList<>(List.of(Boon.values()));
        Random r = new Random(seed * 31L + rebirthsDone * 7919L + 17);
        List<Boon> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            out.add(pool.remove(r.nextInt(pool.size())));
        }
        return out;
    }

    // ---------------------------------------------------------------- ポーチの余白・絆
    public static int spareSlots(int rebirths) {
        int n = 0;
        for (int at : SPARE_AT) {
            if (rebirths >= at) {
                n++;
            }
        }
        return n;
    }

    // 絆の段 (0 = なし、1〜3)
    public static int bondTier(int rebirths) {
        int n = 0;
        for (int at : BOND_AT) {
            if (rebirths >= at) {
                n++;
            }
        }
        return n;
    }

    public static double bondSpeed(int rebirths) {
        int t = bondTier(rebirths);
        return t == 0 ? 1.0 : BOND_SPEED[t - 1];
    }

    public static double bondRange(int rebirths) {
        int t = bondTier(rebirths);
        return t == 0 ? 0 : BOND_RANGE[t - 1];
    }

    // ---------------------------------------------------------------- 共闘のレベル合わせ (B21)
    // 自分のレベルと、いちばん低い仲間のレベルから、傷の倍率 (1.0 以下)
    public static double syncMultiplier(int myLevel, int lowestAllyLevel) {
        int over = myLevel - (lowestAllyLevel + SYNC_GRACE);
        if (over <= 0) {
            return 1.0;
        }
        return Math.max(SYNC_FLOOR, 1.0 - SYNC_STEP * over);
    }
}

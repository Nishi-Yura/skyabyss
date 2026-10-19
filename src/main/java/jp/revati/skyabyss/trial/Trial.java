package jp.revati.skyabyss.trial;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

// 試練のプール (docs/design/29) の語彙: 枠 (歩・智・闘)・類・稀さ・極の種類・試練の定義・修飾の口。
// 引き方・種・記録は TrialDraw / TrialData、セルの口は TrialCell。ここは純粋なデータ (ゲームなしで自己テストできる)
public final class Trial {
    private Trial() {
    }

    // 三つの枠。stone = 石の番号 (岩鯨の共鳴石 0 西 / 1 東 / 2 下 と同じ並び: 歩 = 西、闘 = 東、智 = 下)
    public enum Slot {
        WALK("walk", 0), WIT("wit", 2), FIGHT("fight", 1);

        public final String id;
        public final int stone;

        Slot(String id, int stone) {
            this.id = id;
            this.stone = stone;
        }

        @Nullable
        public static Slot byId(String id) {
            for (Slot s : values()) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
            return null;
        }

        public static Slot ofStone(int stone) {
            for (Slot s : values()) {
                if (s.stone == stone) {
                    return s;
                }
            }
            throw new IllegalArgumentException("stone " + stone);
        }
    }

    // 七つの類 (枠の中の味付け。板と図鑑のアイコン)
    public enum Kind {
        CROSS, BEAT, CARRY, SOLVE, TOGETHER, FIGHT, ESCORT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // 常 70% / 稀 25% / 極 5%
    public enum Rarity {
        COMMON, RARE, EXTREME;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // 極の種類。P1 は「二つの障害」と「限界の数字 (数字がすべて一番きびしい)」。鏡像・逆走は P2 (部屋を左右反転して建てる)
    public enum Extreme {
        NONE, DOUBLE_HAZARD, GAUNTLET;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // 修飾の口。layouts = 配置の型の数 (1 以上)、numbers = 数字の振りの名前 (最大 2。各 3 段: やさしい 0 / 標準 1 / きびしい 2)、
    // enemySets = 敵の組の数 (0 = 敵なし。戦う・護る枠では必ず 1 つ選ぶ)、hazards = 障害の名前、goals = 任意の目標の名前。
    // forbidden = 禁止の組み合わせ {配置, 障害の番号} (その配置ではその障害を出さない)。
    // hazardsLimit = 極のときの障害の数 (2 以上なら DOUBLE_HAZARD が出る)
    public record Space(int layouts, List<String> numbers, int enemySets, List<String> hazards, List<String> goals, List<int[]> forbidden) {
        public static final Space EMPTY = new Space(1, List.of(), 0, List.of(), List.of(), List.of());

        public boolean isEmpty() {
            return layouts <= 1 && numbers.isEmpty() && enemySets == 0 && hazards.isEmpty() && goals.isEmpty();
        }

        public boolean forbids(int layout, int hazard) {
            for (int[] f : forbidden) {
                if (f[0] == layout && f[1] == hazard) {
                    return true;
                }
            }
            return false;
        }

        // 極に出せる種類
        public List<Extreme> extremes() {
            java.util.List<Extreme> out = new java.util.ArrayList<>();
            if (hazards.size() >= 2) {
                out.add(Extreme.DOUBLE_HAZARD);
            }
            if (!numbers.isEmpty()) {
                out.add(Extreme.GAUNTLET);
            }
            return out;
        }
    }

    // 試練の定義。id は巨神ごとの記号 (W 岩鯨・T 潮殻・S 雷翼・E 余燼) と番号。titan は進行の名前 (stone_whale など)。
    // original = 今ある試練 (初回の周は必ずこれ)。physical = 世界が変わる (2 レーン)
    public record Def(String id, String titan, Slot slot, Kind kind, int tier, boolean physical, boolean original, Space space) {
        public String nameKey() {
            return "trial.skyabyss.cell." + id;
        }

        public String descKey() {
            return "trial.skyabyss.cell." + id + ".desc";
        }

        public int lanes() {
            return physical ? 2 : 1;
        }
    }

    // 引いた修飾。number[i] は space.numbers[i] の段 (0 やさしい / 1 標準 / 2 きびしい)。hazards は space.hazards の番号 (0〜2 個)。
    // enemy は敵の組の番号 (なしは -1)、goal は任意の目標の番号 (なしは -1)。quiet = 静 (敵が少ない)
    public record Mods(int layout, int[] numbers, int enemy, int[] hazards, int goal, Extreme extreme, boolean quiet, Rarity rarity) {
        public static final Mods PLAIN = new Mods(0, new int[0], -1, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);

        public int number(int i) {
            return i < numbers.length ? numbers[i] : 1;
        }

        public boolean hasHazard(int h) {
            for (int x : hazards) {
                if (x == h) {
                    return true;
                }
            }
            return false;
        }

        public Mods withQuiet(boolean q) {
            return new Mods(layout, numbers, enemy, hazards, goal, extreme, q, rarity);
        }

        // 「配置.数字.敵.障害.目標.極.静」 例 "1.1-2.0.0+2.-1.0.0"
        public String encode() {
            StringBuilder sb = new StringBuilder();
            sb.append(layout).append('.');
            for (int i = 0; i < numbers.length; i++) {
                sb.append(i > 0 ? "-" : "").append(numbers[i]);
            }
            sb.append('.').append(enemy).append('.');
            for (int i = 0; i < hazards.length; i++) {
                sb.append(i > 0 ? "+" : "").append(hazards[i]);
            }
            sb.append('.').append(goal).append('.').append(extreme.ordinal()).append('.').append(quiet ? 1 : 0).append('.').append(rarity.ordinal());
            return sb.toString();
        }

        public static Mods decode(String s) {
            try {
                String[] p = s.split("\\.", -1);
                int layout = Integer.parseInt(p[0]);
                int[] numbers = p[1].isEmpty() ? new int[0] : java.util.Arrays.stream(p[1].split("-")).mapToInt(Integer::parseInt).toArray();
                int enemy = Integer.parseInt(p[2]);
                int[] hazards = p[3].isEmpty() ? new int[0] : java.util.Arrays.stream(p[3].split("\\+")).mapToInt(Integer::parseInt).toArray();
                int goal = Integer.parseInt(p[4]);
                Extreme extreme = Extreme.values()[Integer.parseInt(p[5])];
                boolean quiet = p[6].equals("1");
                Rarity rarity = Rarity.values()[Integer.parseInt(p[7])];
                return new Mods(layout, numbers, enemy, hazards, goal, extreme, quiet, rarity);
            } catch (RuntimeException e) {
                return PLAIN;
            }
        }
    }
}

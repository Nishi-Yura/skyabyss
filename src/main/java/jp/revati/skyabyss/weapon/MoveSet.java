package jp.revati.skyabyss.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// 武器ひとつぶんの動きの集まり (assets の weapon_moves/<品の id>.json)。クライアントの描画と、サーバーの「当たる tick」が同じものを読む。
//  - 段 (Step): 連撃の 1 振り。キーフレーム (時刻 0..1 = 振りの進み) の列。チャンネルごとに値と、その key へ向かう区間のイージング
//  - strike: 当たる瞬間の進み (0..1)。サーバーは「振り始め + strike × 振りの長さ」の tick にダメージを入れ、クライアントの動きもその時刻に打ち抜きの key (hit) を置く
//  - hitstop / shake: 当たったときの止まり (tick。最大 3) と画面の揺れの強さ (0..1。クライアントの設定で弱められる)
// 時間は全部 tick。振りの長さは武器の攻撃速度から決まる (durationFor)。ダメージの量と次の一撃までの間 (クールダウン) は触らない
public final class MoveSet {
    // チャンネル: 三人称の利き腕 (M) / 反対の腕 (O)、胴のひねり、肩の前後、一人称の移動と回転、体の傾き、軌跡と光の強さ
    public static final String[] CHANNELS = {"MX", "MY", "MZ", "OX", "OY", "OZ", "BY", "PZ", "TX", "TY", "TZ", "RX", "RY", "RZ", "LEAN", "TRAIL", "GLOW"};
    public static final int MX = 0, MY = 1, MZ = 2, OX = 3, OY = 4, OZ = 5, BY = 6, PZ = 7, TX = 8, TY = 9, TZ = 10, RX = 11, RY = 12, RZ = 13,
            LEAN = 14, TRAIL = 15, GLOW = 16;

    // 攻撃速度 1.0 のときの振りの長さ (tick)。長さ = これ / 攻撃速度 (6〜16 tick に収める)
    public static final float TICKS_AT_SPEED_1 = 12f;
    public static final int MIN_TICKS = 6;
    public static final int MAX_TICKS = 16;
    // 点検 (インスペクト) の長さ (tick)
    public static final int INSPECT_TICKS = 44;
    public static final int MAX_HITSTOP = 3;

    public final String item;
    public final String label;
    public final String family;
    public final float strike;
    public final float reach;
    public final int window;
    // 普段の構え (三人称)。NaN = バニラの片手持ち。sway = 待機のゆれの大きさ (ラジアン)、swayHz = 1 秒あたりの往復
    public final float idleMX, idleMY, idleOX, idleOY, sway, swayHz;
    // 弓・盾など、振り以外の長さを固定する動き (放した後のはずみなど)。0 = 攻撃速度から決める
    public final int fixedDuration;
    public final List<Step> steps;
    public final Step inspect;

    private MoveSet(String item, JsonObject o) {
        this.item = item;
        this.label = str(o, "label", item);
        this.family = str(o, "family", "GREATSWORD");
        this.strike = num(o, "strike", 0.5f);
        this.reach = num(o, "reach", 0.6f);
        this.window = (int) num(o, "window", 12f);
        this.fixedDuration = (int) num(o, "fixedDuration", 0f);
        JsonObject idle = o.has("idle") ? o.getAsJsonObject("idle") : new JsonObject();
        this.idleMX = num(idle, "mx", Float.NaN);
        this.idleMY = num(idle, "my", 0f);
        this.idleOX = num(idle, "ox", Float.NaN);
        this.idleOY = num(idle, "oy", 0f);
        this.sway = num(idle, "sway", 0.02f);
        this.swayHz = num(idle, "hz", 0.35f);
        List<Step> list = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("steps")) {
            list.add(new Step(e.getAsJsonObject()));
        }
        this.steps = List.copyOf(list);
        this.inspect = o.has("inspect") ? new Step(o.getAsJsonObject("inspect")) : null;
    }

    public static MoveSet parse(String item, JsonObject json) {
        return new MoveSet(item, json);
    }

    // 振りの長さ (tick)。攻撃速度が速いほど短い
    public int durationFor(double attackSpeed) {
        if (fixedDuration > 0) {
            return fixedDuration;
        }
        if (!(attackSpeed > 0.05)) {
            return MAX_TICKS;
        }
        return Math.max(MIN_TICKS, Math.min(MAX_TICKS, Math.round((float) (TICKS_AT_SPEED_1 / attackSpeed))));
    }

    // ダメージが入る tick (振り始めからの経過)。サーバーはこの tick に当たりを判定し、クライアントの動きの hit の key もこの時刻に来る
    public int strikeTick(int duration) {
        return Math.max(1, Math.min(duration - 1, Math.round(strike * duration)));
    }

    public Step step(int i) {
        return steps.get(Math.floorMod(i, steps.size()));
    }

    // 一つの振り (キーフレームの列)
    public static final class Step {
        public final String name;
        public final int hitstop;
        public final float shake;
        public final float blendIn, blendOut;
        // hit の key の時刻 (なければ -1)
        public final float hitAt;
        private final Track[] tracks = new Track[CHANNELS.length];

        @SuppressWarnings("unchecked")
        Step(JsonObject o) {
            this.name = str(o, "name", "");
            this.hitstop = Math.min(MAX_HITSTOP, (int) num(o, "hitstop", 0f));
            this.shake = num(o, "shake", 0f);
            this.blendIn = num(o, "blendIn", 0.12f);
            this.blendOut = num(o, "blendOut", 0.2f);
            float hit = -1f;
            JsonArray keys = o.getAsJsonArray("keys");
            List<float[]>[] ts = new List[CHANNELS.length];
            List<Ease>[] es = new List[CHANNELS.length];
            for (JsonElement ke : keys) {
                JsonObject k = ke.getAsJsonObject();
                float t = k.get("t").getAsFloat();
                Ease ease = Ease.parse(str(k, "e", "smooth"));
                if (k.has("hit") && k.get("hit").getAsBoolean()) {
                    hit = t;
                }
                for (Map.Entry<String, JsonElement> en : k.getAsJsonObject("v").entrySet()) {
                    int ch = channel(en.getKey());
                    if (ts[ch] == null) {
                        ts[ch] = new ArrayList<>();
                        es[ch] = new ArrayList<>();
                    }
                    ts[ch].add(new float[] {t, en.getValue().getAsFloat()});
                    es[ch].add(ease);
                }
            }
            this.hitAt = hit;
            for (int c = 0; c < CHANNELS.length; c++) {
                if (ts[c] != null) {
                    tracks[c] = new Track(ts[c], es[c]);
                }
            }
        }

        public boolean has(int channel) {
            return tracks[channel] != null;
        }

        // チャンネルの値 (進み t は 0..1)。なければ fallback
        public float get(int channel, float t, float fallback) {
            Track tr = tracks[channel];
            return tr == null ? fallback : tr.at(t);
        }

        // 普段の構えと振りの混ぜ具合 (0 = 構えのまま、1 = 振りの動き)
        public float weight(float t) {
            float in = blendIn <= 0 ? 1 : Math.min(1f, t / blendIn);
            float out = blendOut <= 0 ? 1 : Math.min(1f, (1f - t) / blendOut);
            float w = Math.min(in, out);
            return w * w * (3 - 2 * w);
        }

        // この段のキーの時刻 (全チャンネルの和集合。自己テスト用)
        public List<Float> keyTimes() {
            List<Float> out = new ArrayList<>();
            for (Track tr : tracks) {
                if (tr != null) {
                    for (float t : tr.times) {
                        if (!out.contains(t)) {
                            out.add(t);
                        }
                    }
                }
            }
            out.sort(null);
            return out;
        }
    }

    private static final class Track {
        final float[] times;
        final float[] values;
        final Ease[] ease;

        Track(List<float[]> pts, List<Ease> eases) {
            int n = pts.size();
            times = new float[n];
            values = new float[n];
            ease = new Ease[n];
            for (int i = 0; i < n; i++) {
                times[i] = pts.get(i)[0];
                values[i] = pts.get(i)[1];
                ease[i] = eases.get(i);
            }
        }

        float at(float t) {
            if (t <= times[0]) {
                return values[0];
            }
            for (int i = 1; i < times.length; i++) {
                if (t <= times[i]) {
                    float span = times[i] - times[i - 1];
                    float s = span <= 0f ? 1f : (t - times[i - 1]) / span;
                    return values[i - 1] + (values[i] - values[i - 1]) * ease[i].apply(s);
                }
            }
            return values[values.length - 1];
        }
    }

    static int channel(String name) {
        for (int i = 0; i < CHANNELS.length; i++) {
            if (CHANNELS[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("unknown channel " + name);
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }

    private static float num(JsonObject o, String k, float d) {
        return o.has(k) ? o.get(k).getAsFloat() : d;
    }
}

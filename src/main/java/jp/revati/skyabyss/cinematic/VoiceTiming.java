package jp.revati.skyabyss.cinematic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// 台詞の声に合わせて字幕を出す時刻 (docs/design/20_cinematics.md の 5 章)。tools/gen_cinematic_voice.py が声と一緒に
// assets/skyabyss_cinematic/voice_timing.json へ書く: 台詞 (音の名前) ごとに、日本語の本文の各文字が出る秒 (starts)・声の長さ (dur)。
// 英語など別の本文は、文字の位置の割合で同じ流れに対応させる (句読点の間も、割合で近くなる)
public record VoiceTiming(float[] starts, float dur) {
    private static Map<String, VoiceTiming> cache;

    // 出ている文字の数 (本文は全部で total 文字)。sec = 台詞の頭からの秒。時刻のない台詞は全文
    public static int revealCount(@Nullable VoiceTiming t, float sec, int total) {
        if (t == null) {
            return total;
        }
        return revealCount(t.starts, t.dur, sec, total);
    }

    // 純関数。starts は日本語の本文の各文字の出る秒 (単調増加)、dur は声の長さ。
    // 秒 sec までに出た日本語の文字の数 c を、total 文字へ割合で写す (c が 1 以上なら少なくとも 1 文字)。声の終わり (dur) で全文
    public static int revealCount(float[] starts, float dur, float sec, int total) {
        int m = starts.length;
        if (total <= 0) {
            return 0;
        }
        if (m == 0 || sec >= dur) {
            return total;
        }
        int c = 0;
        while (c < m && starts[c] <= sec) {
            c++;
        }
        if (c == 0) {
            return 0;
        }
        return Math.min(total, (int) Math.ceil((double) c * total / m - 1.0e-9));
    }

    @Nullable
    public static VoiceTiming of(String scene, int index) {
        return all().get(VoiceClips.eventName(scene, index));
    }

    public static synchronized Map<String, VoiceTiming> all() {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    private static Map<String, VoiceTiming> load() {
        Map<String, VoiceTiming> out = new HashMap<>();
        try (InputStream in = VoiceTiming.class.getResourceAsStream("/assets/" + VoiceClips.NAMESPACE + "/voice_timing.json")) {
            if (in == null) {
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                JsonArray ts = o.getAsJsonArray("t");
                float[] starts = new float[ts.size()];
                for (int i = 0; i < starts.length; i++) {
                    starts[i] = ts.get(i).getAsFloat();
                }
                out.put(e.getKey(), new VoiceTiming(starts, o.get("dur").getAsFloat()));
            }
        } catch (Exception e) {
            // 読めなければ時刻なし (全文を出す)
        }
        return out;
    }
}

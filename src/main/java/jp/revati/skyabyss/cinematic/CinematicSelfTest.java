package jp.revati.skyabyss.cinematic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 場面 (シネマティック) の自己テスト (SKYABYSS_SELFTEST=1 のとき SelfTestRunner が呼ぶ。docs/design/20_cinematics.md)。
// 台本の整合 (長さ・台詞が収まる・重ならない・撮影がつながる・最後が本人の目)、日英の文、カメラの座標、パケットの往復、
// サーバーの流れ (始める・二重に始めない・名前違いの End は無視・効果は一度だけ・無敵・人ごとに別・一度きりの記録)
public final class CinematicSelfTest {
    private CinematicSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            scenes(f);
            voice(f);
            lang(f);
            math(f);
            codec(f);
            live(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("cinematic selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST cinematic PASSED (scenes, lang, camera math, payload codec, start/skip/end flow, invulnerable, per-player)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST cinematic FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    // ---------------------------------------------------------------- 台本
    private static void scenes(List<String> f) {
        int count = 0;
        for (Scene s : Scenes.all()) {
            count++;
            check(f, !s.shots().isEmpty() && s.duration() > 0f, s.id() + ": no shots");
            for (Shot shot : s.shots()) {
                check(f, shot.seconds() > 0f, s.id() + ": shot with no length");
            }
            float dur = s.duration();
            DialogueLine prev = null;
            for (DialogueLine l : s.lines()) {
                check(f, l.at() >= 0f && l.end() <= dur, s.id() + ": line outside the scene: " + l.text());
                check(f, l.duration() >= 1.5f, s.id() + ": line too short to read: " + l.text());
                check(f, prev == null || l.at() >= prev.end(), s.id() + ": lines overlap: " + l.text());
                check(f, l.speaker() != Speaker.NPC || !l.label().isEmpty(), s.id() + ": NPC line without a label");
                prev = l;
            }
            // 撮影が切れ目なくつながる (最後の位置 = 次の最初の位置)。場面の座標なので、そのまま比べる
            for (int i = 0; i + 1 < s.shots().size(); i++) {
                check(f, s.shots().get(i).posTo().distanceTo(s.shots().get(i + 1).posFrom()) < 1.0e-6, s.id() + ": shot " + i + " does not meet shot " + (i + 1));
                check(f, sameLook(s.shots().get(i).lookTo(), s.shots().get(i + 1).lookFrom()), s.id() + ": look " + i + " does not meet look " + (i + 1));
            }
            // 最後が本人の目なら、そのまま操作へ戻れる
            if (s.seamless()) {
                Vec3 end = s.shots().get(s.shots().size() - 1).posTo();
                check(f, end.distanceTo(new Vec3(0, CameraMath.EYE, 0)) < 1.0e-6, s.id() + ": seamless scene does not end at the eye");
            }
        }
        // 試練の碑の場面 (trial_tablet_reveal) は順路 (32) で外した。背の初回の場面は FirstRun.registerScene であとから足される
        check(f, count >= 13, "expected 13 scenes (opening, whale, 3 first sights, 4 hearts, 4 dormancies), got " + count);
        Scene opening = Scenes.get(Scenes.OPENING);
        check(f, opening != null && opening.duration() <= 40f && opening.duration() >= 25f, "opening should be 25-40 s");
        check(f, opening != null && opening.intro() && opening.handBack() && opening.seamless(), "opening flags");
        Scene whale = Scenes.get(Scenes.WHALE_FIRST_SIGHT);
        check(f, whale != null && whale.duration() <= 25f, "whale first sight too long");
        for (String titan : Scenes.FIRST_SIGHT_TITANS) {
            // 潮殻・雷翼・余燼の初見 (岩鯨と同じ作り: 約 20 秒、台詞 4 行、カメラは巨神を狙う、スキップ可)
            Scene first = Scenes.get(Scenes.firstId(titan));
            check(f, first != null && first.duration() >= 15f && first.duration() <= 25f && first.lines().size() == 4 && !first.seamless() && first.handBack(),
                    "the first-sight scene for " + titan + " is missing or has odd length / lines");
            if (first != null) {
                check(f, first.shots().get(first.shots().size() - 1).lookTo().kind() == Look.Kind.TARGET, "the first-sight scene for " + titan + " does not end on the titan");
            }
            check(f, CinematicTriggers.sceneOf(titan).equals(Scenes.firstId(titan)), "trigger scene name for " + titan);
        }
        for (String titan : new String[] {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER}) {
            check(f, Scenes.get(Scenes.heartId(titan)) != null, "no heart scene for " + titan);
            // 眠りの場面 (約 16 秒。カメラは巨神を狙い、台詞は 3 行)
            Scene end = Scenes.get(Scenes.endId(titan));
            check(f, end != null && end.duration() >= 14f && end.duration() <= 20f && end.lines().size() == 3 && !end.seamless(),
                    "the dormancy scene for " + titan + " is missing or has odd length / lines");
            if (end != null) {
                for (Shot shot : end.shots()) {
                    check(f, shot.lookFrom().kind() == Look.Kind.TARGET, "the dormancy scene for " + titan + " does not follow the titan");
                }
            }
            // 心臓の台詞は巨神ごと (共通の台詞ではない)
            Scene heart = Scenes.get(Scenes.heartId(titan));
            check(f, heart != null && heart.lines().size() == 2 && heart.lines().get(0).text().contains(titan), "the heart lines for " + titan + " are not per titan");
        }
    }

    // ---------------------------------------------------------------- 台詞の声 (docs/design/20 の 6 章)
    // 台本の全台詞 (場面, 番号) に、声の一覧 (voice_manifest.json。tools/gen/sound/gen_cinematic_voice.py の入力) の行・sounds.json の音・jar の中の .ogg があること。
    // 一覧が台本とずれていたら落ちる。SKYABYSS_DUMP_VOICE=<パス> を付けると、台本から一覧を書き出す (台本を変えたあとに一度流して、tools の README の手順で作り直す)
    private static void voice(List<String> f) {
        StringBuilder out = new StringBuilder("{\"lines\":[\n");
        List<String[]> want = new ArrayList<>();
        for (Scene s : Scenes.all()) {
            for (int i = 0; i < s.lines().size(); i++) {
                DialogueLine l = s.lines().get(i);
                want.add(new String[] {s.id(), Integer.toString(i), Float.toString(l.at()), Float.toString(l.duration()), l.speaker().name(), l.label(), l.text()});
                if (out.length() > 12) {
                    out.append(",\n");
                }
                out.append("{\"scene\":\"").append(s.id()).append("\",\"idx\":").append(i).append(",\"at\":").append(l.at())
                        .append(",\"duration\":").append(l.duration()).append(",\"speaker\":\"").append(l.speaker().name())
                        .append("\",\"label\":\"").append(l.label()).append("\",\"text\":\"").append(l.text()).append("\"}");
            }
        }
        out.append("\n]}\n");
        String dump = System.getenv("SKYABYSS_DUMP_VOICE");
        if (dump != null && !dump.isBlank()) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(dump), out.toString(), StandardCharsets.UTF_8);
                SkyAbyss.LOGGER.info("cinematic voice manifest written: {} ({} lines)", dump, want.size());
            } catch (Exception e) {
                f.add("voice manifest dump: " + e);
            }
        }
        JsonObject manifest = loadJson("/assets/skyabyss_cinematic/voice_manifest.json", f);
        JsonObject sounds = loadJson("/assets/skyabyss_cinematic/sounds.json", f);
        if (manifest == null || sounds == null) {
            return;
        }
        com.google.gson.JsonArray lines = manifest.getAsJsonArray("lines");
        check(f, lines.size() == want.size(), "voice manifest has " + lines.size() + " lines, the scripts have " + want.size() + " (regenerate: tools/README.md)");
        for (int i = 0; i < Math.min(lines.size(), want.size()); i++) {
            JsonObject m = lines.get(i).getAsJsonObject();
            String[] w = want.get(i);
            boolean same = m.get("scene").getAsString().equals(w[0]) && m.get("idx").getAsInt() == Integer.parseInt(w[1])
                    && Math.abs(m.get("at").getAsFloat() - Float.parseFloat(w[2])) < 1.0e-4f
                    && Math.abs(m.get("duration").getAsFloat() - Float.parseFloat(w[3])) < 1.0e-4f
                    && m.get("speaker").getAsString().equals(w[4]) && m.get("label").getAsString().equals(w[5])
                    && m.get("text").getAsString().equals(w[6]);
            check(f, same, "voice manifest line " + i + " differs from the script (" + w[0] + " #" + w[1] + ")");
        }
        for (String[] w : want) {
            int idx = Integer.parseInt(w[1]);
            String name = VoiceClips.eventName(w[0], idx);
            check(f, sounds.has(name), "no voice sound event " + name);
            double clipSec = -1;
            try (InputStream in = CinematicSelfTest.class.getResourceAsStream(VoiceClips.clipResource(w[0], idx))) {
                check(f, in != null, "no voice clip " + VoiceClips.clipResource(w[0], idx));
                if (in != null) {
                    clipSec = oggSeconds(in.readAllBytes());
                }
            } catch (Exception e) {
                f.add("voice clip read " + name + ": " + e);
            }
            // 字幕の時刻: 声の長さ = .ogg の長さ、各文字の時刻は単調で長さに収まり、声は台詞の表示時間に収まる
            VoiceTiming t = VoiceTiming.of(w[0], idx);
            check(f, t != null, "no voice timing " + name);
            if (t != null) {
                check(f, t.steady() || t.starts().length > 0, "voice timing " + name + " has no characters");
                check(f, clipSec > 0 && Math.abs(t.dur() - clipSec) < 0.01, "voice timing " + name + " dur " + t.dur() + " vs clip " + clipSec);
                check(f, t.dur() <= Float.parseFloat(w[3]) + 0.05f, "voice timing " + name + " is longer than the line");
                boolean ok = true;
                for (int k = 0; k < t.starts().length; k++) {
                    ok &= t.starts()[k] >= 0f && t.starts()[k] <= t.dur() + 1.0e-4f && (k == 0 || t.starts()[k] >= t.starts()[k - 1]);
                }
                check(f, ok, "voice timing " + name + " starts are not monotonic / out of range");
                float endSec = t.steady() ? t.span() : t.dur();
                check(f, VoiceTiming.revealCount(t, endSec, 20, VoiceTiming.DEFAULT_CPS) == 20 && VoiceTiming.revealCount(t, -1f, 20, VoiceTiming.DEFAULT_CPS) == 0, "voice timing " + name + " reveal ends");
            }
        }
        revealSchedule(f);
        revealSteady(f);
    }

    // 一定の速さの字幕 (VoiceTiming.revealSteady)
    private static void revealSteady(List<String> f) {
        check(f, VoiceTiming.revealSteady(10f, -0.1f, 10, 8f) == 0, "steady before the line");
        check(f, VoiceTiming.revealSteady(10f, 0f, 10, 8f) == 1, "steady first character");
        check(f, VoiceTiming.revealSteady(10f, 0.5f, 40, 8f) == 5, "steady 8 cps");
        check(f, VoiceTiming.revealSteady(10f, 10f, 40, 8f) == 40, "steady ends at the span");
        check(f, VoiceTiming.revealSteady(2f, 1.7f, 40, 8f) == 40, "steady speeds up for a long line");
        int prev = 0;
        boolean mono = true;
        for (float x = 0f; x < 5f; x += 0.05f) {
            int c = VoiceTiming.revealSteady(5f, x, 30, 8f);
            mono &= c >= prev;
            prev = c;
        }
        check(f, mono && prev == 30, "steady is monotonic");
    }

    // 字幕の出る数の純関数 (VoiceTiming.revealCount)
    private static void revealSchedule(List<String> f) {
        float[] st = {0.0f, 0.5f, 1.0f, 2.0f}; // 4 文字 (2 文字目と 3 文字目、3 文字目と 4 文字目の間があく)
        float dur = 2.5f;
        check(f, VoiceTiming.revealCount(st, dur, -0.1f, 4) == 0, "reveal before the voice");
        check(f, VoiceTiming.revealCount(st, dur, 0.0f, 4) == 1, "reveal first character");
        check(f, VoiceTiming.revealCount(st, dur, 0.49f, 4) == 1, "reveal holds during a gap");
        check(f, VoiceTiming.revealCount(st, dur, 0.5f, 4) == 2, "reveal second character");
        check(f, VoiceTiming.revealCount(st, dur, 1.99f, 4) == 3, "reveal holds during a long pause");
        check(f, VoiceTiming.revealCount(st, dur, 2.0f, 4) == 4, "reveal last character");
        check(f, VoiceTiming.revealCount(st, dur, 2.5f, 7) == 7 && VoiceTiming.revealCount(st, dur, 9f, 7) == 7, "reveal finishes when the voice ends");
        // 別の長さの本文 (英語など) へは割合で写す: 単調で、0 から全文まで
        int prev = 0;
        for (float x = -0.5f; x <= 3f; x += 0.05f) {
            int c = VoiceTiming.revealCount(st, dur, x, 23);
            check(f, c >= prev && c >= 0 && c <= 23, "reveal is monotonic for a longer text");
            prev = c;
        }
        check(f, prev == 23 && VoiceTiming.revealCount(st, dur, 0.0f, 23) >= 1, "reveal maps to another length");
        check(f, VoiceTiming.revealCount((VoiceTiming) null, 0f, 9) == 9 && VoiceTiming.revealCount(new float[0], 1f, 0.1f, 5) == 5, "no timing shows the full text");
    }

    // Ogg Vorbis の長さ (秒): 最後のページの granule position / 標本化周波数 (識別ヘッダーの 4 バイト目から)
    private static double oggSeconds(byte[] b) {
        int rate = 0;
        for (int i = 0; i + 16 < b.length; i++) {
            if (b[i] == 1 && b[i + 1] == 'v' && b[i + 2] == 'o' && b[i + 3] == 'r') {
                rate = (b[i + 12] & 0xFF) | (b[i + 13] & 0xFF) << 8 | (b[i + 14] & 0xFF) << 16 | (b[i + 15] & 0xFF) << 24;
                break;
            }
        }
        for (int i = b.length - 14; i >= 0; i--) {
            if (b[i] == 'O' && b[i + 1] == 'g' && b[i + 2] == 'g' && b[i + 3] == 'S') {
                long g = 0;
                for (int k = 7; k >= 0; k--) {
                    g = g << 8 | (b[i + 6 + k] & 0xFF);
                }
                return rate > 0 ? (double) g / rate : -1;
            }
        }
        return -1;
    }

    private static JsonObject loadJson(String path, List<String> f) {
        try (InputStream in = CinematicSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing resource " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            f.add("read " + path + ": " + e);
            return null;
        }
    }

    private static boolean sameLook(Look a, Look b) {
        return a.kind() == b.kind() && a.v().distanceTo(b.v()) < 1.0e-6;
    }

    // ---------------------------------------------------------------- 日英の文
    private static void lang(List<String> f) {
        for (String locale : new String[] {"ja_jp", "en_us"}) {
            JsonObject json = loadLang(locale, f);
            if (json == null) {
                continue;
            }
            List<String> keys = new ArrayList<>(List.of("cinematic.skyabyss.skip", "cinematic.skyabyss.speaker.hero", "cinematic.skyabyss.speaker.heaven",
                    "cinematic.skyabyss.opening.goal"));
            for (Scene s : Scenes.all()) {
                for (DialogueLine l : s.lines()) {
                    keys.add(l.text());
                    if (!l.label().isEmpty()) {
                        keys.add(l.label());
                    }
                }
            }
            for (String key : keys) {
                // 開幕の一行だけは、既存の開幕の文 (skyabyss_opening)
                if (key.equals("opening.skyabyss.line")) {
                    continue;
                }
                check(f, json.has(key) && !json.get(key).getAsString().isBlank(), locale + ": missing text " + key);
                if (json.has(key)) {
                    // 画面に収まる長さ (3 行まで。幅 約 0.62 画面で日本語 1 行 25 字前後)
                    int max = locale.equals("ja_jp") ? 40 : 90;
                    check(f, json.get(key).getAsString().length() <= max, locale + ": line too long " + key);
                }
            }
        }
    }

    // 場面の文は skyabyss_cinematic。深淵の区切りの場面 (SectionScenes) の文は、五章の層の文といっしょに skyabyss_abyss_v2 (データを直接直す（生成スクリプトはない）)
    private static JsonObject loadLang(String locale, List<String> f) {
        JsonObject json = loadLangFile("skyabyss_cinematic", locale, f);
        JsonObject extra = loadLangFile("skyabyss_abyss_v2", locale, f);
        if (json != null && extra != null) {
            extra.entrySet().forEach(e -> {
                if (!json.has(e.getKey())) {
                    json.add(e.getKey(), e.getValue());
                }
            });
        }
        return json;
    }

    private static JsonObject loadLangFile(String ns, String locale, List<String> f) {
        String path = "/assets/" + ns + "/lang/" + locale + ".json";
        try (InputStream in = CinematicSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing lang file " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            f.add("lang read " + path + ": " + e);
            return null;
        }
    }

    // ---------------------------------------------------------------- カメラの座標
    private static void math(List<String> f) {
        // 前 = yaw の向き (0 = 南 = +z、90 = 西 = -x)、右 = 前の右手
        Vec3 o = new Vec3(10, 64, -5);
        Vec3 south = CameraMath.toWorld(new CameraMath.Ctx(o, 0f, o), new Vec3(2, 3, 5));
        check(f, south.distanceTo(new Vec3(10 - 2, 67, 0)) < 1.0e-6, "toWorld south: " + south);
        Vec3 west = CameraMath.toWorld(new CameraMath.Ctx(o, 90f, o), new Vec3(2, 3, 5));
        check(f, west.distanceTo(new Vec3(5, 67, -5 - 2)) < 1.0e-6, "toWorld west: " + west);
        // 向きの度の往復
        for (float yaw : new float[] {-170f, -90f, 0f, 45f, 90f, 179f}) {
            Vec3 dir = CameraMath.toWorld(new CameraMath.Ctx(Vec3.ZERO, yaw, Vec3.ZERO), new Vec3(0, 0, 1));
            float back = CameraMath.yawOf(dir);
            check(f, Math.abs(net.minecraft.util.Mth.wrapDegrees(back - yaw)) < 1.0e-3, "yaw roundtrip " + yaw + " -> " + back);
        }
        check(f, CameraMath.pitchOf(new Vec3(0, 1, 1)) < -44f && CameraMath.pitchOf(new Vec3(0, 1, 1)) > -46f, "pitch up is negative");
        // 道筋がなめらか (0.05 秒に動く距離が、急に飛ばない)。狙う物は動かない場合
        Vec3 target = new Vec3(0, 200, 250);
        for (Scene s : Scenes.all()) {
            CameraMath.Ctx ctx = new CameraMath.Ctx(Vec3.ZERO, 30f, target);
            CameraMath.Pose last = CameraMath.sample(s, 0f, ctx);
            float maxStep = 0f;
            for (float t = 0.05f; t <= s.duration() + 1.0e-4f; t += 0.05f) {
                CameraMath.Pose p = CameraMath.sample(s, t, ctx);
                maxStep = Math.max(maxStep, (float) p.pos().distanceTo(last.pos()));
                last = p;
            }
            check(f, maxStep < 1.2f, s.id() + ": camera jumps " + maxStep + " blocks in 0.05 s");
            // 範囲外は両端
            check(f, CameraMath.sample(s, -3f, ctx).pos().distanceTo(CameraMath.sample(s, 0f, ctx).pos()) < 1.0e-9, s.id() + ": before start");
            check(f, CameraMath.sample(s, s.duration() + 9f, ctx).pos().distanceTo(CameraMath.sample(s, s.duration(), ctx).pos()) < 1.0e-9,
                    s.id() + ": after end");
        }
        // 開幕の最後は本人の目で、最初の目的の向き (前) を水平に近く見る
        Scene opening = Scenes.get(Scenes.OPENING);
        CameraMath.Ctx ctx = new CameraMath.Ctx(new Vec3(0, 70, 0), 0f, Vec3.ZERO);
        CameraMath.Pose end = CameraMath.sample(opening, opening.duration(), ctx);
        check(f, end.pos().distanceTo(new Vec3(0, 70 + CameraMath.EYE, 0)) < 1.0e-6, "opening ends at the eye");
        float pitch = CameraMath.pitchOf(end.look().subtract(end.pos()));
        check(f, pitch < 0f && pitch > -15f, "opening hands back looking nearly level, got pitch " + pitch);
        check(f, Math.abs(CameraMath.yawOf(end.look().subtract(end.pos()))) < 1.0e-3, "opening hands back facing the goal");
        // 緩急は 0 → 1 で単調
        for (Ease e : Ease.values()) {
            check(f, e.apply(0f) == 0f && Math.abs(e.apply(1f) - 1f) < 1.0e-6 && e.apply(0.5f) > 0f && e.apply(0.5f) < 1f, "ease " + e);
        }
    }

    // ---------------------------------------------------------------- パケット
    private static void codec(List<String> f) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        var start = new CinematicNetwork.StartPayload("whale_first_sight", 1.5, 64, -2.25, 123.5f, 77, 10, 200, -30);
        CinematicNetwork.StartPayload.STREAM_CODEC.encode(buf, start);
        check(f, CinematicNetwork.StartPayload.STREAM_CODEC.decode(buf).equals(start), "start payload roundtrip");
        var end = new CinematicNetwork.EndPayload("opening", true);
        CinematicNetwork.EndPayload.STREAM_CODEC.encode(buf, end);
        check(f, CinematicNetwork.EndPayload.STREAM_CODEC.decode(buf).equals(end), "end payload roundtrip");
        var stop = new CinematicNetwork.StopPayload("opening");
        CinematicNetwork.StopPayload.STREAM_CODEC.encode(buf, stop);
        check(f, CinematicNetwork.StopPayload.STREAM_CODEC.decode(buf).equals(stop), "stop payload roundtrip");
        // 記録の保存
        CinematicState state = new CinematicState();
        state.markSeen("opening");
        var encoded = CinematicState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        CinematicState back = CinematicState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        check(f, back.seen("opening") && !back.seen("whale_first_sight"), "state codec roundtrip");
    }

    // ---------------------------------------------------------------- サーバーの流れ
    private static FakePlayer fake(ServerLevel level, String tail, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-0000000c" + tail), name));
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.getSharedSpawnPos());
        p.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    // 傷を受ける知らせ (LivingIncomingDamageEvent) が取り消されるか
    private static boolean damageBlocked(ServerLevel level, FakePlayer p) {
        var event = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(p, new net.neoforged.neoforge.common.damagesource.DamageContainer(level.damageSources().generic(), 4f));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static void live(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer a = fake(level, "0001", "[cinematic_a]");
        FakePlayer b = fake(level, "0002", "[cinematic_b]");
        Cinematics.state(a).forgetAll();
        Cinematics.state(b).forgetAll();
        Cinematics.stop(a, false);
        Cinematics.stop(b, false);
        // 効果の聞き手: スキップでも一度だけ
        AtomicInteger ends = new AtomicInteger();
        AtomicInteger skips = new AtomicInteger();
        Cinematics.onEnd(Scenes.WHALE_FIRST_SIGHT, (p, skipped) -> {
            ends.incrementAndGet();
            if (skipped) {
                skips.incrementAndGet();
            }
        });
        Vec3 aim = a.position().add(0, 120, 200);
        check(f, Cinematics.start(a, Scenes.WHALE_FIRST_SIGHT, null, aim), "start failed");
        check(f, Cinematics.active(a) && !Cinematics.active(b), "only the target player is in the scene");
        check(f, Scenes.WHALE_FIRST_SIGHT.equals(Cinematics.activeScene(a)), "active scene name");
        check(f, !Cinematics.start(a, Scenes.OPENING, null, null), "started a second scene during one");
        check(f, !Cinematics.startOnce(a, Scenes.WHALE_FIRST_SIGHT, null, aim), "startOnce during the scene");
        check(f, !Cinematics.start(b, "no_such_scene", null, null), "started an unknown scene");
        // 本人は無敵 (場面の間だけ)、ほかの人は変わらない
        check(f, damageBlocked(level, a), "player was hurt during the scene");
        check(f, !damageBlocked(level, b), "other player was protected by someone else's scene");
        // 名前の違う End は無視、本人の End で一度だけ効果
        Cinematics.end(a, Scenes.OPENING, false);
        check(f, Cinematics.active(a) && ends.get() == 0, "wrong-name End ended the scene");
        Cinematics.end(a, Scenes.WHALE_FIRST_SIGHT, true);
        check(f, !Cinematics.active(a) && ends.get() == 1 && skips.get() == 1, "skip did not apply the effects once: " + ends + "/" + skips);
        Cinematics.end(a, Scenes.WHALE_FIRST_SIGHT, true);
        check(f, ends.get() == 1, "effects applied twice");
        // 終わったら傷つく
        check(f, !damageBlocked(level, a), "player stayed invulnerable after the scene");
        // 見た記録: 一度きりの場面は二度出さない。記録を消せばまた出る
        check(f, Cinematics.state(a).seen(Scenes.WHALE_FIRST_SIGHT), "scene not recorded as seen");
        check(f, !Cinematics.startOnce(a, Scenes.WHALE_FIRST_SIGHT, null, aim), "a seen scene started again");
        Cinematics.state(a).forget(Scenes.WHALE_FIRST_SIGHT);
        check(f, Cinematics.startOnce(a, Scenes.WHALE_FIRST_SIGHT, null, aim), "scene did not start again after forgetting");
        // サーバーの打ち切りでも効果は適用される (スキップ扱い)
        Cinematics.stop(a, false);
        check(f, !Cinematics.active(a) && ends.get() == 2 && skips.get() == 2, "server stop did not apply the effects");
        // 心臓の呼び口: 心臓がいなければ何もしない (呼ばれていない間は何も起きない)
        check(f, TitanCinematics.heartAppears(level, "no_such_titan", a) == 0, "heart hook with an unknown titan");
        // 心臓の場面: 範囲内の人それぞれに一度だけ (a, b は近く、c は遠い)。もう一度呼んでも出さない。遠い人・別の人の記録は変わらない
        FakePlayer c = fake(level, "0003", "[cinematic_c]");
        List<FakePlayer> crowd = List.of(a, b, c);
        c.setPos(a.getX() + 500, a.getY(), a.getZ());
        Cinematics.state(c).forgetAll();
        Cinematics.stop(c, false);
        Cinematics.state(a).forgetAll();
        Cinematics.state(b).forgetAll();
        Cinematics.stop(a, false);
        Cinematics.stop(b, false);
        check(f, TitanCinematics.heartAppears(crowd, Titans.STONE_WHALE, a, TitanCinematics.RANGE) == 2, "heart scene did not start for every player in range");
        check(f, Cinematics.active(a) && Cinematics.active(b) && !Cinematics.active(c), "heart scene reached the wrong players");
        Cinematics.stop(a, false);
        Cinematics.stop(b, false);
        check(f, TitanCinematics.heartAppears(crowd, Titans.STONE_WHALE, a, TitanCinematics.RANGE) == 0, "heart scene started twice for the same players");
        check(f, !Cinematics.state(c).seen(Scenes.heartId(Titans.STONE_WHALE)), "far player was marked as having seen the heart scene");
        c.setPos(a.getX() + 5, a.getY(), a.getZ());
        check(f, TitanCinematics.heartAppears(crowd, Titans.STONE_WHALE, a, TitanCinematics.RANGE) == 1 && Cinematics.active(c), "a later player did not get the heart scene on their own");
        Cinematics.stop(c, false);
        Cinematics.state(c).forgetAll();
        Cinematics.state(a).forgetAll();
        Cinematics.state(b).forgetAll();
        // 岩鯨の初見の条件: 伝承で岩鯨を知っている人には出さず、見たことにする
        Cinematics.state(b).forgetAll();
        Lore.reset(b);
        Lore.unlock(b, "chapter1/stone_whale", false);
        check(f, !CinematicTriggers.run(b) && Cinematics.state(b).seen(Scenes.WHALE_FIRST_SIGHT), "whale trigger fired for someone who already knows the whale");
        Lore.reset(b);
        Cinematics.state(b).forgetAll();
        check(f, !CinematicTriggers.run(b) && !Cinematics.active(b), "whale trigger fired with no whale in range");
        Cinematics.stop(a, false);
        Cinematics.stop(b, false);
        Cinematics.state(a).forgetAll();
        Cinematics.state(b).forgetAll();
        firstSights(level, a, b, f);
    }

    // 潮殻・雷翼・余燼の初見の条件 (人ごと・二人目・再入室・範囲・船)。巨神の代わりに置物を立てて、条件の判定 (firstSight) だけを確かめる
    private static void firstSights(ServerLevel level, FakePlayer a, FakePlayer b, List<String> f) {
        for (String titan : Scenes.FIRST_SIGHT_TITANS) {
            String scene = Scenes.firstId(titan);
            // 眠っている巨神は見せない作りなので、前の段 (祠・巨神の子など) がワールドに残した眠りの記録を外してから確かめる
            jp.revati.skyabyss.titan.end.TitanDormancy.clear(level.getServer(), titan);
            Cinematics.state(a).forgetAll();
            Cinematics.state(b).forgetAll();
            Cinematics.stop(a, false);
            Cinematics.stop(b, false);
            a.setPos(b.getX(), b.getY(), b.getZ());
            net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, level);
            // 範囲の内側 (200 ブロック。潮殻 90〜280・雷翼 70〜360・余燼 90〜240 のどれにも入る)
            stand.setPos(a.getX() + 200, a.getY(), a.getZ());
            check(f, CinematicTriggers.firstSight(level, a, titan, null) == false && !Cinematics.active(a), titan + ": started with no titan");
            // 範囲の外: 遠すぎる・近すぎる。記録は付かない
            stand.setPos(a.getX() + 2000, a.getY(), a.getZ());
            check(f, !CinematicTriggers.firstSight(level, a, titan, stand) && !Cinematics.state(a).seen(scene), titan + ": started from too far");
            stand.setPos(a.getX() + 20, a.getY(), a.getZ());
            check(f, !CinematicTriggers.firstSight(level, a, titan, stand) && !Cinematics.state(a).seen(scene), titan + ": started from too close");
            stand.setPos(a.getX() + 200, a.getY(), a.getZ());
            // 一人目: 始まる。同じ人にはもう出ない
            check(f, CinematicTriggers.firstSight(level, a, titan, stand) && scene.equals(Cinematics.activeScene(a)), titan + ": first player did not get the first-sight scene");
            Cinematics.stop(a, false);
            check(f, !CinematicTriggers.firstSight(level, a, titan, stand) && !Cinematics.active(a), titan + ": first-sight scene repeated for the same player");
            // 二人目: 一人目が見たあとでも、自分の分は見る (記録は人ごと)。もう一人の記録は変わらない
            check(f, !Cinematics.state(b).seen(scene), titan + ": another player's record was set by the first player");
            check(f, CinematicTriggers.firstSight(level, b, titan, stand) && Cinematics.active(b), titan + ": second player did not get the first-sight scene");
            Cinematics.stop(b, false);
            // 再入室 (記録の保存と読み戻し) のあとも、見た人には出ない
            CinematicState reloaded = CinematicState.CODEC.parse(JsonOps.INSTANCE, CinematicState.CODEC.encodeStart(JsonOps.INSTANCE, Cinematics.state(a)).getOrThrow()).getOrThrow();
            check(f, reloaded.seen(scene), titan + ": seen record lost across save / relog");
            // 巨神が死んでいる・読み込まれていない (null) ときは出ない。出し逃した人は、あとで条件が合えば見られる
            Cinematics.state(a).forget(scene);
            stand.kill();
            check(f, !CinematicTriggers.firstSight(level, a, titan, stand) && !Cinematics.state(a).seen(scene), titan + ": started for a dead titan");
            stand.discard();
        }
        // 余燼が地の下を泳いでいる (まだ浮かんでいない) あいだは、世界にいても見えない。場面は始まらない (ユーザーの声: 余燼がいないのに初見が始まった)
        Cinematics.state(a).forgetAll();
        var swimmer = jp.revati.skyabyss.titan.ember.EmberTitanSetup.EMBER_TITAN.get().create(level);
        swimmer.setPos(a.getX() + 200, a.getY(), a.getZ());
        check(f, !swimmer.isSurfaced() && !CinematicTriggers.firstSight(level, a, Titans.EMBER, swimmer) && !Cinematics.active(a)
                && !Cinematics.state(a).seen(Scenes.firstId(Titans.EMBER)), "ember first sight started while the titan was still underground");
        // 船に乗っていても見られる (海で会いに行く足)。馬など、ほかの乗り物では見せない。
        // 乗せる操作は環境で失敗しうるので、乗り物の判定 (vehicleAllows) を実体を世界に置かずに確かめ、徒歩の人は canWatch で確かめる
        var boat = new net.minecraft.world.entity.vehicle.Boat(net.minecraft.world.entity.EntityType.BOAT, level);
        var pig = new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG, level);
        check(f, CinematicTriggers.vehicleAllows(boat), "a player in a boat was not eligible for first sights");
        check(f, !CinematicTriggers.vehicleAllows(pig), "a player riding a pig was eligible for first sights");
        check(f, CinematicTriggers.vehicleAllows(null) && CinematicTriggers.canWatch(a), "a player on foot was not eligible for first sights");
        Cinematics.state(a).forgetAll();
        Cinematics.state(b).forgetAll();
    }
}

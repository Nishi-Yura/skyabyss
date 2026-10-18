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
        check(f, count == 10, "expected 10 scenes (opening, whale, 4 hearts, 4 dormancies), got " + count);
        Scene opening = Scenes.get(Scenes.OPENING);
        check(f, opening != null && opening.duration() <= 40f && opening.duration() >= 25f, "opening should be 25-40 s");
        check(f, opening != null && opening.intro() && opening.handBack() && opening.seamless(), "opening flags");
        Scene whale = Scenes.get(Scenes.WHALE_FIRST_SIGHT);
        check(f, whale != null && whale.duration() <= 25f, "whale first sight too long");
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

    private static JsonObject loadLang(String locale, List<String> f) {
        String path = "/assets/skyabyss_cinematic/lang/" + locale + ".json";
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
    }
}

package jp.revati.skyabyss.client.dev;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.NativeImage;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.dev.StructureDump;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 台本で撮るスクショ (開発時だけ)。環境変数 SKYABYSS_SHOTS=<台本ファイル> があるとき、ワールドに入ってから台本の手順を順に動かし、
// 最後にゲームを閉じる。キーやマウスの OS の入力は使わない (ほかの担当のウィンドウに入力が入る事故を起こさないため)。
// ウィンドウが前面になくても動く: 焦点が外れても止めない (pauseOnLostFocus を切り、勝手に開く一時停止の画面は開かせない)。
// 描画は Minecraft の中の描画先 (main render target) から読むので、ほかのウィンドウに隠れていても撮れる。最小化だけはしないこと。
// 出力先: 環境変数 SKYABYSS_SHOTS_OUT、無ければ <ゲームのフォルダ>/shots/<台本の名前>/ (png と shots.log)
// 起動: ./gradlew runShots -Pscript=tools/shots/<台本>.txt (ゲームのフォルダは run/shots/<台本の名前>/)。手順の一覧は tools/README.md
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ShotRunner {
    private static final String ENV = "SKYABYSS_SHOTS";
    private static ShotScript script;
    private static Path out;
    private static PrintWriter log;
    private static boolean worldRequested;
    private static boolean finished;
    private static int index;
    private static int stepTicks;
    private static long deadline;
    // 今の手順の状態
    private static CompletableFuture<Void> pending;
    private static String shotName;
    private static boolean shotDone;
    private static boolean perfOn;
    private static long perfFrames;
    private static long perfStartNanos;
    private static int lastSections = -1;
    private static int stableTicks;
    private static Vec3 cameraTarget;
    private static ResourceKey<Level> cameraDim;
    private static int shots;
    private static int failures;
    private static Screen confirmed;
    // 開発用の監視 (台本の watch on): 目的の帯の文・画面・知らせが変わるたびに、ゲーム内の時刻つきで記録に書く (first_hour.txt)
    private static boolean watching;
    private static String watchBand = null;
    private static String watchScreen = "";
    private static boolean watchAwake;
    private static boolean watchHorror;
    private static int watchNotices;
    private static final java.util.List<String> seenNotices = new java.util.ArrayList<>();
    private static int noticeMark;

    private ShotRunner() {
    }

    public static boolean active() {
        return script != null && !finished;
    }

    // 最初の画面の手前で台本を読む (FMLEnvironment で開発時だけ)
    private static boolean init() {
        if (script != null || finished) {
            return script != null;
        }
        String file = System.getenv(ENV);
        if (file == null || file.isBlank()) {
            return false;
        }
        if (FMLEnvironment.production) {
            SkyAbyss.LOGGER.warn("[shots] {} is ignored outside the development environment", ENV);
            finished = true;
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            Path path = Path.of(file).toAbsolutePath();
            script = ShotScript.load(path);
            String o = System.getenv("SKYABYSS_SHOTS_OUT");
            out = o != null && !o.isBlank() ? Path.of(o) : mc.gameDirectory.toPath().resolve("shots").resolve(script.name);
            out = out.toAbsolutePath().normalize();
            Files.createDirectories(out);
            log = new PrintWriter(Files.newBufferedWriter(out.resolve("shots.log"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), true);
            say("script " + path + " (" + script.steps.size() + " steps) -> " + out);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("[shots] cannot read the script {}", file, e);
            finished = true;
            script = null;
            mc.stop();
            return false;
        }
        deadline = Util.getMillis() + script.timeoutSeconds * 1000L;
        // 焦点が外れても止めない。最初の案内の画面も出さない
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        // 台本の撮影は音を鳴らさない (主音量 0。台本の options.txt に残っても、撮影のフォルダだけのこと)
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        // 撮影は日本語で (TestMute.japanese。SKYABYSS_LANG で変えられる)
        TestMute.japanese(mc);
        if (script.width > 0 && script.height > 0) {
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), script.width, script.height);
        }
        mc.getWindow().setTitle("skyabyss shots: " + script.name);
        return true;
    }

    private static void say(String line) {
        SkyAbyss.LOGGER.info("[shots] {}", line);
        if (log != null) {
            log.println(LocalTime.now().withNano(0) + " " + line);
        }
    }

    // ---------------------------------------------------------------- ワールドに入る
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!init() || finished) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen screen = event.getScreen();
        if (worldRequested) {
            // 入ろうとしたのにタイトル画面へ戻った: 世界を開けなかった (同じ台本のゲームが別に動いていて、世界が使われている など)
            if (screen instanceof TitleScreen && mc.level == null && mc.getSingleplayerServer() == null) {
                fail("could not enter the world '" + script.world + "' (is another game using the same folder? use -PshotsName=<other>)");
            } else if (mc.level == null) {
                say("screen while entering the world: " + screen.getClass().getName() + " \"" + screen.getTitle().getString() + "\"");
                // 「実験的な設定の世界」の確認 (巨神の背の次元がデータパックなので出る)。台本の世界なので、控えを取らずに進む
                if (screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen confirm && confirmed != screen) {
                    confirmed = screen;
                    try {
                        Field f = net.minecraft.client.gui.screens.BackupConfirmScreen.class.getDeclaredField("onProceed");
                        f.setAccessible(true);
                        var listener = (net.minecraft.client.gui.screens.BackupConfirmScreen.Listener) f.get(confirm);
                        mc.execute(() -> listener.proceed(false, false));
                        say("  proceeding without a backup");
                    } catch (ReflectiveOperationException e) {
                        fail("cannot get past " + screen.getTitle().getString() + ": " + e);
                    }
                }
            }
            return;
        }
        if (mc.level != null || mc.getSingleplayerServer() != null) {
            return;
        }
        // タイトル画面か、初回の案内の画面が出たら入る。ただし読み込みの幕 (LoadingOverlay) がまだ上がっている間は入らない
        // (幕の終わりの呼び出しの中で世界を作ると、世界の読み込みが幕の消えるのを待って止まる。onClientTick が幕が消えてから入る)
        if (screen instanceof TitleScreen || screen.getClass().getSimpleName().contains("Onboarding")) {
            if (mc.getOverlay() != null) {
                say("title screen while the loading overlay is still up; entering the world once it is gone");
                // 読み込みの幕 (NeoForgeLoadingOverlay) の描画の最中に世界へ入ると、世界の読み込みの待ちが幕の消えるのを待ったまま止まることがある
                // (幕の描き直しが入れ子になる)。幕が消えてから、毎 tick の側で入る
                enterWhenOverlayGone = true;
                return;
            }
            worldRequested = true;
            mc.execute(ShotRunner::enterWorld);
        } else {
            say("screen before the world: " + screen.getClass().getName());
        }
    }

    // 幕が消えてから世界へ入る (onScreenInit で幕が残っていたとき)
    private static boolean enterWhenOverlayGone;

    private static void enterWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (script.server != null) {
            // 専用サーバーへ入る (マルチの確かめ)。権限・コマンドはサーバー側 (rcon) から
            say("connecting to " + script.server);
            net.minecraft.client.multiplayer.resolver.ServerAddress address = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(script.server);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc, address,
                    new net.minecraft.client.multiplayer.ServerData("shots", script.server, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            return;
        }
        String name = script.world;
        boolean exists = mc.getLevelSource().levelExists(name);
        say((exists ? "opening world " : "creating world ") + name + (exists ? "" : " (" + script.worldType + ")"));
        if (exists) {
            mc.createWorldOpenFlows().openWorld(name, () -> fail("could not open the world " + name));
            return;
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
        // 世界の種は既定 20261016。環境変数 SKYABYSS_SHOTS_SEED で変えられる (別の地形で撮るとき)
        long seed = 20261016L;
        try {
            String fromEnv = System.getenv("SKYABYSS_SHOTS_SEED");
            if (fromEnv != null && !fromEnv.isBlank()) {
                seed = Long.parseLong(fromEnv.trim());
            }
        } catch (NumberFormatException ignored) {
            // 数でなければ既定のまま
        }
        WorldOptions options = new WorldOptions(seed, false, false);
        String type = script.worldType;
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options, registries -> switch (type) {
            case "normal" -> WorldPresets.createNormalWorldDimensions(registries);
            default -> registries.registryOrThrow(Registries.WORLD_PRESET)
                    .getHolderOrThrow(WorldPresets.FLAT)
                    .value().createWorldDimensions();
        }, new TitleScreen());
    }

    // 台本の途中で勝手に開く一時停止の画面は開かせない (ウィンドウの焦点が外れたときなど)
    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (active() && event.getNewScreen() instanceof PauseScreen && Minecraft.getInstance().level != null) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 毎 tick: 手順を進める
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (enterWhenOverlayGone && !worldRequested && mc.getOverlay() == null && mc.level == null) {
            enterWhenOverlayGone = false;
            worldRequested = true;
            enterWorld();
            return;
        }
        if (Util.getMillis() > deadline) {
            fail("timed out after " + script.timeoutSeconds + " s at step " + (index + 1));
            return;
        }
        if (!worldRequested && mc.level == null && mc.getSingleplayerServer() == null && mc.getOverlay() == null
                && (mc.screen instanceof TitleScreen || (mc.screen != null && mc.screen.getClass().getSimpleName().contains("Onboarding")))) {
            worldRequested = true;
            mc.execute(ShotRunner::enterWorld);
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (mc.screen != null && index == 0 && stepTicks == 0 && !(mc.screen instanceof PauseScreen)) {
            // 読み込みの画面が消えるまで待つ
            if (mc.screen.getClass().getSimpleName().contains("Receiving") || mc.screen.getClass().getSimpleName().contains("Progress")) {
                return;
            }
        }
        if (mc.options.pauseOnLostFocus) {
            mc.options.pauseOnLostFocus = false;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (watching) {
            watchTick(mc);
        }
        if (index >= script.steps.size()) {
            finish("SHOTS OK " + shots + " shots");
            return;
        }
        ShotScript.Line line = script.steps.get(index);
        boolean done;
        try {
            done = step(mc, line);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("[shots] step failed", e);
            fail("line " + line.number() + " (" + line.op() + " " + line.rest() + "): " + e);
            return;
        }
        stepTicks++;
        if (done) {
            index++;
            stepTicks = 0;
            pending = null;
            shotName = null;
            shotDone = false;
            lastSections = -1;
            stableTicks = 0;
            cameraTarget = null;
            cameraDim = null;
        }
    }

    private static boolean step(Minecraft mc, ShotScript.Line line) {
        boolean first = stepTicks == 0;
        if (first) {
            say("line " + line.number() + ": " + line.op() + (line.rest().isEmpty() ? "" : " " + line.rest()));
        }
        switch (line.op()) {
            case "marker" -> {
                // marker board|return <current|a|b|c> : 乗り込み口の目印 / 戻る光の見せ方を切り替える (選定用)
                // marker titan <id> : 戻る光の巨神の色・紋様 (背中の外で見るとき)
                // marker fake board <titan> x y z yaw : 偽の乗り込み口の目印を置く / marker fake return x y z : 偽の戻る光 / marker clear
                jp.revati.skyabyss.client.titan.MarkerArt.shotCommand(line.rest());
                return true;
            }
            case "stareffect" -> {
                // stareffect <current|a|b|c>: 星が欠ける見せ方を、設定を書き換えずに切り替える (選定用)
                jp.revati.skyabyss.client.opening.StarEffect.override =
                        jp.revati.skyabyss.Config.StarEffectVariant.valueOf(line.arg(0).toUpperCase(java.util.Locale.ROOT));
                return true;
            }
            case "wait" -> {
                return stepTicks + 1 >= line.intArg(0);
            }
            case "heartreward" -> {
                // heartreward <focus N|confirm|later|done> : 心臓の遺物の選択画面を、その見せ場まで進める (確かめ用。選ぶ決まりはサーバー)
                if (mc.screen instanceof jp.revati.skyabyss.client.reward.HeartRewardScreen screen) {
                    screen.devPhase(line.arg(0), line.rest().contains(" ") ? Integer.parseInt(line.arg(1)) : 0);
                }
                return true;
            }
            case "echo" -> {
                return true;
            }
            case "watch" -> {
                watching = line.arg(0).equals("on");
                if (watching) {
                    watchBand = null;
                    watchScreen = "";
                    watchAwake = false;
                    watchNotices = jp.revati.skyabyss.client.notice.NoticeLog.total();
                }
                return true;
            }
            case "waitgame" -> {
                // 総 tick (ゲーム内の通算。time set に左右されない) がその値になるまで待つ
                return mc.level.getGameTime() >= Long.parseLong(line.arg(0));
            }
            case "waitband" -> {
                // waitband <最大の待ち tick> <帯の文に含まれる語>: 帯がその語を出すまで待つ。来なければ記録に [gap] を残して先へ
                String want = line.rest().substring(line.rest().indexOf(' ') + 1);
                if (jp.revati.skyabyss.client.quest.ObjectiveStrip.shownNow().contains(want)) {
                    say("  [waited] band shows \"" + want + "\" after " + stepTicks + " client ticks, " + watchStamp(mc));
                    return true;
                }
                if (stepTicks >= line.intArg(0)) {
                    say("  [gap] band never showed \"" + want + "\" within " + stepTicks + " client ticks, " + watchStamp(mc)
                            + " (band: \"" + jp.revati.skyabyss.client.quest.ObjectiveStrip.shownNow() + "\")");
                    return true;
                }
                return false;
            }
            case "waitnotice" -> {
                if (first) {
                    noticeMark = seenNotices.size();
                }
                String want = line.rest().substring(line.rest().indexOf(' ') + 1);
                for (int i = noticeMark; i < seenNotices.size(); i++) {
                    if (seenNotices.get(i).contains(want)) {
                        say("  [waited] notice \"" + want + "\" after " + stepTicks + " client ticks, " + watchStamp(mc));
                        return true;
                    }
                }
                if (stepTicks >= line.intArg(0)) {
                    say("  [gap] notice \"" + want + "\" never came within " + stepTicks + " client ticks, " + watchStamp(mc));
                    return true;
                }
                return false;
            }
            case "waitscreen" -> {
                // waitscreen <最大の待ち tick> <none|画面のクラス名>
                String want = line.arg(1);
                boolean ok = want.equals("none") ? mc.screen == null : mc.screen != null && mc.screen.getClass().getSimpleName().equals(want);
                if (ok) {
                    say("  [waited] screen " + want + " after " + stepTicks + " client ticks, " + watchStamp(mc));
                    return true;
                }
                if (stepTicks >= line.intArg(0)) {
                    say("  [gap] screen " + want + " never came within " + stepTicks + " client ticks, " + watchStamp(mc));
                    return true;
                }
                return false;
            }
            case "band" -> {
                // いまの帯の文を一行記録する (変わっていなくても)
                say("  [band] " + watchStamp(mc) + " \"" + jp.revati.skyabyss.client.quest.ObjectiveStrip.shownNow() + "\"");
                return true;
            }
            case "waitfile" -> {
                // 外から (マルチの確かめの台本が) ファイルを置くまで待つ。同期用
                return Files.exists(Path.of(line.rest()));
            }
            case "signal" -> {
                try {
                    Path f = Path.of(line.rest());
                    Files.createDirectories(f.toAbsolutePath().getParent());
                    Files.writeString(f, "1");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return true;
            }
            case "cmd" -> {
                if (first) {
                    String command = line.rest().startsWith("/") ? line.rest().substring(1) : line.rest();
                    pending = onServer(mc, (server, player) -> {
                        int r = server.getCommands().getDispatcher().execute(command, source(server, player));
                        say("  -> result " + r);
                    }, command);
                }
                return pending.isDone();
            }
            case "camera" -> {
                // camera [次元] x y z yaw pitch : 体ごと置く (観戦の姿にしておくと壁を抜ける。cmd gamemode spectator)
                if (first) {
                    int o = line.args().length >= 6 ? 1 : 0;
                    ResourceKey<Level> dim = o == 1 ? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(line.arg(0))) : null;
                    double x = line.doubleArg(o), y = line.doubleArg(o + 1), z = line.doubleArg(o + 2);
                    float yaw = (float) line.doubleArg(o + 3), pitch = (float) line.doubleArg(o + 4);
                    cameraTarget = new Vec3(x, y, z);
                    cameraDim = dim != null ? dim : mc.level.dimension();
                    ResourceKey<Level> target = cameraDim;
                    pending = onServer(mc, (server, player) -> {
                        ServerLevel level = server.getLevel(target);
                        if (level == null) {
                            throw new IllegalArgumentException("no dimension " + target.location());
                        }
                        player.teleportTo(level, x, y, z, yaw, pitch);
                    }, "camera");
                }
                // 自分の側に届くまで待つ (次元を移るときは読み込みも)。着いた直後に重力で y がずれても着いたと見る (水平 0.6・縦 2)。
                // 初回は次元の読み込みと背の建設の最中で teleportTo が遅れるので、待ちは 1200 tick。100 tick 着かなければ置き直す
                double dx = mc.player.getX() - cameraTarget.x;
                double dz = mc.player.getZ() - cameraTarget.z;
                boolean there = pending.isDone() && mc.level.dimension() == cameraDim && dx * dx + dz * dz < 0.36
                        && Math.abs(mc.player.getY() - cameraTarget.y) < 2.0;
                if (!there && pending.isDone() && stepTicks > 0 && stepTicks % 100 == 0) {
                    double rx = cameraTarget.x, ry = cameraTarget.y, rz = cameraTarget.z;
                    float ryaw = mc.player.getYRot(), rpitch = mc.player.getXRot();
                    ResourceKey<Level> rd = cameraDim;
                    pending = onServer(mc, (server, player) -> {
                        ServerLevel lv = server.getLevel(rd);
                        if (lv != null) {
                            player.teleportTo(lv, rx, ry, rz, ryaw, rpitch);
                        }
                    }, "camera (retry)");
                }
                if (there) {
                    // 体の向きも視線に合わせる (そのままだと体が視線から最大 50 度ずれて、三人称の絵で武器や腕が斜めを向く)
                    mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.yHeadRot = mc.player.yHeadRotO = mc.player.getYRot();
                }
                if (stepTicks > 1200) {
                    throw new IllegalStateException("the camera did not arrive at " + cameraTarget + " in " + cameraDim.location() + " (now at "
                            + mc.player.position() + " in " + mc.level.dimension().location() + "; spectator or a short wait for chunks usually fixes it)");
                }
                return there;
            }
            case "look" -> {
                mc.player.setYRot((float) line.doubleArg(0));
                mc.player.setXRot((float) line.doubleArg(1));
                mc.player.yRotO = mc.player.getYRot();
                mc.player.xRotO = mc.player.getXRot();
                mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.yHeadRot = mc.player.yHeadRotO = mc.player.getYRot();
                return true;
            }
            case "waitchunks" -> {
                // 描く区画の数が 20 tick 変わらず、組み立ての列が空になるまで (最大 n tick、既定 600)
                int max = line.args().length > 0 ? line.intArg(0) : 600;
                int sections = mc.levelRenderer.countRenderedSections();
                if (sections == lastSections && mc.levelRenderer.hasRenderedAllSections()) {
                    stableTicks++;
                } else {
                    stableTicks = 0;
                }
                lastSections = sections;
                if (stepTicks >= max) {
                    say("  waitchunks gave up after " + max + " ticks (" + sections + " sections)");
                    return true;
                }
                // 移った直後は区画が 0 のまま落ち着いて見えるので、描く区画が出てから数える
                if (stableTicks >= 20 && stepTicks >= 30 && sections > 0) {
                    say("  " + sections + " sections after " + stepTicks + " ticks");
                    return true;
                }
                return false;
            }
            case "perf" -> {
                // perf <tick> [名前] : その間に描いた枚数から平均のフレーム時間を記録に書く (垂直同期を切り、上限を外す)。モデルの重さの比較用
                if (first) {
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(260);
                    perfFrames = 0;
                    perfStartNanos = System.nanoTime();
                    perfOn = true;
                }
                if (stepTicks + 1 >= line.intArg(0)) {
                    double ms = (System.nanoTime() - perfStartNanos) / 1.0e6 / Math.max(1, perfFrames);
                    say(String.format("  PERF %s: %.2f ms/frame (%.0f fps) over %d frames", line.args().length > 1 ? line.arg(1) : "", ms, 1000.0 / ms, perfFrames));
                    perfOn = false;
                    return true;
                }
                return false;
            }
            case "lowpoly" -> {
                // lowpoly on|off|debug|nodebug : 低ポリ描画の切り替え (off = 元のキューブ。debug = 骨の箱を半透明の赤で重ねる)
                switch (line.arg(0).toLowerCase(java.util.Locale.ROOT)) {
                    case "on" -> jp.revati.skyabyss.client.lowpoly.LowPoly.enabled = true;
                    case "off" -> jp.revati.skyabyss.client.lowpoly.LowPoly.enabled = false;
                    case "debug" -> jp.revati.skyabyss.client.lowpoly.LowPoly.debug = true;
                    case "armoroff" -> jp.revati.skyabyss.client.lowpoly.LowPolyArmor.enabled = false;
                    case "armoron" -> jp.revati.skyabyss.client.lowpoly.LowPolyArmor.enabled = true;
                    default -> jp.revati.skyabyss.client.lowpoly.LowPoly.debug = false;
                }
                return true;
            }
            case "skycircle" -> {
                // skycircle <x> <z> <半径> | off : 空の影の形を、決まった円 (縁は 10 ブロックでぼやける) にする。skypass で影を置いたあとに使う
                jp.revati.skyabyss.client.sky.SkyPassRenderer.testCircle = line.arg(0).equalsIgnoreCase("off") ? null
                        : new double[] {line.doubleArg(0), line.doubleArg(1), line.doubleArg(2)};
                return true;
            }
            case "skypass" -> {
                // skypass <x> <z> <dirX> <dirZ> | off : 動かない空の影 (サーバーなし。速さ 0) をこのクライアントにだけ置く。影の縁を決まった場所に置いて撮る用
                if (line.arg(0).equalsIgnoreCase("off")) {
                    jp.revati.skyabyss.client.sky.SkyPassClient.clear();
                } else {
                    jp.revati.skyabyss.client.sky.SkyPassClient.receive(new jp.revati.skyabyss.sky.SkyPass(mc.level.getGameTime(),
                            line.doubleArg(0), line.doubleArg(1), (float) line.doubleArg(2), (float) line.doubleArg(3), 0f, 1000000));
                }
                return true;
            }
            case "overheadshadow" -> {
                // overheadshadow on|off : 上から落ちる影 (空の影・巨神の影) を画素ごとの影 (OverheadShadow) にする / 昔の地面の板と屋内の弱めに戻す (前後の比べ用)
                jp.revati.skyabyss.client.sky.OverheadShadow.enabled = !line.arg(0).equalsIgnoreCase("off");
                return true;
            }
            case "titanweight" -> {
                // titanweight on|off : 巨神の重さの見せ方 (下ほど暗い陰・穴の蓋・地面や海面の影・雲や水しぶき) を入れる / 外す (前後の比べ用)
                jp.revati.skyabyss.client.lowpoly.LowPoly.titanWeight = !line.arg(0).equalsIgnoreCase("off");
                return true;
            }
            case "armortier" -> {
                // armortier common|rare|legend|auto : 低ポリ防具の階級を決め打つ (品は全部レア。auto = 品の階級)
                String t = line.arg(0).toLowerCase(java.util.Locale.ROOT);
                jp.revati.skyabyss.client.lowpoly.LowPolyArmor.forcedTier = t.equals("auto") ? null : t;
                return true;
            }
            case "sneak" -> {
                // sneak on|off : しゃがむキーを押し続ける / 放す (防具の姿勢の確認用。KeyMapping だけ。OS の入力ではない)
                mc.options.keyShift.setDown(line.arg(0).equalsIgnoreCase("on"));
                return true;
            }
            case "walk" -> {
                // walk on|off : 前進キーを押し続ける / 放す (歩く姿の確認用)
                mc.options.keyUp.setDown(line.arg(0).equalsIgnoreCase("on"));
                return true;
            }
            case "armorlevel" -> {
                // armorlevel <0..10|auto> : 低ポリ防具の強化段階を決め打つ (auto = 品の強化段階)
                jp.revati.skyabyss.client.lowpoly.LowPolyArmor.forcedLevel = line.arg(0).equalsIgnoreCase("auto") ? -1 : line.intArg(0);
                return true;
            }
            case "weaponmesh" -> {
                // weaponmesh on|off : プレイヤーの武器を低ポリ (on) / 元のボクセル (off) で描く
                jp.revati.skyabyss.client.lowpoly.LowPoly.weaponMeshes = !line.arg(0).equalsIgnoreCase("off");
                return true;
            }
            case "weapontier" -> {
                // weapontier common|rare|legend|off : 武器のメッシュの階級を固定する (品の階級を使うのは off)
                jp.revati.skyabyss.client.weapon.WeaponItemRenderer.setTier(line.arg(0));
                return true;
            }
            case "acctier" -> {
                // acctier common|rare|legend|off : 装飾品のメッシュの階級を固定する (品の階級を使うのは off)
                String t = line.arg(0).toLowerCase(java.util.Locale.ROOT);
                jp.revati.skyabyss.client.lowpoly.LowPoly.accessoryTier = t.equals("off") ? null : t;
                return true;
            }
            case "accmesh" -> {
                // accmesh on|off : 装飾品を低ポリ (on) / 元の平絵 (off) で描く (手・地面・額縁)
                jp.revati.skyabyss.client.lowpoly.LowPoly.accessoryMeshes = !line.arg(0).equalsIgnoreCase("off");
                return true;
            }
            case "accworn" -> {
                // accworn on|off : 体に付けた装飾の描画 (AccessoryWornLayer) の入り切り
                jp.revati.skyabyss.client.lowpoly.LowPoly.accessoryWorn = !line.arg(0).equalsIgnoreCase("off");
                return true;
            }
            case "accequip" -> {
                // accequip <装飾の id> [<装飾の id>] : 自分のクライアントの装飾の枠 (1・2 番) に品を入れる (見た目の確認用。サーバーには送らない)。引数なし = 空にする
                jp.revati.skyabyss.item.accessory.AccessoryData data = mc.player.getData(jp.revati.skyabyss.item.accessory.AccessorySetup.DATA);
                for (int i = 1; i < jp.revati.skyabyss.item.accessory.AccessoryData.SIZE; i++) {
                    data.items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
                for (int i = 0; i < Math.min(2, line.args().length); i++) {
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            ResourceLocation.fromNamespaceAndPath("skyabyss", line.arg(i)));
                    data.items.set(1 + i, new net.minecraft.world.item.ItemStack(item));
                }
                return true;
            }
            case "watcher" -> {
                // watcher x y z yaw : 見る者 (クライアントだけの人影) を足元 (x, y, z)・体の向き yaw に置く。消えずに立つ (確かめ用)
                if (first) {
                    jp.revati.skyabyss.client.prologue.WatcherClient.placeForShot(line.doubleArg(0), line.doubleArg(1), line.doubleArg(2),
                            (float) line.doubleArg(3));
                }
                return true;
            }
            case "hud" -> {
                mc.options.hideGui = line.arg(0).equalsIgnoreCase("off");
                return true;
            }
            case "huddemo" -> {
                // huddemo <all|opening|back|boss|off> : 画面の部品をいっぺんに出す (client/hud/HudDemo)
                jp.revati.skyabyss.client.hud.HudDemo.set(line.arg(0));
                return true;
            }
            case "perspective" -> {
                // perspective first|back|front : 視点 (一人称 / 三人称の背中側 / 三人称の正面)
                mc.options.setCameraType(switch (line.arg(0).toLowerCase(java.util.Locale.ROOT)) {
                    case "first" -> net.minecraft.client.CameraType.FIRST_PERSON;
                    case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                    default -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                });
                return true;
            }
            case "bodyyaw" -> {
                // bodyyaw <度> : 体の向きを視線から何度ずらすか (斜めから見た絵用。人は視線と体が 50 度までしかずれない)
                float body = mc.player.getYRot() + (float) line.doubleArg(0);
                mc.player.yBodyRot = body;
                mc.player.yBodyRotO = body;
                mc.player.yHeadRot = mc.player.getYRot();
                mc.player.yHeadRotO = mc.player.getYRot();
                return true;
            }
            case "swingpose" -> {
                // swingpose <0..1|off> : 武器を振る途中の姿で止める (一人称・三人称とも)。武器の動きの確認用
                jp.revati.skyabyss.client.weapon.WeaponClient.forcedSwing = line.arg(0).equalsIgnoreCase("off") ? -1f : (float) line.doubleArg(0);
                return true;
            }
            case "swingclock" -> {
                // swingclock on|off : 武器の振りの長さ (攻撃速度で決まる) の入り切り。off = 昔のバニラの 6 tick (変更前後の比べ用)
                jp.revati.skyabyss.client.weapon.SwingClock.legacy = line.arg(0).equalsIgnoreCase("off");
                return true;
            }
            case "swing" -> {
                // swing : 利き手を一振りする (止めずに動かす)
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return true;
            }
            case "mainhand" -> {
                // mainhand left|right : 利き手の設定 (左利きの鏡写しの確認用)
                mc.options.mainHand().set(line.arg(0).equalsIgnoreCase("left") ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT);
                mc.options.broadcastOptions();
                return true;
            }
            case "use" -> {
                // use : 右クリックを押し続ける (弓を引く姿の確認用。KeyMapping を押したことにするだけで、OS の入力ではない)
                mc.options.keyUse.setDown(true);
                return true;
            }
            case "stopuse" -> {
                // stopuse : 右クリックを放す (引いていた弓は矢を放つ)
                mc.options.keyUse.setDown(false);
                return true;
            }
            case "fov" -> {
                mc.options.fov().set(line.intArg(0));
                return true;
            }
            case "clouds" -> {
                // clouds on|off : 雲 (上から撮るとき邪魔になる)
                mc.options.cloudStatus().set(line.arg(0).equalsIgnoreCase("off") ? net.minecraft.client.CloudStatus.OFF : net.minecraft.client.CloudStatus.FANCY);
                return true;
            }
            case "render" -> {
                // render <チャンク> : 描く距離 (統合サーバーの見える距離もこれに合わせて変わる)
                mc.options.renderDistance().set(line.intArg(0));
                // 統合サーバーが各プレイヤーに送る範囲 (エンティティの追跡) は、プレイヤーの申告 (ClientInformation) で決まる。設定を変えただけでは
                // 申告が古い (既定の 12) ままで、約 190 ブロックより遠い巨神が消えるので、ここで申告し直す
                mc.options.broadcastOptions();
                return true;
            }
            case "guiscale" -> {
                mc.options.guiScale().set(line.intArg(0));
                mc.resizeDisplay();
                return true;
            }
            case "chat" -> {
                // chat clear : チャットの欄と右上の通知 (トースト) を消す
                mc.gui.getChat().clearMessages(false);
                mc.getToasts().clear();
                jp.revati.skyabyss.client.notice.NoticeClient.clear();
                return true;
            }
            case "open" -> {
                Screen screen = screen(mc, line.arg(0));
                mc.setScreen(screen);
                return true;
            }
            case "key" -> {
                // key <キーの名前> : そのキーを押したことにする (mod のキーの KeyMapping。OS の入力ではない)。例 key key.skyabyss.record
                press(mc, line.arg(0));
                return true;
            }
            case "close" -> {
                mc.setScreen(null);
                return true;
            }
            case "shot" -> {
                if (first) {
                    shotName = line.arg(0).replaceAll("[^A-Za-z0-9_.-]", "_");
                    shotDone = false;
                }
                // 撮るのは描き終えたとき (onRenderFrame)。1 枚描いてから撮る
                return shotDone;
            }
            case "waitdump" -> {
                if (stepTicks > 20 * 600) {
                    throw new IllegalStateException("the structure dump did not finish");
                }
                return stepTicks > 2 && !StructureDump.busy();
            }
            case "quit" -> {
                finish("SHOTS OK " + shots + " shots (quit)");
                return false;
            }
            default -> throw new IllegalArgumentException("unknown step " + line.op());
        }
    }

    // ゲーム内の時刻 (総 tick・日・一日の時刻)。時刻を飛ばした (time set) 台本でも、どの日のいつかが分かる
    private static String watchStamp(Minecraft mc) {
        long game = mc.level.getGameTime();
        long dayTime = mc.level.getDayTime();
        return "game=" + game + " (" + (game / 1200) + "m" + String.format("%02d", (game / 20) % 60) + "s) day=" + (dayTime / 24000L) + " tod=" + (dayTime % 24000L);
    }

    private static void watchTick(Minecraft mc) {
        String band = jp.revati.skyabyss.client.quest.ObjectiveStrip.shownNow();
        if (!band.equals(watchBand)) {
            say("[watch] " + watchStamp(mc) + " band " + (band.isEmpty() ? "(none)" : "\"" + band + "\""));
            watchBand = band;
        }
        boolean awake = jp.revati.skyabyss.client.opening.OpeningClient.awakeActive();
        if (awake != watchAwake) {
            say("[watch] " + watchStamp(mc) + " awake-scene " + (awake ? "start" : "end"));
            watchAwake = awake;
        }
        boolean horror = jp.revati.skyabyss.client.opening.OpeningClient.horrorCueActive();
        if (horror != watchHorror) {
            say("[watch] " + watchStamp(mc) + " horror-cue (footsteps / song) " + (horror ? "start" : "end"));
            watchHorror = horror;
        }
        String screen = mc.screen == null ? "" : mc.screen.getClass().getSimpleName();
        if (!screen.equals(watchScreen)) {
            say("[watch] " + watchStamp(mc) + " screen " + (screen.isEmpty() ? "closed" : screen));
            watchScreen = screen;
        }
        int total = jp.revati.skyabyss.client.notice.NoticeLog.total();
        if (total > watchNotices) {
            var entries = jp.revati.skyabyss.client.notice.NoticeLog.entries();
            for (int i = Math.min(total - watchNotices, entries.size()) - 1; i >= 0; i--) {
                var e = entries.get(i);
                seenNotices.add(e.text().getString());
                say("[watch] " + watchStamp(mc) + " notice " + e.kind() + " \"" + e.text().getString().replace('\n', '/') + "\"");
            }
            watchNotices = total;
        }
    }

    // ---------------------------------------------------------------- 撮る (描き終えたあと、画面へ出す前)
    @SubscribeEvent
    static void onRenderFrame(RenderFrameEvent.Post event) {
        if (perfOn) {
            perfFrames++;
        }
        if (!active() || shotName == null || shotDone || stepTicks < 2) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Path file = out.resolve(shotName + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(file);
            shots++;
            say("  saved " + file.getFileName() + " (" + image.getWidth() + "x" + image.getHeight() + ")");
        } catch (IOException e) {
            failures++;
            say("  could not save " + file + ": " + e);
        }
        shotDone = true;
    }

    // ---------------------------------------------------------------- 部品
    private interface ServerAction {
        void run(MinecraftServer server, ServerPlayer player) throws Exception;
    }

    // 統合サーバーで動かす。終わったら完了になる
    private static CompletableFuture<Void> onServer(Minecraft mc, ServerAction action, String what) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("no integrated server");
        }
        java.util.UUID id = mc.player.getUUID();
        CompletableFuture<Void> f = new CompletableFuture<>();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) {
                    throw new IllegalStateException("the player is not on the server yet");
                }
                action.run(server, player);
            } catch (Exception e) {
                failures++;
                say("  " + what + " failed: " + e.getMessage());
            }
            f.complete(null);
        });
        return f;
    }

    // コマンドの出元: プレイヤーの位置と向き、権限 4。返事はチャットではなく shots.log へ (撮る絵に字が入らないように)
    private static CommandSourceStack source(MinecraftServer server, ServerPlayer player) {
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                say("  > " + message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return new CommandSourceStack(sink, player.position(), new Vec2(player.getXRot(), player.getYRot()), player.serverLevel(), 4,
                player.getName().getString(), player.getDisplayName(), server, player);
    }

    // 開ける画面の名前。足りなければ、画面のクラスの完全な名前 (引数なしで作れるもの) でもよい
    private static Screen screen(Minecraft mc, String name) {
        Supplier<Screen> s = switch (name.toLowerCase(Locale.ROOT)) {
            case "inventory" -> () -> new InventoryScreen(mc.player);
            case "lore", "lorebook" -> jp.revati.skyabyss.lore.client.LoreBookScreen::new;
            case "quests", "questlog" -> jp.revati.skyabyss.client.quest.QuestLogScreen::new;
            case "growth" -> jp.revati.skyabyss.rpg.client.GrowthScreen::new;
            case "skills" -> jp.revati.skyabyss.rpg.client.skill.SkillScreen::new;
            case "party" -> jp.revati.skyabyss.party.client.PartyScreen::new;
            case "notices", "noticelog" -> jp.revati.skyabyss.client.notice.NoticeLogScreen::new;
            case "grave" -> jp.revati.skyabyss.client.grave.GraveScreen.Sample::new;
            default -> null;
        };
        if (s != null) {
            return s.get();
        }
        try {
            return (Screen) Class.forName(name).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("unknown screen '" + name + "' (use an alias or a screen class with a no-arg constructor; "
                    + "screens that need server data open with 'key <key name>' or 'cmd ...')");
        }
    }

    private static void press(Minecraft mc, String name) {
        for (KeyMapping k : mc.options.keyMappings) {
            if (k.getName().equals(name)) {
                try {
                    Field f = KeyMapping.class.getDeclaredField("clickCount");
                    f.setAccessible(true);
                    f.setInt(k, f.getInt(k) + 1);
                } catch (ReflectiveOperationException e) {
                    KeyMapping.click(k.getKey());
                }
                return;
            }
        }
        throw new IllegalArgumentException("no key mapping named " + name);
    }

    private static void fail(String why) {
        failures++;
        finish("SHOTS FAILED: " + why);
    }

    // 終わる: 結果を書いて、ゲームを閉じる (ワールドは閉じるときに保存される)。閉じきらなければ 90 秒後に強制終了
    private static void finish(String result) {
        if (finished) {
            return;
        }
        finished = true;
        if (failures > 0 && result.startsWith("SHOTS OK")) {
            result = "SHOTS FAILED: " + failures + " step(s) failed, " + shots + " shots";
        }
        say(result);
        if (log != null) {
            log.close();
        }
        Thread guard = new Thread(() -> {
            try {
                Thread.sleep(90_000);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "skyabyss-shots-exit");
        guard.setDaemon(true);
        guard.start();
        Minecraft.getInstance().stop();
    }
}

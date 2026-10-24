package jp.revati.skyabyss.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.hud.HudLayout;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.hud.HudLayout.Request;
import jp.revati.skyabyss.hud.HudLayout.Result;
import jp.revati.skyabyss.hud.HudLayout.Scene;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

// mod の HUD の置き場の司令塔 (docs/design/18 の 14 章)。毎フレームの最初 (RenderGuiEvent.Pre) に、
//   1. バニラの状態 (画面の大きさ・利き手・オフハンド・体力の段・効き目・ボスバー・札・チャット) を集め、
//   2. 各描画層に「いま出したい部品」の要求 (名前・寸法の決め方) を出させ、
//   3. 純粋な計算 (HudLayout.compute) で長方形を割り当てる。
// 各描画層は自分の座標を持たず、rect(Id) でもらった長方形の中に描く (もらえなければ出さない = 順番待ち・縮小・脱落は計算が決める)。
// 計算が重ねることはない (HudLayoutSelfTest が倍率 1〜4 × 代表的な窓で確かめる)。要求の出し口はここに一覧してある
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class HudManager {
    static final boolean ENABLED = true;
    private static Result current = HudLayout.compute(HudLayout.Scene.plain(427, 240), List.of());
    private static int frame;
    // ボスバーの下端 (その枠の描画中に記す。次の枠の計算に使う。一枠ぶん遅れるだけ)
    private static int bossBottomNow;
    private static int bossBottom;
    // 右上の札 (トースト) の高さの合計 (描画中に数え、次の枠で使う)。出入りで板が跳ねないよう、なめらかに寄せる
    private static int toastNow;
    private static int toastTopNow = Integer.MAX_VALUE;
    private static int toastTop;
    private static float toastEased;
    private static long lastChatMs = -100000;

    private HudManager() {
    }

    // ------------------------------------------------------------ 問い合わせ
    // 割り当てられた長方形 (出さないことになった部品は null。描いてはいけない)
    @Nullable
    public static Rect rect(Id id) {
        return current.rect(id);
    }

    public static boolean placed(Id id) {
        return current.has(id);
    }

    public static Result result() {
        return current;
    }

    public static int frame() {
        return frame;
    }

    // ボスバーを描いた分の下端を記す (複数なら一番下)。NoticeClient のボスバーの事象から
    public static void noteBossBottom(int y) {
        bossBottomNow = Math.max(bossBottomNow, y);
    }

    // 右上の札を描くとき、その下端 (札の枠の位置 + 高さを 32 の枠に丸めたもの) を記す (NoticeToast / QuestToast / LoreToast が呼ぶ)。
    // 札は空いた枠を飛ばさずに積まれるとは限らない (先の札が消えても、あとの札はその枠のまま) ので、一番下の札の下端を使う
    public static void noteToast(net.minecraft.client.gui.GuiGraphics g, int height) {
        int top = Math.round(g.pose().last().pose().m31()) - VanillaShift.toastShift();
        toastNow = Math.max(toastNow, Math.max(0, top) + Math.max(32, (height + 31) / 32 * 32));
        toastTopNow = Math.min(toastTopNow, Math.max(0, top));
    }

    // 戦いの最中か: ボスバーが出ている・直近 10 秒に敵から傷を受けた。操作の手がかり (手引き・キーの案内) は、戦いの最中は出さない (見せ場の邪魔をしない)
    public static boolean inCombat() {
        if (HudDemo.on()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) {
            return false;
        }
        return bossBottom > 0 || p.hurtTime > 0 || (p.getLastHurtByMobTimestamp() > 0 && p.tickCount - p.getLastHurtByMobTimestamp() < 200);
    }

    // 右上の札が積まれている下端 (なめらかにした値)
    public static int toastHeight() {
        return Math.round(toastEased);
    }

    public static int lastBossBottom() {
        return bossBottom;
    }

    // 画面の外の矢印 (仲間・墓の標) の置き場: 向き (vx, vy) の縁の点。板の上は避けて縁を伝う
    public static int[] edgePoint(double vx, double vy) {
        return HudLayout.edgePoint(current, vx, vy);
    }

    // ------------------------------------------------------------ F1 (hideGui) の間
    // mod の層は、いずれも自分で hideGui を見る。取りこぼし (題字の層など) を一か所で止める。画面の暗転 (carry_fade・abyss_flash) は話の演出なので残す
    private static final Set<String> KEEP_WHEN_HIDDEN = Set.of("carry_fade", "abyss_flash");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        ResourceLocation name = event.getName();
        if (Minecraft.getInstance().options.hideGui && SkyAbyss.MODID.equals(name.getNamespace()) && !KEEP_WHEN_HIDDEN.contains(name.getPath())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------ 計算
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onFrame(RenderGuiEvent.Pre event) {
        long t = jp.revati.skyabyss.dev.Prof.begin();
        onFrame0(event);
        jp.revati.skyabyss.dev.Prof.end("client.hudLayout", t);
    }

    private static void onFrame0(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        frame++;
        bossBottom = bossBottomNow;
        bossBottomNow = 0;
        // 札の高さをなめらかに (出るときは速く、消えるときはゆっくり)
        float target = toastNow;
        toastNow = 0;
        toastTop = toastTopNow == Integer.MAX_VALUE ? 0 : toastTopNow;
        toastTopNow = Integer.MAX_VALUE;
        toastEased += (target - toastEased) * (target > toastEased ? 1f : 0.15f);
        if (Math.abs(toastEased - target) < 0.5f) {
            toastEased = target;
        }
        int w = event.getGuiGraphics().guiWidth();
        int h = event.getGuiGraphics().guiHeight();
        if (!ENABLED) {
            return;
        }
        // F1・場面 (カメラの演出) の間は mod の部品を全部出さない
        if (mc.player == null || mc.level == null || mc.options.hideGui || jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            lastScene = null;
            current = HudLayout.compute(Scene.plain(w, h), List.of());
            return;
        }
        List<Request> requests = new ArrayList<>();
        HudRequests.collect(mc, w, h, requests);
        // 計算は入力 (場面と要求の並び) だけで決まる純粋な関数。入力が前の枠と同じなら前の結果をそのまま使う
        Scene sc = scene(mc, w, h);
        if (!sc.equals(lastScene) || !requests.equals(lastRequests)) {
            current = HudLayout.compute(sc, requests);
            lastScene = sc;
            lastRequests = requests;
        }
        applyChatWidth(mc);
    }

    // チャットの幅の上限が変わったら、いまある文を折り返し直す (新しい文は mixin が細くした幅で折り返す)。出入りで何度も走らないよう、15 枠あけて
    private static int appliedChatWidth = -1;
    private static int lastRescaleFrame;

    private static void applyChatWidth(Minecraft mc) {
        int cw = current.chatWidth;
        if (cw != appliedChatWidth && frame - lastRescaleFrame >= 15) {
            appliedChatWidth = cw;
            lastRescaleFrame = frame;
            mc.gui.getChat().rescaleChat();
        }
    }

    private static Scene lastScene;
    private static List<Request> lastRequests = List.of();

    @SubscribeEvent
    static void onChat(ClientChatReceivedEvent event) {
        long now = net.minecraft.Util.getMillis();
        lastChatMs = now;
        // 行数を数える (置き場の計算に使うのは、出ている行の分の高さだけ。欄の高さ全部ではない)
        Minecraft mc = Minecraft.getInstance();
        int lines = 1;
        try {
            var chat = mc.gui.getChat();
            lines = net.minecraft.client.gui.components.ComponentRenderUtils
                    .wrapComponents(event.getMessage(), net.minecraft.util.Mth.floor(chat.getWidth() / chat.getScale()), mc.font).size();
        } catch (RuntimeException e) {
            // 数えられなければ一行
        }
        RECENT_CHAT.add(new long[] {now, Math.max(1, lines)});
    }

    private static java.lang.reflect.Field trimmedField;
    private static boolean trimmedBroken;

    // チャットのいま出ている (200 tick 以内の) 行数。数えられなければ fallback
    private static int shownChatLines(Minecraft mc, int fallback) {
        if (trimmedBroken) {
            return fallback;
        }
        try {
            if (trimmedField == null) {
                trimmedField = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("trimmedMessages");
                trimmedField.setAccessible(true);
            }
            int ticks = mc.gui.getGuiTicks();
            int n = 0;
            for (Object o : (java.util.List<?>) trimmedField.get(mc.gui.getChat())) {
                if (ticks - ((net.minecraft.client.GuiMessage.Line) o).addedTime() >= 200) {
                    break;
                }
                n++;
            }
            return Math.max(1, n);
        } catch (ReflectiveOperationException | RuntimeException e) {
            trimmedBroken = true;
            return fallback;
        }
    }

    // 直近 10 秒のチャットの (時刻, 行数)
    private static final java.util.ArrayList<long[]> RECENT_CHAT = new java.util.ArrayList<>();

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        bossBottom = 0;
        bossBottomNow = 0;
        toastEased = 0;
        toastNow = 0;
    }

    // スコアボードの横板 (右の中ほど) の寸法の見積り: {幅, 高さ}。出ていなければ {0, 0}。バニラの描き方 (行数 × 9 + 題) に合わせる
    private static int[] sidebar(Minecraft mc) {
        try {
            net.minecraft.world.scores.Scoreboard board = mc.level.getScoreboard();
            net.minecraft.world.scores.Objective obj = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
            if (obj == null) {
                return new int[] {0, 0};
            }
            var font = mc.font;
            int wide = font.width(obj.getDisplayName());
            int n = 0;
            for (var score : board.listPlayerScores(obj)) {
                if (score.isHidden() || n >= 15) {
                    continue;
                }
                n++;
                wide = Math.max(wide, font.width(score.ownerName().getString()) + 2 + font.width(Integer.toString(score.value())));
            }
            return new int[] {wide + 4, (n + 1) * 9 + 1};
        } catch (RuntimeException e) {
            return new int[] {0, 0};
        }
    }

    // アクションバー (画面下の一行) が出ているときの文の幅。出ていなければ 0。Gui の持つ文を読む (読めなければ 0)
    private static java.lang.reflect.Field overlayField;
    private static java.lang.reflect.Field overlayTimeField;
    private static boolean overlayBroken;

    private static int overlayWidth(Minecraft mc) {
        if (overlayBroken) {
            return 0;
        }
        try {
            if (overlayField == null) {
                overlayField = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
                overlayField.setAccessible(true);
                overlayTimeField = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageTime");
                overlayTimeField.setAccessible(true);
            }
            Object text = overlayField.get(mc.gui);
            int time = overlayTimeField.getInt(mc.gui);
            if (text instanceof net.minecraft.network.chat.Component c && time > 0) {
                return Math.min(mc.font.width(c), 400);
            }
            return 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            overlayBroken = true;
            return 0;
        }
    }

    static Scene scene(Minecraft mc, int w, int h) {
        Player p = mc.player;
        boolean rightHanded = mc.options.mainHand().get() == HumanoidArm.RIGHT;
        boolean offhand = !p.getOffhandItem().isEmpty();
        boolean attack = mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR;
        float hearts = p.getMaxHealth() + p.getAbsorptionAmount();
        // 体力の帯 (VitalsHud) は何ハートでも一本。バニラのハートに戻した人だけ、段が増えるぶん上へ避ける
        int rows = VitalsHud.styled() ? 1 : Math.max(1, Math.min(4, (int) Math.ceil(hearts / 2f / 10f)));
        int good = 0;
        int bad = 0;
        for (MobEffectInstance e : p.getActiveEffects()) {
            if (!e.showIcon()) {
                continue;
            }
            if (e.getEffect().value().isBeneficial()) {
                good++;
            } else {
                bad++;
            }
        }
        int cols = Math.max(good, bad);
        int effectRows = bad > 0 ? 2 : good > 0 ? 1 : 0;
        int chatW = 0;
        int chatH = 0;
        long nowMs = net.minecraft.Util.getMillis();
        RECENT_CHAT.removeIf(e -> nowMs - e[0] >= 10000);
        // チャットの欲しい幅は設定の幅 (HudLayout が細くしたあとの幅ではない。mixin で細くした getWidth() は使わない)
        int wantW = net.minecraft.client.gui.components.ChatComponent.getWidth(mc.options.chatWidth().get());
        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
            chatW = wantW;
            chatH = mc.gui.getChat().getHeight();
        } else if (!RECENT_CHAT.isEmpty()) {
            int lines = 0;
            for (long[] e : RECENT_CHAT) {
                lines += (int) e[1];
            }
            // 実際に出ている行 (折り返し直したあとの数) が数えられるなら、そちらを使う
            lines = shownChatLines(mc, lines);
            chatW = wantW;
            chatH = Math.min(mc.gui.getChat().getHeight(), lines * 9 + 2);
        }
        int[] sb = sidebar(mc);
        return new Scene(w, h, rightHanded, offhand, attack, rows, cols, effectRows, bossBottom, Math.round(toastEased), chatW, chatH, sb[0], sb[1], mc.options.showSubtitles().get(), overlayWidth(mc), toastTop);
    }
}

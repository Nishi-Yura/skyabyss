package jp.revati.skyabyss.client.hud;

import java.util.ArrayList;
import java.util.List;

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

// mod の HUD の置き場の司令塔 (docs/design/18 の 9 章)。毎フレームの最初 (RenderGuiEvent.Pre) に、
//   1. バニラの状態 (画面の大きさ・利き手・オフハンド・体力の段・効き目・ボスバー・札・チャット) を集め、
//   2. 各描画層に「いま出したい部品」の要求 (名前・寸法の決め方) を出させ、
//   3. 純粋な計算 (HudLayout.compute) で長方形を割り当てる。
// 各描画層は自分の座標を持たず、rect(Id) でもらった長方形の中に描く (もらえなければ出さない = 順番待ち・縮小・脱落は計算が決める)。
// 計算が重ねることはない (HudLayoutSelfTest が倍率 1〜4 × 代表的な窓で確かめる)。要求の出し口はここに一覧してある
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class HudManager {
    // 描画層の置き換えが済むまでは false (旧い置き場のまま)
    static final boolean ENABLED = false;
    private static Result current = HudLayout.compute(HudLayout.Scene.plain(427, 240), List.of());
    private static int frame;
    // ボスバーの下端 (その枠の描画中に記す。次の枠の計算に使う。一枠ぶん遅れるだけ)
    private static int bossBottomNow;
    private static int bossBottom;
    // 右上の札 (トースト) の高さの合計 (描画中に数え、次の枠で使う)。出入りで板が跳ねないよう、なめらかに寄せる
    private static int toastNow;
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

    // 右上の札を描くとき、その高さを数える (NoticeToast / QuestToast / LoreToast が呼ぶ)
    public static void noteToast(int height) {
        toastNow += Math.max(32, (height + 31) / 32 * 32);
    }

    public static int lastBossBottom() {
        return bossBottom;
    }

    // 画面の外の矢印 (仲間・墓の標) の置き場: 向き (vx, vy) の縁の点。板の上は避けて縁を伝う
    public static int[] edgePoint(double vx, double vy) {
        return HudLayout.edgePoint(current, vx, vy);
    }

    // ------------------------------------------------------------ 計算
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onFrame(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        frame++;
        bossBottom = bossBottomNow;
        bossBottomNow = 0;
        // 札の高さをなめらかに (出るときは速く、消えるときはゆっくり)
        float target = toastNow;
        toastNow = 0;
        toastEased += (target - toastEased) * (target > toastEased ? 0.5f : 0.15f);
        if (Math.abs(toastEased - target) < 0.5f) {
            toastEased = target;
        }
        int w = event.getGuiGraphics().guiWidth();
        int h = event.getGuiGraphics().guiHeight();
        if (!ENABLED) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            current = HudLayout.compute(Scene.plain(w, h), List.of());
            return;
        }
        List<Request> requests = new ArrayList<>();
        HudRequests.collect(mc, w, h, requests);
        current = HudLayout.compute(scene(mc, w, h), requests);
    }

    @SubscribeEvent
    static void onChat(ClientChatReceivedEvent event) {
        lastChatMs = net.minecraft.Util.getMillis();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        bossBottom = 0;
        bossBottomNow = 0;
        toastEased = 0;
        toastNow = 0;
    }

    static Scene scene(Minecraft mc, int w, int h) {
        Player p = mc.player;
        boolean rightHanded = mc.options.mainHand().get() == HumanoidArm.RIGHT;
        boolean offhand = !p.getOffhandItem().isEmpty();
        boolean attack = mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR;
        float hearts = p.getMaxHealth() + p.getAbsorptionAmount();
        int rows = Math.max(1, Math.min(4, (int) Math.ceil(hearts / 2f / 10f)));
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
        if (net.minecraft.Util.getMillis() - lastChatMs < 10000) {
            chatW = mc.gui.getChat().getWidth();
            chatH = mc.gui.getChat().getHeight();
        }
        return new Scene(w, h, rightHanded, offhand, attack, rows, cols, effectRows, bossBottom, Math.round(toastEased), chatW, chatH);
    }
}

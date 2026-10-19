package jp.revati.skyabyss.client.guide;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.opening.OpeningClient;
import jp.revati.skyabyss.client.quest.ObjectiveStrip;
import jp.revati.skyabyss.guide.nav.GuideNetwork;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// 目的の道しるべのクライアント側 (docs/design/18 の 8.3 節)。
//  - 10 tick ごとに「いまの目的の場所」を決め、変わったとき (と 5 秒ごと) サーバーへ知らせる。サーバーが歩ける道を探して、その人にだけ返す
//  - 返ってきた道 (Trail) を覚える。目印と合わない・古い・道なしのときは、地面の破線 (まっすぐ 16 ブロック) に落とす
//  - 設定 questTrail (ON / NEAR / OFF) と、すばやく切り替えるキー (;)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GuideClient {
    // NEAR のとき道を出す、目的までの水平距離
    public static final double NEAR_RANGE = 64.0;
    private static final int CHECK_TICKS = 10;
    private static final int HEARTBEAT_TICKS = 100;
    // これだけ前の道は古い (ms)
    private static final long STALE_MS = 25_000L;

    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.skyabyss.guide_trail", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, "key.categories.skyabyss");

    // サーバーから届いた道。nodes は出発点から目印の方へ (空 = 道なし or 着いた)
    public record Trail(BlockPos target, boolean found, BlockPos[] nodes, long at) {
    }

    private static volatile Trail trail;
    private static volatile BlockPos goal;
    private static BlockPos sentTarget;
    private static boolean sentActive;
    private static int sentAge;

    private GuideClient() {
    }

    public static void receive(GuideNetwork.GuideTrail payload) {
        trail = new Trail(payload.target(), payload.found(), payload.nodes().toArray(new BlockPos[0]), Util.getMillis());
    }

    // いま道しるべが向かう場所 (なければ null)
    @Nullable
    public static BlockPos goal() {
        return goal;
    }

    // 目印に合った新しい道 (なければ null → 破線)
    @Nullable
    public static Trail trailFor(BlockPos target) {
        Trail t = trail;
        if (t == null || !t.found() || t.nodes().length < 2 || Util.getMillis() - t.at() > STALE_MS || t.target().distSqr(target) > 16) {
            return null;
        }
        return t;
    }

    // 道しるべを描いてよい場面か (場面・画面・F1 では隠す)
    public static boolean visible(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.level.dimension() == Level.OVERWORLD && mc.screen == null && !mc.options.hideGui
                && !OpeningClient.awakeActive();
    }

    public static boolean trailEnabled() {
        return Config.QUEST_TRAIL.get() != Config.TrailMode.OFF;
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                Config.TrailMode next = Config.QUEST_TRAIL.get() == Config.TrailMode.OFF ? Config.TrailMode.ON : Config.TrailMode.OFF;
                Config.QUEST_TRAIL.set(next);
                Config.QUEST_TRAIL.save();
                mc.player.displayClientMessage(Component.translatable(next == Config.TrailMode.OFF ? "gui.skyabyss.guide.trail_off" : "gui.skyabyss.guide.trail_on"), true);
            }
        }
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            goal = null;
            trail = null;
            sentActive = false;
            sentTarget = null;
            return;
        }
        if (mc.player.tickCount % CHECK_TICKS != 0) {
            return;
        }
        BlockPos target = decide(mc);
        goal = target;
        boolean active = target != null && wantsTrail(mc, target);
        sentAge += CHECK_TICKS;
        if (!active) {
            if (sentActive) {
                PacketDistributor.sendToServer(new GuideNetwork.GuideTarget(false, sentTarget != null ? sentTarget : BlockPos.ZERO));
                sentActive = false;
                sentTarget = null;
                trail = null;
            }
            return;
        }
        if (!sentActive || sentTarget == null || sentTarget.distSqr(target) > 4 || sentAge >= HEARTBEAT_TICKS) {
            PacketDistributor.sendToServer(new GuideNetwork.GuideTarget(true, target));
            sentActive = true;
            sentTarget = target;
            sentAge = 0;
        }
    }

    // いまの目的の地上の場所: 帯の目的 (序盤・メイン) → 追跡中のクエスト
    @Nullable
    private static BlockPos decide(Minecraft mc) {
        ObjectiveStrip.Resolved r = ObjectiveStrip.resolve(mc);
        if (r.pos() != null) {
            return r.pos();
        }
        if (r.inside()) {
            return null;
        }
        return ObjectiveStrip.resolveTracked(mc).pos();
    }

    private static boolean wantsTrail(Minecraft mc, BlockPos target) {
        Config.TrailMode mode = Config.QUEST_TRAIL.get();
        if (mode == Config.TrailMode.OFF || mc.level.dimension() != Level.OVERWORLD) {
            return false;
        }
        if (mode == Config.TrailMode.NEAR) {
            double dx = target.getX() + 0.5 - mc.player.getX();
            double dz = target.getZ() + 0.5 - mc.player.getZ();
            return dx * dx + dz * dz <= NEAR_RANGE * NEAR_RANGE;
        }
        return true;
    }
}

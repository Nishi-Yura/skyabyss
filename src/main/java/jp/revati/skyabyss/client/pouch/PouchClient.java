package jp.revati.skyabyss.client.pouch;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchNet;
import jp.revati.skyabyss.pouch.PouchSetup;
import jp.revati.skyabyss.pouch.PouchUsable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

// 旅嚢のクライアント側 (docs/design/22 の 10 章):
//   キー Y (既定): 長押し (0.2 秒以上) で輪を開き、向きで選んで離すと使う。短く押すと前回の道具をすぐ使う。道具が一つだけなら輪を出さずに使う
//   キー I (既定): 旅嚢の画面を開く
// 使う処理はサーバーが決める (PouchNet.Use → PouchUsable.usePouch。右クリックと同じ中身)。ここは輪の絵と選ぶだけ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PouchClient {
    public static final KeyMapping USE_KEY = new KeyMapping("skyabyss_pouch.key.use", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.skyabyss");
    public static final KeyMapping OPEN_KEY = new KeyMapping("skyabyss_pouch.key.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.skyabyss");
    // 長押しとみなす長さ (tick。4 = 0.2 秒)
    public static final int HOLD_TICKS = 4;

    // 押している長さ (tick)。画面が開いている間は 0
    private static int held;
    // 前回使った道具 (短押しで使う)
    @Nullable
    private static ResourceLocation last;

    private PouchClient() {
    }

    @SubscribeEvent
    static void onScreens(RegisterMenuScreensEvent event) {
        event.register(PouchSetup.MENU.get(), PouchScreen::new);
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(USE_KEY);
        event.register(OPEN_KEY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            held = 0;
            return;
        }
        while (OPEN_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new PouchNet.Open());
        }
        if (USE_KEY.isDown()) {
            held++;
            if (held == HOLD_TICKS) {
                openWheel(mc);
            }
        } else {
            if (held > 0 && held < HOLD_TICKS) {
                shortPress(mc);
            }
            held = 0;
        }
    }

    // 短押し: 前回の道具 (なければ使える品の先頭) をすぐ使う
    private static void shortPress(Minecraft mc) {
        List<PouchEntries.Entry> usable = PouchUsable.usable(mc.player);
        if (usable.isEmpty()) {
            none(mc);
            return;
        }
        ResourceLocation id = usable.get(0).id();
        for (PouchEntries.Entry e : usable) {
            if (e.id().equals(last)) {
                id = last;
            }
        }
        use(id);
    }

    // 長押し: 輪を開く。道具が一つだけなら輪を出さずに使う
    private static void openWheel(Minecraft mc) {
        List<PouchEntries.Entry> usable = PouchUsable.usable(mc.player);
        if (usable.isEmpty()) {
            none(mc);
        } else if (usable.size() == 1) {
            use(usable.get(0).id());
        } else {
            mc.setScreen(new PouchWheelScreen(usable));
        }
    }

    private static void none(Minecraft mc) {
        mc.gui.setOverlayMessage(Component.translatable("skyabyss_pouch.wheel.none"), false);
    }

    // 道具を使う (サーバーへ)。前回の道具として覚える
    static void use(ResourceLocation id) {
        last = id;
        PacketDistributor.sendToServer(new PouchNet.Use(Optional.of(id)));
    }

    @Nullable
    static ResourceLocation last() {
        return last;
    }
}

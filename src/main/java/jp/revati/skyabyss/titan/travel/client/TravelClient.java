package jp.revati.skyabyss.titan.travel.client;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import jp.revati.skyabyss.titan.travel.TravelTitan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 巨神での移動のクライアント側: 海図を開く、雲の帳 (画面が雲の白に包まれる)、乗っているあいだの風
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TravelClient {
    // 雲の帳の濃さ (0〜1) と、目指す濃さ
    private static float veil;
    private static float veilO;
    private static boolean veilOn;
    // 帳が下りたままになるのを防ぐ (終わりの知らせが届かなかったとき)
    private static int veilTicks;
    @Nullable
    private static RideWind wind;

    private TravelClient() {
    }

    public static void openChart(TravelNetwork.Chart chart) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TravelChartScreen screen) {
            screen.update(chart);
        } else {
            mc.setScreen(new TravelChartScreen(chart));
        }
    }

    // 帳の色: 0 = 雲の白 (岩鯨)、1 = 潮の深みの青 (潮殻)
    private static int veilStyle;

    public static void veil(boolean on, int style) {
        veilOn = on;
        veilTicks = 0;
        if (on) {
            veilStyle = style;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        veilO = veil;
        if (veilOn && ++veilTicks > 20 * 20) {
            veilOn = false;
        }
        veil = Mth.clamp(veil + (veilOn ? 0.05f : -0.05f), 0f, 1f);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            veil = 0;
            veilOn = false;
            wind = null;
            return;
        }
        Entity vehicle = player.getVehicle();
        boolean riding = vehicle instanceof TravelTitan;
        if (riding && (wind == null || wind.isStopped())) {
            wind = new RideWind(player);
            mc.getSoundManager().play(wind);
        }
        if (!riding) {
            wind = null;
        }
    }

    private static float veilAmount(float partialTick) {
        return Mth.lerp(partialTick, veilO, veil);
    }

    // 雲の帳: 画面を雲の白で覆う (下は少し青緑に)
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        float a = veilAmount(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (a <= 0.01f) {
            return;
        }
        var g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        boolean sea = veilStyle == 1;
        int top = ((int) (a * 235) << 24) | (sea ? 0x1D5C6E : 0xEEF4F2);
        int bottom = ((int) (a * 245) << 24) | (sea ? 0x0A2234 : 0xC9DEDB);
        g.fillGradient(0, 0, w, h, top, bottom);
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        float a = veilAmount((float) event.getPartialTick());
        if (a <= 0.01f) {
            return;
        }
        event.setNearPlaneDistance(Mth.lerp(a, event.getNearPlaneDistance(), 0f));
        event.setFarPlaneDistance(Mth.lerp(a, event.getFarPlaneDistance(), 6f));
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float a = veilAmount((float) event.getPartialTick());
        if (a <= 0.01f) {
            return;
        }
        boolean sea = veilStyle == 1;
        event.setRed(Mth.lerp(a, event.getRed(), sea ? 0.08f : 0.92f));
        event.setGreen(Mth.lerp(a, event.getGreen(), sea ? 0.30f : 0.95f));
        event.setBlue(Mth.lerp(a, event.getBlue(), sea ? 0.40f : 0.94f));
    }

    // 乗り場で受ける風。巨神が速く泳ぐほど強く、高く鳴る
    private static final class RideWind extends AbstractTickableSoundInstance {
        private final LocalPlayer player;

        RideWind(LocalPlayer player) {
            super(ModSounds.TITAN_WIND.get(), SoundSource.AMBIENT, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            Entity vehicle = player.getVehicle();
            if (player.isRemoved() || !(vehicle instanceof TravelTitan)) {
                stop();
                return;
            }
            double speed = vehicle.getDeltaMovement().length();
            double dx = vehicle.getX() - vehicle.xo;
            double dz = vehicle.getZ() - vehicle.zo;
            speed = Math.max(speed, Math.sqrt(dx * dx + dz * dz));
            float target = (float) Mth.clamp(0.15 + speed * 0.5, 0.15, 0.9);
            volume = Mth.lerp(0.05f, volume, target);
            pitch = (float) Mth.clamp(0.8 + speed * 0.2, 0.8, 1.15);
        }
    }
}

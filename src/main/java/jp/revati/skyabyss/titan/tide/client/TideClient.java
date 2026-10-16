package jp.revati.skyabyss.titan.tide.client;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.TideSetup;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 潮殻の巨神のクライアント側。描画の登録、潮門をくぐるときの「水に沈む」画面、背中の島の波の音
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TideClient {
    // 沈んでいる時間 (tick) と、残り
    private static int plungeTotal;
    private static int plungeLeft;
    private static int plungeLeftO;
    @Nullable
    private static Waves waves;

    private TideClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TideSetup.TIDE_SHELL.get(), TideShellRenderer::new);
    }

    // 門をくぐった: 画面を水の色に沈め、ticks のあいだに薄れて戻る (前半は沈んだまま)
    public static void plunge(int ticks) {
        plungeTotal = Math.max(10, ticks);
        plungeLeft = plungeTotal;
        plungeLeftO = plungeTotal;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        plungeLeftO = plungeLeft;
        if (plungeLeft > 0) {
            plungeLeft--;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean onBack = mc.level != null && mc.level.dimension() == TideTravel.TIDE_BACK;
        if (onBack && (waves == null || waves.isStopped())) {
            waves = new Waves();
            mc.getSoundManager().play(waves);
        } else if (!onBack) {
            waves = null;
        }
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        if (plungeLeft <= 0 && plungeLeftO <= 0) {
            return;
        }
        float left = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(true), plungeLeftO, plungeLeft);
        float t = left / plungeTotal;
        // 前半 (t > 0.5) は沈んだまま、後半で薄れる
        float a = Mth.clamp(t * 2f, 0f, 1f);
        if (a <= 0.01f) {
            return;
        }
        var g = event.getGuiGraphics();
        int top = ((int) (a * 230) << 24) | 0x2E8C8A;
        int bottom = ((int) (a * 245) << 24) | 0x0E3F4A;
        g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), top, bottom);
    }

    // 背中の島に寄せる波 (12 秒で切れ目なくループする)。背中にいるあいだだけ鳴らす
    private static final class Waves extends AbstractTickableSoundInstance {
        Waves() {
            super(TideSetup.BACK_WAVES.get(), SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.5f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.level.dimension() != TideTravel.TIDE_BACK) {
                stop();
            }
        }
    }
}

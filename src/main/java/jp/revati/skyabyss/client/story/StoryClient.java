package jp.revati.skyabyss.client.story;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.StorySetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 天の鍵を手に持っているあいだ、ときどき足もとのずっと下で何かが打つ (遠い鼓動、小さく)。
// オーバーワールドの地上でだけ。鍵から光の粒が一つ、下へ落ちる。2 章 (深淵の心臓) へのほのめかし
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StoryClient {
    private static final ResourceLocation DEEP_BEAT = ResourceLocation.fromNamespaceAndPath("skyabyss_story", "deep_beat");
    private static final int PERIOD = 20 * 14;
    private static int held;

    private StoryClient() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused() || player.level().dimension() != Level.OVERWORLD) {
            held = 0;
            return;
        }
        boolean holding = player.getMainHandItem().is(StorySetup.SKY_KEY.get()) || player.getOffhandItem().is(StorySetup.SKY_KEY.get());
        if (!holding) {
            held = 0;
            return;
        }
        held++;
        if (held % 40 == 0) {
            player.level().addParticle(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 0, -0.08, 0);
        }
        if (held % PERIOD == 60 && !(mc.screen instanceof SkyKeyScene)) {
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvent.createVariableRangeEvent(DEEP_BEAT), SoundSource.AMBIENT, 0.35f, 1f,
                    player.getRandom(), player.getX(), player.getY() - 12, player.getZ()));
        }
    }
}

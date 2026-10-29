package jp.revati.skyabyss.client.music;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.prologue.WatcherClient;
import jp.revati.skyabyss.client.sky.CloudEyeClient;
import jp.revati.skyabyss.client.sky.SkyPassClient;
import jp.revati.skyabyss.mixin.BossHealthOverlayAccessor;
import jp.revati.skyabyss.music.MusicTracks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

// BGM を選んで流す (docs/design/49)。マインクラフトの曲は流さない (SelectMusicEvent で止める)。
// 選び方 (上ほど強い):
//   1. タイトル画面 → title
//   2. 見る者・空の通過・雲の目の間は無音 (ホラーの引き算。01 の演出の原則)
//   3. 曲を流すボスの帯 (setPlayBossMusic) が出ている → 心臓・心臓の番人なら heart、ほかは boss
//   4. ディメンション: 深淵 → abyss、月 → moon、巨神の背 (4 つ) → back
//   5. 地上: サーバーが知らせる場所 (MusicArea) → 都・町・土地の曲 (MusicTracks.forArea)
// 曲が替わるときは前の曲を消しながら次の曲を入れる。同じ曲のままなら続ける
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MusicDirector {
    static final int FADE_IN = 50;
    static final int FADE_OUT = 40;
    static final int FADE_OUT_FAST = 15;
    static final int RETRY = 100;

    private static String area = "";
    private static LoopMusic current;
    private static final List<LoopMusic> FADING = new ArrayList<>();
    private static int retryAt;
    private static int ticks;
    private static String debugOverride;

    private MusicDirector() {
    }

    public static void receive(String a) {
        area = a == null ? "" : a;
    }

    // 開発用 (/skyabyss dev music 相当。撮影の台本からも): 曲を決め打ちにする。null で戻す
    public static void override(String id) {
        debugOverride = id;
    }

    // 確かめ用の一行 (撮影の台本の music で記録に出す)
    public static String debugLine() {
        Minecraft mc = Minecraft.getInstance();
        return String.format("playing=%s active=%s volume=%.2f fading=%d area=%s want=%s", playing(),
                current != null && mc.getSoundManager().isActive(current), current == null ? 0f : current.getVolume(), FADING.size(), area,
                desired(mc));
    }

    public static String playing() {
        return current == null ? "" : current.id;
    }

    // マインクラフトの曲は使わない
    @SubscribeEvent
    static void onSelectMusic(SelectMusicEvent event) {
        event.setMusic(null);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        area = "";
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        String want = debugOverride != null ? (debugOverride.isEmpty() ? null : debugOverride) : desired(mc);
        if (current != null && !mc.getSoundManager().isActive(current) && !current.fadingOut()) {
            // 音の仕組みの読み直しや /stopsound で止まった: しばらくして入れ直す
            current = null;
            retryAt = ticks + RETRY;
        }
        FADING.removeIf(m -> !mc.getSoundManager().isActive(m));
        if (current != null && current.id.equals(want)) {
            return;
        }
        if (current != null) {
            boolean fast = "boss".equals(want) || "heart".equals(want);
            current.fadeTo(0f, fast ? FADE_OUT_FAST : FADE_OUT);
            FADING.add(current);
            current = null;
        }
        if (want == null || ticks < retryAt) {
            return;
        }
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.get(MusicTracks.location(want));
        if (ev == null) {
            return;
        }
        LoopMusic m = new LoopMusic(want, ev);
        m.fadeTo(1f, FADE_IN);
        mc.getSoundManager().play(m);
        current = m;
    }

    static String desired(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return mc.screen != null ? "title" : null;
        }
        if (WatcherClient.hushesMusic() || SkyPassClient.hushesMusic() || CloudEyeClient.hushesMusic()) {
            return null;
        }
        String boss = bossTrack(mc);
        if (boss != null) {
            return boss;
        }
        ResourceLocation dim = mc.level.dimension().location();
        if (SkyAbyss.MODID.equals(dim.getNamespace())) {
            return switch (dim.getPath()) {
                case "abyss" -> "abyss";
                case "moon" -> "moon";
                case "titan_back", "tide_back", "storm_back", "ember_back" -> "back";
                default -> null;
            };
        }
        if (mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            return MusicTracks.forArea(area);
        }
        return null;
    }

    static String bossTrack(Minecraft mc) {
        String out = null;
        for (LerpingBossEvent e : ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).skyabyss$events().values()) {
            if (!e.shouldPlayBossMusic()) {
                continue;
            }
            if (e.getName().getContents() instanceof TranslatableContents t && isHeart(t.getKey())) {
                return "heart";
            }
            out = "boss";
        }
        return out;
    }

    // 巨神の心臓 4 つと、深淵の心臓の番人
    static boolean isHeart(String key) {
        return key.endsWith("_heart") || key.endsWith("heart_warden");
    }
}

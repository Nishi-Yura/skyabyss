package jp.revati.skyabyss.client.ui;

import java.io.Reader;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;

// バニラの画面の絵柄 (mod に同梱のリソースパック skyabyss_ui_a/b/c。tools/ui/gen_vanilla_skin.py が作る)。
// 絵柄のパックが入っていれば assets/skyabyss/ui/skin.json の色で、箱の画面の見出しの文字 (バニラの 0x404040) とツールチップを描く。
// はじめて起動したときに A (暗い板) を選んだ状態にする (設定 uiSkinInstalled。リソースパックの画面で外せる・B と C に替えられる)
public final class UiSkin {
    private UiSkin() {
    }

    public static final String[] PACKS = {"skyabyss_ui_a", "skyabyss_ui_b", "skyabyss_ui_c"};
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ui/skin.json");

    private static volatile boolean active;
    private static volatile int label = 0x404040;
    private static volatile int tipBg = 0xF0100010;
    private static volatile int tipEdge = 0x505000FF;
    private static volatile int tipEdge2 = 0x5028007F;

    public static boolean active() {
        return active;
    }

    // 箱の画面の見出し (バニラの濃い灰色) を絵柄の色に
    public static int label(int color) {
        if (!active || (color & 0xFFFFFF) != 0x404040) {
            return color;
        }
        return (color & 0xFF000000) | (label & 0xFFFFFF);
    }

    static void load(ResourceManager rm) {
        Optional<Resource> r = rm.getResource(SKIN);
        if (r.isEmpty()) {
            active = false;
            return;
        }
        try (Reader in = r.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            label = o.get("label").getAsInt();
            tipBg = (int) o.get("tooltip_bg").getAsLong();
            tipEdge = (int) o.get("tooltip_edge").getAsLong();
            tipEdge2 = (int) o.get("tooltip_edge2").getAsLong();
            active = true;
        } catch (Exception e) {
            SkyAbyss.LOGGER.warn("UI skin: could not read {}: {}", SKIN, e.toString());
            active = false;
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        static void onPacks(AddPackFindersEvent event) {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) {
                return;
            }
            String[] names = {"天と淵: 画面の絵柄 A 暗い板", "天と淵: 画面の絵柄 B 帳面", "天と淵: 画面の絵柄 C 黒い石と金"};
            for (int i = 0; i < PACKS.length; i++) {
                event.addPackFinders(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "resourcepacks/" + PACKS[i]), PackType.CLIENT_RESOURCES,
                        Component.literal(names[i]), PackSource.BUILT_IN, false, Pack.Position.TOP);
            }
        }

        @SubscribeEvent
        static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) UiSkin::load);
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        // はじめての起動: 絵柄 A を選んだ状態にする (一度だけ。外したらそのまま)
        @SubscribeEvent
        static void onTitle(ScreenEvent.Init.Post event) {
            if (!(event.getScreen() instanceof TitleScreen) || Config.UI_SKIN_INSTALLED.get()) {
                return;
            }
            Config.UI_SKIN_INSTALLED.set(true);
            Config.UI_SKIN_INSTALLED.save();
            select(PACKS[0]);
        }

        private static boolean envDone;

        // 撮影・確かめ用: 環境変数 SKYABYSS_UI_SKIN=a|b|c|none で絵柄を選ぶ (最初の tick で一度)
        @SubscribeEvent
        static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (envDone) {
                return;
            }
            envDone = true;
            String env = System.getenv("SKYABYSS_UI_SKIN");
            if (env == null || env.isBlank()) {
                return;
            }
            if (env.equals("none")) {
                Minecraft mc = Minecraft.getInstance();
                PackRepository repo = mc.getResourcePackRepository();
                List<String> sel = new java.util.ArrayList<>(repo.getSelectedIds());
                if (sel.removeIf(x -> java.util.Arrays.stream(PACKS).anyMatch(x::endsWith))) {
                    repo.setSelected(sel);
                    mc.options.updateResourcePacks(repo);
                }
                return;
            }
            select("skyabyss_ui_" + env);
        }

        @SubscribeEvent
        static void onTooltipColor(RenderTooltipEvent.Color event) {
            if (!active) {
                return;
            }
            event.setBackground(tipBg);
            event.setBorderStart(tipEdge2);
            event.setBorderEnd(tipEdge);
        }
    }

    // 絵柄のパックを一つ選ぶ (ほかの絵柄は外す)。開発・撮影用にも使う
    public static void select(String pack) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        String id = null;
        for (String a : repo.getAvailableIds()) {
            if (a.endsWith(pack)) {
                id = a;
            }
        }
        if (id == null) {
            SkyAbyss.LOGGER.warn("UI skin: pack {} not found in {}", pack, repo.getAvailableIds());
            return;
        }
        List<String> sel = new java.util.ArrayList<>(repo.getSelectedIds());
        sel.removeIf(s -> java.util.Arrays.stream(PACKS).anyMatch(s::endsWith));
        sel.add(id);
        repo.setSelected(sel);
        mc.options.updateResourcePacks(repo);
    }
}

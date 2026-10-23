package jp.revati.skyabyss.client.ui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;

// mod の字 (mod に同梱のリソースパック skyabyss_font。tools/gen/ui/gen_fonts.py が作る)。
//   本文の字: DotGothic16。パックが minecraft:default の日本語 (Unifont) を差し替えるので、バニラの画面も含めて日本語の字が変わる (英数はバニラのまま)
//   題字の字: Zen Antique (skyabyss:title)。章の題字・場所の名前・余韻の札だけに title() で掛ける
// はじめて起動したときにパックを選んだ状態にする (設定 fontInstalled)。リソースパックの画面で外すとバニラの字に戻り、
// そのときは skyabyss:title もバニラの字になる (mod 本体の assets/skyabyss/font/title.json は minecraft:default を指すだけ)
public final class ModFont {
    private ModFont() {
    }

    public static final String PACK = "skyabyss_font";
    // 題字の字は描く大きさで三つ (細かさが違う。tools/gen/ui/gen_fonts.py)。2 倍より大きく描く所は TITLE、1.5 倍は TITLE_MID、1〜1.2 倍は TITLE_SMALL
    public static final ResourceLocation TITLE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "title");
    public static final ResourceLocation TITLE_MID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "title_mid");
    public static final ResourceLocation TITLE_SMALL = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "title_small");

    // 題字の字を掛ける。scale = 描くときの拡大。字が太いので太字は外す (重ね描きで潰れる)。
    // 文の中の飾り (引数・兄弟の文) にも掛けるため、書式ごとの切れ端に分けて付け直す。色などほかの書式はそのまま
    public static MutableComponent title(Component c, float scale) {
        ResourceLocation font = scale >= 2f ? TITLE : scale >= 1.4f ? TITLE_MID : TITLE_SMALL;
        MutableComponent out = Component.empty();
        c.visit((style, text) -> {
            out.append(Component.literal(text).withStyle(style.withFont(font).withBold(false)));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    public static MutableComponent title(String s, float scale) {
        return title(Component.literal(s), scale);
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
            event.addPackFinders(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "resourcepacks/" + PACK), PackType.CLIENT_RESOURCES,
                    Component.literal("天と淵: mod の字 (DotGothic16・Zen Antique)"), PackSource.BUILT_IN, false, Pack.Position.TOP);
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        // はじめての起動: 字のパックを選んだ状態にする (一度だけ。外したらそのまま)。
        // 撮影・確かめ用: 環境変数 SKYABYSS_FONT=none でバニラの字のまま始める
        @SubscribeEvent
        static void onTitle(ScreenEvent.Init.Post event) {
            if (!(event.getScreen() instanceof TitleScreen) || Config.FONT_INSTALLED.get()) {
                return;
            }
            Config.FONT_INSTALLED.set(true);
            Config.FONT_INSTALLED.save();
            if (!"none".equals(System.getenv("SKYABYSS_FONT"))) {
                select();
            }
        }

        private static boolean envDone;
        private static int ticks;

        // 撮影・確かめ用: 環境変数 SKYABYSS_FONT=on で字のパックを選ぶ (最初の tick で一度。撮影はタイトル画面を通らないことがある)
        // 字のパックが入っているかは 20 tick おきに見直す (描く所から毎回は見ない)
        @SubscribeEvent
        static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!envDone) {
                envDone = true;
                if ("on".equals(System.getenv("SKYABYSS_FONT"))) {
                    select();
                }
            }
            if (ticks++ % 20 == 0) {
                refreshActive();
            }
        }
    }

    private static volatile boolean active;

    // mod の字のパックが選ばれているか (斜体を外すかの判断に使う。ItalicStyle)
    public static boolean active() {
        return active;
    }

    static void refreshActive() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourcePackRepository() == null) {
                return;
            }
            active = mc.getResourcePackRepository().getSelectedIds().stream().anyMatch(s -> s.endsWith(PACK));
        } catch (RuntimeException e) {
            active = false;
        }
    }

    static void select() {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        String id = null;
        for (String a : repo.getAvailableIds()) {
            if (a.endsWith(PACK)) {
                id = a;
            }
        }
        if (id == null) {
            SkyAbyss.LOGGER.warn("Font: pack {} not found in {}", PACK, repo.getAvailableIds());
            return;
        }
        List<String> sel = new java.util.ArrayList<>(repo.getSelectedIds());
        if (sel.contains(id)) {
            return;
        }
        sel.add(id);
        repo.setSelected(sel);
        mc.options.updateResourcePacks(repo);
    }
}

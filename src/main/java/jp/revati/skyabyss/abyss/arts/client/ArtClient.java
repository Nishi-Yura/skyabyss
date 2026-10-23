package jp.revati.skyabyss.abyss.arts.client;

import java.util.Arrays;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.arts.ArtData;
import jp.revati.skyabyss.abyss.arts.net.ArtCastPayload;
import jp.revati.skyabyss.abyss.arts.net.ArtCooldownPayload;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

// 深淵術の鍵・左の表示 (docs/design/36 の 5.5)。覚えていない・待ち・使える を一行ずつ。鍵はサーバーへ送るだけ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ArtClient {
    public static final KeyMapping[] KEYS = new KeyMapping[AbyssArt.values().length];

    static {
        for (AbyssArt a : AbyssArt.values()) {
            KEYS[a.ordinal()] = new KeyMapping("key.skyabyss.abyss_art_" + a.id, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, a.key, "key.categories.skyabyss");
        }
    }

    // 待ちの終わり (ゲームの tick)。サーバーから届く
    private static final long[] UNTIL = new long[AbyssArt.values().length];

    private ArtClient() {
    }

    public static void onCooldown(ArtCooldownPayload p) {
        if (p.art() >= 0 && p.art() < UNTIL.length) {
            UNTIL[p.art()] = p.until();
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Arrays.fill(UNTIL, 0L);
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : KEYS) {
            event.register(k);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        for (AbyssArt a : AbyssArt.values()) {
            while (KEYS[a.ordinal()].consumeClick()) {
                PacketDistributor.sendToServer(new ArtCastPayload(a.ordinal()));
            }
        }
        // しゃがみ + ジャンプ = 重さ (深淵の中。押した瞬間だけ)
        boolean combo = mc.options.keyShift.isDown() && mc.options.keyJump.isDown();
        if (combo && !comboHeld && canContext(mc, AbyssArt.WEIGHT)) {
            PacketDistributor.sendToServer(new ArtCastPayload(AbyssArt.WEIGHT.ordinal()));
        }
        comboHeld = combo;
    }

    private static boolean comboHeld;

    // 場面の操作で使えるか: 深淵の中・覚えている・待ちでない (使えないときは今までの操作のまま)
    private static boolean canContext(Minecraft mc, AbyssArt a) {
        if (mc.player == null || mc.level == null || mc.level.dimension() != AbyssWorld.LEVEL || mc.player.isSpectator()) {
            return false;
        }
        return AbyssArts.data(mc.player).rank(a) > 0 && UNTIL[a.ordinal()] <= mc.level.getGameTime();
    }

    // 右クリック = 光、しゃがみ + 右クリック = 鼓動。素手か武器を持っているとき、何もない所か何も起きない壁・床に向けたときだけ
    // (扉・箱・人・使える道具の右クリックはそのまま)
    @SubscribeEvent
    static void onInteract(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || mc.player == null || mc.screen != null) {
            return;
        }
        AbyssArt a = mc.player.isShiftKeyDown() ? AbyssArt.HEARTBEAT : AbyssArt.LIGHT;
        if (!canContext(mc, a) || !plainHands(mc) || !plainTarget(mc)) {
            return;
        }
        PacketDistributor.sendToServer(new ArtCastPayload(a.ordinal()));
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static boolean plainHands(Minecraft mc) {
        net.minecraft.world.item.ItemStack main = mc.player.getMainHandItem();
        if (!mc.player.getOffhandItem().isEmpty()) {
            return false;
        }
        if (main.isEmpty()) {
            return true;
        }
        // 武器 (攻撃力のある品) で、右クリックの使い道がないもの
        if (main.getItem() instanceof jp.revati.skyabyss.pouch.PouchUsable || main.getUseAnimation() != net.minecraft.world.item.UseAnim.NONE
                || main.getItem() instanceof net.minecraft.world.item.BlockItem) {
            return false;
        }
        boolean[] weapon = {false};
        main.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                weapon[0] = true;
            }
        });
        return weapon[0];
    }

    private static boolean plainTarget(Minecraft mc) {
        net.minecraft.world.phys.HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return true;
        }
        if (hit instanceof net.minecraft.world.phys.BlockHitResult b) {
            net.minecraft.world.level.block.state.BlockState st = mc.level.getBlockState(b.getBlockPos());
            return !(st.getBlock() instanceof net.minecraft.world.level.block.EntityBlock)
                    && st.getMenuProvider(mc.level, b.getBlockPos()) == null
                    && !st.is(net.minecraft.tags.BlockTags.DOORS) && !st.is(net.minecraft.tags.BlockTags.TRAPDOORS)
                    && !st.is(net.minecraft.tags.BlockTags.BUTTONS) && !st.is(net.minecraft.tags.BlockTags.FENCE_GATES)
                    && !st.is(net.minecraft.world.level.block.Blocks.LEVER) && !st.is(net.minecraft.tags.BlockTags.BEDS);
        }
        return false;
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_arts"), ArtClient::render);
    }

    // 絵の場所と、行の文 (文は残り時間が tick ごとにしか変わらないので、tick ごとに一度だけ作る)
    private static final ResourceLocation[] ICONS = java.util.Arrays.stream(AbyssArt.values())
            .map(a -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/abyss_art/" + a.id + ".png")).toArray(ResourceLocation[]::new);
    private static final Component[] LINES = new Component[AbyssArt.values().length];
    // 右の欄が狭いときの短い行 (名前なし。絵で分かる)
    private static final Component[] SHORT = new Component[AbyssArt.values().length];
    private static final int[] COLORS = new int[AbyssArt.values().length];
    private static long linesTick = Long.MIN_VALUE;

    private static void buildLines(Minecraft mc, long now) {
        ArtData data = AbyssArts.data(mc.player);
        for (AbyssArt a : AbyssArt.values()) {
            int rank = data.rank(a);
            // 場面の操作 (右クリックなど) を先に、専用の鍵を後ろに。狭い欄では場面の操作だけ
            String alt = Component.translatable("hud.skyabyss.art.alt." + a.id).getString();
            String keyName = alt + "/" + KEYS[a.ordinal()].getTranslatedKeyMessage().getString();
            Component name = Component.translatable(a.nameKey());
            Component line;
            Component shortLine;
            int color;
            if (rank == 0) {
                line = Component.translatable("hud.skyabyss.art.none", keyName, name);
                shortLine = Component.translatable("hud.skyabyss.art.short_none", alt);
                color = 0x808080;
            } else {
                long left = Math.max(0, UNTIL[a.ordinal()] - now);
                String secs = String.format("%.1f", left / 20.0);
                line = left > 0
                        ? Component.translatable("hud.skyabyss.art.wait", keyName, name, rank, secs)
                        : Component.translatable("hud.skyabyss.art.ready", keyName, name, rank);
                shortLine = left > 0
                        ? Component.translatable("hud.skyabyss.art.short_wait", alt, rank, secs)
                        : Component.translatable("hud.skyabyss.art.short_ready", alt, rank);
                color = left > 0 ? 0xB8B8B8 : 0xE6F4FF;
            }
            LINES[a.ordinal()] = line;
            SHORT[a.ordinal()] = shortLine;
            COLORS[a.ordinal()] = color;
        }
        linesTick = now;
    }

    private static final int ROW = 20;
    private static final int SHORT_W = 80;
    private static int fullW;

    // 右の真ん中あたりに、右寄せで。右上の板 (誓い・背中の目盛り・仲間) とスコアボードを避けるので、置き場は HudLayout に頼む
    public static void requests(Minecraft mc, int guiW, int guiH, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.level.dimension() != AbyssWorld.LEVEL) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now != linesTick) {
            buildLines(mc, now);
        }
        int w = 0;
        for (Component c : LINES) {
            w = Math.max(w, mc.font.width(c));
        }
        // 幅は 16 刻みに丸める (待ちの秒で文字の幅が変わるたびに置き場の計算をやり直さない)。右の欄に入らないなら、入る幅まで縮める (短い行になる)
        fullW = (w + 4 + 16 + 4 + 4 + 15) / 16 * 16;
        int fw = fullW;
        int h = ROW * LINES.length;
        out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.ABYSS_ARTS,
                jp.revati.skyabyss.hud.HudLayout.Region.TOP_RIGHT,
                jp.revati.skyabyss.hud.HudLayout.Fit.keyed("abyss_arts:" + fw + ":" + h,
                        (maxW, maxH) -> maxW >= SHORT_W && maxH >= h ? new jp.revati.skyabyss.hud.HudLayout.Size(Math.min(fw, maxW), h) : null))
                .minTop(guiH / 2 - 24));
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.ABYSS_ARTS);
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.level.dimension() != AbyssWorld.LEVEL || r == null) {
            return;
        }
        Font font = mc.font;
        long now = mc.level.getGameTime();
        if (now != linesTick) {
            buildLines(mc, now);
        }
        int iconX = r.right() - 4 - 16;
        int y = r.y();
        for (AbyssArt a : AbyssArt.values()) {
            int row = y + a.ordinal() * ROW;
            // 絵 (32px の絵を半分の大きさで出す)
            g.pose().pushPose();
            g.pose().scale(0.5f, 0.5f, 1f);
            g.blit(ICONS[a.ordinal()], iconX * 2, row * 2, 0, 0, 32, 32, 32, 32);
            g.pose().popPose();
            Component line = r.w() >= fullW ? LINES[a.ordinal()] : SHORT[a.ordinal()];
            int color = COLORS[a.ordinal()];
            g.drawString(font, line, iconX - 4 - font.width(line), row + 4, color, true);
        }
    }
}

package jp.revati.skyabyss.client.hud;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// バニラの体力・吸収・防具・満腹・空気の表示を、mod の絵柄 (案 A: 暗い石板に青緑の縁) に描き直す (docs/design/18 の 14.6)。
// ハートの列は体力が増えるほど上へ伸びて他の部品を覆っていたので、いつも一本の細い帯 (数字つき) にする。
// 置き場は HudLayout のバニラの障害物 (V_HEALTH・V_ARMOR・V_FOOD・V_AIR) と同じ長方形で、段数は 1 に固定 (HudManager.scene)。
// 設定 styledVitals が false なら何もせずバニラのまま。馬などの乗り物の体力・ジャンプ・経験値はバニラのまま
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class VitalsHud {
    private static final int EDGE = 0xFF2E5552;
    private static final int SLAB = 0xD00A0C10;
    private static final int TROUGH = 0xFF14181E;

    private VitalsHud() {
    }

    // 帯に置き換えるか
    public static boolean styled() {
        Minecraft mc = Minecraft.getInstance();
        return Config.STYLED_VITALS.get() && mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            onLayer0(event);
        } finally {
            jp.revati.skyabyss.dev.Prof.end("client.vitals", prof);
        }
    }

    static void onLayer0(RenderGuiLayerEvent.Pre event) {
        var name = event.getName();
        boolean health = VanillaGuiLayers.PLAYER_HEALTH.equals(name);
        boolean armor = VanillaGuiLayers.ARMOR_LEVEL.equals(name);
        boolean food = VanillaGuiLayers.FOOD_LEVEL.equals(name);
        boolean air = VanillaGuiLayers.AIR_LEVEL.equals(name);
        if (!(health || armor || food || air) || !styled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (mc.options.hideGui) {
            return;
        }
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        if (health) {
            drawHealth(g, mc, p);
        } else if (armor) {
            drawArmor(g, mc, p);
        } else if (food) {
            // 体力のある乗り物に乗っている間は、バニラが乗り物の体力をここへ描く (満腹は出ない)
            if (!(p.getVehicle() instanceof LivingEntity v && v.showVehicleHealth())) {
                drawFood(g, mc, p);
            } else {
                event.setCanceled(false);
            }
        } else {
            drawAir(g, mc, p);
        }
    }

    private static Rect rect(Id id) {
        return HudManager.result().vanilla.get(id);
    }

    private static void drawHealth(GuiGraphics g, Minecraft mc, Player p) {
        Rect r = rect(Id.V_HEALTH);
        if (r == null) {
            return;
        }
        float max = Math.max(1f, p.getMaxHealth());
        float hp = Mth.clamp(p.getHealth(), 0f, max);
        int color = 0xFFC8505A;
        if (p.hasEffect(MobEffects.POISON)) {
            color = 0xFF8CAA3C;
        } else if (p.hasEffect(MobEffects.WITHER)) {
            color = 0xFF4A4452;
        } else if (p.isFullyFrozen()) {
            color = 0xFF7FB4D6;
        }
        // 傷が浅いうちは落ち着いた赤、残りが四分の一以下なら脈打つ
        if (hp <= max * 0.25f) {
            float k = 0.65f + 0.35f * (float) Math.sin(p.tickCount * 0.45f);
            color = scale(color, k);
        }
        int absorb = (int) Math.ceil(p.getAbsorptionAmount());
        String text = (int) Math.ceil(hp) + "/" + (int) Math.ceil(max);
        bar(g, mc.font, r, HEART, 0xFFE0606C, hp / max, color, text, 0xFFF4EEE6, p.hurtTime > 0 || p.invulnerableTime > 10);
        if (absorb > 0) {
            // 吸収: 帯の上辺に金の線 (最大の体力に対する割合)。数字は体力の数字の左
            int inner = r.w() - 2 - 12;
            int len = Math.max(1, Math.min(inner, Math.round(inner * Math.min(1f, p.getAbsorptionAmount() / max))));
            g.fill(r.x() + 12 + 1, r.y() + 1, r.x() + 12 + 1 + len, r.y() + 2, 0xFFF2C14E);
            String ab = "+" + absorb;
            g.drawString(mc.font, ab, r.x() + r.w() - 3 - mc.font.width(text) - 3 - mc.font.width(ab), r.y() + 1, 0xFFF2C14E, true);
        }
    }

    private static void drawArmor(GuiGraphics g, Minecraft mc, Player p) {
        int armor = p.getArmorValue();
        Rect r = rect(Id.V_ARMOR);
        if (armor <= 0 || r == null) {
            return;
        }
        bar(g, mc.font, r, SHIELD, 0xFFC4D0D8, Math.min(1f, armor / 20f), 0xFF8A9AA6, String.valueOf(armor), 0xFFF4F6F8, false);
    }

    private static void drawFood(GuiGraphics g, Minecraft mc, Player p) {
        Rect r = rect(Id.V_FOOD);
        if (r == null) {
            return;
        }
        var fd = p.getFoodData();
        int level = fd.getFoodLevel();
        int color = p.hasEffect(MobEffects.HUNGER) ? 0xFF7C8A3C : 0xFFD59A3A;
        if (level <= 6) {
            float k = 0.7f + 0.3f * (float) Math.sin(p.tickCount * 0.35f);
            color = scale(color, k);
        }
        bar(g, mc.font, r, APPLE, 0xFFE8B04C, level / 20f, color, String.valueOf(level), 0xFFF4EEE6, false);
        // 隠れた満腹度: 帯の下辺に淡い線
        float sat = Math.min(level, fd.getSaturationLevel());
        if (sat > 0f) {
            int inner = r.w() - 2 - 12;
            int len = Math.max(1, Math.round(inner * sat / 20f));
            g.fill(r.x() + 12 + 1, r.bottom() - 2, r.x() + 12 + 1 + len, r.bottom() - 1, 0xCCFFF1C8);
        }
    }

    private static void drawAir(GuiGraphics g, Minecraft mc, Player p) {
        int max = p.getMaxAirSupply();
        int air = Mth.clamp(p.getAirSupply(), 0, max);
        Rect r = rect(Id.V_AIR);
        if (r == null || !(p.isEyeInFluid(FluidTags.WATER) || air < max)) {
            return;
        }
        String text = (int) Math.ceil(air / 20f) + "s";
        int color = air < max / 4 ? 0xFFD66A6A : 0xFF58B4D2;
        bar(g, mc.font, r, BUBBLE, 0xFFBFE8F4, air / (float) max, color, text, 0xFFF4F6F8, false);
    }

    // 帯の一本: 暗い石板 + 青緑の縁、左に小さな絵、中に割合の色、右に数字
    private static void bar(GuiGraphics g, Font font, Rect r, String[] icon, int iconColor, float frac, int color, String text, int textColor, boolean flash) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), flash ? 0xFFE9E3D4 : EDGE);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, SLAB);
        drawIcon(g, icon, r.x() + 3, r.y() + (r.h() - icon.length) / 2, iconColor);
        int bx = r.x() + 12;
        int bw = r.w() - 12 - 1;
        g.fill(bx, r.y() + 1, bx + bw, r.bottom() - 1, TROUGH);
        int fill = Mth.clamp(Math.round(bw * Mth.clamp(frac, 0f, 1f)), frac > 0f ? 1 : 0, bw);
        if (fill > 0) {
            g.fill(bx, r.y() + 1, bx + fill, r.bottom() - 1, color);
            // 上辺に明るい一段 (石板に彫った段の感じ)
            g.fill(bx, r.y() + 1, bx + fill, r.y() + 2, scale(color, 1.25f));
        }
        g.drawString(font, text, r.right() - 3 - font.width(text), r.y() + 1, textColor, true);
    }

    private static int scale(int argb, float k) {
        int rr = Mth.clamp((int) (((argb >> 16) & 0xFF) * k), 0, 255);
        int gg = Mth.clamp((int) (((argb >> 8) & 0xFF) * k), 0, 255);
        int bb = Mth.clamp((int) ((argb & 0xFF) * k), 0, 255);
        return 0xFF000000 | rr << 16 | gg << 8 | bb;
    }

    private static void drawIcon(GuiGraphics g, String[] shape, int x, int y, int color) {
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                if (shape[row].charAt(col) == '#') {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }

    private static final String[] HEART = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
    private static final String[] SHIELD = {"#######", "#######", "#######", ".#####.", "..###..", "...#..."};
    private static final String[] APPLE = {"...#...", ".#####.", "#######", "#######", ".#####.", "..#.#.."};
    private static final String[] BUBBLE = {".###.", "#...#", "#...#", "#...#", ".###."};
}

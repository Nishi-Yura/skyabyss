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
    private static final int EDGE = 0xFFC9A85C;
    private static final int SLAB = 0xD00A0E1E;
    private static final int TROUGH = 0xFF05070F;

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
        // バニラの緑の経験値のバーと数字は出さない (左上の Lv と成長の帯がある。2026-10-28 の試遊「経験値バーいらない」)
        if (VanillaGuiLayers.EXPERIENCE_BAR.equals(name) || VanillaGuiLayers.EXPERIENCE_LEVEL.equals(name)) {
            event.setCanceled(true);
            return;
        }
        // Mod の乗り物 (乗り笛の獣・巨神の子) の体力は出さない。そこには自分のマナ (2026-11-08 の声「ペットの hp 表記いらない」)
        if (VanillaGuiLayers.VEHICLE_HEALTH.equals(name) && Minecraft.getInstance().player != null && modVehicle(Minecraft.getInstance().player)) {
            event.setCanceled(true);
            return;
        }
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
            if (manaInFoodSlot()) {
                drawMana(g, mc, p);
            } else if (!(p.getVehicle() instanceof LivingEntity v && v.showVehicleHealth()) || modVehicle(p)) {
                drawFood(g, mc, p);
            } else {
                event.setCanceled(false);
            }
        } else {
            drawAir(g, mc, p);
        }
    }

    // 置き場: 下の一枚の帯 (BandHud) の中。帯を出していなければバニラの置き場
    private static Rect rect(Id id) {
        Rect band = BandHud.rect(id);
        return band != null ? band : HudManager.result().vanilla.get(id);
    }

    private static void drawHealth(GuiGraphics g, Minecraft mc, Player p) {
        Rect r = rect(Id.V_HEALTH);
        if (r == null) {
            return;
        }
        float max = Math.max(1f, p.getMaxHealth());
        float hp = Mth.clamp(p.getHealth(), 0f, max);
        int color = 0xFFD45864;
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
        // 数は中の値の 10 倍で見せる (jp.revati.skyabyss.Hp。中の 20 = 帯の「200/200」)
        int absorb = (int) Math.ceil(jp.revati.skyabyss.Hp.shown(p.getAbsorptionAmount()));
        String text = (int) Math.ceil(jp.revati.skyabyss.Hp.shown(hp) - 1e-4) + "/" + (int) Math.ceil(jp.revati.skyabyss.Hp.shown(max) - 1e-4);
        bar(g, mc.font, r, HEART, 0xFFE0606C, hp / max, color, text, 0xFFF4EEE6, p.hurtTime > 0 || p.invulnerableTime > 10);
        if (absorb > 0) {
            // 吸収: 帯の上辺に金の線 (最大の体力に対する割合)。数字は体力の数字の左
            int inner = r.w() - 2 - 12;
            int len = Math.max(1, Math.min(inner, Math.round(inner * Math.min(1f, p.getAbsorptionAmount() / max))));
            g.fill(r.x() + 12 + 1, r.y() + 1, r.x() + 12 + 1 + len, r.y() + 2, 0xFFE9C46A);
            String ab = "+" + absorb;
            g.drawString(mc.font, ab, r.x() + r.w() - 3 - mc.font.width(text) - 3 - mc.font.width(ab), r.y() + 1, 0xFFE9C46A, true);
        }
    }

    static final boolean NO_FOOD_BAR = true;

    // おなかは空かないので、満腹度の場所 (体力の右) にマナの帯を置く (2026-10-23 の試遊)。
    // 体力のある乗り物に乗っている間はバニラが乗り物の体力をここへ描くので、マナは技の枠の上 (SkillHud) に戻す
    public static boolean manaInFoodSlot() {
        Minecraft mc = Minecraft.getInstance();
        return NO_FOOD_BAR && styled() && rect(Id.V_FOOD) != null
                && (!(mc.player.getVehicle() instanceof LivingEntity v && v.showVehicleHealth()) || modVehicle(mc.player));
    }

    // Mod の乗り物に乗っているか
    static boolean modVehicle(Player p) {
        return p.getVehicle() != null && jp.revati.skyabyss.SkyAbyss.MODID.equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(p.getVehicle().getType()).getNamespace());
    }

    private static void drawMana(GuiGraphics g, Minecraft mc, Player p) {
        Rect r = rect(Id.V_FOOD);
        var d = jp.revati.skyabyss.rpg.skill.SkillManager.get(p);
        double max = jp.revati.skyabyss.rpg.skill.SkillManager.maxMana(p);
        if (r == null || max <= 0) {
            return;
        }
        double mana = Mth.clamp(Math.max(0, d.mana), 0, max);
        // 足りずに撃てなかった直後は帯が光る (SkillClient.manaFlash)
        boolean flash = jp.revati.skyabyss.rpg.client.skill.SkillClient.manaFlash > 0;
        bar(g, mc.font, r, DROP, 0xFF7FC8F0, (float) (mana / max), 0xFF3C82D2, (int) Math.floor(mana) + "/" + (int) Math.ceil(max), 0xFFEAF4FC, flash);
        drawGauge(g, mc, p, r);
    }

    // 型の溜まるもの (rpg/skill/Gauges): マナの帯のすぐ上の細い帯。小さい数は点、大きい数は帯。満ちると明るく脈打つ。
    // 水の中で空気の帯が出ている間は重なるので描かない
    private static void drawGauge(GuiGraphics g, Minecraft mc, Player p, Rect r) {
        var k = jp.revati.skyabyss.rpg.skill.Gauges.Kind.byOrdinal(jp.revati.skyabyss.rpg.client.skill.SkillClient.gaugeKind);
        if (k == null || p.isEyeInFluid(FluidTags.WATER) || p.getAirSupply() < p.getMaxAirSupply()) {
            return;
        }
        int value = Mth.clamp(jp.revati.skyabyss.rpg.client.skill.SkillClient.gaugeValue, 0, k.max);
        boolean full = value >= k.max;
        int[] box = BandHud.gaugeBox(r);
        int y0 = box[1];
        int x0 = box[0];
        int w = box[2];
        int color = k.color;
        if (full) {
            color = scale(color, 0.85f + 0.3f * (float) Math.sin(p.tickCount * 0.5f));
        }
        if (jp.revati.skyabyss.rpg.client.skill.SkillClient.gaugeFlash > 0) {
            color = 0xFFFFFFFF;
        }
        int fc = color;
        g.drawManaged(() -> {
            g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + 4, EDGE);
            g.fill(x0, y0, x0 + w, y0 + 3, TROUGH);
            if (k.pips()) {
                // 点: 数だけ等しく分けた升
                int gap = 1;
                int cell = Math.max(1, (w - gap * (k.max - 1)) / k.max);
                for (int i = 0; i < k.max; i++) {
                    int cx = x0 + i * (cell + gap);
                    g.fill(cx, y0, Math.min(x0 + w, cx + cell), y0 + 3, i < value ? fc : 0xFF1C2444);
                }
            } else {
                int fill = Math.round(w * value / (float) k.max);
                if (fill > 0) {
                    g.fill(x0, y0, x0 + fill, y0 + 3, fc);
                }
            }
        });
    }

    private static void drawArmor(GuiGraphics g, Minecraft mc, Player p) {
        int armor = p.getArmorValue();
        Rect r = rect(Id.V_ARMOR);
        if (armor <= 0 || r == null) {
            return;
        }
        BandHud.armorTab(g, mc.font, r, armor, () -> drawIcon(g, SHIELD, r.x() + 4, r.y() + 3, 0xFFC4D0D8));
    }

    private static void drawFood(GuiGraphics g, Minecraft mc, Player p) {
        Rect r = rect(Id.V_FOOD);
        // おなかは空かない (rpg/NoHunger)。満腹度の帯は出さない
        if (r == null || NO_FOOD_BAR) {
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
        BandHud.airTab(g, mc.font, r, air / (float) max, color, text);
    }

    // 帯の一本: 暗い石板 + 青緑の縁、左に小さな絵、中に割合の色、右に数字
    private static void bar(GuiGraphics g, Font font, Rect r, String[] icon, int iconColor, float frac, int color, String text, int textColor, boolean flash) {
        // 塗りだけ先に一度に送り、字はそのあと上に (fill ごとに描き出さない。出る絵は同じ)
        g.drawManaged(() -> {
            g.fill(r.x(), r.y(), r.right(), r.bottom(), flash ? 0xFFE9E3D4 : 0xFF05070F);
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
        });
        g.drawString(font, text, r.right() - 3 - font.width(text), r.y() + 1, textColor, true);
    }

    private static int scale(int argb, float k) {
        int rr = Mth.clamp((int) (((argb >> 16) & 0xFF) * k), 0, 255);
        int gg = Mth.clamp((int) (((argb >> 8) & 0xFF) * k), 0, 255);
        int bb = Mth.clamp((int) ((argb & 0xFF) * k), 0, 255);
        return 0xFF000000 | rr << 16 | gg << 8 | bb;
    }

    // 絵の '#' の横の連なりを一つの fill にまとめる (同じ色の塗りなので出る絵は同じ)。連なりは形ごとに一度だけ求める
    private static final java.util.Map<String[], int[]> ICON_RUNS = new java.util.IdentityHashMap<>();

    private static void drawIcon(GuiGraphics g, String[] shape, int x, int y, int color) {
        int[] runs = ICON_RUNS.computeIfAbsent(shape, VitalsHud::runsOf);
        for (int i = 0; i < runs.length; i += 3) {
            g.fill(x + runs[i + 1], y + runs[i], x + runs[i + 2], y + runs[i] + 1, color);
        }
    }

    // {行, 始まり, 終わり (含まない)} の並び
    private static int[] runsOf(String[] shape) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int row = 0; row < shape.length; row++) {
            int col = 0;
            while (col < shape[row].length()) {
                if (shape[row].charAt(col) == '#') {
                    int start = col;
                    while (col < shape[row].length() && shape[row].charAt(col) == '#') {
                        col++;
                    }
                    out.add(row);
                    out.add(start);
                    out.add(col);
                } else {
                    col++;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static final String[] HEART = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
    private static final String[] SHIELD = {"#######", "#######", "#######", ".#####.", "..###..", "...#..."};
    private static final String[] APPLE = {"...#...", ".#####.", "#######", "#######", ".#####.", "..#.#.."};
    private static final String[] DROP = {"...#...", "..###..", ".#####.", "#######", "#######", ".#####."};
    private static final String[] BUBBLE = {".###.", "#...#", "#...#", "#...#", ".###."};
}

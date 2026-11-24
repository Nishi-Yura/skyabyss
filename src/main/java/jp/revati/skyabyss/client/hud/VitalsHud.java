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
    private static final int EDGE = 0xFF05070F;
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
        // 場面のあいだは出さない (下の帯と同じ)
        if (jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        if (health) {
            drawHealth(g, mc, p);
        } else if (armor) {
            drawArmor(g, mc, p);
        } else if (food) {
            // 体力のある乗り物に乗っている間は、バニラが乗り物の体力をここへ描く (満腹は出ない)
            // おなかは空かない (rpg/NoHunger) ので、満腹度の所はマナの帯。バニラの乗り物の体力が出る間だけバニラに任せる
            if (manaInFoodSlot()) {
                drawMana(g, mc, p);
            } else if (p.getVehicle() instanceof LivingEntity v && v.showVehicleHealth() && !modVehicle(p)) {
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
        // 毒・衰弱・凍えの色 (0 = ふつうの桃色)
        int status = p.hasEffect(MobEffects.POISON) ? 0xFF8CAA3C : p.hasEffect(MobEffects.WITHER) ? 0xFF4A4452 : p.isFullyFrozen() ? 0xFF7FB4D6 : 0;
        // 数は中の値の 10 倍で見せる (jp.revati.skyabyss.Hp。中の 20 = 帯の「200/200」)
        int absorb = (int) Math.ceil(jp.revati.skyabyss.Hp.shown(p.getAbsorptionAmount()));
        String text = (int) Math.ceil(jp.revati.skyabyss.Hp.shown(hp) - 1e-4) + "/" + (int) Math.ceil(jp.revati.skyabyss.Hp.shown(max) - 1e-4);
        bar(g, mc.font, r, 0, hp / max, status, text, p.hurtTime > 0 || p.invulnerableTime > 10, hp <= max * 0.25f);
        if (absorb > 0) {
            // 吸収: 帯のすぐ上の金の細線 (最大の体力に対する割合)。数は出さない (数字が重なって読めなかった。2026-11-11)
            int len = Math.max(2, Math.min(r.w(), Math.round(r.w() * Math.min(1f, p.getAbsorptionAmount() / max))));
            g.fill(r.x(), r.y() - 3, r.x() + len, r.y() - 1, 0xFF05070F);
            g.fill(r.x() + 1, r.y() - 2, r.x() + len - 1, r.y() - 1, 0xFFE9C46A);
        }
    }

    // おなかは空かないので、満腹度の場所 (体力の右) にマナの帯を置く (2026-10-23 の試遊)。
    // 体力のある乗り物に乗っている間はバニラが乗り物の体力をここへ描くので、マナは技の枠の上 (SkillHud) に戻す
    public static boolean manaInFoodSlot() {
        Minecraft mc = Minecraft.getInstance();
        return styled() && rect(Id.V_FOOD) != null
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
        bar(g, mc.font, r, 1, (float) (mana / max), 0, (int) Math.floor(mana) + "/" + (int) Math.ceil(max), flash, false);
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
            g.fill(x0, y0, x0 + w + 1, y0 + 4, P_PINK_LO);
            g.fill(x0 - 1, y0 - 1, x0 + w, y0 + 3, P_BLACK);
            if (k.pips()) {
                // 点: 数だけ等しく分けた升
                int gap = 1;
                int cell = Math.max(1, (w - gap * (k.max - 1)) / k.max);
                for (int i = 0; i < k.max; i++) {
                    int cx = x0 + i * (cell + gap);
                    g.fill(cx, y0, Math.min(x0 + w, cx + cell), y0 + 3, i < value ? fc : 0xFF3A3644);
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

    // ---- 帯 (切り絵の短冊。2026-11-24 に 3 案からユーザーが A を選んだ。docs/design/56 の 5)
    // 帯ごと (0 = 体力・1 = マナ) の見えている割合と、減った分を遅れて追う影 (減ると 0.3 秒待ってから縮む)
    private static final float[] SHOWN = {-1f, -1f};
    private static final float[] GHOST = {-1f, -1f};
    private static final long[] HOLD = {0L, 0L};
    private static final long[] LAST = {0L, 0L};

    private static void step(int k, float frac) {
        long now = net.minecraft.Util.getMillis();
        float dt = LAST[k] == 0 ? 1f : Math.min(0.1f, (now - LAST[k]) / 1000f);
        LAST[k] = now;
        if (SHOWN[k] < 0) {
            SHOWN[k] = frac;
            GHOST[k] = frac;
        }
        // 見えている割合は速く寄る (増えるときは少し遅く。回復が伸びて見える)
        float rate = frac > SHOWN[k] ? 6f : 18f;
        SHOWN[k] += (frac - SHOWN[k]) * Math.min(1f, rate * dt);
        if (frac >= GHOST[k]) {
            GHOST[k] = Math.max(frac, SHOWN[k]);
            HOLD[k] = now + 300;
        } else if (now > HOLD[k]) {
            GHOST[k] = Math.max(frac, GHOST[k] - 0.7f * dt);
        }
    }

    static final int P_BLACK = 0xFF181722;
    static final int P_PINK = 0xFFE45A9C;
    static final int P_PINK_HI = 0xFFF89CD2;
    static final int P_PINK_LO = 0xFF8C2C60;
    static final int P_CREAM = 0xFFECE4D6;
    static final int P_CREAM_LO = 0xFFC9BFAE;
    static final int P_ON = 0xFFFFF6EA;

    // 斜めの一行 (平行四辺形の帯の一行。右へ傾ける: 上の行ほど右へずれる)
    static void slantRow(GuiGraphics g, int x0, int x1, int y, int h, int row, int color) {
        int off = (h - 1 - row) / 3;
        g.fill(x0 + off, y + row, x1 + off, y + row + 1, color);
    }

    // 帯の一本: 右へ傾けた黒い紙の短冊 (下に濃い桃色の影)、左の端に斜めの筋、中に塗り (体力 = 桃色・マナ = 生成り)。
    // 減った分は影の色で残って遅れて縮む。四分の一以下は塗りが脈打つ。打たれた・足りなかった瞬間は紙が生成りに光る。数は右寄せで縁取り
    private static void bar(GuiGraphics g, Font font, Rect r, int k, float frac, int status, String text, boolean flash, boolean low) {
        step(k, Mth.clamp(frac, 0f, 1f));
        float shown = Mth.clamp(SHOWN[k], 0f, 1f);
        float ghost = Mth.clamp(GHOST[k], 0f, 1f);
        // 毒・衰弱・凍えは体力の塗りをその色に
        boolean statusColor = k == 0 && status != 0;
        int base = k == 0 ? (statusColor ? status : P_PINK) : P_CREAM;
        if (low && !statusColor) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(net.minecraft.Util.getMillis() / 130.0);
            base = scale(P_PINK, 0.8f + 0.35f * pulse);
        }
        final int fillC = base;
        final int fillHi = k == 0 ? (statusColor ? scale(status, 1.25f) : P_PINK_HI) : P_ON;
        final int ghostC = k == 0 ? P_ON : P_PINK_HI;
        int x = r.x();
        int y = r.y();
        int w = r.w();
        int h = r.h();
        g.drawManaged(() -> {
            for (int row = 0; row < h; row++) {
                slantRow(g, x + 1, x + w + 1, y + 1, h, row, P_PINK_LO);
            }
            for (int row = 0; row < h; row++) {
                slantRow(g, x, x + w, y, h, row, flash ? P_CREAM : P_BLACK);
            }
            int bx = x + 6;
            int bw = w - 8;
            int gEnd = Math.round(bw * ghost);
            int sEnd = Math.round(bw * shown);
            for (int row = 1; row < h - 1; row++) {
                if (gEnd > sEnd) {
                    slantRow(g, bx + sEnd, bx + gEnd, y, h, row, ghostC);
                }
                if (sEnd > 0) {
                    slantRow(g, bx, bx + sEnd, y, h, row, row == 1 ? fillHi : fillC);
                }
            }
            for (int row = 0; row < h; row++) {
                slantRow(g, x + 2, x + 4, y, h, row, k == 0 ? P_PINK : P_CREAM_LO);
            }
        });
        BandHud.outlined(g, font, text, x + w - 2 - font.width(text), y + 1, P_ON);
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
    private static final String[] DROP = {"...#...", "..###..", ".#####.", "#######", "#######", ".#####."};
}

package jp.revati.skyabyss.client.hud;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Plate;
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

// バニラの体力・吸収・防具・満腹・空気の表示を、mod の絵柄 (鐘の銅。client/ui/Plate の HUD の部品) に描き直す (docs/design/56 の 5)。
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
        // 毒・衰弱・凍えの色 (0 = ふつうの赤)
        int status = p.hasEffect(MobEffects.POISON) ? 0xFF8CAA3C : p.hasEffect(MobEffects.WITHER) ? 0xFF4A4452 : p.isFullyFrozen() ? 0xFF7FB4D6 : 0;
        // 数は中の値の 10 倍で見せる (jp.revati.skyabyss.Hp。中の 20 = 帯の「200/200」)
        int absorb = (int) Math.ceil(jp.revati.skyabyss.Hp.shown(p.getAbsorptionAmount()));
        String text = (int) Math.ceil(jp.revati.skyabyss.Hp.shown(hp) - 1e-4) + "/" + (int) Math.ceil(jp.revati.skyabyss.Hp.shown(max) - 1e-4);
        bar(g, mc.font, r, 0, hp / max, status, text, p.hurtTime > 0 || p.invulnerableTime > 10, hp <= max * 0.25f);
        if (absorb > 0) {
            // 吸収: 帯のすぐ上の金の細線 (最大の体力に対する割合)。数は出さない (数字が重なって読めなかった。2026-11-11)
            int bx = r.x() + CAP - 1;
            int bw = r.w() - CAP + 1;
            g.drawManaged(() -> Plate.bar(g, bx, r.y() - 4, bw, 4, Math.min(1f, p.getAbsorptionAmount() / max), 0f, 0xE9C46A, 0, 1f));
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
            if (k.pips()) {
                // 点: 数だけ等しく分けた升 (溝の中に並べる。空の升は一段明るい石)
                Plate.bar(g, x0 - 1, y0 - 1, w + 2, 5, 0f, 0f, fc, 0, 1f);
                int gap = 1;
                int cell = Math.max(1, (w - gap * (k.max - 1)) / k.max);
                for (int i = 0; i < k.max; i++) {
                    int cx = x0 + i * (cell + gap);
                    int cr = Math.min(x0 + w, cx + cell);
                    if (i < value) {
                        g.fill(cx, y0, cr, y0 + 3, fc);
                        g.fill(cx, y0, cr, y0 + 1, Plate.mix(fc, 0xFFFFFFFF, 0.40f));
                        g.fill(cx, y0 + 2, cr, y0 + 3, Plate.mix(fc, 0xFF000000, 0.38f));
                    } else {
                        g.fill(cx, y0, cr, y0 + 3, Plate.pal().slabHi);
                    }
                }
            } else {
                Plate.bar(g, x0 - 1, y0 - 1, w + 2, 5, value / (float) k.max, 0f, fc, 0, 1f);
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

    // ---- 帯 (鐘の銅。docs/design/56 の 5): 左の端に印の升 (体力 = 心臓・マナ = しずく)、その右に銅の縁の溝と塗り (Plate.bar)
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

    static final int HP_RED = 0xFFD8505C;
    static final int MANA_BLUE = 0xFF4E8AE0;
    // 帯の上の数の色 (明るい骨色)
    static final int P_ON = 0xFFFFF6EA;
    // 印の升の幅 (帯の左の端。溝はこの右から。右の縁は溝の左の縁と一つ)
    static final int CAP = 11;

    // 帯の一本。塗りは体力 = 赤・マナ = 青 (毒・衰弱・凍えは体力の塗りと印がその色)。減った分は明るい影で残って遅れて縮み、回復は少しずつ伸びる。
    // 打たれた・マナが足りなかった瞬間は縁と印が白く光り、四分の一以下は塗りと縁が赤く脈打つ。数は右寄せで縁取り
    private static void bar(GuiGraphics g, Font font, Rect r, int k, float frac, int status, String text, boolean flash, boolean low) {
        step(k, Mth.clamp(frac, 0f, 1f));
        float shown = Mth.clamp(SHOWN[k], 0f, 1f);
        float ghost = Mth.clamp(GHOST[k], 0f, 1f);
        boolean statusColor = k == 0 && status != 0;
        int base = k == 0 ? (statusColor ? status : HP_RED) : MANA_BLUE;
        float pulse = 0.5f + 0.5f * (float) Math.sin(net.minecraft.Util.getMillis() / 130.0);
        boolean danger = low && !statusColor;
        int fillC = danger ? scale(base, 0.8f + 0.35f * pulse) : base;
        Plate.Pal pal = Plate.pal();
        int rim = flash ? 0xFFF6EA : danger ? Plate.mix(pal.f, HP_RED, 0.25f + 0.75f * pulse) & 0xFFFFFF : 0;
        int iconC = flash ? P_ON : danger ? scale(base, 0.9f + 0.3f * pulse) : Plate.mix(base, 0xFFFFFFFF, 0.12f);
        int x = r.x();
        int y = r.y();
        int w = r.w();
        int h = r.h();
        g.drawManaged(() -> {
            // 印の升: 銅の縁 (上と左) と石の地
            int hi = rim != 0 ? 0xFF000000 | rim : pal.f;
            int lo = rim != 0 ? Plate.mix(0xFF000000 | rim, 0xFF000000, 0.35f) : pal.fLo;
            g.fill(x + 1, y, x + CAP - 1, y + 1, hi);
            g.fill(x, y + 1, x + 1, y + h - 1, hi);
            g.fill(x + 1, y + h - 1, x + CAP - 1, y + h, lo);
            g.fill(x + 2, y + h, x + CAP - 1, y + h + 1, 0x70000000);
            g.fill(x + 1, y + 1, x + CAP - 1, y + h - 1, pal.slab);
            g.fill(x + 1, y + 1, x + CAP - 1, y + 2, pal.slabHi);
            drawIcon(g, k == 0 ? HEART : DROP, x + 2, y + 2, iconC);
            Plate.bar(g, x + CAP - 1, y, w - CAP + 1, h, shown, ghost, fillC, rim, 1f);
        });
        BandHud.outlined(g, font, text, x + w - 3 - font.width(text), y + 1, P_ON);
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

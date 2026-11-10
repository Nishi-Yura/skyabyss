package jp.revati.skyabyss.rpg.client.skill;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.client.hud.HudManager;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.hud.HudLayout.Region;
import jp.revati.skyabyss.hud.HudLayout.Request;
import jp.revati.skyabyss.hud.HudLayout.Side;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

// 技の小さな表示: 左上の成長の表示の下に細いマナのバー (月の青)、ホットバーの右に技の枠 4 つ。
// 技を一つも覚えていない人には出さない (画面を散らかさない)。F1 と F3 の画面では出さない
public final class SkillHud {
    // gui/skill/skills.png (1024x512、2 倍の密度)。配置は tools/gen/ui/gen_skill_assets.py と同じ
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/skill/skills.png");
    static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/skill/icons.png");
    static final int TEX_W = 1024;
    static final int TEX_H = 512;
    // 成長の表示 (GrowthHud) の Lv とバーの下
    static final int MANA_X = 6;
    static final int MANA_Y = 24;
    static final int MANA_W = 64;
    // 成長の表示の「+12」の列を、この分だけ下げる (GrowthHud が読む)
    public static final int MANA_ROW = 6;
    static final int SLOT = 20;
    static final int SLOT_GAP = 2;

    private SkillHud() {
    }

    // HUD の要求: 技の枠はホットバーの右 (入らなければ左上のマナの下)
    public static void requests(Minecraft mc, java.util.List<Request> out) {
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.player.isSpectator()) {
            return;
        }
        boolean skills = anySlot(SkillManager.get(mc.player));
        boolean charm = !charm(mc.player).isEmpty();
        if (!skills && !charm) {
            return;
        }
        // 右端に構えの字の分 (11) を足す。護符を付けていれば、その右に護符の枠
        int row = skills ? SkillManager.get(mc.player).slotCount() * (SLOT + SLOT_GAP) - SLOT_GAP + 11 : 0;
        if (charm) {
            row += (skills ? CHARM_GAP : 0) + SLOT;
        }
        // 下の一枚の帯 (BandHud) の中では、持ち物の枠と同じ高さに並ぶ (下から 3)
        out.add(Request.side(Id.SKILL_SLOTS, Side.RIGHT, row, SLOT, jp.revati.skyabyss.client.hud.BandHud.on() ? 3 : 1, Request.of(Id.SLOTS_TL, Region.TOP_LEFT, row + 6, SLOT + 2)));
    }

    static boolean anySlot(SkillData d) {
        for (int i = 0; i < d.slotCount(); i++) {
            if (d.slot(i) != null) {
                return true;
            }
        }
        return false;
    }

    static boolean showMana(Player player, SkillData d) {
        if (d.mana >= 0 && d.mana < SkillManager.maxMana(player) - 0.01) {
            return true;
        }
        for (Skill s : Skill.values()) {
            if (s.active && d.knows(s)) {
                return true;
            }
        }
        return false;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.player.isSpectator()) {
            return;
        }
        SkillData d = SkillManager.get(mc.player);
        if (showMana(mc.player, d) && !jp.revati.skyabyss.client.hud.VitalsHud.manaInFoodSlot()) {
            renderMana(g, mc, d);
        }
        renderCharm(g, mc, d, delta);
        if (anySlot(d)) {
            // 今の職の武器を持っていない間は、枠を薄く出す (使えないことが分かるように。消さない。docs/design/48 の 3 章)
            jp.revati.skyabyss.rpg.skill.Job cur = jp.revati.skyabyss.rpg.skill.Job.byId(d.curJob);
            boolean idle = cur == null || jp.revati.skyabyss.rpg.skill.Job.held(mc.player) != cur;
            if (idle) {
                g.setColor(1f, 1f, 1f, 0.45f);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            }
            renderSlots(g, mc, d);
            if (idle) {
                g.setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    // 左の縁に小さな菱形 (5 画素)。効いている変化の種類ごとに一つ、上から縦に並べる
    // 色は変化の種類 (刻印 金・書き換え 橙・変異 紫・秘伝 水色。SkillChanges.Kind)
    public static void changeMark(GuiGraphics g, net.minecraft.world.entity.player.Player p, Skill s, int x, int y) {
        java.util.EnumSet<jp.revati.skyabyss.rpg.skill.SkillChanges.Kind> kinds = java.util.EnumSet.noneOf(jp.revati.skyabyss.rpg.skill.SkillChanges.Kind.class);
        for (var c : jp.revati.skyabyss.rpg.skill.SkillChanges.of(p, s)) {
            if (c.active()) {
                kinds.add(c.kind());
            }
        }
        if (kinds.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 210);
        int i = 0;
        for (var k : kinds) {
            diamond(g, x, y + i * 6, 0xFF000000 | k.rgb);
            i++;
        }
        g.pose().popPose();
    }

    // 刻印・書き換え・秘伝が効いている技の絵に、その色の縁 (1 画素。SkillChanges.frameColor)
    public static void changeFrame(GuiGraphics g, net.minecraft.world.entity.player.Player p, Skill s, int x, int y, int size) {
        int c = jp.revati.skyabyss.rpg.skill.SkillChanges.frameColor(p, s);
        if (c == 0) {
            return;
        }
        int a = 0xFF000000 | c;
        g.fill(x, y, x + size, y + 1, a);
        g.fill(x, y + size - 1, x + size, y + size, a);
        g.fill(x, y, x + 1, y + size, a);
        g.fill(x + size - 1, y, x + size, y + size, a);
    }

    static void diamond(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 2, y, x + 3, y + 5, 0xFF0A0E1E);
        g.fill(x, y + 2, x + 5, y + 3, 0xFF0A0E1E);
        g.fill(x + 1, y + 1, x + 4, y + 4, 0xFF0A0E1E);
        g.fill(x + 2, y + 1, x + 3, y + 4, c);
        g.fill(x + 1, y + 2, x + 4, y + 3, c);
    }

    private static void renderMana(GuiGraphics g, Minecraft mc, SkillData d) {
        double max = SkillManager.maxMana(mc.player);
        int[] m = jp.revati.skyabyss.rpg.client.GrowthHud.manaOrigin();
        if (m == null) {
            return;
        }
        int mx = m[0];
        int my = m[1];
        float fraction = max <= 0 ? 0 : (float) Mth.clamp(Math.max(0, d.mana) / max, 0, 1);
        g.blit(TEXTURE, mx, my, MANA_W, 4, 240, 440, 128, 8, TEX_W, TEX_H);
        int fill = Mth.clamp(Math.round((MANA_W - 2) * fraction), 0, MANA_W - 2);
        if (fill > 0) {
            g.blit(TEXTURE, mx + 1, my + 1, fill, 2, 240, 450, fill * 2, 4, TEX_W, TEX_H);
        }
        if (SkillClient.manaFlash > 0) {
            int a = (int) (SkillClient.manaFlash / 14f * 0xC0);
            g.fill(mx, my, mx + MANA_W, my + 4, (a << 24) | 0xD9779A);
        }
    }

    // 護符の枠 (2026-11-08 の声「護符付けてもスロット表示されない。画面に」): 技の枠の右に、付けている護符の絵・使うキー・待ちの覆い
    static final int CHARM_GAP = 4;

    static net.minecraft.world.item.ItemStack charm(Player p) {
        return jp.revati.skyabyss.item.accessory.Accessories.stack(p, jp.revati.skyabyss.item.accessory.AccessoryData.CHARM);
    }

    private static void renderCharm(GuiGraphics g, Minecraft mc, SkillData d, DeltaTracker delta) {
        net.minecraft.world.item.ItemStack stack = charm(mc.player);
        int[] o = slotOrigin();
        if (stack.isEmpty() || o == null) {
            return;
        }
        int x = o[0] + (anySlot(d) ? d.slotCount() * (SLOT + SLOT_GAP) - SLOT_GAP + 11 + CHARM_GAP : 0);
        int y = o[1];
        jp.revati.skyabyss.client.ui.Golden.slot(g, x, y, SLOT);
        g.renderItem(stack, x + 2, y + 2);
        boolean ready = jp.revati.skyabyss.item.GearLevel.meets(mc.player, stack);
        float cd = mc.player.getCooldowns().getCooldownPercent(stack.getItem(), delta.getGameTimeDeltaPartialTick(true));
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (!ready) {
            g.fill(x + 2, y + 2, x + 18, y + 18, 0x9A3A1010);
        } else if (cd > 0) {
            int h = Mth.clamp(Math.round(16f * cd), 1, 16);
            g.fill(x + 2, y + 2, x + 18, y + 2 + h, 0xB00A0E1E);
        }
        String k = jp.revati.skyabyss.client.accessory.AccessoryClient.CHARM_KEY.getTranslatedKeyMessage().getString();
        if (k.length() > 3) {
            k = k.substring(0, 3);
        }
        g.drawString(mc.font, k, x + SLOT - 1 - mc.font.width(k), y + SLOT - 8, cd > 0 || !ready ? 0x7682A6 : 0xE8D9A0, true);
        g.pose().popPose();
    }

    // 枠の置き場: HudManager が割り当てた長方形 (ホットバーの右、または左上)
    static int[] slotOrigin() {
        Rect r = HudManager.rect(Id.SKILL_SLOTS);
        if (r != null) {
            return new int[] {r.x(), r.y()};
        }
        r = HudManager.rect(Id.SLOTS_TL);
        return r == null ? null : new int[] {r.x() + 3, r.y()};
    }

    private static void renderSlots(GuiGraphics g, Minecraft mc, SkillData d) {
        int[] o = slotOrigin();
        if (o == null) {
            return;
        }
        Font font = mc.font;
        for (int i = 0; i < d.slotCount(); i++) {
            int x = o[0] + i * (SLOT + SLOT_GAP);
            int y = o[1];
            jp.revati.skyabyss.client.ui.Golden.slot(g, x, y, SLOT);
            Skill s = d.slot(i);
            if (s != null) {
                int rank = d.rank(s);
                drawIcon(g, jp.revati.skyabyss.rpg.skill.SkillChanges.icon(mc.player, s), x + 2, y + 2);
                changeFrame(g, mc.player, s, x + 2, y + 2, 16);
                // 今その技を変えているもの (刻印・書き換え・変異・秘伝) があれば左上に色の印 (SkillChanges)
                changeMark(g, mc.player, s, x + 1, y + 1);
                // マナが足りない: 青黒く沈める
                if (d.mana + 1e-4 < SkillManager.manaCostFor(mc.player, s)) {
                    g.fill(x + 2, y + 2, x + 18, y + 18, 0x9A101A3A);
                }
                // クールダウン: 上から暗く覆い、残りの秒を出す
                int left = d.cooldown(s);
                // 冴え (手の武器の特性) で縮んだ待ちを全体の長さにする (サーバーが待ちを決めるときと同じ計算)。
                // 待ちの途中で武器を持ち替えると全体が変わるが、覆いは 16 で止まるので崩れない
                int total = SkillManager.cooldownFor(mc.player, s);
                if (left > 0) {
                    int h = Mth.clamp(Math.round(16f * left / total), 1, 16);
                    g.fill(x + 2, y + 2, x + 18, y + 2 + h, 0xB00A0E1E);
                    String sec = String.valueOf((left + 19) / 20);
                    g.drawString(font, sec, x + 10 - font.width(sec) / 2, y + 6, 0xECE8DC, true);
                } else if (d.globalCooldown > 0) {
                    g.fill(x + 2, y + 2, x + 18, y + 18, 0x500A0E1E);
                }
            }
            // 光る: 使えた (白く)・使えなかった (赤紫の縁)
            int f = SkillClient.slotFlash[i];
            if (f > 0) {
                g.fill(x + 2, y + 2, x + 18, y + 18, ((int) (f / 8f * 0x90) << 24) | 0xDFFBFF);
            } else if (f < 0) {
                int a = (int) (-f / 10f * 0xE0) << 24;
                g.fill(x, y, x + SLOT, y + 1, a | 0xD9779A);
                g.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, a | 0xD9779A);
                g.fill(x, y, x + 1, y + SLOT, a | 0xD9779A);
                g.fill(x + SLOT - 1, y, x + SLOT, y + SLOT, a | 0xD9779A);
            }
            // 技の稽古: 使う枠を金の縁で呼吸させる
            if (i == jp.revati.skyabyss.client.tutorial.SkillLessonClient.castSlot(mc)) {
                jp.revati.skyabyss.client.tutorial.SkillLessonClient.frame(g, x, y, SLOT, SLOT);
            }
            // キーの名前 (右下、ホットバーの数と同じ置き方)
            Component key = SkillClientSetup.SLOT_KEYS[i].getTranslatedKeyMessage();
            String k = key.getString();
            if (k.length() > 3) {
                k = k.substring(0, 3);
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, k, x + SLOT - 1 - font.width(k), y + SLOT - 8, s == null ? 0x7682A6 : 0xB8B8C8, true);
            g.pose().popPose();
        }
    }

    // 32px の絵を 16 で描く
    static final ResourceLocation ICONS2 = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/skill/icons2.png");
    // 200 以上は icons3.png (技の変異。256x64。tools/gen/ui/gen_mutation_icons.py)
    static final ResourceLocation ICONS3 = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/skill/icons3.png");
    // 300 以上は icons4.png (武器の書き換え。256x256。tools/gen/ui/gen_rewrite_icons.py)
    static final ResourceLocation ICONS4 = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/skill/icons4.png");

    // 100 以上は icons2.png (淵・前世の技・木の印・紋・職の新しい技。256x192)
    public static void drawIcon(GuiGraphics g, int index, int x, int y) {
        drawIcon(g, index, x, y, 16);
    }

    static void drawIcon(GuiGraphics g, int index, int x, int y, int size) {
        if (index >= 300) {
            int n = index - 300;
            g.blit(ICONS4, x, y, size, size, (n % 8) * 32, (n / 8) * 32, 32, 32, 256, 256);
        } else if (index >= 200) {
            int n = index - 200;
            g.blit(ICONS3, x, y, size, size, (n % 8) * 32, (n / 8) * 32, 32, 32, 256, 64);
        } else if (index >= 100) {
            int n = index - 100;
            g.blit(ICONS2, x, y, size, size, (n % 8) * 32, (n / 8) * 32, 32, 32, 256, 192);
        } else {
            g.blit(ICONS, x, y, size, size, (index % 8) * 32, (index / 8) * 32, 32, 32, 256, 192);
        }
    }

}

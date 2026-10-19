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
    // gui/skill/skills.png (1024x512、2 倍の密度)。配置は tools/gen_skill_assets.py と同じ
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
        if (!anySlot(SkillManager.get(mc.player))) {
            return;
        }
        int row = SkillRules.SLOTS * (SLOT + SLOT_GAP) - SLOT_GAP;
        out.add(Request.side(Id.SKILL_SLOTS, Side.RIGHT, row, SLOT, 1, Request.of(Id.SLOTS_TL, Region.TOP_LEFT, row + 6, SLOT + 2)));
    }

    static boolean anySlot(SkillData d) {
        for (int i = 0; i < d.slots.length; i++) {
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
        if (showMana(mc.player, d)) {
            renderMana(g, mc, d);
        }
        if (anySlot(d)) {
            renderSlots(g, mc, d);
        }
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
        for (int i = 0; i < d.slots.length; i++) {
            int x = o[0] + i * (SLOT + SLOT_GAP);
            int y = o[1];
            g.blit(TEXTURE, x, y, SLOT, SLOT, 468, 384, 40, 40, TEX_W, TEX_H);
            Skill s = d.slot(i);
            if (s != null) {
                int rank = d.rank(s);
                drawIcon(g, s.iconIndex(), x + 2, y + 2);
                // マナが足りない: 青黒く沈める
                if (d.mana + 1e-4 < SkillRules.manaCost(s, rank)) {
                    g.fill(x + 2, y + 2, x + 18, y + 18, 0x9A101A3A);
                }
                // クールダウン: 上から暗く覆い、残りの秒を出す
                int left = d.cooldown(s);
                // 冴え (手の武器の特性) で縮んだ待ちを全体の長さにする (サーバーが待ちを決めるときと同じ計算)。
                // 待ちの途中で武器を持ち替えると全体が変わるが、覆いは 16 で止まるので崩れない
                int total = jp.revati.skyabyss.item.affix.AffixEvents.scaleCooldown(mc.player, SkillRules.cooldownTicks(s, rank));
                if (left > 0) {
                    int h = Mth.clamp(Math.round(16f * left / total), 1, 16);
                    g.fill(x + 2, y + 2, x + 18, y + 2 + h, 0xB0101418);
                    String sec = String.valueOf((left + 19) / 20);
                    g.drawString(font, sec, x + 10 - font.width(sec) / 2, y + 6, 0xE6E2D6, true);
                } else if (d.globalCooldown > 0) {
                    g.fill(x + 2, y + 2, x + 18, y + 18, 0x50101418);
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
            // キーの名前 (右下、ホットバーの数と同じ置き方)
            Component key = SkillClientSetup.SLOT_KEYS[i].getTranslatedKeyMessage();
            String k = key.getString();
            if (k.length() > 3) {
                k = k.substring(0, 3);
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, k, x + SLOT - 1 - font.width(k), y + SLOT - 8, s == null ? 0x6A707C : 0xB8B8C8, true);
            g.pose().popPose();
        }
    }

    // 32px の絵を 16 で描く
    static void drawIcon(GuiGraphics g, int index, int x, int y) {
        g.blit(ICONS, x, y, 16, 16, (index % 8) * 32, (index / 8) * 32, 32, 32, 256, 64);
    }
}

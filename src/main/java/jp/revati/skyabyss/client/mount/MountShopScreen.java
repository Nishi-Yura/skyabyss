package jp.revati.skyabyss.client.mount;

import jp.revati.skyabyss.client.facility.FacilityUi;
import jp.revati.skyabyss.client.ui.TextClip;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.mount.MountEntity;
import jp.revati.skyabyss.mount.MountLook;
import jp.revati.skyabyss.mount.MountNetwork;
import jp.revati.skyabyss.mount.MountSetup;
import jp.revati.skyabyss.mount.MountStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 厩舎の画面 (docs/design/38 の 5 章)。左に見た目の一覧 (押すと買う・選ぶ)、右に今の見た目の姿 (一覧に触れるとその見た目)、
// 速さ・跳び・体力の段 (押すと一段上げる)、笛をもらうボタン。決めるのはサーバー (MountService)。押すたびに新しい持ち物が届いて描き直す
public class MountShopScreen extends Screen {
    private static final int LOOK_H = 24;
    private static final int STAT_H = 18;

    private MountNetwork.View view;
    private int x0, y0, w, h;
    private MountEntity preview;

    public MountShopScreen(MountNetwork.View view) {
        super(Component.translatable("gui.skyabyss.mount.title"));
        this.view = view;
    }

    public void update(MountNetwork.View v) {
        this.view = v;
    }

    @Override
    protected void init() {
        w = Math.min(400, width - 16);
        h = Math.min(236, height - 8);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new MountNetwork.Closed());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
    }

    private int leftW() {
        return Math.min(176, w / 2 - 6);
    }

    private int rightX() {
        return x0 + 10 + leftW() + 8;
    }

    private int rightW() {
        return x0 + w - 10 - rightX();
    }

    private int lookY() {
        return y0 + 42;
    }

    private int statY() {
        return y0 + 34 + (h - 100 > 150 ? 92 : 78) + 6;
    }

    private int previewH() {
        return h - 100 > 150 ? 92 : 78;
    }

    private int whistleY() {
        return statY() + 3 * STAT_H + 4;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        FacilityUi.panel(g, x0, y0, w, h);
        FacilityUi.gem(g, x0 + 12, y0 + 13);
        g.drawString(font, title, x0 + 18, y0 + 9, FacilityUi.TEXT, false);
        String coins = Integer.toString(view.coins());
        int cx = x0 + w - 14 - font.width(coins);
        g.drawString(font, coins, cx, y0 + 9, FacilityUi.GOLD, false);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), cx - 18, y0 + 5);

        // 見た目の一覧
        int lx = x0 + 10;
        int lw = leftW();
        g.drawString(font, Component.translatable("gui.skyabyss.mount.looks"), lx, y0 + 28, FacilityUi.TEAL, false);
        FacilityUi.well(g, lx, lookY(), lw, MountLook.values().length * LOOK_H);
        MountLook hoverLook = null;
        for (MountLook look : MountLook.values()) {
            int ry = lookY() + look.ordinal() * LOOK_H;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= ry && mouseY < ry + LOOK_H;
            boolean chosen = view.owns(look) && view.chosen() == look.ordinal();
            if (chosen) {
                g.fill(lx, ry, lx + lw, ry + LOOK_H, 0xFF1E3A44);
            } else if (hover) {
                g.fill(lx, ry, lx + lw, ry + LOOK_H, FacilityUi.ROW_HOVER);
            } else if (look.ordinal() % 2 == 1) {
                g.fill(lx, ry, lx + lw, ry + LOOK_H, FacilityUi.ROW);
            }
            if (hover) {
                hoverLook = look;
            }
            lookRow(g, font, look, lx, ry, lw, chosen);
        }

        // 姿
        int rx = rightX();
        int rw = rightW();
        MountLook shown = hoverLook != null ? hoverLook : MountLook.byOrdinal(view.chosen());
        FacilityUi.well(g, rx, y0 + 34, rw, previewH());
        renderPreview(g, shown, rx, y0 + 34, rw, previewH(), mouseX, mouseY);
        TextClip.line(g, font, Component.translatable(shown.nameKey()), rx + 4, y0 + 38, rw - 8, FacilityUi.TEXT, false);
        TextClip.line(g, font, Component.translatable(shown.nameKey() + ".desc"), rx + 4, y0 + 34 + previewH() - 11, rw - 8, FacilityUi.DIM, false);

        // 性能の段
        for (int k = 0; k < 3; k++) {
            statRow(g, font, k, rx, statY() + k * STAT_H, rw, mouseX, mouseY);
        }
        // 笛
        int wy = whistleY();
        boolean hw = mouseX >= rx && mouseX < rx + rw && mouseY >= wy && mouseY < wy + 14;
        g.fill(rx, wy, rx + rw, wy + 14, view.whistle() ? 0xFF161B22 : hw ? 0xFF2A5E6A : 0xFF243440);
        g.renderOutline(rx, wy, rw, 14, view.whistle() ? FacilityUi.LINE : FacilityUi.TEAL);
        TextClip.line(g, font, Component.translatable(view.whistle() ? "gui.skyabyss.mount.whistle_have" : "gui.skyabyss.mount.whistle_get"),
                rx + 4, wy + 3, rw - 8, view.whistle() ? FacilityUi.DIM : 0xFFFFFFFF, false);
        TextClip.line(g, font, Component.translatable("gui.skyabyss.mount.hint"), x0 + 12, y0 + h - 13, w - 24, FacilityUi.DIM, false);
    }

    private void lookRow(GuiGraphics g, Font font, MountLook look, int x, int y, int rw, boolean chosen) {
        boolean owned = view.owns(look);
        boolean locked = !owned && view.chapter() < look.chapter;
        int color = locked ? FacilityUi.DIM : FacilityUi.TEXT;
        TextClip.line(g, font, Component.translatable(look.nameKey()), x + 6, y + 3, rw - 70, color, false);
        Component sub;
        int subColor;
        if (chosen) {
            sub = Component.translatable("gui.skyabyss.mount.chosen");
            subColor = FacilityUi.TEAL;
        } else if (owned) {
            sub = Component.translatable("gui.skyabyss.mount.owned");
            subColor = FacilityUi.TEAL;
        } else if (locked) {
            sub = Component.translatable("gui.skyabyss.mount.locked", look.chapter - 1);
            subColor = FacilityUi.DIM;
        } else {
            sub = Component.translatable("gui.skyabyss.mount.for_sale");
            subColor = FacilityUi.DIM;
        }
        TextClip.line(g, font, sub, x + 6, y + 13, rw - 70, subColor, false);
        if (!owned && !locked) {
            String s = Integer.toString(look.price);
            int tw = 6 + 18 + font.width(s) + 2;
            int tx = x + rw - 4 - tw;
            FacilityUi.tag(g, tx, y + 3, tw, 18);
            g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), tx + 2, y + 4);
            g.drawString(font, s, tx + 20, y + 8, view.coins() >= look.price ? FacilityUi.GOLD : FacilityUi.LACK, false);
        }
    }

    private void statRow(GuiGraphics g, Font font, int kind, int x, int y, int rw, int mx, int my) {
        int tier = view.tier(kind);
        boolean maxed = tier >= MountStats.TIERS;
        g.drawString(font, Component.translatable("mount.skyabyss.stat." + MountStats.KEYS[kind]), x, y + 5, FacilityUi.TEXT, false);
        int px = x + 36;
        for (int i = 0; i < MountStats.TIERS; i++) {
            int bx = px + i * 9;
            g.fill(bx, y + 5, bx + 7, y + 12, i < tier ? FacilityUi.GOLD : 0xFF2A2F38);
            g.renderOutline(bx, y + 5, 7, 7, FacilityUi.LINE);
        }
        int bw = rw - 36 - MountStats.TIERS * 9 - 4;
        int bx = x + rw - bw;
        if (maxed) {
            TextClip.line(g, font, Component.translatable("gui.skyabyss.mount.max"), bx + 2, y + 5, bw - 2, FacilityUi.TEAL, false);
            return;
        }
        int price = MountStats.price(kind, tier + 1);
        boolean hover = mx >= bx && mx < bx + bw && my >= y && my < y + STAT_H - 2;
        g.fill(bx, y, bx + bw, y + STAT_H - 2, hover ? 0xFF2A5E6A : 0xFF1C222C);
        g.renderOutline(bx, y, bw, STAT_H - 2, hover ? FacilityUi.TEAL : FacilityUi.LINE);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), bx + 1, y - 1);
        g.drawString(font, Integer.toString(price), bx + 19, y + 4, view.coins() >= price ? FacilityUi.GOLD : FacilityUi.LACK, false);
    }

    private void renderPreview(GuiGraphics g, MountLook look, int x, int y, int pw, int ph, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (preview == null || preview.level() != mc.level) {
            preview = new MountEntity(MountSetup.MOUNT.get(), mc.level);
        }
        preview.setLook(look);
        preview.tickCount = (int) (mc.level.getGameTime());
        preview.yBodyRot = 200f;
        preview.setYRot(200f);
        preview.yHeadRot = 200f;
        preview.yBodyRotO = 200f;
        preview.yRotO = 200f;
        preview.yHeadRotO = 200f;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y + 8, x + pw, y + ph, ph / 3, 0.0625f, mx, my, preview);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int lx = x0 + 10;
            int lw = leftW();
            if (mx >= lx && mx < lx + lw && my >= lookY() && my < lookY() + MountLook.values().length * LOOK_H) {
                MountLook look = MountLook.values()[(int) ((my - lookY()) / LOOK_H)];
                if (view.owns(look)) {
                    if (view.chosen() != look.ordinal()) {
                        send(MountNetwork.CHOOSE_LOOK, look.ordinal());
                    }
                } else if (view.chapter() >= look.chapter) {
                    send(MountNetwork.BUY_LOOK, look.ordinal());
                }
                return true;
            }
            int rx = rightX();
            int rw = rightW();
            for (int k = 0; k < 3; k++) {
                int sy = statY() + k * STAT_H;
                if (mx >= rx && mx < rx + rw && my >= sy && my < sy + STAT_H - 2 && view.tier(k) < MountStats.TIERS) {
                    send(MountNetwork.BUY_TIER, k);
                    return true;
                }
            }
            int wy = whistleY();
            if (mx >= rx && mx < rx + rw && my >= wy && my < wy + 14 && !view.whistle()) {
                send(MountNetwork.WHISTLE, 0);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void send(int action, int arg) {
        PacketDistributor.sendToServer(new MountNetwork.Act(action, arg));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }
}

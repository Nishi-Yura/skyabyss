package jp.revati.skyabyss.client.mount;

import jp.revati.skyabyss.client.facility.FacilityUi;
import jp.revati.skyabyss.client.ui.Golden;
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
// 速さ・跳びの段 (押すと一段上げる)、笛をもらう釦。決めるのはサーバー (MountService)。押すたびに新しい持ち物が届いて描き直す。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 見出しの帯に名前と銭、脇の列 (左) に見た目の一覧 (一行 26)、主の列 (右) に姿 (89)・段 (一行 21)・笛・手引き
public class MountShopScreen extends Screen {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    private static final Golden.Box LEFT = LAYOUT.body().minor(true);
    private static final Golden.Box RIGHT = LAYOUT.body().major(false);
    private static final Golden.Box LOOKS = LEFT.afterTop(Golden.ROW);
    private static final Golden.Box PREVIEW = RIGHT.top(Golden.S89);
    private static final int LOOK_H = 2 * Golden.ROW;
    private static final int STAT_H = Golden.S21;

    private MountNetwork.View view;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    // 見た目の一覧の先頭の行 (見た目が 10 になり、枠に入りきらないのでホイールで送る)
    private int scroll;
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
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        // 今の見た目が見える所から
        scroll = Math.max(0, Math.min(maxScroll(), view.chosen() - visibleRows() + 1));
    }

    private int visibleRows() {
        return Math.min(MountLook.values().length, (LOOKS.h() - 4) / LOOK_H);
    }

    private int maxScroll() {
        return Math.max(0, MountLook.values().length - visibleRows());
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

    private int lookY() {
        return LOOKS.y() + 2;
    }

    private int statY() {
        return PREVIEW.bottom() + Golden.S5;
    }

    private int whistleY() {
        return statY() + MountStats.KEYS.length * STAT_H + Golden.S5;
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        Font font = Minecraft.getInstance().font;
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        int hy = Golden.textY(head.y(), head.h());
        FacilityUi.gem(g, head.x() + 4, head.y() + head.h() / 2);
        g.drawString(font, title, head.x() + 10, hy, FacilityUi.TEXT, false);
        String coins = Integer.toString(view.coins());
        int cx = head.right() - Golden.S5 - font.width(coins);
        g.drawString(font, coins, cx, hy, FacilityUi.GOLD, false);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), cx - 18, head.y() + (head.h() - 16) / 2);

        // 見た目の一覧
        int lx = LOOKS.x() + 1;
        int lw = LOOKS.w() - 2;
        g.drawString(font, Component.translatable("gui.skyabyss.mount.looks"), LEFT.x() + Golden.S5, Golden.textY(LEFT.y(), Golden.ROW), FacilityUi.TEAL, false);
        int rows = visibleRows();
        Golden.recess(g, LOOKS);
        // 上と下にまだあるときの印
        if (scroll > 0) {
            g.drawString(font, "▲", LEFT.right() - 20, Golden.textY(LEFT.y(), Golden.ROW), FacilityUi.TEAL, false);
        }
        if (scroll < maxScroll()) {
            g.drawString(font, "▼", LEFT.right() - 10, Golden.textY(LEFT.y(), Golden.ROW), FacilityUi.TEAL, false);
        }
        MountLook hoverLook = null;
        for (int i = 0; i < rows && scroll + i < MountLook.values().length; i++) {
            MountLook look = MountLook.values()[scroll + i];
            int ry = lookY() + i * LOOK_H;
            boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= ry && mouseY < ry + LOOK_H;
            boolean chosen = view.owns(look) && view.chosen() == look.ordinal();
            if (chosen) {
                Golden.selected(g, new Golden.Box(lx, ry, lw, LOOK_H));
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

        // 姿の欄 (姿そのものは変換の外で描く: 切り抜きが板の変換を通らないため)
        int rx = RIGHT.x();
        int rw = RIGHT.w();
        MountLook shown = hoverLook != null ? hoverLook : MountLook.byOrdinal(view.chosen());
        Golden.recess(g, PREVIEW);
        fit.end(g);
        int[] pr = fit.screenRect(PREVIEW.x() + 1, PREVIEW.y() + 1, PREVIEW.w() - 2, PREVIEW.h() - 2);
        renderPreview(g, shown, pr[0], pr[1], pr[2], pr[3], screenX, screenY);
        fit.begin(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        TextClip.line(g, font, Component.translatable(shown.nameKey()), rx + Golden.S5, Golden.textY(PREVIEW.y() + 2, Golden.ROW), rw - 2 * Golden.S5, FacilityUi.TEXT, false);
        TextClip.line(g, font, Component.translatable(shown.nameKey() + ".desc"), rx + Golden.S5, Golden.textY(PREVIEW.bottom() - 2 - Golden.ROW, Golden.ROW), rw - 2 * Golden.S5,
                FacilityUi.DIM, false);
        g.pose().popPose();

        // 性能の段 (速さ・跳び。体力は段がない)
        for (int k = 0; k < MountStats.KEYS.length; k++) {
            statRow(g, font, k, rx + Golden.S5, statY() + k * STAT_H, rw - 2 * Golden.S5, mouseX, mouseY);
        }
        // 笛
        int wy = whistleY();
        boolean hw = mouseX >= rx && mouseX < rx + rw && mouseY >= wy && mouseY < wy + Golden.BTN;
        g.fill(rx, wy, rx + rw, wy + Golden.BTN, view.whistle() ? 0xFF161B22 : hw ? 0xFF2A5E6A : 0xFF243440);
        g.renderOutline(rx, wy, rw, Golden.BTN, view.whistle() ? FacilityUi.LINE : FacilityUi.TEAL);
        TextClip.line(g, font, Component.translatable(view.whistle() ? "gui.skyabyss.mount.whistle_have" : "gui.skyabyss.mount.whistle_get"),
                rx + Golden.S5, Golden.textY(wy, Golden.BTN), rw - 2 * Golden.S5, view.whistle() ? FacilityUi.DIM : 0xFFFFFFFF, false);
        TextClip.line(g, font, Component.translatable("gui.skyabyss.mount.hint"), rx + Golden.S5, Golden.textY(RIGHT.bottom() - Golden.ROW, Golden.ROW), rw - 2 * Golden.S5,
                0xFF000000 | Golden.DIM, false);
        fit.end(g);
    }

    private void lookRow(GuiGraphics g, Font font, MountLook look, int x, int y, int rw, boolean chosen) {
        boolean owned = view.owns(look);
        boolean locked = !owned && view.chapter() < look.chapter;
        int color = locked ? FacilityUi.DIM : FacilityUi.TEXT;
        String price = Integer.toString(look.price);
        int tw = !owned && !locked ? 6 + 18 + font.width(price) + 2 : 0;
        TextClip.line(g, font, Component.translatable(look.nameKey()), x + Golden.S5, Golden.textY(y, Golden.ROW), rw - Golden.S8 - tw - 4, color, false);
        Component sub;
        int subColor;
        if (chosen) {
            sub = Component.translatable("gui.skyabyss.mount.chosen");
            subColor = FacilityUi.TEAL;
        } else if (owned) {
            sub = Component.translatable("gui.skyabyss.mount.owned");
            subColor = FacilityUi.TEAL;
        } else if (locked) {
            sub = Component.translatable(look.lockedKey("gui.skyabyss.mount.locked"), look.chapter - 1);
            subColor = FacilityUi.DIM;
        } else {
            sub = Component.translatable("gui.skyabyss.mount.for_sale");
            subColor = FacilityUi.DIM;
        }
        TextClip.line(g, font, sub, x + Golden.S5, Golden.textY(y + Golden.ROW, Golden.ROW), rw - Golden.S8 - tw - 4, subColor, false);
        if (tw > 0) {
            int tx = x + rw - 3 - tw;
            FacilityUi.tag(g, tx, y + 4, tw, 18);
            g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), tx + 2, y + 5);
            g.drawString(font, price, tx + 20, Golden.textY(y, LOOK_H), view.coins() >= look.price ? FacilityUi.GOLD : FacilityUi.LACK, false);
        }
    }

    // 段の一行 (高さ 21): 名前・段の箱・上げる札 (銭と値段)
    private void statRow(GuiGraphics g, Font font, int kind, int x, int y, int rw, int mx, int my) {
        int tier = view.tier(kind);
        boolean maxed = tier >= MountStats.tiers(kind);
        g.drawString(font, Component.translatable("mount.skyabyss.stat." + MountStats.KEYS[kind]), x, Golden.textY(y, STAT_H), FacilityUi.TEXT, false);
        int px = x + Golden.S34;
        for (int i = 0; i < MountStats.tiers(kind); i++) {
            int bx = px + i * 9;
            g.fill(bx, y + 7, bx + 7, y + 14, i < tier ? FacilityUi.GOLD : 0xFF2A2F38);
            g.renderOutline(bx, y + 7, 7, 7, FacilityUi.LINE);
        }
        int bw = rw - Golden.S34 - MountStats.MAX_TIERS * 9 - Golden.S5;
        int bx = x + rw - bw;
        if (maxed) {
            TextClip.line(g, font, Component.translatable("gui.skyabyss.mount.max"), bx + 2, Golden.textY(y, STAT_H), bw - 2, FacilityUi.TEAL, false);
            return;
        }
        int price = MountStats.price(kind, tier + 1);
        boolean hover = statHit(kind, mx, my);
        g.fill(bx, y + 1, bx + bw, y + STAT_H - 1, hover ? 0xFF2A5E6A : 0xFF1C222C);
        g.renderOutline(bx, y + 1, bw, STAT_H - 2, hover ? FacilityUi.TEAL : FacilityUi.LINE);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), bx + 1, y + 2);
        g.drawString(font, Integer.toString(price), bx + 19, Golden.textY(y, STAT_H), view.coins() >= price ? FacilityUi.GOLD : FacilityUi.LACK, false);
    }

    private boolean statHit(int kind, double mx, double my) {
        int y = statY() + kind * STAT_H;
        int bw = RIGHT.w() - 2 * Golden.S5 - Golden.S34 - MountStats.MAX_TIERS * 9 - Golden.S5;
        int bx = RIGHT.right() - Golden.S5 - bw;
        return mx >= bx && mx < bx + bw && my >= y && my < y + STAT_H;
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
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y + 8, x + pw, y + ph, Math.round(ph / 3f), 0.0625f, mx, my, preview);
    }

    @Override
    public boolean mouseScrolled(double sx, double sy, double dx, double dy) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (LOOKS.contains(mx, my) && dy != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(sx, sy, dx, dy);
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (button == 0) {
            int lx = LOOKS.x() + 1;
            int lw = LOOKS.w() - 2;
            int index = scroll + (int) ((my - lookY()) / LOOK_H);
            if (mx >= lx && mx < lx + lw && my >= lookY() && my < lookY() + visibleRows() * LOOK_H && index < MountLook.values().length) {
                MountLook look = MountLook.values()[index];
                if (view.owns(look)) {
                    if (view.chosen() != look.ordinal()) {
                        send(MountNetwork.CHOOSE_LOOK, look.ordinal());
                    }
                } else if (view.chapter() >= look.chapter) {
                    send(MountNetwork.BUY_LOOK, look.ordinal());
                }
                return true;
            }
            for (int k = 0; k < MountStats.KEYS.length; k++) {
                if (statHit(k, mx, my) && view.tier(k) < MountStats.tiers(k)) {
                    send(MountNetwork.BUY_TIER, k);
                    return true;
                }
            }
            int wy = whistleY();
            if (mx >= RIGHT.x() && mx < RIGHT.right() && my >= wy && my < wy + Golden.BTN && !view.whistle()) {
                send(MountNetwork.WHISTLE, 0);
                return true;
            }
        }
        return super.mouseClicked(sx, sy, button);
    }

    private void send(int action, int arg) {
        PacketDistributor.sendToServer(new MountNetwork.Act(action, arg));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }
}

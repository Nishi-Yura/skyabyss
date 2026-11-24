package jp.revati.skyabyss.client.mount;

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
import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.ShopStage;

// 厩舎の画面 (docs/design/38 の 5 章)。左に見た目の一覧 (押すと買う・選ぶ)、右に今の見た目の姿 (一覧に触れるとその見た目)、
// 速さ・跳びの段 (押すと一段上げる)、笛をもらう釦。決めるのはサーバー (MountService)。押すたびに新しい持ち物が届いて描き直す。
// 組み方は店の類の「帳面と台」(client/ui/ShopStage。docs/design/56 の 2.1): 左の帳面に見た目の一覧 (名前 …… 値段。乗せた行・今の見た目は桃色)、
// 右の黒い紙に今の見た目 (一覧に触れるとその見た目) を台に立てて、名前・説明・性能の段 (押すと一段上げる)・笛・名付け。手持ちは山の上
public class MountShopScreen extends Screen {
    private static final int STAT_H = 14;
    private static final int RX = ShopStage.RIGHT_X;
    private static final int RW = ShopStage.RIGHT_W;
    private static final int STAT_Y = 145;
    private static final int WHISTLE_Y = STAT_Y + 3 * STAT_H + 2;
    private static final int NAME_W = 52;
    private static final int TIER_STEP = 5;
    private static final int LABEL_W = 50;
    private static final int UP_W = RW - LABEL_W - MountStats.MAX_TIERS * TIER_STEP - 4;

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
        fit.fit(width, height, ShopStage.W, ShopStage.H, 3f);
        // 今の見た目が見える所から
        scroll = Math.max(0, Math.min(maxScroll(), view.chosen() - visibleRows() + 1));
    }

    private int visibleRows() {
        return Math.min(MountLook.values().length, ShopStage.ROWS);
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
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, ShopStage.W, ShopStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        Font font = Minecraft.getInstance().font;
        fit.begin(g);
        ShopStage.backdrop(g, fit, width, height);
        // 帳面の上の辺: 一覧の見出し (つまみの位置に札を一枚)
        Paper.tab(g, font, ShopStage.tabX(0), ShopStage.TAB_Y, 74, 13, Component.translatable("gui.skyabyss.mount.looks"), true, false);
        // 見た目の一覧
        int rows = visibleRows();
        int hoverRow = ShopStage.rowAt(mouseX, mouseY);
        MountLook hoverLook = hoverRow >= 0 && scroll + hoverRow < MountLook.values().length ? MountLook.values()[scroll + hoverRow] : null;
        int hotIndex = hoverLook != null ? hoverLook.ordinal() : view.chosen();
        for (int i = 0; i < rows && scroll + i < MountLook.values().length; i++) {
            MountLook look = MountLook.values()[scroll + i];
            boolean hot = look.ordinal() == hotIndex;
            if (hot) {
                ShopStage.hot(g, i);
            }
            lookRow(g, font, look, ShopStage.rowY(i), hot);
        }
        ShopStage.scroll(g, scroll, MountLook.values().length);
        ShopStage.hint(g, font, Component.translatable("gui.skyabyss.mount.hint"));
        // 右: 名前と絆
        ShopStage.title(g, font, title);
        Component bond = Component.translatable("gui.skyabyss.mount.bond", Component.translatable("gui.skyabyss.mount.bond." + view.bond()),
                String.format("%.1f", view.ridden() / 1000.0));
        TextClip.line(g, font, bond, RX, 25, RW, 0xFF000000 | Paper.ON_BLACK_SOFT, false);
        MountLook shown = hoverLook != null ? hoverLook : MountLook.byOrdinal(view.chosen());
        // 台 (姿そのものは変換の外で描く: 切り抜きが変換を通らないため)
        ShopStage.plinth(g, net.minecraft.world.item.ItemStack.EMPTY, mouseX, mouseY);
        fit.end(g);
        int[] pr = fit.screenRect(272, 34, 116, 66);
        renderPreview(g, shown, pr[0], pr[1], pr[2], pr[3], screenX, screenY);
        fit.begin(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        TextClip.line(g, font, Component.translatable(shown.nameKey()), RX, 113, RW, 0xFFFFF6EA, false);
        // 説明は二行まで折り返す (字は等倍。三行目からは流す)
        java.util.List<net.minecraft.util.FormattedCharSequence> desc = jp.revati.skyabyss.client.ui.Marquee.joinRest(
                jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable(shown.nameKey() + ".desc"), RW), 2);
        for (int l = 0; l < desc.size(); l++) {
            jp.revati.skyabyss.client.ui.Marquee.draw(g, font, desc.get(l), RX, 124 + l * 10, RW, 0xFF000000 | Paper.ON_BLACK_SOFT, false);
        }
        // 性能の段 (速さ・跳び・乗れる人数。体力は段がない)
        for (int k = 0; k < MountStats.KEYS.length; k++) {
            statRow(g, font, k, RX, STAT_Y + k * STAT_H, mouseX, mouseY);
        }
        // 笛と名付け (絆 II から)
        boolean canName = view.bond() >= 2;
        int ww = canName ? RW - NAME_W - 4 : RW;
        boolean hw = mouseX >= RX && mouseX < RX + ww && mouseY >= WHISTLE_Y && mouseY < WHISTLE_Y + Golden.BTN;
        Paper.button(g, font, RX, WHISTLE_Y, ww, Golden.BTN, Component.translatable(view.whistle() ? "gui.skyabyss.mount.whistle_have" : "gui.skyabyss.mount.whistle_get"),
                !view.whistle(), Paper.state(!view.whistle(), hw));
        if (canName) {
            int nx = RX + RW - NAME_W;
            boolean hn = mouseX >= nx && mouseX < nx + NAME_W && mouseY >= WHISTLE_Y && mouseY < WHISTLE_Y + Golden.BTN;
            Paper.button(g, font, nx, WHISTLE_Y, NAME_W, Golden.BTN, Component.translatable("gui.skyabyss.mount.name_button"), false, Paper.state(true, hn));
        }
        ShopStage.purse(g, font, new ItemStack(FacilitySetup.BELL_COIN.get()), view.coins());
        g.pose().popPose();
        fit.end(g);
    }

    private void lookRow(GuiGraphics g, Font font, MountLook look, int y, boolean hot) {
        boolean owned = view.owns(look);
        boolean locked = !owned && view.chapter() < look.chapter;
        boolean chosen = owned && view.chosen() == look.ordinal();
        String price = Integer.toString(look.price);
        boolean sale = !owned && !locked;
        int x = ShopStage.LIST_X + 4;
        int right = ShopStage.LIST_X + ShopStage.LIST_W - 4 - (sale ? 17 + font.width(price) : 0);
        int w = right - x - 6;
        Component name = Component.literal(Component.translatable(look.nameKey()).getString());
        TextClip.line(g, font, name, x, y + 2, w, hot ? 0xFFFFF6EA : 0xFF000000 | (locked ? Paper.INK_SOFT : Paper.INK), false);
        Component sub;
        if (chosen) {
            sub = Component.translatable("gui.skyabyss.mount.chosen");
        } else if (owned) {
            sub = Component.translatable("gui.skyabyss.mount.owned");
        } else if (locked) {
            sub = Component.translatable(look.lockedKey("gui.skyabyss.mount.locked"), look.chapter - 1);
        } else {
            sub = Component.translatable("gui.skyabyss.mount.for_sale");
        }
        // 乗れる人数の最大 (見た目ごと)
        sub = Component.translatable("gui.skyabyss.mount.seats_max", sub, look.maxSeats);
        TextClip.line(g, font, sub, x, y + 13, w, hot ? 0xFFFFF6EA : 0xFF000000 | (chosen || owned ? Paper.INK_HEAD : Paper.INK_SOFT), false);
        if (sale) {
            ShopStage.leader(g, x + Math.min(font.width(name), w) + 4, right - 2, y + 8, hot);
            g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), right, y + 3);
            boolean enough = view.coins() >= look.price;
            g.drawString(font, price, right + 17, y + 8, hot ? (enough ? ShopStage.ON_PINK : 0xF0607A) : enough ? Paper.INK : ShopStage.LACK, false);
        }
    }

    // 段の一行 (高さ 16): 名前・段の菱・上げる札 (銭と値段)
    private void statRow(GuiGraphics g, Font font, int kind, int x, int y, int mx, int my) {
        int tier = view.tier(kind);
        boolean maxed = tier >= MountStats.tiers(MountStats.ROW_KIND[kind]);
        Component label = Component.translatable("mount.skyabyss.stat." + MountStats.KEYS[kind]);
        if (MountStats.ROW_KIND[kind] == MountStats.SEATS) {
            // 人数の行: 今の見た目で何人乗れるか (荷車でない見た目は 2 人まで。2026-11-08 の声「2人しか乗れないのに、強化6人まで見えてたら見にくい」)
            jp.revati.skyabyss.mount.MountLook chosen = jp.revati.skyabyss.mount.MountLook.values()[Math.max(0, Math.min(jp.revati.skyabyss.mount.MountLook.values().length - 1, view.chosen()))];
            label = Component.translatable("mount.skyabyss.stat.seats_now", MountStats.seats(tier, chosen));
        }
        TextClip.line(g, font, label, x, y + 4, LABEL_W - 2, 0xFF000000 | Golden.TEXT, false);
        int px = x + LABEL_W;
        for (int i = 0; i < MountStats.tiers(MountStats.ROW_KIND[kind]); i++) {
            Paper.pin(g, px + i * TIER_STEP + 2, y + 8, 2, i < tier);
        }
        int bx = x + RW - UP_W;
        if (maxed) {
            TextClip.line(g, font, Component.translatable("gui.skyabyss.mount.max"), bx + 2, y + 4, UP_W - 2, 0xFF000000 | Paper.ON_BLACK_PINK, false);
            return;
        }
        int price = MountStats.price(MountStats.ROW_KIND[kind], tier + 1);
        boolean hover = statHit(kind, mx, my);
        int[] d = Paper.slip(g, bx, y + 1, UP_W, 13, hover ? Paper.CREAM_HI : Paper.CREAM, 0, hover ? Paper.HOVER : Paper.REST);
        g.pose().pushPose();
        g.pose().translate(bx + 3 + d[0], y + 2 + d[1], 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.renderItem(new ItemStack(FacilitySetup.BELL_COIN.get()), 0, 0);
        g.pose().popPose();
        TextClip.line(g, font, Component.literal(Integer.toString(price)), bx + 16 + d[0], y + 4 + d[1], UP_W - 18, view.coins() >= price ? 0xFF000000 | Paper.INK : 0xFF000000 | ShopStage.LACK, false);
    }

    private boolean statHit(int kind, double mx, double my) {
        int y = STAT_Y + kind * STAT_H;
        int bx = RX + RW - UP_W;
        return mx >= bx && mx < bx + UP_W && my >= y && my < y + STAT_H;
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
        if (mx < ShopStage.LIST_X + ShopStage.LIST_W && dy != 0) {
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
            int r = ShopStage.rowAt(mx, my);
            int index = scroll + r;
            if (r >= 0 && r < visibleRows() && index < MountLook.values().length) {
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
                if (statHit(k, mx, my) && view.tier(k) < MountStats.tiers(MountStats.ROW_KIND[k])) {
                    send(MountNetwork.BUY_TIER, k);
                    return true;
                }
            }
            int nx = RX + RW - NAME_W;
            if (view.bond() >= 2 && mx >= nx && mx < RX + RW && my >= WHISTLE_Y && my < WHISTLE_Y + Golden.BTN) {
                Minecraft.getInstance().setScreen(new MountNameScreen(this, view.name()));
                return true;
            }
            int ww = view.bond() >= 2 ? RW - NAME_W - 4 : RW;
            if (mx >= RX && mx < RX + ww && my >= WHISTLE_Y && my < WHISTLE_Y + Golden.BTN && !view.whistle()) {
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

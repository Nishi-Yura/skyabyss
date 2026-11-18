package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Hp;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.endgame.HunterNotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.neoforged.neoforge.network.PacketDistributor;

// 狩人の手帳 (endgame/HunterNotes)。旅の記録の図鑑の枠を押すと開く。見るだけの画面:
//   左  敵の並び (図鑑の順。倒していない型は「？？？」、慣れた型は金の印)。ホイールで送る
//   右  選んだ敵の頁: 倒した数・次の頁まで・出る所・強さ・狩りの慣れ・見た技
// Esc で旅の記録へ戻る。中身は開いたときにサーバーへ頼む (Request → Snapshot)。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 敵の並びは脇の列 (左)、頁は主の列 (右)、下の段に動かし方
public class HunterNotesScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int BG = 0x05070F;
    private static final int GOLD = 0xE9C46A;
    private static final int TEAL = 0x8FD0FF;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = jp.revati.skyabyss.client.ui.Golden.SOFT;
    private static final int FAINT = jp.revati.skyabyss.client.ui.Golden.DIM;

    private static final jp.revati.skyabyss.client.ui.Golden.Layout LAYOUT = jp.revati.skyabyss.client.ui.Golden.Layout.large(jp.revati.skyabyss.client.ui.Golden.FOOT);
    private static final jp.revati.skyabyss.client.ui.Golden.Box LIST_BOX = LAYOUT.body().minor(true);
    private static final jp.revati.skyabyss.client.ui.Golden.Box PAGE_BOX = LAYOUT.body().major(false);
    private static final int W = jp.revati.skyabyss.client.ui.Golden.L_W;
    private static final int H = jp.revati.skyabyss.client.ui.Golden.L_H;
    private static final int ROW_H = jp.revati.skyabyss.client.ui.Golden.ROW;
    private static final int LIST_X = LIST_BOX.x() + 2;
    private static final int LIST_Y = LIST_BOX.y() + 2;
    private static final int LIST_W = LIST_BOX.w() - 8;
    private static final int ROWS = (LIST_BOX.h() - 4) / ROW_H;
    private static final int PAGE_X = PAGE_BOX.x();
    private static final int PAGE_W = PAGE_BOX.w();

    @Nullable
    private static CompoundTag data;

    private final UiFit fit = new UiFit();
    private int scroll;
    private int selected;
    // 撮影: 届いたら選ぶ型 (なければ倒したことのある一番上)
    @Nullable
    private String wanted;
    // 頁に回して見せる姿 (型ごとに一つ作っておく。描けなかった型は二度と試さない)
    private final java.util.Map<String, LivingEntity> models = new java.util.HashMap<>();
    private final java.util.Set<String> broken = new java.util.HashSet<>();

    public HunterNotesScreen() {
        super(Component.translatable("gui.skyabyss.hunt.title"));
    }

    // 届いたら、開いている手帳は倒したことのある一番上の敵を選ぶ
    public static void receive(HunterNotes.Snapshot snap) {
        data = snap.data();
        if (Minecraft.getInstance().screen instanceof HunterNotesScreen s) {
            s.selectFirstKnown();
        }
    }

    @Override
    protected void init() {
        PacketDistributor.sendToServer(new HunterNotes.Request());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new JourneyScreen());
    }

    private ListTag foes() {
        return data == null ? new ListTag() : data.getList("foes", Tag.TAG_COMPOUND);
    }

    public HunterNotesScreen select(String id) {
        wanted = id;
        return this;
    }

    // 一番上の、倒したことのある敵を選ぶ (select で頼まれた型があれば、それ)
    public void selectFirstKnown() {
        ListTag l = foes();
        for (int i = 0; i < l.size(); i++) {
            if (wanted == null ? known(l.getCompound(i)) : l.getCompound(i).getString("id").equals(wanted)) {
                selected = i;
                scroll = Mth.clamp(i - ROWS / 2, 0, Math.max(0, l.size() - ROWS));
                return;
            }
        }
    }

    private static boolean known(CompoundTag e) {
        return e.getInt("n") > 0 || e.contains("moves");
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double dx, double dy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(dy) * 3, 0, Math.max(0, foes().size() - ROWS));
        return true;
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (button == 0 && mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + ROWS * ROW_H) {
            int i = scroll + (int) ((my - LIST_Y) / ROW_H);
            if (i < foes().size()) {
                selected = i;
            }
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    // ---------------------------------------------------------------- 絵
    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static Component name(CompoundTag e) {
        if (!known(e)) {
            return Component.translatable("gui.skyabyss.hunt.unknown");
        }
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(e.getString("id"))).getDescription();
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        g.fill(0, 0, width, height, argb(0.80f, BG));
        fit.fit(width, height, W, H, 3f, 0.5f);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        var head = LAYOUT.head();
        jp.revati.skyabyss.client.ui.Golden.panel(g, LAYOUT.panel());
        jp.revati.skyabyss.client.ui.Golden.headLine(g, LAYOUT);
        jp.revati.skyabyss.client.ui.Golden.recess(g, LIST_BOX);
        jp.revati.skyabyss.client.ui.Golden.recess(g, PAGE_BOX);
        g.drawString(font, title, head.x() + 5, jp.revati.skyabyss.client.ui.Golden.textY(head.y(), head.h()), argb(1f, GOLD), false);
        ListTag l = foes();
        int known = 0;
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getInt("n") > 0) {
                known++;
            }
        }
        Component count = Component.translatable("gui.skyabyss.hunt.count", known, l.size());
        g.drawString(font, count, head.right() - 5 - font.width(count), jp.revati.skyabyss.client.ui.Golden.textY(head.y(), head.h()), argb(1f, TEAL), false);
        if (data == null) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.journey.loading"), W / 2, H / 2, argb(1f, DIM));
            fit.end(g);
            return;
        }
        renderList(g, l, mx, my);
        renderPage(g, selected < l.size() ? l.getCompound(selected) : null);
        var foot = LAYOUT.foot();
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.hunt.help"), foot.x() + 5,
                jp.revati.skyabyss.client.ui.Golden.textY(foot.y(), foot.h()), foot.w() - 10, argb(1f, FAINT), false);
        fit.end(g);
    }

    private void renderList(GuiGraphics g, ListTag l, int mx, int my) {
        for (int r = 0; r < ROWS && scroll + r < l.size(); r++) {
            int i = scroll + r;
            CompoundTag e = l.getCompound(i);
            int y = LIST_Y + r * ROW_H;
            boolean on = i == selected;
            boolean hover = mx >= LIST_X && mx < LIST_X + LIST_W && my >= y && my < y + ROW_H;
            if (on) {
                jp.revati.skyabyss.client.ui.Golden.selected(g, new jp.revati.skyabyss.client.ui.Golden.Box(LIST_X, y, LIST_W, ROW_H));
            } else if (hover) {
                g.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H, argb(0.5f, 0x1E5552));
            }
            int n = e.getInt("n");
            boolean familiar = n >= HunterNotes.FAMILIAR;
            String num = n > 0 ? String.valueOf(n) : "";
            int numW = font.width(num);
            Component nm = name(e);
            String shown = font.plainSubstrByWidth(nm.getString(), LIST_W - 14 - numW);
            int ty = jp.revati.skyabyss.client.ui.Golden.textY(y, ROW_H);
            g.drawString(font, shown, LIST_X + 10, ty, argb(1f, !known(e) ? FAINT : on ? GOLD : TEXT), false);
            g.drawString(font, num, LIST_X + LIST_W - 3 - numW, ty, argb(1f, familiar ? GOLD : DIM), false);
            if (familiar) {
                g.fill(LIST_X + 3, y + 5, LIST_X + 7, y + 9, argb(1f, GOLD));
            }
        }
        // 送りの目印
        if (l.size() > ROWS) {
            int barH = ROWS * ROW_H;
            int knobH = Math.max(8, barH * ROWS / l.size());
            int knobY = LIST_Y + (barH - knobH) * scroll / Math.max(1, l.size() - ROWS);
            g.fill(LIST_X + LIST_W + 2, knobY, LIST_X + LIST_W + 4, knobY + knobH, argb(1f, 0x4A5A68));
        }
    }

    private void renderPage(GuiGraphics g, @Nullable CompoundTag e) {
        if (e == null) {
            return;
        }
        int x = PAGE_X + 8;
        int y = PAGE_BOX.y() + 2;
        int tw = PAGE_W - 16;
        if (!known(e)) {
            for (FormattedCharSequence s : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.hunt.empty"), tw)) {
                g.drawString(font, s, x, jp.revati.skyabyss.client.ui.Golden.textY(y, ROW_H), argb(1f, DIM), false);
                y += ROW_H;
            }
            return;
        }
        model(g, e.getString("id"));
        int n = e.getInt("n");
        Component kills = Component.translatable("gui.skyabyss.hunt.kills", n);
        int kw = font.width(kills);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, name(e), x, jp.revati.skyabyss.client.ui.Golden.textY(y, ROW_H), tw - kw - 5, argb(1f, GOLD), false);
        g.drawString(font, kills, PAGE_X + PAGE_W - 8 - kw, jp.revati.skyabyss.client.ui.Golden.textY(y, ROW_H), argb(1f, TEAL), false);
        y += ROW_H + 3;
        jp.revati.skyabyss.client.ui.Golden.rule(g, x, x + tw, y - 2);
        // 次の頁まで
        Component next = n < HunterNotes.HABITAT ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.HABITAT - n, Component.translatable("gui.skyabyss.hunt.page.habitat"))
                : n < HunterNotes.STATS ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.STATS - n, Component.translatable("gui.skyabyss.hunt.page.stats"))
                : n < HunterNotes.FAMILIAR ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.FAMILIAR - n, Component.translatable("gui.skyabyss.hunt.page.familiar"))
                : null;
        if (next != null) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, next, x, jp.revati.skyabyss.client.ui.Golden.textY(y, ROW_H), tw, argb(1f, DIM), false);
            y += ROW_H;
        }
        List<Component> lines = new ArrayList<>();
        if (n >= HunterNotes.HABITAT) {
            lines.add(head("gui.skyabyss.hunt.page.habitat"));
            lines.add(habitat(e));
        }
        if (n >= HunterNotes.STATS) {
            lines.add(head("gui.skyabyss.hunt.page.stats"));
            lines.add(Component.translatable("gui.skyabyss.hunt.stats", shown(e.getFloat("hp")), shown(e.getFloat("atk"))).withColor(TEXT));
        }
        if (n >= HunterNotes.FAMILIAR) {
            lines.add(head("gui.skyabyss.hunt.page.familiar"));
            lines.add(Component.translatable("gui.skyabyss.hunt.familiar_on").withColor(TEXT));
        }
        ListTag mv = e.getList("moves", Tag.TAG_STRING);
        if (!mv.isEmpty()) {
            lines.add(head("gui.skyabyss.hunt.moves").append(" " + mv.size()));
            MutableComponent all = Component.empty();
            for (int i = 0; i < mv.size(); i++) {
                if (i > 0) {
                    all.append("・");
                }
                all.append(Component.translatable("gui.skyabyss.attack." + mv.getString(i)));
            }
            lines.add(all.withColor(TEXT));
        }
        int bottom = PAGE_BOX.bottom() - 2;
        // 姿の横では字を狭く (重ねない)
        int modelTop = PAGE_BOX.bottom() - MODEL;
        for (Component c : lines) {
            for (FormattedCharSequence s : jp.revati.skyabyss.client.ui.JpWrap.split(font, c, y + ROW_H > modelTop ? tw - MODEL : tw)) {
                if (y + ROW_H > bottom) {
                    return;
                }
                g.drawString(font, s, x, jp.revati.skyabyss.client.ui.Golden.textY(y, ROW_H), -1, false);
                y += ROW_H;
            }
        }
    }

    // 選んだ敵の姿を、頁の右下でゆっくり回す
    private void model(GuiGraphics g, String id) {
        if (broken.contains(id)) {
            return;
        }
        LivingEntity le = models.computeIfAbsent(id, k -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return null;
            }
            Entity made = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(k)).create(mc.level);
            return made instanceof LivingEntity l ? l : null;
        });
        if (le == null) {
            broken.add(id);
            return;
        }
        float turn = (System.currentTimeMillis() % 14000L) / 14000f * 360f;
        le.setYRot(180f + turn);
        le.yBodyRot = le.yBodyRotO = le.getYRot();
        le.yHeadRot = le.yHeadRotO = le.getYRot();
        le.setXRot(0f);
        le.xRotO = 0f;
        float size = Math.max(le.getBbHeight(), le.getBbWidth());
        float scale = Mth.clamp((MODEL - 16f) / size, 3f, 40f);
        float cx = PAGE_X + PAGE_W - MODEL / 2f;
        float cy = PAGE_BOX.bottom() - MODEL / 2f;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-12));
        try {
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(g, cx, cy, scale,
                    new Vector3f(0f, le.getBbHeight() / 2f, 0f), pose, null, le);
        } catch (RuntimeException ex) {
            broken.add(id);
            jp.revati.skyabyss.SkyAbyss.LOGGER.warn("Hunter's notes: could not draw {}: {}", id, ex.toString());
        }
    }

    // 頁の右下の姿の広さ (正方形)
    private static final int MODEL = jp.revati.skyabyss.client.ui.Golden.S89;

    private static MutableComponent head(String key) {
        return Component.translatable(key).withColor(GOLD);
    }

    private static String shown(float v) {
        return String.valueOf(Math.round(v * Hp.SHOWN));
    }

    private static Component habitat(CompoundTag e) {
        ListTag rs = e.getList("regions", Tag.TAG_STRING);
        if (rs.isEmpty()) {
            return Component.translatable("gui.skyabyss.hunt.fixed").withColor(TEXT);
        }
        MutableComponent lands = Component.empty();
        for (int i = 0; i < rs.size(); i++) {
            if (i > 0) {
                lands.append("・");
            }
            lands.append(Component.translatable("endgame.skyabyss.land." + rs.getString(i)));
        }
        String time = switch (e.getInt("time")) {
            case 1 -> "gui.skyabyss.hunt.time.night";
            case 2 -> "gui.skyabyss.hunt.time.any";
            default -> "gui.skyabyss.hunt.time.day";
        };
        return Component.translatable(e.getBoolean("named") ? "gui.skyabyss.hunt.where_named" : "gui.skyabyss.hunt.where", lands, Component.translatable(time)).withColor(TEXT);
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 3f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}

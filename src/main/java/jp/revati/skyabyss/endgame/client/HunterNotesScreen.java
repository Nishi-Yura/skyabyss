package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Hp;
import jp.revati.skyabyss.client.ui.TextStyle;
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
// Esc で旅の記録へ戻る。中身は開いたときにサーバーへ頼む (Request → Snapshot)
public class HunterNotesScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;
    private static final int FAINT = 0x56606C;

    private static final int W = 380;
    private static final int H = 236;
    private static final int LIST_X = 10;
    private static final int LIST_Y = 34;
    private static final int LIST_W = 150;
    private static final int ROW_H = 12;
    private static final int ROWS = 15;
    private static final int PAGE_X = LIST_X + LIST_W + 8;
    private static final int PAGE_W = W - PAGE_X - 10;

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

    private void frame(GuiGraphics g, int x, int y, int w, int h, int line, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF03050A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(1f, line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, argb(1f, fill));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, argb(0.10f, 0xFFFFFF));
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, argb(0.35f, 0x000000));
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
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        frame(g, 0, 0, W, H, 0x3A4A58, PANEL);
        TextStyle.HEADING.drawLeft(g, font, title, 12, 9, 1f, GOLD);
        ListTag l = foes();
        int known = 0;
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getInt("n") > 0) {
                known++;
            }
        }
        Component count = Component.translatable("gui.skyabyss.hunt.count", known, l.size());
        g.drawString(font, count, W - 12 - font.width(count), 14, argb(1f, TEAL), false);
        if (data == null) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.journey.loading"), W / 2, H / 2, argb(1f, DIM));
            fit.end(g);
            return;
        }
        renderList(g, l, mx, my);
        renderPage(g, selected < l.size() ? l.getCompound(selected) : null);
        g.drawString(font, Component.translatable("gui.skyabyss.hunt.help"), 12, H - 14, argb(1f, DIM), false);
        fit.end(g);
    }

    private void renderList(GuiGraphics g, ListTag l, int mx, int my) {
        frame(g, LIST_X - 2, LIST_Y - 2, LIST_W + 4, ROWS * ROW_H + 4, 0x2C3846, CARD);
        for (int r = 0; r < ROWS && scroll + r < l.size(); r++) {
            int i = scroll + r;
            CompoundTag e = l.getCompound(i);
            int y = LIST_Y + r * ROW_H;
            boolean on = i == selected;
            boolean hover = mx >= LIST_X && mx < LIST_X + LIST_W && my >= y && my < y + ROW_H;
            if (on || hover) {
                g.fill(LIST_X, y, LIST_X + LIST_W, y + ROW_H, argb(on ? 0.9f : 0.5f, 0x2A2618));
            }
            int n = e.getInt("n");
            boolean familiar = n >= HunterNotes.FAMILIAR;
            String num = n > 0 ? String.valueOf(n) : "";
            int numW = font.width(num);
            Component nm = name(e);
            String shown = font.plainSubstrByWidth(nm.getString(), LIST_W - 14 - numW);
            g.drawString(font, shown, LIST_X + 10, y + 2, argb(1f, !known(e) ? FAINT : on ? GOLD : TEXT), false);
            g.drawString(font, num, LIST_X + LIST_W - 3 - numW, y + 2, argb(1f, familiar ? GOLD : DIM), false);
            if (familiar) {
                g.fill(LIST_X + 3, y + 4, LIST_X + 7, y + 8, argb(1f, GOLD));
            }
        }
        // 送りの目印
        if (l.size() > ROWS) {
            int barH = ROWS * ROW_H;
            int knobH = Math.max(8, barH * ROWS / l.size());
            int knobY = LIST_Y + (barH - knobH) * scroll / Math.max(1, l.size() - ROWS);
            g.fill(LIST_X + LIST_W + 3, knobY, LIST_X + LIST_W + 4, knobY + knobH, argb(1f, 0x4A5A68));
        }
    }

    private void renderPage(GuiGraphics g, @Nullable CompoundTag e) {
        frame(g, PAGE_X - 2, LIST_Y - 2, PAGE_W + 4, ROWS * ROW_H + 4, 0x2C3846, CARD);
        if (e == null) {
            return;
        }
        int x = PAGE_X + 6;
        int y = LIST_Y + 4;
        if (!known(e)) {
            for (FormattedCharSequence s : font.split(Component.translatable("gui.skyabyss.hunt.empty"), PAGE_W - 12)) {
                g.drawString(font, s, x, y, argb(1f, DIM), false);
                y += 10;
            }
            return;
        }
        model(g, e.getString("id"));
        g.drawString(font, name(e), x, y, argb(1f, GOLD), true);
        int n = e.getInt("n");
        Component kills = Component.translatable("gui.skyabyss.hunt.kills", n);
        g.drawString(font, kills, PAGE_X + PAGE_W - 6 - font.width(kills), y, argb(1f, TEAL), false);
        y += 14;
        // 次の頁まで
        Component next = n < HunterNotes.HABITAT ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.HABITAT - n, Component.translatable("gui.skyabyss.hunt.page.habitat"))
                : n < HunterNotes.STATS ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.STATS - n, Component.translatable("gui.skyabyss.hunt.page.stats"))
                : n < HunterNotes.FAMILIAR ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.FAMILIAR - n, Component.translatable("gui.skyabyss.hunt.page.familiar"))
                : null;
        if (next != null) {
            g.drawString(font, next, x, y, argb(1f, DIM), false);
            y += 13;
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
        int bottom = LIST_Y + ROWS * ROW_H - 4;
        // 姿の横では字を狭く (重ねない)
        int modelTop = LIST_Y + ROWS * ROW_H - 104;
        for (Component c : lines) {
            for (FormattedCharSequence s : font.split(c, y + 20 > modelTop ? PAGE_W - 104 : PAGE_W - 12)) {
                if (y + 9 > bottom) {
                    return;
                }
                g.drawString(font, s, x, y, -1, false);
                y += 10;
            }
            y += 1;
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
        float scale = Mth.clamp(78f / size, 3f, 44f);
        float cx = PAGE_X + PAGE_W - 52;
        float cy = LIST_Y + ROWS * ROW_H - 56;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-12));
        try {
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(g, cx, cy, scale,
                    new Vector3f(0f, le.getBbHeight() / 2f, 0f), pose, null, le);
        } catch (RuntimeException ex) {
            broken.add(id);
            jp.revati.skyabyss.SkyAbyss.LOGGER.warn("Hunter's notes: could not draw {}: {}", id, ex.toString());
        }
    }

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
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}

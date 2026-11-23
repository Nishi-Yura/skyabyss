package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Hp;
import jp.revati.skyabyss.client.ui.LedgerStage;
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
import jp.revati.skyabyss.client.ui.Paper;

// 狩人の手帳 (endgame/HunterNotes)。旅の記録の図鑑の枠を押すと開く。見るだけの画面:
//   敵の並び (図鑑の順。倒していない型は「？？？」、慣れた型は桃色の菱)。ホイールで送る
//   選んだ敵の頁: 倒した数・次の頁まで・出る所・強さ・狩りの慣れ・見た技
// Esc で旅の記録へ戻る。中身は開いたときにサーバーへ頼む (Request → Snapshot)。
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1):
//   左の黒い紙に選んだ敵の頁 (題は名前、添えに倒した数、右下で姿が回る)。右の生成りの帯 (目録) に敵の並び (選んだ敵は桃色の付箋)。左下の山の上に動かし方
public class HunterNotesScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int GOLD = Paper.ON_BLACK_PINK;
    private static final int TEXT = jp.revati.skyabyss.client.ui.Golden.TEXT;
    private static final int DIM = Paper.ON_BLACK_SOFT;

    private static final int W = LedgerStage.W;
    private static final int H = LedgerStage.H;
    private static final int ROW_H = 13;
    private static final int LIST_X = LedgerStage.INDEX_X;
    private static final int LIST_Y = LedgerStage.LIST_Y;
    private static final int LIST_W = LedgerStage.INDEX_W - 6;
    private static final int ROWS = LedgerStage.LIST_H / ROW_H;
    private static final int PAGE_X = LedgerStage.PAGE_X;
    private static final int PAGE_W = LedgerStage.PAGE_W;
    private static final int PAGE_BOTTOM = LedgerStage.FOOT_Y - 4;

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

    // 撮影の見本 (サーバーへ頼まない)
    private boolean demo;

    // 撮影用: 見本の手帳 (慣れた敵・頁の途中の敵・まだの敵)
    public static HunterNotesScreen demo() {
        ListTag foes = new ListTag();
        String[] ids = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper", "minecraft:husk", "minecraft:stray"};
        int[] ns = {HunterNotes.FAMILIAR + 12, HunterNotes.STATS + 1, HunterNotes.HABITAT + 1, 3, 0, 0};
        for (int i = 0; i < ids.length; i++) {
            CompoundTag e = new CompoundTag();
            e.putString("id", ids[i]);
            e.putInt("n", ns[i]);
            e.putFloat("hp", 20f + i * 6);
            e.putFloat("atk", 3f + i);
            ListTag rs = new ListTag();
            rs.add(net.minecraft.nbt.StringTag.valueOf("meadow"));
            rs.add(net.minecraft.nbt.StringTag.valueOf("whale"));
            e.put("regions", rs);
            e.putInt("time", i % 3);
            foes.add(e);
        }
        CompoundTag d = new CompoundTag();
        d.put("foes", foes);
        data = d;
        HunterNotesScreen s = new HunterNotesScreen();
        s.demo = true;
        return s;
    }

    @Override
    protected void init() {
        fit.fit(width, height, W, H, 3f);
        if (!demo) {
            PacketDistributor.sendToServer(new HunterNotes.Request());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
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
        if (button == 0 && mx >= LIST_X - 4 && mx < LIST_X + LIST_W + 4 && my >= LIST_Y && my < LIST_Y + ROWS * ROW_H) {
            int i = scroll + (int) ((my - LIST_Y) / ROW_H);
            if (i < foes().size()) {
                selected = i;
            }
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    // ---------------------------------------------------------------- 絵

    private static Component name(CompoundTag e) {
        if (!known(e)) {
            return Component.translatable("gui.skyabyss.hunt.unknown");
        }
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(e.getString("id"))).getDescription();
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, W, H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        LedgerStage.backdrop(g, fit, width, height);
        ListTag l = foes();
        int known = 0;
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getInt("n") > 0) {
                known++;
            }
        }
        LedgerStage.indexHead(g, font, Component.translatable("gui.skyabyss.hunt.count", known, l.size()));
        LedgerStage.footHint(g, font, Component.translatable("gui.skyabyss.hunt.help"), PAGE_X, PAGE_W);
        if (data == null) {
            LedgerStage.pageTitle(g, font, title, null);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.loading"), PAGE_X, LedgerStage.BODY_Y, PAGE_W, DIM, false);
            fit.end(g);
            return;
        }
        renderList(g, l, mx, my);
        renderPage(g, selected < l.size() ? l.getCompound(selected) : null);
        fit.end(g);
    }

    // 敵の並び (右の帯。字は墨)
    private void renderList(GuiGraphics g, ListTag l, int mx, int my) {
        for (int r = 0; r < ROWS && scroll + r < l.size(); r++) {
            int i = scroll + r;
            CompoundTag e = l.getCompound(i);
            int y = LIST_Y + r * ROW_H;
            boolean on = i == selected;
            boolean hover = mx >= LIST_X - 4 && mx < LIST_X + LIST_W + 4 && my >= y && my < y + ROW_H;
            if (on) {
                LedgerStage.flag(g, y, ROW_H);
            } else if (hover) {
                LedgerStage.hover(g, y, ROW_H);
            }
            int n = e.getInt("n");
            boolean familiar = n >= HunterNotes.FAMILIAR;
            String num = n > 0 ? String.valueOf(n) : "";
            int numW = font.width(num);
            Component nm = Component.literal(name(e).getString());
            int ty = y + 3;
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, nm, LIST_X + 8, ty, LIST_W - 12 - numW, on ? LedgerStage.ON_PINK : !known(e) ? 0x8A8086 : Paper.INK, false);
            g.drawString(font, num, LIST_X + LIST_W - numW, ty, on ? LedgerStage.ON_PINK : familiar ? Paper.INK_HEAD : Paper.INK_SOFT, false);
            if (familiar && !on) {
                Paper.pin(g, LIST_X + 3, y + 6, 2, true);
            }
        }
        // 送りの糸 (帯の右の辺)
        if (l.size() > ROWS) {
            int barH = ROWS * ROW_H;
            int knobH = Math.max(8, barH * ROWS / l.size());
            int knobY = LIST_Y + (barH - knobH) * scroll / Math.max(1, l.size() - ROWS);
            int bx = LedgerStage.INDEX_X + LedgerStage.INDEX_W - 2;
            g.fill(bx, LIST_Y, bx + 1, LIST_Y + barH, Paper.CREAM_LO);
            g.fill(bx - 1, knobY, bx + 2, knobY + knobH, Paper.PINK);
        }
    }

    private void renderPage(GuiGraphics g, @Nullable CompoundTag e) {
        if (e == null) {
            return;
        }
        int x = PAGE_X;
        int y = LedgerStage.BODY_Y - 2;
        int tw = PAGE_W;
        if (!known(e)) {
            LedgerStage.pageTitle(g, font, name(e), null);
            for (FormattedCharSequence s : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.hunt.empty"), tw)) {
                g.drawString(font, s, x, y + 2, DIM, false);
                y += ROW_H;
            }
            return;
        }
        model(g, e.getString("id"));
        int n = e.getInt("n");
        LedgerStage.pageTitle(g, font, name(e), Component.translatable("gui.skyabyss.hunt.kills", n));
        // 次の頁まで
        Component next = n < HunterNotes.HABITAT ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.HABITAT - n, Component.translatable("gui.skyabyss.hunt.page.habitat"))
                : n < HunterNotes.STATS ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.STATS - n, Component.translatable("gui.skyabyss.hunt.page.stats"))
                : n < HunterNotes.FAMILIAR ? Component.translatable("gui.skyabyss.hunt.next", HunterNotes.FAMILIAR - n, Component.translatable("gui.skyabyss.hunt.page.familiar"))
                : null;
        if (next != null) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, next, x, y + 2, tw, DIM, false);
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
        int bottom = PAGE_BOTTOM;
        // 姿の横では字を狭く (重ねない)
        int modelTop = PAGE_BOTTOM - MODEL;
        for (Component c : lines) {
            for (FormattedCharSequence s : jp.revati.skyabyss.client.ui.JpWrap.split(font, c, y + ROW_H > modelTop ? tw - MODEL : tw)) {
                if (y + ROW_H > bottom) {
                    return;
                }
                g.drawString(font, s, x, y + 2, -1, false);
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
        float cy = PAGE_BOTTOM - MODEL / 2f;
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
    private static final int MODEL = 80;

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
        fit.fit(width, height, W, H, 3f);
        return fit.screenRect(0, 0, W, H);
    }
}

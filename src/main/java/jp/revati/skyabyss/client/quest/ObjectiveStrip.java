package jp.revati.skyabyss.client.quest;

import java.util.function.Function;

import javax.annotation.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.client.guide.GuideArrow;
import jp.revati.skyabyss.client.opening.OpeningClient;
import jp.revati.skyabyss.client.opening.SiteTargets;
import jp.revati.skyabyss.guide.nav.NavMath;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// メインの目的の帯 (docs/design/17 の 5.3)。左上 (クエストの小さな表示の位置) に、今のメインの目的を細い一行で常に出す。
// 目的が変わると一度だけ光る。目印の場所が分かる目的は、右に距離 (10 ブロック単位) と方角 (八方位) を添える。
// 戦いで傷を受けた直後と、画面を開いているあいだは薄くする。設定 objectiveStrip で消せる (全文は U のクエストログ)。
// 追跡中のクエストがメインのときは、この帯が代わりに出す (QuestHud は出さない)
public final class ObjectiveStrip {
    static final int WIDTH = 230;
    private static final int ROW = 14;

    // 目的ごとの目印の位置 (クエストの id → 位置)。野営地・宿り・骨の丘・岩鯨など、位置が決まる目的を扱う担当が登録する
    @Nullable
    public static volatile Function<String, BlockPos> targets;

    private static String lastText = "";
    // 開発用 (台本のログ): 最後に帯へ描いた文と、その時刻
    private static String lastShown = "";
    private static long lastShownMs;
    private static long flashUntil;

    private ObjectiveStrip() {
    }

    @Nullable
    private static QuestDef current(Minecraft mc, QuestLog log) {
        QuestDef best = null;
        for (QuestDef def : QuestClient.all()) {
            QuestLog.Entry e = log.entry(def.id());
            if (def.main() && e != null && !e.done && e.stage < def.stages().size()) {
                best = def;
                if (def.id().equals(log.tracked())) {
                    return def;
                }
            }
        }
        return best;
    }

    // 開発用: いま画面に出ている帯の文 (出ていなければ空)。台本の開発用ログ (client/dev/ShotRunner の watch) が読む
    public static String shownNow() {
        return net.minecraft.Util.getMillis() - lastShownMs < 600 ? lastShown : "";
    }

    // 帯を出すか (設定が入っていて、出す目的があるか)
    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.OBJECTIVE_STRIP.get() || mc.player == null) {
            return false;
        }
        return OpeningClient.objective() != null || current(mc, QuestLog.of(mc.player)) != null;
    }

    // 帯の分だけ、下の小さな表示をずらす
    static int rows() {
        return active() ? ROW : 0;
    }

    public static String direction(Vec3 from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.x;
        double dz = to.getZ() + 0.5 - from.z;
        // yaw 0 = 南 (+Z)、90 = 西 (-X): 角度を 45 度ごとに八つへ
        double deg = Math.toDegrees(Math.atan2(-dx, dz));
        int i = Math.floorMod((int) Math.round(deg / 45.0), 8);
        // 南 = 0 から時計回り (南 南西 西 …) の i を、北 = 0 の短い方位の文 (gui.skyabyss.objective.dir.<0..7>。英語は N NE …) へ。
        // 長い方位の文 (north-west など) は距離の札の幅を食うので短い形を使う。語を日本語に決め打ちしない (英語の画面にも出る)
        return Component.translatable("gui.skyabyss.objective.dir." + (i + 4) % 8).getString();
    }

    // 道しるべの行き先: pos = 地上の目印 (なければ null)、inside = 目印はあるが、いまは背中・ほかの次元の中 (矢印の代わりに文を出す)
    public record Resolved(@Nullable BlockPos pos, boolean inside) {
        static final Resolved NONE = new Resolved(null, false);
    }

    // いまの目的 (序盤の目的 → メインのクエスト) の行き先。道しるべの地面の道もこれを使う
    public static Resolved resolve(Minecraft mc) {
        if (mc.player == null) {
            return Resolved.NONE;
        }
        OpeningClient.Objective opening = OpeningClient.objective();
        if (opening != null) {
            return new Resolved(opening.target(), false);
        }
        QuestLog log = QuestLog.of(mc.player);
        QuestDef def = current(mc, log);
        if (def == null) {
            return Resolved.NONE;
        }
        return resolveQuest(def, log.entry(def.id()));
    }

    // 帯が出さない追跡中のクエスト (メインでないもの) の行き先
    public static Resolved resolveTracked(Minecraft mc) {
        if (mc.player == null) {
            return Resolved.NONE;
        }
        QuestLog log = QuestLog.of(mc.player);
        if (log.tracked().isEmpty()) {
            return Resolved.NONE;
        }
        QuestDef def = QuestClient.quest(log.tracked());
        QuestLog.Entry entry = log.entry(log.tracked());
        if (def == null || entry == null || entry.done || entry.stage >= def.stages().size()) {
            return Resolved.NONE;
        }
        return resolveQuest(def, entry);
    }

    // クエストの今の段の行き先: 「クエスト/段」→ クエストの id → 話す相手 (済んでいない最初の「話す」目的)
    public static Resolved resolveQuest(QuestDef def, QuestLog.Entry entry) {
        if (entry == null || entry.stage >= def.stages().size()) {
            return Resolved.NONE;
        }
        QuestDef.Stage stage = def.stages().get(entry.stage);
        Function<String, BlockPos> provider = targets;
        BlockPos target = null;
        if (provider != null) {
            target = provider.apply(def.id() + "/" + stage.id());
            if (target == null) {
                target = provider.apply(def.id());
            }
        }
        boolean overworld = SiteTargets.inOverworld();
        if (target != null) {
            return new Resolved(target, false);
        }
        BlockPos any = SiteTargets.lookupAny(def.id() + "/" + stage.id());
        if (any == null) {
            any = SiteTargets.lookupAny(def.id());
        }
        if (any == null) {
            for (int i = 0; i < stage.objectives().size(); i++) {
                QuestDef.Objective ob = stage.objectives().get(i);
                if (ob.type().equals("talk") && !ob.targets().isEmpty() && entry.progress(i) < ob.count()) {
                    any = SiteTargets.npc(ob.targets().get(0));
                    if (any != null) {
                        break;
                    }
                }
            }
        }
        if (any == null) {
            return Resolved.NONE;
        }
        return overworld ? new Resolved(any, false) : new Resolved(null, true);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !Config.OBJECTIVE_STRIP.get()
                || OpeningClient.awakeActive()) {
            return;
        }
        // 序盤 (17 の 5.3節、11.2節): 野営地から最初の岩鯨までは、拍子の担当 (opening/) が目的と目印を送ってくる。操作のキーは書かない
        OpeningClient.Objective opening = OpeningClient.objective();
        if (opening != null) {
            draw(g, mc, Component.translatable("objective.skyabyss." + opening.id()).getString(), new Resolved(opening.target(), false));
            return;
        }
        QuestLog log = QuestLog.of(mc.player);
        QuestDef def = current(mc, log);
        if (def == null) {
            return;
        }
        QuestLog.Entry entry = log.entry(def.id());
        String text = QuestHud.firstObjective(mc.font, def, entry, WIDTH - 12);
        Resolved target = resolveQuest(def, entry);
        draw(g, mc, text, target);
    }

    private static void draw(GuiGraphics g, Minecraft mc, String text, Resolved resolved) {
        Font font = mc.font;
        long now = net.minecraft.Util.getMillis();
        if (!text.equals(lastText)) {
            if (!lastText.isEmpty()) {
                flashUntil = now + 1400;
            }
            lastText = text;
        }
        // 戦闘で傷を受けた直後・画面を開いている間・ホラー演出 (足音・歌) の最中は薄くする (雰囲気を壊さない)
        boolean quiet = mc.screen != null || mc.player.hurtTime > 0 || OpeningClient.horrorCueActive();
        float a = quiet ? 0.45f : 1f;
        int x = QuestHud.X;
        int y = QuestHud.y();
        String far = "";
        BlockPos target = resolved.pos();
        boolean arrow = Config.QUEST_ARROW.get();
        float arrowDeg = 0f;
        int vertical = 0;
        if (target != null) {
            double d = Math.sqrt(mc.player.position().distanceToSqr(target.getX() + 0.5, mc.player.getY(), target.getZ() + 0.5));
            far = jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(d, arrow ? "" : direction(mc.player.position(), target));
            arrowDeg = NavMath.arrowDegrees(mc.player.getX(), mc.player.getZ(), target.getX() + 0.5, target.getZ() + 0.5, mc.player.getYRot());
            vertical = NavMath.vertical(target.getY() + 0.5 - mc.player.getY());
        } else if (resolved.inside()) {
            far = Component.translatable("gui.skyabyss.guide.inside").getString();
            arrow = false;
        }
        boolean withArrow = arrow && target != null && !far.isEmpty();
        int arrowRoom = withArrow ? GuideArrow.SIZE + 4 + (vertical != 0 ? 6 : 0) : 0;
        int w = WIDTH;
        float glow = now < flashUntil ? Mth.clamp((flashUntil - now) / 1400f, 0f, 1f) : 0f;
        int bg = ((int) ((0x50 + glow * 0x50) * a)) << 24 | 0x0A0C10;
        g.fill(x - 3, y - 2, x + w + 3, y + ROW - 4, bg);
        int accent = ((int) (255 * a)) << 24 | 0xF2C14E;
        g.fill(x - 3, y - 2, x - 2, y + ROW - 4, accent);
        int textColor = ((int) (255 * a)) << 24 | 0xE9E3D4;
        String shown = "◆ " + text;
        int room = w - (far.isEmpty() ? 0 : font.width(far) + arrowRoom + 6);
        while (font.width(shown) > room && shown.length() > 3) {
            shown = shown.substring(0, shown.length() - 2) + "…";
        }
        g.drawString(font, Component.literal(shown), x + 1, y + 1, textColor, true);
        lastShown = shown + (far.isEmpty() ? "" : "   [" + far + "]");
        lastShownMs = now;
        if (!far.isEmpty()) {
            int fx = x + w - font.width(far);
            g.drawString(font, far, fx, y + 1, ((int) (255 * a)) << 24 | (resolved.inside() ? 0x9A9AAE : 0x7FD6D0), true);
            if (withArrow) {
                int cx = fx - 3 - GuideArrow.SIZE / 2;
                GuideArrow.draw(g, cx, y + 4, arrowDeg, a);
                if (vertical != 0) {
                    GuideArrow.drawVertical(g, cx - GuideArrow.SIZE / 2 - 6, y + 4, vertical > 0, a);
                }
            }
        }
    }
}

package jp.revati.skyabyss.client.quest;

import java.util.function.Function;

import javax.annotation.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.client.opening.OpeningClient;
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
    private static final String[] COMPASS = {"南", "南西", "西", "北西", "北", "北東", "東", "南東"};
    static final int WIDTH = 150;
    private static final int ROW = 14;

    // 目的ごとの目印の位置 (クエストの id → 位置)。野営地・宿り・骨の丘・岩鯨など、位置が決まる目的を扱う担当が登録する
    @Nullable
    public static volatile Function<String, BlockPos> targets;

    private static String lastText = "";
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
        return COMPASS[i];
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !Config.OBJECTIVE_STRIP.get()
                || OpeningClient.awakeActive()) {
            return;
        }
        // 序盤 (17 の 5.3節、11.2節): 野営地から最初の岩鯨までは、拍子の担当 (opening/) が目的と目印を送ってくる
        OpeningClient.Objective opening = OpeningClient.objective();
        if (opening != null) {
            draw(g, mc, Component.translatable("objective.skyabyss." + opening.id()).getString(), opening.target());
            if (opening.id().equals("follow_smoke")) {
                // 最初の目的の下に、操作の案内をひとこと控えめに (バニラのチュートリアルの札の代わり)
                Component hint = Component.translatable("opening.skyabyss.hint_move", mc.options.keyUp.getTranslatedKeyMessage(),
                        mc.options.keyLeft.getTranslatedKeyMessage(), mc.options.keyDown.getTranslatedKeyMessage(),
                        mc.options.keyRight.getTranslatedKeyMessage(), mc.options.keyJump.getTranslatedKeyMessage());
                g.drawString(mc.font, hint, QuestHud.X, QuestHud.y() + ROW - 1, 0xFFB8C4BE, true);
            }
            return;
        }
        QuestLog log = QuestLog.of(mc.player);
        QuestDef def = current(mc, log);
        if (def == null) {
            return;
        }
        QuestLog.Entry entry = log.entry(def.id());
        String text = QuestHud.firstObjective(mc.font, def, entry, WIDTH - 12);
        Function<String, BlockPos> provider = targets;
        draw(g, mc, text, provider == null ? null : provider.apply(def.id()));
    }

    private static void draw(GuiGraphics g, Minecraft mc, String text, @Nullable BlockPos target) {
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
        if (target != null) {
            double d = Math.sqrt(mc.player.position().distanceToSqr(target.getX() + 0.5, mc.player.getY(), target.getZ() + 0.5));
            far = (Math.round(d / 10.0) * 10) + "m " + direction(mc.player.position(), target);
        }
        int w = WIDTH;
        float glow = now < flashUntil ? Mth.clamp((flashUntil - now) / 1400f, 0f, 1f) : 0f;
        int bg = ((int) ((0x50 + glow * 0x50) * a)) << 24 | 0x0A0C10;
        g.fill(x - 3, y - 2, x + w + 3, y + ROW - 4, bg);
        int accent = ((int) (255 * a)) << 24 | 0xF2C14E;
        g.fill(x - 3, y - 2, x - 2, y + ROW - 4, accent);
        int textColor = ((int) (255 * a)) << 24 | 0xE9E3D4;
        String shown = "◆ " + text;
        int room = w - (far.isEmpty() ? 0 : font.width(far) + 6);
        while (font.width(shown) > room && shown.length() > 3) {
            shown = shown.substring(0, shown.length() - 2) + "…";
        }
        g.drawString(font, Component.literal(shown), x + 1, y + 1, textColor, true);
        if (!far.isEmpty()) {
            g.drawString(font, far, x + w - font.width(far), y + 1, ((int) (255 * a)) << 24 | 0x7FD6D0, true);
        }
    }
}

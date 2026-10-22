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

    // 開発用 (/skyabyss_hud demo): 目的の帯と追跡の札を、データなしで出す
    public static volatile boolean demo;
    private static String lastText = "";
    // 開発用 (台本のログ): 最後に帯へ描いた文と、その時刻
    private static String lastShown = "";
    private static long lastShownMs;
    private static long flashUntil;

    private ObjectiveStrip() {
    }

    // 帯が出すメインの目的は、いつも一つだけ (2026-10-22 の試遊: メインが並んで「どれをやるのか」迷った)。
    //   1. 章の支度の段 (guide.PrepQuests の章の表。一章なら鉄の跡・折れた剣・盾と糧) が進行中なら、その段を出す。
    //      親のメイン (雲の下の歌の「支度」の段など) は、支度が終わるのを待っているだけなので出さない
    //   2. 残りのうち、追跡中のものがあればそれ。なければ並び (order) の最初
    @Nullable
    static QuestDef current(Minecraft mc, QuestLog log) {
        QuestDef first = null;
        QuestDef firstTrail = null;
        QuestDef tracked = null;
        QuestDef trackedTrail = null;
        for (QuestDef def : QuestClient.all()) {
            QuestLog.Entry e = log.entry(def.id());
            if (!def.main() || e == null || e.done || e.stage >= def.stages().size()) {
                continue;
            }
            boolean trail = jp.revati.skyabyss.guide.PrepQuests.chapterOf(def.id()) > 0;
            boolean isTracked = def.id().equals(log.tracked());
            if (first == null) {
                first = def;
            }
            if (isTracked) {
                tracked = def;
            }
            if (trail) {
                if (firstTrail == null) {
                    firstTrail = def;
                }
                if (isTracked) {
                    trackedTrail = def;
                }
            }
        }
        if (firstTrail != null) {
            return trackedTrail != null ? trackedTrail : firstTrail;
        }
        return tracked != null ? tracked : first;
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
        if (demo) {
            return true;
        }
        return OpeningClient.objective() != null || current(mc, QuestLog.of(mc.player)) != null;
    }

    // 帯がいまこのクエストを出しているか (出していなければ、追跡の札の側が出す。序盤の目的を帯が出しているときなど)
    static boolean showsQuest(QuestDef def) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || demo || OpeningClient.objective() != null) {
            return false;
        }
        QuestDef cur = current(mc, QuestLog.of(mc.player));
        return cur != null && cur.id().equals(def.id());
    }

    // 帯の幅: 画面が小さい (GUI 倍率が高い) ほど狭く、折り返して行を足す。広い画面でも最大 WIDTH。
    // 倍率 4 (1920 幅で GUI 幅 480) では約 115 になり、背景が画面の半ばまで伸びない
    public static int bandWidth(Minecraft mc) {
        return Mth.clamp((int) (mc.getWindow().getGuiScaledWidth() * 0.24f), 112, WIDTH);
    }

    private record Cur(String text, Resolved resolved) {
    }

    // いまの帯の中身 (出さないなら null)。要求と描画で同じ判断をする
    @Nullable
    private static Cur cur(Minecraft mc) {
        if (demo) {
            BlockPos at = mc.player.blockPosition().offset(96, 0, -70);
            return new Cur("岩鯨の背へ上る鎖を探す", new Resolved(at, false));
        }
        OpeningClient.Objective opening = OpeningClient.objective();
        if (opening != null) {
            return new Cur(Component.translatable("objective.skyabyss." + opening.id()).getString(), new Resolved(opening.target(), false));
        }
        QuestLog log = QuestLog.of(mc.player);
        QuestDef def = current(mc, log);
        if (def == null) {
            return null;
        }
        QuestLog.Entry entry = log.entry(def.id());
        String text = QuestHud.firstObjective(mc.font, def, entry, WIDTH - 12);
        Resolved target = resolveQuest(def, entry);
        // 巨神の背中の中で、試練を越えて個人の「真ん中へ戻る光」が出ているあいだだけ、帯はその戻り方を言う (titan.end.ReturnBeacons)。
        // 入ったばかりでは言わない (光が出ていないので)
        if (jp.revati.skyabyss.client.titan.BoardBeamClient.has("return_offer") && mc.level != null && jp.revati.skyabyss.progress.Titans.titanOfBack(mc.level.dimension()) != null) {
            text = Component.translatable("objective.skyabyss.back_return").getString();
            target = Resolved.NONE;
        }
        // 巨神の背の中: 順路の今の段 (docs/design/32 の 6 章) と、いまの試練の次の目的 (岩鯨の背の TrialGuide など。小さな光の柱とともに送られてくる)
        if (mc.level != null && jp.revati.skyabyss.progress.Titans.titanOfBack(mc.level.dimension()) != null) {
            String route = jp.revati.skyabyss.client.trial.TrialBoardClient.routeLine(mc);
            if (route != null) {
                text = route;
                target = Resolved.NONE;
            }
            var trial = jp.revati.skyabyss.client.titan.BoardBeamClient.byPrefix("trial_obj:");
            // 運ばれるのを待つあいだは、越えた試練の目的を出さない
            if (trial != null && !jp.revati.skyabyss.client.trial.TrialBoardClient.route().carrying()) {
                String next = Component.translatable("objective.skyabyss.trial_" + trial.key().substring("trial_obj:".length())).getString();
                text = route == null ? next : route + "：" + next;
                target = new Resolved(BlockPos.containing(trial.cx(), trial.cy() - 1.0, trial.cz()), false);
            }
        }
        return new Cur(text, target);
    }

    private static boolean hasFar(Cur c) {
        return c.resolved.pos() != null || c.resolved.inside();
    }

    // 文を幅に折り返した行 (先頭に「◆ 」)。行数は maxLines まで (超えたら最後を「…」で切る)
    // 折り返しの結果を覚えておく (毎フレーム同じ文を折り返さない。帯の大きさ決めと描画で二か所から呼ばれるので二つ)
    private static final java.util.Map<String, java.util.List<String>> WRAPPED = new java.util.LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, java.util.List<String>> e) {
            return size() > 6;
        }
    };

    static java.util.List<String> wrap(Font font, String text, int width, int maxLines) {
        String key = width + "|" + maxLines + "|" + font.width(text) + "|" + text;
        java.util.List<String> hit = WRAPPED.get(key);
        if (hit != null) {
            return hit;
        }
        java.util.List<String> made = wrap0(font, text, width, maxLines);
        WRAPPED.put(key, java.util.List.copyOf(made));
        return made;
    }

    private static java.util.List<String> wrap0(Font font, String text, int width, int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<>(jp.revati.skyabyss.lore.LoreWrap.wrap("◆ " + text, width, font::width));
        if (lines.size() > maxLines) {
            lines = new java.util.ArrayList<>(lines.subList(0, maxLines));
            String last = lines.get(maxLines - 1);
            while (font.width(last + "…") > width && last.length() > 1) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(maxLines - 1, last + "…");
        }
        return lines;
    }

    // HUD の要求: 左上の帯 (成長の表示の下)。描画 (render) と同じ条件で出す。
    // 寸法は幅の上限に合わせて文を折り返した行数 (+ 距離の行) で決める
    public static void requests(Minecraft mc, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !Config.OBJECTIVE_STRIP.get() || OpeningClient.awakeActive()) {
            return;
        }
        Cur c = cur(mc);
        if (c == null) {
            return;
        }
        Font font = mc.font;
        int want = bandWidth(mc);
        boolean mazeBig = false;
        boolean far = hasFar(c) && jp.revati.skyabyss.client.titan.LampBandClient.waypoint() == null;
        out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.OBJECTIVE, jp.revati.skyabyss.hud.HudLayout.Region.TOP_LEFT, jp.revati.skyabyss.hud.HudLayout.Fit.keyed(java.util.List.of(c.text, want, far, mazeBig), (maxW, maxH) -> {
            int w = Math.min(want, maxW - 6);
            if (w < 60) {
                return null;
            }
            int extra = far ? (mazeBig ? 26 : 11) : 0;
            int maxLines = (maxH - 5 - extra) / 10;
            if (maxLines < 1) {
                return null;
            }
            java.util.List<String> lines = wrap(font, c.text, w - 6, Math.min(maxLines, 6));
            // 下に 3 画素の余白 (日本語の字は行の高さいっぱいに描かれ、背景の下へはみ出していた)
            return new jp.revati.skyabyss.hud.HudLayout.Size(w + 6, lines.size() * 10 + 5 + extra);
        })));
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
        Cur c = cur(mc);
        if (c != null) {
            draw(g, mc, c.text, c.resolved);
        }
    }

    // 帯の文字化けの調べ用 (試遊の声: 余燼で帯の文が化けた)。帯の文が変わるたびに、生の文と全コードポイントを debug.log へ ([band] raw=...)。
    // 制御文字・置換文字 (U+FFFD)・§・訳されていないキー・書式の取り残し (%s) を含むときは、latest.log にも WARN で出す。
    // 取り方: 化けたら logs/debug.log (または latest.log) の最後の「[band]」の行を貼る。その行の cp= が化けた字の正体を示す
    private static void dumpBand(String text) {
        StringBuilder cp = new StringBuilder();
        boolean odd = text.indexOf('�') >= 0 || text.indexOf('§') >= 0 || text.contains("%s") || text.contains("%d")
                || text.matches(".*[a-z]+\\.[a-z_]+\\.[a-z_.]+.*");
        text.codePoints().forEach(c -> cp.append(String.format("%04X ", c)));
        if (text.codePoints().anyMatch(c -> c < 0x20 || (c >= 0x7F && c < 0xA0))) {
            odd = true;
        }
        String msg = "[band] raw='" + text.replace("\n", "\\n") + "' len=" + text.length() + " cp=" + cp.toString().trim();
        if (odd) {
            jp.revati.skyabyss.SkyAbyss.LOGGER.warn(msg);
        } else {
            jp.revati.skyabyss.SkyAbyss.LOGGER.debug(msg);
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc, String text, Resolved resolved) {
        Font font = mc.font;
        long now = net.minecraft.Util.getMillis();
        if (!text.equals(lastText)) {
            dumpBand(text);
            if (!lastText.isEmpty()) {
                flashUntil = now + 1400;
            }
            lastText = text;
        }
        // 戦闘で傷を受けた直後・画面を開いている間・ホラー演出 (足音・歌) の最中は薄くする (雰囲気を壊さない)
        boolean quiet = mc.screen != null || mc.player.hurtTime > 0 || OpeningClient.horrorCueActive();
        float a = quiet ? 0.45f : 1f;
        jp.revati.skyabyss.hud.HudLayout.Rect rect = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.OBJECTIVE);
        if (rect == null) {
            return;
        }
        int x = rect.x() + 3;
        int y = rect.y() + 2;
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
        // 光海の迷路: 帯の矢印も距離も出さない (道案内は床の一本の金の線だけ。client/guide/GuideTrailRenderer)。帯は文の行だけ
        boolean big = false;
        if (jp.revati.skyabyss.client.titan.LampBandClient.waypoint() != null) {
            far = "";
            arrow = false;
        }
        boolean withArrow = arrow && target != null && !far.isEmpty();
        int w = rect.w() - 6;
        int extra = far.isEmpty() ? 0 : big ? 26 : 11;
        java.util.List<String> lines = wrap(font, text, w - 6, Math.max(1, (rect.h() - 5 - extra) / 10));
        int bottom = y + lines.size() * 10 + extra + 1;
        float glow = now < flashUntil ? Mth.clamp((flashUntil - now) / 1400f, 0f, 1f) : 0f;
        int bg = ((int) ((0x50 + glow * 0x50) * a)) << 24 | 0x0A0C10;
        g.fill(x - 3, y - 2, x + w + 3, bottom, bg);
        int accent = ((int) (255 * a)) << 24 | 0xF2C14E;
        g.fill(x - 3, y - 2, x - 2, bottom, accent);
        int textColor = ((int) (255 * a)) << 24 | 0xE9E3D4;
        int ty = y + 1;
        for (String line : lines) {
            g.drawString(font, Component.literal(line), x + 1, ty, textColor, true);
            ty += 10;
        }
        lastShown = String.join(" ", lines) + (far.isEmpty() ? "" : "   [" + far + "]");
        lastShownMs = now;
        if (!far.isEmpty()) {
            // 距離は最後の行 (矢印と、その右に距離)
            int fy = ty;
            int fx = x + 1;
            if (withArrow && big) {
                // 2 倍の矢印 (22 画素) と、その右に大きめの距離
                g.pose().pushPose();
                g.pose().translate(fx + GuideArrow.SIZE, fy + GuideArrow.SIZE - 1, 0);
                g.pose().scale(2f, 2f, 1f);
                GuideArrow.draw(g, 0f, 0f, arrowDeg, a);
                g.pose().popPose();
                fx += GuideArrow.SIZE * 2 + 5;
                if (vertical != 0) {
                    GuideArrow.drawVertical(g, fx + 2, fy + 10, vertical > 0, a);
                    fx += 9;
                }
                g.drawString(font, far, fx, fy + 7, ((int) (255 * a)) << 24 | 0xFFD866, true);
            } else {
                if (withArrow) {
                    GuideArrow.draw(g, fx + GuideArrow.SIZE / 2, fy + 4, arrowDeg, a);
                    fx += GuideArrow.SIZE + 3;
                    if (vertical != 0) {
                        GuideArrow.drawVertical(g, fx + 2, fy + 4, vertical > 0, a);
                        fx += 9;
                    }
                }
                g.drawString(font, far, fx, fy, ((int) (255 * a)) << 24 | (resolved.inside() ? 0x9A9AAE : 0x7FD6D0), true);
            }
        }
    }
}

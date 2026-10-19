package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.rebirth.RebirthView;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.TrialBoardNet.Card;
import jp.revati.skyabyss.trial.TrialBoardNet.Chip;
import jp.revati.skyabyss.trial.TrialBoardNet.Row;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 試練の板の中身を作る (サーバー側。docs/design/29 の 2.7)。全部見える。ただし極のうち一つ (最初の一つ) は「？」(入ってから分かる)。
public final class TrialBoard {
    private TrialBoard() {
    }

    // 板を開く。titan が null なら、いまいる背中の巨神 (背中の外なら開かない)
    public static void open(ServerPlayer sp, @Nullable String titan) {
        String t = titan;
        if (t == null) {
            t = Titans.titanOfBack(sp.level().dimension());
        }
        if (t == null || !Titans.hasBack(t)) {
            Notice.bar(sp, ModText.deny("message.skyabyss.trial.board_far"));
            return;
        }
        ServerLevel back = sp.server.getLevel(Titans.backOf(t));
        if (back == null) {
            return;
        }
        PacketDistributor.sendToPlayer(sp, build(sp, back, t));
    }

    public static TrialBoardNet.Open build(ServerPlayer sp, ServerLevel back, String titan) {
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record r = GroupProgress.of(back, group);
        TrialDraw.Draw draw = TrialData.current(titan, r, group, TrialData.worldSeed(back));
        TrialCodex codex = TrialCodex.get(sp.server);
        UUID id = sp.getUUID();
        List<Card> cards = new ArrayList<>();
        boolean hiddenGiven = false;
        for (TrialDraw.Pick p : draw.picks()) {
            Def d = p.def();
            Mods m = p.mods();
            boolean hide = false;
            if (m.extreme() != Extreme.NONE && !m.abyss() && !hiddenGiven) {
                hide = true;
                hiddenGiven = true;
            }
            TrialCodex.Entry e = codex.peek(id, d.id());
            int total = TrialRuntime.modTotal(d);
            int state = e.cleared == 0 ? (e.seen ? 1 : 0) : (total > 0 && e.mods.size() >= total ? 3 : 2);
            cards.add(new Card(d.id(), m.rarity().ordinal(), m.quiet(), chips(d, m, hide), TrialData.isCleared(r, d.id()), state));
        }
        for (TrialDraw.Pick p : draw.picks()) {
            codex.markSeen(id, p.cellId());
        }
        Def weekly = TrialData.weekly(titan, TrialData.worldSeed(back), TrialData.week());
        List<Row> rows = new ArrayList<>();
        for (Def d : TrialCatalog.of(titan)) {
            TrialCodex.Entry e = codex.peek(id, d.id());
            rows.add(new Row(d.id(), e.seen || e.cleared > 0, e.cleared, e.goals, e.mods.size(), TrialRuntime.modTotal(d)));
        }
        boolean weeklyDone = codex.weeklyDone(id, titan) == TrialData.week();
        return new TrialBoardNet.Open(titan, draw.cycle(), cards, weekly == null ? "" : weekly.id(), weeklyDone, weekly != null && draw.has(weekly.id()), rows,
                List.copyOf(codex.titles(id)), Abyss.open(sp), Abyss.on(r));
    }

    public enum Toggle { CHANGED, SAME, LOCKED }

    // つまみの決まり (純粋な部分): 入るには転生 3 回以上、切るのは誰でも。記録 r を書き換える
    public static Toggle applyToggle(BackGroupData.Record r, boolean on, int rebirths) {
        if (Abyss.on(r) == on) {
            return Toggle.SAME;
        }
        if (on && !Abyss.open(rebirths)) {
            return Toggle.LOCKED;
        }
        Abyss.set(r, on);
        return Toggle.CHANGED;
    }

    // 深淵のつまみ (グループごと)。転生 3 回以上の人だけが入れる。切るのは誰でも。変えたら動いている試練を止めて (次に入るとき引き直した修飾で組み直す)、板を開き直す
    public static void toggleAbyss(ServerPlayer sp, String titan, boolean on) {
        if (!Titans.hasBack(titan)) {
            return;
        }
        ServerLevel back = sp.server.getLevel(Titans.backOf(titan));
        if (back == null) {
            return;
        }
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record r = GroupProgress.of(back, group);
        Toggle result = applyToggle(r, on, RebirthView.count(sp));
        if (result == Toggle.SAME) {
            open(sp, titan);
            return;
        }
        if (result == Toggle.LOCKED) {
            Notice.bar(sp, ModText.deny("message.skyabyss.abyss.locked", Abyss.OPEN_AT));
            return;
        }
        r.changed();
        TrialRuntime.stopGroup(back, group);
        TrialRuntime.syncGates(back, group, titan, TrialData.current(back, group));
        for (ServerPlayer m : Groups.online(back, group)) {
            Notice.hint(m, on ? "message.skyabyss.abyss.on" : "message.skyabyss.abyss.off");
        }
        open(sp, titan);
    }

    // 修飾の札 (type:code)。数字は標準も見せる (どの値かが分かるように)
    public static List<Chip> chips(Def d, Mods m, boolean hideExtreme) {
        List<Chip> out = new ArrayList<>();
        Trial.Space sp = d.space();
        if (sp.layouts() > 1) {
            out.add(new Chip("layout", String.valueOf(m.layout() + 1), false, 0));
        }
        for (int i = 0; i < sp.numbers().size(); i++) {
            int lv = m.number(i);
            out.add(new Chip("number", sp.numbers().get(i) + ":" + lv, hideExtreme && m.extreme() == Extreme.GAUNTLET, lv == 1 && !m.abyss() ? 0 : m.abyss() ? 3 : m.extreme() == Extreme.GAUNTLET ? 2 : 1));
        }
        if (sp.enemySets() > 1 && m.enemy() >= 0) {
            out.add(new Chip("enemy", d.id() + ":" + m.enemy(), false, 0));
        }
        for (int h : m.hazards()) {
            out.add(new Chip("hazard", sp.hazards().get(h), hideExtreme && m.extreme() == Extreme.DOUBLE_HAZARD, m.abyss() ? 3 : m.extreme() == Extreme.DOUBLE_HAZARD ? 2 : 1));
        }
        if (m.goal() >= 0) {
            out.add(new Chip("goal", sp.goals().get(m.goal()), false, m.abyss() ? 3 : 1));
        }
        if (m.extreme() != Extreme.NONE) {
            out.add(new Chip("extreme", m.extreme().id(), hideExtreme, Rarity.EXTREME.ordinal()));
        }
        if (m.abyss()) {
            out.add(new Chip("abyss", "", false, Rarity.ABYSS.ordinal()));
        }
        if (m.quiet()) {
            out.add(new Chip("quiet", "", false, 0));
        }
        return out;
    }
}

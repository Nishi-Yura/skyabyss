package jp.revati.skyabyss.dev.play;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.playtest.Playtest;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// /skyabyss play raid <巨神> <部屋> [対象]: 巨神の背の順路のうち、指定の部屋 (レイドの部屋) へ対象 (パーティ) を運ぶ。
// 部屋の指定: 段 1〜3 / 枠 walk|wit|fight / 部屋の id (例 WR1)。順路は公開の入口 (TrialRoute.start / carry) で始め、
// 主線は「順路の最中」の段階 (inscriptions) に合わせる。薄い層: 部屋の建て方や順路の記録は TrialRoute / RaidRun 側が持つ。docs/design/37
public final class PlayRaid {
    private PlayRaid() {
    }

    // 主線を合わせる段階 (順路を歩いている最中)。その主線になければ board
    static final String QUEST_STAGE = "inscriptions";

    // 候補: 段・枠・部屋の id
    public static List<String> rooms() {
        List<String> out = new ArrayList<>(List.of("1", "2", "3", "walk", "wit", "fight"));
        for (String t : ProgressRules.TITAN_ORDER) {
            for (Trial.Slot s : Trial.Slot.values()) {
                for (String id : RaidRooms.idsOf(t, s)) {
                    if (!out.contains(id)) {
                        out.add(id);
                    }
                }
            }
        }
        return out;
    }

    // 部屋の指定の読み (枠か null、部屋の id か null、段か 0)
    private record Spec(@Nullable Trial.Slot slot, @Nullable String roomId, int step) {
    }

    @Nullable
    private static Spec parse(String titan, String spec) {
        if (spec.equals("1") || spec.equals("2") || spec.equals("3")) {
            return new Spec(null, null, Integer.parseInt(spec));
        }
        for (Trial.Slot s : Trial.Slot.values()) {
            if (s.id.equals(spec)) {
                return new Spec(s, null, 0);
            }
        }
        for (Trial.Slot s : Trial.Slot.values()) {
            if (RaidRooms.idsOf(titan, s).contains(spec)) {
                return new Spec(s, spec, 0);
            }
        }
        return null;
    }

    // 準備 (1 人ずつ): 前の巨神は止めた状態、装備はこの巨神に挑む tier、主線はこの巨神の順路の段階
    public static void prepare(ServerPlayer p, int idx) {
        Playtest.skipOpening(p);
        Playtest.grantPrior(p, idx);
        Playtest.gear(p, idx);
        for (int i = 1; i <= idx; i++) {
            if (QuestLog.of(p).entry(PlayChapters.MAIN[i]) == null) {
                Quests.start(p, PlayChapters.MAIN[i]);
            }
        }
        String main = PlayChapters.MAIN[idx];
        QuestDef def = QuestManager.quest(main);
        if (def != null) {
            Quests.setStage(p, main, def.stageIndex(QUEST_STAGE) >= 0 ? QUEST_STAGE : "board");
        }
    }

    // 一つのグループ (背にいる人たち) を部屋へ運ぶ。成功 null、失敗は翻訳キー。背の上にいる前提 (乗せるのは呼ぶ側)
    @Nullable
    public static String enter(ServerLevel back, UUID group, String titan, List<ServerPlayer> members, String specText) {
        if (!RaidRooms.active(titan)) {
            return "commands.skyabyss_play.raid_none";
        }
        Spec spec = parse(titan, specText);
        if (spec == null) {
            return "commands.skyabyss_play.raid_bad_room";
        }
        TrialDraw.Draw draw = TrialData.current(back, group);
        List<TrialDraw.Pick> picks = new ArrayList<>(draw.picks());
        int step = spec.step();
        if (spec.slot() != null) {
            for (int i = 0; i < picks.size(); i++) {
                Trial.Def d = picks.get(i).def();
                if (d != null && d.slot() == spec.slot()) {
                    step = i + 1;
                }
            }
            if (step == 0) {
                return "commands.skyabyss_play.raid_bad_room";
            }
            if (spec.roomId() != null && !picks.get(step - 1).cellId().equals(spec.roomId())) {
                // 引きと違う部屋を指定されたら、その枠の引きを入れ替える (この周の引きとして保存する)
                Trial.Def def = TrialCatalog.get(spec.roomId());
                picks.set(step - 1, new TrialDraw.Pick(def.id(), draw.cycle() <= 1 ? Trial.Mods.PLAIN
                        : TrialDraw.rollMods(def, new Random(def.id().hashCode()))));
                var record = GroupProgress.of(back, group);
                record.putString(TrialData.DRAW, new TrialDraw.Draw(draw.cycle(), List.copyOf(picks)).encode());
                record.changed();
            }
        }
        if (step < 1 || step > picks.size()) {
            return "commands.skyabyss_play.raid_bad_room";
        }
        // 順路を始め (段 1 へ運ぶ)、別の段なら記録の段を移して運び直す
        TrialRoute.start(back, group, titan, members);
        if (step != 1) {
            var r = GroupProgress.of(back, group);
            r.putInt(TrialRoute.STEP, step);
            r.changed();
            TrialRoute.carry(back, group, titan, step, false);
        }
        return null;
    }

    // コマンド: 対象を背に乗せ、全員が乗ったらグループごとに運ぶ (巨神の呼び出しを待つことがある)。受け付けたら null、だめなら翻訳キー
    @Nullable
    public static String run(Collection<ServerPlayer> targets, String titanName, String specText) {
        int idx = Playtest.index(titanName);
        if (idx < 0) {
            return "commands.skyabyss_play.unknown_boss";
        }
        String titan = ProgressRules.TITAN_ORDER.get(idx);
        if (!RaidRooms.active(titan)) {
            return "commands.skyabyss_play.raid_none";
        }
        if (parse(titan, specText) == null) {
            return "commands.skyabyss_play.raid_bad_room";
        }
        List<ServerPlayer> all = List.copyOf(targets);
        Set<UUID> ran = new HashSet<>();
        for (ServerPlayer p : all) {
            prepare(p, idx);
        }
        for (ServerPlayer p : all) {
            Playtest.board(p, idx, pp -> {
                for (ServerPlayer q : all) {
                    if (!Playtest.insideBack(q, idx)) {
                        return;
                    }
                }
                Map<UUID, List<ServerPlayer>> groups = new LinkedHashMap<>();
                for (ServerPlayer q : all) {
                    groups.computeIfAbsent(Groups.keyOf(q), k -> new ArrayList<>()).add(q);
                }
                for (var e : groups.entrySet()) {
                    if (ran.add(e.getKey())) {
                        ServerLevel back = e.getValue().get(0).serverLevel();
                        String miss = enter(back, e.getKey(), titan, e.getValue(), specText);
                        if (miss != null) {
                            pp.sendSystemMessage(Component.translatable(miss, specText));
                        }
                    }
                }
            });
        }
        return null;
    }
}

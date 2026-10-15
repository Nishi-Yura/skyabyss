package jp.revati.skyabyss.quest;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.Affinity;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// クエストの入口。始める・合図・話した・倒した・見回り (伝承・持ち物・場所) で目的を進め、段階を送り、報酬を渡す。
// 記録はプレイヤーごと (QuestLog)。経験値は Growth.discover の鍵で一人一回だけ (やり直しやループで稼げない)
public final class Quests {
    private Quests() {
    }

    // ---------------------------------------------------------------- 始める
    // 始めたら true。定義がない・もう始まっている・終えているなら false
    public static boolean start(ServerPlayer player, String questId) {
        QuestDef def = QuestManager.quest(questId);
        QuestLog log = QuestLog.of(player);
        if (def == null || log.quests.containsKey(questId)) {
            return false;
        }
        QuestLog.Entry entry = new QuestLog.Entry(def.stages().get(0).objectives().size(), player.server.overworld().getGameTime());
        log.quests.put(questId, entry);
        // メインは必ず追跡する。サブは何も追跡していないときだけ
        if (def.main() || log.tracked.isEmpty() || QuestLog.of(player).entry(log.tracked) == null) {
            log.tracked = questId;
        }
        player.sendSystemMessage(ModText.progress("message.skyabyss.quest.started", Component.translatable(def.titleKey())));
        if (log.quests.size() == 1) {
            player.sendSystemMessage(ModText.hint("message.skyabyss.quest.open_hint", Component.keybind("key.skyabyss.quest_log")));
        }
        notice(player, QuestNetwork.Notice.STARTED, questId);
        advance(player, def, entry, true);
        sync(player);
        return true;
    }

    // ---------------------------------------------------------------- 目的を進めるきっかけ
    public static void signal(ServerPlayer player, String signal) {
        bump(player, (def, stage, ob) -> ob.type().equals("signal") && ob.targets().contains(signal));
    }

    // 会話でその節 (node) に入った。topic のない talk は、その NPC と話せば何でもよい
    public static void onTalk(ServerPlayer player, String role, String node) {
        bump(player, (def, stage, ob) -> ob.type().equals("talk") && ob.targets().contains(role)
                && (ob.topic() == null || ob.topic().equals(node)));
    }

    // 生き物が倒れた。倒した人なら距離を問わず、そうでなければ目的の半径の内にいたときだけ数える
    public static void onKill(ServerPlayer player, String entityId, boolean killer, double distance) {
        bump(player, (def, stage, ob) -> ob.type().equals("kill") && ob.targets().contains(entityId) && (killer || distance <= ob.radius()));
    }

    interface Match {
        boolean test(QuestDef def, QuestDef.Stage stage, QuestDef.Objective objective);
    }

    private static void bump(ServerPlayer player, Match match) {
        QuestLog log = QuestLog.of(player);
        boolean changed = false;
        for (Map.Entry<String, QuestLog.Entry> e : List.copyOf(log.quests.entrySet())) {
            QuestLog.Entry entry = e.getValue();
            QuestDef def = QuestManager.quest(e.getKey());
            if (def == null || entry.done || entry.stage >= def.stages().size()) {
                continue;
            }
            QuestDef.Stage stage = def.stages().get(entry.stage);
            boolean hit = false;
            for (int i = 0; i < stage.objectives().size(); i++) {
                QuestDef.Objective ob = stage.objectives().get(i);
                if (entry.progress(i) < ob.count() && match.test(def, stage, ob)) {
                    ensureSize(entry, stage);
                    entry.progress[i]++;
                    hit = true;
                }
            }
            if (hit) {
                changed = true;
                advance(player, def, entry, false);
            }
        }
        if (changed) {
            sync(player);
        }
    }

    // ---------------------------------------------------------------- 見回り (QuestEvents が 1 秒ごとに呼ぶ)
    // slow = true のときは、近くの生き物も調べる (重いので 2 秒ごと)
    public static void poll(ServerPlayer player, boolean slow) {
        QuestLog log = QuestLog.of(player);
        boolean changed = false;
        StoryContext ctx = StoryContext.of(player);
        for (QuestDef def : QuestManager.all()) {
            if (def.startWhen() != null && !log.quests.containsKey(def.id()) && def.startWhen().test(ctx)) {
                start(player, def.id());
            }
        }
        for (Map.Entry<String, QuestLog.Entry> e : List.copyOf(log.quests.entrySet())) {
            QuestLog.Entry entry = e.getValue();
            QuestDef def = QuestManager.quest(e.getKey());
            if (def == null || entry.done || entry.stage >= def.stages().size()) {
                continue;
            }
            if (measure(player, def, entry, slow)) {
                changed = true;
            }
            if (advance(player, def, entry, false)) {
                changed = true;
            }
        }
        if (changed) {
            sync(player);
        }
    }

    // 今の段階の「状態で決まる」目的 (伝承・持ち物・場所・近く・条件) の数を、今の状態に合わせる。変わったら true
    static boolean measure(ServerPlayer player, QuestDef def, QuestLog.Entry entry, boolean slow) {
        QuestDef.Stage stage = def.stages().get(entry.stage);
        ensureSize(entry, stage);
        boolean changed = false;
        for (int i = 0; i < stage.objectives().size(); i++) {
            QuestDef.Objective ob = stage.objectives().get(i);
            int value = switch (ob.type()) {
                case "lore" -> (int) ob.targets().stream().filter(t -> Lore.state(player).has(t)).count();
                case "collect" -> {
                    Item item = Condition.item(ob.targets().get(0));
                    yield item == null ? 0 : player.getInventory().countItem(item);
                }
                case "visit" -> ob.targets().contains(player.level().dimension().location().toString()) ? ob.count() : entry.progress(i);
                case "condition" -> ob.condition().test(StoryContext.of(player)) ? ob.count() : 0;
                case "near" -> slow ? (near(player, ob) ? ob.count() : entry.progress(i)) : entry.progress(i);
                default -> entry.progress(i);
            };
            value = Math.min(value, ob.count());
            // 場所と近くは、一度満たせば戻らない (離れても済んだまま)
            if ((ob.type().equals("visit") || ob.type().equals("near")) && value < entry.progress(i)) {
                value = entry.progress(i);
            }
            if (value != entry.progress[i]) {
                entry.progress[i] = value;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean near(ServerPlayer player, QuestDef.Objective ob) {
        for (String target : ob.targets()) {
            ResourceLocation key = ResourceLocation.tryParse(target);
            EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
            if (type != null && !player.level().getEntities(type, player.getBoundingBox().inflate(ob.radius()), Entity::isAlive).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 段階を送る
    // 今の段階が満たされていれば (または skip_if が真なら) 済ませて次へ。続けて満たされている段階もまとめて送る。
    // 送ったら true。starting は始めた直後 (知らせを重ねないため)
    static boolean advance(ServerPlayer player, QuestDef def, QuestLog.Entry entry, boolean starting) {
        boolean moved = false;
        StoryContext ctx = StoryContext.of(player);
        while (!entry.done && entry.stage < def.stages().size()) {
            QuestDef.Stage stage = def.stages().get(entry.stage);
            ensureSize(entry, stage);
            boolean skip = stage.skipIf() != null && stage.skipIf().test(ctx);
            if (!skip && !satisfied(stage, entry)) {
                break;
            }
            completeStage(player, def, entry, stage);
            moved = true;
            if (entry.stage < def.stages().size()) {
                // 次の段階の「状態で決まる」目的は、着いたとたんに調べる (先に伝承を記していたら、すぐ済む)
                measure(player, def, entry, true);
            }
        }
        if (moved && !entry.done && !starting) {
            notice(player, QuestNetwork.Notice.UPDATED, def.id());
        }
        return moved;
    }

    public static boolean satisfied(QuestDef.Stage stage, QuestLog.Entry entry) {
        boolean any = false;
        boolean all = true;
        for (int i = 0; i < stage.objectives().size(); i++) {
            boolean ok = entry.progress(i) >= stage.objectives().get(i).count();
            any |= ok;
            all &= ok;
        }
        return stage.any() ? any : all;
    }

    private static void completeStage(ServerPlayer player, QuestDef def, QuestLog.Entry entry, QuestDef.Stage stage) {
        StoryContext ctx = StoryContext.of(player);
        stage.actions().forEach(a -> a.run(ctx));
        if (stage.xp() > 0 && Growth.discover(player, "quest:" + def.id() + ":" + stage.id())) {
            Growth.addXp(player, stage.xp(), def.titleKey());
        }
        entry.stage++;
        if (entry.stage >= def.stages().size()) {
            finish(player, def, entry);
        } else {
            entry.progress = new int[def.stages().get(entry.stage).objectives().size()];
        }
    }

    private static void finish(ServerPlayer player, QuestDef def, QuestLog.Entry entry) {
        entry.done = true;
        entry.progress = new int[0];
        entry.finished = player.server.overworld().getGameTime();
        QuestDef.Reward reward = def.reward();
        // 報酬は一人一回 (コマンドでやり直したり、ループで戻ったりしても二度は渡さない)
        if (Growth.discover(player, "quest:" + def.id())) {
            if (reward.xp() > 0) {
                Growth.addXp(player, reward.xp(), def.titleKey());
            }
            for (QuestDef.ItemReward r : reward.items()) {
                Item item = Condition.item(r.item());
                if (item != null) {
                    give(player, new ItemStack(item, r.count()));
                }
            }
            reward.affinity().forEach((role, amount) -> Affinity.add(player, role, amount));
        }
        StoryContext ctx = StoryContext.of(player);
        reward.actions().forEach(a -> a.run(ctx));
        player.sendSystemMessage(ModText.progress("message.skyabyss.quest.finished", Component.translatable(def.titleKey())));
        notice(player, QuestNetwork.Notice.FINISHED, def.id());
        QuestLog log = QuestLog.of(player);
        if (log.tracked.equals(def.id())) {
            log.tracked = nextToTrack(log);
        }
    }

    // 追跡を移す先: 進行中のメイン、なければ進行中のサブ (始めた順)
    static String nextToTrack(QuestLog log) {
        String sub = "";
        for (QuestDef def : QuestManager.all()) {
            QuestLog.Entry e = log.quests.get(def.id());
            if (e != null && !e.done) {
                if (def.main()) {
                    return def.id();
                }
                if (sub.isEmpty()) {
                    sub = def.id();
                }
            }
        }
        return sub;
    }

    // ---------------------------------------------------------------- コマンド・ループ用
    // 段階を直接決める (コマンド用)。stageId が null なら最初から
    public static boolean setStage(ServerPlayer player, String questId, @Nullable String stageId) {
        QuestDef def = QuestManager.quest(questId);
        if (def == null) {
            return false;
        }
        int index = stageId == null ? 0 : def.stageIndex(stageId);
        if (index < 0) {
            return false;
        }
        QuestLog log = QuestLog.of(player);
        QuestLog.Entry entry = log.quests.computeIfAbsent(questId, k -> new QuestLog.Entry(0, player.server.overworld().getGameTime()));
        entry.done = false;
        entry.stage = index;
        entry.progress = new int[def.stages().get(index).objectives().size()];
        measure(player, def, entry, true);
        sync(player);
        return true;
    }

    // 今の段階を済ませて次へ (コマンド用)
    public static boolean forceStage(ServerPlayer player, String questId) {
        QuestDef def = QuestManager.quest(questId);
        QuestLog.Entry entry = QuestLog.of(player).entry(questId);
        if (def == null || entry == null || entry.done) {
            return false;
        }
        completeStage(player, def, entry, def.stages().get(entry.stage));
        if (!entry.done) {
            measure(player, def, entry, true);
            advance(player, def, entry, false);
        }
        sync(player);
        return true;
    }

    public static boolean forget(ServerPlayer player, String questId) {
        QuestLog log = QuestLog.of(player);
        boolean removed = log.quests.remove(questId) != null;
        if (log.tracked.equals(questId)) {
            log.tracked = nextToTrack(log);
        }
        sync(player);
        return removed;
    }

    public static void forgetAll(ServerPlayer player) {
        QuestLog log = QuestLog.of(player);
        log.quests.clear();
        log.tracked = "";
        sync(player);
    }

    public static void track(ServerPlayer player, String questId) {
        QuestLog log = QuestLog.of(player);
        QuestLog.Entry e = log.quests.get(questId);
        if (questId.isEmpty() || (e != null && !e.done)) {
            log.tracked = questId;
            sync(player);
        }
    }

    // 月のループ (M4) で呼ぶ: loop_reset のクエストのうち、まだ終えていないものを最初に戻す。
    // 終えたクエストと、ループに関わらないクエストはそのまま (01 の「攻略済みの章とボス撃破記録」は残る)
    public static void onLoop(ServerPlayer player) {
        QuestLog log = QuestLog.of(player);
        for (QuestDef def : QuestManager.all()) {
            QuestLog.Entry e = log.quests.get(def.id());
            if (def.loopReset() && e != null && !e.done) {
                log.quests.remove(def.id());
            }
        }
        if (!log.tracked.isEmpty() && log.quests.get(log.tracked) == null) {
            log.tracked = nextToTrack(log);
        }
        sync(player);
    }

    // ---------------------------------------------------------------- 道具
    private static void ensureSize(QuestLog.Entry entry, QuestDef.Stage stage) {
        if (entry.progress.length != stage.objectives().size()) {
            entry.progress = java.util.Arrays.copyOf(entry.progress, stage.objectives().size());
        }
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            ItemEntity drop = player.drop(stack, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
    }

    public static void take(ServerPlayer player, Item item, int count) {
        var inv = player.getInventory();
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
        inv.setChanged();
    }

    public static void sync(ServerPlayer player) {
        if (!(player instanceof FakePlayer)) {
            player.syncData(QuestSetup.LOG);
        }
    }

    private static void notice(ServerPlayer player, byte kind, String questId) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new QuestNetwork.Notice(kind, questId));
    }
}

package jp.revati.skyabyss.quest;

import jp.revati.skyabyss.notice.Notice;
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
        return start(player, questId, null);
    }

    // origin: 受けた場所 (言伝ての板など)。繰り返すクエストは、前の周期に終えていれば受け直せる (記録を新しくする)
    public static boolean start(ServerPlayer player, String questId, @Nullable net.minecraft.core.BlockPos origin) {
        QuestDef def = QuestManager.quest(questId);
        QuestLog log = QuestLog.of(player);
        if (def == null) {
            return false;
        }
        QuestLog.Entry old = log.quests.get(questId);
        if (old != null) {
            if (!def.repeats() || !old.done || Repeats.takenThisCycle(player.server, def, old)) {
                return false;
            }
            log.quests.remove(questId);
        }
        QuestLog.Entry entry = new QuestLog.Entry(def.stages().get(0).objectives().size(), player.server.overworld().getGameTime());
        entry.cycle = Repeats.cycle(player.server, def.repeat());
        if (origin != null) {
            entry.origin = origin.asLong();
            entry.originDim = player.level().dimension().location().toString();
        }
        log.quests.put(questId, entry);
        // メインは必ず追跡する。サブは何も追跡していないときだけ
        if (def.main() || log.tracked.isEmpty() || QuestLog.of(player).entry(log.tracked) == null) {
            log.tracked = questId;
        }
        if (log.quests.size() == 1) {
            Notice.hint(player, "message.skyabyss.quest.open_hint", Component.keybind("key.skyabyss.quest_log"));
        }
        notice(player, QuestNetwork.Notice.STARTED, questId);
        jp.revati.skyabyss.guide.PrepQuests.onStarted(player, def);
        advance(player, def, entry, true);
        sync(player);
        return true;
    }

    // ---------------------------------------------------------------- 目的を進めるきっかけ
    public static void signal(ServerPlayer player, String signal) {
        // 進捗 (advancement) へ。背中での試練の達成は、どの巨神の背かも伝える
        jp.revati.skyabyss.advancement.Beats.fire(player, "signal:" + signal);
        if (signal.equals(jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED)) {
            String titan = jp.revati.skyabyss.progress.Titans.titanOfBack(player.level().dimension());
            if (titan != null) {
                jp.revati.skyabyss.advancement.Beats.fire(player, "trial:" + titan);
                // 試練を越えたら、順路が仲間ごと次の試練の入口へ運ぶ (trial/TrialRoute)。「光の中でしゃがむ」手順はなくした
            }
        }
        bump(player, (def, stage, ob) -> ob.type().equals("signal") && ob.targets().contains(signal));
    }

    // 巨神の背の順路を越えた (心臓が止まった)。ticks は順路を始めてから止めるまで
    public static void onRun(ServerPlayer player, String titan, long ticks) {
        bump(player, (def, stage, ob) -> ob.type().equals("run") && (ob.targets().contains(titan) || ob.targets().contains("any"))
                && (ob.radius() <= 0 || ticks <= ob.radius() * 20));
    }

    // 会話でその節 (node) に入った。topic のない talk は、その NPC と話せば何でもよい
    public static void onTalk(ServerPlayer player, String role, String node) {
        jp.revati.skyabyss.advancement.Beats.fire(player, "talk:" + role);
        // 聞いた話題を覚える。今の段階でなくても (先に聞いても、途中で閉じても)、その段に着いたとき・ログイン時の measure が済ませる
        // (始めたあとの、そのクエストのどこかの段の話題だけ。始める前に聞いた話では済まない)
        QuestLog hlog = QuestLog.of(player);
        for (Map.Entry<String, QuestLog.Entry> e : hlog.quests.entrySet()) {
            QuestDef hd = QuestManager.quest(e.getKey());
            if (hd == null || e.getValue().done || hd.repeats()) {
                continue;
            }
            for (QuestDef.Stage hs : hd.stages()) {
                for (QuestDef.Objective ho : hs.objectives()) {
                    if (ho.type().equals("talk") && node.equals(ho.topic()) && ho.targets().contains(role)) {
                        hlog.heard.add(e.getKey() + "|" + role + "/" + node);
                    }
                }
            }
        }
        bump(player, (def, stage, ob) -> ob.type().equals("talk") && ob.targets().contains(role)
                && (ob.topic() == null || ob.topic().equals(node)));
    }

    // 生き物が倒れた。倒した人なら距離を問わず、そうでなければ目的の半径の内にいたときだけ数える
    public static void onKill(ServerPlayer player, String entityId, boolean killer, double distance) {
        bump(player, (def, stage, ob) -> ob.type().equals("kill") && ob.targets().contains(entityId) && (killer || distance <= ob.radius()));
    }

    // パーティの仲間が倒した (経験値の分け前と同じ条件の仲間。party の目的だけを数える)
    public static void onKillShared(ServerPlayer player, String entityId) {
        bump(player, (def, stage, ob) -> ob.type().equals("kill") && ob.party() && ob.targets().contains(entityId));
    }

    // 合図を、本人と、居合わせなかったパーティの仲間 (party の目的だけ) に送る。present は合図を受ける本人たち
    public static void signalWithParty(net.minecraft.server.level.ServerLevel level, java.util.Collection<ServerPlayer> present, String signal) {
        signalWithParty(level, present, signal, id -> level.getServer().getPlayerList().getPlayer(id));
    }

    public static void signalWithParty(net.minecraft.server.level.ServerLevel level, java.util.Collection<ServerPlayer> present, String signal,
                                java.util.function.Function<java.util.UUID, ServerPlayer> lookup) {
        present.forEach(p -> signal(p, signal));
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, present, lookup)) {
            bump(mate, (def, stage, ob) -> ob.type().equals("signal") && ob.party() && ob.targets().contains(signal));
        }
    }

    // 品を納める (言伝ての板を開いたとき、依頼主に話したとき)。今の段階の turn_in の目的で、手持ちが足りるものを受け取って済ませる。
    // giver が null なら板 (板に出すクエストだけ)、役なら、その人が依頼主のクエストだけ。納めたクエストの ID を返す
    public static List<String> turnIn(ServerPlayer player, @Nullable String giver) {
        QuestLog log = QuestLog.of(player);
        List<String> done = new java.util.ArrayList<>();
        for (Map.Entry<String, QuestLog.Entry> e : List.copyOf(log.quests.entrySet())) {
            QuestLog.Entry entry = e.getValue();
            QuestDef def = QuestManager.quest(e.getKey());
            if (def == null || entry.done || entry.stage >= def.stages().size()) {
                continue;
            }
            if (giver == null ? !def.board() : !giver.equals(def.giver())) {
                continue;
            }
            QuestDef.Stage stage = def.stages().get(entry.stage);
            ensureSize(entry, stage);
            boolean hit = false;
            for (int i = 0; i < stage.objectives().size(); i++) {
                QuestDef.Objective ob = stage.objectives().get(i);
                if (ob.type().equals("turn_in") && entry.progress[i] < ob.count()
                        && QuestItems.take(player, ob.targets().get(0), ob.token(), ob.count())) {
                    entry.progress[i] = ob.count();
                    hit = true;
                }
            }
            if (hit) {
                done.add(def.id());
                advance(player, def, entry, false);
            }
        }
        if (!done.isEmpty()) {
            sync(player);
        }
        return done;
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
        StoryContext ctx = StoryContext.of(player);
        for (QuestDef def : QuestManager.all()) {
            if (def.startWhen() != null && !log.quests.containsKey(def.id()) && def.startWhen().test(ctx)) {
                start(player, def.id());
            }
        }
        refresh(player, slow);
    }

    // 受けている頼みの数を今の状態に合わせ、済んだ段を進める (新しい頼みは始めない)。
    // 会話を開く直前に呼ぶ: 段が進んだ直後でも、その人の今の段で話題が決まる。自動で始まる頼みは 1 秒おきの見回りに任せる
    public static void refresh(ServerPlayer player, boolean slow) {
        QuestLog log = QuestLog.of(player);
        boolean changed = false;
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
                case "collect" -> QuestItems.count(player, ob.targets().get(0), ob.token());
                // 着ている (防具の枠にある) 品の種類の数。脱げば戻る
                case "wear" -> QuestItems.worn(player, ob.targets());
                case "herd" -> slow ? herded(player, entry, ob) : entry.progress(i);
                case "visit" -> ob.targets().contains(player.level().dimension().location().toString()) ? ob.count() : entry.progress(i);
                case "condition" -> ob.condition().test(StoryContext.of(player)) ? ob.count() : 0;
                // 聞いた話題の目的は、聞いた記録があれば済む (一度きりのクエストだけ。繰り返すものは毎回聞き直す)
                case "talk" -> !def.repeats() && ob.topic() != null && ob.targets().stream().anyMatch(t -> QuestLog.of(player).heard(def.id(), t, ob.topic()))
                        ? ob.count() : entry.progress(i);
                // 試練のあとの石の合図は、パーティの誰かが鳴らせば全員のもの (グループの記録から数える。増えるだけ)
                case "signal" -> Math.max(entry.progress(i), jp.revati.skyabyss.titan.group.TrialSteles.cleared(player, ob.targets()));
                case "near" -> slow ? (near(player, ob) ? ob.count() : entry.progress(i)) : entry.progress(i);
                default -> entry.progress(i);
            };
            value = Math.min(value, ob.count());
            // 場所と近くは、一度満たせば戻らない (離れても済んだまま)。連れ戻しは、そろった時点で済む (途中の数は今いる数)
            if ((ob.type().equals("visit") || ob.type().equals("near")
                    || (ob.type().equals("herd") && entry.progress(i) >= ob.count())) && value < entry.progress(i)) {
                value = entry.progress(i);
            }
            if (value != entry.progress[i]) {
                entry.progress[i] = value;
                changed = true;
            }
        }
        return changed;
    }

    // 受けた場所 (板) の radius 以内に、その生き物が count 頭いる。本人も近く (32 以内) にいるときだけ数える
    // (遠くの村で、たまたま集まっている羊で済まないように)
    static int herded(ServerPlayer player, QuestLog.Entry entry, QuestDef.Objective ob) {
        if (entry.origin == QuestLog.Entry.NO_ORIGIN || !player.level().dimension().location().toString().equals(entry.originDim)) {
            return 0;
        }
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.of(entry.origin);
        if (player.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > 32 * 32) {
            return 0;
        }
        int n = 0;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(at).inflate(ob.radius(), 4, ob.radius());
        for (String target : ob.targets()) {
            ResourceLocation key = ResourceLocation.tryParse(target);
            EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
            if (type != null) {
                n += player.level().getEntities(type, box, Entity::isAlive).size();
            }
        }
        return n;
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
        // 実績 (進捗・実績のタブ): クエストを終えた・板の頼み・町のクエスト
        jp.revati.skyabyss.advancement.Beats.fire(player, "quest:done");
        if (def.board()) {
            jp.revati.skyabyss.advancement.Beats.fire(player, "quest:board");
        }
        if (def.id().startsWith("town/")) {
            jp.revati.skyabyss.advancement.Beats.fire(player, "quest:town");
        }
        entry.progress = new int[0];
        entry.finished = player.server.overworld().getGameTime();
        QuestDef.Reward reward = def.reward();
        // 報酬は一人一回 (コマンドでやり直したり、ループで戻ったりしても二度は渡さない)。
        // 繰り返すクエストは周期ごとに一回 (記録を消して同じ日に受け直しても、二度は渡さない)
        QuestLog log0 = QuestLog.of(player);
        boolean pay = def.repeats() ? log0.claimed(def.id()) != entry.cycle : Growth.discover(player, "quest:" + def.id());
        if (pay) {
            if (def.repeats()) {
                log0.claimed.put(def.id(), entry.cycle);
            }
            if (reward.xp() > 0) {
                Growth.addXp(player, reward.xp(), def.titleKey());
            }
            for (QuestDef.ItemReward r : reward.items()) {
                Item item = Condition.item(r.item());
                if (item != null) {
                    give(player, QuestItems.make(item, r.count(), r.token(), r.name(), r.lore()));
                }
            }
            reward.affinity().forEach((role, amount) -> Affinity.add(player, role, amount));
            // 言伝ての板の頼みは鐘銭も (docs/design/35 の 4 章)
            jp.revati.skyabyss.facility.Coins.onQuestPaid(player, def);
        }
        StoryContext ctx = StoryContext.of(player);
        reward.actions().forEach(a -> a.run(ctx));
        notice(player, QuestNetwork.Notice.FINISHED, def.id());
        jp.revati.skyabyss.guide.PrepQuests.onFinished(player, def);
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
        log.heard.removeIf(h -> h.startsWith(questId + "|"));
        if (log.tracked.equals(questId)) {
            log.tracked = nextToTrack(log);
        }
        sync(player);
        return removed;
    }

    public static void forgetAll(ServerPlayer player) {
        QuestLog log = QuestLog.of(player);
        log.quests.clear();
        log.heard.clear();
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
        // 留め袋に入る品は袋へ (docs/design/22 の 4.1)
        jp.revati.skyabyss.pouch.PouchRouting.give(player, stack);
        if (!stack.isEmpty()) {
            ItemEntity drop = player.drop(stack, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
    }

    public static void take(ServerPlayer player, Item item, int count) {
        // 留め袋の中から先に払う (22 の 8.3)
        count -= jp.revati.skyabyss.pouch.Pouch.take(player, item, count);
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

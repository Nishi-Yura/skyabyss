package jp.revati.skyabyss.story.ch5.warden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssEntrance;
import jp.revati.skyabyss.story.ch5.AbyssGate;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 番人の悪夢 (docs/design/36 の 9)。心臓の台を右クリックすると、番人を倒したあとの悪夢へ入る二択 (チャット) を出す。
//   入る条件  番人を倒した (WardenArena.cleared)・メインの段が心臓のあと (報告まで・完了)・台のそば・番人がいない
//   段       組 (Groups) が越えた段の次の段。越えた段の最大が組の記録 (十段のあとは十段を繰り返す)
//   終わり   番人が倒れたら段を記録して報酬。間が空いたら (倒れた・離れた) 記録せず閉じる (次は同じ段から)
//   報酬     初めての段: 経験値 100×段・深淵術の点 1 (5 は +2、10 は +4)。繰り返し: 経験値 30 だけ。段 5・10 は言葉が出る
//   記録     組ごとの一番高い段・人ごとの一番高い段 (SavedData)。十段を越えた組は印 (crowned)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Nightmare {
    public static final String ENTER_COMMAND = "skyabyss_nightmare";
    static final double REACH = 6;
    static final int FIRST_XP = 100;
    static final int REPEAT_XP = 30;

    // 今の悪夢 (間に一つ)。いなければ null (普通の戦い・闇)
    @Nullable
    private static Session session;

    public record Session(UUID group, NightmareTier tier) {
    }

    // 入れなかった理由 (メッセージの鍵を決める)
    public enum Result {
        OK, FAR, LOCKED, NOT_READY, BUSY
    }

    private Nightmare() {
    }

    // ---------------------------------------------------------------- 今の悪夢
    public static NightmareTier tier() {
        return session == null ? NightmareTier.NORMAL : session.tier();
    }

    public static boolean active() {
        return session != null;
    }

    @Nullable
    public static Session session() {
        return session;
    }

    // 段を始める (WardenArena.wake のあと)。テストと入るときだけ
    static void begin(UUID group, NightmareTier t) {
        session = new Session(group, t);
    }

    // 悪夢を閉じる (記録しない)。番人を出し直すとき・間が空いたとき。今の悪夢がなければ何もしない
    public static void abort(ServerLevel level) {
        session = null;
    }

    // ---------------------------------------------------------------- 記録
    public static int groupBest(MinecraftServer server, UUID group) {
        return Data.get(server).groups.getOrDefault(group, 0);
    }

    public static int playerBest(MinecraftServer server, UUID player) {
        return Data.get(server).players.getOrDefault(player, 0);
    }

    public static boolean crowned(MinecraftServer server, UUID group) {
        return Data.get(server).crowned.contains(group);
    }

    // 組が次に挑む段 (越えた段の次。十段のあとは十段)
    public static NightmareTier next(MinecraftServer server, UUID group) {
        return NightmareTier.of(Math.min(NightmareTier.MAX, groupBest(server, group) + 1));
    }

    // 段の名前・決まりの文の鍵 (ja/en のデータを直接直す（生成スクリプトはない）)
    public static String nameKey(int n) {
        return "message.skyabyss.abyss_nm.tier." + n + ".name";
    }

    public static String ruleKey(int n) {
        return "message.skyabyss.abyss_nm.tier." + n + ".rule";
    }

    // 初めて越えた段の深淵術の点 (5 と 10 は特別)
    static int pointsFor(int n) {
        return 1 + (n == 5 ? 2 : 0) + (n == 10 ? 4 : 0);
    }

    // ---------------------------------------------------------------- 入る
    // 段 heart の人の条件: 心臓の言葉を聞いたあと (報告の段) か、メインが完了した人
    public static boolean passedHeart(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(AbyssEntrance.QUEST);
        if (e != null && e.done) {
            return true;
        }
        return "report".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST));
    }

    // 台の右クリック: 番人を倒していなければ何もしない (闇の台のまま)。倒したあとは二択を出す。入れたら true (右クリックを止める)
    static boolean offer(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null || !WardenArena.cleared(server)) {
            return false;
        }
        if (!passedHeart(p)) {
            Notice.narrate(p, "message.skyabyss.abyss_nm.not_yet");
            return true;
        }
        if (session != null || WardenArena.current(p.serverLevel()) != null) {
            Notice.hint(p, "message.skyabyss.abyss_nm.busy");
            return true;
        }
        NightmareTier t = next(server, Groups.keyOf(p));
        Notice.narrate(p, "message.skyabyss.abyss_nm.offer", t.n(), Component.translatable(nameKey(t.n())));
        Component choose = Component.translatable("message.skyabyss.abyss_nm.choose", t.n())
                .withStyle(Style.EMPTY.withColor(0xB07CFF).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ENTER_COMMAND + " enter")));
        Notice.send(p, choose);
        return true;
    }

    // 入る (二択を押した人。台のそばで確かめる)。普通の戦いの最中・倒していないときは入れない
    public static Result enter(ServerPlayer p) {
        Result r = check(p);
        if (r != Result.OK) {
            deny(p, r);
            return r;
        }
        MinecraftServer server = p.getServer();
        ServerLevel level = p.serverLevel();
        UUID group = Groups.keyOf(p);
        NightmareTier t = next(server, group);
        HeartWarden w = WardenArena.wake(level, t);
        if (w == null) {
            deny(p, Result.FAR);
            return Result.FAR;
        }
        begin(group, t);
        return Result.OK;
    }

    static Result check(ServerPlayer p) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null || !AbyssWorld.in(p) || !p.isAlive() || p.isSpectator() || !p.blockPosition().closerThan(heart, REACH)) {
            return Result.FAR;
        }
        MinecraftServer server = p.getServer();
        if (server == null || !WardenArena.cleared(server)) {
            return Result.LOCKED;
        }
        if (!passedHeart(p)) {
            return Result.NOT_READY;
        }
        if (session != null || WardenArena.current(p.serverLevel()) != null) {
            return Result.BUSY;
        }
        return Result.OK;
    }

    private static void deny(ServerPlayer p, Result r) {
        switch (r) {
            case FAR -> Notice.hint(p, "message.skyabyss.abyss_nm.far");
            case LOCKED -> Notice.hint(p, "message.skyabyss.abyss_nm.locked");
            case NOT_READY -> Notice.narrate(p, "message.skyabyss.abyss_nm.not_yet");
            case BUSY -> Notice.hint(p, "message.skyabyss.abyss_nm.busy");
            case OK -> {
            }
        }
    }

    // ---------------------------------------------------------------- 倒した
    // 番人が倒れた (WardenArena.fallen から)。間にいる組の人へ記録と報酬。終わりの後片付けは HeartWarden.die の続きで
    static void won(ServerLevel level) {
        Session s = session;
        session = null;
        if (s == null) {
            return;
        }
        List<ServerPlayer> members = new ArrayList<>();
        for (ServerPlayer p : WardenArena.inside(level)) {
            if (Groups.keyOf(p).equals(s.group())) {
                members.add(p);
            }
        }
        settle(level.getServer(), s.group(), s.tier(), members);
        // 深淵の装備 (段が深いほど錨鉄が増える。abyss/gear/AbyssGearLoot)
        jp.revati.skyabyss.abyss.gear.AbyssGearLoot.warden(level, members, s.tier().n());
    }

    // 段の記録と報酬 (初めて / 繰り返し)。first は組の一番高い段より上のとき
    static boolean settle(MinecraftServer server, UUID group, NightmareTier t, List<ServerPlayer> members) {
        Data d = Data.get(server);
        int n = t.n();
        boolean first = n > d.groups.getOrDefault(group, 0);
        for (ServerPlayer p : members) {
            d.players.merge(p.getUUID(), n, Math::max);
        }
        if (first) {
            d.groups.put(group, n);
            if (n == NightmareTier.MAX) {
                d.crowned.add(group);
            }
        }
        d.setDirty();
        int xp = first ? FIRST_XP * n : REPEAT_XP;
        int points = first ? pointsFor(n) : 0;
        for (ServerPlayer p : members) {
            p.giveExperiencePoints(xp);
            if (points > 0) {
                SkillData sd = SkillManager.get(p);
                sd.bonusPoints += points;
                SkillManager.sync(p);
            }
            if (first) {
                Notice.alert(p, "message.skyabyss.abyss_nm.cleared_first", n, xp, points);
                if (n == 5 || n == 10) {
                    Notice.narrate(p, "message.skyabyss.abyss_nm.special_" + n);
                }
            } else {
                Notice.alert(p, "message.skyabyss.abyss_nm.cleared_again", n, xp);
            }
        }
        return first;
    }

    // ---------------------------------------------------------------- 合図
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL) {
            return;
        }
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null || !event.getPos().closerThan(heart, 2.5)) {
            return;
        }
        // 段 heart の人の心臓の言葉 (錨の真実) が先。そのほかは悪夢の二択
        if (AbyssGate.touch(p)) {
            return;
        }
        if (offer(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ENTER_COMMAND)
                .then(Commands.literal("enter").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return enter(p) == Result.OK ? 1 : 0;
                })));
    }

    // ---------------------------------------------------------------- 保存 (人と組の記録)
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        final Map<UUID, Integer> groups = new HashMap<>();
        final Map<UUID, Integer> players = new HashMap<>();
        final Set<UUID> crowned = new HashSet<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_nightmare");
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            readInts(tag.getCompound("Groups"), d.groups);
            readInts(tag.getCompound("Players"), d.players);
            CompoundTag c = tag.getCompound("Crowned");
            for (String k : c.getAllKeys()) {
                d.crowned.add(UUID.fromString(k));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put("Groups", writeInts(groups));
            tag.put("Players", writeInts(players));
            CompoundTag c = new CompoundTag();
            for (UUID u : crowned) {
                c.putBoolean(u.toString(), true);
            }
            tag.put("Crowned", c);
            return tag;
        }

        private static void readInts(CompoundTag tag, Map<UUID, Integer> into) {
            for (String k : tag.getAllKeys()) {
                into.put(UUID.fromString(k), tag.getInt(k));
            }
        }

        private static CompoundTag writeInts(Map<UUID, Integer> m) {
            CompoundTag t = new CompoundTag();
            m.forEach((k, v) -> t.putInt(k.toString(), v));
            return t;
        }
    }
}

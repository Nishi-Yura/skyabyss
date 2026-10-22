package jp.revati.skyabyss.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 町のクエストの限定の人 (docs/design/35 の 3.5)。クエストを進めている人が都にいる間だけ、決まった印に立つ。
//   いるべきか: 都 (KEEP_RADIUS) にいる人のうち、その人のクエストが進行中 (終えていない) の人が一人でもいる
//   立つ所  : その人の今の段に印があればそこ (stageMarks)、なければ mark (町のクエストの家 quest_house_<n> の一階の床)
//   見回り  : 5 秒ごと。いなければ立たせ、要らなければ消し、離れていれば戻す (連れ歩きの間は戻さない)
//   連れ歩き: escortStage の段にいる人が近く (ESCORT_RANGE) にいれば、いちばん近い人に付いて歩く。
//             付いて行った先が escortTo の印の近く (ESCORT_DONE) なら、その段の人たちに合図 escortSignal を送る
// 印がなければ (都がまだ建っていない・設計図が古い) その人は出ない (エラーにしない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TownGuests {
    public static final String ROLE_PREFIX = SkyAbyss.MODID + ":guest_";
    static final int KEEP_RADIUS = 160;
    static final int CHECK_INTERVAL = 100;
    static final double DRIFT = 4;
    static final double ESCORT_RANGE = 24;
    static final double ESCORT_DONE = 7;

    // id: 人 (会話 guest_<id>)、quest: そのクエスト、mark: 立つ印、stageMarks: 段ごとの立つ印 (段 id → 印)、scale: 描く大きさ、
    // escort*: 連れ歩きの段・行き先の印・着いたときの合図 (連れ歩かない人は null)
    public record Guest(String id, String quest, String mark, Map<String, String> stageMarks, float scale,
                        @Nullable String escortStage, @Nullable String escortTo, @Nullable String escortSignal) {
        Guest(String id, String quest, String mark) {
            this(id, quest, mark, Map.of(), 1f, null, null, null);
        }

        public String role() {
            return TownGuestEntity.roleOf(id);
        }

        public String markFor(@Nullable String stage) {
            return stage == null ? mark : stageMarks.getOrDefault(stage, mark);
        }
    }

    // 町のクエストの家の住み手 (35 の 6.7) に合わせた置き場
    public static final List<Guest> ALL = List.of(
            // 一章: 見習いの鯨追い (見張りをやめた元兵士の家に下宿)・鐘守 (広場の西の書き役の家に間借り)
            new Guest("apprentice", "town/ch1_apprentice", "quest_house_9"),
            new Guest("bellkeeper", "town/ch1_bell", "quest_house_1"),
            // 二章: 潮殻から逃げてきた漁師 (漁師の家)・旅の吟遊詩人 (吟遊詩人の仮住まい)
            new Guest("fisher", "town/ch2_net", "quest_house_4"),
            new Guest("bard", "town/ch2_bard", "quest_house_10"),
            // 三章: 雷を怖がる子ども (喪の家の、使われていない子の寝台に隠れている。宿に着いたら宿の客室の寝台)・機織り
            new Guest("child", "town/ch3_thunder", "quest_house_11", Map.of("inn", "inn_bed"), 0.72f,
                    "escort", "npc_innkeeper", "town_child_inn"),
            new Guest("weaver", "town/ch3_weaver", "quest_house_3"),
            // 四章: 灰を運ぶ鍛え手の弟子 (鍛冶場の弟子の下宿)・薬草摘みの老婆
            new Guest("ash_apprentice", "town/ch4_ash", "quest_house_7"),
            new Guest("herbalist", "town/ch4_herbs", "quest_house_8"),
            // 選定 第 2 期 q_houses A: 使っていなかった家 2・5・6
            new Guest("sailor", "town/ch2_sailor", "quest_house_2"),
            new Guest("mother", "town/ch3_lanterns", "quest_house_5"),
            new Guest("baker", "town/ch4_baker", "quest_house_6"));

    @Nullable
    public static Guest byId(String id) {
        for (Guest g : ALL) {
            if (g.id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    @Nullable
    public static Guest byQuest(String quest) {
        for (Guest g : ALL) {
            if (g.quest.equals(quest)) {
                return g;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 印
    // 自己テストが差し替える印 (null なら都の設計図の印)
    @Nullable
    static volatile Map<String, BlockPos> testMarks;
    @Nullable
    static volatile BlockPos testCapital;

    // ほかの包みの自己テスト (五章の入口など) が印を差し替える。null で元に戻す
    public static void setTestMarks(@Nullable Map<String, BlockPos> marks) {
        testMarks = marks;
    }

    public static Map<String, BlockPos> marks(MinecraftServer server) {
        Map<String, BlockPos> t = testMarks;
        return t != null ? t : OpeningSites.shelterExtras(server, "");
    }

    @Nullable
    public static BlockPos mark(MinecraftServer server, String name) {
        return marks(server).get(name);
    }

    @Nullable
    static BlockPos capital(MinecraftServer server) {
        BlockPos t = testCapital;
        return t != null ? t : OpeningSites.shelter(server).orElse(null);
    }

    // ---------------------------------------------------------------- いるべきか
    // その人の、このクエストの今の段 (進行中でなければ null)
    @Nullable
    public static String stageOf(ServerPlayer player, String quest) {
        QuestLog.Entry e = QuestLog.of(player).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || e.done || def == null || e.stage >= def.stages().size()) {
            return null;
        }
        return def.stages().get(e.stage).id();
    }

    // 都にいる人 (オーバーワールド・観戦でない)
    static List<ServerPlayer> inTown(ServerLevel level, BlockPos capital) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : java.util.List.copyOf(level.players())) {
            if (!p.isSpectator() && p.blockPosition().distSqr(capital) <= (double) KEEP_RADIUS * KEEP_RADIUS) {
                out.add(p);
            }
        }
        return out;
    }

    // その人を要る人の今の段 (最初に見つかった人のもの)。誰も要らなければ null
    @Nullable
    static String neededStage(Guest g, List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            String s = stageOf(p, g.quest);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long t = level.getGameTime();
        if (t % 20 != 7) {
            return;
        }
        BlockPos capital = capital(server);
        if (capital == null) {
            return;
        }
        List<ServerPlayer> players = inTown(level, capital);
        if (t % CHECK_INTERVAL == 7) {
            keep(level, capital, players);
        }
        escort(level, players);
        TownQuests.pollSpots(server, players);
    }

    // 立たせる・消す・戻す。立っている人数を返す
    public static int keep(ServerLevel level, BlockPos capital, List<ServerPlayer> players) {
        Map<String, BlockPos> marks = marks(level.getServer());
        int n = 0;
        for (Guest g : ALL) {
            String stage = players.isEmpty() ? null : neededStage(g, players);
            List<TownGuestEntity> found = find(level, capital, g.id);
            if (stage == null) {
                // 都に人がいない間は消さない (読み込まれていない所は触れない)。人がいて誰も要らなければ帰る
                if (!players.isEmpty()) {
                    found.forEach(TownGuestEntity::discard);
                }
                continue;
            }
            BlockPos spot = marks.get(g.markFor(stage));
            if (spot == null) {
                found.forEach(TownGuestEntity::discard);
                continue;
            }
            if (!loaded(level, spot)) {
                continue;
            }
            TownGuestEntity e = found.isEmpty() ? null : found.get(0);
            for (int i = 1; i < found.size(); i++) {
                found.get(i).discard();
            }
            if (e == null) {
                e = spawn(level, g, spot);
            } else if (e.follow() == null && !spot.equals(e.home())
                    || e.follow() == null && e.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > DRIFT * DRIFT) {
                e.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                e.setHome(spot);
            }
            if (e != null) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    public static TownGuestEntity spawn(ServerLevel level, Guest g, BlockPos spot) {
        TownGuestEntity e = TownSetup.GUEST.get().create(level);
        if (e == null) {
            return null;
        }
        e.setGuest(g.id);
        e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        e.setHome(spot);
        level.addFreshEntity(e);
        SkyAbyss.LOGGER.info("[town] guest {} at {}", g.id, spot);
        return e;
    }

    public static List<TownGuestEntity> find(ServerLevel level, BlockPos capital, String id) {
        return level.getEntities(TownSetup.GUEST.get(), new AABB(capital).inflate(KEEP_RADIUS + 40, 128, KEEP_RADIUS + 40),
                e -> e.isAlive() && id.equals(e.guestId()));
    }

    static boolean loaded(ServerLevel level, BlockPos at) {
        ChunkPos c = new ChunkPos(at);
        return level.areEntitiesLoaded(c.toLong());
    }

    // ---------------------------------------------------------------- 連れ歩き (1 秒ごと)
    static void escort(ServerLevel level, List<ServerPlayer> players) {
        BlockPos capital = capital(level.getServer());
        if (capital == null) {
            return;
        }
        for (Guest g : ALL) {
            if (g.escortStage == null) {
                continue;
            }
            for (TownGuestEntity e : find(level, capital, g.id)) {
                ServerPlayer best = null;
                for (ServerPlayer p : players) {
                    if (g.escortStage.equals(stageOf(p, g.quest)) && p.distanceTo(e) <= ESCORT_RANGE
                            && (best == null || p.distanceToSqr(e) < best.distanceToSqr(e))) {
                        best = p;
                    }
                }
                e.setFollow(best == null ? null : best.getUUID());
                BlockPos to = g.escortTo == null ? null : mark(level.getServer(), g.escortTo);
                if (best != null && to != null && e.distanceToSqr(to.getX() + 0.5, to.getY(), to.getZ() + 0.5) <= ESCORT_DONE * ESCORT_DONE) {
                    for (ServerPlayer p : players) {
                        if (g.escortStage.equals(stageOf(p, g.quest)) && p.distanceTo(e) <= ESCORT_RANGE) {
                            Quests.signal(p, g.escortSignal);
                        }
                    }
                    e.setFollow(null);
                }
            }
        }
    }
}

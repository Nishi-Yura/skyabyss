package jp.revati.skyabyss.quest.free;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.Repeats;
import jp.revati.skyabyss.quest.StoryContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 言伝ての板 (日替わり・週替わりの頼みごと)。
//  - 板に出す日替わりは、ワールドの日数で決まる抽選 (その日は全員が同じ顔ぶれを見る)。世界の進みの一つ先の段階のものまで混ぜ、
//    まだ届かない人には題を伏せて見せる (「？？？ 岩鯨の心臓を止めると」)
//  - 一人が一日に受けられる日替わりは QUOTA まで (10 の判断 12)。週替わりは種類ごとに週に一度で、枠に数えない
//  - 受けたまま日が替わっても、消えない (急がせない)。報酬は受けた日の分として一度だけ
//  - 品を納める頼みごとは、板を開いたときに手持ちから受け取る
// 受け口を NPC に替える (10 の判断 13 の B) ときは、会話の結果 open_board を使えば同じ画面が開く (語り部の「村の頼みごと」)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Board {
    // 一日に受けられる日替わりの数 (10 の判断 12 の推奨)
    public static final int QUOTA = 3;
    // 板に出す日替わりの数 (抽選)
    public static final int DRAW = 4;
    // 板 (か NPC) からこれより離れたら、画面から受けられない
    public static final double REACH = 8.0;

    // 行の状態
    public static final byte OPEN = 0;
    public static final byte ACTIVE = 1;
    public static final byte DONE = 2;
    public static final byte FULL = 3;
    public static final byte LOCKED = 4;
    public static final byte CLOSED = 5;

    public record Line(String quest, byte state, int mates) {
    }

    private record Session(BlockPos pos, String dim, boolean npc) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Board() {
    }

    // ---------------------------------------------------------------- 今日の顔ぶれ (ワールドで一つ)
    public static List<QuestDef> todays(MinecraftServer server) {
        long day = Repeats.day(server);
        int world = Progression.worldStage(server);
        List<QuestDef> daily = new ArrayList<>();
        List<QuestDef> weekly = new ArrayList<>();
        for (QuestDef def : QuestManager.all()) {
            if (!def.board() || def.minStage() > world + 1) {
                continue;
            }
            (def.repeat().equals("weekly") ? weekly : daily).add(def);
        }
        daily.sort(Comparator.comparing(QuestDef::id));
        // 日ごとに同じ並び (サーバーを再起動しても、全員が同じ顔ぶれ)
        java.util.Collections.shuffle(daily, new Random(day * 0x9E3779B97F4A7C15L + 0x51ED));
        List<QuestDef> out = new ArrayList<>(daily.subList(0, Math.min(DRAW, daily.size())));
        out.sort(Comparator.comparingInt(QuestDef::order));
        weekly.sort(Comparator.comparingInt(QuestDef::order));
        out.addAll(weekly);
        return out;
    }

    public static boolean canAccept(ServerPlayer player, QuestDef def) {
        return Progression.stage(player) >= def.minStage() && def.unlock().test(StoryContext.of(player));
    }

    // 今日受けた日替わりの数 (終えたものも数える)
    public static int usedToday(ServerPlayer player) {
        long today = Repeats.day(player.server);
        int n = 0;
        for (Map.Entry<String, QuestLog.Entry> e : QuestLog.of(player).quests().entrySet()) {
            QuestDef def = QuestManager.quest(e.getKey());
            if (def != null && def.repeat().equals("daily") && e.getValue().cycle == today) {
                n++;
            }
        }
        return n;
    }

    public static List<Line> lines(ServerPlayer player) {
        List<Line> out = new ArrayList<>();
        List<QuestDef> shown = new ArrayList<>(todays(player.server));
        QuestLog log = QuestLog.of(player);
        // 前の日に受けて、まだ終えていない頼みごとも並べる (今日の抽選に出ていなくても)
        for (QuestDef def : QuestManager.all()) {
            QuestLog.Entry e = log.entry(def.id());
            if (def.board() && e != null && !e.done && !shown.contains(def)) {
                shown.add(def);
            }
        }
        boolean full = usedToday(player) >= QUOTA;
        int stage = Progression.stage(player);
        for (QuestDef def : shown) {
            QuestLog.Entry e = log.entry(def.id());
            byte state;
            if (e != null && !e.done) {
                state = ACTIVE;
            } else if (e != null && Repeats.takenThisCycle(player.server, def, e)) {
                state = DONE;
            } else if (stage < def.minStage()) {
                state = LOCKED;
            } else if (!def.unlock().test(StoryContext.of(player))) {
                state = CLOSED;
            } else if (full && def.repeat().equals("daily")) {
                state = FULL;
            } else {
                state = OPEN;
            }
            out.add(new Line(def.id(), state, matesOn(player, def)));
        }
        return out;
    }

    // 同じパーティで、今の周期にこの頼みごとを受けている仲間の数 (オンラインの人だけ)
    private static int matesOn(ServerPlayer player, QuestDef def) {
        PartyData.Party party = Parties.of(player);
        if (party == null) {
            return 0;
        }
        int n = 0;
        for (ServerPlayer m : Parties.onlineMembers(player.server, party)) {
            if (m == player) {
                continue;
            }
            QuestLog.Entry e = QuestLog.of(m).entry(def.id());
            if (e != null && (!e.done || Repeats.takenThisCycle(player.server, def, e))) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 開く・受ける
    // npc: NPC が板の代わりに見せている (語り部の「村の頼みごと」)。pos はその足もと
    public static void open(ServerPlayer player, BlockPos pos, boolean npc) {
        List<String> handed = Quests.turnIn(player, null);
        for (String id : handed) {
            QuestDef def = QuestManager.quest(id);
            if (def != null) {
                player.sendSystemMessage(ModText.hint("message.skyabyss.board.handed", Component.translatable(def.titleKey())));
            }
        }
        if (!handed.isEmpty()) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.8f, 1.1f);
        }
        SESSIONS.put(player.getUUID(), new Session(pos.immutable(), player.level().dimension().location().toString(), npc));
        send(player, npc);
    }

    private static void send(ServerPlayer player, boolean npc) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new BoardNetwork.Open(npc, usedToday(player), QUOTA,
                Repeats.untilNext(player.server, "daily"), lines(player)));
    }

    // 画面で「受ける」を押した。受けたら true (自己テストからも呼ぶ)
    public static boolean accept(ServerPlayer player, String questId) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || !s.dim().equals(player.level().dimension().location().toString())
                || player.distanceToSqr(s.pos().getX() + 0.5, s.pos().getY() + 0.5, s.pos().getZ() + 0.5) > (REACH + 2) * (REACH + 2)) {
            return false;
        }
        QuestDef def = QuestManager.quest(questId);
        boolean ok = false;
        if (def != null && def.board() && todays(player.server).contains(def) && canAccept(player, def)
                && (!def.repeat().equals("daily") || usedToday(player) < QUOTA)) {
            ok = Quests.start(player, questId, s.pos());
        }
        if (ok) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.0f);
            // 品を納める頼みごとは、もう足りていればその場で
            Quests.turnIn(player, null);
        }
        send(player, s.npc());
        return ok;
    }

    @Nullable
    static BlockPos sessionPos(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.pos();
    }

    static void closed(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}

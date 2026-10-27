package jp.revati.skyabyss.town;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 町のクエスト (docs/design/35 の 3.5) と裏通りの頼み (3.3) の入口と、クエストの外から来る合図。
//   一覧   : ALL (章・種類・前のクエスト)。中身 (段・目的・報酬) はデータ data/skyabyss/quest/town/*.json (データを直接直す（生成スクリプトはない）)
//   入口   : 限定の人のクエスト (kind GUEST) は宿の女将の噂 (rumor) で始まる。墓守・酔いどれの頼みは、その人の会話で始まる
//            (会話の足し書き attendant_<役>_town.json)。噂は、まだ始めていない頼みのことも話す (始めはしない)
//   合図   : town_tower_bell (大鐘楼の頂の鐘を鳴らした)、town_at:<印> (印の近くに立った)、
//            town_child_inn (子どもを宿へ連れてきた。TownGuests)、town_hand:<相手> (会話で品を渡した。会話の do が送る)
//   古井戸 : 井戸の下の宝の箱 (印 well_chest) を開けると、一人一度だけ宝を受け取る (TownData.wellTaken)
public final class TownQuests {
    public enum Kind {
        GUEST, GRAVE, CELLAR
    }

    // chapter: 受けられる章 (1〜4。人の章 = 止めた心臓の数 + 1)。after: 先に終えておくクエスト
    public record TownQuest(String id, int chapter, Kind kind, @Nullable String after) {
        public String rumorKey() {
            return QuestDef.keyBase(id) + ".rumor";
        }
    }

    public static final List<TownQuest> ALL = List.of(
            new TownQuest("town/ch1_apprentice", 1, Kind.GUEST, null),
            new TownQuest("town/ch1_bell", 1, Kind.GUEST, null),
            new TownQuest("town/ch2_net", 2, Kind.GUEST, null),
            new TownQuest("town/ch2_bard", 2, Kind.GUEST, null),
            new TownQuest("town/ch3_thunder", 3, Kind.GUEST, null),
            new TownQuest("town/ch3_weaver", 3, Kind.GUEST, null),
            new TownQuest("town/ch4_ash", 4, Kind.GUEST, null),
            new TownQuest("town/ch4_herbs", 4, Kind.GUEST, null),
            // 家 2・5・6 の頼み (選定 第 2 期 q_houses A)。章の中では後ろに
            new TownQuest("town/ch2_sailor", 2, Kind.GUEST, null),
            new TownQuest("town/ch3_lanterns", 3, Kind.GUEST, null),
            new TownQuest("town/ch4_baker", 4, Kind.GUEST, null),
            // 墓守の名のない墓 (六章まで続く。ここは一章 (花と水) と三章 (足跡と蝋燭) の 2 段。2026-10-27 に 4 段から縮めた)
            new TownQuest("town/grave_1", 1, Kind.GRAVE, null),
            new TownQuest("town/grave_3", 3, Kind.GRAVE, "town/grave_1"),
            // 酒場の地下の酔いどれ (深淵の手がかり。三つ目で地下の奥の壁が開く)
            new TownQuest("town/cellar_1", 1, Kind.CELLAR, null),
            new TownQuest("town/cellar_2", 3, Kind.CELLAR, "town/cellar_1"),
            new TownQuest("town/cellar_3", 4, Kind.CELLAR, "town/cellar_2"));

    public static final String SIGNAL_TOWER_BELL = "town_tower_bell";
    public static final String SIGNAL_AT = "town_at:";
    // 大鐘楼の頂の鐘: 一階の鐘 (印 bell) より上 TOWER_RISE 以上、横に TOWER_REACH 以内の鐘
    static final int TOWER_RISE = 25;
    static final double TOWER_REACH = 8;
    // 印の近く (town_at)
    static final double SPOT_RADIUS = 3.5;
    // 古井戸の宝の印
    public static final String WELL_CHEST = "well_chest";
    public static final int WELL_COINS = 15;
    public static final String WELL_LORE = "town/well_song";

    private TownQuests() {
    }

    @Nullable
    public static TownQuest byId(String id) {
        for (TownQuest q : ALL) {
            if (q.id.equals(id)) {
                return q;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 受けられるか
    // その人の章で受けられ、前のクエストを終えていて、まだ始めていない (純粋に近い。自己テストが章を直接渡す)
    public static boolean available(TownQuest q, int chapter, QuestLog log) {
        if (chapter < q.chapter || log.entry(q.id) != null || QuestManager.quest(q.id) == null) {
            return false;
        }
        if (q.after != null) {
            QuestLog.Entry prev = log.entry(q.after);
            return prev != null && prev.done;
        }
        return true;
    }

    // 噂に出す次のクエスト (限定の人のクエストを先に。章の早い順)。なければ null
    @Nullable
    public static TownQuest nextRumor(int chapter, QuestLog log) {
        for (Kind kind : List.of(Kind.GUEST, Kind.GRAVE, Kind.CELLAR)) {
            for (TownQuest q : ALL) {
                if (q.kind == kind && available(q, chapter, log)) {
                    return q;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 宿の噂 (FacilityActions.addRumorSource)
    // 限定の人のクエストは、噂を聞いたときに始まる (その人が都に現れる)。墓守・酔いどれの頼みは噂で知らせるだけ。
    // どれもなければ古井戸の噂 (まだ宝を受け取っていなければ)、それもなければ null (章ごとの決まった噂)
    @Nullable
    public static Component rumor(ServerPlayer player) {
        TownQuest q = nextRumor(Coins.chapter(player), QuestLog.of(player));
        if (q != null) {
            if (q.kind == Kind.GUEST) {
                // すぐには始めない: 女将の選択肢に「引き受ける / やめておく」を出す (選定 第 2 期 q_rumor B)
                offer(player, true);
            }
            return Component.translatable(q.rumorKey());
        }
        if (!TownData.get(player.server).wellTaken.contains(player.getUUID()) && TownGuests.mark(player.server, WELL_CHEST) != null) {
            return Component.translatable("message.skyabyss.town.rumor.well");
        }
        return null;
    }

    // ---------------------------------------------------------------- 噂の頼みを引き受ける・やめておく (TownActions の accept_offer / decline_offer)
    public static final String INNKEEPER = "skyabyss:attendant_innkeeper";
    public static final String OFFER_FLAG = "town_offer";

    static void offer(ServerPlayer player, boolean on) {
        var flags = jp.revati.skyabyss.npc.NpcBonds.of(player).bond(INNKEEPER).flags();
        if (on) {
            flags.add(OFFER_FLAG);
        } else {
            flags.remove(OFFER_FLAG);
        }
        if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            jp.revati.skyabyss.npc.NpcBonds.sync(player);
        }
    }

    // 噂で聞いた頼み (今の噂の先頭の限定の人のクエスト) を始める。始めたら true
    public static boolean acceptOffer(ServerPlayer player) {
        offer(player, false);
        TownQuest q = nextRumor(Coins.chapter(player), QuestLog.of(player));
        if (q == null || q.kind != Kind.GUEST) {
            return false;
        }
        Quests.start(player, q.id);
        return true;
    }

    public static void declineOffer(ServerPlayer player) {
        offer(player, false);
    }

    // ---------------------------------------------------------------- 印の近く (1 秒ごと。TownGuests の見回りから)
    static void pollSpots(MinecraftServer server, List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return;
        }
        java.util.Map<String, BlockPos> marks = TownGuests.marks(server);
        for (ServerPlayer p : players) {
            for (String signal : wantedSpots(p)) {
                BlockPos at = marks.get(signal.substring(SIGNAL_AT.length()));
                if (at != null && p.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) <= SPOT_RADIUS * SPOT_RADIUS) {
                    Quests.signal(p, signal);
                }
            }
        }
        TownCellar.keepOpen(server);
    }

    // その人の進行中の町のクエストの、今の段の town_at の合図 (まだ済んでいないもの)
    static List<String> wantedSpots(ServerPlayer p) {
        List<String> out = new java.util.ArrayList<>();
        QuestLog log = QuestLog.of(p);
        for (TownQuest q : ALL) {
            QuestLog.Entry e = log.entry(q.id);
            QuestDef def = QuestManager.quest(q.id);
            if (e == null || e.done || def == null || e.stage >= def.stages().size()) {
                continue;
            }
            QuestDef.Stage st = def.stages().get(e.stage);
            for (int i = 0; i < st.objectives().size(); i++) {
                QuestDef.Objective ob = st.objectives().get(i);
                if (ob.type().equals("signal") && e.progress(i) < ob.count()) {
                    ob.targets().stream().filter(s -> s.startsWith(SIGNAL_AT)).forEach(out::add);
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 鐘・古井戸 (手で触れたとき)
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos pos = event.getPos();
        if (player.level().getBlockState(pos).getBlock() instanceof BellBlock) {
            if (topBell(player.server, pos)) {
                Quests.signalWithParty(player.serverLevel(), List.of(player), SIGNAL_TOWER_BELL);
            }
            return;
        }
        BlockPos chest = TownGuests.mark(player.server, WELL_CHEST);
        if (chest != null && pos.distSqr(chest) <= 2) {
            claimWell(player);
        }
    }

    // その鐘が大鐘楼の頂の鐘か (一階の鐘の印 bell の真上の高い所)
    public static boolean topBell(MinecraftServer server, BlockPos pos) {
        BlockPos bell = TownGuests.mark(server, "bell");
        if (bell == null) {
            return false;
        }
        double dx = pos.getX() - bell.getX();
        double dz = pos.getZ() - bell.getZ();
        return pos.getY() - bell.getY() >= TOWER_RISE && dx * dx + dz * dz <= TOWER_REACH * TOWER_REACH;
    }

    // 古井戸の下の宝 (一人一度)。受け取ったら true
    public static boolean claimWell(ServerPlayer player) {
        TownData data = TownData.get(player.server);
        if (!data.wellTaken.add(player.getUUID())) {
            return false;
        }
        data.setDirty();
        Coins.give(player, WELL_COINS);
        Quests.give(player, new ItemStack(jp.revati.skyabyss.item.outfit.MaterialGear.GLINT_SHARD.get()));
        Lore.unlock(player, WELL_LORE, true);
        Notice.narrate(player, "message.skyabyss.town.well.found");
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8f, 0.7f);
        SkyAbyss.LOGGER.info("[town] {} took the old well treasure", player.getGameProfile().getName());
        return true;
    }
}

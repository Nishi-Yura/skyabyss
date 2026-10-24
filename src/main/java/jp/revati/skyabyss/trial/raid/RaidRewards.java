package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

import jp.revati.skyabyss.item.storm.StormGearSetup;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.ember.gear.EmberGearSetup;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.TrialCodex;
import jp.revati.skyabyss.trial.TrialRoute;

// レイドの部屋の報い (docs/design/32 の 9.4)。三つの部屋と中ボスの間を越えたとき、人ごとに渡す。
//  - 素材: その巨神の章の素材 (岩鯨の鱗片・潮殻の殻片・雷翼の欠片・余燼の燠石。TitanLoot の手もとへ入る道と同じ)
//  - 共鳴の欠片: その巨神の欠片 (ModItems.resonanceShard。炉の交換 30 で使う)。中ボスの間は共鳴の結晶も
//  - 称号: 巨神ごとに三つ (full = 順路を初めて全部越えた / rooms = 六つの部屋をすべて越えた / nowipe = 一度も崩れずに順路を越えた)。一度だけ
// 渡し方は経験値 (GrowthRewards.onRouteTrialCleared) と同じ。居合わせた人は満額 (部屋の敵は人数で増える)、
// 同じパーティで居合わせなかった仲間は、その割合 (Parties.shareRate)。渡せない人の分は TitanLoot.handOver が預かる (倒れた人・ログアウトの人)。
// 周回 (初回の順路のあと、祠から) は、部屋の報いを半分、その日に六つを超えたら四分の一。心臓の順路の報い (無傷の分) も周回では半分。
public final class RaidRewards {
    private RaidRewards() {
    }

    // ---------------------------------------------------------------- 数 (ここだけ。32 の 9.4 の表と同じ)
    // 歩・智・闘の一人分 (素材 / 共鳴の欠片)
    public static final int WALK_MATERIAL = 1;
    public static final int WALK_SHARDS = 1;
    public static final int WIT_MATERIAL = 1;
    public static final int WIT_SHARDS = 1;
    public static final int FIGHT_MATERIAL = 2;
    public static final int FIGHT_SHARDS = 1;
    // 中ボスの間の一人分 (素材 / 共鳴の欠片 / 共鳴の結晶)。結晶がこの章の希少な報い
    public static final int MID_MATERIAL = 2;
    public static final int MID_SHARDS = 2;
    public static final int MID_CRYSTALS = 1;
    // 順路を一度も崩さずに越えたとき、心臓を止めた人に渡す共鳴の欠片
    public static final int NO_WIPE_SHARDS = 2;
    // 周回 (初回のあと) の割合。その日に replay の部屋を満額の数 (REPLAY_FULL_ROOMS) まで回したら、以後は REPLAY_TIRED_RATE
    public static final double REPLAY_RATE = 0.5;
    public static final int REPLAY_FULL_ROOMS = 6;
    public static final double REPLAY_TIRED_RATE = 0.25;
    // 一日 (ゲームの時刻 24000 tick)
    public static final long DAY = 24000L;

    // 順路の記録 (BackGroupData の keep.trial.*。TrialRoute.reset が消す): 越えた部屋の id・"mid"・"replay" をコンマで
    public static final String ROUTE = "keep.trial.raid";
    // 順路の中で崩れた (全滅して入口へ戻った) 回数
    public static final String WIPES = "keep.trial.wipes";
    static final String MID_TOKEN = "mid";
    static final String REPLAY_TOKEN = "replay";

    // 称号の種類 (titan を付けて id にする: full.stone_whale ・ rooms.tide_shell ・ nowipe.ember)。lang は trial.skyabyss.title.<id>
    public static final String TITLE_FULL = "full";
    public static final String TITLE_ROOMS = "rooms";
    public static final String TITLE_NOWIPE = "nowipe";

    // 一人分の中身 (種類と数。満額)
    public record Grant(Item item, int count) {
    }

    // ---------------------------------------------------------------- 中身の表
    public static int materialCount(Slot slot) {
        return switch (slot) {
            case WALK -> WALK_MATERIAL;
            case WIT -> WIT_MATERIAL;
            case FIGHT -> FIGHT_MATERIAL;
        };
    }

    public static int shardCount(Slot slot) {
        return switch (slot) {
            case WALK -> WALK_SHARDS;
            case WIT -> WIT_SHARDS;
            case FIGHT -> FIGHT_SHARDS;
        };
    }

    // その巨神の章の素材 (鱗片・殻片・欠片・燠石)
    public static Item materialOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> TideMobSetup.TIDE_SHELL_SHARD.get();
            case Titans.THUNDER_WING -> StormGearSetup.SHARD.get();
            case Titans.EMBER -> EmberGearSetup.SHARD.get();
            default -> ModItems.STONE_WHALE_SHARD.get();
        };
    }

    public static List<Grant> roomGrants(String titan, Slot slot) {
        return List.of(new Grant(materialOf(titan), materialCount(slot)), new Grant(ModItems.resonanceShard(titan), shardCount(slot)));
    }

    public static List<Grant> midGrants(String titan) {
        return List.of(new Grant(materialOf(titan), MID_MATERIAL), new Grant(ModItems.RESONANCE_CRYSTAL.get(), MID_CRYSTALS),
                new Grant(ModItems.resonanceShard(titan), MID_SHARDS));
    }

    // 周回の割合 (その日に回した replay の部屋の数から)
    public static double replayRate(int roomsToday) {
        return roomsToday < REPLAY_FULL_ROOMS ? REPLAY_RATE : REPLAY_TIRED_RATE;
    }

    // ---------------------------------------------------------------- 部屋・中ボスの間を越えた (RaidRun から)
    // 越えた部屋の報い。その背の参加者 (TrialRoute.members) と、同じパーティの居合わせなかった仲間に渡す
    public static void onRoomCleared(ServerLevel back, UUID group, String titan, String roomId, Slot slot) {
        boolean replay = !FirstRun.isFirstRun(back, group);
        BackGroupData.Record r = BackGroupData.get(back).of(group);
        mark(r, roomId);
        if (replay) {
            mark(r, REPLAY_TOKEN);
        }
        List<ServerPlayer> near = TrialRoute.members(back, group);
        List<ServerPlayer> absent = Parties.absentMates(back, near);
        grant(back.getServer(), titan, roomId, roomGrants(titan, slot), near, absent, replay, Parties.shareRate(back.getServer()),
                back.getGameTime() / DAY, back.random);
    }

    // 中ボスの間を越えた (RaidRun から。順路の段 4 へ進む前)
    public static void onMidCleared(ServerLevel back, UUID group, String titan) {
        boolean replay = !FirstRun.isFirstRun(back, group);
        BackGroupData.Record r = BackGroupData.get(back).of(group);
        mark(r, MID_TOKEN);
        if (replay) {
            mark(r, REPLAY_TOKEN);
        }
        List<ServerPlayer> near = TrialRoute.members(back, group);
        List<ServerPlayer> absent = Parties.absentMates(back, near);
        grant(back.getServer(), titan, null, midGrants(titan), near, absent, replay, Parties.shareRate(back.getServer()),
                back.getGameTime() / DAY, back.random);
    }

    // 順路の中で全滅 (RaidRun.wipe から)
    public static void onWipe(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).of(group);
        r.putInt(WIPES, r.getInt(WIPES) + 1);
        r.changed();
    }

    // ---------------------------------------------------------------- 心臓を止めた (TrialRoute.onHeartStopped から。記録を消す前)
    public static void onRouteFinished(ServerLevel back, String titan, BackGroupData.Record r, Collection<UUID> party) {
        // 手もとへ渡すため、オンラインの人は server の一覧から、なければその背の人の一覧 (TrialRoute.PLAYERS) から引く
        grantRoute(back.getServer(), titan, r, party, id -> {
            ServerPlayer p = back.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                for (ServerPlayer q : TrialRoute.PLAYERS.apply(back)) {
                    if (q.getUUID().equals(id)) {
                        return q;
                    }
                }
            }
            return p;
        }, back.random);
    }

    // ---------------------------------------------------------------- 核
    // 部屋 (roomId が null なら中ボスの間) の中身を、near へ満額、absent へは shareRate。周回は割合を掛ける。回数と部屋の称号の数えも
    public static void grant(MinecraftServer server, String titan, @Nullable String roomId, List<Grant> grants, List<ServerPlayer> near,
            List<ServerPlayer> absent, boolean replay, double shareRate, long day, RandomSource random) {
        TrialCodex codex = TrialCodex.get(server);
        for (ServerPlayer p : near) {
            double rate = 1.0;
            if (replay) {
                rate = replayRate(codex.replayRoomsOn(p.getUUID(), day));
                codex.countReplayRoom(p.getUUID(), day);
            }
            give(server, p, p.getUUID(), grants, rate, random);
            if (roomId != null && codex.addRaidRoom(p.getUUID(), roomId)) {
                checkRooms(server, codex, titan, p);
            }
        }
        for (ServerPlayer m : absent) {
            double rate = shareRate * (replay ? replayRate(codex.replayRoomsOn(m.getUUID(), day)) : 1.0);
            give(server, m, m.getUUID(), grants, rate, random);
        }
    }

    // 六つの部屋 (歩・智・闘の二案ずつ) をすべて越えた人に、称号 rooms
    private static void checkRooms(MinecraftServer server, TrialCodex codex, String titan, ServerPlayer p) {
        Set<String> have = codex.raidRooms(p.getUUID());
        for (Slot s : Slot.values()) {
            for (String id : RaidRooms.idsOf(titan, s)) {
                if (!have.contains(id)) {
                    return;
                }
            }
        }
        title(server, codex, p.getUUID(), TITLE_ROOMS + "." + titan, p);
    }

    // 順路の記録から、初めての全部 (三つの部屋・中ボス) と、崩れなし・周回の割合を決めて渡す
    public static void grantRoute(MinecraftServer server, String titan, BackGroupData.Record r, Collection<UUID> party,
            Function<UUID, ServerPlayer> lookup, RandomSource random) {
        Set<String> tokens = tokens(r);
        if (!complete(titan, tokens)) {
            return;
        }
        TrialCodex codex = TrialCodex.get(server);
        boolean replay = tokens.contains(REPLAY_TOKEN);
        boolean noWipe = r.getInt(WIPES) == 0;
        for (UUID id : new LinkedHashSet<>(party)) {
            ServerPlayer p = lookup.apply(id);
            title(server, codex, id, TITLE_FULL + "." + titan, p);
            if (noWipe) {
                title(server, codex, id, TITLE_NOWIPE + "." + titan, p);
                give(server, p, id, List.of(new Grant(ModItems.resonanceShard(titan), NO_WIPE_SHARDS)), replay ? REPLAY_RATE : 1.0, random);
            }
        }
    }

    // 三つの部屋 (枠ごとに一つ) と中ボスの間の両方を越えたか
    public static boolean complete(String titan, Set<String> tokens) {
        if (!tokens.contains(MID_TOKEN)) {
            return false;
        }
        for (Slot s : Slot.values()) {
            boolean any = false;
            for (String id : RaidRooms.idsOf(titan, s)) {
                any |= tokens.contains(id);
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    public static Set<String> tokens(BackGroupData.Record r) {
        Set<String> out = new LinkedHashSet<>();
        String s = r.getString(ROUTE);
        if (!s.isEmpty()) {
            for (String t : s.split(",")) {
                out.add(t);
            }
        }
        return out;
    }

    private static void mark(BackGroupData.Record r, String token) {
        Set<String> tokens = tokens(r);
        if (tokens.add(token)) {
            r.putString(ROUTE, String.join(",", tokens));
            r.changed();
        }
    }

    // 称号を一度だけ得る。初めてなら、オンラインの人に知らせる (偽の人には送らない)
    static void title(MinecraftServer server, TrialCodex codex, UUID uuid, String id, @Nullable ServerPlayer p) {
        if (codex.grantTitle(uuid, "raid." + id)) {
            if (p != null && !(p instanceof FakePlayer)) {
                Notice.unlock(p, "message.skyabyss.raid.title", Component.translatable("trial.skyabyss.title.raid." + id));
            }
        }
    }

    // 一人に渡す。rate は割合 (満額は 1.0)。0 になった品は渡さない。渡せない分は預かる (TitanLoot.handOver)
    static void give(MinecraftServer server, @Nullable ServerPlayer player, UUID uuid, List<Grant> grants, double rate, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        for (Grant g : grants) {
            int n = GrowthRules.roundRandom(g.count() * rate, random.nextDouble());
            if (n > 0) {
                items.add(new ItemStack(g.item(), n));
            }
        }
        if (items.isEmpty()) {
            return;
        }
        int held = TitanLoot.handOver(server, uuid, player, items, 0);
        if (player != null && !(player instanceof FakePlayer)) {
            Notice.gain(player, TitanLoot.rewardLine(items));
            TitanLoot.tellHeld(player, held);
        }
    }
}

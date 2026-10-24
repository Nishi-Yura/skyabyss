package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.raid.RaidRewards;
import jp.revati.skyabyss.util.Players;

// 巨神の順路 (docs/design/32 の 2 章): 歩 → 智 → 闘 → 中ボス → 心臓。どれも体の外の閉じた部屋 (巨神の中は建てない。32 の 2.5)
//  - 段 1〜3 は、そのグループの今の周の引き (TrialData.current) の三つのレイドの部屋 (trial/raid。枠ごとの二案から一つ)
//  - 初回 (物語): 乗り込みで着く到着の台 (entryBox) に、その背にいるグループ全員がそろうと 3 秒の予告のあと部屋 1 の中へ運ぶ (2.2)。
//    ほかのグループの順路が動いていても待たない (部屋は区画ごとに別)
//  - 周回: 祠 (titan/shrine) で品を使うと start が呼ばれる。到着の台では始まらない
//  - 部屋を越えると 4 秒後に、順路の参加者を次の部屋の中へ運ぶ (2.3)。三つ目のあとは中ボスの間の中 (段 3.5)、倒すと心臓の間の中へ
//  - 途中から乗り込んだ仲間は、到着の台に着くと参加者に加わり、今の部屋へ運ばれる (rejoin)
//  - あきらめは焚き火で提案し、全員の「はい」で成立。成立するとグループ全員が背の外へ出される (2.4)
//  - 参加者が背に一人もいなくなって EMPTY_GRACE たつと順路は終わる (背を出る・ログアウト)。3 分の不在のリセットでも終わる
//  - 記録はグループの記録の keep.trial.* (keep. なので試練のリセットでは消えない)。順路を終えるとき (心臓を止めた・あきらめ・
//    背を出た・グループのリセット) に明示的に消す
// 区画 (32 の 5 章) の番号は TrialSlot が割り当てる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TrialRoute {
    private TrialRoute() {
    }

    public static final String STEP = "keep.trial.step";
    // 引いた三つの順 (試練の id のコンマ区切り)
    public static final String ROUTE = "keep.trial.route";
    // 参加者 (UUID のコンマ区切り)
    public static final String PARTY = "keep.trial.party";
    // 始めた時刻 (背のゲームの時刻)
    public static final String START = "keep.trial.start";
    // 中ボスの段 (3.5) にいる
    public static final String MID = "keep.trial.mid";
    // 越えてから次へ運ぶまで (29 の 4.6 の 4 秒)
    public static final int CARRY_DELAY = 80;
    // 入口に全員がそろってから始まるまでの予告
    public static final int ENTRY_WAIT = 60;
    // あきらめの提案が流れるまで / 次の提案まで
    public static final int ABANDON_WAIT = 20 * 30;
    public static final int ABANDON_COOLDOWN = 20 * 120;
    // 参加者が背に一人もいなくなってから順路が終わるまで (倒れて戻る・次元の移り変わりの間は待つ)
    public static final int EMPTY_GRACE = 20 * 10;
    public static final int TICK_EVERY = 10;
    public static final int LAST_STEP = 4;
    // 順路を越えた (心臓を止めた) ときのクエストの合図 (自由なモードの依頼「順路を越える」)
    public static final String QUEST_SIGNAL = "route_cleared";

    private record Pending(UUID group, String titan, int step, boolean mid, long due) {
    }

    private record Abandon(UUID group, String titan, UUID by, Set<UUID> yes, long until) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Abandon> ABANDON = new HashMap<>();
    private static final Map<UUID, Long> ABANDON_LAST = new HashMap<>();
    private static final Map<UUID, Long> ENTRY_START = new HashMap<>();
    private static final Map<UUID, Long> EMPTY_SINCE = new HashMap<>();
    // 同じ試練の二重の合図をまとめる (グループ|鍵)
    private static final Set<String> CLEARED_KEYS = new LinkedHashSet<>();

    // ---------------------------------------------------------------- 順路の形
    // 枠の順 (歩・智・闘) の鍵 (TrialSteles / TrialRegions の key)
    public static List<String> keysOf(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> List.of("0", "2", "1");
            case Titans.TIDE_SHELL, Titans.THUNDER_WING -> List.of("west", "east", "front");
            case Titans.EMBER -> List.of("west", "east", "south");
            default -> List.of();
        };
    }

    // 入口の箱 (到着の台から体へ入る口。docs/design/32 の 2.2)
    @Nullable
    public static AABB entryBox(String titan) {
        // 乗り込むと着く到着の台 (体の外。巨神の中は建てない。docs/design/32 の 2.2)。着いたその場が入口で、歩かずに部屋 1 へ運ばれる
        return switch (titan) {
            case Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER -> {
                BlockPos f = RouteSeals.safeFeet(titan);
                yield new AABB(f.getX() - 6, f.getY() - 2, f.getZ() - 6, f.getX() + 7, f.getY() + 4, f.getZ() + 7);
            }
            default -> null;
        };
    }

    // ---------------------------------------------------------------- 段ごとの試練
    // 段 step (1〜3) の試練 (引きの順)。なければ null
    @Nullable
    public static Def defOf(TrialDraw.Draw draw, int step) {
        List<TrialDraw.Pick> picks = draw.picks();
        return step < 1 || step > picks.size() ? null : picks.get(step - 1).def();
    }

    // 段 step の試練の石の鍵
    @Nullable
    public static String keyOf(String titan, TrialDraw.Draw draw, int step) {
        Def d = defOf(draw, step);
        return d == null ? null : TrialRegions.keyOfDef(titan, d, draw);
    }

    // ---------------------------------------------------------------- 記録
    public static int step(BackGroupData.Record r) {
        return Math.max(0, r.getInt(STEP));
    }

    public static int step(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return r == null ? 0 : step(r);
    }

    public static boolean mid(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return r != null && r.getInt(MID) > 0;
    }

    public static boolean active(ServerLevel back, UUID group) {
        return step(back, group) > 0;
    }

    public static Set<UUID> party(BackGroupData.Record r) {
        Set<UUID> out = new LinkedHashSet<>();
        String s = r.getString(PARTY);
        if (!s.isEmpty()) {
            for (String id : s.split(",")) {
                try {
                    out.add(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                    // 壊れた記録は読み飛ばす
                }
            }
        }
        return out;
    }

    public static boolean isParticipant(ServerLevel back, UUID group, UUID player) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return r != null && party(r).contains(player);
    }

    public static void reset(BackGroupData.Record r) {
        r.remove(STEP);
        r.remove(ROUTE);
        r.remove(PARTY);
        r.remove(START);
        r.remove(MID);
        r.remove(TrialSlot.SLOT);
        r.remove(RaidRewards.ROUTE);
        r.remove(RaidRewards.WIPES);
    }

    // 順路を終える (心臓を止めた・あきらめ・背を出た・グループのリセット)。記録とメモリの待ちを消す
    public static void finish(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r != null) {
            TrialSlot.release(back, r);
            reset(r);
        }
        jp.revati.skyabyss.trial.raid.RaidRun.onFinish(back, group);
        PENDING.remove(group);
        ABANDON.remove(group);
        ENTRY_START.remove(group);
        EMPTY_SINCE.remove(group);
        CLEARED_KEYS.removeIf(s -> s.startsWith(group + "|"));
        sync(back, group);
    }

    // ---------------------------------------------------------------- 始める・進める・運ぶ
    // 順路を始める (入口に全員がそろった / 祠で品を使った)。party = 参加者。段 1 の入口へ運ぶ
    public static void start(ServerLevel back, UUID group, String titan, List<ServerPlayer> party) {
        start(back, group, titan, party, false);
    }

    // 祠から始める周回: 区画の写しは tick ごとに少しずつ写し、段 1 の写しが済んでから運ぶ (祠の帳のあいだ。一 tick に止まらない)
    public static void startSoon(ServerLevel back, UUID group, String titan, List<ServerPlayer> party) {
        start(back, group, titan, party, true);
    }

    private static void start(ServerLevel back, UUID group, String titan, List<ServerPlayer> party, boolean soon) {
        finish(back, group);
        // 前の順路の石・休み場・戦いの進みを戻す (引いた三つ・修飾・周は keep.* なので残る)
        if (GroupProgress.of(back, group).hasProgress()) {
            fullReset(back, group, titan);
        }
        finish(back, group);
        BackGroupData.Record r = GroupProgress.of(back, group);
        TrialDraw.Draw draw = TrialData.current(back, group);
        r.putInt(STEP, 1);
        List<String> ids = new ArrayList<>();
        for (TrialDraw.Pick p : draw.picks()) {
            ids.add(p.cellId());
        }
        r.putString(ROUTE, String.join(",", ids));
        List<String> who = new ArrayList<>();
        for (ServerPlayer p : party) {
            who.add(p.getUUID().toString());
        }
        r.putString(PARTY, String.join(",", who));
        r.putLong(START, back.getGameTime());
        // 区画の番号を取る (部屋は運ぶときに RaidRun がそこへ建てる。docs/design/32 の 5 章)
        TrialSlot.assign(back, r);
        r.changed();
        ENTRY_START.remove(group);
        for (ServerPlayer p : party) {
            // 入口が開く (既存の石の音)
            p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        }
        SkyAbyss.LOGGER.info("Route of the {} started for group {} ({}, party {}, slot {})", titan, group, ids, party.size(), TrialSlot.of(r));
        if (soon) {
            // 祠の帳のあいだ: 次の tick で運ぶ (PENDING)
            PENDING.put(group, new Pending(group, titan, 1, false, back.getGameTime()));
        } else {
            carry(back, group, titan, 1, false);
        }
    }

    @Nullable
    private static String progressName(String titan) {
        return switch (titan) {
            case "whale", Titans.STONE_WHALE -> Titans.STONE_WHALE;
            case "tide", Titans.TIDE_SHELL -> Titans.TIDE_SHELL;
            case "storm", Titans.THUNDER_WING -> Titans.THUNDER_WING;
            case Titans.EMBER -> Titans.EMBER;
            default -> null;
        };
    }

    // 岩鯨のレイドの部屋 1〜3 を越えたときに記す碑文 (一章のメインの段 inscriptions の三つ)
    public static final List<String> WHALE_ROOM_INSCRIPTIONS = List.of("chapter1/inscription_west", "chapter1/inscription_east", "chapter1/inscription_below");

    // 石が鳴った (TrialSteles.onRung から。原型の石も新作の石も)。今の段の試練なら、次の段を記し、CARRY_DELAY のあとに運ぶ
    public static void onCleared(ServerLevel back, UUID group, String titanName, String key, @Nullable ServerPlayer by) {
        onCleared(back, group, titanName, key, by, false);
    }

    // raid: レイドの部屋を越えた (RaidRun.complete から)。石を鳴らさないので、経験値と合図を四体ともここで渡す (P2-1)
    public static void onCleared(ServerLevel back, UUID group, String titanName, String key, @Nullable ServerPlayer by, boolean raid) {
        String titan = progressName(titanName);
        if (titan == null || Titans.backOf(titan) != back.dimension()) {
            return;
        }
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null || step(r) < 1 || step(r) >= LAST_STEP || r.getInt(MID) > 0) {
            return;
        }
        TrialDraw.Draw draw = TrialData.current(back, group);
        int step = step(r);
        if (!key.equals(keyOf(titan, draw, step)) || !CLEARED_KEYS.add(group + "|" + key)) {
            return;
        }
        // 試練を越えた人に紋が 1 つ (同じ試練では一度だけ。docs/design/31 の A14)
        for (ServerPlayer m : members(back, group)) {
            jp.revati.skyabyss.rpg.skill.SkillExtOps.award(m, titan, key);
        }
        if (raid || Titans.STONE_WHALE.equals(titan)) {
            // 試練の踏破の経験値・依頼の合図・巨神ごとの試練の合図。岩鯨は石でも部屋でもここ。
            // ほかの巨神の原型の石は各背の石の仕組みが渡すので、ここではレイドの部屋のときだけ (二重に渡さない)
            List<String> signals = jp.revati.skyabyss.titan.group.TrialSteles.signalsOf(titan);
            String signal = raid && step >= 1 && step <= signals.size() ? signals.get(step - 1) : null;
            jp.revati.skyabyss.rpg.GrowthRewards.onRouteTrialCleared(back, titan, members(back, group), signal);
            if (raid && Titans.STONE_WHALE.equals(titan) && step >= 1 && step <= WHALE_ROOM_INSCRIPTIONS.size()) {
                // 碑文は古い試練の翼にあり、レイドの順路では通らない。部屋を越えたら、その部屋の碑文を記す
                // (入口の間に碑文を置いて読ませる形でも同じ id。先に読んでいれば何も起きない。P1-1)
                for (ServerPlayer m : members(back, group)) {
                    jp.revati.skyabyss.lore.Lore.unlock(m, WHALE_ROOM_INSCRIPTIONS.get(step - 1), true);
                }
            }
        }
        boolean mid = step == 3 && TrialMidBoss.needed(titan, draw);
        int next = mid ? 3 : step + 1;
        r.putInt(STEP, next);
        if (mid) {
            r.putInt(MID, 1);
        }
        r.changed();
        queue(back, group, titan, next, mid);
        sync(back, group);
        Component what = mid ? TrialMidBoss.name(titan) : next >= LAST_STEP ? Component.empty() : trialName(defOf(draw, next));
        for (ServerPlayer p : members(back, group)) {
            Notice.bar(p, ModText.progress(mid ? "message.skyabyss.route.next_mid" : next >= LAST_STEP ? "message.skyabyss.route.next_heart" : "message.skyabyss.route.next", what));
        }
        SkyAbyss.LOGGER.info("Route of the {}: group {} cleared step {} ({}), next {}{}", titan, group, step, key, next, mid ? " (mid-boss)" : "");
    }

    // 開発・撮影: 今の段を越えたことにする (試練なら石が鳴ったのと同じ、中ボスなら倒したのと同じ)。何をしたかを返す
    public static String devPass(ServerLevel back, UUID group, String titan, @Nullable ServerPlayer by) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null || step(r) < 1) {
            return "no route";
        }
        // レイドの部屋が動いていれば、その部屋を実際に越えたことにする (報いも通る。RaidRun.devComplete)
        if (jp.revati.skyabyss.trial.raid.RaidRun.devComplete(group)) {
            return r.getInt(MID) > 0 ? "mid-boss room passed" : "raid room passed (step " + step(r) + ")";
        }
        if (r.getInt(MID) > 0) {
            onMidBossDefeated(back, group, titan);
            return "mid-boss passed";
        }
        int step = step(r);
        if (step >= LAST_STEP) {
            return "at the heart (stop it to finish)";
        }
        String key = keyOf(titan, TrialData.current(back, group), step);
        if (key == null) {
            return "no key for step " + step;
        }
        onCleared(back, group, titan, key, by);
        return "passed step " + step + " (" + key + ")";
    }

    // 中ボスを倒した (TrialMidBoss の見張りから)。中ボスの段なら心臓の前室へ
    public static void onMidBossDefeated(ServerLevel back, UUID group, String titan) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null || r.getInt(MID) <= 0) {
            return;
        }
        r.remove(MID);
        r.putInt(STEP, LAST_STEP);
        r.changed();
        queue(back, group, titan, LAST_STEP, false);
        for (ServerPlayer p : members(back, group)) {
            Notice.bar(p, ModText.progress("message.skyabyss.route.next_heart", Component.empty()));
        }
        SkyAbyss.LOGGER.info("Route of the {}: group {} beat the mid-boss", titan, group);
    }

    // そのグループの今の段 (目的の帯・パーティの札へ送る中身)
    public static TrialBoardNet.RouteState routeState(ServerLevel back, UUID group, String titan) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        int step = r == null ? 0 : step(r);
        boolean mid = r != null && r.getInt(MID) > 0;
        TrialDraw.Draw draw = step > 0 ? TrialData.current(back, group) : null;
        Def d = draw == null || mid ? null : defOf(draw, step);
        return new TrialBoardNet.RouteState(titan, step, mid, d == null ? "" : d.id(), d == null ? "" : d.slot().id,
                draw == null ? 0 : draw.cycle(), step > 0 && PENDING.containsKey(group));
    }

    // グループのオンラインの人へ、順路の今の段を送る (目的の帯・パーティの札。docs/design/32 の 6 章)
    public static void sync(ServerLevel back, UUID group) {
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            return;
        }
        TrialBoardNet.RouteState state = routeState(back, group, titan);
        for (ServerPlayer p : Players.snapshot(back.getServer().getPlayerList().getPlayers())) {
            if (Groups.isMember(p, group) && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, state);
            }
        }
    }

    private static void queue(ServerLevel back, UUID group, String titan, int step, boolean mid) {
        PENDING.put(group, new Pending(group, titan, step, mid, back.getGameTime() + CARRY_DELAY));
    }

    public static boolean pending(UUID group) {
        return PENDING.containsKey(group);
    }

    public static Component trialName(@Nullable Def def) {
        return def == null ? Component.empty() : Component.translatable(def.nameKey());
    }

    // その背にいる人の一覧 (自己テストは偽の人の一覧に差し替える。偽の人は level.players() に入らないため)
    public static java.util.function.Function<ServerLevel, List<ServerPlayer>> PLAYERS = l -> Players.snapshot(l.players());

    // その背にいる、グループの生きている人 (観戦者を除く)
    public static List<ServerPlayer> present(ServerLevel back, UUID group) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : PLAYERS.apply(back)) {
            if (p.isAlive() && !p.isSpectator() && p.level() == back && Groups.isMember(p, group)) {
                list.add(p);
            }
        }
        return list;
    }

    // その背にいる順路の参加者 (順路が動いていなければ、その背にいるグループの人)
    public static List<ServerPlayer> members(ServerLevel back, UUID group) {
        List<ServerPlayer> all = present(back, group);
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        Set<UUID> party = r == null ? Set.of() : party(r);
        if (party.isEmpty()) {
            return all;
        }
        all.removeIf(p -> !party.contains(p.getUUID()));
        return all;
    }

    // 段 step の入口の休み場へ、その背にいる参加者を運ぶ。運んだ人数
    public static int carry(ServerLevel back, UUID group, String titan, int step, boolean mid) {
        // レイドの部屋の段は、区画に部屋を建てて入口の間へ (trial/raid/RaidRun)
        if (!mid && jp.revati.skyabyss.trial.raid.RaidRun.enter(back, group, titan, step)) {
            sync(back, group);
            return members(back, group).size();
        }
        // 中ボスの段は、区画に中ボスの間を建てて、そのまま間の中へ (trial/raid/mid)
        if (mid && jp.revati.skyabyss.trial.raid.RaidRun.enterMid(back, group, titan)) {
            sync(back, group);
            return members(back, group).size();
        }
        // 心臓の段は、体の外の封じた心臓の間 (trial/raid/InstanceArenas) の中へ直に運ぶ (前室を歩かない。中に入ると集合の 5 秒のあと封じる)
        if (!mid && step >= LAST_STEP) {
            BlockPos in = jp.revati.skyabyss.trial.raid.InstanceArenas.heartEntry(titan);
            if (in != null) {
                return carryIntoHeart(back, group, titan, TrialData.current(back, group), in);
            }
        }
        SkyAbyss.LOGGER.warn("Route of the {}: nowhere to carry group {} for step {}{}", titan, group, step, mid ? " (mid)" : "");
        return 0;
    }

    private static int carryIntoHeart(ServerLevel back, UUID group, String titan, TrialDraw.Draw draw, BlockPos at) {
        float yaw = jp.revati.skyabyss.trial.raid.InstanceArenas.heartEntryYaw(titan);
        int n = 0;
        for (ServerPlayer p : members(back, group)) {
            move(back, p, at, yaw);
            n++;
            Notice.narrate(p, "message.skyabyss.route.heart", Component.empty());
            RouteCard.show(p, back, group, titan, draw, LAST_STEP, false);
        }
        back.playSound(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        sync(back, group);
        SkyAbyss.LOGGER.info("Route of the {}: group {} carried into the heart arena at {} x{}", titan, group, at.toShortString(), n);
        return n;
    }

    public static void move(ServerLevel back, ServerPlayer p, BlockPos at) {
        move(back, p, at, p.getYRot());
    }

    public static void move(ServerLevel back, ServerPlayer p, BlockPos at, float yaw) {
        p.stopRiding();
        p.teleportTo(back, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            // 試験の偽の人は通信がないので、位置だけ直接動かす
            p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // 立つ位置 at から、目の高さで 16 の向きへ見通しを測り、いちばん遠くまで空いている向き (yaw) を返す。足と頭の高さが空いている間を数える
    public static float facingAt(ServerLevel back, BlockPos at) {
        return facingAt(back, at, null);
    }

    // toward があれば、見通しが最長の 3/4 以上の向きのうち toward に近い向き
    public static float facingAt(ServerLevel back, BlockPos at, @Nullable BlockPos toward) {
        int[] dist = new int[16];
        int best = -1;
        float bestYaw = 0f;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 16; i++) {
            float yaw = i * 22.5f;
            double dx = -Math.sin(Math.toRadians(yaw));
            double dz = Math.cos(Math.toRadians(yaw));
            int d = 0;
            for (int k = 1; k <= 32; k++) {
                int x = (int) Math.floor(at.getX() + 0.5 + dx * k);
                int z = (int) Math.floor(at.getZ() + 0.5 + dz * k);
                m.set(x, at.getY() + 1, z);
                if (!back.getBlockState(m).getCollisionShape(back, m).isEmpty()) {
                    break;
                }
                m.set(x, at.getY(), z);
                if (!back.getBlockState(m).getCollisionShape(back, m).isEmpty()) {
                    break;
                }
                d = k;
            }
            dist[i] = d;
            if (d > best) {
                best = d;
                bestYaw = yaw;
            }
        }
        if (toward == null || best <= 0) {
            return bestYaw;
        }
        double want = Math.toDegrees(Math.atan2(-(toward.getX() + 0.5 - (at.getX() + 0.5)), toward.getZ() + 0.5 - (at.getZ() + 0.5)));
        double bestDiff = Double.MAX_VALUE;
        float chosen = bestYaw;
        for (int i = 0; i < 16; i++) {
            if (dist[i] * 4 < best * 3) {
                continue;
            }
            double diff = Math.abs(net.minecraft.util.Mth.wrapDegrees(i * 22.5 - want));
            if (diff < bestDiff) {
                bestDiff = diff;
                chosen = i * 22.5f;
            }
        }
        return chosen;
    }

    // ---------------------------------------------------------------- tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String titan = Titans.titanOfBack(level.dimension());
        if (titan == null || level.getGameTime() % TICK_EVERY != 0) {
            return;
        }
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            List<ServerPlayer> players = PLAYERS.apply(level);
            if (players.isEmpty() && PENDING.isEmpty() && EMPTY_SINCE.isEmpty()) {
                return;
            }
            tick(level, titan, players, level.getGameTime());
        } finally {
            jp.revati.skyabyss.dev.Prof.end("server.trialRoute", prof);
        }
    }

    public static void tick(ServerLevel back, String titan, List<ServerPlayer> players, long now) {
        for (Pending p : new ArrayList<>(PENDING.values())) {
            if (now < p.due() || Titans.backOf(p.titan()) != back.dimension()) {
                continue;
            }
            PENDING.remove(p.group());
            carry(back, p.group(), p.titan(), p.step(), p.mid());
        }
        tickEntry(back, titan, players, now);
        tickAbandon(back, now);
        tickGroups(back, titan, now);
    }

    // 開発用: 環境変数 SKYABYSS_ROUTE_TRACE があると、入口の箱の判断 (待つ・断る・始める) が変わるたびに記録へ書く (R6 の撮影で、始まらない理由を見る)
    private static final boolean TRACE = System.getenv("SKYABYSS_ROUTE_TRACE") != null;
    private static final Map<UUID, String> TRACE_LAST = new HashMap<>();

    private static void trace(UUID group, String titan, String what) {
        if (TRACE && !what.equals(TRACE_LAST.put(group, what))) {
            SkyAbyss.LOGGER.info("Route entry of the {} for group {}: {}", titan, group, what);
        }
    }

    private static void tickEntry(ServerLevel back, String titan, List<ServerPlayer> players, long now) {
        AABB box = entryBox(titan);
        if (box == null || players.isEmpty()) {
            return;
        }
        Set<UUID> groups = new LinkedHashSet<>();
        for (ServerPlayer p : players) {
            if (p.isAlive() && !p.isSpectator() && box.contains(p.position())) {
                groups.add(Groups.keyOf(p));
            }
        }
        if (TRACE) {
            for (ServerPlayer p : players) {
                if (!groups.contains(Groups.keyOf(p))) {
                    trace(Groups.keyOf(p), titan, "outside the box at " + p.blockPosition().toShortString() + " (box " + box + ")");
                }
            }
        }
        ENTRY_START.keySet().removeIf(g -> !groups.contains(g));
        for (UUID group : groups) {
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer p : players) {
                if (p.isAlive() && !p.isSpectator() && Groups.isMember(p, group) && box.contains(p.position())) {
                    inside.add(p);
                }
            }
            if (active(back, group)) {
                trace(group, titan, "already on the route");
                // 順路の途中で到着の台に来た仲間 (あとから乗り込んだ・台へ戻された) は、仲間のいる今の部屋へそのまま運ぶ (2.2)
                for (ServerPlayer p : inside) {
                    rejoin(back, group, titan, p);
                }
                continue;
            }
            if (!FirstRun.isFirstRun(back, group)) {
                trace(group, titan, "replay group: use the shrine");
                // 周回は祠から (決定 204)
                for (ServerPlayer p : inside) {
                    Notice.bar(p, ModText.hint("message.skyabyss.route.use_shrine"));
                }
                continue;
            }
            List<ServerPlayer> all = present(back, group);
            if (inside.size() < all.size()) {
                trace(group, titan, "waiting " + inside.size() + " of " + all.size());
                ENTRY_START.remove(group);
                for (ServerPlayer p : inside) {
                    Notice.bar(p, ModText.progress("message.skyabyss.route.waiting", inside.size(), all.size()));
                }
                continue;
            }
            long t0 = ENTRY_START.computeIfAbsent(group, g -> now);
            long left = ENTRY_WAIT - (now - t0);
            trace(group, titan, left <= 0 ? "starting" : "counting down");
            if (left <= 0) {
                start(back, group, titan, all);
                continue;
            }
            for (ServerPlayer p : inside) {
                Notice.bar(p, ModText.progress("message.skyabyss.route.starting", (left + 19) / 20));
            }
        }
    }

    // 順路の途中で到着の台にいる人を、そのグループの今の段の部屋へ運ぶ (参加者でなければ参加者に加える)。
    // 次の段への運びを待っている間 (越えた 4 秒) は何もしない (その運びで一緒に運ばれる)。運んだか
    public static boolean rejoin(ServerLevel back, UUID group, String titan, ServerPlayer p) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null || step(r) <= 0 || PENDING.containsKey(group)) {
            return false;
        }
        Set<UUID> who = party(r);
        if (who.add(p.getUUID())) {
            List<String> ids = new ArrayList<>();
            for (UUID u : who) {
                ids.add(u.toString());
            }
            r.putString(PARTY, String.join(",", ids));
            r.changed();
        }
        int step = step(r);
        boolean mid = r.getInt(MID) > 0;
        BlockPos at = null;
        float yaw = 180f;
        jp.revati.skyabyss.trial.raid.RaidRun.Run run = jp.revati.skyabyss.trial.raid.RaidRun.of(group);
        if (step >= LAST_STEP) {
            at = jp.revati.skyabyss.trial.raid.InstanceArenas.heartEntry(titan);
            yaw = jp.revati.skyabyss.trial.raid.InstanceArenas.heartEntryYaw(titan);
        } else if (run != null && run.mid == mid && (mid || run.step == step)) {
            at = run.ctx.cell().rest();
        }
        if (at == null) {
            return false;
        }
        move(back, p, at, yaw);
        RouteCard.show(p, back, group, titan, TrialData.current(back, group), step, mid);
        sync(back, group);
        SkyAbyss.LOGGER.info("Route of the {}: {} rejoined group {} at step {}{}", titan, p.getGameProfile().getName(), group, step, mid ? " (mid)" : "");
        return true;
    }

    // 動いている順路の見張り: 中ボスを倒したか・参加者が背に残っているか (背を出る・ログアウトは EMPTY_GRACE の猶予で終わる)
    private static void tickGroups(ServerLevel back, String titan, long now) {
        for (UUID group : BackGroupData.get(back).ids()) {
            BackGroupData.Record r = BackGroupData.get(back).peek(group);
            if (r == null || step(r) < 1) {
                EMPTY_SINCE.remove(group);
                continue;
            }
            if (r.getInt(MID) > 0 && !PENDING.containsKey(group) && TrialMidBoss.defeated(back, group, titan)) {
                onMidBossDefeated(back, group, titan);
            }
            if (now % 200 == 0) {
                // 入り直した人・ログインし直した人にも、今の段を送り直す
                sync(back, group);
            }
            if (!members(back, group).isEmpty() || PENDING.containsKey(group)) {
                EMPTY_SINCE.remove(group);
                continue;
            }
            long t0 = EMPTY_SINCE.computeIfAbsent(group, g -> now);
            if (now - t0 >= EMPTY_GRACE) {
                SkyAbyss.LOGGER.info("Route of group {} ended: no participant left on the back", group);
                end(back, group);
            }
        }
    }

    // 順路を終え、そのグループの試練の進みを戻す (引いた三つと修飾は同じ周のうちは変わらない。29 の 2.2 の 7)
    public static void end(ServerLevel back, UUID group) {
        finish(back, group);
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        String titan = Titans.titanOfBack(back.dimension());
        if (r != null && r.hasProgress() && titan != null) {
            fullReset(back, group, titan);
        }
        // 誓いは順路が終わると降りる (docs/design/32 の 3.1)
        jp.revati.skyabyss.titan.vow.Vows.lift(back, group);
    }

    // 巨神ごとのグループのリセット (戦い・敵・部屋の持ち主・進み。人は動かさない形で呼ぶ)。順路の記録も消える (onGroupReset)
    static void fullReset(ServerLevel back, UUID group, String titan) {
        try {
            switch (titan) {
                case Titans.STONE_WHALE -> TitanBackTrials.resetGroup(back, group);
                case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackTrials.resetGroup(back, group, false, null, false);
                case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackTrials.resetGroup(back, group, false, null, false, false);
                case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackTrials.resetGroup(back, group, false, null, false);
                default -> GroupProgress.reset(back, group);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.warn("Route: group reset of the {} failed for {}: {}", titan, group, e.toString());
            GroupProgress.reset(back, group);
        }
    }

    // ---------------------------------------------------------------- あきらめ (全員の同意)
    // 焚き火から。提案した人は「はい」。一人のグループならその場で成立
    public static boolean propose(ServerPlayer by) {
        if (!(by.level() instanceof ServerLevel back)) {
            return false;
        }
        String titan = Titans.titanOfBack(back.dimension());
        UUID group = Groups.keyOf(by);
        if (titan == null || !active(back, group)) {
            return false;
        }
        long now = back.getGameTime();
        if (ABANDON.containsKey(group)) {
            Notice.hint(by, "message.skyabyss.route.abandon_pending");
            return true;
        }
        Long last = ABANDON_LAST.get(group);
        if (last != null && now - last < ABANDON_COOLDOWN) {
            Notice.hint(by, "message.skyabyss.route.abandon_cooldown", (ABANDON_COOLDOWN - (now - last) + 19) / 20);
            return true;
        }
        Set<UUID> yes = new LinkedHashSet<>();
        yes.add(by.getUUID());
        Abandon a = new Abandon(group, titan, by.getUUID(), yes, now + ABANDON_WAIT);
        ABANDON.put(group, a);
        ABANDON_LAST.put(group, now);
        if (settle(back, a)) {
            return true;
        }
        Component ask = ModText.progress("message.skyabyss.route.abandon_ask", by.getGameProfile().getName());
        Component yesBtn = Component.translatable("message.skyabyss.route.abandon_yes")
                .withStyle(Style.EMPTY.withColor(0x9FE8FF).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyroute yes")));
        Component noBtn = Component.translatable("message.skyabyss.route.abandon_no")
                .withStyle(Style.EMPTY.withColor(0xD9779A).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyroute no")));
        for (ServerPlayer p : members(back, group)) {
            if (!p.getUUID().equals(by.getUUID())) {
                p.sendSystemMessage(Component.empty().append(ask).append(" ").append(yesBtn).append(" ").append(noBtn));
                Notice.bar(p, ask);
                p.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7f, 0.8f);
            }
        }
        Notice.hint(by, "message.skyabyss.route.abandon_proposed");
        return true;
    }

    // /skyroute yes|no
    public static boolean reply(ServerPlayer p, boolean yes) {
        if (!(p.level() instanceof ServerLevel back)) {
            return false;
        }
        UUID group = Groups.keyOf(p);
        Abandon a = ABANDON.get(group);
        if (a == null) {
            Notice.hint(p, "message.skyabyss.route.abandon_none");
            return false;
        }
        if (!yes) {
            ABANDON.remove(group);
            for (ServerPlayer m : members(back, group)) {
                Notice.hint(m, "message.skyabyss.route.abandon_refused", p.getGameProfile().getName());
            }
            return true;
        }
        a.yes().add(p.getUUID());
        return settle(back, a);
    }

    // 全員が「はい」なら成立
    private static boolean settle(ServerLevel back, Abandon a) {
        List<ServerPlayer> all = members(back, a.group());
        for (ServerPlayer m : all) {
            if (!a.yes().contains(m.getUUID())) {
                return false;
            }
        }
        ABANDON.remove(a.group());
        abandon(back, a.group(), a.titan());
        return true;
    }

    // 順路を終え、その背にいるグループ全員を背の外へ出す (初回は地上の巨神の影の下 = Titans.sendDown、周回は祠)
    public static int abandon(ServerLevel back, UUID group, String titan) {
        List<ServerPlayer> all = present(back, group);
        boolean replay = !FirstRun.isFirstRun(back, group);
        end(back, group);
        for (ServerPlayer m : all) {
            Notice.narrate(m, "message.skyabyss.route.abandoned");
            try {
                if (!replay || !jp.revati.skyabyss.titan.shrine.TitanShrine.sendTo(m, titan)) {
                    Titans.sendDown(m, titan);
                }
            } catch (RuntimeException e) {
                // 通信のない偽の人 (自己テスト) など。ほかの人は続けて降ろす
                SkyAbyss.LOGGER.warn("Route abandon: could not send {} out: {}", m.getGameProfile().getName(), e.toString());
            }
        }
        SkyAbyss.LOGGER.info("Route of the {} abandoned by group {} ({} sent out)", titan, group, all.size());
        return all.size();
    }

    private static void tickAbandon(ServerLevel back, long now) {
        for (Abandon a : new ArrayList<>(ABANDON.values())) {
            if (Titans.backOf(a.titan()) != back.dimension()) {
                continue;
            }
            if (now >= a.until()) {
                ABANDON.remove(a.group());
                for (ServerPlayer m : members(back, a.group())) {
                    Notice.hint(m, "message.skyabyss.route.abandon_expired");
                }
            }
        }
    }

    // ---------------------------------------------------------------- 心臓を止めた (TrialData.onHeartStopped の前。記録を消す前)
    public static void onHeartStopped(ServerLevel back, BackGroupData.Record r) {
        if (step(r) < 1) {
            return;
        }
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            return;
        }
        // レイドの部屋の報い: 三つの部屋と中ボスを全部越えた順路なら、称号と崩れなしの分 (記録を消す前に読む)
        RaidRewards.onRouteFinished(back, titan, r, party(r));
        long t = r.has(START) ? Math.max(0, back.getGameTime() - r.getLong(START)) : 0;
        TrialCodex codex = TrialCodex.get(back.getServer());
        for (UUID id : party(r)) {
            ServerPlayer p = back.getServer().getPlayerList().getPlayer(id);
            boolean best = codex.addRoute(id, titan, t);
            if (p != null) {
                // 自由なモードの依頼「順路を越える」(07 の 8 章)
                jp.revati.skyabyss.quest.Quests.signal(p, QUEST_SIGNAL);
            }
            if (p != null && best) {
                Notice.hint(p, "message.skyabyss.route.best", String.format("%d:%02d", t / 20 / 60, t / 20 % 60));
            }
        }
        TrialSlot.release(back, r);
    }

    // ---------------------------------------------------------------- 自己テスト・開発用
    public static int pendingCount() {
        return PENDING.size();
    }

    // あきらめの提案の間隔の待ちを消す (自己テスト)
    public static void clearCooldownForTest(UUID group) {
        ABANDON_LAST.remove(group);
    }

    public static boolean abandonPending(UUID group) {
        return ABANDON.containsKey(group);
    }

    // 待ちの時刻を今にする (自己テスト)
    public static void flushPending() {
        for (Pending p : new ArrayList<>(PENDING.values())) {
            PENDING.put(p.group(), new Pending(p.group(), p.titan(), p.step(), p.mid(), 0));
        }
    }

    public static void clearAll() {
        PENDING.clear();
        ABANDON.clear();
        ABANDON_LAST.clear();
        ENTRY_START.clear();
        EMPTY_SINCE.clear();
        CLEARED_KEYS.clear();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    // 段の記録を文字にする (開発用)
    public static String describe(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null) {
            return "none";
        }
        return "step " + step(r) + (r.getInt(MID) > 0 ? " (mid)" : "") + " route " + r.getString(ROUTE) + " party " + party(r).size() + " slot " + TrialSlot.of(r);
    }
}

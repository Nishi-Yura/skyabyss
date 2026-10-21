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
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.util.Players;

// 巨神の順路 (docs/design/32 の 2 章): 歩 → 智 → 闘 → (中ボス) → 心臓。
//  - 段 1〜3 の試練は、そのグループの今の周の引き (TrialData.current) の順 (歩・智・闘)。周回 1 は原型の三つ、周回 2 からはプールから
//  - 初回 (物語): 入口 (到着の台から体へ入る口) に、その背にいるグループ全員が入ると 5 秒の予告のあと段 1 へ運ぶ (2.2)。
//    体の中の原型は一度に一グループ (決定 207): ほかのグループの初回の順路が動いているあいだは、入口で待つ
//  - 周回: 祠 (titan/shrine) で品を使うと start が呼ばれる。入口の箱では始まらない
//  - 試練を越える (石が鳴る) と 4 秒後に、順路の参加者 (始めたときにいた人) を次の段の入口の休み場へ運ぶ (2.3)。
//    中ボスのいる原型 (TrialMidBoss) を引いていない周は、闘のあと中ボスの前室へ (段 3.5。決定 201)。中ボスを倒すと心臓の前室へ
//  - 途中からは入れない: 順路のあとに背へ来た仲間は運ばれず、入口で「順路の途中」と出る
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
    public static final int ENTRY_WAIT = 100;
    // あきらめの提案が流れるまで / 次の提案まで
    public static final int ABANDON_WAIT = 20 * 30;
    public static final int ABANDON_COOLDOWN = 20 * 120;
    // 参加者が背に一人もいなくなってから順路が終わるまで (倒れて戻る・次元の移り変わりの間は待つ)
    public static final int EMPTY_GRACE = 20 * 10;
    public static final int TICK_EVERY = 10;
    public static final int LAST_STEP = 4;
    public static final String HEART_REST = "heart";
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
    // 原型の枠の順 (歩・智・闘) の石の鍵 (TrialSteles / TrialRegions の key)
    public static List<String> keysOf(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> List.of("0", "2", "1");
            case Titans.TIDE_SHELL, Titans.THUNDER_WING -> List.of("west", "east", "front");
            case Titans.EMBER -> List.of("west", "east", "south");
            default -> List.of();
        };
    }

    // 原型の試練の入口の休み場 (石の鍵から)
    @Nullable
    public static String legacyRest(String titan, String key) {
        return switch (titan) {
            case Titans.STONE_WHALE -> switch (key) {
                case "0" -> "w0";
                case "2" -> TitanBackTrials.MAZE_ENTRY;
                case "1" -> "e0";
                default -> null;
            };
            case Titans.TIDE_SHELL -> switch (key) {
                case "west" -> "g0";
                case "east" -> "s0";
                case "front" -> "f0";
                default -> null;
            };
            case Titans.THUNDER_WING -> switch (key) {
                case "west" -> "w0";
                case "east" -> "e0";
                case "front" -> "f0";
                default -> null;
            };
            case Titans.EMBER -> switch (key) {
                case "west" -> "w0";
                case "east" -> "e0";
                case "south" -> "s0";
                default -> null;
            };
            default -> null;
        };
    }

    // 試練 def の入口の休み場。原型は石の鍵から、新作はセルの最初の休み場 (なければ戻りの休み場)
    @Nullable
    public static String entryRest(String titan, Def def, @Nullable TrialDraw.Draw draw) {
        if (def.original()) {
            String key = TrialRegions.keyOfDef(titan, def, draw);
            return key == null ? null : legacyRest(titan, key);
        }
        if (Titans.THUNDER_WING.equals(titan)) {
            List<jp.revati.skyabyss.titan.storm.back.StormBackLayout.Rest> rs = jp.revati.skyabyss.titan.storm.back.StormDeck.restsOf(def.id());
            if (rs.isEmpty()) {
                rs = jp.revati.skyabyss.titan.storm.back.StormDeck2.restsOf(def.id());
            }
            return rs.isEmpty() ? null : rs.get(0).id();
        }
        TrialCell cell = TrialCells.get(def.id());
        if (cell == null) {
            return null;
        }
        if (!cell.ports().rests().isEmpty()) {
            return cell.ports().rests().get(0).id();
        }
        return cell.ports().returnRest().isEmpty() ? null : cell.ports().returnRest();
    }

    // 休み場の立つ位置 (各背の休み場の表から。立てる場所を探す)
    @Nullable
    public static BlockPos spawnOf(ServerLevel back, String titan, String rest) {
        return switch (titan) {
            case Titans.STONE_WHALE -> TitanBackTrials.restSpawn(back, rest);
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackTrials.restSpawn(back, rest);
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackTrials.restSpawn(back, rest);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackTrials.restSpawn(back, rest);
            default -> null;
        };
    }

    // 入口の箱 (到着の台から体へ入る口。docs/design/32 の 2.2)
    @Nullable
    public static AABB entryBox(String titan) {
        return switch (titan) {
            // 登り口の階段の最初の 5 段 (台の北寄りから東へ)
            case Titans.STONE_WHALE -> {
                int y = TitanBackLayout.platformY() + 1;
                yield new AABB(TitanBackLayout.STAIR_X0, y, TitanBackLayout.STAIR_Z0,
                        TitanBackLayout.STAIR_X0 + 5, y + TitanBackLayout.STAIR_HEIGHT + 2, TitanBackLayout.STAIR_Z1 + 1);
            }
            // 潮門の内側の段
            case Titans.TIDE_SHELL -> new AABB(-111, 63, 82, -104, 71, 89);
            // 尾の広場の北の端 (体へ入る口)
            case Titans.THUNDER_WING -> new AABB(-5, 146, 103, 6, 153, 109);
            // 口の奥の喉の降り口
            case Titans.EMBER -> new AABB(-4, 77, -129, 5, 84, -121);
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

    // 段 step (mid = 中ボスの段) の入口の休み場
    @Nullable
    public static String restOf(String titan, TrialDraw.Draw draw, int step, boolean mid) {
        if (mid) {
            return TrialMidBoss.restOf(titan);
        }
        if (step >= LAST_STEP) {
            return HEART_REST;
        }
        Def d = defOf(draw, step);
        return d == null ? null : entryRest(titan, d, draw);
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
    }

    // 順路を終える (心臓を止めた・あきらめ・背を出た・グループのリセット)。記録とメモリの待ちを消す
    public static void finish(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r != null) {
            TrialSlot.release(back, r);
            reset(r);
        }
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
        TrialSlot.assign(back, r);
        r.changed();
        ENTRY_START.remove(group);
        for (ServerPlayer p : party) {
            // 入口が開く (既存の石の音)
            p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        }
        SkyAbyss.LOGGER.info("Route of the {} started for group {} ({}, party {}, slot {})", titan, group, ids, party.size(), TrialSlot.of(r));
        TrialRuntime.syncGates(back, group, titan, draw);
        carry(back, group, titan, 1, false);
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

    // 石が鳴った (TrialSteles.onRung から。原型の石も新作の石も)。今の段の試練なら、次の段を記し、CARRY_DELAY のあとに運ぶ
    public static void onCleared(ServerLevel back, UUID group, String titanName, String key, @Nullable ServerPlayer by) {
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
        boolean mid = step == 3 && TrialMidBoss.needed(titan, draw);
        int next = mid ? 3 : step + 1;
        r.putInt(STEP, next);
        if (mid) {
            r.putInt(MID, 1);
        }
        r.changed();
        TrialRuntime.syncGates(back, group, titan, draw);
        queue(back, group, titan, next, mid);
        Component what = mid ? TrialMidBoss.name(titan) : next >= LAST_STEP ? Component.empty() : trialName(defOf(draw, next));
        for (ServerPlayer p : members(back, group)) {
            Notice.bar(p, ModText.progress(mid ? "message.skyabyss.route.next_mid" : next >= LAST_STEP ? "message.skyabyss.route.next_heart" : "message.skyabyss.route.next", what));
        }
        SkyAbyss.LOGGER.info("Route of the {}: group {} cleared step {} ({}), next {}{}", titan, group, step, key, next, mid ? " (mid-boss)" : "");
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

    // グループのオンラインの人へ、順路の今の段を送る (目的の帯・パーティの札。docs/design/32 の 6 章)
    public static void sync(ServerLevel back, UUID group) {
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            return;
        }
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        int step = r == null ? 0 : step(r);
        boolean mid = r != null && r.getInt(MID) > 0;
        TrialDraw.Draw draw = step > 0 ? TrialData.current(back, group) : null;
        Def d = draw == null || mid ? null : defOf(draw, step);
        TrialBoardNet.RouteState state = new TrialBoardNet.RouteState(titan, step, mid, d == null ? "" : d.id(), d == null ? "" : d.slot().id,
                draw == null ? 0 : draw.cycle());
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
        TrialDraw.Draw draw = TrialData.current(back, group);
        String rest = restOf(titan, draw, step, mid);
        BlockPos at = rest == null ? null : spawnOf(back, titan, rest);
        if (at == null) {
            SkyAbyss.LOGGER.warn("Route of the {}: no rest for step {}{}", titan, step, mid ? " (mid)" : "");
            return 0;
        }
        if (mid) {
            TrialMidBoss.prime(back, group, titan);
        }
        int n = 0;
        Component what = mid ? TrialMidBoss.name(titan) : step >= LAST_STEP ? Component.empty() : trialName(defOf(draw, step));
        String line = mid ? "message.skyabyss.route.mid" : step >= LAST_STEP ? "message.skyabyss.route.heart" : "message.skyabyss.route.step" + step;
        for (ServerPlayer p : members(back, group)) {
            move(back, p, at);
            n++;
            Notice.narrate(p, line, what);
            RouteCard.show(p, back, group, titan, draw, step, mid);
        }
        back.playSound(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        sync(back, group);
        SkyAbyss.LOGGER.info("Route of the {}: group {} carried to step {}{} ({}) x{}", titan, group, step, mid ? " (mid)" : "", rest, n);
        return n;
    }

    static void move(ServerLevel back, ServerPlayer p, BlockPos at) {
        p.stopRiding();
        p.teleportTo(back, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), p.getXRot());
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            // 試験の偽の人は通信がないので、位置だけ直接動かす
            p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
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
            TrialStones.autoStones(level, titan, players, level.getGameTime());
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

    // 初回の順路が、体の中で動いているほかのグループ (決定 207)
    @Nullable
    private static UUID otherFirstRun(ServerLevel back, UUID group) {
        for (UUID g : BackGroupData.get(back).ids()) {
            if (!g.equals(group) && active(back, g) && FirstRun.isFirstRun(back, g)) {
                return g;
            }
        }
        return null;
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
        ENTRY_START.keySet().removeIf(g -> !groups.contains(g));
        for (UUID group : groups) {
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer p : players) {
                if (p.isAlive() && !p.isSpectator() && Groups.isMember(p, group) && box.contains(p.position())) {
                    inside.add(p);
                }
            }
            if (active(back, group)) {
                // 途中からは入れない (2.2)
                for (ServerPlayer p : inside) {
                    if (!isParticipant(back, group, p.getUUID())) {
                        Notice.bar(p, ModText.hint("message.skyabyss.route.in_progress"));
                    }
                }
                continue;
            }
            if (!FirstRun.isFirstRun(back, group)) {
                // 周回は祠から (決定 204)
                for (ServerPlayer p : inside) {
                    Notice.bar(p, ModText.hint("message.skyabyss.route.use_shrine"));
                }
                continue;
            }
            if (otherFirstRun(back, group) != null) {
                ENTRY_START.remove(group);
                for (ServerPlayer p : inside) {
                    Notice.bar(p, ModText.hint("message.skyabyss.route.body_busy"));
                }
                continue;
            }
            List<ServerPlayer> all = present(back, group);
            if (inside.size() < all.size()) {
                ENTRY_START.remove(group);
                for (ServerPlayer p : inside) {
                    Notice.bar(p, ModText.progress("message.skyabyss.route.waiting", inside.size(), all.size()));
                }
                continue;
            }
            long t0 = ENTRY_START.computeIfAbsent(group, g -> now);
            long left = ENTRY_WAIT - (now - t0);
            if (left <= 0) {
                start(back, group, titan, all);
                continue;
            }
            for (ServerPlayer p : inside) {
                Notice.bar(p, ModText.progress("message.skyabyss.route.starting", (left + 19) / 20));
            }
        }
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
        TrialRuntime.stopGroup(back, group);
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
        TrialStones.clearAll();
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

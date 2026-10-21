package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.RoomPlay;
import jp.revati.skyabyss.entity.ElderSentinel;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.registry.Ch1BackEntities;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.Activity;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupBossRooms;
import jp.revati.skyabyss.titan.group.GroupEvents;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.Reentry;
import jp.revati.skyabyss.titan.group.ReentryRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 岩鯨の背の試練の進行。みんなで何度でも挑めるようにする仕組みをまとめている。
//  - 休憩地点: 休み場に着くと記録され、谷に落ちたり倒れたりすると、そこからやり直せる。
//    記録には試練の番号 (TitanBackData.epoch) を一緒に書き、リセットで番号が変わると無効 (尾の台) になる。
//    乗り込み・地上へ戻る・焚き火であきらめる、でも記録は消える
//  - 倒れても持ち物と経験値は失わない (巨神の背の上でだけ)。預かり物はプレイヤーの永続 NBT に置くので、
//    サーバーを止めても残る。地上のリスポーン地点はそのまま
//  - 番兵の広間と心臓の間: 内側に人が入ると、5 秒の「集合」の間は扉が開いたまま。そのあと扉が閉じ、前室と戸口の人も中へ入れて、
//    中にいる人を参加者として数える (人数で番兵の数と心臓の強さが変わる)。
//    参加者は途中で倒れても参加者のままで、戦いが続く間は扉に触れると内側へ戻れる。
//    広間を制圧すると、出口と入口の両方が開く (倒れて休み場に戻った仲間も追いつける)
//  - 心臓を倒すと、参加者 (途中で倒れた人も含む) に報酬 (TitanLoot)、光の柱で尾の台へ戻れる
//  - 休み場の焚き火にしゃがんで触れると、試練をあきらめて背の入口 (尾の台) へ戻れる
//  - リセット: 背中に誰もいなくなって3分たつか、心臓を倒したあと封印の先に誰もいなくなって30秒たつと、
//    石・封印・広間・心臓が元に戻り、アスレチックと迷路は新しい形になる
//  - 試練で湧いた番兵と心臓はディスクに保存しない (戦いの状態はメモリにしかないため)
//  - ピースフルのあいだは試練が眠る: 祠の階段を封じ、体内の人は尾の台へ出し、戦いは告知なしで最初に戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanBackTrials {
    private static final String TAG_CHECKPOINT = "skyabyss_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_back_epoch";
    private static final String TAG_DIED_ON_BACK = "skyabyss_died_on_back";
    // 倒れたときに預かった持ち物と経験値 (PlayerPersisted の中)
    private static final String TAG_KEPT = "skyabyss_kept";
    // 預かり物のうち、次に目を覚ましたときに足して渡すもの (重なって倒れたときの分)
    private static final String TAG_KEPT_EXTRA = "skyabyss_kept_extra";
    public static final String MOB_TAG = "skyabyss_guard";
    // 心臓 (最終ボス) に付ける印。残ったものを、リセットや起動時に片付けるため
    public static final String HEART_TAG = "skyabyss_heart";
    private static final int RESET_AFTER_IDLE = 20 * 60 * 3;
    // 心臓を倒したあと、封印の先 (宝物庫より奥) に誰もいなくなってから試練を戻すまで (30 秒)
    private static final int RESET_AFTER_HEART = 20 * 30;
    private static final int WAVE_DELAY = 60;
    // 扉が閉じるまでの集合の時間 (5 秒)
    public static final int GATHER_TICKS = 100;
    // 心臓の間から人が消えてから、心臓を眠らせて入口を開けるまでの時間
    private static final int HEART_ABANDON_DELAY = 60;
    // 心臓が撃破の報告なしに消えたとき、次に集合を始めるまでの間 (開け閉めを繰り返さないように)
    private static final int HEART_RETRY_COOLDOWN = 100;

    // ---------------------------------------------------------------- 休憩地点
    private record Checkpoint(String id, Box area, BlockPos spawn) {
    }

    public static final String MAZE_ENTRY = "mz0";
    private static final List<Checkpoint> BASE_CHECKPOINTS = List.of(
            // 鼓動の間の中央は眠る心臓の台座 (強化深層岩) なので、南へずらす
            new Checkpoint("hub", HUB, HUB.center().offset(0, 0, 6)),
            // 休み場の焚き火のまわり (中心から 2 マスの東西南北) には腰掛けがあるので、斜めに置く
            new Checkpoint("w0", W0, W0.center().offset(2, 0, 2)),
            new Checkpoint("w1", W1, W1.center().offset(2, 0, 2)),
            new Checkpoint("w2", W2, W2.center().offset(2, 0, 2)),
            // 前の谷の休み場 (周回の長い裂け目の回廊。版 31。trial/LongWalk)
            // 焚き火と腰掛けを避け、北の戸口 (前の谷) が見える列に立つ
            new Checkpoint("wp", WP0, WP0.center().offset(-3, 0, 3)),
            // 谷の中ほどの島は休み場にしない (版 25: コースの途中の休み場をなくした。落ちると、その谷の入口の休み場 w0・w1・w2 へ戻る)
            // 戦いの部屋の扉から離れた隅に置く (E0 は西の隅、CE1 は南東の隅。どちらも扉から 5 マス以上)。
            // 倒れて目覚めた所の目の前が戦場、にならないように
            new Checkpoint("e0", E0, E0.center().offset(-2, 0, -2)),
            new Checkpoint("ce1", CE1, CE1.center().offset(2, 0, -2)),
            new Checkpoint("heart", HEART_REST, HEART_REST.center().offset(1, 0, 1)),
            // 第二の広間と詰所のあいだの休み場 (焚き火のまわりの腰掛けを避けて斜めに置く)
            new Checkpoint("ckr", CK_PASS, CK_PASS.center().offset(4, 0, 1)),
            // 闇の水路の 2 つめの入口の小部屋
            new Checkpoint("mz1", MAZE_ENTRY_ROOM2, new BlockPos(MAZE_ENTRY_ROOM2.x1(), MAZE_ENTRY_ROOM2.floorY() + 1, MAZE_ENTRY_ROOM2.z2())),
            // 闇の水路の入口: はしごを下りた先の乾いた小部屋。水の外・戦いの外。足元は部屋の床の一段上
            new Checkpoint(MAZE_ENTRY, MAZE_ENTRY_ROOM, new BlockPos(MAZE_ENTRY_ROOM.x1(), MAZE_ENTRY_ROOM.floorY() + 1, MAZE_ENTRY_ROOM.z2())));
    // 試練の階 (WhaleDeck。docs/design/29) の休み場も、階を置いたあと (WhaleDeck.placed) は同じ決まりで数える
    private static final List<Checkpoint> WITH_DECK_CHECKPOINTS = concatCheckpoints(BASE_CHECKPOINTS,
            WhaleDeck.rests().stream().map(r -> new Checkpoint(r.id(), r.area(), r.spawn())).toList());

    private static List<Checkpoint> checkpoints() {
        return WhaleDeck.placed() ? WITH_DECK_CHECKPOINTS : BASE_CHECKPOINTS;
    }
    private static final List<String> WEST_CHECKPOINTS = List.of("w0", "w1", "w2", "wp");

    private static List<Checkpoint> concatCheckpoints(List<Checkpoint> a, List<Checkpoint> b) {
        List<Checkpoint> out = new ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    // 闇の水路の中ほどの休み場 (息継ぎのくぼみの一つに、乾いた足場を作ってある)。迷路は種で形が変わるので、場所は種から出す
    public static final String MAZE_REST = "mz";
    private static long mazeRestSeed = Long.MIN_VALUE;
    private static Checkpoint mazeRest;

    private static Checkpoint mazeRest(ServerLevel level) {
        long seed = TitanBackData.get(level).mazeSeed();
        if (mazeRest == null || mazeRestSeed != seed) {
            BlockPos p = MazeBuilder.midPocket(MazeBuilder.maze(seed));
            // 足場の床 (y = p.y + 1) の上の空気の層。中は 3×3
            mazeRest = new Checkpoint(MAZE_REST, new Box(p.getX(), p.getX() + MAZE_CELL - 1, p.getZ(), p.getZ() + MAZE_CELL - 1, p.getY() + 1, 3),
                    MazeBuilder.restSpot(p));
            mazeRestSeed = seed;
        }
        return mazeRest;
    }

    private static List<Checkpoint> checkpointsOf(ServerLevel level) {
        if (!TitanBackData.get(level).isBuilt()) {
            return checkpoints();
        }
        List<Checkpoint> all = new ArrayList<>(checkpoints());
        all.add(mazeRest(level));
        return all;
    }

    // ---------------------------------------------------------------- 番兵の広間
    // ante は入口の手前の前室 (扉が閉じるとき、ここにいる人も中へ入れる)
    // cuts: 動かせる仕切り (版 18。少人数のグループだけに、広間の中に立つ封印の壁。人数が maxPlayers 以下のとき張り、封じた側 (sealed) には湧かない)
    private record Cut(int maxPlayers, List<BlockPos> wall, java.util.function.Predicate<BlockPos> sealed) {
    }

    private record Hall(String id, Box box, AABB ante, List<BlockPos> entrance, List<BlockPos> exit, List<List<String>> waves,
            List<Cut> cuts) {
        // その人数のとき封じている仕切りの壁の全部
        List<BlockPos> walls(int players) {
            List<BlockPos> out = new ArrayList<>();
            for (Cut c : cuts) {
                if (players <= c.maxPlayers()) {
                    out.addAll(c.wall());
                }
            }
            return out;
        }

        // その人数のとき、湧いてよい場所か (封じた側ではない)
        boolean open(int players, BlockPos p) {
            for (Cut c : cuts) {
                if (players <= c.maxPlayers() && c.sealed().test(p)) {
                    return false;
                }
            }
            return true;
        }

        List<BlockPos> allWalls() {
            List<BlockPos> out = new ArrayList<>();
            for (Cut c : cuts) {
                out.addAll(c.wall());
            }
            return out;
        }
    }

    private static List<BlockPos> concat(List<BlockPos> a, List<BlockPos> b) {
        List<BlockPos> out = new ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    // 第一の広間は、近接の波 (番兵が前に立ち、苔這いが回り込む) → 射手の波 (番兵の盾の後ろから射手 3 体が撃つ。奥の壁ぎわで骨の砲手が
    // 足もとへ骨の塊を落とす) → 老番兵の波 (名づきの小さな中ボス。号令の骨笛で射手を呼ぶ)。
    // 番兵長の広間の第一波で両方が混ざる。(配置の版 2 で第二の広間を除き、その射手の波を第一の広間へ移した)
    // 動かせる仕切りは、使わない (設計書 26 の 8.7 は B: 同じ広間で湧きを入口側へ寄せ、罠と柱で使わせる。RoomPlay)。入れるなら、各広間の NO_CUTS を
    // List.of(new Cut(最大人数, VEIL_E1, 封じる側の判定), ...) に替え、VEIL_* を OPEN_GATES に足す (仕組みは applyVeil・spawnWave・resetHall が持っている)
    private static final List<Cut> NO_CUTS = List.of();

    private static final List<Hall> HALLS = List.of(
            // 第一の広間 (練兵場): 二人以下は南を封じ (z -26 より南)、一人はさらに東 (x 48 より東) を封じる
            new Hall("e1", E1, E0.aabb(), E1_ENTRANCE, E1_EXIT, List.of(
                    List.of("sentinel", "sentinel", "crawler", "crawler"),
                    List.of("archer", "archer", "archer", "sentinel", "mortar")),
                    NO_CUTS),
            // 版 25: 第二の広間 (射手の回廊) は戦わない通路にした。東の翼の戦いは 3 つ (第一の広間・詰所・番兵長の広間)
            // 番兵の詰所: 盾の壁の列 (骨の番兵 4) → 老番兵。倒すと番兵長の広間への戸 (割符の戸) が開く。初回だけ
            new Hall("ck", CK, CK_PASS.aabb(), CK_ENTRANCE, E3_LOCK, List.of(
                    List.of("sentinel", "sentinel", "sentinel", "sentinel"),
                    List.of("elder")), List.of()),
            // 番兵長の広間: 二人以下は南 (共鳴石の側) を封じる。倒すと開く
            new Hall("e3", E3, E3_ANTE, E3_ENTRANCE, List.of(), List.of(
                    List.of("sentinel", "sentinel", "archer", "crawler", "crawler"),
                    List.of("captain")), NO_CUTS));

    // 周回の長い広間 (32 の 4 章、R5): 第一の広間と番兵長の広間に一波ずつ足す (詰所は初回だけなので、周回の戦いは 4 波 → 6 波)
    static final Map<String, List<List<String>>> LONG_WAVES = Map.of(
            "e1", List.of(
                    List.of("sentinel", "sentinel", "crawler", "crawler"),
                    List.of("archer", "archer", "archer", "sentinel", "mortar"),
                    List.of("crawler", "crawler", "sentinel", "archer", "sentinel")),
            "e3", List.of(
                    List.of("sentinel", "sentinel", "archer", "crawler", "crawler"),
                    List.of("archer", "archer", "sentinel", "sentinel", "mortar"),
                    List.of("captain")));

    // 周回で戦う広間 (第一の広間・番兵長の広間) の波の数の合計。extended = 周回の長い並び (自己テスト用)
    public static int replayWaves(boolean extended) {
        int n = 0;
        for (Hall h : HALLS) {
            if (LONG_WAVES.containsKey(h.id())) {
                n += extended ? LONG_WAVES.get(h.id()).size() : h.waves().size();
            }
        }
        return n;
    }

    // その広間の波の並び (周回のグループは LONG_WAVES)
    static List<List<String>> wavesOf(ServerLevel level, UUID group, Hall hall) {
        List<List<String>> longer = LONG_WAVES.get(hall.id());
        return longer != null && jp.revati.skyabyss.trial.RouteLength.extended(level, group) ? longer : hall.waves();
    }

    // ---------------------------------------------------------------- 状態 (グループごと)
    // 試練の進み (制圧した広間・宝物庫の封印・心臓の撃破・鳴らした石・扉の開け閉め) は、グループ (パーティ、または一人) の記録
    // (BackGroupData.Record) にある。ここにあるのは、戦いの最中のメモリの状態 (保存しない) だけで、(グループ, 広間) ごとに持つ。
    // 同じ広間・心臓の間で、グループごとに別々の戦いが同時に動く (敵・ボスはグループの印つきで、ほかのグループには見えない)
    private static final class HallState {
        final UUID group;
        final Hall hall;
        // 0 = 集合中 (扉は開いている)、1 以上 = 戦っている波の番号
        int wave;
        int gather = GATHER_TICKS;
        int delay;
        int players;
        // 広間が空に見えてからの時間 (一瞬の取りこぼしでは戻さない)
        int emptyTicks;
        // 戦いの参加者 (扉が閉じたときに中にいた人。途中で倒れた人も残す)
        final Set<UUID> participants = new LinkedHashSet<>();
        // 戦いの最中に倒れた参加者の再入場 (20 秒の待ち・回数。戦いが終わる・やり直しで消える。報酬の参加者には残る)
        final Reentry reentry = new Reentry();
        // この戦いでパーティが倒れた回数 (倒れた状態 + 倒れずに死んだ回数)。番兵長に勝ったときの札に使う
        int falls;

        // この戦いの波の並び (周回は長い。wavesOf)
        List<List<String>> waves;

        HallState(UUID group, Hall hall) {
            this.group = group;
            this.hall = hall;
            this.waves = hall.waves();
        }
    }

    private static final Map<String, HallState> HALL_STATES = new HashMap<>();
    // 集合の知らせを最後に出した時刻 (出入りを繰り返しても流れ続けないように)
    private static final Map<String, Long> LAST_GATHER_NOTICE = new HashMap<>();
    // 背中で倒れ、背中へ連れ戻している途中の人 (次元の移動で休み場の記録を消さないため)
    private static final Set<UUID> REVIVING = new HashSet<>();
    private static boolean cleanedAfterLoad;
    private static boolean gatesInstalled;
    // 背中に動いている人がいない時間 (世界のリセット: アスレチックと迷路を作り直す)
    private static int emptyWorldTicks;
    private static boolean sharedRenewed;
    // グループごとに、心臓を倒してから封印の先に誰もいない時間
    private static final Map<UUID, Integer> AFTER_HEART_IDLE = new HashMap<>();
    // 心臓の間の戦い (グループごと)
    private static final GroupBossRooms HEART_ROOMS = new GroupBossRooms();

    // 扉: 建てたときに閉じているもの (進みで開く) と、開いているもの (戦いの間だけ閉じる)
    private static final List<List<BlockPos>> CLOSED_GATES = List.of(GATE, E1_EXIT,
            E3_LOCK, BELL_GATE);
    private static final List<List<BlockPos>> OPEN_GATES = List.of(E1_ENTRANCE, E3_ENTRANCE, HEART_GATE, CK_ENTRANCE);

    private TitanBackTrials() {
    }

    // 戸口の位置の一覧 (閉じている戸・開いている戸の全部。壁の穴の検査 (WhaleScaleSelfTest) が使う)
    static List<List<BlockPos>> allDoorLists() {
        List<List<BlockPos>> all = new ArrayList<>(CLOSED_GATES);
        all.addAll(OPEN_GATES);
        return all;
    }

    // 倒れても消えないよう、PlayerPersisted (死んでも引き継がれる領域) に書く
    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static BackGroupData.Record record(ServerLevel level, UUID group) {
        return GroupProgress.of(level, group);
    }

    // 人のいまの試練の番号 (その人のグループの記録の番号)
    private static int epochOf(ServerLevel level, Player player) {
        return player instanceof ServerPlayer sp ? record(level, Groups.keyOf(sp)).epoch() : 1;
    }

    // 扉のブロックを置く (建てたあと・起動のあと・建て直しのあと。何度呼んでもよい)
    static void installGates(ServerLevel level) {
        for (List<BlockPos> gate : CLOSED_GATES) {
            GroupGates.place(level, gate, false);
        }
        for (List<BlockPos> gate : OPEN_GATES) {
            GroupGates.place(level, gate, true);
        }
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        GroupGates.place(level, shrineSeal(), !peaceful);
        // 試練の階の入口・出口の戸と、原型の三つの入口の戸 (docs/design/29)
        jp.revati.skyabyss.trial.TrialRuntime.installGates(level);
    }

    // 世界の共有の見かけ以外の、グループごとの見かけ (鳴らした共鳴石)
    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        GroupOverlays.register((level, group, out) -> {
            if (level.dimension() != TitanTravel.TITAN_BACK) {
                return;
            }
            BackGroupData.Record r = BackGroupData.get(level).peek(group);
            if (r == null) {
                return;
            }
            for (int i = 0; i < GOAL_SPOTS.size(); i++) {
                if (r.flag(STONE_FLAG + i)) {
                    out.accept(GOAL_SPOTS.get(i), jp.revati.skyabyss.trial.TrialGoal.reached());
                }
            }
        });
        GroupEvents.addMembershipHook(player -> {
            if (player.level().dimension() == TitanTravel.TITAN_BACK && player.level() instanceof ServerLevel level) {
                moveOutOfBody(level, player);
                BackGroupData.Record r = record(level, Groups.keyOf(player));
                clearCheckpointIfStale(player, r.epoch());
                persisted(player).putInt(TAG_SEEN_EPOCH, r.epoch());
            }
        });
    }

    // 試練の中 (体内で、鼓動の間とその周り以外) にいるなら、鼓動の間へ移す (記録が変わるので、先の部屋に取り残さない)
    private static void moveOutOfBody(ServerLevel level, ServerPlayer player) {
        if (player.isAlive() && !player.isSpectator() && isBelowSurface(player) && !inSafeArea(player)) {
            BlockPos hub = safeSpot(level, checkpointSpawnRaw("hub"));
            player.fallDistance = 0;
            player.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, player.getYRot(), 0f);
            Notice.narrate(player, "message.skyabyss.rest_point_lost");
        }
    }

    private static void clearCheckpointIfStale(Player player, int epoch) {
        CompoundTag tag = persisted(player);
        if (tag.contains(TAG_CHECKPOINT) && tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
    }

    // ---------------------------------------------------------------- 起動時: 背中がまだなければ建てる
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") != null) {
            return;
        }
        ServerLevel back = event.getServer().getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            TitanBackData data = TitanBackData.get(back);
            if (data.needsBuild()) {
                if (data.isBuilt()) {
                    // 配置の版が変わった古いワールド: 島ごと建て直す。全グループの進みを戻して試練の番号を進めるので、
                    // 古い休み場の記録は無効になり、体内でログアウトしていた人は次のログインで尾の台へ出る
                    SkyAbyss.LOGGER.info("Rebuilding the stone whale's back (layout {} -> {})...", data.layoutVersion(), LAYOUT_VERSION);
                    BackGroupData groups = BackGroupData.get(back);
                    for (UUID id : groups.ids()) {
                        groups.of(id).resetProgress();
                    }
                } else {
                    SkyAbyss.LOGGER.info("Building the stone whale's back for this world...");
                }
                gatesInstalled = false;
                TitanBackBuilder.schedule(back, data);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        HALL_STATES.clear();
        LAST_GATHER_NOTICE.clear();
        REVIVING.clear();
        HEART_ROOMS.clear();
        AFTER_HEART_IDLE.clear();
        cleanedAfterLoad = false;
        gatesInstalled = false;
        emptyWorldTicks = 0;
        sharedRenewed = false;
    }

    // 試練で湧いた番兵と心臓は保存しないが、この仕組みより前に保存されたものが残っていれば、読み込みのときに消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getLevel().dimension() == TitanTravel.TITAN_BACK) {
            Set<String> tags = event.getEntity().getTags();
            if (tags.contains(MOB_TAG) || tags.contains(HEART_TAG)) {
                event.setCanceled(true);
            }
        }
    }

    // 背中に動いている人 (観戦者と、長く動いていない人を除く) がいるグループ
    private static List<UUID> activeGroups(ServerLevel level) {
        List<UUID> keys = new ArrayList<>();
        for (ServerPlayer player : Players.snapshot(level.players())) {
            if (!player.isSpectator() && Activity.active(player, Activity.PRESENT_TICKS)) {
                UUID key = Groups.keyOf(player);
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    // ---------------------------------------------------------------- 毎tick: 広間と心臓の進行、リセット
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK || level.getGameTime() % 10 != 0) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        if (!data.isBuilt()) {
            return;
        }
        if (!gatesInstalled) {
            gatesInstalled = true;
            installGates(level);
        }
        if (!cleanedAfterLoad) {
            // 前回、戦いの途中でサーバーが止まっていたら、その名残を片付ける (戦いの間だけ閉じる入口を既定の開いた状態に戻す)
            cleanedAfterLoad = true;
            BackGroupData groups = BackGroupData.get(level);
            for (UUID id : groups.ids()) {
                for (List<BlockPos> gate : OPEN_GATES) {
                    GroupGates.release(level, id, gate);
                }
            }
        }
        // ピースフル: 祠の階段を封じ、戦いを静かに終わらせ、体内の人を尾の台へ出す
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        tickShrineSeal(level, peaceful);
        // 試練を動かすのは、動いている人のいるグループだけ (観戦者・放置の人がいても、ほかのグループの進行を止めない)
        List<UUID> groups = activeGroups(level);
        if (peaceful) {
            sleepForPeaceful(level, data);
        } else {
            for (UUID group : groups) {
                BackGroupData.Record record = record(level, group);
                for (Hall hall : HALLS) {
                    if (!record.isHallCleared(hall.id())) {
                        tickHall(level, record, group, hall);
                    }
                }
            }
            // 誰もいなくなったグループの戦いは、最初に戻す
            for (HallState state : List.copyOf(HALL_STATES.values())) {
                if (!groups.contains(state.group)) {
                    resetHall(level, state.hall, state.group, false);
                }
            }
            tickHearts(level);
        }
        // 心臓を倒したグループは、封印の先に誰もいなくなって30秒たったら試練が戻る。
        // 島のほかの場所 (尾の台など) で誰かが待っていても、次の周回へ挑み直せる
        BackGroupData all = BackGroupData.get(level);
        for (UUID id : all.ids()) {
            BackGroupData.Record record = all.peek(id);
            if (record == null || !record.isHeartDefeated()) {
                AFTER_HEART_IDLE.remove(id);
                continue;
            }
            boolean anyoneInside = false;
            for (ServerPlayer player : Groups.online(level, id)) {
                if (!player.isSpectator() && isPastHeartSeal(player)) {
                    anyoneInside = true;
                    break;
                }
            }
            int idle = anyoneInside ? 0 : AFTER_HEART_IDLE.getOrDefault(id, 0) + 10;
            AFTER_HEART_IDLE.put(id, idle);
            if (idle >= RESET_AFTER_HEART) {
                resetGroup(level, id);
                for (ServerPlayer player : Groups.online(level, id)) {
                    Notice.narrate(player, "message.skyabyss.trials_renewed");
                }
            }
        }
        // 誰も背中にいなくなって (または動かなくなって) 3 分たったグループは、試練が戻る
        for (UUID id : GroupProgress.idleGroups(level)) {
            resetGroup(level, id);
        }
        // 世界: 動いている人が誰もいなくなって 3 分たったら、アスレチックと迷路 (みんなで共有する形) を新しい種で作り直す
        if (groups.isEmpty()) {
            emptyWorldTicks += 10;
            if (emptyWorldTicks >= RESET_AFTER_IDLE && !sharedRenewed) {
                sharedRenewed = true;
                renewShared(level);
            }
        } else {
            emptyWorldTicks = 0;
            sharedRenewed = false;
        }
    }

    // ---------------------------------------------------------------- ピースフル (試練は眠る)
    // 祠の階段の封印: ピースフルのあいだだけ閉じ (扉のブロックの既定を変える)、ほかの難易度に戻ると開ける (読み込まれているときだけ触る)
    private static void tickShrineSeal(ServerLevel level, boolean peaceful) {
        List<BlockPos> seal = shrineSeal();
        if (!level.isLoaded(seal.get(0))) {
            return;
        }
        BlockState first = level.getBlockState(seal.get(0));
        boolean gate = first.is(ModBlocks.SEALED_GATE.get());
        boolean closed = gate && !first.getValue(SealedGateBlock.OPEN);
        if (peaceful && !closed) {
            // 封印の面に立っている人は先に尾の台へ出す (sleepForPeaceful)。人のいない所だけ閉じる
            if (level.getEntitiesOfClass(Player.class, doorBox(seal)).isEmpty()) {
                GroupGates.setDefault(level, seal, false);
            }
        } else if (!peaceful && (closed || !gate)) {
            GroupGates.setDefault(level, seal, true);
        }
    }

    // 祠の封印より奥の体内にいるか (封印の面を含む)。地表・祠・封印の手前の階段は含まない
    static boolean isPastShrineSeal(Player player) {
        BlockPos p = player.blockPosition();
        return p.getZ() <= SHRINE_SEAL_Z && shape(p.getX(), p.getZ()) > 0
                && p.getY() <= top(p.getX(), p.getZ()) && p.getY() > bottom(p.getX(), p.getZ());
    }

    // 戦いを静かに終わらせる (告知なし): 番兵と心臓を消し、閉じた扉を開け、広間と心臓を最初に戻す。
    // 体内にいる人 (クリエイティブと観戦者を除く) は尾の台へ出し、一度だけ知らせる
    public static void sleepForPeaceful(ServerLevel level, TitanBackData data) {
        AABB island = new AABB(-RX - 4, BASE_Y - 60, -RZ - 4, RX + 4, BASE_Y + 40, RZ + 4);
        for (Entity e : List.copyOf(level.getEntitiesOfClass(Entity.class, island,
                e -> e.getTags().contains(MOB_TAG) || e.getTags().contains(HEART_TAG)))) {
            e.discard();
        }
        for (HallState state : List.copyOf(HALL_STATES.values())) {
            resetHall(level, state.hall, state.group, false);
        }
        HEART_ROOMS.resetAll(level, HEART_HOST, false);
        // 戦いの間だけ閉じる入口は、どのグループにも開け直す
        BackGroupData records = BackGroupData.get(level);
        for (UUID id : records.ids()) {
            for (List<BlockPos> gate : OPEN_GATES) {
                GroupGates.release(level, id, gate);
            }
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator() && isPastShrineSeal(player)) {
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.trials_peaceful");
            }
        }
    }

    // 心臓の回廊 (宝物庫の奥の扉から心臓の間の入口まで)
    // リセットで作り直したり封じたりしない場所 (鼓動の間とその周り)。リセットのとき、ここにいる人は動かさない
    private static final List<Box> SAFE_ON_RESET = List.of(HUB, ENTRY, W0, E0, WEST_HALL, EAST_HALL, WhaleDeck.STAIR, WhaleDeck.D0, WhaleDeck.C1, WhaleDeck.S0,
            WhaleDeck.W02, WhaleDeck.W06, WhaleDeck.W07, WhaleDeck.W10, WhaleDeck.C2, WhaleDeck.S1, WhaleDeck.W03, WhaleDeck.W04, WhaleDeck.W04_STONE_ROOM,
            WhaleDeck.W11, WhaleDeck.W11_STONE_ROOM, WhaleDeck.W12,
            // 台から背の広場へ登る階段 (到着の道。リセットで動かさない)
            new Box(STAIR_X0, STAIR_X0 + 64, STAIR_Z0, STAIR_Z1, FLANK_FLOOR_Y, 64));

    // 封印の先 (宝物庫・心臓の回廊・心臓の前室・心臓の間) にいるか
    static boolean isPastHeartSeal(Player player) {
        BlockPos p = player.blockPosition();
        return HEART_CORRIDOR.contains(p) || HEART_REST.contains(p) || HEART.contains(p);
    }

    // 体内 (島の地表より下) にいるか
    static boolean isBelowSurface(Player player) {
        BlockPos p = player.blockPosition();
        return shape(p.getX(), p.getZ()) > 0 && p.getY() <= top(p.getX(), p.getZ());
    }

    // ---------------------------------------------------------------- 番兵の広間 (グループごと)
    private static String hallKey(UUID group, String hall) {
        return group + "|" + hall;
    }

    private static void tickHall(ServerLevel level, BackGroupData.Record record, UUID group, Hall hall) {
        String key = hallKey(group, hall.id());
        HallState state = HALL_STATES.get(key);
        if (state == null || state.wave == 0) {
            List<ServerPlayer> inside = playersInside(level, hall, group);
            if (state == null) {
                if (!inside.isEmpty()) {
                    // 集合: 扉は開いたまま、5 秒待つ
                    state = new HallState(group, hall);
                    state.waves = wavesOf(level, group, hall);
                    HALL_STATES.put(key, state);
                    announceGathering(level, group, key, hall.box());
                    applyVeil(level, group, hall, groupNear(level, group, hall));
                }
                return;
            }
            applyVeil(level, group, hall, groupNear(level, group, hall));
            if (inside.isEmpty() && playersInHall(level, hall, group).isEmpty()) {
                // 中から人がいなくなったので、集合を取りやめる
                HALL_STATES.remove(key);
                return;
            }
            state.gather -= 10;
            if (state.gather > 0) {
                countdown(level, group, hall.box(), state.gather);
                return;
            }
            // 扉が閉じる: 戸口と前室にいる人も中へ入れ、中にいる人を参加者として数える
            pullIn(level, group, hall.entrance(), hall.box().center(), hall.ante());
            for (ServerPlayer player : playersInHall(level, hall, group)) {
                state.participants.add(player.getUUID());
            }
            if (state.participants.isEmpty()) {
                HALL_STATES.remove(key);
                return;
            }
            state.wave = 1;
            state.players = state.participants.size();
            applyVeil(level, group, hall, state.players);
            GroupGates.seal(level, group, hall.entrance());
            playToGroup(level, group, hall.entrance().get(0), SoundEvents.IRON_DOOR_CLOSE, 2f, 0.5f);
            broadcast(level, group, hall.box(), ModText.battle("message.skyabyss.hall_begin"));
            SkyAbyss.LOGGER.info("Hall {} sealed with {} challenger(s) of group {}", hall.id(), state.players, group);
            spawnWave(level, hall, state);
            return;
        }
        // 戦いが始まったら、壁ぎわや戸口に立っていても「中にいる」と数える。
        // 全員が倒れて (休み場へ戻されて) 3秒たったときだけ、広間を最初に戻す
        List<ServerPlayer> inHall = playersInHall(level, hall, group);
        if (inHall.isEmpty()) {
            state.emptyTicks += 10;
            if (state.emptyTicks >= 60) {
                resetHall(level, hall, group, true);
            }
            return;
        }
        for (ServerPlayer player : inHall) {
            state.participants.add(player.getUUID());
        }
        state.emptyTicks = 0;
        // 倒れていない参加者 (離れて戻ってきた人) は、戦いが続いている間、扉に触れると内側へ戻れる。
        // 倒れた人は、前室で目を覚まして 20 秒たち、回数 (skyabyssHeartReentryLimit) が残っているときだけ
        tellFallen(level, state.reentry, inHall, state.participants);
        letParticipantsBack(level, group, hall.entrance(), hall.box().center(), state.participants, state.reentry);
        clearStrays(level, hall, group);
        if (aliveGuards(level, hall, group) > 0) {
            return;
        }
        if (state.delay == 0) {
            state.delay = WAVE_DELAY;
            if (state.wave < state.waves.size()) {
                broadcast(level, group, hall.box(), ModText.battle("message.skyabyss.arena_next"));
            }
            return;
        }
        state.delay -= 10;
        if (state.delay > 0) {
            return;
        }
        state.delay = 0;
        if (state.wave >= state.waves.size()) {
            // 制圧: 出口と入口の両方が (このグループには) 開く。倒れて控えの間で目を覚ました仲間も、先へ進める
            record.markHallCleared(hall.id());
            HALL_STATES.remove(key);
            // 制圧のあとに、遅れて呼ばれた仲間・取りこぼしを残さない (行く手をふさぐので)
            for (Mob left : level.getEntitiesOfClass(Mob.class, hall.box().aabb().inflate(14, 2, 14),
                    e -> e.getTags().contains(MOB_TAG) && group.equals(Groups.keyOf(e)))) {
                left.discard();
            }
            GroupGates.open(level, group, hall.exit());
            GroupGates.release(level, group, hall.entrance());
            GroupGates.release(level, group, hall.allWalls());
            playToGroup(level, group, hall.box().center(), SoundEvents.BEACON_POWER_SELECT, 2f, 0.8f);
            broadcast(level, group, hall.box(), ModText.progress(hall.exit().isEmpty() ? "message.skyabyss.arena_cleared" : "message.skyabyss.hall_cleared"));
        } else {
            state.wave++;
            spawnWave(level, hall, state);
        }
    }

    // 中にいた全員が倒れたので、そのグループの番兵を眠らせて最初に戻す (入口が開き、休み場からやり直せる)。出口は閉じたまま
    private static void resetHall(ServerLevel level, Hall hall, UUID group, boolean announce) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, hall.box().aabb().inflate(4),
                e -> e.getTags().contains(MOB_TAG) && group.equals(Groups.keyOf(e)))) {
            e.discard();
        }
        HALL_STATES.remove(hallKey(group, hall.id()));
        GroupGates.release(level, group, hall.entrance());
        GroupGates.release(level, group, hall.allWalls());
        if (!record(level, group).isHallCleared(hall.id())) {
            GroupGates.release(level, group, hall.exit());
        }
        if (announce) {
            broadcast(level, group, hall.box(), ModText.deny("message.skyabyss.arena_reset"));
        }
    }

    // 広間の近く (40 マス以内) にいる、そのグループの人の数 (幕を張るかどうかの目安)
    private static int groupNear(ServerLevel level, UUID group, Hall hall) {
        return Math.max(1, Groups.inBox(level, group, hall.box().aabb().inflate(40)).size());
    }

    // 仕切り (少人数だけ、広間の一部を封じる)。人数に合わせて立てる・外す (人数が増えれば外れる)
    private static void applyVeil(ServerLevel level, UUID group, Hall hall, int players) {
        for (Cut cut : hall.cuts()) {
            if (players <= cut.maxPlayers()) {
                GroupGates.seal(level, group, cut.wall());
            } else {
                GroupGates.release(level, group, cut.wall());
            }
        }
    }

    private static int aliveGuards(ServerLevel level, Hall hall, UUID group) {
        return level.getEntitiesOfClass(Mob.class, hall.box().aabb().inflate(3),
                e -> e.isAlive() && e.getTags().contains(MOB_TAG) && group.equals(Groups.keyOf(e))).size();
    }

    // 人数の上限 (パーティは最大 6 人)
    private static final int MAX_SCALE_PLAYERS = jp.revati.skyabyss.party.Parties.MAX_MEMBERS;

    private static void spawnWave(ServerLevel level, Hall hall, HallState state) {
        List<BlockPos> spawns = EastWingBuilder.spawnPoints(hall.box());
        if (!hall.cuts().isEmpty()) {
            // 仕切りが立っているあいだは、封じていない側にだけ湧かせる
            List<BlockPos> near = spawns.stream().filter(p -> hall.open(state.players, p)).toList();
            if (!near.isEmpty()) {
                spawns = near;
            }
        }
        // 設計書 26 の 8.7 の B: 広間の大きさは同じ。一人・二人は、湧き場所を入口側に寄せる (広間いっぱいは 4 人から)
        BlockPos entrance = RoomPlayRooms.entranceOf(hall.id());
        spawns = RoomPlay.spawnNear(spawns, entrance, state.players);
        List<String> wave = new ArrayList<>(state.waves.get(state.wave - 1));
        // 人数が多いほど番兵も増える (1人増えるごとに2体。番兵長の波では骨の番兵が増える。最大 6 人分)。
        // 老番兵の波は骨の番兵が増える (老番兵は体力が増える)。骨の砲手は 4 人から 2 体
        boolean elderWave = wave.get(0).equals("elder");
        for (int extra = 1; extra < Math.min(state.players, MAX_SCALE_PLAYERS); extra++) {
            if (wave.get(0).equals("captain")) {
                wave.add("sentinel");
                wave.add("archer");
            } else if (elderWave) {
                wave.add("sentinel");
                wave.add("sentinel");
            } else {
                wave.add(wave.get(0));
                wave.add(wave.get(1));
            }
        }
        if (wave.contains("mortar") && state.players >= 4) {
            wave.add("mortar");
        }
        int mortars = 0;
        for (int i = 0; i < wave.size(); i++) {
            String kind = wave.get(i);
            BlockPos at;
            if (kind.equals("captain") || kind.equals("elder")) {
                at = bossSpot(level, hall, entrance, state.players);
            } else if (kind.equals("mortar")) {
                // 骨の砲手は奥の壁ぎわ (広間の出口側の二つの角) から撃つ。少人数は入口側の湧き場所から、広間の内へ 2 マス
                int m = mortars++ % 2;
                BlockPos base = spawns.get(m);
                at = base.offset(Integer.signum(hall.box().center().getX() - base.getX()) * 2, 0, 0);
            } else {
                at = spawns.get(i % spawns.size());
            }
            Mob mob = spawnGuard(level, kind, at, state.group);
            if (mob instanceof WhaleboneCaptain || mob instanceof ElderSentinel) {
                // 番兵長・老番兵は心臓と同じ式で、人数に応じて体力が増える (280・220 × (1 + 0.6 × (人数 − 1)))
                var health = mob.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(Math.min(1024, health.getBaseValue() * StoneWhaleHeart.scaleFor(state.players)));
                    mob.setHealth(mob.getMaxHealth());
                }
            }
        }
        broadcast(level, state.group, hall.box(), ModText.battle("message.skyabyss.arena_wave", state.wave, state.waves.size()));
    }

    // 番兵長・老番兵の立つ場所: 4 人からは広間の真ん中。一人・二人は入口から真ん中へ向かう道の途中 (45%・60%・80%)。
    // 体が大きいので、そこが柱や置物にふさがれていたら真ん中へ戻す
    private static BlockPos bossSpot(ServerLevel level, Hall hall, BlockPos entrance, int players) {
        BlockPos c = hall.box().center();
        if (players >= 4 || hall.id().equals("ck")) {
            return c;
        }
        double f = RoomPlay.keepFraction(players);
        int x = (int) Math.round(entrance.getX() + (c.getX() - entrance.getX()) * f);
        int z = (int) Math.round(entrance.getZ() + (c.getZ() - entrance.getZ()) * f);
        x = Math.max(hall.box().x1() + 3, Math.min(hall.box().x2() - 3, x));
        z = Math.max(hall.box().z1() + 3, Math.min(hall.box().z2() - 3, z));
        BlockPos at = new BlockPos(x, c.getY(), z);
        net.minecraft.world.phys.AABB body = new net.minecraft.world.phys.AABB(x - 1.6, c.getY(), z - 1.6, x + 2.6, c.getY() + 4, z + 2.6);
        return level.noCollision(body) ? at : c;
    }

    @Nullable
    public static Mob spawnGuard(ServerLevel level, String kind, BlockPos pos) {
        return spawnGuard(level, kind, pos, null);
    }

    // その位置を含む番兵の広間 (なければ null)
    @Nullable
    private static Hall hallAt(BlockPos pos) {
        for (Hall h : HALLS) {
            if (h.box().aabb().inflate(0, 1, 0).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                return h;
            }
        }
        return null;
    }

    // 広間の中の、spot にいちばん近い立てる床 (壁ぎわ 1 マスは除く)。なければ null
    @Nullable
    private static BlockPos insideSpot(ServerLevel level, Hall hall, BlockPos spot) {
        Box b = hall.box();
        int x = Math.max(b.x1() + 1, Math.min(b.x2() - 1, spot.getX()));
        int z = Math.max(b.z1() + 1, Math.min(b.z2() - 1, spot.getZ()));
        BlockPos p = new BlockPos(x, b.floorY() + 1, z);
        return level.getBlockState(p.below()).isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() ? p : null;
    }

    // 番兵長・老番兵などが「呼ぶ」仲間 (咆哮の番兵・骨笛の射手)。広間の中の戦いだけで呼べる:
    //  - 呼ぶ者が倒れた・消えたあと (予告の円が遅れて実るとき) は呼ばない
    //  - その広間の戦いがもう終わっている (制圧済み・戦いの外) なら呼ばない
    //  - 位置は広間の中だけ。壁・戸の外なら広間の内へ寄せる (寄せられなければ呼ばない)
    // 広間でない所 (試練の階のセル) の呼び出しは、呼ぶ者が生きているときだけ通す
    @Nullable
    public static Mob spawnSummon(ServerLevel level, String kind, BlockPos pos, @Nullable UUID group, Mob owner) {
        if (owner == null || !owner.isAlive() || owner.isRemoved()) {
            return null;
        }
        Hall hall = hallAt(owner.blockPosition());
        if (hall == null) {
            return spawnGuard(level, kind, pos, group);
        }
        if (group != null) {
            HallState state = HALL_STATES.get(hallKey(group, hall.id()));
            if (state == null || state.wave == 0 || record(level, group).isHallCleared(hall.id())) {
                return null;
            }
        }
        BlockPos at = pos;
        if (hallAt(pos) != hall || !level.getBlockState(pos).isAir()) {
            at = insideSpot(level, hall, pos);
            if (at == null) {
                return null;
            }
        }
        return spawnGuard(level, kind, at, group);
    }

    // 戦いのさなかに広間の外 (壁の向こう・戸の先) へ出てしまった (湧いた) そのグループの番兵を片づける。数えに入らず、行く手をふさぐので
    private static void clearStrays(ServerLevel level, Hall hall, UUID group) {
        net.minecraft.world.phys.AABB room = hall.box().aabb().inflate(0.6, 1.5, 0.6);
        for (Mob m : level.getEntitiesOfClass(Mob.class, room.inflate(12, 1.5, 12),
                e -> e.isAlive() && e.getTags().contains(MOB_TAG) && group.equals(Groups.keyOf(e)))) {
            if (!room.contains(m.position()) && hallAt(m.blockPosition()) == null) {
                m.discard();
            }
        }
    }

    // group: そのグループの敵として湧かせる (ほかのグループには見えず、狙われず、傷を受けない)。null なら所属なし
    @Nullable
    public static Mob spawnGuard(ServerLevel level, String kind, BlockPos pos, @Nullable UUID group) {
        EntityType<? extends Mob> type = switch (kind) {
            case "archer" -> ModEntities.RIB_ARCHER.get();
            case "crawler" -> ModEntities.MOSS_CRAWLER.get();
            case "captain" -> ModEntities.WHALEBONE_CAPTAIN.get();
            case "mortar" -> Ch1BackEntities.BONE_MORTAR.get();
            case "gull" -> Ch1BackEntities.BONE_GULL.get();
            case "elder" -> Ch1BackEntities.ELDER_SENTINEL.get();
            default -> ModEntities.BONE_SENTINEL.get();
        };
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        // この印があると、ディスクに保存されない (GuardMob.shouldBeSaved)
        mob.addTag(MOB_TAG);
        if (group != null && mob instanceof GroupOwned owned) {
            owned.setGroupId(group);
        }
        level.addFreshEntity(mob);
        sendParticlesTo(level, group, ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.4, 0.8, 0.4, 0.02);
        if (group == null) {
            level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
        } else {
            playToGroup(level, group, pos, SoundEvents.SOUL_ESCAPE.value(), 1.5f, 0.6f);
        }
        return mob;
    }

    // 粒を、グループの人にだけ送る (group が null なら近くの全員)
    private static void sendParticlesTo(ServerLevel level, @Nullable UUID group, net.minecraft.core.particles.ParticleOptions particle,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        // 区画の写しのセルの決まりの中 (TrialFrame) では、写しの座標に出す
        net.minecraft.world.phys.Vec3 at = jp.revati.skyabyss.trial.TrialFrame.current().world(new net.minecraft.world.phys.Vec3(x, y, z));
        if (group == null) {
            level.sendParticles(particle, at.x, at.y, at.z, count, dx, dy, dz, speed);
            return;
        }
        for (ServerPlayer player : Groups.online(level, group)) {
            if (player.distanceToSqr(x, y, z) < 64 * 64) {
                level.sendParticles(player, particle, false, at.x, at.y, at.z, count, dx, dy, dz, speed);
            }
        }
    }

    // 音を、グループの人にだけ送る
    private static void playToGroup(ServerLevel level, UUID group, BlockPos pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Groups.sound(level, group, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // 封印された広間の中にいる、そのグループの人 (壁ぎわも含む)
    private static List<ServerPlayer> playersInHall(ServerLevel level, Hall hall, UUID group) {
        return Groups.inBox(level, group, hall.box().aabb().inflate(0, 1, 0));
    }

    private static List<ServerPlayer> playersInside(ServerLevel level, Hall hall, UUID group) {
        // 戸口に立っているだけでは始まらないよう、内側に1マス入ったところから数える
        return Groups.inBox(level, group, hall.box().aabb().deflate(1, 0, 1));
    }

    // 番兵長が倒れたら、そのグループの E3 の参加者に中ボスの報酬を渡す (TitanLoot)
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onCaptainDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WhaleboneCaptain captain) || event.isCanceled()
                || !(captain.level() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK
                || !captain.getTags().contains(MOB_TAG)) {
            return;
        }
        UUID group = captain.groupId();
        Set<UUID> ids = new LinkedHashSet<>();
        int falls = 0;
        if (group != null) {
            HallState state = HALL_STATES.get(hallKey(group, "e3"));
            if (state != null) {
                ids.addAll(state.participants);
                falls = state.falls;
            }
            for (ServerPlayer player : Groups.inBox(level, group, E3.aabb().inflate(0, 1, 0))) {
                ids.add(player.getUUID());
            }
        } else {
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, E3.aabb().inflate(0, 1, 0), p -> !p.isSpectator())) {
                ids.add(player.getUUID());
            }
        }
        TitanLoot.onCaptainDefeat(level, onlinePlayers(level, ids), ids);
        // 倒れた回数の札 (中ボス。見た目だけ)
        jp.revati.skyabyss.titan.group.FightBadges.onVictory(level, captain, ids, falls);
    }

    // UUID の一覧から、いまサーバーにいる人を集める (倒れて復活を待っている人も含む)
    private static List<ServerPlayer> onlinePlayers(ServerLevel level, Set<UUID> ids) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    // ---------------------------------------------------------------- 集合と扉
    private static void announceGathering(ServerLevel level, UUID group, String key, Box area) {
        long now = level.getGameTime();
        Long last = LAST_GATHER_NOTICE.get(key);
        if (last == null || now - last > 400) {
            LAST_GATHER_NOTICE.put(key, now);
            broadcast(level, group, area, ModText.battle("message.skyabyss.gate_gathering", GATHER_TICKS / 20));
        }
        playToGroup(level, group, area.center(), SoundEvents.BELL_RESONATE, 1.5f, 0.6f);
        jp.revati.skyabyss.readiness.ReadinessSummary.show(level, Groups.inBox(level, group, area.aabb().inflate(12)), GATHER_TICKS / 20, true);
    }

    // 扉が閉じるまでの残りを、近くのそのグループの人の画面に出す (1 秒ごと)
    private static void countdown(ServerLevel level, UUID group, Box area, int ticksLeft) {
        if (ticksLeft % 20 != 0) {
            return;
        }
        Component text = ModText.battle("message.skyabyss.gate_countdown", ticksLeft / 20);
        List<ServerPlayer> near = Groups.inBox(level, group, area.aabb().inflate(12));
        for (ServerPlayer player : near) {
            Notice.bar(player, text);
        }
        // 準備確認 (A2): 扉までの秒は一行に入る (後から出すので、上の秒の表示を置き換える)
        jp.revati.skyabyss.readiness.ReadinessSummary.show(level, near, ticksLeft / 20, false);
    }

    // 扉の範囲 (封印のブロックがある所)
    private static AABB doorBox(List<BlockPos> door) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : door) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    // 扉の内側 2 マスの位置へ移す (封印のブロックに埋まらず、部屋の中に立てる)
    private static void moveInside(ServerLevel level, ServerPlayer player, AABB door, BlockPos roomCenter) {
        boolean alongX = door.getZsize() <= 1.01;
        double cx = door.getCenter().x;
        double cz = door.getCenter().z;
        double x;
        double z;
        if (alongX) {
            x = Math.max(door.minX + 0.5, Math.min(door.maxX - 0.5, player.getX()));
            z = cz + Math.signum(roomCenter.getZ() + 0.5 - cz) * 2;
        } else {
            x = cx + Math.signum(roomCenter.getX() + 0.5 - cx) * 2;
            z = Math.max(door.minZ + 0.5, Math.min(door.maxZ - 0.5, player.getZ()));
        }
        player.fallDistance = 0;
        player.teleportTo(level, x, door.minY, z, player.getYRot(), player.getXRot());
    }

    // 扉より外側 (部屋と反対の側) か、扉の上にいるか
    private static boolean outsideOrInDoor(ServerPlayer player, AABB door, BlockPos roomCenter) {
        boolean alongX = door.getZsize() <= 1.01;
        double c = alongX ? door.getCenter().z : door.getCenter().x;
        double room = (alongX ? roomCenter.getZ() : roomCenter.getX()) + 0.5;
        double self = alongX ? player.getZ() : player.getX();
        return (self - c) * Math.signum(room - c) < 0.8;
    }

    // 扉が閉じるとき: 戸口と前室 (ante) にいる、そのグループの人を中へ入れる
    private static void pullIn(ServerLevel level, UUID group, List<BlockPos> door, BlockPos roomCenter, AABB ante) {
        if (door.isEmpty()) {
            return;
        }
        AABB box = doorBox(door);
        Set<ServerPlayer> movers = new LinkedHashSet<>();
        movers.addAll(level.getEntitiesOfClass(ServerPlayer.class, box.inflate(2, 1, 2),
                p -> p.isAlive() && !p.isSpectator() && Groups.isMember(p, group) && outsideOrInDoor(p, box, roomCenter)));
        movers.addAll(level.getEntitiesOfClass(ServerPlayer.class, ante.inflate(0, 1, 0),
                p -> p.isAlive() && !p.isSpectator() && Groups.isMember(p, group)));
        for (ServerPlayer player : movers) {
            moveInside(level, player, box, roomCenter);
        }
    }

    // 戦いの途中: 扉に触れた参加者だけを内側へ通す (倒れて前室で目を覚ました人が戻れるように)
    // 倒れた人が、いま戻ってよいか (reentry が null なら倒れた記録なし = 戻ってよい)。自己テスト用に切り出し
    static boolean mayRejoin(UUID player, Set<UUID> participants, @Nullable Reentry reentry, long now, int limit) {
        return participants.contains(player) && (reentry == null || reentry.mayEnter(player, now, limit));
    }

    private static void letParticipantsBack(ServerLevel level, UUID group, List<BlockPos> door, BlockPos roomCenter, Set<UUID> participants,
            @Nullable Reentry reentry) {
        if (door.isEmpty() || participants.isEmpty()) {
            return;
        }
        AABB box = doorBox(door);
        long now = level.getGameTime();
        int limit = ReentryRules.limit(level.getServer());
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box.inflate(1.2, 0, 1.2),
                p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()) && Groups.isMember(p, group)
                        && outsideOrInDoor(p, box, roomCenter))) {
            if (!mayRejoin(player.getUUID(), participants, reentry, now, limit)) {
                continue;
            }
            moveInside(level, player, box, roomCenter);
            if (reentry != null && reentry.entered(player.getUUID())) {
                ReentryRules.guard(player);
            } else {
                Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
            }
        }
    }

    // 外にいる倒れた人へ、待ち時間・戻れる・戻れない、を 1 秒ごとに画面の下へ。目を覚ました時刻もここで取る
    private static void tellFallen(ServerLevel level, Reentry reentry, List<ServerPlayer> inHall, Set<UUID> participants) {
        long now = level.getGameTime();
        int limit = ReentryRules.limit(level.getServer());
        boolean notice = now % 20 < 10;
        for (UUID id : reentry.fallenIds()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive() || p.level() != level || inHall.contains(p)) {
                continue;
            }
            reentry.observeAwake(id, now);
            if (notice) {
                ReentryRules.tell(p, reentry.status(id, now, limit), reentry.remaining(id, now), reentry.used(id), limit);
            }
        }
    }

    // 参加者が倒れた状態になった (party.Downed から)。死んではいないので再入場の記録は付けず、回数だけ数える
    public static void noteDown(UUID player) {
        for (HallState state : HALL_STATES.values()) {
            if (state.wave > 0 && state.participants.contains(player)) {
                state.falls++;
            }
        }
    }

    // 戦いの最中に倒れた参加者を覚える (20 秒の待ちの起点)。LivingDeathEvent から
    static void noteFallen(ServerPlayer player) {
        // 倒れた状態から死んだ人は、倒れたときに数えている
        boolean wasDown = jp.revati.skyabyss.party.Downed.takeDownDeath(player.getUUID());
        for (HallState state : HALL_STATES.values()) {
            if (state.wave > 0 && state.participants.contains(player.getUUID())) {
                state.reentry.fell(player.getUUID());
                if (!wasDown) {
                    state.falls++;
                }
            }
        }
    }

    // 自己テスト用: そのグループの広間の戦いを始めた状態にする / 終える (やり直しと同じ後始末)
    static void testBeginFight(UUID group, String hallId, Set<UUID> participants) {
        for (Hall hall : HALLS) {
            if (hall.id().equals(hallId)) {
                HallState state = new HallState(group, hall);
                state.wave = 1;
                state.participants.addAll(participants);
                HALL_STATES.put(hallKey(group, hallId), state);
            }
        }
    }

    static void testEndFight(ServerLevel level, UUID group, String hallId) {
        for (Hall hall : HALLS) {
            if (hall.id().equals(hallId)) {
                resetHall(level, hall, group, false);
            }
        }
    }

    // その広間の戦いの再入場の記録 (戦っていなければ null。自己テスト用)
    @Nullable
    static Reentry reentryOf(UUID group, String hall) {
        HallState state = HALL_STATES.get(hallKey(group, hall));
        return state == null ? null : state.reentry;
    }

    static boolean isFallen(UUID group, String hall, UUID player) {
        Reentry r = reentryOf(group, hall);
        return r != null && r.isFallen(player);
    }

    // ---------------------------------------------------------------- 心臓の間 (最終ボス・グループごと)
    // グループの人が内側に入ると 5 秒の集合ののち、そのグループの入口が閉じ、そのグループの心臓が現れる。
    // 全員が倒れるか島から消えて数秒たつと、そのグループの心臓を眠らせて入口を開け直す。
    // 心臓の間の物理 (床・壁) は戦いで変わらないので、グループごとに同時に戦える (ほかのグループには見えない)
    private static void tickHearts(ServerLevel level) {
        HEART_ROOMS.tick(level, HEART_HOST);
        // 心臓を倒したグループには、光の柱が見える
        BackGroupData groups = BackGroupData.get(level);
        for (UUID id : groups.ids()) {
            BackGroupData.Record r = groups.peek(id);
            if (r != null && r.isHeartDefeated()) {
                tickHeartPillar(level, id);
            }
        }
    }

    // 心臓の間の前室: 心臓の前室 (休み場) から入口までの回廊
    private static final AABB HEART_ANTE = new AABB(-7, FLOOR + 1, HEART_GATE_Z + 1, 8, FLOOR + 8, HEART_REST.z2() + 1);

    private static final GroupBossRoom.Host HEART_HOST = new GroupBossRoom.Host() {
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return playersInHeart(level);
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
            GroupGates.seal(level, group, HEART_GATE);
            playToGroup(level, group, HEART.center(), SoundEvents.IRON_DOOR_CLOSE, 2f, 0.5f);
        }

        @Override
        public void open(ServerLevel level, UUID group) {
            GroupGates.release(level, group, HEART_GATE);
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
            // 戸口と前室 (心臓の前室から入口までの回廊) にいる人も中へ入れる
            TitanBackTrials.pullIn(level, group, HEART_GATE, HEART.center(), HEART_ANTE);
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
            letParticipantsBack(level, group, HEART_GATE, HEART.center(), participants, null);
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            if (challengers.isEmpty()) {
                return null;
            }
            StoneWhaleHeart heart = ModEntities.STONE_WHALE_HEART.get().create(level);
            if (heart == null) {
                return null;
            }
            heart.moveTo(HEART_CX + 0.5, FLOOR + 1, HEART_CZ + 0.5, 0f, 0f);
            heart.addTag(HEART_TAG);
            heart.setGroupId(group);
            heart.bindArena(heartArena(), challengers.size());
            level.addFreshEntity(heart);
            TitanBackTrials.broadcast(level, group, HEART, ModText.battle("message.skyabyss.heart_begin"));
            SkyAbyss.LOGGER.info("The heart of the stone whale woke with {} challenger(s) of group {}", challengers.size(), group);
            return heart;
        }

        @Override
        public Vec3 home() {
            return new Vec3(HEART_CX + 0.5, FLOOR + 1, HEART_CZ + 0.5);
        }

        @Override
        public double leash() {
            return 30;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
            TitanBackTrials.broadcast(level, group, HEART, message);
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
            TitanBackTrials.countdown(level, group, HEART, secondsLeft * 20);
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
            // 心臓の間に残った、そのグループの心臓の印つきの個体を片付ける
            for (Entity e : level.getEntitiesOfClass(Entity.class, HEART.aabb().inflate(6),
                    e -> e.getTags().contains(HEART_TAG) && group.equals(Groups.keyOf(e)))) {
                e.discard();
            }
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return level.getDifficulty() == Difficulty.PEACEFUL;
        }

        @Override
        public boolean defeated(ServerLevel level, UUID group) {
            var record = jp.revati.skyabyss.titan.group.BackGroupData.get(level).peek(group);
            return record != null && record.isHeartDefeated();
        }
    };

    // 心臓を今すぐ始める (自己テスト・コマンド用)。そのグループの、部屋にいる人を挑む人にする
    public static boolean startHeartNow(ServerLevel level, UUID group) {
        GroupBossRoom room = HEART_ROOMS.of(group);
        if (room.active()) {
            return false;
        }
        return room.start(level, HEART_HOST, Groups.inBox(level, group, heartArena()));
    }

    // 心臓の間の部屋の仕組み (自己テストが、倒したあとに湧き直さないことを確かめる)
    public static GroupBossRoom.Host heartHost() {
        return HEART_HOST;
    }

    public static GroupBossRooms heartRooms() {
        return HEART_ROOMS;
    }

    // 岩鯨の心臓を倒したときに、心臓そのものが呼ぶ。そのグループの参加者に報酬を渡し、入口と光の柱を開く
    public static void onHeartDefeated(ServerLevel level, StoneWhaleHeart heart) {
        GroupBossRoom room = HEART_ROOMS.byBoss(heart.getUUID());
        // いま戦っている心臓でなければ (古い個体など) 何もしない
        if (room == null) {
            return;
        }
        UUID group = room.group;
        BackGroupData.Record record = record(level, group);
        Set<UUID> ids = room.finish(level, HEART_HOST, heart.getUUID());
        if (ids == null) {
            return;
        }
        if (record.isHeartDefeated()) {
            // 同じ周でもう一度倒した: 報酬は出さないが、岩鯨は眠り、戦った人は運び出される (さもないと岩鯨が去らない)
            jp.revati.skyabyss.titan.end.TitanEnd.onHeartStoppedAgain(level, jp.revati.skyabyss.progress.Titans.STONE_WHALE, ids);
            return;
        }
        record.markHeartDefeated();
        AFTER_HEART_IDLE.put(group, 0);
        // 入口は開けたまま。奥の扉から来た道を戻れる
        List<ServerPlayer> participants = onlinePlayers(level, ids);
        TitanLoot.onHeartDefeat(level, participants, ids);
        for (ServerPlayer player : Groups.online(level, group)) {
            Notice.unlock(player, "message.skyabyss.heart_defeated");
        }
        BlockPos c = HEART.center();
        playToGroup(level, group, c, SoundEvents.WITHER_DEATH, 2.5f, 0.6f);
        playToGroup(level, group, c, SoundEvents.BEACON_ACTIVATE, 3f, 0.7f);
        SkyAbyss.LOGGER.info("The heart of the stone whale was defeated by {} participant(s) of group {}", participants.size(), group);
        // 借りものの鼓動が止まり、岩鯨は眠りに落ちる。戦った人は少しあとに地上へ降ろされる (docs/design/02 の 2.11節)
        jp.revati.skyabyss.titan.end.TitanEnd.onHeartStopped(level, jp.revati.skyabyss.progress.Titans.STONE_WHALE, ids);
    }

    // 心臓を倒したあとの光の柱 (そのグループにだけ見える)。中央に立つと尾の台へ戻される
    private static void tickHeartPillar(ServerLevel level, UUID group) {
        double px = HEART_CX + 0.5;
        double pz = HEART_CZ + 0.5;
        sendParticlesTo(level, group, ParticleTypes.END_ROD, px, FLOOR + 1 + (level.getGameTime() % 12), pz, 3, 0.25, 0.2, 0.25, 0.02);
        for (ServerPlayer player : Groups.online(level, group)) {
            if (player.isAlive() && Math.abs(player.getX() - px) < 1.2 && Math.abs(player.getZ() - pz) < 1.2
                    && player.getY() > FLOOR && player.getY() < FLOOR + 12) {
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.heart_pillar");
            }
        }
    }

    private static void toArrival(ServerPlayer player) {
        BlockPos a = plaza();
        player.fallDistance = 0;
        player.teleportTo(player.serverLevel(), a.getX() + 0.5, a.getY(), a.getZ() + 0.5, player.getYRot(), 0f);
    }

    // 心臓の間の内側。入口 (HEART_GATE, z=-64) より奥だけ。心臓の技と狙いもこの中に限る
    public static AABB heartArena() {
        return new AABB(HEART.x1() + 1, FLOOR + 1, HEART.z1() + 1, HEART.x2(), HEART.ceilingY(), HEART_GATE_Z - 1);
    }

    private static List<ServerPlayer> playersInHeart(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, heartArena(), p -> p.isAlive() && !p.isSpectator());
    }

    // そのグループの人の近く (範囲 area の周り) へ知らせる
    private static void broadcast(ServerLevel level, UUID group, Box area, Component message) {
        for (ServerPlayer player : Groups.online(level, group)) {
            if (area.aabb().inflate(20).contains(player.position())) {
                Notice.send(player, message);
            }
        }
    }

    // ---------------------------------------------------------------- リセット (グループごと)
    // そのグループの試練を最初に戻す: 戦いと敵を片付け、進みと扉を消し、試練の番号を進める。
    // ほかのグループの進み・敵・戦いには触れない。体内の、作り直す所・封じる所にいるそのグループの人は鼓動の間へ出す
    public static void resetGroup(ServerLevel level, UUID group) {
        // そのグループの試練が元に戻る = 鼓動が戻る。眠っている巨神は、最短の長さを過ぎていれば目覚める (titan/end)
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.STONE_WHALE);
        for (Hall hall : HALLS) {
            resetHall(level, hall, group, false);
        }
        GroupBossRoom room = HEART_ROOMS.peek(group);
        if (room != null) {
            room.reset(level, HEART_HOST, false);
        }
        // そのグループの敵が残っていれば消す
        AABB island = new AABB(-RX - 4, BASE_Y - 60, -RZ - 4, RX + 4, BASE_Y + 40, RZ + 4);
        for (Entity e : List.copyOf(level.getEntitiesOfClass(Entity.class, island,
                e -> (e.getTags().contains(MOB_TAG) || e.getTags().contains(HEART_TAG)) && group.equals(Groups.keyOf(e))))) {
            e.discard();
        }
        // 進み具合を消し、試練の番号を進める (古い休み場の記録は、これで無効になる)
        GroupProgress.reset(level, group);
        AFTER_HEART_IDLE.remove(group);
        // 試練の階のセルを止め、引いた試練の戸を開け直す (戸の上書きはいま消えた。引きは keep.* なので残る)
        jp.revati.skyabyss.trial.TrialRuntime.onGroupReset(level, group);
        int epoch = record(level, group).epoch();
        BlockPos hub = safeSpot(level, checkpointSpawnRaw("hub"));
        for (ServerPlayer player : Groups.online(level, group)) {
            CompoundTag tag = persisted(player);
            tag.remove(TAG_CHECKPOINT);
            tag.remove(TAG_CHECKPOINT_EPOCH);
            tag.putInt(TAG_SEEN_EPOCH, epoch);
            if (player.isAlive() && !player.isSpectator() && isBelowSurface(player) && !inSafeArea(player)) {
                player.fallDistance = 0;
                player.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, player.getYRot(), 0f);
                Notice.narrate(player, "message.skyabyss.rest_point_lost");
            }
        }
        SkyAbyss.LOGGER.info("The stone whale's trials have been reset for group {} (epoch {})", group, epoch);
    }

    // 世界のリセット: 動いている人が誰もいなくなって久しいとき。みんなで共有するアスレチックと迷路を、新しい種で作り直す
    public static void renewShared(ServerLevel level) {
        TitanBackData data = TitanBackData.get(level);
        BuildKit kit = new BuildKit(level, RandomSource.create(level.getRandom().nextLong()));
        WestWingBuilder.clearCourses(kit, ParkourCourses.forSeed(data.courseSeed()));
        // 灯の台と戸の場所は迷路の種で決まる: 古い種のうちに、全グループの灯の記録と戸の上書きを消す (23)
        AnemoneLights.clearGroups(level, data.mazeSeed());
        data.reseed(level.getRandom());
        WestWingBuilder.placeCourses(kit, ParkourCourses.forSeed(data.courseSeed()));
        MazeBuilder.buildMaze(new BuildKit(level, RandomSource.create(data.mazeSeed())), data.mazeSeed());
        // 進みのない古いグループの記録を捨てる
        SkyAbyss.LOGGER.info("The stone whale's shared courses were renewed");
    }

    // 背中を建て直すとき: 全グループの進みを戻して試練の番号を進める (古い休み場の記録を無効にする)。扉のブロックは建て直しのあとに置き直す
    public static void resetAllGroups(ServerLevel level) {
        // 野営の道具は、建て直しで消える前に持ち主へ戻す
        CampBlocks.returnAllIn(level.getServer(), level.dimension());
        BackGroupData groups = BackGroupData.get(level);
        for (UUID id : groups.ids()) {
            GroupProgress.reset(level, id);
        }
        HEART_ROOMS.clear();
        HALL_STATES.clear();
        gatesInstalled = false;
        jp.revati.skyabyss.trial.TrialRuntime.clearAll();
    }

    // すべてのグループを戻して共有の形も作り直す (コマンド・自己テスト・昇降の戻し)
    public static void resetTrials(ServerLevel level) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id);
        }
        renewShared(level);
        gatesInstalled = false;
    }

    public static boolean isHallCleared(ServerLevel level, UUID group, String id) {
        return record(level, group).isHallCleared(id);
    }

    // 共鳴石 (index) の記録の名前
    public static final String STONE_FLAG = "stone";

    // そのグループにだけ開ける・閉める (旧: ワールド全体の setBlock)
    static void open(ServerLevel level, UUID group, List<BlockPos> blocks) {
        GroupGates.open(level, group, blocks);
    }

    static void seal(ServerLevel level, UUID group, List<BlockPos> blocks) {
        GroupGates.seal(level, group, blocks);
    }

    // ---------------------------------------------------------------- 休憩地点・落下
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        BlockPos pos = player.blockPosition();
        // 谷に落ちたら、直前の休み場へ戻す (闇の水路は谷の真下を通るので、谷の底より上だけを見る)
        if (player.getY() < FALL_LINE && player.getY() > FALL_LINE - 6) {
            for (Box pit : PITS) {
                if (pos.getX() >= pit.x1() - 1 && pos.getX() <= pit.x2() + 1 && pos.getZ() >= pit.z1() - 1 && pos.getZ() <= pit.z2() + 1) {
                    String cp = validCheckpoint(persisted(player), epochOf(player.serverLevel(), player));
                    returnTo(player, WEST_CHECKPOINTS.contains(cp) ? cp : "w0");
                    player.hurt(player.damageSources().fall(), 3f);
                    Notice.bar(player, ModText.deny("message.skyabyss.fell_pit"));
                    return;
                }
            }
        }
        if (player.tickCount % 5 != 0) {
            return;
        }
        // ピースフルで祠の封印の前まで下りてきた人に、理由を画面に出す (チャットには出さない)
        if (player.tickCount % 40 == 0 && player.level().getDifficulty() == Difficulty.PEACEFUL
                && Math.abs(player.getX()) < 3 && pos.getZ() > SHRINE_SEAL_Z && pos.getZ() <= SHRINE_SEAL_Z + 4 && pos.getY() < shrineY()) {
            Notice.bar(player, ModText.lore("message.skyabyss.trials_peaceful"));
        }
        TrialGuide.tick(player);
        int epoch = epochOf(player.serverLevel(), player);
        CompoundTag tag = persisted(player);
        // いまの試練の番号を見た、という印 (ログインしたときの検査に使う)
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        for (Checkpoint cp : checkpointsOf(player.serverLevel())) {
            if (cp.area().contains(pos) && pos.getY() > cp.area().floorY() && pos.getY() < cp.area().ceilingY()) {
                if (!cp.id().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, cp.id());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    if (!cp.id().equals("hub")) {
                        Notice.bar(player, ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss." + cp.id())));
                        player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
                    }
                }
                return;
            }
        }
    }

    // 記録された休み場。試練の番号が違う (リセットより前の) 記録や、知らない名前なら "" (= 尾の台)
    public static String validCheckpoint(CompoundTag tag, int epoch) {
        String id = tag.getString(TAG_CHECKPOINT);
        if (id.isEmpty() || tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            return "";
        }
        if (MAZE_REST.equals(id)) {
            return id;
        }
        for (Checkpoint cp : checkpoints()) {
            if (cp.id().equals(id)) {
                return id;
            }
        }
        return "";
    }

    // 休み場を記録する (自己テスト用にも公開)
    public static void writeCheckpoint(CompoundTag tag, String id, int epoch) {
        tag.putString(TAG_CHECKPOINT, id);
        tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
    }

    private static void clearCheckpoint(Player player) {
        CompoundTag tag = persisted(player);
        tag.remove(TAG_CHECKPOINT);
        tag.remove(TAG_CHECKPOINT_EPOCH);
    }

    // 決まった場所の休み場の復活位置 (闇の水路の中ほどは含まない)
    public static Map<String, BlockPos> checkpointSpawns() {
        Map<String, BlockPos> map = new HashMap<>();
        for (Checkpoint cp : checkpoints()) {
            map.put(cp.id(), cp.spawn());
        }
        return map;
    }

    // 休み場ごとの復活位置 (闇の水路の中ほどを含む。自己テスト用)
    public static Map<String, BlockPos> checkpointSpawns(ServerLevel level) {
        Map<String, BlockPos> map = new HashMap<>();
        for (Checkpoint cp : checkpointsOf(level)) {
            map.put(cp.id(), cp.spawn());
        }
        return map;
    }

    private static boolean inSafeArea(Player player) {
        BlockPos p = player.blockPosition();
        for (Box b : SAFE_ON_RESET) {
            if (b.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos checkpointSpawnRaw(String id) {
        for (Checkpoint cp : checkpoints()) {
            if (cp.id().equals(id)) {
                return cp.spawn();
            }
        }
        return plaza();
    }

    private static BlockPos checkpointSpawn(ServerLevel level, String id) {
        for (Checkpoint cp : checkpointsOf(level)) {
            if (cp.id().equals(id)) {
                return safeSpot(level, cp.spawn());
            }
        }
        return plaza();
    }

    // 休み場の立つ位置 (順路の運び先。trial/TrialRoute)。知らない id なら広場
    public static BlockPos restSpawn(ServerLevel level, String id) {
        return checkpointSpawn(level, id);
    }

    // 立てる場所か: 足元と頭が空いていて (当たり判定も液体もない)、床が固い
    public static boolean isSafeStand(ServerLevel level, BlockPos feet) {
        BlockState f = level.getBlockState(feet);
        BlockState h = level.getBlockState(feet.above());
        BlockPos below = feet.below();
        return f.getCollisionShape(level, feet).isEmpty() && f.getFluidState().isEmpty()
                && h.getCollisionShape(level, feet.above()).isEmpty() && h.getFluidState().isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // 望んだ位置が立てない場所なら、近く (横 5 マス、上下 1 マス) で立てる場所を探す
    public static BlockPos safeSpot(ServerLevel level, BlockPos want) {
        if (isSafeStand(level, want)) {
            return want;
        }
        for (int r = 1; r <= 5; r++) {
            for (int dy : new int[] {0, 1, -1}) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos p = want.offset(dx, dy, dz);
                        if (isSafeStand(level, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return want;
    }

    // 人がいま記録している休み場 (なければ "")。試練の階のセルが、落ちた人を戻すのに使う
    public static String checkpointOf(ServerPlayer player) {
        return validCheckpoint(persisted(player), epochOf(player.serverLevel(), player));
    }

    public static void returnToCheckpoint(ServerPlayer player, String id) {
        returnTo(player, id);
    }

    private static void returnTo(ServerPlayer player, String id) {
        BlockPos spawn = checkpointSpawn(player.serverLevel(), id);
        player.fallDistance = 0;
        player.teleportTo(player.serverLevel(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
    }

    // 乗り込み・地上へ戻る (次元の移動) では、休み場の記録を消す。背中で倒れて連れ戻すときだけは残す
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == TitanTravel.TITAN_BACK || event.getTo() == TitanTravel.TITAN_BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(TitanTravel.TITAN_BACK);
            if (event.getTo() == TitanTravel.TITAN_BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, epochOf(back, player));
            }
        }
    }

    // ログインしたとき: 背中にいて、最後に見た試練の番号が古く (その間にリセットされた)、体内にいるなら尾の台へ出す。
    // 預かり物が残っていれば返す
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag tag = persisted(player);
        if (player.level().dimension() == TitanTravel.TITAN_BACK && player.level() instanceof ServerLevel back) {
            int epoch = epochOf(back, player);
            if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
                clearCheckpoint(player);
                if (player.isAlive() && isBelowSurface(player)) {
                    toArrival(player);
                    Notice.narrate(player, "message.skyabyss.rest_point_lost");
                }
            }
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        if (player.isAlive()) {
            if (tag.contains(TAG_KEPT)) {
                // 生きているのに預かり物が残っている (ふつうは起きない)。失わないよう、持ち物に足して返す
                CompoundTag kept = tag.getCompound(TAG_KEPT);
                tag.remove(TAG_KEPT);
                addExtra(tag, kept.getList("Inventory", Tag.TAG_COMPOUND), kept.getInt("Total"));
            }
            deliverExtra(player);
        }
    }

    // ---------------------------------------------------------------- 倒れたとき: 持ち物を預かり、最後の休み場で目を覚ます
    // ほかの仕組みが死を取り消したときに預からないよう、最後に (LOWEST) 動く
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TitanTravel.TITAN_BACK || event.isCanceled()) {
            return;
        }
        persisted(player).putBoolean(TAG_DIED_ON_BACK, true);
        noteFallen(player);
        keepBelongings(player);
    }

    // 倒れた人の持ち物と経験値を預かる (潮殻の背など、ほかの巨神の背中からも使う)。
    // 返すのは onClone (目を覚ました体へ) と、足して返す分の deliverExtra (ログイン・復活)。どちらも背中を問わず動く
    public static void keepBelongings(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        if (tag.contains(TAG_KEPT)) {
            // 前に預かった物がまだ返っていない: 消さずに「足して返す分」へ移す
            CompoundTag old = tag.getCompound(TAG_KEPT);
            addExtra(tag, old.getList("Inventory", Tag.TAG_COMPOUND), old.getInt("Total"));
        }
        CompoundTag kept = new CompoundTag();
        kept.put("Inventory", keptInventory(player));
        kept.putInt("Level", player.experienceLevel);
        kept.putFloat("Progress", player.experienceProgress);
        kept.putInt("Total", player.totalExperience);
        tag.put(TAG_KEPT, kept);
    }

    // 持ち物を NBT にする。消滅の呪いが付いた物は、バニラと同じく消える
    private static ListTag keptInventory(ServerPlayer player) {
        ListTag all = player.getInventory().save(new ListTag());
        ListTag kept = new ListTag();
        for (Tag t : all) {
            Optional<ItemStack> stack = ItemStack.parse(player.registryAccess(), t);
            if (stack.isPresent() && !EnchantmentHelper.has(stack.get(), EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                kept.add(t);
            }
        }
        return kept;
    }

    private static void addExtra(CompoundTag tag, ListTag items, int xp) {
        CompoundTag extra = tag.getCompound(TAG_KEPT_EXTRA);
        ListTag list = extra.getList("Items", Tag.TAG_COMPOUND);
        for (Tag t : items) {
            list.add(t.copy());
        }
        extra.put("Items", list);
        extra.putInt("Xp", extra.getInt("Xp") + xp);
        tag.put(TAG_KEPT_EXTRA, extra);
    }

    // 足して返す分を渡す。入りきらない物は足元に置く
    private static void deliverExtra(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        if (!tag.contains(TAG_KEPT_EXTRA)) {
            return;
        }
        CompoundTag extra = tag.getCompound(TAG_KEPT_EXTRA);
        tag.remove(TAG_KEPT_EXTRA);
        for (Tag t : extra.getList("Items", Tag.TAG_COMPOUND)) {
            ItemStack.parse(player.registryAccess(), t).ifPresent(stack -> {
                if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                    player.drop(stack, false);
                }
            });
        }
        if (extra.getInt("Xp") > 0) {
            player.giveExperiencePoints(extra.getInt("Xp"));
        }
    }

    private static boolean keepsBelongings(Entity entity) {
        return entity instanceof ServerPlayer player && jp.revati.skyabyss.titan.TitanBacks.isBack(player.level().dimension())
                && persisted(player).contains(TAG_KEPT);
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        if (keepsBelongings(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // 経験値の玉を落とさない。経験値は預かって onClone で戻すので、落とすと二重になる (02 の 7 章。背中で何度も挑むため)
    @SubscribeEvent
    static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (keepsBelongings(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        CompoundTag oldTag = persisted(event.getOriginal());
        if (!oldTag.contains(TAG_KEPT)) {
            return;
        }
        CompoundTag kept = oldTag.getCompound(TAG_KEPT);
        Player player = event.getEntity();
        player.getInventory().load(kept.getList("Inventory", Tag.TAG_COMPOUND));
        player.experienceLevel = kept.getInt("Level");
        player.experienceProgress = kept.getFloat("Progress");
        player.totalExperience = kept.getInt("Total");
        // 返したので、両方の記録から消す (NeoForge が古い記録を新しい体へ写している)
        oldTag.remove(TAG_KEPT);
        CompoundTag newTag = persisted(player);
        newTag.remove(TAG_KEPT);
        if (oldTag.contains(TAG_KEPT_EXTRA) && !newTag.contains(TAG_KEPT_EXTRA)) {
            newTag.put(TAG_KEPT_EXTRA, oldTag.getCompound(TAG_KEPT_EXTRA));
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        CompoundTag tag = persisted(player);
        if (tag.getBoolean(TAG_DIED_ON_BACK)) {
            tag.remove(TAG_DIED_ON_BACK);
            ServerLevel back = player.server.getLevel(TitanTravel.TITAN_BACK);
            if (back != null) {
                // 地上のリスポーン地点 (ベッドなど) は変えず、背中の休み場へ連れ戻すだけ。
                // 記録が古い (リセットより前) か無ければ、尾の台へ
                int epoch = epochOf(back, player);
                String cp = validCheckpoint(tag, epoch);
                // 休み場が、いまの戦いの扉の前室なら、戦場の目の前で目覚めないよう鼓動の間 (安全な所) にする
                BlockPos spawn = cp.isEmpty() ? plaza() : checkpointSpawn(back, respawnCheckpoint(back, Groups.keyOf(player), cp));
                REVIVING.add(player.getUUID());
                try {
                    player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
                } finally {
                    REVIVING.remove(player.getUUID());
                }
                tag.putInt(TAG_SEEN_EPOCH, epoch);
                jp.revati.skyabyss.titan.group.RespawnGuard.begin(player);
                Notice.narrate(player, cp.isEmpty() && tag.contains(TAG_CHECKPOINT)
                        ? "message.skyabyss.rest_point_lost" : "message.skyabyss.revived_on_back");
                if (cp.isEmpty()) {
                    clearCheckpoint(player);
                }
            }
        }
        deliverExtra(player);
    }

    // 倒れて目覚める休み場。その休み場が戦いの部屋の前室で、そのグループがいま戦っている最中なら、鼓動の間にする
    // (倒れて戻れない扉の前、または戦場のすぐそばで目覚めないように。02 の 7.3)
    static String respawnCheckpoint(ServerLevel level, UUID group, String cp) {
        return isFightNear(group, cp) ? "hub" : cp;
    }

    // 休み場 cp が、そのグループの戦っている部屋の前室か
    static boolean isFightNear(UUID group, String cp) {
        switch (cp) {
            case "e0":
                return hallFighting(group, "e1");
            case "ckr":
                return hallFighting(group, "ck");
            case "heart":
                GroupBossRoom room = HEART_ROOMS.peek(group);
                return room != null && (room.active() || room.gathering());
            default:
                return false;
        }
    }

    private static boolean hallFighting(UUID group, String hall) {
        HallState state = HALL_STATES.get(hallKey(group, hall));
        return state != null;
    }

    // ---------------------------------------------------------------- 台座と焚き火
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        BlockPos pos = event.getPos();
        // 休み場の焚き火: しゃがんで触れると、試練をあきらめて尾の台へ戻る (休み場の記録も消える)
        net.minecraft.world.level.block.state.BlockState clicked = event.getLevel().getBlockState(pos);
        // (煙の出ない魂の火鉢 = 地面の魂のランタン。闇の水路の入口の小部屋 BuildKit.restPoint)
        if (clicked.is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get()) || (clicked.is(Blocks.SOUL_LANTERN) && !clicked.getValue(net.minecraft.world.level.block.LanternBlock.HANGING))) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                if (player.isShiftKeyDown()) {
                    if (jp.revati.skyabyss.trial.TrialRoute.propose(player)) {
                        return;
                    }
                    clearCheckpoint(player);
                    BlockPos a = plaza();
                    player.teleportTo(player.serverLevel(), a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 180f, 0f);
                    Notice.narrate(player, "message.skyabyss.gave_up");
                } else {
                    Notice.bar(player, ModText.hint("message.skyabyss.campfire_hint"));
                }
            }
        }
    }

    private static void broadcast(ServerLevel level, Box area, Component message) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area.aabb().inflate(20))) {
            Notice.send(player, message);
        }
    }
}

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
    // 扉が閉じるまでの集合の時間 (5 秒)
    public static final int GATHER_TICKS = 100;

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
    private static List<Checkpoint> checkpoints() {
        return BASE_CHECKPOINTS;
    }
    private static final List<String> WEST_CHECKPOINTS = List.of("w0", "w1", "w2", "wp");

    private static List<Checkpoint> checkpointsOf(ServerLevel level) {
        return checkpoints();
    }

    // ---------------------------------------------------------------- 状態 (グループごと)
    // 試練の進み (制圧した広間・宝物庫の封印・心臓の撃破・鳴らした石・扉の開け閉め) は、グループ (パーティ、または一人) の記録
    // (BackGroupData.Record) にある。ここにあるのは、戦いの最中のメモリの状態 (保存しない) だけで、(グループ, 広間) ごとに持つ。
    // 同じ広間・心臓の間で、グループごとに別々の戦いが同時に動く (敵・ボスはグループの印つきで、ほかのグループには見えない)
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
    // 巨神の中は建てない (docs/design/32 の 2.5)。置く戸は体の外の心臓の間の大門だけ
    private static final List<List<BlockPos>> CLOSED_GATES = List.of();
    private static final List<List<BlockPos>> OPEN_GATES = List.of(HEART_GATE);

    private TitanBackTrials() {
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
            BlockPos hub = safeSpot(level, jp.revati.skyabyss.trial.RouteSeals.safeFeet(jp.revati.skyabyss.progress.Titans.STONE_WHALE));
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
        // 自己テストは各段が建てる。ただし "back builds" (起動時の裏の建設を測る) のときは本番と同じに建てる
        if (System.getenv("SKYABYSS_SELFTEST") != null && !String.valueOf(System.getenv("SKYABYSS_SELFTEST_ONLY")).contains("back builds")) {
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
        // ピースフル: 戦いを静かに終わらせる。体の中の広間は建てないので、動かすのは体の外の心臓の間だけ (docs/design/32 の 2.5)
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        if (peaceful) {
            sleepForPeaceful(level, data);
        } else {
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
    private static final List<Box> SAFE_ON_RESET = List.of(HUB, ENTRY, W0, E0, WEST_HALL, EAST_HALL,
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

    @Nullable
    public static Mob spawnGuard(ServerLevel level, String kind, BlockPos pos) {
        return spawnGuard(level, kind, pos, null);
    }

    // 番兵長・老番兵などが「呼ぶ」仲間 (咆哮の番兵・骨笛の射手)。呼ぶ者が倒れた・消えたあと (予告の円が遅れて実るとき) は呼ばない
    @Nullable
    public static Mob spawnSummon(ServerLevel level, String kind, BlockPos pos, @Nullable UUID group, Mob owner) {
        if (owner == null || !owner.isAlive() || owner.isRemoved()) {
            return null;
        }
        return spawnGuard(level, kind, pos, group);
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

    // ---------------------------------------------------------------- 扉 (心臓の間の集合と再入場)
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
    private static final AABB HEART_ANTE = new AABB(HEART_CX - 7, FLOOR + 1, HEART_GATE_Z + 1, HEART_CX + 8, FLOOR + 8, HEART_REST.z2() + 1);

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
        BlockPos a = jp.revati.skyabyss.trial.RouteSeals.safeFeet(jp.revati.skyabyss.progress.Titans.STONE_WHALE);
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
        BlockPos hub = safeSpot(level, jp.revati.skyabyss.trial.RouteSeals.safeFeet(jp.revati.skyabyss.progress.Titans.STONE_WHALE));
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

    // 背中を建て直すとき: 全グループの進みを戻して試練の番号を進める (古い休み場の記録を無効にする)。扉のブロックは建て直しのあとに置き直す
    public static void resetAllGroups(ServerLevel level) {
        // 野営の道具は、建て直しで消える前に持ち主へ戻す
        CampBlocks.returnAllIn(level.getServer(), level.dimension());
        BackGroupData groups = BackGroupData.get(level);
        for (UUID id : groups.ids()) {
            GroupProgress.reset(level, id);
        }
        HEART_ROOMS.clear();
        gatesInstalled = false;
        jp.revati.skyabyss.trial.TrialRuntime.clearAll();
    }

    // すべてのグループを戻して共有の形も作り直す (コマンド・自己テスト・昇降の戻し)
    public static void resetTrials(ServerLevel level) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id);
        }
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
                BlockPos spawn = cp.isEmpty() ? jp.revati.skyabyss.trial.RouteSeals.safeFeet(jp.revati.skyabyss.progress.Titans.STONE_WHALE) : checkpointSpawn(back, respawnCheckpoint(back, Groups.keyOf(player), cp));
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
        // 順路では、広間 (hub) は封じて誰も入らない (trial/RouteSeals)。戦いの前室でも、その段の休み場で目覚める (32 の 2.4)
        return cp;
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
                    BlockPos a = jp.revati.skyabyss.trial.RouteSeals.safeFeet(jp.revati.skyabyss.progress.Titans.STONE_WHALE);
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

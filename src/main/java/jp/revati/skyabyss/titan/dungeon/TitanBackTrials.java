package jp.revati.skyabyss.titan.dungeon;

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
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.TitanTravel;
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

    private static final List<Checkpoint> CHECKPOINTS = List.of(
            // 鼓動の間の中央は眠る心臓の台座 (強化深層岩) なので、南へずらす
            new Checkpoint("hub", HUB, HUB.center().offset(0, 0, 6)),
            // 休み場の焚き火のまわり (中心から 2 マスの東西南北) には腰掛けがあるので、斜めに置く
            new Checkpoint("w0", W0, W0.center().offset(2, 0, 2)),
            new Checkpoint("w1", W1, W1.center().offset(2, 0, 2)),
            new Checkpoint("w2", W2, W2.center().offset(2, 0, 2)),
            new Checkpoint("e0", E0, E0.center().offset(2, 0, 2)),
            new Checkpoint("ce1", CE1, CE1.center().offset(2, 0, 2)),
            new Checkpoint("heart", HEART_REST, HEART_REST.center().offset(1, 0, 1)));
    private static final List<String> WEST_CHECKPOINTS = List.of("w0", "w1", "w2");

    // ---------------------------------------------------------------- 番兵の広間
    // ante は入口の手前の前室 (扉が閉じるとき、ここにいる人も中へ入れる)
    private record Hall(String id, Box box, AABB ante, List<BlockPos> entrance, List<BlockPos> exit, List<List<String>> waves) {
    }

    // 第一の広間は、近接の波 (番兵が前に立ち、苔這いが回り込む) → 射手の波 (番兵の盾の後ろから射手 3 体が撃つ)。
    // 番兵長の広間の第一波で両方が混ざる。(配置の版 2 で第二の広間を除き、その射手の波を第一の広間へ移した)
    private static final List<Hall> HALLS = List.of(
            new Hall("e1", E1, E0.aabb(), E1_ENTRANCE, E1_EXIT, List.of(
                    List.of("sentinel", "sentinel", "crawler", "crawler"),
                    List.of("archer", "archer", "archer", "sentinel"))),
            new Hall("e3", E3, E3_ANTE, E3_ENTRANCE, List.of(), List.of(
                    List.of("sentinel", "sentinel", "archer", "crawler", "crawler"),
                    List.of("captain"))));

    private static final class HallState {
        // 0 = 集合中 (扉は開いている)、1 以上 = 戦っている波の番号
        int wave;
        int gather = GATHER_TICKS;
        int delay;
        int players;
        // 広間が空に見えてからの時間 (一瞬の取りこぼしでは戻さない)
        int emptyTicks;
        // 戦いの参加者 (扉が閉じたときに中にいた人。途中で倒れた人も残す)
        final Set<UUID> participants = new LinkedHashSet<>();
    }

    private static final Map<String, HallState> HALL_STATES = new HashMap<>();
    // 集合の知らせを最後にチャットへ出した時刻 (出入りを繰り返しても流れ続けないように)
    private static final Map<String, Long> LAST_GATHER_NOTICE = new HashMap<>();
    // 背中で倒れ、背中へ連れ戻している途中の人 (次元の移動で休み場の記録を消さないため)
    private static final Set<UUID> REVIVING = new HashSet<>();
    private static boolean cleanedAfterLoad;
    private static int idleTicks;
    private static int afterHeartIdle;

    // ---------------------------------------------------------------- 心臓の間 (最終ボス)
    private static boolean heartActive;
    @Nullable
    private static UUID heartId;
    private static final Set<UUID> heartParticipants = new LinkedHashSet<>();
    private static int heartEmpty;
    // 集合の残り時間。-1 なら集合していない
    private static int heartGather = -1;
    private static int heartCooldown;

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
                    // 配置の版が変わった古いワールド: 島ごと建て直す。進み具合を戻して試練の番号を進めるので、
                    // 古い休み場の記録は無効になり、体内でログアウトしていた人は次のログインで尾の台へ出る
                    SkyAbyss.LOGGER.info("Rebuilding the stone whale's back (layout {} -> {})...", data.layoutVersion(), LAYOUT_VERSION);
                    data.resetProgress();
                } else {
                    SkyAbyss.LOGGER.info("Building the stone whale's back for this world...");
                }
                TitanBackBuilder.schedule(back, data);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        HALL_STATES.clear();
        LAST_GATHER_NOTICE.clear();
        REVIVING.clear();
        heartActive = false;
        heartId = null;
        heartParticipants.clear();
        heartEmpty = 0;
        heartGather = -1;
        heartCooldown = 0;
        cleanedAfterLoad = false;
        idleTicks = 0;
        afterHeartIdle = 0;
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
        if (!cleanedAfterLoad) {
            // 前回、戦いの途中でサーバーが止まっていたら、その名残を片付ける (閉じた扉を開け直す)
            cleanedAfterLoad = true;
            for (Hall hall : HALLS) {
                if (data.isHallCleared(hall.id())) {
                    open(level, hall.entrance());
                    open(level, hall.exit());
                } else {
                    resetHall(level, hall, false);
                }
            }
            resetHeart(level, false);
        }
        // ピースフル: 祠の階段を封じ、戦いを静かに終わらせ、体内の人を尾の台へ出す。広間と心臓は動かさない
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        tickShrineSeal(level, peaceful);
        if (peaceful) {
            sleepForPeaceful(level, data);
        } else {
            for (Hall hall : HALLS) {
                if (!data.isHallCleared(hall.id())) {
                    tickHall(level, data, hall);
                }
            }
        }
        // 宝物庫の封印が開いていれば、奥の扉も一緒に開く (三つの石がそろったとき)
        if (data.isGateOpened()) {
            open(level, INNER_GATE);
        }
        if (!peaceful) {
            tickHeart(level, data);
        }
        // 誰もいなくなってしばらくたったら、試練を元に戻す
        if (level.players().isEmpty()) {
            idleTicks += 10;
            if (idleTicks >= RESET_AFTER_IDLE && (data.hasProgress() || anyStoneActive(level))) {
                resetTrials(level);
            }
        } else {
            idleTicks = 0;
        }
        // 心臓を倒したあと、封印の先 (宝物庫より奥) に誰もいなくなって30秒たったら、試練を元に戻す。
        // 島のほかの場所 (尾の台など) で誰かが待っていても、次の周回へ挑み直せる
        if (data.isHeartDefeated()) {
            boolean anyoneInside = false;
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && isPastHeartSeal(player)) {
                    anyoneInside = true;
                    break;
                }
            }
            afterHeartIdle = anyoneInside ? 0 : afterHeartIdle + 10;
            if (afterHeartIdle >= RESET_AFTER_HEART) {
                resetTrials(level);
                for (ServerPlayer player : level.players()) {
                    Notice.narrate(player, "message.skyabyss.trials_renewed");
                }
            }
        } else {
            afterHeartIdle = 0;
        }
    }

    // ---------------------------------------------------------------- ピースフル (試練は眠る)
    // 祠の階段の封印: ピースフルのあいだだけ置き、ほかの難易度に戻ると外す (読み込まれているときだけ触る)
    private static void tickShrineSeal(ServerLevel level, boolean peaceful) {
        List<BlockPos> seal = shrineSeal();
        if (!level.isLoaded(seal.get(0))) {
            return;
        }
        boolean sealed = level.getBlockState(seal.get(0)).is(ModBlocks.SEALED_GATE.get());
        if (peaceful && !sealed) {
            // 封印の面に立っている人は先に尾の台へ出す (sleepForPeaceful)。人のいない所だけ埋める
            for (BlockPos p : seal) {
                if (level.getBlockState(p).isAir() && level.getEntitiesOfClass(Player.class, new AABB(p)).isEmpty()) {
                    level.setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
                }
            }
        } else if (!peaceful && sealed) {
            for (BlockPos p : seal) {
                if (level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
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
        for (Hall hall : HALLS) {
            if (HALL_STATES.containsKey(hall.id()) || (!data.isHallCleared(hall.id()) && isSealed(level, hall.entrance()))) {
                resetHall(level, hall, false);
            }
        }
        if (heartActive || heartGather >= 0 || isSealed(level, HEART_GATE)) {
            resetHeart(level, false);
            heartCooldown = 0;
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.isAlive() && !player.isCreative() && !player.isSpectator() && isPastShrineSeal(player)) {
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.trials_peaceful");
            }
        }
    }

    private static boolean isSealed(ServerLevel level, List<BlockPos> door) {
        return !door.isEmpty() && level.isLoaded(door.get(0)) && level.getBlockState(door.get(0)).is(ModBlocks.SEALED_GATE.get());
    }

    // 心臓の回廊 (宝物庫の奥の扉から心臓の間の入口まで)
    private static final Box HEART_CORRIDOR = new Box(-2, 2, -63, -45, FLOOR, 5);
    // リセットで作り直したり封じたりしない場所 (鼓動の間とその周り)。リセットのとき、ここにいる人は動かさない
    private static final List<Box> SAFE_ON_RESET = List.of(HUB, ENTRY, W0, E0,
            new Box(-15, -10, -23, -21, FLOOR, 4), new Box(10, 15, -23, -21, FLOOR, 4));

    // 封印の先 (宝物庫・心臓の回廊・心臓の前室・心臓の間) にいるか
    static boolean isPastHeartSeal(Player player) {
        BlockPos p = player.blockPosition();
        return TREASURY.contains(p) || HEART_CORRIDOR.contains(p) || HEART_REST.contains(p) || HEART.contains(p);
    }

    // 体内 (島の地表より下) にいるか
    static boolean isBelowSurface(Player player) {
        BlockPos p = player.blockPosition();
        return shape(p.getX(), p.getZ()) > 0 && p.getY() <= top(p.getX(), p.getZ());
    }

    private static void tickHall(ServerLevel level, TitanBackData data, Hall hall) {
        HallState state = HALL_STATES.get(hall.id());
        if (state == null || state.wave == 0) {
            List<ServerPlayer> inside = playersInside(level, hall);
            if (state == null) {
                if (!inside.isEmpty()) {
                    // 集合: 扉は開いたまま、5 秒待つ
                    state = new HallState();
                    HALL_STATES.put(hall.id(), state);
                    announceGathering(level, hall.id(), hall.box());
                }
                return;
            }
            if (inside.isEmpty() && playersInHall(level, hall).isEmpty()) {
                // 中から人がいなくなったので、集合を取りやめる
                HALL_STATES.remove(hall.id());
                return;
            }
            state.gather -= 10;
            if (state.gather > 0) {
                countdown(level, hall.box(), state.gather);
                return;
            }
            // 扉が閉じる: 戸口と前室にいる人も中へ入れ、中にいる人を参加者として数える
            pullIn(level, hall.entrance(), hall.box().center(), hall.ante());
            for (ServerPlayer player : playersInHall(level, hall)) {
                state.participants.add(player.getUUID());
            }
            if (state.participants.isEmpty()) {
                HALL_STATES.remove(hall.id());
                return;
            }
            state.wave = 1;
            state.players = state.participants.size();
            seal(level, hall.entrance());
            level.playSound(null, hall.entrance().get(0), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
            broadcast(level, hall.box(), ModText.battle("message.skyabyss.hall_begin"));
            SkyAbyss.LOGGER.info("Hall {} sealed with {} challenger(s)", hall.id(), state.players);
            spawnWave(level, hall, state);
            return;
        }
        // 戦いが始まったら、壁ぎわや戸口に立っていても「中にいる」と数える。
        // 全員が倒れて (休み場へ戻されて) 3秒たったときだけ、広間を最初に戻す
        List<ServerPlayer> inHall = playersInHall(level, hall);
        if (inHall.isEmpty()) {
            state.emptyTicks += 10;
            if (state.emptyTicks >= 60) {
                resetHall(level, hall, true);
            }
            return;
        }
        for (ServerPlayer player : inHall) {
            state.participants.add(player.getUUID());
        }
        state.emptyTicks = 0;
        // 倒れて前室で目を覚ました参加者は、戦いが続いている間、扉に触れると内側へ戻れる
        letParticipantsBack(level, hall.entrance(), hall.box().center(), state.participants);
        if (aliveGuards(level, hall) > 0) {
            return;
        }
        if (state.delay == 0) {
            state.delay = WAVE_DELAY;
            if (state.wave < hall.waves().size()) {
                broadcast(level, hall.box(), ModText.battle("message.skyabyss.arena_next"));
            }
            return;
        }
        state.delay -= 10;
        if (state.delay > 0) {
            return;
        }
        state.delay = 0;
        if (state.wave >= hall.waves().size()) {
            // 制圧: 出口と入口の両方が開く (倒れて控えの間で目を覚ました仲間も、先へ進める)
            data.markHallCleared(hall.id());
            HALL_STATES.remove(hall.id());
            open(level, hall.exit());
            open(level, hall.entrance());
            level.playSound(null, hall.box().center(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, 0.8f);
            broadcast(level, hall.box(), ModText.progress(hall.exit().isEmpty() ? "message.skyabyss.arena_cleared" : "message.skyabyss.hall_cleared"));
        } else {
            state.wave++;
            spawnWave(level, hall, state);
        }
    }

    // 中にいた全員が倒れたので、番兵を眠らせて最初に戻す (入口が開き、休み場からやり直せる)。出口は閉じたまま
    private static void resetHall(ServerLevel level, Hall hall, boolean announce) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, hall.box().aabb().inflate(4), e -> e.getTags().contains(MOB_TAG))) {
            e.discard();
        }
        HALL_STATES.remove(hall.id());
        open(level, hall.entrance());
        seal(level, hall.exit());
        if (announce) {
            broadcast(level, hall.box(), ModText.deny("message.skyabyss.arena_reset"));
        }
    }

    private static int aliveGuards(ServerLevel level, Hall hall) {
        return level.getEntitiesOfClass(Mob.class, hall.box().aabb().inflate(3), e -> e.isAlive() && e.getTags().contains(MOB_TAG)).size();
    }

    private static void spawnWave(ServerLevel level, Hall hall, HallState state) {
        List<BlockPos> spawns = EastWingBuilder.spawnPoints(hall.box());
        List<String> wave = new ArrayList<>(hall.waves().get(state.wave - 1));
        // 人数が多いほど番兵も増える (1人増えるごとに2体。番兵長の波では骨の番兵が増える。最大4人分)
        for (int extra = 1; extra < Math.min(state.players, 4); extra++) {
            if (wave.get(0).equals("captain")) {
                wave.add("sentinel");
                wave.add("archer");
            } else {
                wave.add(wave.get(0));
                wave.add(wave.get(1));
            }
        }
        for (int i = 0; i < wave.size(); i++) {
            BlockPos at = wave.get(i).equals("captain") ? hall.box().center() : spawns.get(i % spawns.size());
            Mob mob = spawnGuard(level, wave.get(i), at);
            if (mob instanceof WhaleboneCaptain captain) {
                // 番兵長は心臓と同じ式で、人数に応じて体力が増える (280 × (1 + 0.6 × (人数 − 1)))
                var health = captain.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(Math.min(1024, health.getBaseValue() * StoneWhaleHeart.scaleFor(state.players)));
                    captain.setHealth(captain.getMaxHealth());
                }
            }
        }
        broadcast(level, hall.box(), ModText.battle("message.skyabyss.arena_wave", state.wave, hall.waves().size()));
    }

    @Nullable
    public static Mob spawnGuard(ServerLevel level, String kind, BlockPos pos) {
        EntityType<? extends Mob> type = switch (kind) {
            case "archer" -> ModEntities.RIB_ARCHER.get();
            case "crawler" -> ModEntities.MOSS_CRAWLER.get();
            case "captain" -> ModEntities.WHALEBONE_CAPTAIN.get();
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
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
        return mob;
    }

    // 封印された広間の中にいる人 (壁ぎわも含む)
    private static List<ServerPlayer> playersInHall(ServerLevel level, Hall hall) {
        return level.getEntitiesOfClass(ServerPlayer.class, hall.box().aabb().inflate(0, 1, 0), p -> p.isAlive() && !p.isSpectator());
    }

    private static List<ServerPlayer> playersInside(ServerLevel level, Hall hall) {
        // 戸口に立っているだけでは始まらないよう、内側に1マス入ったところから数える
        AABB area = hall.box().aabb().deflate(1, 0, 1);
        return level.getEntitiesOfClass(ServerPlayer.class, area, p -> p.isAlive() && !p.isSpectator());
    }

    // 番兵長が倒れたら、E3 の参加者に中ボスの報酬を渡す (TitanLoot)
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onCaptainDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WhaleboneCaptain captain) || event.isCanceled()
                || !(captain.level() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK
                || !captain.getTags().contains(MOB_TAG)) {
            return;
        }
        HallState state = HALL_STATES.get("e3");
        Set<UUID> ids = new LinkedHashSet<>();
        if (state != null) {
            ids.addAll(state.participants);
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, E3.aabb().inflate(0, 1, 0), p -> !p.isSpectator())) {
            ids.add(player.getUUID());
        }
        TitanLoot.onCaptainDefeat(level, onlinePlayers(level, ids));
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
    private static void announceGathering(ServerLevel level, String key, Box area) {
        long now = level.getGameTime();
        Long last = LAST_GATHER_NOTICE.get(key);
        if (last == null || now - last > 400) {
            LAST_GATHER_NOTICE.put(key, now);
            broadcast(level, area, ModText.battle("message.skyabyss.gate_gathering", GATHER_TICKS / 20));
        }
        level.playSound(null, area.center(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.5f, 0.6f);
    }

    // 扉が閉じるまでの残りを、近くの人の画面に出す (1 秒ごと)
    private static void countdown(ServerLevel level, Box area, int ticksLeft) {
        if (ticksLeft % 20 != 0) {
            return;
        }
        Component text = ModText.battle("message.skyabyss.gate_countdown", ticksLeft / 20);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area.aabb().inflate(12))) {
            Notice.bar(player, text);
        }
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

    // 扉が閉じるとき: 戸口と前室 (ante) にいる人を中へ入れる
    private static void pullIn(ServerLevel level, List<BlockPos> door, BlockPos roomCenter, AABB ante) {
        if (door.isEmpty()) {
            return;
        }
        AABB box = doorBox(door);
        Set<ServerPlayer> movers = new LinkedHashSet<>();
        movers.addAll(level.getEntitiesOfClass(ServerPlayer.class, box.inflate(2, 1, 2),
                p -> p.isAlive() && !p.isSpectator() && outsideOrInDoor(p, box, roomCenter)));
        movers.addAll(level.getEntitiesOfClass(ServerPlayer.class, ante.inflate(0, 1, 0), p -> p.isAlive() && !p.isSpectator()));
        for (ServerPlayer player : movers) {
            moveInside(level, player, box, roomCenter);
        }
    }

    // 戦いの途中: 扉に触れた参加者だけを内側へ通す (倒れて前室で目を覚ました人が戻れるように)
    private static void letParticipantsBack(ServerLevel level, List<BlockPos> door, BlockPos roomCenter, Set<UUID> participants) {
        if (door.isEmpty() || participants.isEmpty()) {
            return;
        }
        AABB box = doorBox(door);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box.inflate(1.2, 0, 1.2),
                p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()) && outsideOrInDoor(p, box, roomCenter))) {
            moveInside(level, player, box, roomCenter);
            Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
        }
    }

    // ---------------------------------------------------------------- 心臓の間 (最終ボス)
    // 内側に人が入ると 5 秒の集合ののち入口が閉じ、心臓が現れる。全員が倒れるか島から消えて数秒たつと、心臓を眠らせて入口を開け直す
    private static void tickHeart(ServerLevel level, TitanBackData data) {
        if (data.isHeartDefeated()) {
            tickHeartPillar(level);
            return;
        }
        List<ServerPlayer> inside = playersInHeart(level);
        if (!heartActive) {
            if (heartCooldown > 0) {
                heartCooldown -= 10;
                return;
            }
            if (heartGather < 0) {
                if (!inside.isEmpty()) {
                    heartGather = GATHER_TICKS;
                    announceGathering(level, "heart", HEART);
                }
                return;
            }
            if (inside.isEmpty()) {
                heartGather = -1;
                return;
            }
            heartGather -= 10;
            if (heartGather > 0) {
                countdown(level, HEART, heartGather);
                return;
            }
            heartGather = -1;
            // 戸口と前室 (心臓の前室から入口までの回廊) にいる人も中へ入れる
            pullIn(level, HEART_GATE, HEART.center(), HEART_ANTE);
            startHeart(level, playersInHeart(level));
            return;
        }
        Entity heart = heartId == null ? null : level.getEntity(heartId);
        if (heart instanceof LivingEntity dying && dying.isDeadOrDying() && !dying.isRemoved()) {
            // 倒れる演出の最中 (撃破の報告は演出が終わってから出る)。部屋は閉じたまま待つ
            return;
        }
        if (heart == null || !heart.isAlive()) {
            // 撃破の報告がないまま心臓が消えたので、最初に戻す。すぐには湧かせ直さない
            resetHeart(level, true);
            heartCooldown = HEART_RETRY_COOLDOWN;
            return;
        }
        // 戦いの中で心臓が部屋の外へ出たら、中央へ戻す
        if (heart.distanceToSqr(HEART_CX + 0.5, FLOOR + 1, HEART_CZ + 0.5) > 20 * 20) {
            heart.moveTo(HEART_CX + 0.5, FLOOR + 1, HEART_CZ + 0.5, heart.getYRot(), 0f);
        }
        if (inside.isEmpty()) {
            heartEmpty += 10;
            if (heartEmpty >= HEART_ABANDON_DELAY) {
                resetHeart(level, true);
            }
            return;
        }
        heartEmpty = 0;
        for (ServerPlayer player : inside) {
            heartParticipants.add(player.getUUID());
        }
        letParticipantsBack(level, HEART_GATE, HEART.center(), heartParticipants);
    }

    // 心臓の間の前室: 心臓の前室 (休み場) から入口までの回廊
    private static final AABB HEART_ANTE = new AABB(-3, FLOOR + 1, HEART_GATE_Z + 1, 4, FLOOR + 6, HEART_REST.z2() + 1);

    private static void startHeart(ServerLevel level, List<ServerPlayer> inside) {
        if (inside.isEmpty()) {
            return;
        }
        heartActive = true;
        heartEmpty = 0;
        heartParticipants.clear();
        for (ServerPlayer player : inside) {
            heartParticipants.add(player.getUUID());
        }
        seal(level, HEART_GATE);
        level.playSound(null, HEART.center(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        StoneWhaleHeart heart = ModEntities.STONE_WHALE_HEART.get().create(level);
        if (heart != null) {
            heart.moveTo(HEART_CX + 0.5, FLOOR + 1, HEART_CZ + 0.5, 0f, 0f);
            heart.addTag(HEART_TAG);
            heart.bindArena(heartArena(), inside.size());
            level.addFreshEntity(heart);
            heartId = heart.getUUID();
        }
        broadcast(level, HEART, ModText.battle("message.skyabyss.heart_begin"));
        SkyAbyss.LOGGER.info("The heart of the stone whale woke with {} challenger(s)", inside.size());
    }

    // 心臓を消して、入口を開ける。心臓の間に残った心臓の印も片付ける
    private static void resetHeart(ServerLevel level, boolean announce) {
        boolean wasActive = heartActive;
        for (Entity e : level.getEntitiesOfClass(Entity.class, HEART.aabb().inflate(6), e -> e.getTags().contains(HEART_TAG))) {
            e.discard();
        }
        heartActive = false;
        heartId = null;
        heartParticipants.clear();
        heartEmpty = 0;
        heartGather = -1;
        open(level, HEART_GATE);
        if (announce && wasActive) {
            broadcast(level, HEART, ModText.deny("message.skyabyss.heart_abandoned"));
        }
    }

    // 岩鯨の心臓を倒したときに、心臓そのものが呼ぶ。参加者に報酬を渡し、入口と光の柱を開く
    public static void onHeartDefeated(ServerLevel level, StoneWhaleHeart heart) {
        TitanBackData data = TitanBackData.get(level);
        // いま戦っている心臓でなければ (古い個体など) 何もしない
        if (data.isHeartDefeated() || !heartActive || !heart.getUUID().equals(heartId)) {
            return;
        }
        data.markHeartDefeated();
        Set<UUID> ids = new LinkedHashSet<>(heartParticipants);
        for (ServerPlayer player : playersInHeart(level)) {
            ids.add(player.getUUID());
        }
        heartActive = false;
        heartId = null;
        heartParticipants.clear();
        heartEmpty = 0;
        afterHeartIdle = 0;
        // 入口は開けたまま。奥の扉から来た道を戻れる
        open(level, HEART_GATE);
        List<ServerPlayer> participants = onlinePlayers(level, ids);
        TitanLoot.onHeartDefeat(level, participants);
        for (ServerPlayer player : level.players()) {
            Notice.unlock(player, "message.skyabyss.heart_defeated");
        }
        BlockPos c = HEART.center();
        level.playSound(null, c, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.5f, 0.6f);
        level.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 0.7f);
        SkyAbyss.LOGGER.info("The heart of the stone whale was defeated by {} participant(s)", participants.size());
    }

    // 心臓を倒したあとの光の柱。中央に立つと尾の台へ戻される
    private static void tickHeartPillar(ServerLevel level) {
        double px = HEART_CX + 0.5;
        double pz = HEART_CZ + 0.5;
        level.sendParticles(ParticleTypes.END_ROD, px, FLOOR + 1 + (level.getGameTime() % 12), pz, 3, 0.25, 0.2, 0.25, 0.02);
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && Math.abs(player.getX() - px) < 1.2 && Math.abs(player.getZ() - pz) < 1.2
                    && player.getY() > FLOOR && player.getY() < FLOOR + 12) {
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.heart_pillar");
            }
        }
    }

    private static void toArrival(ServerPlayer player) {
        BlockPos a = arrival();
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

    // ---------------------------------------------------------------- リセット
    public static void resetTrials(ServerLevel level) {
        TitanBackData data = TitanBackData.get(level);
        resetHeart(level, false);
        heartCooldown = 0;
        for (BlockPos p : RESONANCE_STONES) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ResonanceStoneBlock) {
                level.setBlock(p, s.setValue(ResonanceStoneBlock.ACTIVE, false), 3);
            }
        }
        for (List<BlockPos> gate : List.of(GATE, INNER_GATE, WEST_RETURN, EAST_RETURN, LOWER_RETURN)) {
            seal(level, gate);
        }
        for (Hall hall : HALLS) {
            resetHall(level, hall, false);
        }
        // アスレチックと迷路を、新しい種で作り直す
        BuildKit kit = new BuildKit(level, RandomSource.create(level.getRandom().nextLong()));
        WestWingBuilder.clearCourses(kit, ParkourCourses.forSeed(data.courseSeed()));
        data.reseed(level.getRandom());
        WestWingBuilder.placeCourses(kit, ParkourCourses.forSeed(data.courseSeed()));
        MazeBuilder.buildMaze(new BuildKit(level, RandomSource.create(data.mazeSeed())), data.mazeSeed());
        // 進み具合を消し、試練の番号を進める (古い休み場の記録は、これで無効になる)
        data.resetProgress();
        idleTicks = 0;
        afterHeartIdle = 0;
        // 体内で、作り直す所や封じる所 (翼・水路・広間・宝物庫より奥) にいる人は、鼓動の間へ出す
        // (閉じた封印の奥や、作り直した足場の中に取り残さない)。鼓動の間と地表にいる人はそのまま
        BlockPos hub = safeSpot(level, checkpointSpawnRaw("hub"));
        for (ServerPlayer player : List.copyOf(level.players())) {
            CompoundTag tag = persisted(player);
            tag.remove(TAG_CHECKPOINT);
            tag.remove(TAG_CHECKPOINT_EPOCH);
            tag.putInt(TAG_SEEN_EPOCH, data.epoch());
            if (player.isAlive() && !player.isSpectator() && isBelowSurface(player) && !inSafeArea(player)) {
                player.fallDistance = 0;
                player.teleportTo(level, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, player.getYRot(), 0f);
                Notice.narrate(player, "message.skyabyss.rest_point_lost");
            }
        }
        SkyAbyss.LOGGER.info("The stone whale's trials have been reset (epoch {})", data.epoch());
    }

    private static boolean anyStoneActive(ServerLevel level) {
        for (BlockPos p : RESONANCE_STONES) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ResonanceStoneBlock && s.getValue(ResonanceStoneBlock.ACTIVE)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHallCleared(ServerLevel level, String id) {
        return TitanBackData.get(level).isHallCleared(id);
    }

    static void seal(ServerLevel level, List<BlockPos> blocks) {
        for (BlockPos p : blocks) {
            level.setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
        }
    }

    static void open(ServerLevel level, List<BlockPos> blocks) {
        for (BlockPos p : blocks) {
            if (level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
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
                    String cp = validCheckpoint(persisted(player), TitanBackData.get(player.serverLevel()).epoch());
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
        int epoch = TitanBackData.get(player.serverLevel()).epoch();
        CompoundTag tag = persisted(player);
        // いまの試練の番号を見た、という印 (ログインしたときの検査に使う)
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        for (Checkpoint cp : CHECKPOINTS) {
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
        for (Checkpoint cp : CHECKPOINTS) {
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

    // 休み場ごとの復活位置 (自己テスト用)
    public static Map<String, BlockPos> checkpointSpawns() {
        Map<String, BlockPos> map = new HashMap<>();
        for (Checkpoint cp : CHECKPOINTS) {
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
        for (Checkpoint cp : CHECKPOINTS) {
            if (cp.id().equals(id)) {
                return cp.spawn();
            }
        }
        return arrival();
    }

    private static BlockPos checkpointSpawn(ServerLevel level, String id) {
        for (Checkpoint cp : CHECKPOINTS) {
            if (cp.id().equals(id)) {
                return safeSpot(level, cp.spawn());
            }
        }
        return arrival();
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
                persisted(player).putInt(TAG_SEEN_EPOCH, TitanBackData.get(back).epoch());
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
            int epoch = TitanBackData.get(back).epoch();
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
                int epoch = TitanBackData.get(back).epoch();
                String cp = validCheckpoint(tag, epoch);
                BlockPos spawn = cp.isEmpty() ? arrival() : checkpointSpawn(back, cp);
                REVIVING.add(player.getUUID());
                try {
                    player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
                } finally {
                    REVIVING.remove(player.getUUID());
                }
                tag.putInt(TAG_SEEN_EPOCH, epoch);
                Notice.narrate(player, cp.isEmpty() && tag.contains(TAG_CHECKPOINT)
                        ? "message.skyabyss.rest_point_lost" : "message.skyabyss.revived_on_back");
                if (cp.isEmpty()) {
                    clearCheckpoint(player);
                }
            }
        }
        deliverExtra(player);
    }

    // ---------------------------------------------------------------- 台座と焚き火
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        BlockPos pos = event.getPos();
        if (pos.equals(PEDESTAL)) {
            // 飾りの台座。欠片は心臓を倒すと受け取れる
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                // 天の鍵の欠片をもう受け取った人には、そう伝える
                boolean claimed = TitanBackData.get(player.serverLevel()).hasClaimedFragment(player.getUUID());
                Notice.bar(player, ModText.hint(claimed ? "message.skyabyss.fragment_already" : "message.skyabyss.pedestal_sleep"));
            }
            return;
        }
        // 休み場の焚き火: しゃがんで触れると、試練をあきらめて尾の台へ戻る (休み場の記録も消える)
        if (event.getLevel().getBlockState(pos).is(Blocks.SOUL_CAMPFIRE)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                if (player.isShiftKeyDown()) {
                    clearCheckpoint(player);
                    BlockPos a = arrival();
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

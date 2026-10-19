package jp.revati.skyabyss.titan.ember.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.dungeon.SealedGateBlock;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupEvents;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 余燼の背の試練の進み (13 の 3.5節・7 章): 熾り石・抜け道の光る床・熾の底の門・リセット・静かな試練・休み場・ピースフル。
// 進みはグループ (パーティ、または一人) ごと (docs/design/10 の 9 章、EmberBackState)。背中は一つで、同じ背中に別のグループが同時にいてよい:
//  - 熾り石の光・光る床・広間の炉の火と囲いの口は、グループごとの見かけ (GroupOverlays)。ワールドのブロックは「まだ何も鳴っていない」姿
//  - 熾の底の門 (格子と囲いの南の口)・鍛え手の扉・熾の底の入口は、いつも置いてある扉 (SealedGateBlock)。開け閉めはグループごと (GroupGates)
//  - ブロックが動く試練の部屋 (灰の道・鍛えの間と鍛え手の間・火守り・熾の底) は一度に一つのグループ (EmberRooms)
// ほかの担当が呼ぶ API:
//   markReady(level, group, trial)      石を鳴らせるようにする (東: E が鍛え手を倒したとき、南: C3 が火床を 100 にしたとき。西は奥の岸に着けばいつでも)
//   ring(level, group, trial, player)   石を鳴らす (ふつうは人が石を右クリックしたとき useStone から。テスト用)
//   quiet(level, room)                  部屋の持ち主にとって、この周その部屋が静かな試練か (east が静かだと smith の部屋も。いつでも入れて最後までできる)
//   EmberBackState.forRoom(level, room) 部屋の持ち主の状態 (リセットの種で島の並び・打つ順を決める)
//   inTrial(pos)・toHub(player)         試練の箱の中か / 広間の休み場へ移す (熱は 0)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberBackTrials {
    public static final int RESET_AFTER_IDLE = GroupProgress.IDLE_TICKS;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 60;
    public static final int EMBER_LEVEL = 30;
    // 炉の火が三度弱まってから門が開くまで (tick)
    public static final int GATE_OPEN_TICKS = 70;
    // ピースフルのあいだ喉の坂を封じる所 (広間の手前)
    public static final int PEACEFUL_SEAL_Z = -60;
    private static final String TAG_CHECKPOINT = "skyabyss_ember_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_ember_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_ember_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_ember";

    // 熾の底の門: 広間の中央の炉の底の格子 (3 × 3)・その上の火 (3 × 3。見かけだけ)・炉の囲いの南の口 (3 × 2)
    public static final List<BlockPos> HUB_PLUG = hubPlug();
    public static final List<BlockPos> HUB_FIRE = hubFire();
    public static final List<BlockPos> HUB_WALL = hubWall();

    // グループごとの門の開く演出の残り tick と、心臓を倒したあと門の奥にグループの誰もいない時間
    private static final Map<UUID, Integer> GATE_TIMERS = new HashMap<>();
    private static final Map<UUID, Integer> AFTER_HEART = new HashMap<>();
    private static final Map<UUID, Integer> ON_PAD = new HashMap<>();
    private static final Set<UUID> REVIVING = new HashSet<>();

    private EmberBackTrials() {
    }

    private static List<BlockPos> hubPlug() {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.add(HUB_CENTER.offset(dx, 0, dz));
            }
        }
        return List.copyOf(out);
    }

    private static List<BlockPos> hubFire() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : hubPlug()) {
            out.add(p.above());
        }
        return List.copyOf(out);
    }

    private static List<BlockPos> hubWall() {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 1; y <= 2; y++) {
                out.add(HUB_CENTER.offset(dx, y, 2));
            }
        }
        return List.copyOf(out);
    }

    // 熾の底の門の扉ブロック (格子と囲いの口)。グループごとに開ける・閉める
    public static List<BlockPos> gateBlocks() {
        List<BlockPos> out = new ArrayList<>(HUB_PLUG);
        out.addAll(HUB_WALL);
        return out;
    }

    public static List<BlockPos> boxBlocks(Box b) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(b.min(), b.max())) {
            out.add(p.immutable());
        }
        return out;
    }

    public static boolean onBack(ServerLevel level) {
        return level.dimension() == EmberIds.BACK;
    }

    static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- グループの見かけ・音 (EmberBackSetup から一度だけ)
    static void registerGroupHooks() {
        GroupOverlays.register(EmberBackTrials::overlay);
        GroupEvents.addMembershipHook(EmberBackTrials::onGroupChanged);
    }

    // グループに見せる姿: 鳴った石と光る床、門が閉じていれば炉の火と囲いの口
    static void overlay(ServerLevel level, UUID group, BiConsumer<BlockPos, BlockState> out) {
        if (!onBack(level)) {
            return;
        }
        EmberBackState state = EmberBackState.peek(level, group);
        for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
            if (!state.rung(t)) {
                continue;
            }
            out.accept(stonePos(t), EmberBackSetup.KINDLE_STONE.get().defaultBlockState().setValue(KindleStoneBlock.ACTIVE, true));
            BlockPos pad = padPos(t);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.accept(pad.offset(dx, 0, dz), dx == 0 && dz == 0 ? EmberKit.EMBERLIGHT : Blocks.SHROOMLIGHT.defaultBlockState());
                }
            }
        }
        if (!gateOpenFor(level, group)) {
            BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, fireLit(group)).setValue(CampfireBlock.SIGNAL_FIRE, false);
            for (BlockPos p : HUB_FIRE) {
                out.accept(p, fire);
            }
            for (BlockPos p : HUB_WALL) {
                out.accept(p, p.getY() == HUB_CENTER.getY() + 1 ? EmberKit.RUIN_CHISELED : EmberKit.RUIN);
            }
        }
    }

    // 門の開く演出のうち、火が灯っているか (0・20・40 tick で消え、10 tick あとにまた灯る。最後は消えたまま)
    static boolean fireLit(UUID group) {
        Integer timer = GATE_TIMERS.get(group);
        if (timer == null || timer <= 0) {
            return true;
        }
        int t = GATE_OPEN_TICKS - timer;
        return t < 50 && t % 20 >= 10;
    }

    // グループの人 (近くにいる) にだけ音を送る
    public static void soundTo(ServerLevel level, UUID group, Vec3 at, SoundEvent sound, float volume, float pitch) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, SoundSource.BLOCKS, at.x, at.y, at.z, volume, pitch, level.random.nextLong());
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(at) < 64 * 64 && p.connection != null) {
                p.connection.send(packet);
            }
        }
    }

    // グループの人 (近くにいる) にだけ粒を送る
    public static void particlesTo(ServerLevel level, UUID group, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
            double dz, double speed) {
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(x, y, z) < 64 * 64) {
                level.sendParticles(p, particle, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    // グループの鍵が変わった人 (組む・入る・抜ける): 試練の体の中にいれば広間へ。休み場の記録は試練の番号が違えば無効になる
    static void onGroupChanged(ServerPlayer player) {
        if (!onBack(player.serverLevel())) {
            return;
        }
        if (player.isAlive() && !player.isSpectator() && inTrial(player.position())) {
            toHub(player);
            Notice.narrate(player, EmberIds.LANG_BACK + "group_changed");
        }
        int epoch = EmberBackState.of(player.serverLevel(), player).epoch();
        CompoundTag tag = persisted(player);
        if (tag.contains(TAG_CHECKPOINT) && tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // ---------------------------------------------------------------- 起動・建てたあと
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel back = event.getServer().getLevel(EmberIds.BACK);
        if (back != null) {
            EmberBodyBuilder.schedule(back);
            TitanBackData data = TitanBackData.get(back);
            if (data.isBuilt() && !data.needsBuild(EmberBodyBuilder.buildVersion())) {
                // 建て直さない起動でも、扉のブロックと石の既定の姿をそろえる (何度呼んでも同じ)
                placeFixtures(back);
            }
        }
    }

    // 体を建てた直後 (EmberBodyBuilder から)。扉と石を置き、すべてのグループの試練を最初の形にする (静かにしない)
    static void afterBuild(ServerLevel level) {
        GATE_TIMERS.clear();
        AFTER_HEART.clear();
        placeFixtures(level);
        for (UUID id : BackGroupData.get(level).ids()) {
            EmberBackState state = EmberBackState.of(level, id);
            if (state.record() != null && state.record().hasProgress()) {
                GroupProgress.reset(level, id);
                state.afterReset(level.random, null, true);
            }
        }
        EmberRooms.releaseAll(level);
    }

    // ワールドのブロック (どのグループにも同じ): 扉はいつも扉のブロックとして置く (門と鍛え手の扉は閉じた既定、熾の底の入口は開いた既定)。
    // 石と光る床は鳴っていない姿、炉の火の所は空気 (火はグループの見かけ)
    public static void placeFixtures(ServerLevel level) {
        GroupGates.place(level, gateBlocks(), false);
        for (BlockPos p : HUB_FIRE) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        GroupGates.place(level, boxBlocks(SMITH_DOOR), false);
        GroupGates.place(level, boxBlocks(HEART_DOOR), true);
        for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
            BlockPos s = stonePos(t);
            BlockState want = EmberBackSetup.KINDLE_STONE.get().defaultBlockState().setValue(KindleStoneBlock.ACTIVE, false);
            if (!level.getBlockState(s).equals(want)) {
                level.setBlock(s, want, 3);
            }
            BlockPos pad = padPos(t);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState p = dx == 0 && dz == 0 ? EmberKit.RUIN_CHISELED : EmberKit.RUIN_DARK;
                    if (!level.getBlockState(pad.offset(dx, 0, dz)).equals(p)) {
                        level.setBlock(pad.offset(dx, 0, dz), p, 3);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- API
    public static void markReady(ServerLevel level, UUID group, EmberBackState.Trial trial) {
        EmberBackState state = EmberBackState.of(level, group);
        if (state.ready(trial)) {
            return;
        }
        state.markReady(trial);
        BlockPos s = stonePos(trial);
        soundTo(level, group, Vec3.atCenterOf(s), SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.7f);
        particlesTo(level, group, ParticleTypes.FLAME, s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(Vec3.atCenterOf(s)) < 40 * 40) {
                Notice.hint(p, EmberIds.LANG_BACK + "stone_ready");
            }
        }
    }

    // 部屋の持ち主にとって、この周その部屋が静かな周か (持ち主がいなければ false)
    public static boolean quiet(ServerLevel level, String room) {
        return EmberBackState.forRoom(level, room).roomQuiet(room);
    }

    public static BlockPos stonePos(EmberBackState.Trial t) {
        return switch (t) {
            case WEST -> STONE_WEST;
            case EAST -> STONE_EAST;
            case SOUTH -> STONE_SOUTH;
        };
    }

    static BlockPos padPos(EmberBackState.Trial t) {
        return switch (t) {
            case WEST -> PAD_WEST;
            case EAST -> PAD_EAST;
            case SOUTH -> PAD_SOUTH;
        };
    }

    static BlockPos shortcutTo(EmberBackState.Trial t) {
        return switch (t) {
            case WEST -> SHORTCUT_WEST_TO;
            case EAST -> SHORTCUT_EAST_TO;
            case SOUTH -> SHORTCUT_SOUTH_TO;
        };
    }

    // 試練の箱 (と熾の底) の中か
    public static boolean inTrial(Vec3 pos) {
        for (String room : TRIAL_ROOMS) {
            if (ROOMS.get(room).aabb().inflate(0, 2, 0).contains(pos)) {
                return true;
            }
        }
        return pastGate(pos);
    }

    // 熾の底の門より奥 (門の下の螺旋・前室・熾の底)
    public static boolean pastGate(Vec3 pos) {
        return HEART_ANTE.aabb().inflate(1, 0, 1).contains(pos) || HEART.aabb().contains(pos)
                || new AABB(GATE_SHAFT.minX() - 1, FLOOR_HEART, GATE_SHAFT.minZ() - 1, GATE_SHAFT.maxX() + 2, 84.9, HEART_ANTE.minZ() + 1).contains(pos);
    }

    // ---------------------------------------------------------------- 熾り石
    public static void useStone(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!onBack(level)) {
            return;
        }
        EmberBackState.Trial trial = null;
        for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
            if (stonePos(t).equals(pos)) {
                trial = t;
            }
        }
        if (trial == null) {
            return;
        }
        UUID group = Groups.keyOf(player);
        EmberBackState state = EmberBackState.of(level, group);
        if (state.rung(trial)) {
            jp.revati.skyabyss.titan.group.TrialSteles.tellWho(player, jp.revati.skyabyss.titan.group.TrialSteles.EMBER, trial.id());
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (trial != EmberBackState.Trial.WEST && !state.ready(trial)) {
            Notice.bar(player, ModText.deny(EmberIds.LANG_BACK + trial.id() + "_silent"));
            return;
        }
        ring(level, group, trial, player);
    }

    public static void ring(ServerLevel level, UUID group, EmberBackState.Trial trial, @Nullable ServerPlayer by) {
        EmberBackState state = EmberBackState.of(level, group);
        if (state.rung(trial)) {
            return;
        }
        state.ring(trial);
        GroupOverlays.refresh(level, group);
        BlockPos pos = stonePos(trial);
        soundTo(level, group, Vec3.atCenterOf(pos), EmberBackSetup.STONE_RING.get(), 3f, 1f);
        particlesTo(level, group, ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.05);
        particlesTo(level, group, ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.03);
        if (by != null) {
            Notice.bar(by, ModText.hint(EmberIds.LANG_BACK + "shortcut_opened"));
        }
        Component msg = ModText.progress(EmberIds.LANG_BACK + "stone_ringing", Component.translatable("trial.skyabyss.ember_" + trial.id()),
                state.rungCount(), 3);
        Groups.tell(level, group, msg);
        // 試練の踏破の経験値 (石の近くにいた、そのグループの人。仲間には分け前) と、サブクエストの「試練を踏破する」
        List<ServerPlayer> near = Groups.inBox(level, group, new AABB(pos).inflate(20));
        for (ServerPlayer p : near) {
            Growth.addXp(p, (int) Math.round(TRIAL_XP * GrowthRules.levelFalloff(Growth.get(p).level, EMBER_LEVEL)), "growth.skyabyss.reason.ember_trial");
            jp.revati.skyabyss.quest.Quests.signal(p, "ember_trial_" + trial.id());
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, near)) {
            Growth.addXp(mate, (int) Math.round(TRIAL_XP * rate * GrowthRules.levelFalloff(Growth.get(mate).level, EMBER_LEVEL)),
                    "growth.skyabyss.reason.party");
        }
        jp.revati.skyabyss.quest.Quests.signalWithParty(level, near, jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED);
        jp.revati.skyabyss.titan.group.TrialSteles.onRung(level, group, jp.revati.skyabyss.titan.group.TrialSteles.EMBER, trial.id(), null, by);
        checkAllRung(level, group);
    }

    // 三つそろったら、そのグループの炉の火を三度弱めて門を開く
    static void checkAllRung(ServerLevel level, UUID group) {
        EmberBackState state = EmberBackState.of(level, group);
        if (state.rungCount() >= 3 && !state.gateOpen() && !GATE_TIMERS.containsKey(group)) {
            GATE_TIMERS.put(group, GATE_OPEN_TICKS);
            for (ServerPlayer p : Groups.online(level, group)) {
                Notice.narrate(p, EmberIds.LANG_BACK + "all_rung");
            }
        }
    }

    // ---------------------------------------------------------------- 熾の底の門 (広間の中央の大きな炉の底の格子)
    private static void tickGates(ServerLevel level) {
        if (GATE_TIMERS.isEmpty()) {
            return;
        }
        for (UUID group : List.copyOf(GATE_TIMERS.keySet())) {
            int timer = GATE_TIMERS.get(group);
            int t = GATE_OPEN_TICKS - timer;
            Vec3 c = Vec3.atCenterOf(HUB_CENTER.above());
            // 三度弱まる (0・20・40 tick で消え、10 tick あとにまた灯る)
            if (t == 0 || t == 20 || t == 40) {
                soundTo(level, group, c, EmberBackSetup.FORGE_DIM.get(), 3f, 1f - t / 100f);
                particlesTo(level, group, ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 1, 0.5, 1, 0.02);
                GroupOverlays.refresh(level, group);
            } else if (t == 10 || t == 30 || t == 50) {
                GroupOverlays.refresh(level, group);
            }
            timer -= 1;
            if (timer <= 0) {
                openGate(level, group);
            } else {
                GATE_TIMERS.put(group, timer);
            }
        }
    }

    // そのグループに門を開く (コマンド・テストからも)
    public static void openGate(ServerLevel level, UUID group) {
        GATE_TIMERS.remove(group);
        EmberBackState.of(level, group).markGateOpen();
        GroupGates.open(level, group, gateBlocks());
        GroupOverlays.refresh(level, group);
        Vec3 c = Vec3.atCenterOf(HUB_CENTER);
        soundTo(level, group, c, SoundEvents.IRON_DOOR_OPEN, 3f, 0.5f);
        soundTo(level, group, c, SoundEvents.FIRE_EXTINGUISH, 2f, 0.6f);
        for (ServerPlayer p : Groups.online(level, group)) {
            Notice.narrate(p, EmberIds.LANG_BACK + "gate_open");
        }
    }

    // そのグループに門を閉じる (コマンド用。進みの記録は変えない)
    public static void closeGate(ServerLevel level, UUID group) {
        GATE_TIMERS.remove(group);
        GroupGates.seal(level, group, gateBlocks());
        GroupOverlays.refresh(level, group);
    }

    // そのグループにとって門が開いているか
    public static boolean gateOpenFor(ServerLevel level, UUID group) {
        return GroupGates.isOpen(level, group, HUB_PLUG.get(0));
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !onBack(level)) {
            return;
        }
        tickGates(level);
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        EmberRooms.tick(level);
        tickReset(level);
        tickPeaceful(level, level.getDifficulty() == Difficulty.PEACEFUL);
    }

    // ---------------------------------------------------------------- 抜け道の光る床・休み場の記録 (人ごと)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel()) || player.tickCount % 5 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // 光る床: そのグループが鳴らした石のそばの 3 × 3 に 0.5 秒いると、広間の扉の内側へ
        EmberBackState state = EmberBackState.of(level, player);
        boolean onPad = false;
        if (!player.isSpectator()) {
            for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
                BlockPos pad = padPos(t);
                if (state.rung(t) && Math.abs(player.getX() - (pad.getX() + 0.5)) < 1.6 && Math.abs(player.getZ() - (pad.getZ() + 0.5)) < 1.6
                        && player.getY() >= pad.getY() + 0.9 && player.getY() < pad.getY() + 2.5) {
                    onPad = true;
                    int n = ON_PAD.merge(player.getUUID(), 5, Integer::sum);
                    if (n >= 10) {
                        ON_PAD.remove(player.getUUID());
                        BlockPos to = shortcutTo(t);
                        player.fallDistance = 0;
                        player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), 0f);
                        soundTo(level, state.group(), Vec3.atCenterOf(to), SoundEvents.FIRECHARGE_USE, 0.8f, 0.6f);
                        Notice.bar(player, ModText.lore(EmberIds.LANG_BACK + "shortcut"));
                    }
                }
            }
        }
        if (!onPad) {
            ON_PAD.remove(player.getUUID());
        }
        // 休み場の記録 (試練の番号は、その人のグループのもの)
        int epoch = state.epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        BlockPos pos = player.blockPosition();
        for (Rest r : RESTS) {
            if (r.area().grow(1).contains(pos)) {
                if (!r.name().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, r.name());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    if (!r.name().equals("hub")) {
                        Notice.bar(player, ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss.ember_" + r.name())));
                        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f);
                    }
                }
                return;
            }
        }
    }

    public static String validCheckpoint(CompoundTag tag, int epoch) {
        String id = tag.getString(TAG_CHECKPOINT);
        if (id.isEmpty() || tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            return "";
        }
        return EmberIds.RESTS.contains(id) ? id : "";
    }

    // 休み場の記録 (その人のグループの試練の番号で確かめる)。背中にいない人は、背中のディメンションの記録で見る
    public static String checkpoint(ServerPlayer player) {
        ServerLevel back = player.server.getLevel(EmberIds.BACK);
        if (back == null) {
            return "";
        }
        return validCheckpoint(persisted(player), EmberBackState.of(back, player).epoch());
    }

    static void clearCheckpoint(Player player) {
        CompoundTag tag = persisted(player);
        tag.remove(TAG_CHECKPOINT);
        tag.remove(TAG_CHECKPOINT_EPOCH);
    }

    // 休み場の復活位置 (焚き火の前の台の上。立てる場所を探す)
    // 倒れて目覚める休み場。その休み場が戦いの部屋の前室で、そのグループがいま戦っている最中なら、鼓動の間 (hub) にする
    // (岩鯨の背と同じ。戦場のすぐそばで目覚めないように。02 の 7.3、brushup P1-1)
    public static String respawnCheckpoint(java.util.UUID group, String cp) {
        return isFightNear(group, cp) ? "hub" : cp;
    }

    static boolean isFightNear(java.util.UUID group, String cp) {
        return switch (cp) {
            case "heart" -> jp.revati.skyabyss.titan.ember.heart.EmberHeartBattle.fighting(group);
            case "e0" -> jp.revati.skyabyss.titan.ember.smith.AshSmithBattle.fighting(group);
            default -> false;
        };
    }

    public static BlockPos restSpawn(ServerLevel level, String id) {
        for (Rest r : RESTS) {
            if (r.name().equals(id)) {
                return TitanBackTrials.safeSpot(level, r.floor().above());
            }
        }
        return ARRIVAL.above();
    }

    public static void toArrival(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), ARRIVAL.getX() + 0.5, ARRIVAL.getY() + 1, ARRIVAL.getZ() + 0.5, ARRIVAL_YAW, 0f);
    }

    // 広間の休み場へ移す (リセット・ピースフル・グループが変わった)。熱は 0
    public static void toHub(ServerPlayer p) {
        BlockPos h = rest("hub").floor().above();
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), h.getX() + 0.5, h.getY(), h.getZ() + 0.5, rest("hub").yaw(), 0f);
        EmberHeat.reset(p);
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(rest("hub").floor().above());
    }

    // ---------------------------------------------------------------- リセット (グループごと。7.1節)
    // - 放置: そのグループの誰も背中にいないまま 3 分 (GroupProgress.idleGroups) たち、試練が進んでいれば
    // - 心臓のあと: そのグループが心臓を倒し、そのグループの誰も門の奥にいないまま 30 秒たったら、次の周へ
    private static void tickReset(ServerLevel level) {
        for (UUID group : GroupProgress.idleGroups(level)) {
            if (EmberBackState.of(level, group).hasProgress()) {
                resetGroup(level, group, false, null, false);
            }
        }
        BackGroupData data = BackGroupData.get(level);
        for (UUID group : data.ids()) {
            BackGroupData.Record r = data.peek(group);
            if (r == null || !r.isHeartDefeated()) {
                AFTER_HEART.remove(group);
                continue;
            }
            boolean inside = false;
            for (ServerPlayer p : Groups.online(level, group)) {
                if (!p.isSpectator() && pastGate(p.position())) {
                    inside = true;
                    break;
                }
            }
            int idle = inside ? 0 : AFTER_HEART.getOrDefault(group, 0) + 10;
            if (idle >= RESET_AFTER_HEART) {
                AFTER_HEART.remove(group);
                resetGroup(level, group, true, null, false);
            } else {
                AFTER_HEART.put(group, idle);
            }
        }
    }

    // そのグループの試練を最初に戻す (7.1節)。そのグループの試練の中の人は広間へ (熱は 0)、試練の番号が進む (古い休み場の記録は無効)。
    // そのグループが持っている部屋は手放して最初に戻す。ほかのグループの進み・部屋・人には触らない。2 周目からは一つ静かにする
    public static void resetGroup(ServerLevel level, UUID group, boolean announce, @Nullable EmberBackState.Trial forceSleep, boolean noSleep) {
        // そのグループの試練が元に戻る = 鼓動が戻る。眠っている巨神は、最短の長さを過ぎていれば目覚める (titan/end)
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.EMBER);
        GATE_TIMERS.remove(group);
        AFTER_HEART.remove(group);
        for (ServerPlayer p : Groups.online(level, group)) {
            if (!p.isSpectator() && inTrial(p.position())) {
                toHub(p);
            }
        }
        // 部屋 (戦いの部屋も) を先に戻す。戦いの放棄は扉をそのグループに開け直すので、そのあとで扉の上書きを記録ごと消す
        EmberRooms.releaseHeldBy(level, group);
        resetRecord(level, group, forceSleep, noSleep);
        for (ServerPlayer p : Groups.online(level, group)) {
            jp.revati.skyabyss.titan.ember.back.trial.EmberTrials.strip(p, jp.revati.skyabyss.titan.ember.back.trial.EmberTrials.allTrialItems());
        }
        EmberBackState state = EmberBackState.of(level, group);
        if (announce) {
            for (ServerPlayer p : Groups.online(level, group)) {
                Notice.narrate(p, EmberIds.LANG_BACK + "trials_renewed");
                tellQuiet(p, state);
            }
        }
        SkyAbyss.LOGGER.info("Ember back trials reset for group {} (quiet: {}, seed {})", group,
                state.quiet() == null ? "none" : state.quiet().id(), state.seed());
    }

    private static void resetRecord(ServerLevel level, UUID group, @Nullable EmberBackState.Trial forceSleep, boolean noSleep) {
        GroupProgress.reset(level, group);
        EmberBackState.of(level, group).afterReset(level.random, forceSleep, noSleep);
        GroupOverlays.refresh(level, group);
    }

    // すべてのグループの試練を最初に戻す (コマンド)
    public static void resetAll(ServerLevel level, boolean announce) {
        for (UUID group : BackGroupData.get(level).ids()) {
            resetGroup(level, group, announce, null, false);
        }
        EmberRooms.releaseAll(level);
    }

    // 静かな試練をコマンドで決める (そのグループの今の周に効かせる)。そのグループが持っている部屋は作り直す
    public static void setQuiet(ServerLevel level, UUID group, @Nullable EmberBackState.Trial t) {
        EmberBackState state = EmberBackState.of(level, group);
        state.setQuiet(t);
        for (EmberRooms.Room r : EmberRooms.ALL) {
            if (group.equals(EmberRooms.owner(r))) {
                EmberBackRooms.resetRoom(level, r);
            }
        }
        GroupOverlays.refresh(level, group);
        checkAllRung(level, group);
    }

    static void tellQuiet(ServerPlayer p, EmberBackState state) {
        if (state.quiet() != null) {
            Notice.narrate(p, EmberIds.LANG_BACK + "quiet_" + state.quiet().id());
        }
    }

    // ---------------------------------------------------------------- ピースフル (7.4節): 喉の坂の上を封じる。口と見送りの段は歩ける
    // 封じる所は、いつも扉のブロックとして置いてあり、ワールド全体の既定 (開いている・閉じている) だけを変える
    static boolean peacefulSide(ServerPlayer p) {
        return p.getZ() < PEACEFUL_SEAL_Z;
    }

    private static void tickPeaceful(ServerLevel level, boolean peaceful) {
        int f = EmberBodyBuilder.throatFloor(PEACEFUL_SEAL_Z);
        List<BlockPos> seal = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = f + 1; y <= f + 5; y++) {
                BlockPos p = new BlockPos(x, y, PEACEFUL_SEAL_Z);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.is(ModBlocks.SEALED_GATE.get()) && s.getValue(SealedGateBlock.OPEN) == peaceful) {
                    seal.add(p);
                }
            }
        }
        if (!seal.isEmpty()) {
            GroupGates.setDefault(level, seal, !peaceful);
        }
        if (peaceful) {
            for (ServerPlayer p : List.copyOf(level.players())) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive()) {
                    continue;
                }
                if (!peacefulSide(p)) {
                    toArrival(p);
                    EmberHeat.reset(p);
                    Notice.narrate(p, "message.skyabyss.trials_peaceful");
                } else if (p.tickCount % 40 < 10 && Math.abs(p.getZ() - PEACEFUL_SEAL_Z) < 5) {
                    Notice.bar(p, ModText.lore(EmberIds.LANG_BACK + "peaceful"));
                }
            }
        }
    }

    // ---------------------------------------------------------------- 倒れたとき (7.3節)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel()) || event.isCanceled()) {
            return;
        }
        persisted(player).putBoolean(TAG_DIED, true);
        TitanBackTrials.keepBelongings(player);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        CompoundTag tag = persisted(player);
        if (!tag.getBoolean(TAG_DIED)) {
            return;
        }
        tag.remove(TAG_DIED);
        ServerLevel back = player.server.getLevel(EmberIds.BACK);
        if (back == null) {
            return;
        }
        int epoch = EmberBackState.of(back, player).epoch();
        String cp = validCheckpoint(tag, epoch);
        BlockPos spawn = cp.isEmpty() ? ARRIVAL.above() : restSpawn(back, respawnCheckpoint(Groups.keyOf(player), cp));
        REVIVING.add(player.getUUID());
        try {
            player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
        } finally {
            REVIVING.remove(player.getUUID());
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
        jp.revati.skyabyss.titan.group.RespawnGuard.begin(player);
        Notice.narrate(player, cp.isEmpty() && tag.contains(TAG_CHECKPOINT) ? "message.skyabyss.rest_point_lost" : "message.skyabyss.revived_on_back");
        if (cp.isEmpty()) {
            clearCheckpoint(player);
        }
    }

    // 乗り込み・地上へ戻る (次元の移動) では休み場の記録を消す。背中で倒れて連れ戻すときだけは残す。乗り込んだ人には、眠っている試練を知らせる
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == EmberIds.BACK || event.getTo() == EmberIds.BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(EmberIds.BACK);
            if (event.getTo() == EmberIds.BACK && back != null) {
                EmberBackState state = EmberBackState.of(back, player);
                persisted(player).putInt(TAG_SEEN_EPOCH, state.epoch());
                tellQuiet(player, state);
            }
        }
    }

    // ログイン: 背中にいて、その間にそのグループの試練がリセットされていて、試練の中にいるなら到着点へ
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel())) {
            return;
        }
        int epoch = EmberBackState.of(player.serverLevel(), player).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
            if (player.isAlive() && inTrial(player.position())) {
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.rest_point_lost");
            }
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // 焚き火: しゃがんで触れると、試練をあきらめて到着点へ
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel())) {
            return;
        }
        if (player.serverLevel().getBlockState(event.getPos()).is(Blocks.SOUL_CAMPFIRE)) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                clearCheckpoint(player);
                toArrival(player);
                EmberHeat.reset(player);
                Notice.narrate(player, EmberIds.LANG_BACK + "gave_up");
            } else {
                Notice.bar(player, ModText.hint(EmberIds.LANG_BACK + "campfire_hint"));
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        GATE_TIMERS.clear();
        AFTER_HEART.clear();
        ON_PAD.clear();
        REVIVING.clear();
    }

    // テスト用
    static boolean gateOpening(UUID group) {
        return GATE_TIMERS.containsKey(group);
    }
}

package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scene;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.titan.TitanBacks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 初回と周回の共通の部品 (docs/design/21 の 1.7、10 の 10.10)。部屋そのものは各背中の拡張 (P8/P13/P17/P21) が建てて、ここへ登録する。
//
//  初回の定義   : そのグループの記録 (BackGroupData.Record) の `keep.first_cleared` が立っていない。心臓を止めた瞬間に立つ
//                 (TitanEnd.onHeartStopped → onFirstCleared)。"keep." の欄なので試練のリセットで消えず、パーティの記録の引き継ぎでは記録ごと写る
//  部屋の登録   : Room.firstRun(...)  初回だけ働く部屋・翼。first_cleared のグループには「既知の道」になる
//                   - 静かな通路: 敵を出さない (allowSpawn・allowSpawnAt を湧かせる側が見る)
//                   - 戸が開く: doors の封印の戸を、そのグループにだけ開ける (GroupGates。リセットで閉じても 1 秒ほどで開け直す)
//                   - 光る床: guide の床を、そのグループの画面にだけ光るブロックで見せる (GroupOverlays)
//                 Room.optional(...) 任意の翼。いつも開いている (既知の道にならない)。素材の報酬だけ繰り返し取れる (grantMaterials)
//  場面         : registerScene で場面を足し、playFirstScene で出す。人ごと (`cine_seen.<名前>`)。見ていない人だけに出る (スキップできる)。
//                 グループが周回でも、初めての人には出る (道はグループ、場面と宝は人ごと)
//  追憶の宝     : grantKeepsake(人, id, 品)。人ごとに一度きり。備えの品・素材・小さな装飾だけ (巨神の装備は渡さない)
//  素材の報酬   : grantMaterials(人, 品)。何度でも
public final class FirstRun {
    // 人ごとの場面の記録は CinematicState (プレイヤーの Data Attachment) に、この頭をつけて持つ
    public static final String SEEN_PREFIX = "cine_seen.";

    private static final Map<String, Room> ROOMS = new LinkedHashMap<>();
    private static final BlockState DEFAULT_GUIDE = Blocks.SEA_LANTERN.defaultBlockState();

    static {
        GroupOverlays.register(FirstRun::guideOverlay);
    }

    private FirstRun() {
    }

    // ---------------------------------------------------------------- 部屋の登録

    public static final class Room {
        public final String id;
        public final ResourceKey<Level> dimension;
        public final AABB box;
        // false = 任意の翼 (既知の道にならない)
        public final boolean firstRunOnly;
        private final List<BlockPos> doors = new ArrayList<>();
        private final List<BlockPos> guide = new ArrayList<>();
        private BlockState guideState = DEFAULT_GUIDE;

        private Room(String id, ResourceKey<Level> dimension, AABB box, boolean firstRunOnly) {
            this.id = id;
            this.dimension = dimension;
            this.box = box;
            this.firstRunOnly = firstRunOnly;
        }

        // 初回だけ働く部屋・翼 (周回では静かな通路になる)
        public static Room firstRun(String id, ResourceKey<Level> dimension, AABB box) {
            return new Room(id, dimension, box, true);
        }

        // 任意の翼 (初回も周回もいつも開いている。敵・仕掛けはそのまま)
        public static Room optional(String id, ResourceKey<Level> dimension, AABB box) {
            return new Room(id, dimension, box, false);
        }

        // 既知の道のとき開ける封印の戸 (SealedGateBlock。GroupGates の座標)
        public Room doors(Collection<BlockPos> blocks) {
            doors.addAll(blocks);
            return this;
        }

        // 既知の道のとき光らせる床 (歩く道すじの床のブロックの座標)
        public Room guide(Collection<BlockPos> blocks) {
            guide.addAll(blocks);
            return this;
        }

        public Room guideState(BlockState state) {
            this.guideState = state;
            return this;
        }

        public List<BlockPos> doorBlocks() {
            return List.copyOf(doors);
        }

        public List<BlockPos> guideBlocks() {
            return List.copyOf(guide);
        }
    }

    public static Room register(Room room) {
        ROOMS.put(room.id, room);
        return room;
    }

    public static void unregister(String id) {
        ROOMS.remove(id);
    }

    @Nullable
    public static Room room(String id) {
        return ROOMS.get(id);
    }

    @Nullable
    public static Room roomAt(ServerLevel level, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        for (Room room : ROOMS.values()) {
            if (room.dimension == level.dimension() && room.box.contains(c)) {
                return room;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 初回か周回か

    // そのグループの、この背中での初回か (心臓を一度も止めていない)
    public static boolean isFirstRun(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return r == null || !r.isFirstCleared();
    }

    public static boolean isFirstRun(ServerPlayer player) {
        return player.level() instanceof ServerLevel level && isFirstRun(level, Groups.keyOf(player));
    }

    // そのグループが、巨神 titan (null ならどれでも) の心臓を一度でも止めたか。地上の語り部などが周回の言い回しを選ぶのに使う
    public static boolean hasCleared(MinecraftServer server, UUID group, @Nullable String titan) {
        for (String t : jp.revati.skyabyss.progress.Titans.all()) {
            if (titan != null && !titan.equals(t)) {
                continue;
            }
            var dim = jp.revati.skyabyss.progress.Titans.backOf(t);
            ServerLevel back = dim == null ? null : server.getLevel(dim);
            if (back != null && !isFirstRun(back, group)) {
                return true;
            }
        }
        return false;
    }

    // 部屋 room が、このグループにとって既知の道 (静かな通路) か。任意の翼は常に false
    public static boolean isKnownPath(ServerLevel back, UUID group, Room room) {
        return room.firstRunOnly && room.dimension == back.dimension() && !isFirstRun(back, group);
    }

    public static boolean isKnownPath(ServerLevel back, UUID group, String roomId) {
        Room room = ROOMS.get(roomId);
        return room != null && isKnownPath(back, group, room);
    }

    // 湧かせる側 (部屋の敵・仕掛け) が見る: この部屋でこのグループに敵を出してよいか。未登録の部屋・任意の翼は出す
    public static boolean allowSpawn(ServerLevel back, UUID group, String roomId) {
        return !isKnownPath(back, group, roomId);
    }

    // 座標版 (登録した部屋の中かで決める)
    public static boolean allowSpawnAt(ServerLevel back, UUID group, BlockPos pos) {
        Room room = roomAt(back, pos);
        return room == null || !isKnownPath(back, group, room);
    }

    // 心臓を止めた: 戦った人 (と同じパーティ) のグループに first_cleared を立てる。TitanEnd.onHeartStopped から
    public static void onFirstCleared(ServerLevel back, Collection<UUID> fighters) {
        MinecraftServer server = back.getServer();
        BackGroupData data = BackGroupData.get(back);
        for (UUID id : fighters) {
            UUID group = Groups.keyOf(server, id);
            BackGroupData.Record r = data.of(group);
            if (!r.isFirstCleared()) {
                r.markFirstCleared();
                SkyAbyss.LOGGER.info("Group {} finished its first run of {}", group, back.dimension().location());
                syncDoors(back, group);
                GroupOverlays.refresh(back, group);
            }
        }
    }

    // ---------------------------------------------------------------- 既知の道 (戸・光る床)

    // 既知の道の戸を、そのグループにだけ開ける。変わったら true。初回の間は何もしない (戸は部屋の側の決まり)
    public static boolean syncDoors(ServerLevel back, UUID group) {
        boolean changed = false;
        for (Room room : ROOMS.values()) {
            if (isKnownPath(back, group, room) && !room.doors.isEmpty()) {
                changed |= GroupGates.open(back, group, room.doors);
            }
        }
        return changed;
    }

    // 光る床 (見かけだけ。そのグループの画面に出る)
    private static void guideOverlay(ServerLevel back, UUID group, BiConsumer<BlockPos, BlockState> out) {
        for (Room room : ROOMS.values()) {
            if (isKnownPath(back, group, room)) {
                for (BlockPos p : room.guide) {
                    out.accept(p, room.guideState);
                }
            }
        }
    }

    // 1 秒ごと: 背中にいるグループの戸を合わせる (試練のリセットで上書きが消えても、既知の道の戸は開いたまま)
    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        @SubscribeEvent
        static void onLevelTick(LevelTickEvent.Post event) {
            if (ROOMS.isEmpty() || !(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 7
                    || !TitanBacks.isBack(level.dimension())) {
                return;
            }
            Set<UUID> seen = new HashSet<>();
            for (ServerPlayer p : level.players()) {
                UUID group = Groups.keyOf(p);
                if (!p.isSpectator() && seen.add(group)) {
                    syncDoors(level, group);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 場面

    public static String seenKey(String name) {
        return SEEN_PREFIX + name;
    }

    // 初回の場面を足す (場面の定義は Scenes と同じ。名前 = Scene の id)。同じ名前は置き換える
    public static void registerScene(Scene scene) {
        Scenes.register(scene);
    }

    // この人がその場面を見たか (`cine_seen.<名前>`)
    public static boolean sceneSeen(ServerPlayer player, String name) {
        return Cinematics.state(player).seen(seenKey(name));
    }

    // 初回の場面を出す枠。見ていない人にだけ出す (戻りは出したか)。場面が未登録なら何もしない (見たことにもしない)。
    // 出した時点で `cine_seen.<名前>` を記録する (途中で落ちても繰り返さない)。スキップは場面の仕組みのとおり
    public static boolean playFirstScene(ServerPlayer player, String name, @Nullable Entity target, @Nullable Vec3 faceToward) {
        if (Scenes.get(name) == null || sceneSeen(player, name)) {
            return false;
        }
        if (!Cinematics.start(player, name, target, faceToward)) {
            return false;
        }
        Cinematics.state(player).markSeen(seenKey(name));
        return true;
    }

    // 場面が終わったとき (スキップを含む) の効果を足す (Cinematics.onEnd と同じ)
    public static void onSceneEnd(String name, BiConsumer<ServerPlayer, Boolean> listener) {
        Cinematics.onEnd(name, listener);
    }

    // ---------------------------------------------------------------- 宝

    // 追憶の宝に入れてよい品か: 巨神の装備 (ModGear・武器・防具) は入れない。備えの品・素材・小さな装飾だけ
    public static boolean keepsakeAllowed(ItemStack stack) {
        var item = stack.getItem();
        return !stack.isEmpty() && !(item instanceof ModGear) && !(item instanceof TieredItem) && !(item instanceof ArmorItem);
    }

    public static boolean hasKeepsake(ServerPlayer player, String id) {
        return FirstRunData.get(player.server).hasKeepsake(player.getUUID(), id);
    }

    // 追憶の宝を渡す。その人が初めてなら渡して true (人ごとに一度きり。グループの初回・周回には関わらない)。
    // 受け取り済み・装備が混ざっているときは何も渡さず false
    public static boolean grantKeepsake(ServerPlayer player, String id, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (!keepsakeAllowed(s)) {
                SkyAbyss.LOGGER.error("Keepsake {} refused: {} is gear or empty", id, s);
                return false;
            }
        }
        if (!FirstRunData.get(player.server).markKeepsake(player.getUUID(), id)) {
            return false;
        }
        give(player, stacks);
        return true;
    }

    // 任意の翼の素材の報酬。何度でも渡す
    public static void grantMaterials(ServerPlayer player, List<ItemStack> stacks) {
        give(player, stacks);
    }

    private static void give(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            jp.revati.skyabyss.pouch.PouchRouting.placeBack(player, s.copy());
        }
    }
}

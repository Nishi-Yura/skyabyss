package jp.revati.skyabyss.titan.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 背中の「野営の道具」(docs/design/08 の背中の決まり)。サバイバルでもブロックは置けない・壊せないが、
// 焚き火・たいまつ・灯り・箱 (チェスト・樽) の決まった道具だけは、野営として置いてよい。
// 置いた道具は持ち主の持ち物の「貸し」で、持ち主が背中を出る (運び出し・倒れる・落ちる・ログアウト・次元の移動・眠り) と、
// 中身ごと手もとへ戻る (入りきらない分・オフラインの人の分は報酬の預かり TitanLoot.handOver へ)。
// 置けない場所 (見張り・仕掛けの邪魔・近道・ボスの部屋) は refusal で決まる。記録は ServerPlayer の持ち主の UUID・次元・位置・ブロック
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CampBlocks {
    // 一人が一つの背中に置ける数
    public static final int LIMIT = 8;
    // 仕掛けのブロックからこの距離 (立方体) 以内には置けない
    public static final int MECHANISM_RADIUS = 2;
    // 戦いの部屋・アスレの水平の余白
    private static final double ARENA_MARGIN = 1.5;
    private static final String NAME = "skyabyss_camp_blocks";
    private static final SavedData.Factory<CampData> FACTORY = new SavedData.Factory<>(CampData::new, CampData::load);

    private CampBlocks() {
    }

    public enum Refusal {
        NOT_CAMP(null), STRANGER_ONLY(null), LIMIT("message.skyabyss.camp.limit"), MECHANISM("message.skyabyss.camp.mechanism"),
        ARENA("message.skyabyss.camp.arena"), SUPPORT("message.skyabyss.camp.support");

        public final String lang;

        Refusal(String lang) {
            this.lang = lang;
        }
    }

    public record Entry(UUID owner, ResourceKey<Level> dimension, BlockPos pos, String block) {
    }

    // ---------------------------------------------------------------- 保存
    public static final class CampData extends SavedData {
        final List<Entry> entries = new ArrayList<>();

        CampData() {
        }

        static CampData load(CompoundTag tag, HolderLookup.Provider registries) {
            CampData data = new CampData();
            for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                ResourceLocation dim = ResourceLocation.tryParse(c.getString("dim"));
                if (dim == null || !c.hasUUID("owner")) {
                    continue;
                }
                data.entries.add(new Entry(c.getUUID("owner"), ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim),
                        BlockPos.of(c.getLong("pos")), c.getString("block")));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Entry e : entries) {
                CompoundTag c = new CompoundTag();
                c.putUUID("owner", e.owner());
                c.putString("dim", e.dimension().location().toString());
                c.putLong("pos", e.pos().asLong());
                c.putString("block", e.block());
                list.add(c);
            }
            tag.put("entries", list);
            return tag;
        }
    }

    public static CampData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static List<Entry> entries(MinecraftServer server) {
        return List.copyOf(data(server).entries);
    }

    @Nullable
    public static Entry at(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        for (Entry e : data(server).entries) {
            if (e.pos().equals(pos) && e.dimension().equals(dimension)) {
                return e;
            }
        }
        return null;
    }

    public static int count(MinecraftServer server, UUID owner, ResourceKey<Level> dimension) {
        int n = 0;
        for (Entry e : data(server).entries) {
            if (e.owner().equals(owner) && e.dimension().equals(dimension)) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 置ける道具
    public static boolean isCamp(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE
                || b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH
                || b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN || b == Blocks.CHEST || b == Blocks.BARREL;
    }

    private static boolean isWallTorch(BlockState s) {
        return s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH);
    }

    // 仕掛けのブロック: この Mod のブロック (扉・碑・炉・灯・足場など) と、バニラの仕掛け (感圧板・扉・レバー・レッドストーンなど)
    public static boolean isMechanism(BlockState s) {
        if (isCamp(s)) {
            return false;
        }
        Block b = s.getBlock();
        if (SkyAbyss.MODID.equals(BuiltInRegistries.BLOCK.getKey(b).getNamespace())) {
            return true;
        }
        return s.is(BlockTags.PRESSURE_PLATES) || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.BUTTONS)
                || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.RAILS) || s.is(BlockTags.BEDS) || s.is(BlockTags.CAULDRONS)
                || b == Blocks.LEVER || b == Blocks.TRIPWIRE || b == Blocks.TRIPWIRE_HOOK || b == Blocks.REDSTONE_WIRE
                || b == Blocks.REDSTONE_TORCH || b == Blocks.REDSTONE_WALL_TORCH || b == Blocks.REPEATER || b == Blocks.COMPARATOR
                || b == Blocks.OBSERVER || b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.PISTON_HEAD
                || b == Blocks.DISPENSER || b == Blocks.DROPPER || b == Blocks.HOPPER || b == Blocks.TARGET || b == Blocks.BELL
                || b == Blocks.LECTERN || b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER || b == Blocks.VAULT || b == Blocks.BEACON
                || b == Blocks.LIGHTNING_ROD || b == Blocks.END_PORTAL_FRAME || b == Blocks.END_PORTAL || b == Blocks.END_GATEWAY
                || b == Blocks.NETHER_PORTAL || b == Blocks.COMMAND_BLOCK || b == Blocks.CHAIN_COMMAND_BLOCK || b == Blocks.REPEATING_COMMAND_BLOCK
                || b == Blocks.STRUCTURE_BLOCK || b == Blocks.JIGSAW || b == Blocks.BARRIER || b == Blocks.NOTE_BLOCK
                || b == Blocks.SCULK_SENSOR || b == Blocks.CALIBRATED_SCULK_SENSOR || b == Blocks.SCULK_SHRIEKER || b == Blocks.TNT
                || b == Blocks.SLIME_BLOCK || b == Blocks.HONEY_BLOCK || b == Blocks.POWDER_SNOW || b == Blocks.BUBBLE_COLUMN;
    }

    // 戦いの部屋・試練の足場 (置いた道具が足場や盾になるのを避ける)。水平に ARENA_MARGIN だけ広げる
    private static List<AABB> arenas(ResourceKey<Level> dim) {
        List<AABB> list = new ArrayList<>();
        if (dim == TitanTravel.TITAN_BACK) {
            // 心臓の間・番兵の広間・アスレの谷と足場
            for (TitanBackLayout.Box b : List.of(TitanBackLayout.HEART, TitanBackLayout.E1, TitanBackLayout.E3, TitanBackLayout.CE1,
                    TitanBackLayout.E3_CORRIDOR, TitanBackLayout.WA, TitanBackLayout.WB, TitanBackLayout.WC, TitanBackLayout.W1, TitanBackLayout.W2)) {
                list.add(new AABB(b.x1(), b.floorY() - 12, b.z1(), b.x2() + 1, b.ceilingY(), b.z2() + 1));
            }
        } else if (dim == TideTravel.TIDE_BACK) {
            list.add(TideBackLayout.HEART_HALL.aabb().expandTowards(0, -2, 0));
            list.add(TideBackLayout.HEART_REST.aabb().expandTowards(0, -2, 0));
        } else if (dim == StormTravel.STORM_BACK) {
            BlockPos c = StormBackLayout.HEART_ROOM;
            int r = StormBackLayout.HEART_ROOM_RADIUS;
            list.add(new AABB(c.getX() - r, c.getY() - 3, c.getZ() - r, c.getX() + r + 1, StormBackLayout.HEART_ROOM_CEILING + 1, c.getZ() + r + 1));
            list.add(StormBackLayout.HEART_DOOR.inflate(3));
        } else if (dim == EmberIds.BACK) {
            for (EmberBackLayout.Box b : List.of(EmberBackLayout.HEART, EmberBackLayout.HEART_ANTE)) {
                list.add(new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1));
            }
        }
        List<AABB> grown = new ArrayList<>();
        for (AABB a : list) {
            grown.add(a.inflate(ARENA_MARGIN, 0, ARENA_MARGIN));
        }
        return grown;
    }

    // 背の次元の区画 (レイドの部屋・中ボスの間・心臓の間とその前室。docs/design/32 の 2.4)。どれも閉じた戦いの部屋なので、野営の道具は置けない
    // (人が野営できるのは到着の台だけ)
    public static boolean inInstanceRooms(ResourceKey<Level> dim, BlockPos pos) {
        boolean back = dim == TitanTravel.TITAN_BACK || dim == TideTravel.TIDE_BACK || dim == StormTravel.STORM_BACK || dim == EmberIds.BACK;
        return back && jp.revati.skyabyss.trial.TrialSlot.inInstance(pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean inArena(ResourceKey<Level> dim, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        for (AABB a : arenas(dim)) {
            if (a.contains(c)) {
                return true;
            }
        }
        return false;
    }

    // 置いた道具を支えているブロックの位置
    private static BlockPos supportOf(BlockState placed, BlockPos pos) {
        if (isWallTorch(placed) && placed.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            return pos.relative(placed.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getOpposite());
        }
        if ((placed.is(Blocks.LANTERN) || placed.is(Blocks.SOUL_LANTERN)) && placed.getValue(BlockStateProperties.HANGING)) {
            return pos.above();
        }
        return pos.below();
    }

    private static Direction supportFace(BlockState placed, BlockPos pos, BlockPos support) {
        // 支えるブロックの、道具へ向いた面
        for (Direction d : Direction.values()) {
            if (pos.relative(d).equals(support)) {
                return d.getOpposite();
            }
        }
        return Direction.UP;
    }

    // 置いていいか。置けるなら null、置けない理由はその値 (NOT_CAMP は「野営の道具ではない」)
    @Nullable
    public static Refusal evaluate(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState placed) {
        if (!isCamp(placed)) {
            return Refusal.NOT_CAMP;
        }
        ResourceKey<Level> dim = level.dimension();
        if (inArena(dim, pos) || inArena(dim, player.blockPosition()) || inInstanceRooms(dim, pos) || inInstanceRooms(dim, player.blockPosition())) {
            return Refusal.ARENA;
        }
        // 支え: 背中のブロック (道具どうしを積んで足場にさせない)。吊りランタン・壁たいまつはそれぞれの向きで
        BlockPos support = supportOf(placed, pos);
        BlockState below = level.getBlockState(support);
        if (below.isAir() || !below.getFluidState().isEmpty() || isCamp(below) || at(level.getServer(), dim, support) != null
                || !below.isFaceSturdy(level, support, supportFace(placed, pos, support))) {
            return Refusal.SUPPORT;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-MECHANISM_RADIUS, -MECHANISM_RADIUS, -MECHANISM_RADIUS),
                pos.offset(MECHANISM_RADIUS, MECHANISM_RADIUS, MECHANISM_RADIUS))) {
            if (isMechanism(level.getBlockState(p))) {
                return Refusal.MECHANISM;
            }
        }
        if (count(level.getServer(), player.getUUID(), dim) >= LIMIT) {
            return Refusal.LIMIT;
        }
        return null;
    }

    // 記録して置く (evaluate が null のあと)
    public static void record(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState placed) {
        CampData d = data(level.getServer());
        d.entries.add(new Entry(player.getUUID(), level.dimension(), pos.immutable(), BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString()));
        d.setDirty();
    }

    // ---------------------------------------------------------------- 壊す・戻す
    // 持ち主と同じグループ (パーティ) の人か
    public static boolean mayBreak(ServerPlayer player, Entry e) {
        return e.owner().equals(player.getUUID()) || Groups.keyOf(player.server, e.owner()).equals(Groups.keyOf(player));
    }

    // 道具を取り外す: ブロックを消して、本体と中身を返す (ブロックがもう無ければ空)
    private static List<ItemStack> takeApart(ServerLevel level, Entry e, boolean withBlockItem) {
        List<ItemStack> out = new ArrayList<>();
        BlockPos pos = e.pos();
        BlockState state = level.getBlockState(pos);
        if (!isCamp(state)) {
            return out;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CampfireBlockEntity fire) {
            var items = fire.getItems();
            for (int i = 0; i < items.size(); i++) {
                if (!items.get(i).isEmpty()) {
                    out.add(items.get(i).copy());
                    items.set(i, ItemStack.EMPTY);
                }
            }
        } else if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) {
                    out.add(s.copy());
                }
            }
            c.clearContent();
        }
        if (withBlockItem) {
            ItemStack item = new ItemStack(state.getBlock().asItem());
            if (!item.isEmpty()) {
                out.add(0, item);
            }
        }
        level.removeBlock(pos, false);
        return out;
    }

    private static void forget(MinecraftServer server, Entry e) {
        CampData d = data(server);
        if (d.entries.remove(e)) {
            d.setDirty();
        }
    }

    // 持ち主が (同じグループの人でも) 壊した: 本体と中身を、壊した人の手もとへ。入りきらない分は持ち主の預かりへ
    public static void breakByPlayer(ServerPlayer player, ServerLevel level, Entry e) {
        List<ItemStack> items = takeApart(level, e, true);
        forget(level.getServer(), e);
        giveOrKeep(level.getServer(), player.getUUID(), player, items);
    }

    // 戻す: 持ち主が今ここにいれば手もとへ、いなければ (ログアウト・倒れている) 預かりへ
    private static void giveOrKeep(MinecraftServer server, UUID receiverId, @Nullable ServerPlayer receiver, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        int held = TitanLoot.handOver(server, receiverId, receiver, items, 0);
        if (receiver != null && TitanLoot.canReceive(receiver)) {
            Notice.hint(receiver, "message.skyabyss.camp.returned", items.stream().mapToInt(ItemStack::getCount).sum());
            if (held > 0) {
                TitanLoot.tellHeld(receiver, held);
            }
        }
    }

    // 持ち主の、あるいは (dimension が null でなければ) その背中の分を、すべて戻す。戻した個数を返す
    public static int returnAll(MinecraftServer server, UUID owner, @Nullable ResourceKey<Level> dimension) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        return returnAll(server, owner, player, dimension);
    }

    public static int returnAll(MinecraftServer server, UUID owner, @Nullable ServerPlayer player, @Nullable ResourceKey<Level> dimension) {
        int n = 0;
        List<ItemStack> all = new ArrayList<>();
        for (Entry e : List.copyOf(data(server).entries)) {
            if (!e.owner().equals(owner) || dimension != null && !e.dimension().equals(dimension)) {
                continue;
            }
            ServerLevel level = server.getLevel(e.dimension());
            if (level != null) {
                all.addAll(takeApart(level, e, true));
            }
            forget(server, e);
            n++;
        }
        // 立ち去った人の、置いた時のブロックの分の本体が消えていた (金床が壊れたなど) 場合は、本体だけ無いので何も返らない
        giveOrKeep(server, owner, player, mergeStacks(all));
        return n;
    }

    // ある背中の道具をすべて (持ち主ごとに) 戻す。背中の作り直し・巨神が眠るとき
    public static int returnAllIn(MinecraftServer server, ResourceKey<Level> dimension) {
        Set<UUID> owners = new HashSet<>();
        for (Entry e : data(server).entries) {
            if (e.dimension().equals(dimension)) {
                owners.add(e.owner());
            }
        }
        int n = 0;
        for (UUID owner : owners) {
            n += returnAll(server, owner, dimension);
        }
        return n;
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>();
        outer:
        for (ItemStack s : in) {
            for (ItemStack o : out) {
                if (ItemStack.isSameItemSameComponents(o, s) && o.getCount() < o.getMaxStackSize()) {
                    int room = Math.min(s.getCount(), o.getMaxStackSize() - o.getCount());
                    o.grow(room);
                    s = s.copyWithCount(s.getCount() - room);
                    if (s.isEmpty()) {
                        continue outer;
                    }
                }
            }
            out.add(s.copy());
        }
        return out;
    }

    // 一人の持ち主が「この背中にいる」か (生きていて、同じ次元)
    private static boolean inside(@Nullable ServerPlayer player, ResourceKey<Level> dim) {
        return player != null && player.isAlive() && !player.hasDisconnected() && player.level().dimension().equals(dim);
    }

    // 1 秒ごとの見回り: 持ち主が背中にいない (出た・倒れた・オフライン) 道具は戻す。道具でなくなったブロック (金床が壊れたなど) の記録は消す
    public static void sweep(MinecraftServer server) {
        Set<UUID> leaving = new HashSet<>();
        Set<ResourceKey<Level>> dims = new HashSet<>();
        for (Entry e : List.copyOf(data(server).entries)) {
            ServerLevel level = server.getLevel(e.dimension());
            if (level == null || !TitanBacks.isBack(e.dimension())) {
                forget(server, e);
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(e.owner());
            if (!inside(owner, e.dimension())) {
                leaving.add(e.owner());
                dims.add(e.dimension());
                continue;
            }
            if (level.hasChunkAt(e.pos()) && !isCamp(level.getBlockState(e.pos()))) {
                forget(server, e);
            }
        }
        for (UUID owner : leaving) {
            for (ResourceKey<Level> dim : dims) {
                returnAll(server, owner, dim);
            }
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 7) {
            sweep(server);
        }
    }

    // ログアウト: プレイヤーの保存より前に来るので、持ち物へ戻しておけば保存される
    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            returnAll(player.server, player.getUUID(), player, null);
        }
    }

    // 次元の移動 (運び出し・降りる・落ちる)。見回りを待たずに戻す
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TitanBacks.isBack(event.getFrom())) {
            returnAll(player.server, player.getUUID(), player, event.getFrom());
        }
    }

    // サーバーを止めるとき: 背中は次の起動で建て直されることがあるので、全部戻す (オフラインの分は預かりへ)
    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        Set<UUID> owners = new HashSet<>();
        for (Entry e : data(server).entries) {
            owners.add(e.owner());
        }
        for (UUID owner : owners) {
            returnAll(server, owner, null);
        }
    }

    // ---------------------------------------------------------------- 画面・同期
    public static void refuse(ServerPlayer player, Refusal why) {
        Notice.bar(player, ModText.deny(why.lang != null ? why.lang : "message.skyabyss.titan_refuses"));
    }

    // 置けなかった (または壊せなかった) あと: 持ち物を丸ごと送り直し、置いた場所の見かけをサーバーの状態へ戻す。
    // クライアントは「置いた」と予測して先に手持ちを減らしており、サーバーは数を元に戻すだけで送り直さないので、
    // 送らないと手持ちが消えたように見えたままになる (見えない箱を置いたような残像も)
    public static void resync(ServerPlayer player, Level level, Iterable<BlockPos> positions) {
        try {
            player.containerMenu.sendAllDataToRemote();
            if (player.containerMenu != player.inventoryMenu) {
                player.inventoryMenu.sendAllDataToRemote();
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.warn("[camp] could not resend the inventory to {}", player.getGameProfile().getName(), e);
        }
        Set<BlockPos> sent = new HashSet<>();
        for (BlockPos p : positions) {
            for (Direction d : new Direction[] { null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
                BlockPos q = d == null ? p.immutable() : p.relative(d);
                if (sent.add(q)) {
                    player.connection.send(new ClientboundBlockUpdatePacket(level, q));
                }
            }
        }
    }
}

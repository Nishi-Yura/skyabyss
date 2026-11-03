package jp.revati.skyabyss.endgame;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.LootBoxes;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.util.DayClock;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.RoadClearance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 巡り路の野営地 (2026-11-03 の声「wynncraft のように、それぞれのバイオームのとある場所でルートラン開始できるようにして。計 4 つ、景品も違う、
// 3 日に一回景品の一覧が変わる」「終わった後にシネマティックでどんなアイテム出るか」「その日の景品一覧からランダムに。神器は超低確率」。docs/design/54 の 3.1.1)。
//   置き場  data/skyabyss/continent/camps.json (tools/gen/world/gen_camps.py が町から少し離れた、道の見える平らな所を選ぶ)。鯨見・汐守・風待・灰炉に一つずつ
//   建てる  人が BUILD_RADIUS まで来たら建てる (幕屋・焚き火・帆柱と旗・木箱・灯り・報いの箱)。建った印は SiteData の KIND。遠くからも見えるよう、光の柱 (その人にだけ)
//   箱      報いの箱を押すと、野営地の画面 (今の景品の一覧・入れ替えまでの時間・今日の残り・歩き出す)。始まりは野営地から (近くの組の仲間も一緒)
//   景品    野営地ごとの品の表 (その土地の巨神の品・その土地の目安の帯の階級の品・神器)。PERIOD_DAYS ごとに表を引き直す (野営地と期の番号で決まる。誰が見ても同じ)
//   引き    終わると、印 1 つにつき 1 回 (呪いの柱 1 本につき +1)、表から引く。階級の重み WEIGHTS (神器は 0.1%)。引いた品は旅嚢へ入れ、
//           画面の幕 (client/endgame/CircuitRewardScreen) で一つずつ見せる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CircuitCamps {
    public static final String KIND = "circuit_camp";
    public static final int PERIOD_DAYS = 3;
    static final int BUILD_RADIUS = 72;
    static final int BEAM_RANGE = 220;
    // 箱から、始められる所・画面を開ける所
    public static final double NEAR = 14;
    public static final int MAX_PULLS = 24;
    // 表の中身の数 (階級ごと) と、引きの重み (1 万分率)。神器は表に一つ、引きは 0.1%
    public static final GearTier[] TIERS = {GearTier.UNCOMMON, GearTier.RARE, GearTier.EPIC, GearTier.HEROIC, GearTier.ARTIFACT};
    static final int[] COUNTS = {4, 4, 3, 2, 1};
    public static final int[] WEIGHTS = {6170, 2700, 860, 260, 10};

    public record Camp(int index, String id, int region, int x, int z) {
        public String titan() {
            return Circuits.TITAN[Math.max(0, Math.min(Circuits.TITAN.length - 1, region))];
        }
    }

    private static List<Camp> all;

    private CircuitCamps() {
    }

    public static synchronized List<Camp> all() {
        if (all == null) {
            List<Camp> out = new ArrayList<>();
            try (InputStream in = CircuitCamps.class.getResourceAsStream("/data/skyabyss/continent/camps.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (JsonElement e : o.getAsJsonArray("camps")) {
                        JsonObject c = e.getAsJsonObject();
                        out.add(new Camp(out.size(), c.get("id").getAsString(), c.get("region").getAsInt(), c.get("x").getAsInt(), c.get("z").getAsInt()));
                    }
                }
            } catch (Exception ex) {
                SkyAbyss.LOGGER.warn("Circuit camps could not be read: {}", ex.toString());
            }
            all = List.copyOf(out);
        }
        return all;
    }

    @Nullable
    public static Camp byId(String id) {
        for (Camp c : all()) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    @Nullable
    public static Camp byRegion(int region) {
        for (Camp c : all()) {
            if (c.region() == region) {
                return c;
            }
        }
        return null;
    }

    // 箱 (建っていなければ予定の所) のそば NEAR の中の野営地
    @Nullable
    public static Camp near(ServerLevel level, BlockPos at) {
        SiteData data = SiteData.get(level);
        for (Camp c : all()) {
            BlockPos chest = chest(data, c);
            if (Math.hypot(at.getX() - chest.getX(), at.getZ() - chest.getZ()) <= NEAR && Math.abs(at.getY() - chest.getY()) <= 8) {
                return c;
            }
        }
        return null;
    }

    static BlockPos chest(SiteData data, Camp c) {
        SiteData.Entry e = entry(data, c.index());
        if (e != null) {
            return e.pos;
        }
        return new BlockPos(c.x(), ContinentMap.get().surface(c.x(), c.z()) + 1, c.z());
    }

    @Nullable
    static SiteData.Entry entry(SiteData data, int index) {
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.index == index) {
                return e;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 景品の表 (PERIOD_DAYS ごと)
    public static long period() {
        return Math.floorDiv(DayClock.epochDay(), PERIOD_DAYS);
    }

    // 次の入れ替えまでの tick
    public static long ticksUntilRotation() {
        long into = Math.floorMod(DayClock.epochDay(), PERIOD_DAYS);
        return (PERIOD_DAYS - 1 - into) * 24L * 3600L * 20L + DayClock.ticksUntilNextDay();
    }

    // その野営地・その期の表 (階級の低い順)。野営地と期の番号から決まる
    public static List<Item> pool(Camp c, long period) {
        Random r = new Random(c.id().hashCode() * 1_000_003L + period * 7919L);
        int band = jp.revati.skyabyss.item.wild.WildGearTypes.band(Circuits.TOP_LEVEL[Math.max(0, Math.min(Circuits.TOP_LEVEL.length - 1, c.region()))]);
        BoxKind titanBox = BoxKind.titan(c.titan());
        List<Item> titanPool = titanBox == null ? List.of() : LootBoxes.pool(titanBox);
        List<Item> out = new ArrayList<>();
        for (int t = 0; t < TIERS.length; t++) {
            GearTier tier = TIERS[t];
            List<Item> from = new ArrayList<>();
            if (tier == GearTier.ARTIFACT) {
                BoxKind a = BoxKind.byId("box_artifact");
                if (a != null) {
                    from.addAll(LootBoxes.pool(a));
                }
            } else {
                BoxKind k = BoxKind.rarity(tier, band);
                if (k != null) {
                    from.addAll(LootBoxes.pool(k));
                }
                for (Item i : titanPool) {
                    if (i instanceof ModGear g && g.tier() == tier) {
                        from.add(i);
                    }
                }
            }
            from = new ArrayList<>(new java.util.LinkedHashSet<>(from));
            from.sort(java.util.Comparator.comparing(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString()));
            Collections.shuffle(from, r);
            out.addAll(from.subList(0, Math.min(COUNTS[t], from.size())));
        }
        return out;
    }

    static GearTier tierOf(Item i) {
        return i instanceof ModGear g ? g.tier() : GearTier.COMMON;
    }

    // 表から一つ引く: 階級を重みで決め (表にない階級は外す)、その階級の品から一つ。特性を付ける
    public static ItemStack pull(List<Item> pool, RandomSource random) {
        int total = 0;
        int[] w = new int[TIERS.length];
        for (int t = 0; t < TIERS.length; t++) {
            GearTier tier = TIERS[t];
            if (pool.stream().anyMatch(i -> tierOf(i) == tier)) {
                w[t] = WEIGHTS[t];
                total += w[t];
            }
        }
        if (total <= 0 || pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int roll = random.nextInt(total);
        GearTier tier = TIERS[0];
        for (int t = 0; t < TIERS.length; t++) {
            if (roll < w[t]) {
                tier = TIERS[t];
                break;
            }
            roll -= w[t];
        }
        GearTier picked = tier;
        List<Item> of = pool.stream().filter(i -> tierOf(i) == picked).toList();
        ItemStack s = new ItemStack(of.get(random.nextInt(of.size())));
        return AffixRules.withAffixes(s, random);
    }

    // 巨神の品は図鑑に数える (封の箱を開けたときと同じ)
    static void record(ServerPlayer p, ItemStack gear) {
        if (gear.getItem() instanceof ModGear g && BoxKind.TITANS.contains(g.dungeon())) {
            TitanMemoryData m = TitanMemoryData.get(p.server);
            m.record(p.getUUID(), g.dungeon()).owned.add(TitanLoot.id(gear.getItem()));
            m.setDirty();
        }
    }

    // ---------------------------------------------------------------- 見回り (建てる・光の柱)
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7 || !RoadClearance.live()) {
            return;
        }
        ServerLevel over = server.overworld();
        SiteData data = SiteData.get(over);
        for (ServerPlayer p : over.players()) {
            if (p.isSpectator()) {
                continue;
            }
            for (Camp c : all()) {
                double d = Math.hypot(p.getX() - c.x(), p.getZ() - c.z());
                if (d > BEAM_RANGE) {
                    continue;
                }
                SiteData.Entry e = entry(data, c.index());
                if ((e == null || !e.built) && d <= BUILD_RADIUS && over.hasChunkAt(new BlockPos(c.x(), 0, c.z()))) {
                    build(over, data, c, e);
                    e = entry(data, c.index());
                }
                if (d > 10 && Circuits.runOf(p) == null) {
                    beam(p, e == null ? chest(data, c) : e.pos);
                }
            }
        }
    }

    // 遠くからも見える光の柱 (その人にだけ)
    static void beam(ServerPlayer p, BlockPos at) {
        ServerLevel level = p.serverLevel();
        for (int y = 3; y < 46; y += 3) {
            level.sendParticles(p, ParticleTypes.END_ROD, true, at.getX() + 0.5, at.getY() + y, at.getZ() + 0.5, 1, 0.05, 0.6, 0.05, 0.0);
        }
        level.sendParticles(p, ParticleTypes.WAX_ON, true, at.getX() + 0.5, at.getY() + 46, at.getZ() + 0.5, 3, 0.4, 0.3, 0.4, 0.0);
    }

    // ---------------------------------------------------------------- 建てる
    // 中心 (報いの箱) のまわり半径 7: 土を均して道の土と砂利、北に幕屋、南に焚き火と腰掛け、東に帆柱と旗、木箱と灯り
    static void build(ServerLevel level, SiteData data, Camp c, @Nullable SiteData.Entry old) {
        int base = ContinentMap.get().surface(c.x(), c.z()) + 1;
        BlockPos o = new BlockPos(c.x(), base, c.z());
        RandomSource rnd = RandomSource.create(c.id().hashCode());
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 8.2) {
                    continue;
                }
                for (int y = 0; y <= 12; y++) {
                    set(level, o.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
                }
                for (int y = -4; y <= -2; y++) {
                    set(level, o.offset(dx, y, dz), Blocks.DIRT.defaultBlockState());
                }
                BlockState top = r > 7.2 ? Blocks.GRASS_BLOCK.defaultBlockState()
                        : r < 2.6 || rnd.nextInt(5) == 0 ? Blocks.DIRT_PATH.defaultBlockState()
                        : rnd.nextInt(4) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                set(level, o.offset(dx, -1, dz), top);
            }
        }
        // 報いの箱 (南向き) と、その下の石の台
        set(level, o.below(), Blocks.POLISHED_ANDESITE.defaultBlockState());
        set(level, o, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        // 幕屋 (北。x -3..3、z -7..-3。棟は x=0 の高さ 4、白い布、奥は閉じる)
        for (int z = -7; z <= -3; z++) {
            for (int x = -3; x <= 3; x++) {
                int roof = 4 - Math.abs(x);
                set(level, o.offset(x, roof, z), Blocks.WHITE_WOOL.defaultBlockState());
                if (z == -7) {
                    for (int y = 0; y < roof; y++) {
                        set(level, o.offset(x, y, z), Blocks.WHITE_WOOL.defaultBlockState());
                    }
                }
            }
            set(level, o.offset(0, 5, z), Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        for (int sx : new int[] {-3, 3}) {
            set(level, o.offset(sx, 0, -3), Blocks.OAK_FENCE.defaultBlockState());
        }
        set(level, o.offset(-1, 0, -5), Blocks.RED_CARPET.defaultBlockState());
        set(level, o.offset(-1, 0, -6), Blocks.RED_CARPET.defaultBlockState());
        set(level, o.offset(1, 0, -6), Blocks.BARREL.defaultBlockState());
        set(level, o.offset(1, 0, -5), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        // 焚き火 (南) と石の縁、腰掛け
        set(level, o.offset(0, 0, 3), Blocks.CAMPFIRE.defaultBlockState());
        for (int[] d : new int[][] {{1, 3}, {-1, 3}, {0, 2}, {0, 4}}) {
            set(level, o.offset(d[0], -1, d[1]), Blocks.COBBLESTONE.defaultBlockState());
        }
        set(level, o.offset(-2, 0, 3), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        set(level, o.offset(2, 0, 3), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        set(level, o.offset(0, 0, 5), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        // 帆柱と旗 (東)
        for (int y = 0; y <= 8; y++) {
            set(level, o.offset(5, y, -1), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        set(level, o.offset(5, 9, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        set(level, o.offset(4, 7, -1), Blocks.ORANGE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST));
        set(level, o.offset(5, 7, 0), Blocks.ORANGE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
        // 木箱と荷 (西と東の手前)
        set(level, o.offset(-4, 0, 0), Blocks.BARREL.defaultBlockState());
        set(level, o.offset(-4, 0, 1), Blocks.BARREL.defaultBlockState());
        set(level, o.offset(-4, 1, 0), Blocks.BARREL.defaultBlockState());
        set(level, o.offset(4, 0, 2), Blocks.HAY_BLOCK.defaultBlockState());
        set(level, o.offset(-5, 0, -2), Blocks.SMITHING_TABLE.defaultBlockState());
        // 灯りの柱 (四隅)
        for (int[] d : new int[][] {{-6, -2}, {6, 3}, {-5, 4}, {3, -8}}) {
            set(level, o.offset(d[0], 0, d[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
            set(level, o.offset(d[0], 1, d[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
            set(level, o.offset(d[0], 2, d[1]), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        if (old != null) {
            data.remove(old);
        }
        SiteData.Entry n = data.add(KIND, c.index(), o, 0);
        n.built = true;
        data.setDirty();
        jp.revati.skyabyss.opening.site.FacilityLabels.place(level, o.above(), "circuit_camp_" + c.id(), false);
        SkyAbyss.LOGGER.info("Circuit camp {} built at {}", c.id(), o);
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    // ---------------------------------------------------------------- 箱を押す
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level) || !level.getBlockState(event.getPos()).is(Blocks.CHEST)) {
            return;
        }
        SiteData data = SiteData.get(level);
        for (Camp c : all()) {
            SiteData.Entry e = entry(data, c.index());
            if (e != null && e.pos.equals(event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                open(p, c);
                return;
            }
        }
    }

    // 野営地の画面を開く (今の景品の一覧・入れ替えまで・今日の残り)
    public static void open(ServerPlayer p, Camp c) {
        if (p instanceof FakePlayer || p.connection == null) {
            return;
        }
        List<ItemStack> list = pool(c, period()).stream().map(ItemStack::new).toList();
        PacketDistributor.sendToPlayer(p, new View(c.id(), list, (int) Math.min(Integer.MAX_VALUE, ticksUntilRotation() / 20),
                Math.max(0, Circuits.DAILY - Circuits.runsToday(p)), Circuits.runOf(p) != null));
    }

    // サーバー → 人: 野営地の画面
    public record View(String camp, List<ItemStack> pool, int secondsToRotate, int runsLeft, boolean running) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "circuit_camp"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, View::camp, ItemStack.OPTIONAL_LIST_STREAM_CODEC, View::pool, ByteBufCodecs.VAR_INT, View::secondsToRotate,
                ByteBufCodecs.VAR_INT, View::runsLeft, ByteBufCodecs.BOOL, View::running, View::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 人 → サーバー: 歩き出す
    public record Start(String camp) implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "circuit_camp_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Start::camp, Start::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 人: 終わりの報いの幕 (引いた品を一つずつ見せる)
    public record Reward(String camp, int marks, int coins, List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<Reward> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "circuit_reward"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reward> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Reward::camp, ByteBufCodecs.VAR_INT, Reward::marks, ByteBufCodecs.VAR_INT, Reward::coins,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, Reward::items, Reward::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 開発・撮影: /skyabyss_camp view (近くの野営地の画面) ・ /skyabyss_camp flag <土地の番号> <組合の名前> (旗を今すぐ持たせる)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("skyabyss_camp").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("view").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Camp best = null;
                    for (Camp c : all()) {
                        if (best == null || Math.hypot(p.getX() - c.x(), p.getZ() - c.z()) < Math.hypot(p.getX() - best.x(), p.getZ() - best.z())) {
                            best = c;
                        }
                    }
                    if (best != null) {
                        open(p, best);
                    }
                    return best == null ? 0 : 1;
                }))
                .then(net.minecraft.commands.Commands.literal("flag")
                        .then(net.minecraft.commands.Commands.argument("region", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                                .then(net.minecraft.commands.Commands.argument("guild", com.mojang.brigadier.arguments.StringArgumentType.string()).executes(ctx -> {
                                    var data = jp.revati.skyabyss.facility.guild.GuildData.get(ctx.getSource().getServer());
                                    var g = data.byName(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "guild"));
                                    if (g == null) {
                                        return 0;
                                    }
                                    GuildWeek.get(ctx.getSource().getServer()).setFlagForTest(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "region"), g.id, g.name);
                                    return 1;
                                })))));
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(View.TYPE, View.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.client.endgame.CircuitCampScreen.receive(payload)))
                .playToClient(Reward.TYPE, Reward.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.client.endgame.CircuitRewardScreen.receive(payload)))
                .playToServer(Start.TYPE, Start.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer p) {
                        Camp c = byId(payload.camp());
                        if (c != null && near(p.serverLevel(), p.blockPosition()) == c) {
                            Circuits.startAtCamp(p, c);
                        }
                    }
                }));
    }
}

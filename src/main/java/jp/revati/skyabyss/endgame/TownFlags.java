package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.guild.GuildData;
import jp.revati.skyabyss.facility.guild.GuildEmblems;
import jp.revati.skyabyss.facility.guild.Guilds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentTowns;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 土地の旗の見た目 (docs/design/54 の 5.1.1・00 の 338): その週に土地の旗を持つ組合 (GuildWeek) の旗を、その土地の町の門と広場に掛ける。
//   門: 町の四つの門には、もとから町の色の壁掛けの旗が 8 枚ずつ (外向き 4・内向き 4。tools/capital/region_town.py の region_gate) ある。
//       旗を持つ組合があれば、それを組合の旗 (地の色と模様。GuildEmblems) に掛け替え、無ければ町の色の無地に戻す
//   広場: 依頼の板のそばの空いた二か所 (初めて掛けるときに探し、町の付属の点 flag_plaza_<n> に覚える) に立てる旗。旗を持つ組合が無ければ外す
//   近くに人がいて、門まで読み込まれている町だけを、掛けてある形と違うときに直す (週が替わる・組合が旗を作り直す と直る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TownFlags {
    static final int CHECK = 100;
    static final int NEAR = 160;
    // 門の旗の場所 (町の中心から。門は四方、旗は門の中心から 6・8 横、壁の外 56・内 48)
    static final int GATE_MIN = 46;
    static final int GATE_MAX = 58;
    static final int SIDE_MIN = 4;
    static final int SIDE_MAX = 10;
    static final int REACH = GATE_MAX + 2;
    static final String PLAZA = "flag_plaza_";
    static final int PLAZA_FLAGS = 2;
    // 町の色 (町の設計図の旗の色。ContinentTowns.REGIONS の並び)
    static final DyeColor[] TOWN_COLOR = {DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.YELLOW, DyeColor.ORANGE};

    // 町 (土地) ごとに、いま掛けてある形 ("" = 町の色)。起動のあとは一度ずつ見直す
    private static final Map<Integer, String> applied = new HashMap<>();

    private TownFlags() {
    }

    public static int region(SiteData.Entry town) {
        return ContinentMap.WHALE + town.index;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK != 37) {
            return;
        }
        ServerLevel level = server.overworld();
        for (SiteData.Entry town : SiteData.get(level).entries(ContinentTowns.KIND)) {
            if (!town.built || level.getNearestPlayer(town.pos.getX(), town.pos.getY(), town.pos.getZ(), NEAR, false) == null) {
                continue;
            }
            refresh(level, town, false);
        }
    }

    // その町の旗を、今週の旗の持ち主に合わせる。force = 掛けてある形が同じでも直す。直したら true
    public static boolean refresh(ServerLevel level, SiteData.Entry town, boolean force) {
        int region = region(town);
        GuildData.Guild g = holder(level.getServer(), region);
        String key = g == null ? "" : g.id + "/" + g.color + "/" + g.accent + "/" + g.pattern;
        if (!force && key.equals(applied.get(region))) {
            return false;
        }
        List<LevelChunk> chunks = chunks(level, town.pos);
        if (chunks == null) {
            return false;
        }
        int n = 0;
        for (BlockPos p : gateBanners(chunks, town.pos)) {
            hang(level, p, g, TOWN_COLOR[Math.max(0, Math.min(TOWN_COLOR.length - 1, town.index))]);
            n++;
        }
        plaza(level, town, g);
        applied.put(region, key);
        SkyAbyss.LOGGER.debug("Town flags: {} → {} ({} gate banners)", ContinentTowns.region(town.index), g == null ? "town colors" : g.name, n);
        return true;
    }

    @Nullable
    static GuildData.Guild holder(MinecraftServer server, int region) {
        String id = GuildWeek.get(server).flag(region);
        return id == null ? null : GuildData.get(server).byId(id);
    }

    // 町のまわり (門まで) のチャンク。どれか読み込まれていなければ null
    @Nullable
    static List<LevelChunk> chunks(ServerLevel level, BlockPos center) {
        List<LevelChunk> out = new ArrayList<>();
        for (int cx = (center.getX() - REACH) >> 4; cx <= (center.getX() + REACH) >> 4; cx++) {
            for (int cz = (center.getZ() - REACH) >> 4; cz <= (center.getZ() + REACH) >> 4; cz++) {
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) {
                    return null;
                }
                out.add(c);
            }
        }
        return out;
    }

    // 門の壁掛けの旗 (町の中心からの位置で見分ける。町は 90 度ずつ回して建つので、四つの門は同じ決まり)
    static List<BlockPos> gateBanners(List<LevelChunk> chunks, BlockPos center) {
        List<BlockPos> out = new ArrayList<>();
        for (LevelChunk c : chunks) {
            for (BlockEntity be : c.getBlockEntities().values()) {
                if (!(be instanceof BannerBlockEntity) || !(be.getBlockState().getBlock() instanceof WallBannerBlock)) {
                    continue;
                }
                BlockPos p = be.getBlockPos();
                int dx = Math.abs(p.getX() - center.getX());
                int dz = Math.abs(p.getZ() - center.getZ());
                int far = Math.max(dx, dz);
                int side = Math.min(dx, dz);
                int dy = p.getY() - center.getY();
                if (far >= GATE_MIN && far <= GATE_MAX && side >= SIDE_MIN && side <= SIDE_MAX && dy >= 5 && dy <= 14) {
                    out.add(p.immutable());
                }
            }
        }
        return out;
    }

    // 壁掛けの旗を、組合の旗か町の色の無地に (向きはそのまま)
    static void hang(ServerLevel level, BlockPos p, @Nullable GuildData.Guild g, DyeColor town) {
        BlockState old = level.getBlockState(p);
        if (!(old.getBlock() instanceof WallBannerBlock)) {
            return;
        }
        DyeColor color = g == null ? town : DyeColor.byId(g.color);
        Direction facing = old.getValue(WallBannerBlock.FACING);
        Block wall = wallBanner(color);
        if (old.getBlock() != wall) {
            level.setBlock(p, wall.defaultBlockState().setValue(WallBannerBlock.FACING, facing), Block.UPDATE_CLIENTS);
        }
        paint(level, p, g, color);
    }

    static void paint(ServerLevel level, BlockPos p, @Nullable GuildData.Guild g, DyeColor color) {
        if (level.getBlockEntity(p) instanceof BannerBlockEntity be) {
            ItemStack stack = g == null ? new ItemStack(BannerBlock.byColor(color)) : GuildEmblems.banner(level.registryAccess(), g.pattern, g.color, g.accent);
            be.fromItem(stack, color);
            be.setChanged();
            level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static final Map<DyeColor, Block> WALL = new HashMap<>();

    // 色ごとの壁掛けの旗 (バニラに色から引く口がないので、一度だけ集める)
    static Block wallBanner(DyeColor color) {
        if (WALL.isEmpty()) {
            for (Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (b instanceof WallBannerBlock w) {
                    WALL.put(w.getColor(), w);
                }
            }
        }
        return WALL.getOrDefault(color, BannerBlock.byColor(color));
    }

    // ---------------------------------------------------------------- 広場の立てる旗
    static void plaza(ServerLevel level, SiteData.Entry town, @Nullable GuildData.Guild g) {
        List<BlockPos> spots = plazaSpots(level, town, g != null);
        for (BlockPos p : spots) {
            BlockState here = level.getBlockState(p);
            if (g == null) {
                if (here.getBlock() instanceof BannerBlock) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                continue;
            }
            if (!here.isAir() && !(here.getBlock() instanceof BannerBlock)) {
                continue;
            }
            DyeColor color = DyeColor.byId(g.color);
            level.setBlock(p, BannerBlock.byColor(color).defaultBlockState().setValue(BannerBlock.ROTATION, facingCenter(p, town.pos)), Block.UPDATE_ALL);
            paint(level, p, g, color);
        }
    }

    // 広場の旗の場所 (覚えてあればそれ。無く、find なら依頼の板のまわりで探して覚える)
    static List<BlockPos> plazaSpots(ServerLevel level, SiteData.Entry town, boolean find) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < PLAZA_FLAGS; i++) {
            BlockPos p = town.extra(PLAZA + i);
            if (p != null) {
                out.add(p);
            }
        }
        if (!out.isEmpty() || !find) {
            return out;
        }
        BlockPos board = town.extra("board");
        if (board == null) {
            return out;
        }
        List<BlockPos> marks = new ArrayList<>(town.extraPos);
        for (int r = 2; r <= 8 && out.size() < PLAZA_FLAGS; r++) {
            for (int dx = -r; dx <= r && out.size() < PLAZA_FLAGS; dx++) {
                for (int dz = -r; dz <= r && out.size() < PLAZA_FLAGS; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = board.offset(dx, dy, dz);
                        if (open(level, p) && far(p, marks, 2) && far(p, out, 4)) {
                            out.add(p);
                            break;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < out.size(); i++) {
            town.extra(PLAZA + i, out.get(i));
        }
        SiteData.get(level).setDirty();
        return out;
    }

    // 立てる旗の置ける所: 足もとが固い床、旗の 2 マスと上 1 マスが空いていて、まわり 8 マスも 3 段空いている (木や柱に寄せない。通り道をふさがない)
    static boolean open(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !level.getBlockState(p.above(2)).isAir()) {
            return false;
        }
        if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if ((dx != 0 || dz != 0) && !level.getBlockState(p.offset(dx, dy, dz)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean far(BlockPos p, List<BlockPos> others, int min) {
        for (BlockPos o : others) {
            if (o != null && Math.abs(o.getX() - p.getX()) < min && Math.abs(o.getZ() - p.getZ()) < min && Math.abs(o.getY() - p.getY()) < 4) {
                return false;
            }
        }
        return true;
    }

    // 立てる旗の向き (町の中心から見える向き。0 = 南向き、4 = 西向き)
    static int facingCenter(BlockPos p, BlockPos center) {
        double dx = center.getX() - p.getX();
        double dz = center.getZ() - p.getZ();
        if (dx * dx + dz * dz < 1) {
            return 0;
        }
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return Math.floorMod((int) Math.round(yaw / 22.5), 16);
    }

    @Nullable
    static SiteData.Entry town(MinecraftServer server, int region) {
        for (SiteData.Entry e : SiteData.get(server.overworld()).entries(ContinentTowns.KIND)) {
            if (region(e) == region) {
                return e;
            }
        }
        return null;
    }

    // 開発用: /skyabyss townflag set <土地> (自分の組合に今週の旗) | clear <土地> | goto <土地> (広場の依頼の板へ)
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("townflag")
                        .then(Commands.literal("set").then(Commands.argument("land", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            Integer r = RegionFame.landArg(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "land"));
                            GuildData.Guild g = Guilds.of(c.getSource().getPlayerOrException());
                            if (r == null || g == null) {
                                return 0;
                            }
                            GuildWeek w = GuildWeek.get(c.getSource().getServer());
                            w.roll(c.getSource().getServer());
                            w.flags.put(r, g.id);
                            w.names.put(g.id, g.name);
                            w.setDirty();
                            return 1;
                        })))
                        .then(Commands.literal("clear").then(Commands.argument("land", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            Integer r = RegionFame.landArg(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "land"));
                            if (r == null) {
                                return 0;
                            }
                            GuildWeek w = GuildWeek.get(c.getSource().getServer());
                            w.flags.remove(r);
                            w.setDirty();
                            return 1;
                        })))
                        // 広場の旗の場所を忘れて、次に掛けるときに探し直す (置き場所の決まりを変えたとき)
                        .then(Commands.literal("respot").then(Commands.argument("land", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            Integer r = RegionFame.landArg(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "land"));
                            SiteData.Entry t = r == null ? null : town(c.getSource().getServer(), r);
                            if (t == null) {
                                return 0;
                            }
                            ServerLevel level = c.getSource().getServer().overworld();
                            plaza(level, t, null);
                            for (int i = 0; i < PLAZA_FLAGS; i++) {
                                int k = t.extraNames.indexOf(PLAZA + i);
                                if (k >= 0) {
                                    t.extraNames.remove(k);
                                    t.extraPos.remove(k);
                                }
                            }
                            SiteData.get(level).setDirty();
                            applied.remove(r);
                            return 1;
                        })))
                        .then(Commands.literal("goto").then(Commands.argument("land", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            Integer r = RegionFame.landArg(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "land"));
                            SiteData.Entry t = r == null ? null : town(c.getSource().getServer(), r);
                            if (t == null) {
                                return 0;
                            }
                            // 広場の旗があれば、その正面 5 マスから旗を見る。無ければ依頼の板へ
                            BlockPos flag = t.extra(PLAZA + 0);
                            var p = c.getSource().getPlayerOrException();
                            if (flag != null) {
                                double a = Math.toRadians(facingCenter(flag, t.pos) * 22.5);
                                double x = flag.getX() + 0.5 - Math.sin(a) * 5;
                                double z = flag.getZ() + 0.5 + Math.cos(a) * 5;
                                float yaw = (float) Math.toDegrees(Math.atan2(-(flag.getX() + 0.5 - x), flag.getZ() + 0.5 - z));
                                p.teleportTo(c.getSource().getLevel(), x, flag.getY() + 0.5, z, java.util.Set.of(), yaw, 12f);
                                return 1;
                            }
                            BlockPos at = t.extra("board") != null ? t.extra("board") : t.pos;
                            p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                            return 1;
                        })))));
    }
}

package jp.revati.skyabyss.titan.storm.raiju;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 雷獣の巣 (右手首の瘤の中、12 の 3.3節・5.2節): 直径 26 の丸い洞、天井 15 段、避雷の柱 6 本 (中心から半径 9、60 度おき)。
// 柱の根元に「接地の栓」(レバー)。右クリックで柱が 8 秒接地する (先の灯りが消え、根元の格子が暗くなる)。栓は接地が切れてから 4 秒待つ。
// 座標は StormBackLayout (背中の担当) から読む。背中のディメンション (skyabyss:storm_back) がまだ無いあいだは、
// オーバーワールドの仮の場所 (TEMP_OFFSET だけずらした空中) に、殻と前室ごと建てる (背中ができたら自動で本番へ)。
// 本番の背中では、洞の中 (半径 13.5 の円柱) を空けて床・柱・栓を置き、殻はもとが空気の所だけ埋める (鳥の形を壊さない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaijuNest {
    public static final ResourceKey<Level> STORM_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back"));
    // 仮の巣: 本番の座標から東へ 4096、上へ 57 (床 y 200)。スポーンの近くを避け、まわりは空
    public static final BlockPos TEMP_OFFSET = new BlockPos(4096, 57, 0);
    public static final int LAYOUT_VERSION = 1;
    public static final int RODS = 6;
    public static final int ROD_RADIUS = 9;
    // 柱: 床の上 5 段の銅、6 段目に銅の電球 (先の灯り)、7 段目に避雷針。雷獣は避雷針の上に立つ
    public static final int ROD_TOP = 6;
    public static final double RADIUS = 13.5;
    private static final double SHELL = 15.5;
    public static final int GROUND_TICKS = 160;
    public static final int PLUG_WAIT = 80;

    // 柱ごとの「栓をまた引ける時刻」と、最後に描いた接地の状態
    private static final Map<Integer, Long> READY_AT = new HashMap<>();
    private static final Map<Integer, Boolean> SHOWN = new HashMap<>();

    // 巣の場所 (本番か仮か)
    public record Site(ServerLevel level, BlockPos center, boolean temporary) {
        public int floor() {
            return center.getY();
        }

        public int ceiling() {
            return StormBackLayout.RAIJU_NEST_CEILING + (temporary ? TEMP_OFFSET.getY() : 0);
        }

        public Vec3 home() {
            return Vec3.atBottomCenterOf(center);
        }

        public BlockPos rodTop(int i) {
            return rodBase(i).above(ROD_TOP);
        }

        public BlockPos rodBase(int i) {
            double a = Math.toRadians(30 + 60 * i);
            return new BlockPos(center.getX() + (int) Math.round(ROD_RADIUS * Math.cos(a)), floor(),
                    center.getZ() + (int) Math.round(ROD_RADIUS * Math.sin(a)));
        }

        public List<BlockPos> rodTops() {
            List<BlockPos> out = new ArrayList<>();
            for (int i = 0; i < RODS; i++) {
                out.add(rodTop(i));
            }
            return out;
        }

        // 栓の向き: 柱から中心へ一番近い横の向き
        public Direction plugFacing(int i) {
            BlockPos b = rodBase(i);
            return Direction.getNearest(center.getX() - b.getX(), 0, center.getZ() - b.getZ());
        }

        public BlockPos plug(int i) {
            return rodBase(i).above().relative(plugFacing(i));
        }

        // 雷獣が柱から落ちる所 (根元から中心へ 2 ブロック)
        public Vec3 dropPoint(int i) {
            Vec3 b = Vec3.atBottomCenterOf(rodBase(i));
            Vec3 toC = home().subtract(b).multiply(1, 0, 1).normalize();
            return b.add(toC.scale(2.2));
        }

        public AABB arena() {
            return new AABB(center.getX() - RADIUS + 0.5, floor() - 0.5, center.getZ() - RADIUS + 0.5,
                    center.getX() + RADIUS + 0.5, ceiling(), center.getZ() + RADIUS + 0.5);
        }

        public boolean inside(Vec3 p) {
            double dx = p.x - (center.getX() + 0.5);
            double dz = p.z - (center.getZ() + 0.5);
            return dx * dx + dz * dz <= RADIUS * RADIUS && p.y >= floor() - 0.5 && p.y < ceiling();
        }

        // 前室 (仮の巣では建てた通路。本番では雷の骨の回廊の突き当たり)
        public AABB ante() {
            return new AABB(center.getX() - 24, floor(), center.getZ() - 3, center.getX() - 14.5, floor() + 4, center.getZ() + 4);
        }

        public Vec3 anteSpawn() {
            return new Vec3(center.getX() - 19.5, floor(), center.getZ() + 0.5);
        }

        // 入口の内側 (集合で中へ入れる所)
        public Vec3 entranceInside() {
            return new Vec3(center.getX() - 10.5, floor(), center.getZ() + 0.5);
        }

        public List<BlockPos> gate() {
            List<BlockPos> out = new ArrayList<>();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    out.add(new BlockPos(center.getX() - 14, floor() + dy, center.getZ() + dz));
                }
            }
            return out;
        }

        // 戸口 (封印の外側 1 マス)。戦いの途中に参加者が触れると中へ通す
        public AABB door() {
            return new AABB(center.getX() - 16, floor(), center.getZ() - 1, center.getX() - 14, floor() + 3, center.getZ() + 2);
        }
    }

    private RaijuNest() {
    }

    // 巣の場所: 背中のディメンションがあれば本番、なければオーバーワールドの仮の場所
    public static Site site(MinecraftServer server) {
        ServerLevel back = server.getLevel(STORM_BACK);
        if (back != null) {
            return new Site(back, StormBackLayout.RAIJU_NEST, false);
        }
        return new Site(server.overworld(), StormBackLayout.RAIJU_NEST.offset(TEMP_OFFSET), true);
    }

    public static boolean isNestLevel(Level level) {
        MinecraftServer server = level.getServer();
        return server != null && level instanceof ServerLevel s && site(server).level() == s;
    }

    // ---------------------------------------------------------------- 建てる

    public static boolean isBuilt(Site site) {
        RaijuState state = RaijuState.get(site.level().getServer());
        return state.built(site.temporary()) == LAYOUT_VERSION && site.level().getBlockState(site.rodTop(0)).is(Blocks.LIGHTNING_ROD);
    }

    public static void ensureBuilt(Site site) {
        if (!isBuilt(site)) {
            build(site);
        }
    }

    public static void build(Site site) {
        ServerLevel level = site.level();
        BlockPos c = site.center();
        int f = site.floor();
        int top = site.ceiling();
        int cx = c.getX();
        int cz = c.getZ();
        int r = (int) Math.ceil(SHELL) + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt((dx + 0.0) * dx + (dz + 0.0) * dz);
                if (d > SHELL + 0.5) {
                    continue;
                }
                boolean interior = d <= RADIUS;
                for (int y = f - 2; y <= top; y++) {
                    p.set(cx + dx, y, cz + dz);
                    BlockState s;
                    if (interior && y == f - 1) {
                        s = floorBlock(dx, dz, d);
                    } else if (interior && y >= f && y < top) {
                        s = Blocks.AIR.defaultBlockState();
                    } else if (interior && y == top) {
                        s = ceilingBlock(dx, dz, d);
                    } else {
                        // 殻 (床の下・壁)。本番の背中では、もとが空気の所だけ埋める
                        if (!site.temporary() && !level.getBlockState(p).isAir()) {
                            continue;
                        }
                        s = interior ? Blocks.TUFF_BRICKS.defaultBlockState() : wallBlock(dx, dz, y - f);
                    }
                    level.setBlock(p, s, 2);
                }
            }
        }
        // 柱と栓
        for (int i = 0; i < RODS; i++) {
            BlockPos b = site.rodBase(i);
            for (int y = 0; y < ROD_TOP - 1; y++) {
                level.setBlock(b.above(y), Blocks.WAXED_CUT_COPPER.defaultBlockState(), 2);
            }
            level.setBlock(b.above(ROD_TOP - 1), Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(CopperBulbBlock.LIT, true), 2);
            level.setBlock(b.above(ROD_TOP), Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
            for (int gx = -1; gx <= 1; gx++) {
                for (int gz = -1; gz <= 1; gz++) {
                    if (gx != 0 || gz != 0) {
                        level.setBlock(b.offset(gx, -1, gz), Blocks.WAXED_COPPER_GRATE.defaultBlockState(), 2);
                    }
                }
            }
            level.setBlock(site.plug(i), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                    .setValue(LeverBlock.FACING, site.plugFacing(i)).setValue(LeverBlock.POWERED, false), 2);
            SHOWN.put(i, false);
        }
        // 入口のくぐり (西の壁) と、仮の巣の前室
        for (int x = cx - 17; x <= cx - 13; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(new BlockPos(x, f + dy, cz + dz), Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(x, f - 1, cz + dz), Blocks.POLISHED_TUFF.defaultBlockState(), 2);
            }
        }
        if (site.temporary()) {
            buildAnte(level, cx, f, cz);
        }
        RaijuState.get(level.getServer()).markBuilt(site.temporary(), LAYOUT_VERSION);
        SkyAbyss.LOGGER.info("Built the raiju nest at {} ({})", c, site.temporary() ? "temporary, the storm back is not there yet" : "storm back");
    }

    private static void buildAnte(ServerLevel level, int cx, int f, int cz) {
        for (int x = cx - 25; x <= cx - 16; x++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = f - 1; y <= f + 4; y++) {
                    boolean edge = Math.abs(dz) == 4 || y == f - 1 || y == f + 4 || x == cx - 25;
                    BlockState s = edge ? Blocks.TUFF_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    if (y == f - 1 && Math.abs(dz) <= 3 && x > cx - 25) {
                        s = (x + dz) % 4 == 0 ? Blocks.CHISELED_TUFF.defaultBlockState() : Blocks.POLISHED_TUFF.defaultBlockState();
                    }
                    if (y == f + 4 && dz == 0 && (x - cx) % 3 == 0) {
                        s = Blocks.SEA_LANTERN.defaultBlockState();
                    }
                    level.setBlock(new BlockPos(x, y, cz + dz), s, 2);
                }
            }
        }
    }

    private static BlockState floorBlock(int dx, int dz, double d) {
        // 中心から柱へ伸びる銅の線 (雷の通り道)、外周の灯り、中心の彫り
        if (d < 1.6) {
            return Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
        }
        double a = Math.toDegrees(Math.atan2(dz, dx));
        double rel = ((a - 30) % 60 + 60) % 60;
        double off = Math.toRadians(Math.min(rel, 60 - rel));
        if (d > 2 && d < ROD_RADIUS - 1.2 && d * Math.sin(off) <= 0.55) {
            return Blocks.WAXED_CUT_COPPER.defaultBlockState();
        }
        if (d > 11.6 && d < 12.6 && ((int) Math.round(a / 15)) % 2 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        if (d > 4.5 && d < 5.5) {
            return Blocks.TUFF_BRICKS.defaultBlockState();
        }
        return ((dx * 7 + dz * 13) & 7) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.POLISHED_TUFF.defaultBlockState();
    }

    private static BlockState ceilingBlock(int dx, int dz, double d) {
        if (d < 2.2) {
            return Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
        }
        if (d > 6.5 && d < 7.5 && (dx + dz) % 3 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        return Blocks.TUFF_BRICKS.defaultBlockState();
    }

    private static BlockState wallBlock(int dx, int dz, int h) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        if (h == 7 && ((int) Math.round(a / 10)) % 3 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        if (h == 0 || h == 12) {
            return Blocks.POLISHED_TUFF.defaultBlockState();
        }
        return Blocks.TUFF_BRICKS.defaultBlockState();
    }

    // ---------------------------------------------------------------- 封印

    public static void seal(Site site) {
        for (BlockPos p : site.gate()) {
            site.level().setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
        }
    }

    public static void open(Site site) {
        for (BlockPos p : site.gate()) {
            if (site.level().getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                site.level().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    public static boolean sealed(Site site) {
        return site.level().getBlockState(site.gate().get(4)).is(ModBlocks.SEALED_GATE.get());
    }

    // ---------------------------------------------------------------- 接地の栓

    public static int rodIndexOfPlug(Site site, BlockPos pos) {
        for (int i = 0; i < RODS; i++) {
            if (site.plug(i).equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    // 栓を引く。引けたら true (人へは理由を一行出す)
    public static boolean pull(Site site, int i, @Nullable ServerPlayer player) {
        ServerLevel level = site.level();
        BlockPos rod = site.rodTop(i);
        long now = level.getGameTime();
        if (StormRods.isGrounded(rod)) {
            if (player != null) {
                player.displayClientMessage(ModText.hint("message.skyabyss.raiju.plug_already"), true);
            }
            return false;
        }
        long ready = READY_AT.getOrDefault(i, 0L);
        if (now < ready) {
            if (player != null) {
                player.displayClientMessage(ModText.deny("message.skyabyss.raiju.plug_wait", (ready - now + 19) / 20), true);
            }
            return false;
        }
        StormRods.ground(rod, GROUND_TICKS);
        READY_AT.put(i, now + GROUND_TICKS + PLUG_WAIT);
        show(site, i, true);
        BlockPos b = site.rodBase(i);
        level.playSound(null, b, RaijuSetup.GROUND.get(), SoundSource.BLOCKS, 1.4f, 1f);
        level.sendParticles(ParticleTypes.SMOKE, rod.getX() + 0.5, rod.getY() - 0.5, rod.getZ() + 0.5, 12, 0.2, 0.3, 0.2, 0.02);
        if (player != null) {
            player.displayClientMessage(ModText.progress("message.skyabyss.raiju.grounded", GROUND_TICKS / 20), true);
        }
        return true;
    }

    // 接地の見た目: 先の灯り (銅の電球) を消し、栓を倒し、根元の格子を暗い緑青にする
    private static void show(Site site, int i, boolean grounded) {
        ServerLevel level = site.level();
        BlockPos b = site.rodBase(i);
        BlockPos bulb = b.above(ROD_TOP - 1);
        if (level.getBlockState(bulb).is(Blocks.WAXED_COPPER_BULB)) {
            level.setBlock(bulb, level.getBlockState(bulb).setValue(CopperBulbBlock.LIT, !grounded), 2);
        }
        BlockPos plug = site.plug(i);
        if (level.getBlockState(plug).is(Blocks.LEVER)) {
            level.setBlock(plug, level.getBlockState(plug).setValue(LeverBlock.POWERED, grounded), 2);
        }
        BlockState grate = grounded ? Blocks.WAXED_OXIDIZED_COPPER_GRATE.defaultBlockState() : Blocks.WAXED_COPPER_GRATE.defaultBlockState();
        for (int gx = -1; gx <= 1; gx++) {
            for (int gz = -1; gz <= 1; gz++) {
                if (gx != 0 || gz != 0) {
                    level.setBlock(b.offset(gx, -1, gz), grate, 2);
                }
            }
        }
        SHOWN.put(i, grounded);
    }

    // 5 tick ごと: 接地が切れた柱の見た目を戻す。灯っている柱の先に小さな火花
    public static void tickRods(Site site) {
        ServerLevel level = site.level();
        for (int i = 0; i < RODS; i++) {
            BlockPos top = site.rodTop(i);
            boolean g = StormRods.isGrounded(top);
            if (SHOWN.getOrDefault(i, false) != g) {
                show(site, i, g);
                if (!g) {
                    level.playSound(null, top, net.minecraft.sounds.SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 1.2f, 1.2f);
                }
            }
            if (!g && level.getGameTime() % 20 == i * 3 % 20) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.getX() + 0.5, top.getY() + 0.9, top.getZ() + 0.5, 3, 0.15, 0.2, 0.15, 0.05);
            }
        }
    }

    public static void clearState() {
        READY_AT.clear();
        SHOWN.clear();
    }

    // 栓 (レバー) の右クリックは、レバーとしては動かさず、接地の栓として扱う
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!level.getBlockState(event.getPos()).is(Blocks.LEVER) || !isNestLevel(level)) {
            return;
        }
        Site site = site(level.getServer());
        int i = rodIndexOfPlug(site, event.getPos());
        if (i < 0) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        pull(site, i, player);
        player.swing(event.getHand(), true);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        clearState();
    }
}

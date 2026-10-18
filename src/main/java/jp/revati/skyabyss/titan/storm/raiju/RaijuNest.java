package jp.revati.skyabyss.titan.storm.raiju;

import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormBlocks;
import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.storm.back.StormBackBuilder;
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
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 雷獣の巣 (右手首の瘤の中、12 の 3.3節・5.2節): 直径 26 の丸い洞、避雷の柱 6 本 (中心から半径 9、60 度おき)。
// 洞・柱・栓 (レバー) は背中の担当が鳥と一緒に建てる (StormBirdBuilder)。座標はすべて StormBackLayout から読み、ここでは建てない。
// 柱は本物の避雷の柱 (StormRods)。根元 (StormBackLayout.raijuRods()) で登録し、満ちた (3 段) 柱として置く:
// 段の電球 3 つが灯っているのが「雷が通る柱」、接地すると消える (StormEast.showRods と同じ決まり)。
// 柱の根元の「接地の栓」(レバー) を右クリックすると、柱が 8 秒接地する (電球が消え、栓が倒れる)。栓は接地が切れてから 4 秒待つ。
// 入口は雷の骨の回廊の突き当たり (StormBackLayout.RAIJU_NEST_MOUTH の x)。戦いのあいだ回廊の幅と高さいっぱいを封じる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaijuNest {
    public static final ResourceKey<Level> STORM_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back"));
    // 2: 背中の担当の本物の巣を使う (自前の洞を建てない)
    public static final int LAYOUT_VERSION = 3;
    public static final int RODS = 6;
    public static final int ROD_RADIUS = StormBackLayout.RAIJU_ROD_RADIUS;
    // 柱: 根元 (床のブロックの高さ) の銅、電球 3 つ、銅、5 段上に避雷針。雷獣は避雷針の上に立つ
    public static final int ROD_TOP = 5;
    public static final double RADIUS = StormBackLayout.RAIJU_NEST_RADIUS + 0.5;
    public static final int GROUND_TICKS = 160;
    public static final int PLUG_WAIT = 80;

    // 柱ごとの「栓をまた引ける時刻」と、最後に描いた接地の状態
    private static final Map<Integer, Long> READY_AT = new HashMap<>();
    private static final Map<Integer, Boolean> SHOWN = new HashMap<>();

    // 巣の場所 (背中の storm_back)。center は StormBackLayout.RAIJU_NEST (床のブロックの高さ)
    public record Site(ServerLevel level, BlockPos center) {
        // 立つ高さ (床のブロックの 1 上)
        public int floor() {
            return center.getY() + 1;
        }

        // 丸天井のいちばん上の空気
        public int ceiling() {
            return StormBackLayout.RAIJU_NEST_CEILING + dy();
        }

        public Vec3 home() {
            return new Vec3(center.getX() + 0.5, floor(), center.getZ() + 0.5);
        }

        // 柱の根元 (StormRods の柱の座標。find・isGrounded・ground はこれで引く)
        public BlockPos rodBase(int i) {
            return StormBackLayout.raijuRods().get(i).offset(center.subtract(StormBackLayout.RAIJU_NEST));
        }

        public BlockPos rod(int i) {
            return rodBase(i);
        }

        // 避雷針 (雷獣が立つブロック)
        public BlockPos rodTop(int i) {
            return rodBase(i).above(ROD_TOP);
        }

        public List<BlockPos> rodTops() {
            List<BlockPos> out = new ArrayList<>();
            for (int i = 0; i < RODS; i++) {
                out.add(rodTop(i));
            }
            return out;
        }

        // 栓 (レバー): 柱の根元の 1 上の、横どなり (背中の担当が置いた所)。見つからなければ中心へ一番近い横
        public BlockPos plug(int i) {
            BlockPos b = rodBase(i).above();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(b.relative(d)).is(Blocks.LEVER)) {
                    return b.relative(d);
                }
            }
            return b.relative(Direction.getNearest(center.getX() - b.getX(), 0, center.getZ() - b.getZ()));
        }

        // 雷獣が柱から落ちる所 (根元から中心へ 2.2 ブロック、床の上)
        public Vec3 dropPoint(int i) {
            Vec3 b = Vec3.atBottomCenterOf(rodBase(i));
            Vec3 toC = home().subtract(b).multiply(1, 0, 1).normalize();
            Vec3 p = b.add(toC.scale(2.2));
            return new Vec3(p.x, floor(), p.z);
        }

        public AABB arena() {
            return new AABB(center.getX() - RADIUS + 0.5, floor() - 0.5, center.getZ() - RADIUS + 0.5,
                    center.getX() + RADIUS + 0.5, ceiling() + 1, center.getZ() + RADIUS + 0.5);
        }

        public boolean inside(Vec3 p) {
            double dx = p.x - (center.getX() + 0.5);
            double dz = p.z - (center.getZ() + 0.5);
            return dx * dx + dz * dz <= RADIUS * RADIUS && p.y >= floor() - 0.5 && p.y < ceiling() + 1;
        }

        private int dx() {
            return center.getX() - StormBackLayout.RAIJU_NEST.getX();
        }

        private int dy() {
            return center.getY() - StormBackLayout.RAIJU_NEST.getY();
        }

        private int dz() {
            return center.getZ() - StormBackLayout.RAIJU_NEST.getZ();
        }

        // 入口を封じる列の x (雷の骨の回廊の突き当たり)
        private int gateX() {
            return (int) Math.floor(StormBackLayout.RAIJU_NEST_MOUTH.minX) + dx();
        }

        // 回廊の立つ高さ
        private int corridorY() {
            return StormBackLayout.BONE_FLOOR + 1 + dy();
        }

        // 前室 (入口の手前の回廊 14 ブロック)
        public AABB ante() {
            return new AABB(gateX() - 14, corridorY(), StormBackLayout.BONE_Z1 + dz(),
                    gateX(), corridorY() + StormBackLayout.EAST_HEIGHT, StormBackLayout.BONE_Z2 + 1 + dz());
        }

        public Vec3 anteSpawn() {
            return new Vec3(gateX() - 5.5, corridorY(), center.getZ() + 0.5);
        }

        // 入口の内側 (集合で中へ入れる所)
        public Vec3 entranceInside() {
            return new Vec3(center.getX() + 0.5 - RADIUS + 3, floor(), center.getZ() + 0.5);
        }

        // 封じるブロック (回廊の幅と高さいっぱい)
        public List<BlockPos> gate() {
            List<BlockPos> out = new ArrayList<>();
            int x = gateX();
            for (int y = corridorY(); y < corridorY() + StormBackLayout.EAST_HEIGHT; y++) {
                for (int z = StormBackLayout.BONE_Z1; z <= StormBackLayout.BONE_Z2; z++) {
                    out.add(new BlockPos(x, y, z + dz()));
                }
            }
            return out;
        }

        // 入口の真ん中の足もと (封じたかを見る所・閉じる音)
        public BlockPos gateCenter() {
            return new BlockPos(gateX(), corridorY(), center.getZ());
        }

        // 戸口 (封印の外側 3 マス)。戦いの途中に参加者が触れると中へ通す
        public AABB door() {
            return new AABB(gateX() - 3, corridorY(), StormBackLayout.BONE_Z1 + dz(),
                    gateX(), corridorY() + 3, StormBackLayout.BONE_Z2 + 1 + dz());
        }
    }

    private RaijuNest() {
    }

    // 巣の場所 (背中のディメンション)
    public static Site site(MinecraftServer server) {
        ServerLevel back = server.getLevel(STORM_BACK);
        return new Site(back != null ? back : server.overworld(), StormBackLayout.RAIJU_NEST);
    }

    public static boolean isNestLevel(Level level) {
        return level instanceof ServerLevel s && s.dimension() == STORM_BACK;
    }

    // ---------------------------------------------------------------- 支度 (建てるのは背中の担当)

    // 背中の鳥が建っていて、巣の柱が本物の柱として登録されている
    public static boolean isBuilt(Site site) {
        for (int i = 0; i < RODS; i++) {
            if (!site.level().getBlockState(site.rodTop(i)).is(StormBackSetup.ROD_TIP.get()) || StormRods.get(site.level(), site.rod(i)) == null) {
                return false;
            }
        }
        return true;
    }

    public static void ensureBuilt(Site site) {
        if (!isBuilt(site)) {
            build(site);
        }
    }

    // 巣を戦いの前の姿に戻す: 背中が建っていなければ建て、柱を登録し直し (満ちた柱)、栓と電球を戻し、入口を開ける。
    // 前の版 (1) の雷獣は自前の洞を背中の上に彫っていたので、柱が見つからない古い世界は背中を一度だけ建て直す
    public static void build(Site site) {
        ServerLevel level = site.level();
        if (!isNestLevel(level)) {
            return;
        }
        StormBackBuilder.ensureBuilt(level);
        RaijuState state = RaijuState.get(level.getServer());
        if (!level.getBlockState(site.rodTop(0)).is(StormBackSetup.ROD_TIP.get()) && state.built() < LAYOUT_VERSION) {
            SkyAbyss.LOGGER.info("Found the raiju nest of an older version; rebuilding the storm back once");
            StormBackBuilder.build(level, TitanBackData.get(level));
        }
        state.markBuilt(LAYOUT_VERSION);
        READY_AT.clear();
        for (int i = 0; i < RODS; i++) {
            BlockPos rod = site.rod(i);
            StormRods.register(level, rod);
            StormRods.charge(level, rod, StormRods.FULL);
            show(site, i, false);
        }
        open(site);
    }

    // ---------------------------------------------------------------- 封印

    public static void seal(Site site) {
        for (BlockPos p : site.gate()) {
            if (site.level().getBlockState(p).isAir()) {
                site.level().setBlock(p, ModBlocks.SEALED_GATE.get().defaultBlockState(), 3);
            }
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
        return site.level().getBlockState(site.gateCenter()).is(ModBlocks.SEALED_GATE.get());
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
        BlockPos rod = site.rod(i);
        long now = level.getGameTime();
        if (StormRods.isGrounded(rod)) {
            if (player != null) {
                Notice.bar(player, ModText.hint("message.skyabyss.raiju.plug_already"));
            }
            return false;
        }
        long ready = READY_AT.getOrDefault(i, 0L);
        if (now < ready) {
            if (player != null) {
                Notice.bar(player, ModText.deny("message.skyabyss.raiju.plug_wait", (ready - now + 19) / 20));
            }
            return false;
        }
        StormRods.ground(rod, GROUND_TICKS);
        READY_AT.put(i, now + GROUND_TICKS + PLUG_WAIT);
        show(site, i, true);
        BlockPos top = site.rodTop(i);
        level.playSound(null, rod, RaijuSetup.GROUND.get(), SoundSource.BLOCKS, 1.4f, 1f);
        level.sendParticles(ParticleTypes.SMOKE, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, 12, 0.2, 0.3, 0.2, 0.02);
        if (player != null) {
            Notice.bar(player, ModText.progress("message.skyabyss.raiju.grounded", GROUND_TICKS / 20));
        }
        return true;
    }

    // 接地の見た目: 柱の段の電球 3 つを消し、栓を倒す (戻ると灯り、栓が起きる)
    private static void show(Site site, int i, boolean grounded) {
        ServerLevel level = site.level();
        BlockPos b = site.rodBase(i);
        for (int k = 1; k <= 3; k++) {
            BlockPos bulb = b.above(k);
            BlockState s = level.getBlockState(bulb);
            if (s.is(StormBackSetup.ROD_BULB.get()) && s.getValue(StormBlocks.RodBulb.LIT) == grounded) {
                level.setBlock(bulb, s.setValue(StormBlocks.RodBulb.LIT, !grounded), 2);
            }
        }
        BlockPos plug = site.plug(i);
        BlockState lever = level.getBlockState(plug);
        if (lever.is(Blocks.LEVER) && lever.getValue(LeverBlock.POWERED) != grounded) {
            level.setBlock(plug, lever.setValue(LeverBlock.POWERED, grounded), 2);
        }
        SHOWN.put(i, grounded);
    }

    // 5 tick ごと: 接地が切れた柱の見た目を戻す。灯っている柱の先に小さな火花
    public static void tickRods(Site site) {
        ServerLevel level = site.level();
        for (int i = 0; i < RODS; i++) {
            BlockPos top = site.rodTop(i);
            boolean g = StormRods.isGrounded(site.rod(i));
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

package jp.revati.skyabyss.facility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningSites;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 訓練場の試し打ちの的 (docs/design/35 の 3.2)。
//   見回り: 都の近く (訓練場ははじめから使える。章では閉じない) (師範の印 npc_training_master から 40 ブロック) に人がいれば 5 秒ごと、設計図の木偶の柱
//           (柵 → わら → 彫った南瓜) を探して (一度探せば覚える)、柱ごとに見えない的 (TrainingDummy) を一つかぶせる。柱がなければ的を消す
//   打つと: 与えた量を的の上に浮かぶ数字 (文字の表示。1 秒) で出し、打った人のアクションバーに「今の一打 ・ 5 秒の合計 (毎秒)」
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TrainingYard {
    private TrainingYard() {
    }

    public static final String MASTER_MARK = AttendantRole.TRAINING_MASTER.mark();
    static final int SEARCH = 40;
    static final int WINDOW = 100;
    static final int NUMBER_TICKS = 24;
    static final String NUMBER_TAG = "skyabyss_dummy_number";

    // 探した木偶の柱 (師範の印の位置ごと)
    private static final Map<Long, List<BlockPos>> COLUMNS = new HashMap<>();
    // 打った記録 (人ごと: 時刻と量)
    private static final Map<UUID, ArrayDeque<double[]>> HITS = new HashMap<>();
    // 浮かぶ数字 (消す時刻)
    private static final Map<Display.TextDisplay, Long> NUMBERS = new HashMap<>();

    // 柱か (足もとの柵、その上のわら、その上の彫った南瓜)
    public static boolean isColumn(Level level, BlockPos foot) {
        BlockState a = level.getBlockState(foot);
        return a.getBlock() instanceof FenceBlock && level.getBlockState(foot.above()).is(Blocks.HAY_BLOCK)
                && level.getBlockState(foot.above(2)).is(Blocks.CARVED_PUMPKIN);
    }

    // center のまわりの柱を探す (読み込まれていない所は飛ばす)
    public static List<BlockPos> findColumns(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!level.isLoaded(p.set(center.getX() + dx, center.getY(), center.getZ() + dz))) {
                    continue;
                }
                for (int dy = -3; dy <= 3; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(p).is(Blocks.CARVED_PUMPKIN)) {
                        BlockPos foot = p.below(2).immutable();
                        if (isColumn(level, foot)) {
                            out.add(foot);
                        }
                    }
                }
            }
        }
        return out;
    }

    // 柱に的がいるようにする。いる (立たせた) 的の数
    public static int ensureDummies(ServerLevel level, List<BlockPos> columns) {
        int n = 0;
        for (BlockPos foot : columns) {
            if (!level.isLoaded(foot)) {
                continue;
            }
            List<TrainingDummy> here = level.getEntities(FacilitySetup.TRAINING_DUMMY.get(), new AABB(foot).inflate(0.5, 1, 0.5), e -> e.isAlive());
            if (!isColumn(level, foot)) {
                here.forEach(TrainingDummy::discard);
                continue;
            }
            if (!here.isEmpty()) {
                for (int i = 1; i < here.size(); i++) {
                    here.get(i).discard();
                }
                n++;
                continue;
            }
            TrainingDummy d = FacilitySetup.TRAINING_DUMMY.get().create(level);
            if (d == null) {
                continue;
            }
            d.setColumn(foot);
            d.moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, 0f, 0f);
            level.addFreshEntity(d);
            n++;
        }
        return n;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0x3F) + 37) % 100 != 0) {
            return;
        }
        BlockPos master = OpeningSites.shelterExtras(player.server, MASTER_MARK).get(MASTER_MARK);
        if (master == null || master.distSqr(player.blockPosition()) > (double) (SEARCH + 24) * (SEARCH + 24)
                || !Attendants.loaded(level, master)) {
            return;
        }
        List<BlockPos> cols = COLUMNS.get(master.asLong());
        if (cols == null || cols.isEmpty()) {
            cols = findColumns(level, master, SEARCH);
            COLUMNS.put(master.asLong(), cols);
            SkyAbyss.LOGGER.info("[facility] training yard: {} dummy columns", cols.size());
        }
        ensureDummies(level, cols);
    }

    // ---------------------------------------------------------------- 与えた量
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof TrainingDummy dummy) || dummy.level().isClientSide) {
            return;
        }
        float dealt = event.getNewDamage();
        dummy.setHealth(dummy.getMaxHealth());
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = dummy.level().getGameTime();
        double[] sum = record(player.getUUID(), now, dealt);
        number((ServerLevel) dummy.level(), dummy, dealt, now);
        Notice.bar(player, Component.translatable("message.skyabyss.training.hit", fmt(dealt), fmt(sum[0]), fmt(sum[1])));
    }

    // 記録して、窓 (5 秒) の合計と毎秒を返す
    public static double[] record(UUID who, long now, double dealt) {
        ArrayDeque<double[]> q = HITS.computeIfAbsent(who, k -> new ArrayDeque<>());
        q.addLast(new double[] {now, dealt});
        while (!q.isEmpty() && now - q.peekFirst()[0] > WINDOW) {
            q.removeFirst();
        }
        double total = 0;
        for (double[] h : q) {
            total += h[1];
        }
        double span = Math.max(20, now - q.peekFirst()[0] + 10);
        return new double[] {total, total / (span / 20.0)};
    }

    public static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    static void number(ServerLevel level, TrainingDummy dummy, float dealt, long now) {
        Display.TextDisplay t = EntityType.TEXT_DISPLAY.create(level);
        if (t == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        Component text = Component.literal(fmt(dealt)).withStyle(s -> s.withColor(TextColor.fromRgb(dealt >= 10 ? 0xF2C14E : 0xE2D9C2)).withBold(true));
        tag.put("text", StringTag.valueOf(Component.Serializer.toJson(text, level.registryAccess())));
        tag.putString("billboard", "center");
        tag.putInt("background", 0);
        tag.putBoolean("shadow", true);
        t.load(tag);
        double ox = (level.random.nextDouble() - 0.5) * 0.6;
        double oz = (level.random.nextDouble() - 0.5) * 0.6;
        t.moveTo(dummy.getX() + ox, dummy.getY() + 3.2 + level.random.nextDouble() * 0.3, dummy.getZ() + oz, 0f, 0f);
        t.addTag(NUMBER_TAG);
        level.addFreshEntity(t);
        NUMBERS.put(t, now + NUMBER_TICKS);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (NUMBERS.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        Iterator<Map.Entry<Display.TextDisplay, Long>> it = NUMBERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Display.TextDisplay, Long> e = it.next();
            Display.TextDisplay t = e.getKey();
            if (now >= e.getValue() || t.isRemoved()) {
                t.discard();
                it.remove();
            } else {
                // 少しずつ上へ
                t.setPos(t.getX(), t.getY() + 0.03, t.getZ());
            }
        }
    }

    // 保存の途中で残った数字は、読み込まれたときに消す
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Display.TextDisplay t && t.getTags().contains(NUMBER_TAG)
                && !NUMBERS.containsKey(t) && event.loadedFromDisk()) {
            event.setCanceled(true);
        }
    }

    // 自己テスト用
    @Nullable
    static List<BlockPos> cachedColumns(BlockPos master) {
        return COLUMNS.get(master.asLong());
    }

    static void forget(MinecraftServer server) {
        COLUMNS.clear();
        HITS.clear();
    }
}

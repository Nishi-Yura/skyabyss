package jp.revati.skyabyss.abyss.mobs;

import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 深淵の中の敵 A の湧かせ方 (docs/design/36 の 4 節 A3)。
//   場所: 人のまわり 12〜22 ブロック。道 (螺旋) の上と、溜まり・巣・野営の印の近く (印の近くを選ぶことが多い)。
//   湧かない所: 休み場 (rest_<n>) と着く所 (arrival) の 14 ブロック以内。
//   数: 人のまわり 40 ブロックに 6 匹まで。明るい所 (灯り) は湧きにくい (闇を選ぶ)。
//   種: その層の元の敵 (段 1) が多く、一つ上の層の敵が歪んだ姿 (段 2) で混ざる (20%)。層の下の果て (灰より下) は段 3。
// 開発用: /skyabyss abyss mob <種> <段> (AbyssWorld の木に付ける)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssMobSpawner {
    public static final int CAP_NEAR = 6;
    public static final double CAP_RANGE = 40;
    public static final double REST_CLEAR = 14;
    public static final int EVERY = 60;
    public static final double MIN_D = 12;
    public static final double MAX_D = 22;
    public static final int LIGHT_SKIP = 10;

    private AbyssMobSpawner() {
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p) || p.isSpectator() || p.tickCount % EVERY != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        int near = level.getEntitiesOfClass(AbyssMob.class, p.getBoundingBox().inflate(CAP_RANGE)).size();
        if (near >= CAP_NEAR) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        BlockPos from = pickAnchor(level, p, r);
        if (from == null) {
            return;
        }
        Vec3 spot = findSpot(level, from, r);
        if (spot == null || nearRest(spot)) {
            return;
        }
        int light = AbyssLight.lightAt(level, BlockPos.containing(spot));
        if (light >= LIGHT_SKIP && r.nextDouble() < 0.6) {
            return;
        }
        int layer = AbyssMobStats.layerOf(spot.y);
        int native_ = pickNative(layer, r);
        if (native_ < 0) {
            return;
        }
        java.util.List<AbyssMobKind> kinds = AbyssMobKind.nativeTo(native_);
        AbyssMobKind kind = kinds.get(r.nextInt(kinds.size()));
        int stage = AbyssMobStats.stage(native_, layer);
        AbyssMob m = AbyssMobsA.create(kind, level, stage, layer);
        if (m == null) {
            return;
        }
        m.moveTo(spot.x, spot.y, spot.z, r.nextFloat() * 360f, 0f);
        m.addTag(jp.revati.skyabyss.entity.GuardMob.TEMP_TAG);
        level.addFreshEntity(m);
    }

    // 人のまわりの、湧かせる基点 (印の近くを 35% で選ぶ。印は溜まり・巣・野営)
    private static BlockPos pickAnchor(ServerLevel level, ServerPlayer p, ThreadLocalRandom r) {
        if (r.nextDouble() < 0.35) {
            for (String layer : new String[] {"bone", "salt", "storm", "ash"}) {
                for (String kind : new String[] {"den", "pool", "camp"}) {
                    BlockPos m = AbyssBuild.mark(kind + "_" + layer);
                    if (m != null && m.distSqr(p.blockPosition()) < 24 * 24 && r.nextBoolean()) {
                        return m;
                    }
                }
            }
        }
        return p.blockPosition();
    }

    // 基点から 12〜22 ブロック、地面のある所 (上から 3 つ上、下へ 8 まで探す)
    private static Vec3 findSpot(ServerLevel level, BlockPos from, ThreadLocalRandom r) {
        double a = r.nextDouble() * Math.PI * 2;
        double d = MIN_D + r.nextDouble() * (MAX_D - MIN_D);
        int x = from.getX() + (int) Math.round(Math.cos(a) * d);
        int z = from.getZ() + (int) Math.round(Math.sin(a) * d);
        int top = from.getY() + 3;
        for (int y = top; y >= top - 11; y--) {
            BlockPos ground = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(ground);
            if (below.isSolid() && level.getBlockState(ground.above()).isAir() && level.getBlockState(ground.above(2)).isAir()) {
                return new Vec3(x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    private static boolean nearRest(Vec3 spot) {
        BlockPos at = BlockPos.containing(spot);
        BlockPos arrival = AbyssWorld.arrival();
        if (arrival.distSqr(at) < REST_CLEAR * REST_CLEAR) {
            return true;
        }
        for (int i = 1; i <= 6; i++) {
            BlockPos rest = AbyssBuild.mark("rest_" + i);
            if (rest == null) {
                break;
            }
            if (rest.distSqr(at) < REST_CLEAR * REST_CLEAR) {
                return true;
            }
        }
        return false;
    }

    // 種の元の層: 層の元の敵が 70%、一つ上の層の敵が 30% (灰より下は三層の敵から)
    static int pickNative(int layer, ThreadLocalRandom r) {
        int top = Math.min(layer, 2);
        if (top < 0) {
            return -1;
        }
        if (layer >= 3) {
            return r.nextInt(3);
        }
        if (top == 0 || r.nextDouble() < 0.7) {
            return top;
        }
        return r.nextInt(top);
    }

    // /skyabyss abyss mob <種> <段>
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("mob").then(Commands.argument("kind", StringArgumentType.word()).then(
                Commands.argument("stage", IntegerArgumentType.integer(1, AbyssMobStats.STAGE_MAX)).executes(c -> {
                    CommandSourceStack src = c.getSource();
                    ServerPlayer p = src.getPlayerOrException();
                    AbyssMobKind k = AbyssMobKind.byId(StringArgumentType.getString(c, "kind"));
                    if (k == null) {
                        src.sendFailure(Component.literal("unknown kind (" + ids() + ")"));
                        return 0;
                    }
                    int stage = IntegerArgumentType.getInteger(c, "stage");
                    int layer = Math.max(0, k.native_ + stage - 1);
                    ServerLevel level = (ServerLevel) p.level();
                    AbyssMob m = AbyssMobsA.create(k, level, stage, layer);
                    if (m == null) {
                        return 0;
                    }
                    m.moveTo(p.getX() + 2.5, p.getY(), p.getZ(), p.getYRot() + 180f, 0f);
                    level.addFreshEntity(m);
                    src.sendSuccess(() -> Component.literal("spawned " + k.id + " stage " + stage), false);
                    return 1;
                })));
    }

    private static String ids() {
        StringBuilder sb = new StringBuilder();
        for (AbyssMobKind k : AbyssMobKind.values()) {
            sb.append(sb.length() == 0 ? "" : ", ").append(k.id);
        }
        return sb.toString();
    }
}

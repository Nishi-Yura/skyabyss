package jp.revati.skyabyss.abyss.mobs;

import java.util.Random;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 深淵の敵 B 組の湧き (docs/design/36 の 4・A3)。深淵の人の近くに、層ごとの表から湧く。
//   灰の層 (y -240〜-120)   灰の這い跡・煤の子・燠の番人 (多い)。部屋 den_ash・pool_ash・camp_ash の近くにも湧く
//   深み (y < -300)         前の私の影・名のない形 (心臓の下だけ)
//   どこでも (深淵だけ)     灯喰い・逆さ歩き・鼓動もどき は少なく。縄の人は吹き抜けの縁 (真ん中から 11〜16) にいる人の前にだけ、一体
//   湧かない: 休み場 rest_* と着く所 arrival の 24 ブロック内 (休みと着くところには敵を置かない)
//   数: 人ごとに 40 ブロック内に 5 体まで。40 tick ごとに三分の一の確率で一回。段は湧いた y で決める (AbyssMobsBStage)
// 開発用: /skyabyss abyss foe <id> [段]  (その人の前に、その段で一体。段を省けば y で決める)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssMobsBSpawn {
    static final int EVERY = 40;
    static final int CAP_NEAR = 5;
    static final double CAP_RANGE = 40;
    static final double REST_KEEP = 24;
    static final double SPAWN_MIN = 12;
    static final double SPAWN_MAX = 22;
    static final double ROPE_MIN = 11;
    static final double ROPE_MAX = 16;
    static final String[] IDS = {AshTrail.ID, EmberKeeper.ID, SootChild.ID, PastShadow.ID, NamelessShape.ID, LampEater.ID,
            CeilingWalker.ID, HeartbeatMimic.ID, RopeHanger.ID};
    private static final Random RAND = new Random();

    private AbyssMobsBSpawn() {
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % EVERY != 0 || !AbyssBuild.built(server)) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || RAND.nextInt(3) != 0) {
                continue;
            }
            trySpawn(level, p);
        }
    }

    // 人の近くに一回だけ湧かせる (表で決める)
    static void trySpawn(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (nearRest(at, REST_KEEP) || nearCount(level, at) >= CAP_NEAR) {
            return;
        }
        int y = at.getY();
        int roll = RAND.nextInt(100);
        if (y >= -240 && y <= -120) {
            if (roll < 8) {
                abyssOnly(level, p, at);
            } else if (roll < 50) {
                spawnNamed(level, p, roll < 30 ? AshTrail.ID : SootChild.ID, ashSite(level, at), AbyssMobsBStage.stageAt(y));
            } else if (roll < 56) {
                spawnNamed(level, p, EmberKeeper.ID, ashSite(level, at), AbyssMobsBStage.stageAt(y));
            }
        } else if (y < AbyssMobsBStage.DEPTH_TOP) {
            if (roll < 12) {
                abyssOnly(level, p, at);
            } else if (roll < 50) {
                spawnNamed(level, p, roll < 32 ? PastShadow.ID : NamelessShape.ID, ground(level, at), AbyssMobsBStage.depthStage(y));
            }
        } else if (roll < 6) {
            abyssOnly(level, p, at);
        }
    }

    // 深淵だけの敵の一つ (灯喰い・逆さ歩き・鼓動もどき。縄の人は縁の人の前だけ)
    static void abyssOnly(ServerLevel level, ServerPlayer p, BlockPos at) {
        int y = at.getY();
        double dx = p.getX();
        double dz = p.getZ();
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r >= ROPE_MIN && r <= ROPE_MAX && RAND.nextInt(4) == 0 && !ropeNear(level)) {
            BlockPos axisAt = new BlockPos(0, at.getY() + 2, 0);
            spawnAt(level, RopeHanger.ID, axisAt.getX() + 0.5, axisAt.getY(), axisAt.getZ() + 0.5, AbyssMobsBStage.stageAt(y));
            return;
        }
        String id = switch (RAND.nextInt(3)) {
            case 0 -> LampEater.ID;
            case 1 -> CeilingWalker.ID;
            default -> HeartbeatMimic.ID;
        };
        BlockPos site = id.equals(CeilingWalker.ID) ? ceiling(level, at) : ground(level, at);
        spawnNamed(level, p, id, site, AbyssMobsBStage.stageAt(y));
    }

    // 灰の層の湧き場: 部屋 (den・pool・camp) が近くにあればその近く、なければ人の周り
    static BlockPos ashSite(ServerLevel level, BlockPos at) {
        if (RAND.nextBoolean()) {
            for (String m : new String[] {"den_ash", "pool_ash", "camp_ash"}) {
                BlockPos mark = AbyssBuild.mark(m);
                if (mark != null && mark.distSqr(at) < 30 * 30 && !nearRest(mark, REST_KEEP)) {
                    return ground(level, mark.offset(RAND.nextInt(5) - 2, 0, RAND.nextInt(5) - 2));
                }
            }
        }
        return ground(level, at);
    }

    // 人の周りの湧き場 (床の上)。見つからなければ null
    static BlockPos ground(ServerLevel level, BlockPos from) {
        for (int tries = 0; tries < 12; tries++) {
            double a = RAND.nextDouble() * Math.PI * 2;
            double r = SPAWN_MIN + RAND.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
            BlockPos c = BlockPos.containing(from.getX() + Math.cos(a) * r, from.getY(), from.getZ() + Math.sin(a) * r);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos b = c.offset(0, dy, 0);
                if (level.getBlockState(b).isFaceSturdy(level, b, Direction.UP) && level.getBlockState(b.above()).isAir()
                        && level.getBlockState(b.above(2)).isAir()) {
                    return b.above();
                }
            }
        }
        return null;
    }

    // 天井の下 (体が収まる空間の上が固い所)。見つからなければ null
    static BlockPos ceiling(ServerLevel level, BlockPos from) {
        for (int tries = 0; tries < 12; tries++) {
            double a = RAND.nextDouble() * Math.PI * 2;
            double r = SPAWN_MIN + RAND.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
            BlockPos c = BlockPos.containing(from.getX() + Math.cos(a) * r, from.getY(), from.getZ() + Math.sin(a) * r);
            for (int dy = 1; dy <= 5; dy++) {
                BlockPos b = c.offset(0, dy, 0);
                if (level.getBlockState(b).isAir() && level.getBlockState(b.above()).isAir() && !level.getBlockState(b.above(2)).isAir()) {
                    return b;
                }
            }
        }
        return null;
    }

    static void spawnNamed(ServerLevel level, ServerPlayer p, String id, BlockPos site, int stage) {
        if (site == null) {
            return;
        }
        // 煤の子は群れ (2〜4)。天井歩きは、天井の下の空間 (site) に体の上が天井に触れるように置く
        int n = id.equals(SootChild.ID) ? 2 + RAND.nextInt(3) : 1;
        for (int i = 0; i < n; i++) {
            double ox = i == 0 ? 0 : RAND.nextDouble() * 2 - 1;
            double oz = i == 0 ? 0 : RAND.nextDouble() * 2 - 1;
            double y = id.equals(CeilingWalker.ID) ? site.getY() + 2 - CEILING_WALKER_HEIGHT : site.getY();
            spawnAt(level, id, site.getX() + 0.5 + ox, y, site.getZ() + 0.5 + oz, stage);
        }
    }

    static final double CEILING_WALKER_HEIGHT = 1.9;

    // 湧かせる (段と数値を決めて入れる)。ID が違えば何もしない
    @SuppressWarnings("unchecked")
    static AbyssMobB spawnAt(ServerLevel level, String id, double x, double y, double z, int stage) {
        EntityType<?> type = typeOf(id);
        if (type == null) {
            return null;
        }
        var e = type.create(level);
        if (!(e instanceof AbyssMobB mob)) {
            return null;
        }
        mob.setPos(x, y, z);
        mob.setYRot(RAND.nextFloat() * 360f);
        tuneFor(mob, id, stage);
        level.addFreshEntity(mob);
        return mob;
    }

    static void tuneFor(AbyssMobB mob, String id, int stage) {
        switch (id) {
            case AshTrail.ID -> mob.tune(stage, AshTrail.HP, AshTrail.DMG);
            case EmberKeeper.ID -> mob.tune(stage, EmberKeeper.HP, EmberKeeper.DMG);
            case SootChild.ID -> mob.tune(stage, SootChild.HP, SootChild.DMG);
            case PastShadow.ID -> mob.tune(stage, PastShadow.HP, PastShadow.DMG);
            case NamelessShape.ID -> mob.tune(stage, NamelessShape.HP, NamelessShape.DMG);
            case LampEater.ID -> mob.tune(stage, LampEater.HP, LampEater.DMG);
            case CeilingWalker.ID -> mob.tune(stage, CeilingWalker.HP, CeilingWalker.DMG);
            case HeartbeatMimic.ID -> mob.tune(stage, HeartbeatMimic.HP, HeartbeatMimic.DMG);
            case RopeHanger.ID -> mob.tune(stage, RopeHanger.HP, 0);
            default -> {
            }
        }
    }

    static EntityType<?> typeOf(String id) {
        return switch (id) {
            case AshTrail.ID -> AbyssMobsBSetup.ASH_TRAIL.get();
            case EmberKeeper.ID -> AbyssMobsBSetup.EMBER_KEEPER.get();
            case SootChild.ID -> AbyssMobsBSetup.SOOT_CHILD.get();
            case PastShadow.ID -> AbyssMobsBSetup.PAST_SHADOW.get();
            case NamelessShape.ID -> AbyssMobsBSetup.NAMELESS_SHAPE.get();
            case LampEater.ID -> AbyssMobsBSetup.LAMP_EATER.get();
            case CeilingWalker.ID -> AbyssMobsBSetup.CEILING_WALKER.get();
            case HeartbeatMimic.ID -> AbyssMobsBSetup.HEARTBEAT_MIMIC.get();
            case RopeHanger.ID -> AbyssMobsBSetup.ROPE_HANGER.get();
            default -> null;
        };
    }

    // 休み場 (rest_1〜3) と着く所 (arrival) の近く
    static boolean nearRest(BlockPos p, double r) {
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival != null && arrival.distSqr(p) < r * r) {
            return true;
        }
        for (int i = 1; i <= 3; i++) {
            BlockPos rest = AbyssBuild.mark("rest_" + i);
            if (rest != null && rest.distSqr(p) < r * r) {
                return true;
            }
        }
        return false;
    }

    static int nearCount(ServerLevel level, BlockPos at) {
        return level.getEntitiesOfClass(AbyssMobB.class, new AABB(at).inflate(CAP_RANGE)).size();
    }

    static boolean ropeNear(ServerLevel level) {
        return !level.getEntitiesOfClass(RopeHanger.class, new AABB(-40, -400, -40, 40, 400, 40)).isEmpty();
    }

    // ---------------------------------------------------------------- 開発用: /skyabyss abyss foe <id> [段]
    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abyss")
                        .then(Commands.literal("foe")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(c -> foe(c.getSource(), StringArgumentType.getString(c, "id"), -1))
                                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, AbyssMobsBStage.MAX))
                                                .executes(c -> foe(c.getSource(), StringArgumentType.getString(c, "id"),
                                                        IntegerArgumentType.getInteger(c, "stage"))))))));
    }

    static int foe(net.minecraft.commands.CommandSourceStack src, String id, int stage) {
        if (typeOf(id) == null) {
            src.sendFailure(Component.translatable("commands.skyabyss.foe.unknown", id + " (" + String.join(", ", IDS) + ")"));
            return 0;
        }
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("a player is needed"));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        int y = p.blockPosition().getY();
        int s = stage >= 0 ? stage : (id.equals(PastShadow.ID) || id.equals(NamelessShape.ID) ? AbyssMobsBStage.depthStage(y) : AbyssMobsBStage.stageAt(y));
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize().scale(3);
        double x = p.getX() + look.x;
        double z = p.getZ() + look.z;
        double yy = p.getY();
        if (id.equals(CeilingWalker.ID)) {
            // 見ている先の天井の下 (空気の二段の上が固い所)
            BlockPos base = BlockPos.containing(x, p.getY(), z);
            BlockPos c = null;
            for (int dy = 0; dy <= 5 && c == null; dy++) {
                BlockPos b = base.offset(0, dy, 0);
                if (level.getBlockState(b).isAir() && level.getBlockState(b.above()).isAir() && !level.getBlockState(b.above(2)).isAir()) {
                    c = b;
                }
            }
            if (c == null) {
                src.sendFailure(Component.literal("no ceiling in front (make one, 2 to 5 blocks above)"));
                return 0;
            }
            yy = c.getY() + 2 - CEILING_WALKER_HEIGHT;
            x = c.getX() + 0.5;
            z = c.getZ() + 0.5;
        }
        AbyssMobB mob = spawnAt(level, id, x, yy, z, s);
        src.sendSuccess(() -> Component.translatable("commands.skyabyss.foe.spawned", id, s), false);
        return mob == null ? 0 : 1;
    }
}

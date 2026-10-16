package jp.revati.skyabyss.titan.ember.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 余燼の背の敵のテスト用コマンド (OP。13 の 12章)。湧かせた敵は試練の敵の印 (EmberMob.TRIAL_TAG) を持ち、ディスクに保存されない。
// /skyabyss embermob spawn <thrall|moth|roller|mix> [数] : 自分のまわり 4〜7 ブロックに湧かせる
//        (moth は数を省くと 3〜6 体の群れ。mix は灰被り 1・蛾の群れ・燠虫 1)。熱が効くのは余燼の背の上だけ (ほかの所でも湧かせられる)
// /skyabyss embermob ignite : まわり 16 ブロックの灰被りの殻を割る (燃え上がりを見る)
// /skyabyss embermob clear  : まわり 64 ブロックの余燼の敵を消す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberMobCommands {
    private static final String LANG = EmberIds.LANG_MOB + "command.";

    private EmberMobCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> spawn = op("spawn");
        for (String kind : new String[] {"thrall", "moth", "roller", "mix"}) {
            spawn.then(op(kind).executes(ctx -> spawn(ctx, kind, -1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                            .executes(ctx -> spawn(ctx, kind, IntegerArgumentType.getInteger(ctx, "count")))));
        }
        dispatcher.register(op(SkyAbyss.MODID).then(op("embermob").then(spawn)
                .then(op("ignite").executes(EmberMobCommands::ignite))
                .then(op("clear").executes(EmberMobCommands::clear))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String kind, int count) {
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 at = ctx.getSource().getPosition();
        int n = 0;
        switch (kind) {
            case "thrall" -> n += spawnMany(level, at, EmberMobSetup.ASH_THRALL.get(), count < 0 ? 1 : count, 0);
            case "roller" -> n += spawnMany(level, at, EmberMobSetup.CINDER_ROLLER.get(), count < 0 ? 1 : count, 0);
            case "moth" -> n += spawnMany(level, at, EmberMobSetup.EMBER_MOTH.get(), count < 0 ? 3 + level.random.nextInt(4) : count, 2);
            default -> {
                n += spawnMany(level, at, EmberMobSetup.ASH_THRALL.get(), 1, 0);
                n += spawnMany(level, at, EmberMobSetup.EMBER_MOTH.get(), 3 + level.random.nextInt(4), 2);
                n += spawnMany(level, at, EmberMobSetup.CINDER_ROLLER.get(), 1, 0);
            }
        }
        int spawned = n;
        ctx.getSource().sendSuccess(() -> ModText.hint(LANG + "spawned", spawned), true);
        return spawned;
    }

    // 群れは同じ所の近くに固めて湧かせる (印を付けて、保存しない)
    public static int spawnMany(ServerLevel level, Vec3 near, EntityType<? extends Mob> type, int count, int lift) {
        double ang = level.random.nextDouble() * Math.PI * 2;
        double r = 4 + level.random.nextDouble() * 3;
        double cx = near.x + Math.cos(ang) * r;
        double cz = near.z + Math.sin(ang) * r;
        int n = 0;
        for (int i = 0; i < count; i++) {
            double x = cx + (count > 1 ? level.random.nextDouble() * 3 - 1.5 : 0);
            double z = cz + (count > 1 ? level.random.nextDouble() * 3 - 1.5 : 0);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(x, y + lift, z, level.random.nextFloat() * 360f, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.COMMAND, null);
            mob.addTag(EmberMob.TRIAL_TAG);
            level.addFreshEntity(mob);
            n++;
        }
        return n;
    }

    private static int ignite(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = 0;
        for (AshThrall t : level.getEntitiesOfClass(AshThrall.class, AABB.ofSize(ctx.getSource().getPosition(), 32, 32, 32), AshThrall::isAlive)) {
            t.ignite();
            n++;
        }
        int done = n;
        ctx.getSource().sendSuccess(() -> ModText.hint(LANG + "ignited", done), true);
        return n;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = 0;
        for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.ofSize(ctx.getSource().getPosition(), 128, 128, 128), e -> e instanceof EmberMob)) {
            e.discard();
            n++;
        }
        int cleared = n;
        ctx.getSource().sendSuccess(() -> ModText.hint(LANG + "cleared", cleared), true);
        return n;
    }
}

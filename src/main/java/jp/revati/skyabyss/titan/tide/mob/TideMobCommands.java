package jp.revati.skyabyss.titan.tide.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 潮殻の背の敵のテスト用コマンド (OP)。背中のダンジョンの本番はまだないので、潮殻の背の仮の島で湧かせる。
// 湧かせた敵は試練の敵の印 (TideMob.TRIAL_TAG) を持ち、ディスクに保存されない。
// /skyabyss tidemob spawn <thrall|scuttler|puffer|mix> [数] : 自分のまわり 4〜7 ブロックの地面に湧かせる
//        (scuttler は数を省くと 3〜5 体の群れ。mix は珊瑚憑き 1・磯走りの群れ・泡吹き 1)
// /skyabyss tidemob tide <low|high|cycle> : 潮を止める / 周期に戻す (珊瑚憑きが像になる・目覚めるのを試す)
// /skyabyss tidemob clear : まわり 64 ブロックの潮殻の敵を消す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideMobCommands {
    private TideMobCommands() {
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
        for (String kind : new String[] {"thrall", "scuttler", "puffer", "mix"}) {
            spawn.then(op(kind).executes(ctx -> spawn(ctx, kind, -1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(ctx -> spawn(ctx, kind, IntegerArgumentType.getInteger(ctx, "count")))));
        }
        LiteralArgumentBuilder<CommandSourceStack> tide = op("tide");
        tide.then(op("low").executes(ctx -> tide(ctx, MobTide.Phase.LOW)));
        tide.then(op("high").executes(ctx -> tide(ctx, MobTide.Phase.HIGH)));
        tide.then(op("cycle").executes(ctx -> tide(ctx, null)));
        dispatcher.register(op(SkyAbyss.MODID).then(op("tidemob").then(spawn).then(tide).then(op("clear").executes(TideMobCommands::clear))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String kind, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (player.level().dimension() != TideTravel.TIDE_BACK) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.tidemob.back_only"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        int n = 0;
        switch (kind) {
            case "thrall" -> n += spawnMany(level, player, TideMobSetup.CORAL_THRALL.get(), count < 0 ? 1 : count, 0);
            case "puffer" -> n += spawnMany(level, player, TideMobSetup.BUBBLE_PUFFER.get(), count < 0 ? 1 : count, 3);
            case "scuttler" -> n += spawnMany(level, player, TideMobSetup.REEF_SCUTTLER.get(), count < 0 ? 3 + level.random.nextInt(3) : count, 0);
            default -> {
                n += spawnMany(level, player, TideMobSetup.CORAL_THRALL.get(), 1, 0);
                n += spawnMany(level, player, TideMobSetup.REEF_SCUTTLER.get(), 3 + level.random.nextInt(3), 0);
                n += spawnMany(level, player, TideMobSetup.BUBBLE_PUFFER.get(), 1, 3);
            }
        }
        int spawned = n;
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tidemob.spawned", spawned), true);
        return spawned;
    }

    private static int spawnMany(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type, int count, int lift) {
        // 群れは同じ所の近くに固める
        double ang = level.random.nextDouble() * Math.PI * 2;
        double r = 4 + level.random.nextDouble() * 3;
        double cx = player.getX() + Math.cos(ang) * r;
        double cz = player.getZ() + Math.sin(ang) * r;
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
            mob.addTag(TideMob.TRIAL_TAG);
            level.addFreshEntity(mob);
            n++;
        }
        return n;
    }

    private static int tide(CommandContext<CommandSourceStack> ctx, MobTide.Phase phase) {
        MobTide.force(phase);
        String key = phase == null ? "cycle" : phase == MobTide.Phase.LOW ? "low" : "high";
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tidemob.tide." + key), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = 0;
        for (Entity e : level.getEntitiesOfClass(Entity.class, net.minecraft.world.phys.AABB.ofSize(ctx.getSource().getPosition(), 128, 128, 128),
                e -> e instanceof TideMob || e instanceof BubbleShot)) {
            e.discard();
            n++;
        }
        int cleared = n;
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tidemob.cleared", cleared), true);
        return n;
    }
}

package jp.revati.skyabyss.titan.storm.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 雷翼の背の敵のテスト用コマンド (OP)。背中の本番の配置は背中の担当 (担当 1)。ここではどこでも湧かせられる (雷翼の背がまだ無くても試せる)。
// 湧かせた敵は試練の敵の印 (StormMob.TRIAL_TAG) を持ち、ディスクに保存されない (倒すと 6.1節の物を落とす)。
// /skyabyss stormmob spawn <warden|moth|supplicant|mix> [数] : 自分のまわり 5〜8 ブロックに湧かせる (mix は羽守 1・雷蛾 2・雷乞い 1)
// /skyabyss stormmob storm <calm|storm|cycle> : 敵の見る拍を凪・嵐に止める / 拍に戻す (羽守の突撃・雷蛾の速い帯電を試す)
// /skyabyss stormmob clear : まわり 64 ブロックの雷翼の背の敵を消す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormMobCommands {
    private StormMobCommands() {
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
        for (String kind : new String[] {"warden", "moth", "supplicant", "mix"}) {
            spawn.then(op(kind).executes(ctx -> spawn(ctx, kind, -1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(ctx -> spawn(ctx, kind, IntegerArgumentType.getInteger(ctx, "count")))));
        }
        LiteralArgumentBuilder<CommandSourceStack> storm = op("storm");
        storm.then(op("calm").executes(ctx -> storm(ctx, MobStorm.Phase.CALM)));
        storm.then(op("storm").executes(ctx -> storm(ctx, MobStorm.Phase.STORM)));
        storm.then(op("cycle").executes(ctx -> storm(ctx, null)));
        dispatcher.register(op(SkyAbyss.MODID).then(op("stormmob").then(spawn).then(storm).then(op("clear").executes(StormMobCommands::clear))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String kind, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        int n = 0;
        int c = count < 0 ? 1 : count;
        switch (kind) {
            case "warden" -> n += spawnMany(level, player, StormMobSetup.FEATHER_WARDEN.get(), c, 0);
            case "moth" -> n += spawnMany(level, player, StormMobSetup.STATIC_MOTH.get(), c, 2);
            case "supplicant" -> n += spawnMany(level, player, StormMobSetup.THUNDER_SUPPLICANT.get(), c, 0);
            default -> {
                n += spawnMany(level, player, StormMobSetup.FEATHER_WARDEN.get(), 1, 0);
                n += spawnMany(level, player, StormMobSetup.STATIC_MOTH.get(), 2, 2);
                n += spawnMany(level, player, StormMobSetup.THUNDER_SUPPLICANT.get(), 1, 0);
            }
        }
        int spawned = n;
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.stormmob.spawned", spawned), true);
        return spawned;
    }

    static int spawnMany(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type, int count, int lift) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2;
            double r = 5 + level.random.nextDouble() * 3;
            double x = player.getX() + Math.cos(ang) * r;
            double z = player.getZ() + Math.sin(ang) * r;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - player.getY()) > 6) {
                y = Mth.floor(player.getY());   // 足場の外 (崖や背中の縁) なら、自分の高さに
            }
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(x, y + lift, z, level.random.nextFloat() * 360f, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.COMMAND, null);
            mob.addTag(StormMob.TRIAL_TAG);
            level.addFreshEntity(mob);
            n++;
        }
        return n;
    }

    private static int storm(CommandContext<CommandSourceStack> ctx, MobStorm.Phase phase) {
        MobStorm.force(phase);
        String key = phase == null ? "cycle" : phase == MobStorm.Phase.CALM ? "calm" : "storm";
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.stormmob.storm." + key), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = 0;
        for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.ofSize(ctx.getSource().getPosition(), 128, 128, 128), e -> e instanceof StormMob)) {
            e.discard();
            n++;
        }
        int cleared = n;
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.stormmob.cleared", cleared), true);
        return n;
    }
}

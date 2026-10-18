package jp.revati.skyabyss.titan.tide;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.back.TideBeckoner;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用: /skyabyss dev tideforce <対象> <技> [人数]
// 潮殻の心臓 (BREATH / SHELL_STRIKE / WHIRL / FLOOD / BUBBLE / PEARL / EBB / MOON_WAVE / PEARL_RAIN / ROAR) と
// 潮招き (SLAM / BREATH / BURROW / ROAR) の技を一つ出し、次の技は始めない (台本のスクショで予告の途中を撮るため)。
// 人数は、戦いの人数を実際より多いことにする (一人で複数人の技を見る)。潮招きは、60 ブロック以内の人を戦いの人として狙う
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideForceDev {
    private TideForceDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("tideforce")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .executes(ctx -> run(EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "action"), 0))
                                        .then(Commands.argument("party", IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> run(EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "action"),
                                                        IntegerArgumentType.getInteger(ctx, "party")))))))));
    }

    private static int run(java.util.Collection<? extends Entity> targets, String action, int party) {
        int n = 0;
        for (Entity e : targets) {
            if (e instanceof TideHeart heart) {
                heart.holdAttacks();
                heart.setPartyOverride(party);
                heart.forceAction(TideHeart.Action.valueOf(action.toUpperCase(Locale.ROOT)));
                n++;
            } else if (e instanceof TideBeckoner boss && e.level() instanceof ServerLevel level) {
                List<ServerPlayer> near = level.getPlayers(p -> p.distanceToSqr(boss) < 60 * 60 && p.isAlive() && !p.isSpectator());
                boss.holdAttacks();
                boss.setDevFighters(near);
                boss.setPartyOverride(party);
                boss.forceAction(action);
                n++;
            }
        }
        return n;
    }
}

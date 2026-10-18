package jp.revati.skyabyss.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用: /skyabyss dev force <対象> <技> [人数]
// 番兵長 (SWEEP / LEAP / ERUPT / ROAR) と岩鯨の心臓 (THRUST / SWEEP / LEAP / CRACK / RECLOSE / VOLLEY / BEAM / CHAIN / SHELL_DROP) の技を一つ出し、
// 次の技は始めない (台本のスクショで予告の途中を撮るため)。人数 (心臓だけ) は、心臓の間にいる人数を実際より多いことにする (一人で複数人の技を見る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BossForceDev {
    private BossForceDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("force")
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
            if (e instanceof WhaleboneCaptain captain) {
                captain.holdAttacks();
                captain.forceAction(action);
                n++;
            } else if (e instanceof StoneWhaleHeart heart) {
                heart.holdAttacks();
                heart.setPartyOverride(party);
                heart.forceAttack(action);
                n++;
            }
        }
        return n;
    }
}

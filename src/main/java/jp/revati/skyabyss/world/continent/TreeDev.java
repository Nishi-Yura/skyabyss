package jp.revati.skyabyss.world.continent;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用: 土地の木を一本植える (ラフを並べて撮るため)。/skyabyss dev tree <種類> <場所> [種]
//   種類: 鐘の野の花傘の樹 blossom_young / blossom / blossom_old、岩鯨の木 fir / fir_big / pale / moss_low / teal、潮の浜の汐傘の樹 parasol_young / parasol / parasol_old、嵐の高地の雷晶の螺旋木 spiral_young / spiral / spiral_old、灰の荒野の煤垂れの樹 soot_young / soot / soot_old、外れの雲玉の樫 cloud_young / cloud / cloud_old、砂漠の砂紐の樹 crook_young / crook / crook_old
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TreeDev {
    static final List<String> KINDS = List.of("blossom_young", "blossom", "blossom_old", "fir", "fir_big", "pale", "moss_low", "teal", "parasol_young", "parasol", "parasol_old", "spiral_young", "spiral", "spiral_old", "soot_young", "soot", "soot_old", "cloud_young", "cloud", "cloud_old", "crook_young", "crook", "crook_old");

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("tree")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    KINDS.forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> grow(c, 1L))
                                        .then(Commands.argument("seed", StringArgumentType.word())
                                                .executes(c -> grow(c, StringArgumentType.getString(c, "seed").hashCode()))))))));
    }

    static int grow(CommandContext<CommandSourceStack> c, long seed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String kind = StringArgumentType.getString(c, "kind");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        ServerLevel level = c.getSource().getLevel();
        RandomSource r = RandomSource.create(seed ^ pos.asLong());
        ContinentTrees.Grower g = new ContinentTrees.Grower(level, r);
        switch (kind) {
            case "blossom_young" -> MeadowTrees.blossomUmbrella(g, pos, r, 0.65);
            case "blossom" -> MeadowTrees.blossomUmbrella(g, pos, r, 1.0);
            case "blossom_old" -> MeadowTrees.blossomUmbrella(g, pos, r, 1.35);
            case "fir" -> WhaleTrees.fir(g, pos, r, false);
            case "fir_big" -> WhaleTrees.fir(g, pos, r, true);
            case "pale" -> WhaleTrees.pale(g, pos, r);
            case "moss_low" -> WhaleTrees.mossLow(g, pos, r);
            case "teal" -> WhaleTrees.teal(g, pos, r);
            case "parasol_young" -> TideTrees.parasol(g, pos, r, 0.7);
            case "parasol" -> TideTrees.parasol(g, pos, r, 1.0);
            case "parasol_old" -> TideTrees.parasol(g, pos, r, 1.3);
            case "spiral_young" -> StormTrees.spiral(g, pos, r, 0.7);
            case "spiral" -> StormTrees.spiral(g, pos, r, 1.0);
            case "spiral_old" -> StormTrees.spiral(g, pos, r, 1.3);
            case "soot_young" -> EmberTrees.sootWeeper(g, pos, r, 0.75);
            case "soot" -> EmberTrees.sootWeeper(g, pos, r, 1.0);
            case "soot_old" -> EmberTrees.sootWeeper(g, pos, r, 1.3);
            case "cloud_young" -> OutskirtsTrees.cloudOak(g, pos, r, 0.75);
            case "cloud" -> OutskirtsTrees.cloudOak(g, pos, r, 1.0);
            case "cloud_old" -> OutskirtsTrees.cloudOak(g, pos, r, 1.3);
            case "crook_young" -> DesertTrees.crook(g, pos, r, 0.75);
            case "crook" -> DesertTrees.crook(g, pos, r, 1.0);
            case "crook_old" -> DesertTrees.crook(g, pos, r, 1.3);
            default -> {
                c.getSource().sendFailure(Component.literal("unknown tree " + kind));
                return 0;
            }
        }
        g.finish();
        c.getSource().sendSuccess(() -> Component.literal("grew " + kind + " at " + pos.toShortString()), false);
        return 1;
    }

    private TreeDev() {
    }
}

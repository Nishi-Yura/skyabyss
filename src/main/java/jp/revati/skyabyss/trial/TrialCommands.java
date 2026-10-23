package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用の試練のコマンド (docs/design/29 の 2.6・5.3)。/skyabyss dev trial …
//   board [titan]            板を開く (背中の外からでも)
//   list                     いまの周・三つ・修飾・種
//   cycle <n>                そのグループの周を n にして、引き直す (履歴は消す)
//   force <id> <id> <id>     引いた三つを決める (修飾は標準)
//   mods <id> <符号>         引いた試練の修飾を、符号 (Mods.encode) で決める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TrialCommands {
    private TrialCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("trial")
                        .then(Commands.literal("board").executes(ctx -> board(ctx.getSource(), null))
                                .then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> board(ctx.getSource(), StringArgumentType.getString(ctx, "titan")))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("cycle").then(Commands.argument("n", IntegerArgumentType.integer(1, 99))
                                .executes(ctx -> cycle(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.literal("force").then(Commands.argument("ids", StringArgumentType.greedyString())
                                .executes(ctx -> force(ctx.getSource(), StringArgumentType.getString(ctx, "ids")))))
                        .then(Commands.literal("mods").then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("code", StringArgumentType.word())
                                .executes(ctx -> mods(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "code")))))))));
    }

    // 命令を打った人がいる背 (背の外なら岩鯨の背)
    private static ServerLevel back(CommandSourceStack src) {
        if (src.getEntity() != null && Titans.titanOfBack(src.getEntity().level().dimension()) != null && src.getEntity().level() instanceof ServerLevel here) {
            return here;
        }
        return src.getServer().getLevel(jp.revati.skyabyss.titan.TitanTravel.TITAN_BACK);
    }

    private static String titan(ServerLevel back) {
        String t = Titans.titanOfBack(back.dimension());
        return t == null ? Titans.STONE_WHALE : t;
    }

    private static int board(CommandSourceStack src, String titan) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TrialBoard.open(src.getPlayerOrException(), titan == null ? titan(back(src)) : titan);
        return 1;
    }

    private static int list(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel back = back(src);
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record r = GroupProgress.of(back, group);
        TrialDraw.Draw draw = TrialData.current(back, group);
        src.sendSuccess(() -> Component.literal("cycle " + draw.cycle() + " seed " + TrialDraw.seed(TrialData.worldSeed(back), group, titan(back), draw.cycle())
                + " cleared " + TrialData.clearedIds(r)), false);
        for (TrialDraw.Pick p : draw.picks()) {
            src.sendSuccess(() -> Component.literal("  " + p.cellId() + " " + p.mods().encode() + " " + p.mods().rarity() + (p.mods().quiet() ? " quiet" : "")), false);
        }
        return 1;
    }

    private static int cycle(CommandSourceStack src, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel back = back(src);
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record r = GroupProgress.of(back, group);
        r.putInt(TrialData.DONE, n - 1);
        r.remove(TrialData.DRAW);
        for (Trial.Slot s : Trial.Slot.values()) {
            r.remove(TrialData.SEEN + s.id);
            r.remove(TrialData.LAST + s.id);
        }
        return list(src);
    }

    private static int force(CommandSourceStack src, String ids) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel back = back(src);
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record r = GroupProgress.of(back, group);
        List<TrialDraw.Pick> picks = new ArrayList<>();
        for (String id : ids.trim().split("\\s+")) {
            if (TrialCatalog.get(id) == null) {
                src.sendFailure(Component.literal("unknown trial " + id));
                return 0;
            }
            picks.add(new TrialDraw.Pick(id, Mods.PLAIN));
        }
        r.putString(TrialData.DRAW, new TrialDraw.Draw(TrialData.cycle(r), picks).encode());
        return list(src);
    }

    private static int mods(CommandSourceStack src, String id, String code) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ServerLevel back = back(src);
        UUID group = Groups.keyOf(sp);
        BackGroupData.Record r = GroupProgress.of(back, group);
        TrialDraw.Draw draw = TrialData.current(back, group);
        List<TrialDraw.Pick> picks = new ArrayList<>();
        boolean found = false;
        for (TrialDraw.Pick p : draw.picks()) {
            if (p.cellId().equals(id)) {
                picks.add(new TrialDraw.Pick(id, Mods.decode(code)));
                found = true;
            } else {
                picks.add(p);
            }
        }
        if (!found) {
            src.sendFailure(Component.literal(id + " is not drawn"));
            return 0;
        }
        r.putString(TrialData.DRAW, new TrialDraw.Draw(draw.cycle(), picks).encode());
        GroupOverlays.refresh(back, group);
        return list(src);
    }

}

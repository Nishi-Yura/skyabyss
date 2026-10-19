package jp.revati.skyabyss.abyss;

import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.TrialCatalog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用 (権限 2): /skyabyss dev abyss on|off|status [巨神]。試練の板のつまみの代わり (転生 3 回の条件なしで、そのグループの深淵を入れ切りする)。
// 巨神を省くと四体すべて。つまみは巨神の背ごとの記録 (Abyss.FLAG)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssCommands {
    private AbyssCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("abyss")
                        .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), null, true))
                                .then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "titan"), true))))
                        .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), null, false))
                                .then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "titan"), false))))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))))));
    }

    private static int set(CommandSourceStack src, String titan, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        UUID group = Groups.keyOf(p);
        int n = 0;
        for (String t : TrialCatalog.titans()) {
            if ((titan == null || titan.equals(t)) && Titans.hasBack(t)) {
                ServerLevel back = src.getServer().getLevel(Titans.backOf(t));
                if (back != null) {
                    BackGroupData.Record r = GroupProgress.of(back, group);
                    Abyss.set(r, on);
                    r.changed();
                    n++;
                }
            }
        }
        int count = n;
        src.sendSuccess(() -> Component.literal("abyss " + (on ? "on" : "off") + " for " + count + " back(s)"), false);
        return n;
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        UUID group = Groups.keyOf(p);
        StringBuilder sb = new StringBuilder("abyss: open=" + Abyss.open(p));
        for (String t : TrialCatalog.titans()) {
            if (Titans.hasBack(t)) {
                ServerLevel back = src.getServer().getLevel(Titans.backOf(t));
                sb.append(' ').append(t).append('=').append(back != null && Abyss.groupOn(back, group));
            }
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}

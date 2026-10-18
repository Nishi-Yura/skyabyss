package jp.revati.skyabyss.titan.end;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 巨神の眠りのテスト用コマンド (OP。docs/design/01 の 4.3、20 の 3.4)。名前は stone_whale / tide_shell / thunder_wing / ember。
//   /skyabyss titan end <名前> sleep    : いますぐ眠る姿にする (沈む・光が消える。心臓は止めない)
//   /skyabyss titan end <名前> wake     : いますぐ目覚めさせる
//   /skyabyss titan end <名前> status   : 眠りの様子
//   /skyabyss titan end <名前> carry    : 自分を、降ろす場所へ運ぶ (初めてなら眠りの場面。その巨神は眠る姿になる)
//   /skyabyss titan end <名前> where    : 降ろす場所の座標
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanEndCommands {
    private TitanEndCommands() {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        var titan = Commands.argument("titan", StringArgumentType.word()).suggests((ctx, b) -> {
            ProgressRules.TITAN_ORDER.forEach(b::suggest);
            return b.buildFuture();
        });
        event.getDispatcher().register(op(SkyAbyss.MODID).then(op("titan").then(op("end").then(titan
                .then(op("sleep").executes(ctx -> sleep(ctx)))
                .then(op("wake").executes(ctx -> wake(ctx)))
                .then(op("status").executes(ctx -> status(ctx)))
                .then(op("carry").executes(ctx -> carry(ctx)))
                .then(op("where").executes(ctx -> where(ctx)))))));
    }

    private static String name(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "titan");
    }

    private static boolean known(CommandContext<CommandSourceStack> ctx) {
        if (ProgressRules.titanIndex(name(ctx)) >= 0) {
            return true;
        }
        ctx.getSource().sendFailure(Component.literal("unknown titan: " + name(ctx)));
        return false;
    }

    private static int sleep(CommandContext<CommandSourceStack> ctx) {
        if (!known(ctx)) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        String titan = name(ctx);
        TitanDormancy.begin(server, titan, 0);
        if (Titans.THUNDER_WING.equals(titan)) {
            TitanEnd.chooseStormPeak(server.overworld());
        }
        ctx.getSource().sendSuccess(() -> Component.literal(titan + " sleeps"), true);
        return 1;
    }

    private static int wake(CommandContext<CommandSourceStack> ctx) {
        if (!known(ctx)) {
            return 0;
        }
        boolean was = TitanDormancy.wakeNow(ctx.getSource().getServer(), name(ctx));
        ctx.getSource().sendSuccess(() -> Component.literal(name(ctx) + (was ? " woke" : " was not asleep")), true);
        return was ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        if (!known(ctx)) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        String titan = name(ctx);
        boolean resting = TitanDormancy.isResting(server, titan);
        int age = TitanDormancy.age(server, titan);
        ctx.getSource().sendSuccess(() -> Component.literal(titan + ": resting=" + resting + " dormantAge=" + age), false);
        return 1;
    }

    private static int carry(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!known(ctx)) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        String titan = name(ctx);
        TitanEnd.Landing landing = TitanEnd.landing(server, titan);
        TitanDormancy.begin(server, titan, 0);
        if (Titans.THUNDER_WING.equals(titan)) {
            TitanEnd.chooseStormPeak(server.overworld());
        }
        TitanEnd.deliver(player, titan, landing);
        ctx.getSource().sendSuccess(() -> Component.literal("carried to " + landing.pos()), true);
        return 1;
    }

    private static int where(CommandContext<CommandSourceStack> ctx) {
        if (!known(ctx)) {
            return 0;
        }
        TitanEnd.Landing landing = TitanEnd.landing(ctx.getSource().getServer(), name(ctx));
        Entity e = TitanEnd.entity(ctx.getSource().getServer(), name(ctx));
        ctx.getSource().sendSuccess(() -> Component.literal("landing " + landing.pos() + " yaw " + landing.yaw() + " titan " + landing.titan()
                + (e == null ? " (not loaded)" : "")), false);
        return 1;
    }
}

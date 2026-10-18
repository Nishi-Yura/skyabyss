package jp.revati.skyabyss.titan.tide.heart;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 潮殻の心臓のテスト用コマンド (OP)。
// /skyabyss tideheart go           : 殻の底の前室へ (潮殻の背へ渡ってから。部屋がなければ建てる)
// /skyabyss tideheart start        : 集合を待たず、部屋の中の人で戦いを始める
// /skyabyss tideheart phase <1-3>  : 段階を決める (体力もその段階の頭に合わせる)
// /skyabyss tideheart action <技>  : 技を今すぐ使わせる (breath, shell_strike, whirl, flood, bubble, pearl, ebb, moon_wave, pearl_rain, roar)
// /skyabyss tideheart win          : 心臓を倒したことにする (報酬・進み・記録まで通す)
// /skyabyss tideheart reset        : 戦いを放棄して戻す。倒したあとなら、そのグループの試練ごと戻して心臓をもう一度挑めるようにする
// (start・reset・status・phase などは、コマンドを打った人のグループの戦いに効く。殻の底は一度に一つのグループが使う)
// /skyabyss tideheart rebuild      : 部屋を建て直す
// /skyabyss tideheart status       : 今の様子
// /skyabyss tideheart shot <人> <名前> : その人の画面を run/screenshots/<名前>.png に写す (キーを送らずに確かめるため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideHeartCommands {
    private TideHeartCommands() {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(op(SkyAbyss.MODID).then(op("tideheart")
                .then(op("go").executes(TideHeartCommands::go))
                .then(op("start").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    boolean ok = TideHeartBattle.startNow(back, group(ctx));
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "tide heart: started" : "tide heart: already active or failed"), true);
                    return ok ? 1 : 0;
                }))
                .then(op("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                    TideHeart heart = heart(ctx);
                    int n = IntegerArgumentType.getInteger(ctx, "n");
                    float frac = n == 1 ? 1f : n == 2 ? 0.6f : 0.3f;
                    heart.setHealth(heart.getMaxHealth() * frac);
                    heart.forcePhase(n - 1);
                    ctx.getSource().sendSuccess(() -> Component.literal("tide heart: phase " + n), true);
                    return 1;
                })))
                .then(op("action").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(TideHeart.Action.values()).filter(a -> a != TideHeart.Action.NONE)
                                        .map(a -> a.name().toLowerCase(java.util.Locale.ROOT)), b))
                        .executes(ctx -> {
                            TideHeart heart = heart(ctx);
                            TideHeart.Action action = TideHeart.Action.valueOf(StringArgumentType.getString(ctx, "name").toUpperCase(java.util.Locale.ROOT));
                            heart.forceAction(action);
                            ctx.getSource().sendSuccess(() -> Component.literal("tide heart: " + action), true);
                            return 1;
                        })))
                .then(op("win").executes(ctx -> {
                    TideHeart heart = heart(ctx);
                    heart.setHealth(1f);
                    DamageSource source = ctx.getSource().getEntity() instanceof ServerPlayer p
                            ? heart.damageSources().playerAttack(p) : heart.damageSources().genericKill();
                    heart.forcePhase(2);
                    heart.hurt(source, 1000f);
                    return 1;
                }))
                .then(op("reset").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    java.util.UUID group = group(ctx);
                    if (TideHeartBattle.defeated(back, group)) {
                        // 倒したあとなら、そのグループの試練ごと戻す (心臓の撃破はグループの記録にある)
                        jp.revati.skyabyss.titan.tide.back.TideBackTrials.resetGroup(back, group, true, null, true);
                    } else {
                        TideHeartBattle.abandon(back, group);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("tide heart: reset"), true);
                    return 1;
                }))
                .then(op("rebuild").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    TideHeartBattle.releaseAll(back);
                    TideHeartArena.build(back);
                    ctx.getSource().sendSuccess(() -> Component.literal("tide heart: chamber rebuilt"), true);
                    return 1;
                }))
                .then(op("shot").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            TideHeartNetwork.shot(net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        }))))
                .then(op("status").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    java.util.UUID group = group(ctx);
                    TideHeart heart = TideHeartBattle.current(back, group);
                    String s = "active=" + TideHeartBattle.room(group).active() + " gathering=" + TideHeartBattle.room(group).gathering()
                            + " defeated=" + TideHeartBattle.defeated(back, group) + " owner=" + TideHeartBattle.owner()
                            + " built=" + TideHeartArena.isBuilt(back)
                            + " flooding=" + TideHeartArena.flooding()
                            + (heart == null ? "" : " phase=" + (heart.getPhase() + 1) + " hp=" + Math.round(heart.getHealth()) + "/" + Math.round(heart.getMaxHealth())
                                    + " x" + heart.toughness() + " action=" + heart.currentAction());
                    ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))));
    }

    private static ServerLevel back(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = ctx.getSource().getServer().getLevel(TideTravel.TIDE_BACK);
        if (back == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("tide_back is missing")).create();
        }
        return back;
    }

    // コマンドを打った人のグループ (進みと戦いはグループごと)
    private static java.util.UUID group(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return jp.revati.skyabyss.titan.group.Groups.keyOf(ctx.getSource().getPlayerOrException());
    }

    private static TideHeart heart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        TideHeart heart = TideHeartBattle.current(back(ctx), group(ctx));
        if (heart == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("no tide heart is fighting (use /skyabyss tideheart start)")).create();
        }
        return heart;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (player.level().dimension() != TideTravel.TIDE_BACK && !TideTravel.board(player)) {
            return 0;
        }
        TideHeartBattle.descend(back(ctx), player);
        player.sendSystemMessage(ModText.hint("message.skyabyss.tide_heart.go"));
        return 1;
    }
}

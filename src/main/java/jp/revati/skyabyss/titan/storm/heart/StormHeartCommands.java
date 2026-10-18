package jp.revati.skyabyss.titan.storm.heart;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import java.util.UUID;

import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
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

// 雷翼の心臓のテスト用コマンド (OP)。
// /skyabyss stormheart go           : 竜骨の前室へ (嵐の上へ渡る。部屋がなければ建てる。乗る資格は見ない)
// /skyabyss stormheart start        : 集合を待たず、部屋の中の人で戦いを始める
// /skyabyss stormheart phase <1-3>  : 段階を決める (体力もその段階の頭に合わせる)
// /skyabyss stormheart action <技>  : 技を今すぐ使わせる (summon, blade, chain, wingbeat, charge, feather, flash, web, barrage, roar)
// /skyabyss stormheart split        : 4 本の柱を満たしたことにする (翼が裂ける)
// /skyabyss stormheart win          : 心臓を倒したことにする (報酬・進み・記録まで通す)
// /skyabyss stormheart reset        : 打った人のグループの戦いを放棄して戻す。倒したあとなら、心臓をもう一度挑めるようにする
// /skyabyss stormheart rebuild      : 部屋を建て直す
// /skyabyss stormheart status       : 今の様子
// /skyabyss stormheart shot <人> <名前> : その人の画面を run/screenshots/<名前>.png に写す (キーを送らずに確かめるため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormHeartCommands {
    private StormHeartCommands() {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(op(SkyAbyss.MODID).then(op("stormheart")
                .then(op("go").executes(StormHeartCommands::go))
                .then(op("start").executes(ctx -> {
                    boolean ok = StormHeartBattle.startNow(back(ctx), Groups.keyOf(ctx.getSource().getPlayerOrException()));
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "storm heart: started" : "storm heart: already active or failed"), true);
                    return ok ? 1 : 0;
                }))
                .then(op("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                    StormHeart heart = heart(ctx);
                    int n = IntegerArgumentType.getInteger(ctx, "n");
                    float frac = n == 1 ? 1f : n == 2 ? 0.6f : 0.3f;
                    heart.setHealth(heart.getMaxHealth() * frac);
                    heart.forcePhase(n - 1);
                    StormHeartArena.resetRods(back(ctx));
                    ctx.getSource().sendSuccess(() -> Component.literal("storm heart: phase " + n), true);
                    return 1;
                })))
                .then(op("action").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(StormHeart.Action.values()).filter(a -> a != StormHeart.Action.NONE).map(StormHeart.Action::key), b))
                        .executes(ctx -> {
                            StormHeart heart = heart(ctx);
                            StormHeart.Action action;
                            try {
                                action = StormHeart.Action.valueOf(StringArgumentType.getString(ctx, "name").toUpperCase(java.util.Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("unknown move")).create();
                            }
                            heart.forceAction(action);
                            ctx.getSource().sendSuccess(() -> Component.literal("storm heart: " + action.key()), true);
                            return 1;
                        })))
                .then(op("split").executes(ctx -> {
                    StormHeart heart = heart(ctx);
                    ServerLevel back = back(ctx);
                    heart.forcePhase(0);
                    for (int i = 0; i < 4; i++) {
                        while (StormHeartArena.charge(i) < StormHeartArena.ROD_STAGES) {
                            StormHeartArena.addCharge(back, i);
                        }
                    }
                    heart.forceSplitForTest(back);
                    ctx.getSource().sendSuccess(() -> Component.literal("storm heart: wings split"), true);
                    return 1;
                }))
                .then(op("win").executes(ctx -> {
                    StormHeart heart = heart(ctx);
                    heart.setHealth(1f);
                    DamageSource source = ctx.getSource().getEntity() instanceof ServerPlayer p
                            ? heart.damageSources().playerAttack(p) : heart.damageSources().genericKill();
                    heart.forcePhase(2);
                    heart.invulnerableTime = 0;
                    heart.hurt(source, 1000f);
                    return 1;
                }))
                .then(op("reset").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    UUID group = Groups.keyOf(ctx.getSource().getPlayerOrException());
                    if (StormProgress.of(back, group).heartDefeated()) {
                        StormHeartBattle.reawaken(back, group);
                    } else {
                        StormHeartBattle.abandon(back, group);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("storm heart: reset"), true);
                    return 1;
                }))
                .then(op("rebuild").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    StormHeartBattle.abandonAll(back);
                    StormHeartArena.build(back);
                    ctx.getSource().sendSuccess(() -> Component.literal("storm heart: chamber rebuilt"), true);
                    return 1;
                }))
                .then(op("shot").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            StormHeartNetwork.shot(net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        }))))
                .then(op("status").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    StormHeart heart = StormHeartBattle.current(back);
                    StringBuilder rods = new StringBuilder();
                    for (int i = 0; i < 4; i++) {
                        rods.append(StormHeartArena.charge(i));
                    }
                    UUID group = Groups.keyOf(ctx.getSource().getPlayerOrException());
                    String s = "active=" + StormHeartBattle.room(group).active() + " gathering=" + StormHeartBattle.room(group).gathering()
                            + " defeated=" + StormProgress.of(back, group).heartDefeated() + " built=" + StormHeartArena.isBuilt(back)
                            + " room owner=" + jp.revati.skyabyss.titan.group.RoomLocks.owner(StormHeartBattle.ROOM)
                            + " rods=" + rods
                            + (heart == null ? "" : " phase=" + (heart.getPhase() + 1) + " hp=" + Math.round(heart.getHealth()) + "/" + Math.round(heart.getMaxHealth())
                                    + " x" + heart.toughness() + " action=" + heart.currentAction().key() + " split=" + heart.splitTicks()
                                    + String.format(" y=%.1f", heart.getY()));
                    ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))));
    }

    private static ServerLevel back(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = StormHeartBattle.back(ctx.getSource().getServer());
        if (back == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("storm_back is missing")).create();
        }
        return back;
    }

    private static StormHeart heart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        StormHeart heart = StormHeartBattle.current(back(ctx));
        if (heart == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("no storm heart is fighting (use /skyabyss stormheart start)")).create();
        }
        return heart;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StormHeartBattle.descend(back(ctx), player);
        player.sendSystemMessage(ModText.hint("message.skyabyss.storm_heart.go"));
        return 1;
    }
}

package jp.revati.skyabyss.titan.ember.heart;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 余燼の心臓のテスト用コマンド (OP)。
// /skyabyss emberheart go               : 熾の底の前室へ (余燼の背へ渡ってから。部屋がなければ建てる)
// /skyabyss emberheart start            : 集合を待たず、部屋の中の人で戦いを始める
// /skyabyss emberheart phase <1-3>      : 段階を決める (体力もその段階の頭に合わせる)
// /skyabyss emberheart action <技>      : 技を今すぐ使わせる (breath, pulse, orb, floor, gale, pellet, chill, embers, fading, veil, roar)
// /skyabyss emberheart sink <0-5>       : 区画を一つ割る (予告 2 秒のあと 8 秒沈む)
// /skyabyss emberheart win              : 心臓を倒したことにする (報酬・進み・記録まで通す)
// /skyabyss emberheart reset            : 戦いを放棄して戻す。倒したあとなら、心臓をもう一度挑めるようにする
// /skyabyss emberheart rebuild          : 部屋を建て直す
// /skyabyss emberheart status           : 今の様子
// /skyabyss emberheart shot <人> <名前>  : その人の画面を run/screenshots/<名前>.png に写す (キーを送らずに確かめるため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberHeartCommands {
    private EmberHeartCommands() {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(op(SkyAbyss.MODID).then(op("emberheart")
                .then(op("go").executes(EmberHeartCommands::go))
                .then(op("start").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    boolean ok = ctx.getSource().getPlayer() != null
                            ? EmberHeartBattle.startNow(back, jp.revati.skyabyss.titan.group.Groups.keyOf(ctx.getSource().getPlayer()))
                            : EmberHeartBattle.startNow(back);
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "ember heart: started" : "ember heart: already active or failed"), true);
                    return ok ? 1 : 0;
                }))
                .then(op("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                    EmberHeart heart = heart(ctx);
                    int n = IntegerArgumentType.getInteger(ctx, "n");
                    float frac = n == 1 ? 1f : n == 2 ? 0.6f : 0.3f;
                    heart.setHealth(heart.getMaxHealth() * frac);
                    heart.forcePhase(n - 1);
                    ctx.getSource().sendSuccess(() -> Component.literal("ember heart: phase " + n), true);
                    return 1;
                })))
                .then(op("action").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(EmberHeart.Action.values()).filter(a -> a != EmberHeart.Action.NONE)
                                        .map(a -> a.name().toLowerCase(Locale.ROOT)), b))
                        .executes(ctx -> {
                            EmberHeart heart = heart(ctx);
                            EmberHeart.Action action = EmberHeart.Action.valueOf(StringArgumentType.getString(ctx, "name").toUpperCase(Locale.ROOT));
                            heart.forceAction(action);
                            ctx.getSource().sendSuccess(() -> Component.literal("ember heart: " + action), true);
                            return 1;
                        })))
                .then(op("sink").then(Commands.argument("k", IntegerArgumentType.integer(0, EmberHeartArena.SECTIONS - 1)).executes(ctx -> {
                    ServerLevel back = back(ctx);
                    boolean ok = EmberHeartArena.crack(back, IntegerArgumentType.getInteger(ctx, "k"));
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "ember heart: cracking" : "ember heart: could not crack (already or at the limit)"), true);
                    return ok ? 1 : 0;
                })))
                .then(op("win").executes(ctx -> {
                    EmberHeart heart = heart(ctx);
                    heart.setHealth(1f);
                    DamageSource source = ctx.getSource().getEntity() instanceof ServerPlayer p
                            ? heart.damageSources().playerAttack(p) : heart.damageSources().genericKill();
                    heart.forcePhase(2);
                    heart.hurt(source, 1000f);
                    return 1;
                }))
                .then(op("reset").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    if (EmberHeartBattle.isDefeated(back)) {
                        EmberHeartBattle.reawakenForTest(back);
                    } else {
                        EmberHeartBattle.abandon(back);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("ember heart: reset"), true);
                    return 1;
                }))
                .then(op("rebuild").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    EmberHeartBattle.abandon(back);
                    EmberHeartArena.build(back);
                    ctx.getSource().sendSuccess(() -> Component.literal("ember heart: chamber rebuilt"), true);
                    return 1;
                }))
                .then(op("shot").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            EmberHeartNetwork.shot(EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "name"));
                            return 1;
                        }))))
                .then(op("status").executes(ctx -> {
                    ServerLevel back = back(ctx);
                    EmberHeart heart = EmberHeartBattle.current(back);
                    StringBuilder sections = new StringBuilder();
                    for (int k = 0; k < EmberHeartArena.SECTIONS; k++) {
                        sections.append(EmberHeartArena.sunk(k) ? 'S' : EmberHeartArena.cracking(k) ? 'c' : '.');
                    }
                    String s = "active=" + EmberHeartBattle.room().active() + " gathering=" + EmberHeartBattle.room().gathering()
                            + " defeated=" + EmberHeartBattle.isDefeated(back) + " built=" + EmberHeartArena.isBuilt(back)
                            + " cold=" + EmberHeartArena.isCold() + " sections=" + sections
                            + (heart == null ? "" : " phase=" + (heart.getPhase() + 1) + " hp=" + Math.round(heart.getHealth()) + "/" + Math.round(heart.getMaxHealth())
                                    + " x" + heart.toughness() + " action=" + heart.currentAction() + " embers=" + heart.emberCount()
                                    + " chilled=" + heart.chilledPlayers().size());
                    ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))));
    }

    private static ServerLevel back(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = ctx.getSource().getServer().getLevel(EmberIds.BACK);
        if (back == null) {
            throw new SimpleCommandExceptionType(Component.literal("ember_back is missing")).create();
        }
        return back;
    }

    private static EmberHeart heart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        EmberHeart heart = EmberHeartBattle.current(back(ctx));
        if (heart == null) {
            throw new SimpleCommandExceptionType(Component.literal("no ember heart is fighting (use /skyabyss emberheart start)")).create();
        }
        return heart;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel back = back(ctx);
        EmberHeat.reset(player);
        EmberHeartBattle.descend(back, player);
        player.sendSystemMessage(ModText.hint(EmberIds.LANG_HEART + "go"));
        return 1;
    }
}

package jp.revati.skyabyss.titan.storm.raiju;

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
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 雷獣のテスト用コマンド (OP)。
// /skyabyss raiju go              : 巣の前室へ (背中がまだ無ければ、オーバーワールドの仮の巣を建てて、そこへ)
// /skyabyss raiju start           : 集合を待たず、巣の中の人で戦いを始める (倒したあとなら、もう一度挑めるようにしてから)
// /skyabyss raiju win             : 雷獣を倒したことにする (報酬・記録まで通す)
// /skyabyss raiju reset           : 戦いを放棄し、倒した記録も戻す (背中のリセットと同じ)
// /skyabyss raiju rebuild         : 巣を建て直す
// /skyabyss raiju action <技>     : 技を今すぐ使わせる (rail, descend, pounce, claw, roar, return)
// /skyabyss raiju ground <1-6>    : 柱を接地させる (栓を引いたのと同じ)
// /skyabyss raiju status          : 今の様子
// /skyabyss raiju shot <人> <名前> : その人の画面を run/screenshots/<名前>.png に写す (キーを送らずに確かめるため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaijuCommands {
    private RaijuCommands() {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(op(SkyAbyss.MODID).then(op("raiju")
                .then(op("go").executes(RaijuCommands::go))
                .then(op("start").executes(ctx -> {
                    boolean ok = RaijuBattle.startNow(ctx.getSource().getServer(), Groups.keyOf(ctx.getSource().getPlayerOrException()));
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "raiju: started" : "raiju: already active or failed"), true);
                    return ok ? 1 : 0;
                }))
                .then(op("win").executes(ctx -> {
                    Raiju raiju = raiju(ctx);
                    DamageSource source = ctx.getSource().getEntity() instanceof ServerPlayer p
                            ? raiju.damageSources().playerAttack(p) : raiju.damageSources().genericKill();
                    raiju.setHealth(1f);
                    raiju.invulnerableTime = 0;
                    if (!raiju.hurt(source, 1000f) || raiju.isAlive()) {
                        raiju.hurt(raiju.damageSources().genericKill(), 1000f);
                    }
                    return 1;
                }))
                .then(op("reset").executes(ctx -> {
                    RaijuBattle.reawaken(ctx.getSource().getServer(), Groups.keyOf(ctx.getSource().getPlayerOrException()));
                    ctx.getSource().sendSuccess(() -> Component.literal("raiju: reset"), true);
                    return 1;
                }))
                .then(op("rebuild").executes(ctx -> {
                    RaijuBattle.abandonAll(ctx.getSource().getServer());
                    RaijuNest.build(RaijuNest.site(ctx.getSource().getServer()));
                    ctx.getSource().sendSuccess(() -> Component.literal("raiju: nest rebuilt"), true);
                    return 1;
                }))
                .then(op("action").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Raiju.Action.values())
                                .filter(a -> a != Raiju.Action.NONE).map(a -> a.name().toLowerCase(Locale.ROOT)), b))
                        .executes(ctx -> {
                            Raiju raiju = raiju(ctx);
                            Raiju.Action a = Raiju.Action.valueOf(StringArgumentType.getString(ctx, "name").toUpperCase(Locale.ROOT));
                            raiju.forceAction(a);
                            ctx.getSource().sendSuccess(() -> Component.literal("raiju: " + a), true);
                            return 1;
                        })))
                .then(op("ground").then(Commands.argument("rod", IntegerArgumentType.integer(1, RaijuNest.RODS)).executes(ctx -> {
                    int i = IntegerArgumentType.getInteger(ctx, "rod") - 1;
                    boolean ok = RaijuNest.pull(RaijuNest.site(ctx.getSource().getServer()), i, null);
                    ctx.getSource().sendSuccess(() -> Component.literal("raiju: rod " + (i + 1) + (ok ? " grounded" : " could not be grounded now")), true);
                    return ok ? 1 : 0;
                })))
                .then(op("shot").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                            jp.revati.skyabyss.titan.tide.heart.TideHeartNetwork.shot(EntityArgument.getPlayer(ctx, "player"),
                                    StringArgumentType.getString(ctx, "name"));
                            return 1;
                        }))))
                .then(op("status").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    RaijuNest.Site site = RaijuNest.site(server);
                    Raiju r = RaijuBattle.current(server);
                    StringBuilder rods = new StringBuilder();
                    for (int i = 0; i < RaijuNest.RODS; i++) {
                        rods.append(StormRods.isGrounded(site.rod(i)) ? 'G' : '-');
                    }
                    java.util.UUID group = Groups.keyOf(ctx.getSource().getPlayerOrException());
                    String s = "site=" + site.level().dimension().location() + " " + site.center()
                            + " built=" + RaijuNest.isBuilt(site) + " active=" + RaijuBattle.room(group).active()
                            + " gathering=" + RaijuBattle.room(group).gathering() + " defeated=" + RaijuBattle.defeated(site.level(), group)
                            + " nest owner=" + jp.revati.skyabyss.titan.group.RoomLocks.owner(RaijuBattle.ROOM)
                            + " rods=" + rods
                            + (r == null ? "" : " hp=" + Math.round(r.getHealth()) + "/" + Math.round(r.getMaxHealth()) + " x" + r.multiplier()
                                    + " mode=" + r.mode() + " action=" + r.currentAction() + " rod=" + (r.rod() + 1) + " angry=" + r.isAngry());
                    ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))));
    }

    private static Raiju raiju(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Raiju r = RaijuBattle.current(ctx.getSource().getServer());
        if (r == null) {
            throw new SimpleCommandExceptionType(Component.literal("no raiju is fighting (use /skyabyss raiju start)")).create();
        }
        return r;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        RaijuNest.Site site = RaijuNest.site(ctx.getSource().getServer());
        RaijuNest.ensureBuilt(site);
        Vec3 at = site.anteSpawn();
        player.fallDistance = 0;
        player.teleportTo(site.level(), at.x, at.y, at.z, -90f, 0f);
        player.sendSystemMessage(ModText.hint("message.skyabyss.raiju.go"));
        return 1;
    }
}

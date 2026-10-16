package jp.revati.skyabyss.titan.ember.smith;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import jp.revati.skyabyss.SkyAbyss;
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

// 灰の鍛え手のテスト用コマンド (OP)。
// /skyabyss smith go             : 鍛え手の間の前 (鍛えの間の奥) へ。部屋が建っていなければ建てる
// /skyabyss smith start          : 集合を待たず、部屋の中の人で戦いを始める
// /skyabyss smith win            : 鍛え手を倒したことにする (報酬・記録まで通す)
// /skyabyss smith reset          : 戦いを放棄し、入口を開け、炉の扉を閉じる
// /skyabyss smith rebuild        : 部屋を建て直す
// /skyabyss smith action <技>    : 技を今すぐ使わせる (slam, breath, quench, sparks, rage)
// /skyabyss smith status         : 今の様子
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AshSmithCommands {
    private AshSmithCommands() {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(op(SkyAbyss.MODID).then(op("smith")
                .then(op("go").executes(AshSmithCommands::go))
                .then(op("start").executes(ctx -> {
                    boolean ok = AshSmithBattle.startNow(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "smith: started" : "smith: already active or failed"), true);
                    return ok ? 1 : 0;
                }))
                .then(op("win").executes(ctx -> {
                    AshSmith smith = smith(ctx);
                    DamageSource source = ctx.getSource().getEntity() instanceof ServerPlayer p
                            ? smith.damageSources().playerAttack(p) : smith.damageSources().genericKill();
                    smith.setHealth(1f);
                    smith.invulnerableTime = 0;
                    if (!smith.hurt(source, 1000f) || smith.isAlive()) {
                        smith.hurt(smith.damageSources().genericKill(), 1000f);
                    }
                    return 1;
                }))
                .then(op("reset").executes(ctx -> {
                    AshSmithBattle.reawaken(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("smith: reset"), true);
                    return 1;
                }))
                .then(op("rebuild").executes(ctx -> {
                    ServerLevel level = AshSmithBattle.level(ctx.getSource().getServer());
                    if (level == null) {
                        return 0;
                    }
                    AshSmithBattle.abandon(ctx.getSource().getServer());
                    AshSmithArena.build(level, AshSmithArena.BOX);
                    ctx.getSource().sendSuccess(() -> Component.literal("smith: arena rebuilt"), true);
                    return 1;
                }))
                .then(op("action").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AshSmith.Action.values())
                                .filter(a -> a != AshSmith.Action.NONE).map(a -> a.name().toLowerCase(Locale.ROOT)), b))
                        .executes(ctx -> {
                            AshSmith smith = smith(ctx);
                            AshSmith.Action a = AshSmith.Action.valueOf(StringArgumentType.getString(ctx, "name").toUpperCase(Locale.ROOT));
                            smith.forceAction(a);
                            ctx.getSource().sendSuccess(() -> Component.literal("smith: " + a), true);
                            return 1;
                        })))
                .then(op("status").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    ServerLevel level = AshSmithBattle.level(server);
                    AshSmith s = AshSmithBattle.current(server);
                    String text = "built=" + (level != null && AshSmithArena.isBuilt(level)) + " active=" + AshSmithBattle.room().active()
                            + " gathering=" + AshSmithBattle.room().gathering() + " done=" + (level != null && AshSmithBattle.done(level))
                            + " sealed=" + (level != null && AshSmithArena.sealed(level))
                            + (s == null ? "" : " hp=" + Math.round(s.getHealth()) + "/" + Math.round(s.getMaxHealth()) + " x" + s.multiplier()
                                    + " action=" + s.currentAction() + " raged=" + s.raged() + " weak=" + s.isWeak() + " cold=" + s.coldSlams()
                                    + " steam=" + s.steamTicks());
                    ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                    return 1;
                }))));
    }

    private static AshSmith smith(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AshSmith s = AshSmithBattle.current(ctx.getSource().getServer());
        if (s == null) {
            throw new SimpleCommandExceptionType(Component.literal("no ash smith is fighting (use /skyabyss smith start)")).create();
        }
        return s;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = AshSmithBattle.level(ctx.getSource().getServer());
        if (level == null) {
            return 0;
        }
        if (!AshSmithArena.isBuilt(level)) {
            AshSmithArena.build(level, AshSmithArena.BOX);
        }
        double x = AshSmithArena.CX + 0.5;
        double z = AshSmithArena.ANTE.maxZ() - 1.5;
        player.fallDistance = 0;
        player.teleportTo(level, x, AshSmithArena.FLOOR + 1, z, 0f, 0f);
        return 1;
    }
}

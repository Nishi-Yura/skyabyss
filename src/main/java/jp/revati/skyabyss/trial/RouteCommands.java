package jp.revati.skyabyss.trial;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;

// 順路 (docs/design/32): あきらめの返事 (/skyroute yes|no。チャットの [はい] [いいえ] が押す) と、管理者の確認 (status・start・clear・shrine <巨神>・shard <巨神>)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RouteCommands {
    private RouteCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyroute")
                .then(Commands.literal("yes").executes(ctx -> reply(ctx, true)))
                .then(Commands.literal("no").executes(ctx -> reply(ctx, false)))
                .then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(RouteCommands::status))
                .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(RouteCommands::start))
                .then(Commands.literal("enter").requires(s -> s.hasPermission(2)).executes(RouteCommands::enter))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(RouteCommands::clear))
                // 開発・撮影 (R6 の通しの撮影): 今の段を越えたことにする (本物と同じ 4 秒のあと次の段へ運ばれる)
                .then(Commands.literal("pass").requires(s -> s.hasPermission(2)).executes(RouteCommands::pass))
                // 開発・撮影: 初回の手引き (十字の下の操作の案内) を済ませる (序章を飛ばした撮影で残るため)
                .then(Commands.literal("tidyhud").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    for (jp.revati.skyabyss.tutorial.TutorialHints.Hint h : jp.revati.skyabyss.tutorial.TutorialHints.ALL) {
                        jp.revati.skyabyss.tutorial.TutorialHints.start(p, h.id());
                        jp.revati.skyabyss.tutorial.TutorialHints.done(p, h.id());
                    }
                    return 1;
                }))
                // 開発・撮影: このグループをこの背の周回のグループにする (心臓を一度止めた扱い。祠も使える)
                .then(Commands.literal("replayable").requires(s -> s.hasPermission(2)).executes(RouteCommands::replayable))
                // 開発・撮影: 地上の祠へ行き、歌片を持って祠を使う (周回の順路の入口)
                .then(Commands.literal("useshrine").requires(s -> s.hasPermission(2)).then(Commands.argument("titan", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Titans.all(), b))
                        .executes(ctx -> useShrine(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "titan")))))
                .then(Commands.literal("shrine").requires(s -> s.hasPermission(2)).then(Commands.argument("titan", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Titans.all(), b))
                        .executes(ctx -> shrine(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "titan")))))
                .then(Commands.literal("shard").requires(s -> s.hasPermission(2)).then(Commands.argument("titan", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Titans.all(), b))
                        .executes(ctx -> shard(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "titan"))))));
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, boolean yes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return TrialRoute.reply(p, yes) ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel back = p.serverLevel();
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.literal("not on a titan back"));
            return 0;
        }
        java.util.UUID group = Groups.keyOf(p);
        ctx.getSource().sendSuccess(() -> Component.literal("route of the " + titan + ": " + TrialRoute.describe(back, group)
                + " pending " + TrialRoute.pendingCount() + " abandon " + TrialRoute.abandonPending(group)), false);
        return 1;
    }

    // 入口の箱のまんなかへ立つ (撮影と確かめ用。初回なら予告のあと段 1 へ運ばれる)
    private static int enter(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String titan = Titans.titanOfBack(p.serverLevel().dimension());
        net.minecraft.world.phys.AABB box = titan == null ? null : TrialRoute.entryBox(titan);
        if (box == null) {
            ctx.getSource().sendFailure(Component.literal("not on a titan back"));
            return 0;
        }
        net.minecraft.world.phys.Vec3 c = box.getCenter();
        p.teleportTo(p.serverLevel(), c.x, box.minY, c.z, p.getYRot(), p.getXRot());
        ctx.getSource().sendSuccess(() -> Component.literal("entered the route box of the " + titan + " at " + net.minecraft.core.BlockPos.containing(c).toShortString()), false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel back = p.serverLevel();
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.literal("not on a titan back"));
            return 0;
        }
        TrialRoute.start(back, Groups.keyOf(p), titan, TrialRoute.present(back, Groups.keyOf(p)));
        return 1;
    }

    // 開発用: 祠を建てて (まだなら) そこへ行く
    private static int shrine(CommandContext<CommandSourceStack> ctx, String titan) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        net.minecraft.core.BlockPos at = jp.revati.skyabyss.titan.shrine.TitanShrine.ensure(p.server, titan);
        jp.revati.skyabyss.titan.shrine.TitanShrine.sendTo(p, titan);
        ctx.getSource().sendSuccess(() -> Component.literal("shrine of the " + titan + " at " + at.toShortString()), false);
        return 1;
    }

    // 開発用: 歌片を一つ
    private static int shard(CommandContext<CommandSourceStack> ctx, String titan) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        p.getInventory().add(new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.registry.ModItems.songShard(titan)));
        return 1;
    }

    private static int pass(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel back = p.serverLevel();
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.literal("not on a titan back"));
            return 0;
        }
        String what = TrialRoute.devPass(back, Groups.keyOf(p), titan, p);
        ctx.getSource().sendSuccess(() -> Component.literal("route of the " + titan + ": " + what), false);
        return 1;
    }

    private static int replayable(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel back = p.serverLevel();
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.literal("not on a titan back"));
            return 0;
        }
        java.util.UUID g = Groups.keyOf(p);
        jp.revati.skyabyss.titan.group.BackGroupData.Record r = jp.revati.skyabyss.titan.group.BackGroupData.get(back).of(g);
        r.markFirstCleared();
        if (r.getInt(TrialData.DONE) < 1) {
            r.putInt(TrialData.DONE, 1);
        }
        r.remove(TrialData.DRAW);
        // 撮影のための引き: 歩は原型 (周回の前の道を通る)、闘は中ボスの原型でないもの (中ボスの段を通る)。智は引いたまま
        TrialDraw.Draw d = TrialRoute.walkOriginalDraw(titan, TrialData.current(back, g));
        r.putString(TrialData.DRAW, d.encode());
        r.changed();
        jp.revati.skyabyss.progress.ProgressData.get(p.server).markPersonal(p.getUUID(), titan);
        jp.revati.skyabyss.titan.TitanVisibility.clearCache();
        ctx.getSource().sendSuccess(() -> Component.literal("the group now replays the " + titan), false);
        return 1;
    }

    private static int useShrine(CommandContext<CommandSourceStack> ctx, String titan) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        net.minecraft.core.BlockPos at = jp.revati.skyabyss.titan.shrine.TitanShrine.ensure(p.server, titan);
        jp.revati.skyabyss.titan.shrine.TitanShrine.sendTo(p, titan);
        net.minecraft.world.item.ItemStack shard = new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.registry.ModItems.songShard(titan));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, shard);
        jp.revati.skyabyss.titan.shrine.TitanShrine.activate(p, titan, at);
        ctx.getSource().sendSuccess(() -> Component.literal("used the shrine of the " + titan + " at " + at.toShortString()), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TrialRoute.finish(p.serverLevel(), Groups.keyOf(p));
        return 1;
    }
}

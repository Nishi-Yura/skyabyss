package jp.revati.skyabyss.party;

import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// パーティのコマンド (誰でも使える)。画面 (O キー) からも同じ操作ができる
// /party                  : 仲間の一覧
// /party invite <人>      : 招待する (まだ組んでいなければ、自分がリーダーになる)
// /party accept | decline : 招待を受ける・断る
// /party leave            : 抜ける (二人のパーティなら解散)
// /party kick <名前>      : 外す (リーダー)
// /party leader <名前>    : リーダーを渡す (リーダー)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PartyCommands {
    private PartyCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("party")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> ok(Parties.invite(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("accept").executes(ctx -> ok(Parties.accept(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("decline").executes(ctx -> ok(Parties.decline(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("leave").executes(ctx -> ok(Parties.leave(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("kick").then(memberArg().executes(ctx -> member(ctx, true))))
                .then(Commands.literal("leader").then(memberArg().executes(ctx -> member(ctx, false)))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> memberArg() {
        return Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> {
            ServerPlayer p = c.getSource().getPlayer();
            PartyData.Party party = p == null ? null : Parties.of(p);
            return SharedSuggestionProvider.suggest(party == null ? java.util.List.of() : party.names.values(), b);
        });
    }

    private static int member(CommandContext<CommandSourceStack> ctx, boolean kick) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        PartyData.Party party = Parties.of(p);
        UUID target = party == null ? null : Parties.memberByName(party, name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.translatable("message.skyabyss.party.not_member", name));
            return 0;
        }
        return ok(kick ? Parties.kick(p, target, name) : Parties.promote(p, target, name));
    }

    private static int ok(boolean done) {
        return done ? 1 : 0;
    }

    private static int list(ServerPlayer player) {
        PartyData.Party party = Parties.of(player);
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.skyabyss.party.none_help").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        MutableComponent text = Component.translatable("message.skyabyss.party.list_head", party.members.size(), Parties.MAX_MEMBERS,
                Math.round(Parties.shareRate(player.server) * 100)).withStyle(ChatFormatting.AQUA);
        for (UUID id : party.members) {
            ServerPlayer m = player.server.getPlayerList().getPlayer(id);
            String name = party.names.getOrDefault(id, "?");
            MutableComponent row = Component.literal("\n " + (party.isLeader(id) ? "★ " : "・ ") + name);
            if (m == null) {
                row.append(Component.translatable("message.skyabyss.party.offline")).withStyle(ChatFormatting.DARK_GRAY);
            } else {
                row.append(Component.literal("  Lv " + Growth.get(m).level + "  " + jp.revati.skyabyss.Hp.number(m.getHealth()) + "/" + jp.revati.skyabyss.Hp.of(m.getMaxHealth())))
                        .withStyle(ChatFormatting.WHITE);
            }
            text.append(row);
        }
        player.sendSystemMessage(text);
        return party.members.size();
    }
}

package jp.revati.skyabyss.rebirth;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// コマンド。
//   /skyabyss rebirth title <id|none>      称号を選ぶ (画面からも選べる)
//   /skyabyss rebirth titles               解けた称号の一覧
//   /skyabyss rebirth board <show|hide>    順位表に載せる / 断る
//   /skyabyss dev rebirth <n>              (開発用・権限 2) 転生の回数を n にする。転生の本体ができるまでの仮の元 (RebirthView.isStub のとき)
//   /skyabyss dev rebirth_do               (開発用) 転生を一度終えた扱いにする (記録・称号・実績まで)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RebirthCommands {
    private RebirthCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        var root = Commands.literal(SkyAbyss.MODID);
        root.then(Commands.literal("rebirth")
                .then(Commands.literal("title").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(ctx, "id");
                    boolean ok = Rebirths.selectTitle(p, "none".equals(id) ? "" : id);
                    ctx.getSource().sendSystemMessage(Component.translatable(ok ? "message.skyabyss_rebirth.title_set" : "message.skyabyss_rebirth.title_locked"));
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("titles").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    long mask = RebirthView.data(p).unlocked();
                    for (RebirthTitles.Title t : RebirthTitles.all()) {
                        boolean has = RebirthTitles.has(mask, t);
                        ctx.getSource().sendSystemMessage(Component.literal((has ? "[o] " : "[ ] ") + t.id() + " ")
                                .append(Component.translatable("title.skyabyss_rebirth." + t.id() + (has ? "" : ".how"))));
                    }
                    return 1;
                }))
                .then(Commands.literal("board")
                        .then(Commands.literal("show").executes(ctx -> hidden(ctx.getSource(), false)))
                        .then(Commands.literal("hide").executes(ctx -> hidden(ctx.getSource(), true)))));
        if (!FMLEnvironment.production) {
            root.then(Commands.literal("dev").requires(s -> s.hasPermission(2))
                    .then(Commands.literal("rebirth").then(Commands.argument("n", IntegerArgumentType.integer(0, 99)).executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                        Rebirths.setCount(p, IntegerArgumentType.getInteger(ctx, "n"));
                        ctx.getSource().sendSystemMessage(Component.literal("rebirths = " + RebirthView.count(p)));
                        return RebirthView.count(p);
                    })))
                    .then(Commands.literal("rebirth_do").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                        Rebirths.onRebirth(p);
                        return RebirthView.count(p);
                    })));
        }
        event.getDispatcher().register(root);
    }

    private static int hidden(CommandSourceStack src, boolean hide) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Rebirths.setHidden(src.getPlayerOrException(), hide);
        src.sendSystemMessage(Component.translatable(hide ? "message.skyabyss_rebirth.board_hidden" : "message.skyabyss_rebirth.board_shown"));
        return 1;
    }
}

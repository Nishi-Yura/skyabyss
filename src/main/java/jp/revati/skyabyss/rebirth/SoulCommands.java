package jp.revati.skyabyss.rebirth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発・確かめ用の転生コマンド (管理者)。
// /skyabyss soul show <人>                 : 回数・魂盤・恩恵・欠片・余白を見る
// /skyabyss soul set <人> <0-20>           : 回数を決める (魂盤の節と恩恵を、腕と候補の順に埋める。装備・物語は触らない)
// /skyabyss soul do <人> <腕> <候補 0-2>   : 転生する (台座は問わない。結晶・Lv 50・戦いの外は問う)
// /skyabyss soul shards <人> <数>          : 導きの欠片を決める
// /skyabyss soul open <人>                 : 転生の画面を開かせる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SoulCommands {
    private SoulCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("soul")
                        .then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                            SoulData rd = Rebirth.data(p);
                            ctx.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + ": " + Rebirth.levelLabel(p) + " rebirths "
                                    + Rebirth.count(p) + " nodes " + rd.nodes + " boons " + rd.boons + " shards " + rd.shards + "/" + rd.shardNodes
                                    + " spare " + java.util.Arrays.toString(rd.spareItem) + java.util.Arrays.toString(rd.spareCount)
                                    + " damage +" + String.format("%.1f", Rebirth.effects(p).damage() * 100) + "%"), false);
                            return 1;
                        })))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, RebirthRules.MAX_REBIRTHS)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    set(p, IntegerArgumentType.getInteger(ctx, "n"));
                                    return 1;
                                }))))
                        .then(Commands.literal("do").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("arm", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(SoulArm.values()).map(a -> a.id), b))
                                        .then(Commands.argument("card", IntegerArgumentType.integer(0, 2)).executes(ctx -> {
                                            ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                            Boon boon = Rebirth.offer(p).get(IntegerArgumentType.getInteger(ctx, "card"));
                                            Rebirth.Result r = Rebirth.perform(p, StringArgumentType.getString(ctx, "arm"), boon.id, false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("rebirth: " + r), false);
                                            return r == Rebirth.Result.OK ? 1 : 0;
                                        })))))
                        .then(Commands.literal("shards").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 99)).executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    Rebirth.data(p).shards = IntegerArgumentType.getInteger(ctx, "n");
                                    Rebirth.sync(p);
                                    return 1;
                                }))))
                        .then(Commands.literal("open").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(EntityArgument.getPlayer(ctx, "player"), new RebirthOpenPayload());
                            return 1;
                        })))));
    }

    // 回数を決める: 魂盤の節は腕を順にめぐり、恩恵は毎回の候補の 1 枚目。力の記録は作り直す
    static void set(ServerPlayer p, int n) {
        GrowthData g = Growth.get(p);
        SoulData rd = Rebirth.data(p);
        rd.nodes.clear();
        rd.boons.clear();
        rd.shardNodes = 0;
        g.rebirths = 0;
        for (int i = 0; i < n; i++) {
            rd.nodes.add(SoulArm.values()[(i * 5 + i / 6) % SoulArm.values().length].id);
            rd.boons.add(RebirthRules.offer(p.getUUID().getMostSignificantBits() ^ p.getUUID().getLeastSignificantBits(), i).get(0).id);
            g.rebirths++;
        }
        rd.sanitize(g.rebirths);
        Rebirth.sync(p);
        Growth.sync(p);
        Growth.applyModifiers(p);
    }
}

package jp.revati.skyabyss.titan.ember.back.trial;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 三つの試練のテスト用コマンド (OP。13 の 12章。画面の台本と手での確かめ用)。
// /skyabyss embertrial build         三つの部屋の中身を、余燼の背の箱に建てる (体がなくてもよい)
// /skyabyss embertrial reset         三つの部屋をリセットの状態へ戻す
// /skyabyss embertrial urn           灰の壺 (満) を受け取る
// /skyabyss embertrial key <hot|cold> 火の鍵 (焼き入れ前・焼き入れた) を受け取る
// /skyabyss embertrial coal [数]     熾を受け取る
// /skyabyss embertrial forge <熱>    大炉の熱を決める (0〜100)
// /skyabyss embertrial hearth <火>   火床の強さを決める (0〜100)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberTrialCommands {
    private EmberTrialCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(op(SkyAbyss.MODID).then(op("embertrial")
                .then(op("build").executes(EmberTrialCommands::build))
                .then(op("reset").executes(EmberTrialCommands::reset))
                .then(op("urn").executes(ctx -> give(ctx, new ItemStack(EmberTrialSetup.ASH_URN.get()))))
                .then(op("key").then(op("hot").executes(ctx -> hotKey(ctx))).then(op("cold").executes(ctx -> give(ctx, new ItemStack(EmberTrialSetup.FIRE_KEY.get())))))
                .then(op("coal").executes(ctx -> give(ctx, new ItemStack(EmberTrialSetup.LIVE_COAL.get(), 4)))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(
                                ctx -> give(ctx, new ItemStack(EmberTrialSetup.LIVE_COAL.get(), IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(op("forge").then(Commands.argument("heat", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                    Forge.setHeat(IntegerArgumentType.getInteger(ctx, "heat"));
                    Forge.stepHeat(ctx.getSource().getServer().getLevel(EmberIds.BACK), ctx.getSource().getServer().getLevel(EmberIds.BACK).getGameTime());
                    return 1;
                })))
                .then(op("hearth").then(Commands.argument("strength", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                    Hearth.setStrength(IntegerArgumentType.getInteger(ctx, "strength"));
                    Hearth.show(ctx.getSource().getServer().getLevel(EmberIds.BACK));
                    return 1;
                })))));
    }

    private static int build(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().getLevel(EmberIds.BACK);
        if (level == null) {
            return 0;
        }
        AshPathRoom.build(level, EmberBackLayout.WEST);
        ForgeRoom.build(level, EmberBackLayout.EAST);
        HearthRoom.build(level, EmberBackLayout.SOUTH);
        ctx.getSource().sendSuccess(() -> Component.literal("ember trial rooms built"), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().getLevel(EmberIds.BACK);
        if (level == null) {
            return 0;
        }
        AshPath.reset(level, false);
        Forge.reset(level, false);
        Hearth.reset(level, false);
        ctx.getSource().sendSuccess(() -> Component.literal("ember trials reset"), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ItemStack stack) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            return 0;
        }
        p.getInventory().add(stack);
        return 1;
    }

    private static int hotKey(CommandContext<CommandSourceStack> ctx) {
        ItemStack key = new ItemStack(EmberTrialSetup.FIRE_KEY_HOT.get());
        FireKeyItem.stamp(key, ctx.getSource().getLevel().getGameTime());
        return give(ctx, key);
    }
}

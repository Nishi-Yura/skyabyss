package jp.revati.skyabyss.titan.storm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.storm.back.StormBackBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 雷翼の巨神のテスト用コマンド (OP)。/skyabyss titan ... に、巨神の名前 storm を足す (12 の 12章)。
// 名前を省いたコマンドは岩鯨 (TitanEvents)、tide は TideCommands。/skyabyss titan leave は TitanEvents が、いる背中に合わせて戻す
// /skyabyss titan summon storm   : 自分から 160 ブロック先の空に呼ぶ
// /skyabyss titan rest storm     : 近くの峰に止まらせる (夕立の刻を待たない。3 分)
// /skyabyss titan rest storm here: その場で止まらせる
// /skyabyss titan board storm    : 背中へ直接渡る
// /skyabyss titan rebuild storm  : 背中の鳥を建て直す
// /skyabyss titan status storm   : 今の様子と、次の夕立の刻まで
// (titan storm calm|storm|cycle、reset storm、sleep storm は背中の担当が別に足す)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormCommands {
    // 止まらせる時間 (tick)
    private static final int FORCED_REST = 3600;

    private StormCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 親の節には executes を付けない (岩鯨・潮殻の同じ節へ合わさるため)
        dispatcher.register(op(SkyAbyss.MODID).then(op("titan")
                .then(op("summon").then(op("storm").executes(StormCommands::summon)))
                .then(op("rest").then(op("storm").executes(ctx -> rest(ctx, false))
                        .then(op("here").executes(ctx -> rest(ctx, true)))))
                .then(op("board").then(op("storm").executes(ctx -> {
                    StormTravel.board(ctx.getSource().getPlayerOrException());
                    return 1;
                })))
                .then(op("rebuild").then(op("storm").executes(StormCommands::rebuild)))
                .then(op("status").then(op("storm").executes(StormCommands::status)))));
    }

    private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.server.overworld();
        if (player.level() != level) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.storm.overworld_only"));
            return 0;
        }
        float yaw = player.getYRot() * ((float) Math.PI / 180f);
        double x = player.getX() - Math.sin(yaw) * 160;
        double z = player.getZ() + Math.cos(yaw) * 160;
        StormWingEntity existing = StormKeeper.KEEPER.current(level);
        CompoundTag state = existing != null ? existing.saveState() : StormKeeper.KEEPER.slot(level).state;
        StormWingEntity titan = StormKeeper.KEEPER.spawn(level, x, z, state.isEmpty() ? null : state);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.storm.summoned", (int) titan.getX(), (int) titan.getZ()), true);
        return 1;
    }

    private static int rest(CommandContext<CommandSourceStack> ctx, boolean here) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StormWingEntity titan = StormKeeper.KEEPER.current(player.server.overworld());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.storm.missing"));
            return 0;
        }
        if (here) {
            titan.restHere(FORCED_REST);
        } else if (!titan.comeRest(player, FORCED_REST)) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.storm.no_peak"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.storm.resting"), true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = ctx.getSource().getServer().getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            return 0;
        }
        StormBackBuilder.build(back, TitanBackData.get(back));
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.storm.rebuilt"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        StormWingEntity titan = StormKeeper.KEEPER.current(level);
        long t = Math.floorMod(level.getDayTime(), 24000L);
        long until = Math.floorMod(StormWingEntity.WINDOW_START - t, 24000L) / 20;
        if (titan == null) {
            boolean spawned = StormKeeper.KEEPER.slot(level).spawned;
            ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.storm.status_absent", String.valueOf(spawned), until), false);
            return 1;
        }
        String mode = titan.modeName();
        Vec3 p = titan.position();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.storm.status", mode, (int) p.x, (int) p.y, (int) p.z, until), false);
        return 1;
    }
}

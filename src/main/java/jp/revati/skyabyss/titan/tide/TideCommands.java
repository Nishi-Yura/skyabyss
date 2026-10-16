package jp.revati.skyabyss.titan.tide;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
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

// 潮殻の巨神のテスト用コマンド (OP)。岩鯨の /skyabyss titan ... に、巨神の名前 tide を足す (08 の 12章)。
// 名前を省いたコマンドは今までどおり岩鯨 (TitanEvents)。/skyabyss titan leave は、いる背中に合わせて戻す
// /skyabyss titan summon tide  : 自分から 120 ブロック先の海に呼ぶ (海がなければ断る)
// /skyabyss titan rest tide    : 自分の近くの浅瀬へ寄せて休ませる (干潮の刻を待たない。3 分)
// /skyabyss titan rest tide here : その場ですぐに休ませる (門を試すため)
// /skyabyss titan board tide   : 背中へ直接渡る
// /skyabyss titan rebuild tide : 背中の (仮の) 島を建て直す
// /skyabyss titan status tide  : 今の様子と、次の干潮の刻まで
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideCommands {
    private TideCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 親の節 (summon など) には executes を付けない。岩鯨の同じ節へ合わさるとき、岩鯨のコマンドを上書きしないため
        dispatcher.register(op(SkyAbyss.MODID).then(op("titan")
                .then(op("summon").then(op("tide").executes(TideCommands::summon)))
                .then(op("rest").then(op("tide").executes(ctx -> rest(ctx, false))
                        .then(op("here").executes(ctx -> rest(ctx, true)))))
                .then(op("board").then(op("tide").executes(ctx -> {
                    TideTravel.board(ctx.getSource().getPlayerOrException());
                    return 1;
                })))
                .then(op("rebuild").then(op("tide").executes(TideCommands::rebuild)))
                .then(op("status").then(op("tide").executes(TideCommands::status)))));
    }

    private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.server.overworld();
        if (player.level() != level) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.tide.overworld_only"));
            return 0;
        }
        float yaw = player.getYRot() * ((float) Math.PI / 180f);
        double x = player.getX() - Math.sin(yaw) * 120;
        double z = player.getZ() + Math.cos(yaw) * 120;
        Vec3 at = TideOcean.walkable(level, x, z, 6) ? new Vec3(x, 0, z)
                : TideOcean.findOceanPoint(level, level.random, x, z, 0, 48, 6, 48);
        if (at == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.tide.no_sea"));
            return 0;
        }
        TideShellEntity existing = TideKeeper.KEEPER.current(level);
        CompoundTag state = existing != null ? existing.saveState() : TideKeeper.KEEPER.slot(level).state;
        TideShellEntity titan = TideKeeper.KEEPER.spawn(level, at.x, at.z, state.isEmpty() ? null : state);
        // 向こうを向かせる (プレイヤーから見て横向き。甲羅と首が見える)
        titan.setYRot(player.getYRot() + 90f);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tide.summoned", (int) at.x, (int) at.z), true);
        return 1;
    }

    private static int rest(CommandContext<CommandSourceStack> ctx, boolean here) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TideShellEntity titan = TideKeeper.KEEPER.current(player.server.overworld());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.tide.missing"));
            return 0;
        }
        if (here) {
            titan.restHere();
        } else if (!titan.comeRestNear(player)) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.tide.no_shallows"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tide.resting"), true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = ctx.getSource().getServer().getLevel(TideTravel.TIDE_BACK);
        if (back == null) {
            return 0;
        }
        TideBackBuilder.build(back, TitanBackData.get(back));
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tide.rebuilt"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        TideShellEntity titan = TideKeeper.KEEPER.current(level);
        long until = TideOcean.ticksUntilLowTide(level.getDayTime()) / 20;
        if (titan == null) {
            boolean spawned = TideKeeper.KEEPER.slot(level).spawned;
            ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tide.status_absent", String.valueOf(spawned), until), false);
            return 1;
        }
        String mode = titan.mode().name().toLowerCase(java.util.Locale.ROOT);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tide.status", mode, (int) titan.getX(), (int) titan.getY(),
                (int) titan.getZ(), until), false);
        return 1;
    }
}

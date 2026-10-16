package jp.revati.skyabyss.titan;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;


import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackBuilder;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 岩鯨の出現と、テスト用のコマンド。
// 3日目以降、オーバーワールドにいるプレイヤーの遠くに一度だけ現れ、こちらへ泳いでくる。
// 現れたあとの見守り (チャンクの読み込み、一体だけの決まり、見失ったときの呼び直し) は WhaleKeeper
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanEvents {
    private static final long FIRST_APPEARANCE_DAY = 2;

    private TitanEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        // 出たあとは、見守りが毎 tick 岩鯨を動かし続け、見失えば呼び直す
        WhaleKeeper.tick(level);
        if (level.getGameTime() % 200 != 0) {
            return;
        }
        TitanWorldData.Slot data = TitanWorldData.get(level).whale();
        // 経過日数は、時刻の操作 (/time set や昼夜の停止) に左右されないゲーム内の総 tick で数える
        if (data.spawned || level.getGameTime() / 24000L < FIRST_APPEARANCE_DAY) {
            return;
        }
        ServerPlayer player = WhaleKeeper.randomEligible(level);
        if (player == null) {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2;
        WhaleKeeper.spawn(level, player.getX() + Math.cos(angle) * WhaleKeeper.SPAWN_DISTANCE,
                player.getZ() + Math.sin(angle) * WhaleKeeper.SPAWN_DISTANCE, null);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    // /skyabyss titan summon : 目の前の空に岩鯨を呼ぶ
    // /skyabyss titan rest   : 岩鯨をすぐ近くへ降ろし、鎖を垂らさせる
    // /skyabyss titan board  : 直接背中へ乗る
    // /skyabyss titan leave  : 背中から地上へ戻る
    // /skyabyss titan rebuild: 背中を建て直し、試練の進み具合を最初に戻す (開発・テスト用)
    // /skyabyss titan reset  : 建物はそのままで、試練だけを最初に戻す
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("titan")
                        .then(Commands.literal("summon").executes(TitanEvents::summon))
                        .then(Commands.literal("rest").executes(TitanEvents::rest))
                        .then(Commands.literal("board").executes(TitanEvents::board))
                        .then(Commands.literal("rebuild").executes(TitanEvents::rebuild))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerLevel back = ctx.getSource().getServer().getLevel(TitanTravel.TITAN_BACK);
                            if (back == null) {
                                return 0;
                            }
                            TitanBackTrials.resetTrials(back);
                            ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.titan.reset"), true);
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            // 巨神の背にいれば、その巨神のもとへ戻す (背中の一覧 Titans に登録された道)。岩鯨の背は TitanTravel
                            String back = Titans.titanOfBack(player.level().dimension());
                            if (back != null) {
                                Titans.sendDown(player, back);
                            } else {
                                TitanTravel.leave(player);
                            }
                            return 1;
                        }))));
    }

    private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.server.overworld();
        // 前の岩鯨は、読み込まれていればここで消え、読み込まれていなければ次に読み込まれたときに取り消される
        StoneWhaleEntity existing = WhaleKeeper.current(level);
        CompoundTag state = existing != null ? existing.saveState() : null;
        float yaw = player.getYRot() * ((float) Math.PI / 180f);
        double x = player.getX() - Math.sin(yaw) * 120;
        double z = player.getZ() + Math.cos(yaw) * 120;
        WhaleKeeper.spawn(level, x, z, state);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.titan.summoned"), true);
        return 1;
    }

    private static int rest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoneWhaleEntity whale = WhaleKeeper.current(player.server.overworld());
        if (whale == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.titan.missing"));
            return 0;
        }
        whale.comeRestNear(player);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.titan.resting"), true);
        return 1;
    }

    private static int board(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoneWhaleEntity whale = WhaleKeeper.current(player.server.overworld());
        if (whale == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.titan.missing"));
            return 0;
        }
        TitanTravel.board(player, whale);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = ctx.getSource().getServer().getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            return 0;
        }
        TitanBackData data = TitanBackData.get(back);
        data.resetProgress();
        TitanBackBuilder.build(back, data);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.titan.rebuilt"), true);
        return 1;
    }
}

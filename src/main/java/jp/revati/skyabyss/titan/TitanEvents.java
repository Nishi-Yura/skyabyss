package jp.revati.skyabyss.titan;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.dungeon.TitanBackBuilder;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.registry.ModSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 岩鯨の出現と、テスト用のコマンド。
// 3日目以降、オーバーワールドにいるプレイヤーの遠くに一度だけ現れ、こちらへ泳いでくる。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanEvents {
    private static final long FIRST_APPEARANCE_DAY = 2;

    private TitanEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % 200 != 0) {
            return;
        }
        TitanWorldData data = TitanWorldData.get(level);
        if (data.spawned || level.getDayTime() / 24000L < FIRST_APPEARANCE_DAY || level.players().isEmpty()) {
            return;
        }
        ServerPlayer player = level.players().get(level.random.nextInt(level.players().size()));
        double angle = level.random.nextDouble() * Math.PI * 2;
        spawn(level, player.getX() + Math.cos(angle) * 280, player.getZ() + Math.sin(angle) * 280, data);
    }

    private static StoneWhaleEntity spawn(ServerLevel level, double x, double z, TitanWorldData data) {
        StoneWhaleEntity whale = ModEntities.STONE_WHALE.get().create(level);
        if (whale == null) {
            throw new IllegalStateException("Failed to create stone whale");
        }
        whale.moveTo(x, 200, z, level.random.nextFloat() * 360f, 0f);
        level.addFreshEntity(whale);
        data.spawned = true;
        data.setDirty();
        level.playSound(null, x, 200, z, ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 24f, 0.8f);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, 200, z) < 640 * 640) {
                player.sendSystemMessage(ModText.lore("message.skyabyss.titan_appears"));
            }
        }
        SkyAbyss.LOGGER.info("The stone whale appeared at {}, {}", (int) x, (int) z);
        return whale;
    }

    @Nullable
    private static StoneWhaleEntity findWhale(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof StoneWhaleEntity whale) {
                return whale;
            }
        }
        return null;
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
                            TitanTravel.leave(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))));
    }

    private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.server.overworld();
        StoneWhaleEntity existing = findWhale(level);
        if (existing != null) {
            existing.discard();
        }
        float yaw = player.getYRot() * ((float) Math.PI / 180f);
        double x = player.getX() - Math.sin(yaw) * 120;
        double z = player.getZ() + Math.cos(yaw) * 120;
        spawn(level, x, z, TitanWorldData.get(level));
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.titan.summoned"), true);
        return 1;
    }

    private static int rest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StoneWhaleEntity whale = findWhale(player.server.overworld());
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
        StoneWhaleEntity whale = findWhale(player.server.overworld());
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

    // 岩鯨がもう現れたかを覚えておく
    static final class TitanWorldData extends SavedData {
        private static final Factory<TitanWorldData> FACTORY = new Factory<>(TitanWorldData::new, TitanWorldData::load);
        private boolean spawned;

        static TitanWorldData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, "skyabyss_titans");
        }

        private static TitanWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
            TitanWorldData data = new TitanWorldData();
            data.spawned = tag.getBoolean("StoneWhaleSpawned");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("StoneWhaleSpawned", spawned);
            return tag;
        }
    }
}

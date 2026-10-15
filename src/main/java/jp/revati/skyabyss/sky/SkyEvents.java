package jp.revati.skyabyss.sky;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 序章の演出「雲の上を何かが通る」のサーバー側。
// 晴れた昼に空の見えるプレイヤーがいると、数日おきに通過が起きる。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkyEvents {
    private static final long DAY = 24000L;
    private static final long MIN_GAP = DAY * 2;
    private static final long MAX_GAP = DAY * 4;
    // 条件が合わなかったときに再判定するまでの時間 (1分)
    private static final long RETRY_DELAY = 1200L;
    private static final float SPEED = 0.35f;
    // プレイヤーの何ブロック手前から歩いてくるか
    private static final double PATH_HALF = 220.0;
    // 何tick先の影まで動物が怯えるか
    private static final double FEAR_LOOKAHEAD = 80.0;

    @Nullable
    private static SkyPass active;

    private SkyEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = level.getGameTime();
        if (active != null && !active.isActive(now)) {
            active = null;
        }
        if (active != null) {
            if (now % 2 == 0) {
                frightenAnimals(level, active, now);
            }
            return;
        }
        if (now % 20 != 0) {
            return;
        }
        double frequency = Config.HORROR_EVENT_FREQUENCY.get();
        if (frequency <= 0) {
            return;
        }
        SkyEventData data = SkyEventData.get(level);
        if (data.nextPassTick() < 0) {
            data.schedule(now + randomGap(level, frequency));
            return;
        }
        if (now < data.nextPassTick()) {
            return;
        }
        ServerPlayer witness = pickWitness(level);
        if (witness == null) {
            data.schedule(now + RETRY_DELAY);
            return;
        }
        start(level, witness);
        data.schedule(now + randomGap(level, frequency));
    }

    // 途中でログインしたプレイヤーにも、進行中の通過を見せる
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (active != null && event.getEntity() instanceof ServerPlayer player && player.level().dimension() == Level.OVERWORLD) {
            PacketDistributor.sendToPlayer(player, new SkyPassPayload(active));
        }
    }

    // ネザーなどからオーバーワールドへ戻ってきたプレイヤーにも見せる
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (active != null && event.getTo() == Level.OVERWORLD && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SkyPassPayload(active));
        }
    }

    // シングルプレイでワールドを抜けたとき、次のワールドに持ち越さない
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        active = null;
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    // /skyabyss pass [player] : すぐに通過を起こす（テスト用）
    // /skyabyss next          : 次の通過までの時間を表示する
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("pass")
                        .executes(ctx -> forcePass(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> forcePass(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("next")
                        .executes(SkyEvents::showNext)));
    }

    private static int forcePass(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.pass.overworld_only"));
            return 0;
        }
        start(level, player);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.pass.started", player.getDisplayName()), true);
        return 1;
    }

    private static int showNext(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        long remaining = SkyEventData.get(level).nextPassTick() - level.getGameTime();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.next", Math.max(0, remaining / 20)), false);
        return 1;
    }

    private static void start(ServerLevel level, ServerPlayer witness) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        float dirX = (float) Math.cos(angle);
        float dirZ = (float) Math.sin(angle);
        // 少し横にずらし、真上を通るとは限らないようにする
        double offset = (level.random.nextDouble() - 0.5) * 60.0;
        double originX = witness.getX() - dirX * PATH_HALF - dirZ * offset;
        double originZ = witness.getZ() - dirZ * PATH_HALF + dirX * offset;
        int duration = (int) (PATH_HALF * 2.0 / SPEED);

        active = new SkyPass(level.getGameTime() + 1, originX, originZ, dirX, dirZ, SPEED, duration);
        PacketDistributor.sendToPlayersInDimension(level, new SkyPassPayload(active));
        SkyAbyss.LOGGER.debug("Sky pass started near {}", witness.getName().getString());
    }

    @Nullable
    private static ServerPlayer pickWitness(ServerLevel level) {
        if (!level.isDay() || level.isRaining()) {
            return null;
        }
        List<ServerPlayer> players = new ArrayList<>(level.players());
        Collections.shuffle(players);
        for (ServerPlayer player : players) {
            if (player.isAlive() && !player.isSpectator() && level.canSeeSky(player.blockPosition().above())) {
                return player;
            }
        }
        return null;
    }

    private static long randomGap(ServerLevel level, double frequency) {
        long gap = MIN_GAP + (long) (level.random.nextDouble() * (MAX_GAP - MIN_GAP));
        return (long) (gap / frequency);
    }

    // 影の下と、影がこれから来る場所の動物は、動きを止めて空を見上げる
    private static void frightenAnimals(ServerLevel level, SkyPass pass, long now) {
        double age = pass.age(now, 0f);
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        double reach = SkyPass.REACH + FEAR_LOOKAHEAD * pass.speed();
        AABB area = new AABB(cx - reach, level.getMinBuildHeight(), cz - reach, cx + reach, level.getMaxBuildHeight(), cz + reach);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, area)) {
            float fear = Math.max(pass.shadow(animal.getX(), animal.getZ(), age),
                    pass.shadow(animal.getX(), animal.getZ(), age + FEAR_LOOKAHEAD));
            if (fear <= 0f) {
                continue;
            }
            animal.getNavigation().stop();
            animal.getLookControl().setLookAt(animal.getX() + pass.dirX() * 4.0, animal.getY() + 40.0, animal.getZ() + pass.dirZ() * 4.0);
            animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6, false, false));
        }
    }
}

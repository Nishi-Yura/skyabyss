package jp.revati.skyabyss.sky;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
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
// ただし最初の一回は、初日の正午以降の最初の機会に必ず起こす (条件が合わないあいだは 10 秒おきに調べ、遅れるほど条件をゆるめる)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkyEvents {
    private static final long DAY = 24000L;
    private static final long MIN_GAP = DAY * 2;
    private static final long MAX_GAP = DAY * 4;
    // 条件が合わなかったときに再判定するまでの時間 (1分)
    private static final long RETRY_DELAY = 1200L;
    // 最初の通過 (06 の 2.7節): ゲーム開始の昼 (6000 tick = 初日の正午) 以降の、最初の機会に必ず起こす。
    // 条件が合わなければ 10 秒おきに調べ直し、遅れた分だけ条件をゆるめる
    private static final long FIRST_PASS_AT = 6000L;
    private static final long FIRST_PASS_MIN_WAIT = 2400L;
    // 最初の一回は雨でも起こす (17 の 3.3節)。丸一日遅れたら、空の見えない所にいる人の上にも起こす
    private static final long FIRST_LOOSEN_SKY = DAY;
    private static final float SPEED = 0.35f;
    // プレイヤーの何ブロック手前から歩いてくるか
    private static final double PATH_HALF = 220.0;
    // 何tick先の影まで動物が怯えるか
    private static final double FEAR_LOOKAHEAD = 80.0;

    @Nullable
    private static SkyPass active;
    // 遅れて来た人のための個人の影 (その人にだけ送り、世界の影 (active) は立てない。ほかの人の空・足音・歌・岩鯨の出現を止めない)
    private static final java.util.Map<java.util.UUID, SkyPass> PERSONAL = new java.util.HashMap<>();

    private SkyEvents() {
    }

    // 進行中の通過 (なければ null)。伝承の書が「空の影を見た」かを調べるのに使う
    @Nullable
    public static SkyPass activePass() {
        return active;
    }

    // その人に見えている影 (個人の影があればそれ、なければ世界の影)。なければ null
    @Nullable
    public static SkyPass activePassFor(ServerPlayer player) {
        SkyPass personal = PERSONAL.get(player.getUUID());
        if (personal != null && player.level().getGameTime() <= personal.startTick() + personal.duration()) {
            return personal;
        }
        return active;
    }

    // その人の個人の影が去る時刻 (なければ -1)。序盤の拍子が「影が去った」を見るのに使う
    public static long personalEnd(ServerPlayer player) {
        SkyPass personal = PERSONAL.get(player.getUUID());
        return personal == null ? -1 : personal.startTick() + personal.duration();
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = level.getGameTime();
        if (!PERSONAL.isEmpty()) {
            PERSONAL.values().removeIf(p -> now > p.startTick() + p.duration() + 600);
            if (now % 2 == 0) {
                for (SkyPass personal : PERSONAL.values()) {
                    if (personal.isActive(now)) {
                        frightenAnimals(level, personal, now);
                    }
                }
            }
        }
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
        boolean first = data.passCount() == 0;
        if (data.nextPassTick() < 0) {
            data.schedule(first ? firstPassTime(now) : now + randomGap(level, frequency));
            return;
        }
        if (first && data.nextPassTick() > firstPassTime(now) + DAY) {
            // 初めての通過の予定が遠すぎる記録 (頻度を下げた設定のまま始めたワールドなど)。早い予定に引き直す
            data.schedule(firstPassTime(now));
        }
        // 天と淵の大陸: 最初の通過は、外れの一本道の終わり (都の門の前) に人が着いたとき (docs/design/40 の 4 章)。
        // 誰も来ないまま予定より一日遅れたら、いつもどおり (下の時刻の決まり) に起こす
        if (first) {
            ServerPlayer atGate = atCapitalGate(level);
            if (atGate != null) {
                start(level, atGate, true);
                data.schedule(now + randomGap(level, frequency));
                return;
            }
            if (now < data.nextPassTick() + DAY) {
                return;
            }
        }
        if (now < data.nextPassTick()) {
            return;
        }
        long overdue = now - data.nextPassTick();
        ServerPlayer witness = pickWitness(level, first ? overdue : -1);
        if (witness == null) {
            // 最初の通過は予定の時刻を動かさない (毎秒見直す)。動かすと「予定から何 tick 遅れたか」(overdue) がいつまでも 0 付近のままで、
            // 一日たったら空の見える条件をゆるめる (FIRST_LOOSEN_SKY) 備えが働かず、屋内・洞窟にいる人にはいつまでも影が来なかった (first_hour.txt の通しで発見)
            if (!first) {
                data.schedule(now + RETRY_DELAY);
            }
            return;
        }
        start(level, witness);
        data.schedule(now + randomGap(level, frequency));
    }

    // 都の門の前 (都の中心から GATE_RADIUS の中) にいる、地上の人 (観戦者は除く)。都がまだなければ null
    static final double GATE_RADIUS = 130;

    @Nullable
    static ServerPlayer atCapitalGate(ServerLevel level) {
        net.minecraft.core.BlockPos capital = jp.revati.skyabyss.opening.OpeningSites.shelter(level.getServer()).orElse(null);
        if (capital == null) {
            return null;
        }
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(capital.getX() + 0.5, p.getY(), capital.getZ() + 0.5) <= GATE_RADIUS * GATE_RADIUS) {
                return p;
            }
        }
        return null;
    }

    // 最初の通過の予定の時刻: 初日の正午。始めたのがそれより遅ければ (古いワールド・夜に始めた) 2 分後
    public static long firstPassTime(long now) {
        return Math.max(now + FIRST_PASS_MIN_WAIT, FIRST_PASS_AT);
    }

    // その人のためだけの影 (序盤の拍子の遅れた人)。世界の影はそのまま (進行中でも起こせる)。影の通過数も数えない
    public static void startPersonal(ServerLevel level, ServerPlayer witness) {
        SkyPass current = PERSONAL.get(witness.getUUID());
        if (current != null && current.isActive(level.getGameTime())) {
            return;
        }
        SkyPass pass = makePass(level, witness);
        PERSONAL.put(witness.getUUID(), pass);
        if (witness.connection != null) {
            PacketDistributor.sendToPlayer(witness, new SkyPassPayload(pass));
        }
    }

    // 途中でログインしたプレイヤーにも、進行中の通過を見せる
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (active != null && event.getEntity() instanceof ServerPlayer player && player.level().dimension() == Level.OVERWORLD
                && !jp.revati.skyabyss.lore.LoreEvents.sawSkyShadow(player)) {
            PacketDistributor.sendToPlayer(player, new SkyPassPayload(active));
        }
    }

    // ネザーなどからオーバーワールドへ戻ってきたプレイヤーにも見せる
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (active != null && event.getTo() == Level.OVERWORLD && event.getEntity() instanceof ServerPlayer player
                && !jp.revati.skyabyss.lore.LoreEvents.sawSkyShadow(player)) {
            PacketDistributor.sendToPlayer(player, new SkyPassPayload(active));
        }
    }

    // シングルプレイでワールドを抜けたとき、次のワールドに持ち越さない
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        active = null;
        PERSONAL.clear();
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
        start(level, player, true);
        ctx.getSource().sendSuccess(() -> ModText.lore("commands.skyabyss.pass.started", player.getDisplayName()), true);
        return 1;
    }

    private static int showNext(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        long remaining = SkyEventData.get(level).nextPassTick() - level.getGameTime();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.next", Math.max(0, remaining / 20)), false);
        return 1;
    }

    // その人のために影を起こす (序盤の拍子。最初の影のあとに目覚めた人に、翌日の窓で見せる。17 の 判断 6 A)。進行中ならしない
    public static void startFor(ServerLevel level, ServerPlayer witness) {
        if (active == null) {
            start(level, witness);
        }
    }

    private static void start(ServerLevel level, ServerPlayer witness) {
        start(level, witness, false);
    }

    private static void start(ServerLevel level, ServerPlayer witness, boolean force) {
        active = makePass(level, witness);
        SkyEventData.get(level).countPass(level.getGameTime());
        // 影をもう見た人には送らない (見た人には二度目以降の影を見せない。2026-10-21 の試遊)。開発の pass は起こした人には送る
        for (ServerPlayer p : level.players()) {
            if ((force && p == witness) || !jp.revati.skyabyss.lore.LoreEvents.sawSkyShadow(p)) {
                PacketDistributor.sendToPlayer(p, new SkyPassPayload(active));
            }
        }
        SkyAbyss.LOGGER.debug("Sky pass started near {}", witness.getName().getString());
    }

    private static SkyPass makePass(ServerLevel level, ServerPlayer witness) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        float dirX = (float) Math.cos(angle);
        float dirZ = (float) Math.sin(angle);
        // 少し横にずらし、真上を通るとは限らないようにする
        double offset = (level.random.nextDouble() - 0.5) * 60.0;
        double originX = witness.getX() - dirX * PATH_HALF - dirZ * offset;
        double originZ = witness.getZ() - dirZ * PATH_HALF + dirX * offset;
        int duration = (int) (PATH_HALF * 2.0 / SPEED);

        return new SkyPass(level.getGameTime() + 1, originX, originZ, dirX, dirZ, SPEED, duration);
    }

    @Nullable
    private static ServerPlayer pickWitness(ServerLevel level, long firstOverdue) {
        boolean first = firstOverdue >= 0;
        if (!level.isDay() || (level.isRaining() && !first)) {
            return null;
        }
        List<ServerPlayer> players = new ArrayList<>(level.players());
        Collections.shuffle(players);
        boolean anySky = first && firstOverdue >= FIRST_LOOSEN_SKY;
        for (ServerPlayer player : players) {
            if (player.isAlive() && !player.isSpectator() && (anySky || level.canSeeSky(player.blockPosition().above()))) {
                return player;
            }
        }
        return null;
    }

    private static long randomGap(ServerLevel level, double frequency) {
        long gap = MIN_GAP + (long) (level.random.nextDouble() * (MAX_GAP - MIN_GAP));
        return (long) (gap / frequency);
    }

    // 影の下と、影がこれから来る場所の動物と村人は、動きを止めて空を見上げる
    // (うずくまって震える見た目は client/prologue/CowerClient)
    private static void frightenAnimals(ServerLevel level, SkyPass pass, long now) {
        double age = pass.age(now, 0f);
        double cx = pass.centerX(age);
        double cz = pass.centerZ(age);
        double reach = SkyPass.REACH + FEAR_LOOKAHEAD * pass.speed();
        AABB area = new AABB(cx - reach, level.getMinBuildHeight(), cz - reach, cx + reach, level.getMaxBuildHeight(), cz + reach);
        for (PathfinderMob animal : level.getEntitiesOfClass(PathfinderMob.class, area, mob -> mob instanceof Animal || mob instanceof AbstractVillager)) {
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

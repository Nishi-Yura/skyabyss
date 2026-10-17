package jp.revati.skyabyss.prologue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 序章の演出「見る者」のサーバー側。
// 夜、屋外に一人でいるプレイヤーに、数日おきに一度だけ、遠くに立ってこちらを見ている人影を見せる。
// サーバーは「いつ・誰に・どこに」を決めるだけで、人影そのものは本人のクライアントの中にしか置かない
// (他のプレイヤーには見えない)。消え方はクライアントが決めて知らせてくる。
// 人影が立っている間、本人の近くの動物はそちらを向いて動きを止める (気づくための手がかり)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WatcherEvents {
    static final long DAY = 24000L;
    // 初めて現れるまで (このワールドで初めて見かけてから)
    static final long FIRST_DELAY_MIN = DAY * 2;
    static final long FIRST_DELAY_MAX = DAY * 3;
    // 一度現れたら、次まで空ける間隔
    static final long GAP_MIN = DAY * 3;
    static final long GAP_MAX = DAY * 5;
    // 気づかれずに終わったときは、少し早めにもう一度 (続けて 2 回まで)
    static final long RETRY_MIN = DAY * 3 / 4;
    static final long RETRY_MAX = DAY * 5 / 4;
    static final int MAX_QUICK_RETRIES = 2;
    // 条件がそろっている夜に、1 秒ごとに現れる確率 (平均 100 秒ほど待つ。夕暮れちょうどには出ない)
    static final float CHANCE_PER_SECOND = 0.01f;
    // 夜の範囲 (一日の時刻)。日没直後の明るさが残るうちと、夜明け前は避ける
    static final int NIGHT_START = 13000;
    static final int NIGHT_END = 22800;
    // この距離にほかのプレイヤーがいたら「一人」ではない
    static final double ALONE_RADIUS = 96.0;
    // 人影が立ってから何 tick 後に、近くの動物がそちらを向くか。どの範囲の動物か
    static final int ANIMALS_NOTICE_AFTER = 60;
    static final double ANIMAL_RADIUS = 20.0;
    static final int ANIMAL_LIMIT = 12;
    // クライアントから終わりの知らせが来なくても、段階の時間の2倍とこれだけたてば終わったものとする
    // (気づいたあとは段階の時間の1.5倍まで立っていられるので、それより長く待つ)
    static final int SERVER_GRACE = 400;

    record Active(int id, Vec3 pos, long start, long end) {
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static int nextId = 1;

    private WatcherEvents() {
    }

    // 演出全体の頻度 (ホラー演出の頻度 × 見る者の頻度)。0 なら現れない
    static double frequency() {
        return Config.HORROR_EVENT_FREQUENCY.get() * Config.WATCHER_FREQUENCY.get();
    }

    // 月の近さ (0〜1)。ループと月の接近が入ったら、そこから読む。今は序章なので 0
    static float moonNearness(ServerLevel level) {
        return 0f;
    }

    static long randomBetween(ServerLevel level, long min, long max) {
        return min + (long) (level.random.nextDouble() * (max - min));
    }

    // 次に現れるまでの間隔。月が近いほど少し短くなる
    static long gap(ServerLevel level, double frequency, float moon) {
        return (long) (randomBetween(level, GAP_MIN, GAP_MAX) * (1.0 - 0.3 * moon) / frequency);
    }

    static boolean isNight(ServerLevel level) {
        long time = level.getDayTime() % DAY;
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = level.getGameTime();
        if (!ACTIVE.isEmpty() && now % 2 == 0) {
            tickActive(level, now);
        }
        if (now % 20 != 7) {
            return;
        }
        double frequency = frequency();
        if (frequency <= 0 || level.players().isEmpty()) {
            return;
        }
        PrologueData data = PrologueData.get(level);
        float moon = moonNearness(level);
        for (ServerPlayer player : level.players()) {
            PrologueData.Entry entry = data.entry(player.getUUID());
            if (entry.nextWatcher < 0) {
                entry.nextWatcher = now + (long) (randomBetween(level, FIRST_DELAY_MIN, FIRST_DELAY_MAX) / frequency);
                data.changed();
                continue;
            }
            if (now < entry.nextWatcher || ACTIVE.containsKey(player.getUUID()) || ineligible(level, player) != null) {
                continue;
            }
            if (level.random.nextFloat() < CHANCE_PER_SECOND) {
                start(level, player, false);
            }
        }
    }

    // 今このプレイヤーに見る者を出せない理由 (出せるなら null)。テスト用のコマンドでも使う
    @Nullable
    static String ineligible(ServerLevel level, ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()) {
            return "not awake";
        }
        if (!isNight(level)) {
            return "not night";
        }
        if (jp.revati.skyabyss.opening.OpeningDirector.quietNight(level, player)) {
            return "opening night";
        }
        if (level.isThundering()) {
            return "thunderstorm";
        }
        if (player.isUnderWater() || !level.canSeeSky(player.blockPosition().above())) {
            return "not outdoors";
        }
        for (ServerPlayer other : level.players()) {
            if (other != player && !other.isSpectator() && other.distanceToSqr(player) < ALONE_RADIUS * ALONE_RADIUS) {
                return "not alone";
            }
        }
        return null;
    }

    // 立ち位置を選んで、本人にだけ見せる。force = テスト用 (向きを問わず、どこでもよいから立たせる)
    static Optional<WatcherSpot> start(ServerLevel level, ServerPlayer player, boolean force) {
        PrologueData data = PrologueData.get(level);
        PrologueData.Entry entry = data.entry(player.getUUID());
        float moon = moonNearness(level);
        // 1 章に入った人にも出し続けるが、段階 2 で止める (00 の 2 = B)
        int chapter = jp.revati.skyabyss.sky.ChapterSky.inChapterOne(player) ? 1 : 0;
        WatcherStage stage = WatcherStage.of(entry.sightings, moon, chapter);
        Vec3 eye = player.getEyePosition();
        Optional<WatcherSpot> spot = WatcherSpot.find(level, eye, player.getYRot(), stage.minDistance(), stage.maxDistance(),
                level.random, !force);
        if (spot.isEmpty() && force) {
            spot = WatcherSpot.find(level, eye, player.getYRot(), 32, stage.maxDistance(), level.random, false);
        }
        if (spot.isEmpty()) {
            return spot;
        }
        WatcherSpot s = spot.get();
        long now = level.getGameTime();
        int id = nextId++;
        ACTIVE.put(player.getUUID(), new Active(id, s.feet(), now, now + stage.lifetimeTicks() * 2L + SERVER_GRACE));
        double frequency = frequency();
        if (frequency > 0) {
            entry.nextWatcher = now + gap(level, frequency, moon);
        }
        data.changed();
        PacketDistributor.sendToPlayer(player, new WatcherPayload(id, s.feet().x, s.feet().y, s.feet().z, s.yaw(), stage.index()));
        SkyAbyss.LOGGER.debug("Watcher {} shown to {} at {} ({} blocks, skyline {})", id, player.getName().getString(),
                s.feet(), Math.round(s.distance()), s.skyline());
        return spot;
    }

    // 人影が立っている間、本人の近くの動物は、少したってからそちらを向いて固まる
    private static void tickActive(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> e = it.next();
            Active a = e.getValue();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || player.level() != level || !player.isAlive() || now > a.end()) {
                it.remove();
                continue;
            }
            if (now - a.start() < ANIMALS_NOTICE_AFTER) {
                continue;
            }
            faceTowards(level, player.getBoundingBox().inflate(ANIMAL_RADIUS), a.pos());
        }
    }

    // 範囲の動物の足を止め、pos (見る者の足もと) の頭の高さを見させる。何匹にかけたかを返す
    static int faceTowards(ServerLevel level, AABB area, Vec3 pos) {
        int count = 0;
        for (Animal animal : level.getEntitiesOfClass(Animal.class, area)) {
            if (count >= ANIMAL_LIMIT) {
                break;
            }
            count++;
            animal.getNavigation().stop();
            animal.getLookControl().setLookAt(pos.x, pos.y + WatcherSpot.HEAD, pos.z, 10f, 40f);
            animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 6, false, false));
        }
        return count;
    }

    // クライアントから「消えた」と届いたとき。次に現れるまでと、段階を更新する
    public static void onClientEnded(ServerPlayer player, int id, WatcherOutcome outcome) {
        Active a = ACTIVE.get(player.getUUID());
        if (a == null || a.id() != id) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        ServerLevel overworld = player.server.overworld();
        PrologueData data = PrologueData.get(overworld);
        PrologueData.Entry entry = data.entry(player.getUUID());
        double frequency = frequency();
        long retry = frequency > 0 ? overworld.getGameTime() + (long) (randomBetween(overworld, RETRY_MIN, RETRY_MAX) / frequency) : -1;
        applyOutcome(entry, outcome, retry);
        data.changed();
        SkyAbyss.LOGGER.debug("Watcher {} ended for {}: {}", id, player.getName().getString(), outcome);
    }

    // 消え方を記録に反映する。気づいたら段階が進む。気づかれなかったら、続けて 2 回までは
    // 早め (retryAt) にもう一度。見せない設定の人は何も変えない (間隔は出したときに決めたまま)
    static void applyOutcome(PrologueData.Entry entry, WatcherOutcome outcome, long retryAt) {
        if (outcome.seen()) {
            entry.sightings++;
            entry.misses = 0;
        } else if (outcome != WatcherOutcome.DISABLED) {
            entry.misses++;
            if (entry.misses <= MAX_QUICK_RETRIES && retryAt >= 0) {
                entry.nextWatcher = Math.min(entry.nextWatcher, retryAt);
            }
        }
    }

    @Nullable
    static Vec3 activePosition(UUID player) {
        Active a = ACTIVE.get(player);
        return a == null ? null : a.pos();
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    // /skyabyss watcher show [player]  : 今すぐ見る者を立たせる (夜・一人・屋外の条件を問わない。テスト用)
    // /skyabyss watcher next [player]  : 次に現れうるまでの時間と、これまでに気づいた回数
    // /skyabyss watcher reset [player] : 記録を消す (次の条件がそろった夜から現れうる)
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("watcher")
                        .then(Commands.literal("show")
                                .executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("next")
                                .executes(ctx -> next(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> next(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.pass.overworld_only"));
            return 0;
        }
        Optional<WatcherSpot> spot = start(level, player, true);
        if (spot.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.watcher.no_spot"));
            return 0;
        }
        long distance = Math.round(spot.get().distance());
        // 位置も添える (試す人が /tp ~ ~ ~ facing x y z で向けるように)
        net.minecraft.world.phys.Vec3 feet = spot.get().feet();
        String where = String.format(java.util.Locale.ROOT, "%d %d %d", Math.round(feet.x), Math.round(feet.y + WatcherSpot.HEAD), Math.round(feet.z));
        ctx.getSource().sendSuccess(() -> ModText.lore("commands.skyabyss.watcher.shown", player.getDisplayName(), distance, where), true);
        return 1;
    }

    private static int next(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        PrologueData.Entry entry = PrologueData.get(level).entry(player.getUUID());
        long remaining = entry.nextWatcher < 0 ? -1 : Math.max(0, entry.nextWatcher - level.getGameTime()) / 20;
        String why = ineligible(level, player);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.watcher.next", player.getDisplayName(),
                remaining, entry.sightings, why == null ? "ok" : why), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        PrologueData data = PrologueData.get(level);
        PrologueData.Entry entry = data.entry(player.getUUID());
        entry.nextWatcher = level.getGameTime();
        entry.sightings = 0;
        entry.misses = 0;
        data.changed();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.watcher.reset", player.getDisplayName()), true);
        return 1;
    }

    // 自己テスト用: 進行中の記録を消す
    static void clearActive() {
        ACTIVE.clear();
    }

    static boolean isActive(UUID player) {
        return ACTIVE.containsKey(player);
    }
}

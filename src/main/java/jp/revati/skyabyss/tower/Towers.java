package jp.revati.skyabyss.tower;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.finale.FinaleEnding;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 超大型レイド「あいだの塔」の挑戦の進め方 (docs/design/54 の 2 章。橋の先に座る主との一戦だけ)。
//   資格     終章の結末を一つ選んだ人 (FinaleEnding.chosen)
//   一日一回 入場が一人一日一回 (失敗しても終わり)。朝 5 時 (サーバーの時計) で戻る。人のデータ DAY に入った日を書く
//   全滅     橋の上で立っている人がいなくなる・時間切れ (30 分) で、その挑戦は終わり。全員を都の受付へ
//   倒れた   起きると橋の入口へ (全滅でなければ)。抜ける (/skyabyss_tower leave) と都の受付へ戻り、その日はもう入れない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Towers {
    public static final String DAY = "skyabyss_tower_day";
    // 越えた回数と最速 (tick)。人のデータ (LifeKeep で倒れても残る)
    public static final String CLEARS = "skyabyss_tower_clears";
    public static final String BEST = "skyabyss_tower_best";
    // 主が倒れてから都の受付へ戻すまで
    static final int CLEAR_BACK = 300;
    public static final int MAX_SQUAD = 12;
    static final int EMPTY_END = 1200;
    static final Map<Integer, TowerRun> RUNS = new HashMap<>();
    static final Map<UUID, Integer> BY_PLAYER = new HashMap<>();
    private static final Map<Integer, Integer> EMPTY = new HashMap<>();
    private static int nextId = 1;

    private Towers() {
    }

    // ---------------------------------------------------------------- 資格と一日一回
    public static boolean eligible(ServerPlayer p) {
        return FinaleEnding.chosen(p) != null;
    }

    // 今日 (朝 5 時で切り替わる)
    public static LocalDate today() {
        return LocalDateTime.now().minusHours(5).toLocalDate();
    }

    public static boolean usedToday(ServerPlayer p) {
        return today().toString().equals(p.getPersistentData().getString(DAY));
    }

    static void markUsed(ServerPlayer p) {
        p.getPersistentData().putString(DAY, today().toString());
    }

    public static TowerRun runOf(ServerPlayer p) {
        Integer id = BY_PLAYER.get(p.getUUID());
        return id == null ? null : RUNS.get(id);
    }

    // ---------------------------------------------------------------- 始める
    // squad = 入る人 (隊長が先頭)。dev = 一日一回を数えない (開発用)
    public static TowerRun start(MinecraftServer server, List<ServerPlayer> squad, boolean dev) {
        ServerLevel level = TowerWorld.level(server);
        if (level == null || squad.isEmpty()) {
            return null;
        }
        int slot = -1;
        for (int s = 0; s < TowerWorld.SLOTS && slot < 0; s++) {
            final int fs = s;
            if (RUNS.values().stream().noneMatch(r -> r.slot == fs)) {
                slot = s;
            }
        }
        if (slot < 0) {
            for (ServerPlayer p : squad) {
                Notice.hint(p, "message.skyabyss.tower.full");
            }
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer p : squad) {
            ids.add(p.getUUID());
        }
        TowerRun run = new TowerRun(server, nextId++, slot, ids, level.getGameTime());
        RUNS.put(run.id, run);
        for (ServerPlayer p : squad) {
            BY_PLAYER.put(p.getUUID(), run.id);
            if (!dev) {
                markUsed(p);
            }
        }
        SkyAbyss.LOGGER.info("[tower] run #{} slot {} with {} player(s)", run.id, slot, squad.size());
        TowerWorld.pour(level, slot);
        clearFoes(run);
        run.arena = new TowerArena(run);
        run.arena.begin(level);
        Vec3 e = run.markCenter("entry");
        for (ServerPlayer p : run.online()) {
            p.teleportTo(level, e.x + (p.getRandom().nextDouble() - 0.5) * 4, e.y, e.z + (p.getRandom().nextDouble() - 0.5) * 2, 180f, 0f);
            p.resetFallDistance();
            p.setHealth(p.getMaxHealth());
            Notice.place(p, Component.translatable("message.skyabyss.tower.title"), Component.translatable("message.skyabyss.tower.sub", squad.size()));
        }
        return run;
    }

    static void clearFoes(TowerRun run) {
        ServerLevel level = run.level();
        if (level == null) {
            return;
        }
        for (Mob m : level.getEntitiesOfClass(Mob.class, run.box().inflate(16), m -> m.getTags().contains(run.foeTag()))) {
            m.discard();
        }
    }

    static void end(TowerRun run) {
        clearFoes(run);
        if (run.arena != null) {
            run.arena.close();
        }
        RUNS.remove(run.id);
        BY_PLAYER.values().removeIf(id -> id == run.id);
        EMPTY.remove(run.id);
        SkyAbyss.LOGGER.info("[tower] run #{} ended (cleared {})", run.id, run.cleared);
    }

    // 挑戦が終わった (全滅・時間切れ): 全員を都の受付へ
    static void fail(TowerRun run, String why) {
        for (ServerPlayer p : run.online()) {
            Notice.narrate(p, "message.skyabyss.tower." + why);
            Tasks.later(60, () -> {
                if (!p.hasDisconnected() && TowerWorld.in(p)) {
                    if (!p.isAlive()) {
                        return;
                    }
                    TowerDesk.sendBack(p);
                }
            });
        }
        end(run);
    }

    // 主が倒れた: 塔の中にいる隊の人に、越えた回数と最速を書く
    static void cleared(TowerRun run, long ticks) {
        run.cleared = true;
        for (ServerPlayer p : run.online()) {
            if (!TowerWorld.in(p)) {
                continue;
            }
            var d = p.getPersistentData();
            d.putInt(CLEARS, d.getInt(CLEARS) + 1);
            if (!d.contains(BEST) || ticks < d.getLong(BEST)) {
                d.putLong(BEST, ticks);
            }
            Notice.narrate(p, "message.skyabyss.tower.cleared", time(ticks));
            Notice.hint(p, "message.skyabyss.tower.clear_back");
        }
        SkyAbyss.LOGGER.info("[tower] run #{} cleared in {} with {} player(s)", run.id, time(ticks), run.members.size());
    }

    public static String time(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // 抜ける: 都の受付へ。その日はもう入れない
    public static void leave(ServerPlayer p) {
        TowerRun run = runOf(p);
        BY_PLAYER.remove(p.getUUID());
        TowerDesk.sendBack(p);
        if (run != null && run.online().isEmpty()) {
            end(run);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (RUNS.isEmpty()) {
            return;
        }
        ServerLevel level = TowerWorld.level(event.getServer());
        if (level == null) {
            return;
        }
        long t = level.getGameTime();
        for (TowerRun run : List.copyOf(RUNS.values())) {
            tick(level, run, t);
        }
    }

    static void tick(ServerLevel level, TowerRun run, long t) {
        if (run.arena == null) {
            return;
        }
        run.arena.tick(level, t);
        TowerFight fight = run.arena.fight;
        if (fight.won) {
            if (!run.cleared) {
                cleared(run, fight.wonAt - run.startedTick);
            }
            if (t >= fight.wonAt + CLEAR_BACK) {
                for (ServerPlayer p : run.online()) {
                    if (p.isAlive() && TowerWorld.in(p)) {
                        TowerDesk.sendBack(p);
                    }
                }
                end(run);
            }
            return;
        }
        if (fight.failed != null) {
            fail(run, fight.failed);
            return;
        }
        if (run.arena.timedOut(t)) {
            fail(run, "timeout");
            return;
        }
        if (t % 20 == 0) {
            List<ServerPlayer> here = run.present();
            long standing = here.stream().filter(p -> !jp.revati.skyabyss.party.Downed.isDowned(p)).count();
            boolean anyInside = run.online().stream().anyMatch(p -> p.level() == level);
            if (standing == 0 && anyInside && run.online().stream().anyMatch(p -> p.level() == level && (!p.isAlive() || jp.revati.skyabyss.party.Downed.isDowned(p)))) {
                fail(run, "wipe");
                return;
            }
            // 誰も塔にいなくなって 60 秒で終わり
            int e = anyInside ? 0 : EMPTY.getOrDefault(run.id, 0) + 20;
            EMPTY.put(run.id, e);
            if (e >= EMPTY_END) {
                end(run);
            }
        }
    }

    // ---------------------------------------------------------------- 出来事
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !TowerWorld.in(p)) {
            return;
        }
        TowerRun run = runOf(p);
        if (run != null && event.getPos().equals(run.mark("return"))) {
            event.setCanceled(true);
            leave(p);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TowerRun run = runOf(p);
            if (run != null && TowerWorld.in(p)) {
                run.fallen.add(p.getUUID());
            }
        }
    }

    // 起きたら橋の入口へ (挑戦がもう無ければ都の受付へ)
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isEndConquered()) {
            return;
        }
        TowerRun run = runOf(p);
        if (run == null) {
            return;
        }
        Tasks.later(2, () -> {
            if (p.isAlive() && !p.hasDisconnected() && RUNS.containsKey(run.id)) {
                Vec3 e = run.markCenter("entry");
                p.teleportTo(run.level(), e.x, e.y, e.z, 180f, 0f);
                Notice.narrate(p, "message.skyabyss.tower.woke");
            }
        });
    }

    // 入り直したとき: 挑戦が続いていれば橋の入口へ、終わっていれば都の受付へ
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !TowerWorld.in(p)) {
            return;
        }
        TowerRun run = runOf(p);
        if (run == null) {
            Tasks.later(10, () -> TowerDesk.sendBack(p));
        } else {
            Tasks.later(10, () -> {
                Vec3 e = run.markCenter("entry");
                p.teleportTo(run.level(), e.x, e.y, e.z, 180f, 0f);
            });
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        for (TowerRun run : RUNS.values()) {
            if (run.arena != null) {
                run.arena.close();
            }
        }
        RUNS.clear();
        BY_PLAYER.clear();
        EMPTY.clear();
        TowerDesk.resetAll();
    }

    // ---------------------------------------------------------------- 開発用
    // /skyabyss tower start (一人で、一日一回を数えずに) | day (今日の一回を戻す) | leave | act <技> (主の技をすぐ出す) | hp <割合 %> (主の体力)
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("tower")
                        .then(Commands.literal("start").executes(c -> start(c.getSource().getServer(), List.of(c.getSource().getPlayerOrException()), true) != null ? 1 : 0))
                        .then(Commands.literal("day").executes(c -> {
                            c.getSource().getPlayerOrException().getPersistentData().remove(DAY);
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(c -> {
                            leave(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("act").then(Commands.argument("move", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            TowerRun run = runOf(c.getSource().getPlayerOrException());
                            if (run == null || run.arena == null) {
                                return 0;
                            }
                            run.arena.fight.devMove(run.level(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "move"));
                            return 1;
                        })))
                        .then(Commands.literal("hp").then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100)).executes(c -> {
                            TowerRun run = runOf(c.getSource().getPlayerOrException());
                            if (run == null || run.arena == null || run.arena.fight.master() == null) {
                                return 0;
                            }
                            var m = run.arena.fight.master();
                            m.setHealth(m.getMaxHealth() * com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "percent") / 100f);
                            return 1;
                        })))));
    }
}

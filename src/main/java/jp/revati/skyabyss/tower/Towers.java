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
    // 越えてから都へ戻るまで (越えた場面 TowerFight.CLEAR_SCENE のあと 15 秒)
    static final int CLEAR_BACK = TowerFight.CLEAR_SCENE + 300;
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
        return jp.revati.skyabyss.util.DayClock.today();
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
        return start(server, squad, dev, java.util.Set.of());
    }

    // wagers = 隊長が受付で選んだ週の賭け (TowerWagers)
    public static TowerRun start(MinecraftServer server, List<ServerPlayer> squad, boolean dev, java.util.Set<String> wagers) {
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
        run.wagers.addAll(wagers);
        RUNS.put(run.id, run);
        for (ServerPlayer p : squad) {
            BY_PLAYER.put(p.getUUID(), run.id);
            if (!dev) {
                markUsed(p);
            }
        }
        SkyAbyss.LOGGER.info("[tower] run #{} slot {} with {} player(s)", run.id, slot, squad.size());
        announce(server, "message.skyabyss.tower.announce_enter", squad, squad.get(0).getGameProfile().getName(), squad.size());
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
            if (!run.wagers.isEmpty()) {
                Notice.hint(p, "message.skyabyss.tower.wager.on", wagerNames(run.wagers));
            }
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
            Notice.hint(p, "message.skyabyss.tower." + why);
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

    public static Component wagerNames(java.util.Collection<String> wagers) {
        net.minecraft.network.chat.MutableComponent c = Component.empty();
        int i = 0;
        for (String w : wagers) {
            if (i++ > 0) {
                c.append("・");
            }
            c.append(Component.translatable("message.skyabyss.tower.wager." + w));
        }
        return c;
    }

    // 主が倒れた: 塔の中にいる隊の人に、越えた回数と最速を書き、記録の板 (人数ごとの最速・今週の組合) と称号を更新する
    static void cleared(TowerRun run, long ticks) {
        run.cleared = true;
        List<String> names = new ArrayList<>();
        List<String[]> guilds = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ServerPlayer p : run.online()) {
            names.add(p.getGameProfile().getName());
            var g = jp.revati.skyabyss.facility.guild.Guilds.of(p);
            if (g != null && seen.add(g.id)) {
                guilds.add(new String[]{g.id, g.name});
            }
        }
        boolean fastest = run.server != null && TowerRecords.get(run.server).record(run.members.size(), ticks, names, guilds);
        // 組合の塔の順位 (endgame/GuildWeek。週が替わると上位 3 組合が誉れ)
        if (run.server != null) {
            for (String[] g : guilds) {
                jp.revati.skyabyss.endgame.GuildWeek.towerClear(run.server, g[0], g[1]);
            }
        }
        for (ServerPlayer p : run.online()) {
            if (!TowerWorld.in(p)) {
                continue;
            }
            var d = p.getPersistentData();
            d.putInt(CLEARS, d.getInt(CLEARS) + 1);
            if (!d.contains(BEST) || ticks < d.getLong(BEST)) {
                d.putLong(BEST, ticks);
            }
            Notice.place(p, Component.translatable("message.skyabyss.tower.clear_title"), Component.translatable("message.skyabyss.tower.clear_sub", time(ticks)));
            p.playNotifySound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.MASTER, 1f, 1f);
            Notice.gain(p, "message.skyabyss.tower.cleared", time(ticks));
            if (fastest) {
                Notice.gain(p, "message.skyabyss.tower.record", run.members.size());
            }
            reward(p, run);
            wagerMark(p, run);
            jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
            Notice.hint(p, "message.skyabyss.tower.clear_back");
        }
        SkyAbyss.LOGGER.info("[tower] run #{} cleared in {} with {} player(s)", run.id, time(ticks), run.members.size());
        if (run.server != null) {
            List<ServerPlayer> on = run.online();
            announce(run.server, "message.skyabyss.tower.announce_clear", on, on.isEmpty() ? "?" : on.get(0).getGameProfile().getName(), time(ticks));
        }
    }

    // 塔へ挑んだ・越えたことを、ほかの人にも一行で知らせる (手本 Annihilation の「Wynn faces Annihilation」の知らせ)。自己テストの偽の人だけの隊は知らせない
    static void announce(MinecraftServer server, String key, List<ServerPlayer> squad, Object... args) {
        if (squad.stream().allMatch(p -> p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(Component.translatable(key, args).withStyle(s -> s.withColor(0xF2D78A)), false);
    }

    // 越えた人への報酬: 塔の欠片・鐘銭と、塔の箱 (確率と天井。TowerGear.BOX_CHANCE・BOX_PITY。中は塔の装備がどれか一つ)
    static void reward(ServerPlayer p, TowerRun run) {
        rollBox(p, false);
        if (p.getRandom().nextFloat() < jp.revati.skyabyss.tower.gear.TowerGear.SCROLL_CHANCE) {
            jp.revati.skyabyss.quest.Quests.give(p, new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.tower.gear.TowerGearSetup.SECRET_SCROLL.get()));
            Notice.gainLog(p, "message.skyabyss.tower.scroll");
        }
        int shards = (int) Math.round(jp.revati.skyabyss.tower.gear.TowerGear.SHARDS_PER_CLEAR * TowerWagers.shardScale(run == null ? java.util.Set.of() : run.wagers));
        jp.revati.skyabyss.quest.Quests.give(p, new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.tower.gear.TowerGearSetup.SHARD.get(), shards));
        jp.revati.skyabyss.facility.Coins.give(p, jp.revati.skyabyss.tower.gear.TowerGear.COINS_PER_CLEAR);
        Notice.gainLog(p, "message.skyabyss.tower.reward", shards,
                jp.revati.skyabyss.tower.gear.TowerGear.COINS_PER_CLEAR);
    }

    // 重ね賭けの印 (2026-11-05 のダンジョンの頁 2-2): 賭けを三つ付けて越えた週は、その週に一度だけ塔の箱を必ず (15% の抽選と天井とは別)。
    // 記録の板に名前、印の週の数で称号 (tower_wager3 = 1 週・tower_wager3_10 = 10 週)
    public static final String MARK_WEEK = "skyabyss_tower_mark_week";
    public static final String MARK_WEEKS = "skyabyss_tower_mark_weeks";

    static void wagerMark(ServerPlayer p, TowerRun run) {
        if (run == null || run.wagers.size() < TowerWagers.MAX_PICK) {
            return;
        }
        var d = p.getPersistentData();
        String week = TowerWagers.week().toString();
        if (week.equals(d.getString(MARK_WEEK))) {
            return;
        }
        d.putString(MARK_WEEK, week);
        d.putInt(MARK_WEEKS, d.getInt(MARK_WEEKS) + 1);
        if (run.server != null) {
            TowerRecords.get(run.server).addMark(p.getGameProfile().getName());
        }
        net.minecraft.world.item.Item box = jp.revati.skyabyss.loot.box.LootBoxSetup.item(jp.revati.skyabyss.loot.box.BoxKind.byId("box_tower"));
        if (box != null) {
            jp.revati.skyabyss.quest.Quests.give(p, new net.minecraft.world.item.ItemStack(box));
        }
        Notice.unlock(p, "message.skyabyss.tower.mark", d.getInt(MARK_WEEKS));
    }

    // 称号の印 (rebirth/Rebirths.facts): 重ね賭けの印の週の数
    public static java.util.Set<String> marks(ServerPlayer p) {
        int n = p.getPersistentData().getInt(MARK_WEEKS);
        java.util.Set<String> out = new java.util.HashSet<>();
        if (n >= 1) {
            out.add("tower_wager3");
        }
        if (n >= 10) {
            out.add("tower_wager3_10");
        }
        return out;
    }

    // 塔の箱を引く (force = 必ず)。出たら true
    static boolean rollBox(ServerPlayer p, boolean force) {
        var d = p.getPersistentData();
        int dry = d.getInt(jp.revati.skyabyss.tower.gear.TowerGear.BOX_DRY) + 1;
        if (!force && dry < jp.revati.skyabyss.tower.gear.TowerGear.BOX_PITY && p.getRandom().nextFloat() >= jp.revati.skyabyss.tower.gear.TowerGear.BOX_CHANCE) {
            d.putInt(jp.revati.skyabyss.tower.gear.TowerGear.BOX_DRY, dry);
            return false;
        }
        d.putInt(jp.revati.skyabyss.tower.gear.TowerGear.BOX_DRY, 0);
        net.minecraft.world.item.Item box = jp.revati.skyabyss.loot.box.LootBoxSetup.item(jp.revati.skyabyss.loot.box.BoxKind.byId("box_tower"));
        if (box != null) {
            jp.revati.skyabyss.quest.Quests.give(p, new net.minecraft.world.item.ItemStack(box));
            Notice.gainLog(p, "message.skyabyss.tower.box");
        }
        return true;
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
            // 報いと記録は越えた場面のあと (時間は主が倒れた tick で数える)
            if (!run.cleared && t >= fight.wonAt + TowerFight.CLEAR_SCENE) {
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

    // 賭け half_heal: 塔の中の隊の人の回復が半分
    @SubscribeEvent
    static void onHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && TowerWorld.in(p) && TowerWagers.isOn(runOf(p), "half_heal")) {
            event.setAmount(event.getAmount() * 0.5f);
        }
    }

    // 賭け no_revive: 倒れた人を起こせない (WildGearEvents.reviveStep から)
    public static boolean noRevive(net.minecraft.world.entity.player.Player p) {
        return p instanceof ServerPlayer sp && TowerWorld.in(sp) && TowerWagers.isOn(runOf(sp), "no_revive");
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
                Notice.hint(p, "message.skyabyss.tower.woke");
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
    // /skyabyss tower start (一人で、一日一回を数えずに) | day (今日の一回を戻す) | leave | act <技> (主の技をすぐ出す。pose_<動き> は動きだけ、hold は自分から技を出さない、win は倒す) | hp <割合 %> (主の体力)
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("tower")
                        .then(Commands.literal("start").executes(c -> start(c.getSource().getServer(), List.of(c.getSource().getPlayerOrException()), true) != null ? 1 : 0))
                        .then(Commands.literal("day").executes(c -> {
                            resetDay(c.getSource().getPlayerOrException());
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
                        .then(Commands.literal("hp").then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100)).executes(c ->
                                masterHealth(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "percent")) ? 1 : 0)))));
    }

    // 今日の一回を戻す (開発用と管理者の /skyabyss admin tower day)
    public static void resetDay(ServerPlayer p) {
        p.getPersistentData().remove(DAY);
    }

    // その人のいる塔の主の体力を、最大の percent % にする (月の段の確かめ。開発用と管理者の /skyabyss admin tower hp)。塔の中で主がいなければ false
    public static boolean masterHealth(ServerPlayer p, int percent) {
        TowerRun run = runOf(p);
        if (run == null || run.arena == null || run.arena.fight.master() == null) {
            return false;
        }
        var m = run.arena.fight.master();
        m.setHealth(m.getMaxHealth() * Math.max(1, Math.min(100, percent)) / 100f);
        return true;
    }
}

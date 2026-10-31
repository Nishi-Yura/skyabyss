package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.facility.TownGuide;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteGuard;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.pouch.PouchRouting;
import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import jp.revati.skyabyss.tower.Towers;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentPois;
import jp.revati.skyabyss.world.continent.ContinentTowns;
import jp.revati.skyabyss.world.continent.RegionSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 巡り路 (docs/design/54 の 3.1。Wynncraft のルートラン)。土地の町 (鯨見・汐守・風待・灰炉) の案内人に「巡り路を歩く」と言うと始まる。
//   20 分のあいだ、その土地の見どころに小さな課題が次々と出る。行き先は光の柱 (BoardBeamClient) と目的の帯 (CircuitClient) で示す
//     倒す  HUNT  : 着くとその土地の敵が湧く (4 + 仲間 1 人につき 2。10 まで)。みな倒すと越える
//     守る  GUARD : 柱のそば (GUARD_R) に誰かがいるあいだだけ進む (40 秒)。10 秒ごとに敵が寄せてくる
//     運ぶ  CARRY : 柱の所で荷を背負い (足が少し重い)、もう一つの柱の所へ届ける。半ばで待ち伏せ。背負った人が倒れる・離れると荷が落ちる
//   越えるたびに「巡りの印」+1。課題ごとに 5 分で間に合わなければ次へ (印なし)。近くの組の仲間 (START_NEAR) も一緒に歩く
//   終わると一人ずつ報い: 印 × 土地の鐘銭、印 2 つにつき封の箱 1 (アンコモン 60・レア 32・エピック 8)、印 6 以上で土地の巨神の封の箱 1。
//   報いは一日 DAILY 回まで (朝 5 時に戻る。Towers.today)。印はいつでも土地の点 (RegionPoints。旗取りと名声) に数える
//   「壊す」課題は敵の杭の形が決まってから足す (00 の未決ではなく、形の承認待ち)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Circuits {
    public static final int DURATION = 20 * 60 * 20;
    public static final int TASK_LIMIT = 20 * 60 * 5;
    public static final int DAILY = 3;
    public static final String DAY = "skyabyss_circuit_day";
    public static final String RUNS = "skyabyss_circuit_runs";
    static final int RGB = 0xF2C14E;
    static final double START_NEAR = 24;
    static final double ARRIVE = 18;
    static final double GUARD_R = 7;
    static final double CARRY_R = 5;
    public static final int GUARD_NEED = 800;
    static final int WAVE_EVERY = 200;
    static final int SPOT_MIN = 70;
    static final int SPOT_MAX = 240;
    // 土地ごとの印一つの鐘銭 (野・島・岩鯨・潮殻・雷翼・余燼。ContinentMap の番号の順)
    static final int[] COINS = {20, 0, 25, 35, 50, 70};
    // 土地の目安のいちばん上の Lv (layout.json の levels。箱の帯 WildGearTypes.band を決める)
    static final int[] TOP_LEVEL = {4, 4, 12, 18, 24, 30};
    static final String[] TITAN = {null, null, "stone_whale", "tide_shell", "thunder_wing", "ember"};

    private static final List<CircuitRun> ACTIVE = new ArrayList<>();
    private static final Map<UUID, CircuitRun> BY_PLAYER = new HashMap<>();

    private Circuits() {
    }

    @Nullable
    public static CircuitRun runOf(ServerPlayer p) {
        return BY_PLAYER.get(p.getUUID());
    }

    // ---------------------------------------------------------------- 一日の回数
    public static int runsToday(ServerPlayer p) {
        return Towers.today().toString().equals(p.getPersistentData().getString(DAY)) ? p.getPersistentData().getInt(RUNS) : 0;
    }

    static void countRun(ServerPlayer p) {
        int n = runsToday(p) + 1;
        p.getPersistentData().putString(DAY, Towers.today().toString());
        p.getPersistentData().putInt(RUNS, n);
    }

    // 土地の町の番号 (ContinentTowns.REGIONS の並び) → 土地の番号 (ContinentMap)
    static int regionOfTown(int townIndex) {
        return ContinentMap.WHALE + townIndex;
    }

    @Nullable
    static SiteData.Entry townHere(ServerPlayer p) {
        return TownGuide.townAt(p.server, p.blockPosition());
    }

    // ---------------------------------------------------------------- 案内人の会話から
    public static void offer(ServerPlayer p) {
        SiteData.Entry town = townHere(p);
        if (town == null) {
            Notice.hint(p, "message.skyabyss.circuit.no_town");
            return;
        }
        if (runOf(p) != null) {
            Notice.hint(p, "message.skyabyss.circuit.busy");
            return;
        }
        String r = ContinentTowns.region(town.index);
        p.sendSystemMessage(Component.translatable("message.skyabyss.circuit.offer.head", Component.translatable("map.skyabyss.town." + r))
                .withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.translatable("message.skyabyss.circuit.offer.body").withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.translatable("message.skyabyss.circuit.offer.left", Math.max(0, DAILY - runsToday(p)), DAILY)
                .withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.translatable("message.skyabyss.circuit.offer.start").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyabyss_circuit start"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.skyabyss.circuit.offer.hover")))));
    }

    // 案内人の会話の「歩き出す」から: 始めて、今日の報いの残りを知らせる
    public static void startFromGuide(ServerPlayer p) {
        if (startHere(p)) {
            int left = Math.max(0, DAILY - runsToday(p));
            Notice.hint(p, left > 0 ? "message.skyabyss.circuit.left_today" : "message.skyabyss.circuit.none_today", left, DAILY);
        }
    }

    static boolean startHere(ServerPlayer p) {
        SiteData.Entry town = townHere(p);
        if (town == null) {
            Notice.hint(p, "message.skyabyss.circuit.no_town");
            return false;
        }
        if (runOf(p) != null) {
            Notice.hint(p, "message.skyabyss.circuit.busy");
            return false;
        }
        List<ServerPlayer> members = new ArrayList<>();
        members.add(p);
        for (ServerPlayer m : Parties.onlineMembers(p.server, Parties.of(p))) {
            if (m != p && m.level() == p.level() && m.distanceTo(p) <= START_NEAR && runOf(m) == null && !m.isSpectator()) {
                members.add(m);
            }
        }
        start(p.server, members, regionOfTown(town.index), p.blockPosition());
        return true;
    }

    // ---------------------------------------------------------------- 始める・終える
    public static CircuitRun start(MinecraftServer server, List<? extends ServerPlayer> members, int region, BlockPos origin) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        CircuitRun run = new CircuitRun(region, origin, now, now + DURATION);
        for (ServerPlayer m : members) {
            run.members.add(m.getUUID());
            if (m instanceof FakePlayer) {
                run.fakes.put(m.getUUID(), m);
            }
            BY_PLAYER.put(m.getUUID(), run);
            Notice.place(m, Component.translatable("circuit.skyabyss.title"), Component.translatable("message.skyabyss.circuit.started"));
            m.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        ACTIVE.add(run);
        nextTask(level, run);
        return run;
    }

    public static void finish(MinecraftServer server, CircuitRun run) {
        ACTIVE.remove(run);
        for (UUID id : run.members) {
            BY_PLAYER.remove(id, run);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                p = run.fakes.get(id);
            }
            if (p != null) {
                reward(p, run);
                clearView(p);
            }
        }
        run.members.clear();
    }

    // 一人だけ抜ける (残りの人は続ける。誰もいなくなれば終わり)。抜けた人にも、それまでの印の報いを渡す
    public static void leave(ServerPlayer p) {
        CircuitRun run = BY_PLAYER.remove(p.getUUID());
        if (run == null) {
            return;
        }
        run.members.remove(p.getUUID());
        Notice.hint(p, "message.skyabyss.circuit.left");
        reward(p, run);
        clearView(p);
        if (run.members.isEmpty()) {
            ACTIVE.remove(run);
        }
    }

    static void reward(ServerPlayer p, CircuitRun run) {
        Notice.gain(p, "message.skyabyss.circuit.end", run.marks);
        RegionPoints.add(p, run.region, run.marks);
        if (run.marks <= 0) {
            Notice.hint(p, "message.skyabyss.circuit.no_marks");
            return;
        }
        if (runsToday(p) >= DAILY) {
            Notice.hint(p, "message.skyabyss.circuit.no_reward_daily", DAILY);
            return;
        }
        countRun(p);
        int coins = run.marks * COINS[Math.max(0, Math.min(COINS.length - 1, run.region))];
        Coins.give(p, coins);
        int boxes = (run.marks + 1) / 2;
        int band = jp.revati.skyabyss.item.wild.WildGearTypes.band(TOP_LEVEL[Math.max(0, Math.min(TOP_LEVEL.length - 1, run.region))]);
        for (int i = 0; i < boxes; i++) {
            int r = p.getRandom().nextInt(100);
            GearTier tier = r < 60 ? GearTier.UNCOMMON : r < 92 ? GearTier.RARE : GearTier.EPIC;
            giveBox(p, BoxKind.rarity(tier, band));
        }
        Notice.gain(p, "message.skyabyss.circuit.reward", coins, boxes);
        String titan = run.region >= 0 && run.region < TITAN.length ? TITAN[run.region] : null;
        if (run.marks >= 6 && titan != null) {
            giveBox(p, BoxKind.titan(titan));
            Notice.gain(p, "message.skyabyss.circuit.reward_titan");
        }
    }

    static void giveBox(ServerPlayer p, @Nullable BoxKind kind) {
        var item = kind == null ? null : LootBoxSetup.item(kind);
        if (item != null) {
            PouchRouting.giveOrDrop(p, new ItemStack(item));
        }
    }

    // ---------------------------------------------------------------- 課題
    static void nextTask(ServerLevel level, CircuitRun run) {
        run.mobs.clear();
        run.progress = 0;
        run.need = 0;
        run.phase = 0;
        run.carrier = null;
        run.ambushed = false;
        run.dropped = false;
        run.spot2 = null;
        run.place2 = null;
        run.taskStart = level.getGameTime();
        CircuitRun.Kind[] kinds = CircuitRun.Kind.values();
        CircuitRun.Kind k = kinds[level.random.nextInt(kinds.length)];
        if (k == run.kind) {
            k = kinds[(k.ordinal() + 1 + level.random.nextInt(kinds.length - 1)) % kinds.length];
        }
        run.kind = k;
        BlockPos from = run.spot == null ? run.origin : run.spot;
        Spot s = pickSpot(level, run, from, SPOT_MIN, SPOT_MAX);
        run.spot = s.pos();
        run.place = s.place();
        if (k == CircuitRun.Kind.CARRY) {
            Spot b = pickSpot(level, run, run.spot, 90, 180);
            run.spot2 = b.pos();
            run.place2 = b.place();
        }
    }

    record Spot(BlockPos pos, @Nullable String place) {
    }

    // その土地の見どころのうち、from から min〜max 離れた、まだ使っていない所 (町の中は除く)。なければ土地の中の離れた地面
    static Spot pickSpot(ServerLevel level, CircuitRun run, BlockPos from, int min, int max) {
        ContinentMap map = ContinentMap.get();
        List<ContinentPois.Poi> all = ContinentPois.all();
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            ContinentPois.Poi poi = all.get(i);
            if (!poi.site() || run.usedPois.contains(i) || map.region(poi.x(), poi.z()) != run.region) {
                continue;
            }
            double d = Math.hypot(poi.x() - from.getX(), poi.z() - from.getZ());
            if (d >= min && d <= max && !SiteGuard.inTown(level, new BlockPos(poi.x(), map.surface(poi.x(), poi.z()), poi.z()))) {
                ok.add(i);
            }
        }
        if (!ok.isEmpty()) {
            int i = ok.get(level.random.nextInt(ok.size()));
            run.usedPois.add(i);
            ContinentPois.Poi poi = all.get(i);
            return new Spot(new BlockPos(poi.x(), map.surface(poi.x(), poi.z()) + 1, poi.z()), "map.skyabyss.poi." + poi.kind());
        }
        for (int t = 0; t < 60; t++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = min + level.random.nextDouble() * (max - min);
            int x = (int) Math.floor(from.getX() + Math.cos(a) * d);
            int z = (int) Math.floor(from.getZ() + Math.sin(a) * d);
            BlockPos at = new BlockPos(x, map.surface(x, z) + 1, z);
            if (map.inside(x, z) && map.region(x, z) == run.region && !SiteGuard.inTown(level, at)) {
                return new Spot(at, null);
            }
        }
        return new Spot(from, null);
    }

    static void complete(ServerLevel level, CircuitRun run) {
        run.marks++;
        run.tasks++;
        for (ServerPlayer p : online(level, run)) {
            Notice.gain(p, "message.skyabyss.circuit.done", run.marks);
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        nextTask(level, run);
    }

    static void fail(ServerLevel level, CircuitRun run) {
        run.tasks++;
        for (ServerPlayer p : online(level, run)) {
            Notice.warn(p, "message.skyabyss.circuit.failed");
        }
        nextTask(level, run);
    }

    // 開発・自己テスト用: いまの課題を越えたことにする
    public static void forceComplete(ServerLevel level, CircuitRun run) {
        complete(level, run);
    }

    static List<ServerPlayer> online(ServerLevel level, CircuitRun run) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : run.members) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                p = run.fakes.get(id);
            }
            if (p != null && p.level() == level && p.isAlive()) {
                out.add(p);
            }
        }
        return out;
    }

    static double flat(BlockPos a, double x, double z) {
        return Math.hypot(a.getX() + 0.5 - x, a.getZ() + 0.5 - z);
    }

    @Nullable
    static ServerPlayer nearest(List<ServerPlayer> ps, BlockPos at, double within) {
        ServerPlayer best = null;
        double bd = within;
        for (ServerPlayer p : ps) {
            double d = flat(at, p.getX(), p.getZ());
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 毎 10 tick
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (ACTIVE.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        for (CircuitRun run : List.copyOf(ACTIVE)) {
            step(level, run, server.getTickCount() % 20 == 0);
        }
    }

    public static void step(ServerLevel level, CircuitRun run, boolean show) {
        long now = level.getGameTime();
        List<ServerPlayer> ps = online(level, run);
        // 誰もいない (落ちた・ほかの次元) あいだは時計だけ進む。全員が抜けたら終わり
        if (now >= run.end || run.members.isEmpty()) {
            finish(level.getServer(), run);
            return;
        }
        if (ps.isEmpty()) {
            return;
        }
        if (now - run.taskStart > TASK_LIMIT) {
            fail(level, run);
            return;
        }
        switch (run.kind) {
            case HUNT -> stepHunt(level, run, ps);
            case GUARD -> stepGuard(level, run, ps, now);
            case CARRY -> stepCarry(level, run, ps);
        }
        if (show && ACTIVE.contains(run)) {
            for (ServerPlayer p : ps) {
                view(p, run, now);
            }
        }
    }

    static void stepHunt(ServerLevel level, CircuitRun run, List<ServerPlayer> ps) {
        if (run.phase == 0) {
            ServerPlayer near = nearest(ps, run.spot, ARRIVE);
            if (near == null) {
                return;
            }
            // 初めて着いたときに数を決める。離れて敵が片づけられたあとに戻ったときは、残りの数だけ湧かせ直す
            if (run.need == 0) {
                run.need = Math.min(10, 4 + 2 * (ps.size() - 1));
            }
            run.mobs.clear();
            spawnAround(level, run, run.spot, run.need - run.progress, 2, 7, near);
            run.phase = 1;
            if (run.mobs.isEmpty()) {
                complete(level, run);
            }
            return;
        }
        // 倒した数 (onDeath が数える) で越える。倒さずに消えた (離れて片づけられた) 敵は、戻ったときに湧かせ直す
        if (run.progress >= run.need) {
            complete(level, run);
        } else if (alive(level, run) == 0) {
            run.phase = 0;
        }
    }

    static void stepGuard(ServerLevel level, CircuitRun run, List<ServerPlayer> ps, long now) {
        ServerPlayer near = nearest(ps, run.spot, GUARD_R);
        if (run.phase == 0) {
            if (near != null) {
                run.phase = 1;
                run.nextWave = now;
            }
            return;
        }
        if (near != null) {
            run.progress += 10;
        }
        if (now >= run.nextWave) {
            run.nextWave = now + WAVE_EVERY;
            spawnAround(level, run, run.spot, 2 + ps.size(), 12, 16, near != null ? near : ps.get(0));
        }
        if (run.progress >= GUARD_NEED) {
            complete(level, run);
        }
    }

    static void stepCarry(ServerLevel level, CircuitRun run, List<ServerPlayer> ps) {
        if (run.phase == 0) {
            ServerPlayer near = nearest(ps, run.spot, CARRY_R);
            if (near != null) {
                if (run.need == 0) {
                    run.need = Math.max(1, (int) flat(run.spot2, run.spot.getX() + 0.5, run.spot.getZ() + 0.5));
                }
                run.carrier = near.getUUID();
                run.dropped = false;
                run.phase = 1;
                for (ServerPlayer p : ps) {
                    Notice.hint(p, "message.skyabyss.circuit.carry_take", near.getDisplayName());
                }
            }
            return;
        }
        ServerPlayer c = null;
        for (ServerPlayer p : ps) {
            if (p.getUUID().equals(run.carrier)) {
                c = p;
            }
        }
        if (c == null) {
            // 背負った人が倒れた・離れた: 荷は最後にいた所に落ちる (その人が戻れば拾い直せる)
            run.phase = 0;
            run.carrier = null;
            run.dropped = true;
            for (ServerPlayer p : ps) {
                Notice.warn(p, "message.skyabyss.circuit.carry_drop");
            }
            return;
        }
        run.spot = c.blockPosition();
        c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, true));
        double left = flat(run.spot2, c.getX(), c.getZ());
        if (!run.ambushed && left < run.need * 0.55) {
            run.ambushed = true;
            for (ServerPlayer p : ps) {
                Notice.alert(p, "message.skyabyss.circuit.ambush");
            }
            double k = 9 / Math.max(1, left);
            BlockPos ahead = BlockPos.containing(c.getX() + (run.spot2.getX() - c.getX()) * k, c.getY(), c.getZ() + (run.spot2.getZ() - c.getZ()) * k);
            spawnAround(level, run, ahead, 2 + ps.size(), 2, 5, c);
        }
        if (left <= CARRY_R) {
            complete(level, run);
        }
    }

    // 課題の敵が倒れたら数える (倒す課題の進み)
    @SubscribeEvent
    static void onMobDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (ACTIVE.isEmpty() || event.getEntity().level().isClientSide()) {
            return;
        }
        UUID id = event.getEntity().getUUID();
        for (CircuitRun run : ACTIVE) {
            if (run.kind == CircuitRun.Kind.HUNT && run.mobs.remove(id)) {
                run.progress++;
                return;
            }
        }
    }

    static int alive(ServerLevel level, CircuitRun run) {
        int n = 0;
        for (UUID id : run.mobs) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    // 土地の敵を at のまわり (rMin〜rMax) に n 体。target を狙わせる
    static void spawnAround(ServerLevel level, CircuitRun run, BlockPos at, int n, int rMin, int rMax, ServerPlayer target) {
        ContinentMap map = ContinentMap.get();
        for (int i = 0, tries = 0; i < n && tries < n * 6; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = rMin + level.random.nextDouble() * (rMax - rMin);
            int x = (int) Math.floor(at.getX() + Math.cos(a) * d);
            int z = (int) Math.floor(at.getZ() + Math.sin(a) * d);
            BlockPos g = ground(level, x, z);
            if (g == null) {
                continue;
            }
            RegionSpawner.Entry e = RegionSpawner.pick(run.region, map.biome(x, z), RegionSpawner.night(level), level.random.nextInt(1000), x, z);
            if (e == null) {
                e = RegionSpawner.pick(run.region, map.biome(x, z), RegionSpawner.night(level), level.random.nextInt(1000));
            }
            Mob mob = e == null ? null : RegionSpawner.spawn(level, e.entity(), g, target);
            if (mob != null) {
                mob.setTarget(target);
                run.mobs.add(mob.getUUID());
                i++;
            }
        }
    }

    @Nullable
    static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos at = new BlockPos(x, y, z);
        if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at).isAir()
                || !level.getBlockState(at.above()).isAir()) {
            return null;
        }
        return at;
    }

    // ---------------------------------------------------------------- 見せる (目的の帯と光の柱)
    static Component place(@Nullable String key) {
        return key == null ? Component.translatable("circuit.skyabyss.place.beam") : Component.translatableWithFallback(key, "?");
    }

    public static Component taskLine(CircuitRun run) {
        return switch (run.kind) {
            case HUNT -> run.phase == 0 ? Component.translatable("circuit.skyabyss.task.hunt_go", place(run.place))
                    : Component.translatable("circuit.skyabyss.task.hunt", run.progress, run.need);
            case GUARD -> run.phase == 0 ? Component.translatable("circuit.skyabyss.task.guard_go", place(run.place))
                    : Component.translatable("circuit.skyabyss.task.guard", Math.max(0, (GUARD_NEED - run.progress) / 20));
            case CARRY -> run.phase == 0
                    ? (run.dropped ? Component.translatable("circuit.skyabyss.task.carry_pick")
                            : Component.translatable("circuit.skyabyss.task.carry_go", place(run.place)))
                    : Component.translatable("circuit.skyabyss.task.carry", place(run.place2));
        };
    }

    static BlockPos target(CircuitRun run) {
        return run.kind == CircuitRun.Kind.CARRY && run.phase == 1 ? run.spot2 : run.spot;
    }

    static void view(ServerPlayer p, CircuitRun run, long now) {
        if (p instanceof FakePlayer || p.connection == null) {
            return;
        }
        int seconds = (int) Math.max(0, (run.end - now) / 20);
        BlockPos t = target(run);
        PacketDistributor.sendToPlayer(p, new CircuitPayload(true, seconds, run.marks, taskLine(run), true, t));
        PacketDistributor.sendToPlayer(p, new TitanEndNetwork.Beam("circuit", RGB, t.getX() + 0.5, t.getY() + 1.0, t.getZ() + 0.5,
                1.2f, 1.0f, 1.2f, 0f, t.getY(), t.getY() + 48, 0.6f, 60));
    }

    static void clearView(ServerPlayer p) {
        if (p instanceof FakePlayer || p.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(p, CircuitPayload.off());
        PacketDistributor.sendToPlayer(p, new TitanEndNetwork.Beam("circuit", RGB, 0, 0, 0, 0f, 0f, 0f, 0f, 0, 0, 0f, 1));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && runOf(p) == null) {
            clearView(p);
        }
    }

    // ---------------------------------------------------------------- コマンド
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_circuit")
                .then(Commands.literal("start").executes(c -> startHere(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("leave").executes(c -> {
                    leave(c.getSource().getPlayerOrException());
                    return 1;
                })));
        // 開発用: /skyabyss circuit start <土地 2〜5> | next | goto (行き先のそばへ) | end | reset
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("circuit")
                        .then(Commands.literal("start").then(Commands.argument("region", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    if (runOf(p) != null) {
                                        return 0;
                                    }
                                    start(p.server, List.of(p), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "region"), p.blockPosition());
                                    return 1;
                                })))
                        .then(Commands.literal("next").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            CircuitRun run = runOf(p);
                            if (run != null) {
                                forceComplete(p.serverLevel(), run);
                            }
                            return run == null ? 0 : 1;
                        }))
                        .then(Commands.literal("goto").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            CircuitRun run = runOf(p);
                            if (run == null) {
                                return 0;
                            }
                            BlockPos t = target(run);
                            int x = t.getX() + 6;
                            int z = t.getZ() + 6;
                            p.teleportTo(x + 0.5, ContinentMap.get().surface(x, z) + 2, z + 0.5);
                            return 1;
                        }))
                        .then(Commands.literal("end").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            CircuitRun run = runOf(p);
                            if (run != null) {
                                finish(p.server, run);
                            }
                            return run == null ? 0 : 1;
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            c.getSource().getPlayerOrException().getPersistentData().remove(DAY);
                            c.getSource().getPlayerOrException().getPersistentData().remove(RUNS);
                            return 1;
                        }))));
    }
}

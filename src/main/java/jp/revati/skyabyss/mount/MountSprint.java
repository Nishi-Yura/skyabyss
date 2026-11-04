package jp.revati.skyabyss.mount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.ContinentLayout;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 乗り物の早駆けの道 (docs/design/38 の 8。2026-11-05 の封の箱の頁 3-1)。都から四つの土地の町へ向かう道に一本ずつ。
//   道の点 (ContinentLayout.roads の 1・3・5・8 本目) を RING_STEP ごとに区切り、輪 (光の柱) を順にくぐって終わりの輪まで。
//   始まり: 自分の乗り物に乗って、始まりの輪 (道の一点目) を通り抜けると時計が回る。乗っている人には始まりの輪が光る
//   進み:   次の輪だけが光る (柱と地面の粒の輪)。画面の下に時間と輪の数。降りる・10 分たつ・道から 200 離れると、やめる (損はない)
//   着いた: 自分の一番 (人の永続データ BEST + 道) と週の上位 5 人 (Board)。初めて走りきると鐘銭 FIRST_COINS
//   見る:   旅の番の「早駆けの記録を見る」(自分の一番・今週の上位 5 人)。地図に始まりの印 (MapMarks.Kind.SPRINT)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MountSprint {
    static final int[] ROADS = {1, 3, 5, 8};
    static final String[] IDS = {"north", "west", "south", "east"};
    public static final double RING_STEP = 90;
    public static final double PASS = 7;
    public static final int LIMIT = 20 * 60 * 10;
    static final double STRAY = 200;
    public static final int FIRST_COINS = 200;
    public static final String BEST = "skyabyss_sprint_best_";
    static final int RGB = 0xE8C27A;

    public record Route(String id, List<BlockPos> points) {
    }

    record Run(Route route, long start, int next) {
    }

    private static volatile List<Route> routes;
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Long> COOL = new HashMap<>();

    private MountSprint() {
    }

    public static List<Route> routes() {
        List<Route> r = routes;
        if (r == null) {
            synchronized (MountSprint.class) {
                if (routes == null) {
                    routes = List.copyOf(build());
                }
                r = routes;
            }
        }
        return r;
    }

    static List<Route> build() {
        List<List<BlockPos>> roads = ContinentLayout.get().roads();
        ContinentMap map = ContinentMap.get();
        List<Route> out = new ArrayList<>();
        for (int i = 0; i < ROADS.length; i++) {
            if (ROADS[i] >= roads.size()) {
                continue;
            }
            List<BlockPos> line = roads.get(ROADS[i]);
            List<BlockPos> pts = new ArrayList<>();
            pts.add(at(map, line.get(0).getX(), line.get(0).getZ()));
            double carry = 0;
            for (int k = 1; k < line.size(); k++) {
                BlockPos a = line.get(k - 1);
                BlockPos b = line.get(k);
                double len = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
                double t = RING_STEP - carry;
                while (t <= len) {
                    double f = t / len;
                    pts.add(at(map, (int) Math.round(a.getX() + (b.getX() - a.getX()) * f), (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * f)));
                    t += RING_STEP;
                }
                carry = len - (t - RING_STEP);
            }
            BlockPos end = line.get(line.size() - 1);
            BlockPos last = pts.get(pts.size() - 1);
            if (Math.hypot(end.getX() - last.getX(), end.getZ() - last.getZ()) > RING_STEP * 0.4) {
                pts.add(at(map, end.getX(), end.getZ()));
            } else {
                pts.set(pts.size() - 1, at(map, end.getX(), end.getZ()));
            }
            out.add(new Route(IDS[i], List.copyOf(pts)));
        }
        return out;
    }

    static BlockPos at(ContinentMap map, int x, int z) {
        return new BlockPos(x, map.surface(x, z) + 1, z);
    }

    @Nullable
    public static Route route(String id) {
        for (Route r : routes()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    static double flat(BlockPos a, double x, double z) {
        return Math.hypot(a.getX() + 0.5 - x, a.getZ() + 0.5 - z);
    }

    // 自分の乗り物に乗っているか
    static boolean riding(ServerPlayer p) {
        return p.getVehicle() instanceof MountEntity m && MountService.active(p) == m;
    }

    // ---------------------------------------------------------------- 見張り (5 tick ごと)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 5 != 2 || p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        step(p, p.level().getGameTime(), riding(p));
    }

    // 一歩 (自己テストからも。riding = 自分の乗り物に乗っている)
    public static void step(ServerPlayer p, long now, boolean riding) {
        Run run = RUNS.get(p.getUUID());
        if (run == null) {
            if (!riding || now < COOL.getOrDefault(p.getUUID(), 0L)) {
                return;
            }
            for (Route r : routes()) {
                BlockPos st = r.points().get(0);
                double d = flat(st, p.getX(), p.getZ());
                if (d < PASS && Math.abs(p.getY() - st.getY()) < 8) {
                    start(p, r, now);
                    return;
                }
                if (d < 48 && p.tickCount % 20 == 2) {
                    beam(p, "sprint_start", st, 40);
                }
            }
            return;
        }
        if (!riding) {
            stop(p, "message.skyabyss.sprint.off_mount");
            return;
        }
        long t = now - run.start();
        if (t > LIMIT) {
            stop(p, "message.skyabyss.sprint.timeout");
            return;
        }
        BlockPos next = run.route().points().get(run.next());
        double d = flat(next, p.getX(), p.getZ());
        if (d > STRAY + RING_STEP) {
            stop(p, "message.skyabyss.sprint.strayed");
            return;
        }
        if (d < PASS && Math.abs(p.getY() - next.getY()) < 10) {
            int n = run.next() + 1;
            p.serverLevel().playSound(null, next, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.9f, 0.8f + 0.06f * n);
            ring(p.serverLevel(), next);
            if (n >= run.route().points().size()) {
                finish(p, run, t);
                return;
            }
            run = new Run(run.route(), run.start(), n);
            RUNS.put(p.getUUID(), run);
            next = run.route().points().get(n);
        }
        if (p.tickCount % 10 == 2) {
            beam(p, "sprint", next, 20);
            ring(p.serverLevel(), next);
            p.displayClientMessage(Component.translatable("message.skyabyss.sprint.bar", Component.translatable("mount.skyabyss.sprint." + run.route().id()),
                    time(t), run.next(), run.route().points().size() - 1).withStyle(s -> s.withColor(RGB)), true);
        }
    }

    static void start(ServerPlayer p, Route r, long now) {
        RUNS.put(p.getUUID(), new Run(r, now, 1));
        Notice.hint(p, "message.skyabyss.sprint.start", Component.translatable("mount.skyabyss.sprint." + r.id()), r.points().size() - 1);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f);
    }

    static void stop(ServerPlayer p, String key) {
        RUNS.remove(p.getUUID());
        COOL.put(p.getUUID(), p.level().getGameTime() + 100);
        clearBeam(p);
        Notice.hint(p, key);
    }

    public static void cancel(ServerPlayer p) {
        RUNS.remove(p.getUUID());
        clearBeam(p);
    }

    public static boolean running(ServerPlayer p) {
        return RUNS.containsKey(p.getUUID());
    }

    public static int nextRing(ServerPlayer p) {
        Run r = RUNS.get(p.getUUID());
        return r == null ? -1 : r.next();
    }

    static void finish(ServerPlayer p, Run run, long ticks) {
        RUNS.remove(p.getUUID());
        COOL.put(p.getUUID(), p.level().getGameTime() + 200);
        clearBeam(p);
        String key = BEST + run.route().id();
        long best = p.getPersistentData().getLong(key);
        boolean first = best <= 0;
        if (first || ticks < best) {
            p.getPersistentData().putLong(key, ticks);
        }
        Component name = Component.translatable("mount.skyabyss.sprint." + run.route().id());
        Notice.gain(p, first || ticks < best ? "message.skyabyss.sprint.best" : "message.skyabyss.sprint.done", name, time(ticks), first ? "" : time(Math.min(best, ticks)));
        if (first) {
            Coins.give(p, FIRST_COINS);
            Notice.unlock(p, "message.skyabyss.sprint.first", name, FIRST_COINS);
        }
        if (p.server != null) {
            Board.get(p.server).enter(run.route().id(), p.getGameProfile().getName(), ticks);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);
    }

    public static long best(ServerPlayer p, String route) {
        return p.getPersistentData().getLong(BEST + route);
    }

    public static String time(long ticks) {
        long cs = ticks * 5;
        return String.format("%d:%02d.%d", cs / 6000, (cs / 100) % 60, (cs / 10) % 10);
    }

    static void ring(ServerLevel level, BlockPos c) {
        DustParticleOptions dust = new DustParticleOptions(new org.joml.Vector3f(0.91f, 0.76f, 0.48f), 1.6f);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            level.sendParticles(dust, c.getX() + 0.5 + Math.cos(a) * 3, c.getY() + 0.6, c.getZ() + 0.5 + Math.sin(a) * 3, 1, 0, 0, 0, 0);
        }
    }

    static void beam(ServerPlayer p, String key, BlockPos t, int ttl) {
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(p, new jp.revati.skyabyss.titan.end.TitanEndNetwork.Beam(key, RGB, t.getX() + 0.5, t.getY() + 1.0, t.getZ() + 0.5,
                1.2f, 1.0f, 1.2f, 0f, t.getY(), t.getY() + 40, 0.6f, ttl));
    }

    static void clearBeam(ServerPlayer p) {
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(p, new jp.revati.skyabyss.titan.end.TitanEndNetwork.Beam("sprint", RGB, 0, 0, 0, 0f, 0f, 0f, 0f, 0, 0, 0f, 1));
    }

    // 旅の番の「早駆けの記録を見る」: 道ごとに自分の一番と今週の上位 5 人
    public static void show(ServerPlayer p) {
        Board b = Board.get(p.server);
        p.sendSystemMessage(Component.translatable("message.skyabyss.sprint.board_head").withStyle(s -> s.withColor(RGB)));
        for (Route r : routes()) {
            long mine = best(p, r.id());
            p.sendSystemMessage(Component.translatable("message.skyabyss.sprint.board_route", Component.translatable("mount.skyabyss.sprint." + r.id()),
                    mine > 0 ? time(mine) : "―"));
            List<Board.Entry> top = b.top(r.id());
            for (int i = 0; i < top.size(); i++) {
                p.sendSystemMessage(Component.translatable("message.skyabyss.sprint.board_line", i + 1, top.get(i).name(), time(top.get(i).ticks())));
            }
        }
        jp.revati.skyabyss.endgame.ChatMenus.open(p);
    }

    // ---------------------------------------------------------------- 週の上位 5 人 (地上に保存。週が替わると空に)
    public static final class Board extends SavedData {
        private static final String NAME = "skyabyss_sprint_board";
        public static final int TOP = 5;

        public record Entry(String name, long ticks) {
        }

        final Map<String, List<Entry>> top = new HashMap<>();
        String week = "";

        public static Board get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Board::new, Board::load), NAME);
        }

        void roll() {
            String w = jp.revati.skyabyss.util.DayClock.weekId();
            if (!w.equals(week)) {
                week = w;
                top.clear();
                setDirty();
            }
        }

        public void enter(String route, String name, long ticks) {
            roll();
            List<Entry> list = top.computeIfAbsent(route, k -> new ArrayList<>());
            // 一人一行 (その人の今週の一番だけ)
            if (list.stream().anyMatch(e -> e.name().equals(name) && e.ticks() <= ticks)) {
                return;
            }
            list.removeIf(e -> e.name().equals(name));
            list.add(new Entry(name, ticks));
            list.sort((a, b) -> Long.compare(a.ticks(), b.ticks()));
            while (list.size() > TOP) {
                list.remove(list.size() - 1);
            }
            setDirty();
        }

        public List<Entry> top(String route) {
            roll();
            return List.copyOf(top.getOrDefault(route, List.of()));
        }

        static Board load(CompoundTag tag, HolderLookup.Provider registries) {
            Board b = new Board();
            b.week = tag.getString("week");
            CompoundTag routes = tag.getCompound("routes");
            for (String k : routes.getAllKeys()) {
                List<Entry> list = new ArrayList<>();
                for (Tag t : routes.getList(k, Tag.TAG_COMPOUND)) {
                    CompoundTag c = (CompoundTag) t;
                    list.add(new Entry(c.getString("name"), c.getLong("ticks")));
                }
                b.top.put(k, list);
            }
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putString("week", week);
            CompoundTag routes = new CompoundTag();
            top.forEach((k, list) -> {
                ListTag l = new ListTag();
                for (Entry e : list) {
                    CompoundTag c = new CompoundTag();
                    c.putString("name", e.name());
                    c.putLong("ticks", e.ticks());
                    l.add(c);
                }
                routes.put(k, l);
            });
            tag.put("routes", routes);
            return tag;
        }
    }
}

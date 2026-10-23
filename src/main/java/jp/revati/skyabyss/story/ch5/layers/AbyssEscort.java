package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.facility.AttendantEntity;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 形 B の層 (塩・灰) の護りの下り道 (docs/design/36 の 8.1・8.4。設計図は tools/abyss/gen_abyss_layers.py の build_expedition と layer_<層>.py)。
//   連れて下りる人: 塩 = 荷運びのロク (abyss_porter)、灰 = 隊長のトウヤ (survivor_ash)。野営の人は道のあいだ野営から消え、終わると戻る
//   始まり: 段が <層>_marks で、まだ送っていない人が、野営の北の下り口 (escstart_<層>) に立つ。その人の仲間・近くに来た人も加わる (participants)
//   歩く: 設計図の道すじ (esc_<層>_<n>) を一マスずつ。だれかが 10 以内にいる間だけ進む (置いていかない)
//   三つの待ち場 (escwait_<層>_<k>): 着くと足を止め、待ち場の仕事を確かにするまで動かない。そのあいだ待ち場の隅 (wpspawn) から敵が来て、人を狙う。
//     塩: 1 杭を打つ (重さの術で脆い床を渡り、向こうの杭を右クリック → 板の道) / 2 井戸の上の灯り (重さの術で塩風に乗り、灯りを右クリック → 梯子) /
//         3 錨を打つあいだ守る (45 秒。縄張りの白い貝守と塩の殻虫)
//     灰: 1 迷路の出口の火 (光の術の灯りの中の燠の跡をたどり、出口の火を灯す。右クリックか光の術) / 2 三つの術の封 (LayerPuzzles の封が開く) /
//         3 縄張りを払う (大きな燠の番と煤の子の群れ二つ)
//   気力: 連れて下りる人のそばの敵が削る (ボスバー)。尽きると倒れ、ひとつ前に確かにした待ち場 (無ければ野営) へ戻ってやり直し (失敗はその待ち場の仕事を戻す)
//   待ち場を確かにすると: そのとき近くにいた人とパーティに印 (AbyssMarks.take。REQUIRE の "clear:wp_<層>_<k>")
//   逃げの道 (三つ目のあと): そばに人がそろって 3 秒で走り出す。後ろから「崩れ」(塩: 床が崩れる) か「闇の波」(灰: 灯りを喰う。光の術で押し返せる) が追う。
//     人が呑まれる (崩れの後ろに 1.5 秒・闇の中に 3 秒) か、連れて下りる人に追いつかれると、三つ目の待ち場からやり直し。人が遅れると連れて下りる人は待つ
//   終わり: 連れて下りる人が格子の門の前 (escend_<層>) に着くと、加わった人とパーティに "escort:<層>"・三つの印・合図 abyss_escort_<層>。
//     格子の門は印が三つで送った人に上がる (AbyssMarks.canPassGate)。主が倒れると一言、しばらくして野営へ帰る
//   だれも 96 以内に来ないまま 3 分たつと、道をやめて野営へ戻る (次は初めから)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssEscort {
    public static final String TAG = "skyabyss_abyss_escort";
    public static final String SIGNAL = "abyss_escort_";
    public static final String NS = "message.skyabyss.abyss_v2.escort.";
    public static final List<String> LAYERS = List.of("salt", "ash");

    public enum Task { STAKE, BEACON, HOLD, SEAL, CLEAR }

    public enum State { WALK, WAIT, ARMED, ESCAPE, DONE }

    static final Map<String, Task[]> TASKS = Map.of("salt", new Task[] {Task.STAKE, Task.BEACON, Task.HOLD}, "ash",
            new Task[] {Task.BEACON, Task.SEAL, Task.CLEAR});
    static final Map<String, String> ROLE = Map.of("salt", "abyss_porter", "ash", "survivor_ash");
    static final Map<String, String> HOME = Map.of("salt", "trader_salt", "ash", "survivor_ash");
    static final Map<String, String> HARASS = Map.of("salt", "salt_husk", "ash", "soot_child");
    static final Map<String, String> ELITE = Map.of("salt", "white_shell_keeper", "ash", "ember_keeper");
    static final double WALK = 0.14;
    static final double RUN = 0.2;
    static final double FRONT = 0.16;
    static final double NEAR = 10;
    static final double JOIN = 18;
    static final int LEAD = 9;
    static final int ARM = 60;
    static final int HOLD_TICKS = 900;
    static final float ENDURANCE = 100f;
    static final float DRAIN = 2.5f;
    static final int MAX_HARASS = 6;
    static final long ABANDON = 3600;
    static final int CAUGHT_SALT = 30;
    static final int CAUGHT_ASH = 60;
    // 自己テストのあいだ、ふだんの見回りは止める (テストは自分で時刻と人を渡す)
    public static boolean paused;
    // 自己テスト: いない仲間を探す口 (偽の人は名簿にいない)。null ならサーバーの名簿
    @Nullable
    public static java.util.function.Function<UUID, ServerPlayer> lookup;

    public static final class Run {
        final String layer;
        final List<Vec3> nodes = new ArrayList<>();
        final List<BlockPos> cells = new ArrayList<>();
        final int[] waitIdx = new int[3];
        int endIdx;
        @Nullable
        UUID npc;
        double at;
        int cp;
        State state = State.WALK;
        final Set<UUID> who = new LinkedHashSet<>();
        float endurance = ENDURANCE;
        long since;
        long nextHarass;
        long holdUntil;
        long lastSeen;
        long lastSay;
        boolean triggered;
        boolean warned;
        final List<UUID> mobs = new ArrayList<>();
        @Nullable
        LayerFights.Run elite;
        int wave;
        @Nullable
        ServerBossEvent bar;
        double front;
        int frontDone;
        long pushedAt;
        final Map<BlockPos, BlockState> saved = new HashMap<>();
        final Map<UUID, Integer> caught = new HashMap<>();
        long doneAt = -1;
        long lordAt = -1;
        int fails;

        Run(String layer) {
            this.layer = layer;
        }

        public State state() {
            return state;
        }

        public int secured() {
            return cp;
        }

        public float endurance() {
            return endurance;
        }

        public double at() {
            return at;
        }

        public int fails() {
            return fails;
        }

        public Set<UUID> participants() {
            return who;
        }

        public Task task() {
            return TASKS.get(layer)[Math.min(cp, 2)];
        }

        Vec3 pos() {
            int i = Math.max(0, Math.min(nodes.size() - 1, (int) Math.floor(at)));
            double f = at - i;
            if (i + 1 >= nodes.size() || f <= 0) {
                return nodes.get(i);
            }
            return nodes.get(i).lerp(nodes.get(i + 1), f);
        }
    }

    private static final Map<String, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Long> HINTED = new HashMap<>();
    private static boolean listening;

    private AbyssEscort() {
    }

    public static boolean expedition(String layer) {
        return LAYERS.contains(layer);
    }

    @Nullable
    public static Run run(String layer) {
        return RUNS.get(layer);
    }

    // その役の人が道に出ている (野営の人を立てない)
    public static boolean busy(String role) {
        for (Run r : RUNS.values()) {
            if (ROLE.get(r.layer).equals(role)) {
                return true;
            }
        }
        return false;
    }

    public static boolean escorted(ServerPlayer p, String layer) {
        return LayerData.get(p.server).has(p.getUUID(), "escort:" + layer) || Layers.pastMarks(p, layer);
    }

    // 送り出せる人: 段が <層>_marks で、まだ送っていない
    public static boolean needs(ServerPlayer p, String layer) {
        return (layer + "_marks").equals(Layers.stage(p)) && !LayerData.get(p.server).has(p.getUUID(), "escort:" + layer);
    }

    public static Component npcName(String layer) {
        return Component.translatable("entity.skyabyss.attendant." + ROLE.get(layer));
    }

    @Nullable
    public static Entity npc(ServerLevel level, Run run) {
        return run.npc == null ? null : level.getEntity(run.npc);
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (paused) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || !AbyssBuild.built(server)) {
            return;
        }
        if (!listening) {
            listening = true;
            AbyssArts.ON_CAST.add(AbyssEscort::onCast);
        }
        if (RUNS.isEmpty() && level.players().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<ServerPlayer> players = List.copyOf(level.players());
        for (Run r : List.copyOf(RUNS.values())) {
            tick(level, r, now, players);
        }
        if (server.getTickCount() % 10 == 6) {
            for (String layer : LAYERS) {
                if (!RUNS.containsKey(layer)) {
                    tryStart(level, layer, now, players);
                }
            }
        }
        if (server.getTickCount() % 200 == 77) {
            // 起動し直しで道が消えたあとに残った、道の人を片づける (野営の人は AbyssSurvivors が立て直す)
            for (String layer : LAYERS) {
                BlockPos c = AbyssBuild.mark("camp_" + layer);
                if (RUNS.containsKey(layer) || c == null || !level.isLoaded(c)) {
                    continue;
                }
                for (Entity e : level.getEntitiesOfClass(AttendantEntity.class, new net.minecraft.world.phys.AABB(c).inflate(120, 50, 120),
                        e -> e.getTags().contains(TAG + "_" + layer))) {
                    e.discard();
                }
            }
        }
    }

    // 下り口に送り出せる人が立てば始める (始めたら true)
    public static boolean tryStart(ServerLevel level, String layer, long now, List<ServerPlayer> players) {
        BlockPos s = AbyssBuild.mark("escstart_" + layer);
        if (s == null || RUNS.containsKey(layer)) {
            return false;
        }
        for (ServerPlayer p : players) {
            if (!p.isAlive() || p.isSpectator() || Math.abs(p.getY() - s.getY()) > 3 || p.position().distanceTo(Vec3.atBottomCenterOf(s)) > 3.5) {
                continue;
            }
            if (needs(p, layer)) {
                start(level, layer, p, now);
                return true;
            }
            if ((layer + "_camp").equals(Layers.stage(p))) {
                Long last = HINTED.get(p.getUUID());
                if (last == null || now - last > 400) {
                    HINTED.put(p.getUUID(), now);
                    Notice.hint(p, NS + "talk_first." + layer);
                }
            }
        }
        return false;
    }

    public static Run start(ServerLevel level, String layer, ServerPlayer starter, long now) {
        Run run = new Run(layer);
        for (int i = 0;; i++) {
            BlockPos c = AbyssBuild.mark("esc_" + layer + "_" + i);
            if (c == null) {
                break;
            }
            run.cells.add(c);
            run.nodes.add(feet(level, c));
        }
        for (int k = 0; k < 3; k++) {
            run.waitIdx[k] = indexOf(run, AbyssBuild.mark("escwait_" + layer + "_" + (k + 1)));
        }
        run.endIdx = indexOf(run, AbyssBuild.mark("escend_" + layer));
        if (run.endIdx <= 0) {
            run.endIdx = run.nodes.size() - 1;
        }
        RUNS.put(layer, run);
        resetGadgets(level, layer);
        // 野営の人を下げて、道の人を立てる
        BlockPos home = AbyssBuild.mark(HOME.get(layer));
        if (home != null) {
            for (AttendantEntity a : level.getEntitiesOfClass(AttendantEntity.class, new net.minecraft.world.phys.AABB(home).inflate(40, 12, 40),
                    e -> e.getTags().contains(AbyssSurvivors.TAG) && ROLE.get(layer).equals(e.roleId()))) {
                a.discard();
            }
        }
        spawnNpc(level, run);
        run.who.add(starter.getUUID());
        run.lastSeen = now;
        run.since = now;
        say(level, run, "start." + layer + "_1");
        LayerFights.later(level.getServer(), 60, () -> say(level, run, "start." + layer + "_2"));
        level.playSound(null, starter.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1f, 0.8f);
        return run;
    }

    static int indexOf(Run run, @Nullable BlockPos m) {
        if (m == null) {
            return -1;
        }
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < run.cells.size(); i++) {
            double d = run.cells.get(i).distSqr(m);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    // 立つ高さ (段と半石は半分上)
    static Vec3 feet(ServerLevel level, BlockPos c) {
        BlockState st = level.getBlockState(c);
        double y = c.getY();
        if (st.getBlock() instanceof StairBlock || st.getBlock() instanceof SlabBlock && st.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            y += 0.5;
        }
        return new Vec3(c.getX() + 0.5, y, c.getZ() + 0.5);
    }

    static void spawnNpc(ServerLevel level, Run run) {
        AttendantEntity a = FacilitySetup.ATTENDANT.get().create(level);
        if (a == null) {
            return;
        }
        a.setRoleId(ROLE.get(run.layer));
        a.addTag(TAG);
        a.addTag(TAG + "_" + run.layer);
        Vec3 p = run.pos();
        a.moveTo(p.x, p.y, p.z, 180f, 0f);
        a.setNoAi(true);
        a.setNoGravity(true);
        a.noPhysics = true;
        a.setPersistenceRequired();
        level.addFreshEntity(a);
        run.npc = a.getUUID();
    }

    // ---------------------------------------------------------------- 一回の見回り (毎 tick)
    public static void tick(ServerLevel level, Run run, long now, List<ServerPlayer> players) {
        Entity npc = npc(level, run);
        if (npc == null || !npc.isAlive()) {
            spawnNpc(level, run);
            npc = npc(level, run);
            if (npc == null) {
                return;
            }
        }
        Vec3 here = run.pos();
        List<ServerPlayer> party = new ArrayList<>();
        boolean near = false;
        for (ServerPlayer p : players) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            double d = p.position().distanceTo(here);
            if (d <= JOIN && Math.abs(p.getY() - here.y) < 12 && run.who.add(p.getUUID())) {
                joined(level, run, p);
            }
            if (run.who.contains(p.getUUID())) {
                party.add(p);
                if (d <= NEAR && Math.abs(p.getY() - here.y) < 14) {
                    near = true;
                }
                if (d <= 96) {
                    run.lastSeen = now;
                }
            }
        }
        switch (run.state) {
            case WALK -> {
                int limit = run.cp < 3 ? run.waitIdx[run.cp] : run.endIdx;
                if (near) {
                    if (advance(run, WALK, limit)) {
                        enterWait(level, run, now);
                    }
                } else if (now - run.lastSay > 300 && !party.isEmpty()) {
                    run.lastSay = now;
                    say(level, run, "wait_you." + run.layer);
                }
            }
            case WAIT -> {
                if (now % 10 == 0) {
                    waitTick(level, run, now, party);
                }
            }
            case ARMED -> {
                if (!near) {
                    run.since = now;
                } else if (now - run.since >= ARM) {
                    startEscape(level, run, now);
                } else if (now - run.since == 1) {
                    say(level, run, "arm." + run.layer);
                }
            }
            case ESCAPE -> escapeTick(level, run, now, party);
            case DONE -> {
                if (run.lordAt < 0) {
                    for (ServerPlayer p : party) {
                        if (AbyssMarks.lordBeaten(p, run.layer)) {
                            run.lordAt = now;
                            say(level, run, "after." + run.layer);
                            break;
                        }
                    }
                }
                if (run.lordAt >= 0 && now - run.lordAt > 200 || now - run.doneAt > 6000) {
                    end(level, run, true);
                    return;
                }
            }
        }
        // 位置 (道すじの上に置き直す。押されても戻る)
        Vec3 p = run.pos();
        Vec3 ahead = run.nodes.get(Math.min(run.nodes.size() - 1, (int) Math.floor(run.at) + 1));
        float yaw = npc.getYRot();
        if (ahead.distanceToSqr(p) > 1e-4 && (run.state == State.WALK || run.state == State.ESCAPE)) {
            yaw = (float) (Math.atan2(ahead.z - p.z, ahead.x - p.x) * 180 / Math.PI) - 90f;
        } else if (!party.isEmpty()) {
            ServerPlayer look = party.get(0);
            yaw = (float) (Math.atan2(look.getZ() - p.z, look.getX() - p.x) * 180 / Math.PI) - 90f;
        }
        npc.setPos(p.x, p.y, p.z);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        if (npc instanceof LivingEntity le) {
            le.setYBodyRot(yaw);
        }
        if (now % 10 == 0) {
            bar(level, run, players);
        }
        if (run.state != State.DONE && now - run.lastSeen > ABANDON) {
            end(level, run, false);
        }
    }

    // 道すじに沿って dist だけ進む。limit の一マスに着いたら true
    static boolean advance(Run run, double dist, int limit) {
        while (dist > 1e-6 && run.at < limit) {
            int i = (int) Math.floor(run.at);
            double seg = run.nodes.get(i).distanceTo(run.nodes.get(Math.min(i + 1, run.nodes.size() - 1)));
            if (seg < 1e-6) {
                run.at = i + 1;
                continue;
            }
            double left = (1 - (run.at - i)) * seg;
            if (dist >= left) {
                run.at = i + 1;
                dist -= left;
            } else {
                run.at += dist / seg;
                dist = 0;
            }
        }
        if (run.at >= limit) {
            run.at = limit;
            return true;
        }
        return false;
    }

    static void joined(ServerLevel level, Run run, ServerPlayer p) {
        Notice.hint(p, NS + "joined", npcName(run.layer));
        // 灰の野営の話の相手 (トウヤ) が道に出ていれば、加わったことを話したことにする
        if ((run.layer + "_camp").equals(Layers.stage(p)) && ROLE.get(run.layer).startsWith("survivor_")) {
            Quests.onTalk(p, "skyabyss:attendant_" + ROLE.get(run.layer), "ch5_" + run.layer + "_camp");
        }
    }

    // ---------------------------------------------------------------- 待ち場
    static void enterWait(ServerLevel level, Run run, long now) {
        run.state = State.WAIT;
        run.since = now;
        run.nextHarass = now + 100;
        run.triggered = false;
        run.warned = false;
        run.wave = 0;
        int k = run.cp + 1;
        Task t = run.task();
        say(level, run, "wp." + run.layer + "_" + k);
        BlockPos c = AbyssBuild.mark("wp_" + run.layer + "_" + k);
        if (t == Task.HOLD) {
            run.holdUntil = now + HOLD_TICKS;
            spawnElite(level, run, c);
        } else if (t == Task.CLEAR) {
            spawnElite(level, run, c);
            wave(level, run, k, 3);
        } else if (t == Task.SEAL) {
            LayerPuzzles.resetSeal();
        }
        if (c != null) {
            level.playSound(null, c, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.8f, 0.7f);
        }
    }

    static void spawnElite(ServerLevel level, Run run, @Nullable BlockPos c) {
        if (c == null) {
            return;
        }
        if (run.elite != null) {
            // 前の主が残っていれば (バーごと) 片づけてから
            for (UUID u : run.elite.mobs) {
                Entity e = level.getEntity(u);
                if (e != null) {
                    e.discard();
                }
            }
            if (run.elite.bar != null) {
                run.elite.bar.removeAllPlayers();
            }
            run.elite = null;
        }
        String name = "abyss_v2.skyabyss.elite.terr_" + run.layer;
        BlockPos at = c.offset(0, 0, run.layer.equals("salt") ? -4 : 4);
        LivingEntity boss = LayerFights.elite(level, ELITE.get(run.layer), at, 3.0, 1.5, name);
        if (boss != null) {
            run.elite = new LayerFights.Run("wp_" + run.layer);
            run.elite.mobs.add(boss.getUUID());
            boss.addTag(TAG);
        }
    }

    static void wave(ServerLevel level, Run run, int k, int base) {
        int n = base + Math.max(0, run.who.size() - 1);
        for (int i = 0; i < n; i++) {
            BlockPos s = AbyssBuild.mark("wpspawn_" + run.layer + "_" + k + "_" + (i % 4));
            if (s == null) {
                continue;
            }
            Entity e = AbyssMobSpawner.spawnAt(level, HARASS.get(run.layer), s.getX() + 0.5 + (i / 4) * 0.6, s.getY(), s.getZ() + 0.5, 1);
            if (e != null) {
                e.addTag(LayerFights.TAG);
                e.addTag(TAG);
                run.mobs.add(e.getUUID());
                level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
            }
        }
        run.wave++;
    }

    static void waitTick(ServerLevel level, Run run, long now, List<ServerPlayer> party) {
        int k = run.cp + 1;
        Task t = run.task();
        Entity npc = npc(level, run);
        // 敵: 隅から来て、連れて下りる人を狙う
        if (t != Task.CLEAR && now >= run.nextHarass && alive(level, run.mobs) < MAX_HARASS) {
            wave(level, run, k, 2);
            run.nextHarass = now + (t == Task.HOLD ? 200 : 260);
            if (run.wave == 1) {
                say(level, run, "harass." + run.layer);
            }
        }
        LivingEntity boss = eliteBoss(level, run);
        if (boss != null && run.elite != null) {
            LayerFights.eliteTick(level, run.elite, boss, party, now);
        }
        Vec3 at = run.pos();
        for (UUID u : run.mobs) {
            if (!(level.getEntity(u) instanceof Mob m) || !m.isAlive()) {
                continue;
            }
            double d = m.position().distanceTo(at);
            if (d < 1.9) {
                run.endurance -= DRAIN;
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 1.2, at.z, 2, 0.2, 0.2, 0.2, 0.1);
                if (npc != null && now % 20 == 0) {
                    level.playSound(null, npc.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.NEUTRAL, 0.6f, 0.8f);
                }
            } else if (now % 20 == 0 && npc != null && nearestPlayer(m, party) > 4) {
                m.getNavigation().moveTo(npc, 1.15);
            }
        }
        if (!run.warned && run.endurance < ENDURANCE * 0.35f) {
            run.warned = true;
            say(level, run, "worn." + run.layer);
        }
        if (run.endurance <= 0) {
            fail(level, run, now);
            return;
        }
        boolean done = switch (t) {
            case STAKE -> run.triggered;
            case BEACON -> run.triggered || lit(level, run.layer);
            case SEAL -> LayerData.get(level.getServer()).isOpen("seal_ash");
            case HOLD -> {
                long left = run.holdUntil - now;
                if (now % 20 == 0 && left > 0) {
                    for (ServerPlayer p : party) {
                        p.displayClientMessage(Component.translatable(NS + "hold_left", (left + 19) / 20), true);
                    }
                }
                yield left <= 0 && boss == null;
            }
            case CLEAR -> {
                if (alive(level, run.mobs) == 0 && run.wave < 2) {
                    wave(level, run, k, 4);
                    say(level, run, "wave." + run.layer);
                }
                yield boss == null && run.wave >= 2 && alive(level, run.mobs) == 0;
            }
        };
        if (done) {
            secure(level, run, now, party);
        }
    }

    static boolean lit(ServerLevel level, String layer) {
        BlockPos b = AbyssBuild.mark("beacon_" + layer);
        return b != null && level.isLoaded(b) && AbyssLight.lampAt(level, b) >= 6;
    }

    @Nullable
    static LivingEntity eliteBoss(ServerLevel level, Run run) {
        if (run.elite == null || run.elite.mobs.isEmpty()) {
            return null;
        }
        Entity e = level.getEntity(run.elite.mobs.get(0));
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    static double nearestPlayer(Entity e, List<ServerPlayer> party) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : party) {
            best = Math.min(best, p.distanceTo(e));
        }
        return best;
    }

    static int alive(ServerLevel level, List<UUID> ids) {
        int n = 0;
        for (UUID u : ids) {
            Entity e = level.getEntity(u);
            if (e != null && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    // 待ち場を確かにした: 仕掛けを開け、近くの人とパーティに印。三つ目なら逃げの支度
    public static void secure(ServerLevel level, Run run, long now, List<ServerPlayer> party) {
        int k = run.cp + 1;
        Task t = run.task();
        clearMobs(level, run);
        switch (t) {
            case STAKE -> {
                fill(level, "bridge_salt", Blocks.SPRUCE_PLANKS.defaultBlockState(), null);
                level.playSound(null, AbyssBuild.mark("stake_salt"), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.4f, 0.7f);
            }
            case BEACON -> {
                light(level, AbyssBuild.mark("beacon_" + run.layer), true);
                if ("salt".equals(run.layer)) {
                    ladders(level, true);
                }
            }
            default -> {
            }
        }
        LayerData d = LayerData.get(level.getServer());
        Vec3 at = run.pos();
        for (ServerPlayer p : party) {
            if (p.position().distanceTo(at) > 64) {
                continue;
            }
            for (UUID u : AbyssMarks.withParty(p)) {
                d.set(u, "clear:wp_" + run.layer + "_" + k);
            }
            giveMark(p, run.layer, k);
        }
        run.cp = k;
        run.endurance = ENDURANCE;
        say(level, run, "secured." + run.layer + "_" + k);
        if (k >= 3) {
            run.state = State.ARMED;
            run.since = now;
        } else {
            run.state = State.WALK;
        }
    }

    // 印を渡す (まだなら台で取ったのと同じに。仲間の分で持っていれば、その人の段の合図だけ)
    static void giveMark(ServerPlayer p, String layer, int k) {
        if (!LayerData.get(p.server).has(p.getUUID(), "mark:" + layer + "_" + k)) {
            AbyssMarks.take(p, layer, k);
        } else {
            Quests.signal(p, AbyssMarks.SIGNAL_MARK + layer + "_" + k);
        }
    }

    // 倒れた (気力が尽きた): 敵を消し、ひとつ前の待ち場 (無ければ野営) へ戻ってやり直す
    public static void fail(ServerLevel level, Run run, long now) {
        clearMobs(level, run);
        run.fails++;
        say(level, run, "fail." + run.layer);
        run.at = run.cp == 0 ? 0 : run.waitIdx[run.cp - 1];
        run.endurance = ENDURANCE;
        run.state = State.WALK;
        run.since = now;
        run.triggered = false;
        Entity npc = npc(level, run);
        if (npc != null) {
            level.sendParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        }
    }

    static void clearMobs(ServerLevel level, Run run) {
        for (UUID u : run.mobs) {
            Entity e = level.getEntity(u);
            if (e != null) {
                level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
                e.discard();
            }
        }
        run.mobs.clear();
        if (run.elite != null) {
            for (UUID u : run.elite.mobs) {
                Entity e = level.getEntity(u);
                if (e != null) {
                    e.discard();
                }
            }
            if (run.elite.bar != null) {
                run.elite.bar.removeAllPlayers();
            }
            run.elite = null;
        }
    }

    // ---------------------------------------------------------------- 逃げの道
    static void startEscape(ServerLevel level, Run run, long now) {
        run.state = State.ESCAPE;
        run.since = now;
        run.front = run.waitIdx[2] - 3;
        run.frontDone = (int) Math.floor(run.front);
        run.caught.clear();
        say(level, run, "run." + run.layer);
        BlockPos c = AbyssBuild.mark("wp_" + run.layer + "_3");
        if (c != null) {
            level.playSound(null, c, "salt".equals(run.layer) ? SoundEvents.CALCITE_BREAK : SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2f, 0.5f);
        }
    }

    static void escapeTick(ServerLevel level, Run run, long now, List<ServerPlayer> party) {
        boolean salt = "salt".equals(run.layer);
        // 人の進み (道すじの何番目の近くにいるか)
        int lo = run.waitIdx[2] - 4;
        int best = -1;
        Map<UUID, Integer> prog = new HashMap<>();
        for (ServerPlayer p : party) {
            int i = progress(run, p.position(), lo, run.endIdx);
            prog.put(p.getUUID(), i);
            best = Math.max(best, i);
        }
        // 連れて下りる人は、遅れた人を待つ (崩れは待たない)
        if (best < 0 || run.at - best < LEAD) {
            if (advance(run, RUN, run.endIdx)) {
                complete(level, run, now, party);
                return;
            }
        } else if (now - run.lastSay > 100) {
            run.lastSay = now;
            say(level, run, "lead." + run.layer);
        }
        if (now - run.since < ARM / 2) {
            return;
        }
        run.front = Math.min(run.endIdx, run.front + FRONT);
        while (run.frontDone < Math.floor(run.front)) {
            run.frontDone++;
            if (run.frontDone >= 0 && run.frontDone < run.cells.size()) {
                if (salt) {
                    collapse(level, run, run.cells.get(run.frontDone));
                } else {
                    eat(level, run, run.cells.get(run.frontDone));
                }
            }
        }
        Vec3 f = run.nodes.get(Math.max(0, Math.min(run.nodes.size() - 1, (int) Math.floor(run.front))));
        if (now % 2 == 0) {
            if (salt) {
                level.sendParticles(ParticleTypes.CLOUD, f.x, f.y + 0.4, f.z, 6, 1.0, 0.3, 1.0, 0.02);
                level.sendParticles(ParticleTypes.WHITE_ASH, f.x, f.y + 1.5, f.z, 20, 1.2, 1.2, 1.2, 0.02);
            } else {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, f.x, f.y + 1, f.z, 10, 1.0, 1.0, 1.0, 0.01);
                level.sendParticles(ParticleTypes.SQUID_INK, f.x, f.y + 1, f.z, 6, 0.8, 0.8, 0.8, 0.01);
            }
        }
        if (now % 20 == 0) {
            level.playSound(null, BlockPos.containing(f), salt ? SoundEvents.CALCITE_BREAK : SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE,
                    1.2f, salt ? 0.5f : 0.4f);
        }
        // 呑まれる
        for (ServerPlayer p : party) {
            Integer i = prog.get(p.getUUID());
            if (i == null || i < 0) {
                continue;
            }
            if (i < run.front - 1) {
                int c = run.caught.merge(p.getUUID(), 1, Integer::sum);
                if (c % 10 == 1) {
                    if (salt) {
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, true));
                        p.hurt(level.damageSources().fall(), 2f);
                    } else {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, true));
                        p.hurt(level.damageSources().inFire(), 1.5f);
                    }
                    Notice.alert(p, NS + "caught." + run.layer);
                }
                if (c > (salt ? CAUGHT_SALT : CAUGHT_ASH)) {
                    failEscape(level, run, now, party);
                    return;
                }
            } else {
                run.caught.remove(p.getUUID());
            }
        }
        if (run.front >= run.at - 0.5) {
            failEscape(level, run, now, party);
        }
    }

    // その位置の近くの道すじの番号 (lo〜hi。道から 4 より遠ければ -1)
    static int progress(Run run, Vec3 p, int lo, int hi) {
        int best = -1;
        double bd = 16;
        for (int i = Math.max(0, lo); i <= Math.min(hi, run.nodes.size() - 1); i++) {
            double d = run.nodes.get(i).distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    // 塩の崩れ: 道すじのまわりの床 (足もとの一つ下) を崩す。あとで戻す
    static void collapse(ServerLevel level, Run run, BlockPos c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = c.offset(dx, -1, dz);
                BlockState above = level.getBlockState(q.above());
                BlockState st = level.getBlockState(q);
                if (st.isAir() || !above.getCollisionShape(level, q.above()).isEmpty() && !(above.getBlock() instanceof StairBlock)) {
                    continue;
                }
                run.saved.putIfAbsent(q.immutable(), st);
                level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 4, 0.8, 0.1, 0.8, 0.01);
    }

    // 灰の闇の波: 道すじの上の灯りを喰う。あとで戻す
    static void eat(ServerLevel level, Run run, BlockPos c) {
        for (int dy = 2; dy <= 6; dy++) {
            BlockPos q = c.above(dy);
            BlockState st = level.getBlockState(q);
            if (st.is(Blocks.SOUL_LANTERN) || st.is(Blocks.LANTERN)) {
                run.saved.putIfAbsent(q.immutable(), st);
                level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.SMOKE, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    static void restore(ServerLevel level, Run run) {
        // 戻す順: 下の物から (吊りの灯りは上の鎖があるので順は問わない)
        List<Map.Entry<BlockPos, BlockState>> es = new ArrayList<>(run.saved.entrySet());
        es.sort((a, b) -> Integer.compare(a.getKey().getY(), b.getKey().getY()));
        for (Map.Entry<BlockPos, BlockState> e : es) {
            if (level.isLoaded(e.getKey()) && level.getBlockState(e.getKey()).isAir()) {
                level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
            }
        }
        run.saved.clear();
    }

    // 呑まれた / 追いつかれた: 三つ目の待ち場からやり直し
    static void failEscape(ServerLevel level, Run run, long now, List<ServerPlayer> party) {
        restore(level, run);
        run.fails++;
        say(level, run, "escape_fail." + run.layer);
        run.at = run.waitIdx[2];
        Vec3 back = run.nodes.get(run.waitIdx[2]);
        for (ServerPlayer p : party) {
            if (p.position().distanceTo(back) < 140) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
                if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
                    p.moveTo(back.x + 1.5, back.y, back.z, p.getYRot(), 0f);
                } else {
                    p.teleportTo(level, back.x + 1.5, back.y, back.z, p.getYRot(), 0f);
                }
                p.fallDistance = 0;
            }
        }
        run.caught.clear();
        run.state = State.ARMED;
        run.since = now;
    }

    static void complete(ServerLevel level, Run run, long now, List<ServerPlayer> party) {
        restore(level, run);
        run.state = State.DONE;
        run.doneAt = now;
        LayerData d = LayerData.get(level.getServer());
        List<ServerPlayer> present = new ArrayList<>();
        for (ServerPlayer p : party) {
            present.add(p);
            for (UUID u : AbyssMarks.withParty(p)) {
                d.set(u, "escort:" + run.layer);
                for (int k = 1; k <= 3; k++) {
                    d.set(u, "clear:wp_" + run.layer + "_" + k);
                }
            }
            for (int k = 1; k <= 3; k++) {
                giveMark(p, run.layer, k);
            }
        }
        // 加わったが、いまはここにいない人にも (記録だけ)
        for (UUID u : run.who) {
            d.set(u, "escort:" + run.layer);
        }
        if (lookup != null) {
            Quests.signalWithParty(level, present, SIGNAL + run.layer, lookup);
        } else {
            Quests.signalWithParty(level, present, SIGNAL + run.layer);
        }
        say(level, run, "done." + run.layer + "_1");
        LayerFights.later(level.getServer(), 70, () -> say(level, run, "done." + run.layer + "_2"));
        BlockPos g = AbyssBuild.mark("gate_" + run.layer);
        if (g != null) {
            level.playSound(null, g, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.4f, 0.7f);
        }
    }

    // 道を終える: 連れて下りる人を消し (野営に戻る)、敵と崩れを戻す
    public static void end(ServerLevel level, Run run, boolean home) {
        clearMobs(level, run);
        restore(level, run);
        if (run.bar != null) {
            run.bar.removeAllPlayers();
        }
        Entity npc = npc(level, run);
        if (npc != null) {
            level.sendParticles(ParticleTypes.CLOUD, npc.getX(), npc.getY() + 1, npc.getZ(), 16, 0.4, 0.8, 0.4, 0.02);
            if (home) {
                say(level, run, "home." + run.layer);
            }
            npc.discard();
        }
        RUNS.remove(run.layer);
    }

    // ---------------------------------------------------------------- ボスバー (連れて下りる人の気力)
    static void bar(ServerLevel level, Run run, List<ServerPlayer> players) {
        if (run.state == State.DONE) {
            if (run.bar != null) {
                run.bar.removeAllPlayers();
            }
            return;
        }
        if (run.bar == null) {
            run.bar = new ServerBossEvent(Component.translatable(NS + "bar." + run.layer), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        float f = Math.max(0f, Math.min(1f, run.endurance / ENDURANCE));
        run.bar.setProgress(f);
        run.bar.setColor(f > 0.6f ? BossEvent.BossBarColor.GREEN : f > 0.3f ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        Vec3 at = run.pos();
        for (ServerPlayer p : players) {
            if (p.connection == null) {
                continue;
            }
            if (run.who.contains(p.getUUID()) && p.position().distanceTo(at) < 48) {
                run.bar.addPlayer(p);
            } else {
                run.bar.removePlayer(p);
            }
        }
    }

    static void say(ServerLevel level, Run run, String key) {
        Vec3 at = run.pos();
        for (UUID u : run.who) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(u);
            if (p != null && p.level() == level && p.position().distanceTo(at) < 64) {
                Notice.narrate(p, NS + key);
            }
        }
    }

    // ---------------------------------------------------------------- 仕掛け (板の道・梯子・灯り・封)
    static void resetGadgets(ServerLevel level, String layer) {
        if ("salt".equals(layer)) {
            fill(level, "bridge_salt", Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.SPRUCE_PLANKS);
            ladders(level, false);
        } else {
            LayerData d = LayerData.get(level.getServer());
            if (d.isOpen("seal_ash")) {
                d.close("seal_ash");
                resealAsh(level);
            }
            LayerPuzzles.resetSeal();
        }
        light(level, AbyssBuild.mark("beacon_" + layer), false);
    }

    // 箱の中を state に (only があればその種のマスと空気だけ)
    static void fill(ServerLevel level, String name, BlockState state, @Nullable Block only) {
        BlockPos a = AbyssBuild.mark(name + "_a");
        BlockPos b = AbyssBuild.mark(name + "_b");
        if (a == null || b == null || !level.isLoaded(a)) {
            return;
        }
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            BlockState st = level.getBlockState(q);
            if (only == null || st.is(only) || st.isAir()) {
                level.setBlock(q, state, Block.UPDATE_ALL);
            }
        }
    }

    // 塩の井戸の梯子 (上の縁の口から底まで)。外すと、いちばん上は縁の石に戻す
    static void ladders(ServerLevel level, boolean on) {
        BlockPos a = AbyssBuild.mark("ladder_salt_a");
        BlockPos b = AbyssBuild.mark("ladder_salt_b");
        if (a == null || b == null || !level.isLoaded(a)) {
            return;
        }
        Direction face = Direction.NORTH;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos back = a.relative(dir.getOpposite());
            if (level.getBlockState(back).isFaceSturdy(level, back, dir) && level.getBlockState(a.relative(dir)).isAir()) {
                face = dir;
                break;
            }
        }
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            if (on) {
                level.setBlock(q, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, face), Block.UPDATE_ALL);
            } else if (q.getY() == b.getY()) {
                level.setBlock(q, Blocks.SMOOTH_QUARTZ.defaultBlockState(), Block.UPDATE_ALL);
            } else if (level.getBlockState(q).is(Blocks.LADDER)) {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    static void light(ServerLevel level, @Nullable BlockPos at, boolean on) {
        if (at == null || !level.isLoaded(at)) {
            return;
        }
        BlockState st = level.getBlockState(at);
        if (st.hasProperty(CampfireBlock.LIT) && st.getValue(CampfireBlock.LIT) != on) {
            level.setBlock(at, st.setValue(CampfireBlock.LIT, on), Block.UPDATE_ALL);
            if (on) {
                level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1f, 0.7f);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    // 灰の封の戸を閉じ直す (中に人がいれば閉じない)
    static void resealAsh(ServerLevel level) {
        BlockPos a = AbyssBuild.mark("seal_ash_a");
        BlockPos b = AbyssBuild.mark("seal_ash_b");
        BlockPos s = AbyssBuild.mark("seal_ash");
        if (a == null || b == null || s == null || !level.isLoaded(a)) {
            return;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(a.getX(), a.getY(), a.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1)).isEmpty()) {
            return;
        }
        boolean wideX = b.getX() - a.getX() >= 2;
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            boolean mid = q.getY() == s.getY() && (wideX ? q.getX() == s.getX() : q.getZ() == s.getZ());
            level.setBlock(q, (mid ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS).defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- 右クリック (杭・灯り) と術
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (use(p, event.getPos())) {
            event.setCanceled(true);
        }
    }

    public static boolean use(ServerPlayer p, BlockPos at) {
        BlockPos stake = AbyssBuild.mark("stake_salt");
        if (stake != null && (at.equals(stake) || at.equals(stake.above()))) {
            Run r = RUNS.get("salt");
            if (r != null && r.state == State.WAIT && r.task() == Task.STAKE) {
                r.triggered = true;
                Notice.narrate(p, NS + "stake_done");
            } else {
                Notice.hint(p, NS + (r != null && r.cp >= 1 ? "stake_already" : "stake_none"));
            }
            return true;
        }
        for (String layer : LAYERS) {
            BlockPos b = AbyssBuild.mark("beacon_" + layer);
            if (b != null && at.equals(b)) {
                Run r = RUNS.get(layer);
                if (r != null && r.state == State.WAIT && r.task() == Task.BEACON) {
                    r.triggered = true;
                } else if (r == null || r.cp < (layer.equals("salt") ? 2 : 1)) {
                    Notice.hint(p, NS + "beacon_none");
                }
                return true;
            }
        }
        return false;
    }

    static void onCast(ServerPlayer p, AbyssArt a) {
        if (a != AbyssArt.LIGHT || !AbyssWorld.in(p)) {
            return;
        }
        Run r = RUNS.get("ash");
        if (r == null || r.state != State.ESCAPE) {
            return;
        }
        long now = p.level().getGameTime();
        Vec3 f = r.nodes.get(Math.max(0, Math.min(r.nodes.size() - 1, (int) Math.floor(r.front))));
        if (now - r.pushedAt > 100 && p.position().distanceTo(f) < 14) {
            pushBack(p.serverLevel(), r, now);
        }
    }

    // 光の術で闇の波を押し返す (灰)
    public static void pushBack(ServerLevel level, Run r, long now) {
        r.pushedAt = now;
        r.front = Math.max(r.waitIdx[2] - 3, r.front - 8);
        r.caught.clear();
        say(level, r, "push");
    }

    // ---------------------------------------------------------------- 開発用・自己テスト
    public static void stopAll(ServerLevel level) {
        for (Run r : List.copyOf(RUNS.values())) {
            end(level, r, false);
        }
        RUNS.clear();
    }

    public static int nodeCount(Run run) {
        return run.nodes.size();
    }

    public static Vec3 npcPos(Run run) {
        return run.pos();
    }

    public static int waitIndex(Run run, int k) {
        return run.waitIdx[k - 1];
    }

    public static int endIndex(Run run) {
        return run.endIdx;
    }

    public static void setEndurance(Run run, float v) {
        run.endurance = v;
    }

    public static void trigger(Run run) {
        run.triggered = true;
    }

    public static List<UUID> mobsForTest(Run run) {
        List<UUID> out = new ArrayList<>(run.mobs);
        if (run.elite != null) {
            out.addAll(run.elite.mobs);
        }
        return out;
    }

    public static void skipHold(Run run, long now) {
        run.holdUntil = now;
    }

    public static double front(Run run) {
        return run.front;
    }
}

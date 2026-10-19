package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 円い闘技場のセルの共通部分 (W12 鯨骨の闘技場・T10 満ち潮の円環。docs/design/29)。
// 円い床 (半径 10.2) は、中心の「芯」(半径 5.2。崩れない) と、崩れる床 (封印の扉のブロック。「段」0 と 1) でできている。
// 三つの波。波を片付けるたびに、床が一段ずつ崩れ (段 0 → 段 1。崩れる 2〜5 秒前に白く瞬く)、最後の波は芯の上で戦う。
// 床の模様が崩れる順を教える (ひびの入った床が先に崩れる)。崩れた所は穴 (底は水。落ちると控えへ戻る。傷は軽い)。
// 配置 (3): 0 外の輪から / 1 向かい合う二つの扇から / 2 市松の半分から。
// 一人・二人: 幕で半分。最初に段 0 の床がなくなっていて、波は小さい。 三人から: 全面。 人数で敵が増える。
// 入口の橋は段 0 と一緒に崩れる (入ったあとは戻れない。やり直すとき、全員が闘技場から出て 6 秒で、最初に戻る)。出口の橋は最後の波のあとに現れる。
public abstract class RingArenaCell implements TrialCell {
    public static final double R_CORE = 5.2;
    public static final double R_DISC = 10.2;
    public static final double R_HALL = 11.6;
    static final int[] FOES = {3, 4, 5};
    static final int[] WARN = {110, 75, 50};
    static final int ROCK_EVERY = 210;

    protected abstract int cx();

    protected abstract int cz();

    // 円の床のブロックの y
    protected abstract int floorY();

    protected abstract List<BlockPos> bridgeIn();

    protected abstract List<BlockPos> bridgeOut();

    // メッセージの接頭辞 ("message.skyabyss.trial.w12")。.start .wave .collapse .cleared .reset .fell を使う
    protected abstract String msg();

    // 波の敵を湧かせる。spots = 生きている床の上の湧き場所 (count 個以上)。作った敵を返す
    protected abstract List<Mob> spawnWave(ServerLevel back, Run run, int wave, int waves, int count, List<BlockPos> spots);

    // 落ちた人を戻す
    protected abstract void sendBack(ServerLevel back, ServerPlayer p);

    protected abstract BlockPos stone();

    // この高さより下へ沈んだ人は控えへ戻す (穴の底が水で、泳げる闘技場は、深く潜ったときだけにする)
    protected double fallY() {
        return floorY() + 1 - 1.5;
    }

    // この高さより下へ落ちた敵は消える
    protected double mobLine() {
        return floorY() - 2;
    }

    // 橋の見かけ
    protected BlockState bridgeLook() {
        return Blocks.BONE_BLOCK.defaultBlockState();
    }

    // ---------------------------------------------------------------- 形
    // 円の床のマス (中心 (cx, cz))。dx, dz は中心から。段: 2 = 芯 / 0・1 = 崩れる床 / -1 = 円の外
    public static int stage(int layout, int dx, int dz) {
        double r = Math.hypot(dx, dz);
        if (r > R_DISC) {
            return -1;
        }
        if (r <= R_CORE) {
            return 2;
        }
        return switch (layout) {
            case 0 -> r <= 7.7 ? 1 : 0;
            case 1 -> {
                int q = (dx >= 0 ? 0 : 1) + (dz >= 0 ? 0 : 2);
                yield q == 0 || q == 3 ? 0 : 1;
            }
            default -> (((dx + 20) >> 1) + ((dz + 20) >> 1)) % 2 == 0 ? 0 : 1;
        };
    }

    // 崩れる床のすべて (封印の扉のブロックを置く所。三つの配置で同じ)
    public List<BlockPos> discCells() {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -11; dx <= 11; dx++) {
            for (int dz = -11; dz <= 11; dz++) {
                int st = stage(0, dx, dz);
                if (st == 0 || st == 1) {
                    out.add(new BlockPos(cx() + dx, floorY(), cz() + dz));
                }
            }
        }
        return out;
    }

    public List<BlockPos> cellsOfStage(int layout, int stage) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -11; dx <= 11; dx++) {
            for (int dz = -11; dz <= 11; dz++) {
                if (stage(layout, dx, dz) == stage) {
                    out.add(new BlockPos(cx() + dx, floorY(), cz() + dz));
                }
            }
        }
        return out;
    }

    public List<BlockPos> coreCells() {
        return cellsOfStage(0, 2);
    }

    protected List<BlockPos> everything() {
        Set<BlockPos> set = new LinkedHashSet<>(discCells());
        set.addAll(bridgeIn());
        set.addAll(bridgeOut());
        return List.copyOf(set);
    }

    // ---------------------------------------------------------------- 数字
    protected int foes(Run run, int players) {
        int n = FOES[run.mods.number(0)] + Math.min(5, Math.max(0, players - 1));
        n = Abyss.plusOne(run.mods, n);
        return run.mods.quiet() ? Math.max(1, n - 2) : n;
    }

    protected int warn(Run run) {
        return Abyss.shorter(run.mods, WARN[run.mods.number(1)]);
    }

    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    protected boolean onDisc(ServerPlayer p) {
        return Math.hypot(p.getX() - (cx() + 0.5), p.getZ() - (cz() + 0.5)) <= R_DISC + 0.3 && p.getY() >= floorY() + 0.9;
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        resetArena(back, run);
    }

    private void resetArena(ServerLevel back, Run run) {
        clearMobs(back, run);
        GroupGates.release(back, run.group, everything());
        GroupGates.open(back, run.group, bridgeOut());
        run.put("phase", 0);
        run.put("wave", 0);
        run.put("collapsed", -1);
        run.put("first", 0L);
        run.put("emptySince", 0L);
        run.put("nextRock", back.getGameTime() + 400L);
        run.put("view", -1L);
        if (run.cleared) {
            collapseAll(back, run);
            GroupGates.seal(back, run.group, bridgeOut());
        }
    }

    private void collapseAll(ServerLevel back, Run run) {
        GroupGates.open(back, run.group, discCells());
        GroupGates.open(back, run.group, bridgeIn());
        run.put("collapsed", 1);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        clearMobs(back, run);
        GroupGates.release(back, run.group, everything());
    }

    private void clearMobs(ServerLevel back, Run run) {
        for (UUID id : mobs(run)) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        mobs(run).clear();
    }

    private int alive(ServerLevel back, Run run) {
        int n = 0;
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (e == null || !e.isAlive() || e.getY() < mobLine()) {
                if (e != null && e.getY() < mobLine()) {
                    e.discard();
                }
                list.remove(i);
            } else {
                n++;
            }
        }
        return n;
    }

    // 生きている床の上の湧き場所 (その波に崩れていない床。半径 3.5〜9)
    protected List<BlockPos> spots(Run run, int collapsed, int count) {
        List<BlockPos> cand = new ArrayList<>();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                int st = stage(run.mods.layout(), dx, dz);
                double r = Math.hypot(dx, dz);
                if (st < 0 || r < 3.0 || r > 9.0 || st <= collapsed && st < 2) {
                    continue;
                }
                cand.add(new BlockPos(cx() + dx, floorY() + 1, cz() + dz));
            }
        }
        if (cand.isEmpty()) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cand.add(new BlockPos(cx() + dx, floorY() + 1, cz() + dz));
                }
            }
        }
        Random rng = new Random(run.seed ^ (collapsed * 977L) ^ count);
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < Math.max(count, 1); i++) {
            out.add(cand.get(rng.nextInt(cand.size())));
        }
        return out;
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int layout = m.layout();
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective(msg() + ".start"));
        }
        // 落ちた人は控えへ戻す
        for (ServerPlayer p : inside) {
            if (p.getY() < fallY() && Math.hypot(p.getX() - (cx() + 0.5), p.getZ() - (cz() + 0.5)) < R_HALL + 1) {
                sendBack(back, p);
                p.setDeltaMovement(0, 0, 0);
                p.hurtMarked = true;
                p.fallDistance = 0;
                if (p.getHealth() > 4f) {
                    p.hurt(p.damageSources().fall(), 2f);
                }
                run.put("falls", run.getInt("falls") + 1);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
                Notice.bar(p, ModText.deny(msg() + ".fell"));
            }
        }
        if (run.cleared) {
            return;
        }
        int phase = run.getInt("phase");
        int onDisc = 0;
        for (ServerPlayer p : inside) {
            if (onDisc(p)) {
                onDisc++;
            }
        }
        // 闘技場に誰もいないまま 6 秒: 最初に戻る
        if (phase > 0 && onDisc == 0) {
            long since = run.getLong("emptySince");
            if (since == 0) {
                run.put("emptySince", now);
            } else if (now - since >= 120) {
                TrialFx.bar(inside, ModText.hint(msg() + ".reset"));
                resetArena(back, run);
                TrialRuntime.refresh(back, run);
                return;
            }
        } else {
            run.put("emptySince", 0L);
        }
        int collapsed = run.getInt("collapsed");
        switch (phase) {
            case 0 -> {
                // 始め: 闘技場の上に全員が揃った (または最初の一人から 8 秒)
                if (onDisc > 0) {
                    long first = run.getLong("first");
                    if (first == 0) {
                        run.put("first", now);
                    } else if (onDisc >= inside.size() || now - first >= 160) {
                        int n = Math.max(1, onDisc);
                        run.put("players", n);
                        if (n <= 2) {
                            // 幕で半分: 段 0 の床と入口の橋は最初からない
                            GroupGates.open(back, run.group, cellsOfStage(layout, 0));
                            GroupGates.open(back, run.group, bridgeIn());
                            run.put("collapsed", 0);
                        }
                        run.put("phase", 3);
                        run.put("phaseAt", now + 50L);
                        run.put("wave", 0);
                        TrialRuntime.refresh(back, run);
                    }
                }
            }
            case 1 -> {
                if (alive(back, run) == 0) {
                    int wave = run.getInt("wave");
                    if (wave >= 3) {
                        run.cleared = true;
                        run.clearedAt = now;
                        GroupGates.seal(back, run.group, bridgeOut());
                        TrialFx.sound(back, run.group, stone(), SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
                        TrialFx.bar(inside, ModText.progress(msg() + ".cleared"));
                        TrialRuntime.refresh(back, run);
                        return;
                    }
                    // 床が崩れる前の予告。崩れる段は、いまの崩れた段の次 (小さい人数は最初に段 0 がない)
                    int next = Math.max(collapsed + 1, wave - 1);
                    if (next > 1) {
                        run.put("phase", 3);
                        run.put("phaseAt", now + 60L);
                    } else {
                        run.put("phase", 2);
                        run.put("stage", next);
                        run.put("phaseAt", now + warn(run));
                        TrialFx.bar(inside, ModText.battle(msg() + ".collapse", warn(run) / 20));
                        TrialFx.sound(back, run.group, new BlockPos(cx(), floorY() + 1, cz()), SoundEvents.DEEPSLATE_BREAK, 2.0f, 0.5f);
                        TrialRuntime.refresh(back, run);
                    }
                }
            }
            case 2 -> {
                int st = run.getInt("stage");
                if (now % 10 == 0) {
                    for (BlockPos p : cellsOfStage(layout, st)) {
                        if ((p.getX() + p.getZ()) % 5 == 0) {
                            TrialFx.particles(back, run.group, ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 1, 0.2, 0, 0.2, 0.02);
                        }
                    }
                }
                if (now >= run.getLong("phaseAt")) {
                    GroupGates.open(back, run.group, cellsOfStage(layout, st));
                    if (st == 0) {
                        GroupGates.open(back, run.group, bridgeIn());
                    }
                    TrialFx.sound(back, run.group, new BlockPos(cx(), floorY(), cz()), SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
                    run.put("collapsed", st);
                    run.put("phase", 3);
                    run.put("phaseAt", now + 70L);
                    TrialRuntime.refresh(back, run);
                }
            }
            default -> {
                if (now >= run.getLong("phaseAt")) {
                    int wave = run.getInt("wave") + 1;
                    run.put("wave", wave);
                    int n = Math.max(1, run.getInt("players"));
                    int count = foes(run, n) + (wave - 1);
                    List<BlockPos> sp = spots(run, run.getInt("collapsed"), count + 2);
                    List<Mob> made = spawnWave(back, run, wave, 3, count, sp);
                    for (Mob mob : made) {
                        mobs(run).add(mob.getUUID());
                    }
                    TrialFx.bar(Groups.online(back, run.group), ModText.battle(msg() + ".wave", wave, 3));
                    run.put("phase", 1);
                }
            }
        }
        if (m.hasHazard(0) && run.getInt("phase") == 1) {
            hazardTick(back, run, inside, now);
        }
    }

    // 障害 (落石など)。巨神ごとに見かけを替えられる
    protected void hazardTick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        TrialRock.tick(back, run, inside, now, ROCK_EVERY, 4f);
    }

    // ---------------------------------------------------------------- 見かけ
    // 崩れる床の見かけ: 段 0 = ひびの入った床 (先に崩れる)、段 1 = 平らな床。崩れる予告の床は白い石が瞬く。
    protected BlockState crackedLook() {
        return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
    }

    protected BlockState plainLook() {
        return Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    // 沈んだ (崩れた) 床の見かけ。穴が空いて見えるなら null (底が見える)、水面なら水
    protected BlockState goneLook() {
        return null;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int layout = run.mods.layout();
        int phase = run.getInt("phase");
        int stage = run.getInt("stage");
        int collapsed = run.data.get("collapsed") instanceof Integer c ? c : -1;
        BlockState warn = Blocks.CALCITE.defaultBlockState();
        BlockState gone = goneLook();
        for (int s = 0; s <= 1; s++) {
            boolean flicker = phase == 2 && stage == s;
            for (BlockPos p : cellsOfStage(layout, s)) {
                if (s <= collapsed) {
                    // もう崩れた床 (そのグループには扉が開いている)。見かけの上書きは載せない (水面などの見かけがあれば、それを)
                    if (gone != null) {
                        out.accept(p, gone);
                    }
                    continue;
                }
                out.accept(p, flicker ? warn : s == 0 ? crackedLook() : plainLook());
            }
        }
        BlockState bridge = bridgeLook();
        for (BlockPos p : bridgeIn()) {
            if (collapsed >= 0) {
                if (gone != null) {
                    out.accept(p, gone);
                }
            } else {
                out.accept(p, bridge);
            }
        }
        for (BlockPos p : bridgeOut()) {
            if (run.cleared) {
                out.accept(p, bridge);
            } else if (gone != null) {
                out.accept(p, gone);
            }
        }
    }

    // ---------------------------------------------------------------- 検査
    protected List<String> auditArena(ServerLevel back, String id) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : discCells()) {
            if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                bad.add(id + " floor block missing at " + p);
                break;
            }
        }
        for (BlockPos p : coreCells()) {
            if (back.getBlockState(p).isAir() || !back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(2)).isAir()) {
                bad.add(id + " core is not a clean floor at " + p);
                break;
            }
        }
        for (int l = 0; l < 3; l++) {
            int a = cellsOfStage(l, 0).size();
            int b = cellsOfStage(l, 1).size();
            if (a < 20 || b < 20) {
                bad.add(id + " layout " + l + ": the stages are lopsided (" + a + " / " + b + ")");
            }
            // 芯の外へ出る橋は、段 0 の側にあること (崩れるとつながりが切れる)
            for (BlockPos br : bridgeIn()) {
                if (Math.abs(br.getX() - cx()) > 3) {
                    bad.add(id + " the entry bridge is not centred");
                    break;
                }
            }
        }
        return bad;
    }
}

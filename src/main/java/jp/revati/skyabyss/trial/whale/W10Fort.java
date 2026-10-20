package jp.revati.skyabyss.trial.whale;

import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

// W10 骨の砲手の砦 (闘・戦う・★★。docs/design/29 の 3.1)。遮蔽の壁の並ぶ広間の奥の高台に、骨の砲手が陣取る。砲の予告を読み、遮蔽から遮蔽へ走って一つずつ崩す。
//   波ごとに砲手 2〜4 体 (人数で増える)。敵の組 B は盾の番兵も連れる。波を片付けると石の前の戸が開く。静 (敵が少ない) のときは 1 波・一体少ない。
//   敵はそのグループのもの (ほかのグループには見えず、狙わない)。倒れて戻ったら休み場 (砦の入口の隅) から。全員いなくなって 5 秒で最初に戻る。
// 修飾: 配置 3 (砲手の持ち場の順)・数字 2 (一波の砲手 2・3・4 / 波の数 1・2・3)・敵の組 2 (砲手だけ / 盾の番兵つき)・障害 (落石: 予告 1.2 秒)・任意の目標 (無傷で)
public final class W10Fort implements TrialCell {
    // 高台 (3×3、高さ 1) の中心。砲手はこの上に立つ (南の辺から階段で上れる)
    public static final int[][] SLOTS = {{16, 108}, {22, 104}, {38, 104}, {44, 108}, {44, 96}, {16, 96}};
    // 配置ごとの砲手の持ち場の順
    public static final int[][] ORDER = {{0, 3, 5, 1, 2, 4}, {1, 4, 2, 5, 0, 3}, {5, 2, 0, 4, 3, 1}};
    // 遮蔽の壁 {x1, x2, z} (高さ 3)
    public static final int[][] COVERS = {{19, 22, 98}, {38, 41, 98}, {23, 26, 92}, {34, 37, 92}, {17, 20, 102}, {40, 43, 102}};
    static final int[] GUNNERS = {2, 3, 4};
    static final int[] WAVES = {1, 2, 3};
    // この z より奥へ進んだら始まる
    static final int START_Z = 92;
    static final int ROCK_EVERY = 220;
    static final int ROCK_WARN = 24;

    private final Def def = TrialCatalog.get("W10");
    private final Ports ports = new Ports(WhaleDeck.W10_GATE, false, WhaleDeck.W10_EXIT, WhaleDeck.W10_STONE, WhaleDeck.W10_PAD, WhaleDeck.REST_S0,
            WhaleDeck.W10.aabb(), WhaleDeck.rests().stream().filter(r -> r.id().equals(WhaleDeck.REST_W10)).toList());

    public static BlockPos slotSpawn(int slot) {
        return new BlockPos(SLOTS[slot][0], Y + 2, SLOTS[slot][1]);
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    public static int gunners(Run run, int players) {
        int g = jp.revati.skyabyss.abyss.Abyss.plusOne(run.mods, GUNNERS[run.mods.number(0)] + (players >= 3 ? 1 : 0) + (players >= 5 ? 1 : 0));
        return run.mods.quiet() ? Math.max(1, g - 1) : g;
    }

    static int waves(Run run) {
        return run.mods.quiet() ? 1 : jp.revati.skyabyss.abyss.Abyss.plusOne(run.mods, WAVES[run.mods.number(1)]);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("wave", 0);
        run.put("started", false);
        if (run.cleared) {
            GroupGates.open(back, run.group, WhaleDeck.W10_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        for (UUID id : mobs(run)) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        mobs(run).clear();
        if (!run.cleared) {
            GroupGates.release(back, run.group, WhaleDeck.W10_EXIT);
        }
    }

    private static int alive(ServerLevel back, Run run) {
        int n = 0;
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (e == null || !e.isAlive()) {
                list.remove(i);
            } else {
                n++;
            }
        }
        return n;
    }

    private void spawnWave(ServerLevel back, Run run, int players, int wave) {
        // 設計書 26 の 8.7 の B: 一人・二人は、入口に近い口から先に使う (4 人から、もとの順)
        java.util.List<BlockPos> slotPos = new java.util.ArrayList<>();
        for (int i = 0; i < SLOTS.length; i++) {
            slotPos.add(slotSpawn(i));
        }
        BlockPos entrance = jp.revati.skyabyss.titan.dungeon.RoomPlayRooms.entranceOf("w10");
        int[] order = jp.revati.skyabyss.combat.RoomPlay.orderNear(ORDER[run.mods.layout()], slotPos, entrance, players);
        int g = gunners(run, players);
        for (int j = 0; j < g; j++) {
            Mob m = TitanBackTrials.spawnGuard(back, "mortar", slotSpawn(order[j % order.length]), run.group);
            if (m != null) {
                mobs(run).add(m.getUUID());
            }
        }
        if (run.mods.enemy() == 1 && !run.mods.quiet()) {
            int s = 2 + Math.max(0, players - 1);
            for (int j = 0; j < s; j++) {
                int x = 24 + (j % 2) * 12 + (j / 2) * 2;
                Mob m = TitanBackTrials.spawnGuard(back, "sentinel", new BlockPos(x, Y + 1, (players <= 2 ? 92 : 100) + (j / 2)), run.group);
                if (m != null) {
                    mobs(run).add(m.getUUID());
                }
            }
        }
        TrialFx.sound(back, run.group, ports.stone(), SoundEvents.RAVAGER_ROAR, 0.8f, 0.6f);
        List<ServerPlayer> all = jp.revati.skyabyss.titan.group.Groups.online(back, run.group);
        TrialFx.bar(all, ModText.battle("message.skyabyss.trial.w10.wave", wave, waves(run)));
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w10.start"));
        }
        if (run.cleared) {
            return;
        }
        if (run.mods.goal() == 0 && run.getInt("hits") > 0) {
            run.goalOk = false;
        }
        boolean started = (Boolean) run.data.getOrDefault("started", false);
        if (!started) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= START_Z) {
                    run.put("started", true);
                    run.put("nextWave", now + 60L);
                    run.put("nextRock", now + 200L);
                    started = true;
                    break;
                }
            }
            if (!started) {
                return;
            }
        }
        int wave = run.getInt("wave");
        int alive = alive(back, run);
        boolean inProgress = (Boolean) run.data.getOrDefault("inProgress", false);
        if (inProgress && alive == 0) {
            run.put("inProgress", false);
            if (wave >= waves(run)) {
                run.cleared = true;
                run.clearedAt = now;
                GroupGates.open(back, run.group, WhaleDeck.W10_EXIT);
                TrialFx.sound(back, run.group, WhaleDeck.W10_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w10.cleared"));
                return;
            }
            run.put("nextWave", now + 80L);
        } else if (!inProgress && now >= run.getLong("nextWave")) {
            run.put("inProgress", true);
            run.put("wave", wave + 1);
            spawnWave(back, run, inside.size(), wave + 1);
        }
        // 落石 (障害): 予告の輪 1.2 秒 → 落ちる
        if (run.mods.hasHazard(0)) {
            long rockAt = run.getLong("rockAt");
            if (rockAt > 0) {
                BlockPos at = (BlockPos) run.data.get("rockPos");
                if (now < rockAt) {
                    for (int k = 0; k < 8; k++) {
                        double a = k * Math.PI / 4;
                        TrialFx.particles(back, run.group, ParticleTypes.POOF, at.getX() + 0.5 + Math.cos(a) * 1.5, at.getY() + 0.1, at.getZ() + 0.5 + Math.sin(a) * 1.5,
                                1, 0, 0, 0, 0);
                    }
                } else {
                    TrialFx.particles(back, run.group, ParticleTypes.EXPLOSION, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
                    TrialFx.sound(back, run.group, at, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.2f);
                    for (ServerPlayer p : inside) {
                        if (Math.hypot(p.getX() - (at.getX() + 0.5), p.getZ() - (at.getZ() + 0.5)) < 1.7 && Math.abs(p.getY() - at.getY()) < 3) {
                            p.hurt(p.damageSources().generic(), 4f);
                        }
                    }
                    run.put("rockAt", 0L);
                }
            } else if (now >= run.getLong("nextRock") && !inside.isEmpty()) {
                ServerPlayer target = inside.get(new Random(run.seed ^ now).nextInt(inside.size()));
                run.put("rockPos", target.blockPosition());
                run.put("rockAt", now + ROCK_WARN);
                run.put("nextRock", now + ROCK_EVERY);
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < ORDER.length; l++) {
            boolean[] seen = new boolean[SLOTS.length];
            for (int s : ORDER[l]) {
                seen[s] = true;
            }
            for (boolean b : seen) {
                if (!b) {
                    bad.add("W10 layout " + l + ": the order must list every slot");
                    break;
                }
            }
        }
        for (int s = 0; s < SLOTS.length; s++) {
            BlockPos at = slotSpawn(s);
            if (back.getBlockState(at.below()).isAir() || !back.getBlockState(at).isAir() || !back.getBlockState(at.above()).isAir()) {
                bad.add("W10 gunner slot " + s + " has no standing place");
            }
        }
        // 入口から石の前の戸まで、真ん中の道 (x 28〜32) が開いている
        for (int z = 83; z <= 110; z++) {
            for (int x = 28; x <= 32; x++) {
                for (int y = Y + 1; y <= Y + 3; y++) {
                    if (!back.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        bad.add("W10 aisle blocked at " + x + "," + y + "," + z);
                        return bad;
                    }
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(WhaleDeck.W10_STONE))) {
            bad.add("W10 stone missing");
        }
        return bad;
    }
}

package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.F;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.RingArenaCell;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialBacks;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialRock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// T10 満ち潮の円環 (闘・戦う・★★。docs/design/29 の 3.2)。円い砂の島で、三つの波と戦う。波のたびに潮が満ち、島の砂が洗われて足場が狭まる (島は水に沈む)。
// 仕組みは RingArenaCell (円い床・崩れる順・橋。少人数も円は全面 (26 の 8.7))。W12 と違い、足もとの穴は水 (深さ 6)。沈んだ床は水面になり、落ちても溺れず、泳いで足場へ戻れる
// (深く潜ったときだけ控えへ戻る)。敵は水の中でも動く。砂 (崩れる床) は先に洗われ、固めた砂岩は次に洗われる。中心の芯は沈まない。
// 敵: 磯のはさみ虫と珊瑚の下僕 / 泡吹きつき。人数で増える。最後の波は、下僕が増える。水に浸かった人がいたかは任意の目標に数える。
// 修飾: 配置 3 (沈む順)・数字 2 (波の敵の数・沈む前の予告)・敵の組 2 (群れ / 泡吹きつき)・障害 (高波)・任意の目標 (水に浸からずに)
public final class T10Ring extends RingArenaCell {
    // 区画の写しで動かせる (決まりは arm・tick・onUse・disarm・overlay の中だけで座標を使う。docs/design/32 の 5 章)
    @Override
    public boolean relocatable() {
        return true;
    }

    private final Def def = TrialCatalog.get("T10");
    private final Ports ports = new Ports(TideDeck.T10_GATE, false, List.of(), TideDeck.T10_STONE, TideDeck.T10_PAD, TideDeck.REST_H, TideDeck.T10.aabb(),
            TideDeckRests.of("t10"));

    public static final List<BlockPos> BRIDGE_IN = bridge(56);
    public static final List<BlockPos> BRIDGE_OUT = bridge(79);

    private static List<BlockPos> bridge(int z) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = 38; x <= 42; x++) {
            for (int dz = 0; dz < 2; dz++) {
                out.add(new BlockPos(x, F, z + dz));
            }
        }
        return out;
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    @Override
    protected int cx() {
        return TideDeck.T10_CX;
    }

    @Override
    protected int cz() {
        return TideDeck.T10_CZ;
    }

    @Override
    protected int floorY() {
        return F;
    }

    @Override
    protected List<BlockPos> bridgeIn() {
        return BRIDGE_IN;
    }

    @Override
    protected List<BlockPos> bridgeOut() {
        return BRIDGE_OUT;
    }

    @Override
    protected String msg() {
        return "message.skyabyss.trial.t10";
    }

    @Override
    protected BlockPos stone() {
        return TideDeck.T10_STONE;
    }

    @Override
    protected double fallY() {
        return F - 3.5;
    }

    @Override
    protected double mobLine() {
        return F - 9;
    }

    @Override
    protected BlockState crackedLook() {
        return Blocks.SAND.defaultBlockState();
    }

    @Override
    protected BlockState plainLook() {
        return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    }

    @Override
    protected BlockState goneLook() {
        return Blocks.WATER.defaultBlockState();
    }

    @Override
    protected BlockState bridgeLook() {
        return Blocks.CUT_SANDSTONE.defaultBlockState();
    }

    @Override
    protected void sendBack(ServerLevel back, ServerPlayer p) {
        TrialBacks.of(TideDeck_TITAN).returnTo(p, TideDeck.REST_T10);
    }

    private static final String TideDeck_TITAN = jp.revati.skyabyss.progress.Titans.TIDE_SHELL;

    @Override
    protected void hazardTick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        // 高波: 予告の泡の輪 → 落ちて傷
        TrialRock.tick(back, run, inside, now, 210, 4f, ParticleTypes.BUBBLE, ParticleTypes.SPLASH, SoundEvents.PLAYER_SPLASH_HIGH_SPEED);
    }

    private static EntityType<? extends Mob> type(String kind) {
        return switch (kind) {
            case "puffer" -> TideMobSetup.BUBBLE_PUFFER.get();
            case "thrall" -> TideMobSetup.CORAL_THRALL.get();
            default -> TideMobSetup.REEF_SCUTTLER.get();
        };
    }

    @Override
    protected List<Mob> spawnWave(ServerLevel back, Run run, int wave, int waves, int count, List<BlockPos> spots) {
        List<Mob> out = new ArrayList<>();
        boolean puffers = run.mods.enemy() == 1;
        for (int j = 0; j < count; j++) {
            String kind = puffers ? (j % 3 == 0 ? "scuttler" : "puffer") : (j % 3 == 2 ? "thrall" : "scuttler");
            BlockPos at = spots.get(j % spots.size());
            if (kind.equals("puffer")) {
                at = at.above(3);
            }
            Mob m = TideSpawns.spawn(back, type(kind), at, run.group);
            if (m != null) {
                out.add(m);
            }
        }
        if (wave == waves && !run.mods.quiet()) {
            for (int j = 0; j < 2; j++) {
                Mob m = TideSpawns.spawn(back, type("thrall"), spots.get((count + j) % spots.size()), run.group);
                if (m != null) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        super.tick(back, run, inside, now);
        // 水に浸かった人 (任意の目標)
        if (run.cleared) {
            return;
        }
        Set<UUID> wet = run.get("wet", () -> new HashSet<UUID>());
        for (ServerPlayer p : inside) {
            boolean swimming = p.isInWater() && Math.hypot(p.getX() - (cx() + 0.5), p.getZ() - (cz() + 0.5)) < R_HALL + 1;
            if (swimming && wet.add(p.getUUID())) {
                run.put("falls", run.getInt("falls") + 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
                Notice.bar(p, ModText.hint("message.skyabyss.trial.t10.wet"));
            } else if (!swimming) {
                wet.remove(p.getUUID());
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = auditArena(back, "T10");
        for (BlockPos p : BRIDGE_IN) {
            if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                bad.add("T10 entry bridge missing at " + p);
                break;
            }
        }
        for (BlockPos p : BRIDGE_OUT) {
            if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                bad.add("T10 exit bridge missing at " + p);
                break;
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(TideDeck.T10_STONE))) {
            bad.add("T10 stone missing");
        }
        return bad;
    }
}

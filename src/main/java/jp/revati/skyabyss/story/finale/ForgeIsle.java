package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 島 3「炉の島」(docs/design/50 の 4.2.3)。地形は data/skyabyss/finale/isle3.json (tools/gen/world/build_cloud_isle3.py。FinaleWorld の版 4)。
//   崩れた橋  戻り石 (RewindStoneBlock) は橋ごとの組 (いちばん近い印 gap_<k>) で CYCLE ごとに巻き戻る:
//             0〜GONE は崩れている・〜WARN は残り火の輪郭 (1 秒)・〜SOLID は石 (3 秒。最後の 1 秒は薄い石)・そこから CYCLE までまた崩れている。組ごとに時がずれる (OFFSET)。
//             主の間の外の輪 (gap_arena) は、戻し手が段 2 になるまで石のまま
//   炉        かけらの冷えた炉 (印 forge_<w|e|n>) を右クリックで 30 秒火が入る。合図 finale_forge_<k> (近くの人と組へ)。炉の前で前の灰炉の人の最後の言葉
//   扉        大炉の扉 (印 door): 三つに火を入れた人 (頼みの段 door 以降) を主の間へ。主の間の帰りの台で扉の前へ
//   光の橋    灯の島の主の間から下る橋 (印 bridge_gate)。灯の島の頼みを終えていない人は灯台の上の入口へ戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ForgeIsle {
    public static final String NAME = "isle3";
    public static final String QUEST = "main_finale_forge";
    public static final int CYCLE = 200;
    public static final int GONE = 100;
    public static final int WARN = 120;
    public static final int SOLID = 180;
    static final String[] FORGES = {"w", "e", "n"};
    static final String[] GAPS = {"w", "e", "n", "arena"};
    static final int[] OFFSET = {0, 70, 140, 0};
    static final int FORGE_TICKS = 600;
    static final double ARRIVE_NEAR = 10;
    static final double FRAG_NEAR = 8;
    private static Map<String, List<BlockPos>> groups;
    private static final Map<String, Integer> PHASE = new HashMap<>();
    private static final Map<BlockPos, Long> FORGE_ON = new HashMap<>();
    // 主の間の輪が巻き戻るか (戻し手の段 2 から。ForgeArena が決める)
    public static boolean arenaRewinds;

    private ForgeIsle() {
    }

    public static FinaleTerrain terrain() {
        return FinaleTerrain.of(NAME);
    }

    public static BlockPos mark(String key) {
        return terrain().mark(key);
    }

    public static boolean onIsland(Vec3 p) {
        BlockPos c = mark("center");
        return Math.hypot((p.x - c.getX()) / 38.0, (p.z - c.getZ()) / 34.0) < 1.0 && p.y < mark("arena").getY() - 6;
    }

    // かけらも含めた島の上 (湧きに使う)
    public static boolean onIsles(Vec3 p) {
        if (onIsland(p)) {
            return true;
        }
        for (String k : FORGES) {
            BlockPos f = mark("forge_" + k);
            if (Math.hypot(p.x - f.getX(), p.z - f.getZ()) < 9 && Math.abs(p.y - f.getY()) < 8) {
                return true;
            }
        }
        return false;
    }

    public static synchronized Map<String, List<BlockPos>> groups() {
        if (groups == null) {
            Map<String, List<BlockPos>> out = new HashMap<>();
            for (String k : GAPS) {
                out.put(k, new ArrayList<>());
            }
            for (BlockPos p : terrain().positionsOf("skyabyss:rewind_stone")) {
                String best = null;
                double bd = Double.MAX_VALUE;
                for (String k : GAPS) {
                    double d = p.distSqr(mark("gap_" + k));
                    if (d < bd) {
                        bd = d;
                        best = k;
                    }
                }
                out.get(best).add(p);
            }
            groups = Map.copyOf(out);
        }
        return groups;
    }

    // 組 k の今の相 (RewindStoneBlock.PHASE)
    public static int phaseOf(ServerLevel level, String k) {
        if (k.equals("arena") && !arenaRewinds) {
            return 2;
        }
        int i = java.util.Arrays.asList(GAPS).indexOf(k);
        long t = (level.getGameTime() + OFFSET[Math.max(0, i)]) % CYCLE;
        return t < GONE ? 0 : t < WARN ? 1 : t < SOLID - 20 ? 2 : t < SOLID ? 3 : 0;
    }

    public static void update(ServerLevel level, boolean force) {
        for (Map.Entry<String, List<BlockPos>> g : groups().entrySet()) {
            int ph = phaseOf(level, g.getKey());
            Integer was = PHASE.put(g.getKey(), ph);
            if (!force && was != null && was == ph) {
                continue;
            }
            for (BlockPos p : g.getValue()) {
                BlockState st = level.getBlockState(p);
                if (st.getBlock() instanceof RewindStoneBlock && st.getValue(RewindStoneBlock.PHASE) != ph) {
                    level.setBlock(p, st.setValue(RewindStoneBlock.PHASE, ph), 2);
                }
            }
            if (ph == 1 && !g.getValue().isEmpty()) {
                BlockPos m = mark("gap_" + g.getKey());
                level.playSound(null, m, CloudMobsSetup.REWIND_BACK.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL
                || !FinaleWorld.built(level.getServer()) || FinaleWorld.version(level.getServer()) < 4) {
            return;
        }
        if (level.getGameTime() % 5 == 0) {
            update(level, false);
        }
        if (level.getGameTime() % 10 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    gate(p);
                }
            }
        }
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    visit(p);
                }
            }
            forgesOut(level);
            ForgeArena.tick(level);
        }
        if (level.getGameTime() % 40 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && !p.isCreative()) {
                    ForgeArena.spawn(level, p);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 光の橋の関所 (灯の島の主の間 → 島 3)
    static boolean inGate(Vec3 p) {
        BlockPos g = mark("bridge_gate");
        return Math.abs(p.x - (g.getX() + 0.5)) <= 2.5 && p.z <= g.getZ() + 4 && p.z >= g.getZ() - 12 && Math.abs(p.y - g.getY()) <= 4;
    }

    public static boolean bridgeOpen(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(LampIsle.QUEST);
        return e != null && e.done;
    }

    static void gate(ServerPlayer p) {
        if (!inGate(p.position()) || bridgeOpen(p)) {
            return;
        }
        BlockPos to = LampIsle.mark("arena_entry");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        }
        p.resetFallDistance();
        Notice.narrate(p, "message.skyabyss.cloudsea.bridge_shut");
    }

    // ---------------------------------------------------------------- 着く所・炉
    static void visit(ServerPlayer p) {
        BlockPos arrive = mark("arrive");
        if (p.distanceToSqr(arrive.getX() + 0.5, arrive.getY(), arrive.getZ() + 0.5) < ARRIVE_NEAR * ARRIVE_NEAR) {
            Quests.signal(p, "finale_isle3_arrive");
        }
        for (int i = 0; i < FORGES.length; i++) {
            BlockPos f = mark("frag_" + FORGES[i]);
            if (p.distanceToSqr(f.getX() + 0.5, f.getY(), f.getZ() + 0.5) < FRAG_NEAR * FRAG_NEAR && Lore.unlock(p, "finale/forge_echo_" + (i + 1), true)) {
                Notice.narrate(p, "message.skyabyss.isle3.echo." + FORGES[i]);
            }
        }
    }

    static void forgesOut(ServerLevel level) {
        FORGE_ON.entrySet().removeIf(e -> {
            if (e.getValue() > level.getGameTime()) {
                return false;
            }
            BlockState st = level.getBlockState(e.getKey());
            if (st.getBlock() instanceof BlastFurnaceBlock) {
                level.setBlock(e.getKey(), st.setValue(BlastFurnaceBlock.LIT, false), 2);
            }
            return true;
        });
    }

    public static void lightForge(ServerLevel level, String key) {
        BlockPos pos = mark("forge_" + key);
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof BlastFurnaceBlock) {
            level.setBlock(pos, st.setValue(BlastFurnaceBlock.LIT, true), 2);
        }
        FORGE_ON.put(pos, level.getGameTime() + FORGE_TICKS);
        level.playSound(null, pos, CloudMobsSetup.FORGE_LIT.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.4, 0.5, 0.4, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 3.2, pos.getZ() + 0.5, 10, 0.2, 0.4, 0.2, 0.02);
    }

    // ---------------------------------------------------------------- 右クリック (炉・扉・帰りの台)
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !FinaleWorld.in(p)
                || FinaleWorld.version(p.server) < 4) {
            return;
        }
        BlockPos pos = event.getPos();
        ServerLevel level = p.serverLevel();
        for (String k : FORGES) {
            if (pos.equals(mark("forge_" + k))) {
                event.setCanceled(true);
                lightForge(level, k);
                List<ServerPlayer> near = new ArrayList<>();
                for (ServerPlayer o : level.players()) {
                    if (o.distanceToSqr(p) < 32 * 32) {
                        near.add(o);
                    }
                }
                Quests.signalWithParty(level, near, "finale_forge_" + k);
                Notice.narrate(p, "message.skyabyss.isle3.forge." + k);
                return;
            }
        }
        if (pos.equals(mark("door"))) {
            event.setCanceled(true);
            if (!doorOpen(p)) {
                Notice.narrate(p, "message.skyabyss.isle3.door.shut");
                return;
            }
            BlockPos to = mark("arena_entry");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
            Quests.signal(p, "finale_forge_door");
            ForgeArena.arrive(level, p);
        } else if (pos.equals(mark("arena_back"))) {
            event.setCanceled(true);
            BlockPos to = mark("door_front");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
        }
    }

    // 扉が開くか: 頼みの段 door 以降 (三つの炉に火を入れた)・頼みを終えた人
    public static boolean doorOpen(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null) {
            return false;
        }
        if (e.done) {
            return true;
        }
        QuestDef def = QuestManager.quest(QUEST);
        for (int i = 0; def != null && i < def.stages().size(); i++) {
            if (def.stages().get(i).id().equals("door")) {
                return e.stage >= i;
            }
        }
        return false;
    }
}

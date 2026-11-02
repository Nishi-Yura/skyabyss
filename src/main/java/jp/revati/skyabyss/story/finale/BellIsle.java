package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 島 1「鐘の島」(docs/design/50 の 4.2.1)。地形は data/skyabyss/finale/isle1.json (tools/gen/world/build_cloud_isle1.py。FinaleWorld の版 2)。
//   鐘雲   鐘 (広場・かけら 3・主の間の 4) が鳴ると、半径 RING の鐘雲が RING_TICKS だけ固まる。終わりの FADE_TICKS は薄くなる
//   無音   亡者と主が silence を呼ぶと、その近くの鐘の残りの時間が消える (鐘雲がほどける)
//   かけら 西・東・北西の鳴り鐘を鳴らすと合図 finale_bell_<w|e|n> (近くの人と組へ)。かけらに着くと前の都の人の最後の言葉 (伝承 finale/bell_echo_<n>)
//   扉     鐘楼の扉 (印 door) を右クリック: 三つ鳴らした人 (頼みの段 door 以降) を主の間へ。主の間の帰りの台で扉の前へ
//   着く   島の南の着く所 (印 arrive) に来ると合図 finale_isle1_arrive
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BellIsle {
    public static final String NAME = "isle1";
    public static final String QUEST = "main_finale_bell";
    public static final int RING = 16;
    public static final int RING_TICKS = 400;
    public static final int FADE_TICKS = 80;
    static final String[] FRAGS = {"w", "e", "n"};
    static final double ARRIVE_NEAR = 10;
    static final double FRAG_NEAR = 9;
    // 鐘の位置 → 固まりの終わる時刻
    private static final Map<BlockPos, Long> RUNG = new HashMap<>();
    // 主の間の床が崩れて鐘雲になった所 (BellArena.crumble)
    private static final Set<BlockPos> EXTRA = new HashSet<>();
    private static List<BlockPos> clouds;
    private static Set<BlockPos> bells;

    private BellIsle() {
    }

    public static FinaleTerrain terrain() {
        return FinaleTerrain.of(NAME);
    }

    public static BlockPos mark(String key) {
        return terrain().mark(key);
    }

    static synchronized List<BlockPos> clouds() {
        if (clouds == null) {
            clouds = List.copyOf(terrain().positionsOf("skyabyss:bell_cloud"));
        }
        return clouds;
    }

    static synchronized Set<BlockPos> bells() {
        if (bells == null) {
            Set<BlockPos> out = new HashSet<>();
            for (Map.Entry<String, BlockPos> e : terrain().marks.entrySet()) {
                if (e.getKey().startsWith("bell_") || e.getKey().startsWith("arena_bell_")) {
                    out.add(e.getValue());
                }
            }
            bells = Set.copyOf(out);
        }
        return bells;
    }

    // ---------------------------------------------------------------- 鐘
    public static void ring(ServerLevel level, BlockPos bell) {
        RUNG.put(bell.immutable(), level.getGameTime() + RING_TICKS);
        level.playSound(null, bell, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.6f, 0.9f);
        level.sendParticles(ParticleTypes.END_ROD, bell.getX() + 0.5, bell.getY() + 0.5, bell.getZ() + 0.5, 24, 1.5, 0.6, 1.5, 0.03);
        update(level);
    }

    // 近くの鐘の残りの時間を消す (亡者の割れた鐘・主の無音)。消した鐘の数
    public static int silence(ServerLevel level, BlockPos at, double radius) {
        int n = 0;
        for (Map.Entry<BlockPos, Long> e : RUNG.entrySet()) {
            if (e.getValue() > level.getGameTime() && e.getKey().distSqr(at) <= radius * radius) {
                e.setValue(level.getGameTime());
                n++;
            }
        }
        if (n > 0) {
            level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.4f, 0.5f);
            update(level);
        }
        return n;
    }

    // 近くで鳴っている (固まりの残っている) 鐘の数
    public static int ringingNear(ServerLevel level, BlockPos at, double radius) {
        int n = 0;
        for (Map.Entry<BlockPos, Long> e : RUNG.entrySet()) {
            if (e.getValue() > level.getGameTime() && e.getKey().distSqr(at) <= radius * radius) {
                n++;
            }
        }
        return n;
    }

    static void addExtra(java.util.Collection<BlockPos> at) {
        EXTRA.addAll(at);
    }

    static void clearExtra() {
        EXTRA.clear();
    }

    // 鐘雲を決め直す (鳴った鐘と時刻から)
    public static void update(ServerLevel level) {
        long now = level.getGameTime();
        List<BlockPos> all = clouds();
        if (!EXTRA.isEmpty()) {
            all = new ArrayList<>(all);
            all.addAll(EXTRA);
        }
        for (BlockPos p : all) {
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof BellCloudBlock)) {
                continue;
            }
            long until = 0;
            for (Map.Entry<BlockPos, Long> e : RUNG.entrySet()) {
                if (e.getValue() > now && e.getKey().distSqr(p) <= (double) RING * RING) {
                    until = Math.max(until, e.getValue());
                }
            }
            boolean solid = until > now;
            boolean fade = solid && until - now <= FADE_TICKS;
            if (st.getValue(BellCloudBlock.SOLID) != solid || st.getValue(BellCloudBlock.FADE) != fade) {
                level.setBlock(p, st.setValue(BellCloudBlock.SOLID, solid).setValue(BellCloudBlock.FADE, fade), 2);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL || level.getGameTime() % 5 != 0
                || !FinaleWorld.built(level.getServer()) || FinaleWorld.version(level.getServer()) < 2) {
            return;
        }
        if (!RUNG.isEmpty()) {
            update(level);
            RUNG.values().removeIf(t -> t < level.getGameTime() - 20);
        }
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    visit(p);
                }
            }
            BellArena.tick(level);
        }
        if (level.getGameTime() % 40 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && !p.isCreative()) {
                    BellArena.spawn(level, p);
                }
            }
        }
    }

    // 着く所・かけらに来た
    static void visit(ServerPlayer p) {
        BlockPos arrive = mark("arrive");
        if (p.distanceToSqr(arrive.getX() + 0.5, arrive.getY(), arrive.getZ() + 0.5) < ARRIVE_NEAR * ARRIVE_NEAR) {
            Quests.signal(p, "finale_isle1_arrive");
        }
        for (int i = 0; i < FRAGS.length; i++) {
            BlockPos f = mark("frag_" + FRAGS[i]);
            if (p.distanceToSqr(f.getX() + 0.5, f.getY(), f.getZ() + 0.5) < FRAG_NEAR * FRAG_NEAR && Lore.unlock(p, "finale/bell_echo_" + (i + 1), true)) {
                Notice.narrate(p, "message.skyabyss.isle1.echo." + FRAGS[i]);
            }
        }
    }

    // ---------------------------------------------------------------- 右クリック (鐘・扉・帰りの台)
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !FinaleWorld.in(p)
                || FinaleWorld.version(p.server) < 2) {
            return;
        }
        BlockPos pos = event.getPos();
        ServerLevel level = p.serverLevel();
        if (bells().contains(pos)) {
            // 鐘の揺れと音はバニラの鐘のまま。固まりと合図をここで
            ring(level, pos);
            for (String f : FRAGS) {
                if (pos.equals(mark("bell_" + f))) {
                    List<ServerPlayer> near = new ArrayList<>();
                    for (ServerPlayer o : level.players()) {
                        if (o.distanceToSqr(p) < 32 * 32) {
                            near.add(o);
                        }
                    }
                    Quests.signalWithParty(level, near, "finale_bell_" + f);
                    Notice.narrate(p, "message.skyabyss.isle1.bell." + f);
                }
            }
        } else if (pos.equals(mark("door"))) {
            event.setCanceled(true);
            if (!doorOpen(p)) {
                Notice.narrate(p, "message.skyabyss.isle1.door.shut");
                return;
            }
            BlockPos to = mark("arena_entry");
            level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.4f, 0.6f);
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
            Quests.signal(p, "finale_bell_door");
            BellArena.arrive(level, p);
        } else if (pos.equals(mark("arena_back"))) {
            event.setCanceled(true);
            BlockPos to = mark("door_front");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
        }
    }

    // 扉が開くか: 頼みの段 door 以降 (三つの鐘を鳴らした)・頼みを終えた人
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

    // 自己テスト向け
    static void reset() {
        RUNG.clear();
        EXTRA.clear();
    }
}

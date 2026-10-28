package jp.revati.skyabyss.story.ch6.valley;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.ch6.mob.ShellHare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 新月の谷の決まり (docs/design/46 の 4.3)。頼み main_moon_valley の段: meet → pool → shards → gate → lord → promise
//   星の溜まり   星の灯をもらう (合図 moon_star_pool)
//   欠片の台     影の橋 (柱の上に立てた = 渡れた)・鏡の窪 (鏡の石が三つとも真ん中を向く)・落ちた星の穴 (巣の兎がまわりにいない)。合図 moon_shard_<所>
//   星の門       欠片を三つそろえた人 (段 gate より先・済み) を台地の上の主の間へ。上の帰り口は下へ (合図 moon_valley_gate)
public final class ValleyPuzzles {
    public static final String QUEST = "main_moon_valley";
    public static final String POOL = "moon_star_pool";
    public static final String SHARD = "moon_shard_";
    public static final String GATE = "moon_valley_gate";
    public static final String LORD = "moon_lord_new";
    static final double NEST_CLEAR = 14;
    static final int MIRROR_RESET_TICKS = 1200;

    private ValleyPuzzles() {
    }

    // 頼みの今の段の番号 (始めていなければ -1、済んだら段の数)
    public static int stage(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null) {
            return -1;
        }
        QuestDef def = QuestManager.quest(QUEST);
        return e.done ? (def == null ? 99 : def.stages().size()) : e.stage;
    }

    public static int stageIndex(String id) {
        QuestDef def = QuestManager.quest(QUEST);
        if (def == null) {
            return 99;
        }
        for (int i = 0; i < def.stages().size(); i++) {
            if (def.stages().get(i).id().equals(id)) {
                return i;
            }
        }
        return 99;
    }

    // ---------------------------------------------------------------- 星の溜まり
    static void pool(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        if (!StarLampItem.carries(p)) {
            Quests.give(p, new ItemStack(ValleyBlocks.STAR_LAMP.get()));
            Notice.narrate(p, "message.skyabyss.moon.pool.learn");
        } else {
            Notice.narrate(p, "message.skyabyss.moon.pool.again");
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 1.2f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.05);
        Quests.signal(p, POOL);
    }

    // ---------------------------------------------------------------- 欠片の台
    static void claim(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        String site = MoonValley.siteOf(pos);
        if (site == null) {
            return;
        }
        String no = refusal(level, site, pos);
        if (no != null) {
            Notice.narrate(p, no);
            return;
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.3, 1.2, 0.3, 0.04);
        Notice.narrate(p, "message.skyabyss.moon.shard." + site);
        Quests.signal(p, SHARD + site);
        if (site.equals("mirror")) {
            // しばらくすると鏡はまた外れる (後から来た人の分)
            Tasks.later(MIRROR_RESET_TICKS, () -> scramble(level));
        }
    }

    // 欠片を取れない理由 (取れるなら null)
    static String refusal(ServerLevel level, String site, BlockPos pos) {
        return switch (site) {
            case "mirror" -> mirrorsAligned(level) ? null : "message.skyabyss.moon.shard.mirror_dark";
            case "hole" -> level.getEntitiesOfClass(ShellHare.class, new AABB(pos).inflate(NEST_CLEAR), e -> e.isAlive()).isEmpty()
                    && !ValleyTicker.hareNear(level, pos, NEST_CLEAR + 4) ? null : "message.skyabyss.moon.shard.hole_guarded";
            default -> null;
        };
    }

    // ---------------------------------------------------------------- 鏡の窪
    public static boolean mirrorsAligned(ServerLevel level) {
        BlockPos center = MoonValley.mark("shard_mirror");
        for (int i = 0; i < 3; i++) {
            BlockPos m = MoonValley.mark("mirror_" + i);
            BlockState st = level.getBlockState(m);
            if (!(st.getBlock() instanceof MirrorStoneBlock) || st.getValue(MirrorStoneBlock.FACING) != MirrorStoneBlock.toward(m, center)) {
                return false;
            }
        }
        return true;
    }

    static void mirrorsTurned(ServerLevel level) {
        if (!mirrorsAligned(level)) {
            return;
        }
        BlockPos center = MoonValley.mark("shard_mirror");
        level.playSound(null, center, ValleyBlocks.MIRROR_ALIGN.get(), SoundSource.BLOCKS, 1.5f, 1.0f);
        // 三つの石から台へ光の筋、空から台へ光の柱
        for (int i = 0; i < 3; i++) {
            BlockPos m = MoonValley.mark("mirror_" + i);
            for (int k = 0; k <= 10; k++) {
                double t = k / 10.0;
                level.sendParticles(ParticleTypes.END_ROD, m.getX() + 0.5 + (center.getX() - m.getX()) * t, m.getY() + 0.8 + (center.getY() - m.getY()) * t,
                        m.getZ() + 0.5 + (center.getZ() - m.getZ()) * t, 2, 0.05, 0.05, 0.05, 0.0);
            }
        }
        for (int y = 0; y < 30; y += 2) {
            level.sendParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 1 + y, center.getZ() + 0.5, 2, 0.1, 0.5, 0.1, 0.0);
        }
    }

    static void scramble(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            BlockPos m = MoonValley.mark("mirror_" + i);
            BlockState st = level.getBlockState(m);
            if (st.getBlock() instanceof MirrorStoneBlock && st.getValue(MirrorStoneBlock.FACING) == MirrorStoneBlock.toward(m, MoonValley.mark("shard_mirror"))) {
                level.setBlock(m, st.setValue(MirrorStoneBlock.FACING, st.getValue(MirrorStoneBlock.FACING).getClockWise()), 3);
            }
        }
    }

    // ---------------------------------------------------------------- 星の門
    public static boolean mayCross(ServerPlayer p) {
        return stage(p) >= stageIndex("gate");
    }

    static void gate(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        boolean up = pos.getY() < MoonValley.mark("arena_entry").getY() - 6;
        if (up && !mayCross(p)) {
            Notice.narrate(p, stage(p) < stageIndex("shards") ? "message.skyabyss.moon.gate_star.sealed" : "message.skyabyss.moon.gate_star.need_shards");
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1f, 0.5f);
            return;
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        if (up) {
            BlockPos to = MoonValley.mark("arena_entry");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
            Quests.signal(p, GATE);
            ValleyArena.arrive(level, p);
        } else {
            BlockPos to = standable(level, MoonValley.mark("valley_gate").offset(0, 0, -4));
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        }
    }

    // 立てる所 (足もとが固く、上に 2 つ空き)。下から上へ 8 まで探す
    static BlockPos standable(ServerLevel level, BlockPos at) {
        for (int dy = -2; dy <= 8; dy++) {
            BlockPos p = at.above(dy);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()) {
                return p;
            }
        }
        return at;
    }

    // 欠片の数 (段 shards の三つの目的のうち済んだ数。段より先なら 3)
    public static int shards(ServerPlayer p) {
        int st = stage(p);
        int si = stageIndex("shards");
        if (st > si) {
            return 3;
        }
        if (st < si) {
            return 0;
        }
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        int n = 0;
        for (int i = 0; e != null && i < 3; i++) {
            n += e.progress(i) > 0 ? 1 : 0;
        }
        return n;
    }

    static List<String> sites() {
        return List.of("bridge", "mirror", "hole");
    }
}

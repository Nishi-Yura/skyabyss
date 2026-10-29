package jp.revati.skyabyss.story.ch6.cliff;

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

// 上弦の崖の決まり (docs/design/46 の 4.4)。頼み main_moon_cliff の段: meet → pool → shards → gate → lord → promise
//   月の溜まり   星術「軽身」を覚える (合図 moon_cliff_pool)。野営の溜まりは谷の星の溜まりと同じブロック (置き場所で回す)
//   欠片の台     跳び石の塔の頂・崩れる棚の先の柱・天秤の先の高い柱。そこへ立てた = 渡れた (合図 moon_cliff_shard_<所>)
//   頂の門       欠片を三つそろえた人を主の間へ。皿の縁の帰り口は門の前へ (合図 moon_cliff_gate)
public final class CliffPuzzles {
    public static final String QUEST = "main_moon_cliff";
    public static final String POOL = "moon_cliff_pool";
    public static final String SHARD = "moon_cliff_shard_";
    public static final String GATE = "moon_cliff_gate";
    public static final String LORD = "moon_lord_waxing";

    private CliffPuzzles() {
    }

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

    // ---------------------------------------------------------------- 月の溜まり (軽身を覚える)
    public static void pool(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        if (!LightBody.learned(p)) {
            LightBody.learn(p);
            Notice.narrate(p, "message.skyabyss.moon.pool.light_body");
        } else {
            Notice.narrate(p, "message.skyabyss.moon.pool.light_body_again");
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 1.5f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 30, 0.6, 1.2, 0.6, 0.08);
        Quests.signal(p, POOL);
    }

    // ---------------------------------------------------------------- 欠片の台 (そこへ立てたことが決まり)
    public static void claim(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        String site = MoonCliff.siteOf(pos);
        if (site == null) {
            return;
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.3, 1.2, 0.3, 0.04);
        Notice.narrate(p, "message.skyabyss.moon.cliff_shard." + site);
        Quests.signal(p, SHARD + site);
    }

    // ---------------------------------------------------------------- 頂の門
    public static boolean mayCross(ServerPlayer p) {
        return stage(p) >= stageIndex("gate");
    }

    public static void gate(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        boolean up = pos.getY() < MoonCliff.mark("arena_entry").getY() - 6;
        if (up && !mayCross(p)) {
            Notice.narrate(p, stage(p) < stageIndex("shards") ? "message.skyabyss.moon.cliff_gate.sealed" : "message.skyabyss.moon.cliff_gate.need_shards");
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1f, 0.5f);
            return;
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        if (up) {
            BlockPos to = MoonCliff.mark("arena_entry");
            BlockPos c = MoonCliff.mark("arena_center");
            float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - to.getX()), c.getZ() - to.getZ()));
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, yaw, 0f);
            Quests.signal(p, GATE);
            CliffArena.arrive(level, p);
        } else {
            BlockPos to = beforeGate(level);
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
        }
    }

    // 門の前の道 (門から弧に沿って 4 戻った所)
    public static BlockPos beforeGate(ServerLevel level) {
        BlockPos g = MoonCliff.mark("cliff_gate");
        double a = Math.atan2(g.getZ() - MoonCliff.ARC_Z, g.getX() - MoonCliff.ARC_X);
        double r = Math.hypot(g.getX() - MoonCliff.ARC_X, g.getZ() - MoonCliff.ARC_Z);
        a -= 4.5 / r;
        BlockPos at = BlockPos.containing(MoonCliff.ARC_X + Math.cos(a) * r, g.getY(), MoonCliff.ARC_Z + Math.sin(a) * r);
        return standable(level, at);
    }

    static BlockPos standable(ServerLevel level, BlockPos at) {
        for (int dy = -3; dy <= 8; dy++) {
            BlockPos p = at.above(dy);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).isAir()) {
                return p;
            }
        }
        return at;
    }

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
}

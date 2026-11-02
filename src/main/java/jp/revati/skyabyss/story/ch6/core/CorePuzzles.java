package jp.revati.skyabyss.story.ch6.core;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch6.rift.RiftPuzzles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// 月の核の決まり (docs/design/46 の 4.7)。頼み main_moon_core の段: gather → descend → core → end
//   核の門 (着く所の北)   淵を終えた人が触れると四人と会う (合図 moon_core_gather)。もう一度触れると核の間へ下りる (合図 moon_core_gate)
//   帰り口 (床の縁)       着く所の門の前へ
//   核を止める            合図 moon_lord_core (CoreArena)。そのあと語りの紙芝居を見終えると合図 moon_core_end
public final class CorePuzzles {
    public static final String QUEST = "main_moon_core";
    public static final String GATHER = "moon_core_gather";
    public static final String GATE = "moon_core_gate";
    public static final String LORD = "moon_lord_core";
    public static final String END = "moon_core_end";
    public static final String REEL = "moon_core_end";

    private CorePuzzles() {
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

    // 淵を終えたか (核の門が開く)
    public static boolean open(ServerPlayer p) {
        return RiftPuzzles.stage(p) >= RiftPuzzles.stageIndex("promise") + 1;
    }

    public static boolean mayDescend(ServerPlayer p) {
        return open(p) && stage(p) >= stageIndex("descend");
    }

    // 星の門 (核の門か帰り口) に触れた
    public static void gate(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        if (MoonCore.isBackGate(pos)) {
            BlockPos to = MoonCore.mark("gate_front");
            p.removeTag(CoreArena.TAG);
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
            level.playSound(null, to, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 1.4f);
            return;
        }
        if (!open(p)) {
            Notice.narrate(p, "message.skyabyss.moon.core_gate.sealed");
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1f, 0.5f);
            return;
        }
        if (stage(p) < 0) {
            Quests.start(p, QUEST);
        }
        if (stage(p) == stageIndex("gather")) {
            Quests.signal(p, GATHER);
            Notice.narrate(p, "message.skyabyss.moon.core_gate.gather");
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 0.7f);
            return;
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 1.2f);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 1.2, 0.4, 0.05);
        BlockPos to = MoonCore.mark("arena_entry");
        BlockPos c = MoonCore.mark("arena_center");
        float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - to.getX()), c.getZ() - to.getZ()));
        p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, yaw, 0f);
        Quests.signal(p, GATE);
        p.addTag(CoreArena.TAG);
        CoreArena.arrive(level, p);
    }
}

package jp.revati.skyabyss.story.ch6.field;

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
import net.minecraft.world.item.ItemStack;

// 満月の野の決まり (docs/design/46 の 4.5)。頼み main_moon_field の段: meet → pool → shards → gate → lord → promise
//   月の溜まり   星術「一瞬止め」を覚える = 月の砂時計をもらう (合図 moon_field_pool)。失くしてもまたもらえる
//   欠片の台     満ち引きの池の柱・降る光の溝の奥・影の塔の頂。そこへ立てた = 渡れた (合図 moon_field_shard_<所>)
//   満月の門     欠片を三つそろえた人を満月の台の上へ。台の縁の帰り口は門の前へ (合図 moon_field_gate)
public final class FieldPuzzles {
    public static final String QUEST = "main_moon_field";
    public static final String POOL = "moon_field_pool";
    public static final String SHARD = "moon_field_shard_";
    public static final String GATE = "moon_field_gate";
    public static final String LORD = "moon_lord_full";

    private FieldPuzzles() {
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

    // ---------------------------------------------------------------- 月の溜まり (一瞬止めを覚える = 月の砂時計)
    public static void pool(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        boolean first = !MomentStop.learned(p);
        MomentStop.learn(p);
        if (!MoonHourglassItem.carries(p)) {
            ItemStack glass = new ItemStack(FieldBlocks.MOON_HOURGLASS.get());
            if (!p.getInventory().add(glass)) {
                p.drop(glass, false);
            }
        }
        Notice.narrate(p, first ? "message.skyabyss.moon.pool.moment_stop" : "message.skyabyss.moon.pool.moment_stop_again");
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 1.2f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 30, 0.6, 1.2, 0.6, 0.08);
        Quests.signal(p, POOL);
    }

    // ---------------------------------------------------------------- 欠片の台 (そこへ立てたことが決まり)
    public static void claim(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        String site = MoonField.siteOf(pos);
        if (site == null) {
            return;
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.3, 1.2, 0.3, 0.04);
        Notice.narrate(p, "message.skyabyss.moon.field_shard." + site);
        Quests.signal(p, SHARD + site);
    }

    // ---------------------------------------------------------------- 満月の門
    public static boolean mayCross(ServerPlayer p) {
        return stage(p) >= stageIndex("gate");
    }

    public static void gate(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        boolean up = pos.getY() < MoonField.mark("arena_entry").getY() - 6;
        if (up && !mayCross(p)) {
            Notice.narrate(p, stage(p) < stageIndex("shards") ? "message.skyabyss.moon.field_gate.sealed" : "message.skyabyss.moon.field_gate.need_shards");
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1f, 0.5f);
            return;
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        if (up) {
            BlockPos to = MoonField.mark("arena_entry");
            BlockPos c = MoonField.mark("arena_center");
            float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - to.getX()), c.getZ() - to.getZ()));
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, yaw, 0f);
            Quests.signal(p, GATE);
            p.addTag(FieldArena.TAG);
            FieldArena.arrive(level, p);
        } else {
            BlockPos to = MoonField.mark("gate_front");
            p.removeTag(FieldArena.TAG);
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 90f, 0f);
        }
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

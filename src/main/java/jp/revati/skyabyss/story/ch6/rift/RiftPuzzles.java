package jp.revati.skyabyss.story.ch6.rift;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch6.cliff.LightBody;
import jp.revati.skyabyss.story.ch6.field.FieldBlocks;
import jp.revati.skyabyss.story.ch6.field.MomentStop;
import jp.revati.skyabyss.story.ch6.field.MoonHourglassItem;
import jp.revati.skyabyss.story.ch6.valley.StarLampItem;
import jp.revati.skyabyss.story.ch6.valley.ValleyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

// 下弦の淵の決まり (docs/design/46 の 4.6)。頼み main_moon_rift の段: meet → pool → shards → gate → lord → promise
//   月の溜まり   三つの術を思い出す (星の灯・月の砂時計を失くしていたら渡す。覚えた印も付け直す。合図 moon_rift_pool)
//   欠片の台     二つの板の門の奥・残像の渡りの島・灯りの回廊の奥。そこへ立てた = 渡れた (合図 moon_rift_shard_<所>)
//   下弦の門     欠片を三つそろえた人を主の間の床へ。床の縁の帰り口は門の前へ (合図 moon_rift_gate)
public final class RiftPuzzles {
    public static final String QUEST = "main_moon_rift";
    public static final String POOL = "moon_rift_pool";
    public static final String SHARD = "moon_rift_shard_";
    public static final String GATE = "moon_rift_gate";
    public static final String LORD = "moon_lord_waning";

    private RiftPuzzles() {
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

    // ---------------------------------------------------------------- 月の溜まり (三つの術を思い出す)
    public static void pool(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        LightBody.learn(p);
        MomentStop.learn(p);
        give(p, new ItemStack(ValleyBlocks.STAR_LAMP.get()), StarLampItem.carries(p));
        give(p, new ItemStack(FieldBlocks.MOON_HOURGLASS.get()), MoonHourglassItem.carries(p));
        Notice.narrate(p, "message.skyabyss.moon.pool.rift");
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 0.8f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 30, 0.6, 1.2, 0.6, 0.08);
        Quests.signal(p, POOL);
    }

    private static void give(ServerPlayer p, ItemStack stack, boolean has) {
        if (has) {
            return;
        }
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
    }

    // ---------------------------------------------------------------- 欠片の台 (そこへ立てたことが決まり)
    public static void claim(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        String site = MoonRift.siteOf(pos);
        if (site == null) {
            return;
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.3, 1.2, 0.3, 0.04);
        Notice.narrate(p, "message.skyabyss.moon.rift_shard." + site);
        Quests.signal(p, SHARD + site);
    }

    // ---------------------------------------------------------------- 下弦の門
    public static boolean mayCross(ServerPlayer p) {
        return stage(p) >= stageIndex("gate");
    }

    public static void gate(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        boolean down = pos.getY() > MoonRift.mark("arena_entry").getY() + 6;
        if (down && !mayCross(p)) {
            Notice.narrate(p, stage(p) < stageIndex("shards") ? "message.skyabyss.moon.rift_gate.sealed" : "message.skyabyss.moon.rift_gate.need_shards");
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1f, 0.5f);
            return;
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        if (down) {
            BlockPos to = MoonRift.mark("arena_entry");
            BlockPos c = MoonRift.mark("arena_center");
            float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - to.getX()), c.getZ() - to.getZ()));
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, yaw, 0f);
            Quests.signal(p, GATE);
            p.addTag(RiftArena.TAG);
            RiftArena.arrive(level, p);
        } else {
            BlockPos to = MoonRift.mark("gate_front");
            p.removeTag(RiftArena.TAG);
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

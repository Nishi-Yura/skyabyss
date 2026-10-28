package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

// 常に効く技のうち、毎 tick・毎秒の見張りが要るもの (サーバーだけ):
// 動かずにいる時間 (静かな弦)、近くの野営 (野営の手)、備えの三灯 (備えの報い)、マナの回復の倍率 (凪の心得・野営の手・精神 15・巡りの唄)。
// 修飾子の付け直しは SkillManager.applyModifiers (ここから 0.5 秒ごとに呼ぶ)
final class SkillPassives {
    private SkillPassives() {
    }

    static void tick(ServerPlayer player, SkillData d, long now) {
        // 動かずにいる tick (静かな弦): 水平に 0.02 ブロック以内
        double x = player.getX();
        double z = player.getZ();
        boolean still = !Double.isNaN(d.stillX) && Math.hypot(x - d.stillX, z - d.stillZ) < 0.02;
        d.stillTicks = still ? Math.min(d.stillTicks + 1, 400) : 0;
        d.stillX = x;
        d.stillZ = z;
        if (player.tickCount % 10 == 3) {
            SkillExtOps.refreshRebirth(player);
            SkillManager.applyModifiers(player);
            d.regenScale = regenScale(player, d);
        }
    }

    // 焚き火・共鳴の炉の 6 ブロック以内か (刻印・型の切り替えの場所)
    static boolean campNear(Player player) {
        BlockPos center = player.blockPosition();
        int r = 6;
        if (!jp.revati.skyabyss.util.SectionScan.mayContain(player.level(), center.offset(-r, -r, -r), center.offset(r, r, r),
                s -> s.is(BlockTags.CAMPFIRES) || s.is(ModBlocks.RESONANCE_FORGE.get()))) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState s = player.level().getBlockState(p);
            if (s.is(BlockTags.CAMPFIRES) || s.is(ModBlocks.RESONANCE_FORGE.get())) {
                return true;
            }
        }
        return false;
    }

    static String nextTitan(ServerPlayer player) {
        String[] order = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};
        for (String t : order) {
            var rec = jp.revati.skyabyss.loot.TitanMemoryData.get(player.server).peek(player.getUUID(), t);
            if (rec == null || !rec.firstCleared) {
                return t;
            }
        }
        return order[order.length - 1];
    }

    // マナの回復の倍率 (いつもの分): 1 + 凪の心得。巡りの唄の上乗せは regenTick が期限を見て足す
    static double regenScale(ServerPlayer player, SkillData d) {
        double add = 1;
        double calm = SkillRules.calmHeartBonus(d.rank(Skill.CALM_HEART));
        // 近くの仲間の凪の心得も効く (高いほう。重ならない)
        for (ServerPlayer ally : SkillEffects.allies(player, SkillRules.ALLY_RANGE)) {
            calm = Math.max(calm, SkillRules.calmHeartBonus(SkillManager.get(ally).rank(Skill.CALM_HEART)));
        }
        return add + calm + SkillBuild.regenAdd(player);
    }
}

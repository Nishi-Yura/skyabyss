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
        int camp = d.rank(Skill.CAMP_HAND);
        if (camp > 0) {
            // 満腹度の減り: 野営の近くでは、その tick に増えた消耗の一部を戻す
            float ex = player.getFoodData().getExhaustionLevel();
            if (d.campNear && ex > d.lastExhaustion) {
                float back = (ex - d.lastExhaustion) * (float) SkillRules.campHunger(camp);
                player.getFoodData().setExhaustion(Math.max(0f, ex - back));
            }
            d.lastExhaustion = player.getFoodData().getExhaustionLevel();
        }
        if (player.tickCount % 10 == 3) {
            d.campNear = camp > 0 && campNear(player);
            if (player.tickCount % 100 == 3 || d.rank(Skill.READINESS_REWARD) == 0) {
                d.readyBlue = d.rank(Skill.READINESS_REWARD) > 0 && allBlue(player);
            }
            SkillManager.applyModifiers(player);
            d.regenScale = regenScale(player, d);
        }
    }

    // 焚き火・共鳴の炉の 6 ブロック以内か
    static boolean campNear(Player player) {
        BlockPos center = player.blockPosition();
        int r = (int) SkillRules.CAMP_RANGE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState s = player.level().getBlockState(p);
            if (s.is(BlockTags.CAMPFIRES) || s.is(ModBlocks.RESONANCE_FORGE.get())) {
                return true;
            }
        }
        return false;
    }

    // 備えの三灯がすべて青 (緑) か。次に挑む巨神 (まだ初回を終えていない最初の巨神。全部終えていれば最後) で見る
    static boolean allBlue(ServerPlayer player) {
        String titan = nextTitan(player);
        Readiness.Result r = Readiness.of(player, titan);
        return r.worst() == Readiness.Colour.GREEN;
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

    // マナの回復の倍率 (いつもの分): (1 + 精神 15 + 凪の心得) × 野営の手。巡りの唄の上乗せは regenTick が期限を見て足す
    static double regenScale(ServerPlayer player, SkillData d) {
        double add = 1;
        if (Breakpoints.reached(player, jp.revati.skyabyss.rpg.GrowthRules.Stat.SPIRIT, Breakpoints.FIRST)) {
            add += Breakpoints.SPIRIT_15_REGEN;
        }
        double calm = SkillRules.calmHeartBonus(d.rank(Skill.CALM_HEART));
        // 近くの仲間の凪の心得も効く (高いほう。重ならない)
        for (ServerPlayer ally : SkillEffects.allies(player, SkillRules.ALLY_RANGE)) {
            calm = Math.max(calm, SkillRules.calmHeartBonus(SkillManager.get(ally).rank(Skill.CALM_HEART)));
        }
        add += calm;
        int camp = d.rank(Skill.CAMP_HAND);
        return d.campNear && camp > 0 ? add * SkillRules.campRegen(camp) : add;
    }
}

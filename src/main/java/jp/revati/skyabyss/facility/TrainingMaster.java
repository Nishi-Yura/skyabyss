package jp.revati.skyabyss.facility;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.server.level.ServerPlayer;

// 訓練場の師範の振り直し (docs/design/35 の 3.2、27 の 17 節の決まりのまま)。
//   師範の前は「共鳴の炉のそば」と同じに扱う (炉まで行かなくてよい)。代価は 27 のとおり共鳴の結晶 (技: 結晶 1、目利きで無料、
//   Lv 15 まで無料。力: 初回無料、以後 1〜5)。結晶の代わりに鐘銭でも払える (結晶 1 つ = COINS_PER_CRYSTAL 枚)
//   見積もり (quote) は、振り直しの節に入ったときに一行で出す
public final class TrainingMaster {
    private TrainingMaster() {
    }

    public static final int COINS_PER_CRYSTAL = 30;

    // 技の振り直しの結晶の数 (無料なら 0)
    public static int skillCrystals(ServerPlayer player) {
        return SkillManager.freeRespec(player) ? 0 : SkillManager.respecCostFor(player);
    }

    // 力 (ステータス) の振り直しの結晶の数
    public static int statCrystals(ServerPlayer player) {
        return GrowthRules.statRespecCost(Growth.get(player).statRespecs);
    }

    public static void quote(ServerPlayer player) {
        int s = skillCrystals(player);
        int g = statCrystals(player);
        Notice.hint(player, "message.skyabyss.training.quote", s, s * COINS_PER_CRYSTAL, g, g * COINS_PER_CRYSTAL);
    }

    // stats = 力の振り直し (false なら技)、coins = 鐘銭で払う
    public static SkillManager.RespecResult respec(ServerPlayer player, boolean stats, boolean coins) {
        boolean nothing = stats ? Growth.get(player).spentPoints() <= 0 : SkillManager.get(player).ranks.isEmpty();
        if (nothing) {
            Notice.hint(player, stats ? "message.skyabyss.training.nothing_stats" : "message.skyabyss.training.nothing_skills");
            return SkillManager.RespecResult.NOTHING;
        }
        int crystals = stats ? statCrystals(player) : skillCrystals(player);
        int price = coins && !player.isCreative() ? crystals * COINS_PER_CRYSTAL : 0;
        if (price > 0 && Coins.count(player) < price) {
            Notice.hint(player, "message.skyabyss.training.no_coins", price);
            return SkillManager.RespecResult.NO_CRYSTALS;
        }
        SkillManager.RespecResult r = stats ? Growth.respecStats(player, true, !coins) : SkillManager.respec(player, true, !coins);
        switch (r) {
            case OK -> {
                if (price > 0) {
                    Coins.take(player, price);
                }
            }
            case NOTHING -> Notice.hint(player, stats ? "message.skyabyss.training.nothing_stats" : "message.skyabyss.training.nothing_skills");
            case NO_CRYSTALS -> Notice.hint(player, "message.skyabyss.training.no_crystals", crystals);
            default -> {
            }
        }
        return r;
    }
}

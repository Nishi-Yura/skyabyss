package jp.revati.skyabyss.world.continent;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Hardness;
import jp.revati.skyabyss.combat.LevelScale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// 外れの帯 (docs/design/40 の 3 章。2026-11-06 の頁「大陸の外れ」の m_reasons・m_foes = a。issue #19)。
// 都と土地の町から FAR より先の、四つの巨神の土地 (岩鯨・潮・嵐・灰) を「外れ」とする。
//   ① 敵の目安 Lv を土地の上限 +LEVEL_PLUS に: 外れで出た敵は、土地の上限の Lv と +3 の Lv の見込みの傷の比 (LevelScale.expectedDps) だけ
//      体力が多く (Hardness の "far")、その 0.6 乗だけ攻撃が強い (MobTuning.attackFactor が読む)
//   ② 一人ずつの宝箱を多く置き、中身の封の箱の格を一段上げる (FrontierCaches)
//   ③ 珍しい獣と名のある強敵は外れに出る (RareBeasts・NamedFoes)
//   敵の数: 外れでは近くの上限 NEAR_CAP、群れは昼も出る、外れの道に野盗の見回り (RegionSpawner)
//   ④ 土地の全部の名所で称号と鐘銭 (前からある旅帳。endgame/RegionFame) ⑤ 外れの集落の頼みが奥を指す (前からある)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FrontierBand {
    public static final double FAR = 600;
    public static final int LEVEL_PLUS = 3;
    public static final int NEAR_CAP = 14;
    public static final String TAG = "skyabyss_far_foe";
    public static final String ATTACK = "skyabyss_far_attack";
    static final double ATTACK_POW = 0.6;

    private FrontierBand() {
    }

    // 都と四つの町のいちばん近いものまでの距離
    public static double townDistance(double x, double z) {
        ContinentLayout lay = ContinentLayout.get();
        BlockPos c = lay.capital();
        double best = c == null ? Double.MAX_VALUE : Math.hypot(x - c.getX(), z - c.getZ());
        for (String t : new String[] {"whale", "tide", "storm", "ember"}) {
            BlockPos p = lay.town(t);
            if (p != null) {
                best = Math.min(best, Math.hypot(x - p.getX(), z - p.getZ()));
            }
        }
        return best;
    }

    // 巨神の土地か (外れになりうる土地)
    public static boolean titanLand(int region) {
        return region == ContinentMap.WHALE || region == ContinentMap.TIDE || region == ContinentMap.STORM || region == ContinentMap.EMBER;
    }

    public static boolean far(double x, double z) {
        ContinentMap map = ContinentMap.get();
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        return map.inside(ix, iz) && titanLand(map.region(ix, iz)) && townDistance(x, z) > FAR;
    }

    // 土地の上限の Lv (章の終わりの目安。Chapters.levelTo)
    public static int landLevel(int region) {
        return switch (region) {
            case ContinentMap.WHALE -> 12;
            case ContinentMap.TIDE -> 18;
            case ContinentMap.STORM -> 24;
            case ContinentMap.EMBER -> 30;
            default -> 4;
        };
    }

    // 外れの体力の倍率 (土地の上限 +3 の Lv の見込みの傷 / 上限の Lv の見込みの傷)
    public static double healthFactor(int region) {
        int lv = landLevel(region);
        return LevelScale.expectedDps(lv + LEVEL_PLUS) / LevelScale.expectedDps(lv);
    }

    // 外れの敵にする (二度目は何もしない)
    public static void toughen(Mob m, int region) {
        if (m.getTags().contains(TAG)) {
            return;
        }
        double k = healthFactor(region);
        m.addTag(TAG);
        Hardness.set(m, "far", k);
        m.getPersistentData().putDouble(ATTACK, Math.pow(k, ATTACK_POW));
    }

    // 攻撃の倍率 (MobTuning.attackFactor から)
    public static double attack(LivingEntity e) {
        double k = e.getPersistentData().getDouble(ATTACK);
        return k > 1 ? k : 1;
    }

    // 地上に現れた敵 (野の湧き・見どころの野盗・名のある強敵・珍しい獣以外の敵) が外れにいれば強くする。
    // 空の高い所 (Y 250 より上) と地の底 (Y 0 より下) の試し (自己テスト) の敵は変えない
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel) || event.getLevel().dimension() != Level.OVERWORLD
                || !(event.getEntity() instanceof Mob m) || !(m instanceof Enemy) || m.getY() > 250 || m.getY() < 0) {
            return;
        }
        if (far(m.getX(), m.getZ())) {
            toughen(m, ContinentMap.get().region(m.getBlockX(), m.getBlockZ()));
        }
    }
}

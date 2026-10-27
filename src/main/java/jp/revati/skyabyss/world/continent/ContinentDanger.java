package jp.revati.skyabyss.world.continent;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// 土地ごとの敵の強さ (docs/design/40 の 4 章「都のまわりと最初の土地の敵は弱い」と、地図の目安のレベル)。
// 天と淵の大陸の地上で、マインクラフトの敵 (ゾンビ・クモなど) が現れたとき、その場所の土地で体力と攻撃を変える。
//   都のまわり ×0.55 / ×0.5 (二〜三回で倒れる)、外れの谷 ×0.45 / ×0.4、岩鯨の丘陵 ×1、潮の浜 ×1.3 / ×1.25、嵐の高地 ×1.7 / ×1.5、灰の荒野 ×2.1 / ×1.8
// この作品の敵 (skyabyss:) は章ごとに釣り合いを取ってあるので変えない。ただし大陸の新しい敵 (entity/foe の ContinentFoe) は岩鯨を 1 とした素の値なので掛ける
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentDanger {
    static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "continent_region_health");
    static final ResourceLocation DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "continent_region_damage");
    // 土地の番号 (ContinentMap.MEADOW..EMBER) ごとの {体力, 攻撃}
    static final double[][] SCALE = {
        {0.55, 0.5}, {0.45, 0.4}, {1.0, 1.0}, {1.3, 1.25}, {1.7, 1.5}, {2.1, 1.8},
    };

    private ContinentDanger() {
    }

    public static double[] scale(int region) {
        return SCALE[Math.max(0, Math.min(SCALE.length - 1, region))];
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || !(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)
                || !("minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace()) || mob instanceof jp.revati.skyabyss.entity.foe.ContinentFoe)) {
            return;
        }
        // 空の高い所 (Y 250 より上) と地の底 (Y 0 より下。大陸に洞窟は無い) には自然には湧かない。試し (自己テスト) の敵が土地の強さで変わらないように
        if (mob.getY() > 250 || mob.getY() < 0) {
            return;
        }
        apply(mob, ContinentMap.get().region(mob.getBlockX(), mob.getBlockZ()));
    }

    public static void apply(Mob mob, int region) {
        double[] k = scale(region);
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && !hp.hasModifier(HEALTH_ID)) {
            hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, k[0] - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && !dmg.hasModifier(DAMAGE_ID)) {
            dmg.addPermanentModifier(new AttributeModifier(DAMAGE_ID, k[1] - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}

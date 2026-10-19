package jp.revati.skyabyss.client.entity;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.entity.BoneGull;
import jp.revati.skyabyss.entity.BoneMortar;
import jp.revati.skyabyss.entity.ElderSentinel;
import jp.revati.skyabyss.registry.Ch1BackEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 岩鯨の背の新しい敵 (骨の砲手・骨鳥・老番兵。docs/design/21 の 3.3) の描画。どれも低ポリのメッシュ (client/lowpoly)。
// 武器・盾はメッシュの骨の一部なので GearLayer は無い。発光 (琥珀の目) は textures/lowpoly/<名>_glowmask.png があれば自動で付く
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class Ch1BackRenderers {
    private Ch1BackRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        // 骨の砲手は低く幅がある: メッシュ 1.5 倍 (台座の幅 2 マス・砲身の先 2 マス)
        event.registerEntityRenderer(Ch1BackEntities.BONE_MORTAR.get(), ctx -> new LowPolyGeoRenderer<BoneMortar>(ctx, "bone_mortar", 1.5f));
        // 骨鳥は小さい: 1.6 倍 (翼を広げて 2.2 マス)
        event.registerEntityRenderer(Ch1BackEntities.BONE_GULL.get(), ctx -> new LowPolyGeoRenderer<BoneGull>(ctx, "bone_gull", 1.6f));
        // 老番兵は、ラフが骨の番兵の 1.12 倍で作ってあるので等倍
        event.registerEntityRenderer(Ch1BackEntities.ELDER_SENTINEL.get(), ctx -> new LowPolyGeoRenderer<ElderSentinel>(ctx, "elder_sentinel", 1.0f));
    }
}

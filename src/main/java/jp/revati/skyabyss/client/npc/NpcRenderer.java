package jp.revati.skyabyss.client.npc;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;

// 語り部と鯨追いの描画 (GeckoLib + 低ポリのメッシュ)。光る画素が無いアトラスは発光層を描かない (LowPolyGeoRenderer.GlowLayer がマスクの有無で決める)。
// 頭は相手の方を向く。geo の root 骨が yaw 180 度回してあるので、上下の向き (pitch) は符号を反転して入れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class NpcRenderer {
    // 頭の向きの符号 (ゲームで確かめて、逆なら -1 を 1 に)
    static final float PITCH_SIGN = -1f;
    static final float YAW_SIGN = 1f;

    private NpcRenderer() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcSetup.STORYTELLER.get(), ctx -> create(ctx, "storyteller", 0.38f));
        event.registerEntityRenderer(NpcSetup.WHALE_CHASER.get(), ctx -> create(ctx, "whale_chaser", 0.42f));
    }

    // 低ポリ (client/lowpoly): LowPoly.enabled = false で元の滑らかなボクセルの geo (geo/entity/<name>.geo.json) に戻る
    private static LowPolyGeoRenderer<NpcEntity> create(EntityRendererProvider.Context ctx, String name, float shadow) {
        return new LowPolyGeoRenderer<>(ctx, new Model(name), e -> name, 1.0f) {
            {
                this.shadowRadius = shadow;
            }
        };
    }

    static final class Model extends LowPolyGeoRenderer.Model<NpcEntity> {
        Model(String name) {
            super(name, false);
        }

        @Override
        public void setCustomAnimations(NpcEntity entity, long instanceId, AnimationState<NpcEntity> state) {
            super.setCustomAnimations(entity, instanceId, state);
            GeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head == null || data == null) {
                return;
            }
            float pitch = Mth.clamp(data.headPitch(), -40f, 40f);
            float yaw = Mth.clamp(data.netHeadYaw(), -70f, 70f);
            // 話している間は、ゆっくり小さくうなずく
            float nod = entity.isTalking() ? Mth.sin((entity.tickCount + state.getPartialTick()) * 0.18f) * 3f : 0f;
            head.setRotX(PITCH_SIGN * (pitch + nod) * Mth.DEG_TO_RAD);
            head.setRotY(YAW_SIGN * yaw * Mth.DEG_TO_RAD);
        }
    }
}

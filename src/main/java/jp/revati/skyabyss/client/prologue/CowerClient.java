package jp.revati.skyabyss.client.prologue;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.sky.SkyPassClient;
import jp.revati.skyabyss.sky.SkyPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// 空の影の下で、動物と村人が身を低くしてうずくまり、小さく震える (見た目だけ)。
// 動きを止めて空を見上げるのはサーバー (SkyEvents.frightenAnimals)。ここでは、影の濃さに合わせて
// 体を縦に縮め、横に少し広げて「しゃがみ込んだ」姿にする。影が去ると、ゆっくり元に戻る。
// 影の形はサーバーと同じ SkyPass から計算するので、パケットは要らない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CowerClient {
    // 何ブロック以内の動物まで計算するか
    private static final double RANGE = 96.0;
    // 何 tick 先の影まで怯えるか (サーバーと同じ)
    private static final double LOOKAHEAD = 80.0;
    // うずくまりの深さ: 縦にどれだけ縮めるか、横にどれだけ広げるか
    private static final float SQUASH = 0.26f;
    private static final float SPREAD = 0.08f;

    private static final Int2FloatOpenHashMap AMOUNT = new Int2FloatOpenHashMap();
    // 描く前にかけた変形を、描いたあとで打ち消すために覚えておく (entity id -> [震え, 縦, 横])
    private static final Int2ObjectOpenHashMap<float[]> APPLIED = new Int2ObjectOpenHashMap<>();

    private CowerClient() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            AMOUNT.clear();
            return;
        }
        SkyPass pass = SkyPassClient.current();
        boolean active = pass != null && pass.isActive(level.getGameTime());
        if (!active && AMOUNT.isEmpty()) {
            return;
        }
        double age = active ? pass.age(level.getGameTime(), 0f) : 0;
        double range2 = RANGE * RANGE;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Animal || e instanceof AbstractVillager)) {
                continue;
            }
            boolean near = e.distanceToSqr(mc.player) <= range2;
            if (!near && !AMOUNT.containsKey(e.getId())) {
                continue;
            }
            float target = 0f;
            if (active && near) {
                float fear = Math.max(pass.shadow(e.getX(), e.getZ(), age), pass.shadow(e.getX(), e.getZ(), age + LOOKAHEAD));
                target = Mth.clamp(fear * 1.5f, 0f, 1f);
            }
            float now = AMOUNT.get(e.getId());
            // 身をかがめるのは速く、起き上がるのはゆっくり
            now += (target - now) * (target > now ? 0.12f : 0.035f);
            if (now < 0.002f && target == 0f) {
                AMOUNT.remove(e.getId());
            } else {
                AMOUNT.put(e.getId(), now);
            }
        }
        // 見えなくなった (離れた・消えた) ものの記録を消す
        if (!active) {
            AMOUNT.int2FloatEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        float amount = AMOUNT.get(entity.getId());
        if (amount <= 0.002f || event.isCanceled()) {
            return;
        }
        // 震え: 横にごく小さく、速く揺れる
        double t = entity.tickCount + event.getPartialTick();
        float tremble = (float) Math.sin(t * 2.9 + entity.getId()) * 0.012f * amount;
        float squash = 1f - SQUASH * amount;
        float spread = 1f + SPREAD * amount;
        // pushPose を使わないのは、ほかの Mod が描画を取り消して Post が来なかったときに、
        // 姿勢の積み残しでゲームが落ちないようにするため (その場合も見た目が一瞬ずれるだけ)
        var pose = event.getPoseStack();
        pose.translate(tremble, 0, 0);
        pose.scale(spread, squash, spread);
        APPLIED.put(entity.getId(), new float[] {tremble, squash, spread});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        float[] applied = APPLIED.remove(event.getEntity().getId());
        if (applied != null) {
            var pose = event.getPoseStack();
            pose.scale(1f / applied[2], 1f / applied[1], 1f / applied[2]);
            pose.translate(-applied[0], 0, 0);
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        AMOUNT.clear();
        APPLIED.clear();
    }
}

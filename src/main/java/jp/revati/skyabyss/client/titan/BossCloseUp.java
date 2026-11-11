package jp.revati.skyabyss.client.titan;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.camera.ShoulderCam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

// ボスの登場の「一瞬の見せ場」: 名前の札 (combat/BossIntro の NAME) が出るあいだ、カメラが主の顔の前へ寄ってアップにする
// (2026-11-11 の声。最初の目的の三つ目「地中からの出現や顔のアップのような一瞬の見せ場」)。
//   寄り   IN tick: 人の目からなめらかに、主の顔の斜め前へ (少し下から見上げる)
//   見せ場 主の顔へゆっくり寄り続け (ドリー)、わずかに回り込む
//   戻り   OUT tick: 人の目へ戻る
// 壁や天井にめり込むときは、主の顔から引いた線の当たりで止める。傷を受けた・主が消えた・死んだら、すぐ戻る。手は描かない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BossCloseUp {
    static final int IN = 10;
    static final int OUT = 12;
    static final int MAX = 80;
    private static int entityId = -1;
    private static int length;
    private static int age = -1;
    private static float hurtAt;
    // 撮り始めの主の足もとと向き。アップの間はこれに固定して撮る (主が少しでも動いてもカメラは揺れない。2026-11-11 の声)
    private static Vec3 anchor;
    private static float anchorYaw;

    private BossCloseUp() {
    }

    public static void start(int entity, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        entityId = entity;
        length = jp.revati.skyabyss.combat.BossIntro.closeUpLength(ticks);
        age = 0;
        anchor = null;
        hurtAt = mc.player.hurtTime;
    }

    public static boolean active() {
        return age >= 0;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (age < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        boolean gone = e == null || !e.isAlive() || mc.player == null || !mc.player.isAlive();
        if (!gone && mc.player.hurtTime > hurtAt && age < length - OUT) {
            // 傷を受けたら戻り始める
            age = length - OUT;
        }
        if (gone || ++age > length) {
            age = -1;
        }
    }

    @SubscribeEvent
    static void onHand(RenderHandEvent event) {
        if (active()) {
            event.setCanceled(true);
        }
    }

    // アップの間は画面の表示 (クエスト・ホットバーなど) を隠す。名前の札 (BossIntroClient) は RenderGuiEvent.Post で描くので残る
    @SubscribeEvent
    static void onGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (active()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        age = -1;
    }

    // カメラを置き換えたら true
    public static boolean place(Camera camera, ShoulderCam.CameraAccess access, Entity viewer, float partial) {
        if (age < 0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity boss = mc.level == null ? null : mc.level.getEntity(entityId);
        if (boss == null) {
            return false;
        }
        if (anchor == null) {
            anchor = boss.getPosition(partial);
            anchorYaw = boss instanceof LivingEntity al ? Mth.rotLerp(partial, al.yBodyRotO, al.yBodyRot) : boss.getYRot();
        }
        Vec3 base = anchor;
        float t = age + partial;
        float in = Mth.clamp(t / IN, 0f, 1f);
        float out = Mth.clamp((length - t) / OUT, 0f, 1f);
        float w = smooth(Math.min(in, out));
        float hold = Mth.clamp((t - IN) / Math.max(1, length - IN - OUT), 0f, 1f);
        // 主の見た目の大きさ (描くときの倍率。当たりの箱より大きく描く主が多い) と、顔の高さ・前の向き
        float vs = mc.getEntityRenderDispatcher().getRenderer(boss) instanceof jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<?> r ? r.visualScale() : 1f;
        double vh = boss.getBbHeight() * Math.max(1f, vs * 0.75f);
        Vec3 face = base.add(0, vh * 0.82, 0);
        Vec3 head = headPoint(mc, boss, vs);
        // 前の向き (yaw の正面) は下で決めるので、頭の置き場は主の向きで回してから足す
        float byaw = anchorYaw;
        if (head != null) {
            double c = Math.cos(Math.toRadians(byaw)), sn = Math.sin(Math.toRadians(byaw));
            // 模型の前は -Z。描くときに 180 - yaw 回すので、世界では (x, z) → 回した向き
            double wx = -head.x * c - (-head.z) * sn;
            double wz = -head.x * sn + (-head.z) * c;
            face = base.add(wx, head.y, wz);
        }
        float yaw = anchorYaw;
        // 主の顔が人の側を向いていないときは、人のいる側から撮る
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 toViewer = viewer.getPosition(partial).subtract(base).multiply(1, 0, 1);
        if (toViewer.lengthSqr() > 1e-4 && fwd.dot(toViewer.normalize()) < -0.2) {
            fwd = toViewer.normalize();
        }
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        double size = Math.max(1.2, Math.max(boss.getBbWidth() * Math.max(1f, vs * 0.75f), vh * 0.6));
        // 顔のアップ: 寄る距離は顔の大きさ (見た目の高さのおよそ 2 割) に合わせ、見せ場の間にさらに寄る
        double faceSize = Math.max(0.8, vh * 0.2);
        double dist = (1.8 + faceSize * 2.2) * (1.0 - 0.3 * hold);
        double swing = (0.5 - 0.35 * hold) * faceSize;
        size = faceSize;
        Vec3 want = face.add(fwd.scale(dist)).add(side.scale(swing)).add(0, -size * 0.18, 0);
        // めり込まないように、顔から引いた線の当たりで止める
        HitResult hit = mc.level.clip(new ClipContext(face, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 d = want.subtract(face);
            want = face.add(d.scale(Math.max(0.2, hit.getLocation().distanceTo(face) / Math.max(1e-3, d.length()) - 0.08)));
        }
        Vec3 eye = viewer.getEyePosition(partial);
        Vec3 pos = eye.lerp(want, w);
        Vec3 look = face.subtract(pos);
        float camYaw = (float) (Mth.atan2(look.z, look.x) * 57.2957763671875) - 90f;
        float camPitch = (float) -(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * 57.2957763671875);
        float vYaw = viewer.getViewYRot(partial);
        float vPitch = viewer.getViewXRot(partial);
        access.skyabyss$setPosition(pos);
        access.skyabyss$setRotation(vYaw + Mth.wrapDegrees(camYaw - vYaw) * w, Mth.lerp(w, vPitch, camPitch));
        return true;
    }

    // 模型の頭の部品の真ん中 (ブロック単位、主の足もとから。模型の空間のまま: 前は -Z)。頭の部品が無ければ null
    static final String[] HEADS = {"head", "f0", "skull", "jaw", "beak_u", "mask_l", "heart", "neck2"};

    @org.jetbrains.annotations.Nullable
    static Vec3 headPoint(Minecraft mc, Entity boss, float vs) {
        if (!(mc.getEntityRenderDispatcher().getRenderer(boss) instanceof jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<?> r)) {
            return null;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        jp.revati.skyabyss.client.lowpoly.LowPolyMesh mesh = ((jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer) r).meshOf(boss);
        if (mesh == null) {
            return null;
        }
        for (String n : HEADS) {
            jp.revati.skyabyss.client.lowpoly.LowPolyMesh.Bone b = mesh.bones.get(n);
            if (b == null || b.pos.length < 3) {
                continue;
            }
            double sx = 0, sy = 0, sz = 0;
            int k = b.pos.length / 3;
            for (int i = 0; i < k; i++) {
                sx += b.pos[i * 3];
                sy += b.pos[i * 3 + 1];
                sz += b.pos[i * 3 + 2];
            }
            float sc = vs * (boss instanceof LivingEntity le ? le.getScale() : 1f);
            return new Vec3(sx / k * sc, sy / k * sc, sz / k * sc);
        }
        return null;
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }
}

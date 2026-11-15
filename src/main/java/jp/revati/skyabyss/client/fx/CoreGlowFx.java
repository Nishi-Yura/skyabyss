package jp.revati.skyabyss.client.fx;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.combat.BreakGaugeClient;
import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.titan.ember.heart.EmberHeart;
import jp.revati.skyabyss.titan.storm.heart.StormHeart;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// 心臓の核の光 (2026-11-15 の光の表現の選定 6)。核の骨 (動く骨の位置をそのまま追う) のまわりに、柔らかくにじむ光を重ねる。
// 鼓動に合わせて脈打ち、崩れている間 (崩しゲージが割れた = 傷が大きく通る時) は大きく白く燃え立つ。「狙う所」がひと目でわかる。
// 骨の位置は LowPolyGeoRenderer が骨を描くときに知らせる (track)。殻の内側に隠れないよう、光はカメラ側へ殻の厚みだけ寄せる。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CoreGlowFx {
    private CoreGlowFx() {
    }

    // 核として追う骨の名 (先にあるものほど優先)
    private static final String[] CORE_BONES = {"core_eye", "core", "heart"};

    private record Seen(Vec3 at, int rank, long frame) {
    }

    private static final Map<Integer, Seen> CORES = new HashMap<>();
    private static long frame;

    public static boolean wants(Entity e) {
        return e instanceof HeartBoss;
    }

    // 骨を描く直前 (姿勢は骨の空間)。核の骨なら、その pivot のワールドの位置を覚える
    public static void track(Entity e, String bone, float pivotX, float pivotY, float pivotZ, Matrix4f pose) {
        int rank = -1;
        for (int i = 0; i < CORE_BONES.length; i++) {
            if (CORE_BONES[i].equals(bone)) {
                rank = i;
                break;
            }
        }
        if (rank < 0) {
            return;
        }
        Seen was = CORES.get(e.getId());
        if (was != null && was.frame() == frame && was.rank() < rank) {
            return;
        }
        Vector3f p = pose.transformPosition(new Vector3f(pivotX / 16f, pivotY / 16f, pivotZ / 16f));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        CORES.put(e.getId(), new Seen(new Vec3(p.x() + cam.x, p.y() + cam.y, p.z() + cam.z), rank, frame));
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CORES.clear();
    }

    static int rgbOf(Entity e) {
        if (e instanceof HeartBoss h && h.isAbyss()) {
            return 0xB48CFF;
        }
        if (e instanceof TideHeart) {
            return 0x8FE4FF;
        }
        if (e instanceof StormHeart) {
            return 0xC9B8FF;
        }
        if (e instanceof EmberHeart) {
            return 0xFF8A3C;
        }
        if (e instanceof StoneWhaleHeart) {
            return 0xCFF5DC;
        }
        return 0xFFFFFF;
    }

    // 鼓動 (どくん・どくん、休み)
    static float beat(double t) {
        double ph = (t % 28.0) / 28.0;
        double a = Math.exp(-Math.pow((ph - 0.08) / 0.035, 2));
        double b = Math.exp(-Math.pow((ph - 0.22) / 0.04, 2)) * 0.7;
        return (float) (a + b);
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            frame++;
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CORES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f m = event.getPoseStack().last().pose();
        BufferBuilder b = LightDraw.begin();
        boolean any = false;
        for (var it = CORES.entrySet().iterator(); it.hasNext();) {
            var en = it.next();
            Seen s = en.getValue();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || frame - s.frame() > 3) {
                if (e == null || frame - s.frame() > 200) {
                    it.remove();
                }
                continue;
            }
            any = true;
            draw(b, m, camera, cam, e, s.at(), t);
        }
        if (any) {
            // 殻の外まで光がにじむように奥行きは見ない (壁の向こうの心臓は draw で見通しを確かめて描かない)
            LightDraw.end(b, LightDraw.SOFT, false);
        } else {
            b.build();
        }
    }

    private static void draw(BufferBuilder b, Matrix4f m, Camera camera, Vec3 cam, Entity e, Vec3 at, double t) {
        // ブロックの壁の向こうなら描かない (奥行きを見ないため)
        Vec3 eye = cam;
        var hit = e.level().clip(new net.minecraft.world.level.ClipContext(eye, at, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(eye) < at.distanceToSqr(eye) - 1.0
                && !e.level().getBlockState(((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos()).is(net.minecraft.world.level.block.Blocks.BARRIER)) {
            return;
        }
        int rgb = rgbOf(e);
        float broken = BreakGaugeClient.broken(e.getId());
        float dying = e instanceof HeartBoss h && !h.isAlive() ? 0.4f : 1f;
        Vec3 rel = at.subtract(cam);
        double dist = rel.length();
        // 殻の厚みだけカメラ側へ (近すぎるときは寄せない)
        double pull = Math.min(e.getBbWidth() * 0.32, Math.max(0, dist - 2.5));
        Vec3 c = dist < 1e-3 ? rel : rel.subtract(rel.scale(pull / dist));
        float beat = beat(t + e.getId() * 3);
        float s = (float) Mth.clamp(e.getBbWidth() * 0.42 + 1.0, 1.6, 6.0) * (1 + 0.6f * broken);
        float a = (0.55f + 0.3f * beat + 0.45f * broken) * dying;
        int hot = LightDraw.whiten(rgb, 0.35f + 0.5f * broken);
        LightDraw.sprite(b, m, camera, c, s * (2.6f + 0.3f * beat), rgb, 0.28f * a);
        LightDraw.sprite(b, m, camera, c, s * (1.1f + 0.15f * beat), hot, 0.55f * a);
        LightDraw.sprite(b, m, camera, c, s * 0.38f, 0xFFFFFF, Math.min(1f, 0.6f * a + 0.3f * broken));
        // 十字の光の筋 (ゆっくり回る。崩れている間は長く伸びる。2026-11-15 の選定 B)
        float roll = (float) (t * 0.01);
        float len = s * (3.4f + 1.2f * beat + 2.5f * broken);
        LightDraw.sprite(b, m, camera, c, len, s * 0.16f, roll, hot, 0.7f * a);
        LightDraw.sprite(b, m, camera, c, len, s * 0.16f, roll + Mth.HALF_PI, hot, 0.7f * a);
        LightDraw.sprite(b, m, camera, c, len * 0.55f, s * 0.1f, roll + Mth.HALF_PI * 0.5f, hot, 0.4f * a);
        LightDraw.sprite(b, m, camera, c, len * 0.55f, s * 0.1f, roll - Mth.HALF_PI * 0.5f, hot, 0.4f * a);
    }
}

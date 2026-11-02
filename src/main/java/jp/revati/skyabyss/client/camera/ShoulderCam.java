package jp.revati.skyabyss.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;

// この mod の三人称 (肩越し)。2026-11-02 の声「3人称視点をオリジナルの物にして、3人称視点でも戦いやすくして」。docs/design/18 の 7 章。
//   - F5 は 一人称 → 肩越し → 一人称 (前から映す視点は飛ばす。設定 shoulderCamera を切るとバニラのまま)
//   - カメラは体の後ろ shoulderDistance・肩の高さ・片側 (右。F6 で左右を替える) に置く。壁や天井に当たると寄る。乗り物の上では少し引く
//   - カメラの向きはマウスで動かし、体は「画面の真ん中の照準の先」を向く (肩越しのずれをなくす)。殴る・技・弓は照準の所へ
//   - 照準を画面の真ん中に出す (バニラは三人称で出さない)。照準が敵に乗ると赤い角を足す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ShoulderCam {
    public static final KeyMapping SWAP_KEY = new KeyMapping("key.skyabyss.swap_shoulder", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.SWAP_SHOULDER, "key.categories.skyabyss");
    private static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("hud/crosshair");
    // 肩の高さ・横のずれ (ブロック)。照準の先を探す遠さ
    static final double UP = 0.5;
    static final double SIDE = 0.75;
    static final double AIM_REACH = 64;

    private static boolean wasActive;
    private static float camYaw;
    private static float camPitch;
    // 右 = 1、左 = -1。いまの横のずれは side へなめらかに寄る
    private static int side = 1;
    private static double sideNow = 1;
    private static long lastFrame;
    // 前のフレームで体に置いた向き (ほかの仕組みが体を回したら、カメラもそれに合わせる)
    private static float setYaw;
    private static float setPitch;
    // 照準の先の敵 (赤い角を出す)
    private static Entity aimed;
    // 撮影の台本 (ShotRunner の perspective front) が前から映す視点を選んだときは、F5 の飛ばしで一人称へ戻さない
    public static boolean keepFront;

    private ShoulderCam() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(SWAP_KEY);
        }
    }

    // ほかのカメラの mod (Shoulder Surfing Reloaded) が入っていれば、この mod の肩越しは使わない (二つのカメラがぶつかる)
    private static final boolean OTHER_CAMERA = net.neoforged.fml.ModList.get().isLoaded("shouldersurfing");

    // 肩越しが効いているか (三人称の背中側・自分を映している・場面の最中でない)
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return Config.SHOULDER_CAMERA.get() && !OTHER_CAMERA && mc.player != null && mc.level != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                && mc.getCameraEntity() == mc.player && !mc.player.isSleeping();
    }

    public static float yaw() {
        return camYaw;
    }

    public static float pitch() {
        return camPitch;
    }

    // マウスの動き (MouseHandlerShoulderMixin): 肩越しの間はカメラの向きだけを回す。体は照準へ向け直す
    public static boolean turn(LocalPlayer player, double dx, double dy) {
        if (!active()) {
            return false;
        }
        camYaw += (float) dx * 0.15f;
        camPitch = Mth.clamp(camPitch + (float) dy * 0.15f, -89.9f, 89.9f);
        return true;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            wasActive = false;
            return;
        }
        // F5: 前から映す視点を飛ばす (一人称 → 肩越し → 一人称)
        if (Config.SHOULDER_CAMERA.get() && !OTHER_CAMERA && !keepFront && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        while (SWAP_KEY.consumeClick()) {
            side = -side;
        }
        boolean now = active();
        if (now && !wasActive) {
            camYaw = mc.player.getYRot();
            camPitch = mc.player.getXRot();
            setYaw = camYaw;
            setPitch = camPitch;
            sideNow = side;
        }
        wasActive = now;
    }

    // 毎フレーム: 体を照準の先へ向ける (描画とバニラの的の拾い (pick) の前)
    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.isPaused()) {
            aimed = null;
            return;
        }
        long t = net.minecraft.Util.getMillis();
        double dt = lastFrame == 0 ? 0.016 : Math.min(0.1, (t - lastFrame) / 1000.0);
        lastFrame = t;
        sideNow += (side - sideNow) * Math.min(1, dt * 8);
        LocalPlayer p = mc.player;
        // 体の向きが外から変わった (場所移し・乗り物が回った・技の向き合わせなど): カメラをその向きへ
        if (Math.abs(Mth.wrapDegrees(p.getYRot() - setYaw)) > 0.5f || Math.abs(p.getXRot() - setPitch) > 0.5f) {
            camYaw += Mth.wrapDegrees(p.getYRot() - setYaw);
            camPitch = Mth.clamp(camPitch + (p.getXRot() - setPitch), -89.9f, 89.9f);
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 eye = p.getEyePosition(partial);
        Vec3 cam = position(p, eye, partial);
        Vec3 look = Vec3.directionFromRotation(camPitch, camYaw);
        Vec3 aim = aimPoint(p, cam, look, eye);
        Vec3 d = aim.subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        if (d.lengthSqr() < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        // 体の向きは yaw を連続に保つ (−180/180 をまたいでも一回転しない)
        float cur = p.getYRot();
        yaw = cur + Mth.wrapDegrees(yaw - cur);
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -90f, 90f));
        p.yRotO = p.getYRot();
        p.xRotO = p.getXRot();
        setYaw = p.getYRot();
        setPitch = p.getXRot();
    }

    // カメラの置き場: 目から、肩の高さ・片側・後ろへ。途中に壁があれば手前で止める
    static Vec3 position(LocalPlayer p, Vec3 eye, float partial) {
        Vec3 look = Vec3.directionFromRotation(camPitch, camYaw);
        Vec3 right = Vec3.directionFromRotation(0, camYaw + 90f);
        Vec3 up = right.cross(look).normalize();
        double dist = Config.SHOULDER_DISTANCE.get();
        Entity vehicle = p.getVehicle();
        if (vehicle != null) {
            dist += Math.min(3.0, vehicle.getBbWidth() * 0.9);
        }
        Vec3 shoulder = eye.add(up.scale(UP)).add(right.scale(SIDE * sideNow));
        shoulder = clip(p, eye, shoulder);
        Vec3 back = shoulder.subtract(look.scale(dist));
        return clip(p, shoulder, back);
    }

    // from → to の途中にブロックがあれば、その手前 (0.2) まで
    private static Vec3 clip(LocalPlayer p, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0E-4) {
            return to;
        }
        Vec3 n = dir.scale(1 / len);
        double best = len;
        // 八隅を少しずらして探す (バニラのカメラの寄り方と同じ考え)
        for (int i = 0; i < 8; i++) {
            Vec3 o = new Vec3(((i & 1) * 2 - 1) * 0.1, ((i >> 1 & 1) * 2 - 1) * 0.1, ((i >> 2 & 1) * 2 - 1) * 0.1);
            BlockHitResult hit = p.level().clip(new ClipContext(from.add(o), to.add(o), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) {
                best = Math.min(best, hit.getLocation().distanceTo(from.add(o)) - 0.2);
            }
        }
        return from.add(n.scale(Math.max(0, best)));
    }

    // 照準の先: カメラから見ている向きに、ブロックか生き物に当たる所。自分とカメラの間のものは数えない
    static Vec3 aimPoint(LocalPlayer p, Vec3 cam, Vec3 look, Vec3 eye) {
        double skip = Math.max(0, eye.subtract(cam).dot(look));
        Vec3 start = cam.add(look.scale(skip + 0.3));
        Vec3 end = cam.add(look.scale(AIM_REACH));
        BlockHitResult block = p.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 far = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB box = new AABB(start, far).inflate(1.0);
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, start, far, box,
                e -> !e.isSpectator() && e.isPickable() && e != p && e != p.getVehicle() && !p.isPassengerOfSameVehicle(e), start.distanceToSqr(far));
        aimed = ent != null && ent.getEntity() instanceof LivingEntity le && le instanceof Enemy && le.isAlive() ? ent.getEntity() : null;
        return ent != null ? ent.getLocation() : far;
    }

    // Camera.setup のあと (CameraShoulderMixin): 向きと置き場を肩越しのものに
    public static void place(Camera camera, CameraAccess access, Entity entity, float partial) {
        if (!(entity instanceof LocalPlayer p)) {
            return;
        }
        Vec3 eye = p.getEyePosition(partial);
        access.skyabyss$setRotation(camYaw, camPitch);
        access.skyabyss$setPosition(position(p, eye, partial));
    }

    // 照準 (バニラは三人称で描かない)。照準の先が敵なら赤い角
    @SubscribeEvent
    static void onCrosshair(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR) || !active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = g.guiWidth() / 2;
        int y = g.guiHeight() / 2;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        g.blitSprite(CROSSHAIR, x - 7, y - 7, 15, 15);
        RenderSystem.defaultBlendFunc();
        if (aimed != null) {
            int c = 0xE0FF5A4A;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    int cx = x + sx * 7;
                    int cy = y + sy * 7;
                    g.fill(Math.min(cx, cx - sx * 3), cy, Math.max(cx, cx - sx * 3) + 1, cy + 1, c);
                    g.fill(cx, Math.min(cy, cy - sy * 3), cx + 1, Math.max(cy, cy - sy * 3) + 1, c);
                }
            }
        }
        RenderSystem.disableBlend();
    }

    // Camera の守られた手 (CameraShoulderMixin が持つ)
    public interface CameraAccess {
        void skyabyss$setRotation(float yaw, float pitch);

        void skyabyss$setPosition(Vec3 pos);
    }
}

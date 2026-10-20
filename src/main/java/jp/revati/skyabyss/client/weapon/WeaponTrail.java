package jp.revati.skyabyss.client.weapon;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

// 刃の軌跡 (スミア): 振りの間、武器の「刃の根もと」と「先」の通った跡を、光の帯 (薄い面) で数 tick だけ引く。
// 粒は使わず、描くたびに武器の位置 (描画の変換行列) から先と根もとを取って記録し、新しい順に面でつなぐ。
//  - 一人称は描画の座標 (視点の空間) のまま記録する。三人称は「描画の座標 = ワールド - カメラ」なので、ワールドに直して覚え、描くときカメラを引く
//  - 濃さは MoveSet の TRAIL (振りの打ち抜きの前後だけ上がる) と、古いほど薄くなる減衰の積。設定 weaponTrail で止められる
// 先は、メッシュの原点 (握り) から一番遠い頂点、根もとはそこへ向かって 42% の点
final class WeaponTrail {
    // 帯が残る長さ (tick)
    private static final float LIFE = 4.5f;
    // 同じ位置を細かく記録しすぎない間隔 (tick)
    private static final float STEP = 0.12f;

    private record Sample(float tx, float ty, float tz, float bx, float by, float bz, float time, float level) {
    }

    private static final Map<Integer, ArrayDeque<Sample>> HISTORY = new HashMap<>();
    private static final Map<String, float[]> TIPS = new HashMap<>();
    private static final Vector3f TIP = new Vector3f();
    private static final Vector3f BASE = new Vector3f();

    private WeaponTrail() {
    }

    private static float[] tipOf(LowPolyMesh mesh) {
        return TIPS.computeIfAbsent(mesh.name, n -> {
            float best = -1f;
            float[] tip = {0f, 1f, 0f};
            for (LowPolyMesh.Bone b : mesh.bones.values()) {
                float[] p = b.pos;
                for (int i = 0; i + 2 < p.length; i += 3) {
                    float d = p[i] * p[i] + p[i + 1] * p[i + 1] + p[i + 2] * p[i + 2];
                    if (d > best) {
                        best = d;
                        tip = new float[] {p[i], p[i + 1], p[i + 2]};
                    }
                }
            }
            return tip;
        });
    }

    // 武器の色 (巨神ごと): 帯の色
    private static int[] colour(String id) {
        if (id.contains("trident") || id.contains("harpoon") || id.contains("tidecaller") || id.contains("coral")) {
            return new int[] {150, 225, 255};
        }
        if (id.contains("glaive") || id.contains("raiju") || id.contains("thunder") || id.contains("storm")) {
            return new int[] {215, 235, 255};
        }
        if (id.contains("ember") || id.contains("smith") || id.contains("poker")) {
            return new int[] {255, 160, 70};
        }
        if (id.startsWith("abyss_")) {
            return new int[] {185, 130, 255};
        }
        return new int[] {235, 230, 210};
    }

    // 先と根もとを記録して帯を描く。pose は武器のメッシュを描くときと同じ変換 (メッシュの座標がそのまま使える)
    static void draw(PoseStack pose, MultiBufferSource buffers, LowPolyMesh mesh, String id, int key, boolean world, float level) {
        if (key == -2 || !Config.WEAPON_TRAIL.get()) {
            return;
        }
        ArrayDeque<Sample> h = HISTORY.get(key);
        if (level <= 0.04f && (h == null || h.isEmpty())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float now = mc.level.getGameTime() + SwingClock.partial();
        Vec3 cam = world ? mc.gameRenderer.getMainCamera().getPosition() : Vec3.ZERO;
        if (h == null) {
            h = new ArrayDeque<>();
            HISTORY.put(key, h);
        }
        while (!h.isEmpty() && now - h.peekFirst().time > LIFE) {
            h.pollFirst();
        }
        if (now < (h.isEmpty() ? now : h.peekLast().time)) {
            h.clear(); // 時間が戻った (ワールドの入り直し)
        }
        if (level <= 0.04f && h.isEmpty()) {
            return;
        }
        float[] tip = tipOf(mesh);
        Matrix4f m = pose.last().pose();
        m.transformPosition(tip[0], tip[1], tip[2], TIP);
        m.transformPosition(tip[0] * 0.42f, tip[1] * 0.42f, tip[2] * 0.42f, BASE);
        Sample cur = new Sample((float) (TIP.x + cam.x), (float) (TIP.y + cam.y), (float) (TIP.z + cam.z),
                (float) (BASE.x + cam.x), (float) (BASE.y + cam.y), (float) (BASE.z + cam.z), now, level);
        if (level > 0.04f && (h.isEmpty() || now - h.peekLast().time >= STEP)) {
            h.addLast(cur);
        }
        if (h.isEmpty()) {
            return;
        }
        // 帯: 古い → 新しい の順に、隣どうしを面でつなぐ。最後は今の位置につなぐ
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        int[] c = colour(id);
        Sample prev = null;
        for (Sample s : h) {
            if (prev != null) {
                quad(vc, prev, s, cam, now, c);
            }
            prev = s;
        }
        if (prev != null && level > 0.04f && prev != cur && now > prev.time) {
            quad(vc, prev, cur, cam, now, c);
        }
    }

    private static void quad(VertexConsumer vc, Sample a, Sample b, Vec3 cam, float now, int[] c) {
        int a0 = alpha(a, now);
        int a1 = alpha(b, now);
        if (a0 <= 0 && a1 <= 0) {
            return;
        }
        vc.addVertex((float) (a.tx - cam.x), (float) (a.ty - cam.y), (float) (a.tz - cam.z)).setColor(c[0], c[1], c[2], a0);
        vc.addVertex((float) (a.bx - cam.x), (float) (a.by - cam.y), (float) (a.bz - cam.z)).setColor(c[0], c[1], c[2], a0 / 3);
        vc.addVertex((float) (b.bx - cam.x), (float) (b.by - cam.y), (float) (b.bz - cam.z)).setColor(c[0], c[1], c[2], a1 / 3);
        vc.addVertex((float) (b.tx - cam.x), (float) (b.ty - cam.y), (float) (b.tz - cam.z)).setColor(c[0], c[1], c[2], a1);
    }

    private static int alpha(Sample s, float now) {
        float age = Math.max(0f, (now - s.time) / LIFE);
        float k = 1f - age;
        return Math.max(0, Math.min(255, (int) (s.level * k * k * 210f)));
    }

    static void clear() {
        HISTORY.clear();
    }
}

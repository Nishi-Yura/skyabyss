package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// 低ポリメッシュ描画の共通の入口とスイッチ (docs/design/15_high_poly.md 9 章)。
public final class LowPoly {
    // 低ポリの描画を使うか。false にすると、低ポリ対応のモブは元のキューブの geo と GearLayer に戻る (一か所で戻せる)。
    // ShotRunner の `lowpoly on|off` で実行中にも切り替えられる (比較の撮影・計測用)
    public static volatile boolean enabled = true;
    // true: 骨のぴったりの箱 (geo/entity/<name>_lowpoly_debug.geo.json) を半透明の赤で重ねる。メッシュと GeckoLib の座標が合っているかの確認用
    public static volatile boolean debug = false;

    private LowPoly() {
    }

    // 骨 1 本ぶんのメッシュを、今のポーズ (poseStack の先頭) で buffer へ流す。毎フレームは座標の変換だけ
    public static void drawBone(PoseStack poseStack, VertexConsumer buffer, LowPolyMesh.Bone bone, int packedLight, int packedOverlay, int colour) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        Vector3f t = new Vector3f();
        float[] pos = bone.pos;
        float[] uv = bone.uv;
        float[] nrm = bone.nrm;
        for (int q = 0; q < bone.quadCount; q++) {
            nm.transform(nrm[q * 3], nrm[q * 3 + 1], nrm[q * 3 + 2], t).normalize();
            float nx = t.x, ny = t.y, nz = t.z;
            for (int k = 0; k < 4; k++) {
                int pi = q * 12 + k * 3;
                m.transformPosition(pos[pi], pos[pi + 1], pos[pi + 2], t);
                buffer.addVertex(t.x, t.y, t.z, colour, uv[q * 8 + k * 2], uv[q * 8 + k * 2 + 1], packedOverlay, packedLight, nx, ny, nz);
            }
        }
    }

    public static void drawAll(PoseStack poseStack, VertexConsumer buffer, LowPolyMesh mesh, int packedLight, int packedOverlay, int colour) {
        for (LowPolyMesh.Bone b : mesh.bones.values()) {
            drawBone(poseStack, buffer, b, packedLight, packedOverlay, colour);
        }
    }
}

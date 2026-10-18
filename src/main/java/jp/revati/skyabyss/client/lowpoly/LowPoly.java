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
    // プレイヤーの武器 13 種 (手・地面・額縁) を低ポリメッシュで描くか。false にすると元のボクセルのモデル (models/item_voxel/) で描く (client/weapon/WeaponItemRenderer)。
    // 台本の `weaponmesh on|off`
    public static volatile boolean weaponMeshes = true;
    // 開発用: 武器のメッシュの階級を common|rare|legend に固定する (null = 品の階級)。台本の `weapontier <階級|off>`
    public static volatile String weaponTier = null;

    // 装飾品・補助の品 16 種 (手・地面・額縁) を低ポリメッシュで描くか。false にすると元の平絵のモデル (models/item_flat/) で描く (client/accessory/AccessoryItemRenderer)。台本の `accmesh on|off`
    public static volatile boolean accessoryMeshes = true;
    // 装飾の枠に付けた品を、プレイヤーの体に低ポリメッシュで描くか (client/accessory/AccessoryWornLayer)。台本の `accworn on|off`
    public static volatile boolean accessoryWorn = true;
    // 開発用: 装飾品のメッシュの階級を common|rare|legend に固定する (null = 品の階級)。台本の `acctier <階級|off>`
    public static volatile String accessoryTier = null;

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

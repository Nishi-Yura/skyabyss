package jp.revati.skyabyss.client.look;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

// 試作の描き方 (GearLook): 立方体の模型 (tools/gen/cube/cube_gear.py が lowpoly/look/<案>/ に書く) を、絵をそのまま貼って描く
public final class GearLookDraw {
    private GearLookDraw() {
    }

    // 今の案の立方体の模型 (無ければ null)。name は lowpoly/look/<案>/ からの名前
    public static LowPolyMesh cube(char look, String name) {
        return look == '0' ? null : LowPolyMeshes.get("look/" + look + "/" + name);
    }

    // 模型の面をそのまま描く (bone = null なら全部の骨)。光る画素は明るさ一定でもう一度重ねる
    public static void mesh(LowPolyMesh mesh, String bone, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        mesh(mesh, bone, pose, buffers, light, overlay, 0f);
    }

    // clear > 0: カメラから clear ブロック以内の面は描かない (技の形が画面をふさがない。描く空間の原点がカメラのとき)
    public static void mesh(LowPolyMesh mesh, String bone, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float clear) {
        mesh(mesh, bone, pose, buffers, light, overlay, clear, false);
    }

    // flat: 面の向きの陰を付けない (どの面も上向きの明るさ)。光そのものの技 (炎など) 用
    public static void mesh(LowPolyMesh mesh, String bone, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float clear, boolean flat) {
        draw(mesh, bone, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), light, overlay, clear, flat);
        if (mesh.glowMask != null) {
            draw(mesh, bone, pose, buffers.getBuffer(RenderType.eyes(mesh.glowMask)), LightTexture.FULL_BRIGHT, overlay, clear, flat);
        }
    }

    private static void draw(LowPolyMesh mesh, String bone, PoseStack pose, VertexConsumer buf, int light, int overlay, float clear, boolean flat) {
        PoseStack.Pose p = pose.last();
        org.joml.Vector3f c = new org.joml.Vector3f();
        for (LowPolyMesh.Bone b : mesh.bones.values()) {
            if (bone != null && !bone.equals(b.name)) {
                continue;
            }
            for (int q = 0; q < b.quadCount; q++) {
                if (clear > 0f) {
                    int o0 = q * 12;
                    c.set((b.pos[o0] + b.pos[o0 + 6]) / 2f, (b.pos[o0 + 1] + b.pos[o0 + 7]) / 2f, (b.pos[o0 + 2] + b.pos[o0 + 8]) / 2f);
                    p.pose().transformPosition(c);
                    if (c.lengthSquared() < clear * clear) {
                        continue;
                    }
                }
                float nx = flat ? 0f : b.nrm[q * 3], ny = flat ? 1f : b.nrm[q * 3 + 1], nz = flat ? 0f : b.nrm[q * 3 + 2];
                for (int k = 0; k < 4; k++) {
                    int o = q * 12 + k * 3;
                    buf.addVertex(p, b.pos[o], b.pos[o + 1], b.pos[o + 2]).setColor(-1).setUv(b.uv[q * 8 + k * 2], b.uv[q * 8 + k * 2 + 1])
                            .setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
                }
            }
        }
    }
}

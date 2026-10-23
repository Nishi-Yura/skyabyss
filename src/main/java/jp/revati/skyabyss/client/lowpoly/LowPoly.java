package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
    // 巨神の重さの見せ方 (下ほど暗い陰・関節の穴の蓋・地面や海面の影・雲や水しぶきと土ぼこり) を使うか。台本の `titanweight on|off` (前後の比べ用)
    public static volatile boolean titanWeight = true;
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

    // 描画スレッドだけが使う作業用 (drawBone のたびに作らない)
    private static final Vector3f SCRATCH = new Vector3f();

    // 元のキューブの素材 (geo/entity/<名>.geo.json・textures/entity/<名>.png) は、通常のジャーには入れていない
    // (tools/sources/geo_cube/ に出してある。戻し方は tools/dev/cube_geo.py)。LowPoly.enabled = false のときに無ければ、
    // 落ちずに低ポリのまま描く (素材ごとに一度だけログ)。リソースの再読み込みで LowPolyMeshes が clearCubeCache を呼ぶ
    private static final Map<ResourceLocation, Boolean> CUBE_PRESENT = new ConcurrentHashMap<>();

    private LowPoly() {
    }

    // キューブの素材があるか (無ければ一度だけログを出す)。描画スレッドから呼ぶ
    public static boolean cubeHere(ResourceLocation cube) {
        return CUBE_PRESENT.computeIfAbsent(cube, c -> {
            boolean ok = Minecraft.getInstance().getResourceManager().getResource(c).isPresent();
            if (!ok) {
                SkyAbyss.LOGGER.warn("[lowpoly] cube material missing, keeping the low-poly look: {} (copy it back: python tools/dev/cube_geo.py back)", c);
            }
            return ok;
        });
    }

    // キューブの素材があればそれ、無ければ低ポリの素材
    public static ResourceLocation cubeOr(ResourceLocation cube, ResourceLocation low) {
        return cubeHere(cube) ? cube : low;
    }

    public static void clearCubeCache() {
        CUBE_PRESENT.clear();
    }

    // 骨 1 本ぶんのメッシュを、今のポーズ (poseStack の先頭) で buffer へ流す。毎フレームは座標の変換だけ
    public static void drawBone(PoseStack poseStack, VertexConsumer buffer, LowPolyMesh.Bone bone, int packedLight, int packedOverlay, int colour) {
        drawBone(poseStack, buffer, bone, packedLight, packedOverlay, colour, 0f);
    }

    // flatten: 0..1。法線を真上へこの割合だけ寄せる (防具用: 絵に面の向きの陰影が焼いてあるので、腕を上げて面が下を向いても二重に暗くならないように)
    public static void drawBone(PoseStack poseStack, VertexConsumer buffer, LowPolyMesh.Bone bone, int packedLight, int packedOverlay, int colour, float flatten) {
        drawBone(poseStack, buffer, bone, packedLight, packedOverlay, colour, flatten, true);
    }

    // shade: 巨神の焼き込みの明るさ (Bone.vshade) をかけるか。光る層 (発光マスクの重ね描き) では false にして、光を暗くしない
    public static void drawBone(PoseStack poseStack, VertexConsumer buffer, LowPolyMesh.Bone bone, int packedLight, int packedOverlay, int colour, float flatten, boolean shade) {
        drawBone(poseStack, buffer, bone, packedLight, packedOverlay, colour, flatten, shade, null);
    }

    // only: quad ごとに流すか (null = 全部)。発光の重ね描きで、光る画素が掛からない quad を飛ばすのに使う
    public static void drawBone(PoseStack poseStack, VertexConsumer buffer, LowPolyMesh.Bone bone, int packedLight, int packedOverlay, int colour, float flatten, boolean shade,
                                boolean[] only) {
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        boolean weight = titanWeight;
        float[] vshade = shade && weight ? bone.vshade : null;
        int ca = colour >>> 24;
        int cr = (colour >> 16) & 255;
        int cg = (colour >> 8) & 255;
        int cb = colour & 255;
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        Vector3f t = SCRATCH;
        int[] shaded = null;
        if (vshade != null) {
            shaded = bone.shadedColours;
            if (shaded == null || bone.shadedFor != colour) {
                shaded = new int[vshade.length];
                for (int i = 0; i < shaded.length; i++) {
                    float sh = vshade[i];
                    shaded[i] = (ca << 24) | ((int) (cr * sh) << 16) | ((int) (cg * sh) << 8) | (int) (cb * sh);
                }
                bone.shadedColours = shaded;
                bone.shadedFor = colour;
            }
        }
        float[] pos = bone.pos;
        float[] uv = bone.uv;
        float[] nrm = bone.nrm;
        int quadLimit = weight ? bone.quadCount : bone.capStart;
        for (int q = 0; q < quadLimit; q++) {
            if (only != null && !only[q]) {
                continue;
            }
            nm.transform(nrm[q * 3], nrm[q * 3 + 1], nrm[q * 3 + 2], t).normalize();
            float nx = t.x, ny = t.y, nz = t.z;
            if (flatten > 0f) {
                nx *= 1f - flatten;
                ny = ny * (1f - flatten) + flatten;
                nz *= 1f - flatten;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= l;
                ny /= l;
                nz /= l;
            }
            for (int k = 0; k < 4; k++) {
                int pi = q * 12 + k * 3;
                m.transformPosition(pos[pi], pos[pi + 1], pos[pi + 2], t);
                int c = shaded != null ? shaded[q * 4 + k] : colour;
                buffer.addVertex(t.x, t.y, t.z, c, uv[q * 8 + k * 2], uv[q * 8 + k * 2 + 1], packedOverlay, packedLight, nx, ny, nz);
            }
        }
        jp.revati.skyabyss.dev.Prof.acc("client.lowpoly.drawBone", prof);
    }

    public static void drawAll(PoseStack poseStack, VertexConsumer buffer, LowPolyMesh mesh, int packedLight, int packedOverlay, int colour) {
        for (LowPolyMesh.Bone b : mesh.bones.values()) {
            drawBone(poseStack, buffer, b, packedLight, packedOverlay, colour);
        }
    }
}

package jp.revati.skyabyss.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.combat.prop.AttackProp;
import jp.revati.skyabyss.combat.prop.AttackProps;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 技の形 (combat/prop/AttackProp) の描画: 低ポリの模型 prop_<種類> を、大きさ (size) を掛けて描く。
// RISE は床から突き出る分だけ下へずらし (床の下は床に隠れる)、TOSS は進む向きへ頭を傾け、FALL の待ちの間は描かない
public class AttackPropRenderer extends LowPolyGeoRenderer<AttackProp> {
    // 突き出る深さ (ブロック、size 1 のとき)。模型の高さにおおよそ合わせる
    private static final float RISE_DEPTH = 3.6f;

    public AttackPropRenderer(EntityRendererProvider.Context ctx, String kind) {
        super(ctx, "prop_" + kind, 1f);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(AttackProp e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.hidden(partial)) {
            return;
        }
        // 燃え尽きの間 (AttackProps.afterburn) は試作 d だけが描く。ほかの見た目は元の命で消える
        int after = AttackProps.afterburn(e.kind());
        if (after > 0 && jp.revati.skyabyss.client.look.GearLook.skill != 'd' && e.tickCount + partial - e.delay() > e.life() - e.delay() - after) {
            return;
        }
        pose.pushPose();
        float s = e.size();
        float sunk = 0f;
        if (e.mode() == AttackProp.RISE) {
            sunk = (1f - e.rise(partial)) * RISE_DEPTH;
            pose.translate(0, -sunk * s, 0);
        }
        pose.scale(s, s, s);
        // 試作 (client/look/GearLook): 自分の技の形は、立方体の模型があればそれを (tools/gen/cube/cube_gear.py)
        char look = jp.revati.skyabyss.client.look.GearLook.skill;
        if (look != '0' && (jp.revati.skyabyss.client.look.GearLook.playerProp(e.kind()) || look == 'd') && drawCube(e, look, partial, pose, buffers, light, sunk)) {
            pose.popPose();
            return;
        }
        super.render(e, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    // 試作 d の攻撃の形の模型を、AttackProp ではない実体 (敵の弾・礫・棘・光の塊) に描く。描いたら true (d 以外の見た目・模型なしは false で元の絵)
    //   dir = 進む向き (模型の前 -z をそちらへ向ける。null・止まっているときは yawDeg だけ)。t = 生まれてからの tick
    public static boolean drawKindD(String kind, float t, float yawDeg, @javax.annotation.Nullable net.minecraft.world.phys.Vec3 dir, float size,
                                    PoseStack pose, MultiBufferSource buffers) {
        if (jp.revati.skyabyss.client.look.GearLook.skill != 'd') {
            return false;
        }
        jp.revati.skyabyss.client.lowpoly.LowPolyMesh cube = jp.revati.skyabyss.client.look.GearLookDraw.cube('d', kind.startsWith("area_") ? kind : "prop_" + kind);
        if (cube == null) {
            return false;
        }
        float yaw = yawDeg;
        float pitch = 0f;
        if (dir != null && dir.lengthSqr() > 1e-6) {
            yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            pitch = (float) Math.toDegrees(Math.atan2(-dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
        }
        pose.pushPose();
        pose.scale(size, size, size);
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        if (pitch != 0f) {
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
        }
        jp.revati.skyabyss.client.look.BurnDraw.fallLand = -1f;
        jp.revati.skyabyss.client.look.BurnDraw.draw(cube, pose, buffers, 2.5f, t, 0f, 0f, (x, y, z, k) -> {
        }, jp.revati.skyabyss.client.look.BurnDraw.style(kind));
        pose.popPose();
        return true;
    }

    // 試作の立方体の技 (4 回目): 現れるとき 3 tick で少し行き過ぎて大きくなり、命の後ろ 2 割で縮んで消える。
    // 世界の明るさで描き、カメラから 2.5 ブロック以内の面は描かない。床に立つ形には足もとの影と、現れたときの床の土ぼこり (床のブロックの粒) をつける
    private static final java.util.Set<Integer> DUSTED = new java.util.HashSet<>();
    private static final java.util.Map<String, net.minecraft.resources.ResourceLocation> DECALS = new java.util.HashMap<>();
    private static final java.util.Map<String, Float> DECAL_R = new java.util.HashMap<>();
    private static final net.minecraft.resources.ResourceLocation NONE = net.minecraft.resources.ResourceLocation.withDefaultNamespace("none");
    private static final net.minecraft.resources.ResourceLocation SHADOW = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/shadow.png");

    private static boolean drawCube(AttackProp e, char look, float partial, PoseStack pose, MultiBufferSource buffers, int light, float sunk) {
        jp.revati.skyabyss.client.lowpoly.LowPolyMesh cube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, "prop_" + e.kind());
        if (cube == null && look == 'd') {
            // 試作 d に無い形は a のまま
            look = 'a';
            cube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, "prop_" + e.kind());
        }
        if (cube == null) {
            return false;
        }
        float age = e.tickCount + partial - e.delay();
        int after = AttackProps.afterburn(e.kind());
        boolean burnLook = look == 'd' && after > 0;
        float life = Math.max(1, e.life() - e.delay() - (burnLook ? 0 : after));
        float u = age / life;
        // 試作 d: 燃え尽き (0..1)。燃える間は縮まず、燃え尽きの間に部位ごとに消える (BurnDraw)
        float burn = burnLook ? net.minecraft.util.Mth.clamp((age - (life - after)) / after, 0f, 1f) : 0f;
        float grow;
        if (age < 3f) {
            float t = Math.max(0f, age / 3f);
            grow = 1f + 2.2f * (float) Math.pow(t - 1f, 3) + 1.2f * (float) Math.pow(t - 1f, 2);
        } else {
            grow = burnLook || u < 0.8f ? 1f : net.minecraft.util.Mth.clamp((1f - u) / 0.2f, 0f, 1f);
        }
        if (grow <= 0.01f) {
            return true;
        }
        boolean flying = e.mode() == AttackProp.TOSS || e.mode() == AttackProp.FALL;
        if (!flying && age >= 0f && DUSTED.add(e.getId())) {
            if (DUSTED.size() > 512) {
                DUSTED.clear();
            }
            dust(e, e.size());
        }
        if (!flying) {
            pose.pushPose();
            pose.translate(0, sunk, 0);
            // 氷・雷: 形が崩れるのに合わせて影も薄れる (形のない所に影だけ残さない)
            int st = jp.revati.skyabyss.client.look.BurnDraw.style(e.kind());
            float shadeEnd = st == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FROST ? 0.75f
                    : e.kind().equals("ss_blink") ? 0.5f : 0.95f;
            float shade = burnLook && st != jp.revati.skyabyss.client.look.BurnDraw.STYLE_EMBER
                    ? net.minecraft.util.Mth.clamp((shadeEnd - burn) / 0.45f, 0f, 1f) : 1f;
            if (shade > 0f) {
                float[] spot = look == 'd' ? shadowSpot(st) : null;
                if (spot != null) {
                    // 真ん中に立つ物がない技: 影は前の物の足もと (spot = 前への距離, 半径)。半径 0 は影なし
                    pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                    if (spot[1] > 0f) {
                        shadow(pose, buffers, 0f, 0.02f, spot[0], spot[1], 0.5f * Math.min(1f, grow) * shade);
                    }
                } else {
                    shadow(pose, buffers, 0f, 0.02f, 0f, 1.0f, 0.5f * Math.min(1f, grow) * shade);
                }
            }
            pose.popPose();
        }
        pose.pushPose();
        // 技の向き (放った向き)。模型の前 (-z) を体の前へ
        pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot()));
        float pitch = e.pitch(partial);
        // 飛ぶ形 (前 = -z) を空から落とすとき (敵の火の玉・真珠・岩・墨): 頭を下へ向ける (落ちる形は上 = -z で組んで 90 度、飛ぶ形はその逆)
        if (look == 'd' && e.mode() == AttackProp.FALL && (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYFLY
                || jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FOEORB)) {
            pitch = -90f;
        }
        // 床から出る形 (上 = +y) を投げるとき (帯の撃ち出しの晶の棘): 先を進む向き (-z) へ寝かせる
        if (look == 'd' && e.mode() == AttackProp.TOSS && (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYRISE
                || jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FOESPIKE)) {
            pitch -= 90f;
        }
        if (pitch != 0f) {
            pose.translate(0, 0.25, 0);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(0, -0.25, 0);
        }
        pose.scale(grow, grow, grow);
        // 試作 d: 技は光そのものとして描く (世界の暗さに沈まず、面の向きの陰も付けない)
        int meshLight = look == 'd' ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
        if (look == 'd') {
            jp.revati.skyabyss.client.look.BurnDraw.fallLand = e.mode() == AttackProp.FALL && !jp.revati.skyabyss.client.look.GearLook.playerProp(e.kind()) ? e.landTick() : -1f;
            final float ss = e.size();
            jp.revati.skyabyss.client.look.BurnDraw.draw(cube, pose, buffers, 2.5f, e.tickCount + partial, burn, after,
                    (x, y, z, kind) -> burst(e, ss, x, y, z, kind), jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()));
        } else {
            jp.revati.skyabyss.client.look.GearLookDraw.mesh(cube, null, pose, buffers, meshLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 2.5f, false);
        }
        pose.popPose();
        // 床の絵 (割れ目など。tools/gen/pixel/pixel_extrude.py の DECALS): 4 tick で広がり、命の終わりに合わせて消える
        net.minecraft.resources.ResourceLocation decal = DECALS.computeIfAbsent(look + "/prop_" + e.kind(), key -> {
            String k = key.substring(2);
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID,
                    "textures/lowpoly/look/" + key.charAt(0) + "/" + k + "_decal.png");
            var res = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) {
                return NONE;
            }
            // 絵の幅で広さを決める (32px = 半径 1.7、64px 以上 = 3.4。128px は同じ広さで細かさ 2 倍)。PNG の幅は 16 バイト目からの 4 バイト
            try (java.io.InputStream in = res.get().open()) {
                byte[] head = in.readNBytes(24);
                int w = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
                DECAL_R.put(key, 1.7f * Math.min(64, Math.max(1, w)) / 32f);
            } catch (Exception ex) {
                DECAL_R.put(key, 1.7f);
            }
            return id;
        });
        if (decal != NONE && !flying) {
            pose.pushPose();
            pose.translate(0, sunk, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
            float r = DECAL_R.getOrDefault(look + "/prop_" + e.kind(), 1.7f) * net.minecraft.util.Mth.clamp(age / 4f, 0.2f, 1f);
            float fade = burnLook ? net.minecraft.util.Mth.clamp((1f - burn) / 0.3f, 0f, 1f)
                    : u < 0.7f ? 1f : net.minecraft.util.Mth.clamp((1f - u) / 0.3f, 0f, 1f);
            com.mojang.blaze3d.vertex.VertexConsumer buf = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(decal));
            PoseStack.Pose p = pose.last();
            int c = ((int) (fade * 255f) << 24) | 0xFFFFFF;
            if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_CLEAVE) {
                // 地割れ: 青緑から暗い石の色へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.6f * cool)), gg = (int) (255f * (1f - 0.45f * cool)), bb = (int) (255f * (1f - 0.4f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FINAL) {
                // 夕映えの跡: 白茜から暮れの紫がかった茶へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.65f * cool)), bb = (int) (255f * (1f - 0.6f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_STRIKE) {
                // 斬り跡: 生成りからセピアの茶へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.55f * cool)), bb = (int) (255f * (1f - 0.65f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_MAW) {
                // 噛み跡: 黄緑から墨紫へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.7f * cool)), bb = (int) (255f * (1f - 0.5f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_DEEPECHO) {
                // 響きの跡: 菫から瑠璃紺へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.6f * cool)), gg = (int) (255f * (1f - 0.6f * cool)), bb = (int) (255f * (1f - 0.35f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_PULL) {
                // 渦の跡: 燐光の青緑から淵の藍へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.7f * cool)), gg = (int) (255f * (1f - 0.55f * cool)), bb = (int) (255f * (1f - 0.3f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SPARK) {
                // 火花の跡: 白金から青鋼へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.5f * cool)), bb = (int) (255f * (1f - 0.35f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FORGE) {
                // 炉の跡: 黄白から煉瓦の茶へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.6f * cool)), bb = (int) (255f * (1f - 0.65f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_ASH) {
                // 灰の跡: 藤鼠から暗い灰へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.3f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.4f * cool)), gg = (int) (255f * (1f - 0.45f * cool)), bb = (int) (255f * (1f - 0.3f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_EMBERHAM) {
                // 熾の跡: 深紅から冷えた灰へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.5f * cool)), gg = (int) (255f * (1f - 0.3f * cool)), bb = (int) (255f * (1f - 0.3f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_TEMPER) {
                // 焼き戻しの跡: 麦わらから冷えた青へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.4f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.6f * cool)), gg = (int) (255f * (1f - 0.5f * cool)), bb = (int) (255f * (1f - 0.1f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_LAMP) {
                // 灯の跡: 灯の桃白から銅の茶へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.6f * cool)), bb = (int) (255f * (1f - 0.7f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_CURSE) {
                // 印の跡: 朱から煤の黒へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.6f * cool)), gg = (int) (255f * (1f - 0.7f * cool)), bb = (int) (255f * (1f - 0.7f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_CYCLE) {
                // 巡りの跡: 紅藤から銀鼠へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.5f * cool)), gg = (int) (255f * (1f - 0.4f * cool)), bb = (int) (255f * (1f - 0.45f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_EBB) {
                // 引き波の跡: 泡の白から濡れた砂の灰緑へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.48f * cool)), bb = (int) (255f * (1f - 0.55f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_WARD) {
                // 唄の跡: 水浅葱から藤鼠へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.6f * cool)), bb = (int) (255f * (1f - 0.5f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SNARE) {
                // 縛りの跡: 白から墨へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int v = (int) (255f * (1f - 0.6f * cool));
                c = ((int) (fade * 255f) << 24) | (v << 16) | (v << 8) | v;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_RAIN) {
                // 矢の跡: 銀から鉛へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.5f * cool)), bb = (int) (255f * (1f - 0.4f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_TRAP) {
                // 罠の跡: 臙脂から乾いた血の茶へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.5f * cool)), gg = (int) (255f * (1f - 0.65f * cool)), bb = (int) (255f * (1f - 0.65f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FANG) {
                // 噛み跡: 黄から黒ずんだ黄土へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.6f * cool)), bb = (int) (255f * (1f - 0.7f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_MIST) {
                // 露の跡: 乳白から乾いた灰へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.45f * cool)), bb = (int) (255f * (1f - 0.4f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BACK) {
                // 煤の跡: 弁柄から灰へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.6f * cool)), gg = (int) (255f * (1f - 0.55f * cool)), bb = (int) (255f * (1f - 0.5f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_LUNGE) {
                // 突きの溝: 牡丹から黒ずんだ葡萄色へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.7f * cool)), bb = (int) (255f * (1f - 0.55f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SPIN) {
                // 斬り跡: 翡翠から苔の緑へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.6f * cool)), gg = (int) (255f * (1f - 0.4f * cool)), bb = (int) (255f * (1f - 0.55f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BONE) {
                // 擦り溝: 菫から灰紫へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.6f * cool)), bb = (int) (255f * (1f - 0.35f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BASH) {
                // 扇のひび: 白熱から黒い焦げへ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.6f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.65f * cool)), gg = (int) (255f * (1f - 0.75f * cool)), bb = (int) (255f * (1f - 0.7f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_RUSH) {
                // 擦り跡: 金から焦げ茶へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.5f * cool)), gg = (int) (255f * (1f - 0.65f * cool)), bb = (int) (255f * (1f - 0.7f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_TIDE) {
                // 渦のぬれた跡: 乾いて暗い青へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.45f * cool)), bb = (int) (255f * (1f - 0.3f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_EARTH) {
                // 割れた輪: 琥珀から土の茶へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.6f * cool)), bb = (int) (255f * (1f - 0.7f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.frost(e.kind())) {
                // 霜のひび: 少し灰がかって薄れる
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.3f * cool)), gg = (int) (255f * (1f - 0.18f * cool)), bb = (int) (255f * (1f - 0.08f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_STAR) {
                // 空間のひび: 金から夜の藍へ
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.6f * cool)), gg = (int) (255f * (1f - 0.55f * cool)), bb = (int) (255f * (1f - 0.1f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook && jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_THUNDER) {
                // 雷のひび: クリーム色から灰紫へ冷める
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.45f * cool)), gg = (int) (255f * (1f - 0.55f * cool)), bb = (int) (255f * (1f - 0.15f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            } else if (burnLook) {
                // ひびが冷える: 黄 → 赤 → 赤茶の焦げ跡
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.85f * cool)), bb = (int) (255f * (1f - 0.9f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            }
            float[][] q = {{-r, -r, 0, 0}, {-r, r, 0, 1}, {r, r, 1, 1}, {r, -r, 1, 0}};
            for (float[] v : q) {
                buf.addVertex(p, v[0], 0.03f, v[1]).setColor(c).setUv(v[2], v[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
            }
            pose.popPose();
        }
        return true;
    }

    // 試作 d の影の位置 (模型の大きさ 1 のときの前への距離, 半径)。null = 真ん中に半径 1
    private static float[] shadowSpot(int style) {
        return switch (style) {
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_CLEAVE -> new float[] {1.16f, 0.4f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_RUSH -> new float[] {4.38f, 0.6f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_TIDE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_BASH -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_BONE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_SPIN -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_LUNGE -> new float[] {4.9f, 0.5f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_BACK -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MIST -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_FANG -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_TRAP -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_RAIN -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_SNARE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_WARD -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_EBB -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_CYCLE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_CURSE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_LAMP -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_TEMPER -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_EMBERHAM -> new float[] {0f, 0.35f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_ASH -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_FORGE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_SPARK -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_PULL -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_DEEPECHO -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MAW -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_STRIKE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_BELL -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_FINAL -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_SKHEX -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_SKKIT -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYRISE -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYFALL3 -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYFLY -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYCLOCK -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYFLOAT -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYFALL6 -> new float[] {0f, 0f};
            case jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYFALL8 -> new float[] {0f, 0f};
            default -> null;
        };
    }

    // 試作 d の燃え尽きの粒 (BurnDraw.Sink)。模型の座標 (Y 軸 180 度回し、技の大きさ倍) を世界へ。同じ所からは 1 度だけ
    private static final java.util.Set<Long> BURST = new java.util.HashSet<>();

    // 敵の攻撃の形の属性 (粒の色)
    static jp.revati.skyabyss.fx.FxParticles.Element foeElement(String kind) {
        return switch (kind) {
            case "fireball", "lava_pillar", "ash_ball", "whirlwind", "area_ember_a", "area_ember_b" -> jp.revati.skyabyss.fx.FxParticles.Element.EMBER;
            case "ice_spike", "icicle", "water_spout", "coral_spear", "shell_spike", "pearl", "area_water_a", "area_water_b", "bubble_shot", "tide_bubble" -> jp.revati.skyabyss.fx.FxParticles.Element.WATER;
            case "lightning", "crystal_spike", "area_storm_a", "area_storm_b" -> jp.revati.skyabyss.fx.FxParticles.Element.STORM;
            case "tentacle", "ink_blob", "spore", "wisp_orb" -> jp.revati.skyabyss.fx.FxParticles.Element.ABYSS;
            case "light_pillar", "feather", "weight", "wind_blade" -> jp.revati.skyabyss.fx.FxParticles.Element.MOON;
            default -> jp.revati.skyabyss.fx.FxParticles.Element.STONE;
        };
    }

    // 技の燃え尽きの粒: その技の属性の mod の粒 (決まり 59。破片・光・煙)
    private static net.minecraft.core.particles.SimpleParticleType fxp(AttackProp e, jp.revati.skyabyss.fx.FxParticles.Kind k) {
        return jp.revati.skyabyss.fx.FxParticles.type(skillElement(e.kind()), k);
    }

    // 技の属性 (名から): 熾・水・雷・深淵・月・石
    static jp.revati.skyabyss.fx.FxParticles.Element skillElement(String kind) {
        String k = kind;
        if (k.matches(".*(ember|ash|forge|fire|spark|temper|hammer|brand|melt|lava).*")) {
            return jp.revati.skyabyss.fx.FxParticles.Element.EMBER;
        }
        if (k.matches(".*(frost|tide|ebb|water|freeze|mist|whale_wave|bubble|snare).*")) {
            return jp.revati.skyabyss.fx.FxParticles.Element.WATER;
        }
        if (k.matches(".*(thunder|storm|cloud|wind|sky|pierce|bolt|feather|spin|gale).*")) {
            return jp.revati.skyabyss.fx.FxParticles.Element.STORM;
        }
        if (k.matches(".*(deep|abyss|curse|maw|echo|grave|shadow|hex|pull|silence|blink|fang|lone).*")) {
            return jp.revati.skyabyss.fx.FxParticles.Element.ABYSS;
        }
        if (k.matches(".*(moon|star|light|lamp|rewind|final|gaze|moth|promise|ward|cycle|song|balance|bell|clap|chase|eye|mastery|rune|secret).*")) {
            return jp.revati.skyabyss.fx.FxParticles.Element.MOON;
        }
        return jp.revati.skyabyss.fx.FxParticles.Element.STONE;
    }

    private static void burst(AttackProp e, float s, float x, float y, float z, int kind) {
        long key = ((long) e.getId() << 32) ^ ((long) kind << 28) ^ (Float.floatToIntBits(Math.round(x * 8f) * 31f + Math.round(z * 8f)) & 0x0FFFFFFFL);
        if (!BURST.add(key)) {
            return;
        }
        if (BURST.size() > 8192) {
            BURST.clear();
        }
        net.minecraft.world.level.Level level = e.level();
        // 模型の座標を技の向き (Y 軸 180 - yaw 度) で回す
        double ya = Math.toRadians(180f - e.getYRot());
        double wx = e.getX() + (x * Math.cos(ya) + z * Math.sin(ya)) * s, wy = e.getY() + y * s, wz = e.getZ() + (-x * Math.sin(ya) + z * Math.cos(ya)) * s;
        net.minecraft.util.RandomSource rnd = level.random;
        if (jp.revati.skyabyss.client.look.BurnDraw.foe(jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()))) {
            // 敵の攻撃の形: その属性の mod の粒 (破片は落ちて転がり、光は浮いて薄れ、煙は昇る)
            var el = foeElement(e.kind());
            var fk = kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE ? jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE
                    : kind == jp.revati.skyabyss.client.look.BurnDraw.GLINT ? jp.revati.skyabyss.fx.FxParticles.Kind.GLOW : jp.revati.skyabyss.fx.FxParticles.Kind.SHARD;
            int n = fk == jp.revati.skyabyss.fx.FxParticles.Kind.SHARD ? 3 : 1;
            for (int i = 0; i < n; i++) {
                level.addParticle(jp.revati.skyabyss.fx.FxParticles.type(el, fk), wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.12,
                        fk == jp.revati.skyabyss.fx.FxParticles.Kind.SHARD ? 0.08 + rnd.nextFloat() * 0.1 : 0.03, (rnd.nextFloat() - 0.5f) * 0.12);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_CLEAVE) {
            // 巨神斬り: 弧は END_ROD、光の幕は GLOW、大剣が沈む所は土ぼこり、岩板は深層岩の粒
            var slate = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
            switch (kind) {
                case jp.revati.skyabyss.client.look.BurnDraw.GLINT -> level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.04, 0.02 + rnd.nextFloat() * 0.03, (rnd.nextFloat() - 0.5f) * 0.04);
                case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.2, wz,
                        (rnd.nextFloat() - 0.5f) * 0.02, 0.05, (rnd.nextFloat() - 0.5f) * 0.02);
                case jp.revati.skyabyss.client.look.BurnDraw.ROCK -> {
                    for (int i = 0; i < 5; i++) {
                        level.addParticle(slate, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.3, 0.1 + rnd.nextFloat() * 0.15, (rnd.nextFloat() - 0.5f) * 0.3);
                    }
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy + 0.2, wz, 0, 0.03, 0);
                }
                default -> {
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(slate, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                    }
                }
            }
            return;
        }
        int myStyle = jp.revati.skyabyss.client.look.BurnDraw.style(e.kind());
        if (myStyle >= jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYRISE && myStyle <= jp.revati.skyabyss.client.look.BurnDraw.STYLE_MYFALL8) {
            // 神器の大きな能力: 姿が消える所は白い光、固いものが沈む所は深層岩
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0.02, 0);
            } else {
                var deep = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                level.addParticle(deep, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.1, 0.05, (rnd.nextFloat() - 0.5f) * 0.1);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SKKIT) {
            // 小さな印: 板が消える所は白い光、固いものが沈む所は凝灰岩
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0.02, 0);
            } else {
                var tuff = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                level.addParticle(tuff, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.1, 0.05, (rnd.nextFloat() - 0.5f) * 0.1);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SKHEX) {
            // 呪印: 噴き上がりが消える所は魔女の粒、固いものが沈む所は黒い石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0.02, 0);
            } else {
                var black = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                level.addParticle(black, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.1, 0.06, (rnd.nextFloat() - 0.5f) * 0.1);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_STARFALL) {
            // 降る星: 尾が縮む所は金の塵と白い光、石が沈む所は深層岩
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.86f, 0.45f), 0.6f);
                level.addParticle(dust, wx, wy, wz, 0, 0.02, 0);
                if (rnd.nextFloat() < 0.3f) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0.03, 0);
                }
            } else {
                var slate = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                level.addParticle(slate, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.1, 0.06, (rnd.nextFloat() - 0.5f) * 0.1);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FINAL) {
            // 最期の光: 幕が消える所は茜の塵と白い光、欠片が沈む所は凝灰岩
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.62f, 0.45f), 0.7f);
                level.addParticle(dust, wx, wy, wz, 0, 0.03, 0);
                if (rnd.nextFloat() < 0.4f) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0.04, 0);
                }
            } else {
                var tuff = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(tuff, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.12, 0.07, (rnd.nextFloat() - 0.5f) * 0.12);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BELL) {
            // 墓鐘: 鳴りが昇る所は白緑の塵と白い光、鐘と墓石が沈む所は緑青
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.8f, 1.0f, 0.88f), 0.6f);
                level.addParticle(dust, wx, wy, wz, 0, 0.03, 0);
                if (rnd.nextFloat() < 0.3f) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.2, wz, 0, 0.03, 0);
                }
            } else {
                var patina = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(patina, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.12, 0.07, (rnd.nextFloat() - 0.5f) * 0.12);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_STRIKE) {
            // 遺響の一撃: 剣の影が昇る所はセピアの塵と白い灰、錆びた剣が沈む所は錆
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.96f, 0.88f, 0.72f), 0.6f);
                level.addParticle(dust, wx, wy, wz, 0, 0.03, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.02, 0);
            } else {
                var rust = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(rust, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.12, 0.07, (rnd.nextFloat() - 0.5f) * 0.12);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_MAW) {
            // 淵の顎: 提灯が落ちる所は黄緑の光、歯が沈む所は黒い石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.86f, 1.0f, 0.55f), 0.7f);
                level.addParticle(dust, wx, wy, wz, 0, -0.02, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.05, -0.03, (rnd.nextFloat() - 0.5f) * 0.05);
            } else {
                var black = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(black, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.12, 0.07, (rnd.nextFloat() - 0.5f) * 0.12);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_DEEPECHO) {
            // 淵の残響: 輪が消える所は菫の塵と魔法の文字、石が沈む所は深層岩
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.78f, 0.62f, 1.0f), 0.7f);
                level.addParticle(dust, wx, wy, wz, 0, 0.02, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.2, wz, 0, -0.3, 0);
            } else {
                var slate = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(slate, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.12, 0.06, (rnd.nextFloat() - 0.5f) * 0.12);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_PULL) {
            // 淵の引き: 波頭が吸われる所は燐光の墨、岩が沈む所は玄武岩
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var glow = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.55f, 1.0f, 0.86f), 0.6f);
                level.addParticle(glow, wx, wy, wz, 0, -0.02, 0);
            } else {
                var basalt = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(basalt, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.12, 0.06, (rnd.nextFloat() - 0.5f) * 0.12);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SPARK) {
            // 火花散らし: 筋が消える所は火花と金の塵、板が沈む所は深層岩
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.93f, 0.7f), 0.6f);
                level.addParticle(dust, wx, wy, wz, 0, 0, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.2, 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
            } else {
                var steel = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(steel, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FORGE) {
            // 炉の踏み: 舌が消える所は黄白い火の粉と煙、壁が沈む所は煉瓦
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.96f, 0.66f), 0.8f);
                level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.3f, wy + rnd.nextFloat() * 0.3f, wz + (rnd.nextFloat() - 0.5f) * 0.3f, 0, 0, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.03, 0);
            } else {
                var brick = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(brick, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_ASH) {
            // 灰散らし: 旋風が消える所は藤鼠の灰、鉄滓が沈む所は黒い石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.8f, 0.76f, 0.9f), 1.0f);
                for (int i = 0; i < 2; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.6f, wy + rnd.nextFloat() * 0.5f, wz + (rnd.nextFloat() - 0.5f) * 0.6f, 0, 0, 0);
                }
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.02, 0);
            } else {
                var slag = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(slag, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_EMBERHAM) {
            // 熾の鎚: 灰が消える所は灰と熾の塵、鎚と炭が沈む所は炭
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.86f, 0.14f, 0.2f), 0.7f);
                level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.3f, wy + rnd.nextFloat() * 0.3f, wz + (rnd.nextFloat() - 0.5f) * 0.3f, 0, 0, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.02, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.03, 0);
            } else {
                var coal = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(coal, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_TEMPER) {
            // 焼き入れ: 湯気が消える所は白い湯気と焼き色の塵、刃が沈む所は鋼
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.62f, 0.44f, 0.86f), 0.8f);
                level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.3f, wy + rnd.nextFloat() * 0.3f, wz + (rnd.nextFloat() - 0.5f) * 0.3f, 0, 0, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.04, 0);
            } else {
                var steel = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(steel, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_LAMP) {
            // 灯の守り札: 炎が消える所は煙と灯の塵、行灯が落ちる所と灯籠が沈む所は銅
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.86f, 0.68f), 0.9f);
                level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.2f, wy + rnd.nextFloat() * 0.2f, wz + (rnd.nextFloat() - 0.5f) * 0.2f, 0, 0, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy + 0.1, wz, 0, 0.03, 0);
            } else {
                var cop = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(cop, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_CURSE) {
            // 呪いの印: 印と札が消える所は朱の塵と灰の煙、杭が沈む所は黒い石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.96f, 0.36f, 0.16f), 1.2f);
                for (int i = 0; i < 2; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.3f, wy + rnd.nextFloat() * 0.2f, wz + (rnd.nextFloat() - 0.5f) * 0.3f, 0, 0, 0);
                }
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.03, 0);
            } else {
                var lac = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(lac, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_CYCLE) {
            // 巡りの唄: 帯と房が消える所は桜の花びらと紅藤の塵、柱が沈む所は鉄
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.9f, 0.68f, 0.92f), 1.1f);
                level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.4f, wy, wz + (rnd.nextFloat() - 0.5f) * 0.4f, 0, 0, 0);
                if (rnd.nextFloat() < 0.6f) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, -0.02, 0);
                }
            } else {
                var iron = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(iron, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_EBB) {
            // 引き潮: 波頭が消える所は泡としぶき、藻と岩が沈む所は乾いた昆布
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var foam = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.93f, 0.99f, 0.95f), 1.3f);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(foam, wx + (rnd.nextFloat() - 0.5f) * 0.6f, wy + rnd.nextFloat() * 0.3f, wz + (rnd.nextFloat() - 0.5f) * 0.6f, 0, 0, 0);
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.15, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            } else {
                var kelp = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(kelp, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_WARD) {
            // 守りの唄: 輪が消える所は水浅葱の塵と音符、扇が沈む所は方解石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.5f, 0.92f, 0.95f), 1.2f);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.5f, wy + (rnd.nextFloat() - 0.5f) * 0.3f, wz + (rnd.nextFloat() - 0.5f) * 0.5f, 0, 0, 0);
                }
                if (rnd.nextFloat() < 0.25f) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.2, wz, 0.72 + rnd.nextFloat() * 0.06, 0, 0);
                }
            } else {
                var shell = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(shell, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SNARE || jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SNAREFLY) {
            // 雪縛り: 縄が切れる所と矢は雪、杭が沈む所は黒い石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz,
                            (rnd.nextFloat() - 0.5f) * 0.08, 0.02, (rnd.nextFloat() - 0.5f) * 0.08);
                }
            } else {
                var ink = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ink, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_RAIN || jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_RAINFALL) {
            // 空の雨: 星と刺さった所は銀の塵、沈む所は砂利
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.86f, 0.9f, 1f), 1.2f);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.7f, wy + rnd.nextFloat() * 0.3f, wz + (rnd.nextFloat() - 0.5f) * 0.7f, 0, 0, 0);
                }
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.1, wz, 0, 0.15, 0);
            } else {
                var gravel = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(gravel, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.1, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_TRAP) {
            // 骨の罠: 噛み合いと鬼火は臙脂の塵と火花、沈む所は骨のかけら
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.82f, 0.12f, 0.22f), 1.3f);
                for (int i = 0; i < 4; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.8f, wy + (rnd.nextFloat() - 0.5f) * 0.4f, wz + (rnd.nextFloat() - 0.5f) * 0.8f, 0, 0, 0);
                }
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0.1, 0);
            } else {
                var bone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(bone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.15, 0.08, (rnd.nextFloat() - 0.5f) * 0.15);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_PIERCE) {
            // 雲穿ち: 雲の輪は鴇色の塵と小さな雲、矢は骨のかけら
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.98f, 0.66f, 0.58f), 1.2f);
                for (int i = 0; i < 2; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.5f, wy + (rnd.nextFloat() - 0.5f) * 0.5f, wz + (rnd.nextFloat() - 0.5f) * 0.5f, 0, 0, 0);
                }
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, 0, 0.01, 0);
            } else {
                var bone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(bone, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.1, -0.05, (rnd.nextFloat() - 0.5f) * 0.1);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_WIND) {
            // 風裂き: 刃は萌黄の塵、葉はつつじの葉のかけら
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.7f, 0.92f, 0.3f), 1.0f);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.6f, wy + (rnd.nextFloat() - 0.5f) * 0.6f, wz, 0, 0, 0);
                }
            } else {
                var leaf = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(leaf, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.1, -0.02, (rnd.nextFloat() - 0.5f) * 0.1);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_FANG) {
            // 双牙: しぶきは黄の塵、牙が引っ込む所は黒い石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.85f, 0.15f), 1.0f);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * 0.4f, wy, wz + (rnd.nextFloat() - 0.5f) * 0.4f, 0, 0, 0);
                }
            } else {
                var coal = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 4; i++) {
                    level.addParticle(coal, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.08 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_MIST) {
            // 霧隠れ: 霧は雲、丸太は雲と木くず
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                for (int i = 0; i < 2; i++) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx + (rnd.nextFloat() - 0.5f) * s, wy, wz + (rnd.nextFloat() - 0.5f) * s,
                            0, 0.01, 0);
                }
            } else {
                var wood = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 8; i++) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx + (rnd.nextFloat() - 0.5f) * 1.2f, wy + rnd.nextFloat() * 0.5f,
                            wz + (rnd.nextFloat() - 0.5f) * 1.2f, 0, 0.03, 0);
                    level.addParticle(wood, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.25, 0.1 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.25);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BACK) {
            // 後ろ跳び: 煙は大きな煙、殻は素焼きのかけら、撒き菱は小さな煙
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx + (rnd.nextFloat() - 0.5f) * s, wy, wz + (rnd.nextFloat() - 0.5f) * s,
                            0, 0.04, 0);
                }
            } else if (kind == jp.revati.skyabyss.client.look.BurnDraw.ROCK) {
                var clay = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(clay, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            } else {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy + 0.1, wz, 0, 0.03, 0);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_LUNGE) {
            // 踏み込み: 輪は牡丹の塵、苦無が沈む所は黒い石の粒
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.4f, 0.82f), 1.2f);
                for (int i = 0; i < 4; i++) {
                    level.addParticle(dust, wx + (rnd.nextFloat() - 0.5f) * s, wy + (rnd.nextFloat() - 0.5f) * s, wz, 0, 0.02, 0);
                }
            } else {
                var stone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 4; i++) {
                    level.addParticle(stone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.08 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SPIN) {
            // 回し斬り: 輪は SWEEP_ATTACK、石柱は方解石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0, 0);
            } else {
                var calcite = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(calcite, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BONE) {
            // 鯨骨の振り: 帯と噴きは WITCH、骨は骨のブロック
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0.03, 0);
            } else {
                var bone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(bone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BASH) {
            // 盾殴り: 波は POOF、火花は LAVA、楔は黒石
            switch (kind) {
                case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.05, 0.02, (rnd.nextFloat() - 0.5f) * 0.05);
                case jp.revati.skyabyss.client.look.BurnDraw.GLINT -> level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, 0, 0, 0);
                default -> {
                    var stone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(stone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.08 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                    }
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_RUSH) {
            // 盾の踏み込み: 閃光は CRIT、残像は END_ROD、畝は土、盾は紅の焼き物の粒
            switch (kind) {
                case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.5, (rnd.nextFloat() - 0.2f) * 0.4, (rnd.nextFloat() - 0.5f) * 0.5);
                case jp.revati.skyabyss.client.look.BurnDraw.GLINT -> level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.03, 0.02, (rnd.nextFloat() - 0.5f) * 0.03);
                case jp.revati.skyabyss.client.look.BurnDraw.ROCK -> {
                    var dirt = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(dirt, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.08 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                    }
                }
                default -> {
                    var red = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(red, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.05 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                    }
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_TIDE) {
            // 鯨尾薙ぎ: しぶきと潜る所は SPLASH、潜る所には泡も
            for (int i = 0; i < (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE ? 2 : 5); i++) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx + (rnd.nextFloat() - 0.5f) * 0.4, wy + 0.1, wz + (rnd.nextFloat() - 0.5f) * 0.4,
                        (rnd.nextFloat() - 0.5f) * 0.1, 0.1 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.1);
            }
            if (kind != jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.15, wz, 0, 0.02, 0);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_EARTH) {
            // 岩呼び: 土煙は DUST_PLUME、牙が割れたら砂岩の粒
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.04, 0.04, (rnd.nextFloat() - 0.5f) * 0.04);
            } else {
                var sand = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(sand, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.05 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.frost(e.kind())) {
            // 氷: 冷気の光は雪に、砕けた氷は氷のかけらと雪に
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.3, wz, (rnd.nextFloat() - 0.5f) * 0.02, 0.04, (rnd.nextFloat() - 0.5f) * 0.02);
            } else {
                var ice = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ice, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.3, 0.08 + rnd.nextFloat() * 0.15, (rnd.nextFloat() - 0.5f) * 0.3);
                }
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.2, wz, 0, 0.03, 0);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_STAR) {
            // 星: 光の柱は花火のきらめき (FIREWORK) になって昇る。星屑の石が崩れたら黒い石の粒
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.CRUMBLE) {
                var stone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(stone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
                return;
            }
            level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx + (rnd.nextFloat() - 0.5f) * 0.3, wy, wz + (rnd.nextFloat() - 0.5f) * 0.3,
                    (rnd.nextFloat() - 0.5f) * 0.02, 0.03 + rnd.nextFloat() * 0.03, (rnd.nextFloat() - 0.5f) * 0.02);
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_THUNDER) {
            // 雷: 消える雷は電気の火花、焦げた岩は白い煙、崩れた岩は黒い石の粒
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.ROCK) {
                for (int i = 0; i < 2; i++) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.4, (rnd.nextFloat() - 0.5f) * 0.4, (rnd.nextFloat() - 0.5f) * 0.4);
                }
            } else if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy + 0.2, wz, 0, 0.03, 0);
            } else {
                var stone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(stone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        switch (kind) {
            case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> {
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy + 0.4, wz, 0, 0.04, 0);
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.02, 0.03, (rnd.nextFloat() - 0.5f) * 0.02);
            }
            case jp.revati.skyabyss.client.look.BurnDraw.ROCK -> {
                var opt = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 6; i++) {
                    level.addParticle(opt, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.3, 0.15 + rnd.nextFloat() * 0.15, (rnd.nextFloat() - 0.5f) * 0.3);
                }
                level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy + 0.1, wz, 0, 0.02, 0);
            }
            default -> {
                var stone = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                var magma = fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SHARD);
                for (int i = 0; i < 18; i++) {
                    level.addParticle(i % 3 == 0 ? magma : stone, wx, wy + 0.2, wz, (rnd.nextFloat() - 0.5f) * 0.4, 0.1 + rnd.nextFloat() * 0.2, (rnd.nextFloat() - 0.5f) * 0.4);
                }
                for (int i = 0; i < 3; i++) {
                    level.addParticle(fxp(e, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), wx, wy + 0.4, wz, (rnd.nextFloat() - 0.5f) * 0.05, 0.05, (rnd.nextFloat() - 0.5f) * 0.05);
                }
            }
        }
    }

    // 丸い柔らかい影 (バニラの影の絵) を床に一枚
    private static void shadow(PoseStack pose, MultiBufferSource buffers, float cx, float y, float cz, float r, float alpha) {
        com.mojang.blaze3d.vertex.VertexConsumer buf = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityShadow(SHADOW));
        PoseStack.Pose p = pose.last();
        int c = ((int) (net.minecraft.util.Mth.clamp(alpha, 0f, 1f) * 255f) << 24) | 0xFFFFFF;
        float[][] v = {{cx - r, cz - r, 0, 0}, {cx - r, cz + r, 0, 1}, {cx + r, cz + r, 1, 1}, {cx + r, cz - r, 1, 0}};
        for (float[] q : v) {
            buf.addVertex(p, q[0], y, q[1]).setColor(c).setUv(q[2], q[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
        }
    }

    // 足もとのブロックの粒 (バニラの壊れる粒) を、形のまわりに散らす
    private static void dust(AttackProp e, float s) {
        net.minecraft.world.level.Level level = e.level();
        net.minecraft.core.BlockPos below = e.blockPosition().below();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(below);
        if (state.isAir()) {
            below = below.below();
            state = level.getBlockState(below);
        }
        if (state.isAir()) {
            return;
        }
        var opt = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state);
        float r = 1.0f * s;
        var rnd = level.random;
        for (int i = 0; i < 16; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = 0.3 + rnd.nextDouble() * 0.7;
            level.addParticle(opt, e.getX() + Math.cos(a) * r * d, below.getY() + 1.05, e.getZ() + Math.sin(a) * r * d, Math.cos(a) * 0.08, 0.12 + rnd.nextDouble() * 0.1,
                    Math.sin(a) * 0.08);
        }
    }

    @Override
    protected void applyRotations(AttackProp e, PoseStack pose, float age, float yaw, float partial, float nativeScale) {
        super.applyRotations(e, pose, age, yaw, partial, nativeScale);
        float pitch = e.pitch(partial);
        if (pitch != 0f) {
            pose.translate(0, 0.25, 0);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(0, -0.25, 0);
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Register {
        private Register() {
        }

        @SubscribeEvent
        static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            for (var en : AttackProps.TYPES.entrySet()) {
                String kind = en.getKey();
                event.registerEntityRenderer(en.getValue().get(), ctx -> new AttackPropRenderer(ctx, kind));
            }
        }
    }
}

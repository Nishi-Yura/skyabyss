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
        if (look != '0' && jp.revati.skyabyss.client.look.GearLook.playerProp(e.kind()) && drawCube(e, look, partial, pose, buffers, light, sunk)) {
            pose.popPose();
            return;
        }
        super.render(e, yaw, partial, pose, buffers, light);
        pose.popPose();
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
        if (pitch != 0f) {
            pose.translate(0, 0.25, 0);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(0, -0.25, 0);
        }
        pose.scale(grow, grow, grow);
        // 試作 d: 技は光そのものとして描く (世界の暗さに沈まず、面の向きの陰も付けない)
        int meshLight = look == 'd' ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
        if (look == 'd') {
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
            default -> null;
        };
    }

    // 試作 d の燃え尽きの粒 (BurnDraw.Sink)。模型の座標 (Y 軸 180 度回し、技の大きさ倍) を世界へ。同じ所からは 1 度だけ
    private static final java.util.Set<Long> BURST = new java.util.HashSet<>();

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
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_CLEAVE) {
            // 巨神斬り: 弧は END_ROD、光の幕は GLOW、大剣が沈む所は土ぼこり、岩板は深層岩の粒
            var slate = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE.defaultBlockState());
            switch (kind) {
                case jp.revati.skyabyss.client.look.BurnDraw.GLINT -> level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.04, 0.02 + rnd.nextFloat() * 0.03, (rnd.nextFloat() - 0.5f) * 0.04);
                case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> level.addParticle(net.minecraft.core.particles.ParticleTypes.GLOW, wx, wy + 0.2, wz,
                        (rnd.nextFloat() - 0.5f) * 0.02, 0.05, (rnd.nextFloat() - 0.5f) * 0.02);
                case jp.revati.skyabyss.client.look.BurnDraw.ROCK -> {
                    for (int i = 0; i < 5; i++) {
                        level.addParticle(slate, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.3, 0.1 + rnd.nextFloat() * 0.15, (rnd.nextFloat() - 0.5f) * 0.3);
                    }
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.2, wz, 0, 0.03, 0);
                }
                default -> {
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(slate, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                    }
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
                var leaf = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.AZALEA_LEAVES.defaultBlockState());
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
                var coal = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.COAL_BLOCK.defaultBlockState());
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
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, wx + (rnd.nextFloat() - 0.5f) * s, wy, wz + (rnd.nextFloat() - 0.5f) * s,
                            0, 0.01, 0);
                }
            } else {
                var wood = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.SPRUCE_LOG.defaultBlockState());
                for (int i = 0; i < 8; i++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, wx + (rnd.nextFloat() - 0.5f) * 1.2f, wy + rnd.nextFloat() * 0.5f,
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
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, wx + (rnd.nextFloat() - 0.5f) * s, wy, wz + (rnd.nextFloat() - 0.5f) * s,
                            0, 0.04, 0);
                }
            } else if (kind == jp.revati.skyabyss.client.look.BurnDraw.ROCK) {
                var clay = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.TERRACOTTA.defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    level.addParticle(clay, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            } else {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, wx, wy + 0.1, wz, 0, 0.03, 0);
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
                var stone = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
                for (int i = 0; i < 4; i++) {
                    level.addParticle(stone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.08 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_SPIN) {
            // 回し斬り: 輪は SWEEP_ATTACK、石柱は方解石
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, wx, wy, wz, 0, 0, 0);
            } else {
                var calcite = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.CALCITE.defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    level.addParticle(calcite, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BONE) {
            // 鯨骨の振り: 帯と噴きは WITCH、骨は骨のブロック
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.WITCH, wx, wy, wz, 0, 0.03, 0);
            } else {
                var bone = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    level.addParticle(bone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_BASH) {
            // 盾殴り: 波は POOF、火花は LAVA、楔は黒石
            switch (kind) {
                case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> level.addParticle(net.minecraft.core.particles.ParticleTypes.POOF, wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.05, 0.02, (rnd.nextFloat() - 0.5f) * 0.05);
                case jp.revati.skyabyss.client.look.BurnDraw.GLINT -> level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, wx, wy, wz, 0, 0, 0);
                default -> {
                    var stone = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
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
                case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> level.addParticle(net.minecraft.core.particles.ParticleTypes.CRIT, wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.5, (rnd.nextFloat() - 0.2f) * 0.4, (rnd.nextFloat() - 0.5f) * 0.5);
                case jp.revati.skyabyss.client.look.BurnDraw.GLINT -> level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, wx, wy, wz,
                        (rnd.nextFloat() - 0.5f) * 0.03, 0.02, (rnd.nextFloat() - 0.5f) * 0.03);
                case jp.revati.skyabyss.client.look.BurnDraw.ROCK -> {
                    var dirt = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(dirt, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.08 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                    }
                }
                default -> {
                    var red = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.RED_TERRACOTTA.defaultBlockState());
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
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, wx + (rnd.nextFloat() - 0.5f) * 0.4, wy + 0.1, wz + (rnd.nextFloat() - 0.5f) * 0.4,
                        (rnd.nextFloat() - 0.5f) * 0.1, 0.1 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.1);
            }
            if (kind != jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP, wx, wy + 0.15, wz, 0, 0.02, 0);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_EARTH) {
            // 岩呼び: 土煙は DUST_PLUME、牙が割れたら砂岩の粒
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.DUST_PLUME, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.04, 0.04, (rnd.nextFloat() - 0.5f) * 0.04);
            } else {
                var sand = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.SANDSTONE.defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    level.addParticle(sand, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.05 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.frost(e.kind())) {
            // 氷: 冷気の光は雪に、砕けた氷は氷のかけらと雪に
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, wx, wy + 0.3, wz, (rnd.nextFloat() - 0.5f) * 0.02, 0.04, (rnd.nextFloat() - 0.5f) * 0.02);
            } else {
                var ice = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState());
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ice, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.3, 0.08 + rnd.nextFloat() * 0.15, (rnd.nextFloat() - 0.5f) * 0.3);
                }
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, wx, wy + 0.2, wz, 0, 0.03, 0);
            }
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_STAR) {
            // 星: 光の柱は花火のきらめき (FIREWORK) になって昇る。星屑の石が崩れたら黒い石の粒
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.CRUMBLE) {
                var stone = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    level.addParticle(stone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
                return;
            }
            level.addParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK, wx + (rnd.nextFloat() - 0.5f) * 0.3, wy, wz + (rnd.nextFloat() - 0.5f) * 0.3,
                    (rnd.nextFloat() - 0.5f) * 0.02, 0.03 + rnd.nextFloat() * 0.03, (rnd.nextFloat() - 0.5f) * 0.02);
            return;
        }
        if (jp.revati.skyabyss.client.look.BurnDraw.style(e.kind()) == jp.revati.skyabyss.client.look.BurnDraw.STYLE_THUNDER) {
            // 雷: 消える雷は電気の火花、焦げた岩は白い煙、崩れた岩は黒い石の粒
            if (kind == jp.revati.skyabyss.client.look.BurnDraw.ROCK) {
                for (int i = 0; i < 2; i++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, wx, wy, wz, (rnd.nextFloat() - 0.5f) * 0.4, (rnd.nextFloat() - 0.5f) * 0.4, (rnd.nextFloat() - 0.5f) * 0.4);
                }
            } else if (kind == jp.revati.skyabyss.client.look.BurnDraw.SMOKE) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.2, wz, 0, 0.03, 0);
            } else {
                var stone = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
                for (int i = 0; i < 3; i++) {
                    level.addParticle(stone, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.2, 0.06 + rnd.nextFloat() * 0.1, (rnd.nextFloat() - 0.5f) * 0.2);
                }
            }
            return;
        }
        switch (kind) {
            case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.4, wz, 0, 0.04, 0);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.02, 0.03, (rnd.nextFloat() - 0.5f) * 0.02);
            }
            case jp.revati.skyabyss.client.look.BurnDraw.ROCK -> {
                var opt = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
                for (int i = 0; i < 6; i++) {
                    level.addParticle(opt, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.3, 0.15 + rnd.nextFloat() * 0.15, (rnd.nextFloat() - 0.5f) * 0.3);
                }
                level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.1, wz, 0, 0.02, 0);
            }
            default -> {
                var stone = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
                var magma = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState());
                for (int i = 0; i < 18; i++) {
                    level.addParticle(i % 3 == 0 ? magma : stone, wx, wy + 0.2, wz, (rnd.nextFloat() - 0.5f) * 0.4, 0.1 + rnd.nextFloat() * 0.2, (rnd.nextFloat() - 0.5f) * 0.4);
                }
                for (int i = 0; i < 3; i++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.4, wz, (rnd.nextFloat() - 0.5f) * 0.05, 0.05, (rnd.nextFloat() - 0.5f) * 0.05);
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

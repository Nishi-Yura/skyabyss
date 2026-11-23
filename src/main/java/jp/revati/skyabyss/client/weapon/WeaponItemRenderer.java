package jp.revati.skyabyss.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// プレイヤーの武器 13 種を、手に持つ・地面・額縁・頭で低ポリメッシュとして描く (docs/design/15 の 4.6.2)。
// 品の item モデル (models/item/<id>.json) は base が builtin/entity (display は持ち方の表のまま)、gui は 32x32 の絵 (perspectives)。
// base のときだけここが呼ばれる (IClientItemExtensions#getCustomRenderer)。メッシュは階級 (common / rare / legend) で選び、
// アトラスと光る画像は強化 (+0〜4 / +5〜9 / +10) で選ぶ。光る画素は entityTranslucentEmissive でもう一度描く。
// メッシュの無い品だけ、元のボクセルのモデル (models/item_voxel/<名>.json) を同じ置き方で描く。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WeaponItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final String[] VOXEL_MODELS = {"whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon",
            "tidecaller_claw", "coral_shield", "thunder_wing_bow", "thunder_wing_bow_pulling_0", "thunder_wing_bow_pulling_1", "thunder_wing_bow_pulling_2",
            "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker"};
    private static final int WHITE = 0xFFFFFFFF;

    private static WeaponItemRenderer instance;
    // 三人称で今描いている生き物 (弓の引き絵を決めるため)。RenderLivingEvent の Pre/Post の間だけ入る。一人称は自分
    private static LivingEntity holder;
    // (アトラス + 強化) → [アトラス, 光る画像 (無ければ null)]
    private static final Map<String, ResourceLocation[]> VARIANTS = new HashMap<>();

    private WeaponItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static synchronized WeaponItemRenderer get() {
        if (instance == null) {
            instance = new WeaponItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // 見た目替え (endgame/Looks): 形は見た目の武器、鍛えた光と輝きは元の品のまま
        net.minecraft.world.item.Item lookItem = jp.revati.skyabyss.endgame.Looks.look(stack);
        ItemStack shown = lookItem == null ? stack : new ItemStack(lookItem);
        String id = BuiltInRegistries.ITEM.getKey(shown.getItem()).getPath();
        int stage = pullStage(stack, context);
        String tier = tierName(shown, id);
        LowPolyMesh mesh = LowPolyMeshes.get("items/" + id + "/" + tier + (stage >= 0 ? "_pull" + stage : ""));
        if (mesh != null) {
            cubeStage = stage >= 0 ? "_pull" + stage : "";
            renderMesh(mesh, id, tier, level(stack), stack.hasFoil(), mythic(stack), context, pose, buffers, light, overlay);
            return;
        }
        renderVoxel(id, stage, shown, pose, buffers, light, overlay);
    }

    // ---------------------------------------------------------------- メッシュ

    // 試作 (client/look/GearLook): 弓の引きの段 ("_pull0" など。引いていなければ "")
    private static String cubeStage = "";

    // 今の軌跡の濃さと光の強まり ([0] = 軌跡、[1] = 光)。手 (一人称) は WeaponClient が入れた値、三人称は持っている人の今の動き、それ以外 (地面・額縁・画面) は 0
    private static float[] effects(ItemDisplayContext context) {
        if (context.firstPerson()) {
            return new float[] {WeaponFeel.fpTrail, WeaponFeel.fpGlow};
        }
        if (holder != null && (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            SwingClock.Active a = SwingClock.current(holder, SwingClock.partial());
            if (a != null) {
                return new float[] {a.step().get(jp.revati.skyabyss.weapon.MoveSet.TRAIL, a.t(), 0f) * a.w(),
                        a.step().get(jp.revati.skyabyss.weapon.MoveSet.GLOW, a.t(), 0f) * a.w()};
            }
        }
        return new float[] {0f, 0f};
    }

    // 神器 (七段目) か。形の段は伝説と同じなので、光の脈で見分ける (03 の 1.2。2026-11-02 の監査)
    static boolean mythic(ItemStack stack) {
        return stack.getItem() instanceof ModGear g && g.tier() == GearTier.MYTHIC;
    }


    private static void renderMesh(LowPolyMesh mesh, String id, String tier, int level, boolean foil, boolean mythic, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation[] tex = variant(mesh.texture, level);
        pose.pushPose();
        // 元のボクセルは盾以外、全部の要素を z 軸まわりに -45 度 (中心 8,8,8) 傾けていて、display (持ち方) はその傾きを前提にしている。メッシュは刃を立てて作ってあるので、同じだけ傾ける
        if (!id.equals("coral_shield") && !id.equals("travel_shield")) {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45f));
            pose.translate(-0.5f, -0.5f, -0.5f);
        }
        // item モデルの原点は block の角 (この時点で -0.5 ずみ)。メッシュは x = 0, z = 0 が中心で、LowPolyMesh は geo (GeckoLib) 向けに x を反転して焼くので、ここで戻す
        pose.translate(0.5f, 0f, 0.5f);
        pose.scale(-1f, 1f, 1f);
        // 刃の向きが逆に作ってあるメッシュは、柄の軸で半回転 (2026-10-29 の試遊「短剣や斧、持ち方逆」: 一人称で刃が後ろを向いていた)。
        // 鎚はどれも打つ面を +x に作ってあり、持ち方を借りた戦斧 (刃が -x) と逆なので、まとめて半回転 (2026-11-01「ハンマー、全体的に前後ろ逆かも」)
        // 手で組んだ模型 (look a) は種類で決める: 片刃の武器は刃・打つ面を +x に作る決まり (docs/design/60 の 1.2) なので、いつも半回転
        char look = jp.revati.skyabyss.client.look.GearLook.weapon;
        boolean hasCube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, id) != null;
        if (hasCube ? lookFlip(id) : (EDGE_FLIP.contains(id) || faceFlip(id))) {
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        }
        // 短剣・爪・火かき棒は小さく見えるので、階級が上がるほど大きくする (握りの位置 = メッシュの原点を中心に)
        float grow = daggerGrow(id, tier);
        // 一人称の短剣・爪は手に隠れるので、手元だけ一回り大きく見せる
        if (context.firstPerson()) {
            WeaponKind k = WeaponKind.of(id);
            if (k == WeaponKind.FANG || k == WeaponKind.CLAW) {
                grow *= 1.18f;
            }
        }
        if (grow != 1f) {
            pose.scale(grow, grow, grow);
        }
        // 輝き (foil: エンチャント・品の foil): 本体のメッシュを glint の描画型へもう一度流す (バニラの item と同じ getFoilBuffer。UV はアトラスのまま)
        // 点の模型 (client/splat)。強化の段の絵で塗る。光る画素は光る点になる
        LowPolyMesh cube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, id + cubeStage);
        if (cube == null && !cubeStage.isEmpty()) {
            cube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, id);
        }
        if (cube != null) {
            // 試作: 立方体の模型 (client/look/GearLook)。一人称もそのままの大きさ (面のまま描くと点の模型より手前に大きく見える)
            // 弓は弓腕の端から端まで 1.8 ブロックあり、一人称ではバニラの弓の倍に見えるので、握りを中心に縮める (2026-11-18「弓近すぎない？」。引いている間も同じ大きさ)
            if (context.firstPerson() && id.endsWith("bow")) {
                pose.translate(-0.375f, 0.47f, 0f);   // 握り (x32 の 12, 15。メッシュは x を反転して焼いてある)
                pose.scale(0.62f, 0.62f, 0.62f);
                pose.translate(0.375f, -0.47f, 0f);
            }
            // 手は原点より柄の向きに 1/4 ブロック上を握る (2026-11-18 の撮影で 6 品とも、手が鍔・輪の高さにあった)。
            // 立方体の模型は握りの真ん中を原点に作るので、その分だけ模型を上げて、握りを手に合わせる (三人称だけ。一人称の置き場所は前のままで合う)
            if (!context.firstPerson()) {
                // 弓は握りが原点より上 (弦の真ん中の高さ) にあるので上げない (撮って手の高さに合うのを確かめた)。
                // 弓の腹の向き (模型の +x。メッシュは x を反転して焼いてある) へ前に出す (2026-11-18「弓のみもう少し前」)
                if (id.endsWith("bow")) {
                    pose.translate(-0.2f, 0f, 0f);
                    // 引いていない間は、握りを中心に弓を斜めに倒して持つ (2026-11-18「弓の向き下なんじゃない？」→ 4 案から B)
                    if (cubeStage.isEmpty()) {
                        pose.translate(-0.375f, 0.47f, 0f);
                        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(BOW_REST_TILT));
                        pose.translate(0.375f, -0.47f, 0f);
                    }
                } else {
                    pose.translate(0f, 0.25f, 0f);
                }
            }
            jp.revati.skyabyss.client.look.GearLookDraw.mesh(cube, null, pose, buffers, light, overlay);
        } else {
            jp.revati.skyabyss.client.splat.SplatDraw.mesh(mesh, tex[0], null, pose.last().pose(), buffers, light, overlay, WHITE);
        }
        // 動きの間だけ: 刃の軌跡の帯を引く (TRAIL)
        float[] fx = effects(context);
        WeaponTrail.draw(pose, buffers, mesh, id, context.firstPerson() ? -1 : holder == null ? -2 : holder.getId(), !context.firstPerson(), fx[0]);
        pose.popPose();
    }

    // 三人称で引いていない弓を倒す角度 (度)。上の端が後ろ上、下の端が前下へ斜めに倒れる (2026-11-18 に 4 案を撮って B を選んだ)
    static final float BOW_REST_TILT = 65f;

    // 刃を +x に作ってある品 (祠の短剣・採取の斧と、野の斧 5 本。2026-11-01 にメッシュの刃の側を見て足した。天割りの両刃斧は両側に刃があるので入れない)
    static final java.util.Set<String> EDGE_FLIP = java.util.Set.of("shrine_dagger",
            "wild_tusk_axe", "wild_conductor_axe", "wild_snowsplitter_axe", "wild_blasting_axe", "wild_molten_axe");

    // 片刃の武器 (刃・打つ面が片側だけにある)。名前で見分ける (tools/gen/pixel/gear_manifest.py の種類と同じ語)
    // look a: swung weapons (sword, greatsword, dagger, fang, claw, hammer, axe, scythe) are built with the edge / striking face at +x
    // (docs/design/60 1.2) and get the half turn. Until 2026-11-21 the swords were left out, so one-edged swords and the key sword
    // showed their back edge forward. Staffs, bell staffs, poles and bows use other holds where +x already faces forward: no turn.
    static boolean lookFlip(String id) {
        return singleEdge(id) || id.contains("sword") || id.contains("blade");
    }

    static boolean singleEdge(String id) {
        for (String w : new String[] {"dagger", "knife", "fang", "claw", "sickle", "axe", "scythe", "hammer", "maul", "mace", "anchor"}) {
            if (id.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // 打つ面が +x の鎚 (武器の種類が鎚のもの。採集の道具は除く)
    static boolean faceFlip(String id) {
        return WeaponKind.of(id) == WeaponKind.HAMMER && !id.startsWith("gather_");
    }

    private static float daggerGrow(String id, String tier) {
        boolean dagger = id.equals("waterway_fang") || id.equals("raiju_claw") || id.equals("tidecaller_claw");
        if (!dagger && !id.equals("thrall_poker")) {
            return 1f;
        }
        return tier.equals("legend") ? 1.12f : tier.equals("rare") ? 1.06f : 1f;
    }

    // 強化のアトラスと光る画像。メッシュの texture は textures/lowpoly/items/<id>/<段>[_pullN]_atlas.png で、強化ぶんは _atlas の前に _plus5 / _plus10 が入る
    private static ResourceLocation[] variant(ResourceLocation base, int level) {
        String suffix = level >= 10 ? "_plus10" : level >= 5 ? "_plus5" : "";
        String key = base + suffix;
        ResourceLocation[] v = VARIANTS.get(key);
        if (v == null) {
            String path = base.getPath();
            ResourceLocation atlas = suffix.isEmpty() ? base : base.withPath(path.replace("_atlas.png", suffix + "_atlas.png"));
            ResourceLocation glow = atlas.withPath(atlas.getPath().replace(".png", "_glow.png"));
            boolean hasGlow = Minecraft.getInstance().getResourceManager().getResource(glow).isPresent();
            v = new ResourceLocation[] {atlas, hasGlow ? glow : null};
            VARIANTS.put(key, v);
        }
        return v;
    }

    private static int level(ItemStack stack) {
        return ModGear.enhance(stack);
    }

    // メッシュの段 = 品の階級。台本の weapontier で固定していても、その段のメッシュが無ければ (品の階級は一つに決まり、ほかの段は消した) 品の階級
    private static String tierName(ItemStack stack, String id) {
        String forced = LowPoly.weaponTier;
        if (forced != null && LowPolyMeshes.get("items/" + id + "/" + forced) != null) {
            return forced;
        }
        GearTier tier = stack.getItem() instanceof ModGear g ? g.tier() : GearTier.COMMON;
        return tier.meshTier;
    }

    // 弓の引き絵の段 (-1 = 引いていない)。バニラの pulling / pull の述語と同じ: 引いている間は 0、pull 0.65 から 1、0.9 から 2
    private static int pullStage(ItemStack stack, ItemDisplayContext context) {
        if (!(stack.getItem() instanceof BowItem)) {
            return -1;
        }
        LivingEntity e = context.firstPerson() ? Minecraft.getInstance().player : holder;
        if (e == null || !e.isUsingItem() || !e.getUseItem().is(stack.getItem())) {
            return -1;
        }
        float pull = jp.revati.skyabyss.combat.BowDraw.pull(stack.getUseDuration(e) - e.getUseItemRemainingTicks());
        return pull >= 0.9f ? 2 : pull >= 0.65f ? 1 : 0;
    }

    // ---------------------------------------------------------------- 元のボクセル (戻り先)

    private static void renderVoxel(String id, int stage, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        String name = stage >= 0 ? id + "_pulling_" + stage : id;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(voxelId(name));
        RenderType type = ItemBlockRenderTypes.getRenderType(stack, true);
        VertexConsumer consumer = ItemRenderer.getFoilBuffer(buffers, type, true, stack.hasFoil());
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, stack, light, overlay, pose, consumer);
    }

    private static ModelResourceLocation voxelId(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "item_voxel/" + name));
    }

    @SubscribeEvent
    static void registerVoxelModels(ModelEvent.RegisterAdditional event) {
        for (String name : VOXEL_MODELS) {
            event.register(voxelId(name));
        }
    }

    @SubscribeEvent
    static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> VARIANTS.clear());
    }

    // ---------------------------------------------------------------- 誰が持っているか

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        holder = event.getEntity();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onLivingPost(RenderLivingEvent.Post<?, ?> event) {
        holder = null;
    }

    // 台本用: `weapontier legend` など
    public static void setTier(String name) {
        LowPoly.weaponTier = name == null || name.equalsIgnoreCase("off") ? null : name.toLowerCase(Locale.ROOT);
    }
}

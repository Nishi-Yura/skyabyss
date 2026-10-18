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
import jp.revati.skyabyss.item.storm.ThunderWingBowItem;
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
// `LowPoly.weaponMeshes = false` なら元のボクセルのモデル (models/item_voxel/<名>.json) を同じ置き方で描く。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WeaponItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final String[] VOXEL_MODELS = {"whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon",
            "tidecaller_claw", "coral_shield", "thunder_wing_bow", "thunder_wing_bow_pulling_0", "thunder_wing_bow_pulling_1", "thunder_wing_bow_pulling_2",
            "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker"};
    private static final int WHITE = 0xFFFFFFFF;
    private static final int FULL_BRIGHT = 0xF000F0;

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
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int stage = pullStage(stack, context);
        if (LowPoly.weaponMeshes) {
            LowPolyMesh mesh = LowPolyMeshes.get("items/" + id + "/" + tierName(stack) + (stage >= 0 ? "_pull" + stage : ""));
            if (mesh != null) {
                renderMesh(mesh, id, level(stack), pose, buffers, light, overlay);
                return;
            }
        }
        renderVoxel(id, stage, stack, pose, buffers, light, overlay);
    }

    // ---------------------------------------------------------------- メッシュ

    private static void renderMesh(LowPolyMesh mesh, String id, int level, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation[] tex = variant(mesh.texture, level);
        pose.pushPose();
        // 元のボクセルは盾以外、全部の要素を z 軸まわりに -45 度 (中心 8,8,8) 傾けていて、display (持ち方) はその傾きを前提にしている。メッシュは刃を立てて作ってあるので、同じだけ傾ける
        if (!id.equals("coral_shield")) {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45f));
            pose.translate(-0.5f, -0.5f, -0.5f);
        }
        // item モデルの原点は block の角 (この時点で -0.5 ずみ)。メッシュは x = 0, z = 0 が中心で、LowPolyMesh は geo (GeckoLib) 向けに x を反転して焼くので、ここで戻す
        pose.translate(0.5f, 0f, 0.5f);
        pose.scale(-1f, 1f, 1f);
        LowPoly.drawAll(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex[0])), mesh, light, overlay, WHITE);
        if (tex[1] != null) {
            LowPoly.drawAll(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(tex[1])), mesh, FULL_BRIGHT, overlay, WHITE);
        }
        pose.popPose();
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

    private static String tierName(ItemStack stack) {
        String forced = LowPoly.weaponTier;
        if (forced != null) {
            return forced;
        }
        GearTier tier = stack.getItem() instanceof ModGear g ? g.tier() : GearTier.COMMON;
        return tier.ordinal() >= GearTier.LEGENDARY.ordinal() ? "legend" : tier == GearTier.RARE ? "rare" : "common";
    }

    // 弓の引き絵の段 (-1 = 引いていない)。バニラの pulling / pull の述語と同じ: 引いている間は 0、pull 0.65 から 1、0.9 から 2
    private static int pullStage(ItemStack stack, ItemDisplayContext context) {
        if (!(stack.getItem() instanceof ThunderWingBowItem)) {
            return -1;
        }
        LivingEntity e = context.firstPerson() ? Minecraft.getInstance().player : holder;
        if (e == null || !e.isUsingItem() || e.getUseItem() != stack) {
            return -1;
        }
        float pull = (stack.getUseDuration(e) - e.getUseItemRemainingTicks()) / 20f;
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

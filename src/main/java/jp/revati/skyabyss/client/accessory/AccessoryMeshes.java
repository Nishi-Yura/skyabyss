package jp.revati.skyabyss.client.accessory;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

// 装飾品・補助の品 16 種の低ポリメッシュの選び方と描き方 (手・地面・額縁の AccessoryItemRenderer と、体に付けた姿の AccessoryWornLayer が共有)。
// メッシュ: lowpoly/items/<id>/<階級>.json (階級 = common / rare / legend。品の GearTier か、台本の固定)。
// 装飾品は鍛えないので、絵は階級ごとに 1 枚 (+ 光る画素の画像)。
public final class AccessoryMeshes {
    // 低ポリにした品 (models/item/<id>.json が builtin/entity)。tools/install/install_lowpoly_accessories.py の ITEMS と同じ
    public static final Set<String> IDS = withWild(Set.of("storm_charm", "storm_anklet", "storm_eye_feather",
            "kindling_charm", "ember_mantle", "ember_lantern", "ember_horn", "cinder_lamp",
            "resonance_charm", "moss_talisman", "stone_whale_plating",
            "tide_chime_charm", "moontide_pearl", "tide_watching_shell",
            "abyss_pulse_listener", "abyss_lantern_charm", "abyss_surveyor_plumb"));

    // 野の装備の装飾の枠の品 (item/wild/WildGearTable.ACCESSORIES。docs/design/51 の 6 章)
    private static Set<String> withWild(Set<String> base) {
        java.util.Set<String> all = new java.util.HashSet<>(base);
        for (jp.revati.skyabyss.item.wild.WildGearTypes.Accessory x : jp.revati.skyabyss.item.wild.WildGearTable.ACCESSORIES) {
            all.add(x.id());
        }
        return Set.copyOf(all);
    }
    public static final int WHITE = 0xFFFFFFFF;
    public static final int FULL_BRIGHT = 0xF000F0;

    // メッシュのアトラス → [アトラス, 光る画像 (無ければ null)]
    private static final Map<ResourceLocation, ResourceLocation[]> VARIANTS = new HashMap<>();

    private AccessoryMeshes() {
    }

    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    public static String tierName(ItemStack stack) {
        String forced = LowPoly.accessoryTier;
        if (forced != null) {
            return forced;
        }
        GearTier tier = stack.getItem() instanceof ModGear g ? g.tier() : GearTier.COMMON;
        return tier.meshTier;
    }

    public static LowPolyMesh mesh(ItemStack stack) {
        return LowPolyMeshes.get("items/" + idOf(stack) + "/" + tierName(stack));
    }

    // 今の座標系 (描く点が原点、ブロック単位) のまま、メッシュを流す。メッシュは x 反転で焼いてある (GeckoLib 向け) ので、向きの決め方は呼ぶ側で
    public static void draw(LowPolyMesh mesh, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation[] tex = variant(mesh.texture);
        LowPoly.drawAll(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex[0])), mesh, light, overlay, WHITE);
        if (tex[1] != null) {
            LowPoly.drawAll(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(tex[1])), mesh, FULL_BRIGHT, overlay, WHITE);
        }
    }

    private static ResourceLocation[] variant(ResourceLocation atlas) {
        ResourceLocation[] v = VARIANTS.get(atlas);
        if (v == null) {
            ResourceLocation glow = atlas.withPath(atlas.getPath().replace(".png", "_glow.png"));
            boolean hasGlow = Minecraft.getInstance().getResourceManager().getResource(glow).isPresent();
            v = new ResourceLocation[] {atlas, hasGlow ? glow : null};
            VARIANTS.put(atlas, v);
        }
        return v;
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Reload {
        @SubscribeEvent
        static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> VARIANTS.clear());
        }
    }
}

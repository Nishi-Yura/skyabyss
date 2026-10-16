package jp.revati.skyabyss.registry;

import java.util.EnumMap;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// 防具の素材
public final class ModArmorMaterials {
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);

    // 岩鯨の甲殻: 防御・強靭はダイヤと同じ (20 / 1 部位 2)。ノックバックに強い。骨のブロックで直せる
    // 2026-10-17: 強靭 2.5 → 2.0。一式の耐性 I が個性で、素の硬さはダイヤにそろえた (03 の 9章)
    public static final Holder<ArmorMaterial> STONE_WHALE = MATERIALS.register("stone_whale", () -> new ArmorMaterial(
            new EnumMap<>(java.util.Map.of(
                    ArmorItem.Type.HELMET, 3,
                    ArmorItem.Type.CHESTPLATE, 8,
                    ArmorItem.Type.LEGGINGS, 6,
                    ArmorItem.Type.BOOTS, 3,
                    ArmorItem.Type.BODY, 8)),
            12,
            SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(Items.BONE_BLOCK),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "stone_whale"))),
            2.0f,
            0.1f));

    private ModArmorMaterials() {
    }

    public static void register(IEventBus modEventBus) {
        MATERIALS.register(modEventBus);
    }
}

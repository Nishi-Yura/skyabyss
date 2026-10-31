package jp.revati.skyabyss.loot.box;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 封の箱の品の登録 (docs/design/52 の 2 章)。BoxKind.ALL の種類ごとに一つ
public final class LootBoxSetup {
    private LootBoxSetup() {
    }

    // 重なる数 (同じ種類・同じ帯)
    public static final int STACK = 16;

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    public static final Map<String, DeferredItem<Item>> BOX_ITEMS = new LinkedHashMap<>();

    public static void register(IEventBus bus) {
        for (BoxKind k : BoxKind.ALL) {
            BOX_ITEMS.put(k.id(), ITEMS.register(k.id(), () -> new SealedBoxItem(new Item.Properties().stacksTo(STACK)
                    .rarity(k.tier().atLeast(jp.revati.skyabyss.item.GearTier.HEROIC) ? Rarity.EPIC : Rarity.UNCOMMON), k)));
        }
        ITEMS.register(bus);
    }

    @Nullable
    public static Item item(BoxKind kind) {
        DeferredItem<Item> d = BOX_ITEMS.get(kind.id());
        return d == null ? null : d.get();
    }
}

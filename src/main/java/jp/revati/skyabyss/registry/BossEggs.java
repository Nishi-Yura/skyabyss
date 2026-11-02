package jp.revati.skyabyss.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 舞台の主のスポーンエッグ (2026-11-02 の全体監査: 敵 110 型のうち、舞台でしか湧かない 11 体だけ卵が無かった)。
// 心臓・中ボス・番人・塔の主は自分の登録の所で卵を作っていないので、ここでまとめて作る。型は使うときに引く (登録の順に寄らない)。
// 創造の欄は CreativeTabs が SpawnEggItem をまとめて「敵の卵」に並べる
public final class BossEggs {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    public static final Map<String, DeferredItem<? extends Item>> EGGS = new LinkedHashMap<>();

    static {
        egg("tide_beckoner", 0x2F8F8A, 0xE8F4EE);
        egg("tide_heart", 0x1F6F7A, 0xBFEAF0);
        egg("raiju", 0x7A6AA8, 0xF2E66A);
        egg("thunder_wing_heart", 0x3E4A78, 0xF2E66A);
        egg("ash_smith", 0x4A3A34, 0xF08A3A);
        egg("ember_heart", 0x6A2A1A, 0xFFB347);
        egg("heart_warden", 0x1C1A24, 0x8CB4FF);
        egg("tower_master", 0xF2E8D0, 0x3A2A58);
        egg("tower_eye", 0xE8E0C8, 0xC9A23C);
        egg("tower_eye_big", 0xE8E0C8, 0x8C6A1C);
        egg("tower_moon", 0xF8F4E8, 0xD9B84A);
    }

    private BossEggs() {
    }

    @SuppressWarnings("unchecked")
    private static void egg(String id, int base, int spot) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id);
        EGGS.put(id, ITEMS.register(id + "_spawn_egg",
                () -> new DeferredSpawnEggItem(() -> (EntityType<? extends Mob>) BuiltInRegistries.ENTITY_TYPE.get(key), base, spot, new Item.Properties())));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

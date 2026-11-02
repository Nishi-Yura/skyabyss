package jp.revati.skyabyss.dungeon;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.wild.WildAccessoryItem;
import jp.revati.skyabyss.item.wild.WildEffect;
import jp.revati.skyabyss.item.wild.WildGearSetup;
import jp.revati.skyabyss.item.wild.WildGearTypes;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 地下のダンジョンの装飾品 4 品 (docs/design/55 の 5 章、形は 00 の 368〜371)。主を初めて倒した人に一つ (Dungeons.reward)。
// 品の動き (装飾の枠・必要 Lv・効き目・説明) は野の装備の装飾 (WildAccessoryItem) と同じ。ただし野の装備の表 (WildGearTable) には入れない
// (敵の落とし物・封の箱から出ない、ダンジョンだけの品)。メッシュと絵は tools/rough/lowpoly/accessories/dungeon_final.py
public final class DungeonGear {
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 体に描く場所 (spec.py の CHEST・HIP_R・HIP_L・BELT_BACK と同じ。留め点は吊る輪の高さ)
    static final WildGearTypes.Mount CHEST = new WildGearTypes.Mount("BODY", 0, 1.4f, -2.7f, 0, 0, 0, 0.45f, 0, 4.8f, 0);
    static final WildGearTypes.Mount HIP_R = new WildGearTypes.Mount("BODY", -3.4f, 10.0f, -2.9f, 0, 0, 0, 0.42f, 0, 9.4f, 0);
    static final WildGearTypes.Mount HIP_L = new WildGearTypes.Mount("BODY", 3.4f, 10.0f, -2.9f, 0, 0, 0, 0.42f, 0, 7.0f, 0);
    static final WildGearTypes.Mount BELT_BACK = new WildGearTypes.Mount("BODY", 3.6f, 10.6f, 3.1f, 0, 0, 0, 0.4f, 0, 6.4f, 0);

    public static final List<WildGearTypes.Accessory> ROWS = List.of(
            // 骨掘りの顎飾り: 6% で傷 +50%、受ける傷 −3% (体力が半分未満で −6%)
            row("pit_jaw_pendant", GearTier.RARE, 10, CHEST, fx(WildEffect.Kind.CRIT, 0.06, 0.5), fx(WildEffect.Kind.GRIT, 0.03, 0.06)),
            // 殻負いの螺貝: マナ +10、水の中で息が長い、泳ぎ +20%
            row("tide_spiral_shell", GearTier.RARE, 16, HIP_R, fx(WildEffect.Kind.MAX_MANA, 10), fx(WildEffect.Kind.BREATH, 1), fx(WildEffect.Kind.SWIM, 0.2)),
            // 背晶の欠片: 技の待ち −4%、雷の傷 −20%
            row("storm_crystal_shard", GearTier.EPIC, 22, HIP_L, fx(WildEffect.Kind.COOLDOWN, 0.04), fx(WildEffect.Kind.LIGHTNING_TAKEN, -0.2)),
            // 炉の熾: 体力 +24、火の傷 −30%
            row("mine_forge_ember", GearTier.EPIC, 28, BELT_BACK, fx(WildEffect.Kind.MAX_HEALTH, 2.4), fx(WildEffect.Kind.FIRE_TAKEN, -0.3)));

    // ダンジョン → 品の id
    static final Map<Dungeon, String> BY_DUNGEON = Map.of(Dungeon.BONE_PIT, "pit_jaw_pendant", Dungeon.TIDE_CAVE, "tide_spiral_shell",
            Dungeon.STORM_HOLLOW, "storm_crystal_shard", Dungeon.MOLTEN_MINE, "mine_forge_ember");
    static final Map<String, DeferredItem<Item>> ITEM_OF = new java.util.HashMap<>();

    private DungeonGear() {
    }

    static WildGearTypes.Accessory row(String id, GearTier tier, int level, WildGearTypes.Mount mount, WildEffect.Fx... fx) {
        return new WildGearTypes.Accessory(id, false, tier, level, "dungeon", 0, List.of(fx), mount);
    }

    static WildEffect.Fx fx(WildEffect.Kind kind, double... p) {
        return new WildEffect.Fx(kind, p);
    }

    public static void register(IEventBus bus) {
        for (WildGearTypes.Accessory x : ROWS) {
            ITEM_OF.put(x.id(), ITEMS.register(x.id(), () -> new WildAccessoryItem(WildGearSetup.props(x.tier()), x)));
        }
        ITEMS.register(bus);
    }

    public static Item item(Dungeon d) {
        return ITEM_OF.get(BY_DUNGEON.get(d)).get();
    }
}

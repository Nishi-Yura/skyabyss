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

// 地下のダンジョンと奥のダンジョンの装飾品 8 品 (docs/design/55 の 5 章、形は 00 の 368〜371)。主を初めて倒した人に一つ (Dungeons.reward)。
// 品の動き (装飾の枠・必要 Lv・効き目・説明) は野の装備の装飾 (WildAccessoryItem) と同じ。ただし野の装備の表 (WildGearTable) には入れない
// (敵の落とし物・封の箱から出ない、ダンジョンだけの品)。メッシュと絵は tools/rough/lowpoly/accessories/dungeon_final.py
public final class DungeonGear {
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 体に描く場所 (spec.py の CHEST・HIP_R・HIP_L・BELT_BACK と同じ。留め点は吊る輪の高さ)
    static final WildGearTypes.Mount CHEST = new WildGearTypes.Mount("BODY", 0, 2.2f, -3.4f, 0, 0, 0, 0.45f, 0, 4.8f, 0);
    static final WildGearTypes.Mount HIP_R = new WildGearTypes.Mount("BODY", -3.4f, 10.0f, -2.9f, 0, 0, 0, 0.42f, 0, 9.4f, 0);
    static final WildGearTypes.Mount HIP_L = new WildGearTypes.Mount("BODY", 3.4f, 10.0f, -2.9f, 0, 0, 0, 0.42f, 0, 7.0f, 0);
    static final WildGearTypes.Mount BELT_BACK = new WildGearTypes.Mount("BODY", 3.6f, 10.6f, 3.1f, 0, 0, 0, 0.4f, 0, 6.4f, 0);

    public static final List<WildGearTypes.Accessory> ROWS = List.of(
            // 骨掘りの顎飾り: 6% で傷 +50%、受ける傷 −3% (体力が半分未満で −6%)
            row("pit_jaw_pendant", GearTier.RARE, 10, CHEST, fx(WildEffect.Kind.CRIT, 0.06, 0.5), fx(WildEffect.Kind.GRIT, 0.03, 0.06), fx(WildEffect.Kind.BONE_WARD, 2, 100)),
            // 殻負いの螺貝: マナ +10、水の中で息が長い、泳ぎ +20%
            row("tide_spiral_shell", GearTier.RARE, 16, HIP_R, fx(WildEffect.Kind.MAX_MANA, 10), fx(WildEffect.Kind.BREATH, 1), fx(WildEffect.Kind.SWIM, 0.2), fx(WildEffect.Kind.WET_MANA, 0.3)),
            // 背晶の欠片: 技の待ち −4%、雷の傷 −20%
            row("storm_crystal_shard", GearTier.EPIC, 22, HIP_L, fx(WildEffect.Kind.COOLDOWN, 0.04), fx(WildEffect.Kind.LIGHTNING_TAKEN, -0.2), fx(WildEffect.Kind.STORM_DAMAGE, 0.12)),
            // 炉の熾: 体力 +24、火の傷 −30%
            row("mine_forge_ember", GearTier.EPIC, 28, BELT_BACK, fx(WildEffect.Kind.MAX_HEALTH, 2.4), fx(WildEffect.Kind.FIRE_TAKEN, -0.3), fx(WildEffect.Kind.BURNING_RAGE, 0.15)),
            // ---- 奥のダンジョン (55 の 7 章。tools/rough/lowpoly/accessories/deep_trinkets.py)
            // 砦の骨の角笛: 体力 +16、5% で傷 +50%
            row("fort_bone_horn", GearTier.RARE, 15, BELT_BACK, fx(WildEffect.Kind.MAX_HEALTH, 1.6), fx(WildEffect.Kind.CRIT, 0.05, 0.5), fx(WildEffect.Kind.HORN, 8, 100, 1200)),
            // 祭司の珊瑚の鈴: マナ +14、技の待ち −3%、水の中で息が長い
            row("temple_coral_bell", GearTier.EPIC, 21, HIP_R, fx(WildEffect.Kind.MAX_MANA, 14), fx(WildEffect.Kind.COOLDOWN, 0.03), fx(WildEffect.Kind.BREATH, 1), fx(WildEffect.Kind.WATER_REGEN, 1)),
            // 大熊の氷の牙: 7% で傷 +60%、足 +4%
            row("ice_bear_fang", GearTier.EPIC, 27, CHEST, fx(WildEffect.Kind.CRIT, 0.07, 0.6), fx(WildEffect.Kind.MOVE_SPEED, 0.04), fx(WildEffect.Kind.CRIT_SLOW, 40, 1)),
            // 大蛇の熾の鱗: 体力 +30、火のダメージ −40%
            row("wyrm_ember_scale", GearTier.EPIC, 33, HIP_L, fx(WildEffect.Kind.MAX_HEALTH, 3.0), fx(WildEffect.Kind.FIRE_TAKEN, -0.4), fx(WildEffect.Kind.HURT_IGNITE, 0.25, 4)));

    // ダンジョン → 品の id
    static final Map<Dungeon, String> BY_DUNGEON = Map.of(Dungeon.BONE_PIT, "pit_jaw_pendant", Dungeon.TIDE_CAVE, "tide_spiral_shell",
            Dungeon.STORM_HOLLOW, "storm_crystal_shard", Dungeon.MOLTEN_MINE, "mine_forge_ember", Dungeon.DEEP_FORT, "fort_bone_horn",
            Dungeon.DEEP_TEMPLE, "temple_coral_bell", Dungeon.DEEP_ICE, "ice_bear_fang", Dungeon.DEEP_LAVA, "wyrm_ember_scale");
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

    // そのダンジョンだけの装飾品 (奥のダンジョンなど、無いときは空気)
    public static Item item(Dungeon d) {
        DeferredItem<Item> it = BY_DUNGEON.containsKey(d) ? ITEM_OF.get(BY_DUNGEON.get(d)) : null;
        return it == null ? net.minecraft.world.item.Items.AIR : it.get();
    }

    // ---------------------------------------------------------------- 育つ (2026-11-05 のダンジョンの頁 1-2)
    // 同じ主を倒すたびに数える (深く潜ったら 2 回ぶん)。5 回で II、15 回で III。効き目の数字が 1.25 倍・1.5 倍。数は品に書く
    public static final String GROW_KEY = "skyabyss_trinket_clears";
    public static final int[] GROW_STEPS = {5, 15};
    public static final double[] GROW_SCALE = {1.0, 1.25, 1.5};

    // その装飾品の土地の巨神 (強化・分解の素材。2026-11-10: 由来が「dungeon」で岩鯨の鱗片に落ちていた)
    public static String originTitan(String id) {
        for (Map.Entry<Dungeon, String> e : BY_DUNGEON.entrySet()) {
            if (e.getValue().equals(id)) {
                return switch (e.getKey().region) {
                    case jp.revati.skyabyss.world.continent.ContinentMap.TIDE -> "tide_shell";
                    case jp.revati.skyabyss.world.continent.ContinentMap.STORM -> "thunder_wing";
                    case jp.revati.skyabyss.world.continent.ContinentMap.EMBER -> "ember";
                    default -> "stone_whale";
                };
            }
        }
        return "stone_whale";
    }

    public static boolean isTrinket(Item item) {
        for (Dungeon d : Dungeon.mids()) {
            DeferredItem<Item> it = ITEM_OF.get(BY_DUNGEON.get(d));
            if (it != null && it.get() == item) {
                return true;
            }
        }
        return false;
    }

    public static int clears(net.minecraft.world.item.ItemStack s) {
        net.minecraft.world.item.component.CustomData d = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getInt(GROW_KEY);
    }

    public static void setClears(net.minecraft.world.item.ItemStack s, int n) {
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, s, t -> t.putInt(GROW_KEY, n));
    }

    public static int stage(int clears) {
        int st = 0;
        for (int step : GROW_STEPS) {
            if (clears >= step) {
                st++;
            }
        }
        return st;
    }

    public static int stage(net.minecraft.world.item.ItemStack s) {
        return isTrinket(s.getItem()) ? stage(clears(s)) : 0;
    }

    public static double scale(net.minecraft.world.item.ItemStack s) {
        return GROW_SCALE[stage(s)];
    }

    // 育った装飾品の効き目 (数字をみな掛ける)
    public static List<WildEffect.Fx> scaled(net.minecraft.world.item.ItemStack s, List<WildEffect.Fx> fx) {
        double k = scale(s);
        if (k == 1.0) {
            return fx;
        }
        List<WildEffect.Fx> out = new java.util.ArrayList<>(fx.size());
        for (WildEffect.Fx f : fx) {
            double[] p = f.p().clone();
            // 伸びるのは強さの数だけ (広さ・長さ・待ちは伸ばさない。2026-11-10)
            for (int i : jp.revati.skyabyss.item.wild.AccessoryRolls.strength(f.kind())) {
                if (i < p.length) {
                    p[i] *= k;
                }
            }
            out.add(new WildEffect.Fx(f.kind(), p));
        }
        return out;
    }

    // そのダンジョンの装飾品 (装飾の枠を先に、なければ持ち物) に数を足す。段が上がったら知らせる
    public static void grow(net.minecraft.server.level.ServerPlayer p, Dungeon d, int n) {
        Item want = item(d);
        if (want == net.minecraft.world.item.Items.AIR) {
            return;
        }
        net.minecraft.world.item.ItemStack found = net.minecraft.world.item.ItemStack.EMPTY;
        boolean worn = false;
        for (int i = 0; i < jp.revati.skyabyss.item.accessory.AccessoryData.SIZE && found.isEmpty(); i++) {
            net.minecraft.world.item.ItemStack s = jp.revati.skyabyss.item.accessory.Accessories.stack(p, i);
            if (s.is(want)) {
                found = s;
                worn = true;
            }
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && found.isEmpty(); i++) {
            if (inv.getItem(i).is(want)) {
                found = inv.getItem(i);
            }
        }
        if (found.isEmpty()) {
            return;
        }
        int before = clears(found);
        int after = Math.max(1, before) + n;
        setClears(found, after);
        if (worn) {
            jp.revati.skyabyss.item.accessory.Accessories.sync(p);
        } else {
            inv.setChanged();
        }
        if (stage(after) > stage(before) && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            jp.revati.skyabyss.notice.Notice.gain(p, "message.skyabyss.dungeon.trinket_grew", found.getHoverName(), roman(stage(after)));
            p.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.2f);
        }
    }

    static String roman(int stage) {
        return stage >= 2 ? "III" : stage == 1 ? "II" : "I";
    }

    // ツールチップ: 育ちの段と次まで
    public static void tooltip(net.minecraft.world.item.ItemStack s, List<net.minecraft.network.chat.Component> out) {
        if (!isTrinket(s.getItem())) {
            return;
        }
        int c = Math.max(1, clears(s));
        int st = stage(c);
        int color = 0xE8C27A;
        if (st >= GROW_STEPS.length) {
            out.add(net.minecraft.network.chat.Component.translatable("tooltip.skyabyss.trinket_grow.max", roman(st), GROW_SCALE[st]).withStyle(x -> x.withColor(color)));
        } else {
            out.add(net.minecraft.network.chat.Component.translatable("tooltip.skyabyss.trinket_grow", roman(st), GROW_SCALE[st], GROW_STEPS[st] - c)
                    .withStyle(x -> x.withColor(color)));
        }
    }
}

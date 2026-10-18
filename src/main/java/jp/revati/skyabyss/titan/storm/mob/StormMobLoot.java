package jp.revati.skyabyss.titan.storm.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LorePageItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// 雷翼の背の敵が落とす物 (12 の 6.1節)。潮殻の背の敵 (TideMobLoot) と同じ型: 一体ごとに、倒した人のための抽選をし、
// その人にだけ拾える形で落とす。雷翼の背 (storm_back) の上か、試練の印のある個体を、プレイヤーが倒したときだけ。
//
// | 敵     | 雷翼の羽軸 | 共鳴の欠片 | 雲間の干し果 | そのほか |
// | 羽守   | 30%、1〜2  | 5%、1      | 3%、1        | 雷翼の羽衣のどれか一部位 1% |
// | 雷蛾   | 12%、1     | 2%、1      | —            | 鱗粉 15% |
// | 雷乞い | 25%、1〜2  | 5%、1      | 3%、1        | 破れた頁「風見の帳」5% (出どころ storm_back。まだ記していない頁があるときだけ) |
//
// 品 (欠片・干し果・鱗粉・羽衣) は装備の担当 (担当 5) が登録する。ここでは ID (11.1) で引き、まだ無い品は落とさない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormMobLoot {
    public static final String SHARD = id("thunder_wing_shard");
    public static final String RESONANCE = id("resonance_shard_thunder_wing");
    public static final String FRUIT = id("cloudgap_fruit");
    public static final String DUST = id("moth_dust");
    public static final List<String> ARMOR = List.of(id("thunder_wing_helmet"), id("thunder_wing_chestplate"),
            id("thunder_wing_leggings"), id("thunder_wing_boots"));
    public static final String PAGE = "page";
    public static final String PAGE_SOURCE = "storm_back";

    private StormMobLoot() {
    }

    private static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    public record Row(float shard, int shardMin, int shardMax, float resonance, float fruit, float dust, float armor, float page) {
    }

    public static final Row WARDEN = new Row(0.30f, 1, 2, 0.05f, 0.03f, 0f, 0.01f, 0f);
    public static final Row MOTH = new Row(0.12f, 1, 1, 0.02f, 0f, 0.15f, 0f, 0f);
    public static final Row SUPPLICANT = new Row(0.25f, 1, 2, 0.05f, 0.03f, 0f, 0f, 0.05f);

    public static Row row(LivingEntity mob) {
        if (mob instanceof FeatherWarden) {
            return WARDEN;
        }
        if (mob instanceof StaticMoth) {
            return MOTH;
        }
        if (mob instanceof ThunderSupplicant) {
            return SUPPLICANT;
        }
        return null;
    }

    public record Drop(String id, int count) {
    }

    // 倒した人への、一体ぶんの抽選 (品の ID と数。頁は PAGE)
    public static List<Drop> roll(Row r, RandomSource random) {
        List<Drop> out = new ArrayList<>();
        if (r == null) {
            return out;
        }
        if (random.nextFloat() < r.shard()) {
            out.add(new Drop(SHARD, r.shardMin() + random.nextInt(r.shardMax() - r.shardMin() + 1)));
        }
        if (random.nextFloat() < r.resonance()) {
            out.add(new Drop(RESONANCE, 1));
        }
        if (random.nextFloat() < r.fruit()) {
            out.add(new Drop(FRUIT, 1));
        }
        if (random.nextFloat() < r.dust()) {
            out.add(new Drop(DUST, 1));
        }
        if (random.nextFloat() < r.armor()) {
            out.add(new Drop(ARMOR.get(random.nextInt(ARMOR.size())), 1));
        }
        if (random.nextFloat() < r.page()) {
            out.add(new Drop(PAGE, 1));
        }
        return out;
    }

    public static Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        Row r = row(mob);
        if (r == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != StormMob.STORM_BACK && !mob.getTags().contains(StormMob.TRIAL_TAG)) {
            return;
        }
        ServerPlayer killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            killer = p;
        } else if (mob.getKillCredit() instanceof ServerPlayer p) {
            killer = p;
        }
        if (killer == null || killer instanceof FakePlayer) {
            return;
        }
        for (Drop d : roll(r, level.random)) {
            ItemStack stack;
            if (d.id().equals(PAGE)) {
                if (LorePageItem.candidates(killer, PAGE_SOURCE).isEmpty()) {
                    continue;
                }
                stack = LorePageItem.of(PAGE_SOURCE);
            } else {
                Optional<Item> item = item(d.id());
                if (item.isEmpty()) {
                    continue;
                }
                stack = new ItemStack(item.get(), d.count());
            }
            ItemEntity e = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
            e.setTarget(killer.getUUID());
            e.setDefaultPickUpDelay();
            event.getDrops().add(e);
        }
    }
}

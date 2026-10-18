package jp.revati.skyabyss.titan.ember.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import net.minecraft.core.BlockPos;
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

// 余燼の背の敵が落とす物 (13 の 6.1節)。潮殻の敵 (TideMobLoot) と同じ型:
// 一体ごとに、倒した人のための抽選をし、その人にだけ拾える形で落とす。余燼の背の上で、プレイヤーが倒したときだけ
// (燃え尽きた灰被りは、最後に叩いた人が倒した扱い。AshThrall.burnOut)。
//
// | 敵     | 余燼の燠石 | 共鳴の欠片 | 熾火の角杯 | そのほか |
// | 灰被り | 35%、1〜2  | 6%、1      | 4%、1      | 余燼の鎧のどれか一部位 1%。火守り (EmberBackLayout.SOUTH) では熾 1 (必ず。だれでも拾える) |
// | 火の粉蛾 | 5%、1    | 1%、1      | —          | 火の粉の粉 15% |
// | 燠虫   | 25%、1〜2  | 5%、1      | 3%、1      | 燠の殻 20% |
//
// 品 (余燼の燠石・角杯・粉・殻・鎧) は担当 G、熾は担当 C3 が登録する。表は ID で持ち、登録されていない品は落とさない
// (品が登録されればそのまま出る)。鎧の 1% は図鑑に記さない (図鑑の記録は心臓の報酬 EmberLoot の担当 F)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberMobLoot {
    private EmberMobLoot() {
    }

    public static final ResourceLocation SHARD = EmberIds.id(EmberIds.EMBER_SHARD);
    public static final ResourceLocation RESONANCE = EmberIds.id("resonance_shard_ember");
    public static final ResourceLocation HORN = EmberIds.id(EmberIds.EMBER_HORN);
    public static final ResourceLocation SPARK_DUST = EmberIds.id(EmberIds.SPARK_DUST);
    public static final ResourceLocation CINDER_HUSK = EmberIds.id(EmberIds.CINDER_HUSK);
    public static final ResourceLocation LIVE_COAL = EmberIds.id(EmberIds.LIVE_COAL);
    public static final List<ResourceLocation> ARMOR = List.of(EmberIds.id(EmberIds.EMBER_HELMET), EmberIds.id(EmberIds.EMBER_CHESTPLATE),
            EmberIds.id(EmberIds.EMBER_LEGGINGS), EmberIds.id(EmberIds.EMBER_BOOTS));

    // 一体ぶんの表。extra は「そのほか」の品 (null なら無し)
    public record Row(float shard, int shardMin, int shardMax, float resonance, float horn, float armor, ResourceLocation extra, float extraChance) {
    }

    public static final Row THRALL = new Row(0.35f, 1, 2, 0.06f, 0.04f, 0.01f, null, 0f);
    public static final Row MOTH = new Row(0.05f, 1, 1, 0.01f, 0f, 0f, SPARK_DUST, 0.15f);
    public static final Row ROLLER = new Row(0.25f, 1, 2, 0.05f, 0.03f, 0f, CINDER_HUSK, 0.20f);

    public record Drop(ResourceLocation id, int count) {
    }

    public static Row row(LivingEntity mob) {
        if (mob instanceof AshThrall) {
            return THRALL;
        }
        if (mob instanceof EmberMoth) {
            return MOTH;
        }
        if (mob instanceof CinderRoller) {
            return ROLLER;
        }
        return null;
    }

    // 倒した人への、一体ぶんの抽選 (品の ID と数。登録されているかは見ない)
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
        if (random.nextFloat() < r.horn()) {
            out.add(new Drop(HORN, 1));
        }
        if (random.nextFloat() < r.armor()) {
            out.add(new Drop(ARMOR.get(random.nextInt(ARMOR.size())), 1));
        }
        if (r.extra() != null && random.nextFloat() < r.extraChance()) {
            out.add(new Drop(r.extra(), 1));
        }
        return out;
    }

    // 登録されている品だけを物にする
    public static List<ItemStack> stacks(List<Drop> drops) {
        List<ItemStack> out = new ArrayList<>();
        for (Drop d : drops) {
            Item item = BuiltInRegistries.ITEM.getOptional(d.id()).orElse(null);
            if (item != null) {
                out.add(new ItemStack(item, d.count()));
            }
        }
        return out;
    }

    // 火守りの谷 (C3 の部屋の箱) で倒れた灰被りか
    public static boolean inHearthValley(LivingEntity mob) {
        return EmberBackLayout.SOUTH.contains(BlockPos.containing(mob.position()));
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        Row r = row(mob);
        if (r == null || !(mob.level() instanceof ServerLevel level) || level.dimension() != EmberIds.BACK) {
            return;
        }
        // 火守りの谷の灰被りは熾を必ず落とす (だれでも拾える。灰運び。倒し方を問わない)
        if (mob instanceof AshThrall && inHearthValley(mob)) {
            for (ItemStack coal : stacks(List.of(new Drop(LIVE_COAL, 1)))) {
                ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), coal);
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
            }
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
        for (ItemStack stack : stacks(roll(r, level.random))) {
            ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
            item.setTarget(killer.getUUID());
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
        }
    }
}

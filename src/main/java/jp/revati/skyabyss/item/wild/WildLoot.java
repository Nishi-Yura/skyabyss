package jp.revati.skyabyss.item.wild;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BossFooting;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.NamedFoes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 野の装備の落とし物 (docs/design/51 の 3 章)。敵を倒したとき、倒した人と、そばにいる同じ組の人ごとに抽選し、当たった品をその人に直接渡す
// (旅嚢か持ち物。入らなければ足もとに、その人だけが拾える)。ほかの人の当たりで自分の確率は減らない。
//  - 土地の敵 (大陸): 土地ごとの帯と階級の確率 (REGIONS)
//  - 名のある強敵・主 (層の主・月の区画の主・雲の島の主。BossFooting.planted): 人ごとに初めて倒したとき帯のレアかエピックを必ず、二度目から 12%。ヒロイックは 3%
//  - 深淵: 骨と塩の層 = 帯 IV、雷と灰の層と深み = 帯 V。月 = 帯 V と Lv 48 のヒロイック。雲海 = 帯 V と Lv 50 のヒロイック
//  - 武器が当たったら 2/3 で今の職の武器。品の種類は 武器 70% / 防具の部位 30%
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WildLoot {
    public static final double SHARE_RANGE = 32;
    public static final float JOB_BIAS = 2f / 3f;
    public static final float WEAPON_SHARE = 0.7f;
    public static final float UNCOMMON = 0.02f, RARE = 0.008f, EPIC = 0.003f, HEROIC = 0.0005f;
    public static final float BOSS_AGAIN = 0.12f, BOSS_HEROIC = 0.03f;
    static final String FIRST = SkyAbyss.MODID + ":wild_first_";

    // 一つの源の抽選: 帯ごとの階級の確率 (順に試す)、主のときの帯と階級、ヒロイックの絞り込み
    public record Roll(int band, GearTier tier, float chance) {
    }

    public record Source(String name, List<Roll> rolls, int bossBand, GearTier[] bossTiers, Predicate<WildGearTypes.Row> heroic, float heroicChance) {
    }

    static final GearTier[] RE = {GearTier.RARE, GearTier.EPIC};
    static final Predicate<WildGearTypes.Row> ANY_HEROIC = r -> r.tier() == GearTier.HEROIC;

    // 大陸の土地 (ContinentMap の番号 → 源)
    static Source region(int r) {
        return switch (r) {
            case ContinentMap.OUTSKIRTS -> new Source("island", List.of(new Roll(1, GearTier.UNCOMMON, 0.01f)), 1, new GearTier[]{GearTier.UNCOMMON}, null, 0);
            case ContinentMap.MEADOW -> new Source("meadow", List.of(new Roll(1, GearTier.UNCOMMON, UNCOMMON), new Roll(1, GearTier.RARE, RARE / 2)),
                    1, new GearTier[]{GearTier.RARE}, null, 0);
            case ContinentMap.WHALE -> new Source("hills", List.of(new Roll(1, GearTier.UNCOMMON, UNCOMMON / 2), new Roll(1, GearTier.RARE, RARE)),
                    1, new GearTier[]{GearTier.RARE}, null, 0);
            case ContinentMap.TIDE -> new Source("coast", List.of(new Roll(2, GearTier.UNCOMMON, UNCOMMON), new Roll(2, GearTier.RARE, RARE)),
                    2, new GearTier[]{GearTier.RARE}, null, 0);
            case ContinentMap.STORM -> new Source("heights", List.of(new Roll(2, GearTier.EPIC, EPIC), new Roll(3, GearTier.RARE, RARE)),
                    2, new GearTier[]{GearTier.EPIC}, r2 -> r2.tier() == GearTier.HEROIC && WildGearTypes.band(r2.level()) == 3, 0);
            case ContinentMap.EMBER -> new Source("wastes", List.of(new Roll(3, GearTier.EPIC, EPIC)),
                    3, new GearTier[]{GearTier.EPIC}, r2 -> r2.tier() == GearTier.HEROIC && WildGearTypes.band(r2.level()) == 3, HEROIC);
            default -> null;
        };
    }

    static Source abyss(int layer) {
        if (layer <= 1) {
            return new Source("abyss_upper", List.of(new Roll(4, GearTier.RARE, RARE), new Roll(4, GearTier.EPIC, EPIC)), 4, RE,
                    r -> r.tier() == GearTier.HEROIC && WildGearTypes.band(r.level()) == 4, HEROIC);
        }
        return new Source("abyss_lower", List.of(new Roll(5, GearTier.RARE, RARE), new Roll(5, GearTier.EPIC, EPIC)), 5, RE, null, 0);
    }

    static final Source MOON = new Source("moon", List.of(new Roll(5, GearTier.RARE, RARE), new Roll(5, GearTier.EPIC, EPIC)), 5, RE,
            r -> r.tier() == GearTier.HEROIC && r.level() == 48, HEROIC);
    static final Source CLOUD = new Source("cloud", List.of(new Roll(5, GearTier.RARE, RARE), new Roll(5, GearTier.EPIC, EPIC)), 5, RE,
            r -> r.tier() == GearTier.HEROIC && r.level() == 50, HEROIC);

    public static Source sourceOf(ServerLevel level, LivingEntity dead) {
        if (level.dimension() == Level.OVERWORLD) {
            ContinentMap map = ContinentMap.get();
            return map.inside(dead.getBlockX(), dead.getBlockZ()) ? region(map.region(dead.getBlockX(), dead.getBlockZ())) : null;
        }
        if (level.dimension() == jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL) {
            return abyss(jp.revati.skyabyss.abyss.mobs.AbyssScaling.layerOf(dead.getY()));
        }
        if (level.dimension() == jp.revati.skyabyss.story.ch6.MoonWorld.LEVEL) {
            return MOON;
        }
        if (level.dimension() == jp.revati.skyabyss.story.finale.FinaleWorld.LEVEL) {
            return CLOUD;
        }
        return null;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || !(dead.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        Source src = sourceOf(level, dead);
        if (src == null) {
            return;
        }
        boolean boss = dead.getTags().contains(NamedFoes.TAG) || jp.revati.skyabyss.combat.MobTuning.hasBossBar(dead.getClass());
        for (ServerPlayer p : recipients(level, killer, dead)) {
            ItemStack drop = roll(p, src, boss, BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString(), level.random);
            if (!drop.isEmpty()) {
                give(p, drop);
            }
        }
    }

    static List<ServerPlayer> recipients(ServerLevel level, ServerPlayer killer, LivingEntity dead) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(killer);
        var party = jp.revati.skyabyss.party.Parties.of(killer);
        if (party != null) {
            for (ServerPlayer m : jp.revati.skyabyss.party.Parties.onlineMembers(level.getServer(), party)) {
                if (m != killer && m.level() == level && m.distanceTo(dead) <= SHARE_RANGE && !m.isSpectator()) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    // 一人ぶんの抽選 (外れは空)
    public static ItemStack roll(ServerPlayer p, Source src, boolean boss, String foeId, RandomSource rand) {
        if (boss) {
            // ヒロイック (主の 3%)
            if (src.heroic() != null && rand.nextFloat() < BOSS_HEROIC) {
                ItemStack h = pick(p, r -> src.heroic().test(r), rand);
                if (!h.isEmpty()) {
                    return h;
                }
            }
            String key = FIRST + foeId;
            boolean first = !p.getPersistentData().getBoolean(key);
            if (first || rand.nextFloat() < BOSS_AGAIN) {
                p.getPersistentData().putBoolean(key, true);
                GearTier t = src.bossTiers()[rand.nextInt(src.bossTiers().length)];
                return pick(p, r -> WildGearTypes.band(r.level()) == src.bossBand() && r.tier() == t, rand);
            }
            return ItemStack.EMPTY;
        }
        if (src.heroic() != null && src.heroicChance() > 0 && rand.nextFloat() < src.heroicChance()) {
            return pick(p, r -> src.heroic().test(r), rand);
        }
        for (Roll roll : src.rolls()) {
            if (rand.nextFloat() < roll.chance()) {
                return pick(p, r -> WildGearTypes.band(r.level()) == roll.band() && r.tier() == roll.tier(), rand);
            }
        }
        return ItemStack.EMPTY;
    }

    // 条件に合う品から一つ: 武器 70% (2/3 で今の職) / 防具の部位 30%。どちらかが無ければもう一方
    public static ItemStack pick(ServerPlayer p, Predicate<WildGearTypes.Row> want, RandomSource rand) {
        List<WildGearTypes.Weapon> weapons = new ArrayList<>();
        for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
            if (want.test(w)) {
                weapons.add(w);
            }
        }
        List<WildGearTypes.ArmorSet> armors = new ArrayList<>();
        for (WildGearTypes.ArmorSet a : WildGearTable.ARMORS) {
            if (want.test(a)) {
                armors.add(a);
            }
        }
        boolean weapon = !weapons.isEmpty() && (armors.isEmpty() || rand.nextFloat() < WEAPON_SHARE);
        if (weapon) {
            Job job = currentJob(p);
            List<WildGearTypes.Weapon> mine = weapons.stream().filter(w -> w.job() == job).toList();
            List<WildGearTypes.Weapon> from = !mine.isEmpty() && rand.nextFloat() < JOB_BIAS ? mine : weapons;
            return new ItemStack(WildGearSetup.item(from.get(rand.nextInt(from.size())).id()));
        }
        if (armors.isEmpty()) {
            return ItemStack.EMPTY;
        }
        WildGearTypes.ArmorSet a = armors.get(rand.nextInt(armors.size()));
        List<net.neoforged.neoforge.registries.DeferredItem<Item>> pieces = WildGearSetup.ARMOR_ITEMS.get(a.id());
        return new ItemStack(pieces.get(rand.nextInt(pieces.size())).get());
    }

    static Job currentJob(ServerPlayer p) {
        Job held = Job.held(p);
        if (held != null) {
            return held;
        }
        String id = jp.revati.skyabyss.rpg.skill.SkillManager.get(p).curJob;
        return id == null || id.isEmpty() ? null : Job.byId(id);
    }

    // 渡す: 旅嚢か持ち物、入らなければ足もとに (その人だけが拾える)。名前の色は階級
    public static void give(ServerPlayer p, ItemStack stack) {
        Component name = stack.getHoverName();
        GearTier tier = stack.getItem() instanceof WildGear g ? g.tier() : GearTier.UNCOMMON;
        ItemStack left = jp.revati.skyabyss.pouch.PouchRouting.give(p, stack.copy());
        if (!left.isEmpty()) {
            ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY() + 0.3, p.getZ(), left);
            e.setTarget(p.getUUID());
            e.setPickUpDelay(10);
            p.level().addFreshEntity(e);
        }
        Notice.gain(p, Component.translatable("message.skyabyss.wild_drop", name));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), tier.atLeast(GearTier.HEROIC) ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                : tier.atLeast(GearTier.EPIC) ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7f, tier.atLeast(GearTier.EPIC) ? 1.2f : 1.0f);
        if (tier.atLeast(GearTier.RARE)) {
            level.sendParticles(tier.atLeast(GearTier.HEROIC) ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(),
                    tier.atLeast(GearTier.HEROIC) ? 40 : 12, 0.4, 0.8, 0.4, 0.05);
        }
    }
}

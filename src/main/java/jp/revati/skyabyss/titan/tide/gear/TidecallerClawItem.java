package jp.revati.skyabyss.titan.tide.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.SimpleTier;

// 潮招きの大鋏 (ヒロイック): 潮招きの大きなはさみを柄に据えた、重い片手の武器。
// 三度目ごとに (+5 から二度目ごとに)、はさみが潮を招いて前へ水の線が走る (長さ 6、+10 で 9。攻撃力の 50%。斬った相手にも当たる)。
// 水の中では攻撃速度 +20% (TideGearEvents)
public class TidecallerClawItem extends TideWeaponItem {
    // 攻撃力 11 = 1 + 4 + 6、攻撃速度 1.0。水の線の上乗せ込みで一体へ 12.8 (+5 から 13.75)
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 2400, 6.0f, 4.0f, 16,
            () -> Ingredient.of(TideGearSetup.shard()));
    private static final String HITS = SkyAbyss.MODID + ":claw_hits";

    public TidecallerClawItem(Properties properties) {
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 6, -3.0f)), GearTier.HEROIC, "item.skyabyss.tidecaller_claw.desc");
    }

    // 何度目ごとに水の線が走るか
    public static int every(ItemStack stack) {
        return ModGear.awakened(stack) ? 2 : 3;
    }

    public static double lineLength(ItemStack stack) {
        return ModGear.enhance(stack) >= ModGear.MAX_ENHANCE ? TideGear.CLAW_LINE_LENGTH_MAX : TideGear.CLAW_LINE_LENGTH;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            int hits = attacker.getPersistentData().getInt(HITS) + 1;
            if (hits >= every(stack)) {
                hits = 0;
                float damage = (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * TideGear.CLAW_LINE_RATE);
                waterLine(level, target, attacker, damage, lineLength(stack));
            }
            attacker.getPersistentData().putInt(HITS, hits);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    // 前へ走る水の線: 持ち主の向きに、幅 1.6 の帯。斬った相手と、帯の上の敵に当たる
    private static void waterLine(ServerLevel level, LivingEntity target, LivingEntity attacker, float damage, double length) {
        Vec3 look = attacker.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) {
            look = new Vec3(0, 0, 1);
        }
        look = look.normalize();
        Vec3 start = attacker.position();
        DamageSource source = attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
        AABB box = new AABB(start, start.add(look.scale(length))).inflate(1.2, 1.5, 1.2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != attacker && e.isAlive() && (e == target || e instanceof Enemy))) {
            Vec3 to = e.position().subtract(start).multiply(1, 0, 1);
            double along = to.dot(look);
            double side = to.subtract(look.scale(along)).length();
            if (e != target && (along < 0 || along > length || side > 0.8 + e.getBbWidth() * 0.5)) {
                continue;
            }
            e.invulnerableTime = 0;
            e.hurt(source, damage);
            e.push(look.x * 0.3, 0.15, look.z * 0.3);
            e.hurtMarked = true;
        }
        for (double d = 0.8; d <= length; d += 0.5) {
            Vec3 p = start.add(look.scale(d));
            level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.15, p.z, 4, 0.25, 0.05, 0.25, 0.1);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.x, p.y + 0.1, p.z, 1, 0.2, 0.05, 0.2, 0.05);
        }
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.6f, 1.3f);
    }
}

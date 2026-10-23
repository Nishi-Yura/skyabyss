package jp.revati.skyabyss.item;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.SimpleTier;

// 番兵長の鉾 (ヒロイック): 鯨骨の番兵長が振るっていた長柄。
// 間合いが長く、一振りが前方の扇形に届く。三度目ごとに、足もとから骨が噴き上がる
public class CaptainPikeItem extends SwordItem implements ModGear {
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 2200, 6.0f, 3.5f, 16,
            () -> Ingredient.of(Items.BONE_BLOCK));
    private static final String HITS = SkyAbyss.MODID + ":pike_hits";

    public CaptainPikeItem(Properties properties) {
        // 攻撃力 8.5 (2026-10-17 に 9 から)、攻撃速度 1.1、間合い +1.5。扇と噴出の上乗せで、一体へはダイヤの剣ほど
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 4, -2.9f)
                .withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "pike_reach"), 1.5, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)));
    }

    @Override
    public GearTier tier() {
        return GearTier.HEROIC;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 必要レベルに届かない人の手では、扇も噴出も出ない (GearLevel)
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
            sweepFan(level, target, attacker, damage * 0.5f);
            int hits = attacker.getPersistentData().getInt(HITS) + 1;
            // +5 からは二度に一度、骨が噴く。+10 では噴出が広がる
            int every = ModGear.awakened(stack) ? 2 : 3;
            if (hits >= every) {
                hits = 0;
                erupt(level, target, attacker, damage * 0.6f, ModGear.enhance(stack) >= ModGear.MAX_ENHANCE ? 3.5 : 2.5);
            }
            attacker.getPersistentData().putInt(HITS, hits);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    // 前方およそ 90 度、4.5 マス以内の敵に、半分の威力で届く
    private static void sweepFan(ServerLevel level, LivingEntity target, LivingEntity attacker, float damage) {
        Vec3 look = attacker.getLookAngle().multiply(1, 0, 1).normalize();
        DamageSource source = source(attacker);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(4.5, 1.5, 4.5),
                e -> e != target && e != attacker && e.isAlive() && e instanceof Enemy)) {
            Vec3 to = e.position().subtract(attacker.position()).multiply(1, 0, 1);
            if (to.length() > 4.5 || to.length() < 0.01 || to.normalize().dot(look) < 0.7) {
                continue;
            }
            e.hurt(source, damage);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX() + look.x * 2, attacker.getY(0.6), attacker.getZ() + look.z * 2, 3, 0.8, 0.1, 0.8, 0);
    }

    // 骨の噴出: 相手の足もとから骨が噴き上がり、まわりの敵を打ち上げる
    private static void erupt(ServerLevel level, LivingEntity target, LivingEntity attacker, float damage, double radius) {
        DamageSource source = source(attacker);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius, 1, radius),
                e -> e != attacker && e.isAlive() && (e == target || e instanceof Enemy))) {
            e.invulnerableTime = 0;
            e.hurt(source, damage);
            e.push(0, 0.45, 0);
            e.hurtMarked = true;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                target.getX(), target.getY() + 0.2, target.getZ(), 50, radius * 0.4, 0.6, radius * 0.4, 0.2);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + 0.2, target.getZ(), 12, radius * 0.3, 0.1, radius * 0.3, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    private static DamageSource source(LivingEntity attacker) {
        return attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.captain_pike.desc", true);
    }
}

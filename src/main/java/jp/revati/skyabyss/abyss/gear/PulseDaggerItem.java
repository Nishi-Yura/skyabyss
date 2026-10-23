package jp.revati.skyabyss.abyss.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

// 鼓動の短剣 (伝説): 心臓の番人の肋から削った、脈を打つ短剣。攻撃力 9、速さ 2.0。
// 同じ敵を 1.5 秒の内に打ち続けると拍が数えられ、4 度目ごとに (+5 から 3 度目ごとに) 「拍」: 攻撃力の 50% の追い打ちと、自分の体力 1 回復。
// 鼓動の術と同じく、敵の鼓動を聞いて合わせる形
public class PulseDaggerItem extends AbyssGearWeaponItem {
    private static final String CHAIN = SkyAbyss.MODID + ":pulse_dagger";

    public PulseDaggerItem(Properties properties) {
        // 攻撃力 9 = 1 + 5 + 3、攻撃速度 2.0 = 4 − 2.0
        super(properties.attributes(SwordItem.createAttributes(TIER, 3, -2.0f)), GearTier.LEGENDARY, "item.skyabyss.abyss_pulse_dagger.desc");
    }

    public static int every(ItemStack stack) {
        return ModGear.awakened(stack) ? AbyssGear.DAGGER_EVERY_AWAKENED : AbyssGear.DAGGER_EVERY;
    }

    // 拍を数える (純粋な部分。自己テストが呼ぶ): 前の相手・前の時刻と比べ、続いていれば数を足す。返すのは新しい数 (拍が出たら 0)
    public static int count(int prev, boolean sameTarget, long since, int every) {
        int n = sameTarget && since <= AbyssGear.DAGGER_CHAIN_TICKS ? prev + 1 : 1;
        return n >= every ? 0 : n;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            CompoundTag tag = attacker.getPersistentData().getCompound(CHAIN);
            long now = level.getGameTime();
            boolean same = tag.hasUUID("t") && tag.getUUID("t").equals(target.getUUID());
            int n = count(tag.getInt("n"), same, now - tag.getLong("at"), every(stack));
            tag.putUUID("t", target.getUUID());
            tag.putLong("at", now);
            tag.putInt("n", n);
            attacker.getPersistentData().put(CHAIN, tag);
            if (n == 0) {
                beat(level, attacker, target, (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * AbyssGear.DAGGER_RATE));
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    static void beat(ServerLevel level, LivingEntity attacker, LivingEntity target, float damage) {
        DamageSource source = attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
        attacker.heal(AbyssGear.DAGGER_HEAL);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.3f);
    }
}

package jp.revati.skyabyss.titan.ember.gear;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 余燼の装備のうち、持ち主や相手の状態で変わる効果 (03 の 12章、13 の 6.5節):
//  - 余燼の鎧: 2 部位で強靭 +3。4 部位で、余燼の背の火照り (熱 50 以上) のとき与える傷 +10%、どこでも火に触れたら筋力 I 10 秒。
//    熱のたまり方 ×0.7 は EmberGear.heatGain (熱の担当 B の EmberHeat.gainMultiplier が呼ぶ)
//  - 余燼の戦斧: 燃えている・くすぶる相手への傷 +10% (くすぶりの印は smolder)
//  - 鍛え手の大鎚: 亀裂 (crack) が 0.8 秒後に噴く
//  - 灰被りの火掻き: 灰が固まると鈍足 II と防御 −4 (brittle、3 秒)
//  - 熾火の外套: 燃焼・溶岩・火の傷 (#is_fire) を ×0.7。余燼の熱の傷 (heat・heat_burst・engulfed) は火ではないので減らない
//  - 余燼の灯: 倒れる傷を一度だけ体力 4 で踏みとどまり、まわりを焼く。待ち 600 秒。不死のトーテムが先に使われる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberGearEvents {
    static final ResourceLocation SET2 = id("ember_set2_toughness");
    static final ResourceLocation BRITTLE = id("ember_poker_brittle");
    private static final String SMOLDER = SkyAbyss.MODID + ":ember_smolder_until";

    // 噴く前の亀裂 (サーバーの tick で数える)
    private record Crack(ServerLevel level, LivingEntity attacker, LivingEntity target, Vec3 start, Vec3 dir, double length, float damage, long due) {
    }

    private static final List<Crack> CRACKS = new ArrayList<>();
    // 灰で脆くなった相手と、戻す時刻
    private static final Map<LivingEntity, Long> BRITTLE_UNTIL = new WeakHashMap<>();

    private EmberGearEvents() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    // ---------------------------------------------------------------- くすぶり (戦斧・護符・灯)
    // 相手を燃やし (火に強くなければ)、くすぶりの印を付ける (火に強くても付く)
    public static void smolder(LivingEntity target, int ticks) {
        if (!target.fireImmune()) {
            target.igniteForTicks(ticks);
        }
        long until = target.level().getGameTime() + ticks;
        target.getPersistentData().putLong(SMOLDER, Math.max(until, target.getPersistentData().getLong(SMOLDER)));
    }

    public static boolean smoldering(LivingEntity target) {
        return target.isOnFire() || target.getPersistentData().getLong(SMOLDER) > target.level().getGameTime();
    }

    // ---------------------------------------------------------------- 一式・火照り
    // 4 部位で、余燼の背の火照り (熱 50 以上) のときに与える傷の倍率
    public static double flushedAttack(Player player) {
        return EmberArmorItem.piecesWorn(player) >= 4 && player instanceof ServerPlayer sp && EmberHeat.affects(sp) && EmberHeat.flushed(sp)
                ? 1 + EmberGear.SET4_FLUSHED_ATTACK : 1;
    }

    // 外套で、火の傷に掛ける値
    public static double mantleTaken(LivingEntity entity, DamageSource source) {
        return entity instanceof Player p && source.is(DamageTypeTags.IS_FIRE) && EmberGear.mantleWorn(p) ? EmberGear.MANTLE_FIRE_TAKEN : 1;
    }

    // 与える傷 (防具の前): 戦斧のくすぶり、鎧の火照り。受ける傷: 外套
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || event.getAmount() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        double m = mantleTaken(target, source);
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
            m *= flushedAttack(player);
            ItemStack hand = player.getMainHandItem();
            if (hand.getItem() instanceof EmberWarAxeItem && GearLevel.meets(player, hand) && smoldering(target)) {
                m *= 1 + EmberGear.WAR_AXE_SMOLDER_BONUS;
            }
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    // ---------------------------------------------------------------- 大鎚の亀裂
    // 打った先の床へ亀裂を走らせる。今すぐ赤い線を見せ、HAMMER_CRACK_DELAY tick 後に噴く
    public static void crack(ServerLevel level, LivingEntity attacker, LivingEntity target, Vec3 look, double length, float damage) {
        Vec3 dir = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 start = attacker.position();
        for (double d = 0.8; d <= length; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.1, p.z, 1, 0.1, 0.02, 0.1, 0);
            level.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.1, p.z, 2, 0.15, 0.02, 0.15, 0.01);
        }
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        CRACKS.add(new Crack(level, attacker, target, start, dir, length, damage, level.getGameTime() + EmberGear.HAMMER_CRACK_DELAY));
    }

    // 噴く: 線の上 (幅 1.6) の敵と、打った相手に当たる
    static int erupt(ServerLevel level, LivingEntity attacker, LivingEntity target, Vec3 start, Vec3 dir, double length, float damage) {
        DamageSource source = attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
        AABB box = new AABB(start, start.add(dir.scale(length))).inflate(1.2, 1.5, 1.2);
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != attacker && e.isAlive() && (e == target || e instanceof Enemy))) {
            Vec3 to = e.position().subtract(start).multiply(1, 0, 1);
            double along = to.dot(dir);
            double side = to.subtract(dir.scale(along)).length();
            if (e != target && (along < 0 || along > length || side > 0.8 + e.getBbWidth() * 0.5)) {
                continue;
            }
            e.invulnerableTime = 0;
            if (e.hurt(source, damage)) {
                hit++;
            }
            e.push(0, 0.35, 0);
            e.hurtMarked = true;
        }
        for (double d = 0.8; d <= length; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.2, p.z, 4, 0.15, 0.3, 0.15, 0.04);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.4, p.z, 1, 0.1, 0.2, 0.1, 0.02);
        }
        level.playSound(null, start.x, start.y, start.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.5f);
        return hit;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!CRACKS.isEmpty()) {
            Iterator<Crack> it = CRACKS.iterator();
            List<Crack> due = new ArrayList<>();
            while (it.hasNext()) {
                Crack c = it.next();
                if (c.level().getGameTime() >= c.due()) {
                    due.add(c);
                    it.remove();
                }
            }
            for (Crack c : due) {
                if (c.attacker().isAlive() && c.attacker().level() == c.level()) {
                    erupt(c.level(), c.attacker(), c.target(), c.start(), c.dir(), c.length(), c.damage());
                }
            }
        }
        if (!BRITTLE_UNTIL.isEmpty()) {
            Iterator<Map.Entry<LivingEntity, Long>> it = BRITTLE_UNTIL.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, Long> e = it.next();
                LivingEntity target = e.getKey();
                if (!target.isAlive() || target.level().getGameTime() >= e.getValue()) {
                    AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
                    if (armor != null) {
                        armor.removeModifier(BRITTLE);
                    }
                    it.remove();
                }
            }
        }
    }

    // ---------------------------------------------------------------- 火掻きの灰
    public static void brittle(LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EmberGear.POKER_BRITTLE_TICKS, 1), attacker);
        AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.addOrUpdateTransientModifier(new AttributeModifier(BRITTLE, EmberGear.POKER_BRITTLE_ARMOR, AttributeModifier.Operation.ADD_VALUE));
            BRITTLE_UNTIL.put(target, target.level().getGameTime() + EmberGear.POKER_BRITTLE_TICKS);
        }
    }

    // ---------------------------------------------------------------- 余燼の灯
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !Accessories.worn(player, EmberGearSetup.LANTERN.get())
                || player.getCooldowns().isOnCooldown(EmberGearSetup.LANTERN.get())) {
            return;
        }
        event.setCanceled(true);
        lanternFlare(player);
    }

    // 灯が燃え上がる: 体力 4 で踏みとどまり、まわり半径 3 の敵に 6 の傷と燃焼。待ち 600 秒
    public static int lanternFlare(ServerPlayer player) {
        player.setHealth(EmberGear.LANTERN_HEALTH);
        player.getCooldowns().addCooldown(EmberGearSetup.LANTERN.get(), EmberGear.LANTERN_COOLDOWN);
        ServerLevel level = player.serverLevel();
        double r = EmberGear.LANTERN_RADIUS;
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 1.5, r),
                e -> e != player && e.isAlive() && e instanceof Enemy && e.distanceToSqr(player) <= r * r)) {
            e.invulnerableTime = 0;
            if (e.hurt(player.damageSources().indirectMagic(player, player), EmberGear.LANTERN_DAMAGE)) {
                hit++;
            }
            smolder(e, EmberGear.LANTERN_BURN_TICKS);
        }
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 80, 1.2, 0.8, 1.2, 0.08);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1, player.getZ(), 12, 0.8, 0.6, 0.8, 0);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 0.7f);
        return hit;
    }

    // ---------------------------------------------------------------- 毎 tick (一式)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 9) {
            return;
        }
        int pieces = EmberArmorItem.piecesWorn(player);
        toggle(player, Attributes.ARMOR_TOUGHNESS, SET2, pieces >= 2 ? EmberGear.SET2_TOUGHNESS : 0, AttributeModifier.Operation.ADD_VALUE);
        if (pieces >= 4 && touchingFire(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EmberGear.SET4_FIRE_STRENGTH_TICKS, 0, true, true, true));
        }
        // 背中を出たら角杯の効き目も終わる (地上へ持ち越さない)
        if (player.level().dimension() != EmberIds.BACK && EmberHornItem.active(player)) {
            EmberHornItem.clear(player);
        }
    }

    // 火に触れている: 燃えている、溶岩・火・ソウルファイア・マグマの上、余燼の背で焦げかけ (熱 85 以上)
    public static boolean touchingFire(ServerPlayer player) {
        if (player.isOnFire() || player.isInLava() || player.getRemainingFireTicks() > 0) {
            return true;
        }
        BlockPos feet = player.blockPosition();
        var level = player.level();
        if (level.getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE) || level.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
            return true;
        }
        return EmberHeat.affects(player) && EmberHeat.get(player) >= EmberHeat.SCORCHING;
    }

    private static void toggle(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CRACKS.clear();
        BRITTLE_UNTIL.clear();
    }

    // 自己テスト用: 一式の tick を一度だけ流す
    static void tickForTest(ServerPlayer player) {
        player.tickCount = 9;
        onPlayerTick(new PlayerTickEvent.Post(player));
    }

    // 自己テスト用: 噴く前の亀裂の数
    static int pendingCracks() {
        return CRACKS.size();
    }
}

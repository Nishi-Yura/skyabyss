package jp.revati.skyabyss.item.storm;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessories;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 雷翼の装備のうち、持ち主の状態で変わる効果 (03 の 12章):
//  - 雷翼の羽衣: 2 部位で落下の傷 −50%。4 部位で「嵐の衣」(受ける傷 ×0.85、空中で ×0.8。耐性の効果があるときは重ねない) と空駆け
//  - 空駆け: 空中で跳躍のキーをもう一度押すと、前へ跳ぶ (待ち 8 秒、西の試練の中では効かない)。キーはクライアント (StormGearClient)、
//    確かめて動かすのはサーバー (dash)
//  - 雷翼の弓: 命中で感電 / 羽守の鉤槍: 空中の傷 ×1.3 / 嵐の護符: 次の 3 回に雷
//  - 嵐纏の足環: 落下の傷 −25%、滑空 (風切りの羽根・エリトラ) の速さ +15% (動きはプレイヤーの側で付ける)
//  - 嵐の目の羽根: まわり 6 ブロックへ飛んでくる飛び道具を、3 秒に一度そらす
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormGearEvents {
    // 足した雷の傷が、また護符・感電を呼ばないための印
    private static boolean zapping;

    private StormGearEvents() {
    }

    // ---------------------------------------------------------------- 一式
    // 4 部位の嵐の衣で受ける傷に掛ける値 (着ていない・耐性の効果があるときは 1)
    public static double setTaken(LivingEntity entity) {
        if (ThunderWingArmorItem.piecesWorn(entity) < 4 || entity.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            return 1;
        }
        return airborne(entity) ? StormGear.SET4_TAKEN_AIR : StormGear.SET4_TAKEN;
    }

    public static boolean airborne(LivingEntity entity) {
        return !entity.onGround() && !entity.isInWater() && !entity.isPassenger() && !entity.onClimbable();
    }

    // 落下の傷に掛ける値 (羽衣 2 部位 ×0.5、足環 ×0.75。重ねて ×0.375)
    public static double fallMultiplier(LivingEntity entity) {
        double m = 1;
        if (ThunderWingArmorItem.piecesWorn(entity) >= 2) {
            m *= StormGear.SET2_FALL;
        }
        if (entity instanceof Player p && Accessories.worn(p, StormGearSetup.ANKLET.get())) {
            m *= StormGear.ANKLET_FALL;
        }
        return m;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getNewDamage() <= 0) {
            return;
        }
        double m = setTaken(entity);
        if (m < 1) {
            event.setNewDamage((float) (event.getNewDamage() * m));
        }
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        double m = fallMultiplier(event.getEntity());
        if (m < 1) {
            event.setDamageMultiplier((float) (event.getDamageMultiplier() * m));
        }
    }

    // ---------------------------------------------------------------- 鉤槍 (空中の傷)
    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (zapping || !(event.getSource().getDirectEntity() instanceof Player attacker) || attacker == event.getEntity()) {
            return;
        }
        ItemStack hand = attacker.getMainHandItem();
        if (hand.getItem() instanceof WardenGlaiveItem && GearLevel.meets(attacker, hand) && WardenGlaiveItem.aerialHit(attacker, event.getEntity())) {
            event.setAmount((float) (event.getAmount() * WardenGlaiveItem.aerial(hand)));
            if (attacker.level() instanceof ServerLevel level) {
                LivingEntity t = event.getEntity();
                level.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY(0.6), t.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY(0.6), t.getZ(), 10, 0.3, 0.4, 0.3, 0.15);
            }
        }
    }

    // ---------------------------------------------------------------- 弓の感電・護符の雷
    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        if (zapping || event.getNewDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = event.getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || target == player) {
            return;
        }
        // 雷翼の弓の矢
        if (direct instanceof AbstractArrow arrow && arrow.getPersistentData().contains(ThunderWingBowItem.ARROW_TAG)) {
            int enhance = arrow.getPersistentData().getInt(ThunderWingBowItem.ARROW_TAG) - 1;
            shock(level, target, player, enhance >= ModGear.AWAKEN_LEVEL);
        }
        // 嵐の護符 (近接か、自分の飛び道具。同じ tick に何体斬っても 1 回だけ数える)
        boolean own = direct == player || (direct instanceof Projectile p && p.getOwner() == player);
        if (own && target.isAlive() && StormCharmItem.charges(player) > 0) {
            long now = level.getGameTime();
            if (player.getPersistentData().getLong(SkyAbyss.MODID + ":storm_charm_tick") != now) {
                player.getPersistentData().putLong(SkyAbyss.MODID + ":storm_charm_tick", now);
                StormCharmItem.spend(player);
                strike(level, target, player, StormGear.CHARM_DAMAGE);
            }
        }
    }

    // 感電: 当たった敵が 3 秒光り、まわりの別の敵へ小さな雷 (+5 で 2.5 を二体へ)
    public static void shock(ServerLevel level, LivingEntity target, Player owner, boolean awakened) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, StormGear.BOW_GLOW_TICKS, 0, false, false), owner);
        double r = StormGear.BOW_SHOCK_RADIUS;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r),
                e -> e != target && e != owner && e.isAlive() && e instanceof Enemy && e.distanceToSqr(target) <= r * r);
        near.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
        int n = Math.min(awakened ? 2 : 1, near.size());
        for (int i = 0; i < n; i++) {
            arc(level, target, near.get(i));
            zap(level, near.get(i), owner, (float) (awakened ? StormGear.BOW_SHOCK_AWAKENED : StormGear.BOW_SHOCK));
        }
        sparks(level, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.8f);
    }

    // 護符の雷: 上から細い稲妻が落ちる見た目 (本物の落雷は呼ばない。火も付かない)
    public static void strike(ServerLevel level, LivingEntity target, Player owner, float damage) {
        for (double y = 0; y < 3.5; y += 0.25) {
            double jitter = Math.sin(y * 7 + target.getId()) * 0.25;
            level.sendParticles(ParticleTypes.END_ROD, target.getX() + jitter, target.getY() + y, target.getZ() - jitter, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
        zap(level, target, owner, damage);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    // 雷の傷 (持ち主の魔法の傷にする。倒した手柄は持ち主へ)。無敵の間を消してから当てる
    public static void zap(ServerLevel level, LivingEntity target, Entity owner, float damage) {
        zapping = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(owner instanceof LivingEntity le ? le.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), damage);
        } finally {
            zapping = false;
        }
        sparks(level, target);
    }

    public static void sparks(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 14, 0.3, 0.45, 0.3, 0.25);
    }

    // 二体のあいだに走る稲妻の線 (折れ線の粒)
    public static void arc(ServerLevel level, LivingEntity from, LivingEntity to) {
        Vec3 a = from.position().add(0, from.getBbHeight() * 0.6, 0);
        Vec3 b = to.position().add(0, to.getBbHeight() * 0.6, 0);
        int steps = Math.max(4, (int) (a.distanceTo(b) * 4));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            double k = (i % 2 == 0 ? 0.18 : -0.18) * Math.sin(i * 1.3);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + k, p.y + k, p.z - k, 1, 0, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deflect(player);
        }
    }

    // 嵐の目の羽根: 6 ブロック以内で自分へ向かってくる (いちばん近づくときに 1.5 ブロック以内を通る) 飛び道具を跳ね返す
    public static boolean deflect(ServerPlayer player) {
        Item feather = StormGearSetup.EYE_FEATHER.get();
        if (player.getCooldowns().isOnCooldown(feather) || !Accessories.worn(player, feather)) {
            return false;
        }
        double r = StormGear.FEATHER_RADIUS;
        Vec3 me = player.position().add(0, player.getBbHeight() * 0.5, 0);
        for (Projectile p : player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(r), e -> e.isAlive())) {
            if (p.getOwner() == player || p.distanceToSqr(me) > r * r) {
                continue;
            }
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.04) {
                continue;
            }
            Vec3 to = me.subtract(p.position());
            double t = to.dot(v) / v.lengthSqr();
            if (t <= 0) {
                continue;
            }
            if (to.subtract(v.scale(t)).length() > 1.5) {
                continue;
            }
            ProjectileDeflection.REVERSE.deflect(p, player, player.getRandom());
            p.hurtMarked = true;
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 8, 0.2, 0.2, 0.2, 0.08);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY(), p.getZ(), 12, 0.25, 0.25, 0.25, 0.2);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 1.0f, 1.2f);
            player.getCooldowns().addCooldown(feather, StormGear.FEATHER_COOLDOWN);
            return true;
        }
        return false;
    }

    // 嵐纏の足環: 滑空を速める (プレイヤーの側。動きは手元で付けるので遅れて揺れない)
    //  - エリトラ: 横の空気抵抗 0.99 を 0.99 × K にして、行き着く速さを 1.15 倍にする (K = (1 − 0.01/1.15) / 0.99)
    private static final double ELYTRA_K = (1 - 0.01 / (1 + StormGear.ANKLET_GLIDE)) / 0.99;

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide || !p.isLocalPlayer() || !Accessories.worn(p, StormGearSetup.ANKLET.get())) {
            return;
        }
        Vec3 v = p.getDeltaMovement();
        if (p.isFallFlying()) {
            p.setDeltaMovement(v.x * ELYTRA_K, v.y, v.z * ELYTRA_K);
        }
    }

    // ---------------------------------------------------------------- 空駆け
    // 今この人が空駆けできるか (両側で同じ判定。待ちは羽衣の胸当ての品の待ちで、サーバーから送られる)
    public static boolean canDash(Player p) {
        return ThunderWingArmorItem.piecesWorn(p) >= 4 && !p.getCooldowns().isOnCooldown(StormGearSetup.CHESTPLATE.get())
                && !p.onGround() && !p.isFallFlying() && !p.getAbilities().flying && !p.isInWater() && !p.isInLava()
                && !p.isPassenger() && !p.onClimbable() && !p.isSpectator();
    }

    // 空駆けの向き (見ている向きの水平、前へ 1.15・上へ 0.32)
    public static Vec3 dashVelocity(Player p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize().scale(StormGear.DASH_FORWARD);
        return new Vec3(flat.x, StormGear.DASH_LIFT, flat.z);
    }

    public static boolean dash(ServerPlayer p) {
        if (!canDash(p)) {
            return false;
        }
        p.setDeltaMovement(dashVelocity(p));
        p.hurtMarked = true;
        p.fallDistance = 0;
        p.getCooldowns().addCooldown(StormGearSetup.CHESTPLATE.get(), StormGear.DASH_COOLDOWN);
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 14, 0.35, 0.15, 0.35, 0.06);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.8, p.getZ(), 16, 0.3, 0.5, 0.3, 0.2);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8f, 1.3f);
        return true;
    }

    // 持ち主 → サーバー: 空中で跳躍のキーを押した
    public record DashPayload() implements CustomPacketPayload {
        public static final Type<DashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_air_dash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DashPayload> STREAM_CODEC = StreamCodec.unit(new DashPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(DashPayload.TYPE, DashPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dash(player);
            }
        });
    }
}

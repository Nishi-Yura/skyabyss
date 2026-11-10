package jp.revati.skyabyss.item.extra;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.affix.AffixType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 巨神の粉の技と、土地の敵が落とす粉 (docs/design/41 の 3。2026-11-05 の粉と炉の頁 1-1・1-5)。
//   粉の技は、一つの装備で同じ粉の段の和が Powder.WEIGHT_FOR_ART 以上のとき働く。強さは段の和で伸びる (4 で ×1、8 で ×1.5、12 で ×2)。
//   武器: 4 回当てるごとに一度 (近接は満ちた一撃だけ数える。矢・技の当たりも数える)。出るときに粉の色の粒と音、傷の数字も粉の色
//     岩 地割れ  当てた敵のまわり 3 マスに衝撃 (攻撃力 ×0.8)、0.5 秒よろける
//     風 つむじ  当てた敵のまわり 3 マスの敵を引き寄せ、1 秒浮かせる (大物は浮かない)
//     潮 引き潮  与えた傷の 20% を、自分とまわり 6 マスの人で分けて癒す
//     灰 熾の印  3 秒燃やし、燃え尽きると爆ぜる (攻撃力 ×0.6、まわり 2 マス。地形は壊さない)
//     骨 骨の呪い 4 秒、その敵の受ける傷 +12% (誰の傷にも効く)
//   防具 (受けたとき。粉ごとに 8 秒に一度)
//     岩 岩肌 2 秒 受ける傷 −30% ・風 追い風 2 秒 速さ II ・潮 満ち潮 3 秒 再生 I ・灰 火の粉 打った相手を 3 秒燃やす ・骨 骨の棘 打った相手へ受けた傷の 30%
//   落ちる粉 (人が倒した敵): 土地の粉を土地の段で (丘陵 I・浜 II・高地 III・荒野 IV。都のまわりと外れの谷は無し)、深淵は骨の V
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PowderArts {
    public static final int HITS_PER_ART = 4;
    public static final int ARMOR_COOLDOWN = 160;
    // 武器の粉の技
    public static final double QUAKE_DAMAGE = 0.8;
    public static final double QUAKE_RADIUS = 3;
    public static final int QUAKE_STAGGER = 10;
    public static final double WHIRL_RADIUS = 3;
    public static final int WHIRL_FLOAT = 20;
    public static final double EBB_HEAL = 0.20;
    public static final double EBB_RADIUS = 6;
    public static final int BRAND_TICKS = 60;
    public static final double BRAND_DAMAGE = 0.6;
    public static final double BRAND_RADIUS = 2;
    public static final int CURSE_TICKS = 80;
    public static final double CURSE_TAKEN = 0.12;
    // 防具の粉の技
    public static final int SKIN_TICKS = 40;
    public static final double SKIN_CUT = 0.30;
    public static final int GUST_TICKS = 40;
    public static final int FLOOD_TICKS = 60;
    public static final int SPARK_SECONDS = 3;
    public static final double THORN_SHARE = 0.30;
    // 大物 (浮かせない)。combat/ShieldRules と同じ線
    static final double LARGE_HEALTH = 150;
    // 土地ごとの落ちる率と段 (ContinentMap の土地の番号の並び: 都のまわり・外れ・丘陵・浜・高地・荒野)
    static final float[] DROP_CHANCE = {0f, 0f, 0.06f, 0.08f, 0.10f, 0.12f};
    static final Element[] DROP_ELEMENT = {null, null, Element.STONE, Element.TIDE, Element.WIND, Element.ASH};
    public static final int[] DROP_TIER = {0, 0, 1, 2, 3, 4};
    static final float ABYSS_CHANCE = 0.10f;
    public static final int ABYSS_TIER = 5;

    // 粉の色 (粒と傷の数字。DamageNumbersClient と同じ並び)
    public static final int[] COLORS = {0xC9A27A, 0xB8F0E0, 0x5FC8F0, 0xFF8A3D, 0xD8D0FF};

    private static final Map<Player, Integer> HITS = new WeakHashMap<>();
    private static final Map<Player, Map<Element, Long>> ARMOR_READY = new WeakHashMap<>();
    private static final Map<LivingEntity, Brand> BRANDS = new WeakHashMap<>();
    private static final Map<LivingEntity, Curse> CURSES = new WeakHashMap<>();
    private static final Map<Player, Long> SKIN = new WeakHashMap<>();
    // 粉の技の傷を入れている最中 (数えない・数字を粉の色にする)
    private static final ThreadLocal<Element> IN_ART = new ThreadLocal<>();

    private record Brand(ServerPlayer owner, long end, float damage) {
    }

    private record Curse(long end, double mult) {
    }

    private PowderArts() {
    }

    // 粉の技の傷の最中なら、その粉 (DamageNumbers が数字の色に使う)
    @Nullable
    public static Element dealing() {
        return IN_ART.get();
    }

    // 段の和 → 強さ (4 で ×1、8 で ×1.5、12 以上で ×2)
    public static double strength(int weight) {
        return Math.min(2.0, 1.0 + Math.max(0, weight - Powder.WEIGHT_FOR_ART) / 8.0);
    }

    public static Vector3f color(Element e) {
        int c = COLORS[e.ordinal()];
        return new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
    }

    // ---------------------------------------------------------------- 数える
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel) || event.getNewDamage() <= 0) {
            return;
        }
        // 当てた側 (人の武器)
        if (event.getSource().getEntity() instanceof ServerPlayer p && target != p && IN_ART.get() == null && counts(p, event.getSource().getDirectEntity())) {
            onWeaponHit(p, target, event.getNewDamage());
        }
        // 受けた側 (人の防具)
        if (target instanceof ServerPlayer p && IN_ART.get() == null) {
            onArmorHit(p, event.getSource().getEntity() instanceof LivingEntity a && a != p ? a : null, event.getNewDamage());
        }
    }

    // 数える当たり: 満ちた近接の一撃・技 (近接の振りの外で、人が直に入れた傷)・その人の弓の矢
    static boolean counts(ServerPlayer p, @Nullable net.minecraft.world.entity.Entity direct) {
        if (direct == p) {
            return jp.revati.skyabyss.combat.MeleeHits.fullHit(p) || !jp.revati.skyabyss.combat.MeleeHits.weakHit(p);
        }
        return direct instanceof AbstractArrow arrow && arrow.getOwner() == p && GearFreedom.isBow(p.getMainHandItem());
    }

    // 武器の粉の技が働く粉と、その段の和 (必要 Lv に届いていなければ無し)
    public static Map<Element, Integer> weaponArts(Player p) {
        ItemStack hand = p.getMainHandItem();
        if (GearFreedom.socketSlot(hand.getItem()) != AffixType.Slot.WEAPON || !GearLevel.meets(p, hand)) {
            return Map.of();
        }
        return Powder.arts(hand);
    }

    // 防具 (盾を含む) の粉の技。同じ粉が二つの部位で働くなら、段の和の大きいほう
    public static Map<Element, Integer> armorArts(Player p) {
        Map<Element, Integer> out = new EnumMap<>(Element.class);
        for (ItemStack s : GearFreedom.worn(p)) {
            if (GearFreedom.socketSlot(s.getItem()) == AffixType.Slot.ARMOR) {
                Powder.arts(s).forEach((e, w) -> out.merge(e, w, Math::max));
            }
        }
        return out;
    }

    static void onWeaponHit(ServerPlayer p, LivingEntity target, float dealt) {
        Map<Element, Integer> arts = weaponArts(p);
        if (arts.isEmpty()) {
            return;
        }
        int n = HITS.merge(p, 1, Integer::sum);
        if (n % HITS_PER_ART != 0) {
            return;
        }
        HITS.put(p, 0);
        for (Map.Entry<Element, Integer> e : arts.entrySet()) {
            fireWeapon(e.getKey(), p, target, dealt, strength(e.getValue()));
        }
    }

    // 粉の技の傷の元: 手の武器の攻撃力 (弓は満月の一射の傷)
    public static double base(ServerPlayer p) {
        ItemStack hand = p.getMainHandItem();
        if (hand.getItem() instanceof jp.revati.skyabyss.item.ChapterBowItem bow) {
            return Math.ceil(6.0 * bow.multiplier(jp.revati.skyabyss.item.ModGear.enhance(hand)));
        }
        if (GearFreedom.isBow(hand)) {
            return 6.0;
        }
        return p.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    public static void fireWeapon(Element e, ServerPlayer p, LivingEntity target, float dealt, double s) {
        ServerLevel level = p.serverLevel();
        Vec3 c = target.position();
        switch (e) {
            case STONE -> {
                float dmg = (float) (base(p) * QUAKE_DAMAGE * s);
                for (LivingEntity v : foesNear(p, c, QUAKE_RADIUS)) {
                    artHurt(e, p, v, dmg);
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, QUAKE_STAGGER, 4), p);
                    // 打たれた敵の足もとに石の欠片 (目の前を覆わないよう少なく)
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), v.getX(), v.getY() + 0.1, v.getZ(), 6, 0.25, 0.05, 0.25, 0);
                }
                ring(level, c, QUAKE_RADIUS, new DustParticleOptions(color(e), 1.0f), 24);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 0.5f);
            }
            case WIND -> {
                int ticks = (int) Math.round(WHIRL_FLOAT * s);
                for (LivingEntity v : foesNear(p, c, WHIRL_RADIUS)) {
                    if (v.getMaxHealth() >= LARGE_HEALTH) {
                        continue;
                    }
                    Vec3 pull = c.subtract(v.position()).scale(0.25);
                    v.setDeltaMovement(pull.x, 0.45, pull.z);
                    v.hurtMarked = true;
                    v.addEffect(new MobEffectInstance(MobEffects.LEVITATION, ticks, 0), p);
                }
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 6;
                    double r = WHIRL_RADIUS * (1 - i / 30.0);
                    level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + 0.2 + i * 0.07, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.01);
                }
                level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.7f, 1.6f);
            }
            case TIDE -> {
                List<ServerPlayer> near = new ArrayList<>();
                for (ServerPlayer q : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(EBB_RADIUS), q -> q.isAlive() && !q.isSpectator())) {
                    near.add(q);
                }
                if (!near.contains(p)) {
                    near.add(p);
                }
                float each = (float) (dealt * EBB_HEAL * s / near.size());
                for (ServerPlayer q : near) {
                    q.heal(each);
                    level.sendParticles(ParticleTypes.SPLASH, q.getX(), q.getY() + 0.4, q.getZ(), 12, 0.35, 0.2, 0.35, 0.1);
                    level.sendParticles(new DustParticleOptions(color(e), 1.0f), q.getX(), q.getY() + 0.4, q.getZ(), 8, 0.35, 0.3, 0.35, 0);
                }
                level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + target.getBbHeight() * 0.6, c.z, 16, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.5f, 1.3f);
            }
            case ASH -> {
                target.igniteForSeconds(BRAND_TICKS / 20f);
                BRANDS.put(target, new Brand(p, level.getGameTime() + BRAND_TICKS, (float) (base(p) * BRAND_DAMAGE * s)));
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y + target.getBbHeight() * 0.6, c.z, 16, 0.25, 0.4, 0.25, 0.02);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
            case BONE -> {
                CURSES.put(target, new Curse(level.getGameTime() + CURSE_TICKS, 1 + CURSE_TAKEN * s));
                level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + target.getBbHeight() * 0.8, c.z, 10, 0.3, 0.3, 0.3, 0.02);
                level.sendParticles(new DustParticleOptions(color(e), 1.2f), c.x, c.y + target.getBbHeight() * 0.5, c.z, 14, 0.35, 0.5, 0.35, 0);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6f, 0.5f);
            }
        }
        // どの技にも、粉の色の小さな輪
        ring(level, c.add(0, 0.15, 0), 1.2, new DustParticleOptions(color(e), 1.3f), 16);
    }

    // 粉の技の傷 (数えない・数字は粉の色)
    static void artHurt(Element e, ServerPlayer p, LivingEntity v, float amount) {
        Element before = IN_ART.get();
        IN_ART.set(e);
        try {
            v.invulnerableTime = 0;
            v.hurt(p.damageSources().playerAttack(p), amount);
            v.invulnerableTime = 0;
        } finally {
            IN_ART.set(before);
        }
    }

    // 敵 (人・飼い慣らしたもの・名札の味方でない、生きているもの)
    static List<LivingEntity> foesNear(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r, 2, r),
                v -> v != p && v.isAlive() && !(v instanceof Player) && (v instanceof Enemy || v.getLastHurtByMob() == p
                        || v instanceof net.minecraft.world.entity.Mob m && m.getTarget() == p)
                        && v.position().distanceToSqr(c) <= r * r + 4);
    }

    static void ring(ServerLevel level, Vec3 c, double r, net.minecraft.core.particles.ParticleOptions particle, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            level.sendParticles(particle, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ---------------------------------------------------------------- 防具
    static void onArmorHit(ServerPlayer p, @Nullable LivingEntity attacker, float taken) {
        Map<Element, Integer> arts = armorArts(p);
        if (arts.isEmpty()) {
            return;
        }
        long now = p.level().getGameTime();
        Map<Element, Long> ready = ARMOR_READY.computeIfAbsent(p, k -> new EnumMap<>(Element.class));
        for (Map.Entry<Element, Integer> e : arts.entrySet()) {
            Long at = ready.get(e.getKey());
            if (at != null && now < at) {
                continue;
            }
            if (fireArmor(e.getKey(), p, attacker, taken, strength(e.getValue()))) {
                ready.put(e.getKey(), now + ARMOR_COOLDOWN);
            }
        }
    }

    // 防具の粉の技。働いたら true (相手の要る技で相手がいなければ働かない)
    public static boolean fireArmor(Element e, ServerPlayer p, @Nullable LivingEntity attacker, float taken, double s) {
        ServerLevel level = p.serverLevel();
        switch (e) {
            case STONE -> {
                SKIN.put(p, level.getGameTime() + Math.round(SKIN_TICKS * s));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.35, 0.05, 0.35, 0);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 0.9f, 0.6f);
            }
            case WIND -> {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, (int) Math.round(GUST_TICKS * s), 1));
                level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 12, 0.4, 0.1, 0.4, 0.05);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
            case TIDE -> {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) Math.round(FLOOD_TICKS * s), 0));
                level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.3, p.getZ(), 12, 0.35, 0.2, 0.35, 0.1);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.5f, 1.3f);
            }
            case ASH -> {
                if (attacker == null || !attacker.isAlive()) {
                    return false;
                }
                attacker.igniteForSeconds((float) (SPARK_SECONDS * s));
                level.sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.4f, 1.5f);
            }
            case BONE -> {
                if (attacker == null || !attacker.isAlive()) {
                    return false;
                }
                Element before = IN_ART.get();
                IN_ART.set(e);
                try {
                    attacker.hurt(p.damageSources().thorns(p), (float) (taken * THORN_SHARE * s));
                } finally {
                    IN_ART.set(before);
                }
                level.sendParticles(new DustParticleOptions(color(e), 1.1f), attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 12, 0.3, 0.4, 0.3, 0);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.5f, 0.6f);
            }
        }
        ring(level, p.position().add(0, 0.15, 0), 0.9, new DustParticleOptions(color(e), 1.1f), 12);
        return true;
    }

    // ---------------------------------------------------------------- 岩肌と骨の呪い (受ける傷)
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity v = event.getEntity();
        if (v.level().isClientSide || event.getNewDamage() <= 0) {
            return;
        }
        long now = v.level().getGameTime();
        Curse curse = CURSES.get(v);
        if (curse != null) {
            if (now < curse.end()) {
                event.setNewDamage((float) (event.getNewDamage() * curse.mult()));
            } else {
                CURSES.remove(v);
            }
        }
        if (v instanceof Player p) {
            Long skin = SKIN.get(p);
            if (skin != null) {
                if (now < skin) {
                    event.setNewDamage((float) (event.getNewDamage() * (1 - SKIN_CUT)));
                } else {
                    SKIN.remove(p);
                }
            }
        }
    }

    public static boolean cursed(LivingEntity v) {
        Curse c = CURSES.get(v);
        return c != null && v.level().getGameTime() < c.end();
    }

    public static boolean skinned(Player p) {
        Long s = SKIN.get(p);
        return s != null && p.level().getGameTime() < s;
    }

    // ---------------------------------------------------------------- 熾の印が爆ぜる
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (BRANDS.isEmpty()) {
            return;
        }
        List<Map.Entry<LivingEntity, Brand>> due = new ArrayList<>();
        for (Iterator<Map.Entry<LivingEntity, Brand>> it = BRANDS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<LivingEntity, Brand> e = it.next();
            LivingEntity v = e.getKey();
            if (v == null || v.isRemoved() || !v.isAlive() || e.getValue().owner().isRemoved()) {
                it.remove();
            } else if (v.level().getGameTime() >= e.getValue().end()) {
                it.remove();
                due.add(e);
            }
        }
        for (Map.Entry<LivingEntity, Brand> e : due) {
            burst(e.getKey(), e.getValue());
        }
    }

    static void burst(LivingEntity v, Brand b) {
        if (!(v.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 c = v.position();
        for (LivingEntity w : foesNear(b.owner(), c, BRAND_RADIUS)) {
            artHurt(Element.ASH, b.owner(), w, b.damage());
        }
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y + v.getBbHeight() * 0.5, c.z, 10, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y + v.getBbHeight() * 0.5, c.z, 30, 0.6, 0.5, 0.6, 0.06);
        ring(level, c, BRAND_RADIUS, new DustParticleOptions(color(Element.ASH), 1.4f), 20);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 0.7f);
    }

    // 熾の印の残り (自己テスト)
    public static boolean branded(LivingEntity v) {
        return BRANDS.containsKey(v);
    }

    // すぐに爆ぜさせる (自己テスト)
    public static void burstNow(LivingEntity v) {
        Brand b = BRANDS.remove(v);
        if (b != null) {
            burst(v, b);
        }
    }

    public static int hitCount(Player p) {
        return HITS.getOrDefault(p, 0);
    }

    public static void reset(Player p) {
        HITS.remove(p);
        ARMOR_READY.remove(p);
        SKIN.remove(p);
    }

    // ---------------------------------------------------------------- 落ちる粉
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || !(dead.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }
        ItemStack powder = landPowder(level, dead.getBlockX(), dead.getBlockZ());
        if (powder.isEmpty() || level.random.nextFloat() >= dropChance(level, dead.getBlockX(), dead.getBlockZ())) {
            return;
        }
        event.getDrops().add(new ItemEntity(level, dead.getX(), dead.getY() + 0.3, dead.getZ(), powder));
    }

    // 名のある敵: 人ごとに (その敵の種類ごとに) 初めて倒したとき、その土地の粉を一つ必ず。倒した人と、そばの組の人へ直に渡す
    public static final String NAMED_FIRST = SkyAbyss.MODID + ":powder_first_";

    @SubscribeEvent
    static void onNamedDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!dead.getTags().contains(jp.revati.skyabyss.world.continent.NamedFoes.TAG) || !(dead.level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        ItemStack powder = landPowder(level, dead.getBlockX(), dead.getBlockZ());
        if (powder.isEmpty()) {
            return;
        }
        String key = NAMED_FIRST + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        for (ServerPlayer p : jp.revati.skyabyss.item.wild.WildLoot.recipients(level, killer, dead)) {
            if (p.getPersistentData().getBoolean(key)) {
                continue;
            }
            p.getPersistentData().putBoolean(key, true);
            givePowder(p, powder.copy());
        }
    }

    // 直に渡す (旅袋か持ち物。入らなければ足もとに、その人だけが拾える)
    public static void givePowder(ServerPlayer p, ItemStack powder) {
        Component name = powder.getHoverName();
        ItemStack left = jp.revati.skyabyss.pouch.PouchRouting.give(p, powder);
        if (!left.isEmpty()) {
            ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY() + 0.3, p.getZ(), left);
            e.setTarget(p.getUUID());
            e.setPickUpDelay(10);
            p.level().addFreshEntity(e);
        }
        jp.revati.skyabyss.notice.Notice.gainLog(p, Component.translatable("message.skyabyss.powder.named", name));
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // その場所の土地の粉 (土地の段)。落ちない所は空
    public static ItemStack landPowder(ServerLevel level, int x, int z) {
        if (level.dimension() == Level.OVERWORLD) {
            int r = jp.revati.skyabyss.world.continent.ContinentMap.get().region(x, z);
            if (r >= 0 && r < DROP_ELEMENT.length && DROP_ELEMENT[r] != null) {
                return Powder.withTier(new ItemStack(jp.revati.skyabyss.registry.ModItems.socketShard(DROP_ELEMENT[r]).get()), DROP_TIER[r]);
            }
        } else if (level.dimension() == jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL) {
            return Powder.withTier(new ItemStack(jp.revati.skyabyss.registry.ModItems.socketShard(Element.BONE).get()), ABYSS_TIER);
        }
        return ItemStack.EMPTY;
    }

    static float dropChance(ServerLevel level, int x, int z) {
        if (level.dimension() == Level.OVERWORLD) {
            int r = jp.revati.skyabyss.world.continent.ContinentMap.get().region(x, z);
            return r >= 0 && r < DROP_CHANCE.length ? DROP_CHANCE[r] : 0;
        }
        return level.dimension() == jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL ? ABYSS_CHANCE : 0;
    }

    // 粉の技の名前 (powder_art.skyabyss.<粉>.weapon_name / armor_name)
    public static String artKey(Element e, boolean weapon) {
        return "powder_art.skyabyss." + e.id + (weapon ? ".weapon_name" : ".armor_name");
    }
}

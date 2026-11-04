package jp.revati.skyabyss.rpg.skill;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BreakGauge;
import jp.revati.skyabyss.combat.MeleeHits;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.tree.NodeEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 型の要の「動きが変わる効果」(docs/design/48 の 4.1。2026-11-04 の鍛冶師の手本を全職へ)。鍛冶師の三つは BreakGauge・BossOpenings・SkillEvents。
//   硝子の刃 (戦士・暗殺者): 満ちた近接の一撃が敵の背中から当たると、傷 ×GLASS_BACK
//   疾風の舞 (戦士・暗殺者): 主の技の予告の外へよけきるたびに、発動スキルの再使用まで −DANCE_DODGE_TICKS
//   満ち潮 (呪術師): マナが半分より多いときに使った技は、再使用まで ×FULL_TIDE_COOLDOWN (SkillManager.cast が fullTideScale を見る)
//   呪いの詠唱 (呪術師。節の鍵 hex_mark): 技で傷を与えた敵は HEX_TICKS のあいだ呪われ、人から受ける傷 ×(1 + hex_mark)
//   孤高の弦 (弓使い): 矢が敵の頭 (目の高さ − HEAD_BAND より上) に当たると、傷 ×LONE_HEAD
//   嵐呼び (弓使い。鍵 storm_call): 技を使うたびに、まわり STORM_RANGE の敵ひとりへ雷 (傷 = storm_call × (STORM_BASE + STORM_PER_LEVEL × レベル))
//   星の誓い (魔法使い): マナが最大の STAR_LOW を切っているあいだ、技の傷 ×STAR_LOW_DAMAGE
//   太陽の心臓 (魔法使い。鍵 sun_burst): 技で倒した敵が爆ぜ、まわり SUN_RANGE の敵へ、倒した敵の最大体力 × sun_burst の傷 (SUN_CAP まで)
//   潮渡り (魔法使い。鍵 blink_guard): 転移のあと BLINK_GUARD_TICKS のあいだ、受ける傷 ×(1 − blink_guard)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class KeystoneEffects {
    public static final float GLASS_BACK = 1.5f;
    public static final int DANCE_DODGE_TICKS = 30;
    public static final double FULL_TIDE_COOLDOWN = 0.7;
    public static final int HEX_TICKS = 80;
    public static final float LONE_HEAD = 1.4f;
    public static final double HEAD_BAND = 0.35;
    public static final double STORM_RANGE = 10;
    public static final double STORM_BASE = 3;
    public static final double STORM_PER_LEVEL = 0.25;
    public static final double STAR_LOW = 0.3;
    public static final float STAR_LOW_DAMAGE = 1.25f;
    public static final double SUN_RANGE = 3;
    public static final float SUN_CAP = 40;
    public static final int BLINK_GUARD_TICKS = 40;

    private record Hex(long until, double extra) {
    }

    private static final Map<UUID, Hex> HEX = new HashMap<>();
    private static final Map<UUID, Long> BLINK = new HashMap<>();
    private static boolean bursting;

    static {
        Telegraph.onDodge(KeystoneEffects::dodged);
    }

    private KeystoneEffects() {
    }

    static boolean has(Player p, SkillKeystone k) {
        return SkillBuild.has(SkillManager.get(p), k);
    }

    // ---------------------------------------------------------------- 口ごとの値 (自己テストからも見る)

    // 満ち潮: この人がいま技を使ったときの再使用までの掛け目
    public static double fullTideScale(Player p) {
        double max = SkillManager.maxMana(p);
        return has(p, SkillKeystone.FULL_TIDE) && max > 0 && SkillManager.get(p).mana > max * 0.5 ? FULL_TIDE_COOLDOWN : 1.0;
    }

    // 星の誓い: 技の傷の掛け目
    public static float starScale(Player p) {
        double max = SkillManager.maxMana(p);
        return SkillManager.get(p).knows(Skill.STAR_OATH) && max > 0 && SkillManager.get(p).mana < max * STAR_LOW ? STAR_LOW_DAMAGE : 1f;
    }

    // 孤高の弦: 矢が頭に当たったか
    public static boolean headshot(LivingEntity victim, double arrowY) {
        return victim.getBbHeight() > 0.9 && arrowY >= victim.getEyeY() - HEAD_BAND;
    }

    // 呪われているときの上乗せ (呪われていなければ 0)
    public static double hexExtra(LivingEntity victim) {
        Hex h = HEX.get(victim.getUUID());
        return h != null && victim.level().getGameTime() < h.until ? h.extra : 0;
    }

    public static boolean blinkGuarded(Player p) {
        Long until = BLINK.get(p.getUUID());
        return until != null && p.level().getGameTime() < until;
    }

    // ---------------------------------------------------------------- 出来事

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        // 潮渡り: 転移のあとの守り
        if (victim instanceof Player vp && blinkGuarded(vp)) {
            event.setAmount((float) (event.getAmount() * (1 - NodeEffects.sum(vp, "blink_guard"))));
        }
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        float m = 1f;
        // 硝子の刃: 背中への満ちた一撃
        if (direct == attacker && MeleeHits.fullHit(attacker) && has(attacker, SkillKeystone.GLASS_BLADE) && BreakGauge.behind(victim, attacker)) {
            m *= GLASS_BACK;
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
            }
        }
        // 孤高の弦: 頭への矢
        if (direct instanceof AbstractArrow arrow && has(attacker, SkillKeystone.LONE_STRING) && headshot(victim, arrow.getY())) {
            m *= LONE_HEAD;
            if (attacker instanceof ServerPlayer sp) {
                sp.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
        }
        Skill dealing = SkillActions.dealing();
        // 星の誓い: マナが少ないときの技
        if (dealing != null) {
            m *= starScale(attacker);
        }
        // 呪い: 呪われた敵は誰からの傷も増える
        m *= (float) (1 + hexExtra(victim));
        if (m != 1f) {
            event.setAmount(event.getAmount() * m);
        }
        // 呪いの詠唱: 技で傷を与えた敵を呪う (この一撃のあとから効く)
        double hex = dealing == null ? 0 : NodeEffects.sum(attacker, "hex_mark");
        if (hex > 0 && !(victim instanceof Player)) {
            HEX.put(victim.getUUID(), new Hex(victim.level().getGameTime() + HEX_TICKS, hex));
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.WITCH, victim.getX(), victim.getY() + victim.getBbHeight() + 0.2, victim.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    // 太陽の心臓: 技で倒した敵が爆ぜる
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        HEX.remove(dead.getUUID());
        if (bursting || dead instanceof Player || !(event.getSource().getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || SkillActions.dealing() == null || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        double share = NodeEffects.sum(player, "sun_burst");
        if (share <= 0) {
            return;
        }
        float dmg = (float) Math.min(SUN_CAP, dead.getMaxHealth() * share);
        level.sendParticles(ParticleTypes.FLAME, dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(), 24, SUN_RANGE * 0.3, 0.3, SUN_RANGE * 0.3, 0.08);
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 1.3f);
        bursting = true;
        try {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(SUN_RANGE),
                    e -> e != dead && e.isAlive() && e instanceof Enemy && !(e instanceof Player))) {
                e.hurt(level.damageSources().indirectMagic(player, player), dmg);
            }
        } finally {
            bursting = false;
        }
    }

    // SkillManager.cast が技を使えたあとに呼ぶ (嵐呼び・潮渡り)
    public static void afterCast(ServerPlayer player, Skill skill) {
        if (skill == Skill.BLINK && NodeEffects.sum(player, "blink_guard") > 0) {
            BLINK.put(player.getUUID(), player.level().getGameTime() + BLINK_GUARD_TICKS);
        }
        double storm = NodeEffects.sum(player, "storm_call");
        if (storm > 0 && player.level() instanceof ServerLevel level) {
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(STORM_RANGE),
                    e -> e.isAlive() && e instanceof Enemy && !(e instanceof Player));
            if (!near.isEmpty()) {
                LivingEntity target = near.get(level.random.nextInt(near.size()));
                float dmg = (float) (storm * (STORM_BASE + STORM_PER_LEVEL * Growth.get(player).level));
                target.hurt(level.damageSources().indirectMagic(player, player), dmg);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 30, 0.2, 1.5, 0.2, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.4f);
            }
        }
    }

    // 疾風の舞: よけきったら、発動スキルの再使用までが縮む
    static void dodged(ServerPlayer player) {
        if (!has(player, SkillKeystone.WIND_DANCE)) {
            return;
        }
        SkillData d = SkillManager.get(player);
        if (!d.cooldowns.isEmpty()) {
            d.cooldowns.replaceAll((k, v) -> v - DANCE_DODGE_TICKS);
            d.cooldowns.values().removeIf(v -> v <= 0);
            SkillManager.sync(player);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HEX.clear();
        BLINK.clear();
    }
}

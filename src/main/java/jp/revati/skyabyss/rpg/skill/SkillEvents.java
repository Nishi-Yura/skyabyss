package jp.revati.skyabyss.rpg.skill;

import java.util.Map;
import java.util.WeakHashMap;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 技の時間の流れと常時の技: クールダウンとマナの回復 (毎 tick)、返し刃・遠矢の心得・鷹の目、
// ログイン・復活・ディメンション移動での付け直しと同期、技の点が増えた知らせ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkillEvents {
    // 鷹の目の印 (印を付けられた敵 → 期限と上乗せ)。サーバーだけ。敵が消えれば自然に外れる
    private record Mark(long until, double bonus) {
    }

    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();

    private SkillEvents() {
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        SkillData d = SkillManager.get(player);
        d.tickCooldowns();
        SkillManager.regenTick(player, d, player.level().getGameTime());
        SkillManager.sendMana(player, false);
        if (player.tickCount % 20 == 7) {
            notifyPoints(player, d);
        }
    }

    // 技の点が増えたら一行で知らせる (レベルアップの知らせのすぐ後に出る)
    static void notifyPoints(ServerPlayer player, SkillData d) {
        int total = d.totalPoints(SkillManager.level(player));
        if (total > d.notifiedPoints) {
            int gained = total - d.notifiedPoints;
            boolean first = d.notifiedPoints == 0;
            d.notifiedPoints = total;
            if (first) {
                // はじめて世界に来たとき: 増えたのではなく、はじめから一つ覚えられることを知らせる
                player.sendSystemMessage(ModText.hint("message.skyabyss.skill.first_hint", Component.keybind("key.skyabyss.skills")));
                return;
            }
            player.sendSystemMessage(ModText.progress("message.skyabyss.skill.points_gained", gained, d.unspentPoints(SkillManager.level(player))));
            if (d.spentPoints() == 0 || d.unspentPoints(SkillManager.level(player)) >= 3) {
                player.sendSystemMessage(ModText.hint("message.skyabyss.skill.open_hint", Component.keybind("key.skyabyss.skills")));
            }
        } else if (total < d.notifiedPoints) {
            d.notifiedPoints = total;
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        SkillActions.tick(event.getServer());
        if (event.getServer().getTickCount() % 40 == 0) {
            long now = event.getServer().overworld().getGameTime();
            MARKS.values().removeIf(m -> m.until < now - 200);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SkillActions.clear();
        MARKS.clear();
    }

    // ---------------------------------------------------------------- 傷の上乗せ (受ける側の出来事で掛ける)
    // 敏捷の飛び道具の補正 (GrowthRewards) と同じ出来事。掛け算なので順は問わない
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        long now = victim.level().getGameTime();
        float amount = event.getAmount();
        // 戦っている印 (マナの回復が遅くなる)
        if (victim instanceof ServerPlayer hurtPlayer) {
            SkillManager.get(hurtPlayer).lastCombat = now;
        }
        if (attacker instanceof ServerPlayer player && attacker != victim) {
            SkillData d = SkillManager.get(player);
            d.lastCombat = now;
            Skill dealing = SkillActions.dealing();
            // 返し刃: 近接の攻撃 (技の斬撃を含む。驟雨は含まない)
            boolean melee = direct == player && dealing != Skill.SKY_RAIN;
            if (melee && d.riposteUntil >= now && d.knows(Skill.RIPOSTE)) {
                amount *= (float) (1 + SkillRules.riposteBonus(d.rank(Skill.RIPOSTE)));
                d.riposteUntil = 0;
                if (victim.level() instanceof ServerLevel level) {
                    level.sendParticles(Skills.RUNE_SPARK.get(), victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                            10, 0.3, 0.3, 0.3, 0.06);
                    level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Skills.SOUND_RIPOSTE.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
                }
            }
            // 遠矢の心得: 自分の飛び道具が、撃った所から離れた敵に当たった
            if (direct instanceof Projectile && d.knows(Skill.FAR_SHOT)) {
                double dist = player.distanceTo(victim);
                amount *= (float) (1 + SkillRules.farShotBonus(d.rank(Skill.FAR_SHOT), dist));
            }
        }
        // 鷹の目の印: 誰の攻撃でも (プレイヤーからの傷だけ)
        if (attacker instanceof Player) {
            Mark mark = MARKS.get(victim);
            if (mark != null && mark.until >= now) {
                amount *= (float) (1 + mark.bonus);
            }
        }
        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
    }

    // 実際に傷が入ったあと: 返し刃の構え、鷹の目の印
    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        long now = victim.level().getGameTime();
        // 返し刃: 生き物から傷を受けた
        if (victim instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity from && from != player) {
            SkillData d = SkillManager.get(player);
            if (d.knows(Skill.RIPOSTE)) {
                d.riposteUntil = now + SkillRules.RIPOSTE_WINDOW;
            }
        }
        // 鷹の目: 自分の矢が敵に当たった
        if (source.getDirectEntity() instanceof Projectile && source.getEntity() instanceof ServerPlayer shooter
                && SkillActions.isFoe(shooter, victim)) {
            int rank = SkillManager.get(shooter).rank(Skill.HAWK_EYE);
            if (rank > 0) {
                double bonus = SkillRules.markBonus(rank);
                Mark old = MARKS.get(victim);
                if (old == null || old.until < now) {
                    if (victim.level() instanceof ServerLevel level) {
                        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Skills.SOUND_MARK.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
                    }
                }
                // 重ならない: 高いほうの上乗せを残し、期限は延ばす
                MARKS.put(victim, new Mark(now + SkillRules.MARK_TICKS, old != null && old.until >= now ? Math.max(old.bonus, bonus) : bonus));
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, SkillRules.MARK_TICKS, 0, false, false), shooter);
            }
        }
    }

    // 自己テスト用
    static boolean marked(LivingEntity e, long now) {
        Mark m = MARKS.get(e);
        return m != null && m.until >= now;
    }

    // ---------------------------------------------------------------- 付け直しと同期
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillData d = SkillManager.get(player);
            d.sanitize(SkillManager.level(player));
            SkillManager.applyModifiers(player);
            SkillManager.settleMana(player);
            if (d.notifiedPoints == 0 && d.spentPoints() > 0) {
                d.notifiedPoints = d.totalPoints(SkillManager.level(player));
            }
            SkillManager.sync(player);
            SkillManager.sendMana(player, true);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillManager.applyModifiers(player);
            SkillData d = SkillManager.get(player);
            // 倒れて目を覚ましたら、マナも満タン (体力と同じ)
            if (!event.isEndConquered()) {
                d.mana = (float) SkillManager.maxMana(player);
            }
            d.riposteUntil = 0;
            SkillManager.sync(player);
            SkillManager.sendMana(player, true);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillManager.applyModifiers(player);
            SkillManager.sync(player);
            SkillManager.sendMana(player, true);
        }
    }
}

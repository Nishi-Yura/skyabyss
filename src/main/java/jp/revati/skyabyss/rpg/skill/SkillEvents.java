package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.notice.Notice;
import java.util.Map;
import java.util.WeakHashMap;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 技の時間の流れと常時の技: クールダウンとマナの回復 (毎 tick)、返し刃・遠矢の心得・鷹の目・炉の記憶・熾の心得・守り石の約束ほか、
// ログイン・復活・ディメンション移動での付け直しと同期、技の点が増えた知らせ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkillEvents {
    // 鷹の目の印 (印を付けられた敵 → 期限と上乗せ)。サーバーだけ。敵が消えれば自然に外れる
    private record Mark(long until, double bonus) {
    }

    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();
    // 守り石の約束の肩代わりの最中 (肩代わりした傷がまた肩代わりされないように)
    private static boolean oathBusy;

    private SkillEvents() {
    }

    static {
        // 予告をよけた (範囲の外で終わった) とき: 見切り・敏捷 15 の刻み目
        Telegraph.onDodge(SkillEvents::onDodge);
    }

    private static void onDodge(ServerPlayer player) {
        SkillData d = SkillManager.get(player);
        int foresight = d.rank(Skill.FORESIGHT);
        if (foresight > 0) {
            double bonus = SkillRules.foresightBonus(foresight) + (WeaponType.has(player, WeaponType.FAST) ? SkillRules.FORESIGHT_FAST_BONUS : 0);
            SkillEffects.timedMod(player, Attributes.ATTACK_SPEED, "foresight", bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, SkillRules.FORESIGHT_TICKS);
        }
        if (Breakpoints.reached(player, jp.revati.skyabyss.rpg.GrowthRules.Stat.AGILITY, Breakpoints.FIRST)) {
            SkillEffects.timedMod(player, Attributes.MOVEMENT_SPEED, "agility_15", Breakpoints.AGILITY_15_MOVE,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, Breakpoints.AGILITY_15_TICKS);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        SkillData d = SkillManager.get(player);
        d.tickCooldowns();
        long now = player.level().getGameTime();
        SkillPassives.tick(player, d, now);
        SkillManager.regenTick(player, d, now);
        SkillManager.sendMana(player, false);
        if (player.tickCount % 20 == 7) {
            notifyPoints(player, d);
        }
    }

    // 技の点が増えたら一行で知らせる (レベルアップの知らせのすぐ後に出る)
    static void notifyPoints(ServerPlayer player, SkillData d) {
        int total = d.totalPoints(SkillManager.level(player));
        // はじめの案内は、開幕の暗転・帳面の知らせと重ならないよう、書を持ち、Lv2 (鐘の宿り) に着くか、20 分たってから (17 の 5.2)
        if (d.notifiedPoints == 0 && total > 0 && !firstHintReady(player)) {
            return;
        }
        if (total > d.notifiedPoints) {
            int gained = total - d.notifiedPoints;
            boolean first = d.notifiedPoints == 0;
            d.notifiedPoints = total;
            if (first) {
                // はじめて世界に来たとき: 増えたのではなく、はじめから一つ覚えられることを知らせる
                Notice.hint(player, "message.skyabyss.skill.first_hint", Component.keybind("key.skyabyss.skills"));
                return;
            }
            Notice.levelUp(player, "message.skyabyss.skill.points_gained", gained, d.unspentPoints(SkillManager.level(player)));
            if (d.spentPoints() == 0 || d.unspentPoints(SkillManager.level(player)) >= 3) {
                Notice.hint(player, "message.skyabyss.skill.open_hint", Component.keybind("key.skyabyss.skills"));
            }
        } else if (total < d.notifiedPoints) {
            d.notifiedPoints = total;
        }
    }

    // 最初の技の案内を出してよいか。Lv1 の 1 点で、すぐ一つ覚えられる (04 の 10.3)
    static boolean firstHintReady(ServerPlayer player) {
        if (!jp.revati.skyabyss.lore.Lore.hasBook(player)) {
            return player.tickCount >= FIRST_HINT_FALLBACK;
        }
        return SkillManager.level(player) >= 2 || player.tickCount >= FIRST_HINT_FALLBACK;
    }

    // 書を持たない人にも、この時間 (20 分) たてば案内を出す
    static final int FIRST_HINT_FALLBACK = 20 * 60 * 20;

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

    // ---------------------------------------------------------------- 矢が放たれた
    // 静かな弦 (3 秒動かずに引いた矢 +25%)、灰かぶりの敵の飛び道具は遅くなる
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk() || !(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        if (projectile.getOwner() instanceof ServerPlayer shooter) {
            SkillData d = SkillManager.get(shooter);
            if (projectile instanceof AbstractArrow arrow && d.knows(Skill.STILL_STRING) && d.stillTicks >= SkillRules.STILL_TICKS) {
                arrow.setBaseDamage(arrow.getBaseDamage() * (1 + SkillRules.STILL_BONUS));
                d.stillTicks = 0;
                if (shooter.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.END_ROD, shooter.getX(), shooter.getEyeY(), shooter.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                }
            }
        }
        if (projectile.getOwner() instanceof LivingEntity owner && !(owner instanceof Player) && SkillEffects.ashed(owner)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(1 - SkillRules.ASH_PROJECTILE));
        }
    }

    // ---------------------------------------------------------------- 押し出される強さ
    // 岩呼び: 狙いを引いた敵の攻撃を受けても、押されにくい (ノックバック 0.5 倍)
    @SubscribeEvent
    static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SkillManager.get(player).knows(Skill.ROCK_CALL)
                && SkillEffects.taunting(player, 7)) {
            event.setStrength((float) (event.getStrength() * SkillRules.ROCK_CALL_KNOCKBACK));
        }
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
        boolean riderless = SkillActions.riderless();
        // 戦っている印 (マナの回復が遅くなる)
        if (victim instanceof ServerPlayer hurtPlayer) {
            SkillData vd = SkillManager.get(hurtPlayer);
            vd.lastCombat = now;
            // 熾の心得・耐久 30: 火の傷、(毒の傷) を減らす
            boolean fire = source.is(DamageTypeTags.IS_FIRE);
            boolean poison = source.is(DamageTypes.MAGIC) && hurtPlayer.hasEffect(MobEffects.POISON);
            if (fire) {
                amount *= (float) (1 - SkillRules.emberHeartReduction(vd.rank(Skill.EMBER_HEART)));
                // 炉の手 (灰の鍛え人の天性)
                if (vd.callingTree() == SkillTree.ASH) {
                    amount *= (float) (1 - SkillRules.TRAIT_ASH_FIRE);
                }
            }
            if (fire || poison) {
                if (Breakpoints.reached(hurtPlayer, jp.revati.skyabyss.rpg.GrowthRules.Stat.VITALITY, Breakpoints.SECOND)) {
                    amount *= (float) (1 - Breakpoints.VITALITY_30_FIRE);
                }
            }
            // 重い声: 狙いを引いているあいだ、受ける傷 −10%
            if (vd.tauntRune == 2 && vd.tauntUntil >= now && SkillEffects.taunting(hurtPlayer, 16)) {
                amount *= (float) (1 - SkillRules.HEAVY_VOICE_REDUCTION);
            }
            // 守り石の約束: 近くの要の持ち主が、傷の 10% (1 回 2 点まで) を肩代わりする
            if (!oathBusy && amount > 0 && attacker != hurtPlayer) {
                ServerPlayer keeper = null;
                for (ServerPlayer o : SkillEffects.allies(hurtPlayer, SkillRules.ALLY_RANGE)) {
                    if (SkillManager.get(o).knows(Skill.STONE_OATH) && (keeper == null || o.distanceToSqr(hurtPlayer) < keeper.distanceToSqr(hurtPlayer))) {
                        keeper = o;
                    }
                }
                if (keeper != null) {
                    float shared = Math.min(SkillRules.OATH_CAP, amount * (float) SkillRules.OATH_SHARE);
                    amount -= amount * (float) SkillRules.OATH_SHARE;
                    oathBusy = true;
                    try {
                        keeper.invulnerableTime = 0;
                        keeper.hurt(keeper.damageSources().generic(), shared);
                    } finally {
                        oathBusy = false;
                    }
                }
            }
        }
        if (attacker instanceof ServerPlayer player && attacker != victim) {
            SkillData d = SkillManager.get(player);
            d.lastCombat = now;
            Skill dealing = SkillActions.dealing();
            // 近接の攻撃 (技の斬撃を含む。驟雨・出血・炎の上乗せ・稲光は含まない)
            boolean melee = direct == player && dealing != Skill.SKY_RAIN && !riderless;
            // 返し刃
            if (melee && d.riposteUntil >= now && d.knows(Skill.RIPOSTE)) {
                amount *= (float) (1 + SkillRules.riposteBonus(d.rank(Skill.RIPOSTE)));
                d.riposteUntil = 0;
                if (victim.level() instanceof ServerLevel level) {
                    level.sendParticles(Skills.RUNE_SPARK.get(), victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                            10, 0.3, 0.3, 0.3, 0.06);
                    level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Skills.SOUND_RIPOSTE.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
                }
            }
            // 炉の手 (灰の鍛え人の天性): 武器の強化の上乗せが 1 割増える
            if (melee && d.callingTree() == SkillTree.ASH) {
                net.minecraft.world.item.ItemStack held = player.getMainHandItem();
                if (held.getItem() instanceof jp.revati.skyabyss.item.ModGear g && g.enhanceable()) {
                    amount += (float) (SkillRules.TRAIT_ASH_ENHANCE * jp.revati.skyabyss.item.GearEvents.enhanceAttack(held.getItem(), jp.revati.skyabyss.item.ModGear.enhance(held)));
                }
            }
            // 骨の目 (骨の射手の天性): 飛び道具の傷 +5%
            if (direct instanceof Projectile && d.callingTree() == SkillTree.BONE) {
                amount *= (float) (1 + SkillRules.TRAIT_BONE_DAMAGE);
            }
            // 炉の記憶: 溜まった熾を使い切る
            if (melee && d.embers > 0) {
                amount *= (float) (1 + SkillRules.FORGE_MEMORY_BONUS * d.embers);
                d.embers = 0;
            }
            // 岩の返り (岩呼びの刻印): 引いた敵の攻撃を受けたあとの次の近接
            if (melee && d.rockReturn > 0) {
                amount *= (float) (1 + d.rockReturn);
                d.rockReturn = 0;
            }
            // 遠矢の心得: 自分の飛び道具が、撃った所から離れた敵に当たった
            if (direct instanceof Projectile && d.knows(Skill.FAR_SHOT)) {
                double dist = player.distanceTo(victim);
                amount *= (float) (1 + SkillRules.farShotBonus(d.rank(Skill.FAR_SHOT), dist));
            }
            // 射返し (跳び退きの刻印): 直後の飛び道具
            if (direct instanceof Projectile && d.returnFireUntil >= now) {
                amount *= (float) (1 + SkillRules.RETURN_FIRE_BONUS);
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

    // 実際に傷が入ったあと: 返し刃の構え、鷹の目の印、焼き入れの炎、稲光、逃げ足、岩の返りの用意
    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        long now = victim.level().getGameTime();
        if (victim instanceof ServerPlayer player) {
            SkillData d = SkillManager.get(player);
            // 返し刃: 生き物から傷を受けた
            if (source.getEntity() instanceof LivingEntity from && from != player) {
                if (d.knows(Skill.RIPOSTE)) {
                    d.riposteUntil = now + SkillRules.RIPOSTE_WINDOW;
                }
                // 岩の返り: 狙いを引いた敵の攻撃を受けた
                if (d.tauntRune == 3 && d.tauntUntil >= now && SkillEffects.tauntedBy(from, player)) {
                    d.rockReturn = SkillRules.ROCK_RETURN_BONUS;
                }
            }
            // 逃げ足: 体力が 3❤ 以下になったとき、少しのあいだ速くなる (20 秒に 1 度)
            int fleet = d.rank(Skill.FLEET_FOOT);
            if (fleet > 0 && player.getHealth() - event.getNewDamage() <= SkillRules.FLEET_HEALTH && now >= d.fleetReadyAt && player.isAlive()) {
                d.fleetReadyAt = now + SkillRules.FLEET_COOLDOWN;
                SkillEffects.timedMod(player, Attributes.MOVEMENT_SPEED, "fleet_foot", SkillRules.fleetBonus(fleet),
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE, SkillRules.FLEET_TICKS);
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(Skills.SKY_MOTE.get(), player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.4, 0.1, 0.4, 0.03);
                }
            }
        }
        if (source.getEntity() instanceof ServerPlayer attackerPlayer && !SkillActions.riderless()) {
            SkillData ad = SkillManager.get(attackerPlayer);
            // 焼き入れ: 熾の武器の近接に、炎の傷が乗る
            if (ad.temperUntil >= now && source.getDirectEntity() == attackerPlayer && SkillActions.dealing() != Skill.SKY_RAIN
                    && !(victim instanceof Player) && victim != attackerPlayer) {
                double mult = ad.temperMult + (WeaponType.has(attackerPlayer, WeaponType.HEAVY) ? SkillRules.TEMPER_HEAVY_BONUS : 0);
                float extra = (float) (attackerPlayer.getAttributeValue(Attributes.ATTACK_DAMAGE) * mult * SkillEffects.damagePower(attackerPlayer, Skill.TEMPER));
                if (extra > 0) {
                    SkillActions.hitPlain(attackerPlayer, victim, extra, Skill.TEMPER);
                    victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), SkillRules.TEMPER_BURN_TICKS));
                    if (victim.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 6, 0.25, 0.25, 0.25, 0.02);
                    }
                }
            }
        }
        // 稲光 (雲穿ちの刻印): 貫いた敵の近くの別の敵へ、小さな雷
        if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getTags().contains(SkillActions.LIGHTNING_TAG)
                && source.getEntity() instanceof ServerPlayer shooter && SkillActions.isFoe(shooter, victim)) {
            LivingEntity next = null;
            for (LivingEntity e : victim.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(SkillRules.LIGHTNING_RANGE),
                    e -> e != victim && SkillActions.isFoe(shooter, e))) {
                if (next == null || e.distanceToSqr(victim) < next.distanceToSqr(victim)) {
                    next = e;
                }
            }
            if (next != null && victim.level() instanceof ServerLevel level) {
                SkillActions.hitPlain(shooter, next, event.getNewDamage() * (float) SkillRules.LIGHTNING_SCALE, Skill.CLOUD_PIERCE);
                Vec3 a = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
                Vec3 b = next.position().add(0, next.getBbHeight() * 0.6, 0);
                for (int i = 0; i <= 8; i++) {
                    Vec3 p = a.lerp(b, i / 8.0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        }
        // 鷹の目: 自分の矢が敵に当たった
        if (source.getDirectEntity() instanceof Projectile && source.getEntity() instanceof ServerPlayer shooter
                && SkillActions.isFoe(shooter, victim)) {
            int rank = SkillManager.get(shooter).rank(Skill.HAWK_EYE);
            if (rank > 0) {
                double bonus = SkillRules.markBonus(rank) + (WeaponType.has(shooter, WeaponType.RANGED) ? SkillRules.HAWK_RANGED_BONUS : 0);
                mark(victim, bonus, now, SkillRules.MARK_TICKS, shooter);
            }
        }
    }

    // 印を付ける (鷹の目・合図)。重ならない: 高いほうの上乗せを残し、期限は延ばす
    public static void mark(LivingEntity victim, double bonus, long now, int ticks, LivingEntity source) {
        Mark old = MARKS.get(victim);
        if (old == null || old.until < now) {
            if (victim.level() instanceof ServerLevel level) {
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), Skills.SOUND_MARK.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
            }
        }
        MARKS.put(victim, new Mark(now + ticks, old != null && old.until >= now ? Math.max(old.bonus, bonus) : bonus));
        victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), source);
    }

    // 自己テスト用
    static boolean marked(LivingEntity e, long now) {
        Mark m = MARKS.get(e);
        return m != null && m.until >= now;
    }

    static double markBonus(LivingEntity e, long now) {
        Mark m = MARKS.get(e);
        return m != null && m.until >= now ? m.bonus : 0;
    }

    // 骨の目: 撃った矢の 25% は消えない (弓で撃った 1 本を次の tick に返す)
    @SubscribeEvent
    static void onArrowLoose(net.neoforged.neoforge.event.entity.player.ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || event.getCharge() < 3) {
            return;
        }
        if (SkillManager.get(player).callingTree() != SkillTree.BONE || player.getRandom().nextDouble() >= SkillRules.TRAIT_BONE_KEEP) {
            return;
        }
        net.minecraft.world.item.ItemStack ammo = player.getProjectile(event.getBow());
        if (ammo.isEmpty()) {
            return;
        }
        net.minecraft.world.item.ItemStack back = ammo.copyWithCount(1);
        jp.revati.skyabyss.story.ch1.Tasks.later(1, () -> {
            if (!player.hasDisconnected() && !player.isRemoved() && !player.getInventory().add(back)) {
                player.drop(back, false);
            }
        });
    }

    // ---------------------------------------------------------------- 付け直しと同期
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillData d = SkillManager.get(player);
            d.sanitize(SkillManager.level(player));
            SkillManager.applyModifiers(player);
            // ここではまだ上限まで切り詰めない。装備の特性 (共鳴・静心) の最大マナは最初の tick で付くので、
            // 上限を超えていれば regenTick がそのとき収める
            if (d.mana < 0) {
                SkillManager.settleMana(player);
            }
            if (d.notifiedPoints == 0 && d.spentPoints() > 0) {
                d.notifiedPoints = d.totalPoints(SkillManager.level(player));
            }
            SkillManager.sync(player);
            SkillManager.sendMana(player, true);
            // 天職を選べるのに、まだ選んでいない人 (あとでにした人・古いセーブ)
            SkillCalling.offerSoon(player, SkillCalling.LOGIN_DELAY, false);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillManager.applyModifiers(player);
            SkillData d = SkillManager.get(player);
            // 倒れて目を覚ましたら、マナも満タン (体力と同じ)
            // 装備の最大マナが付く次の tick で満たす (regenTick は負の値を「満タンにする」と読む)
            if (!event.isEndConquered()) {
                d.mana = -1;
            }
            d.riposteUntil = 0;
            d.embers = 0;
            d.temperUntil = 0;
            d.rockReturn = 0;
            SkillManager.sync(player);
            if (d.mana >= 0) {
                SkillManager.sendMana(player, true);
            }
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

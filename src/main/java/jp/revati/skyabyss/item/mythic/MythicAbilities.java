package jp.revati.skyabyss.item.mythic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.rpg.Rpg;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillEffects;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.story.ch6.gear.MoonGearEvents;
import jp.revati.skyabyss.story.finale.gear.DivineGearSetup;
import jp.revati.skyabyss.tower.gear.TowerGearSetup;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

// 神器の武器 18 本の「大きな能力」(Wynncraft のミシックの立ち位置。docs/design/03 の 13.4。2026-11-02 の 00 の 340 A・344)。
// どれも、強い代わりに欠点を一つ持つ。手に持って必要 Lv に届いているときだけ効く。終章の一本目の技の書き換え (LegendRewrites) はそのまま。
//   戦士   天穹の剣「天の手」/ 羽の番人の大剣「番人の誓い」/ あいだの大剣「天と淵の天秤」
//   鍛冶師 七つ鐘の大鎚「七つ鐘」/ 灰打ちの大鎚「溶かし打ち」/ 引き合いの鎚「引力」
//   魔法使い 暁の灯杖「命の灯」/ 雲の瞼の杖「凝視」/ 落ちる月の杖「新しい月」
//   弓使い 巡礼の羽弓「羽の嵐」/ 霞鳥の弓「霞隠れ」/ 側の目の弓「凝視の矢」
//   暗殺者 巻き戻りの短剣「巻き戻し」/ 灯蛾の短剣「灯の粉」/ 追う印の短剣「追う印」
//   呪術師 約束の鈴杖「約束」/ 鐘守の鈴杖「沈黙の鐘」/ 合掌の鈴杖「合掌」
// 目覚め (MythicAwakening): 段 1 から大きな能力の芯の数値に B (1.15、段 3 で 1.30)、段 2 から欠点に D (0.5) を掛ける。
// 数値はこの class の定数。技からは onSkillCast (SkillManager.cast)・manaScale / cooldownExtra / payWithLife (SkillManager)・
// noPierce (SkillActions.pierce)・powerScale (SkillEffects.damagePower) を呼ぶ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MythicAbilities {
    private MythicAbilities() {
    }

    // ---------------------------------------------------------------- 数値
    static final int SKY_HANDS = 5;
    static final double SKY_HAND = 0.8;
    static final int SKY_COOLDOWN = 40;
    static final double WARDEN_OATH = 0.4;
    static final double WARDEN_TAKEN = 1.10;
    static final double BALANCE_HIGH = 0.30;
    static final double BALANCE_DRAIN = 0.25;
    static final double BALANCE_HEAL = 0.5;
    static final int BELLS = 7;
    static final double BELL_RADIUS = 6;
    static final double BELL_MULT = 3.0;
    static final double BELL_ASPEED = -0.15;
    static final double MELT_STEP = 0.06;
    static final int MELT_MAX = 5;
    static final int MELT_DECAY = 80;
    static final double ASH_ARMOR = -0.15;
    static final int PULL_EVERY = 4;
    static final double PULL_RADIUS = 8;
    static final double PULL_MULT = 2.0;
    static final double PULL_MOVE = -0.10;
    static final double LAMP_LIFE = 0.01;
    static final double LAMP_MANA = -0.30;
    static final double GAZE_STEP = 0.08;
    static final int GAZE_MAX = 5;
    static final double GAZE_RANGE = 24;
    static final double EYELID_MOVE = -0.10;
    static final int MOON_EVERY = 3;
    static final double MOON_RADIUS = 4;
    static final double MOON_MULT = 2.5;
    static final double MOON_MANA = 1.20;
    static final int FEATHERS = 12;
    static final double FEATHER = 0.25;
    static final double PLUME_ARROW = 0.85;
    static final int MIST_TICKS = 80;
    static final double MIST_RADIUS = 3;
    static final double MIST_ARROW = 1.20;
    static final double MIST_HEALTH = -0.15;
    static final double EYE_STEP = 0.10;
    static final int EYE_MAX = 10;
    static final int REWIND_TICKS = 30;
    static final double REWIND_POWER = 0.5;
    static final double REWIND_TAKEN = 1.15;
    static final int DUST_MAX = 5;
    static final double DUST_STEP = 0.30;
    static final double MOTH_FRONT = 0.80;
    static final double SIGIL_ON = 1.40;
    static final double SIGIL_OFF = 0.80;
    static final double PROMISE_RANGE = 8;
    static final double PROMISE_SHARE = 0.25;
    static final double PROMISE_OWN = 0.80;
    static final int KEEPER_EVERY = 4;
    static final double KEEPER_RADIUS = 6;
    static final int KEEPER_TICKS = 40;
    static final double KEEPER_ASPEED = -0.20;
    static final int PRAYERS = 5;
    static final double CLAP_RANGE = 8;
    static final double CLAP_HEAL = 0.30;
    static final int CLAP_WARD_TICKS = 120;
    static final double CLAP_WARD = 0.80;
    static final double CLAP_OWN = 0.75;

    static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.45f), 1.1f);
    static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62f, 0.36f, 1.0f), 1.1f);
    static final DustParticleOptions MIST = new DustParticleOptions(new Vector3f(0.78f, 0.86f, 1.0f), 1.3f);
    static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.22f, 0.18f), 1.0f);

    // ---------------------------------------------------------------- 人ごとの数 (保存しない)
    static final class State {
        int hands;
        double oath;
        int bells;
        int pulls;
        int moonCount;
        long moonTick;
        int feathers;
        int eyeTarget = -1;
        int eyeCount;
        int gazeTarget = -1;
        double gaze;
        int sigil = -1;
        int keeper;
        int prayers;
        long clapWardUntil;
        long skyTick;
        boolean echoing;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    // 灰打ちの溶け (敵ごと: 重ね・最後に打った tick)、灯蛾の粉 (敵ごとの数)
    private static final Map<LivingEntity, long[]> MELT = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> DUST = new WeakHashMap<>();
    // 霞鳥の霞
    record Mist(ServerLevel level, Vec3 pos, UUID owner, long until) {
    }

    private static final List<Mist> MISTS = new ArrayList<>();
    // 足した傷・肩代わり・癒しを入れているあいだ (そこからまた効き方を起こさない)
    private static boolean inner;
    private static double echoScale = 1;

    static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), u -> new State());
    }

    // 手に持って、必要 Lv に届いているか
    static boolean holds(Player p, Item item) {
        ItemStack hand = p.getMainHandItem();
        return hand.getItem() == item && GearLevel.meets(p, hand);
    }

    static boolean arrowFrom(Entity direct, Player p, Item bow) {
        return direct instanceof AbstractArrow arrow && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() == bow
                && GearLevel.meets(p, arrow.getWeaponItem());
    }

    // 目覚めの倍率: 能力 (B) と欠点 (D)。手の品から
    static double B(Player p) {
        return MythicAwakening.boost(p);
    }

    static double D(Player p) {
        return MythicAwakening.drawback(p);
    }

    // 矢の能力と欠点は、放った弓の品から
    static double arrowB(Entity direct) {
        return direct instanceof AbstractArrow arrow && arrow.getWeaponItem() != null ? MythicAwakening.boost(arrow.getWeaponItem()) : 1;
    }

    static double arrowD(Entity direct) {
        return direct instanceof AbstractArrow arrow && arrow.getWeaponItem() != null ? MythicAwakening.drawback(arrow.getWeaponItem()) : 1;
    }

    static float attack(Player p) {
        return (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    // ---------------------------------------------------------------- 技から呼ばれる
    // 技の傷に掛ける倍率 (巻き戻しのもう一度は半分)
    public static double powerScale() {
        return echoScale;
    }

    // 力を scale 倍にして run を流す (アスペクトの「もう一度」。rpg/aspect/Aspects)
    public static void withPowerScale(double scale, Runnable run) {
        double before = echoScale;
        echoScale = before * scale;
        try {
            run.run();
        } finally {
            echoScale = before;
        }
    }

    // 技のマナに掛ける倍率 (落ちる月の杖: +20%)
    public static double manaScale(Player p, Skill skill) {
        return holds(p, TowerGearSetup.STAFF.get()) ? 1 + (MOON_MANA - 1) * D(p) : 1;
    }

    // 技の待ちに足す tick (天穹の剣: 巨神斬り +2 秒)
    public static int cooldownExtra(Player p, Skill skill) {
        return skill == Skill.TITAN_CLEAVE && holds(p, DivineGearSetup.SWORD.get()) ? (int) Math.round(SKY_COOLDOWN * D(p)) : 0;
    }

    // 雲穿ちが貫かない (側の目の弓)。目覚めの段 2 からは、貫く数が半分 (pierceLimit)
    public static boolean noPierce(Player p) {
        return holds(p, TowerGearSetup.BOW.get()) && D(p) >= 1;
    }

    public static int pierceLimit(Player p, int pierce) {
        if (!holds(p, TowerGearSetup.BOW.get())) {
            return pierce;
        }
        return D(p) >= 1 ? 0 : pierce / 2;
    }

    // 暁の灯杖「命の灯」: 足りないマナを体力で払う (マナ 1 につき最大体力の 1%)。払えたら true
    public static boolean payWithLife(ServerPlayer p, double missing) {
        if (missing <= 0 || !holds(p, DivineGearSetup.STAFF.get())) {
            return false;
        }
        float cost = (float) (missing * LAMP_LIFE / B(p) * p.getMaxHealth());
        if (p.getHealth() <= cost + 1f) {
            return false;
        }
        p.setHealth(p.getHealth() - cost);
        p.serverLevel().sendParticles(RED, p.getX(), p.getY() + 1.2, p.getZ(), 12, 0.3, 0.4, 0.3, 0);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    // 技を使った (SkillManager.cast。マナと待ちを払ったあと)
    public static void onSkillCast(ServerPlayer p, Skill skill, int rank) {
        State s = state(p);
        ServerLevel level = p.serverLevel();
        // 巻き戻りの短剣「巻き戻し」: 1.5 秒後に同じ技がもう一度 (力は半分、マナは要らない)
        if (!s.echoing && holds(p, DivineGearSetup.DAGGER.get())) {
            double echo = REWIND_POWER * B(p);
            level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.01);
            SkillEffects.later(level, REWIND_TICKS, () -> {
                if (!p.isAlive() || p.level() != level || !holds(p, DivineGearSetup.DAGGER.get())) {
                    return;
                }
                s.echoing = true;
                echoScale = echo;
                try {
                    SkillActions.perform(p, skill, rank);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6f, 1.6f);
                } finally {
                    echoScale = 1;
                    s.echoing = false;
                }
            });
        }
        // 巡礼の羽弓「羽の嵐」: 背の羽根が、狙った敵へみな飛ぶ
        if (s.feathers > 0 && holds(p, DivineGearSetup.BOW.get())) {
            LivingEntity target = aimed(p, 24);
            if (target != null) {
                int n = s.feathers;
                s.feathers = 0;
                float dmg = attack(p) * (float) (FEATHER * B(p));
                for (int i = 0; i < n; i++) {
                    SkillEffects.later(level, 2 + i, () -> {
                        if (target.isAlive() && target.level() == level) {
                            line(level, p.getEyePosition(), target.position().add(0, target.getBbHeight() * 0.6, 0), ParticleTypes.END_ROD, 6);
                            extra(level, p, target, dmg);
                        }
                    });
                }
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.9f, 1.4f);
            }
        }
    }

    // ---------------------------------------------------------------- 傷
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (inner || !(target.level() instanceof ServerLevel level) || event.getAmount() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        long now = level.getGameTime();
        double m = 1;
        float amount = event.getAmount();
        // ---- 受ける側
        if (target instanceof ServerPlayer victim && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (holds(victim, DivineGearSetup.WARDEN.get())) {
                m *= 1 + (WARDEN_TAKEN - 1) * D(victim);
            }
            if (holds(victim, DivineGearSetup.DAGGER.get())) {
                m *= 1 + (REWIND_TAKEN - 1) * D(victim);
            }
            if (state(victim).clapWardUntil > now) {
                m *= CLAP_WARD;
            }
            // 約束の鈴杖「約束」: まわりの持ち主 (自分以外) が 25% を代わりに受け、その半分をマナにする
            if (!(source.getEntity() instanceof Player)) {
                for (Player o : level.getEntitiesOfClass(Player.class, victim.getBoundingBox().inflate(PROMISE_RANGE),
                        q -> q != victim && q.isAlive() && !q.isSpectator())) {
                    if (o instanceof ServerPlayer keeper && holds(keeper, DivineGearSetup.BELL.get())) {
                        float share = (float) (amount * m * PROMISE_SHARE);
                        m *= 1 - PROMISE_SHARE;
                        inner = true;
                        try {
                            keeper.invulnerableTime = 0;
                            keeper.hurt(keeper.damageSources().generic(), share);
                        } finally {
                            inner = false;
                        }
                        SkillManager.gainMana(keeper, share * 0.5 * B(keeper));
                        line(level, keeper.position().add(0, 1, 0), victim.position().add(0, 1, 0), ParticleTypes.END_ROD, 8);
                        break;
                    }
                }
            }
        }
        // 灰打ちの溶け (だれの攻撃にも)
        long[] melt = MELT.get(target);
        if (melt != null && source.getEntity() instanceof Player) {
            int stacks = (int) Math.max(0, melt[0] - (now - melt[1]) / MELT_DECAY);
            double step = melt.length > 2 ? melt[2] / 10000.0 : MELT_STEP;
            m *= 1 + step * stacks;
        }
        // ---- 与える側
        if (source.getEntity() instanceof ServerPlayer p && p != target && !(target instanceof Player) && !jp.revati.skyabyss.combat.MeleeHits.weakHit(p)) {
            m *= dealt(level, p, target, source, amount, now);
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    // 持ち主が与える傷の倍率と、当てたときの数え
    static double dealt(ServerLevel level, ServerPlayer p, LivingEntity target, DamageSource source, float amount, long now) {
        State s = state(p);
        Entity direct = source.getDirectEntity();
        Skill skill = SkillActions.dealing();
        boolean melee = direct == p && skill == null;
        double m = 1;
        // ---- 戦士
        if (holds(p, DivineGearSetup.SWORD.get())) {
            if (melee && s.hands < SKY_HANDS) {
                s.hands++;
            }
            if (skill == Skill.TITAN_CLEAVE && s.hands > 0 && s.skyTick != now) {
                s.skyTick = now;
                skyHands(level, p, target.position(), s.hands);
                s.hands = 0;
            }
        } else if (holds(p, DivineGearSetup.WARDEN.get())) {
            if (skill != null && s.oath > 0) {
                amount += s.oath;
                m *= amount / Math.max(0.01f, amount - (float) s.oath);
                s.oath = 0;
                level.sendParticles(GOLD, target.getX(), target.getY(0.6), target.getZ(), 20, 0.4, 0.4, 0.4, 0);
            }
        } else if (holds(p, TowerGearSetup.SWORD.get())) {
            if (p.getHealth() > p.getMaxHealth() * 0.5f) {
                m *= 1 + BALANCE_HIGH * B(p);
            }
        }
        // ---- 鍛冶師
        else if (holds(p, DivineGearSetup.HAMMER.get())) {
            if (melee && ++s.bells >= BELLS) {
                s.bells = 0;
                bigBell(level, p, target);
            }
        } else if (holds(p, DivineGearSetup.ASH.get())) {
            if (melee) {
                long[] melt = MELT.get(target);
                int stacks = melt == null ? 0 : (int) Math.max(0, melt[0] - (now - melt[1]) / MELT_DECAY);
                MELT.put(target, new long[] {Math.min(MELT_MAX, stacks + 1), now, Math.round(MELT_STEP * B(p) * 10000)});
                level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY(0.6), target.getZ(), 2, 0.3, 0.3, 0.3, 0);
            }
        } else if (holds(p, TowerGearSetup.HAMMER.get())) {
            if (melee && ++s.pulls >= PULL_EVERY) {
                s.pulls = 0;
                pull(level, p);
            }
        }
        // ---- 魔法使い
        else if (holds(p, DivineGearSetup.EYELID.get())) {
            if (skill != null && target.getId() == s.gazeTarget && s.gaze > 0) {
                m *= 1 + GAZE_STEP * B(p) * Math.floor(s.gaze);
            }
        } else if (holds(p, TowerGearSetup.STAFF.get())) {
            if (skill != null && s.moonTick != now) {
                s.moonTick = now;
                if (++s.moonCount >= MOON_EVERY) {
                    s.moonCount = 0;
                    newMoon(level, p, target.position());
                }
            }
        }
        // ---- 弓使い
        else if (arrowFrom(direct, p, DivineGearSetup.BOW.get())) {
            if (skill == null && !direct.getTags().contains(SkillActions.PIERCE_TAG)) {
                m *= 1 - (1 - PLUME_ARROW) * arrowD(direct);
                if (s.feathers < FEATHERS) {
                    s.feathers++;
                }
            }
        } else if (arrowFrom(direct, p, DivineGearSetup.MIST.get())) {
            MISTS.add(new Mist(level, target.position(), p.getUUID(), now + MIST_TICKS));
            level.sendParticles(MIST, target.getX(), target.getY() + 0.5, target.getZ(), 30, 1.2, 0.4, 1.2, 0);
            if (inOwnMist(level, p, now)) {
                m *= 1 + (MIST_ARROW - 1) * arrowB(direct);
            }
        } else if (arrowFrom(direct, p, TowerGearSetup.BOW.get())) {
            s.eyeCount = s.eyeTarget == target.getId() ? Math.min(EYE_MAX, s.eyeCount + 1) : 1;
            s.eyeTarget = target.getId();
            m *= 1 + EYE_STEP * arrowB(direct) * (s.eyeCount - 1);
            level.sendParticles(GOLD, target.getX(), target.getY(0.6), target.getZ(), 2 + s.eyeCount, 0.3, 0.3, 0.3, 0);
        }
        // ---- 暗殺者
        else if (holds(p, DivineGearSetup.MOTH.get())) {
            if (direct == p) {
                int dust = DUST.getOrDefault(target, 0);
                if (MoonGearEvents.fromBehind(target, p.position())) {
                    if (dust > 0) {
                        m *= 1 + DUST_STEP * B(p) * dust;
                        DUST.remove(target);
                        target.removeEffect(MobEffects.GLOWING);
                        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10 + dust * 4, 0.3, 0.4, 0.3, 0.15);
                    } else {
                        DUST.put(target, 1);
                    }
                } else {
                    m *= 1 - (1 - MOTH_FRONT) * D(p);
                    DUST.put(target, Math.min(DUST_MAX, dust + 1));
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, true, false));
                    level.sendParticles(GOLD, target.getX(), target.getY(0.8), target.getZ(), 4 + dust * 2, 0.3, 0.3, 0.3, 0);
                }
            }
        } else if (holds(p, TowerGearSetup.DAGGER.get())) {
            Entity marked = s.sigil < 0 ? null : level.getEntity(s.sigil);
            if (!(marked instanceof LivingEntity le) || !le.isAlive()) {
                s.sigil = target.getId();
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60, 0, true, false));
                level.sendParticles(RED, target.getX(), target.getY() + 0.1, target.getZ(), 16, 0.6, 0, 0.6, 0);
                marked = target;
            }
            m *= marked == target ? 1 + (SIGIL_ON - 1) * B(p) : 1 - (1 - SIGIL_OFF) * D(p);
        }
        // ---- 呪術師
        else if (holds(p, DivineGearSetup.BELL.get())) {
            m *= 1 - (1 - PROMISE_OWN) * D(p);
        } else if (holds(p, DivineGearSetup.KEEPER.get())) {
            if (melee && ++s.keeper >= KEEPER_EVERY) {
                s.keeper = 0;
                silence(level, p);
            }
        } else if (holds(p, TowerGearSetup.BELL.get())) {
            if (melee) {
                m *= 1 - (1 - CLAP_OWN) * D(p);
                if (++s.prayers >= PRAYERS) {
                    s.prayers = 0;
                    clap(level, p, now);
                }
            }
        }
        return m;
    }

    // 傷が入ったあと: 番人の誓いを溜める・天と淵の天秤の吸い
    @SubscribeEvent
    static void onDamaged(LivingDamageEvent.Post event) {
        if (inner || event.getNewDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer p && event.getSource().getEntity() instanceof LivingEntity && holds(p, DivineGearSetup.WARDEN.get())) {
            State s = state(p);
            s.oath = Math.min(p.getMaxHealth() * 0.5, s.oath + event.getNewDamage() * WARDEN_OATH * B(p));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer p && p != victim && !(victim instanceof Player) && holds(p, TowerGearSetup.SWORD.get())
                && p.getHealth() <= p.getMaxHealth() * 0.5f) {
            inner = true;
            try {
                p.heal(event.getNewDamage() * (float) (BALANCE_DRAIN * B(p)));
            } finally {
                inner = false;
            }
            level.sendParticles(VIOLET, p.getX(), p.getY() + 1, p.getZ(), 4, 0.3, 0.4, 0.3, 0);
        }
    }

    // あいだの大剣の欠点: ほかから受ける癒し −50%
    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        if (!inner && event.getEntity() instanceof ServerPlayer p && holds(p, TowerGearSetup.SWORD.get())) {
            event.setAmount((float) (event.getAmount() * (1 - (1 - BALANCE_HEAL) * D(p))));
        }
    }

    // 追う印の短剣: 印の敵が倒れたら、技の待ちがみな戻る
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        for (ServerPlayer p : dead.level().getServer().getPlayerList().getPlayers()) {
            State s = STATES.get(p.getUUID());
            if (s != null && s.sigil == dead.getId()) {
                s.sigil = -1;
                if (holds(p, TowerGearSetup.DAGGER.get())) {
                    SkillData d = SkillManager.get(p);
                    d.cooldowns.clear();
                    SkillManager.sync(p);
                    p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.6f);
                    p.serverLevel().sendParticles(RED, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick: 欠点の属性・凝視・霞・天の手の見た目
    static final ResourceLocation M_BELL = id("mythic_bell_attack_speed");
    static final ResourceLocation M_ASH = id("mythic_ash_armor");
    static final ResourceLocation M_PULL = id("mythic_pull_move");
    static final ResourceLocation M_LAMP = id("mythic_lamp_mana");
    static final ResourceLocation M_EYELID = id("mythic_eyelid_move");
    static final ResourceLocation M_MIST = id("mythic_mist_health");
    static final ResourceLocation M_KEEPER = id("mythic_keeper_attack_speed");

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        State s = state(p);
        if (p.tickCount % 10 == 0) {
            double d = D(p);
            toggle(p, Attributes.ATTACK_SPEED, M_BELL, holds(p, DivineGearSetup.HAMMER.get()), BELL_ASPEED * d);
            toggle(p, Attributes.ARMOR, M_ASH, holds(p, DivineGearSetup.ASH.get()), ASH_ARMOR * d);
            toggle(p, Attributes.MOVEMENT_SPEED, M_PULL, holds(p, TowerGearSetup.HAMMER.get()), PULL_MOVE * d);
            toggle(p, Rpg.MAX_MANA, M_LAMP, holds(p, DivineGearSetup.STAFF.get()), LAMP_MANA * d);
            toggle(p, Attributes.MOVEMENT_SPEED, M_EYELID, holds(p, DivineGearSetup.EYELID.get()), EYELID_MOVE * d);
            toggle(p, Attributes.MAX_HEALTH, M_MIST, holds(p, DivineGearSetup.MIST.get()), MIST_HEALTH * d);
            toggle(p, Attributes.ATTACK_SPEED, M_KEEPER, holds(p, DivineGearSetup.KEEPER.get()), KEEPER_ASPEED * d);
            if (p.getHealth() > p.getMaxHealth()) {
                p.setHealth(p.getMaxHealth());
            }
            // 雲の瞼の杖「凝視」: 同じ敵を見続けると 1 秒ごとに一段
            if (holds(p, DivineGearSetup.EYELID.get())) {
                LivingEntity seen = aimed(p, GAZE_RANGE);
                if (seen == null || seen.getId() != s.gazeTarget) {
                    s.gazeTarget = seen == null ? -1 : seen.getId();
                    s.gaze = 0;
                } else {
                    s.gaze = Math.min(GAZE_MAX, s.gaze + 0.5);
                    if (s.gaze >= 1 && p.tickCount % 20 == 0) {
                        line(level, p.getEyePosition(), seen.position().add(0, seen.getBbHeight() * 0.6, 0), ParticleTypes.WAX_ON, 4 + (int) s.gaze);
                    }
                }
            }
            // 霞鳥の弓「霞隠れ」: 自分の霞の中では、狙っていた敵が見失う
            if (!MISTS.isEmpty() && holds(p, DivineGearSetup.MIST.get()) && inOwnMist(level, p, now)) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), q -> q.getTarget() == p)) {
                    mob.setTarget(null);
                }
            }
            // 天の手と羽根: 背に浮かぶ数
            if (s.hands > 0 && holds(p, DivineGearSetup.SWORD.get())) {
                for (int i = 0; i < s.hands; i++) {
                    double a = Math.toRadians(p.yBodyRot) + Math.PI / 2 + (i - (s.hands - 1) / 2.0) * 0.5;
                    level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 0.9, p.getY() + 2.2, p.getZ() + Math.sin(a) * 0.9, 1, 0, 0.05, 0, 0);
                }
            }
            if (s.feathers > 0 && holds(p, DivineGearSetup.BOW.get())) {
                level.sendParticles(GOLD, p.getX(), p.getY() + 1.6, p.getZ(), Math.min(6, s.feathers), 0.4, 0.3, 0.4, 0);
            }
        }
        if (p.tickCount % 20 == 0) {
            MISTS.removeIf(mi -> mi.until() < now);
            for (Mist mi : MISTS) {
                if (mi.owner().equals(p.getUUID()) && mi.level() == level) {
                    level.sendParticles(MIST, mi.pos().x, mi.pos().y + 0.4, mi.pos().z, 10, MIST_RADIUS * 0.5, 0.3, MIST_RADIUS * 0.5, 0);
                }
            }
        }
    }

    static boolean inOwnMist(ServerLevel level, Player p, long now) {
        for (Mist mi : MISTS) {
            if (mi.owner().equals(p.getUUID()) && mi.level() == level && mi.until() >= now && mi.pos().distanceToSqr(p.position()) <= MIST_RADIUS * MIST_RADIUS) {
                return true;
            }
        }
        return false;
    }

    static void toggle(Player p, Holder<Attribute> attr, ResourceLocation id, boolean on, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier cur = inst.getModifier(id);
        // 目覚めで欠点の大きさが変わったら付け直す
        if (cur != null && (!on || Math.abs(cur.amount() - amount) > 1e-6)) {
            inst.removeModifier(id);
            cur = null;
        }
        if (on && cur == null) {
            inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ---------------------------------------------------------------- 放つもの
    // 天の手: 巨神斬りの相手のまわりへ、溜めた手がみな落ちる
    static void skyHands(ServerLevel level, ServerPlayer p, Vec3 at, int hands) {
        float dmg = attack(p) * (float) (SKY_HAND * B(p) * hands);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 6, at.z, 12 * hands, 0.6, 0.8, 0.6, 0.02);
        SkillEffects.later(level, 8, () -> {
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 10 + hands * 6, 1.4, 0.1, 1.4, 0.05);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.5f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3, 2, 3), q -> SkillActions.isFoe(p, q))) {
                extra(level, p, e, dmg);
            }
        });
    }

    // 七つ鐘: 大鐘 (まわり 6 に攻撃力の 300%。小さな敵は 2 秒縛り、大きな相手は鈍る)
    static void bigBell(ServerLevel level, ServerPlayer p, LivingEntity at) {
        float dmg = attack(p) * (float) (BELL_MULT * B(p));
        for (int i = 0; i < 36; i++) {
            double a = i / 36.0 * Math.PI * 2;
            level.sendParticles(ParticleTypes.WAX_ON, at.getX() + Math.cos(a) * BELL_RADIUS, at.getY() + 0.3, at.getZ() + Math.sin(a) * BELL_RADIUS, 1, 0, 0, 0, 0);
        }
        level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2f, 0.7f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(BELL_RADIUS), q -> SkillActions.isFoe(p, q) || q == at)) {
            extra(level, p, e, dmg);
            if (SkillActions.isLarge(e)) {
                SkillEffects.slow(e, 0.4, 40);
            } else {
                SkillEffects.bind(e, 40);
            }
        }
    }

    // 引力: まわり 8 の敵を足もとへ引き寄せて叩く (大きな相手は引かない)
    static void pull(ServerLevel level, ServerPlayer p) {
        float dmg = attack(p) * (float) (PULL_MULT * B(p));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(PULL_RADIUS), q -> SkillActions.isFoe(p, q))) {
            if (!SkillActions.isLarge(e)) {
                Vec3 d = p.position().subtract(e.position()).multiply(1, 0, 1);
                if (d.lengthSqr() > 1.5) {
                    double len = d.length();
                    d = d.normalize().scale(Math.min(1.6, len * 0.3));
                    e.setDeltaMovement(d.x, 0.25, d.z);
                    e.hurtMarked = true;
                }
            }
            extra(level, p, e, dmg);
        }
        level.sendParticles(VIOLET, p.getX(), p.getY() + 0.2, p.getZ(), 50, PULL_RADIUS * 0.5, 0.1, PULL_RADIUS * 0.5, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.6f, 0.5f);
    }

    // 新しい月: 狙った所へ小さな月が落ちる (まわり 4 に攻撃力の 250%)
    static void newMoon(ServerLevel level, ServerPlayer p, Vec3 at) {
        float dmg = attack(p) * (float) (MOON_MULT * B(p));
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 7, at.z, 16, 0.3, 0.6, 0.3, 0.01);
        SkillEffects.later(level, 10, () -> {
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 40, MOON_RADIUS * 0.5, 0.3, MOON_RADIUS * 0.5, 0.06);
            level.sendParticles(GOLD, at.x, at.y + 0.3, at.z, 30, MOON_RADIUS * 0.5, 0.1, MOON_RADIUS * 0.5, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 0.6f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(MOON_RADIUS, 2, MOON_RADIUS), q -> SkillActions.isFoe(p, q))) {
                extra(level, p, e, dmg);
            }
        });
    }

    // 沈黙の鐘: まわり 6 の小さな敵は 2 秒動けず傷も入れられない。大きな相手は鈍る
    static void silence(ServerLevel level, ServerPlayer p) {
        int ticks = (int) Math.round(KEEPER_TICKS * B(p));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(KEEPER_RADIUS), q -> SkillActions.isFoe(p, q))) {
            if (SkillActions.isLarge(e)) {
                SkillEffects.slow(e, 0.4, ticks);
            } else {
                SkillEffects.bind(e, ticks);
                SkillEffects.timedMod(e, Attributes.ATTACK_DAMAGE, "mythic_silence", -0.95, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks);
            }
            level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 2, 0.2, 0.1, 0.2, 0);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.6f);
        level.sendParticles(GOLD, p.getX(), p.getY() + 0.3, p.getZ(), 30, KEEPER_RADIUS * 0.4, 0.1, KEEPER_RADIUS * 0.4, 0);
    }

    // 合掌: まわり 8 の仲間 (自分も) の体力を 3 割戻し、6 秒 受ける傷 −20%
    static void clap(ServerLevel level, ServerPlayer p, long now) {
        inner = true;
        try {
            List<Player> near = new ArrayList<>(level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(CLAP_RANGE), q -> q.isAlive() && !q.isSpectator()));
            if (!near.contains(p)) {
                near.add(p);
            }
            for (Player ally : near) {
                ally.heal(ally.getMaxHealth() * (float) (CLAP_HEAL * B(p)));
                state(ally).clapWardUntil = now + CLAP_WARD_TICKS;
                level.sendParticles(GOLD, ally.getX(), ally.getY() + 1.2, ally.getZ(), 14, 0.3, 0.5, 0.3, 0);
            }
        } finally {
            inner = false;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.6f);
    }

    // ---------------------------------------------------------------- 小物
    // 足した傷 (効き方を起こさない)
    static void extra(ServerLevel level, ServerPlayer p, LivingEntity e, float dmg) {
        inner = true;
        try {
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(p), dmg);
            e.invulnerableTime = 0;
        } finally {
            inner = false;
        }
    }

    // 見ている向きの最初の敵 (届く所まで)。いなければ近い敵
    static LivingEntity aimed(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.5),
                e -> e instanceof LivingEntity && SkillActions.isFoe(p, e), range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, int n) {
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.lerp(b, i / (double) n);
            level.sendParticles(particle, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    // 自己テスト用
    static State peek(Player p) {
        return state(p);
    }

    static int dust(LivingEntity e) {
        return DUST.getOrDefault(e, 0);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        STATES.clear();
        MELT.clear();
        DUST.clear();
        MISTS.clear();
        inner = false;
        echoScale = 1;
    }
}

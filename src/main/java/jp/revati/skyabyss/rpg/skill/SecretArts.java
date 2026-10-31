package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 秘伝 (docs/design/54 の 3.2・3.2.1。Wynncraft のアスペクトにあたる)。職ごとに 4 つ、その職の使う技の一つに「技のあとの動き」を足す。
//   覚える  秘伝の巻を読む (SecretScroll。中の秘伝は読んだときに、いまの職の 4 つから引く)。同じ秘伝はもう一度で段が上がる (I → II → III)。
//           III のあとの巻は塔の欠片 OVER_SHARDS になる
//   枠      覚えた秘伝のうち SLOTS (2) つを枠に付けたときだけ効く。付け外しは秘伝の一覧 (チャット。/skyabyss_arts、巻を読んだとき、訓練所の師範)
//   効き方  SkillActions.perform の後 (伝説の書き換えと同じつなぎ口) で after が呼ばれ、枠の秘伝の技なら上乗せが続く
//   人のデータ ARTS (秘伝 → 段)・SLOTS_KEY (枠の秘伝)。LifeKeep で倒れても残る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SecretArts {
    public static final String ARTS = "skyabyss_secret_arts";
    public static final String SLOTS_KEY = "skyabyss_secret_slots";
    public static final int SLOTS = 2;
    public static final int MAX_TIER = 3;
    public static final int OVER_SHARDS = 20;
    static final String FROST = SkyAbyss.MODID + ":secret_frost";
    static final String FROST_AMT = SkyAbyss.MODID + ":secret_frost_amt";
    static final String COMMAND = "skyabyss_arts";
    private static boolean inner;

    public enum Art {
        // 戦士
        SECOND_CUT("second_cut", Skill.TITAN_CLEAVE, Job.WARRIOR),
        QUAKE_CALL("quake_call", Skill.ROCK_CALL, Job.WARRIOR),
        WHALE_WHIRL("whale_whirl", Skill.WHALE_SWEEP, Job.WARRIOR),
        BLADE_STORM("blade_storm", Skill.SPIN_CUT, Job.WARRIOR),
        // 暗殺者
        SHADOW_STEP("shadow_step", Skill.LUNGE, Job.ASSASSIN),
        CALTROPS("caltrops", Skill.BACKSTEP, Job.ASSASSIN),
        CHASING_BLADES("chasing_blades", Skill.WIND_REND, Job.ASSASSIN),
        DOUBLE_FANG("double_fang", Skill.TWIN_FANG, Job.ASSASSIN),
        // 呪術師
        WARD_ECHO("ward_echo", Skill.WARD_SONG, Job.SHAMAN),
        MAELSTROM("maelstrom", Skill.EBB_TIDE, Job.SHAMAN),
        CURSE_SPREAD("curse_spread", Skill.CURSE_MARK, Job.SHAMAN),
        LAMP_BLESSING("lamp_blessing", Skill.LAMP_CHARM, Job.SHAMAN),
        // 鍛冶師
        AFTERHEAT("afterheat", Skill.TEMPER, Job.SMITH),
        DOUBLE_HAMMER("double_hammer", Skill.EMBER_HAMMER, Job.SMITH),
        ASH_VEIL("ash_veil", Skill.ASH_SCATTER, Job.SMITH),
        FORGE_PULSE("forge_pulse", Skill.FORGE_STOMP, Job.SMITH),
        // 弓使い
        SPLIT_ARROWS("split_arrows", Skill.CLOUD_PIERCE, Job.ARCHER),
        LINKED_TRAP("linked_trap", Skill.BONE_TRAP, Job.ARCHER),
        LINGERING_RAIN("lingering_rain", Skill.SKY_RAIN, Job.ARCHER),
        HAWK_LANDING("hawk_landing", Skill.ARCHER_LEAP, Job.ARCHER),
        // 魔法使い
        CHAIN_BOLT("chain_bolt", Skill.THUNDER_BOLT, Job.MAGE),
        EMBER_GROUND("ember_ground", Skill.EMBER_ORB, Job.MAGE),
        FROST_BITE("frost_bite", Skill.FROST_TIDE, Job.MAGE),
        STAR_TRAIL("star_trail", Skill.BLINK, Job.MAGE);

        public final String id;
        public final Skill skill;
        public final Job job;

        Art(String id, Skill skill, Job job) {
            this.id = id;
            this.skill = skill;
            this.job = job;
        }

        public static Art byId(String id) {
            for (Art a : values()) {
                if (a.id.equals(id)) {
                    return a;
                }
            }
            return null;
        }

        public String nameKey() {
            return "secret.skyabyss." + id;
        }
    }

    private SecretArts() {
    }

    // ---------------------------------------------------------------- 覚える・段・枠
    public static int tier(ServerPlayer p, Art a) {
        return p.getPersistentData().getCompound(ARTS).getInt(a.id);
    }

    public static List<Art> equipped(ServerPlayer p) {
        List<Art> out = new ArrayList<>();
        for (Tag t : p.getPersistentData().getList(SLOTS_KEY, Tag.TAG_STRING)) {
            Art a = Art.byId(t.getAsString());
            if (a != null && tier(p, a) > 0) {
                out.add(a);
            }
        }
        return out;
    }

    // いまの職の秘伝から一つ (職が無ければ全部から)
    public static Art roll(Job job, RandomSource rng) {
        List<Art> pool = new ArrayList<>();
        for (Art a : Art.values()) {
            if (job == null || a.job == job) {
                pool.add(a);
            }
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    // 覚える (段を一つ上げる)。III のあとは false (呼び手が欠片に替える)
    public static boolean learn(ServerPlayer p, Art a) {
        CompoundTag arts = p.getPersistentData().getCompound(ARTS);
        int t = arts.getInt(a.id);
        if (t >= MAX_TIER) {
            return false;
        }
        arts.putInt(a.id, t + 1);
        p.getPersistentData().put(ARTS, arts);
        // 枠に空きがあれば付ける
        List<Art> eq = equipped(p);
        if (!eq.contains(a) && eq.size() < SLOTS) {
            eq.add(a);
            saveSlots(p, eq);
        }
        return true;
    }

    static void saveSlots(ServerPlayer p, List<Art> eq) {
        ListTag l = new ListTag();
        eq.forEach(a -> l.add(StringTag.valueOf(a.id)));
        p.getPersistentData().put(SLOTS_KEY, l);
    }

    public static boolean equip(ServerPlayer p, Art a) {
        List<Art> eq = equipped(p);
        if (tier(p, a) <= 0 || eq.contains(a)) {
            return false;
        }
        if (eq.size() >= SLOTS) {
            Notice.hint(p, "message.skyabyss.secret.full", SLOTS);
            return false;
        }
        eq.add(a);
        saveSlots(p, eq);
        return true;
    }

    public static boolean unequip(ServerPlayer p, Art a) {
        List<Art> eq = equipped(p);
        boolean r = eq.remove(a);
        saveSlots(p, eq);
        return r;
    }

    static String roman(int t) {
        return t >= 3 ? "III" : t == 2 ? "II" : "I";
    }

    public static Component name(Art a, int tier) {
        return Component.translatable(a.nameKey()).append(" " + roman(Math.max(1, tier)));
    }

    // 秘伝の一覧 (チャット): 覚えたものに [付ける]/[外す]
    public static void menu(ServerPlayer p) {
        p.sendSystemMessage(Component.translatable("message.skyabyss.secret.head", equipped(p).size(), SLOTS).withStyle(s -> s.withColor(0xE8C8FF)));
        boolean any = false;
        List<Art> eq = equipped(p);
        for (Art a : Art.values()) {
            int t = tier(p, a);
            if (t <= 0) {
                continue;
            }
            any = true;
            boolean on = eq.contains(a);
            MutableComponent line = Component.literal(on ? " ◆ " : " ◇ ").append(name(a, t).copy().withStyle(s -> s.withColor(on ? 0xFFD890 : 0xD8D0E8)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(a.nameKey() + ".desc")))))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("skill.skyabyss." + a.skill.id).withStyle(ChatFormatting.GRAY)).append("  ");
            line.append(Component.translatable(on ? "message.skyabyss.secret.btn.off" : "message.skyabyss.secret.btn.on")
                    .withStyle(Style.EMPTY.withColor(on ? 0xB0B0B8 : 0x9CE0A8).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + (on ? " off " : " on ") + a.id))));
            p.sendSystemMessage(line);
        }
        if (!any) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.secret.none").withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .executes(c -> {
                    menu(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("on").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Art a = Art.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                    if (a != null && equip(p, a)) {
                        menu(p);
                    }
                    return 1;
                })))
                .then(Commands.literal("off").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Art a = Art.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                    if (a != null && unequip(p, a)) {
                        menu(p);
                    }
                    return 1;
                }))));
        // 開発用: /skyabyss arts learn <id> | clear
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("arts")
                        .then(Commands.literal("learn").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            Art a = Art.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                            return a != null && learn(c.getSource().getPlayerOrException(), a) ? 1 : 0;
                        })))
                        .then(Commands.literal("clear").executes(c -> {
                            c.getSource().getPlayerOrException().getPersistentData().remove(ARTS);
                            c.getSource().getPlayerOrException().getPersistentData().remove(SLOTS_KEY);
                            return 1;
                        }))));
    }

    // ---------------------------------------------------------------- 効き方 (SkillActions.perform の後)
    static void after(ServerPlayer player, Skill skill, int rank, Vec3 from) {
        for (Art a : equipped(player)) {
            if (a.skill == skill) {
                apply(player, a, tier(player, a), from);
            }
        }
    }

    static double weapon(ServerPlayer p) {
        return JobActions.weaponDamage(p);
    }

    static void hit(ServerPlayer p, LivingEntity e, double amount, Skill skill) {
        inner = true;
        try {
            SkillActions.hit(p, e, (float) amount, skill);
        } finally {
            inner = false;
        }
    }

    // 自己テスト用: 秘伝の上乗せを一度動かす (敵がいなくても落ちないことを見る)
    public static void applyForTest(ServerPlayer p, Art a, int tier) {
        apply(p, a, tier, p.position());
    }

    static void apply(ServerPlayer p, Art a, int t, Vec3 from) {
        ServerLevel level = p.serverLevel();
        Vec3 f = LegendRewrites.flatLook(p);
        double k = t == 1 ? 1.0 : t == 2 ? 1.5 : 2.0;
        Skill s = a.skill;
        switch (a) {
            case SECOND_CUT -> SkillEffects.later(level, 10, () -> {
                for (LivingEntity e : LegendRewrites.cone(p, p.position(), f, 4.0, 60)) {
                    hit(p, e, weapon(p) * 0.4 * k, s);
                }
                LegendRewrites.ring(level, p.position().add(f.scale(2)), 1.5, ParticleTypes.SWEEP_ATTACK, 3);
            });
            case QUAKE_CALL -> {
                Vec3 c = LegendRewrites.aimPoint(p, 8);
                for (LivingEntity e : LegendRewrites.around(p, c, 4)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * (1 + t), 2), p);
                }
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.2, c.z, 12, 2, 0.1, 2, 0.01);
            }
            case WHALE_WHIRL -> {
                for (LivingEntity e : LegendRewrites.around(p, p.position(), 5)) {
                    LegendRewrites.push(e, e.position().add(e.position().subtract(p.position())), 0.8);
                    hit(p, e, weapon(p) * 0.3 * k, s);
                }
                LegendRewrites.ring(level, p.position(), 5, ParticleTypes.SPLASH, 30);
            }
            case BLADE_STORM -> {
                int n = t >= 3 ? 2 : 1;
                for (int i = 1; i <= n; i++) {
                    SkillEffects.later(level, 8 * i, () -> {
                        for (LivingEntity e : LegendRewrites.around(p, p.position(), 3.5)) {
                            hit(p, e, weapon(p) * 0.35, s);
                        }
                        LegendRewrites.ring(level, p.position(), 3.0, ParticleTypes.SWEEP_ATTACK, 8);
                    });
                }
            }
            case SHADOW_STEP -> SkillEffects.later(level, 20, () -> {
                for (LivingEntity e : LegendRewrites.around(p, from, 3)) {
                    hit(p, e, weapon(p) * 0.5 * k, s);
                }
                level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 0.8, from.z, 30, 1.2, 0.6, 1.2, 0.05);
            });
            case CALTROPS -> {
                int secs = 2 + t;
                for (int i = 1; i <= secs; i++) {
                    SkillEffects.later(level, 20 * i, () -> {
                        LegendRewrites.ring(level, from, 2.5, ParticleTypes.CRIT, 12);
                        for (LivingEntity e : LegendRewrites.around(p, from, 2.5)) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1), p);
                            hit(p, e, weapon(p) * 0.2, s);
                        }
                    });
                }
            }
            case CHASING_BLADES -> {
                List<LivingEntity> near = LegendRewrites.around(p, p.position(), 6);
                near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
                int n = 1 + t;
                for (int i = 0; i < n && !near.isEmpty(); i++) {
                    LivingEntity e = near.get(i % near.size());
                    LegendRewrites.line(level, p.position().add(0, 1, 0), e.position().add(0, 1, 0), ParticleTypes.SWEEP_ATTACK);
                    hit(p, e, weapon(p) * 0.3, s);
                }
            }
            case DOUBLE_FANG -> {
                List<LivingEntity> near = LegendRewrites.around(p, p.position(), 4);
                near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
                if (!near.isEmpty()) {
                    LivingEntity e = near.get(0);
                    SkillEffects.later(level, 6, () -> hit(p, e, weapon(p) * 0.4 * k, s));
                    level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY(0.6), e.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                }
            }
            case WARD_ECHO -> SkillEffects.later(level, 40, () -> {
                for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(6), ServerPlayer::isAlive)) {
                    ally.heal(1f + t);
                    level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 2, ally.getZ(), 2, 0.3, 0.1, 0.3, 0);
                }
            });
            case MAELSTROM -> {
                Vec3 c = LegendRewrites.aimPoint(p, 8);
                for (int i = 1; i <= 3; i++) {
                    SkillEffects.later(level, 20 * i, () -> {
                        LegendRewrites.ring(level, c, 3, ParticleTypes.SPLASH, 20);
                        for (LivingEntity e : LegendRewrites.around(p, c, 3.5)) {
                            LegendRewrites.push(e, e.position().add(e.position().subtract(c)), 0.4);
                            hit(p, e, JobActions.spell(p, 0.1 + 0.05 * t), s);
                        }
                    });
                }
            }
            case CURSE_SPREAD -> {
                List<LivingEntity> near = LegendRewrites.around(p, LegendRewrites.aimPoint(p, 8), 6);
                for (int i = 0; i < Math.min(t, near.size()); i++) {
                    LivingEntity e = near.get(i);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), p);
                    hit(p, e, JobActions.spell(p, 0.3), s);
                    level.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY(0.8), e.getZ(), 10, 0.3, 0.4, 0.3, 0);
                }
            }
            case LAMP_BLESSING -> {
                for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(6), ServerPlayer::isAlive)) {
                    ally.setAbsorptionAmount(Math.max(ally.getAbsorptionAmount(), 1f + t));
                    level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + 1.2, ally.getZ(), 6, 0.3, 0.5, 0.3, 0.01);
                }
            }
            case AFTERHEAT -> {
                for (LivingEntity e : LegendRewrites.around(p, p.position(), 4)) {
                    e.igniteForSeconds(1 + t);
                    hit(p, e, weapon(p) * 0.2, s);
                }
                LegendRewrites.ring(level, p.position(), 4, ParticleTypes.FLAME, 24);
            }
            case DOUBLE_HAMMER -> {
                Vec3 c = LegendRewrites.aimPoint(p, 5);
                SkillEffects.later(level, 12, () -> {
                    for (LivingEntity e : LegendRewrites.around(p, c, 3)) {
                        hit(p, e, weapon(p) * 0.4 * k, s);
                    }
                    level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 10, 1, 0.2, 1, 0.1);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.8f);
                });
            }
            case ASH_VEIL -> {
                for (LivingEntity e : LegendRewrites.around(p, p.position(), 5)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * (2 + t), 1), p);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * (2 + t), 0), p);
                }
                level.sendParticles(ParticleTypes.ASH, p.getX(), p.getY() + 1, p.getZ(), 80, 3, 1, 3, 0.02);
            }
            case FORGE_PULSE -> {
                int n = t >= 2 ? 3 : 2;
                double m = 0.2 + 0.05 * t;
                for (int i = 1; i <= n; i++) {
                    SkillEffects.later(level, 10 * i, () -> {
                        LegendRewrites.ring(level, p.position(), 4, ParticleTypes.FLAME, 20);
                        for (LivingEntity e : LegendRewrites.around(p, p.position(), 4)) {
                            hit(p, e, weapon(p) * m, s);
                        }
                    });
                }
            }
            case SPLIT_ARROWS -> {
                Vec3 c = LegendRewrites.aimPoint(p, 24);
                List<LivingEntity> near = LegendRewrites.around(p, c, 8);
                for (int i = 0; i < Math.min(1 + t, near.size()); i++) {
                    LivingEntity e = near.get(i);
                    LegendRewrites.line(level, c.add(0, 1, 0), e.position().add(0, 1, 0), ParticleTypes.CRIT);
                    hit(p, e, weapon(p) * 0.3, s);
                }
            }
            case LINKED_TRAP -> {
                Vec3 c = LegendRewrites.aimPoint(p, 6);
                SkillEffects.later(level, 30, () -> {
                    for (LivingEntity e : LegendRewrites.around(p, c, 3)) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
                        hit(p, e, weapon(p) * 0.3 * k, s);
                    }
                    LegendRewrites.ring(level, c, 3, ParticleTypes.CRIT, 16);
                });
            }
            case LINGERING_RAIN -> {
                Vec3 c = LegendRewrites.aimPoint(p, 16);
                for (int i = 1; i <= 1 + t; i++) {
                    SkillEffects.later(level, 20 * i + 40, () -> {
                        List<LivingEntity> in = LegendRewrites.around(p, c, 5);
                        level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 4, c.z, 16, 3, 1, 3, 0.1);
                        if (!in.isEmpty()) {
                            hit(p, in.get(p.getRandom().nextInt(in.size())), weapon(p) * 0.2, s);
                        }
                    });
                }
            }
            case HAWK_LANDING -> SkillEffects.later(level, 20, () -> {
                for (LivingEntity e : LegendRewrites.around(p, p.position(), 3)) {
                    hit(p, e, weapon(p) * 0.4 * k, s);
                }
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
                LegendRewrites.ring(level, p.position(), 3, ParticleTypes.CLOUD, 16);
            });
            case CHAIN_BOLT -> {
                LivingEntity first = LegendRewrites.aimFoe(p, SkillRules.BOLT_RANGE);
                if (first != null) {
                    List<LivingEntity> next = LegendRewrites.around(p, first.position(), 6);
                    next.remove(first);
                    next.sort(Comparator.comparingDouble(e -> e.distanceToSqr(first)));
                    Vec3 prev = first.position().add(0, first.getBbHeight() * 0.6, 0);
                    for (LivingEntity e : next.subList(0, Math.min(t, next.size()))) {
                        Vec3 b = e.position().add(0, e.getBbHeight() * 0.6, 0);
                        LegendRewrites.line(level, prev, b, ParticleTypes.ELECTRIC_SPARK);
                        hit(p, e, JobActions.spell(p, 0.4), s);
                        prev = b;
                    }
                }
            }
            case EMBER_GROUND -> {
                Vec3 c = LegendRewrites.aimPoint(p, 16);
                for (int i = 1; i <= 3; i++) {
                    SkillEffects.later(level, 20 * i, () -> {
                        LegendRewrites.ring(level, c, 2.5, ParticleTypes.FLAME, 16);
                        for (LivingEntity e : LegendRewrites.around(p, c, 2.5)) {
                            hit(p, e, JobActions.spell(p, 0.1 + 0.05 * t), s);
                        }
                    });
                }
            }
            case FROST_BITE -> {
                long until = level.getGameTime() + 80;
                for (LivingEntity e : LegendRewrites.around(p, p.position(), 6)) {
                    e.getPersistentData().putLong(FROST, until);
                    e.getPersistentData().putDouble(FROST_AMT, 0.10 + 0.05 * t);
                    level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY(0.6), e.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
                }
            }
            case STAR_TRAIL -> SkillEffects.later(level, 20, () -> {
                for (LivingEntity e : LegendRewrites.around(p, from, 3)) {
                    hit(p, e, JobActions.spell(p, 0.5 * k), s);
                }
                level.sendParticles(ParticleTypes.END_ROD, from.x, from.y + 1, from.z, 30, 1, 0.8, 1, 0.08);
            });
            default -> {
            }
        }
    }

    // 凍てつく潮: 印のある相手が受ける傷が増える
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity t = event.getEntity();
        if (inner || t.level().isClientSide() || t.getPersistentData().getLong(FROST) <= t.level().getGameTime()) {
            return;
        }
        event.setAmount((float) (event.getAmount() * (1 + t.getPersistentData().getDouble(FROST_AMT))));
    }
}

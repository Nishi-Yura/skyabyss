package jp.revati.skyabyss.rpg.aspect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillEffects;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 巨神のアスペクト (docs/design/58)。2026-11-09 の声「紋の盤をなくして、wynncraft のようなアスペクトの要素を追加してほしい」。
//   持ち物: 人ごとに、品ごとの集めた数 (SkillData.aspects)。数で Lv が決まる (同じものを重ねて強くなる。最高 Lv 5。どの稀さも 1・2・4・7・11 個)
//   枠: Lv 20 で 1、30 で 2、40 で 3、50 で 4、転生 3 回で 5 (SkillData.aspectSlots)。効くのは今の職の品だけ
//   効き目: 力・待ち・マナは技の数字の口 (SkillExt が足す)。そのほかは技を使ったとき (afterCast・下の傷の口)
//   出る所: 巨神の祠のルートを越えたとき (AspectDrops)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Aspects {
    public static final int MAX_LEVEL = 5;
    // その Lv に要る数 (合計)。どの稀さも同じ (頁の選定 B)
    static final int[] COPIES = {0, 1, 2, 4, 7, 11};
    public static final int MAX_SLOTS = 5;
    static final int[] SLOT_LEVELS = {20, 30, 40, 50};
    public static final int REBIRTH_SLOT = 3;
    // 使ったあとの時間 (tick)・もう一度までの遅れ
    static final int AFTER_TICKS = 60;
    static final int ECHO_DELAY = 12;
    static final String SURGE_UNTIL = "skyabyss_aspect_surge_until";
    static final String SURGE_POWER = "skyabyss_aspect_surge";
    static final String GUARD_UNTIL = "skyabyss_aspect_guard_until";
    static final String GUARD_POWER = "skyabyss_aspect_guard";

    private static final Map<String, Aspect> BY_ID = new LinkedHashMap<>();

    static {
        for (Aspect a : AspectTable.ALL) {
            BY_ID.put(a.id(), a);
        }
    }

    private Aspects() {
    }

    @Nullable
    public static Aspect get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<Aspect> all() {
        return AspectTable.ALL;
    }

    public static List<Aspect> ofJob(Job job) {
        List<Aspect> out = new ArrayList<>();
        for (Aspect a : AspectTable.ALL) {
            if (a.job() == job) {
                out.add(a);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- Lv と枠
    public static int levelOf(int copies) {
        int l = 0;
        for (int i = 1; i <= MAX_LEVEL; i++) {
            if (copies >= COPIES[i]) {
                l = i;
            }
        }
        return l;
    }

    // 次の Lv に要る合計 (最高なら -1)
    public static int nextCopies(int copies) {
        int l = levelOf(copies);
        return l >= MAX_LEVEL ? -1 : COPIES[l + 1];
    }

    public static int copiesFor(int level) {
        return COPIES[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    public static int level(SkillData d, Aspect a) {
        return levelOf(d.aspects.getOrDefault(a.id(), 0));
    }

    // 開いている枠の数
    public static int slots(int playerLevel, int rebirth) {
        int n = 0;
        for (int at : SLOT_LEVELS) {
            if (playerLevel >= at) {
                n++;
            }
        }
        if (rebirth >= REBIRTH_SLOT && n >= SLOT_LEVELS.length) {
            n++;
        }
        return n;
    }

    public static int slots(Player p) {
        return slots(jp.revati.skyabyss.rpg.Growth.get(p).level, SkillManager.get(p).rebirth);
    }

    // 付けていて効いている品 (今の職・開いた枠・持っている) と、その Lv
    public record Worn(Aspect aspect, int level) {
    }

    public static List<Worn> worn(Player p) {
        SkillData d = SkillManager.get(p);
        Job job = Job.byId(d.curJob);
        List<Worn> out = new ArrayList<>();
        int open = slots(p);
        for (int i = 0; i < Math.min(open, d.aspectSlots.length); i++) {
            Aspect a = get(d.aspectSlots[i]);
            if (a != null && a.job() == job) {
                int l = level(d, a);
                if (l > 0) {
                    out.add(new Worn(a, l));
                }
            }
        }
        return out;
    }

    // その技への効き目の合計
    public static double sum(Player p, Skill skill, Aspect.Verb verb) {
        double v = 0;
        for (Worn w : worn(p)) {
            if (w.aspect().skill() == skill) {
                v += w.aspect().value(verb, w.level());
            }
        }
        return v;
    }

    // ---------------------------------------------------------------- 技の数字の口 (rpg/skill/SkillExt から)
    // 一つの技への力とマナは、重ねても見積もりの上限まで (SkillRules.ASPECT_POWER_MAX・ASPECT_MANA_MAX)
    public static double power(Player p, Skill skill) {
        return Math.min(jp.revati.skyabyss.rpg.skill.SkillRules.ASPECT_POWER_MAX, sum(p, skill, Aspect.Verb.POWER));
    }

    public static double cooldown(Player p, Skill skill) {
        return -sum(p, skill, Aspect.Verb.COOLDOWN);
    }

    public static double mana(Player p, Skill skill) {
        return -Math.min(jp.revati.skyabyss.rpg.skill.SkillRules.ASPECT_MANA_MAX, sum(p, skill, Aspect.Verb.MANA));
    }

    // ---------------------------------------------------------------- 技を使ったあと (SkillManager.cast から)
    private static boolean echoing;

    public static void afterCast(ServerPlayer p, Skill skill, int rank) {
        if (echoing) {
            return;
        }
        double haste = sum(p, skill, Aspect.Verb.HASTE);
        double surge = sum(p, skill, Aspect.Verb.SURGE);
        double guard = sum(p, skill, Aspect.Verb.GUARD);
        double echo = sum(p, skill, Aspect.Verb.ECHO);
        double reset = sum(p, skill, Aspect.Verb.RESET);
        long now = p.level().getGameTime();
        if (haste > 0) {
            SkillEffects.timedMod(p, Attributes.MOVEMENT_SPEED, "aspect_haste", haste, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, AFTER_TICKS);
        }
        if (surge > 0) {
            p.getPersistentData().putLong(SURGE_UNTIL, now + AFTER_TICKS);
            p.getPersistentData().putDouble(SURGE_POWER, surge);
        }
        if (guard > 0) {
            p.getPersistentData().putLong(GUARD_UNTIL, now + AFTER_TICKS);
            p.getPersistentData().putDouble(GUARD_POWER, Math.min(0.6, guard));
        }
        if (reset > 0 && p.getRandom().nextDouble() < Math.min(0.9, reset)) {
            SkillManager.get(p).cooldowns.put(skill.id, 1);
            ServerLevel level = p.serverLevel();
            level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 16, 0.4, 0.5, 0.4, 0.2);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.6f);
            SkillManager.sync(p);
        }
        if (echo > 0) {
            ServerLevel level = p.serverLevel();
            SkillEffects.later(level, ECHO_DELAY, () -> {
                if (!p.isAlive() || p.level() != level) {
                    return;
                }
                echoing = true;
                try {
                    jp.revati.skyabyss.item.mythic.MythicAbilities.withPowerScale(echo,
                            () -> jp.revati.skyabyss.rpg.skill.SkillActions.perform(p, skill, rank));
                    level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 14, 0.4, 0.6, 0.4, 0.04);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.5f, 1.3f);
                } finally {
                    echoing = false;
                }
            });
        }
    }

    // 次の一撃 (近接・矢) と、受ける傷
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onHurt(LivingIncomingDamageEvent event) {
        long now = event.getEntity().level().getGameTime();
        if (event.getSource().getEntity() instanceof ServerPlayer p && event.getEntity() != p) {
            var data = p.getPersistentData();
            if (data.getLong(SURGE_UNTIL) > now && event.getSource().getDirectEntity() == p
                    || data.getLong(SURGE_UNTIL) > now && event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
                event.setAmount((float) (event.getAmount() * (1 + data.getDouble(SURGE_POWER))));
                data.remove(SURGE_UNTIL);
            }
        }
        if (event.getEntity() instanceof ServerPlayer p && p.getPersistentData().getLong(GUARD_UNTIL) > now) {
            event.setAmount((float) (event.getAmount() * (1 - p.getPersistentData().getDouble(GUARD_POWER))));
        }
    }

    // ---------------------------------------------------------------- 付け外し (画面から)
    public enum EquipResult {
        OK, LOCKED, NOT_OWNED, WRONG_JOB, FULL
    }

    // slot に id を付ける (空の id で外す)。同じ品は一つの枠だけ
    public static EquipResult equip(ServerPlayer p, int slot, String id) {
        SkillData d = SkillManager.get(p);
        if (slot < 0 || slot >= d.aspectSlots.length || slot >= slots(p)) {
            return EquipResult.LOCKED;
        }
        if (id == null || id.isEmpty()) {
            d.aspectSlots[slot] = "";
            SkillManager.sync(p);
            return EquipResult.OK;
        }
        Aspect a = get(id);
        if (a == null || level(d, a) <= 0) {
            return EquipResult.NOT_OWNED;
        }
        if (a.job() != Job.byId(d.curJob)) {
            return EquipResult.WRONG_JOB;
        }
        for (int i = 0; i < d.aspectSlots.length; i++) {
            if (id.equals(d.aspectSlots[i])) {
                d.aspectSlots[i] = "";
            }
        }
        d.aspectSlots[slot] = id;
        SkillManager.sync(p);
        return EquipResult.OK;
    }

    // ---------------------------------------------------------------- 出る (AspectDrops が使う)
    // 一つ引く: 稀さ (レア 60・エピック 30・ミシック 10。ミシックなしが PITY 回続いたら必ずミシック)。
    // 品はその職のその稀さの、どの祠でも出るものとその祠のもの (ミシックはその祠のものだけ)
    public static final int PITY = 8;
    static final double[] RARITY_WEIGHTS = {0.60, 0.30, 0.10};

    @Nullable
    public static Aspect roll(Job job, String shrine, int sinceMythic, RandomSource r) {
        int rarity;
        if (sinceMythic >= PITY) {
            rarity = Aspect.MYTHIC;
        } else {
            double x = r.nextDouble();
            rarity = x < RARITY_WEIGHTS[0] ? Aspect.RARE : x < RARITY_WEIGHTS[0] + RARITY_WEIGHTS[1] ? Aspect.EPIC : Aspect.MYTHIC;
        }
        List<Aspect> pool = new ArrayList<>();
        for (Aspect a : AspectTable.ALL) {
            if (a.job() == job && a.rarity() == rarity && (a.shared() || a.shrine().equals(shrine))) {
                pool.add(a);
            }
        }
        return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
    }

    // その祠で出るもの (祭司の画面の「報い」の頁): その職のどの祠でも出るレア・エピックと、その祠のレア・エピック・ミシック
    public static List<Aspect> pool(Job job, String shrine) {
        List<Aspect> out = new ArrayList<>();
        for (Aspect a : AspectTable.ALL) {
            if (a.job() == job && (a.shared() || a.shrine().equals(shrine))) {
                out.add(a);
            }
        }
        return out;
    }
}

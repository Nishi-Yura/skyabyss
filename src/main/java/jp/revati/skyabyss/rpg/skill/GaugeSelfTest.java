package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 型の溜まるもの (Gauges。docs/design/48 の 2.6) の自己テスト。SKYABYSS_SELFTEST_ONLY="gauges"。
// 18 の型ごとに、溜まり方・上限・放ったときの傷・使い切りを確かめる (偽のプレイヤーと、動かない硬いゾンビで)
public final class GaugeSelfTest {
    private GaugeSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double px = spawn.getX() + 0.5;
        double py = 300;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "gauge_test"));
        p.moveTo(px, py, pz, 0f, 0f); // 南 (+Z) を向く
        Growth.get(p).level = 50;
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
        List<Zombie> zs = new ArrayList<>();
        Zombie a = zombie(level, px, py, pz + 2.0, zs);
        Zombie b = zombie(level, px + 2.0, py, pz + 2.5, zs);
        try {
            SkillManager.forgetAll(p);
            check(f, Gauges.pick(p) == null, "a player without a job has a gauge");
            check(f, Gauges.Kind.values().length == 18 && Gauges.Kind.of(Job.WARRIOR, "cleaver") == Gauges.Kind.CLEAVER, "gauge kinds are wrong");
            for (Gauges.Kind k : Gauges.Kind.values()) {
                check(f, Gauges.Kind.of(k.job, k.arch) == k, "gauge " + k + " has no archetype");
            }

            // 研ぎ: 近接で溜め、巨神斬りの力に足し、使い切る
            Gauges.force(p, Gauges.Kind.CLEAVER);
            for (int i = 0; i < 7; i++) {
                Gauges.onMelee(p, a);
            }
            check(f, Gauges.value(p) == 5, "whetting did not stop at 5: " + Gauges.value(p));
            check(f, Math.abs(Gauges.power(p, Skill.TITAN_CLEAVE) - 1.0) < 1e-9 && Gauges.power(p, Skill.WHALE_SWEEP) == 0, "whetting power is wrong");
            Gauges.afterCast(p, Skill.TITAN_CLEAVE);
            check(f, Gauges.value(p) == 0, "titan cleave did not use the whetting");

            // 舞: 違う技で一段ずつ、同じ技で 0。段の分だけ待ちが短い
            Gauges.force(p, Gauges.Kind.DANCE);
            Gauges.afterCast(p, Skill.LUNGE);
            Gauges.afterCast(p, Skill.TWIN_FANG);
            Gauges.afterCast(p, Skill.WIND_REND);
            check(f, Gauges.value(p) == 2 && Math.abs(Gauges.cooldownScale(p, Skill.LUNGE) - 0.9) < 1e-9, "dance steps are wrong: " + Gauges.value(p));
            Gauges.afterCast(p, Skill.WIND_REND);
            check(f, Gauges.value(p) == 0, "repeating a skill did not reset the dance");

            // 潮位: 引いた数で溜まり、満ちると次の技が満ち潮になる
            Gauges.force(p, Gauges.Kind.EBB);
            Gauges.onEbb(p, 5);
            Gauges.onEbb(p, 5);
            check(f, Gauges.value(p) == 8, "tide level did not stop at 8: " + Gauges.value(p));
            float ha = a.getHealth();
            Gauges.afterCast(p, Skill.WARD_SONG);
            check(f, Gauges.value(p) == 0 && a.getHealth() < ha, "flood tide did not fire: " + ha + " -> " + a.getHealth());

            // 影の印: 踏み込みで印、風裂きで弾ける
            Gauges.force(p, Gauges.Kind.SHADOW);
            for (int i = 0; i < 4; i++) {
                Gauges.state(p).lastCastGain = -1;
                Gauges.onSkillHit(p, a, Skill.LUNGE);
            }
            check(f, Gauges.value(p) == 3, "shadow marks did not stop at 3: " + Gauges.value(p));
            Gauges.state(p).lastCastGain = -1;
            Gauges.onSkillHit(p, b, Skill.TWIN_FANG);
            check(f, Gauges.value(p) == 1, "marking another enemy did not start over: " + Gauges.value(p));
            heal(zs);
            float hb = b.getHealth();
            Gauges.onSkillHit(p, b, Skill.WIND_REND);
            check(f, Gauges.value(p) == 0 && b.getHealth() < hb, "wind rend did not detonate the marks");

            // 帯電: 3 重ねの敵へ雷の矢を当てると跳ねる
            Gauges.force(p, Gauges.Kind.THUNDER);
            SkillManager.forceRank(p, Skill.THUNDER_BOLT, 1);
            for (int i = 0; i < 3; i++) {
                Gauges.onSkillHit(p, a, Skill.THUNDER_BOLT);
            }
            check(f, Gauges.value(p) == 3, "static did not reach 3: " + Gauges.value(p));
            heal(zs);
            hb = b.getHealth();
            Gauges.onSkillHit(p, a, Skill.THUNDER_BOLT);
            check(f, Gauges.value(p) == 0 && b.getHealth() < hb, "a charged bolt did not jump: " + hb + " -> " + b.getHealth());

            // 凍え: 冷えた敵に技が当たると凍り、凍った敵への雷の矢 +50%
            Gauges.force(p, Gauges.Kind.TIDE);
            Gauges.chill(a, 100);
            Gauges.onSkillHit(p, a, Skill.STARDUST);
            check(f, Gauges.frozen(a) && Math.abs(Gauges.versus(p, a, Skill.THUNDER_BOLT) - 1.5) < 1e-9 && Gauges.versus(p, a, Skill.STARDUST) == 1,
                    "frostbite did not freeze or boost the bolt");
            Gauges.onSkillHit(p, b, Skill.STARDUST);
            check(f, !Gauges.frozen(b), "an enemy that was not chilled froze");

            // 孤り: 満ちていれば次の一撃 ×2.5、使い切る
            Gauges.force(p, Gauges.Kind.LONE);
            Gauges.set(p, 120);
            check(f, Gauges.meleeMultiplier(p) == Gauges.LONE_STRIKE && Gauges.meleeMultiplier(p) == 1, "solitude strike is wrong");

            // 呪いの重ね: 3 重ねで弾け、まわりへ一つ移る
            Gauges.force(p, Gauges.Kind.CURSE);
            heal(zs);
            ha = a.getHealth();
            for (int i = 0; i < 3; i++) {
                Gauges.onCurse(p, a, 1, 100);
            }
            check(f, a.getHealth() < ha && Gauges.value(p) == 1 && Gauges.state(p).target == b, "the curse did not burst and spread");

            // 熾火: 燃えている敵に当てると溜まる (一回の技で一つ)
            Gauges.force(p, Gauges.Kind.FIRE);
            a.setRemainingFireTicks(100);
            Gauges.onSkillHit(p, a, Skill.STARDUST);
            Gauges.onSkillHit(p, a, Skill.STARDUST);
            check(f, Gauges.value(p) == 1 && Gauges.orbStacks(p) == 1 && Math.abs(Gauges.orbPower(1) - 1.2) < 1e-9, "embers are wrong: " + Gauges.value(p));
            a.setRemainingFireTicks(0);

            // 調べ: 唄で溜まり、灯の守り札を広く長くする
            Gauges.force(p, Gauges.Kind.WARD);
            for (int i = 0; i < 4; i++) {
                Gauges.afterCast(p, Skill.WARD_SONG);
            }
            check(f, Gauges.value(p) == 3 && Gauges.lampRadius(p) == 3 && Gauges.lampTicks(p) == 180, "refrain is wrong");
            Gauges.afterCast(p, Skill.LAMP_CHARM);
            check(f, Gauges.value(p) == 0, "lamp charm did not use the refrain");

            // 岩の重み: 傷と盾の技で溜まり、満ちた盾殴りが岩砕き
            Gauges.force(p, Gauges.Kind.SHIELD);
            Gauges.onHurt(p);
            Gauges.afterCast(p, Skill.SHIELD_RUSH);
            check(f, Gauges.value(p) == Gauges.SHIELD_HURT + Gauges.SHIELD_SKILL, "stone weight is wrong: " + Gauges.value(p));
            Gauges.set(p, 100);
            heal(zs);
            ha = a.getHealth();
            Gauges.afterCast(p, Skill.SHIELD_BASH);
            check(f, Gauges.value(p) == 0 && a.getHealth() < ha, "rockbreaker did not fire");

            // 鍛え: 焼き入れのあいだの近接で溜まり、満ちると炎 ×2
            Gauges.force(p, Gauges.Kind.TEMPER);
            SkillManager.get(p).temperUntil = level.getGameTime() + 100;
            for (int i = 0; i < 10; i++) {
                Gauges.onMelee(p, a);
            }
            check(f, Gauges.temperBoost(p) && Gauges.value(p) == 0, "tempering did not boost the fire");
            SkillManager.get(p).temperUntil = 0;

            // 空の矢筒・狙い・勢い・灰の層
            Gauges.force(p, Gauges.Kind.RAIN);
            for (int i = 0; i < 25; i++) {
                Gauges.onArrow(p);
            }
            check(f, Gauges.value(p) == 20, "sky quiver did not stop at 20");
            Gauges.force(p, Gauges.Kind.PIERCE);
            Gauges.set(p, 2);
            check(f, Gauges.pierceBonus(p) == 2 && Math.abs(Gauges.power(p, Skill.CLOUD_PIERCE) - 0.6) < 1e-9, "aim bonus is wrong");
            Gauges.force(p, Gauges.Kind.TAIL);
            Gauges.onSkillHit(p, a, Skill.SPIN_CUT);
            Gauges.onSkillHit(p, b, Skill.SPIN_CUT);
            check(f, Gauges.value(p) == 2 && p.getAttribute(Attributes.ATTACK_SPEED).getModifier(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gauge/tail_attack_speed")) != null, "momentum is wrong");
            Gauges.reset(p);
            check(f, p.getAttribute(Attributes.ATTACK_SPEED).getModifier(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gauge/tail_attack_speed")) == null, "momentum left its attack speed");
            Gauges.force(p, Gauges.Kind.ASH);
            SkillEffects.ashFall(a, 100);
            Gauges.onSkillHit(p, a, Skill.STARDUST);
            check(f, Gauges.value(p) == 1, "ash layers did not grow");

            // 炉の熱: 技で溜まり、満ちた熾火の槌が炉開き
            Gauges.force(p, Gauges.Kind.EMBER);
            for (int i = 0; i < 5; i++) {
                Gauges.afterCast(p, Skill.TEMPER);
            }
            check(f, Gauges.value(p) == 100, "forge heat did not fill: " + Gauges.value(p));
            heal(zs);
            ha = a.getHealth();
            Gauges.afterCast(p, Skill.EMBER_HAMMER);
            check(f, Gauges.value(p) == 0 && a.getHealth() < ha, "forge open did not fire");

            // 罠の糸: 置いた罠の数を見せ、足止めの矢で近くの罠がはぜる
            Gauges.force(p, Gauges.Kind.TRAP);
            SkillEffects.zone(SkillEffects.ZoneKind.TRAP, p, new Vec3(px - 1, py, pz + 3), SkillRules.TRAP_TRIGGER, 200, 1);
            SkillEffects.zone(SkillEffects.ZoneKind.TRAP, p, new Vec3(px + 1, py, pz + 3), SkillRules.TRAP_TRIGGER, 200, 1);
            Gauges.tick(p);
            check(f, Gauges.value(p) == 2, "trap lines did not count the traps: " + Gauges.value(p));
            heal(zs);
            ha = a.getHealth();
            Gauges.onSkillHit(p, a, Skill.SNARE_SHOT);
            check(f, a.getHealth() < ha && SkillEffects.zonesListOf(SkillEffects.ZoneKind.TRAP, p.getUUID()).isEmpty(), "snare shot did not detonate the traps");

            // 待ちとマナ (00 の 343 A)
            check(f, SkillRules.cooldownTicks(Skill.TITAN_CLEAVE, 1) == Math.round(8 * 20 * SkillRules.COOLDOWN_SCALE)
                    && SkillRules.manaCost(Skill.TITAN_CLEAVE, 1) == Math.round(10 * SkillRules.MANA_SCALE), "cooldown / mana scale is wrong");
        } catch (Exception e) {
            f.add("threw " + e);
            SkyAbyss.LOGGER.error("SELFTEST gauges threw", e);
        } finally {
            Gauges.forget(p.getUUID());
            for (Zombie z : zs) {
                z.discard();
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST gauges PASSED (18 archetype gauges: build, cap, release, reset; cooldown and mana scale)");
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST gauges FAILED: {}", s));
        }
    }

    private static Zombie zombie(ServerLevel level, double x, double y, double z, List<Zombie> out) {
        Zombie zb = new Zombie(EntityType.ZOMBIE, level);
        zb.moveTo(x, y, z, 180f, 0f);
        zb.setNoAi(true);
        zb.setNoGravity(true);
        zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        zb.setHealth(1000);
        level.addFreshEntity(zb);
        out.add(zb);
        return zb;
    }

    private static void heal(List<Zombie> zs) {
        for (Zombie z : zs) {
            z.setHealth(z.getMaxHealth());
            z.invulnerableTime = 0;
        }
    }
}

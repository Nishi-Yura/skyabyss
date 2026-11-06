package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 二人の試遊 (2026-10-22) の決まりの自己テスト。段の名前は "combat rules"。
//  - 人が当てた敵の無敵の間がほぼ無い (同じ tick の二人の一撃が両方通る。MobHitFrames)。人の無敵はそのまま
//  - 巨神の背の中では耐久が減らない (地上では減る。mixin/ItemStackDurabilityMixin)
public final class CombatRulesSelfTest {
    private CombatRulesSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("combat rules", CombatRulesSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            ServerLevel over = server.overworld();
            FakePlayer a = FakePlayerFactory.get(over, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0a01"), "hit_a"));
            FakePlayer b = FakePlayerFactory.get(over, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0a02"), "hit_b"));
            BlockPos at = over.getSharedSpawnPos().above(40);
            Zombie z = EntityType.ZOMBIE.create(over);
            if (z == null) {
                f.add("could not make a zombie");
            } else {
                z.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                z.setNoAi(true);
                over.addFreshEntity(z);
                float hp = z.getHealth();
                z.hurt(over.damageSources().playerAttack(a), 3f);
                float one = hp - z.getHealth();
                z.hurt(over.damageSources().playerAttack(b), 3f);
                z.hurt(over.damageSources().playerAttack(a), 3f);
                float lost = hp - z.getHealth();
                // 防具で少し減るので、一撃目の減りの 3 倍と比べる
                if (one <= 0 || lost < one * 2.95f) {
                    f.add("player hits in the same tick were eaten by invulnerability: lost " + lost + " (one hit " + one + ")");
                }
                // 人でない出どころ (ここでは名前のない傷) の無敵はバニラのまま
                Zombie y = EntityType.ZOMBIE.create(over);
                y.setPos(at.getX() + 3.5, at.getY(), at.getZ() + 0.5);
                y.setNoAi(true);
                over.addFreshEntity(y);
                float hy = y.getHealth();
                y.hurt(over.damageSources().generic(), 3f);
                y.hurt(over.damageSources().generic(), 3f);
                if (hy - y.getHealth() > 3.1f) {
                    f.add("non-player damage lost its invulnerability frames");
                }
                z.discard();
                y.discard();
            }
            // 耐久: 背の中では減らず、地上では減る
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.hurtAndBreak(5, over, a, item -> {
            });
            if (sword.getDamageValue() != 5) {
                f.add("durability did not drop on the ground: " + sword.getDamageValue());
            }
            ServerLevel back = server.getLevel(jp.revati.skyabyss.titan.TitanTravel.TITAN_BACK);
            if (back == null) {
                f.add("no titan back to test durability");
            } else {
                FakePlayer c = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0a03"), "wear_c"));
                ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
                sword.hurtAndBreak(5, back, c, item -> {
                });
                chest.hurtAndBreak(3, back, c, item -> {
                });
                if (sword.getDamageValue() != 5 || chest.getDamageValue() != 0) {
                    f.add("durability dropped on a titan back: sword " + sword.getDamageValue() + ", chestplate " + chest.getDamageValue());
                }
            }
            // 崩しゲージ (docs/design/47 の 2.1)。ふつうの敵には倍率なし (身構えは外した)
            Zombie boss = EntityType.ZOMBIE.create(over);
            Zombie foe = EntityType.ZOMBIE.create(over);
            if (boss != null && foe != null) {
                boss.setPos(at.getX() + 6.5, at.getY(), at.getZ() + 0.5);
                foe.setPos(at.getX() + 9.5, at.getY(), at.getZ() + 0.5);
                boss.setNoAi(true);
                foe.setNoAi(true);
                over.addFreshEntity(boss);
                over.addFreshEntity(foe);
                BossOpenings.plantedForTest = e -> e == boss;
                // 一撃ではゲージが満ちない大きさに
                boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                boss.setHealth(200);
                try {
                    if (BossOpenings.multiplier(boss) != BreakGauge.NORMAL || BossOpenings.multiplier(foe) != 1f) {
                        f.add("a boss should take x" + BreakGauge.NORMAL + " and a plain foe x1 (" + BossOpenings.multiplier(boss) + ", "
                                + BossOpenings.multiplier(foe) + ")");
                    }
                    for (int i = 0; i < 8; i++) {
                        foe.invulnerableTime = 0;
                        foe.setHealth(foe.getMaxHealth());
                        foe.hurt(over.damageSources().playerAttack(a), 1f);
                    }
                    if (BossOpenings.multiplier(foe) != 1f) {
                        f.add("a foe hit many times should not brace any more");
                    }
                    // 技・矢の傷で溜まる (×SKILL)。隙のあいだは ×OPENING
                    float hp = boss.getHealth();
                    boss.invulnerableTime = 0;
                    boss.hurt(over.damageSources().playerAttack(a), 2f);
                    double plain = BreakGauge.fill(boss);
                    if (hp - boss.getHealth() > 2f * BreakGauge.NORMAL + 0.01f || plain <= 0) {
                        f.add("a boss hit should deal x" + BreakGauge.NORMAL + " and fill the gauge (lost " + (hp - boss.getHealth()) + ", fill " + plain + ")");
                    }
                    BossOpenings.moveEnded(boss);
                    boss.setHealth(boss.getMaxHealth());
                    boss.invulnerableTime = 0;
                    boss.hurt(over.damageSources().playerAttack(a), 1f);
                    double opened = BreakGauge.fill(boss) - plain;
                    if (opened < plain * 0.5 * BreakGauge.OPENING * 0.9) {
                        f.add("a hit in the opening should fill x" + BreakGauge.OPENING + " (" + plain + " then +" + opened + ")");
                    }
                    // 満ちると崩れる: 傷 ×BROKEN、技を出せない、次のゲージは大きい
                    double first = BreakGauge.size(boss, 0);
                    if (!BreakGauge.add(boss, first) || !BreakGauge.broken(boss) || BossOpenings.multiplier(boss) != BreakGauge.BROKEN
                            || !BreakGauge.silenced(boss)) {
                        f.add("a full gauge should break the boss (x" + BreakGauge.BROKEN + ", no moves)");
                    }
                    if (BreakGauge.breaks(boss) != 1 || Math.abs(BreakGauge.size(boss, 1) - first * BreakGauge.GROW[1]) > 1.0E-6
                            || Math.abs(BreakGauge.size(boss, 5) - first * BreakGauge.GROW[2]) > 1.0E-6) {
                        f.add("the next gauge should grow x" + BreakGauge.GROW[1] + " then x" + BreakGauge.GROW[2]);
                    }
                    if (BreakGauge.add(boss, first * 3) || BreakGauge.fill(boss) != 0) {
                        f.add("a broken boss should not fill");
                    }
                    // 仕掛けを解くと半分
                    BreakGauge.forget(boss);
                    BreakGauge.solved(boss);
                    if (Math.abs(BreakGauge.fill(boss) - BreakGauge.SOLVED) > 1.0E-6 || !BossOpenings.isOpen(boss)) {
                        f.add("solving a mechanic should fill " + BreakGauge.SOLVED + " and open the boss (" + BreakGauge.fill(boss) + ")");
                    }
                } finally {
                    BossOpenings.plantedForTest = null;
                    BreakGauge.forget(boss);
                    boss.discard();
                    foe.discard();
                }
            }
            // 倒れたあとの弱り (DeathWeakness): 与える傷 ×0.85
            Zombie dummy = EntityType.ZOMBIE.create(over);
            if (dummy != null) {
                dummy.setPos(at.getX() + 12.5, at.getY(), at.getZ() + 0.5);
                dummy.setNoAi(true);
                over.addFreshEntity(dummy);
                dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                dummy.setHealth(200);
                try {
                    dummy.invulnerableTime = 0;
                    float h0 = dummy.getHealth();
                    dummy.hurt(over.damageSources().playerAttack(a), 10f);
                    float plain = h0 - dummy.getHealth();
                    a.addEffect(new net.minecraft.world.effect.MobEffectInstance(DeathWeakness.WEAKENED, 100));
                    dummy.invulnerableTime = 0;
                    float h1 = dummy.getHealth();
                    dummy.hurt(over.damageSources().playerAttack(a), 10f);
                    float weak = h1 - dummy.getHealth();
                    if (plain <= 0 || Math.abs(weak - plain * DeathWeakness.DEALT) > 0.05f) {
                        f.add("a weakened player should deal x" + DeathWeakness.DEALT + " (" + plain + " -> " + weak + ")");
                    }
                } finally {
                    a.removeEffect(DeathWeakness.WEAKENED);
                    dummy.discard();
                }
            }
            // 傷と体力は有限のまま (DamageSanity。2026-11-07: /kill × 弱りで体力が NaN になり、体力 0 のまま動けなくなった)
            Zombie odd = EntityType.ZOMBIE.create(over);
            if (odd != null) {
                odd.setPos(at.getX() + 14.5, at.getY(), at.getZ() + 0.5);
                odd.setNoAi(true);
                over.addFreshEntity(odd);
                try {
                    odd.setAbsorptionAmount(Float.NaN);
                    odd.setHealth(Float.NaN);
                    if (!DamageSanity.repair(odd) || !Float.isFinite(odd.getHealth()) || odd.getHealth() <= 0 || odd.getAbsorptionAmount() != 0f) {
                        f.add("non-finite health was not repaired (" + odd.getHealth() + " / " + odd.getAbsorptionAmount() + ")");
                    }
                    odd.invulnerableTime = 0;
                    odd.hurt(over.damageSources().generic(), Float.POSITIVE_INFINITY);
                    if (!Float.isFinite(odd.getHealth()) || odd.isAlive()) {
                        f.add("an infinite hit should kill with finite health (" + odd.getHealth() + ")");
                    }
                } finally {
                    odd.discard();
                }
            }
            if (!FightRations.ration(new ItemStack(Items.BREAD)) || FightRations.ration(new ItemStack(Items.IRON_SWORD))) {
                f.add("bread should count as a ration and a sword should not");
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST combat rules crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST combat rules PASSED (player hits pass with no invulnerability on foes; no wear on titan backs; boss break gauge (x0.8, fills, opening x3, breaks x2.0, grows, mechanic half), no bracing, weakened after death x0.85, finite damage and health, rations)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST combat rules FAILED: {}", s);
            }
        }
    }
}

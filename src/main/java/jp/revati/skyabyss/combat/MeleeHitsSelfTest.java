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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 近接の一撃の決まり (MeleeHits) の自己テスト。段の名前は "melee hits"。
//  - 同じ人が同じ敵を、殴れる間のうちにもう一度殴っても空振り。ほかの人の一撃は同じ tick でも通る
//  - 溜め 0 の一撃はほぼ傷にならない (満ちた一撃の 5% 未満)。溜め半分は満ちた一撃の 2.5 割ほど
//  - 満ちていない一撃の中の回復は起こらず、満ちた一撃の中の回復は起こる (自分も仲間も)
//  - 二人: 同じ tick の満ちた一撃はどちらも通る。一人の弱い一撃の中でも、もう一人の傷は弱まらない
//  - 溜めの手がかり: 弱い一撃が溜まり、戦いが落ち着いたら出る。満ちた一撃に慣れた人には出ない
//  - 武器の効き目 (上乗せ・追加効果) は満ちた一撃だけ (水路の牙の出血で確かめる)
public final class MeleeHitsSelfTest {
    private MeleeHitsSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("melee hits", MeleeHitsSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel over = server.overworld();
        FakePlayer a = FakePlayerFactory.get(over, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0b01"), "melee_a"));
        FakePlayer b = FakePlayerFactory.get(over, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0b02"), "melee_b"));
        BlockPos at = over.getSharedSpawnPos().above(44);
        Zombie z = EntityType.ZOMBIE.create(over);
        MeleeHits.testAllowFake = true;
        try {
            if (z == null) {
                f.add("could not make a zombie");
                return;
            }
            z.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            z.setNoAi(true);
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
            z.setHealth(500);
            over.addFreshEntity(z);
            a.setPos(at.getX() + 0.5, at.getY(), at.getZ() - 1.0);
            b.setPos(at.getX() + 1.5, at.getY(), at.getZ() - 1.0);
            MeleeHits.forget(a);
            MeleeHits.forget(b);
            // 満ちた一撃
            a.attackStrengthTicker = 100;
            float h0 = z.getHealth();
            boolean first = MeleeHits.attack(a, z);
            float full = h0 - z.getHealth();
            if (!first || full <= 0) {
                f.add("a full-charge hit did not land (" + full + ")");
            }
            // 同じ人がすぐもう一度: 空振り
            a.attackStrengthTicker = 100;
            float h1 = z.getHealth();
            if (MeleeHits.attack(a, z) || z.getHealth() != h1 || !MeleeHits.cooling(a, z)) {
                f.add("the same player hit the same foe again inside the cooldown (lost " + (h1 - z.getHealth()) + ")");
            }
            // ほかの人は同じ tick でも通る
            b.attackStrengthTicker = 100;
            if (!MeleeHits.attack(b, z) || z.getHealth() >= h1) {
                f.add("another player's hit was blocked by the first player's cooldown");
            }
            // 溜め 0
            MeleeHits.forget(a);
            a.attackStrengthTicker = 0;
            float h2 = z.getHealth();
            MeleeHits.attack(a, z);
            float weak = h2 - z.getHealth();
            if (weak >= full * 0.05f) {
                f.add("a no-charge hit still did " + weak + " (full " + full + ")");
            }
            // 溜め半分
            MeleeHits.forget(a);
            a.attackStrengthTicker = Math.round(a.getCurrentItemAttackStrengthDelay() * 0.5f - 0.5f);
            float charge = a.getAttackStrengthScale(0.5f);
            float h3 = z.getHealth();
            MeleeHits.attack(a, z);
            float half = h3 - z.getHealth();
            float want = full * charge * charge;
            if (Math.abs(half - want) > full * 0.08f) {
                f.add(String.format(java.util.Locale.ROOT, "a %.2f-charge hit did %.3f, expected about %.3f (full %.3f)", charge, half, want, full));
            }
            // 回復は満ちた一撃だけ
            a.setHealth(10f);
            MeleeHits.during(a, 0.5f, () -> a.heal(4f));
            if (a.getHealth() != 10f) {
                f.add("a heal went through inside a half-charge hit (" + a.getHealth() + ")");
            }
            MeleeHits.during(a, 1f, () -> a.heal(4f));
            if (a.getHealth() != 14f) {
                f.add("a heal was blocked inside a full-charge hit (" + a.getHealth() + ")");
            }
            a.heal(2f);
            if (a.getHealth() != 16f) {
                f.add("a heal outside any hit was blocked (" + a.getHealth() + ")");
            }
            // 二人: 同じ tick に満ちた一撃を当てると、どちらも通る
            MeleeHits.forget(a);
            MeleeHits.forget(b);
            a.attackStrengthTicker = 100;
            b.attackStrengthTicker = 100;
            float h4 = z.getHealth();
            boolean both = MeleeHits.attack(a, z) & MeleeHits.attack(b, z);
            float two = h4 - z.getHealth();
            if (!both || two < full * 1.9f) {
                f.add("two players' full hits in the same tick did not both land (" + two + ", one " + full + ")");
            }
            // 二人: 一人の満ちていない一撃の中でも、もう一人の傷は弱まらない
            float h5 = z.getHealth();
            MeleeHits.during(a, 0.1f, () -> z.hurt(over.damageSources().playerAttack(b), 3f));
            float bHit = h5 - z.getHealth();
            float h6 = z.getHealth();
            MeleeHits.during(a, 1f, () -> z.hurt(over.damageSources().playerAttack(b), 3f));
            if (Math.abs(bHit - (h6 - z.getHealth())) > 0.01f || bHit <= 0) {
                f.add("the other player's damage was scaled by this player's weak swing (" + bHit + " vs " + (h6 - z.getHealth()) + ")");
            }
            // 二人: 満ちていない一撃の中では、仲間を癒す効き目も起こらない。満ちた一撃では起こる
            b.setHealth(10f);
            MeleeHits.during(a, 0.5f, () -> b.heal(3f));
            MeleeHits.during(a, 1f, () -> b.heal(3f));
            if (b.getHealth() != 13f) {
                f.add("an ally heal inside the swing was wrong (" + b.getHealth() + ", want 13: weak blocked, full passes)");
            }
            // 武器の効き目は満ちた一撃だけ (weakHit・GearLevel.proc と、水路の牙の出血で通しを確かめる)
            MeleeHits.during(a, 0.5f, () -> {
                if (!MeleeHits.weakHit(a) || MeleeHits.weakHit(b) || jp.revati.skyabyss.item.GearLevel.proc(a, net.minecraft.world.item.ItemStack.EMPTY)) {
                    f.add("inside a half-charge swing, weakHit/proc should hold for the swinger only");
                }
            });
            MeleeHits.during(a, 1f, () -> {
                if (MeleeHits.weakHit(a) || !jp.revati.skyabyss.item.GearLevel.proc(a, net.minecraft.world.item.ItemStack.EMPTY)) {
                    f.add("inside a full-charge swing, weapon effects should be allowed");
                }
            });
            int level = jp.revati.skyabyss.rpg.Growth.get(a).level;
            jp.revati.skyabyss.rpg.Growth.get(a).level = 60;
            a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.registry.ModItems.WATERWAY_FANG.get()));
            MeleeHits.forget(a);
            z.removeAllEffects();
            a.attackStrengthTicker = 0;
            MeleeHits.attack(a, z);
            boolean weakBled = z.hasEffect(jp.revati.skyabyss.registry.ModEffects.BLEEDING);
            MeleeHits.forget(a);
            a.attackStrengthTicker = 100;
            MeleeHits.attack(a, z);
            boolean fullBled = z.hasEffect(jp.revati.skyabyss.registry.ModEffects.BLEEDING);
            if (weakBled || !fullBled) {
                f.add("the waterway fang's bleed should come only from a full-charge hit (weak " + weakBled + ", full " + fullBled + ")");
            }
            a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            jp.revati.skyabyss.rpg.Growth.get(a).level = level;
            z.removeAllEffects();
            // 溜めの手がかり: 弱い一撃が WEAK_HITS 回たまり、近接が落ち着いたら出す。満ちた一撃を LEARNED 回当てた人には出さない
            MeleeHits.forget(a);
            a.getPersistentData().remove(MeleeHits.TIPS_KEY);
            a.getPersistentData().remove(MeleeHits.FULL_KEY);
            long now = over.getGameTime();
            for (int i = 0; i < MeleeHits.WEAK_HITS; i++) {
                MeleeHits.coach(a, 0.1f, now);
            }
            if (MeleeHits.tipDue(a, now)) {
                f.add("the charge tip came during the fight");
            }
            if (!MeleeHits.tipDue(a, now + MeleeHits.CALM_MELEE)) {
                f.add("the charge tip did not come after " + MeleeHits.WEAK_HITS + " weak swings and a calm moment");
            }
            MeleeHits.forget(a);
            a.getPersistentData().putInt(MeleeHits.FULL_KEY, MeleeHits.LEARNED);
            for (int i = 0; i < MeleeHits.WEAK_HITS; i++) {
                MeleeHits.coach(a, 0.1f, now);
            }
            if (MeleeHits.tipDue(a, now + MeleeHits.CALM_MELEE)) {
                f.add("the charge tip came for a player who already lands full hits");
            }
            a.getPersistentData().remove(MeleeHits.FULL_KEY);
            if (MeleeHits.cooldown(a) != Math.max(MeleeHits.MIN_COOLDOWN, Math.round(a.getCurrentItemAttackStrengthDelay() * MeleeHits.COOLDOWN_SHARE))) {
                f.add("cooldown " + MeleeHits.cooldown(a) + " does not follow the swing length " + a.getCurrentItemAttackStrengthDelay());
            }
        } finally {
            MeleeHits.testAllowFake = false;
            MeleeHits.forget(a);
            MeleeHits.forget(b);
            if (z != null) {
                z.discard();
            }
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST melee hits FAILED: {}", m));
            if (f.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST melee hits PASSED (same-foe cooldown per player, others pass in the same tick, damage x charge^2, heals and weapon effects only on full-charge hits, two players, charge tip)");
            }
        }
    }
}

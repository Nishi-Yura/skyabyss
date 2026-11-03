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
//  - 満ちていない一撃の中の回復は起こらず、満ちた一撃の中の回復は起こる
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
                SkyAbyss.LOGGER.info("SELFTEST melee hits PASSED (same-foe cooldown per player, others pass in the same tick, damage x charge^2, heals only on full-charge hits)");
            }
        }
    }
}

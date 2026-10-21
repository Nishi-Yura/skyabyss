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
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST combat rules crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST combat rules PASSED (player hits pass with no invulnerability on foes; no wear on titan backs)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST combat rules FAILED: {}", s);
            }
        }
    }
}

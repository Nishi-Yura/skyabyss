package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 魔法使いの魔弾 (StaffBolts) の自己テスト。段 "staff bolt":
//  - 杖を持ち、8 ブロック先の敵を見て撃つと当たり、傷が入る
//  - 続けて撃てる間より早い二発目は出ない
//  - 間に壁があると当たらない。RANGE より遠い敵には届かない
//  - 杖でない武器では撃たない
public final class StaffBoltSelfTest {
    private StaffBoltSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("staff bolt", StaffBoltSelfTest::run));
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000b0175"), "staff_bolt"));
        // いつも読み込まれている初めの場所の上 (近接の自己テストと同じ)
        BlockPos base = level.getSharedSpawnPos().above(48).north(10);
        Mob z = null;
        boolean melee = MeleeHits.testAllowFake;
        MeleeHits.testAllowFake = true;
        try {
            var staff = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "wild_scribe_staff"));
            check(f, staff != Items.AIR, "the test staff is not registered");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(staff));
            p.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0f, 0f);
            p.setYRot(0f);
            p.setXRot(0f);
            p.yHeadRot = 0f;
            // この MOD の敵 (バニラの敵は世界に入れない決まり)
            var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "moss_crawler"));
            z = (Mob) type.create(level);
            z.setNoAi(true);
            z.setNoGravity(true);
            z.moveTo(base.getX() + 0.5, p.getEyeY() - 0.3, base.getZ() + 8.5, 180f, 0f);
            level.addFreshEntity(z);
            float before = z.getHealth();
            p.attackStrengthTicker = 100;
            StaffBolts.forgetForTest(p);
            boolean added = level.getEntity(z.getId()) != null;
            var hitFoe = StaffBolts.cast(p);
            check(f, hitFoe == z, "the bolt did not hit the foe 8 blocks ahead (hit " + hitFoe + ", foe in level " + added + " at " + z.position()
                    + ", eye " + p.getEyePosition() + ", look " + p.getViewVector(1f) + ", foe " + jp.revati.skyabyss.rpg.skill.SkillActions.isFoe(p, z) + ")");
            check(f, z.getHealth() < before, "the bolt hit but did no damage (" + before + " -> " + z.getHealth() + ")");
            // 続けては撃てない
            check(f, StaffBolts.cast(p) == null, "a second bolt went out within the gap");
            // 壁があれば当たらない
            BlockPos wall = BlockPos.containing(base.getX() + 0.5, p.getEyeY(), base.getZ() + 4.5);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            StaffBolts.forgetForTest(p);
            p.attackStrengthTicker = 100;
            check(f, StaffBolts.cast(p) == null, "the bolt went through a wall");
            level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            // 届かない
            z.moveTo(base.getX() + 0.5, p.getEyeY() - 0.3, base.getZ() + StaffBolts.RANGE + 4.5, 180f, 0f);
            StaffBolts.forgetForTest(p);
            check(f, StaffBolts.cast(p) == null, "the bolt reached beyond its range");
            // 杖でなければ撃たない
            z.moveTo(base.getX() + 0.5, p.getEyeY() - 0.3, base.getZ() + 6.5, 180f, 0f);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            StaffBolts.forgetForTest(p);
            check(f, StaffBolts.cast(p) == null, "a sword shot a bolt");
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST staff bolt crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            MeleeHits.testAllowFake = melee;
            if (z != null) {
                z.discard();
            }
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST staff bolt PASSED (a staff hits a foe 8 blocks ahead with melee damage, one bolt per gap, no bolt through walls or past {} blocks, no bolt from a sword)", StaffBolts.RANGE);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST staff bolt FAILED: {}", m));
        }
    }

    static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}

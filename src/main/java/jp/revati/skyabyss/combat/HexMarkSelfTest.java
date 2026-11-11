package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 呪術師の呪印 (HexMarks) と魔法使いの魔弾の段 (StaffBolts.tier) の自己テスト。段 "hex mark":
//  - 鈴の杖を持ち、10 ブロック先の床の敵を見て置くと、遅れて噴き上がって傷が入る。マナを使う
//  - 印の中の二体に同時に当たる。印から離れた敵には当たらない
//  - 溜めが空なら置かない。剣では置かない。何もない空を指しても置かない
//  - 魔弾の見た目の段はレベル 10 ごとに上がり、5 段で止まる
public final class HexMarkSelfTest {
    private HexMarkSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("hex mark", HexMarkSelfTest::run));
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000b0176"), "hex_mark"));
        // いつも読み込まれている初めの場所の上に、石の床を敷く
        BlockPos base = level.getSharedSpawnPos().above(56).south(10);
        List<BlockPos> floor = new ArrayList<>();
        Mob a = null;
        Mob b = null;
        boolean melee = MeleeHits.testAllowFake;
        MeleeHits.testAllowFake = true;
        try {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -1; dz <= 14; dz++) {
                    BlockPos q = base.offset(dx, -1, dz);
                    level.setBlock(q, Blocks.STONE.defaultBlockState(), 3);
                    floor.add(q);
                }
            }
            var bell = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "wild_scarecrow_bell_staff"));
            check(f, bell != Items.AIR, "the test bell staff is not registered");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(bell));
            p.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0f, 0f);
            // 10 ブロック先の床を見下ろす (目の高さ 1.62 から)
            float pitch = (float) Math.toDegrees(Math.atan2(1.62, 10.0));
            p.setYRot(0f);
            p.setXRot(pitch);
            p.yHeadRot = 0f;
            var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "moss_crawler"));
            a = (Mob) type.create(level);
            a.setNoAi(true);
            a.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 10.5, 180f, 0f);
            level.addFreshEntity(a);
            b = (Mob) type.create(level);
            b.setNoAi(true);
            b.moveTo(base.getX() + 1.3, base.getY(), base.getZ() + 11.0, 180f, 0f);
            level.addFreshEntity(b);
            float beforeA = a.getHealth();
            float beforeB = b.getHealth();
            SkillManager.setMana(p, SkillManager.maxMana(p));
            double mana0 = SkillManager.get(p).mana;
            boolean[] fired = new boolean[1];
            p.attackStrengthTicker = 100;
            HexMarks.forgetForTest(p);
            MeleeHits.forget(p);
            List<LivingEntity> hits = HexMarks.castAndErupt(p, fired);
            check(f, fired[0], "no hex mark was placed at the foe 10 blocks ahead");
            check(f, hits.contains(a) && hits.contains(b), "the hex mark did not hit both foes inside it (" + hits.size() + " hit)");
            check(f, a.getHealth() < beforeA && b.getHealth() < beforeB, "the hex mark hit but did no damage");
            check(f, Math.abs(mana0 - SkillManager.get(p).mana - HexMarks.cost(p)) < 1e-3, "the hex mark did not spend its mana");
            // 印から離れた敵には当たらない
            // (狙いの線の外: 横へ 3.5)
            b.moveTo(base.getX() + 3.5, base.getY(), base.getZ() + 10.5, 180f, 0f);
            p.attackStrengthTicker = 100;
            HexMarks.forgetForTest(p);
            MeleeHits.forget(p);
            hits = HexMarks.castAndErupt(p, fired);
            check(f, fired[0] && hits.contains(a) && !hits.contains(b), "the hex mark hit a foe far from the mark");
            // 溜めが空なら置かない
            p.attackStrengthTicker = 0;
            HexMarks.forgetForTest(p);
            HexMarks.castAndErupt(p, fired);
            check(f, !fired[0], "a hex mark was placed without charge");
            // 何もない空を指したら置かない
            p.setXRot(-60f);
            p.attackStrengthTicker = 100;
            HexMarks.forgetForTest(p);
            HexMarks.castAndErupt(p, fired);
            check(f, !fired[0], "a hex mark was placed in the empty sky");
            p.setXRot(pitch);
            // 剣では置かない
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            p.attackStrengthTicker = 100;
            HexMarks.forgetForTest(p);
            HexMarks.castAndErupt(p, fired);
            check(f, !fired[0], "a sword placed a hex mark");
            // 魔弾の段
            check(f, StaffBolts.tier(1) == 0 && StaffBolts.tier(9) == 0 && StaffBolts.tier(10) == 1 && StaffBolts.tier(29) == 2
                    && StaffBolts.tier(40) == 4 && StaffBolts.tier(50) == 4 && StaffBolts.LOOKS.length == 5, "the staff bolt look tiers are wrong");
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST hex mark crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            MeleeHits.testAllowFake = melee;
            if (a != null) {
                a.discard();
            }
            if (b != null) {
                b.discard();
            }
            for (BlockPos q : floor) {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST hex mark PASSED (a charged bell staff places a hex mark 10 blocks ahead that erupts on both foes inside it and spends mana; none without charge, at the sky, or from a sword; staff bolt looks change every 10 levels)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST hex mark FAILED: {}", m));
        }
    }

    static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}

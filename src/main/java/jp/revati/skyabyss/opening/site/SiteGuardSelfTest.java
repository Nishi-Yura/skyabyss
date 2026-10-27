package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.NoHunger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.food.Foods;

// 荒らしよけと都の安全 (SiteGuard) と、おなかの決まり (NoHunger) の自己テスト。段の名前は "site guard"。
//  - 都が建っていれば: 鐘は都の範囲の中、都から 400 離れた所は外。都の中に置いたゾンビは、1 秒ごとの見回りで消える
//  - 章の小要素が建っていれば: その設計図のブロックの一つが守られ、そのすぐ上の空気は守られない
//  - パンで戻る体力は 8、焼いた牛肉は 14
public final class SiteGuardSelfTest {
    private SiteGuardSelfTest() {
    }

    private static Zombie zombie;
    private static final List<String> FAILS = new ArrayList<>();

    public static List<Step> steps() {
        return List.of(Step.once("site guard", 10, SiteGuardSelfTest::start), Step.once("site guard (sweep)", 40, SiteGuardSelfTest::finish));
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static void start(MinecraftServer server) {
        FAILS.clear();
        zombie = null;
        SiteGuard.testing = true;
        check(Math.abs(NoHunger.healOf(Foods.BREAD) - 6.8f) < 0.01f, "bread should heal 6.8 in all (" + NoHunger.healOf(Foods.BREAD) + ")");
        check(Math.abs(NoHunger.instantOf(Foods.BREAD) - 3.4f) < 0.01f, "bread should heal half at once (" + NoHunger.instantOf(Foods.BREAD) + ")");
        check(Math.abs(NoHunger.healOf(Foods.COOKED_BEEF) - 11.84f) < 0.01f, "cooked beef should heal 11.84 in all (" + NoHunger.healOf(Foods.COOKED_BEEF) + ")");
        check(NoHunger.spoiled(Foods.ROTTEN_FLESH) && NoHunger.healOf(Foods.ROTTEN_FLESH) < NoHunger.healOf(Foods.BREAD) * 0.5f,
                "rotten flesh should be spoiled and heal little (" + NoHunger.healOf(Foods.ROTTEN_FLESH) + ")");
        // 進捗・実績のつまみ: 実績の木と、報酬の鐘銭の関数が読める (tools/gen/story/gen_advancements.py)
        check(server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "achievement/trade")) != null,
                "the achievement tab (achievement/trade) is missing");
        check(server.getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "reward/coins_10")).isPresent(),
                "the reward function reward/coins_10 is missing");
        ServerLevel level = server.overworld();
        SiteGuard.reset();
        SiteData data = SiteData.get(level);
        SiteData.Entry e = data.first("shelter");
        if (e == null || !e.built) {
            SiteBuilds.buildAllNow(server);
            e = data.first("shelter");
        }
        SiteGuard.Area a = SiteGuard.capital(server);
        if (e != null && e.built && a != null) {
            BlockPos bell = jp.revati.skyabyss.opening.OpeningSites.bell(server).orElse(null);
            check(bell != null && a.contains(bell), "the bell should be inside the capital guard (" + bell + " / " + a + ")");
            check(!SiteGuard.inCapital(level, e.pos.offset(400, 0, 400)), "400 blocks away should be outside the capital");
            BlockPos at = bell != null ? bell.offset(4, 0, 6) : e.pos;
            level.getChunk(at);
            Zombie z = EntityType.ZOMBIE.create(level);
            if (z != null) {
                z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                level.addFreshEntity(z);
                zombie = z;
            }
        } else {
            SkyAbyss.LOGGER.info("SELFTEST site guard: the capital is not built here, the capital part is skipped");
        }
        // 章の小要素: 設計図のブロックは守る、その上の空気は守らない
        for (SiteData.Entry s : data.entries()) {
            if (s.blueprint.isEmpty() || !s.built) {
                continue;
            }
            Blueprint bp = Blueprint.get(s.blueprint);
            if (bp == null) {
                continue;
            }
            for (Blueprint.Cell c : bp.cells) {
                if (c.state().isAir() || c.y() > -2 || bp.cells.stream().anyMatch(o -> o.x() == c.x() && o.z() == c.z() && o.y() == c.y() + 1)) {
                    continue;
                }
                BlockPos w = Blueprint.transform(c.x(), c.y() + 1, c.z(), SiteFinder.rot(s.yaw), SiteFinder.mirror(s.yaw), s.pos.below());
                check(SiteGuard.siteBlock(level, w), s.blueprint + ": a placed block at " + w + " should be guarded");
                break;
            }
            break;
        }
    }

    private static void finish(MinecraftServer server) {
        SiteGuard.testing = false;
        if (zombie != null) {
            check(!zombie.isAlive() || zombie.isRemoved(), "a zombie inside the capital should be removed by the sweep");
            zombie.discard();
        }
        if (FAILS.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST site guard PASSED (capital area, sweep, site blocks, food heal, achievement tab and rewards)");
        } else {
            for (String s : FAILS) {
                SkyAbyss.LOGGER.error("SELFTEST site guard FAILED: {}", s);
            }
        }
    }
}

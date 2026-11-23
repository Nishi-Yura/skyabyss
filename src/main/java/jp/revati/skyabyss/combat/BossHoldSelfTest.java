package jp.revati.skyabyss.combat;

import java.util.List;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// 顔のアップの間は主を止める (BossIntro.hold) の自己テスト。SKYABYSS_SELFTEST_ONLY="boss hold"
//   止めている間: 押されても・向きを変えられても次の tick で元へ戻り、AI は止まる
//   終わったら: 止めた AI は戻り、手放される
public final class BossHoldSelfTest {
    private static Mob mob;
    private static Vec3 at;
    private static int born;
    private static int waited;

    private BossHoldSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(
                Step.once("boss hold (start)", BossHoldSelfTest::start),
                // 主が実際に数 tick 動いてから確かめる (足したばかりの実体は、すぐには tick しないことがある)
                Step.until("boss hold (pinned)", server -> {
                    if (mob != null && mob.tickCount - born < 3 && ++waited < 200) {
                        return false;
                    }
                    pinned(server);
                    return true;
                }),
                Step.once("boss hold (released)", BossIntro.closeUpLength(90) + 10, BossHoldSelfTest::released));
    }

    private static void start(MinecraftServer server) {
        var level = server.overworld();
        level.setChunkForced(0, 0, true);
        mob = EntityType.ZOMBIE.create(level);
        if (mob == null) {
            fail("could not create");
            return;
        }
        mob.moveTo(8.5, 200, 8.5, 30f, 0f);
        mob.setYBodyRot(30f);
        mob.setYHeadRot(30f);
        mob.setNoGravity(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        at = mob.position();
        born = mob.tickCount;
        waited = 0;
        BossIntro.hold(mob, 90);
        if (!BossIntro.held(mob) || !mob.isNoAi()) {
            fail("hold did not start");
        }
        // 押す・回す (次の tick で戻るはず)
        mob.setDeltaMovement(0.8, 0.4, 0.8);
        mob.setYRot(170f);
        mob.setYBodyRot(170f);
    }

    private static void pinned(MinecraftServer server) {
        if (mob == null) {
            return;
        }
        boolean ok = true;
        if (mob.position().distanceTo(at) > 0.01) {
            fail("moved while held (" + mob.position() + " from " + at + ")");
            ok = false;
        }
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(mob.getYRot() - 30f)) > 0.5f || Math.abs(net.minecraft.util.Mth.wrapDegrees(mob.yBodyRot - 30f)) > 0.5f) {
            fail("turned while held (yaw " + mob.getYRot() + ", body " + mob.yBodyRot + ", ticks " + (mob.tickCount - born) + ")");
            ok = false;
        }
        if (!mob.isNoAi()) {
            fail("AI ran while held");
            ok = false;
        }
        if (ok) {
            // 登場の演出が先に AI を戻しても、止め直す
            mob.setNoAi(false);
        }
    }

    private static void released(MinecraftServer server) {
        if (mob == null) {
            return;
        }
        try {
            if (BossIntro.held(mob)) {
                fail("still held after the close-up");
            } else if (mob.isNoAi()) {
                fail("AI was not given back");
            } else {
                SkyAbyss.LOGGER.info("SELFTEST boss hold PASSED (pinned position and facing, AI off while held, given back after)");
            }
        } finally {
            mob.discard();
            mob = null;
        }
    }

    private static void fail(String why) {
        SkyAbyss.LOGGER.error("SELFTEST boss hold FAILED: {}", why);
    }
}

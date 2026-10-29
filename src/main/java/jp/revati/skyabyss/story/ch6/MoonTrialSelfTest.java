package jp.revati.skyabyss.story.ch6;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch6.core.CoreArena;
import jp.revati.skyabyss.story.ch6.gear.MoonGearSetup;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.ShadowEmbracer;
import jp.revati.skyabyss.story.ch6.rift.RiftBlocks;
import jp.revati.skyabyss.story.ch6.valley.ValleyArena;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

// 位相の挑戦の自己テスト (docs/design/46 の 4.8)。段: "moon trial" (先に "moon (load)" で塊を読み込む。月の核の自己テストのあと)
//   月の暦が着く所の南にある / 核を止める前は閉じている / 段の強さ (体力 ×(1 + 0.15 × 段)、1024 で止める) / 段の印 /
//   挑戦の主を倒すと段が記録され、月の欠片 (3〜5 + 段) が手に入る / ふだんの主がいる間は挑めない
public final class MoonTrialSelfTest {
    private MoonTrialSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        try {
            check(f, moon != null, "the moon dimension is missing");
            if (moon == null) {
                report(f);
                return;
            }
            check(f, moon.getBlockState(MoonTrial.calendar()).is(RiftBlocks.MOON_CALENDAR.get()), "the moon calendar is missing at " + MoonTrial.calendar());
            check(f, moon.getBlockState(MoonTrial.calendar().below()).isSolid(), "the moon calendar has no stand");
            FakePlayer p = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_trial"));
            p.setPos(MoonTrial.calendar().getX() + 0.5, MoonTrial.calendar().getY(), MoonTrial.calendar().getZ() + 2.5);
            check(f, !MoonTrial.open(p), "the trials are open before the core");
            check(f, MoonTrial.next(p, "valley") == 1, "the first tier should be 1");
            check(f, Math.abs(MoonTrial.hpScale(3) - 1.45) < 1e-6 && Math.abs(MoonTrial.dmgScale(5) - 1.40) < 1e-6, "tier scales are off");
            // ふだんの主がいるあいだは挑めない
            ValleyArena.lords(moon).forEach(Entity::discard);
            MoonMob plain = ValleyArena.summon(moon);
            if (plain != null) {
                spawned.add(plain);
                check(f, MoonTrial.start(p, "valley", 1) == null, "a trial started while the normal lord was up");
                plain.discard();
            }
            // 段 2 の影抱き: 強さ・印・倒すと記録と報酬
            MoonMob boss = MoonTrial.start(p, "valley", 2);
            check(f, boss instanceof ShadowEmbracer, "the valley trial did not summon the shadow embracer");
            if (boss != null) {
                spawned.add(boss);
                check(f, boss.getTags().contains(MoonTrial.TAG) && boss.getPersistentData().getInt(MoonTrial.TIER) == 2, "the trial lord is not tagged with its tier");
                check(f, boss.getMaxHealth() >= ShadowEmbracer.HP * MoonTrial.hpScale(2) - 1 || boss.getMaxHealth() >= 1023, "tier 2 health "
                        + boss.getMaxHealth() + " is below " + ShadowEmbracer.HP * MoonTrial.hpScale(2));
                check(f, boss.getHealth() == boss.getMaxHealth(), "the trial lord did not start at full health");
                check(f, MoonTrial.lordOf(boss).equals("valley"), "lordOf did not name the valley");
                p.setPos(boss.getX() + 3, boss.getY(), boss.getZ());
                int before = p.getInventory().countItem(MoonGearSetup.SHARD.get());
                boss.kill();
                check(f, !boss.isAlive(), "the trial lord did not die");
                // 偽の人は世界の人の一覧に入らないので、倒れた時の配りを直に呼ぶ
                MoonTrial.finish(moon, boss, "valley", List.of(p));
                check(f, MoonTrial.best(p, "valley") == 2 && MoonTrial.next(p, "valley") == 3, "clearing tier 2 was not recorded: " + MoonTrial.best(p, "valley"));
                int got = p.getInventory().countItem(MoonGearSetup.SHARD.get()) - before;
                check(f, got >= 5, "the tier 2 lord gave " + got + " moon fragments (want 3-5 + 2)");
                check(f, p.getInventory().countItem(MoonGearSetup.HELMET.get()) == 1, "the first valley clear did not give the hood");
            }
            // 段 5 の核: 体力は生き物の上限で止まる
            CoreArena.clear(moon);
            MoonMob core = MoonTrial.start(p, "core", 5);
            check(f, core != null, "the core trial did not summon the core");
            if (core != null) {
                spawned.add(core);
                check(f, core.getMaxHealth() <= 1024 && core.getMaxHealth() >= 1000, "tier 5 core health " + core.getMaxHealth() + " should cap at 1024");
            }
            p.removeTag(CoreArena.TAG);
            p.removeTag(jp.revati.skyabyss.story.ch6.field.FieldArena.TAG);
            p.removeTag(jp.revati.skyabyss.story.ch6.rift.RiftArena.TAG);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("moon trial selftest crashed", e);
            f.add("crashed: " + e);
        } finally {
            spawned.forEach(Entity::discard);
            if (moon != null) {
                CoreArena.clear(moon);
                ValleyArena.lords(moon).forEach(Entity::discard);
            }
        }
        report(f);
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon trial PASSED (calendar, sealed before the core, tier scales, busy hall, tier record, rewards, health cap)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST moon trial FAILED: {}", x));
        }
    }
}

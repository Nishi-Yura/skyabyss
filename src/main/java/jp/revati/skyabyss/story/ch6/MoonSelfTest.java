package jp.revati.skyabyss.story.ch6;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

// 六章の月の入口の自己テスト (docs/design/46 の 5 章の 1)。段: "moon world"
//   ディメンションがある / 着く所と帰りの台が建つ / 位相の計算と進め / 軽い重さが付いて外れる / 門の決まり (鍵と「近づく月」) / 頼み main_moon
public final class MoonSelfTest {
    private MoonSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
            check(f, moon != null, "the moon dimension is missing");
            check(f, QuestManager.quest("main_moon") != null, "main_moon is not loaded");
            // 位相
            check(f, MoonClock.phase(0) == 0 && MoonClock.phase(6000) == 1 && MoonClock.phase(12000) == 2 && MoonClock.phase(18000) == 3 && MoonClock.phase(24000) == 0,
                    "phase math is wrong");
            if (moon == null) {
                report(f);
                return;
            }
            for (int n = 0; n < 4; n++) {
                MoonClock.skipTo(moon, n);
                check(f, MoonClock.phase(moon) == n, "skipping to phase " + n + " gave " + MoonClock.phase(moon));
            }
            FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "moon_world"));
            // 門: 鍵がなければ断る
            check(f, "message.skyabyss.moon.gate.no_key".equals(MoonGate.refusal(p)), "a player without the sky key was let through: " + MoonGate.refusal(p));
            // 入る: 着く所と帰りの台
            // 着く所を建てる (偽の人はディメンションを渡れないので、渡るのは撮影で確かめる: tools/shots/moon_gate.txt)
            moon.getChunk(MoonWorld.ARRIVAL);
            MoonWorld.build(moon);
            check(f, moon.getBlockState(MoonWorld.returnStone()).is(Blocks.LODESTONE), "the return stone is missing");
            check(f, !moon.getBlockState(MoonWorld.ARRIVAL.below()).isAir() && moon.getBlockState(MoonWorld.ARRIVAL).isAir(), "the landing is not standable");
            int craters = 0;
            for (int x = -70; x <= 70; x += 2) {
                for (int z = -70; z <= 70; z += 2) {
                    if (Math.hypot(x, z) > 12 && moon.getBlockState(new BlockPos(x, MoonWorld.SURFACE, z)).isAir()) {
                        craters++;
                    }
                }
            }
            check(f, craters > 40, "no craters were dug around the landing (" + craters + ")");
            // 重さ
            MoonGravity.apply(p, true);
            double g = p.getAttributeValue(Attributes.GRAVITY);
            check(f, g < 0.05 && g > 0.01, "moon gravity is not light: " + g);
            check(f, p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) >= 10, "safe fall is not raised");
            MoonGravity.apply(p, false);
            check(f, Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1e-6, "moon gravity did not come off: " + p.getAttributeValue(Attributes.GRAVITY));
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST moon world crashed", ex);
            f.add("crashed: " + ex);
        }
        report(f);
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon world PASSED (dimension, landing and return stone, craters, phases, light gravity on and off, gate refuses without the key, main_moon)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST moon world FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}

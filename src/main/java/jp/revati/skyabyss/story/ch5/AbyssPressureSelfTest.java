package jp.revati.skyabyss.story.ch5;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss pressure: 深淵の潜るときの圧 (docs/design/36 の A4)。
//   重さ: 底から 6 までは 0、上るほど増え、足の速さが下がる。軽さ (重さの術の口) で減る。休めば底が追いつく
//   淵の声: 暗い所でたまり、明るさ (光の術の口) で引く。深さと一番深い記録
public final class AbyssPressureSelfTest {
    private AbyssPressureSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        var reliefWas = AbyssPressure.weightRelief;
        var lightWas = AbyssPressure.lightBonus;
        try {
            check(f, level != null, "the abyss dimension is missing");
            if (level == null) {
                report(f);
                return;
            }
            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_pressure"));
            AbyssPressure.State s = AbyssPressure.state(p);
            // 暗い所 (何もない空間の y 0。明るさ 0) に立って、底を決める
            p.moveTo(500.5, 0, 500.5);
            AbyssPressure.second(p, s);
            check(f, s.weight == 0 && Math.abs(s.lowY) < 1e-6, "standing at the bottom should weigh nothing");
            double base = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            // 30 上る → (30 - 6) / 40 = 0.6
            p.moveTo(500.5, 30, 500.5);
            AbyssPressure.second(p, s);
            check(f, Math.abs(s.weight - (30 - 0.4 - 6) / 40.0) < 0.02, "30 up should weigh about 0.59: " + s.weight);
            check(f, p.getAttributeValue(Attributes.MOVEMENT_SPEED) < base * 0.8, "the weight did not slow the feet");
            // 重さの術で半分軽く
            AbyssPressure.weightRelief = q -> 0.5;
            AbyssPressure.second(p, s);
            check(f, s.weight < 0.32 && s.weight > 0.25, "relief 0.5 should halve the weight: " + s.weight);
            AbyssPressure.weightRelief = reliefWas;
            // 休めば底が追いつく (1 秒に 0.4)
            double before = s.lowY;
            AbyssPressure.second(p, s);
            check(f, s.lowY > before, "the low point did not catch up while resting");
            // 淵の声: 暗いと +1.5/秒
            s.voice = 0;
            for (int i = 0; i < 10; i++) {
                AbyssPressure.second(p, s);
            }
            check(f, Math.abs(s.voice - 15f) < 0.01, "ten dark seconds should give 15 voice: " + s.voice);
            // 光の術の明るさ 12 で -4/秒
            AbyssPressure.lightBonus = q -> 12;
            AbyssPressure.second(p, s);
            check(f, Math.abs(s.voice - 11f) < 0.01, "light should pull the voice down by 4: " + s.voice);
            AbyssPressure.lightBonus = lightWas;
            // 深さ: 縁の上面 - 足
            int top = AbyssWorld.arrival().getY();
            p.moveTo(500.5, top - 123, 500.5);
            AbyssPressure.second(p, s);
            check(f, AbyssPressure.depth(p) == 123 && p.getPersistentData().getInt(AbyssPressure.MAX_DEPTH) >= 123, "depth and the deepest record are wrong: " + AbyssPressure.depth(p));
            AbyssPressure.clear(p);
            check(f, p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream().noneMatch(m -> m.id().getPath().startsWith("abyss_")), "clearing kept the weight");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss pressure crashed", e);
            f.add("crashed: " + e);
        } finally {
            AbyssPressure.weightRelief = reliefWas;
            AbyssPressure.lightBonus = lightWas;
        }
        report(f);
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss pressure PASSED (weight from the low point, slow feet, relief hook, catch-up, voice in the dark, light hook, depth record, clear)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss pressure FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}

package jp.revati.skyabyss.story.ch5;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss finds: 深淵の寄り道 (docs/design/36 の 8 章)。
//   刻み 16・墓 12・近道の格子の箱 12・見る者の立つ所 4 の印と石、刻み・墓を見つけると層のぶんで伝承が開く、足音の深さの割合
public final class AbyssFindsSelfTest {
    private AbyssFindsSelfTest() {
    }

    // 縦穴のまわりの塊を読み込み、層を建てておく
    public static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        hold(level, true);
        if (!AbyssBuild.built(server)) {
            AbyssBuild.finishNow(server);
        }
    }

    private static void hold(ServerLevel level, boolean on) {
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -3; cz <= 2; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        try {
            check(f, level != null, "the abyss dimension is missing");
            if (level == null) {
                report(f);
                return;
            }
            for (int li = 0; li < AbyssFinds.LAYERS.length; li++) {
                String layer = AbyssFinds.LAYERS[li];
                for (int i = 1; i <= AbyssFinds.CARVINGS; i++) {
                    BlockPos m = AbyssBuild.mark("carve_" + layer + "_" + i);
                    check(f, m != null && level.getBlockState(m).is(Blocks.CHISELED_DEEPSLATE), "carve_" + layer + "_" + i + " is not a carved stone");
                }
                for (int i = 1; i <= AbyssFinds.GRAVES; i++) {
                    BlockPos m = AbyssBuild.mark("grave_" + layer + "_" + i);
                    check(f, m != null && level.getBlockState(m.above()).is(Blocks.SKELETON_SKULL), "grave_" + layer + "_" + i + " has no skull");
                }
                // 近道の格子 (三本の道の終わりから野営へ。story/ch5/layers/AbyssMarks)
                for (int i = 1; i <= 3; i++) {
                    check(f, AbyssBuild.mark("scut_" + layer + "_" + i + "_a") != null && AbyssBuild.mark("scut_" + layer + "_" + i + "_b") != null,
                            "scut_" + layer + "_" + i + " box is missing");
                }
                BlockPos w = AbyssBuild.mark("watch_" + layer);
                check(f, w != null && level.getBlockState(w).isAir() && level.getBlockState(w.above()).isAir(), "watch_" + layer + " is not an open spot");
            }
            // 見つける: 層の四つ目の刻みで伝承
            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_finds"));
            for (int i = 1; i <= 3; i++) {
                AbyssFinds.find(p, "carve", "bone", i);
            }
            // lock は「記されていたか」を返して外す (前は記されていないはず)
            check(f, !Lore.lock(p, "ch5/carvings_bone"), "the carvings lore opened before all four");
            check(f, !AbyssFinds.find(p, "carve", "bone", 2), "reading a carving twice counted twice");
            AbyssFinds.find(p, "carve", "bone", 4);
            check(f, Lore.lock(p, "ch5/carvings_bone"), "four carvings did not open the lore");
            for (int i = 1; i <= 3; i++) {
                AbyssFinds.find(p, "grave", "ash", i);
            }
            check(f, Lore.lock(p, "ch5/graves_ash"), "three graves did not open the lore");
            // 足音: 深いほど大きい
            BlockPos arrival = AbyssWorld.arrival();
            check(f, AbyssHorror.depthShare(arrival.getY()) == 0f && AbyssHorror.depthShare(arrival.getY() - 540) == 1f
                    && AbyssHorror.depthShare(arrival.getY() - 200) > AbyssHorror.depthShare(arrival.getY() - 100), "the depth share is wrong");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss finds crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (level != null) {
                hold(level, false);
            }
        }
        report(f);
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss finds PASSED (16 carvings, 12 graves, 12 shortcut boxes, 4 watcher spots, lore after a full layer, depth share)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss finds FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}

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
//   刻み 16・墓 12・近道の両端 8・見る者の立つ所 4 の印と石、刻み・墓を見つけると層のぶんで伝承が開く、近道の開け閉めと立つ所、足音の深さの割合
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
        boolean[] wasOpen = new boolean[AbyssFinds.LAYERS.length];
        try {
            check(f, level != null, "the abyss dimension is missing");
            if (level == null) {
                report(f);
                return;
            }
            for (int li = 0; li < AbyssFinds.LAYERS.length; li++) {
                String layer = AbyssFinds.LAYERS[li];
                wasOpen[li] = AbyssShortcuts.isOpen(server, layer);
                for (int i = 1; i <= AbyssFinds.CARVINGS; i++) {
                    BlockPos m = AbyssBuild.mark("carve_" + layer + "_" + i);
                    check(f, m != null && level.getBlockState(m).is(Blocks.CHISELED_DEEPSLATE), "carve_" + layer + "_" + i + " is not a carved stone");
                }
                for (int i = 1; i <= AbyssFinds.GRAVES; i++) {
                    BlockPos m = AbyssBuild.mark("grave_" + layer + "_" + i);
                    check(f, m != null && level.getBlockState(m.above()).is(Blocks.SKELETON_SKULL), "grave_" + layer + "_" + i + " has no skull");
                }
                BlockPos bottom = AbyssBuild.mark("shortcut_" + layer + "_bottom");
                BlockPos top = AbyssBuild.mark("shortcut_" + layer + "_top");
                check(f, bottom != null && level.getBlockState(bottom).is(Blocks.LODESTONE), layer + " winch is missing");
                check(f, top != null && level.getBlockState(top).is(Blocks.IRON_BARS), layer + " anchor is missing");
                for (String end : new String[] {"bottom", "top"}) {
                    BlockPos s = AbyssShortcuts.stand("shortcut_" + layer + "_" + end);
                    check(f, s != null && !level.getBlockState(s.below()).isAir() && level.getBlockState(s).isAir() && level.getBlockState(s.above()).isAir(),
                            layer + " " + end + " stand spot is not standable: " + s);
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
            // 近道: 開くと記録され、鎖が下がる
            AbyssShortcuts.open(server, "salt");
            check(f, AbyssShortcuts.isOpen(server, "salt"), "opening the salt shortcut was not recorded");
            // 足音: 深いほど大きい
            BlockPos arrival = AbyssWorld.arrival();
            check(f, AbyssHorror.depthShare(arrival.getY()) == 0f && AbyssHorror.depthShare(arrival.getY() - 540) == 1f
                    && AbyssHorror.depthShare(arrival.getY() - 200) > AbyssHorror.depthShare(arrival.getY() - 100), "the depth share is wrong");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss finds crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (level != null) {
                AbyssShortcuts.Data d = AbyssShortcuts.Data.get(server);
                for (int li = 0; li < AbyssFinds.LAYERS.length; li++) {
                    if (!wasOpen[li]) {
                        d.open.remove(AbyssFinds.LAYERS[li]);
                    }
                }
                d.setDirty();
                hold(level, false);
            }
        }
        report(f);
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss finds PASSED (16 carvings, 12 graves, 4 shortcuts and stand spots, 4 watcher spots, lore after a full layer, shortcut record, depth share)");
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

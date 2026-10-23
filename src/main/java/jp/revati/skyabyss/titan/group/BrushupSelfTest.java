package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// brushup_review の直しの自己テスト:
//  - P1-4 ReentryRules の守りの記録: 退出・サーバー停止・別の次元へ移ったとき捨てる
public final class BrushupSelfTest {
    private BrushupSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            guardCleanup(server, f);
            titanTargets(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST brushup crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST brushup PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST brushup FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.setPos(0, 100, 0);
        return p;
    }

    // ---------------------------------------------------------------- P1-4
    private static void guardCleanup(MinecraftServer server, List<String> f) {
        ServerLevel level = server.getLevel(TitanTravel.TITAN_BACK);
        if (level == null) {
            level = server.overworld();
        }
        FakePlayer a = player(level, "brushup_guard_a");
        FakePlayer b = player(level, "brushup_guard_b");
        try {
            ReentryRules.clearAll();
            ReentryRules.guard(a, false);
            ReentryRules.guard(b, false);
            check(f, ReentryRules.guarded(a) && ReentryRules.guarded(b) && ReentryRules.guardCount() == 2, "the guard was not recorded");
            // 退出: その人の記録だけが消える
            ReentryRules.onLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(a));
            check(f, !ReentryRules.guarded(a) && ReentryRules.guarded(b) && ReentryRules.guardCount() == 1,
                    "logging out did not drop the player's guard (or dropped another's)");
            // 次元の移動: 守りを受けた次元と違う次元へ移ったら消える。同じ次元への到着の通知 (復活のテレポートの直後) では消えない
            ReentryRules.onDimensionChange(new PlayerEvent.PlayerChangedDimensionEvent(b, Level.OVERWORLD, level.dimension()));
            check(f, ReentryRules.guarded(b), "arriving in the guarded dimension dropped the guard");
            var elsewhere = level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
            ReentryRules.onDimensionChange(new PlayerEvent.PlayerChangedDimensionEvent(b, level.dimension(), elsewhere));
            check(f, !ReentryRules.guarded(b) && ReentryRules.guardCount() == 0, "changing dimension did not drop the guard");
            // サーバー停止: すべて消える
            ReentryRules.guard(a, false);
            ReentryRules.guard(b, false);
            ReentryRules.clearAll();
            check(f, ReentryRules.guardCount() == 0 && !ReentryRules.guarded(a) && !ReentryRules.guarded(b),
                    "a server stop did not clear the guard records");
        } finally {
            ReentryRules.clearAll();
            a.removeAllEffects();
            b.removeAllEffects();
        }
    }

    // ---------------------------------------------------------------- P1-8 / T-8: 章のクエストの帯に出す巨神の位置 (SiteNetwork が送る)
    private static void titanTargets(MinecraftServer server, List<String> f) {
        var data = jp.revati.skyabyss.titan.TitanWorldData.get(server.overworld());
        var slot = data.slot(jp.revati.skyabyss.progress.Titans.THUNDER_WING);
        boolean had = slot.hasLast;
        double x = slot.lastX;
        double y = slot.lastY;
        double z = slot.lastZ;
        try {
            slot.hasLast = true;
            slot.lastX = 1234.5;
            slot.lastY = 190;
            slot.lastZ = -4321.5;
            var sites = jp.revati.skyabyss.opening.site.SiteNetwork.payload(server).sites();
            var key = "titan." + jp.revati.skyabyss.progress.Titans.THUNDER_WING;
            check(f, sites.containsKey(key) && sites.get(key).getX() == 1234 && sites.get(key).getZ() == -4322, "the thunder wing's position was not sent to the clients: " + sites.get(key));
            slot.hasLast = false;
            check(f, !jp.revati.skyabyss.opening.site.SiteNetwork.payload(server).sites().containsKey(key), "a titan with no known position was sent");
            // 8 ブロック以上動いたときだけ送り直す
            var a = new java.util.HashMap<String, net.minecraft.core.BlockPos>();
            a.put(key, new net.minecraft.core.BlockPos(0, 100, 0));
            var b = new java.util.HashMap<>(a);
            b.put(key, new net.minecraft.core.BlockPos(5, 100, 0));
            check(f, !jp.revati.skyabyss.opening.site.SiteNetwork.moved(a, b), "a 5-block move was resent");
            b.put(key, new net.minecraft.core.BlockPos(9, 100, 0));
            check(f, jp.revati.skyabyss.opening.site.SiteNetwork.moved(a, b), "a 9-block move was not resent");
            b.put("titan.ember", new net.minecraft.core.BlockPos(0, 0, 0));
            check(f, jp.revati.skyabyss.opening.site.SiteNetwork.moved(a, b), "a new titan position was not sent");
        } finally {
            slot.hasLast = had;
            slot.lastX = x;
            slot.lastY = y;
            slot.lastZ = z;
        }
    }
}

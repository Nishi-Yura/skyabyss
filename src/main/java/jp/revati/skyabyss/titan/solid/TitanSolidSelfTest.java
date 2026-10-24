package jp.revati.skyabyss.titan.solid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import jp.revati.skyabyss.titan.ember.EmberTitanSetup;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import jp.revati.skyabyss.titan.tide.TideSetup;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 巨神の体に入れない仕組みの自己テスト (SKYABYSS_SELFTEST。結果は SELFTEST titan solid PASSED / FAILED)。docs/design/02 の「体に入れない」
//  - 体積の表: 四体とも読め、体の中心は体の中、遠くは外。体の中の点は、いちばん近い外へ押し出される (押し出した先は立入禁止の外)
//  - 通り道: 鎖の下・潮門・昇り風・熱の口は体積の中でも入れる。その隣の体の点は入れない
//  - 向き: 巨神がどの向きでも、局所と世界の変換が往復する
//  - 近づく: 16 方向 × 3 つの高さから、歩き・走り・創造の飛行の速さで体の真ん中へ向かっても、体そのもの (core) へは入れない
//  - 巨神が動く・回る: 毎 tick 1 ブロック進みながら回る巨神の前に立つ人は、体に入らない
//  - 本物の人 (FakePlayer) と矢: 体の中に置いた人は tick で外へ向かう速さをもらう (深ければ外へ出される)。通り道の人と観戦者は動かさない。体の中を飛ぶ矢は消える
public final class TitanSolidSelfTest {
    private static final int PAD_X = -6000;
    private static final int PAD_Z = 6000;
    private static final int PAD = 48;

    private TitanSolidSelfTest() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = (PAD_X - PAD) >> 4; cx <= (PAD_X + PAD) >> 4; cx++) {
            for (int cz = (PAD_Z - PAD) >> 4; cz <= (PAD_Z + PAD) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    private static void unload(ServerLevel level) {
        for (int cx = (PAD_X - PAD) >> 4; cx <= (PAD_X + PAD) >> 4; cx++) {
            for (int cz = (PAD_Z - PAD) >> 4; cz <= (PAD_Z + PAD) >> 4; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }
    }

    private record Case(String name, String volume, Entity titan, Vec3 center, Vec3 corridorPoint, Vec3 neighbour) {
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> f = new ArrayList<>();
        List<Case> cases = new ArrayList<>();
        try {
            StoneWhaleEntity whale = ModEntities.STONE_WHALE.get().create(level);
            TideShellEntity shell = TideSetup.TIDE_SHELL.get().create(level);
            StormWingEntity wing = StormSetup.THUNDER_WING.get().create(level);
            EmberTitanEntity ember = EmberTitanSetup.EMBER_TITAN.get().create(level);
            // (局所 = 横, 上, 前): 体の中心・通り道の中の体の点・その隣の通り道でない体の点
            cases.add(new Case("whale", "stone_whale", whale, new Vec3(0, 20, 0), new Vec3(10.5, 14, -30.5), new Vec3(-10.5, 14, -30.5)));
            cases.add(new Case("tide", "tide_shell", shell, new Vec3(0, 30, 0), new Vec3(33, 8, -32), new Vec3(-33, 8, -32)));
            cases.add(new Case("storm", "thunder_wing", wing, new Vec3(0, 20, 0), new Vec3(14, 27, -4), new Vec3(-14, 27, -4)));
            cases.add(new Case("ember", "ember_titan", ember, new Vec3(0, 15, 0), new Vec3(0, 5, 83), new Vec3(14, 5, 83)));
            for (Case c : cases) {
                volumes(f, c);
                corridors(f, c);
                transforms(f, c);
                approach(f, c);
                moving(f, c);
            }
            live(f, level, cases);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan solid crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (Case c : cases) {
                TitanSolids.untrack(c.titan());
            }
            unload(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST titan solid PASSED (volumes load, push-out leaves the keep-out, boarding corridors open, 16 approach angles x 3 heights never reach the body, moving/turning titan, real player push and arrow removal)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST titan solid FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static List<SolidTitan.Corridor> corridorsOf(Entity titan) {
        return ((SolidTitan) titan).corridors();
    }

    private static void volumes(List<String> f, Case c) {
        TitanVolume v = TitanVolume.get(c.volume());
        check(f, v != null, c.name() + ": the volume table did not load");
        if (v == null) {
            return;
        }
        check(f, v.inCore(c.center().x, c.center().y, c.center().z), c.name() + ": the body centre is not inside the body");
        check(f, !v.inKeepOut(0, 20, 400) && !v.inKeepOut(400, 20, 0) && !v.inCore(0, -50, 0), c.name() + ": a far point is inside the keep-out");
        // 体の中の点 (中心・中心から少しずれた点) は、押し出されると立入禁止の外へ出る
        List<SolidTitan.Corridor> corridors = corridorsOf(c.titan());
        Vec3[] inside = {c.center(), c.center().add(3, 2, 5), c.center().add(-4, -3, -6)};
        for (Vec3 p : inside) {
            if (!TitanSolids.blocked(v, corridors, p, false)) {
                continue;
            }
            Vec3 out = TitanSolids.pushOutLocal(v, corridors, p, x -> true);
            check(f, out != null, c.name() + ": no way out from " + p);
            if (out != null) {
                check(f, !TitanSolids.blocked(v, corridors, p.add(out), false), c.name() + ": the push from " + p + " ended inside the keep-out (" + p.add(out) + ")");
            }
        }
        check(f, TitanSolids.pushOutLocal(v, corridors, new Vec3(0, 20, 400), x -> true) == null, c.name() + ": a point outside was pushed");
    }

    private static void corridors(List<String> f, Case c) {
        TitanVolume v = TitanVolume.get(c.volume());
        if (v == null) {
            return;
        }
        List<SolidTitan.Corridor> corridors = corridorsOf(c.titan());
        Vec3 p = c.corridorPoint();
        check(f, v.inKeepOut(p.x, p.y, p.z), c.name() + ": the corridor test point is not in the volume at all (" + p + ")");
        check(f, !TitanSolids.blocked(v, corridors, p, false), c.name() + ": the boarding corridor is closed at " + p);
        check(f, !TitanSolids.blocked(v, corridors, p, true), c.name() + ": the boarding corridor is closed for projectiles/hard push at " + p);
        Vec3 n = c.neighbour();
        check(f, TitanSolids.blocked(v, corridors, n, false), c.name() + ": the point next to the corridor is open (" + n + ")");
    }

    private static void transforms(List<String> f, Case c) {
        for (int yaw = -180; yaw < 360; yaw += 37) {
            Vec3 local = new Vec3(7.5, 11, -23);
            Vec3 d = TitanSolids.toWorldDelta(yaw, local);
            Vec3 back = TitanSolids.toLocal(100, 50, -30, yaw, new Vec3(100, 50, -30).add(d));
            check(f, back.distanceTo(local) < 0.02, c.name() + ": local/world round trip failed at yaw " + yaw + " (" + back + ")");
        }
        // 巨神の局所の流儀 (TideShellEntity.local・offset と同じ): 向き 0 は +z (南) が前、左は +x (東)
        Vec3 fwd = TitanSolids.toLocal(0, 0, 0, 0f, new Vec3(0, 0, 10));
        check(f, Math.abs(fwd.z - 10) < 1e-9 && Math.abs(fwd.x) < 1e-9, c.name() + ": yaw 0 forward is not +z");
        Vec3 left = TitanSolids.toLocal(0, 0, 0, 0f, new Vec3(10, 0, 0));
        check(f, Math.abs(left.x - 10) < 1e-9, c.name() + ": yaw 0 left is not +x (" + left + ")");
    }

    // 歩き・走り・創造の飛行の速さで、あらゆる向きと高さから体の真ん中へ向かい続ける人。押し出しは本物と同じ式 (pushSpeed)
    private static void approach(List<String> f, Case c) {
        TitanVolume v = TitanVolume.get(c.volume());
        if (v == null) {
            return;
        }
        List<SolidTitan.Corridor> corridors = corridorsOf(c.titan());
        double[] speeds = {0.22, 0.28, 0.9};
        double[] heights = {4, 20, 45};
        for (double speed : speeds) {
            for (double h : heights) {
                for (int a = 0; a < 16; a++) {
                    double ang = a * Math.PI / 8;
                    Vec3 p = new Vec3(Math.cos(ang) * 170, h, Math.sin(ang) * 170);
                    Vec3 target = new Vec3(0, c.center().y, 0);
                    boolean hit = false;
                    boolean boarding = false;
                    for (int t = 0; t < 1200 && !hit && !boarding; t++) {
                        Vec3 dir = target.subtract(p);
                        if (dir.length() < 0.5) {
                            break;
                        }
                        p = p.add(dir.normalize().scale(speed));
                        if (TitanSolids.blocked(v, corridors, p, false)) {
                            Vec3 out = TitanSolids.pushOutLocal(v, corridors, p, x -> true);
                            if (out != null) {
                                p = p.add(out.normalize().scale(Math.min(TitanSolids.pushSpeed(out.length()), out.length())));
                            }
                        }
                        // 通り道の中へ入った人は、そこで乗り込む (入ってよい。通り道の先の体の中へ歩き続ける人は、本物では一息に外へ出される)
                        if (v.inKeepOut(p.x, p.y, p.z) && inAny(corridors, p)) {
                            boarding = true;
                        } else if (TitanSolids.blocked(v, corridors, p, true)) {
                            hit = true;
                        }
                    }
                    check(f, !hit, c.name() + ": a player at speed " + speed + " from angle " + a * 22.5 + " height " + h + " reached the body at " + p);
                }
            }
        }
    }

    private static boolean inAny(List<SolidTitan.Corridor> corridors, Vec3 p) {
        for (SolidTitan.Corridor c : corridors) {
            if (c.contains(p.x, p.y, p.z)) {
                return true;
            }
        }
        return false;
    }

    // 巨神が毎 tick 0.3 ブロック進みながら 0.15 度ずつ回る (実際の速さは岩鯨 0.2〜0.45・0.5 度、潮殻 0.2・0.4 度、余燼 0.3・0.3 度。余燼は長さ 230 ブロックで先の速さが大きいので回りは遅め。雷翼の 3 度は含めない)。その進路に立ちつづける人 (動かない・逃げない) は、毎 tick 押し出されて体に入らない
    private static void moving(List<String> f, Case c) {
        TitanVolume v = TitanVolume.get(c.volume());
        if (v == null) {
            return;
        }
        List<SolidTitan.Corridor> corridors = corridorsOf(c.titan());
        for (double h : new double[] {5, 25}) {
            Vec3 player = new Vec3(0, h, 90);
            double tx = 0;
            double tz = 0;
            float yaw = 0;
            int hard = 0;
            for (int t = 0; t < 700; t++) {
                tz += 0.3;
                yaw += 0.15f;
                Vec3 local = TitanSolids.toLocal(tx, 0, tz, yaw, player);
                if (TitanSolids.blocked(v, corridors, local, false)) {
                    Vec3 out = TitanSolids.pushOutLocal(v, corridors, local, x -> true);
                    if (out != null) {
                        Vec3 world = TitanSolids.toWorldDelta(yaw, out);
                        double len = world.length();
                        if (TitanSolids.blocked(v, corridors, local, true)) {
                            // 体そのものに入られたら、本物は一息に外へ出す (TitanSolids の hard)。数だけ数える
                            hard++;
                            player = player.add(world);
                        } else {
                            player = player.add(world.normalize().scale(Math.min(len, TitanSolids.pushSpeed(len))));
                        }
                    }
                }
            }
            boolean hit = hard > 70;  // 幅の広い潮殻・余燼が低い所の人を横から掃くときは、ときどき体の中へ入られて一息に出される (その数が 1 割を超えないこと)
            check(f, !hit, c.name() + ": a moving, turning titan ran over a standing player at height " + h + " (hard pushes " + hard + ", tz " + tz + ", yaw " + yaw + ")");
        }
    }

    // 本物の人と矢
    private static void live(List<String> f, ServerLevel level, List<Case> cases) {
        Case c = cases.get(0);
        StoneWhaleEntity whale = (StoneWhaleEntity) c.titan();
        double cx = PAD_X;
        double cz = PAD_Z;
        whale.moveTo(cx, 120, cz, 90f, 0f);
        TitanSolids.track(whale);
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7e5a0e0d-0000-4000-8000-0000000f0001"), "[titan_solid]"));
        // 前の回の残り (この人が岩鯨を止めた記録) があると、巨神が見えない人として押されない。先に消す
        jp.revati.skyabyss.progress.ProgressData.get(level.getServer()).clearPersonal(p.getUUID(), jp.revati.skyabyss.progress.Titans.STONE_WHALE);
        jp.revati.skyabyss.loot.TitanMemoryData.Record mem = jp.revati.skyabyss.loot.TitanMemoryData.get(level.getServer()).peek(p.getUUID(), jp.revati.skyabyss.progress.Titans.STONE_WHALE);
        if (mem != null && mem.clears > 0) {
            SkyAbyss.LOGGER.info("SELFTEST titan solid: cleared a stale stone whale record of the test player ({} clears)", mem.clears);
            mem.clears = 0;
        }
        jp.revati.skyabyss.titan.TitanVisibility.clearCache();
        float yaw = whale.getYRot();
        // 体の中 (体の外寄り 横 12、上 22): いちばん近い外へ向かう速さをもらう
        Vec3 inside = new Vec3(cx, 120, cz).add(TitanSolids.toWorldDelta(yaw, new Vec3(17.2, 20, 0)));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setPos(inside.x, inside.y, inside.z);
        p.setDeltaMovement(Vec3.ZERO);
        TitanVolume v = TitanVolume.get("stone_whale");
        Vec3 localNow = TitanSolids.toLocal(whale, p.position().add(0, 0.9, 0));
        check(f, v != null && TitanSolids.blocked(v, corridorsOf(whale), localNow, false), "live: the test player is not inside the keep-out at " + localNow);
        Vec3 dbgOut = TitanSolids.pushOutLocal(v, corridorsOf(whale), localNow, x -> true);
        TitanSolids.onPlayerTick(new PlayerTickEvent.Post(p));
        Vec3 after = p.getDeltaMovement().length() > 0.01 ? p.getDeltaMovement() : p.position().subtract(inside);
        check(f, after.length() > 0.05, "live: a player inside the whale was not pushed (" + after + ", exempt " + TitanSolids.exempt(p) + ", exit " + dbgOut + ", local " + localNow + ", alive " + p.isAlive() + ", level " + (p.level() == whale.level()) + ")");
        Vec3 localAfter = TitanSolids.toLocal(whale, p.position().add(0, 0.9, 0).add(p.getDeltaMovement().length() > 0.01 ? p.getDeltaMovement() : Vec3.ZERO));
        check(f, localAfter.distanceTo(localNow) > 0.05, "live: the push did not move the player's local position");
        // 体の核の中に深く入った人は、一息に外へ出される
        Vec3 deep = new Vec3(cx, 120, cz).add(TitanSolids.toWorldDelta(yaw, new Vec3(0, 20, 0)));
        p.setPos(deep.x, deep.y - 0.9, deep.z);
        p.setDeltaMovement(Vec3.ZERO);
        TitanSolids.onPlayerTick(new PlayerTickEvent.Post(p));
        Vec3 deepLocal = TitanSolids.toLocal(whale, p.position().add(0, 0.9, 0));
        check(f, v != null && !TitanSolids.blocked(v, corridorsOf(whale), deepLocal, true), "live: a player in the middle of the body was left inside the body (" + deepLocal + ")");
        // 通り道 (背中の鎖の下) の人は動かさない
        Vec3 chain = new Vec3(cx, 120, cz).add(TitanSolids.toWorldDelta(yaw, new Vec3(10.5, 14, -30.5)));
        p.setPos(chain.x, chain.y - 0.9, chain.z);
        p.setDeltaMovement(Vec3.ZERO);
        TitanSolids.onPlayerTick(new PlayerTickEvent.Post(p));
        check(f, p.getDeltaMovement().lengthSqr() < 1e-6 && p.position().distanceTo(chain.subtract(0, 0.9, 0)) < 1e-6, "live: a player in the boarding corridor was pushed");
        // 観戦者は動かさない
        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        p.setPos(inside.x, inside.y, inside.z);
        p.setDeltaMovement(Vec3.ZERO);
        TitanSolids.onPlayerTick(new PlayerTickEvent.Post(p));
        check(f, p.getDeltaMovement().lengthSqr() < 1e-6, "live: a spectator was pushed");
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // 矢: 体の中を飛ぶ矢は消え、体の外の矢は残る
        Arrow arrow = EntityType.ARROW.create(level);
        Vec3 inBody = new Vec3(cx, 120, cz).add(TitanSolids.toWorldDelta(yaw, new Vec3(0, 20, 0)));
        arrow.setPos(inBody.x, inBody.y, inBody.z);
        arrow.xo = inBody.x;
        arrow.yo = inBody.y;
        arrow.zo = inBody.z;
        level.addFreshEntity(arrow);
        TitanSolids.onProjectileTick(new EntityTickEvent.Post(arrow));
        check(f, arrow.isRemoved(), "live: an arrow inside the whale's body was not removed");
        Arrow outside = EntityType.ARROW.create(level);
        Vec3 away = new Vec3(cx + 120, 220, cz);
        outside.setPos(away.x, away.y, away.z);
        outside.xo = away.x;
        outside.yo = away.y;
        outside.zo = away.z;
        level.addFreshEntity(outside);
        TitanSolids.onProjectileTick(new EntityTickEvent.Post(outside));
        check(f, !outside.isRemoved(), "live: an arrow far from the whale was removed");
        outside.discard();
        // 速い矢が体を横切る (前の位置と今の位置が体をはさむ): 抜けずに消える
        Arrow fast = EntityType.ARROW.create(level);
        Vec3 from = new Vec3(cx, 120, cz).add(TitanSolids.toWorldDelta(yaw, new Vec3(-40, 20, 0)));
        Vec3 to = new Vec3(cx, 120, cz).add(TitanSolids.toWorldDelta(yaw, new Vec3(40, 20, 0)));
        fast.setPos(to.x, to.y, to.z);
        fast.xo = from.x;
        fast.yo = from.y;
        fast.zo = from.z;
        level.addFreshEntity(fast);
        TitanSolids.onProjectileTick(new EntityTickEvent.Post(fast));
        check(f, fast.isRemoved(), "live: a fast arrow passed through the whale");
        arrow.discard();
        fast.discard();
        TitanSolids.untrack(whale);
    }
}

package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackSelfTest;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.raid.InstanceArenas;
import jp.revati.skyabyss.trial.raid.RaidRun;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 順路の部屋の自己テスト (docs/design/32 の 2.2・2.5。段の名前は "route rooms")。四体の背で、一人の偽の人が:
//  - 乗り込んで到着の台に着くと、歩かずに (予告のあと) 区画のレイドの部屋 1 の中へ運ばれる
//  - 部屋 1〜3 を越えるたびに次の部屋の中へ、三つ目のあとは中ボスの間の中へ、中ボスを倒すと体の外の心臓の間の中へ (前室を歩かない)
//  - 運ばれた先のどの部屋も封じてある (休み場から空気を辿って、部屋の箱の外へ抜けない)
public final class RouteRoomsSelfTest {
    private RouteRoomsSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("route rooms", 10, RouteRoomsSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        int rooms = 0;
        for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
            ServerLevel back = server.getLevel(Titans.backOf(titan));
            if (back == null) {
                f.add(titan + ": the back dimension is missing");
                continue;
            }
            UUID group = null;
            try {
                InstanceArenas.ensureHeart(back, titan);
                FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.nameUUIDFromBytes(("route rooms " + titan).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "route_" + titan.substring(0, Math.min(8, titan.length()))));
                group = Groups.keyOf(p);
                clean(back, group);
                List<ServerPlayer> players = List.of(p);
                TrialRoute.PLAYERS = l -> l == back ? players : List.of();
                // 乗り込み: 到着の台に着く
                Vec3 pad = Vec3.atBottomCenterOf(jp.revati.skyabyss.trial.RouteSeals.safeFeet(titan));
                p.setPos(pad.x, pad.y, pad.z);
                long now = back.getGameTime();
                TrialRoute.tick(back, titan, players, now);
                TrialRoute.tick(back, titan, players, now + TrialRoute.ENTRY_WAIT + 1);
                if (TrialRoute.step(back, group) != 1) {
                    f.add(titan + ": standing on the arrival pad did not start the route (step " + TrialRoute.step(back, group) + ")");
                    continue;
                }
                for (int step = 1; step <= 3; step++) {
                    RaidRun.Run run = RaidRun.of(group);
                    if (run == null || run.mid || run.step != step) {
                        f.add(titan + ": no raid room for step " + step);
                        break;
                    }
                    if (!run.ctx.cell().inner().contains(p.position())) {
                        f.add(titan + ": step " + step + " did not carry the player into room " + run.ctx.roomId() + ": " + p.position());
                    }
                    sealed(f, back, titan + " " + run.ctx.roomId(), run.ctx.cell().rest(), run.ctx.cell().bounds());
                    rooms++;
                    TrialRoute.devPass(back, group, titan, p);
                    TrialRoute.flushPending();
                    TrialRoute.tick(back, titan, players, now + 1000L * step);
                }
                RaidRun.Run mid = RaidRun.of(group);
                if (!TrialRoute.mid(back, group) || mid == null || !mid.mid) {
                    f.add(titan + ": after the third room the route is not at the mid-boss");
                } else {
                    if (!mid.ctx.cell().inner().contains(p.position())) {
                        f.add(titan + ": the player was not carried into the mid-boss arena: " + p.position());
                    }
                    sealed(f, back, titan + " " + mid.ctx.roomId(), mid.ctx.cell().rest(), mid.ctx.cell().bounds());
                    rooms++;
                }
                TrialRoute.devPass(back, group, titan, p);
                TrialRoute.flushPending();
                TrialRoute.tick(back, titan, players, now + 5000);
                AABB heart = InstanceArenas.heartArena(titan);
                if (TrialRoute.step(back, group) != TrialRoute.LAST_STEP || heart == null || !heart.contains(p.position())) {
                    f.add(titan + ": after the mid-boss the player is not inside the heart arena (step " + TrialRoute.step(back, group) + ", at " + p.position() + ")");
                } else {
                    sealed(f, back, titan + " heart", InstanceArenas.heartEntry(titan), heart);
                    rooms++;
                }
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("SELFTEST route rooms crashed ({})", titan, e);
                f.add(titan + ": crashed: " + e);
            } finally {
                if (group != null) {
                    clean(back, group);
                }
                TrialRoute.clearAll();
                TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST route rooms PASSED (four titans: the arrival pad carries one player straight into room 1, then rooms 2-3, "
                    + "the mid-boss arena and the heart arena; {} rooms, all sealed)", rooms);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST route rooms FAILED: {}", s));
        }
    }

    private static void sealed(List<String> f, ServerLevel back, String what, BlockPos from, AABB box) {
        String leak = TitanBackSelfTest.leak(back, from, box.inflate(1));
        if (leak != null) {
            f.add(what + ": the room is not sealed, air reaches " + leak);
        }
    }

    private static void clean(ServerLevel back, UUID g) {
        TrialRoute.finish(back, g);
        BackGroupData.get(back).remove(g);
    }
}

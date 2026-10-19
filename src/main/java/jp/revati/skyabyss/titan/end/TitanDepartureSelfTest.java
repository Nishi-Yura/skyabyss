package jp.revati.skyabyss.titan.end;

import java.util.List;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.TitanKeeper;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.ember.EmberKeeper;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 巨神が心臓のあとに地上から去ることの自己テスト (TitanEndSelfTest の一段。docs/design/02 の 7.2節、TitanDormancy.DEPART_TICKS)。
//  - 四体とも: 眠って 10 秒までは落ち着く姿で本体が残り、そのあと去る動き (岩鯨は昇る/沈む・潮殻は沈みきる・雷翼は飛び去る・余燼は潜る) になり、
//    40 秒で本体が地上から取り除かれる。取り除かれたあと、見守りは眠りのあいだ呼び直さない (restHolds)。目覚めたあとは控えが止まる
//  - 試遊のコマンドの道: `playtest next`・`trials` のように心臓を止めずに進みだけ記しても眠らない (巨神は残る)。
//    `titan end sleep` (TitanDormancy.begin(…, 0)) と本物の撃破 (TitanEnd.onHeartStopped) のどちらでも、同じように去る
//  - 光の柱と枠 (TitanEndNetwork.Beam) の送受信、帰り石の形と置き場所
final class TitanDepartureSelfTest {
    private TitanDepartureSelfTest() {
    }

    private static void tick(Entity e, int n) {
        for (int i = 0; i < n; i++) {
            e.tickCount++;
            e.tick();
        }
    }

    // pad = 試験場の中心 (読み込み済み)
    static void run(MinecraftServer server, ServerLevel level, List<String> f, double cx, double cz, int padY) {
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.snapshot(level.registryAccess());
        try {
            for (String titan : Titans.all()) {
                leaves(server, level, f, titan, cx, cz, padY, false);
            }
            // 本物の撃破の道 (報酬を除く眠りと運び出し) でも去る: 岩鯨で代表させる
            leaves(server, level, f, Titans.STONE_WHALE, cx, cz, padY, true);
            playtestPath(server, f);
            beam(server, f);
            returnStones(server, level, f, cx, cz, padY);
        } finally {
            data.restore(saved, level.registryAccess());
            for (String t : Titans.all()) {
                TitanDormancy.clear(server, t);
            }
            ReturnBeacons.clearCache();
        }
    }

    @SuppressWarnings("unchecked")
    private static TitanKeeper<? extends Entity> keeper(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> TideKeeper.KEEPER;
            case Titans.THUNDER_WING -> StormKeeper.KEEPER;
            case Titans.EMBER -> EmberKeeper.KEEPER;
            default -> WhaleKeeper.keeper();
        };
    }

    private static void leaves(MinecraftServer server, ServerLevel level, List<String> f, String titan, double cx, double cz, int padY, boolean realKill) {
        String tag = titan + (realKill ? " (real kill)" : " (titan end sleep)");
        TitanDormancy.clear(server, titan);
        TitanKeeper<? extends Entity> keeper = keeper(titan);
        Entity e = keeper.spawn(level, cx, cz, null);
        switch (titan) {
            case Titans.STONE_WHALE -> e.moveTo(cx, 200, cz, 0f, 0f);
            case Titans.TIDE_SHELL -> e.moveTo(cx, level.getSeaLevel() - 24, cz, 0f, 0f);
            case Titans.THUNDER_WING -> e.moveTo(cx - 40, 150, cz, 0f, 0f);
            default -> e.moveTo(cx, padY - 58, cz + 10, 0f, 0f);
        }
        try {
            if (realKill) {
                // 本物の撃破: 報酬を渡す前の部分 (眠りと運び出しの段取り) だけ。ここでは眠りだけ確かめて運び出しの段取りは流す
                ServerLevel back = server.getLevel(Titans.backOf(titan));
                TitanEnd.onHeartStoppedAgain(back, titan, List.of(UUID.fromString("7e5a0e0d-0000-4000-8000-0000000e0061")));
                Tasks.flush();
            } else {
                TitanDormancy.begin(server, titan, 0);
            }
            if (Titans.THUNDER_WING.equals(titan)) {
                TitanDormancy.setSpot(server, titan, cx + 20, padY, cz);
            }
            check(f, TitanDormancy.isResting(server, titan), tag + ": the sleep did not begin");
            // 眠って 10 秒までは本体が残る (岩鯨の沈みは目に見える。場面の前半)
            TitanDormancy.backdate(server, titan, TitanDormancy.DEPART_START - 20 + (realKill ? TitanEnd.CARRY_AT : 0));
            tick(e, 40);
            check(f, !e.isRemoved(), tag + ": the titan left before the sleep scene could be seen");
            Vec3 before = e.position();
            // 去りはじめ: 動きが変わり、本体はまだある
            TitanDormancy.backdate(server, titan, 100);
            tick(e, 300);
            check(f, !e.isRemoved(), tag + ": the titan was removed before 40 seconds");
            Vec3 moved = e.position();
            switch (titan) {
                case Titans.STONE_WHALE -> check(f, Math.abs(moved.y - before.y) > 3, tag + ": the whale did not rise or sink away (" + before.y + " -> " + moved.y + ")");
                case Titans.TIDE_SHELL -> check(f, moved.y < level.getSeaLevel() - TideShellEntity.SLEEP_DRAFT - 5, tag + ": the tide shell did not sink out of sight (y " + moved.y + ")");
                case Titans.THUNDER_WING -> check(f, moved.distanceTo(new Vec3(cx + 20, padY, cz)) > 40 && moved.y > padY + 8, tag + ": the thunder wing did not fly off (" + moved + ")");
                default -> check(f, moved.y < before.y - 8, tag + ": the ember titan did not burrow away (" + before.y + " -> " + moved.y + ")");
            }
            // 40 秒: 取り除かれる。見守りも、眠りのあいだは呼び直さない
            TitanDormancy.backdate(server, titan, TitanDormancy.DEPART_TICKS);
            tick(e, 3);
            check(f, e.isRemoved(), tag + ": the titan stayed in the overworld after 40 seconds of sleep");
            check(f, keeper.current(level) == null, tag + ": the keeper still found a body after it left");
            int holds = keeper.restHolds();
            long now = level.getGameTime();
            for (int i = 0; i < 400; i++) {
                keeper.tick(level, now + i * 20L);
            }
            check(f, keeper.restHolds() >= holds + 400, tag + ": the keeper did not hold back while the titan slept (" + holds + " -> " + keeper.restHolds() + ")");
            check(f, keeper.current(level) == null, tag + ": the keeper called the sleeping titan again");
            check(f, countOf(level, e.getClass(), cx, cz) == 0, tag + ": a second body appeared while the titan slept");
            // 目覚めたら、控えは止まる (呼び直しは近くの人がいるときに見守りが行う)
            TitanDormancy.wakeNow(server, titan);
            holds = keeper.restHolds();
            keeper.tick(level, now + 99999L);
            check(f, keeper.restHolds() == holds, tag + ": the keeper kept holding back after the titan woke");
        } finally {
            if (!e.isRemoved()) {
                e.discard();
            }
            keeper.releaseTicket(level);
            TitanDormancy.clear(server, titan);
            for (var chain : level.getEntitiesOfClass(jp.revati.skyabyss.titan.WhaleChainEntity.class, new net.minecraft.world.phys.AABB(cx - 200, 0, cz - 200, cx + 200, 320, cz + 200))) {
                chain.discard();
            }
            for (var gale : level.getEntitiesOfClass(jp.revati.skyabyss.titan.storm.GaleColumnEntity.class, new net.minecraft.world.phys.AABB(cx - 200, 0, cz - 200, cx + 200, 400, cz + 200))) {
                gale.discard();
            }
            for (var mouth : level.getEntitiesOfClass(jp.revati.skyabyss.titan.ember.EmberMouthEntity.class, new net.minecraft.world.phys.AABB(cx - 200, 0, cz - 200, cx + 200, 400, cz + 200))) {
                mouth.discard();
            }
        }
    }

    private static int countOf(ServerLevel level, Class<? extends Entity> type, double cx, double cz) {
        return level.getEntitiesOfClass(type, new net.minecraft.world.phys.AABB(cx - 600, -64, cz - 600, cx + 600, 400, cz + 600)).size();
    }

    // `playtest next` / `trials` は心臓を止めずに進みだけ記す: 巨神は眠らず、残る。止めるのは心臓の撃破 (または `titan end sleep`) だけ
    private static void playtestPath(MinecraftServer server, List<String> f) {
        UUID who = UUID.fromString("7e5a0e0d-0000-4000-8000-0000000e0062");
        for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL)) {
            TitanDormancy.clear(server, titan);
            Progression.onTitanStopped(server, List.of(who), titan);
            check(f, !TitanDormancy.isResting(server, titan), titan + ": marking the titan as stopped (playtest next) put it to sleep without a heart kill");
        }
    }

    // 光の柱と枠の知らせは、書いて読み戻しても同じ。巨神ごとの色が違う
    private static void beam(MinecraftServer server, List<String> f) {
        BoardGlow.Shape shape = new BoardGlow.Shape(new Vec3(10.5, 70, -3.25), 2f, 3f, 4f, 45f, 60, 120, 1.5f);
        var seen = new java.util.HashSet<Integer>();
        for (String titan : Titans.all()) {
            TitanEndNetwork.Beam b = BoardGlow.beam(titan, shape);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            TitanEndNetwork.Beam.STREAM_CODEC.encode(buf, b);
            TitanEndNetwork.Beam back = TitanEndNetwork.Beam.STREAM_CODEC.decode(buf);
            check(f, back.equals(b), "the boarding beam changed on the wire for " + titan);
            check(f, back.ttl() > BoardGlow.BEAM_EVERY, "the boarding beam would flicker (ttl " + back.ttl() + ")");
            seen.add(back.rgb());
        }
        check(f, seen.size() == Titans.all().size(), "the four titans do not have four beam colours");
        check(f, BoardGlow.Shape.fromPoints(List.of(new Vec3(0, 80, 0), new Vec3(0, 60, 0), new Vec3(0, 100, 0))).beamFrom() == 60
                && BoardGlow.Shape.fromPoints(List.of(new Vec3(0, 80, 0))).beamTo() >= 80 + BoardGlow.MIN_BEAM, "the default beam shape is wrong");
    }

    // 帰り石: 形の判定、到着点のそばの置き場所 (戻り道のある方へ寄る)、四体の休み場の一覧
    private static void returnStones(MinecraftServer server, ServerLevel level, List<String> f, double cx, double cz, int padY) {
        ReturnBeacons.Beacon b = new ReturnBeacons.Beacon("t", new Vec3(5.5, 70, 5.5));
        check(f, ReturnBeacons.inside(b, new Vec3(5.5, 70, 5.5)) && ReturnBeacons.inside(b, new Vec3(6.5, 71, 5.5)), "standing on a return stone did not count");
        check(f, !ReturnBeacons.inside(b, new Vec3(8.5, 70, 5.5)) && !ReturnBeacons.inside(b, new Vec3(5.5, 74, 5.5)) && !ReturnBeacons.inside(b, new Vec3(5.5, 60, 5.5)),
                "a return stone counted from too far");
        // 床を敷いて、到着点から 3〜7 ブロックの床で、戻り道 (東) にいちばん近い所が選ばれる
        int bx = (int) cx;
        int bz = (int) cz;
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlockAndUpdate(new BlockPos(bx + dx, padY - 1, bz + dz), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(bx + dx, padY, bz + dz), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(bx + dx, padY + 1, bz + dz), Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos arrival = new BlockPos(bx, padY, bz);
        Vec3 east = ReturnBeacons.findNear(level, arrival, new Vec3(bx + 40, padY, bz));
        Vec3 west = ReturnBeacons.findNear(level, arrival, new Vec3(bx - 40, padY, bz));
        check(f, east != null && west != null, "no return stone spot near the arrival on open floor");
        if (east != null && west != null) {
            double de = Math.hypot(east.x - (bx + 0.5), east.z - (bz + 0.5));
            check(f, de >= ReturnBeacons.RADIUS && de <= 7.5 && east.x > bx + 0.5, "the east return stone is misplaced (" + east + ")");
            check(f, west.x < bx + 0.5, "the west return stone is misplaced (" + west + ")");
        }
        // 水の上・穴は置かない
        level.setBlockAndUpdate(new BlockPos(bx + 5, padY, bz), Blocks.WATER.defaultBlockState());
        check(f, !ReturnBeacons.standable(level, new BlockPos(bx + 5, padY, bz)), "water counted as a place for a return stone");
        level.setBlockAndUpdate(new BlockPos(bx + 5, padY, bz), Blocks.AIR.defaultBlockState());
        for (String titan : Titans.all()) {
            check(f, !ReturnBeacons.restSpots(titan).isEmpty(), titan + ": no rest spot has a return stone");
            check(f, ReturnBeacons.arrivalOf(titan) != null && ReturnBeacons.exitOf(titan) != null, titan + ": the arrival or exit of the back is unknown");
        }
        // 背中の層が建っているときは、休み場の復活の位置が帰り石に使える (立てる床)
        for (String titan : Titans.all()) {
            var dim = Titans.backOf(titan);
            ServerLevel back = dim == null ? null : server.getLevel(dim);
            if (back == null || !jp.revati.skyabyss.titan.dungeon.TitanBackData.get(back).isBuilt()) {
                continue;
            }
            for (var e : ReturnBeacons.restSpots(titan).entrySet()) {
                BlockPos p = e.getValue();
                if (back.hasChunkAt(p) && ReturnBeacons.standable(back, p) == false && ReturnBeacons.standable(back, p.above()) == false) {
                    f.add(titan + ": the rest spot " + e.getKey() + " at " + p + " cannot hold a return stone");
                }
            }
        }
        // 戻り先 (潮殻): 岸が見つからないときだけ海の上 (舟つき)。見つかれば陸
        check(f, jp.revati.skyabyss.titan.tide.TideTravel.landingSafe(level), "leaving the tide back would drop the player into the open sea without a boat");
        // 岩鯨の戻りは TitanTravel のまま (鎖の下)。ここでは戻り道が登録されていることだけ
        check(f, Titans.hasBack(Titans.STONE_WHALE) && Titans.hasBack(Titans.TIDE_SHELL), "a back has no way down");
        check(f, TitanTravel.TITAN_BACK != null, "whale back key");
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}

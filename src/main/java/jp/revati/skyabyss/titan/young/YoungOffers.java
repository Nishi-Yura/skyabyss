package jp.revati.skyabyss.titan.young;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.readiness.BoardReadiness;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 子が成体のもとへ着いたあとの「その人たちにだけ開く乗り込み口」(docs/design/28 の 5 章)。
// 成体は動かない。降りた所 (成体の体の外) に、光の柱と輪を立てる。輪の中に立つと (備えの三灯の確かめのあとで) その巨神の背へ入る。
// 光が見えて輪が効くのは、持ち主と同じパーティの人だけ。ほかの人には何も出ない。
final class YoungOffers {
    // 光が見える距離
    private static final double SEE = 220;
    // 輪を待つ人を数える距離 (歩いて戻ってくる人がいる間は、子は残る)
    private static final double WAITING = 60;

    private YoungOffers() {
    }

    // 光と輪を受けられる人 (持ち主と同じパーティ。観戦者・倒れた人は除く)
    private static List<ServerPlayer> party(YoungTrip trip, ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        Vec3 c = trip.ringCentre();
        for (ServerPlayer p : YoungCarriers.online(level.getServer())) {
            if (p.level() == level && p.isAlive() && !p.isSpectator() && trip.mayRide(p) && p.distanceToSqr(c.x, p.getY(), c.z) <= SEE * SEE) {
                list.add(p);
            }
        }
        return list;
    }

    static void tick(YoungTrip trip, ServerLevel level, String titan, List<ServerPlayer> riders) {
        Vec3 c = trip.ringCentre();
        long t = level.getGameTime();
        for (ServerPlayer p : party(trip, level)) {
            if (!trip.titanEligible(p, titan)) {
                continue;
            }
            if (t % 20 == 0) {
                PacketDistributor.sendToPlayer(p, trip.beam(titan));
            }
            if (t % 4 != 0 || p.isPassenger() || !p.isAlive()) {
                continue;
            }
            double dx = p.getX() - c.x;
            double dz = p.getZ() - c.z;
            if (dx * dx + dz * dz > YoungTrip.RING_RADIUS * YoungTrip.RING_RADIUS || Math.abs(p.getY() - c.y) > 4.0) {
                continue;
            }
            if (BoardReadiness.attempt(p, titan, BoardReadiness.Mode.WALK_IN)) {
                board(p, titan);
            }
        }
    }

    // 輪へ向かっている (戻ってくる) かもしれない仲間の数。降りたばかりで遠くへ出た人も数える
    static int waiting(YoungTrip trip, ServerLevel level) {
        int n = 0;
        Vec3 c = trip.ringCentre();
        for (ServerPlayer p : party(trip, level)) {
            if (!p.isPassenger() && p.distanceToSqr(c.x, p.getY(), c.z) <= WAITING * WAITING) {
                n++;
            }
        }
        return n;
    }

    // 背へ入れる (その巨神の入口の関数。乗る資格・眠りは入口が見る)
    static boolean board(ServerPlayer p, String titan) {
        try {
            return switch (titan) {
                case Titans.STONE_WHALE -> jp.revati.skyabyss.titan.TitanTravel.boardFromYoung(p);
                case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.TideTravel.board(p);
                case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.StormTravel.board(p);
                case Titans.EMBER -> jp.revati.skyabyss.titan.ember.EmberTravel.board(p);
                default -> false;
            };
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("Boarding {} from the young one failed", titan, ex);
            return false;
        }
    }
}

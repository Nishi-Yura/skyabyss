package jp.revati.skyabyss.trial.raid.tide;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

// この巨神のレイドの部屋を一人で越える台本 (trial/raid/RaidSolo。自己テスト raid solo)
public final class TideSolo {
    private TideSolo() {
    }

    public static void register() {
        RaidSolo.register("TR1", (ctx, room, p, t) -> pearls(ctx, (TidePearlRoom) room, p, t));
        RaidSolo.register("TR4", (ctx, room, p, t) -> coral(ctx, (TideCoralRoadRoom) room, p, t));
        RaidSolo.register("TR5", (ctx, room, p, t) -> light(ctx, (TideLightRoom) room, p, t));
        RaidSolo.register("TR6", (ctx, room, p, t) -> moons(ctx, (TideMoonRoom) room, p, t));
        RaidSolo.register("TR3", RaidSolo.FIGHT);
        RaidSolo.register("TR7", (ctx, room, p, t) -> brood(ctx, (TideBroodRoom) room, p, t));
    }

    // TR1: 台の真珠へ歩み寄って持ち、祭壇の前まで運ぶ。一度に一つ
    private static void pearls(RaidCtx ctx, TidePearlRoom room, ServerPlayer p, int t) {
        if (t % 10 != 0) {
            return;
        }
        for (int i = 0; i < room.pearlCount(); i++) {
            if (p.getUUID().equals(room.carrierOf(i)) && !room.isDelivered(i)) {
                RaidSolo.put(p, ctx.origin().offset(0, 1, TidePearlRoom.ALTAR_Z + 2));
                return;
            }
        }
        for (int i = 0; i < room.pearlCount(); i++) {
            if (!room.isDelivered(i) && room.carrierOf(i) == null) {
                RaidSolo.put(p, room.pearlHome(i));
                return;
            }
        }
    }

    // TR4: いちばん手前のまだ越えていない壁の、開いている戸を通る
    private static void coral(RaidCtx ctx, TideCoralRoadRoom room, ServerPlayer p, int t) {
        if (t % 5 != 0) {
            return;
        }
        int row = room.passedBy(p);
        if (row >= TideCoralRoadRoom.ROWS) {
            return;
        }
        for (int lane = 0; lane < TideCoralRoadRoom.LANE_X.length; lane++) {
            if (room.fullyOpen(row, lane)) {
                // 開いている戸をくぐって、壁 (厚さ 2) の向こうへ
                RaidSolo.put(p, room.gateSpot(row, lane).north(2));
                return;
            }
        }
    }

    // TR5: 光の道の台を、正しい向きまで回す (光の筋を目で追うのと同じ)
    private static void light(RaidCtx ctx, TideLightRoom room, ServerPlayer p, int t) {
        if (t % 5 != 0) {
            return;
        }
        for (int[] n : room.pathNodes()) {
            if (room.facing(n[0], n[1]) != room.correctFacing(n[0], n[1])) {
                RaidSolo.use(ctx, room, p, room.mirrorPos(n[0], n[1]), false);
                return;
            }
        }
    }

    // TR6: 壁画を読み、月の石を同じ形まで回す
    private static void moons(RaidCtx ctx, TideMoonRoom room, ServerPlayer p, int t) {
        if (t % 5 != 0) {
            return;
        }
        for (int k = 0; k < TideMoonRoom.DIALS; k++) {
            if (room.phaseOf(k) != room.correctOf(k)) {
                RaidSolo.use(ctx, room, p, room.dialPos(k), false);
                return;
            }
        }
    }

    // TR7: 影のある本物の母を追って叩く (いなければ近い敵)
    private static void brood(RaidCtx ctx, TideBroodRoom room, ServerPlayer p, int t) {
        Mob m = room.mother();
        if (m == null || !m.isAlive()) {
            RaidSolo.FIGHT.act(ctx, room, p, t);
            return;
        }
        if (t % 12 != 0) {
            return;
        }
        BlockPos stand = RaidSolo.standNear(ctx, m.blockPosition());
        if (stand != null) {
            RaidSolo.put(p, stand);
        }
        m.invulnerableTime = 0;
        m.hurt(p.damageSources().playerAttack(p), 7f);
    }
}

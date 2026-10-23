package jp.revati.skyabyss.trial.raid.storm;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

// 雷翼のレイドの部屋を一人で越える台本 (trial/raid/RaidSolo。自己テスト raid solo)。人ができることだけ: 歩く・拾う・叩く・踏む
public final class StormSolo {
    private StormSolo() {
    }

    // 歩く速さ (ブロック / tick。歩きで 4.3 m/s、羽を持つと遅い)
    static final double WALK = 0.21;
    static final double SLOW = 0.15;

    public static void register() {
        RaidSolo.register(StormRaid.SR1, StormSolo::feathers);
        RaidSolo.register(StormRaid.SR2, StormSolo::circuit);
        RaidSolo.register(StormRaid.SR3, RaidSolo.FIGHT);
        RaidSolo.register(StormRaid.SR4, StormSolo::keys);
        RaidSolo.register(StormRaid.SR5, StormSolo::beat);
        RaidSolo.register(StormRaid.SR6, RaidSolo.FIGHT);
    }

    // feet へ向かって speed だけ歩く (高さは feet に合わせる)。着いたら true
    static boolean walk(ServerPlayer p, Vec3 feet, double speed) {
        Vec3 at = p.position();
        double dx = feet.x - at.x;
        double dz = feet.z - at.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= speed) {
            RaidSolo.put(p, feet);
            return true;
        }
        RaidSolo.put(p, new Vec3(at.x + dx / d * speed, feet.y, at.z + dz / d * speed));
        return false;
    }

    // ---- SR1: 西の羽を拾い、東の止まり木へ。持つと雷の予告が来るが、歩き続ければ赤い輪 (半径 4) の外へ出る
    private static void feathers(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        FeatherCarry r = (FeatherCarry) room;
        boolean has = false;
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
            if (FeatherCarry.featherIndex(p.getInventory().getItem(slot)) >= 0) {
                has = true;
            }
        }
        double fy = ctx.origin().getY() + 1;
        if (has) {
            BlockPos perch = r.guideTarget(ctx);
            if (perch != null) {
                // 止まり木の台の前 (西側) へ
                walk(p, new Vec3(perch.getX() - 1.5, fy, perch.getZ() + 0.5), SLOW);
            }
            return;
        }
        ItemEntity best = null;
        for (ItemEntity e : ctx.level().getEntitiesOfClass(ItemEntity.class, ctx.cell().inner().inflate(1))) {
            int idx = FeatherCarry.featherIndex(e.getItem());
            if (idx >= 0 && !r.delivered(idx) && (best == null || e.distanceToSqr(p) < best.distanceToSqr(p))) {
                best = e;
            }
        }
        if (best == null) {
            return;
        }
        if (walk(p, new Vec3(best.getX(), fy, best.getZ()), WALK)) {
            best.playerTouch(p);
        }
    }

    // ---- SR2: 盤を見て、合っていないタイルを一枚ずつ叩いて回す (0.3 秒に一度)
    private static void circuit(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        if (t % 6 != 0) {
            return;
        }
        CircuitRoom r = (CircuitRoom) room;
        CircuitRoom.Layout l = r.layout();
        for (int x = 0; x < CircuitRoom.N; x++) {
            for (int z = 0; z < CircuitRoom.N; z++) {
                if (!l.isFixed(x, z) && r.current()[x][z] != l.solution[x][z]) {
                    RaidSolo.use(ctx, room, p, r.tileCenter(ctx, x, z), true);
                    return;
                }
            }
        }
    }

    // ---- SR4: 光の柱の鍵へ歩く (柱より速い)
    private static void keys(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        BlockPos k = room.guideTarget(ctx);
        if (k != null) {
            walk(p, Vec3.atBottomCenterOf(k), WALK);
        }
    }

    // ---- SR5: 見せている間に一枚目へ立って待ち、拍ごとに次の板 (いつも隣) へ歩く。板の間の筋を通るので、ほかの板は踏まない
    private static void beat(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        BeatTiles r = (BeatTiles) room;
        int[] seq = r.seq();
        switch (r.phase()) {
            case PREP, SHOW, GAP, PASS, FAIL -> {
                if (r.phase() != BeatTiles.Phase.PASS || r.phaseAge() > BeatTiles.PAUSE - 4) {
                    RaidSolo.put(p, tile(ctx, seq[0]));
                }
            }
            case INPUT -> {
                if (r.next() < r.length()) {
                    walk(p, tile(ctx, seq[r.next()]), 0.26);
                }
            }
            default -> {
            }
        }
    }

    private static Vec3 tile(RaidCtx ctx, int i) {
        BlockPos c = ctx.origin().offset(BeatTiles.tileC(i % BeatTiles.N), 1, BeatTiles.tileC(i / BeatTiles.N));
        return Vec3.atBottomCenterOf(c);
    }
}

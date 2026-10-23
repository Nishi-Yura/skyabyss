package jp.revati.skyabyss.trial.raid.ember;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 余燼のレイドの部屋を一人で越える台本 (trial/raid/RaidSolo。自己テスト raid solo)。人ができることだけ: 歩く・立つ・しゃがむ・叩く
public final class EmberSolo {
    private EmberSolo() {
    }

    static final double WALK = 0.21;

    public static void register() {
        RaidSolo.register(EmberCarry.ID, EmberSolo::carry);
        RaidSolo.register(EmberForgeOrder.ID, EmberSolo::order);
        RaidSolo.register(EmberForgeDefense.ID, RaidSolo.FIGHT);
        RaidSolo.register(EmberChainPath.ID, EmberSolo::chain);
        RaidSolo.register(EmberValves.ID, EmberSolo::valves);
        RaidSolo.register(EmberApprentices.ID, RaidSolo.FIGHT);
    }

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

    // ---- ER1: 噴き口の台の手前に立って熾を取り (台の上は熾の灯り)、冷えた炉の前へ歩く (行き先の印のとおり)
    private static void carry(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        EmberCarry r = (EmberCarry) room;
        if (r.guideTarget(ctx) == null) {
            return;
        }
        BlockPos to = r.carrying() == 0 ? r.sourcePos().offset(0, 1, 2) : r.forgePos().offset(0, 1, -2);
        walk(p, Vec3.atBottomCenterOf(to), WALK);
    }

    // ---- ER2: 四つの台に一つずつ立って光らせ (2 秒)、壁画の順に台の上でしゃがむ
    private static final int[] ORDER_STATE = new int[2];

    private static void order(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        EmberForgeOrder r = (EmberForgeOrder) room;
        if (t == 0) {
            ORDER_STATE[0] = 0;
            ORDER_STATE[1] = 0;
        }
        p.setShiftKeyDown(false);
        if (r.readyCount() + r.next() < 4) {
            // 光っていない台へ順に立つ (一台 45 tick)
            int kind = ORDER_STATE[0] % 4;
            // 台の真ん中は道具なので、その隣の色の床に立つ
            RaidSolo.put(p, r.padOf(ctx, kind).offset(1, 1, 0));
            if (++ORDER_STATE[1] >= EmberForgeOrder.CHARGE_TICKS + 5) {
                ORDER_STATE[1] = 0;
                ORDER_STATE[0]++;
            }
            return;
        }
        // 壁画の順の次の台へ乗り、10 tick ごとに一度しゃがむ
        int kind = r.order()[Math.min(r.next(), 3)];
        // 台の真ん中は道具なので、その隣の色の床に立つ
            RaidSolo.put(p, r.padOf(ctx, kind).offset(1, 1, 0));
        if (t % 10 == 5) {
            p.setShiftKeyDown(true);
        }
    }

    // ---- ER4: 上り段から島へ、橋を歩いて渡る (板は後ろから落ちるが、歩けば先へ行ける)。奥の下り段へ
    private static void chain(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        double deckFeet = ctx.origin().getY() + EmberChainPath.DECK + 1;
        double lz = p.getZ() - ctx.origin().getZ();
        if (t == 0 || p.getY() < deckFeet - 0.5 && lz > -23) {
            // 上り段を上がって手前の島へ (落ちて手前の島へ戻されたときは、その場から)
            if (t == 0) {
                RaidSolo.put(p, new Vec3(ctx.origin().getX() + 0.5, deckFeet, ctx.origin().getZ() + 15.5));
            }
            return;
        }
        if (lz > -23.6) {
            walk(p, new Vec3(ctx.origin().getX() + 0.5, deckFeet, ctx.origin().getZ() - 23.6), WALK);
        } else {
            RaidSolo.put(p, Vec3.atBottomCenterOf(ctx.local(0, 3, -24)));
        }
    }

    // ---- ER5: 針を見て、そろうまでの回し方を考え、一手ずつ弁を回す (0.4 秒に一度)
    private static void valves(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t) {
        if (t % 8 != 0) {
            return;
        }
        EmberValves r = (EmberValves) room;
        int[] path = r.solve();
        if (path != null && path.length > 0) {
            RaidSolo.use(ctx, room, p, r.valvePos(ctx, path[0]), false);
        }
    }
}

package jp.revati.skyabyss.trial.raid.whale;

import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

// この巨神のレイドの部屋を一人で越える台本 (trial/raid/RaidSolo。自己テスト raid solo)
public final class WhaleSolo {
    private WhaleSolo() {
    }

    public static void register() {
        RaidSolo.register(WhaleRaid.WALK, (ctx, room, p, t) -> carry(ctx, (WhaleCarryRoom) room, p, t));
        RaidSolo.register(WhaleRaid.WIT, (ctx, room, p, t) -> bones(ctx, (WhaleBonesRoom) room, p, t));
        RaidSolo.register(WhaleRaid.FIGHT, (ctx, room, p, t) -> core(ctx, p, t));
        RaidSolo.register(WhaleRaid.WALK2, (ctx, room, p, t) -> shaft(ctx, (WhaleShaftRoom) room, p, t));
        RaidSolo.register(WhaleRaid.WIT2, (ctx, room, p, t) -> tiles(ctx, (WhaleTilesRoom) room, p, t));
        RaidSolo.register(WhaleRaid.FIGHT2, RaidSolo.FIGHT);
    }

    // WR1: 床の骨玉へ歩いて拾い、台座の前まで運ぶ。一度に一つ
    private static void carry(RaidCtx ctx, WhaleCarryRoom room, ServerPlayer p, int t) {
        if (t % 10 != 0) {
            return;
        }
        boolean has = false;
        for (int s = 0; s < p.getInventory().getContainerSize(); s++) {
            if (p.getInventory().getItem(s).is(ModItems.RAID_BONE_ORB.get())) {
                has = true;
            }
        }
        if (has) {
            RaidSolo.put(p, ctx.origin().offset(0, 1, WhaleCarryRoom.ALTAR_Z + 2));
            return;
        }
        ItemEntity best = null;
        for (ItemEntity e : ctx.level().getEntitiesOfClass(ItemEntity.class, ctx.cell().inner())) {
            if (e.getItem().is(ModItems.RAID_BONE_ORB.get()) && !e.hasPickUpDelay() && (best == null || e.distanceToSqr(p) < best.distanceToSqr(p))) {
                best = e;
            }
        }
        if (best != null) {
            // 歩いて行って拾う (触れると持ち物に入る)
            RaidSolo.put(p, best.position());
            if (p.getInventory().add(best.getItem().copy())) {
                best.discard();
            }
        }
    }

    // WR2: 聴いた順に骨を叩く。和音は一本叩いて、走って (1 秒) もう一本
    private static void bones(RaidCtx ctx, WhaleBonesRoom room, ServerPlayer p, int t) {
        if (!room.inputting() || t % 20 != 0 || room.counter() >= WhaleBonesRoom.LENGTH) {
            return;
        }
        int[] want = room.sequence().get(room.counter());
        int bone = want[Math.min(want.length - 1, room.struckCount())];
        BlockPos at = ctx.origin().offset(WhaleBonesRoom.CORNER[bone][0], 2, WhaleBonesRoom.CORNER[bone][1]);
        RaidSolo.use(ctx, room, p, at, true);
    }

    // WR3: 核にいちばん近い敵から倒す
    private static void core(RaidCtx ctx, ServerPlayer p, int t) {
        if (t % 12 != 0) {
            return;
        }
        Vec3 core = Vec3.atBottomCenterOf(ctx.origin().above());
        Mob best = null;
        for (Mob m : ctx.aliveMobs()) {
            if (best == null || m.distanceToSqr(core) < best.distanceToSqr(core)) {
                best = m;
            }
        }
        if (best == null) {
            RaidSolo.put(p, ctx.cell().floor(0, 3));
            return;
        }
        BlockPos stand = RaidSolo.standNear(ctx, best.blockPosition());
        if (stand != null) {
            RaidSolo.put(p, stand);
        }
        best.invulnerableTime = 0;
        best.hurt(p.damageSources().playerAttack(p), 7f);
    }

    // WR4: 今の踊り場の手元の突起を叩き、階段が伸びきったら次の踊り場へ上る
    private static void shaft(RaidCtx ctx, WhaleShaftRoom room, ServerPlayer p, int t) {
        int f = room.tier();
        if (f >= WhaleShaftRoom.FLIGHTS || t % 10 != 0) {
            return;
        }
        if (room.grown(f) >= WhaleShaftRoom.RISE) {
            RaidSolo.put(p, room.landingSpot(ctx, f + 1));
            return;
        }
        if (f > 0) {
            RaidSolo.put(p, room.landingSpot(ctx, f));
        }
        if (t % 40 == 0) {
            // 床の手元の突起は歩いて行って叩く (use がそばへ移す)
            RaidSolo.use(ctx, room, p, room.localSwitch(ctx, f), true);
        }
    }

    // WR5: 見本と違う石板を、合うまで一枚ずつ叩いて回す
    private static void tiles(RaidCtx ctx, WhaleTilesRoom room, ServerPlayer p, int t) {
        if (t % 4 != 0) {
            return;
        }
        for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
            for (int r = 0; r < WhaleTilesRoom.ROWS; r++) {
                if (room.turnsNeeded(c, r) > 0) {
                    RaidSolo.use(ctx, room, p, room.tileCenter(ctx, c, r), true);
                    return;
                }
            }
        }
    }
}

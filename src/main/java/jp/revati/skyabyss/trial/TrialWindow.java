package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import jp.revati.skyabyss.titan.group.Groups;

// 区画の写しのセル (docs/design/32 の 5 章) の決まりを、手本の座標で動かす窓。
//   1. 写しの中の、そのグループの人とそのグループの敵を、手本の座標へ移す (setPosRaw。通信は出ない)
//   2. ずれを置いて (TrialFrame.with) セルの決まりを呼ぶ。戸・音・粒は写しの座標へずれる (GroupGates・Groups.sound・TrialFx)
//   3. 移した人と敵を写しの座標へ戻す。決まりの中で動かされた (休み場へ戻された・押された) なら、動いた先の写しの座標へ。
//      決まりが手本の部屋に出した、そのグループの敵も写しへ移す
// 手本の部屋のチャンクは TrialSlot が読み込んでおく (移した人・敵が読み込まれていない所へ出て、追い出されないように)
public final class TrialWindow {
    private TrialWindow() {
    }

    // 写しの決まりを呼ぶ。frame がずれ 0 なら、そのまま呼ぶ
    public static void run(ServerLevel back, UUID group, TrialCell cell, TrialFrame frame, Runnable call) {
        if (frame.isZero()) {
            call.run();
            return;
        }
        AABB template = cell.buildBox().inflate(8);
        AABB copy = frame.box(template);
        Map<Entity, Vec3> placed = new IdentityHashMap<>();
        List<Entity> inCopy = back.getEntities((Entity) null, copy, e -> e.isAlive() && belongs(e, group));
        for (Entity e : inCopy) {
            Vec3 local = frame.local(e.position());
            e.setPosRaw(local.x, local.y, local.z);
            placed.put(e, local);
        }
        try {
            TrialFrame.with(frame, call);
        } finally {
            for (Map.Entry<Entity, Vec3> en : placed.entrySet()) {
                Entity e = en.getKey();
                if (e.isRemoved()) {
                    continue;
                }
                Vec3 now = e.position();
                Vec3 world = frame.world(now);
                if (now.distanceToSqr(en.getValue()) < 1.0e-6) {
                    e.setPosRaw(world.x, world.y, world.z);
                } else {
                    move(back, e, world);
                }
            }
            // 決まりが手本の部屋に出した (または戻した) そのグループの人・敵を写しへ
            List<Entity> fresh = new ArrayList<>(back.getEntities((Entity) null, template, x -> x.isAlive() && belongs(x, group)));
            for (Entity e : fresh) {
                if (!placed.containsKey(e)) {
                    move(back, e, frame.world(e.position()));
                }
            }
            // 決まりの中で道を探させた敵 (手本の座標の行き先) は、写しの座標の行き先で探し直す
            for (Entity e : placed.keySet()) {
                repath(e, template, frame);
            }
            for (Entity e : fresh) {
                repath(e, template, frame);
            }
        }
    }

    // そのグループの人か、そのグループの持ち物の敵・実体か (観戦者は除く)
    static boolean belongs(Entity e, UUID group) {
        if (e instanceof Player p) {
            return !p.isSpectator() && e instanceof ServerPlayer sp && Groups.isMember(sp, group);
        }
        UUID g = Groups.keyOf(e);
        return g != null && g.equals(group) && !g.equals(e.getUUID());
    }

    private static void repath(Entity e, AABB template, TrialFrame frame) {
        if (!(e instanceof net.minecraft.world.entity.Mob mob) || e.isRemoved()) {
            return;
        }
        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone() || !template.contains(net.minecraft.world.phys.Vec3.atCenterOf(path.getTarget()))) {
            return;
        }
        net.minecraft.core.BlockPos to = frame.at(path.getTarget());
        mob.getNavigation().stop();
        mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
    }

    private static void move(ServerLevel back, Entity e, Vec3 to) {
        if (e instanceof ServerPlayer p) {
            p.teleportTo(back, to.x, to.y, to.z, p.getYRot(), p.getXRot());
            if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
                p.setPos(to.x, to.y, to.z);
            }
        } else {
            e.setPos(to.x, to.y, to.z);
        }
    }
}

package jp.revati.skyabyss.entity;

import java.util.Optional;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

// 体の長い主の当たりの部品の並び (BodyPart)。使い方 (主の型の中で):
//   private final LongBody body = LongBody.of(this, new double[][] {{前, 横, 幅, 高さ}, ...});
//   コンストラクタの最後で body.reserveIds()、setId で body.setIds(id)、tick の最後で body.follow()、
//   isMultipartEntity は true、getParts は body.parts()
// 部品の id は本体の id の続き (エンダードラゴンと同じ。サーバーとクライアントで同じ部品を指す)
public final class LongBody {
    private final Mob owner;
    private final BodyPart[] parts;

    private LongBody(Mob owner, BodyPart[] parts) {
        this.owner = owner;
        this.parts = parts;
    }

    // 部品: {前 (ブロック。負 = 後ろ), 横 (正 = 右), 幅, 高さ}
    public static LongBody of(Mob owner, double[][] spec) {
        BodyPart[] parts = new BodyPart[spec.length];
        for (int i = 0; i < spec.length; i++) {
            parts[i] = new BodyPart(owner, spec[i][0], spec[i][1], (float) spec[i][2], (float) spec[i][3]);
        }
        return new LongBody(owner, parts);
    }

    public PartEntity<?>[] parts() {
        return parts;
    }

    // 本体の id を取り直して、続きの番号を部品のぶん空けておく (コンストラクタの最後で一度)
    public void reserveIds(java.util.function.IntConsumer setId) {
        setId.accept(Ids.next(parts.length + 1));
    }

    public void setIds(int id) {
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(id + i + 1);
        }
    }

    // 体の向き (yBodyRot) に合わせて部品を置き直す (サーバーとクライアントの両方の tick で)
    public void follow() {
        double yaw = Math.toRadians(owner.yBodyRot);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double scale = owner.getScale();
        for (BodyPart p : parts) {
            double f = p.forward * scale;
            double s = p.side * scale;
            double x = owner.getX() + fx * f - fz * s;
            double z = owner.getZ() + fz * f + fx * s;
            p.xo = p.getX();
            p.yo = p.getY();
            p.zo = p.getZ();
            p.xOld = p.xo;
            p.yOld = p.yo;
            p.zOld = p.zo;
            p.setPos(x, owner.getY(), z);
        }
    }

    // 部品なら本体、そうでなければそのまま
    public static Entity owner(Entity e) {
        return e instanceof BodyPart p ? p.getParent() : e;
    }

    // 線 (from → to) が、その生き物の当たり (本体の箱と部品の箱) に最初に触れる所。触れなければ空
    public static Optional<Vec3> clip(LivingEntity e, Vec3 from, Vec3 to, double fat) {
        Optional<Vec3> best = e.getBoundingBox().inflate(fat).clip(from, to);
        if (e.isMultipartEntity() && e.getParts() != null) {
            for (PartEntity<?> p : e.getParts()) {
                Optional<Vec3> h = p.getBoundingBox().inflate(fat).clip(from, to);
                if (h.isPresent() && (best.isEmpty() || h.get().distanceToSqr(from) < best.get().distanceToSqr(from))) {
                    best = h;
                }
            }
        }
        return best;
    }

    // 線のそばにある部品の本体 (本体の箱が線の探す箱の外にある長い体を拾う)
    public static java.util.List<LivingEntity> ownersNear(Level level, AABB box) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        for (PartEntity<?> p : level.getPartEntities()) {
            if (p instanceof BodyPart bp && bp.getBoundingBox().intersects(box) && bp.getParent() instanceof LivingEntity le && !out.contains(le)) {
                out.add(le);
            }
        }
        return out;
    }

    // Entity.ENTITY_COUNTER は守られた (protected) ので、Entity の子から触る
    private static final class Ids extends Entity {
        private Ids() {
            super(null, null);
        }

        static int next(int count) {
            return ENTITY_COUNTER.getAndAdd(count) + 1;
        }

        @Override
        protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        }

        @Override
        protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        }
    }
}

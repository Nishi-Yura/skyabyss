package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 区画の写し (docs/design/32 の 5 章) の、手本のセルからのずれ。セルの決まりは手本の座標で書いてあり、写しで動かすときは
// TrialRuntime がこのずれで人・敵・戸・音・見かけを手本の座標と写しの座標のあいだで行き来させる (TrialFrame.Window)。
// ZERO = 手本そのもの (背の体の中の一組。初回・写しのないセル)
public record TrialFrame(int dx, int dy, int dz) {
    public static final TrialFrame ZERO = new TrialFrame(0, 0, 0);

    public boolean isZero() {
        return dx == 0 && dy == 0 && dz == 0;
    }

    // 手本 → 写し
    public BlockPos at(BlockPos template) {
        return isZero() ? template : template.offset(dx, dy, dz);
    }

    public List<BlockPos> at(Collection<BlockPos> template) {
        if (isZero()) {
            return List.copyOf(template);
        }
        List<BlockPos> out = new ArrayList<>(template.size());
        for (BlockPos p : template) {
            out.add(p.offset(dx, dy, dz));
        }
        return out;
    }

    public AABB box(AABB template) {
        return isZero() ? template : template.move(dx, dy, dz);
    }

    public Vec3 world(Vec3 template) {
        return isZero() ? template : template.add(dx, dy, dz);
    }

    // 写し → 手本
    public BlockPos local(BlockPos world) {
        return isZero() ? world : world.offset(-dx, -dy, -dz);
    }

    public Vec3 local(Vec3 world) {
        return isZero() ? world : world.subtract(dx, dy, dz);
    }

    // ---------------------------------------------------------------- いま動いているずれ (セルの決まりを呼ぶあいだだけ。サーバーの tick の中だけ)
    private static TrialFrame current = ZERO;

    public static TrialFrame current() {
        return current;
    }

    // ずれを置いて action を動かす (入れ子は外側に戻す)
    public static void with(@Nullable TrialFrame frame, Runnable action) {
        TrialFrame before = current;
        current = frame == null ? ZERO : frame;
        try {
            action.run();
        } finally {
            current = before;
        }
    }

    // いまのずれで手本の座標を写しの座標へ (GroupGates・TrialFx が使う)
    public static BlockPos shift(BlockPos template) {
        return current.at(template);
    }

    public static List<BlockPos> shift(Collection<BlockPos> template) {
        return current.at(template);
    }
}

package jp.revati.skyabyss.rebirth;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

// 転生した人の見せ場 (サーバー側): C13 倒れ・立ち上がりの光 (パケットを送り、描くのは client)
public final class RebirthFx {
    public static final String FLAIR_TAG = "skyabyss_grave_flair";

    private RebirthFx() {
    }

    // ---------------------------------------------------------------- C13 倒れと回収の光
    // rise = false: 倒れた、true: 立ち上がった。転生 0 の人には何もしない。描くのは受け取った client (1 秒まで。設定で切れる)
    public static void burst(ServerPlayer p, boolean rise) {
        if (RebirthView.count(p) > 0) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new RebirthNet.BurstPayload(p.getId(), rise));
        }
    }

    // ---------------------------------------------------------------- 前の版の墓の飾り
    // C12 墓の飾りは 2026-11-05 に外した (墓をもう作らない)。前の版の墓に残った札は、墓が消えるときに消す
    // 墓がなくなった (GraveBlock の onRemove から)
    public static void graveRemoved(ServerLevel level, BlockPos pos) {
        for (Display.TextDisplay d : labels(level, pos)) {
            d.discard();
        }
    }

    static List<Display.TextDisplay> labels(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(pos).inflate(0.6, 1.6, 0.6), d -> d.getTags().contains(FLAIR_TAG));
    }
}

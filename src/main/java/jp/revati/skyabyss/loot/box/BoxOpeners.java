package jp.revati.skyabyss.loot.box;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantRole;
import jp.revati.skyabyss.facility.Attendants;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.world.continent.ContinentTowns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 封切り師を都と四つの土地の町に立たせる (docs/design/52 の 5 章)。設計図に印を足すと町の建て直しになるので、
// 鍛冶屋の係の人の印 (npc_smith) のそばで、そこから歩いて行ける空いた床 (2〜4 ブロック) を探して立たせる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BoxOpeners {
    static final int CHECK_INTERVAL = 100;
    static final int KEEP_RADIUS = 96;
    static final String ANCHOR = AttendantRole.SMITH.mark();

    private BoxOpeners() {
    }

    // 立たせる所の元 (都と、建った土地の町の鍛冶屋の印)
    public static List<BlockPos> anchors(net.minecraft.server.MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos capital = OpeningSites.shelterExtras(server, ANCHOR).get(ANCHOR);
        if (capital != null) {
            out.add(capital);
        }
        for (SiteData.Entry e : SiteData.get(server.overworld()).entries(ContinentTowns.KIND)) {
            BlockPos at = e.extra(ANCHOR);
            if (e.built && at != null) {
                out.add(at);
            }
        }
        return out;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0x3F) + 61) % CHECK_INTERVAL != 0) {
            return;
        }
        for (BlockPos anchor : anchors(player.server)) {
            if (anchor.distSqr(player.blockPosition()) > (double) KEEP_RADIUS * KEEP_RADIUS || !level.isLoaded(anchor)) {
                continue;
            }
            BlockPos spot = spotNear(level, anchor);
            if (spot != null && Attendants.find(level, AttendantRole.BOX_OPENER, spot) == null) {
                // 前に立った人が近くにいれば (床の形が少し変わって別の所が選ばれても) 二人目は立たせない
                if (!level.getEntities(jp.revati.skyabyss.facility.FacilitySetup.ATTENDANT.get(), new net.minecraft.world.phys.AABB(anchor).inflate(8),
                        e -> e.isAlive() && AttendantRole.BOX_OPENER.id.equals(e.roleId())).isEmpty()) {
                    continue;
                }
            }
            if (spot != null) {
                Attendants.ensureAll(level, java.util.Map.of(AttendantRole.BOX_OPENER.mark(), spot));
            }
        }
    }

    // 印のそばの空いた床。近い順に (同じ高さを先に) 見て、印からまっすぐ歩いて行ける所
    @Nullable
    public static BlockPos spotNear(ServerLevel level, BlockPos anchor) {
        for (int r = 2; r <= 4; r++) {
            for (int dy : new int[] {0, -1, 1}) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos p = anchor.offset(dx, dy, dz);
                        if (standable(level, p) && clearLine(level, anchor, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    static boolean standable(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return open(level, p) && open(level, p.above()) && below.isFaceSturdy(level, p.below(), Direction.UP);
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    // 印から足もとの高さで、壁や台を越えずに行けるか (間を 8 つに分けて見る)
    private static boolean clearLine(ServerLevel level, BlockPos a, BlockPos b) {
        for (int i = 1; i < 8; i++) {
            double t = i / 8.0;
            BlockPos q = BlockPos.containing(a.getX() + 0.5 + (b.getX() - a.getX()) * t, a.getY() + 0.5, a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t);
            if (!open(level, q) || !open(level, q.above())) {
                return false;
            }
        }
        return true;
    }
}

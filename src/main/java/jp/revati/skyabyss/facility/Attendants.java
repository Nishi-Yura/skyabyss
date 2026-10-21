package jp.revati.skyabyss.facility;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 係の人を、都の設計図の印 npc_<役> に立たせ、居続けさせる (docs/design/35 の 2・6 章)。
//   建てたとき: ShelterBuilder.finish が印を場所の付属の点 (SiteData.Entry.extra) に写し、ここで立たせる (印がなければ何もしない)
//   そのあと: 都の近く (160 ブロック以内) にいる人がいれば 5 秒ごとに見回り、いなければ立たせ直し、離れていれば戻す
// 一つの役は都に一人 (印のまわり 24 ブロックに同じ役がいれば二人目は立たせない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Attendants {
    // 宿の寝台の印 (InnService)。番号付き (inn_bed_2 …) も読む
    public static final String INN_BED = "inn_bed";
    static final int KEEP_RADIUS = 160;
    static final int CHECK_INTERVAL = 100;
    static final double SAME_ROLE = 24;
    static final double DRIFT = 4;

    private Attendants() {
    }

    // 設計図の印のうち、この仕組みが読む印か (npc_<役>・inn_bed*)
    public static boolean ours(String mark) {
        return AttendantRole.byMark(mark) != null || mark.startsWith(INN_BED);
    }

    // 印 spot に役 role の係の人がいるようにする。立たせた・戻したときは、その人を返す
    @Nullable
    public static AttendantEntity ensure(ServerLevel level, AttendantRole role, BlockPos spot) {
        AttendantEntity found = find(level, role, spot);
        if (found != null) {
            if (found.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > DRIFT * DRIFT || Math.abs(found.getY() - spot.getY()) > 1.5) {
                found.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                found.setHome(spot);
            }
            return found;
        }
        AttendantEntity npc = FacilitySetup.ATTENDANT.get().create(level);
        if (npc == null) {
            return null;
        }
        npc.setRole(role);
        npc.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        npc.setHome(spot);
        level.addFreshEntity(npc);
        SkyAbyss.LOGGER.info("[facility] {} placed at {}", role.id, spot);
        return npc;
    }

    @Nullable
    public static AttendantEntity find(ServerLevel level, AttendantRole role, BlockPos spot) {
        List<AttendantEntity> list = level.getEntities(FacilitySetup.ATTENDANT.get(), new AABB(spot).inflate(SAME_ROLE),
                e -> e.isAlive() && role.id.equals(e.roleId()));
        return list.isEmpty() ? null : list.get(0);
    }

    // 印の一覧 (名前 → 位置) から、役の印ごとに立たせる。立たせた (いる) 人数
    public static int ensureAll(ServerLevel level, Map<String, BlockPos> marks) {
        int n = 0;
        for (Map.Entry<String, BlockPos> e : marks.entrySet()) {
            AttendantRole role = AttendantRole.byMark(e.getKey());
            if (role != null && loaded(level, e.getValue()) && ensure(level, role, e.getValue()) != null) {
                n++;
            }
        }
        return n;
    }

    // 印のまわり 3x3 チャンクの生き物が読み込まれているか (読み込み前に「いない」と見て二人目を立たせないため)
    static boolean loaded(ServerLevel level, BlockPos at) {
        ChunkPos c = new ChunkPos(at);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(c.x + dx, c.z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0x3F)) % CHECK_INTERVAL != 0) {
            return;
        }
        BlockPos capital = OpeningSites.shelter(player.server).orElse(null);
        if (capital == null || capital.distSqr(player.blockPosition()) > (double) KEEP_RADIUS * KEEP_RADIUS) {
            return;
        }
        ensureAll(level, OpeningSites.shelterExtras(player.server, AttendantRole.MARK_PREFIX));
    }
}

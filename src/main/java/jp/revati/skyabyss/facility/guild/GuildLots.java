package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

// ギルドの家 = 都の空き区画 (lot_1..5) を一つ借りる (docs/design/35 の 6.5)。一つの区画に一つのギルド、一つのギルドに一つの区画。
//   借りるのは長 (鐘銭 RENT_COINS。ギルドがある間ずっと。手放すと空く。返金はなし。解散でも空く)
//   借りた区画の中で壊す・置くのは、そのギルドの人だけ (クリエイティブの OP は別)。借りていない区画は、今までどおり誰でも建てられる
//   区画の範囲は設計図 (tools/capital/town.py の Town.lot) の座標の写し LOTS。碑の印 sign_lot_<n> と合っていることを自己テストで見る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GuildLots {
    private GuildLots() {
    }

    public static final int RENT_COINS = 150;

    // 設計図の座標の区画 (x0, z0, x1, z1。両端を含む。town.py の lot=(...) と同じ)
    public record Lot(String id, int x0, int z0, int x1, int z1) {
        // 碑の印の位置 (Town.lot の tablet: (mx - 1, 0, z1 + 1))
        public int[] sign() {
            int mx = Math.floorDiv(x0 + x1, 2);
            return new int[] {mx - 1, 0, z1 + 1};
        }
    }

    public static final List<Lot> LOTS = List.of(
            new Lot("lot_1", -80, -80, -64, -64),
            new Lot("lot_2", 64, -80, 80, -64),
            new Lot("lot_3", -80, 76, -64, 92),
            new Lot("lot_4", 22, 75, 36, 89),
            new Lot("lot_5", 64, 76, 80, 92));

    // 世界の中の範囲 (y は地面の下 8 から上 48)
    public record Area(String id, int x0, int z0, int x1, int z1, int y0, int y1) {
        public boolean contains(BlockPos p) {
            return p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1 && p.getY() >= y0 && p.getY() <= y1;
        }

        public BlockPos center() {
            return new BlockPos((x0 + x1) / 2, y0 + 8, (z0 + z1) / 2);
        }
    }

    private static List<Area> cache = List.of();
    private static long cacheKey = Long.MIN_VALUE;

    // 都の区画の世界の範囲 (都がまだない・設計図が読めなければ空)
    public static List<Area> areas(MinecraftServer server) {
        SiteData.Entry e = SiteData.get(server.overworld()).first("shelter");
        if (e == null || !e.built) {
            return List.of();
        }
        long key = e.pos.asLong() * 31 + e.yaw;
        if (key == cacheKey) {
            return cache;
        }
        Blueprint bp = Blueprint.get("capital");
        int rot = SiteFinder.rot(e.yaw);
        boolean mirror = SiteFinder.mirror(e.yaw);
        BlockPos origin = origin(e, bp, rot, mirror);
        if (origin == null) {
            return List.of();
        }
        List<Area> out = new ArrayList<>();
        for (Lot l : LOTS) {
            BlockPos a = Blueprint.transform(l.x0(), 0, l.z0(), rot, mirror, origin);
            BlockPos b = Blueprint.transform(l.x1(), 0, l.z1(), rot, mirror, origin);
            out.add(new Area(l.id(), Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
                    Math.max(a.getZ(), b.getZ()), origin.getY() - 8, origin.getY() + 48));
        }
        cache = List.copyOf(out);
        cacheKey = key;
        return cache;
    }

    // 建てたときの設計図の原点 (SiteJob.mark は transform(x, y + 1, z, …, origin))。写した付属の点の一つから逆に求める
    @Nullable
    static BlockPos origin(SiteData.Entry e, @Nullable Blueprint bp, int rot, boolean mirror) {
        if (bp == null) {
            return null;
        }
        for (int i = 0; i < e.extraNames.size(); i++) {
            int[] m = bp.marks.get(e.extraNames.get(i));
            if (m == null) {
                continue;
            }
            BlockPos rel = Blueprint.transform(m[0], m[1] + 1, m[2], rot, mirror, BlockPos.ZERO);
            return e.extraPos.get(i).subtract(rel);
        }
        return null;
    }

    @Nullable
    public static Area areaOf(MinecraftServer server, String lot) {
        for (Area a : areas(server)) {
            if (a.id().equals(lot)) {
                return a;
            }
        }
        return null;
    }

    // その区画を借りているギルド (なければ null)
    @Nullable
    public static GuildData.Guild holder(MinecraftServer server, String lot) {
        for (GuildData.Guild g : GuildData.get(server).all()) {
            if (lot.equals(g.lot)) {
                return g;
            }
        }
        return null;
    }

    // 長が区画を借りる
    public static Guilds.Result rent(ServerPlayer leader, String lot) {
        GuildData.Guild g = Guilds.of(leader);
        if (g == null) {
            return Guilds.Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Guilds.Result.NOT_LEADER;
        }
        if (!g.lot.isEmpty()) {
            return Guilds.Result.HAS_LOT;
        }
        if (LOTS.stream().noneMatch(l -> l.id().equals(lot))) {
            return Guilds.Result.NO_LOT;
        }
        if (holder(leader.server, lot) != null) {
            return Guilds.Result.LOT_TAKEN;
        }
        if (!Coins.take(leader, RENT_COINS)) {
            return Guilds.Result.NO_COINS;
        }
        g.lot = lot;
        GuildData.get(leader.server).dirty();
        SkyAbyss.LOGGER.info("[guild] {} rented {}", g.name, lot);
        return Guilds.Result.RENTED;
    }

    // 長が区画を手放す
    public static Guilds.Result release(ServerPlayer leader) {
        GuildData.Guild g = Guilds.of(leader);
        if (g == null) {
            return Guilds.Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Guilds.Result.NOT_LEADER;
        }
        if (g.lot.isEmpty()) {
            return Guilds.Result.NO_LOT;
        }
        g.lot = "";
        GuildData.get(leader.server).dirty();
        return Guilds.Result.RELEASED;
    }

    // その場所で建ててよいか (借りた区画の中は、そのギルドの人だけ)
    public static boolean mayBuild(MinecraftServer server, Player player, BlockPos pos) {
        if (player.isCreative() && player.hasPermissions(2)) {
            return true;
        }
        for (Area a : areas(server)) {
            if (!a.contains(pos)) {
                continue;
            }
            GuildData.Guild g = holder(server, a.id());
            return g == null || g.members.contains(player.getUUID());
        }
        return true;
    }

    private static boolean guarded(LevelAccessor level, @Nullable Player player, BlockPos pos) {
        if (!(level instanceof Level l) || l.isClientSide || l.dimension() != Level.OVERWORLD || player == null || l.getServer() == null) {
            return false;
        }
        return !mayBuild(l.getServer(), player, pos);
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (guarded(event.getLevel(), event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
            Notice.hint(event.getPlayer(), "message.skyabyss.guild.lot_guarded");
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && guarded(event.getLevel(), p, event.getPos())) {
            event.setCanceled(true);
            Notice.hint(p, "message.skyabyss.guild.lot_guarded");
        }
    }
}

package jp.revati.skyabyss.skin;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantRole;
import jp.revati.skyabyss.facility.Attendants;
import jp.revati.skyabyss.loot.box.BoxOpeners;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.world.continent.ContinentTowns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 仕立て屋 (スキンの店。docs/design/54 の 6.1.1) の立つ所。設計図の印は持たず、武具屋の係の人のそばの空いた床に立つ
// (封切り師 loot/box/BoxOpeners と同じ決め方。都と、建った土地の町の武具屋)。近く (KEEP_RADIUS) に人がいるあいだ立たせ続ける
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TailorStand {
    static final int CHECK_INTERVAL = 100;
    static final int KEEP_RADIUS = 96;
    static final String ANCHOR = AttendantRole.OUTFITTER.mark();

    private TailorStand() {
    }

    public static List<BlockPos> anchors(MinecraftServer server) {
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
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0x3F) + 83) % CHECK_INTERVAL != 0) {
            return;
        }
        for (BlockPos anchor : anchors(player.server)) {
            if (anchor.distSqr(player.blockPosition()) > (double) KEEP_RADIUS * KEEP_RADIUS || !level.isLoaded(anchor)) {
                continue;
            }
            stand(level, anchor);
        }
    }

    // 武具屋の印のそばに立たせる (もういれば何もしない)。立った所を返す
    public static BlockPos stand(ServerLevel level, BlockPos anchor) {
        BlockPos spot = BoxOpeners.spotNear(level, anchor);
        if (spot == null) {
            return null;
        }
        if (Attendants.find(level, AttendantRole.TAILOR, spot) == null && !level.getEntities(jp.revati.skyabyss.facility.FacilitySetup.ATTENDANT.get(),
                new net.minecraft.world.phys.AABB(anchor).inflate(8), e -> e.isAlive() && AttendantRole.TAILOR.id.equals(e.roleId())).isEmpty()) {
            return spot;
        }
        Attendants.ensureAll(level, java.util.Map.of(AttendantRole.TAILOR.mark(), spot));
        return spot;
    }
}

package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 倒れたら近い休み場へ (docs/design/40 の 2 章「倒れた罰は休み場へ戻るだけ」)。天と淵の大陸の地上で倒れ、寝台などの戻り場所を持っていない人は、
// 倒れた所にいちばん近い休み場 (土地の町の井戸の脇・都・野営地。建ってあるもの) で目覚める。背・深淵で倒れたときは、それぞれの仕組みのまま
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentRespawn {
    private ContinentRespawn() {
    }

    // 地上の休み場 (立つ場所) の一覧
    public static List<BlockPos> rests(ServerLevel over) {
        SiteData data = SiteData.get(over);
        List<BlockPos> out = new ArrayList<>();
        for (SiteData.Entry e : data.entries(ContinentTowns.KIND)) {
            BlockPos r = e.extra("rest");
            if (e.built && r != null) {
                out.add(r);
            }
        }
        for (String kind : new String[] {"shelter"}) {
            for (SiteData.Entry e : data.entries(kind)) {
                if (e.built) {
                    BlockPos stand = e.extra("stand");
                    out.add(stand != null ? stand : e.pos);
                }
            }
        }
        return out;
    }

    // はじまりの島の休み場 (野営地と、道ぞいの祠)
    public static List<BlockPos> islandRests(ServerLevel over) {
        SiteData data = SiteData.get(over);
        List<BlockPos> out = new ArrayList<>();
        for (SiteData.Entry e : data.entries("camp")) {
            if (e.built) {
                BlockPos stand = e.extra("stand");
                out.add(stand != null ? stand : e.pos);
            }
        }
        for (SiteData.Entry e : data.entries(ContinentOutskirts.KIND)) {
            if (e.built && e.index == 1) {
                out.add(e.pos);
            }
        }
        return out;
    }

    @Nullable
    public static BlockPos nearest(List<BlockPos> rests, BlockPos at) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos r : rests) {
            double d = r.distSqr(at);
            if (d < bd) {
                bd = d;
                best = r;
            }
        }
        return best;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered() || player.getRespawnPosition() != null
                || player.level().dimension() != Level.OVERWORLD || !(player.level() instanceof ServerLevel over)) {
            return;
        }
        GlobalPos death = player.getLastDeathLocation().orElse(null);
        if (death == null || death.dimension() != Level.OVERWORLD) {
            return;
        }
        // 島を出た人は、島の休み場 (野営地・道ぞいの祠) では目覚めない。まだの人は島の中だけ
        List<BlockPos> rests = ContinentOutskirts.ferried(player) ? rests(over) : islandRests(over);
        BlockPos at = nearest(rests, death.pos());
        if (at != null) {
            player.teleportTo(over, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
    }
}

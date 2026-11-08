package jp.revati.skyabyss.world.continent;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 内海の渡し船 (docs/design/40 の 3 章。地図の渡し船の線)。layout.json の ferry_docks (渡し船ごとに [都の側, 向こう岸]) に船着き場を建て、
// 船着き場の渡し守 (town/RegionFolk の inner_ferryman) と話して「渡る」を選ぶと、もう一方の船着き場へ渡る (行き来は何度でも。
// 2026-10-27 の声「渡し船で内海わたるとき、NPC 設置してそこから行けるように」。前は桟橋の先に立つと渡った)。部品ははじまりの島と同じ outskirts_dock (向こう岸は向きを返す)。
// SiteData の種類は "ferry"、番号 = 渡し船の番号 × 2 + (0 = 手前の岸・1 = 向こう岸)。渡し船 0 = 内海、1 = 北西の小島、2 から先 = 四つの大陸のあいだの海 (signLine・arrivalName)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentFerries {
    public static final String KIND = "ferry";
    static final String ARRIVED = "skyabyss_ferry_at";

    private ContinentFerries() {
    }

    // 渡し船ごとの名前 (番号 = 渡し船の番号 × 2 + 岸)。0 = 内海 (都の側 ↔ 汐守)、1 = 北西の小島 (風待の北の岸 ↔ 霜の小島。2026-11-06 の外れの道)、
    // 2 から先 = 四つの大陸のあいだの海 (2026-11-08。道が海で切れた所。gen_continent.split_roads_at_sea)。
    // 行き先の名は layout.json の ferry_names (その岸のいちばん近い都・町・外れの集落)。古い図で無ければ前の決まった名
    static Component signLine(int index) {
        boolean far = (index & 1) == 1;
        String to = ContinentLayout.get().ferryName(index >> 1, far ? 0 : 1);
        if (to != null) {
            return Component.translatable("sign.skyabyss.ferry.to", Component.translatable(to));
        }
        return Component.translatable(oldSignKey(index) + ".2");
    }

    static Component signTitle(int index) {
        return Component.translatable((index >> 1) == 1 ? "sign.skyabyss.ferry.to_isle.1" : "sign.skyabyss.ferry.to_tide.1");
    }

    private static String oldSignKey(int index) {
        boolean far = (index & 1) == 1;
        return switch (index >> 1) {
            case 1 -> far ? "sign.skyabyss.ferry.to_shore" : "sign.skyabyss.ferry.to_isle";
            default -> far ? "sign.skyabyss.ferry.to_capital" : "sign.skyabyss.ferry.to_tide";
        };
    }

    // index の岸から渡って着いた先の名前
    static String arrivalName(int fromIndex) {
        boolean fromFar = (fromIndex & 1) == 1;
        String to = ContinentLayout.get().ferryName(fromIndex >> 1, fromFar ? 0 : 1);
        if (to != null) {
            return to;
        }
        return switch (fromIndex >> 1) {
            case 1 -> fromFar ? "map.skyabyss.frontier.storm_shore" : "map.skyabyss.frontier.frost_isle";
            default -> fromFar ? "signpost.skyabyss.capital" : "map.skyabyss.town.tide";
        };
    }

    static String crossedKey(int fromIndex) {
        return switch (fromIndex >> 1) {
            case 0 -> "message.skyabyss.ferry.crossed";
            case 1 -> "message.skyabyss.ferry.crossed_strait";
            default -> "message.skyabyss.ferry.crossed_sea";
        };
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        boolean far = (entry.index & 1) == 1;
        return new SiteJob(level, data, entry, Blueprint.get("outskirts_dock"), "ferry " + entry.index, job -> {
            BlockPos ferry = job.mark("ferry");
            BlockPos stand = job.mark("stand");
            if (stand != null) {
                job.entry.extra("stand", stand);
            }
            if (ferry != null) {
                // 桟橋を水の上まで延ばし、その先を渡し船に乗る所にする
                job.entry.extra("ferry", stand == null ? ferry : ContinentPiers.extend(level, ferry, stand));
            }
            BlockPos sign = job.mark("sign");
            if (sign != null && level.getBlockEntity(sign) instanceof SignBlockEntity be) {
                be.setText(new SignText().setMessage(1, signTitle(entry.index)).setMessage(2, signLine(entry.index)), true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }

    // 近くに人がいれば、船着き場に渡し守を立たせ続ける (2 秒ごと)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || p.level().dimension() != Level.OVERWORLD
                || (p.tickCount + p.getId()) % 40 != 0) {
            return;
        }
        ServerLevel over = p.serverLevel();
        for (SiteData.Entry e : SiteData.get(over).entries(KIND)) {
            BlockPos stand = e.extra("stand");
            if (e.built && stand != null && stand.distSqr(p.blockPosition()) < 80 * 80) {
                // 一つめ (内海) は内海の渡し守、二つめ (霜の小島) は霜の渡し守
                jp.revati.skyabyss.town.RegionFolk.ensure(over, (e.index >> 1) == 1 ? jp.revati.skyabyss.town.RegionFolk.ISLE_FERRY : "ferry", "dock", stand);
            }
        }
    }

    // 渡し守の会話の「渡る」(town の act inner_cross): いちばん近い船着き場から、もう一方へ
    public static boolean cross(ServerPlayer p) {
        ServerLevel over = p.serverLevel();
        List<SiteData.Entry> docks = SiteData.get(over).entries(KIND);
        SiteData.Entry from = null;
        double best = 32 * 32;
        for (SiteData.Entry e : docks) {
            BlockPos at = e.extra("stand") != null ? e.extra("stand") : e.pos;
            double d = at.distSqr(p.blockPosition());
            if (e.built && d < best) {
                best = d;
                from = e;
            }
        }
        if (from == null) {
            return false;
        }
        int other = from.index ^ 1;
        for (SiteData.Entry o : docks) {
            if (o.index == other && o.built) {
                BlockPos to = o.extra("stand") != null ? o.extra("stand") : o.pos;
                p.getPersistentData().putLong(ARRIVED, over.getGameTime());
                p.teleportTo(over, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
                jp.revati.skyabyss.notice.Notice.place(p, Component.translatable(arrivalName(from.index)), Component.translatable(crossedKey(from.index)));
                over.playSound(null, to, SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 0.8f, 1f);
                return true;
            }
        }
        return false;
    }
}

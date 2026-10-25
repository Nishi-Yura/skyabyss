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
// 桟橋の先に立つと、もう一方の船着き場へ渡る (行き来は何度でも)。部品ははじまりの島と同じ outskirts_dock (向こう岸は向きを返す)。
// SiteData の種類は "ferry"、番号 = 渡し船の番号 × 2 + (0 = 都の側・1 = 向こう岸)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentFerries {
    public static final String KIND = "ferry";
    static final double RADIUS = 3.5;
    static final String ARRIVED = "skyabyss_ferry_at";

    private ContinentFerries() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        boolean far = (entry.index & 1) == 1;
        return new SiteJob(level, data, entry, Blueprint.get("outskirts_dock"), "ferry " + entry.index, job -> {
            for (String m : new String[] {"ferry", "stand"}) {
                BlockPos at = job.mark(m);
                if (at != null) {
                    job.entry.extra(m, at);
                }
            }
            BlockPos sign = job.mark("sign");
            if (sign != null && level.getBlockEntity(sign) instanceof SignBlockEntity be) {
                String key = far ? "sign.skyabyss.ferry.to_capital" : "sign.skyabyss.ferry.to_tide";
                be.setText(new SignText().setMessage(1, Component.translatable(key + ".1")).setMessage(2, Component.translatable(key + ".2")), true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || p.level().dimension() != Level.OVERWORLD
                || (p.tickCount + p.getId()) % 10 != 0) {
            return;
        }
        ServerLevel over = p.serverLevel();
        long now = over.getGameTime();
        long last = p.getPersistentData().getLong(ARRIVED);
        if (now - last < 100 && now >= last) {
            return;
        }
        List<SiteData.Entry> docks = SiteData.get(over).entries(KIND);
        for (SiteData.Entry e : docks) {
            BlockPos f = e.extra("ferry");
            if (!e.built || f == null || Math.hypot(p.getX() - (f.getX() + 0.5), p.getZ() - (f.getZ() + 0.5)) > RADIUS || Math.abs(p.getY() - f.getY()) >= 4) {
                continue;
            }
            int other = e.index ^ 1;
            for (SiteData.Entry o : docks) {
                if (o.index == other && o.built) {
                    BlockPos to = o.extra("stand") != null ? o.extra("stand") : o.pos;
                    p.getPersistentData().putLong(ARRIVED, now);
                    p.teleportTo(over, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
                    jp.revati.skyabyss.notice.Notice.place(p, Component.translatable((e.index & 1) == 0 ? "map.skyabyss.town.tide" : "signpost.skyabyss.capital"),
                            Component.translatable("message.skyabyss.ferry.crossed"));
                    over.playSound(null, to, SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 0.8f, 1f);
                    return;
                }
            }
        }
    }
}

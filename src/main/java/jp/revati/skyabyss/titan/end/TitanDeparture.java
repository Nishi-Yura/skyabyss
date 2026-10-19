package jp.revati.skyabyss.titan.end;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

// 眠った巨神が地上から去る最後 (docs/design/02 の 7.2節、TitanDormancy.DEPART_TICKS)。各巨神の本体が、眠る姿が始まって 40 秒で呼ぶ。
// 本体を取り除き (岩鯨は雲の海へ・潮殻は海の底へ・雷翼は空の向こうへ・余燼は土の底へ)、近くの人に一行。
// 取り除いたあとは、眠っているあいだ見守りが呼び直さない (TitanKeeper)。目覚めると、見守りが遠くから呼び直す
public final class TitanDeparture {
    // 去った知らせが届く距離 (水平)
    public static final double TELL_RANGE = 480;

    private TitanDeparture() {
    }

    // 本体を地上から取り除く (サーバー側・オーバーワールドの本体だけ)。戻りは取り除いたか
    public static boolean finish(Entity titan, String id) {
        if (!(titan.level() instanceof ServerLevel level) || titan.isRemoved() || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        ParticleOptions puff = switch (id) {
            case Titans.TIDE_SHELL -> ParticleTypes.SPLASH;
            case Titans.THUNDER_WING -> ParticleTypes.ELECTRIC_SPARK;
            case Titans.EMBER -> ParticleTypes.LARGE_SMOKE;
            default -> ParticleTypes.CLOUD;
        };
        double x = titan.getX();
        double y = titan.getY() + titan.getBbHeight() * 0.5;
        double z = titan.getZ();
        for (ServerPlayer p : jp.revati.skyabyss.util.Players.snapshot(level.players())) {
            if (p.distanceToSqr(x, p.getY(), z) < TELL_RANGE * TELL_RANGE) {
                level.sendParticles(p, puff, true, x, y, z, 80, 14, 4, 14, 0.15);
                if (!p.isSpectator()) {
                    Notice.narrate(p, "message.skyabyss.titan_end.departed." + id);
                }
            }
        }
        level.playSound(null, x, y, z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.AMBIENT, 16f, 0.5f);
        SkyAbyss.LOGGER.info("The {} left the overworld at {} {} {} (asleep for {} ticks)", id, (int) x, (int) y, (int) z,
                TitanDormancy.age(level.getServer(), id));
        titan.discard();
        return true;
    }
}

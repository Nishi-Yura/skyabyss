package jp.revati.skyabyss.trial;

import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

// セルの演出の小道具: 粒・音・一行を、そのグループの人にだけ送る
public final class TrialFx {
    private TrialFx() {
    }

    public static void particles(ServerLevel level, UUID group, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
            double dz, double speed) {
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(x, y, z) < 64 * 64) {
                level.sendParticles(p, particle, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    public static void sound(ServerLevel level, UUID group, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        Groups.sound(level, group, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static void bar(List<ServerPlayer> players, Component text) {
        for (ServerPlayer p : players) {
            Notice.bar(p, text);
        }
    }
}

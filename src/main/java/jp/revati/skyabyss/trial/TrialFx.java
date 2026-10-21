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
        // 区画の写しのセルの決まりの中 (TrialFrame): 距離は手本の座標で比べ、粒は写しの座標に出す
        net.minecraft.world.phys.Vec3 at = TrialFrame.current().world(new net.minecraft.world.phys.Vec3(x, y, z));
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(x, y, z) < 64 * 64) {
                level.sendParticles(p, particle, false, at.x, at.y, at.z, count, dx, dy, dz, speed);
            }
        }
    }

    public static void sound(ServerLevel level, UUID group, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        Groups.sound(level, group, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // 世界に鳴らす音 (level.playSound(null, …) の置き換え)。区画の写しのセルの決まりの中 (TrialFrame) では、
    // 人は手本の座標にいるので、手本の座標の近くの人へ、写しの座標の音を送る。外ではそのまま
    public static void worldSound(ServerLevel level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch) {
        TrialFrame f = TrialFrame.current();
        if (f.isZero()) {
            level.playSound(null, x, y, z, sound, source, volume, pitch);
            return;
        }
        net.minecraft.world.phys.Vec3 at = f.world(new net.minecraft.world.phys.Vec3(x, y, z));
        double range = Math.max(16.0, volume * 16.0);
        var packet = new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                source, at.x, at.y, at.z, volume, pitch, level.random.nextLong());
        for (ServerPlayer p : jp.revati.skyabyss.util.Players.snapshot(level.players())) {
            if (p.distanceToSqr(x, y, z) < range * range && p.connection != null) {
                p.connection.send(packet);
            }
        }
    }

    public static void worldSound(ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        worldSound(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, source, volume, pitch);
    }

    public static void worldSound(ServerLevel level, double x, double y, double z, net.minecraft.core.Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        worldSound(level, x, y, z, sound.value(), source, volume, pitch);
    }

    public static void worldSound(ServerLevel level, BlockPos pos, net.minecraft.core.Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        worldSound(level, pos, sound.value(), source, volume, pitch);
    }

    public static void bar(List<ServerPlayer> players, Component text) {
        for (ServerPlayer p : players) {
            Notice.bar(p, text);
        }
    }
}

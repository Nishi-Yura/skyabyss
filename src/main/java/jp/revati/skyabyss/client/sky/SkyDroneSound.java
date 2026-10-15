package jp.revati.skyabyss.client.sky;

import jp.revati.skyabyss.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

// 通過中ずっと鳴り続ける低い唸り。近づくほど大きく、去ると消えていく
final class SkyDroneSound extends AbstractTickableSoundInstance {
    SkyDroneSound() {
        super(ModSounds.SKY_DRONE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        float target = SkyPassClient.droneVolume();
        this.volume += (target - this.volume) * 0.05f;
        if (!SkyPassClient.isPassing() && this.volume < 0.01f) {
            stop();
        }
    }
}

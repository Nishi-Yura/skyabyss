package jp.revati.skyabyss.client.sky;

import jp.revati.skyabyss.sky.ChapterSkyRegistry;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// 雲の目が開いている間の、雲の上の深い気配 (ループ)。
// 目のある方向から聞こえるが、距離では小さくならない (遠いのに近い、という違和感)
final class CloudEyeSound extends AbstractTickableSoundInstance {
    CloudEyeSound(Vec3 pos) {
        super(ChapterSkyRegistry.EYE_PRESENCE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        this.volume = Math.max(0.0001f, CloudEyeClient.volume());
        if (!CloudEyeClient.soundWanted()) {
            stop();
        }
    }
}

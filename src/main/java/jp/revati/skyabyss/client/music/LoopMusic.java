package jp.revati.skyabyss.client.music;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

// ループする BGM の一曲。音量は tick ごとに目標へ寄せる (入り・抜けのフェード)。消えきったら止まる
public final class LoopMusic extends AbstractTickableSoundInstance {
    final String id;
    private float target;
    private float step;

    LoopMusic(String id, SoundEvent event) {
        super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.id = id;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    void fadeTo(float to, int ticks) {
        target = to;
        step = Math.max(0.001f, Math.abs(to - volume) / Math.max(1, ticks));
    }

    boolean fadingOut() {
        return target <= 0f;
    }

    @Override
    public void tick() {
        if (volume < target) {
            volume = Math.min(target, volume + step);
        } else if (volume > target) {
            volume = Math.max(target, volume - step);
        }
        if (target <= 0f && volume <= 0.001f) {
            stop();
        }
    }

    // 音量 0 で始めても鳴らす (フェードで入るため)
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return true;
    }
}

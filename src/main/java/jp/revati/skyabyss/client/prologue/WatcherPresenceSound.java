package jp.revati.skyabyss.client.prologue;

import jp.revati.skyabyss.prologue.PrologueRegistry;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

// 見る者に気づいている間の、頭の中で鳴るようなかすかな耳鳴り (ループ)。大きさは WatcherClient が決める
final class WatcherPresenceSound extends AbstractTickableSoundInstance {
    // 音量の上限。耳鳴りなので、ほかの音より前に出さない
    private static final float MAX_VOLUME = 0.35f;

    WatcherPresenceSound() {
        super(PrologueRegistry.WATCHER_PRESENCE.get(), SoundSource.AMBIENT, RandomSource.create());
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
        this.volume = Math.max(0.0001f, WatcherClient.presence() * MAX_VOLUME);
        if (!WatcherClient.presenceActive() && WatcherClient.presence() < 0.01f) {
            stop();
        }
    }
}

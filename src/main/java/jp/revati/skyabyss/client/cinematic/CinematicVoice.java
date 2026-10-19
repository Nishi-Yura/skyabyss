package jp.revati.skyabyss.client.cinematic;

import javax.annotation.Nullable;

import jp.revati.skyabyss.cinematic.DialogueLine;
import jp.revati.skyabyss.cinematic.VoiceClips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

// 場面の台詞の「声のようなもの」を、台詞の出る時刻に鳴らす (docs/design/20_cinematics.md の 6 章)。
// 音の種類は VOICE (設定の「声」の音量に従う)。次の台詞の始まり・スキップ・終わり・退出で止め、重ねて鳴らさない。
// 台詞に音がない場面 (あとから足した場面など) は鳴らさない
final class CinematicVoice {
    @Nullable
    private static SoundInstance current;

    private CinematicVoice() {
    }

    // PLAY のあいだ毎 tick。始まる時刻 (台詞の at) に来たら、その台詞の声を一度だけ鳴らす。遅れて (一時停止明けなど) 台詞の頭を過ぎていたら鳴らさない
    static void tick(CinematicClient.Run r) {
        var lines = r.scene.lines();
        while (r.voiceNext < lines.size()) {
            DialogueLine line = lines.get(r.voiceNext);
            int startTick = Math.round(line.at() * 20f);
            if (r.ticks < startTick) {
                return;
            }
            int index = r.voiceNext++;
            if (r.ticks <= startTick + 10) {
                play(r.scene.id(), index);
            }
        }
    }

    private static void play(String scene, int index) {
        Minecraft mc = Minecraft.getInstance();
        stop();
        ResourceLocation id = VoiceClips.eventId(scene, index);
        if (mc.getSoundManager().getSoundEvent(id) == null) {
            return;
        }
        SoundInstance si = new SimpleSoundInstance(id, SoundSource.VOICE, 1.0f, 1.0f, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        current = si;
        mc.getSoundManager().play(si);
    }

    static void stop() {
        SoundInstance si = current;
        current = null;
        if (si != null) {
            Minecraft.getInstance().getSoundManager().stop(si);
        }
    }
}

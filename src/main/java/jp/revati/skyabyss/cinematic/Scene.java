package jp.revati.skyabyss.cinematic;

import java.util.ArrayList;
import java.util.List;

// 名前のついた場面 (サーバーもクライアントも同じ定義を使う。定義は Scenes)。
//   shots      : 撮影を順に (切れ目なく続く。切り替えたいときは前の最後と次の最初の位置を離す)
//   lines      : 台詞 (場面の始まりからの秒)
//   intro      : 開幕の暗転・まぶた・題字 (OpeningOverlay) を重ねる
//   handBack   : 終わりに操作する人の向きを最後のカメラの向きに合わせる (最後のカメラが本人の目の位置なら、切れ目なく操作へ戻る)
//   seamless   : 終わりで暗転を挟まない (最後のカメラが本人の目と同じ位置のとき)
public record Scene(String id, List<Shot> shots, List<DialogueLine> lines, boolean intro, boolean handBack, boolean seamless) {
    public float duration() {
        float t = 0f;
        for (Shot s : shots) {
            t += s.seconds();
        }
        return t;
    }

    public int ticks() {
        return Math.round(duration() * 20f);
    }

    public static Builder of(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final List<Shot> shots = new ArrayList<>();
        private final List<DialogueLine> lines = new ArrayList<>();
        private boolean intro;
        private boolean handBack;
        private boolean seamless;

        private Builder(String id) {
            this.id = id;
        }

        public Builder shot(Shot shot) {
            shots.add(shot);
            return this;
        }

        public Builder line(float at, float duration, Speaker speaker, String textKey) {
            lines.add(new DialogueLine(at, duration, speaker, "", textKey));
            return this;
        }

        public Builder npcLine(float at, float duration, String labelKey, String textKey) {
            lines.add(new DialogueLine(at, duration, Speaker.NPC, labelKey, textKey));
            return this;
        }

        public Builder intro() {
            this.intro = true;
            return this;
        }

        public Builder handBack() {
            this.handBack = true;
            return this;
        }

        public Builder seamless() {
            this.seamless = true;
            return this;
        }

        public Scene build() {
            return new Scene(id, List.copyOf(shots), List.copyOf(lines), intro, handBack, seamless);
        }
    }
}

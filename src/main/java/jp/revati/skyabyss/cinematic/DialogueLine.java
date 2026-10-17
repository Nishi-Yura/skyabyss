package jp.revati.skyabyss.cinematic;

// 場面の台詞: at 秒から duration 秒。label は話し手の名札の翻訳キー (NPC のときだけ。ほかは空)、text は本文の翻訳キー
public record DialogueLine(float at, float duration, Speaker speaker, String label, String text) {
    public float end() {
        return at + duration;
    }
}

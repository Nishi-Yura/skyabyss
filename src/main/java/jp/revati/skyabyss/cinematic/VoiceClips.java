package jp.revati.skyabyss.cinematic;

import net.minecraft.resources.ResourceLocation;

// 場面の台詞の「声のようなもの」の名前 (docs/design/20_cinematics.md の 6 章)。台詞 (場面, 何番目) ごとに一つ、assets の sounds.json に
// 音 "voice.<場面>.l<番号>" があり、実体は sounds/voice/<場面>/l<番号>.ogg。作るのは tools/gen_cinematic_voice.py。
// 台本の定義にない場面 (あとから足す場面) には音がない。クライアントは音があるときだけ鳴らす
public final class VoiceClips {
    public static final String NAMESPACE = "skyabyss_cinematic";

    private VoiceClips() {
    }

    public static String eventName(String scene, int index) {
        return "voice." + scene + ".l" + index;
    }

    public static ResourceLocation eventId(String scene, int index) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, eventName(scene, index));
    }

    // jar の中の音ファイルの場所 (自己テスト用)
    public static String clipResource(String scene, int index) {
        return "/assets/" + NAMESPACE + "/sounds/voice/" + scene + "/l" + index + ".ogg";
    }
}

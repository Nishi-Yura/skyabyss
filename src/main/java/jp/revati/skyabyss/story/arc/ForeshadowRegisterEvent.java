package jp.revati.skyabyss.story.arc;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

// 予兆の拍を登録する口 (モッドバスのイベント)。共通の準備 (FMLCommonSetupEvent) のあとに一度だけ流れる。
// 各巨神の担当は、自分の Setup クラスにこの購読を書けば、共有のファイルを編集せずに拍を足せる (使い方は Foreshadow の頭のコメント)
public final class ForeshadowRegisterEvent extends Event implements IModBusEvent {
    public void register(Beat beat) {
        Foreshadow.register(beat);
    }
}

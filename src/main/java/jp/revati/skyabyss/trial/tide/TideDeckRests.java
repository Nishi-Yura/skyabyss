package jp.revati.skyabyss.trial.tide;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.trial.TrialCell;

// 潮殻の試練の階の休み場を、セルの口 (TrialCell.Ports.rests) の形にする
final class TideDeckRests {
    private TideDeckRests() {
    }

    // 名前が prefix で始まる休み場
    static List<TrialCell.Rest> of(String prefix) {
        List<TrialCell.Rest> out = new ArrayList<>();
        for (TideBackLayout.Rest r : TideDeck.rests()) {
            if (r.id().startsWith(prefix)) {
                TideBackLayout.Area a = r.area();
                out.add(new TrialCell.Rest(r.id(), new TitanBackLayout.Box(a.x1(), a.x2(), a.z1(), a.z2(), a.floor(), a.height()), r.spawn()));
            }
        }
        return List.copyOf(out);
    }
}

package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCell.Ports;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

// セルの登録簿。原型は LegacyCell で包む (口だけ)。新作のセル (29 のプール) は巨神の中を建てなくなったので消した。
// 巨神を増やすときは、ここに register(...) を足す (定義は TrialCatalog)
public final class TrialCells {
    private TrialCells() {
    }

    // 原型の包み (docs/design/29 の 5.1「今の *Builder・*Trials を TrialCell の実装へ薄く包む」)。口の登録だけで、動かすのはもとの仕組み
    public record LegacyCell(Def def, Ports ports) implements TrialCell {
        @Override
        public boolean runtime() {
            return false;
        }
    }

    private static final Map<String, TrialCell> CELLS = new LinkedHashMap<>();
    private static boolean ready;

    public static synchronized void init() {
        if (ready) {
            return;
        }
        ready = true;
        for (Def d : TrialCatalog.all()) {
            if (!d.original()) {
                continue;
            }
            register(new LegacyCell(d, legacyPorts(d)));
        }
        for (Def d : TrialCatalog.all()) {
            // レイドの部屋は区画に建てる (trial/raid)。セルの写しはない
            // 新作のセル (29 のプール) は消した (巨神の中は建てない。docs/design/32 の 2.5)。原型の口だけが残る
        }
    }

    public static void register(TrialCell cell) {
        CELLS.put(cell.def().id(), cell);
    }

    @Nullable
    public static TrialCell get(String id) {
        init();
        return CELLS.get(id);
    }

    public static List<TrialCell> of(String titan) {
        init();
        List<TrialCell> out = new ArrayList<>();
        for (TrialCell c : CELLS.values()) {
            if (c.def().titan().equals(titan)) {
                out.add(c);
            }
        }
        return out;
    }

    public static List<TrialCell> all() {
        init();
        return List.copyOf(CELLS.values());
    }

    // 位置がゴールのセル (新作)。原型のゴールは別 (WhaleGoals などが扱う)
    @Nullable
    public static TrialCell atStone(BlockPos pos) {
        for (TrialCell c : all()) {
            if (c.runtime() && pos.equals(c.ports().stone())) {
                return c;
            }
        }
        return null;
    }

    private static Ports legacyPorts(Def d) {
        return switch (d.id()) {
            case "W01" -> new Ports(WhaleDeck.WEST_GATE, true, List.of(), TitanBackLayout.GOAL_SPOTS.get(0), null, "", empty(), List.of());
            case "W09" -> new Ports(WhaleDeck.EAST_GATE, true, List.of(), TitanBackLayout.GOAL_SPOTS.get(1), null, "", empty(), List.of());
            case "W05" -> new Ports(WhaleDeck.mazeLids(), true, List.of(), TitanBackLayout.GOAL_SPOTS.get(2), null, "", empty(), List.of());
            // 雷翼: 広間の三つの出口 (西の肩の階段・東・首) の戸。引かなかったグループに閉じる
            case "S01" -> new Ports(jp.revati.skyabyss.titan.storm.back.StormDeck.WEST_GATE, true, List.of(), null, null, "", empty(), List.of());
            case "S05" -> new Ports(jp.revati.skyabyss.titan.storm.back.StormDeck.EAST_GATE, true, List.of(), null, null, "", empty(), List.of());
            case "S06" -> new Ports(jp.revati.skyabyss.titan.storm.back.StormDeck.FRONT_GATE, true, List.of(), null, null, "", empty(), List.of());
            // 余燼: 広間からの冷えた溶岩洞の口の戸 (引かなかったグループに閉じる。石は熾り石 = EmberBackTrials が扱う)
            case "E01" -> new Ports(jp.revati.skyabyss.titan.ember.back.EmberBackTrials.boxBlocks(jp.revati.skyabyss.titan.ember.back.EmberBackLayout.WEST_DOOR), true,
                    List.of(), null, null, "", empty(), List.of());
            case "E05" -> new Ports(jp.revati.skyabyss.titan.ember.back.EmberBackTrials.boxBlocks(jp.revati.skyabyss.titan.ember.back.EmberBackLayout.EAST_DOOR), true,
                    List.of(), null, null, "", empty(), List.of());
            case "E09" -> new Ports(jp.revati.skyabyss.titan.ember.back.EmberBackTrials.boxBlocks(jp.revati.skyabyss.titan.ember.back.EmberBackLayout.SOUTH_DOOR), true,
                    List.of(), null, null, "", empty(), List.of());
            default -> Ports.NONE;
        };
    }

    private static AABB empty() {
        return new AABB(0, 0, 0, 0, 0, 0);
    }
}

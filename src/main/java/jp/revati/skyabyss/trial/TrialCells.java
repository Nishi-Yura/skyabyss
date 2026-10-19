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
import jp.revati.skyabyss.trial.whale.W02Bell;
import jp.revati.skyabyss.trial.whale.W03Moss;
import jp.revati.skyabyss.trial.whale.W04Vents;
import jp.revati.skyabyss.trial.whale.W11Coffin;
import jp.revati.skyabyss.trial.whale.W12Arena;
import jp.revati.skyabyss.trial.whale.W06Score;
import jp.revati.skyabyss.trial.whale.W07Gates;
import jp.revati.skyabyss.trial.whale.W10Fort;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

// セルの登録簿。原型は LegacyCell で包み (口だけ)、岩鯨の新作 4 は動かす仕組みを持つセル。
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
        register(new W02Bell());
        register(new W03Moss());
        register(new W04Vents());
        register(new W06Score());
        register(new W07Gates());
        register(new W10Fort());
        register(new W11Coffin());
        register(new W12Arena());
        for (Def d : TrialCatalog.all()) {
            if (!CELLS.containsKey(d.id())) {
                throw new IllegalStateException("trial " + d.id() + " has no cell");
            }
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

    // 位置が石の差し口のセル (新作)。原型の石は別 (ResonanceStoneBlock などが扱う)
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
            case "W01" -> new Ports(WhaleDeck.WEST_GATE, true, List.of(), TitanBackLayout.RESONANCE_STONES.get(0), null, "", empty(), List.of());
            case "W09" -> new Ports(WhaleDeck.EAST_GATE, true, List.of(), TitanBackLayout.RESONANCE_STONES.get(1), null, "", empty(), List.of());
            case "W05" -> new Ports(WhaleDeck.mazeLids(), true, List.of(), TitanBackLayout.RESONANCE_STONES.get(2), null, "", empty(), List.of());
            default -> Ports.NONE;
        };
    }

    private static AABB empty() {
        return new AABB(0, 0, 0, 0, 0, 0);
    }
}

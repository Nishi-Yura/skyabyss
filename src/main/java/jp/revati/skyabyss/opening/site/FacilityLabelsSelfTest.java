package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;

// 施設の浮く名札 (FacilityLabels) の自己テスト。段: "facility labels"。設計図だけで見る (都と四つの土地の町):
//   文: 名札の題が日本語と英語の両方にあり、中身の行は両方にあるか両方に無い。土地の町の宿は「旅の宿」の文
//   置き場: 出す名札はどれも、どの向きを向いてもブロックに触れない所 (触れるマス 0) が探す範囲の中にある
public final class FacilityLabelsSelfTest {
    static final List<String> BLUEPRINTS = List.of("capital", "town_whale", "town_tide", "town_storm", "town_ember");

    private FacilityLabelsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("facility labels", FacilityLabelsSelfTest::run));
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        Map<String, String> ja = SignFit.lang("ja_jp");
        Map<String, String> en = SignFit.lang("en_us");
        int labels = 0;
        int moved = 0;
        for (String name : BLUEPRINTS) {
            Blueprint bp = Blueprint.get(name);
            if (bp == null) {
                f.add("no blueprint " + name);
                continue;
            }
            boolean town = name.startsWith("town_");
            Map<Long, BlockState> cells = new HashMap<>();
            for (Blueprint.Cell c : bp.cells) {
                cells.put(BlockPos.asLong(c.x(), c.y(), c.z()), c.state());
            }
            // 設計図に無い所は、地面 (y <= -1) が固く、上は空気
            FacilityLabels.Cells look = (x, y, z) -> {
                BlockState s = cells.get(BlockPos.asLong(x, y, z));
                return s == null ? y <= -1 : FacilityLabels.filled(s);
            };
            for (Map.Entry<String, int[]> m : bp.marks.entrySet()) {
                if (!m.getKey().startsWith("label_")) {
                    continue;
                }
                String label = m.getKey().substring(6);
                if (FacilityLabels.HIDDEN.contains(label)) {
                    continue;
                }
                String key = FacilityLabels.key(label, town);
                String lk = FacilityLabels.langKey(key);
                check(f, !ja.getOrDefault(lk, "").isEmpty() && !en.getOrDefault(lk, "").isEmpty(), name + " " + label + ": no title text " + lk);
                check(f, ja.containsKey(lk + ".sub") == en.containsKey(lk + ".sub"), name + " " + label + ": sub line in only one language");
                int[] v = m.getValue();
                BlockPos at = new BlockPos(v[0], v[1], v[2]);
                FacilityLabels.Spot spot = FacilityLabels.find(at, key, look);
                labels++;
                if (!spot.cell().equals(at)) {
                    moved++;
                }
                check(f, spot.touching() == 0, name + " " + label + ": no clear spot (best " + spot.cell().toShortString() + " touches " + spot.touching() + ", radius "
                        + String.format("%.2f", FacilityLabels.radius(key)) + ")");
                SkyAbyss.LOGGER.info("[SELFTEST] facility labels: {} {} mark {} -> {} (radius {}, touching {})", name, label, at.toShortString(), spot.cell().toShortString(),
                        String.format("%.2f", FacilityLabels.radius(key)), spot.touching());
            }
        }
        check(f, "旅の宿".equals(ja.get(FacilityLabels.langKey(FacilityLabels.key("inn", true)))), "town inn label is not 旅の宿");
        check(f, labels >= 38, "only " + labels + " labels in the capital and towns");
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST facility labels PASSED ({} labels, {} moved off the mark, every one clear of blocks at any angle; ja/en text)", labels, moved);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST facility labels FAILED: {}", m));
        }
    }
}

package jp.revati.skyabyss.client.map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.map.MapMarks;
import jp.revati.skyabyss.titan.travel.TravelDest;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

// 旅の地図の印の文: 名前と「北東 420」(自分からの方角と距離。座標の数字は出さない)。方角の字は海図と同じ (gui.skyabyss.travel.dir.*)
public final class MapText {
    private static final String[] DIRS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private MapText() {
    }

    public static Component name(MapMarks.Mark m) {
        return switch (m.kind()) {
            case TITAN -> Component.translatable("progress.skyabyss.titan." + m.name());
            case WAYMARK -> m.extra().startsWith("poi~:") ? Component.translatable("map.skyabyss.poi.undiscovered", Component.translatable("map.skyabyss.poi." + m.extra().substring(5)))
                    : m.extra().startsWith("poi:") ? Component.translatable("map.skyabyss.poi." + m.extra().substring(4))
                    : m.name().isEmpty() ? TravelDest.waymarkName(m.extra()) : Component.literal(m.name());
            case SITE -> siteName(m.name(), m.extra());
            case SPAWN -> Component.translatable("gui.skyabyss.travel.spawn");
            case TOWN -> m.extra().isEmpty() ? Component.translatable("map.skyabyss.mark.town") : Component.translatable("map.skyabyss.town." + m.extra());
            case SHRINE -> m.extra().isEmpty() ? Component.translatable("map.skyabyss.mark.shrine")
                    : Component.translatable("map.skyabyss.shrine_of", Component.translatable("progress.skyabyss.titan." + m.extra()));
            default -> Component.translatable("map.skyabyss.mark." + m.kind().name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    // 章の場所: 要素ごとの名前 (map.skyabyss.site.<id>。末尾の番号は外して引く)、なければ「岩鯨の章の場所」
    static Component siteName(String elementId, String chapter) {
        Language lang = Language.getInstance();
        String base = elementId.replaceAll("_\\d+$", "");
        for (String id : new String[]{elementId, base}) {
            String key = "map.skyabyss.site." + id;
            if (lang.has(key)) {
                return Component.translatable(key);
            }
        }
        String tab = "lore.skyabyss.chapter." + chapter + ".name";
        return lang.has(tab) ? Component.translatable("map.skyabyss.site.of_chapter", Component.translatable(tab))
                : Component.translatable("map.skyabyss.mark.site");
    }

    // 8 方位の字 (dx = 東が正、dz = 南が正)
    public static Component dir(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return Component.translatable("gui.skyabyss.travel.dir." + DIRS[sector]);
    }

    // 自分からの方角と距離。地上にいなければ「地上の印」とだけ
    public static Component where(@Nullable double[] me, double x, double z) {
        if (me == null) {
            return Component.translatable("gui.skyabyss.map.far_below");
        }
        double dx = x - me[0];
        double dz = z - me[1];
        double dist = Math.hypot(dx, dz);
        if (dist < 24) {
            return Component.translatable("gui.skyabyss.map.here");
        }
        int rounded = dist < 200 ? (int) Math.round(dist / 10.0) * 10 : (int) Math.round(dist / 50.0) * 50;
        return Component.translatable("gui.skyabyss.map.dist", dir(dx, dz), String.format("%,d", rounded));
    }

    // 噂: 方角だけ (距離は出さない)
    public static Component rumor(@Nullable double[] me, double x, double z) {
        if (me == null) {
            return Component.translatable("gui.skyabyss.map.rumor_only");
        }
        return Component.translatable("gui.skyabyss.map.rumor", dir(x - me[0], z - me[1]));
    }
}

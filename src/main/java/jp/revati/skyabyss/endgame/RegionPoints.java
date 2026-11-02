package jp.revati.skyabyss.endgame;

import java.time.temporal.IsoFields;

import jp.revati.skyabyss.tower.Towers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

// 土地ごとの点 (docs/design/54 の 3.1・3.3・5.1・6.2.2)。巡り路の印・大陸の大事件の与えた分などを、人ごと・土地ごとに数える。
//   週の点  (WEEK) : その週 (ISO 週。朝 5 時で日が替わる Towers.today と同じ暦) に稼いだ点。組合の旗取り (5.1) が数える
//   名声    (FAME) : これまでに稼いだ点の合計。土地の名声 (6.2.2) の段はこれで決まる
// 土地の番号は ContinentMap (WHALE 2・TIDE 3・STORM 4・EMBER 5。野 0 も数えられる)
public final class RegionPoints {
    public static final String WEEK = "skyabyss_region_week";
    public static final String FAME = "skyabyss_region_fame";

    private RegionPoints() {
    }

    public static String weekId() {
        return jp.revati.skyabyss.util.DayClock.weekId();
    }

    public static void add(Player p, int region, int n) {
        if (n <= 0 || region < 0) {
            return;
        }
        CompoundTag data = p.getPersistentData();
        CompoundTag week = data.getCompound(WEEK);
        String id = weekId();
        if (!id.equals(week.getString("id"))) {
            week = new CompoundTag();
            week.putString("id", id);
        }
        week.putInt("r" + region, week.getInt("r" + region) + n);
        data.put(WEEK, week);
        CompoundTag fame = data.getCompound(FAME);
        int before = fame.getInt("r" + region);
        fame.putInt("r" + region, before + n);
        data.put(FAME, fame);
        if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
            RegionFame.changed(sp, region, before, before + n);
            // 組合の旗取り (GuildWeek): 組合ごとの土地の週の点
            GuildWeek.regionPoints(sp, region, n);
        }
    }

    // この週の点 (週が替わっていれば 0)
    public static int week(Player p, int region) {
        CompoundTag week = p.getPersistentData().getCompound(WEEK);
        return weekId().equals(week.getString("id")) ? week.getInt("r" + region) : 0;
    }

    public static int fame(Player p, int region) {
        return p.getPersistentData().getCompound(FAME).getInt("r" + region);
    }
}

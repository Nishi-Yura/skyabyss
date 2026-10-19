package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.record.RecordSnapshot;
import jp.revati.skyabyss.record.RecordSnapshot.Kind;
import jp.revati.skyabyss.record.RecordSnapshot.Row;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// 記録画面の「転生の欄」 (C15)。回数・記号・Lv 50+n と、前の人生の要約 (人生の最速・最少の死・死なずの転生・死の合計・巨神ごとの自己最速・選んでいる称号)。
// 行 "rebirth" の bar は count / 上限 (画面が名前の枠の色を決めるのに読む: RebirthRecord.countOf)
public final class RebirthRecord {
    public static final String KEY = "rebirth";

    private RebirthRecord() {
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("skyabyss_rebirth.record." + key, args);
    }

    public static List<Row> rows(ServerPlayer p) {
        List<Row> rows = new ArrayList<>();
        int n = RebirthView.count(p);
        RebirthData d = RebirthView.data(p);
        RebirthStats.Entry e = RebirthStats.get(p.server).peek(p.getUUID());
        rows.add(new Row("h." + KEY, Kind.HEADER, ItemStack.EMPTY, t("header"), Component.empty(), RecordSnapshot.MARK_NONE, -1, List.of(), RecordSnapshot.ACTION_NONE));
        List<Component> detail = new ArrayList<>();
        Component value;
        if (n <= 0) {
            value = t("none");
            detail.add(t("none_hint"));
        } else {
            value = RebirthText.symbols(n, 0L, true).append(" ").append(RebirthText.level(50, n, 0L, true));
            detail.add(t("count", n, RebirthTier.CAP));
        }
        if (e != null && n > 0) {
            if (e.fastestLife >= 0) {
                detail.add(t("fastest", RebirthLeaderboard.time(e.fastestLife)));
            }
            if (e.fewestDeaths >= 0) {
                detail.add(t("fewest", e.fewestDeaths));
            }
            detail.add(t("flawless", e.flawless));
            detail.add(t("deaths", e.deathsTotal));
        }
        if (e != null) {
            for (Map.Entry<String, Long> b : e.titanBest.entrySet()) {
                detail.add(t("titan_best", Progression.titanName(b.getKey()), RebirthLeaderboard.time(b.getValue())));
            }
        }
        RebirthTitles.Title cur = RebirthTitles.byId(d.title());
        if (cur != null) {
            detail.add(t("title", Component.translatable("title.skyabyss_rebirth." + cur.id())));
        }
        rows.add(new Row(KEY, Kind.VALUE, ItemStack.EMPTY, t("label"), value, n >= RebirthTier.CAP ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE,
                (float) n / RebirthTier.CAP, detail, RecordSnapshot.ACTION_NONE));
        return rows;
    }

    // 画面側: 行の bar から回数を戻す
    public static int countOf(RecordSnapshot snapshot) {
        RecordSnapshot.Tab tab = snapshot.tab(RecordSnapshot.GROWTH);
        Row row = tab == null ? null : tab.row(KEY);
        return row == null || row.bar() < 0 ? 0 : RebirthTier.clamp(Math.round(row.bar() * RebirthTier.CAP));
    }
}

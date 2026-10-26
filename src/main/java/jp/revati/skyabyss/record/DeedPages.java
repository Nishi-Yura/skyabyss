package jp.revati.skyabyss.record;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.record.RecordSnapshot.Kind;
import jp.revati.skyabyss.record.RecordSnapshot.Row;
import jp.revati.skyabyss.record.RecordSnapshot.Tab;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// 控え帳の「記し」のつまみ (バニラの進捗の画面の代わり。2026-10-26 の声「進捗の UI 変えてもいい。みにくいので」)。
// 中身はこの Mod の進捗そのもの (data/skyabyss/advancement、tools/gen/story/gen_advancements.py)。分け方と並びは data/skyabyss/deeds.json。
//   deeds          一覧: 分けごとの済んだ数と帯、ぜんぶの数
//   deeds.<分け>   旅・巨神・名のある強敵・成長・町: 一つの記しが一行 (絵・題・済/途中/まだ)。まだ見えない記し (hidden で済んでいない) は「？？？」
public final class DeedPages {
    public static final String TAB = "deeds";
    public static final List<String> GROUPS = List.of("journey", "titans", "foes", "growth", "town");
    private static volatile Map<String, List<String>> pages;

    private DeedPages() {
    }

    public static Map<String, List<String>> pages() {
        Map<String, List<String>> p = pages;
        if (p == null) {
            p = new LinkedHashMap<>();
            try (InputStream in = DeedPages.class.getResourceAsStream("/data/skyabyss/deeds.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pages");
                    for (String g : GROUPS) {
                        List<String> ids = new ArrayList<>();
                        if (o.has(g)) {
                            for (JsonElement e : o.getAsJsonArray(g)) {
                                ids.add(e.getAsString());
                            }
                        }
                        p.put(g, List.copyOf(ids));
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                SkyAbyss.LOGGER.error("could not read data/skyabyss/deeds.json", e);
            }
            pages = p;
        }
        return p;
    }

    // 一つの記しの今
    public record Deed(String id, AdvancementHolder holder, DisplayInfo display, AdvancementProgress progress) {
        public boolean done() {
            return progress.isDone();
        }

        // まだ見えない (隠しで、済んでいない)
        public boolean secret() {
            return display.isHidden() && !progress.isDone();
        }
    }

    public static List<Deed> deeds(ServerPlayer p, String group) {
        List<Deed> out = new ArrayList<>();
        for (String id : pages().getOrDefault(group, List.of())) {
            AdvancementHolder h = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id));
            if (h == null || h.value().display().isEmpty()) {
                continue;
            }
            out.add(new Deed(id, h, h.value().display().get(), p.getAdvancements().getOrStartProgress(h)));
        }
        return out;
    }

    public static List<Tab> tabs(ServerPlayer p) {
        List<Tab> tabs = new ArrayList<>();
        List<Row> overview = new ArrayList<>();
        int allDone = 0;
        int all = 0;
        List<Tab> groupTabs = new ArrayList<>();
        for (String g : GROUPS) {
            List<Deed> ds = deeds(p, g);
            int done = (int) ds.stream().filter(Deed::done).count();
            allDone += done;
            all += ds.size();
            int mark = ds.isEmpty() ? RecordSnapshot.MARK_NONE : done == ds.size() ? RecordSnapshot.MARK_SAFE : done > 0 ? RecordSnapshot.MARK_ATTENTION
                    : RecordSnapshot.MARK_NONE;
            overview.add(new Row("group." + g, Kind.VALUE, ItemStack.EMPTY, RecordBuilder.t("deeds." + g), RecordBuilder.t("v.of", done, ds.size()), mark,
                    ds.isEmpty() ? 0f : done / (float) ds.size(), List.of(RecordBuilder.t("d.deeds." + g)), RecordSnapshot.ACTION_NONE));
            List<Row> rows = new ArrayList<>();
            for (Deed d : ds) {
                rows.add(row(d));
            }
            groupTabs.add(new Tab(TAB + RecordSnapshot.SUB + g, RecordBuilder.t("deeds.chip." + g), mark, rows));
        }
        overview.add(0, new Row("all", Kind.VALUE, ItemStack.EMPTY, RecordBuilder.t("deeds.all"), RecordBuilder.t("v.of", allDone, all),
                allDone == all && all > 0 ? RecordSnapshot.MARK_SAFE : RecordSnapshot.MARK_NONE, all == 0 ? 0f : allDone / (float) all,
                List.of(RecordBuilder.t("d.deeds.all")), RecordSnapshot.ACTION_NONE));
        tabs.add(new Tab(TAB, RecordBuilder.t("sub.overview"), RecordSnapshot.MARK_NONE, overview));
        tabs.addAll(groupTabs);
        return tabs;
    }

    static Row row(Deed d) {
        if (d.secret()) {
            return new Row(d.id(), Kind.VALUE, ItemStack.EMPTY, RecordBuilder.t("r.unknown"), RecordBuilder.t("v.deed_secret"), RecordSnapshot.MARK_LOCKED, -1,
                    List.of(RecordBuilder.t("d.deed_secret")), RecordSnapshot.ACTION_NONE);
        }
        AdvancementProgress pr = d.progress();
        int total = d.holder().value().requirements().size();
        int got = 0;
        for (String c : pr.getCompletedCriteria()) {
            got++;
        }
        Component value;
        int mark;
        float bar = -1;
        if (pr.isDone()) {
            value = RecordBuilder.t("v.deed_done");
            mark = RecordSnapshot.MARK_SAFE;
        } else if (total > 1) {
            value = RecordBuilder.t("v.of", Math.min(got, total), total);
            mark = got > 0 ? RecordSnapshot.MARK_ATTENTION : RecordSnapshot.MARK_NONE;
            bar = got / (float) total;
        } else {
            value = RecordBuilder.t("v.deed_open");
            mark = RecordSnapshot.MARK_NONE;
        }
        List<Component> detail = new ArrayList<>();
        detail.add(d.display().getDescription());
        AdvancementType type = d.display().getType();
        detail.add(RecordBuilder.t("d.deed_kind." + type.getSerializedName()));
        return new Row(d.id(), Kind.ITEM, d.display().getIcon().copy(), d.display().getTitle().copy(), value, mark, bar, detail, RecordSnapshot.ACTION_NONE);
    }
}

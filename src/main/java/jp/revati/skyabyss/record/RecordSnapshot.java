package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

// 控え帳の中身 (サーバーが作り、そのまま画面に描く)。
// 文はすべてサーバーで翻訳キーとして組むので、画面は値を計算しない (クライアントの値を信じない、の裏返し)。
//   target   : 誰の控え帳か
//   self     : 本人が見ているか (管理者がほかの人を見ているときは false。受け取りのボタンを出さない)
//   tabs     : つまみごとの行
public record RecordSnapshot(UUID target, Component targetName, boolean self, List<Tab> tabs) {
    public static final String KEEPSAKES = "keepsakes";
    public static final String TITANS = "titans";
    public static final String GROWTH = "growth";
    public static final String BONDS = "bonds";
    public static final String DEEDS = "deeds";
    public static final List<String> TAB_IDS = List.of(KEEPSAKES, TITANS, GROWTH, BONDS, DEEDS);

    // 行の種類: 見出し、品 (左に絵)、値
    public enum Kind {
        HEADER, ITEM, VALUE
    }

    // 印: なし、無事 (青緑)、気をつける (金)、なくした・足りない (赤紫)、まだ (灰)
    public static final int MARK_NONE = 0;
    public static final int MARK_SAFE = 1;
    public static final int MARK_ATTENTION = 2;
    public static final int MARK_LOST = 3;
    public static final int MARK_LOCKED = 4;

    // 行の押しボタン
    public static final int ACTION_NONE = 0;
    // 預かっている報酬を受け取る (本人だけ)
    public static final int ACTION_CLAIM = 1;
    // 初回の選択報酬をチャットで選ぶ (/titanmemory を走らせて画面を閉じる。本人だけ)
    public static final int ACTION_CHOOSE = 2;
    // なくした天の鍵の欠片・天の鍵を呼び戻す (KeepsakeBonds.recall。本人だけ)
    public static final int ACTION_RECALL_FRAGMENT = 3;
    public static final int ACTION_RECALL_KEY = 4;
    // 見た語り (紙芝居) をもう一度流す (行の key = "reel.<語りの名前>"。本人だけ。story/reel/StoryReels)
    public static final int ACTION_REPLAY = 5;
    // 試練の記録 (図鑑) を開く (facility/KeepRecords。前は砦の老兵が開いた。本人だけ)
    public static final int ACTION_RECORDS = 6;
    public static final String REEL_ROW = "reel.";

    // 中のつまみ: 「巨神」のつまみの中に、一覧と巨神ごとの頁を並べる (id は「titans.巨神の名前」)。
    // 巨神の一覧 (progress.Titans) の順に、サーバーが頁を作る。巨神が増えれば頁も自動で増える
    public static final String SUB = ".";

    public static String parentTab(String id) {
        int i = id.indexOf(SUB);
        return i < 0 ? id : id.substring(0, i);
    }

    // key: 行の名前 (変わった値を見つけるため。つまみの中で一意)
    // bar: 0..1 の帯 (経験値など)。帯がなければ -1
    // detail: 右の頁に出す段落 (画面が折り返す)
    public record Row(String key, Kind kind, ItemStack icon, Component label, Component value, int mark, float bar,
                      List<Component> detail, int action) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeUtf(r.key);
                    buf.writeVarInt(r.kind.ordinal());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.icon);
                    ComponentSerialization.STREAM_CODEC.encode(buf, r.label);
                    ComponentSerialization.STREAM_CODEC.encode(buf, r.value);
                    buf.writeVarInt(r.mark);
                    buf.writeFloat(r.bar);
                    buf.writeVarInt(r.detail.size());
                    for (Component c : r.detail) {
                        ComponentSerialization.STREAM_CODEC.encode(buf, c);
                    }
                    buf.writeVarInt(r.action);
                },
                buf -> {
                    String key = buf.readUtf();
                    Kind kind = Kind.values()[Math.floorMod(buf.readVarInt(), Kind.values().length)];
                    ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
                    Component value = ComponentSerialization.STREAM_CODEC.decode(buf);
                    int mark = buf.readVarInt();
                    float bar = buf.readFloat();
                    int n = Math.min(buf.readVarInt(), 64);
                    List<Component> detail = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        detail.add(ComponentSerialization.STREAM_CODEC.decode(buf));
                    }
                    return new Row(key, kind, icon, label, value, mark, bar, detail, buf.readVarInt());
                });
    }

    // title: 中のつまみの名前 (伏せ字のこともある)。mark: 中のつまみの印
    public record Tab(String id, Component title, int mark, List<Row> rows) {
        public Tab(String id, List<Row> rows) {
            this(id, Component.empty(), MARK_NONE, rows);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Tab> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Tab::id,
                ComponentSerialization.STREAM_CODEC, Tab::title,
                ByteBufCodecs.VAR_INT, Tab::mark,
                Row.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Tab::rows,
                Tab::new);

        public Row row(String key) {
            for (Row r : rows) {
                if (r.key.equals(key)) {
                    return r;
                }
            }
            return null;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordSnapshot> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RecordSnapshot::target,
            ComponentSerialization.STREAM_CODEC, RecordSnapshot::targetName,
            ByteBufCodecs.BOOL, RecordSnapshot::self,
            Tab.STREAM_CODEC.apply(ByteBufCodecs.list(32)), RecordSnapshot::tabs,
            RecordSnapshot::new);

    public Tab tab(String id) {
        for (Tab t : tabs) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    // 中のつまみ (一覧の頁 parent と、parent:… の頁)。中のつまみがなければ空
    public List<Tab> subTabs(String parent) {
        List<Tab> out = new ArrayList<>();
        for (Tab t : tabs) {
            if (t.id.startsWith(parent + SUB)) {
                out.add(t);
            }
        }
        if (!out.isEmpty()) {
            Tab head = tab(parent);
            if (head != null) {
                out.add(0, head);
            }
        }
        return out;
    }

    // 変わった値を見つけるための、行の名前 → 行 (見出しは除く)。名前は「つまみ/行」
    public Map<String, Row> flat() {
        Map<String, Row> map = new LinkedHashMap<>();
        for (Tab t : tabs) {
            for (Row r : t.rows) {
                if (r.kind != Kind.HEADER) {
                    map.put(t.id + "/" + r.key, r);
                }
            }
        }
        return map;
    }

    // 時間とともに勝手に動く値 (マナ、巨神を呼べるまでの時間)。「変わった値」には数えない
    public static boolean isVolatile(String flatKey) {
        return flatKey.equals(GROWTH + "/mana") || flatKey.equals(TITANS + "/cooldown") || flatKey.endsWith("/cooldown");
    }

    // 二つの控え帳で値が変わった行 (前にない行も「変わった」に入れる。前にあって今ない行は、値を空にして入れる)
    public record Change(String key, Component label, Component before, Component after) {
    }

    public static List<Change> diff(RecordSnapshot before, RecordSnapshot after) {
        List<Change> changes = new ArrayList<>();
        Map<String, Row> a = before.flat();
        Map<String, Row> b = after.flat();
        b.forEach((key, row) -> {
            Row old = a.get(key);
            if (old == null) {
                changes.add(new Change(key, row.label, Component.literal("—"), row.value));
            } else if (!old.value.equals(row.value) || old.mark != row.mark) {
                changes.add(new Change(key, row.label, old.value, row.value));
            }
        });
        a.forEach((key, row) -> {
            if (!b.containsKey(key)) {
                changes.add(new Change(key, row.label, row.value, Component.literal("—")));
            }
        });
        return changes;
    }
}

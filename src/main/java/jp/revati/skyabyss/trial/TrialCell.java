package jp.revati.skyabyss.trial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 試練のセル (docs/design/29 の 2.4・5.1)。入口・出口・石の差し口・休み場の決まった口を持つ部屋。
// 世界のブロックは常設 (建てるのは版ごとに一度)。引いたグループには入口の戸 (GroupGates) が開き、仕掛けはグループごとの記録・
// 見かけの上書き (GroupOverlays)・敵の持ち主 (GroupOwned) で動くので、同じ部屋に二つのグループが同時にいても混ざらない。
// 今ある試練 (原型) は LegacyCell で薄く包み (口の登録と図鑑・報いだけ)、動かす仕組みはもとのまま。
public interface TrialCell {
    // 休み場: 記録の名前・床の箱・復活の位置
    record Rest(String id, TitanBackLayout.Box area, BlockPos spawn) {
    }

    // 口。entrance = 入口の戸のブロック (封印の扉)。entranceDefaultOpen = 戸の既定 (原型は開いていて、引かなかったグループに閉じる /
    // 新作は閉じていて、引いたグループに開ける)。exitGate = 終わると開く戸 (石の前)。stone = 石の差し口 (なければ null)。
    // pad = 終えたあとの戻りの光る床 (null なら無し)、returnRest = 戻る休み場の名前。area = セルの床の範囲 (中にいるかの判定)
    record Ports(List<BlockPos> entrance, boolean entranceDefaultOpen, List<BlockPos> exitGate, @Nullable BlockPos stone, @Nullable BlockPos pad,
            String returnRest, AABB area, List<Rest> rests) {
        public static final Ports NONE = new Ports(List.of(), true, List.of(), null, null, "", new AABB(0, 0, 0, 0, 0, 0), List.of());
    }

    // いま動いているセル一つ分の状態 (グループごと)。メモリだけで保存しない
    final class Run {
        public final UUID group;
        public final TrialCell cell;
        public final Mods mods;
        public final long seed;
        public final long startTick;
        public boolean cleared;
        public boolean goalOk = true;
        // 区画の写しで動かすときの、手本からのずれ (docs/design/32 の 5 章。ZERO = 手本の部屋)
        public TrialFrame frame = TrialFrame.ZERO;
        public long clearedAt;
        public long lastPresent;
        public final Map<String, Object> data = new HashMap<>();

        public Run(UUID group, TrialCell cell, Mods mods, long seed, long startTick) {
            this.group = group;
            this.cell = cell;
            this.mods = mods;
            this.seed = seed;
            this.startTick = startTick;
            this.lastPresent = startTick;
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key, Supplier<T> init) {
            return (T) data.computeIfAbsent(key, k -> init.get());
        }

        public int getInt(String key) {
            Object o = data.get(key);
            return o instanceof Integer i ? i : 0;
        }

        public long getLong(String key) {
            Object o = data.get(key);
            return o instanceof Long l ? l : 0L;
        }

        public void put(String key, Object value) {
            data.put(key, value);
        }
    }

    Def def();

    Ports ports();

    // 動かし始め (そのグループが初めて中に入ったとき)
    default void arm(ServerLevel back, Run run) {
    }

    // 中にグループの人がいるあいだ 5 tick ごと。inside = 中にいる、そのグループの人
    default void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
    }

    // 動かすのをやめる (誰もいなくなって少したった・試練が戻った)。敵・仕掛けの片付け
    default void disarm(ServerLevel back, Run run) {
    }

    // そのグループの画面だけに見せるブロック
    default void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
    }

    // 右クリックされる仕掛けの位置
    default Set<BlockPos> interactive() {
        return Set.of();
    }

    // 右クリック。扱ったら true
    default boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        return false;
    }

    // 建てたあとの形の検査 (問題の一覧。空なら良い)
    default List<String> audit(ServerLevel back) {
        return List.of();
    }

    // 動かす仕組みのあるセルか (false = 原型の包み。もとの仕組みが動かす)
    default boolean runtime() {
        return true;
    }

    // 区画に写して動かせるか (docs/design/32 の 5 章)。決まりがセルの呼び出し (arm・tick・onUse・disarm・overlay) の中だけで
    // 座標を使うセルだけ true にする。false なら背の中の手本の部屋を、グループごとの上書きで共有する
    default boolean relocatable() {
        return false;
    }

    // 区画へ写す箱 (手本の座標)。既定は床の範囲・休み場・戸・ゴール・光る床を囲んで 3 ブロック広げたもの
    default AABB buildBox() {
        Ports p = ports();
        AABB box = p.area();
        for (Rest r : p.rests()) {
            box = box.minmax(r.area().aabb());
        }
        for (BlockPos b : p.entrance()) {
            box = box.minmax(new AABB(b));
        }
        for (BlockPos b : p.exitGate()) {
            box = box.minmax(new AABB(b));
        }
        if (p.stone() != null) {
            box = box.minmax(new AABB(p.stone()));
        }
        if (p.pad() != null) {
            box = box.minmax(new AABB(p.pad()));
        }
        return box.inflate(3);
    }
}

package jp.revati.skyabyss.titan.travel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

// プレイヤーごとの「巨神での移動」の記録。プレイヤーのデータと一緒に保存し、死んでも引き継ぐ。
// - unlocked: 認めてくれた巨神 (心臓を倒した巨神。コマンドで足すこともできる)
// - callGiven: 呼び笛をすでに渡した巨神 (一度だけ自動で渡す。なくしたら作り直す)
// - waymarks: 覚えた碑 (オーバーワールドの位置。覚えた順)
// - readyAt: 次に呼べるようになる時刻 (オーバーワールドの総 tick)
// - voyages: 巨神に運ばれた回数
public final class TravelState {
    public static final int MAX_WAYMARKS = 16;

    public static final Codec<TravelState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(s -> List.copyOf(s.unlocked)),
            Codec.STRING.listOf().optionalFieldOf("callGiven", List.of()).forGetter(s -> List.copyOf(s.callGiven)),
            Codec.LONG.listOf().optionalFieldOf("waymarks", List.of()).forGetter(s -> s.waymarks.stream().map(BlockPos::asLong).toList()),
            Codec.LONG.optionalFieldOf("readyAt", 0L).forGetter(s -> s.readyAt),
            Codec.INT.optionalFieldOf("voyages", 0).forGetter(s -> s.voyages)
    ).apply(i, TravelState::new));

    public final Set<String> unlocked = new LinkedHashSet<>();
    public final Set<String> callGiven = new LinkedHashSet<>();
    public final List<BlockPos> waymarks = new ArrayList<>();
    public long readyAt;
    public int voyages;

    public TravelState() {
    }

    private TravelState(List<String> unlocked, List<String> callGiven, List<Long> waymarks, long readyAt, int voyages) {
        this.unlocked.addAll(unlocked);
        this.callGiven.addAll(callGiven);
        for (long pos : waymarks) {
            BlockPos p = BlockPos.of(pos);
            if (!this.waymarks.contains(p) && this.waymarks.size() < MAX_WAYMARKS) {
                this.waymarks.add(p);
            }
        }
        this.readyAt = readyAt;
        this.voyages = voyages;
    }

    public enum AddResult { ADDED, ALREADY, FULL }

    public AddResult addWaymark(BlockPos pos) {
        if (waymarks.contains(pos)) {
            return AddResult.ALREADY;
        }
        if (waymarks.size() >= MAX_WAYMARKS) {
            return AddResult.FULL;
        }
        waymarks.add(pos.immutable());
        return AddResult.ADDED;
    }

    public boolean forgetWaymark(BlockPos pos) {
        return waymarks.remove(pos);
    }
}

package jp.revati.skyabyss.trial;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.titan.group.BackGroupData;
import net.minecraft.server.level.ServerLevel;

// 順路のセルを太らせる (docs/design/32 の 4 章、R5)。原型の十二の試練は、周回 (そのグループがその巨神の心臓を一度止めたあと) では
// 長さ 1.5〜2 倍になる: 智は仕掛けの段を、闘は波・巣・火の量を増やす (数は各試練の「長い」定数)。初回 (物語) は今の長さのまま
// (初回の延長の部屋が長さを担う。21 の 1.1)。一人で解ける・猶予の最短は変えない。状態 (時間・天気・背) では変えない。
public final class RouteLength {
    // 自己テスト用: null でなければ、周回かどうかに関わらずこの値
    public static volatile Boolean force;

    private RouteLength() {
    }

    // このグループの記録で、原型を長くするか
    public static boolean extended(@Nullable BackGroupData.Record r) {
        Boolean f = force;
        if (f != null) {
            return f;
        }
        return r != null && r.isFirstCleared();
    }

    public static boolean extended(ServerLevel back, UUID group) {
        Boolean f = force;
        if (f != null) {
            return f;
        }
        return extended(BackGroupData.get(back).peek(group));
    }
}

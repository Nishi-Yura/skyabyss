package jp.revati.skyabyss.trial;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.trial.Trial.Def;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 周回で長くなる歩の原型 (docs/design/32 の 4 章、R5。RouteLength)。原型の入口の休み場の手前に「前の道」(谷・湖・登り) を足し、
// 周回のグループは前の道の休み場へ運ばれて、前の道を越えてから原型の入口の休み場へ歩いて入る (原型の中身・石・休み場はそのまま)。
// 前の道の出口の戸 (封印の扉) は、周回のグループの今の段がその原型のときだけ、そのグループに開く (GroupGates)。初回のグループには閉じたまま
// (前の道は見えない所にあり、入る口は戸だけ)。前の道の形と落ちたときの戻しは各背 (岩鯨 TitanBackLayout.WP など)
public final class LongWalk {
    // titan: 巨神、def: 原型の歩の試練、rest: 前の道の休み場 (各背の休み場の表の名前)、gate: 前の道の出口の戸のブロック
    public record Pre(String titan, String def, String rest, List<BlockPos> gate) {
    }

    private static Map<String, Pre> table;

    private LongWalk() {
    }

    // 巨神ごとの前の道 (各背の配置から。一度だけ作る)
    static synchronized Map<String, Pre> all() {
        if (table == null) {
            java.util.LinkedHashMap<String, Pre> m = new java.util.LinkedHashMap<>();
            for (Pre p : List.of(
                    jp.revati.skyabyss.titan.dungeon.TitanBackLayout.longWalk(),
                    jp.revati.skyabyss.titan.storm.back.StormBackLayout.longWalk(),
                    jp.revati.skyabyss.titan.ember.back.EmberBackLayout.longWalk())) {
                if (p != null) {
                    m.put(p.titan(), p);
                }
            }
            table = java.util.Collections.unmodifiableMap(m);
        }
        return table;
    }

    @Nullable
    public static Pre of(String titan) {
        return all().get(titan);
    }

    // そのグループが段 step で、前の道から始めるか (周回で、その段がその巨神の前の道のある原型)
    @Nullable
    public static Pre preOf(ServerLevel back, UUID group, String titan, @Nullable Def def) {
        Pre p = of(titan);
        if (p == null || def == null || !def.id().equals(p.def())) {
            return null;
        }
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return RouteLength.extended(r) ? p : null;
    }

    // 前の道の出口の戸を、今の段に合わせて開け閉めする (TrialRuntime.syncGates から)
    public static void sync(ServerLevel back, UUID group, String titan, @Nullable Def current) {
        Pre p = of(titan);
        if (p == null || p.gate().isEmpty()) {
            return;
        }
        if (preOf(back, group, titan, current) != null) {
            GroupGates.open(back, group, p.gate());
        } else {
            GroupGates.seal(back, group, p.gate());
        }
    }

    public static boolean has(String titan) {
        return of(titan) != null && !Titans.TIDE_SHELL.equals(titan);
    }
}

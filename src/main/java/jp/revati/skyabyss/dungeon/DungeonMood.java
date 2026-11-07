package jp.revati.skyabyss.dungeon;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 日替わりの「坑の様子」(docs/design/55 の 2 章。2026-11-05 のダンジョンの頁 1-3)。ダンジョンごとに、その日の四つのうち一つ (全員同じ)。
//   宝の日  主が倒れると主の間に宝箱がもう一つ (中身は二層目の箱と同じ。人ごとに受け取る)
//   罠の日  罠が二度続けて落ち、休みが半分。越えた経験値 +30%
//   鍵の日  鍵持ちが二人 (二人とも倒すと鍵が開く)
//   群れの日 道中の敵が 1.5 倍 (二つに一つの印でもう一体)。越えたときの粉 +1
// 分かる所: 層の門の名札・門のそばの知らせ・入ったときの知らせ・土地の町の案内人の「坑の様子を聞く」
public enum DungeonMood {
    TREASURE("treasure"), TRAPS("traps"), KEYS("keys"), SWARM("swarm");

    public static final double TRAP_XP = 0.30;

    public final String id;

    DungeonMood(String id) {
        this.id = id;
    }

    public static DungeonMood today(Dungeon d) {
        return of(jp.revati.skyabyss.util.DayClock.today().toEpochDay(), d);
    }

    // 日とダンジョンで決まる (四つのダンジョンは同じ日に違う様子になり、一つのダンジョンは日ごとに巡る)
    public static DungeonMood of(long day, Dungeon d) {
        return values()[(int) Math.floorMod(day + d.ordinal(), (long) values().length)];
    }

    public String nameKey() {
        return "dungeon.skyabyss.mood." + id;
    }

    public String descKey() {
        return nameKey() + ".desc";
    }

    // 土地の町の案内人: その土地のダンジョンの今日の様子を一行で
    public static void tell(ServerPlayer p, @Nullable Entity npc) {
        int region = ContinentMap.get().region(npc != null ? npc.getBlockX() : p.getBlockX(), npc != null ? npc.getBlockZ() : p.getBlockZ());
        Dungeon d = null;
        for (Dungeon x : Dungeon.mids()) {
            if (x.region == region) {
                d = x;
            }
        }
        if (d == null) {
            d = Dungeon.BONE_PIT;
        }
        DungeonMood m = today(d);
        Notice.hint(p, "message.skyabyss.dungeon.mood_tell", Component.translatable(d.nameKey()), Component.translatable(m.nameKey()),
                Component.translatable(m.descKey()));
    }
}

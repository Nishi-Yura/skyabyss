package jp.revati.skyabyss.trial;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

// 順路の周の区切り (docs/design/32 の 3 章): 心臓を止めたときの歌片・順路の記録・周の進み・祠、グループのリセットの後始末。
// 図鑑の修飾の数え (modKeys・modTotal) もここ
public final class TrialRuntime {
    private TrialRuntime() {
    }

    // 出会った修飾の印 (図鑑の「出会った修飾」の数え)
    public static Set<String> modKeys(Def def, Mods mods) {
        Set<String> keys = new LinkedHashSet<>();
        Trial.Space sp = def.space();
        if (sp.layouts() > 1) {
            keys.add("layout:" + mods.layout());
        }
        for (int i = 0; i < sp.numbers().size(); i++) {
            keys.add("number:" + sp.numbers().get(i) + ":" + mods.number(i));
        }
        if (mods.enemy() >= 0 && sp.enemySets() > 1) {
            keys.add("enemy:" + mods.enemy());
        }
        for (int h : mods.hazards()) {
            keys.add("hazard:" + sp.hazards().get(h));
        }
        if (mods.goal() >= 0) {
            keys.add("goal:" + sp.goals().get(mods.goal()));
        }
        if (mods.extreme() != Trial.Extreme.NONE) {
            keys.add("extreme:" + mods.extreme().id());
        }
        return keys;
    }

    // 図鑑の「全部」の数 (その試練に出る修飾の印の総数)
    public static int modTotal(Def def) {
        Trial.Space sp = def.space();
        int n = sp.layouts() > 1 ? sp.layouts() : 0;
        n += sp.numbers().size() * 3;
        n += sp.enemySets() > 1 ? sp.enemySets() : 0;
        n += sp.hazards().size() + sp.goals().size() + sp.extremes().size();
        return n;
    }

    // ---------------------------------------------------------------- 心臓を止めた: 周を進める
    public static void onHeartStopped(ServerLevel back, java.util.Collection<UUID> fighters) {
        for (UUID player : fighters) {
            UUID group = Groups.keyOf(back.getServer(), player);
            BackGroupData.Record r = GroupProgress.of(back, group);
            String epoch = group + "@" + r.epoch();
            // 歌片 (順路の鍵。docs/design/32 の 3.2): 心臓を止めるたびに、戦った人に一つ (次の周の分。初回も渡す)
            if (shardGiven.add(player + "@" + epoch)) {
                net.minecraft.server.level.ServerPlayer p = back.getServer().getPlayerList().getPlayer(player);
                String titan = Titans.titanOfBack(back.dimension());
                if (p != null && titan != null) {
                    jp.revati.skyabyss.pouch.PouchRouting.placeBack(p, new ItemStack(jp.revati.skyabyss.registry.ModItems.songShard(titan)));
                    Notice.gain(p, ModText.progress("message.skyabyss.shrine.shard_gained", jp.revati.skyabyss.registry.ModItems.songShard(titan).getDescription()));
                }
            }
            if (heartCounted.add(epoch)) {
                // 順路の記録 (越えた回数・最速) を控えてから、周を進める (順路の記録も消える)
                TrialRoute.onHeartStopped(back, r);
                TrialData.onHeartStopped(r);
                // 巨神の祠を建てる (まだなら。周回の入口)
                String titan = Titans.titanOfBack(back.dimension());
                if (titan != null) {
                    jp.revati.skyabyss.titan.shrine.TitanShrine.ensure(back.getServer(), titan);
                }
            }
        }
    }

    // 歌片を渡した (人@グループ@epoch)
    private static final Set<String> shardGiven = new LinkedHashSet<>();

    // 同じ周の同じ心臓で二重に数えない (グループごとに epoch が同じうちは一度)
    private static final Set<String> heartCounted = new LinkedHashSet<>();

    // 進みが戻った (resetGroup): 順路を終える。引きは keep.* なので残る
    public static void onGroupReset(ServerLevel back, UUID group) {
        heartCounted.removeIf(s -> s.startsWith(group + "@"));
        TrialRoute.finish(back, group);
    }

    // 自己テスト・開発用: 状態を全部捨てる
    public static void clearAll() {
        heartCounted.clear();
        shardGiven.clear();
        TrialRoute.clearAll();
    }
}

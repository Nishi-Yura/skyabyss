package jp.revati.skyabyss.titan.vow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.end.TitanEnd;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.FirstRunData;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 誓い (Vow) の選び方・効き方・報酬 (10 の 10.12)。周回 (そのグループがその巨神の first_cleared を持つ) だけの縛り。
//
//  置き場       : そのグループのその巨神の背中の記録 (BackGroupData.Record) の `keep.vows` (id のコンマ区切り)。パーティの記録ごと写る
//                 (組む・入る・抜ける。10.7)。"keep." なので試練のリセットでは消えない。心臓を止めたとき (報酬のあと) と clear で消える
//  選べる時     : 周回で、まだ試練を一つも進めていないあいだ (Record.hasProgress が false。乗る前・最初の休み場まで)。最大 3 つ
//  効き方       : Vow の数字 (体力 +30% は VowEvents、予告の短縮は Telegraph から telegraphWarn、野営の禁止・受ける傷・時間制限は VowEvents)
//  報酬         : 誓いの段 (選んだ誓いの points の合計。時間制限を破った誓いは外す) に応じて、巨神の素材を (倍率 - 1) の分だけ上乗せ、
//                 共鳴の欠片が出る確率、称号 (飾りだけ)。装備の確率・天井は動かさない (03 の曲線のまま)。最高の段は人ごとに控え帳へ
public final class Vows {
    public static final int MAX_VOWS = 3;
    // 一段ごとの素材の倍率の上乗せ (1 + 0.2 * 段)
    public static final float MULTIPLIER_PER_LEVEL = 0.2f;
    // 素材の上乗せの元の数 (これに (倍率 - 1) をかける。段 6 で 12 個)
    public static final int BONUS_MATERIALS = 10;
    // 共鳴の欠片が出る確率 (一段ごと)。出れば SHARD_COUNT 個
    public static final float SHARD_CHANCE_PER_LEVEL = 0.08f;
    public static final int SHARD_COUNT = 3;
    // 称号 (飾り) の段の境: 1〜2 / 3〜4 / 5 / 6
    public static final int[] TITLE_FROM = {1, 3, 5, 6};

    static final String KEY_VOWS = "keep.vows";
    static final String KEY_START = "keep.vow_start";

    private static final Map<String, Vow> REGISTRY = new LinkedHashMap<>();

    static {
        // id, 重さ, 敵の体力, 予告, 受ける傷, 野営禁止, 時間制限
        register(new Vow("hardy", 2, 0.30f, 1f, 0f, false, 0));
        register(new Vow("sharp", 2, 0f, 0.75f, 0f, false, 0));
        register(new Vow("thin", 2, 0f, 1f, 0.25f, false, 0));
        register(new Vow("bare", 1, 0f, 1f, 0f, true, 0));
        register(new Vow("hasty", 1, 0f, 1f, 0f, false, 45 * 60 * 20));
    }

    private Vows() {
    }

    // ---------------------------------------------------------------- 登録

    public static synchronized void register(Vow vow) {
        REGISTRY.put(vow.id(), vow);
    }

    public static synchronized void unregister(String id) {
        REGISTRY.remove(id);
    }

    @Nullable
    public static synchronized Vow get(String id) {
        return REGISTRY.get(id);
    }

    public static synchronized List<Vow> all() {
        return new ArrayList<>(REGISTRY.values());
    }

    // ---------------------------------------------------------------- 段・倍率・称号

    public static int level(Collection<Vow> vows) {
        int n = 0;
        for (Vow v : vows) {
            n += v.points();
        }
        return n;
    }

    public static float multiplier(int level) {
        return 1f + MULTIPLIER_PER_LEVEL * Math.max(0, level);
    }

    public static int bonusMaterials(int level) {
        return Math.round(BONUS_MATERIALS * (multiplier(level) - 1f));
    }

    public static float shardChance(int level) {
        return Math.min(1f, SHARD_CHANCE_PER_LEVEL * Math.max(0, level));
    }

    // 称号の段 (0 = まだない)。1..TITLE_FROM.length
    public static int titleTier(int level) {
        int tier = 0;
        for (int i = 0; i < TITLE_FROM.length; i++) {
            if (level >= TITLE_FROM[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    // ---------------------------------------------------------------- 記録の読み書き

    @Nullable
    public static ServerLevel backLevel(MinecraftServer server, String titan) {
        var dim = Titans.backOf(titan);
        return dim == null ? null : server.getLevel(dim);
    }

    // そのグループが今その背中に立てている誓い (記録がなければ空)
    public static List<Vow> active(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return r == null ? List.of() : parse(r.getString(KEY_VOWS));
    }

    private static List<Vow> parse(String csv) {
        List<Vow> out = new ArrayList<>();
        if (csv.isEmpty()) {
            return out;
        }
        for (String id : csv.split(",")) {
            Vow v = get(id);
            if (v != null && !out.contains(v)) {
                out.add(v);
            }
        }
        return out;
    }

    // 背中にいる人 (や、その背中のグループの敵) のグループの誓い。背中でなければ空
    public static List<Vow> activeFor(@Nullable Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || !jp.revati.skyabyss.titan.TitanBacks.isBack(level.dimension())) {
            return List.of();
        }
        UUID group = entity instanceof GroupOwned owned && owned.groupId() != null ? owned.groupId() : Groups.keyOf(entity);
        return group == null ? List.of() : active(level, group);
    }

    private static void store(ServerLevel back, UUID group, List<Vow> vows) {
        BackGroupData.Record r = BackGroupData.get(back).of(group);
        if (vows.isEmpty()) {
            r.remove(KEY_VOWS);
            r.remove(KEY_START);
        } else {
            StringBuilder sb = new StringBuilder();
            for (Vow v : vows) {
                sb.append(sb.length() == 0 ? "" : ",").append(v.id());
            }
            r.putString(KEY_VOWS, sb.toString());
        }
    }

    // ---------------------------------------------------------------- 選ぶ

    public enum Result {
        OK, NO_BACK, NOT_REPEAT, STARTED, UNKNOWN, FULL, ALREADY, NOT_SET
    }

    // いま誓いを選び直せるか。周回で、順路がまだ動いていない (選ぶ場所は祠の画面。VowCommands が祠のそばかを見る。docs/design/32 の 3.1)
    public static Result canChange(MinecraftServer server, UUID group, String titan) {
        ServerLevel back = backLevel(server, titan);
        if (back == null) {
            return Result.NO_BACK;
        }
        if (FirstRun.isFirstRun(back, group)) {
            return Result.NOT_REPEAT;
        }
        return BackGroupData.get(back).of(group).hasProgress() || jp.revati.skyabyss.trial.TrialRoute.active(back, group) ? Result.STARTED : Result.OK;
    }

    // 順路が終わった (あきらめ・背を出た): 誓いを降ろす (心臓を止めたときは onHeartStopped が報いのあとに降ろす)
    public static void lift(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null || !r.has(KEY_VOWS)) {
            return;
        }
        store(back, group, List.of());
        VowNetwork.syncGroup(back.getServer(), group);
    }

    public static Result add(MinecraftServer server, UUID group, String titan, String vowId) {
        Result can = canChange(server, group, titan);
        if (can != Result.OK) {
            return can;
        }
        Vow vow = get(vowId);
        if (vow == null) {
            return Result.UNKNOWN;
        }
        ServerLevel back = backLevel(server, titan);
        List<Vow> now = new ArrayList<>(active(back, group));
        if (now.contains(vow)) {
            return Result.ALREADY;
        }
        if (now.size() >= MAX_VOWS) {
            return Result.FULL;
        }
        now.add(vow);
        store(back, group, now);
        VowNetwork.syncGroup(server, group);
        return Result.OK;
    }

    public static Result remove(MinecraftServer server, UUID group, String titan, String vowId) {
        Result can = canChange(server, group, titan);
        if (can != Result.OK) {
            return can;
        }
        ServerLevel back = backLevel(server, titan);
        List<Vow> now = new ArrayList<>(active(back, group));
        if (!now.removeIf(v -> v.id().equals(vowId))) {
            return Result.NOT_SET;
        }
        store(back, group, now);
        VowNetwork.syncGroup(server, group);
        return Result.OK;
    }

    public static Result clear(MinecraftServer server, UUID group, String titan) {
        Result can = canChange(server, group, titan);
        if (can != Result.OK) {
            return can;
        }
        store(backLevel(server, titan), group, List.of());
        VowNetwork.syncGroup(server, group);
        return Result.OK;
    }

    // ---------------------------------------------------------------- 時間制限

    // 時間制限の誓いがあって、まだ始まっていなければ今を始まりにする (背中に誰かが入ったとき VowEvents が呼ぶ)
    static void startTimerIfNeeded(ServerLevel back, UUID group, long now) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r != null && r.getLong(KEY_START) == 0 && timerTicks(parse(r.getString(KEY_VOWS))) > 0) {
            r.putLong(KEY_START, now);
        }
    }

    static int timerTicks(Collection<Vow> vows) {
        int best = 0;
        for (Vow v : vows) {
            if (v.timerTicks() > 0) {
                best = best == 0 ? v.timerTicks() : Math.min(best, v.timerTicks());
            }
        }
        return best;
    }

    // 残りの tick (時間制限がない・まだ始まっていないなら -1。過ぎていれば 0)
    public static long timerRemaining(ServerLevel back, UUID group, long now) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null) {
            return -1;
        }
        int limit = timerTicks(parse(r.getString(KEY_VOWS)));
        long start = r.getLong(KEY_START);
        return limit == 0 || start == 0 ? -1 : Math.max(0, limit - (now - start));
    }

    // 報酬に数える誓い: 時間制限のある誓いは、制限を過ぎていたら外す
    public static List<Vow> effective(ServerLevel back, UUID group, long now) {
        List<Vow> vows = active(back, group);
        boolean expired = timerRemaining(back, group, now) == 0;
        if (!expired) {
            return vows;
        }
        List<Vow> out = new ArrayList<>();
        for (Vow v : vows) {
            if (v.timerTicks() == 0) {
                out.add(v);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 予告の短縮 (Telegraph から)

    // 技の予告の長さ warn (tick) に、持ち主のグループの誓いを当てる。最短 Telegraph.MIN_WARN_TICKS (0.8 秒) は割らない
    public static int telegraphWarn(@Nullable Entity owner, int warn) {
        if (owner == null) {
            return warn;
        }
        List<Vow> vows = activeFor(owner);
        if (vows.isEmpty()) {
            return warn;
        }
        return scaleWarn(vows, warn);
    }

    public static int scaleWarn(Collection<Vow> vows, int warn) {
        float scale = 1f;
        for (Vow v : vows) {
            scale *= v.telegraphScale();
        }
        return scale >= 1f ? warn : Math.max(jp.revati.skyabyss.combat.Telegraph.MIN_WARN_TICKS, Math.round(warn * scale));
    }

    // ---------------------------------------------------------------- 心臓を止めた (報酬)

    // TitanEnd.onHeartStopped から。戦った人のグループごとに、その背中の誓いで報酬を上乗せし、誓いを降ろす
    public static void onHeartStopped(ServerLevel back, String titan, Collection<UUID> fought) {
        MinecraftServer server = back.getServer();
        long now = server.overworld().getGameTime();
        Set<UUID> groups = new LinkedHashSet<>();
        for (UUID id : fought) {
            groups.add(Groups.keyOf(server, id));
        }
        for (UUID group : groups) {
            List<Vow> vows = active(back, group);
            if (vows.isEmpty()) {
                continue;
            }
            List<Vow> counted = effective(back, group, now);
            int level = level(counted);
            boolean lost = counted.size() < vows.size();
            for (ServerPlayer p : TitanEnd.targets(server, back, fought)) {
                if (group.equals(Groups.keyOf(p))) {
                    reward(p, titan, level, lost, back.random);
                }
            }
            store(back, group, List.of());
            VowNetwork.syncGroup(server, group);
            SkyAbyss.LOGGER.info("Group {} finished {} under vows {} (level {}{})", group, titan, vows.stream().map(Vow::id).toList(), level,
                    lost ? ", timer broken" : "");
        }
    }

    // 一人への報酬。最高の段の記録・素材の上乗せ・共鳴の欠片・称号の知らせ
    public static void reward(ServerPlayer p, String titan, int level, boolean timerBroken, RandomSource random) {
        reward(p, titan, level, timerBroken, random.nextFloat());
    }

    // roll: 共鳴の欠片の抽選の目 (0 以上 1 未満。shardChance(level) より小さければ出る)。自己テストが目を決める
    public static void reward(ServerPlayer p, String titan, int level, boolean timerBroken, float roll) {
        if (timerBroken) {
            Notice.hint(p, "message.skyabyss.vow.timer_broken");
        }
        if (level <= 0) {
            return;
        }
        int mats = bonusMaterials(level);
        if (mats > 0) {
            give(p, new ItemStack(materialOf(titan), mats));
        }
        boolean shard = roll < shardChance(level);
        if (shard) {
            give(p, new ItemStack(resonanceShardOf(titan), SHARD_COUNT));
        }
        Notice.gain(p, jp.revati.skyabyss.ModText.progress("message.skyabyss.vow.reward", level, Math.round(multiplier(level) * 100), mats)
                .append(shard ? jp.revati.skyabyss.ModText.progress("message.skyabyss.vow.reward_shard", SHARD_COUNT) : net.minecraft.network.chat.Component.empty()));
        if (FirstRunData.get(p.server).recordVowBest(p.getUUID(), titan, level)) {
            int tier = titleTier(level);
            Notice.hint(p, "message.skyabyss.vow.best", level);
            if (tier > 0) {
                Notice.unlock(p, "message.skyabyss.vow.title", net.minecraft.network.chat.Component.translatable(titleKey(tier)));
            }
        }
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        jp.revati.skyabyss.pouch.PouchRouting.placeBack(p, stack);
    }

    public static String titleKey(int tier) {
        return "vow.skyabyss.title." + tier;
    }

    public static Item materialOf(String titan) {
        TitanGearSet set = TitanGearSets.get(titan);
        Item shard = set == null ? null : set.shardItem().orElse(null);
        return shard != null ? shard : ModItems.STONE_WHALE_SHARD.get();
    }

    public static Item resonanceShardOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> ModItems.RESONANCE_SHARD_TIDE_SHELL.get();
            case Titans.THUNDER_WING -> ModItems.RESONANCE_SHARD_THUNDER_WING.get();
            case Titans.EMBER -> ModItems.RESONANCE_SHARD_EMBER.get();
            default -> ModItems.RESONANCE_SHARD_STONE_WHALE.get();
        };
    }
}

package jp.revati.skyabyss.titan.end;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 巨神の眠り (docs/design/01 の 4.3、02 の 7 章)。心臓を止めても巨神は死なない。借りものの鼓動が止まり、深い眠りに落ちる。
// 鼓動は遠い下からゆっくり戻り、巨神はまた目を覚ます (背中の試練が元に戻る時 = 目覚め)。
//
//   begin       : 心臓が止まった (TitanEnd.onHeartStopped)。visualDelay tick あとから「眠る姿」(entity の REST) が始まる。
//                 その前から、巨神への乗り込みは断る (isResting)
//   requestWake : 背中の試練が元に戻った (各巨神の背の reset)。眠りは少なくとも MIN_TICKS は続け、そのあとで目覚める
//   目覚め      : 眠りが最短を過ぎ (requestWake の時刻)、かつ背中の中で続けているグループがいなくなったとき (GroupProgress.activeInside)。
//                 眠りが MAX_TICKS を超えても、続けているグループがいる間は待つ (HARD_MAX_TICKS が安全弁)
//   眠りは世界に一つ: どれかのグループが心臓を止めると巨神は元の世界から去り、新しく乗ることはだれにもできなくなる (isResting)。
//   すでに背中にいるグループは、そのまま試練・中ボス・心臓まで続けられる (自分の心臓を止めれば、同じ報酬・運び出し・場面)。眠りは数え直さない
//
// 保存する (オーバーワールドの SavedData)。時刻はオーバーワールドのゲーム時間。巨神の本体 (entity) は毎 tick これを見て、動かず・歌わず・光らずに過ごす
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanDormancy extends SavedData {
    // 眠りの最短 (90 秒)・最長 (15 分)
    public static final long MIN_TICKS = 20L * 90;
    public static final long MAX_TICKS = 20L * 60 * 15;
    // 背中の中で続けているグループがいるときの最長 (60 分)。それでも居座る人がいても、巨神が眠りっぱなしにならない安全弁
    public static final long HARD_MAX_TICKS = 20L * 60 * 60;
    // entity の REST の同期は、これを超えたら止める (光が消えきったあとは動かない)
    public static final int SYNC_CAP = 600;
    // 去る (docs/design/02 の 7.2節): 眠る姿が始まって DEPART_START tick (10 秒。眠りの場面の前半) までは、各巨神ごとの眠り方で落ち着く。
    // そこから去りはじめ (岩鯨は昇る・潮殻は沈みきる・雷翼は飛び去る・余燼は土へ潜る)、DEPART_TICKS tick (40 秒) で本体を地上から取り除く。
    // 眠っているあいだは見守りも呼び直さない (TitanKeeper)。目覚めたとき、見守りが遠くから呼び直す
    public static final int DEPART_START = 200;
    public static final int DEPART_TICKS = 800;

    private static final Factory<TitanDormancy> FACTORY = new Factory<>(TitanDormancy::new, TitanDormancy::load);

    // 巨神一体の眠り。since = 眠る姿が始まる時刻 (心臓が止まった時刻より後でもよい)、wakeAt = 目覚める時刻 (Long.MAX_VALUE = まだ決まっていない)
    public static final class Rest {
        public long since;
        public long wakeAt = Long.MAX_VALUE;
        // 眠る場所 (雷翼の峰など。使わない巨神は hasSpot = false)
        public boolean hasSpot;
        public double spotX;
        public double spotY;
        public double spotZ;

        @Nullable
        public Vec3 spot() {
            return hasSpot ? new Vec3(spotX, spotY, spotZ) : null;
        }
    }

    private final Map<String, Rest> rests = new LinkedHashMap<>();

    public static TitanDormancy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_titan_rest");
    }

    // 自己テスト用: 保存の読み戻しで取り出した眠りの記録
    Map<String, Rest> rests() {
        return rests;
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---------------------------------------------------------------- 問い合わせ

    // 眠っている (または、これから眠る) か。乗り込みを断る・光る印を消す・呼び笛を断る判定に使う
    public static boolean isResting(MinecraftServer server, String titan) {
        return get(server).rests.containsKey(titan);
    }

    // 眠る姿が始まっているか (沈む・歌をやめる・光が消える)
    public static boolean isDormant(MinecraftServer server, String titan) {
        Rest r = get(server).rests.get(titan);
        return r != null && now(server) >= r.since;
    }

    // 去りはじめているか (眠る姿が始まって DEPART_START tick 以降)
    public static boolean isDeparting(int age) {
        return age >= DEPART_START;
    }

    // 去る動きの進み (0〜1。DEPART_START → DEPART_TICKS)
    public static float departProgress(int age) {
        return net.minecraft.util.Mth.clamp((age - DEPART_START) / (float) (DEPART_TICKS - DEPART_START), 0f, 1f);
    }

    // 眠る姿が始まってからの tick。眠っていない・まだ始まっていなければ -1
    public static int age(MinecraftServer server, String titan) {
        Rest r = get(server).rests.get(titan);
        long now = now(server);
        if (r == null || now < r.since) {
            return -1;
        }
        return (int) Math.min(Integer.MAX_VALUE, now - r.since);
    }

    @Nullable
    public static Rest rest(MinecraftServer server, String titan) {
        return get(server).rests.get(titan);
    }

    // ---------------------------------------------------------------- 始める・目覚めさせる

    // 心臓が止まった。visualDelay tick あとから眠る姿になる。すでに眠っているときは数え直さない (続けて止めても眠りは延びない)
    public static Rest begin(MinecraftServer server, String titan, long visualDelay) {
        TitanDormancy data = get(server);
        Rest r = data.rests.get(titan);
        if (r != null) {
            return r;
        }
        r = new Rest();
        r.since = now(server) + Math.max(0, visualDelay);
        data.rests.put(titan, r);
        data.setDirty();
        SkyAbyss.LOGGER.info("The {} fell into its sleep (visible from tick {})", titan, r.since);
        return r;
    }

    // 自己テスト用: 眠りが ticks だけ前から始まっていたことにする (時間を飛ばす。ゲーム時間は進めない)
    public static void backdate(MinecraftServer server, String titan, long ticks) {
        TitanDormancy data = get(server);
        Rest r = data.rests.get(titan);
        if (r != null) {
            r.since -= ticks;
            if (r.wakeAt != Long.MAX_VALUE) {
                r.wakeAt -= ticks;
            }
            data.setDirty();
        }
    }

    // 眠りの場所を決める
    public static void setSpot(MinecraftServer server, String titan, double x, double y, double z) {
        TitanDormancy data = get(server);
        Rest r = data.rests.get(titan);
        if (r != null) {
            r.hasSpot = true;
            r.spotX = x;
            r.spotY = y;
            r.spotZ = z;
            data.setDirty();
        }
    }

    // 背中の試練が元に戻った: 眠りが最短を過ぎたら目覚める。眠っていなければ何もしない
    public static void requestWake(MinecraftServer server, String titan) {
        TitanDormancy data = get(server);
        Rest r = data.rests.get(titan);
        if (r == null) {
            return;
        }
        long at = Math.max(now(server), r.since + MIN_TICKS);
        if (at < r.wakeAt) {
            r.wakeAt = at;
            data.setDirty();
        }
    }

    // いますぐ目覚めさせる (コマンド・自己テスト)。眠っていたら true
    public static boolean wakeNow(MinecraftServer server, String titan) {
        TitanDormancy data = get(server);
        if (data.rests.remove(titan) == null) {
            return false;
        }
        data.setDirty();
        fireWake(server, titan);
        return true;
    }

    // 自己テスト用: 眠りを残さず消す (目覚めの知らせは出さない)
    public static void clear(MinecraftServer server, String titan) {
        TitanDormancy data = get(server);
        if (data.rests.remove(titan) != null) {
            data.setDirty();
        }
    }

    private static void fireWake(MinecraftServer server, String titan) {
        SkyAbyss.LOGGER.info("The {} woke from its sleep", titan);
        TitanEnd.onWake(server, titan);
    }

    // 1 秒に 1 回、目覚める時刻を見る
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 3) {
            tickWake(event.getServer());
        }
    }

    // その巨神の背中の中で続けているグループの数 (背中がなければ 0)
    public static int activeGroups(MinecraftServer server, String titan) {
        var dim = jp.revati.skyabyss.progress.Titans.backOf(titan);
        net.minecraft.server.level.ServerLevel back = dim == null ? null : server.getLevel(dim);
        return back == null ? 0 : jp.revati.skyabyss.titan.group.GroupProgress.activeInside(back);
    }

    // 目覚める時刻を過ぎた巨神を起こす。起こした数 (自己テストは時刻をずらして直接呼ぶ)
    public static int tickWake(MinecraftServer server) {
        TitanDormancy data = get(server);
        if (data.rests.isEmpty()) {
            return 0;
        }
        long now = now(server);
        List<String> woken = new ArrayList<>();
        data.rests.forEach((titan, r) -> {
            boolean due = now >= r.wakeAt || now >= r.since + MAX_TICKS;
            if (due && (now >= r.since + HARD_MAX_TICKS || activeGroups(server, titan) == 0)) {
                woken.add(titan);
            }
        });
        for (String titan : woken) {
            wakeNow(server, titan);
        }
        return woken.size();
    }

    // ---------------------------------------------------------------- 保存

    static TitanDormancy load(CompoundTag tag, HolderLookup.Provider registries) {
        TitanDormancy data = new TitanDormancy();
        CompoundTag all = tag.getCompound("Rests");
        for (String key : all.getAllKeys()) {
            CompoundTag t = all.getCompound(key);
            Rest r = new Rest();
            r.since = t.getLong("Since");
            r.wakeAt = t.contains("WakeAt") ? t.getLong("WakeAt") : Long.MAX_VALUE;
            r.hasSpot = t.getBoolean("HasSpot");
            r.spotX = t.getDouble("SpotX");
            r.spotY = t.getDouble("SpotY");
            r.spotZ = t.getDouble("SpotZ");
            data.rests.put(key, r);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        rests.forEach((titan, r) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("Since", r.since);
            if (r.wakeAt != Long.MAX_VALUE) {
                t.putLong("WakeAt", r.wakeAt);
            }
            t.putBoolean("HasSpot", r.hasSpot);
            t.putDouble("SpotX", r.spotX);
            t.putDouble("SpotY", r.spotY);
            t.putDouble("SpotZ", r.spotZ);
            all.put(titan, t);
        });
        tag.put("Rests", all);
        return tag;
    }
}

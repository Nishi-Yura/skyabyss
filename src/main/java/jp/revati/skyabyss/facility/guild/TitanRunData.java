package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;

// 背中の踏破の記録 (docs/design/10 の 7.2「ギルド同士で競うもの」)。ワールドに一つ (オーバーワールドの SavedData)。
//   最速    巨神ごと・人数の部 (1・2・3・4 人以上) ごとに、順路を始めてから心臓を止めるまでの時間の最速 10 件 (人の名前とギルドの名前)
//   週の数  ゲーム内の 7 日ごとの、ギルドの団員が止めた心臓の数 (人ごとに一つ。同じ順路の団員はそれぞれ数える)
// 記録しない回: 一緒にいた人の誰かが創造・観戦のモード (管理者の確かめの回)。記録するのは TrialRoute.onHeartStopped
public final class TitanRunData extends SavedData {
    public static final int TOP = 10;
    public static final int MAX_BRACKET = 4;
    static final long WEEK_TICKS = 7L * 24000L;
    private static final String NAME = "skyabyss_titan_runs";
    private static final SavedData.Factory<TitanRunData> FACTORY = new SavedData.Factory<>(TitanRunData::new, TitanRunData::load);

    public record Entry(long ticks, List<String> names, List<String> guilds, long day) {
    }

    // 巨神 → 人数の部 → 最速の並び
    final Map<String, Map<Integer, List<Entry>>> best = new HashMap<>();
    // 週の番号 → ギルドの名前 → 心臓の数
    final Map<Long, Map<String, Integer>> weekly = new HashMap<>();

    public static TitanRunData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static int bracket(int players) {
        return Math.max(1, Math.min(MAX_BRACKET, players));
    }

    public List<Entry> top(String titan, int bracket) {
        return best.getOrDefault(titan, Map.of()).getOrDefault(bracket, List.of());
    }

    public Map<String, Integer> week(long week) {
        return weekly.getOrDefault(week, Map.of());
    }

    public static long weekOf(long gameTime) {
        return gameTime / WEEK_TICKS;
    }

    // 順路を越えた。記録に残れば、その順位 (1 始まり)。残らない・記録しない回は 0
    public int record(MinecraftServer server, String titan, long ticks, List<ServerPlayer> party, long gameTime) {
        if (party.isEmpty()) {
            return 0;
        }
        for (ServerPlayer p : party) {
            if (p.gameMode.getGameModeForPlayer() == GameType.CREATIVE || p.isSpectator()) {
                return 0;
            }
        }
        List<String> names = new ArrayList<>();
        List<String> guilds = new ArrayList<>();
        Map<String, Integer> week = weekly.computeIfAbsent(weekOf(gameTime), k -> new HashMap<>());
        for (ServerPlayer p : party) {
            names.add(p.getGameProfile().getName());
            GuildData.Guild g = Guilds.of(p);
            if (g != null) {
                if (!guilds.contains(g.name)) {
                    guilds.add(g.name);
                }
                week.merge(g.name, 1, Integer::sum);
            }
        }
        // 古い週は 4 週ぶんだけ残す
        weekly.keySet().removeIf(w -> w < weekOf(gameTime) - 4);
        List<Entry> list = new ArrayList<>(top(titan, bracket(party.size())));
        Entry e = new Entry(ticks, names, guilds, gameTime / 24000L);
        list.add(e);
        list.sort(Comparator.comparingLong(Entry::ticks));
        int rank = list.indexOf(e) + 1;
        if (list.size() > TOP) {
            list = new ArrayList<>(list.subList(0, TOP));
        }
        best.computeIfAbsent(titan, k -> new HashMap<>()).put(bracket(party.size()), list);
        setDirty();
        return rank <= TOP ? rank : 0;
    }

    public static String clock(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // 記録を一人に見せる (チャットの行)。titan = null なら四巨神の 1 人の部と 4 人以上の部の一位、と今週のギルドの上位 3
    public static void show(ServerPlayer p, String titan) {
        TitanRunData d = get(p.server);
        List<String> titans = titan == null ? jp.revati.skyabyss.progress.Titans.all() : List.of(titan);
        p.sendSystemMessage(Component.translatable("message.skyabyss.runs.title").withStyle(ChatFormatting.GOLD));
        for (String t : titans) {
            Component name = Component.translatable("entity.skyabyss." + (t.equals(jp.revati.skyabyss.progress.Titans.EMBER) ? "ember_titan" : t));
            for (int b = 1; b <= MAX_BRACKET; b++) {
                List<Entry> list = d.top(t, b);
                int shown = titan == null ? Math.min(1, list.size()) : list.size();
                for (int i = 0; i < shown; i++) {
                    Entry e = list.get(i);
                    p.sendSystemMessage(Component.translatable("message.skyabyss.runs.line", name, Component.translatable("message.skyabyss.runs.bracket." + b), i + 1,
                            clock(e.ticks()), String.join("・", e.names()), e.guilds().isEmpty() ? "-" : String.join("・", e.guilds())));
                }
                if (titan != null && list.isEmpty()) {
                    p.sendSystemMessage(Component.translatable("message.skyabyss.runs.none", name, Component.translatable("message.skyabyss.runs.bracket." + b)));
                }
            }
        }
        List<Map.Entry<String, Integer>> week = new ArrayList<>(d.week(weekOf(p.serverLevel().getGameTime())).entrySet());
        week.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (week.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.runs.week_none"));
        }
        for (int i = 0; i < Math.min(3, week.size()); i++) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.runs.week", i + 1, week.get(i).getKey(), week.get(i).getValue()));
        }
    }

    // ---------------------------------------------------------------- 保存
    static TitanRunData load(CompoundTag tag, HolderLookup.Provider registries) {
        TitanRunData d = new TitanRunData();
        CompoundTag b = tag.getCompound("best");
        for (String titan : b.getAllKeys()) {
            CompoundTag per = b.getCompound(titan);
            Map<Integer, List<Entry>> m = new HashMap<>();
            for (String k : per.getAllKeys()) {
                List<Entry> list = new ArrayList<>();
                for (Tag t : per.getList(k, Tag.TAG_COMPOUND)) {
                    CompoundTag c = (CompoundTag) t;
                    list.add(new Entry(c.getLong("ticks"), strings(c.getList("names", Tag.TAG_STRING)), strings(c.getList("guilds", Tag.TAG_STRING)), c.getLong("day")));
                }
                try {
                    m.put(Integer.parseInt(k), list);
                } catch (NumberFormatException e) {
                    SkyAbyss.LOGGER.warn("titan runs: bad bracket {}", k);
                }
            }
            d.best.put(titan, m);
        }
        CompoundTag w = tag.getCompound("weekly");
        for (String k : w.getAllKeys()) {
            CompoundTag c = w.getCompound(k);
            Map<String, Integer> m = new HashMap<>();
            for (String g : c.getAllKeys()) {
                m.put(g, c.getInt(g));
            }
            try {
                d.weekly.put(Long.parseLong(k), m);
            } catch (NumberFormatException e) {
                SkyAbyss.LOGGER.warn("titan runs: bad week {}", k);
            }
        }
        return d;
    }

    private static List<String> strings(ListTag l) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            out.add(l.getString(i));
        }
        return out;
    }

    private static ListTag tags(List<String> l) {
        ListTag out = new ListTag();
        for (String s : l) {
            out.add(StringTag.valueOf(s));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag b = new CompoundTag();
        best.forEach((titan, per) -> {
            CompoundTag c = new CompoundTag();
            per.forEach((k, list) -> {
                ListTag l = new ListTag();
                for (Entry e : list) {
                    CompoundTag x = new CompoundTag();
                    x.putLong("ticks", e.ticks());
                    x.put("names", tags(e.names()));
                    x.put("guilds", tags(e.guilds()));
                    x.putLong("day", e.day());
                    l.add(x);
                }
                c.put(Integer.toString(k), l);
            });
            b.put(titan, c);
        });
        tag.put("best", b);
        CompoundTag w = new CompoundTag();
        weekly.forEach((k, m) -> {
            CompoundTag c = new CompoundTag();
            m.forEach(c::putInt);
            w.put(Long.toString(k), c);
        });
        tag.put("weekly", w);
        return tag;
    }

    // 自己テスト用
    public void clear() {
        best.clear();
        weekly.clear();
        setDirty();
    }
}

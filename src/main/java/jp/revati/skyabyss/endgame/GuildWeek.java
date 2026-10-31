package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.guild.GuildData;
import jp.revati.skyabyss.facility.guild.Guilds;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 組合の週 (docs/design/54 の 5.1 土地の旗取り・3.5 組合の塔の順位)。地上に保存。週は RegionPoints.weekId (ISO 週、朝 5 時で日が替わる)。
//   旗取り: 組合の人が稼いだ土地の点 (RegionPoints.add から) を、土地ごと・組合ごとに数える。週が替わると、前の週に一番の組合がその土地の旗を持つ。
//           旗を持つ組合の人は、その土地の町の店で FLAG_DISCOUNT 引き (名声の値引きに足す。合わせて 25％ まで)。組合の倉庫に週の品 (封の箱 1・塔の欠片 10)
//   塔の順位: 組合ごとの週の塔を越えた回数 (Towers.cleared から)。週が替わると前の週の上位 3 組合を「塔の誉れ」(1〜3 位) にし、
//           組合の倉庫に塔の欠片 (30・20・10)。記録の板 (TowerDesk.records) と旅帳 (/skyabyss_journal) に出す
//   旗の見た目 (町の碑と門の旗・名札の土地の印) は、形を一品一案で見せてから足す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GuildWeek extends SavedData {
    private static final String NAME = "skyabyss_guild_week";
    public static final int FLAG_DISCOUNT = 10;
    public static final int DISCOUNT_CAP = 25;
    public static final int[] TOWER_SHARDS = {30, 20, 10};
    public static final int FLAG_SHARDS = 10;

    String week = "";
    // 今週の数え: 土地 → 組合 → 点、組合 → 塔を越えた回数
    final Map<Integer, Map<String, Integer>> points = new HashMap<>();
    final Map<String, Integer> tower = new HashMap<>();
    // 今週の旗 (前の週の一番。土地 → 組合) と塔の誉れ (組合 → 順位 1〜3)
    final Map<Integer, String> flags = new HashMap<>();
    final Map<String, Integer> honor = new HashMap<>();
    final Map<String, String> names = new HashMap<>();

    public static GuildWeek get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GuildWeek::new, GuildWeek::load), NAME);
    }

    // ---------------------------------------------------------------- 数える
    public static void regionPoints(ServerPlayer p, int region, int n) {
        GuildData.Guild g = Guilds.of(p);
        if (g == null || n <= 0) {
            return;
        }
        GuildWeek w = get(p.server);
        w.roll(p.server);
        w.points.computeIfAbsent(region, k -> new HashMap<>()).merge(g.id, n, Integer::sum);
        w.names.put(g.id, g.name);
        w.setDirty();
    }

    public static void towerClear(MinecraftServer server, String guildId, String guildName) {
        GuildWeek w = get(server);
        w.roll(server);
        w.tower.merge(guildId, 1, Integer::sum);
        w.names.put(guildId, guildName);
        w.setDirty();
    }

    // ---------------------------------------------------------------- 週が替わる
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 600 == 77 && !jp.revati.skyabyss.SelfTestRunner.enabled()) {
            get(event.getServer()).roll(event.getServer());
        }
    }

    public void roll(MinecraftServer server) {
        String now = RegionPoints.weekId();
        if (now.equals(week)) {
            return;
        }
        boolean first = week.isEmpty();
        week = now;
        if (!first) {
            settle(server);
        }
        points.clear();
        tower.clear();
        setDirty();
    }

    // 前の週の数えから、今週の旗と塔の誉れを決めて、倉庫へ品を入れる
    void settle(MinecraftServer server) {
        flags.clear();
        honor.clear();
        GuildData data = GuildData.get(server);
        for (var en : points.entrySet()) {
            String best = top(en.getValue());
            if (best != null) {
                flags.put(en.getKey(), best);
                GuildData.Guild g = guild(data, best);
                if (g != null) {
                    int band = jp.revati.skyabyss.item.wild.WildGearTypes.band(Circuits.TOP_LEVEL[Math.max(0, Math.min(Circuits.TOP_LEVEL.length - 1, en.getKey()))]);
                    BoxKind box = BoxKind.rarity(GearTier.RARE, band);
                    Item boxItem = box == null ? null : LootBoxSetup.item(box);
                    if (boxItem != null) {
                        store(g, new ItemStack(boxItem));
                    }
                    store(g, shards(FLAG_SHARDS));
                    tell(server, g, Component.translatable("message.skyabyss.guild_week.flag", RegionFame.landName(en.getKey())));
                }
            }
        }
        List<Map.Entry<String, Integer>> rank = new ArrayList<>(tower.entrySet());
        rank.sort((a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < Math.min(3, rank.size()); i++) {
            String id = rank.get(i).getKey();
            honor.put(id, i + 1);
            GuildData.Guild g = guild(data, id);
            if (g != null) {
                store(g, shards(TOWER_SHARDS[i]));
                tell(server, g, Component.translatable("message.skyabyss.guild_week.honor", i + 1, TOWER_SHARDS[i]));
            }
        }
        data.setDirty();
    }

    @Nullable
    static String top(Map<String, Integer> m) {
        String best = null;
        int bv = 0;
        for (var e : m.entrySet()) {
            if (e.getValue() > bv || (e.getValue() == bv && best != null && e.getKey().compareTo(best) < 0)) {
                bv = e.getValue();
                best = e.getKey();
            }
        }
        return bv > 0 ? best : null;
    }

    @Nullable
    static GuildData.Guild guild(GuildData data, String id) {
        for (GuildData.Guild g : data.all()) {
            if (g.id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    static ItemStack shards(int n) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(jp.revati.skyabyss.tower.gear.TowerGear.SHARD));
        return new ItemStack(item, n);
    }

    // 倉庫の空きへ (重ねられる所から)。入りきらない分は捨てる (倉庫は 54 枠)
    static void store(GuildData.Guild g, ItemStack stack) {
        for (int i = 0; i < g.storage.size() && !stack.isEmpty(); i++) {
            ItemStack s = g.storage.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int k = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(k);
                stack.shrink(k);
            }
        }
        for (int i = 0; i < g.storage.size() && !stack.isEmpty(); i++) {
            if (g.storage.get(i).isEmpty()) {
                g.storage.set(i, stack.copy());
                stack.setCount(0);
            }
        }
    }

    static void tell(MinecraftServer server, GuildData.Guild g, Component text) {
        for (var id : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                Notice.gain(p, text);
            }
        }
    }

    // ---------------------------------------------------------------- 見る
    @Nullable
    public String flag(int region) {
        return flags.get(region);
    }

    public int honor(String guildId) {
        return honor.getOrDefault(guildId, 0);
    }

    public String name(String guildId) {
        return names.getOrDefault(guildId, guildId);
    }

    public Map<String, Integer> honors() {
        return honor;
    }

    // その人の組合が、その土地の旗を持っているか
    public static boolean holdsFlag(ServerPlayer p, int region) {
        GuildData.Guild g = Guilds.of(p);
        return g != null && g.id.equals(get(p.server).flag(region));
    }

    // ---------------------------------------------------------------- 保存
    GuildWeek() {
    }

    static GuildWeek load(CompoundTag tag, HolderLookup.Provider registries) {
        GuildWeek w = new GuildWeek();
        w.week = tag.getString("week");
        CompoundTag pts = tag.getCompound("points");
        for (String r : pts.getAllKeys()) {
            CompoundTag m = pts.getCompound(r);
            Map<String, Integer> map = new HashMap<>();
            for (String g : m.getAllKeys()) {
                map.put(g, m.getInt(g));
            }
            w.points.put(Integer.parseInt(r), map);
        }
        CompoundTag tw = tag.getCompound("tower");
        tw.getAllKeys().forEach(k -> w.tower.put(k, tw.getInt(k)));
        CompoundTag fl = tag.getCompound("flags");
        fl.getAllKeys().forEach(k -> w.flags.put(Integer.parseInt(k), fl.getString(k)));
        CompoundTag ho = tag.getCompound("honor");
        ho.getAllKeys().forEach(k -> w.honor.put(k, ho.getInt(k)));
        CompoundTag nm = tag.getCompound("names");
        nm.getAllKeys().forEach(k -> w.names.put(k, nm.getString(k)));
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("week", week);
        CompoundTag pts = new CompoundTag();
        points.forEach((r, m) -> {
            CompoundTag t = new CompoundTag();
            m.forEach(t::putInt);
            pts.put(String.valueOf(r), t);
        });
        tag.put("points", pts);
        CompoundTag tw = new CompoundTag();
        tower.forEach(tw::putInt);
        tag.put("tower", tw);
        CompoundTag fl = new CompoundTag();
        flags.forEach((r, g) -> fl.putString(String.valueOf(r), g));
        tag.put("flags", fl);
        CompoundTag ho = new CompoundTag();
        honor.forEach(ho::putInt);
        tag.put("honor", ho);
        CompoundTag nm = new CompoundTag();
        names.forEach(nm::putString);
        tag.put("names", nm);
        return tag;
    }

    // 自己テスト用: 週を一つ進めたことにする (今週の数えで旗と誉れを決める)
    public void settleForTest(MinecraftServer server) {
        settle(server);
        points.clear();
        tower.clear();
        setDirty();
    }
}

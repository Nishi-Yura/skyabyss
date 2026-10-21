package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

// プレイヤーが作るギルドの記録 (docs/design/35 の 3.4、00 の 222。オーバーワールドに保存)。
//   一人は一つのギルドにだけ入る。最大 30 人。作った人が長 (長が抜けると、いちばん古い人が長になる。誰もいなくなると解散)
//   参加は申請 (長が受ける)。長が「誰でも入れる」にしたギルドは、申請でそのまま入る
//   名前は 2〜16 字 (大文字小文字を区別せずに重ならない)。印 (tag) は 1〜4 字
//   位 (rank): 長 2・副長 1・人 0。副長は申請を受けられる。倉庫から取り出せる位は長が決める (takeRank。既定は副長から)
//   倉庫 (storage) 54 枠、週替わりの頼み (GuildRequests)、ギルドの家 = 空き区画を一つ借りる (lot。GuildLots)
public final class GuildData extends SavedData {
    private static final String NAME = "skyabyss_guilds";
    private static final Factory<GuildData> FACTORY = new Factory<>(GuildData::new, GuildData::load);

    public static final int MAX_MEMBERS = 30;
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 16;
    public static final int TAG_MAX = 4;
    public static final int STORAGE_SIZE = 54;
    // 位
    public static final int RANK_MEMBER = 0;
    public static final int RANK_OFFICER = 1;
    public static final int RANK_LEADER = 2;

    public static final class Guild {
        public final String id;
        public String name;
        public String tag;
        public int color;
        public int accent;
        public UUID leader;
        public boolean open;
        public long created;
        // 入った順 (長を含む)
        public final Set<UUID> members = new LinkedHashSet<>();
        public final Set<UUID> applicants = new LinkedHashSet<>();
        // 名前の写し (抜けている人も一覧に出せるように)
        public final Map<UUID, String> names = new LinkedHashMap<>();
        // 副長 (長は leader で持つ)
        public final Set<UUID> officers = new LinkedHashSet<>();
        // 倉庫から取り出せる位 (0 = 誰でも、1 = 副長から、2 = 長だけ)。預けるのは誰でも
        public int takeRank = RANK_OFFICER;
        public final NonNullList<ItemStack> storage = NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);
        // 借りている空き区画 (lot_1..5。空 = 借りていない)
        public String lot = "";
        // 週替わりの頼み: どの週の、どの頼みか、進み、人ごとの納めた量、済んだか
        public long reqWeek = -1;
        public String reqId = "";
        public int reqProgress;
        public boolean reqDone;
        public final Map<UUID, Integer> contrib = new LinkedHashMap<>();
        // まだ受け取っていない頼みの報い (いなかった人。入ったときに渡す)
        public final Map<UUID, Integer> rewardsDue = new LinkedHashMap<>();

        Guild(String id) {
            this.id = id;
        }

        public GuildBadge badge() {
            return new GuildBadge(id, tag, color, accent);
        }

        public String nameOf(UUID u) {
            return names.getOrDefault(u, u.toString().substring(0, 8));
        }

        public int rank(UUID u) {
            if (u.equals(leader)) {
                return RANK_LEADER;
            }
            return officers.contains(u) ? RANK_OFFICER : RANK_MEMBER;
        }

        public boolean canTake(UUID u) {
            return members.contains(u) && rank(u) >= takeRank;
        }
    }

    private final Map<String, Guild> guilds = new LinkedHashMap<>();
    private int nextId = 1;

    public static GuildData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public List<Guild> all() {
        return new ArrayList<>(guilds.values());
    }

    @Nullable
    public Guild byId(String id) {
        return guilds.get(id);
    }

    @Nullable
    public Guild byName(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (Guild g : guilds.values()) {
            if (g.name.toLowerCase(Locale.ROOT).equals(n)) {
                return g;
            }
        }
        return null;
    }

    @Nullable
    public Guild of(UUID player) {
        for (Guild g : guilds.values()) {
            if (g.members.contains(player)) {
                return g;
            }
        }
        return null;
    }

    Guild create(String name, String tag, int color, int accent, UUID leader, String leaderName, long now) {
        Guild g = new Guild("g" + nextId++);
        g.name = name;
        g.tag = tag;
        g.color = color;
        g.accent = accent;
        g.leader = leader;
        g.created = now;
        g.members.add(leader);
        g.names.put(leader, leaderName);
        guilds.put(g.id, g);
        setDirty();
        return g;
    }

    void remove(Guild g) {
        guilds.remove(g.id);
        setDirty();
    }

    // 自己テスト用
    void clearForTest() {
        guilds.clear();
        setDirty();
    }

    // 自己テスト用: 写し (roundTrip) の中身にそっくり戻す
    void restoreFrom(GuildData other) {
        guilds.clear();
        guilds.putAll(other.guilds);
        nextId = Math.max(nextId, other.nextId);
        setDirty();
    }

    public void dirty() {
        setDirty();
    }

    // 旗の色 (DyeColor) を名札の文字の色に (暗い色は読めるよう明るく)
    public static int textColor(int dye) {
        int rgb = DyeColor.byId(dye).getTextureDiffuseColor() & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int lum = (r * 3 + g * 6 + b) / 10;
        if (lum < 90) {
            r = r + (255 - r) / 2;
            g = g + (255 - g) / 2;
            b = b + (255 - b) / 2;
        }
        return (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- 保存
    static GuildData load(CompoundTag tag, HolderLookup.Provider registries) {
        GuildData d = new GuildData();
        d.nextId = Math.max(1, tag.getInt("next"));
        ListTag list = tag.getList("guilds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Guild g = new Guild(t.getString("id"));
            g.name = t.getString("name");
            g.tag = t.getString("tag");
            g.color = t.getInt("color");
            g.accent = t.getInt("accent");
            g.leader = t.getUUID("leader");
            g.open = t.getBoolean("open");
            g.created = t.getLong("created");
            ListTag ms = t.getList("members", Tag.TAG_COMPOUND);
            for (int k = 0; k < ms.size(); k++) {
                CompoundTag m = ms.getCompound(k);
                UUID u = m.getUUID("u");
                g.members.add(u);
                g.names.put(u, m.getString("n"));
            }
            ListTag as = t.getList("applicants", Tag.TAG_COMPOUND);
            for (int k = 0; k < as.size(); k++) {
                CompoundTag m = as.getCompound(k);
                UUID u = m.getUUID("u");
                g.applicants.add(u);
                g.names.put(u, m.getString("n"));
            }
            for (Tag o : t.getList("officers", Tag.TAG_INT_ARRAY)) {
                UUID u = net.minecraft.nbt.NbtUtils.loadUUID(o);
                if (g.members.contains(u)) {
                    g.officers.add(u);
                }
            }
            g.takeRank = t.contains("take") ? t.getInt("take") : RANK_OFFICER;
            ContainerHelper.loadAllItems(t.getCompound("storage"), g.storage, registries);
            g.lot = t.getString("lot");
            g.reqWeek = t.contains("req_week") ? t.getLong("req_week") : -1;
            g.reqId = t.getString("req_id");
            g.reqProgress = t.getInt("req_progress");
            g.reqDone = t.getBoolean("req_done");
            ListTag cs = t.getList("contrib", Tag.TAG_COMPOUND);
            for (int k = 0; k < cs.size(); k++) {
                CompoundTag m = cs.getCompound(k);
                g.contrib.put(m.getUUID("u"), m.getInt("n"));
            }
            ListTag rs = t.getList("due", Tag.TAG_COMPOUND);
            for (int k = 0; k < rs.size(); k++) {
                CompoundTag m = rs.getCompound(k);
                g.rewardsDue.put(m.getUUID("u"), m.getInt("n"));
            }
            if (!g.id.isEmpty() && !g.members.isEmpty()) {
                d.guilds.put(g.id, g);
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", nextId);
        ListTag list = new ListTag();
        for (Guild g : guilds.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", g.id);
            t.putString("name", g.name);
            t.putString("tag", g.tag);
            t.putInt("color", g.color);
            t.putInt("accent", g.accent);
            t.putUUID("leader", g.leader);
            t.putBoolean("open", g.open);
            t.putLong("created", g.created);
            ListTag ms = new ListTag();
            for (UUID u : g.members) {
                CompoundTag m = new CompoundTag();
                m.putUUID("u", u);
                m.putString("n", g.nameOf(u));
                ms.add(m);
            }
            t.put("members", ms);
            ListTag as = new ListTag();
            for (UUID u : g.applicants) {
                CompoundTag m = new CompoundTag();
                m.putUUID("u", u);
                m.putString("n", g.nameOf(u));
                as.add(m);
            }
            t.put("applicants", as);
            ListTag os = new ListTag();
            for (UUID u : g.officers) {
                os.add(net.minecraft.nbt.NbtUtils.createUUID(u));
            }
            t.put("officers", os);
            t.putInt("take", g.takeRank);
            t.put("storage", ContainerHelper.saveAllItems(new CompoundTag(), g.storage, true, registries));
            t.putString("lot", g.lot);
            t.putLong("req_week", g.reqWeek);
            t.putString("req_id", g.reqId);
            t.putInt("req_progress", g.reqProgress);
            t.putBoolean("req_done", g.reqDone);
            ListTag cs = new ListTag();
            g.contrib.forEach((u, n) -> {
                CompoundTag m = new CompoundTag();
                m.putUUID("u", u);
                m.putInt("n", n);
                cs.add(m);
            });
            t.put("contrib", cs);
            ListTag rs = new ListTag();
            g.rewardsDue.forEach((u, n) -> {
                CompoundTag m = new CompoundTag();
                m.putUUID("u", u);
                m.putInt("n", n);
                rs.add(m);
            });
            t.put("due", rs);
            list.add(t);
        }
        tag.put("guilds", list);
        return tag;
    }

    // 自己テスト用: 保存して読み直した写し
    GuildData roundTrip(HolderLookup.Provider registries) {
        return load(save(new CompoundTag(), registries), registries);
    }
}

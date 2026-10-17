package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 序盤の構造物 (野営地・鐘の宿り・巨大な足跡・巨神の骨。docs/design/17 の 4 章) の場所と、建て終わったかの記録 (オーバーワールドに保存)。
// 読む側は opening.OpeningSites を使うこと (この型は建てる側 opening.site の内部)
public final class SiteData extends SavedData {
    private static final String NAME = "skyabyss_opening_sites";
    private static final Factory<SiteData> FACTORY = new Factory<>(SiteData::new, SiteData::load);

    // 一つの場所。pos は「立つ場所の中心」(地面の一つ上)。extra は構造物ごとの付属の点 (名前 → 位置)
    public static final class Entry {
        public final String kind;
        public final int index;
        public BlockPos pos;
        public boolean built;
        public int yaw;
        public final List<String> extraNames = new ArrayList<>();
        public final List<BlockPos> extraPos = new ArrayList<>();

        Entry(String kind, int index, BlockPos pos, int yaw) {
            this.kind = kind;
            this.index = index;
            this.pos = pos;
            this.yaw = yaw;
        }

        public Entry extra(String name, BlockPos at) {
            int i = extraNames.indexOf(name);
            if (i >= 0) {
                extraPos.set(i, at);
            } else {
                extraNames.add(name);
                extraPos.add(at);
            }
            return this;
        }

        @Nullable
        public BlockPos extra(String name) {
            int i = extraNames.indexOf(name);
            return i < 0 ? null : extraPos.get(i);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    // 場所を決めたか (決めたあとは、建てている途中でも読める)
    private boolean planned;
    // 最初に決めたときの、世界の初めのスポーン地点と、動かしたか (17-11)
    private BlockPos originalSpawn = BlockPos.ZERO;
    private boolean spawnMoved;
    private long plannedAt = -1;

    public static SiteData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean planned() {
        return planned;
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<Entry> entries(String kind) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind.equals(kind)) {
                out.add(e);
            }
        }
        return out;
    }

    @Nullable
    public Entry first(String kind) {
        for (Entry e : entries) {
            if (e.kind.equals(kind)) {
                return e;
            }
        }
        return null;
    }

    public Entry add(String kind, int index, BlockPos pos, int yaw) {
        Entry e = new Entry(kind, index, pos, yaw);
        entries.add(e);
        setDirty();
        return e;
    }

    public void markPlanned(long gameTime, BlockPos originalSpawn, boolean spawnMoved) {
        this.planned = true;
        this.plannedAt = gameTime;
        this.originalSpawn = originalSpawn;
        this.spawnMoved = spawnMoved;
        setDirty();
    }

    // 計画を捨てる (自己テストと開発コマンドの作り直し用)
    public void clear() {
        entries.clear();
        planned = false;
        setDirty();
    }

    public long plannedAt() {
        return plannedAt;
    }

    public BlockPos originalSpawn() {
        return originalSpawn;
    }

    public boolean spawnMoved() {
        return spawnMoved;
    }

    static SiteData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiteData d = new SiteData();
        d.planned = tag.getBoolean("planned");
        d.plannedAt = tag.contains("plannedAt") ? tag.getLong("plannedAt") : -1;
        d.originalSpawn = BlockPos.of(tag.getLong("originalSpawn"));
        d.spawnMoved = tag.getBoolean("spawnMoved");
        for (Tag t : tag.getList("sites", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Entry e = new Entry(c.getString("kind"), c.getInt("index"), BlockPos.of(c.getLong("pos")), c.getInt("yaw"));
            e.built = c.getBoolean("built");
            CompoundTag x = c.getCompound("extra");
            for (String key : x.getAllKeys()) {
                e.extra(key, BlockPos.of(x.getLong(key)));
            }
            d.entries.add(e);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("planned", planned);
        tag.putLong("plannedAt", plannedAt);
        tag.putLong("originalSpawn", originalSpawn.asLong());
        tag.putBoolean("spawnMoved", spawnMoved);
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putString("kind", e.kind);
            c.putInt("index", e.index);
            c.putLong("pos", e.pos.asLong());
            c.putInt("yaw", e.yaw);
            c.putBoolean("built", e.built);
            CompoundTag x = new CompoundTag();
            for (int i = 0; i < e.extraNames.size(); i++) {
                x.putLong(e.extraNames.get(i), e.extraPos.get(i).asLong());
            }
            c.put("extra", x);
            list.add(c);
        }
        tag.put("sites", list);
        return tag;
    }
}

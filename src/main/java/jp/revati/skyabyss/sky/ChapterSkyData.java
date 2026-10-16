package jp.revati.skyabyss.sky;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 1章の空の演出 (削られた山頂・雲の目) の記録。オーバーワールドに保存する。
// - プレイヤーごと: 巨神の背に乗ったか (1章に入った印)、雲の目の次の時刻と見た回数、復活地点 (拠点の目安)
// - ワールド: プレイヤーがブロックを置いたことのあるチャンク (削る対象から外す)、削った山頂の一覧、月の近さの上書き (テスト用)
public class ChapterSkyData extends SavedData {
    private static final String NAME = "skyabyss_chapter_sky";
    private static final Factory<ChapterSkyData> FACTORY = new Factory<>(ChapterSkyData::new, ChapterSkyData::load);
    // 覚えておく削り跡の数 (古いものから忘れる。忘れても地形はそのまま)
    static final int MAX_SCARS = 64;

    public static final class Entry {
        // 巨神の背 (岩鯨の背) に一度でも乗った
        public boolean boarded;
        // 次に雲の目が開きうるゲーム時刻 (-1 = まだ決めていない)
        public long nextEye = -1;
        // 雲の目を見た回数 (目が合った・画面に入った)。段階を決める
        public int eyeSightings;
        // 続けて見逃した回数
        public int eyeMisses;
        // 最後に知った復活地点 (ベッド・リスポーンアンカー)。オーバーワールドのときだけ。なければ null
        public BlockPos home;
    }

    public record Scar(BlockPos pos, long gameTime, int removed) {
    }

    private final Map<UUID, Entry> players = new HashMap<>();
    // プレイヤーがブロックを置いたことのあるチャンク (ChunkPos.toLong)
    private final LongOpenHashSet placedChunks = new LongOpenHashSet();
    private final List<Scar> scars = new ArrayList<>();
    // 月の近さの上書き (0〜1)。負なら上書きしない
    private float moonOverride = -1f;

    public static ChapterSkyData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Entry entry(UUID player) {
        return players.computeIfAbsent(player, id -> new Entry());
    }

    public Map<UUID, Entry> entries() {
        return players;
    }

    public void changed() {
        setDirty();
    }

    public boolean markPlaced(long chunk) {
        if (placedChunks.add(chunk)) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean wasPlaced(long chunk) {
        return placedChunks.contains(chunk);
    }

    void clearPlacedForTest(long chunk) {
        placedChunks.remove(chunk);
    }

    public List<Scar> scars() {
        return scars;
    }

    public void addScar(Scar scar) {
        scars.add(scar);
        while (scars.size() > MAX_SCARS) {
            scars.remove(0);
        }
        setDirty();
    }

    public float moonOverride() {
        return moonOverride;
    }

    public void setMoonOverride(float value) {
        moonOverride = value;
        setDirty();
    }

    static ChapterSkyData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChapterSkyData data = new ChapterSkyData();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            if (!p.hasUUID("id")) {
                continue;
            }
            Entry e = data.entry(p.getUUID("id"));
            e.boarded = p.getBoolean("boarded");
            e.nextEye = p.contains("nextEye") ? p.getLong("nextEye") : -1;
            e.eyeSightings = p.getInt("eyeSightings");
            e.eyeMisses = p.getInt("eyeMisses");
            if (p.contains("home")) {
                e.home = BlockPos.of(p.getLong("home"));
            }
        }
        for (long chunk : tag.getLongArray("placedChunks")) {
            data.placedChunks.add(chunk);
        }
        for (Tag t : tag.getList("scars", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            data.scars.add(new Scar(BlockPos.of(s.getLong("pos")), s.getLong("time"), s.getInt("removed")));
        }
        data.moonOverride = tag.contains("moonOverride") ? tag.getFloat("moonOverride") : -1f;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((id, e) -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("id", id);
            p.putBoolean("boarded", e.boarded);
            p.putLong("nextEye", e.nextEye);
            p.putInt("eyeSightings", e.eyeSightings);
            p.putInt("eyeMisses", e.eyeMisses);
            if (e.home != null) {
                p.putLong("home", e.home.asLong());
            }
            list.add(p);
        });
        tag.put("players", list);
        tag.put("placedChunks", new LongArrayTag(placedChunks.toLongArray()));
        ListTag scarList = new ListTag();
        for (Scar s : scars) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", s.pos().asLong());
            c.putLong("time", s.gameTime());
            c.putInt("removed", s.removed());
            scarList.add(c);
        }
        tag.put("scars", scarList);
        tag.putFloat("moonOverride", moonOverride);
        return tag;
    }
}

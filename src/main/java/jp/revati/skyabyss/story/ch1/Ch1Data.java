package jp.revati.skyabyss.story.ch1;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 序章・一章 (P4a) の小要素の記録 (オーバーワールドに保存。docs/design/17 の 1.5)。
//   人ごと (Player): 歌石の進み (song)、小さな異変を起こした日 (oddityDay) と使った種類 (oddityMask) と回数 (oddityCount)、
//                    見て回った積み石・跡の印 (rounds)、積み石の見回りの頼みに送った印 (signalled)、足跡の道しるべを描いている頼みの出どころ
//   世界 (World):  岩鯨が初めて降りたときの「影と静けさ」(hush) の状態、宿りの炉端 (ベッド・かまど・板) を置いたか (nook)
public final class Ch1Data extends SavedData {
    private static final String NAME = "skyabyss_ch1";
    private static final Factory<Ch1Data> FACTORY = new Factory<>(Ch1Data::new, Ch1Data::load);

    // 人ごとの記録
    public static final class Player {
        // 歌石を順に叩いて、いま何番まで来ているか (0 = まだ。5 = 鳴らしきった)
        public int song;
        public boolean songDone;
        // 小さな異変: 最後に起こした日・使った種類のビット・総数
        public long oddityDay = -1;
        public int oddityMask;
        public int oddityCount;
        // 見て回った積み石・跡の印 ("c:x,z" か "s:要素 ID#番号")
        public final java.util.Set<String> rounds = new java.util.LinkedHashSet<>();
        // 積み石の見回りの頼みへ、もう合図を送った印
        public final java.util.Set<String> signalled = new java.util.LinkedHashSet<>();
        // 小さな印 (起きたことの記録)
        public final java.util.Set<String> flags = new java.util.LinkedHashSet<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("song", song);
            t.putBoolean("songDone", songDone);
            t.putLong("oddityDay", oddityDay);
            t.putInt("oddityMask", oddityMask);
            t.putInt("oddityCount", oddityCount);
            t.put("rounds", strings(rounds));
            t.put("signalled", strings(signalled));
            t.put("flags", strings(flags));
            return t;
        }

        static Player load(CompoundTag t) {
            Player p = new Player();
            p.song = t.getInt("song");
            p.songDone = t.getBoolean("songDone");
            p.oddityDay = t.contains("oddityDay") ? t.getLong("oddityDay") : -1;
            p.oddityMask = t.getInt("oddityMask");
            p.oddityCount = t.getInt("oddityCount");
            read(t.getList("rounds", Tag.TAG_STRING), p.rounds);
            read(t.getList("signalled", Tag.TAG_STRING), p.signalled);
            read(t.getList("flags", Tag.TAG_STRING), p.flags);
            return p;
        }
    }

    // 世界の記録: 岩鯨が初めて降りたときの出来事
    //   hushState 0 = まだ / 1 = 進行中 / 2 = 済み
    public int hushState;
    public long hushStart = -1;
    public long hushEnd = -1;
    // 宿りの炉端 (ベッド・かまど・言伝ての板) を置いたか
    public boolean nook;

    private final Map<UUID, Player> players = new HashMap<>();

    public static Ch1Data get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Player player(UUID id) {
        return players.computeIfAbsent(id, k -> new Player());
    }

    public Map<UUID, Player> players() {
        return players;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("hushState", hushState);
        tag.putLong("hushStart", hushStart);
        tag.putLong("hushEnd", hushEnd);
        tag.putBoolean("nook", nook);
        CompoundTag all = new CompoundTag();
        players.forEach((id, p) -> all.put(id.toString(), p.save()));
        tag.put("players", all);
        return tag;
    }

    static Ch1Data load(CompoundTag tag, HolderLookup.Provider registries) {
        Ch1Data d = new Ch1Data();
        d.hushState = tag.getInt("hushState");
        d.hushStart = tag.contains("hushStart") ? tag.getLong("hushStart") : -1;
        d.hushEnd = tag.contains("hushEnd") ? tag.getLong("hushEnd") : -1;
        d.nook = tag.getBoolean("nook");
        CompoundTag all = tag.getCompound("players");
        for (String key : all.getAllKeys()) {
            try {
                d.players.put(UUID.fromString(key), Player.load(all.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // 壊れた鍵は捨てる
            }
        }
        return d;
    }

    private static ListTag strings(java.util.Set<String> set) {
        ListTag l = new ListTag();
        set.forEach(s -> l.add(StringTag.valueOf(s)));
        return l;
    }

    private static void read(ListTag l, java.util.Set<String> into) {
        for (Tag t : l) {
            into.add(t.getAsString());
        }
    }
}

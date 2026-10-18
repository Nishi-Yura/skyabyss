package jp.revati.skyabyss.npc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;

// プレイヤーと NPC (役) のあいだの記録 (Data Attachment skyabyss:npc_bonds)。死んでも引き継ぎ、持ち主にだけ同期する。
//   affinity : 好感度 (-100〜100)
//   flags    : このループで立った会話の印 (「数え歌を聞いた」など)。ループで echoes へ移って消える
//   echoes   : 前のループまでに立った印。NPC がループ前の会話を「覚えているような」セリフを言うのに使う
//   talks    : このループで話した回数、lastDay: 最後に話した日 (一日の最初の会話で好感度が少し上がる)
//   loopSeen : この記録が最後に合わせた月のループの回数 (ループの間にいなかった人は、入ったときに追いつく)
public final class NpcBonds {
    public static final class Bond {
        static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("affinity", 0).forGetter(b -> b.affinity),
                Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(b -> List.copyOf(b.flags)),
                Codec.STRING.listOf().optionalFieldOf("echoes", List.of()).forGetter(b -> List.copyOf(b.echoes)),
                Codec.INT.optionalFieldOf("talks", 0).forGetter(b -> b.talks),
                Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(b -> b.lastDay)).apply(i, Bond::new));

        int affinity;
        final Set<String> flags = new LinkedHashSet<>();
        final Set<String> echoes = new LinkedHashSet<>();
        int talks;
        long lastDay = -1;

        Bond() {
        }

        private Bond(int affinity, List<String> flags, List<String> echoes, int talks, long lastDay) {
            this.affinity = affinity;
            this.flags.addAll(flags);
            this.echoes.addAll(echoes);
            this.talks = talks;
            this.lastDay = lastDay;
        }

        public int affinity() {
            return affinity;
        }

        public Set<String> flags() {
            return flags;
        }

        public Set<String> echoes() {
            return echoes;
        }

        public int talks() {
            return talks;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Bond b && b.affinity == affinity && b.flags.equals(flags) && b.echoes.equals(echoes) && b.talks == talks
                    && b.lastDay == lastDay;
        }

        @Override
        public int hashCode() {
            return affinity * 31 + flags.hashCode();
        }

        @Override
        public String toString() {
            return "Bond{" + affinity + " flags=" + flags + " echoes=" + echoes + " talks=" + talks + "}";
        }
    }

    public static final Codec<NpcBonds> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Bond.CODEC).optionalFieldOf("bonds", Map.of()).forGetter(b -> b.bonds),
            Codec.INT.optionalFieldOf("loop_seen", 0).forGetter(b -> b.loopSeen)).apply(i, NpcBonds::new));
    public static final StreamCodec<ByteBuf, NpcBonds> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    final Map<String, Bond> bonds = new LinkedHashMap<>();
    int loopSeen;

    public NpcBonds() {
    }

    private NpcBonds(Map<String, Bond> bonds, int loopSeen) {
        this.bonds.putAll(bonds);
        this.loopSeen = loopSeen;
    }

    public static NpcBonds of(Player player) {
        return player.getData(NpcSetup.BONDS);
    }

    public static void sync(ServerPlayer player) {
        if (!(player instanceof FakePlayer)) {
            player.syncData(NpcSetup.BONDS);
        }
    }

    // その役との記録 (なければ作る)
    public Bond bond(String role) {
        return bonds.computeIfAbsent(role, r -> new Bond());
    }

    // 読むだけ (作らない)。条件の判定・表示用。bond() で読むと、読んだだけで記録ができ、「すでに遊んでいた人」と取り違えられる
    public boolean hasFlag(String role, String flag) {
        Bond b = bonds.get(role);
        return b != null && b.flags.contains(flag);
    }

    public boolean hasEcho(String role, String flag) {
        Bond b = bonds.get(role);
        return b != null && b.echoes.contains(flag);
    }

    // 何か残っているか (会話の回数・印・好感度)。空の記録は「遊んだ」うちに入らない
    public boolean hasSubstance() {
        for (Bond b : bonds.values()) {
            if (b.talks > 0 || b.affinity != 0 || !b.flags.isEmpty() || !b.echoes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // 読むだけ (作らない)。クライアントの表示用
    public int affinity(String role) {
        Bond b = bonds.get(role);
        return b == null ? 0 : b.affinity;
    }

    public Map<String, Bond> bonds() {
        return java.util.Collections.unmodifiableMap(bonds);
    }

    public int loopSeen() {
        return loopSeen;
    }

    // 月のループ: このループの印を「覚えているような記憶」(echoes) へ移し、好感度と回数を戻す。
    // 01 のループの表で「NPC の状態と会話フラグ」は巻き戻るもの。ただし、留まる者に向けた微かな覚えだけは残る
    void onLoop() {
        for (Bond b : bonds.values()) {
            b.echoes.addAll(b.flags);
            b.flags.clear();
            b.affinity = 0;
            b.talks = 0;
            b.lastDay = -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NpcBonds b && b.bonds.equals(bonds) && b.loopSeen == loopSeen;
    }

    @Override
    public int hashCode() {
        return bonds.hashCode();
    }

    @Override
    public String toString() {
        return "NpcBonds" + bonds + " loopSeen=" + loopSeen;
    }
}

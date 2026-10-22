package jp.revati.skyabyss.abyss.arts;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

// 深淵術の記録 (人ごと)。ranks[術の順] = ランク (0 = 覚えていない)。持ち主の画面と同期する
public final class ArtData implements INBTSerializable<CompoundTag> {
    public final int[] ranks = new int[AbyssArt.values().length];

    public int rank(AbyssArt a) {
        return ranks[a.ordinal()];
    }

    public boolean has(AbyssArt a) {
        return ranks[a.ordinal()] > 0;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ArtData> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                for (int r : d.ranks) {
                    buf.writeVarInt(r);
                }
            },
            buf -> {
                ArtData d = new ArtData();
                for (int i = 0; i < d.ranks.length; i++) {
                    d.ranks[i] = buf.readVarInt();
                }
                return d;
            });

    public ArtData copy() {
        ArtData c = new ArtData();
        System.arraycopy(ranks, 0, c.ranks, 0, ranks.length);
        return c;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (AbyssArt a : AbyssArt.values()) {
            tag.putInt(a.id, ranks[a.ordinal()]);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        for (AbyssArt a : AbyssArt.values()) {
            ranks[a.ordinal()] = Math.max(0, Math.min(AbyssArt.MAX_RANK, tag.getInt(a.id)));
        }
    }
}

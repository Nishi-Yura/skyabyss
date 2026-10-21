package jp.revati.skyabyss.facility.guild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;

// 名札に出すギルドの印 (人ごとの写し。正本は GuildData)。guild が空ならギルドに入っていない。
//   tag = 名札の短い印 (1〜4 字)、color = 旗の地の色、accent = 旗の模様の色 (どちらも DyeColor の番号)
public record GuildBadge(String guild, String tag, int color, int accent) {
    public static final GuildBadge NONE = new GuildBadge("", "", 0, 0);

    public static final Codec<GuildBadge> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("guild", "").forGetter(GuildBadge::guild),
            Codec.STRING.optionalFieldOf("tag", "").forGetter(GuildBadge::tag),
            Codec.INT.optionalFieldOf("color", 0).forGetter(GuildBadge::color),
            Codec.INT.optionalFieldOf("accent", 0).forGetter(GuildBadge::accent))
            .apply(i, GuildBadge::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuildBadge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GuildBadge::guild,
            ByteBufCodecs.STRING_UTF8, GuildBadge::tag,
            ByteBufCodecs.VAR_INT, GuildBadge::color,
            ByteBufCodecs.VAR_INT, GuildBadge::accent,
            GuildBadge::new);

    public boolean present() {
        return !guild.isEmpty();
    }

    // 名札の文字の色 (旗の地の色を明るめに)
    public int textColor() {
        return GuildData.textColor(color);
    }

    public int accentColor() {
        return GuildData.textColor(accent);
    }

    public static DyeColor dye(int id) {
        return DyeColor.byId(id);
    }
}

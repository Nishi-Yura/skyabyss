package jp.revati.skyabyss.endgame;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.facility.guild.GuildData;
import jp.revati.skyabyss.facility.guild.Guilds;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.JobMastery;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 「プロフィール」の画面 (endgame/client/ProfileScreen。持ち物の画面の右上の札から開く) へ送る、その人のまとめ。開くと手元が頼み (Request)、サーバーが答える (Snapshot)。
//   称号・転生の回数・称号の位置は同期してある RebirthData を手元で読むので、ここには入れない
//   job・level・mastery : いまの職とレベルと熟練の段
//   guild : 組合の名前・印・旗 (模様・地の色・模様の色)。入っていなければ無い
//   coins・play (遊んだ tick)・kills (倒した敵)・deaths (倒れた回数)・tower (塔を越えた回数)・fame (土地の名声の段の合計)・foes (敵の図鑑)
public final class Profile {
    private Profile() {
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "profile_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "profile"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(Snapshot::new, Snapshot::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static CompoundTag snapshot(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        jp.revati.skyabyss.rpg.GrowthData g = Growth.get(p);
        Job job = jp.revati.skyabyss.story.finale.gear.DivineGear.job(p);
        t.putString("job", job == null ? "" : job.id);
        t.putInt("level", g.level);
        t.putInt("mastery", job == null ? 0 : JobMastery.tier(p, job));
        GuildData.Guild guild = Guilds.of(p);
        if (guild != null) {
            CompoundTag c = new CompoundTag();
            c.putString("name", guild.name);
            c.putString("tag", guild.tag);
            c.putInt("color", guild.color);
            c.putInt("accent", guild.accent);
            c.putString("pattern", guild.pattern);
            c.putInt("rank", guild.leader != null && guild.leader.equals(p.getUUID()) ? GuildData.RANK_LEADER
                    : guild.officers.contains(p.getUUID()) ? GuildData.RANK_OFFICER : GuildData.RANK_MEMBER);
            t.put("guild", c);
        }
        t.putInt("coins", Coins.count(p));
        t.putLong("play", p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)));
        t.putInt("kills", p.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)));
        t.putInt("deaths", p.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)));
        t.putInt("tower", p.getPersistentData().getInt(jp.revati.skyabyss.tower.Towers.CLEARS));
        int fame = 0;
        for (int r : RegionFame.LANDS) {
            fame += RegionFame.tier(p, r);
        }
        t.putInt("fame", fame);
        t.putInt("fame_max", RegionFame.LANDS.length * RegionFame.STEPS.length);
        t.putInt("foes", Codex.foesDefeated(p));
        t.putInt("foes_total", Codex.foes().size());
        t.putInt("fish", jp.revati.skyabyss.fishing.Fishing.codexCount(p));
        t.putInt("fish_total", jp.revati.skyabyss.fishing.Fish.ALL.size());
        return t;
    }

    static void answer(ServerPlayer p) {
        if (!(p instanceof FakePlayer) && p.connection != null) {
            // 解けた称号はここで数え直す (開いたときに新しい称号が一覧に出るように)
            jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
            PacketDistributor.sendToPlayer(p, new Snapshot(snapshot(p)));
        }
    }
}

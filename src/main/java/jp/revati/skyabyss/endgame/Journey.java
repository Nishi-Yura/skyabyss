package jp.revati.skyabyss.endgame;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.JobMastery;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 「旅の記録」の画面 (endgame/client/JourneyScreen) へ送る、その人の旅のまとめ。画面を開くと手元が頼み (Request)、サーバーが答える (Snapshot)。
//   lands : 土地ごと (RegionFame.LANDS) の名声の点・週の点・名所の数・今週の旗の組合・自分の組合の旗か・店の値引き
//   sets  : 巨神ごとの装備の図鑑 (手にした数 / 表の数)。foes: 敵の図鑑
//   job・mastery : いまの職と熟練の量
public final class Journey {
    private Journey() {
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "journey_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "journey"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(Snapshot::new, Snapshot::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static CompoundTag snapshot(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        GuildWeek w = GuildWeek.get(p.server);
        ListTag lands = new ListTag();
        for (int r : RegionFame.LANDS) {
            CompoundTag l = new CompoundTag();
            l.putInt("r", r);
            l.putInt("fame", RegionPoints.fame(p, r));
            l.putInt("week", RegionPoints.week(p, r));
            RegionFame.Journal j = RegionFame.journal(p, r);
            l.putInt("found", j.found());
            l.putInt("total", j.total());
            String g = RegionFame.townLand(r) ? w.flag(r) : null;
            l.putString("flag", g == null ? "" : w.name(g));
            l.putBoolean("mine", RegionFame.townLand(r) && GuildWeek.holdsFlag(p, r));
            l.putInt("discount", RegionFame.discountFor(p, r));
            lands.add(l);
        }
        t.put("lands", lands);
        ListTag sets = new ListTag();
        for (TitanGearSet set : TitanGearSets.all()) {
            int total = Codex.pieces(set).size();
            if (total == 0) {
                continue;
            }
            CompoundTag s = new CompoundTag();
            s.putString("titan", set.titan);
            s.putInt("owned", Codex.owned(p, set));
            s.putInt("total", total);
            sets.add(s);
        }
        t.put("sets", sets);
        t.putInt("foes", Codex.foesDefeated(p));
        t.putInt("foes_total", Codex.foes().size());
        Job job = jp.revati.skyabyss.story.finale.gear.DivineGear.job(p);
        t.putString("job", job == null ? "" : job.id);
        t.putLong("mastery", JobMastery.xp(Growth.get(p), job));
        return t;
    }

    static void answer(ServerPlayer p) {
        if (!(p instanceof FakePlayer) && p.connection != null) {
            PacketDistributor.sendToPlayer(p, new Snapshot(snapshot(p)));
        }
    }
}

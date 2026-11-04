package jp.revati.skyabyss.tower;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 塔の受付の画面に見せる今の状態 (サーバー → その人)。open = 受付で話したとき (画面を開く)。false は開いている画面を新しくするだけ。
// 2026-11-02 の声「なぜ大規模レイドの入り方がチャット？これまで通り UI 出したらいいんじゃない」(前はチャットの押せる一覧)
public record TowerDeskState(boolean open, boolean usedToday, String leader, List<String> members, boolean isLeader, int max,
        List<String> joinLeaders, List<Integer> joinCounts, List<String> wagers, List<String> chosen, int pickMax, int bonusPct, String stance) implements CustomPacketPayload {
    public static final Type<TowerDeskState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tower_desk_state"));
    public static final StreamCodec<FriendlyByteBuf, TowerDeskState> STREAM_CODEC = StreamCodec.of((buf, s) -> {
        buf.writeBoolean(s.open);
        buf.writeBoolean(s.usedToday);
        buf.writeUtf(s.leader);
        writeList(buf, s.members);
        buf.writeBoolean(s.isLeader);
        buf.writeVarInt(s.max);
        writeList(buf, s.joinLeaders);
        buf.writeVarInt(s.joinCounts.size());
        s.joinCounts.forEach(buf::writeVarInt);
        writeList(buf, s.wagers);
        writeList(buf, s.chosen);
        buf.writeVarInt(s.pickMax);
        buf.writeVarInt(s.bonusPct);
        buf.writeUtf(s.stance);
    }, buf -> {
        boolean open = buf.readBoolean();
        boolean used = buf.readBoolean();
        String leader = buf.readUtf();
        List<String> members = readList(buf);
        boolean isLeader = buf.readBoolean();
        int max = buf.readVarInt();
        List<String> join = readList(buf);
        int n = buf.readVarInt();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            counts.add(buf.readVarInt());
        }
        List<String> wagers = readList(buf);
        List<String> chosen = readList(buf);
        int pick = buf.readVarInt();
        int bonus = buf.readVarInt();
        return new TowerDeskState(open, used, leader, members, isLeader, max, join, counts, wagers, chosen, pick, bonus, buf.readUtf());
    });

    private static void writeList(FriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        list.forEach(buf::writeUtf);
    }

    private static List<String> readList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(buf.readUtf());
        }
        return out;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

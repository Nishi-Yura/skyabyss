package jp.revati.skyabyss.titan.group;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.record.FightBadgeData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 倒れた回数の札 (docs/design/10 の 10.14、rpg_brushup B6)。心臓・中ボスの戦いに勝ったとき、その戦いでパーティが倒れた回数
// (倒れた状態に入った回数 + 倒れずに死んだ回数) で称号の一行を付ける。見た目だけで、報酬は変えない (ソフトな死の罰を保つ)。
//   0 回       「無傷の誓い」 (OATH)
//   2 回まで   「堅き歩み」   (STEADY)
//   それより多い 札なし (回数だけ見せる)
// 記録は人ごと・巨神ごと・戦いの種類 (心臓/中ボス) ごとの最良 (最少) を控え帳に残す (record.FightBadgeData)
public final class FightBadges {
    public static final String OATH = "oath";
    public static final String STEADY = "steady";
    public static final int STEADY_MAX = 2;

    private FightBadges() {
    }

    // 回数に対する札の名前 (なければ null)
    @Nullable
    public static String badgeOf(int falls) {
        if (falls <= 0) {
            return OATH;
        }
        return falls <= STEADY_MAX ? STEADY : null;
    }

    public static Component badgeName(String badge) {
        return Component.translatable("badge.skyabyss.fight." + badge);
    }

    // 勝った戦い。boss はいま倒れたボス (なければ記録しない)。ids は参加者
    public static void onVictory(ServerLevel level, @Nullable Entity boss, Set<UUID> ids, int falls) {
        String titan = Titans.titanOfBack(level.dimension());
        if (boss == null || titan == null) {
            return;
        }
        record(level, titan, boss instanceof HeartBoss ? FightBadgeData.HEART : FightBadgeData.MID, ids, falls);
    }

    // 記録して、オンラインの参加者に勝利の知らせを足す (自己テストからも呼ぶ)
    public static void record(ServerLevel level, String titan, String kind, Set<UUID> ids, int falls) {
        FightBadgeData data = FightBadgeData.get(level.getServer());
        String badge = badgeOf(falls);
        for (UUID id : ids) {
            boolean better = data.record(id, titan, kind, falls);
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            if (badge != null) {
                Notice.gain(p, "message.skyabyss.badge.earned", badgeName(badge), falls);
                if (better) {
                    Notice.hint(p, "message.skyabyss.badge.best");
                }
            } else {
                Notice.hint(p, "message.skyabyss.badge.none", falls);
            }
        }
    }
}

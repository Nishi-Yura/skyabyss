package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.util.Players;

import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// 巨神の見守り (岩鯨の WhaleKeeper を、巨神の種類で使い回せる形にしたもの)。巨神は種類ごとにワールドに一体だけで、見失ってはいけない。
// - 近く (水平 640 ブロック以内) にプレイヤーがいる間と、誰かが背中にいる間は、巨神のいるチャンクに
//   時間切れ付きのチケットを張り続ける。シミュレーション距離の外でも動き続け、待ち時間も進む。
//   張るのは巨神のいる 1 チャンク (と、エンティティを動かすのに要る周り) だけで、離れたら外す
// - TitanWorldData の巨神ごとの記録 (Slot) に本物の UUID と最後の位置・状態を残す。UUID の違う個体は、読み込まれた時点で取り消す
// - 本物が読み込まれていなければ、最後の位置にチケットを張って読み込み直す。それでも見つからなければ
//   (/kill などで消えた)、近くのプレイヤーのそばへ呼び直す。誰からも遠く離れたまま 2 分たったときも呼び直す
// 巨神ごとに違うのは、作り方・呼び直す位置・背中に人がいるか・旅の最中か・現れたときの知らせ (下の抽象メソッド)
public abstract class TitanKeeper<T extends Entity> {
    // 33 - 2 = 31: 中心のチャンクがエンティティの tick に入る最小の距離
    private static final int TICKET_RADIUS = 2;
    public static final double NEAR = 640;
    // 誰からも遠いまま、これだけたったら呼び直す
    private static final int RELOCATE_AFTER = 2400;
    // 最後の位置を読み込み直してから、見つからないと判断するまで
    private static final int RELOAD_GRACE = 200;
    // チケットを張っているのに tick が進まない (止まっている) と判断するまで
    private static final int STUCK_AFTER = 600;

    private final String slotId;
    private final Class<T> type;
    private final String logName;
    // 60 tick で切れる。張り直しが止まれば (巨神が消えた・サーバーが止まった) 自然に外れる
    private final TicketType<ChunkPos> ticket;

    @Nullable
    private ChunkPos ticketPos;
    private long ticketRefreshed;
    private long unreachableSince = -1;
    private long missingSince = -1;
    private int lastTickCount = -1;
    private long frozenSince = -1;

    protected TitanKeeper(String slotId, Class<T> type, String ticketName, String logName) {
        this.slotId = slotId;
        this.type = type;
        this.logName = logName;
        this.ticket = TicketType.create(ticketName, Comparator.comparingLong(ChunkPos::toLong), 60);
    }

    // ---- 巨神ごとに決めること

    // 新しい個体を作り、(x, z) に置いて、前の状態があれば引き継ぐ (まだワールドには加えない)
    protected abstract T create(ServerLevel level, double x, double z, @Nullable CompoundTag state);

    // 見守りが覚えておく状態
    protected abstract CompoundTag saveState(T titan);

    // 呼び直すときの新しい位置 (y は使わない)。null なら今は呼び直せない (しばらく待ってやり直す)
    @Nullable
    protected abstract Vec3 relocationPoint(ServerLevel level, ServerPlayer target);

    // 誰かが背中にいるか (その間は呼び直さず、チケットを張り続ける)
    protected abstract boolean someoneAboard(ServerLevel overworld);

    // 現れたときの音と知らせ
    protected abstract void announce(ServerLevel level, T titan);

    // 巨神での移動の最中か (この間は、動きと読み込みは旅の操縦が受け持つ)
    // 呼び直しの宛先で優先する人 (まだこの巨神を見ていない人)。既定は誰も優先しない
    protected boolean preferred(ServerPlayer player) {
        return false;
    }

    // 呼び直しの宛先: 優先する人がいればその中から、いなければ誰か一人 (観戦者・倒れている人を除く)
    protected ServerPlayer pickTarget(ServerLevel level) {
        List<ServerPlayer> preferred = level.players().stream().filter(p -> eligible(p) && preferred(p)).toList();
        return preferred.isEmpty() ? randomEligible(level) : preferred.get(level.random.nextInt(preferred.size()));
    }

    protected boolean inVoyage(T titan) {
        return false;
    }

    // 旅の操縦がこの巨神を持っているか (読み込まれていなくても呼び直さない)
    protected boolean busy() {
        return false;
    }

    // ---- 共通

    public TitanWorldData.Slot slot(ServerLevel overworld) {
        return TitanWorldData.get(overworld).slot(slotId);
    }

    public String slotId() {
        return slotId;
    }

    // 本物の巨神 (読み込まれているときだけ)
    @Nullable
    public T current(ServerLevel overworld) {
        TitanWorldData.Slot slot = slot(overworld);
        if (slot.id == null) {
            return null;
        }
        Entity entity = overworld.getEntity(slot.id);
        return type.isInstance(entity) && !entity.isRemoved() ? type.cast(entity) : null;
    }

    // 毎 tick、オーバーワールドで呼ばれる
    public void tick(ServerLevel level) {
        TitanWorldData data = TitanWorldData.get(level);
        TitanWorldData.Slot slot = data.slot(slotId);
        if (!slot.spawned) {
            return;
        }
        long now = level.getGameTime();
        boolean someoneAboard = someoneAboard(level);
        T titan = current(level);
        if (titan != null) {
            missingSince = -1;
            boolean near = nearestEligible(level, titan.getX(), titan.getZ(), NEAR) != null;
            if (near || someoneAboard) {
                holdTicket(level, titan.chunkPosition(), now);
                unreachableSince = -1;
            } else {
                releaseTicket(level);
                if (unreachableSince < 0) {
                    unreachableSince = now;
                }
            }
            if (now % 20 == 0) {
                data.remember(slot, titan, saveState(titan));
            }
            // 巨神での移動の最中 (人を乗せている・迎えに行く) は、呼び直さない。動きと読み込みは旅の操縦が受け持つ
            if (inVoyage(titan)) {
                frozenSince = -1;
                unreachableSince = -1;
                return;
            }
            // チケットを張っていて、オーバーワールドに人がいる (エンティティが動く) のに tick が進まない
            if (near && !level.players().isEmpty()) {
                if (titan.tickCount != lastTickCount || frozenSince < 0) {
                    lastTickCount = titan.tickCount;
                    frozenSince = now;
                } else if (now - frozenSince > STUCK_AFTER) {
                    SkyAbyss.LOGGER.warn("The {} stopped ticking at {}; calling it again", logName, titan.blockPosition());
                    relocate(level, slot, titan, pickTarget(level), now);
                    return;
                }
            } else {
                frozenSince = -1;
            }
            if (unreachableSince >= 0 && !someoneAboard && !resting(level) && now - unreachableSince > RELOCATE_AFTER) {
                ServerPlayer target = pickTarget(level);
                if (target != null) {
                    relocate(level, slot, titan, target, now);
                }
            }
            return;
        }
        if (now % 20 != 0) {
            return;
        }
        // 本物が読み込まれていない
        ServerPlayer near = slot.hasLast ? nearestEligible(level, slot.lastX, slot.lastZ, NEAR) : null;
        if (slot.hasLast && (near != null || someoneAboard)) {
            // 最後にいた所を読み込み直す
            holdTicket(level, new ChunkPos(BlockPos.containing(slot.lastX, slot.lastY, slot.lastZ)), now);
            unreachableSince = -1;
            if (missingSince < 0) {
                missingSince = now;
            } else if (now - missingSince > RELOAD_GRACE && !busy()) {
                // (巨神での移動の最中は、旅の操縦が巨神を持っているので呼び直さない。読み込まれれば見つかる)
                ServerPlayer target = near != null ? near : pickTarget(level);
                if (target != null) {
                    SkyAbyss.LOGGER.warn("The {} was not found at its last position; calling it again", logName);
                    if (!relocate(level, slot, null, target, now)) {
                        missingSince = now;
                    }
                }
            }
            return;
        }
        releaseTicket(level);
        missingSince = -1;
        if (unreachableSince < 0) {
            unreachableSince = now;
        } else if (!someoneAboard && !resting(level) && now - unreachableSince > RELOCATE_AFTER) {
            ServerPlayer target = pickTarget(level);
            if (target != null) {
                relocate(level, slot, null, target, now);
            }
        }
    }

    // 眠っている巨神は、誰からも遠くなっても呼び直さない (眠ったその場所で、目覚めるまで動かない。TitanDormancy)
    private boolean resting(ServerLevel level) {
        return jp.revati.skyabyss.titan.end.TitanDormancy.isResting(level.getServer(), slotId);
    }

    // 古い個体 (読み込まれていれば) を消し、プレイヤーの遠くへ新しい個体を出して、前の状態を引き継がせる。
    // 呼び直せる位置がなければ (潮殻で海が見つからないなど) 何もしない。そのときは 2 分待ってやり直す
    private boolean relocate(ServerLevel level, TitanWorldData.Slot slot, @Nullable T old, @Nullable ServerPlayer target, long now) {
        if (target == null) {
            return false;
        }
        Vec3 at = relocationPoint(level, target);
        if (at == null) {
            unreachableSince = now;
            frozenSince = -1;
            return false;
        }
        CompoundTag state = old != null ? saveState(old) : slot.state;
        if (old != null) {
            old.discard();
        }
        spawn(level, at.x, at.z, state);
        return true;
    }

    // 新しい巨神を出し、本物として登録する。登録してから加えるので、参加の検査で取り消されない
    public T spawn(ServerLevel level, double x, double z, @Nullable CompoundTag state) {
        TitanWorldData data = TitanWorldData.get(level);
        T old = current(level);
        if (old != null) {
            old.discard();
        }
        T titan = create(level, x, z, state);
        data.register(data.slot(slotId), titan, saveState(titan));
        releaseTicket(level);
        unreachableSince = -1;
        missingSince = -1;
        frozenSince = -1;
        level.addFreshEntity(titan);
        holdTicket(level, titan.chunkPosition(), level.getGameTime());
        announce(level, titan);
        SkyAbyss.LOGGER.info("The {} appeared at {}, {} ({})", logName, (int) x, (int) z, titan.getUUID());
        return titan;
    }

    private void holdTicket(ServerLevel level, ChunkPos pos, long now) {
        if (pos.equals(ticketPos) && now - ticketRefreshed < 20) {
            return;
        }
        if (ticketPos != null && !pos.equals(ticketPos)) {
            level.getChunkSource().removeRegionTicket(ticket, ticketPos, TICKET_RADIUS, ticketPos);
        }
        level.getChunkSource().addRegionTicket(ticket, pos, TICKET_RADIUS, pos);
        ticketPos = pos;
        ticketRefreshed = now;
    }

    public void releaseTicket(ServerLevel level) {
        if (ticketPos != null) {
            level.getChunkSource().removeRegionTicket(ticket, ticketPos, TICKET_RADIUS, ticketPos);
            ticketPos = null;
        }
    }

    // 自己テスト用: 今チケットを張っているチャンク
    @Nullable
    public ChunkPos ticketPos() {
        return ticketPos;
    }

    // 本物でない個体は、ワールドに加わる前に取り消す (古い保存、呼び直す前の個体、/summon など)
    public void handleJoin(EntityJoinLevelEvent event) {
        if (!type.isInstance(event.getEntity()) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity titan = event.getEntity();
        if (level.dimension() != Level.OVERWORLD) {
            event.setCanceled(true);
            return;
        }
        TitanWorldData data = TitanWorldData.get(level);
        TitanWorldData.Slot slot = data.slot(slotId);
        if (slot.id == null) {
            // 記録より前のワールドの個体か、まだ誰もいないときの /summon。これを本物とする
            data.register(slot, titan, saveState(type.cast(titan)));
            return;
        }
        if (!slot.id.equals(titan.getUUID())) {
            SkyAbyss.LOGGER.info("Discarded a stale {} {} (the current one is {})", logName, titan.getUUID(), slot.id);
            event.setCanceled(true);
        }
    }

    // サーバーが止まったとき (シングルプレイで次のワールドに持ち越さない)
    public void reset() {
        ticketPos = null;
        unreachableSince = -1;
        missingSince = -1;
        frozenSince = -1;
        lastTickCount = -1;
    }

    // ---- プレイヤーの選び方 (巨神で共通)

    public static boolean eligible(ServerPlayer player) {
        return !player.isSpectator() && player.isAlive();
    }

    @Nullable
    public static ServerPlayer nearestEligible(ServerLevel level, double x, double z, double range) {
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (ServerPlayer player : Players.snapshot(level.players())) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            double d = dx * dx + dz * dz;
            if (eligible(player) && d < bestDistance) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    @Nullable
    public static ServerPlayer randomEligible(ServerLevel level) {
        List<ServerPlayer> players = level.players().stream().filter(TitanKeeper::eligible).toList();
        return players.isEmpty() ? null : players.get(level.random.nextInt(players.size()));
    }
}

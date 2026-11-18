package jp.revati.skyabyss.trial;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 祠から入った順路の守り (2026-11-15 の試遊の声):
//   能力を選ぶ間は無敵 「試練の後の能力選ぶとき無敵にして。落下ダメージで死んだ」
//       挑戦の加護を選んでいる間 (RouteBoons.offered) と、次の部屋へ運ばれるのを待つ間 (TrialRoute.pending) は傷を受けない
//   入り直したら祠へ 「巨神の祠に入ってるときにワールドから抜けると、再度参加したときは入る前のところでスポーンするように」
//       祠から背へ運ぶとき印 (SHRINE_TAG = 巨神) を付け、背の中でログインしたら祠の前へ戻す。背を出たら (地上へ移ったら) 印を消す
//   全滅は祠へ 「祠から入って死んだら、祠じゃないところでスポーンする」
//       祠からの順路で、背に残る仲間が全員倒れたら (一人なら自分が倒れたら)、心臓の戦いの全滅と同じく祠の前で起きて順路は終わる
//       (titan/group/WipeReturn)。心臓・中ボスの間は GroupBossRoom が同じことをするので、ここは歩・智・闘の部屋だけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RouteSafety {
    private RouteSafety() {
    }

    static final String SHRINE_TAG = "skyabyss_shrine_entry";

    // 祠から背へ運んだ (TitanShrine)
    public static void markShrine(ServerPlayer p, String titan) {
        p.getPersistentData().putString(SHRINE_TAG, titan);
    }

    public static String shrineOf(ServerPlayer p) {
        CompoundTag t = p.getPersistentData();
        return t.contains(SHRINE_TAG) ? t.getString(SHRINE_TAG) : "";
    }

    // ---------------------------------------------------------------- 能力を選ぶ間は無敵
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level) || Titans.titanOfBack(level.dimension()) == null) {
            return;
        }
        if (RouteBoons.offered(p) || TrialRoute.pending(Groups.keyOf(p))) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 入り直したら祠へ
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        String titan = shrineOf(p);
        if (titan.isEmpty()) {
            return;
        }
        if (Titans.titanOfBack(p.level().dimension()) == null) {
            p.getPersistentData().remove(SHRINE_TAG);
            return;
        }
        // 背の仕組みのログインの処理 (休み場の確かめ・預かり物) が済んでから
        Tasks.later(5, () -> {
            if (p.hasDisconnected() || Titans.titanOfBack(p.level().dimension()) == null) {
                return;
            }
            if (jp.revati.skyabyss.titan.shrine.TitanShrine.sendTo(p, titan)) {
                p.getPersistentData().remove(SHRINE_TAG);
                Notice.narrate(p, "message.skyabyss.shrine.rejoin_home");
                SkyAbyss.LOGGER.info("{} logged back in on the {} back after a shrine route; sent to the shrine", p.getGameProfile().getName(), titan);
            }
        });
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Titans.titanOfBack(event.getTo()) == null) {
            p.getPersistentData().remove(SHRINE_TAG);
        }
    }

    // ---------------------------------------------------------------- 全滅は祠へ
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel back)
                || Titans.titanOfBack(back.dimension()) == null) {
            return;
        }
        UUID group = Groups.keyOf(p);
        if (!TrialRoute.active(back, group) || !TrialRoute.fromShrine(back, group) || TrialRoute.mid(back, group)
                || TrialRoute.step(back, group) >= TrialRoute.LAST_STEP || jp.revati.skyabyss.titan.group.WipeReturn.pending(p.getUUID())) {
            return;
        }
        Set<UUID> fallen = new HashSet<>();
        for (ServerPlayer m : TrialRoute.members(back, group)) {
            if (m != p && m.isAlive() && !m.isSpectator()) {
                // まだ立っている仲間がいる: 今までどおり今の部屋の入口から戻る (TrialRoute.rejoinAfterDeath)
                return;
            }
            fallen.add(m.getUUID());
        }
        fallen.add(p.getUUID());
        jp.revati.skyabyss.titan.group.WipeReturn.wiped(back, group, fallen);
    }
}

package jp.revati.skyabyss.titan.travel;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 巨神での移動の「記録」の窓口 (docs/design/28 以降)。移動そのものは巨神の子 (titan.young) が受け持つ。ここに残るのは、
// プレイヤーごとの記録 (認められた巨神・出会い・覚えた碑・回数・次に笛を吹ける時刻)、心臓を倒したときの笛の贈り物、
// 旅のあとの「降ろす場所」の印 (ログアウト・サーバー停止で子から外れた人を、次のログインで地面へ降ろす)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Voyages {
    // ログアウト・サーバーの停止で子から外れた人を、次のログインで降ろす場所 (プレイヤーの保存データ)
    private static final String TAG_SAFE = "skyabyss_voyage_safe";

    private Voyages() {
    }

    public static TravelState state(ServerPlayer player) {
        return player.getData(TravelSetup.STATE);
    }

    // 巨神に認められているか: その巨神の心臓を倒した (巨神の記憶に撃破の記録がある)、またはコマンドで認めた
    public static boolean isUnlocked(ServerPlayer player, TitanKind kind) {
        if (state(player).unlocked.contains(kind.id())) {
            return true;
        }
        TitanMemoryData.Record r = TitanMemoryData.get(player.server).peek(player.getUUID(), kind.id());
        return r != null && r.clears > 0;
    }

    // 残りの待ち (tick。笛を吹き直せるまで)
    public static long cooldownLeft(ServerPlayer player) {
        long now = player.server.overworld().getGameTime();
        return Math.max(0, state(player).readyAt - now);
    }

    // ---------------------------------------------------------------- 記録

    // 着いた (旅の回数・クエスト)
    public static void arrived(ServerPlayer player, jp.revati.skyabyss.titan.young.YoungKind kind, @Nullable TravelNetwork.Dest dest) {
        TravelState s = state(player);
        s.voyages++;
        Quests.signal(player, "titan_voyage");
    }

    public static void setCooldown(ServerPlayer player, int ticks) {
        TravelState s = state(player);
        s.readyAt = Math.max(s.readyAt, player.server.overworld().getGameTime() + ticks);
    }

    // 世界にもうない碑を、プレイヤーの記録から外す。外した数を返す
    public static int pruneLost(TravelState s, TravelWorldData world) {
        int before = s.waymarks.size();
        s.waymarks.removeIf(pos -> world.waymark(pos) == null);
        return before - s.waymarks.size();
    }

    public static Component waymarkName(String biome) {
        return TravelDest.waymarkName(biome);
    }

    // ---------------------------------------------------------------- 降ろす場所の印

    public static void markSafe(ServerPlayer player, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        player.getPersistentData().put(TAG_SAFE, tag);
    }

    public static void clearSafe(ServerPlayer player) {
        player.getPersistentData().remove(TAG_SAFE);
    }

    // ログインのときの記録の整え: ログアウト中の呼んだ人の待ち (古い版の記録) を本人へ移し、子の旅の途中で抜けた人を地面へ降ろす
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long ready = TravelWorldData.get(player.server).takeOfflineReady(player.getUUID());
        if (ready != null) {
            state(player).readyAt = Math.max(state(player).readyAt, ready);
        }
        CompoundTag tag = player.getPersistentData().getCompound(TAG_SAFE);
        if (!tag.contains("x")) {
            return;
        }
        clearSafe(player);
        // 旅の途中で抜けた (サーバーが落ちた場合も)。宙に残さず、降ろす場所へ
        if (player.isNoGravity()) {
            player.setNoGravity(false);
        }
        if (player.isAlive() && player.level().dimension() == Level.OVERWORLD) {
            ServerLevel overworld = player.serverLevel();
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            // 着く先の地面の高さは、チャンクを読み込んでから決める (読み込まれていないと高さが最低になり、地中へ埋まる)。
            // 古い高さで埋まらないよう、高い方を取る。ログインのときに 1 チャンクだけ同期で読み込む
            overworld.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            int top = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
            double y = Math.max(pos.getY(), top) + 0.1;
            player.fallDistance = 0;
            player.teleportTo(overworld, pos.getX() + 0.5, y, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 10, 0, false, false, true));
            Notice.narrate(player, "message.skyabyss.travel.woke_landed");
        }
    }

    // ---------------------------------------------------------------- 笛の贈り物

    // 解放の確認と、最初の呼び笛 (2 秒ごと。古いワールドで心臓をすでに倒している人にも渡る)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 7 || !player.isAlive()) {
            return;
        }
        giveCalls(player);
    }

    // 心臓を倒した巨神 (巨神の記憶の撃破の記録) の呼び笛を、一度だけ渡す。渡した数を返す
    static int giveCalls(ServerPlayer player) {
        int given = 0;
        for (TitanKind kind : TitanKind.all()) {
            TravelState s = state(player);
            // 笛は出会ったときにもう渡っていることがある (TitanCalls)。そのときも、心臓を倒したら「認められた」言葉は一度出す
            boolean hadCall = s.callGiven.contains(kind.id());
            if (!isUnlocked(player, kind) || (hadCall && s.unlocked.contains(kind.id()))) {
                continue;
            }
            given++;
            s.callGiven.add(kind.id());
            s.unlocked.add(kind.id());
            if (!hadCall) {
                ItemStack call = new ItemStack(kind.callItem().get());
                // 袋に入る設定のとき (使う入力ができたあと) は袋へ。今は笛は手に持って使うので持ち物へ (docs/design/22)
                jp.revati.skyabyss.pouch.PouchRouting.give(player, call);
                if (!call.isEmpty()) {
                    player.drop(call, false);
                }
            }
            Notice.narrate(player, "message.skyabyss.young.recognized." + kind.id());
            Notice.hint(player, "message.skyabyss.young.recognized_hint");
            player.playNotifySound(kind.callSound().get(), SoundSource.PLAYERS, 0.6f, 1.2f);
            Quests.signal(player, "titan_travel_" + kind.id());
        }
        return given;
    }
}

package jp.revati.skyabyss.titan.group;

import jp.revati.skyabyss.util.Players;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 試練の敵・ボスが、どのグループ (パーティ、または一人) のものかを持つ印 (docs/design/10 の 10 章)。
// 実装する基底クラス (GuardMob・TideMob・StormMob・EmberMob) は、次の三つを必ず上書きする:
//   broadcastToPlayer → 他グループの人にはパケットを送らない (描画も当たり判定も音も届かない)
//   canAttack         → 他グループの人を狙わない (最寄りの探索に入れない)
//   playSound         → 鳴き声・足音は自分のグループにだけ送る
// groupId が null の個体 (スポーンエッグなど) は、誰にでも見えて誰でも狙える。
public interface GroupOwned {
    @Nullable
    UUID groupId();

    void setGroupId(@Nullable UUID group);

    // viewer にこの個体を送ってよいか (観戦者は全部見える)
    public static boolean visibleTo(Entity self, ServerPlayer viewer) {
        if (!(self instanceof GroupOwned owned) || owned.groupId() == null || viewer.isSpectator()) {
            return true;
        }
        return owned.groupId().equals(Groups.keyOf(viewer));
    }

    // 狙ってよい相手か。どちらかが無所属、または同じグループなら true
    static boolean mayTarget(Entity self, @Nullable LivingEntity target) {
        if (!(self instanceof GroupOwned owned) || owned.groupId() == null || target == null) {
            return true;
        }
        UUID other = Groups.keyOf(target);
        return other == null || other.equals(owned.groupId());
    }

    // 音を自分のグループの人にだけ送る (持ち主なしなら false = 従来どおり全員へ)。true なら送り済み
    static boolean playToGroup(Entity self, SoundEvent sound, float volume, float pitch) {
        if (!(self instanceof GroupOwned owned) || owned.groupId() == null || !(self.level() instanceof ServerLevel level)) {
            return false;
        }
        if (self.isSilent()) {
            return true;
        }
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, self.getSoundSource(), self.getX(), self.getY(), self.getZ(),
                volume, pitch, level.random.nextLong());
        for (ServerPlayer player : Players.snapshot(level.players())) {
            if (player.distanceToSqr(self) < 64 * 64 && (player.isSpectator() || owned.groupId().equals(Groups.keyOf(player)))) {
                player.connection.send(packet);
            }
        }
        return true;
    }
}

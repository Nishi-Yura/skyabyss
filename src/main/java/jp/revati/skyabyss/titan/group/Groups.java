package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.PartyData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

// 「グループ」= パーティ、またはパーティに入っていない一人 (一人のグループ)。
// 試練の進み・扉・宝箱・休み場・心臓の撃破・リセットの番号は、グループごとに持つ (docs/design/10 の 10 章)。
// グループの鍵 (UUID): パーティならパーティの id、一人ならプレイヤーの UUID。
public final class Groups {
    private Groups() {
    }

    public static UUID keyOf(MinecraftServer server, UUID player) {
        PartyData.Party party = PartyData.get(server).of(player);
        return party == null ? player : party.id;
    }

    public static UUID keyOf(ServerPlayer player) {
        return keyOf(player.server, player.getUUID());
    }

    // 人・グループ付きの敵・その飛び道具の持ち主から、グループの鍵を引く。どれでもなければ null (所属なし)
    @Nullable
    public static UUID keyOf(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return keyOf(player);
        }
        if (entity instanceof GroupOwned owned) {
            return owned.groupId();
        }
        if (entity instanceof Projectile projectile) {
            return keyOf(projectile.getOwner());
        }
        return null;
    }

    // 二者が別のグループか (どちらかが所属なしなら false)
    public static boolean differ(@Nullable Entity a, @Nullable Entity b) {
        UUID ka = keyOf(a);
        UUID kb = keyOf(b);
        return ka != null && kb != null && !ka.equals(kb);
    }

    public static boolean isMember(ServerPlayer player, UUID group) {
        return keyOf(player).equals(group);
    }

    // そのディメンションにいる、グループの生きている人 (観戦者を除く)
    public static List<ServerPlayer> alive(ServerLevel level, UUID group) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && isMember(player, group)) {
                list.add(player);
            }
        }
        return list;
    }

    // そのディメンションにいる、グループの人 (倒れている人・観戦者も含む)
    public static List<ServerPlayer> online(ServerLevel level, UUID group) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (isMember(player, group)) {
                list.add(player);
            }
        }
        return list;
    }

    // 箱の中にいる、グループの生きている人 (観戦者を除く)
    public static List<ServerPlayer> inBox(ServerLevel level, UUID group, AABB box) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
            if (isMember(player, group)) {
                list.add(player);
            }
        }
        return list;
    }

    // 箱の中にいる、生きている人 (観戦者を除く) が属するグループの鍵 (重複なし)
    public static List<UUID> groupsIn(ServerLevel level, AABB box) {
        List<UUID> keys = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
            UUID key = keyOf(player);
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    // グループの人にだけ、位置つきの音を送る (範囲 64)
    public static void sound(ServerLevel level, UUID group, double x, double y, double z, net.minecraft.sounds.SoundEvent sound,
            net.minecraft.sounds.SoundSource source, float volume, float pitch) {
        var holder = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        var packet = new net.minecraft.network.protocol.game.ClientboundSoundPacket(holder, source, x, y, z, volume, pitch, level.random.nextLong());
        for (ServerPlayer player : online(level, group)) {
            if (player.distanceToSqr(x, y, z) < 64 * 64 && player.connection != null) {
                player.connection.send(packet);
            }
        }
    }

    // グループの人へ知らせる (そのディメンションにいる全員。倒れている人も含む)
    public static void tell(ServerLevel level, UUID group, Component message) {
        for (ServerPlayer player : online(level, group)) {
            Notice.send(player, message);
        }
    }

    // グループの人の画面 (アクションバー) へ
    public static void bar(ServerLevel level, UUID group, Component message) {
        for (ServerPlayer player : online(level, group)) {
            Notice.bar(player, message);
        }
    }
}

package jp.revati.skyabyss.titan.storm.back;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 雷翼の自己テストの「人」(二つのグループで同じ背中を試すため)。NeoForge の FakePlayer に、同じディメンションの中の
// teleportTo で本当に動く (FakePlayer の通信は何もしないので、そのままでは動かない) ことだけを足したもの。
// join でディメンションの人の一覧に入れ (部屋の占有・グループの人の一覧に数えられる)、leave で外す。グループの鍵は UUID (一人のグループ)
public final class StormTestPlayer extends FakePlayer {
    public StormTestPlayer(ServerLevel level, UUID id, String name) {
        super(level, new GameProfile(id, name));
    }

    @Override
    public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        if (level == level()) {
            moveTo(x, y, z, yaw, pitch);
        } else {
            super.teleportTo(level, x, y, z, yaw, pitch);
        }
    }

    // 人を作る (ディメンションには入れない。見える・狙う・傷の判定だけに使う)
    public static StormTestPlayer make(ServerLevel level, UUID id, String name) {
        StormTestPlayer p = new StormTestPlayer(level, id, name);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    // 人を作って、at に立たせてディメンションに入れる
    public static StormTestPlayer join(ServerLevel level, UUID id, String name, Vec3 at) {
        StormTestPlayer p = make(level, id, name);
        p.moveTo(at.x, at.y, at.z, 0f, 0f);
        level.addNewPlayer(p);
        return p;
    }

    public void leave() {
        if (!isRemoved()) {
            serverLevel().removePlayerImmediately(this, Entity.RemovalReason.DISCARDED);
        }
    }
}

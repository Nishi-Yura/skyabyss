package jp.revati.skyabyss.town;

import java.util.EnumSet;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 町のクエストの間だけ現れる人 (docs/design/35 の 3.5)。型は一つ (skyabyss:town_guest) で、誰か (TownGuests.Guest の id) を持つ。
//   会話の役の ID は skyabyss:guest_<id> (NpcEntity.role を上書き)。会話は data/skyabyss/dialogue/guest_<id>.json
//   見た目は係の人の案 A の形 (attendant_a) に、人ごとの絵 (textures/lowpoly/guest_<id>.png)。子どもは小さく描く (TownGuests.Guest.scale)
//   **保存しない** (shouldBeSaved = false)。いるべきかはクエストの記録から毎回決め直す (TownGuests の見回り)
//   連れ歩き: follow を決めると、その人のあとを付いて歩く (雷の夜の子ども)
public class TownGuestEntity extends NpcEntity {
    private static final EntityDataAccessor<String> GUEST = SynchedEntityData.defineId(TownGuestEntity.class, EntityDataSerializers.STRING);
    // 付いて歩く相手 (サーバーだけ)
    @Nullable
    private UUID follow;

    public TownGuestEntity(EntityType<? extends TownGuestEntity> type, Level level) {
        super(type, level, 3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GUEST, "apprentice");
    }

    public String guestId() {
        return entityData.get(GUEST);
    }

    public void setGuest(String id) {
        entityData.set(GUEST, id);
        nameByRole();
    }

    @Override
    public String role() {
        return roleOf(entityData.get(GUEST));
    }

    public static String roleOf(String guestId) {
        return TownGuests.ROLE_PREFIX + guestId;
    }

    @Override
    public void nameByRole() {
        setCustomName(Component.translatable("entity.skyabyss.guest_" + entityData.get(GUEST)));
    }

    // 保存しない (クエストの記録から立たせ直す。古い人がワールドに残らない)
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // 撮影・コマンドの summon 用 ({Guest:"child"})。ワールドには保存しない
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Guest", guestId());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Guest") && TownGuests.byId(tag.getString("Guest")) != null) {
            setGuest(tag.getString("Guest"));
        }
    }

    // ---------------------------------------------------------------- 連れ歩き
    @Nullable
    public UUID follow() {
        return follow;
    }

    // 付いて歩く相手を決める (null で止める。止めたら家のまわりに戻る)
    public void setFollow(@Nullable UUID player) {
        if (java.util.Objects.equals(follow, player)) {
            return;
        }
        follow = player;
        if (player != null) {
            clearRestriction();
        } else if (home() != null) {
            setHome(home());
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(3, new FollowGoal());
    }

    private final class FollowGoal extends Goal {
        FollowGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private Player target() {
            return follow == null ? null : level().getPlayerByUUID(follow);
        }

        @Override
        public boolean canUse() {
            Player p = target();
            return p != null && !isTalking() && distanceToSqr(p) > 2.5 * 2.5;
        }

        @Override
        public boolean canContinueToUse() {
            Player p = target();
            return p != null && !isTalking() && distanceToSqr(p) > 1.8 * 1.8;
        }

        @Override
        public void tick() {
            Player p = target();
            if (p == null) {
                return;
            }
            getLookControl().setLookAt(p, 30f, 30f);
            // 離れすぎたら (角で見失った・扉) 後ろに寄る
            if (distanceToSqr(p) > 16 * 16) {
                BlockPos at = p.blockPosition();
                teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                getNavigation().stop();
                return;
            }
            if (tickCount % 10 == 0) {
                getNavigation().moveTo(p, 0.9);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }
}

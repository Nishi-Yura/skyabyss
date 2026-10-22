package jp.revati.skyabyss.facility;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.npc.NpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// 都の施設の係の人 (docs/design/35 の 2・3 章)。エンティティの型は一つ (skyabyss:attendant) で、役 (AttendantRole) を持つ。
//   会話の役の ID は skyabyss:attendant_<役> (NpcEntity.role を上書き)。好感度・会話の印・頭の印 (NpcMarkers) は役ごとに分かれる
//   見た目: 低ポリの形は一つ (見た目の案 a/b/c = variant 1〜3、0 は既定の案)、絵は役ごと (client/npc/AttendantRenderer)
//   受付の机の前に立ち、ほとんど動かない (家のまわり 2 ブロック)
public class AttendantEntity extends NpcEntity {
    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(AttendantEntity.class, EntityDataSerializers.STRING);

    public AttendantEntity(EntityType<? extends AttendantEntity> type, Level level) {
        super(type, level, 2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, AttendantRole.GUILD_CLERK.id);
    }

    @Nullable
    public AttendantRole attendantRole() {
        return AttendantRole.byId(entityData.get(ROLE));
    }

    public String roleId() {
        return entityData.get(ROLE);
    }

    // 深淵の生き残りなど、施設の役でない人 (役の ID をそのまま。会話は skyabyss:attendant_<id>、絵は役の ID の絵)
    public void setRoleId(String id) {
        entityData.set(ROLE, id);
        nameByRole();
    }

    public static boolean extraRole(String id) {
        return id.startsWith("survivor_") || id.startsWith("abyss_");
    }

    public void setRole(AttendantRole role) {
        entityData.set(ROLE, role.id);
        nameByRole();
    }

    @Override
    public String role() {
        return AttendantRole.DIALOGUE_PREFIX + entityData.get(ROLE);
    }

    @Override
    public void nameByRole() {
        setCustomName(Component.translatable("entity.skyabyss.attendant." + entityData.get(ROLE)));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", entityData.get(ROLE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        AttendantRole r = AttendantRole.byId(tag.getString("Role"));
        if (r != null) {
            entityData.set(ROLE, r.id);
        } else if (extraRole(tag.getString("Role"))) {
            entityData.set(ROLE, tag.getString("Role"));
        }
    }
}

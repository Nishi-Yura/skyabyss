package jp.revati.skyabyss.tablet;

import jp.revati.skyabyss.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// 碑に刻まれた文のキー (lang の "<key>.1"〜"<key>.4")
public class TabletBlockEntity extends BlockEntity {
    private String key = "";

    public TabletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TABLET.get(), pos, state);
    }

    public String key() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("key", key);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        key = tag.getString("key");
    }
}

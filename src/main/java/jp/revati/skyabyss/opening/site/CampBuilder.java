package jp.revati.skyabyss.opening.site;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LoreSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;

// 旅の野営地 (docs/design/17 の 4.1)。消えた焚き火、倒れた荷車、開いた寝袋、木箱 (パン 4・松明 8・木の斧 1)、切り株の上の古い帳面。
// 帳面は伝承の書を ItemEntity にして置く (消えない。タグ NOTEBOOK_TAG)。拾ったときの扱いは拍子 (opening/) の側で決める
public final class CampBuilder {
    public static final String NOTEBOOK_TAG = "skyabyss_opening_notebook";

    private CampBuilder() {
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        return new SiteJob(level, data, entry, Blueprint.get("camp"), "camp", CampBuilder::finish);
    }

    static void finish(SiteJob job) {
        ServerLevel level = job.level;
        BlockPos barrel = job.mark("barrel");
        if (barrel != null && level.getBlockEntity(barrel) instanceof BarrelBlockEntity be) {
            be.setItem(0, new ItemStack(Items.BREAD, 4));
            be.setItem(1, new ItemStack(Items.TORCH, 8));
            be.setItem(2, new ItemStack(Items.WOODEN_AXE));
        } else {
            SkyAbyss.LOGGER.error("Opening camp: no barrel at {}", barrel);
        }
        BlockPos nb = job.mark("notebook");
        if (nb != null) {
            job.entry.extra("notebook", nb);
            List<ItemEntity> existing = level.getEntitiesOfClass(ItemEntity.class, new AABB(nb).inflate(2), e -> e.getTags().contains(NOTEBOOK_TAG));
            if (existing.isEmpty()) {
                ItemEntity item = new ItemEntity(level, nb.getX() + 0.5, nb.getY() + 0.02, nb.getZ() + 0.5, new ItemStack(LoreSetup.LORE_BOOK.get()));
                item.setDeltaMovement(0, 0, 0);
                item.setNoPickUpDelay();
                item.setUnlimitedLifetime();
                item.addTag(NOTEBOOK_TAG);
                level.addFreshEntity(item);
            }
        }
        BlockPos stand = job.mark("stand");
        if (stand != null) {
            job.entry.extra("stand", stand);
        }
    }
}

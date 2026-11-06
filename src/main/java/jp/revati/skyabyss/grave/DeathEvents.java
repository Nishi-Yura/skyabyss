package jp.revati.skyabyss.grave;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.titan.TitanBacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 地上などで倒れたときの持ち物の扱い (docs/design/40 の 2 章「落とさない」)。巨神の背中の死は TitanBackTrials のまま (ここは何もしない)。
//   倒れても持ち物と経験値は全部残る。ふだんはバニラの keepInventory (RpgRules.migrate が入れる) が残すので、ここは何もしない。
//   keepInventory が切られていても残す: 倒れた瞬間 (LivingDeathEvent) に持ち物を「預かり」(persisted) に入れて外し、新しい体 (Clone) へ戻す。
//   2026-11-05 の決めごと ct_grave = cut で、墓・預かり帳・語り部の救いの型 (skyabyssDeathMode など) は消した。前の版の墓は Graves が開けて返す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DeathEvents {
    static final String KEEP_TAG = "skyabyss_death_keep";
    static final String NO_XP_TAG = "skyabyss_death_no_xp";
    static final String NOTE_TAG = "skyabyss_death_note";

    private DeathEvents() {
    }

    // 倒れた結果 (自己テストの確認用)
    public record Outcome(int keptStacks, int xpLevel) {
    }

    static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag persistedForWrite(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- 倒れたとき
    // ほかの仕組みが死を取り消したときに動かないよう、最後に (LOWEST) 動く
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        handleDeath(player);
    }

    // 倒れた人の持ち物と経験値を預かる。背中の死・バニラの keepInventory では何もしない (null)
    @Nullable
    public static Outcome handleDeath(ServerPlayer player) {
        if (TitanBacks.isBack(player.level().dimension()) || player.server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return null;
        }
        CompoundTag tag = persistedForWrite(player);
        if (tag.contains(KEEP_TAG)) {
            return null;
        }
        // 留め袋の中の品は持ち物にないので、ここには来ない (袋は copyOnDeath で新しい体へ。docs/design/22 の 5 章)
        Inventory inv = player.getInventory();
        Inventory keepInv = new Inventory(player);
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty()) {
                keepInv.setItem(slot, stack.copy());
                inv.setItem(slot, ItemStack.EMPTY);
            }
        }
        ListTag kept = keepInv.save(new ListTag());
        CompoundTag keep = new CompoundTag();
        keep.put("Inventory", kept);
        keep.putInt("Level", player.experienceLevel);
        keep.putFloat("Progress", player.experienceProgress);
        keep.putInt("Total", player.totalExperience);
        tag.put(KEEP_TAG, keep);
        tag.putBoolean(NO_XP_TAG, true);
        tag.putBoolean(NOTE_TAG, true);
        return new Outcome(kept.size(), player.experienceLevel);
    }

    // 経験値の玉は落とさない (預かった)
    @SubscribeEvent
    static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && persisted(player).getBoolean(NO_XP_TAG)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 復活したとき
    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            restore(event.getOriginal(), event.getEntity());
        }
    }

    // 預かった持ち物と経験値を新しい体へ戻す
    public static void restore(Player original, Player fresh) {
        CompoundTag oldTag = persisted(original);
        CompoundTag newTag = persistedForWrite(fresh);
        if (oldTag.contains(KEEP_TAG)) {
            CompoundTag kept = oldTag.getCompound(KEEP_TAG);
            // 新しい体の持ち物に、すでに品があっても (ほかの仕組みが入れた) 消さない: 空いた枠にだけ戻す
            Inventory inv = fresh.getInventory();
            Inventory temp = new Inventory(fresh);
            temp.load(kept.getList("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND));
            for (int i = 0; i < temp.getContainerSize(); i++) {
                ItemStack s = temp.getItem(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (inv.getItem(i).isEmpty()) {
                    inv.setItem(i, s.copy());
                } else {
                    ItemStack rest = s.copy();
                    if (!inv.add(rest) && !rest.isEmpty()) {
                        fresh.drop(rest, false, true);
                    }
                }
            }
            if (kept.contains("Total")) {
                fresh.experienceLevel = kept.getInt("Level");
                fresh.experienceProgress = kept.getFloat("Progress");
                fresh.totalExperience = kept.getInt("Total");
            }
            oldTag.remove(KEEP_TAG);
        }
        newTag.remove(KEEP_TAG);
        oldTag.remove(NO_XP_TAG);
        newTag.remove(NO_XP_TAG);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        // 宿を寝る場所に決めているなら、町の真ん中から始まる
        net.minecraft.world.phys.Vec3 plaza = jp.revati.skyabyss.facility.FacilityActions.plazaSpotFor(player);
        if (plaza != null) {
            jp.revati.skyabyss.util.RespawnMove.after(player, player.server.overworld(),
                    () -> player.teleportTo(player.server.overworld(), plaza.x, plaza.y, plaza.z, player.getYRot(), 0f));
        }
        CompoundTag tag = persisted(player);
        if (tag.contains(NOTE_TAG)) {
            tag.remove(NOTE_TAG);
            Notice.hint(player, "skyabyss_grave.kept_all");
        }
    }
}

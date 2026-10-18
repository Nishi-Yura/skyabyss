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
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 地上などで倒れたときの持ち物の扱い (docs/design/11_record_screen.md の 6 章)。巨神の背中の死は TitanBackTrials のまま (ここは何もしない)。
//   どの型でも: 大事な品 (Keepsakes.guarded。欠片・天の鍵・心核・呼び笛・呼び貝) は持ち物に残る
//   grave  : 装備中の防具・左手・装飾品・ホットバーを持って復活し、残りの持ち物と経験値 (バニラの玉の分) は墓へ
//   vanilla: 大事な品だけ残し、あとはバニラのとおり散らばる
//   keep   : 全部残る (経験値も)
//   ledger : keep と同じ。耐久の減りが代償
//   バニラの keepInventory が true ならバニラのとおり (何もしない)
// 仕組み: 倒れた瞬間 (LivingDeathEvent) に持ち物を二つに分け、残す分を「預かり」(persisted) に入れて持ち物から外し、
// 墓へ行く分も外す。バニラの散らかしは空の持ち物を散らすだけになり、新しい体 (Clone) へ預かりを戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DeathEvents {
    static final String KEEP_TAG = "skyabyss_death_keep";
    static final String NO_XP_TAG = "skyabyss_death_no_xp";
    static final String NOTE_TAG = "skyabyss_death_note";
    static final String SAFE_TAG = "skyabyss_last_safe";

    private DeathEvents() {
    }

    // 倒れた結果 (復活したときの知らせと、自己テストの確認用)
    public record Outcome(DeathRules.Mode mode, int keptStacks, int graveStacks, int graveItems, int xp, @Nullable GraveData.Grave grave, int accessoryStacks) {
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

    // ---------------------------------------------------------------- 装飾品の枠の扱い
    // 倒れても装飾品の枠の中身を落とさないか (Accessories.keepsOnDeath が見る)
    public static boolean keepsAccessories(ServerPlayer player) {
        DeathRules.Mode mode = DeathRules.mode(player.server);
        return switch (mode) {
            case KEEP, LEDGER -> true;
            case GRAVE -> DeathRules.keepGear(player.server);
            case VANILLA -> false;
        };
    }

    // ---------------------------------------------------------------- 倒れたとき
    // ほかの仕組みが死を取り消したときに分けないよう、最後に (LOWEST) 動く
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        handleDeath(player);
    }

    // 倒れた人の持ち物を分ける。背中の死・バニラの keepInventory では何もしない (null)
    @Nullable
    public static Outcome handleDeath(ServerPlayer player) {
        if (TitanBacks.isBack(player.level().dimension()) || DeathRules.vanillaKeep(player.server)) {
            return null;
        }
        CompoundTag tag = persistedForWrite(player);
        if (tag.contains(KEEP_TAG)) {
            return null;
        }
        DeathRules.Mode mode = DeathRules.mode(player.server);
        boolean keepAll = mode == DeathRules.Mode.KEEP || mode == DeathRules.Mode.LEDGER;
        boolean keepGear = mode == DeathRules.Mode.GRAVE && DeathRules.keepGear(player.server);
        Inventory inv = player.getInventory();
        // keepInv: 持って復活する品 (同じ枠の並びで預かる)。grave: 墓へ入れる品
        Inventory keepInv = new Inventory(player);
        List<ItemStack> grave = new ArrayList<>();
        int armorStart = inv.items.size();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            // 留め袋の中の品は持ち物にないので、ここには来ない (袋は copyOnDeath で新しい体へ。docs/design/22 の 5 章)。
            // 袋の外に残っている品 (掃除の前の 1 秒・袋が満杯でこぼれた品・取り出し済みの品) は、今までどおりの扱い
            boolean important = Keepsakes.guarded(stack);
            if (mode == DeathRules.Mode.VANILLA && !important) {
                // バニラ: 大事な品以外はそのまま散らばらせる (消滅の呪いもバニラが消す)
                continue;
            }
            boolean gear = slot >= armorStart || slot < Inventory.getSelectionSize();
            if (important || keepAll || (keepGear && gear)) {
                if (!keepAll && !important && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    inv.setItem(slot, ItemStack.EMPTY);
                    continue;
                }
                keepInv.setItem(slot, stack.copy());
            } else if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                grave.add(stack.copy());
            }
            inv.setItem(slot, ItemStack.EMPTY);
        }
        // 装飾品の枠: grave で装備を残さない設定のときは墓へ (残すときは枠に残り、バニラ型では Accessories の落とし方に任せる)
        int accessoryStacks = 0;
        if (mode == DeathRules.Mode.GRAVE && !keepGear) {
            for (ItemEntity drop : Accessories.takeDeathDrops(player)) {
                grave.add(drop.getItem().copy());
                accessoryStacks++;
            }
        }
        // 残す分に耐久の減りを足して預かる
        if (mode != DeathRules.Mode.VANILLA) {
            applyDurability(keepInv, DeathRules.durabilityPercent(player.server));
        }
        ListTag kept = keepInv.save(new ListTag());
        int keptStacks = kept.size();
        if (keptStacks > 0 || keepAll) {
            CompoundTag keep = new CompoundTag();
            keep.put("Inventory", kept);
            if (keepAll) {
                keep.putInt("Level", player.experienceLevel);
                keep.putFloat("Progress", player.experienceProgress);
                keep.putInt("Total", player.totalExperience);
            }
            tag.put(KEEP_TAG, keep);
        }
        // 経験値: grave は玉の分を墓へ、keep / ledger は預かる (どちらも玉は落とさない)
        int xp = 0;
        if (mode != DeathRules.Mode.VANILLA) {
            tag.putBoolean(NO_XP_TAG, true);
        }
        if (mode == DeathRules.Mode.GRAVE) {
            xp = vanillaXp(player);
        }
        GraveData.Grave made = null;
        if (!grave.isEmpty() || xp > 0) {
            made = Graves.create(player, player.serverLevel(), player.blockPosition(), lastSafe(player), grave, xp);
        }
        int graveItems = 0;
        for (ItemStack s : grave) {
            graveItems += s.getCount();
        }
        boolean noSpot = made == null && (!grave.isEmpty() || xp > 0);
        Outcome outcome = new Outcome(mode, keptStacks, grave.size(), graveItems, xp, made, accessoryStacks);
        // 復活したとき知らせる
        CompoundTag note = new CompoundTag();
        note.putString("mode", mode.name());
        if (made != null) {
            note.putUUID("grave", made.id);
        }
        if (noSpot) {
            note.putBoolean("no_spot", true);
        }
        tag.put(NOTE_TAG, note);
        return outcome;
    }

    // バニラが玉として落とす経験値 (レベル × 7、最大 100)
    static int vanillaXp(Player player) {
        return Math.min(player.experienceLevel * 7, 100);
    }

    // 耐久が減る (壊れる手前で止める。壊れない品・耐久のない品は変わらない)
    static void applyDurability(Inventory inv, int percent) {
        if (percent <= 0) {
            return;
        }
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            if (s.isEmpty() || !s.isDamageableItem() || s.has(DataComponents.UNBREAKABLE)) {
                continue;
            }
            int max = s.getMaxDamage();
            int loss = Math.max(1, max * percent / 100);
            s.setDamageValue(Math.min(max - 1, s.getDamageValue() + loss));
        }
    }

    // 経験値の玉は落とさない (grave は墓へ、keep / ledger は預かった)
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

    // 預かった持ち物 (と keep / ledger の経験値) を新しい体へ戻す
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
        CompoundTag tag = persisted(player);
        if (!tag.contains(NOTE_TAG)) {
            return;
        }
        CompoundTag note = tag.getCompound(NOTE_TAG);
        tag.remove(NOTE_TAG);
        if (note.hasUUID("grave")) {
            GraveData.Grave g = GraveData.get(player.server).get(note.getUUID("grave"));
            if (g != null) {
                BlockPos p = g.pos;
                Notice.narrate(player, "skyabyss_grave.made", jp.revati.skyabyss.record.RecordBuilder.dimension(g.dimension.location().toString()), p.getX(), p.getY(), p.getZ());
            }
        } else if (note.getBoolean("no_spot")) {
            Notice.warn(player, "skyabyss_grave.no_spot");
        } else if (DeathRules.Mode.valueOf(note.getString("mode")) != DeathRules.Mode.VANILLA) {
            Notice.hint(player, "skyabyss_grave.kept_all");
        }
    }

    // ---------------------------------------------------------------- 最後に安全だった場所
    // 奈落・溶岩で倒れたときに、墓を置く先に使う (1 秒ごとに、足が固い地面にあって液体と火のそばでないとき控える)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 3 || !player.isAlive() || !player.onGround()
                || player.isInWaterOrBubble() || player.isInLava() || player.isOnFire() || TitanBacks.isBack(player.level().dimension())) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (!(player.level() instanceof ServerLevel level) || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) {
            return;
        }
        CompoundTag tag = persistedForWrite(player);
        CompoundTag safe = new CompoundTag();
        safe.putString("dim", level.dimension().location().toString());
        safe.putLong("pos", pos.asLong());
        tag.put(SAFE_TAG, safe);
    }

    // 倒れた世界と同じ世界で、最後に安全だった場所
    @Nullable
    public static BlockPos lastSafe(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        if (!tag.contains(SAFE_TAG)) {
            return null;
        }
        CompoundTag safe = tag.getCompound(SAFE_TAG);
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(safe.getString("dim")));
        return dim.equals(player.level().dimension()) ? BlockPos.of(safe.getLong("pos")) : null;
    }
}

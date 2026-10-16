package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.travel.TravelSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// 大事な品 (落としたら困る品) の守りと、在りかの調べ。決めたことは docs/design/11_record_screen.md の 4章。
//  - 守る品: 天の鍵の欠片・岩鯨の心核・岩鯨の呼び笛。伝承の書は 05 の仕組み (次の項目で戻る) があるので重ねない
//  - 地面に落ちても消えない (時間で消えない、燃えない、爆発・サボテンで壊れない)。奈落へ落ちたら持ち主の手もとへ戻す
//  - 天の鍵の欠片だけは投げ捨てられない (しまうのは箱へ)。一人一つで、人に渡す意味がないため
//  - 在りか: 持ち物 (中の袋・箱も)、エンダーチェスト、報酬の預かり、背中で倒れたときの預かり、地面 (DropLedger)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Keepsakes {
    // 落ちた品の持ち主 (エンティティの永続データ。倒れて散らばった品には投げた人が付かないため、自分で持つ)
    static final String OWNER_TAG = "skyabyss_record_owner";
    // 背中で倒れたときの預かり物 (TitanBackTrials と同じ名前。PlayerPersisted の中)
    static final String KEPT_TAG = "skyabyss_kept";
    static final String KEPT_EXTRA_TAG = "skyabyss_kept_extra";
    // この高さより下へ落ちた品は奈落に落ちたとみなす (バニラは最低の高さ - 64 で消す)
    static final int VOID_MARGIN = 8;

    private Keepsakes() {
    }

    // ---------------------------------------------------------------- どの品か
    public static List<Item> guardedItems() {
        return List.of(ModItems.SKY_KEY_FRAGMENT.get(), jp.revati.skyabyss.story.StorySetup.SKY_KEY.get(), ModItems.STONE_WHALE_CORE.get(),
                TravelSetup.STONE_WHALE_CALL.get());
    }

    public static boolean guarded(ItemStack stack) {
        return !stack.isEmpty() && guardedItems().contains(stack.getItem());
    }

    public static boolean untossable(ItemStack stack) {
        return stack.is(ModItems.SKY_KEY_FRAGMENT.get()) || stack.is(jp.revati.skyabyss.story.StorySetup.SKY_KEY.get());
    }

    // ---------------------------------------------------------------- 在りか
    public record Ground(String dimension, BlockPos pos, int count, boolean loaded) {
    }

    // ほかの仕組みが持つ「手もとの枠」(例: 装飾品の専用スロット)。id は画面の行 d.place_<id> の名前になる。
    // 枠を作る側が起動時に registerHolder("accessory", (player, item) -> 数) を呼べば、在りかに並び、手もとに数えられる
    @FunctionalInterface
    public interface Holder {
        int count(ServerPlayer player, Item item);
    }

    private static final Map<String, Holder> HOLDERS = new java.util.LinkedHashMap<>();

    public static synchronized void registerHolder(String id, Holder holder) {
        HOLDERS.put(id, holder);
    }

    static synchronized void unregisterHolder(String id) {
        HOLDERS.remove(id);
    }

    // holders: 枠の名前 → 数 (0 の枠は入れない)
    public record Where(int inventory, int bags, int ender, Map<String, Integer> holders, int pending, int kept, List<Ground> ground) {
        public int holderCount() {
            int n = 0;
            for (int c : holders.values()) {
                n += c;
            }
            return n;
        }

        public int groundCount() {
            int n = 0;
            for (Ground g : ground) {
                n += g.count();
            }
            return n;
        }

        public int total() {
            return safe() + pending + kept + groundCount();
        }

        // 手もと (持ち物とその中の袋・箱、エンダーチェスト、ほかの仕組みの枠) にあるか
        public int safe() {
            return inventory + bags + ender + holderCount();
        }
    }

    public static Where locate(ServerPlayer player, Item item) {
        int inventory = 0;
        int bags = 0;
        int ender = 0;
        var inv = player.getInventory();
        for (List<ItemStack> part : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack s : part) {
                if (s.is(item)) {
                    inventory += s.getCount();
                }
                bags += inside(s, item);
            }
        }
        // 画面でつかんでいる品も持ち物に数える
        ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
        if (carried.is(item)) {
            inventory += carried.getCount();
        }
        bags += inside(carried, item);
        var chest = player.getEnderChestInventory();
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (s.is(item)) {
                ender += s.getCount();
            }
            ender += inside(s, item);
        }
        MinecraftServer server = player.server;
        int pending = 0;
        TitanMemoryData memory = TitanMemoryData.get(server);
        if (memory.hasPending(player.getUUID())) {
            for (ItemStack s : memory.pending(player.getUUID()).items) {
                if (s.is(item)) {
                    pending += s.getCount();
                }
            }
        }
        int kept = countKept(player, item);
        Map<String, Integer> holders = new java.util.LinkedHashMap<>();
        List<Map.Entry<String, Holder>> registered;
        synchronized (Keepsakes.class) {
            registered = new ArrayList<>(HOLDERS.entrySet());
        }
        for (Map.Entry<String, Holder> e : registered) {
            int n = e.getValue().count(player, item);
            if (n > 0) {
                holders.put(e.getKey(), n);
            }
        }
        return new Where(inventory, bags, ender, holders, pending, kept, ground(server, player.getUUID(), item));
    }

    // 袋や箱 (シュルカーボックスなど) の中 (一段だけ)
    private static int inside(ItemStack holder, Item item) {
        if (holder.isEmpty()) {
            return 0;
        }
        int n = 0;
        ItemContainerContents box = holder.get(DataComponents.CONTAINER);
        if (box != null) {
            for (ItemStack s : box.nonEmptyItems()) {
                if (s.is(item)) {
                    n += s.getCount();
                }
            }
        }
        BundleContents bundle = holder.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack s : bundle.items()) {
                if (s.is(item)) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static int countKept(ServerPlayer player, Item item) {
        CompoundTag tag = persisted(player);
        int n = 0;
        n += countList(player.registryAccess(), tag.getCompound(KEPT_TAG).getList("Inventory", Tag.TAG_COMPOUND), item);
        n += countList(player.registryAccess(), tag.getCompound(KEPT_EXTRA_TAG).getList("Items", Tag.TAG_COMPOUND), item);
        return n;
    }

    // 背中で倒れたときの預かり物の数 (品の種類を問わない。画面の「背中の預かり」の行)
    public static int keptStacks(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        return tag.getCompound(KEPT_TAG).getList("Inventory", Tag.TAG_COMPOUND).size()
                + tag.getCompound(KEPT_EXTRA_TAG).getList("Items", Tag.TAG_COMPOUND).size();
    }

    private static int countList(HolderLookup.Provider registries, ListTag list, Item item) {
        int n = 0;
        for (Tag t : list) {
            ItemStack s = ItemStack.parse(registries, t).orElse(ItemStack.EMPTY);
            if (s.is(item)) {
                n += s.getCount();
            }
            n += inside(s, item);
        }
        return n;
    }

    private static List<Ground> ground(MinecraftServer server, UUID owner, Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        List<Ground> list = new ArrayList<>();
        for (Map.Entry<UUID, DropLedger.Drop> e : DropLedger.get(server).owned(owner)) {
            DropLedger.Drop d = e.getValue();
            if (!d.item().equals(id)) {
                continue;
            }
            ServerLevel level = server.getLevel(d.dimension());
            Entity live = level == null ? null : level.getEntity(e.getKey());
            if (live instanceof ItemEntity ie && !ie.isRemoved()) {
                list.add(new Ground(d.dimension().location().toString(), ie.blockPosition(), ie.getItem().getCount(), true));
            } else {
                list.add(new Ground(d.dimension().location().toString(), d.pos(), d.count(), false));
            }
        }
        return list;
    }

    // 天の鍵の欠片を受け取ったのに、どこにも見当たらない (心臓をもう一度倒したときに戻すかどうか)
    // 天の鍵にした後は、欠片が手もとになくても「なくした」ではない (鍵になった。14_beta の 4 章)
    public static boolean fragmentMissing(ServerPlayer player) {
        if (jp.revati.skyabyss.story.SkyKey.assembled(player)) {
            return false;
        }
        return locate(player, ModItems.SKY_KEY_FRAGMENT.get()).total() == 0;
    }

    // ---------------------------------------------------------------- 守り
    @Nullable
    static UUID owner(ItemEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.hasUUID(OWNER_TAG)) {
            return data.getUUID(OWNER_TAG);
        }
        if (entity.getOwner() instanceof Player p) {
            return p.getUUID();
        }
        return entity.getTarget();
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level) || !guarded(item.getItem())) {
            return;
        }
        item.setUnlimitedLifetime();
        item.setInvulnerable(true);
        UUID owner = owner(item);
        if (owner != null) {
            item.getPersistentData().putUUID(OWNER_TAG, owner);
            DropLedger.get(level.getServer()).put(item.getUUID(), new DropLedger.Drop(owner,
                    BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(), item.getItem().getCount(),
                    level.dimension(), item.blockPosition()));
        }
    }

    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level) || !guarded(item.getItem())) {
            return;
        }
        DropLedger ledger = DropLedger.get(level.getServer());
        Entity.RemovalReason reason = item.getRemovalReason();
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            // 読み込みが外れただけ: 最後の位置を残す
            UUID owner = owner(item);
            if (owner != null) {
                ledger.put(item.getUUID(), new DropLedger.Drop(owner, BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
                        item.getItem().getCount(), level.dimension(), item.blockPosition()));
            }
        } else {
            // 拾われた・ほかの品とまとまった・ディメンションを移った (移った先でまた控える)
            ledger.remove(item.getUUID());
        }
    }

    // 奈落へ落ちた品は、消える前に持ち主へ戻す
    @SubscribeEvent
    static void onItemTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(item.level() instanceof ServerLevel level) || item.isRemoved()) {
            return;
        }
        if (item.getY() >= level.getMinBuildHeight() - VOID_MARGIN || !guarded(item.getItem())) {
            return;
        }
        rescue(level, item);
    }

    static void rescue(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem().copy();
        UUID owner = owner(item);
        item.discard();
        MinecraftServer server = level.getServer();
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (player != null && player.isAlive() && !player.hasDisconnected()) {
            giveOrDrop(player, stack);
            player.sendSystemMessage(ModText.progress("skyabyss_record.guard.rescued", stack.getHoverName()));
        } else if (owner != null) {
            // 倒れている・いない人の分は報酬の預かりに入れる (目を覚ましたとき・入ったときに渡る)
            TitanMemoryData.get(server).pending(owner).items.add(stack);
            if (player != null) {
                player.sendSystemMessage(ModText.hint("skyabyss_record.guard.rescued_pending", stack.getHoverName()));
            }
        } else {
            // 持ち主の分からない品は、オーバーワールドの初めの場所へ
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            ItemEntity moved = new ItemEntity(overworld, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, stack);
            overworld.addFreshEntity(moved);
        }
        SkyAbyss.LOGGER.info("[record] rescued {} x{} from the void (owner {})", BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), owner);
    }

    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack left = stack.copy();
        if (!player.getInventory().add(left) && !left.isEmpty()) {
            // 入りきらない分は足もとへ (落ちた品も守られる。投げた人として持ち主が付く)
            player.drop(left, false, true);
        }
    }

    // 天の鍵の欠片は投げ捨てられない (クリエイティブは別)
    @SubscribeEvent
    static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (!untossable(stack) || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        // 自分で投げた (Q キー・画面の外へ) ときだけ、投げた人が付く。/give の見せかけの品や、
        // 入りきらずに足もとへ出る品には付かないので、拒まない (拒んで戻すと品が増える・止めると品が消える)
        if (event.getEntity().getOwner() != player) {
            return;
        }
        event.setCanceled(true);
        // 投げる前に持ち物から外されているので、戻す
        giveOrDrop(player, stack.copy());
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(ModText.deny("skyabyss_record.guard.no_toss"));
    }

    // 倒れて散らばった品には投げた人が付かないので、持ち主を書いておく
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (ItemEntity item : event.getDrops()) {
            if (guarded(item.getItem())) {
                item.getPersistentData().putUUID(OWNER_TAG, player.getUUID());
                item.setThrower(player);
            }
        }
    }
}

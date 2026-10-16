package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.SkyKey;
import jp.revati.skyabyss.story.StorySetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 天の鍵の欠片と天の鍵を「持ち主に結ぶ」守りと、なくしたときの戻り方 (11 の 4.2 節)。
// 投げ捨て不可・燃えない・奈落から戻る (Keepsakes) で防げない失い方を、一人一つを壊さずに戻す。
//
//  結ぶ   : 持ち物・エンダーチェストにある品に、持ち主と「代」(gen) を書く (1 秒ごと。心臓・コマンドで渡った品も)
//  帰る   : ほかの人の持ち物に入った品は、持ち主のもとへ帰る (いなければ報酬の預かりへ)
//  箱     : 箱を閉じたとき、中の品を数えて在りかを控える (控え帳に「箱の中 (x, y, z)」)。箱にしまって忘れても、なくしたことにならない
//  戻り方 : なくした (受け取った数より、在りかの分かる品が少ない) とき
//           1. その巨神の心臓をもう一度倒す (撃破の記録が増えたのを見て、鼓動のそばで再び見つかる)
//           2. 控え帳の「呼び戻す」
//           どちらも代を一つ進め、手もとにある品は新しい代に書き直し、足りない分だけ渡す。前の代の品は、
//           見つかった所 (持ち物・箱を開けたとき・地面に出たとき) で砂になって崩れる。だから同時に二つにはならない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class KeepsakeBonds {
    static final String TAG = "skyabyss_bond";

    public enum Kind {
        FRAGMENT("fragment"), KEY("key");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        public Item item() {
            return this == FRAGMENT ? ModItems.SKY_KEY_FRAGMENT.get() : StorySetup.SKY_KEY.get();
        }

        String itemId() {
            return BuiltInRegistries.ITEM.getKey(item()).toString();
        }

        @Nullable
        static Kind of(ItemStack stack) {
            if (stack.isEmpty()) {
                return null;
            }
            if (stack.is(ModItems.SKY_KEY_FRAGMENT.get())) {
                return FRAGMENT;
            }
            return stack.is(StorySetup.SKY_KEY.get()) ? KEY : null;
        }
    }

    private KeepsakeBonds() {
    }

    // ---------------------------------------------------------------- 品の印
    @Nullable
    public static UUID owner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag().getCompound(TAG);
        return tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    static int gen(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? -1 : data.copyTag().getCompound(TAG).getInt("gen");
    }

    static void bind(ItemStack stack, UUID owner, int gen) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag bond = new CompoundTag();
            bond.putUUID("owner", owner);
            bond.putInt("gen", gen);
            tag.put(TAG, bond);
        });
    }

    // 前の代の品か (持ち主が呼び戻した・心臓で取り戻した)
    public static boolean stale(MinecraftServer server, ItemStack stack) {
        Kind kind = Kind.of(stack);
        UUID owner = kind == null ? null : owner(stack);
        return owner != null && gen(stack) < BondData.get(server).gen(owner, kind.id);
    }

    // その人の品として数えてよいか (まだ結ばれていない品と、その人の今の代の品)
    static Predicate<ItemStack> mine(ServerPlayer player) {
        return s -> {
            UUID owner = owner(s);
            return owner == null || (owner.equals(player.getUUID()) && !stale(player.server, s));
        };
    }

    // ---------------------------------------------------------------- 数
    // 受け取った数: 欠片は、巨神の記憶で「欠片を受け取った」巨神の数 (鍵にしたあとは 0)。鍵は、そろえたら 1
    public static int expected(ServerPlayer player, Kind kind) {
        boolean assembled = SkyKey.assembled(player);
        if (kind == Kind.KEY) {
            return assembled ? 1 : 0;
        }
        if (assembled) {
            return 0;
        }
        int n = 0;
        TitanMemoryData memory = TitanMemoryData.get(player.server);
        for (String titan : Titans.all()) {
            TitanMemoryData.Record r = memory.peek(player.getUUID(), titan);
            if (r != null && r.fragment) {
                n++;
            }
        }
        return n;
    }

    public static Keepsakes.Where where(ServerPlayer player, Kind kind) {
        return Keepsakes.locate(player, kind.item(), mine(player));
    }

    // 在りかの分かる数
    public static int held(ServerPlayer player, Kind kind) {
        return where(player, kind).total();
    }

    // なくした数 (受け取った数より、在りかの分かる品が少ない分)
    public static int missing(ServerPlayer player, Kind kind) {
        return Math.max(0, expected(player, kind) - held(player, kind));
    }

    // 箱にしまった品 (Keepsakes.locate から)。読み込まれている箱は中身を数え直す。箱がなくなっていれば控えを外す
    static List<Keepsakes.Ground> stashed(ServerPlayer player, Item item, Predicate<ItemStack> is) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        BondData data = BondData.get(player.server);
        List<Keepsakes.Ground> out = new ArrayList<>();
        for (BondData.Stash s : data.stash(player.getUUID())) {
            if (!s.item().equals(id)) {
                continue;
            }
            ServerLevel level = player.server.getLevel(s.dimension());
            if (level != null && level.isLoaded(s.pos())) {
                BlockEntity be = level.getBlockEntity(s.pos());
                int n = 0;
                if (be instanceof Container c) {
                    for (int i = 0; i < c.getContainerSize(); i++) {
                        ItemStack st = c.getItem(i);
                        if (is.test(st) && player.getUUID().equals(owner(st))) {
                            n += st.getCount();
                        }
                    }
                }
                if (n != s.count()) {
                    data.setStash(player.getUUID(), s.dimension(), s.pos(), id, n);
                }
                if (n > 0) {
                    out.add(new Keepsakes.Ground(s.dimension().location().toString(), s.pos(), n, true));
                }
            } else {
                out.add(new Keepsakes.Ground(s.dimension().location().toString(), s.pos(), s.count(), false));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 見回り (1 秒ごと)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || !player.isAlive()) {
            return;
        }
        if (player.tickCount % 20 == 3) {
            sweep(player);
        }
        if (player.tickCount % 40 == 17) {
            checkHearts(player);
        }
    }

    // 持ち物・エンダーチェスト・開いている箱を見て、結ぶ・崩す・持ち主へ帰す
    static void sweep(ServerPlayer player) {
        Inventory inv = player.getInventory();
        boolean changed = sweepContainer(player, inv, true);
        changed |= sweepContainer(player, player.getEnderChestInventory(), true);
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != null && menu != player.inventoryMenu) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && Kind.of(carried) != null && handle(player, carried, true)) {
                menu.setCarried(ItemStack.EMPTY);
                changed = true;
            }
            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory || slot.container == player.getEnderChestInventory()) {
                    continue;
                }
                ItemStack s = slot.getItem();
                if (Kind.of(s) != null && stale(player.server, s)) {
                    crumble(player, s);
                    slot.set(ItemStack.EMPTY);
                    changed = true;
                }
            }
        }
        if (changed) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static boolean sweepContainer(ServerPlayer player, Container container, boolean own) {
        boolean changed = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (Kind.of(s) != null && handle(player, s, own)) {
                container.setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
    }

    // 一つの品を見る。持ち物から外すべきなら true
    private static boolean handle(ServerPlayer player, ItemStack stack, boolean own) {
        Kind kind = Kind.of(stack);
        UUID owner = owner(stack);
        if (owner == null) {
            if (own) {
                bind(stack, player.getUUID(), BondData.get(player.server).gen(player.getUUID(), kind.id));
            }
            return false;
        }
        if (stale(player.server, stack)) {
            crumble(player, stack);
            return true;
        }
        if (!owner.equals(player.getUUID()) && own && !player.isCreative()) {
            sendHome(player, owner, stack.copy());
            return true;
        }
        return false;
    }

    // 前の代の品は砂になる
    private static void crumble(ServerPlayer player, ItemStack stack) {
        player.sendSystemMessage(ModText.lore("skyabyss_record.bond.crumbled", stack.getHoverName()));
        player.serverLevel().sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.5, 0.4, 0.02);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
        SkyAbyss.LOGGER.info("[record] a stale {} (owner {}) crumbled near {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), owner(stack),
                player.getGameProfile().getName());
    }

    // ほかの人の品は持ち主へ帰る
    private static void sendHome(ServerPlayer holder, UUID ownerId, ItemStack stack) {
        MinecraftServer server = holder.server;
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        holder.sendSystemMessage(ModText.hint("skyabyss_record.bond.went_home", stack.getHoverName(),
                owner != null ? owner.getDisplayName() : net.minecraft.network.chat.Component.literal(nameOf(server, ownerId))));
        if (owner != null && owner.isAlive() && !owner.hasDisconnected()) {
            Keepsakes.giveOrDrop(owner, stack);
            owner.sendSystemMessage(ModText.lore("skyabyss_record.bond.came_home", stack.getHoverName(), holder.getDisplayName()));
        } else {
            TitanMemoryData.get(server).pending(ownerId).items.add(stack);
        }
        SkyAbyss.LOGGER.info("[record] {} held {}'s {}; sent it home", holder.getGameProfile().getName(), ownerId, stack.getHoverName().getString());
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        if (server.getProfileCache() != null) {
            var profile = server.getProfileCache().get(id);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return "?";
    }

    // ---------------------------------------------------------------- 箱
    @SubscribeEvent
    static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            noteStash(player, event.getContainer());
        }
    }

    @SubscribeEvent
    static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            noteStash(player, event.getContainer());
        }
    }

    // 開いた・閉じた箱 (ブロックの箱だけ。二つ並んだチェストは片方ずつ) の中を数え、在りかを控え直す。前の代の品は崩す
    static void noteStash(ServerPlayer player, AbstractContainerMenu menu) {
        Map<BlockEntity, List<Slot>> boxes = new HashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof BlockEntity be && !(slot.container instanceof Inventory)) {
                boxes.computeIfAbsent(be, k -> new ArrayList<>()).add(slot);
            }
        }
        BondData data = BondData.get(player.server);
        for (Map.Entry<BlockEntity, List<Slot>> e : boxes.entrySet()) {
            BlockEntity be = e.getKey();
            if (be.getLevel() == null) {
                continue;
            }
            var dim = be.getLevel().dimension();
            BlockPos pos = be.getBlockPos();
            for (Kind kind : Kind.values()) {
                Map<UUID, Integer> counts = new HashMap<>();
                for (Slot slot : e.getValue()) {
                    ItemStack s = slot.getItem();
                    if (Kind.of(s) != kind) {
                        continue;
                    }
                    if (stale(player.server, s)) {
                        crumble(player, s);
                        slot.set(ItemStack.EMPTY);
                        continue;
                    }
                    UUID owner = owner(s);
                    if (owner == null) {
                        // 結ばれる前に箱へ入った品は、箱を開けた人ではなく、だれのものでもない (次に持った人に結ばれる)
                        continue;
                    }
                    counts.merge(owner, s.getCount(), Integer::sum);
                }
                data.clearStashAt(dim, pos, kind.itemId());
                counts.forEach((owner, n) -> data.setStash(owner, dim, pos, kind.itemId(), n));
            }
        }
    }

    // 地面に出た前の代の品は、その場で消える (控えにも載らない)
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onItemJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && event.getLevel() instanceof ServerLevel level && Kind.of(item.getItem()) != null
                && stale(level.getServer(), item.getItem())) {
            event.setCanceled(true);
            SkyAbyss.LOGGER.info("[record] a stale {} on the ground crumbled at {}", item.getItem().getHoverName().getString(), item.blockPosition());
        }
    }

    // ---------------------------------------------------------------- 戻り方
    // 1. 心臓をもう一度倒した (巨神の記憶の撃破数が増えた)。その巨神の欠片を受け取っていて、欠片をなくしていれば戻す
    static void checkHearts(ServerPlayer player) {
        TitanMemoryData memory = TitanMemoryData.get(player.server);
        BondData data = BondData.get(player.server);
        for (String titan : Titans.all()) {
            TitanMemoryData.Record r = memory.peek(player.getUUID(), titan);
            if (r == null) {
                continue;
            }
            int last = data.lastClears(player.getUUID(), titan);
            data.setLastClears(player.getUUID(), titan, r.clears);
            if (last >= 0 && r.clears > last && r.fragment && missing(player, Kind.FRAGMENT) > 0) {
                int given = reissue(player, Kind.FRAGMENT);
                player.sendSystemMessage(ModText.lore("skyabyss_record.bond.heart_restored", given));
            }
        }
    }

    public enum RecallResult { RECALLED, NOTHING_MISSING }

    // 2. 控え帳から呼び戻す
    public static RecallResult recall(ServerPlayer player, Kind kind) {
        if (missing(player, kind) <= 0) {
            player.sendSystemMessage(ModText.hint("skyabyss_record.bond.nothing_missing_" + kind.id));
            return RecallResult.NOTHING_MISSING;
        }
        int given = reissue(player, kind);
        player.sendSystemMessage(ModText.lore("skyabyss_record.bond.recalled_" + kind.id, given));
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 4, player.getZ(), 24, 0.3, 2.0, 0.3, 0.0);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.8f);
        return RecallResult.RECALLED;
    }

    // 代を進め、手もとの品を新しい代に書き直し、足りない分を渡す。渡した数を返す
    static int reissue(ServerPlayer player, Kind kind) {
        MinecraftServer server = player.server;
        UUID me = player.getUUID();
        Predicate<ItemStack> mine = mine(player);
        BondData data = BondData.get(server);
        // 書き直す前に、今の代で手もとにある品を拾っておく (代を進めると前の代になるため)
        List<ItemStack> reachable = new ArrayList<>();
        collect(player.getInventory(), kind, mine, reachable);
        collect(player.getEnderChestInventory(), kind, mine, reachable);
        TitanMemoryData memory = TitanMemoryData.get(server);
        if (memory.hasPending(me)) {
            for (ItemStack s : memory.pending(me).items) {
                if (Kind.of(s) == kind && mine.test(s)) {
                    reachable.add(s);
                }
            }
        }
        int gen = data.nextGen(me, kind.id);
        int kept = 0;
        for (ItemStack s : reachable) {
            bind(s, me, gen);
            kept += s.getCount();
        }
        // 地面に落ちている (読み込まれている) 前の代の品は、いま消す。読み込まれていない品は、次に読み込まれたときに消える
        DropLedger ledger = DropLedger.get(server);
        String itemId = kind.itemId();
        for (Map.Entry<UUID, DropLedger.Drop> e : ledger.owned(me)) {
            if (!e.getValue().item().equals(itemId)) {
                continue;
            }
            ServerLevel level = server.getLevel(e.getValue().dimension());
            Entity live = level == null ? null : level.getEntity(e.getKey());
            if (live instanceof ItemEntity ie && stale(server, ie.getItem())) {
                ie.discard();
            }
        }
        data.clearStash(me, itemId);
        int give = Math.max(0, expected(player, kind) - kept);
        for (int i = 0; i < give; i++) {
            ItemStack stack = new ItemStack(kind.item());
            bind(stack, me, gen);
            Keepsakes.giveOrDrop(player, stack);
        }
        player.containerMenu.broadcastChanges();
        SkyAbyss.LOGGER.info("[record] {} reissued {} {} (gen {}, kept {})", player.getGameProfile().getName(), give, kind.id, gen, kept);
        return give;
    }

    private static void collect(Container container, Kind kind, Predicate<ItemStack> mine, List<ItemStack> out) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (Kind.of(s) == kind && mine.test(s)) {
                out.add(s);
            }
        }
    }
}

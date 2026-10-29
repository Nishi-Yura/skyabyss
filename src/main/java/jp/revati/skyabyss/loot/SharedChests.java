package jp.revati.skyabyss.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 世界のチェストと樽は、人ごとに中身を受け取る (2026-10-29 の試遊「チェストや樽の中にアイテム入れるのやめて。色んな人いるのに一人しか取れない。
// 右クリックでアイテム取れて、それぞれ個人でチェストに対してのクールダウンがある様に。大体一つ 20 分くらい」)。
//   初めて右クリックされたときに中身があれば、その中身を見本として覚えて箱は空にする (以後は開かない)。右クリックした人に見本の写しを渡し、
//   その人はその箱から WAIT の間は受け取れない (人ごと・箱ごと)。中身のなかった箱はふつうの箱のまま (形見の隠し場所などに使える)。
//   見本のある箱にまた中身が入ったら (深淵の階の作り直しなど)、その中身を新しい見本にして、待ちを消す。
//   創造・観戦の人はふつうに開く (作り手が中身を直せるように)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SharedChests extends SavedData {
    private static final String NAME = "skyabyss_shared_chests";
    // 待ち: 20 分 (ゲームの時刻で 24000 tick)
    public static final long WAIT = 24000L;

    // 箱の鍵 (ディメンション + 位置) → 見本の中身
    private final Map<String, List<ItemStack>> templates = new HashMap<>();
    // 箱の鍵 → 人 → 最後に受け取った時刻
    private final Map<String, Map<UUID, Long>> taken = new HashMap<>();
    // 初めて開いたとき空だった箱 (ふつうの箱のまま。人が入れた品を見本にしない)
    private final java.util.Set<String> plain = new java.util.HashSet<>();

    public static SharedChests get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SharedChests::new, SharedChests::load), NAME);
    }

    // 二つ並びのチェストは片方 (北か西の方) の位置にそろえる
    static BlockPos main(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            return other.asLong() < pos.asLong() ? other : pos;
        }
        return pos;
    }

    static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isCreative() || p.isSpectator()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock)) {
            return;
        }
        // しゃがんで手に品を持っているとき (箱の横に置く) はふつうの扱い
        if (p.isSecondaryUseActive() && !p.getMainHandItem().isEmpty()) {
            return;
        }
        Container box = container(level, state, event.getPos());
        if (box == null) {
            return;
        }
        if (open(level, p, state, event.getPos(), box)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    static Container container(ServerLevel level, BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }

    // 人が箱を右クリックした。人ごとの受け取りで済ませたら true (箱は開かない)。ふつうの箱なら false
    public static boolean open(ServerLevel level, ServerPlayer p, BlockState state, BlockPos pos, Container box) {
        // 置き場の決まりの中身 (バニラの戦利品表) は、ここで中身にしてから見本にする
        BlockPos at = main(state, pos);
        for (BlockPos q : List.of(pos, at)) {
            if (level.getBlockEntity(q) instanceof RandomizableContainerBlockEntity r && r.getLootTable() != null) {
                r.unpackLootTable(p);
            }
        }
        SharedChests data = get(level.getServer());
        String k = key(level, at);
        List<ItemStack> inside = new ArrayList<>();
        for (int i = 0; i < box.getContainerSize(); i++) {
            if (!box.getItem(i).isEmpty()) {
                inside.add(box.getItem(i).copy());
            }
        }
        if (!data.templates.containsKey(k) && (inside.isEmpty() || data.plain.contains(k))) {
            if (data.plain.add(k)) {
                data.setDirty();
            }
            return false;
        }
        if (!inside.isEmpty()) {
            // 新しい中身 (初めて・入れ直された): 見本にして箱は空に、待ちは消す
            data.templates.put(k, inside);
            data.taken.remove(k);
            box.clearContent();
            box.setChanged();
            data.setDirty();
        }
        List<ItemStack> template = data.templates.get(k);
        if (template == null) {
            return false;
        }
        long now = level.getServer().overworld().getGameTime();
        Long last = data.taken.getOrDefault(k, Map.of()).get(p.getUUID());
        if (last != null && now - last < WAIT) {
            long minutes = Math.max(1, (WAIT - (now - last) + 1199) / 1200);
            Notice.hint(p, "message.skyabyss.chest.wait", minutes);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.5f, 1.2f);
            return true;
        }
        data.taken.computeIfAbsent(k, x -> new HashMap<>()).put(p.getUUID(), now);
        data.setDirty();
        MutableComponent names = Component.empty();
        boolean first = true;
        for (ItemStack s : template) {
            ItemStack give = s.copy();
            if (!first) {
                names.append(Component.literal("・"));
            }
            first = false;
            names.append(give.getHoverName().copy());
            if (give.getCount() > 1) {
                names.append(Component.literal(" ×" + give.getCount()));
            }
            jp.revati.skyabyss.quest.Quests.give(p, give);
        }
        Notice.hint(p, "message.skyabyss.chest.took", names);
        level.playSound(null, pos, state.getBlock() instanceof BarrelBlock ? SoundEvents.BARREL_OPEN : SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 1f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    // 自己テスト向け
    public boolean hasTemplate(ServerLevel level, BlockPos pos) {
        return templates.containsKey(key(level, pos));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, List<ItemStack>> e : templates.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            ListTag items = new ListTag();
            for (ItemStack s : e.getValue()) {
                items.add(s.save(registries));
            }
            c.put("items", items);
            CompoundTag who = new CompoundTag();
            for (Map.Entry<UUID, Long> t : taken.getOrDefault(e.getKey(), Map.of()).entrySet()) {
                who.putLong(t.getKey().toString(), t.getValue());
            }
            c.put("taken", who);
            list.add(c);
        }
        tag.put("chests", list);
        ListTag pl = new ListTag();
        for (String k : plain) {
            pl.add(net.minecraft.nbt.StringTag.valueOf(k));
        }
        tag.put("plain", pl);
        return tag;
    }

    private static SharedChests load(CompoundTag tag, HolderLookup.Provider registries) {
        SharedChests d = new SharedChests();
        for (Tag t : tag.getList("chests", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            String k = c.getString("k");
            List<ItemStack> items = new ArrayList<>();
            for (Tag it : c.getList("items", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, it).ifPresent(items::add);
            }
            d.templates.put(k, items);
            CompoundTag who = c.getCompound("taken");
            Map<UUID, Long> m = new HashMap<>();
            for (String u : who.getAllKeys()) {
                m.put(UUID.fromString(u), who.getLong(u));
            }
            if (!m.isEmpty()) {
                d.taken.put(k, m);
            }
        }
        for (Tag t : tag.getList("plain", Tag.TAG_STRING)) {
            d.plain.add(t.getAsString());
        }
        return d;
    }

    // 自己テスト向け: 待ちを今から外す
    public void forget(ServerLevel level, BlockPos pos, UUID who) {
        Map<UUID, Long> m = taken.get(key(level, pos));
        if (m != null) {
            m.remove(who);
        }
    }
}

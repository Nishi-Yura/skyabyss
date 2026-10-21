package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.record.KeepsakeBonds;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// 四巨神の社の鍵の間に、天の鍵の欠片を納める (docs/design/35 の 3.2、33 の 2.2)。
//   納める: 心臓を止めた巨神の石 (CapitalShrine) に触れて一つずつ、または社の祭司の「欠片を納める」でまとめて。巨神ごとに一つまで
//   納めた欠片は、その人の「手もと」に数える (控え帳の在りか「鍵の間」。Keepsakes の枠 key_room)。だから天の鍵がそろう決まり
//   (SkyKey: 四つの心臓と四つの欠片) はそのまま効き、そろうときは鍵の間から取られる
//   返してもらう: 祭司の「納めた欠片を返してもらう」で、持ち主に結んで持ち物へ
@EventBusSubscriber(modid = SkyAbyss.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class KeyRoom {
    private KeyRoom() {
    }

    public static final String HOLDER = "key_room";
    // 納めるときは、鍵の間の枠から取らない (自分の枠から自分へ数え直さないため)
    private static final ThreadLocal<Boolean> OFFERING = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> Keepsakes.registerHolder(HOLDER, new Keepsakes.Holder() {
            @Override
            public int count(ServerPlayer player, Item item) {
                return item == ModItems.SKY_KEY_FRAGMENT.get() ? offered(player).size() : 0;
            }

            @Override
            public int take(ServerPlayer player, Item item, int n) {
                if (OFFERING.get() || item != ModItems.SKY_KEY_FRAGMENT.get()) {
                    return 0;
                }
                return takeOffered(player, n);
            }
        }));
    }

    // ---------------------------------------------------------------- 記録
    public static final class Data extends SavedData {
        private static final String NAME = "skyabyss_key_room";
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        final Map<UUID, Set<String>> offered = new HashMap<>();

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            CompoundTag all = tag.getCompound("offered");
            for (String k : all.getAllKeys()) {
                Set<String> set = new LinkedHashSet<>();
                for (Tag t : all.getList(k, Tag.TAG_STRING)) {
                    set.add(t.getAsString());
                }
                try {
                    d.offered.put(UUID.fromString(k), set);
                } catch (IllegalArgumentException ignored) {
                    // 壊れた鍵は捨てる
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag all = new CompoundTag();
            offered.forEach((u, set) -> {
                ListTag list = new ListTag();
                set.forEach(s -> list.add(StringTag.valueOf(s)));
                all.put(u.toString(), list);
            });
            tag.put("offered", all);
            return tag;
        }
    }

    public static Set<String> offered(ServerPlayer player) {
        return Data.get(player.server).offered.getOrDefault(player.getUUID(), Set.of());
    }

    static int takeOffered(ServerPlayer player, int n) {
        Data d = Data.get(player.server);
        Set<String> set = d.offered.get(player.getUUID());
        if (set == null || n <= 0) {
            return 0;
        }
        int taken = 0;
        var it = set.iterator();
        while (it.hasNext() && taken < n) {
            it.next();
            it.remove();
            taken++;
        }
        d.setDirty();
        return taken;
    }

    // 手もと (鍵の間を除く) の欠片の数
    public static int carried(ServerPlayer player) {
        return KeepsakeBonds.where(player, KeepsakeBonds.Kind.FRAGMENT).safe() - offered(player).size();
    }

    // 巨神の石に一つ納める。納めたら true (心臓を止めていない・もう納めた・欠片がないときは false で、知らせも出す)
    public static boolean offer(ServerPlayer player, String titan, boolean tell) {
        if (!Progression.stopped(player.server, player.getUUID(), titan)) {
            return false;
        }
        Data d = Data.get(player.server);
        Set<String> set = d.offered.computeIfAbsent(player.getUUID(), k -> new LinkedHashSet<>());
        if (set.contains(titan)) {
            if (tell) {
                Notice.hint(player, "message.skyabyss.keyroom.already", Progression.titanName(titan));
            }
            return false;
        }
        int took;
        OFFERING.set(true);
        try {
            took = Keepsakes.take(player, ModItems.SKY_KEY_FRAGMENT.get(), KeepsakeBonds.mine(player), 1);
        } finally {
            OFFERING.set(false);
        }
        if (took <= 0) {
            if (tell) {
                Notice.hint(player, "message.skyabyss.keyroom.no_fragment");
            }
            return false;
        }
        set.add(titan);
        d.setDirty();
        player.containerMenu.broadcastChanges();
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.7f + 0.1f * set.size());
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
        if (tell) {
            Notice.narrate(player, set.size() >= Titans.all().size() ? "message.skyabyss.keyroom.all" : "message.skyabyss.keyroom.offered",
                    Progression.titanName(titan), set.size());
        }
        SkyAbyss.LOGGER.info("[facility] {} laid a sky key fragment for {} ({} in the key room)", player.getGameProfile().getName(), titan, set.size());
        return true;
    }

    // 祭司から: 止めた心臓の巨神ごとに、まだ納めていなければ一つずつ。納めた数
    public static int offerAll(ServerPlayer player) {
        int n = 0;
        for (String t : Titans.all()) {
            if (Progression.stopped(player.server, player.getUUID(), t) && !offered(player).contains(t) && offer(player, t, false)) {
                n++;
            }
        }
        if (n > 0) {
            Set<String> set = offered(player);
            Notice.narrate(player, set.size() >= Titans.all().size() ? "message.skyabyss.keyroom.all_at_once" : "message.skyabyss.keyroom.offered_some",
                    n, set.size());
        } else {
            Notice.hint(player, carried(player) <= 0 ? "message.skyabyss.keyroom.no_fragment" : "message.skyabyss.keyroom.nothing_to_offer");
        }
        return n;
    }

    // 納めた欠片を返してもらう。返した数
    public static int giveBack(ServerPlayer player) {
        List<String> all = new ArrayList<>(offered(player));
        if (all.isEmpty()) {
            Notice.hint(player, "message.skyabyss.keyroom.empty");
            return 0;
        }
        int n = takeOffered(player, all.size());
        ItemStack stack = new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), n);
        KeepsakeBonds.bindFor(player, stack);
        Quests.give(player, stack);
        Notice.hint(player, "message.skyabyss.keyroom.returned", n);
        return n;
    }
}

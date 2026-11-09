package jp.revati.skyabyss.rpg.aspect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// ルートを越えたときオフラインだった仲間のアスペクト (docs/design/58 の 4 章)。次に入ったとき、そのときの職で引いて報いの祭壇を開く。
//   一人あたり「巨神|崩れなし」の並び。入った人の分を順に渡して空にする (多すぎる並びは古いほうから捨てる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AspectPending extends SavedData {
    private static final String NAME = "skyabyss_aspect_pending";
    private static final Factory<AspectPending> FACTORY = new Factory<>(AspectPending::new, AspectPending::load);
    static final int MAX_PER_PLAYER = 32;

    private final Map<UUID, List<String>> waiting = new HashMap<>();

    public static AspectPending get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static void add(MinecraftServer server, UUID id, String titan, boolean noWipe) {
        AspectPending d = get(server);
        List<String> list = d.waiting.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(titan + "|" + (noWipe ? 1 : 0));
        while (list.size() > MAX_PER_PLAYER) {
            list.remove(0);
        }
        d.setDirty();
    }

    public static int count(MinecraftServer server, UUID id) {
        List<String> list = get(server).waiting.get(id);
        return list == null ? 0 : list.size();
    }

    // 入った人の分を渡す。渡した数
    public static int deliver(ServerPlayer p) {
        AspectPending d = get(p.getServer());
        List<String> list = d.waiting.remove(p.getUUID());
        if (list == null) {
            return 0;
        }
        d.setDirty();
        for (String s : list) {
            int bar = s.indexOf('|');
            AspectNet.grant(p, s.substring(0, bar), s.endsWith("1"));
        }
        return list.size();
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            deliver(p);
        }
    }

    private static AspectPending load(CompoundTag tag, HolderLookup.Provider registries) {
        AspectPending d = new AspectPending();
        CompoundTag all = tag.getCompound("waiting");
        for (String key : all.getAllKeys()) {
            List<String> list = new ArrayList<>();
            for (Tag t : all.getList(key, Tag.TAG_STRING)) {
                list.add(t.getAsString());
            }
            try {
                d.waiting.put(UUID.fromString(key), list);
            } catch (IllegalArgumentException ignored) {
                // 壊れた鍵は捨てる
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, List<String>> e : waiting.entrySet()) {
            ListTag list = new ListTag();
            for (String s : e.getValue()) {
                list.add(StringTag.valueOf(s));
            }
            all.put(e.getKey().toString(), list);
        }
        tag.put("waiting", all);
        return tag;
    }
}

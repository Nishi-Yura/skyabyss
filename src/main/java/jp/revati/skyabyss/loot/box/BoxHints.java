package jp.revati.skyabyss.loot.box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 封の箱の手がかり (docs/design/52 の 1 章。2026-11-05 の封の箱の頁 1-1)。箱の種類ごとの封の列の先頭 (次に開けて出る品) の種類を、箱のツールチップに一行。
//   今の職の武器 / ほかの職の武器 / 防具 / 装飾 / まだ決まっていない (列が空。開けたときに箱の表から引く)
// 品の名前までは言わない (ルーレットの楽しみは残す)。列はサーバーにしかないので、変わったら (1 秒ごとに見て) その人へ送る。手元は CLIENT に持つ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BoxHints {
    public static final int NONE = 0;
    public static final int JOB_WEAPON = 1;
    public static final int WEAPON = 2;
    public static final int ARMOR = 3;
    public static final int TRINKET = 4;
    static final String[] KEYS = {"none", "job_weapon", "weapon", "armor", "trinket"};

    // 手元の写し (箱の ID → 手がかり)。サーバーの側では空のまま
    public static final Map<String, Integer> CLIENT = new HashMap<>();
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private BoxHints() {
    }

    // 一つの品の手がかり
    public static int of(ItemStack gear, Job job) {
        if (gear.isEmpty()) {
            return NONE;
        }
        if (gear.getItem() instanceof ArmorItem || gear.getItem() instanceof ShieldItem) {
            return ARMOR;
        }
        if (gear.getItem() instanceof jp.revati.skyabyss.item.accessory.Accessory) {
            return TRINKET;
        }
        Job of = Job.of(gear);
        if (of == null) {
            return TRINKET;
        }
        return job != null && of == job ? JOB_WEAPON : WEAPON;
    }

    // その人の、箱の種類ごとの手がかり (列が空なら NONE)
    public static Map<String, Integer> compute(ServerPlayer p) {
        LootBoxData data = LootBoxData.get(p.server);
        Job job = LootBoxes.currentJob(p);
        Map<String, Integer> out = new HashMap<>();
        for (BoxKind k : BoxKind.ALL) {
            var q = data.queue(p.getUUID(), k.id());
            out.put(k.id(), q.isEmpty() ? NONE : of(q.peekFirst(), job));
        }
        return out;
    }

    public static void send(ServerPlayer p) {
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null) {
            return;
        }
        Map<String, Integer> m = compute(p);
        List<String> ids = new ArrayList<>(m.keySet());
        List<Integer> codes = new ArrayList<>();
        for (String id : ids) {
            codes.add(m.get(id));
        }
        SENT.put(p.getUUID(), m.hashCode());
        PacketDistributor.sendToPlayer(p, new Hints(ids, codes));
    }

    // 1 秒ごとに見て、変わっていれば送る (箱が落ちた・開けた・職を替えた)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 11) {
            return;
        }
        Integer last = SENT.get(p.getUUID());
        if (last == null || last != compute(p).hashCode()) {
            send(p);
        }
    }

    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    // ツールチップの一行 (手元)
    public static Component line(String boxId) {
        int c = CLIENT.getOrDefault(boxId, NONE);
        return Component.translatable("item.skyabyss.box.hint." + KEYS[Math.max(0, Math.min(KEYS.length - 1, c))]);
    }

    public record Hints(List<String> ids, List<Integer> codes) implements CustomPacketPayload {
        public static final Type<Hints> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "box_hints"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hints> STREAM_CODEC = StreamCodec.of((buf, h) -> {
            buf.writeVarInt(h.ids.size());
            for (int i = 0; i < h.ids.size(); i++) {
                buf.writeUtf(h.ids.get(i));
                buf.writeVarInt(h.codes.get(i));
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<String> ids = new ArrayList<>();
            List<Integer> codes = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ids.add(buf.readUtf());
                codes.add(buf.readVarInt());
            }
            return new Hints(ids, codes);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("box-hints-1").playToClient(Hints.TYPE, Hints.STREAM_CODEC, (h, c) -> c.enqueueWork(() -> {
            CLIENT.clear();
            for (int i = 0; i < h.ids().size(); i++) {
                CLIENT.put(h.ids().get(i), h.codes().get(i));
            }
        }));
    }
}

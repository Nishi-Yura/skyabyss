package jp.revati.skyabyss.rpg.aspect;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// アスペクトが出る (巨神の祠のルートを越えたとき。trial/raid/RaidRewards.grantRoute から) と、報いの祭壇の画面へ送る (docs/design/58)。
//   一回のルートで 1 つ、崩れなしならもう 1 つ (頁の選定: 推奨 A)。品は今の職のもの。レジェンドが 8 回出なければ次は必ずレジェンド
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AspectNet {
    private static final String PROTOCOL_VERSION = "aspect-1";

    private AspectNet() {
    }

    // 出た一つ (品の id、前の数、後の数)
    public record Got(String id, int before, int after) {
    }

    // サーバー → 持ち主: 報いの祭壇を開く (出たものを並べる。titan はルートの巨神、extras = 心臓のあとに一緒に手に入れた箱・鐘銭・素材)
    public record Reveal(String titan, List<Got> got, List<net.minecraft.world.item.ItemStack> extras) implements CustomPacketPayload {
        public Reveal(String titan, List<Got> got) {
            this(titan, got, List.of());
        }

        public static final Type<Reveal> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "aspect_reveal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reveal> STREAM_CODEC = StreamCodec.of((buf, r) -> {
            buf.writeUtf(r.titan, 32);
            buf.writeVarInt(r.got.size());
            for (Got g : r.got) {
                buf.writeUtf(g.id, 64);
                buf.writeVarInt(g.before);
                buf.writeVarInt(g.after);
            }
            buf.writeVarInt(r.extras.size());
            for (net.minecraft.world.item.ItemStack s : r.extras) {
                net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
            }
        }, buf -> {
            String titan = buf.readUtf(32);
            int n = buf.readVarInt();
            List<Got> got = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                got.add(new Got(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
            }
            int m = Math.min(64, buf.readVarInt());
            List<net.minecraft.world.item.ItemStack> extras = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                extras.add(net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            return new Reveal(titan, List.copyOf(got), List.copyOf(extras));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 一人に渡す。渡したもの (祭壇に並ぶ順)
    public static List<Got> grant(ServerPlayer p, String titan, boolean noWipe) {
        SkillData d = SkillManager.get(p);
        Job job = Job.byId(d.curJob);
        List<Got> out = new ArrayList<>();
        if (job == null) {
            return out;
        }
        int rolls = noWipe ? 2 : 1;
        for (int i = 0; i < rolls; i++) {
            Aspect a = Aspects.roll(job, titan, d.aspectPity, p.getRandom());
            if (a == null) {
                continue;
            }
            d.aspectPity = a.rarity() == Aspect.LEGENDARY ? 0 : d.aspectPity + 1;
            int before = d.aspects.getOrDefault(a.id(), 0);
            // 最高 Lv の先の数は数えない (それより上は意味がない)
            int after = Math.min(Aspects.copiesFor(Aspects.MAX_LEVEL), before + 1);
            d.aspects.put(a.id(), after);
            out.add(new Got(a.id(), before, after));
        }
        SkillManager.sync(p);
        if (!out.isEmpty() && !(p instanceof FakePlayer) && p.connection != null) {
            // 心臓の報酬 (箱・鐘銭・素材) が渡り終わるのを少し待ってから、まとめて祭壇へ (sendLater)
            long now = p.server.getTickCount();
            LATER.put(p.getUUID(), new Later(titan, List.copyOf(out), now - LOOK_BACK, now + WAIT));
        }
        for (Got g : out) {
            Aspect a = Aspects.get(g.id());
            Notice.gain(p, Component.translatable(a.nameKey()).append(" ").append(Component.translatable("aspect.skyabyss.level", Aspects.levelOf(g.after()))));
        }
        return out;
    }

    // 祭壇を開くのを待っている人 (心臓の報酬が渡るまで)。保存しない
    private record Later(String titan, List<Got> got, long from, long at) {
    }

    private static final java.util.Map<java.util.UUID, Later> LATER = new java.util.concurrent.ConcurrentHashMap<>();
    // 心臓のあとの報酬を数えはじめる所 (祭壇の決まりの少し前から) と、待つ長さ (tick)
    static final int LOOK_BACK = 20 * 10;
    static final int WAIT = 60;

    @SubscribeEvent
    static void sendLater(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (LATER.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        for (var it = LATER.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now < e.getValue().at()) {
                continue;
            }
            it.remove();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && p.connection != null) {
                Later l = e.getValue();
                List<net.minecraft.world.item.ItemStack> extras = jp.revati.skyabyss.loot.RewardTally.since(p.getUUID(), l.from());
                PacketDistributor.sendToPlayer(p, new Reveal(l.titan(), l.got(), extras.subList(0, Math.min(64, extras.size()))));
            }
        }
    }

    @SubscribeEvent
    static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        LATER.clear();
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Reveal.TYPE, Reveal.STREAM_CODEC,
                (p, ctx) -> jp.revati.skyabyss.client.aspect.AspectRevealScreen.open(p));
    }
}

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

    // サーバー → 持ち主: 報いの祭壇を開く (出たものを並べる。titan はルートの巨神)
    public record Reveal(String titan, List<Got> got) implements CustomPacketPayload {
        public static final Type<Reveal> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "aspect_reveal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reveal> STREAM_CODEC = StreamCodec.of((buf, r) -> {
            buf.writeUtf(r.titan, 32);
            buf.writeVarInt(r.got.size());
            for (Got g : r.got) {
                buf.writeUtf(g.id, 64);
                buf.writeVarInt(g.before);
                buf.writeVarInt(g.after);
            }
        }, buf -> {
            String titan = buf.readUtf(32);
            int n = buf.readVarInt();
            List<Got> got = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                got.add(new Got(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
            }
            return new Reveal(titan, List.copyOf(got));
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
            PacketDistributor.sendToPlayer(p, new Reveal(titan, List.copyOf(out)));
        }
        for (Got g : out) {
            Aspect a = Aspects.get(g.id());
            Notice.gain(p, Component.translatable(a.nameKey()).append(" ").append(Component.translatable("aspect.skyabyss.level", Aspects.levelOf(g.after()))));
        }
        return out;
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Reveal.TYPE, Reveal.STREAM_CODEC,
                (p, ctx) -> jp.revati.skyabyss.client.aspect.AspectRevealScreen.open(p));
    }
}

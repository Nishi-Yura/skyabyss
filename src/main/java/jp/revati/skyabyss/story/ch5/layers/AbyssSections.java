package jp.revati.skyabyss.story.ch5.layers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssNetwork;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 深淵の区切り (docs/design/36 の 8 章の「章」)。五章の一「骨の層」・五章の二「塩の層」・五章の三「雷の層」・五章の四「灰の層」・五章の終わり「心臓」。
//   左上の章の札は、深淵の中ではいる所の区切りを出す (Section の payload → client/AbyssSectionClient → ChapterLabel)
//   その区切りの野営 (心臓は控えの間) に初めて入ると: 短い場面 (SectionScenes) → 題字の札 (Notice.chapter) → 語り部の二言 (人ごとに一度。LayerData "seen:<区切り>")
//   目的の帯の上書き: 主の戦い・番人の戦いのあいだ、その人の帯に戦いの一言を出す (objective)。期限が切れると元のクエストの目的に戻る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssSections {
    static final String[] KEYS = {"bone", "salt", "storm", "ash", "heart"};

    private record Line(String key, long until) {
    }

    private record Sent(int section, String objective) {
    }

    private static final Map<UUID, Line> OBJECTIVE = new HashMap<>();
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    // 場面のあとに題字を出す待ち (場面の ID → 区切り)
    private static final Map<UUID, Integer> CARD_AFTER = new HashMap<>();
    private static boolean hooked;

    private AbyssSections() {
    }

    // 帯の一言 (ticks のあいだ)。key = 翻訳キー。null で消す
    public static void objective(ServerPlayer p, String key, int ticks) {
        if (key == null) {
            OBJECTIVE.remove(p.getUUID());
        } else {
            OBJECTIVE.put(p.getUUID(), new Line(key, p.level().getGameTime() + ticks));
        }
    }

    public static String objectiveNow(ServerPlayer p) {
        Line l = OBJECTIVE.get(p.getUUID());
        return l == null || l.until() < p.level().getGameTime() ? "" : l.key();
    }

    public static Component tab(int section) {
        return Component.translatable("chapter.skyabyss.abyss_v2." + KEYS[section] + ".tab");
    }

    public static Component title(int section) {
        return Component.translatable("chapter.skyabyss.abyss_v2." + KEYS[section] + ".title");
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null
                || p.tickCount % 10 != 3) {
            return;
        }
        if (!hooked) {
            hooked = true;
            for (String k : KEYS) {
                Cinematics.onEnd(SectionScenes.PREFIX + k, (pl, skipped) -> card(pl));
            }
        }
        int section = AbyssWorld.in(p) && AbyssBuild.built(p.server) ? Layers.sectionAt(p.blockPosition()) : -1;
        String obj = section >= 0 ? objectiveNow(p) : "";
        Sent now = new Sent(section, obj);
        if (!Objects.equals(SENT.get(p.getUUID()), now)) {
            SENT.put(p.getUUID(), now);
            PacketDistributor.sendToPlayer(p, new AbyssNetwork.Section(section, obj));
        }
        if (section >= 0) {
            arrive(p, section);
        }
    }

    // 区切りの入口 (野営・控えの間) に入ったか見て、初めてなら場面と題字
    static void arrive(ServerPlayer p, int section) {
        boolean at;
        BlockPos face;
        if (section == Layers.HEART) {
            BlockPos in = AbyssBuild.mark("heart_in");
            at = in != null && in.closerThan(p.blockPosition(), 7);
            face = AbyssBuild.mark("heart");
        } else {
            String layer = Layers.ORDER.get(section);
            at = Layers.inCamp(layer, p.blockPosition());
            face = AbyssBuild.mark("camp_" + layer);
        }
        if (!at || p.isSpectator() || !p.isAlive()) {
            return;
        }
        LayerData d = LayerData.get(p.server);
        if (!d.set(p.getUUID(), "seen:" + KEYS[section])) {
            return;
        }
        CARD_AFTER.put(p.getUUID(), section);
        Vec3 toward = face == null ? null : Vec3.atBottomCenterOf(face);
        if (!Cinematics.start(p, SectionScenes.PREFIX + KEYS[section], null, toward)) {
            card(p);
        }
    }

    // 題字の札と語り部の二言
    static void card(ServerPlayer p) {
        Integer s = CARD_AFTER.remove(p.getUUID());
        if (s == null) {
            return;
        }
        Notice.chapter(p, tab(s), title(s));
        Notice.narrate(p, "message.skyabyss.abyss_v2.teller." + KEYS[s] + "_1");
        LayerFights.later(p.server, 80, () -> Notice.narrate(p, "message.skyabyss.abyss_v2.teller." + KEYS[s] + "_2"));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        OBJECTIVE.remove(event.getEntity().getUUID());
        CARD_AFTER.remove(event.getEntity().getUUID());
    }
}

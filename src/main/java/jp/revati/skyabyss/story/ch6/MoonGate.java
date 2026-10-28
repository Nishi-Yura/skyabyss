package jp.revati.skyabyss.story.ch6;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.SkyKey;
import jp.revati.skyabyss.story.ch1.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 月への門 (docs/design/46 の 3.2)。都の大鐘楼の鐘の間の床の天然磁石 (都の設計図の印 moon_gate。ShelterBuilder が付属の点 moon_gate に残す)。
//   渡る    天の鍵をそろえ、「近づく月」を終えた人が右クリック → 光の柱が 2 秒立ってから月の着く所へ (合図 moon_gate)
//   足りない 鍵がない・まだの人には一言 (何が足りないか)
//   帰る    月の着く所の帰りの台 (天然磁石) を右クリック → 門のそばへ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonGate {
    public static final String MARK = "moon_gate";
    public static final String SIGNAL = "moon_gate";
    static final int BEAM_TICKS = 40;

    private MoonGate() {
    }

    @Nullable
    public static BlockPos gatePos(MinecraftServer server) {
        SiteData.Entry e = SiteData.get(server.overworld()).first(OpeningSites.Kind.SHELTER.id);
        return e == null ? null : e.extra(MARK);
    }

    // 渡れるか: null なら渡れる。だめなら一言の鍵
    @Nullable
    public static String refusal(ServerPlayer p) {
        if (!SkyKey.assembled(p)) {
            return "message.skyabyss.moon.gate.no_key";
        }
        QuestLog.Entry e = QuestLog.of(p).entry(jp.revati.skyabyss.story.ch5.MoonNear.QUEST);
        if (e == null || !e.done) {
            return "message.skyabyss.moon.gate.not_yet";
        }
        return null;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        if (p.level().dimension() == Level.OVERWORLD && pos.equals(gatePos(p.server))) {
            event.setCanceled(true);
            String no = refusal(p);
            if (no != null) {
                Notice.narrate(p, no);
                return;
            }
            open(p, pos);
        } else if (MoonWorld.in(p) && pos.equals(MoonWorld.returnStone())) {
            event.setCanceled(true);
            p.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.7f);
            MoonWorld.leave(p);
        }
    }

    // 光の柱を立てて、2 秒後に渡す
    public static void open(ServerPlayer p, BlockPos gate) {
        ServerLevel level = p.serverLevel();
        level.playSound(null, gate, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
        Notice.narrate(p, "message.skyabyss.moon.gate.open");
        for (int t = 0; t < BEAM_TICKS; t += 4) {
            int tt = t;
            Tasks.later(t, () -> {
                for (int y = 0; y < 40; y += 2) {
                    level.sendParticles(ParticleTypes.END_ROD, gate.getX() + 0.5, gate.getY() + 1 + y + tt * 0.1, gate.getZ() + 0.5, 2, 0.3, 0.6, 0.3, 0.01);
                }
            });
        }
        Tasks.later(BEAM_TICKS, () -> {
            if (p.isAlive() && !p.hasDisconnected() && MoonWorld.enter(p)) {
                Quests.signal(p, SIGNAL);
                Notice.place(p, net.minecraft.network.chat.Component.translatable("chapter.skyabyss.moon.arrive.title"),
                        MoonClock.name(MoonClock.phase(p.serverLevel())));
            }
        });
    }
}

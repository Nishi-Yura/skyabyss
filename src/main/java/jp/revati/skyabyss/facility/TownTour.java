package jp.revati.skyabyss.facility;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 都の町めぐり (docs/design/40 の 4 章「はじめの一時間」の 2)。初めて都に入った人を、案内の灯りの道 (TownGuide) で
// 鍛冶屋 → 宿 → 道具屋 → 組合 の順に連れて回る。戸に着くたびに小さな報い (鐘銭、宿ではパン)。終わると「門の外の弱い敵で腕を試そう」。
// 一章より先へ進んでいる人は、めぐらずに済んだことにする。帯の文は一行
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TownTour {
    static final String TAG = "skyabyss_town_tour";
    static final List<String> STOPS = List.of("smithy", "inn", "tool_shop", "guild");
    static final int[] COINS = {3, 3, 5, 5};
    static final double ARRIVE = 4.5;
    static final int CHECK = 20;
    public static final int DONE = STOPS.size() + 1;

    private TownTour() {
    }

    // 0 = まだ、1..4 = その番号の所へ向かっている、DONE = 済み
    public static int step(ServerPlayer p) {
        return p.getPersistentData().getInt(TAG);
    }

    static void setStep(ServerPlayer p, int s) {
        CompoundTag t = p.getPersistentData();
        t.putInt(TAG, s);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isSpectator()
                || p.level().dimension() != Level.OVERWORLD || (p.tickCount + p.getId()) % CHECK != 0) {
            return;
        }
        tick(p);
    }

    public static void tick(ServerPlayer p) {
        int s = step(p);
        if (s >= DONE) {
            return;
        }
        if (s == 0) {
            if (!TownGuide.inTown(p.server, p.blockPosition())) {
                return;
            }
            if (Chapters.of(p).ordinal() > Chapters.WHALE.ordinal()) {
                setStep(p, DONE);
                return;
            }
            setStep(p, 1);
            show(p, 1);
            return;
        }
        String id = STOPS.get(s - 1);
        BlockPos door = TownGuide.doors(p.server).get(id);
        if (door == null) {
            setStep(p, DONE);
            return;
        }
        if (p.distanceToSqr(door.getX() + 0.5, door.getY(), door.getZ() + 0.5) > ARRIVE * ARRIVE) {
            return;
        }
        Coins.give(p, COINS[s - 1]);
        if (id.equals("inn")) {
            jp.revati.skyabyss.quest.Quests.give(p, new ItemStack(Items.BREAD, 3));
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.4f);
        int next = s + 1;
        setStep(p, next);
        if (next >= DONE) {
            TownGuide.pick(p, "");
            Notice.hint(p, "message.skyabyss.tour.done");
        } else {
            show(p, next);
        }
    }

    static void show(ServerPlayer p, int s) {
        String id = STOPS.get(s - 1);
        Notice.hint(p, "message.skyabyss.tour.step", s, STOPS.size(), Component.translatable(TownGuide.place(id).toKey()));
        TownGuide.pick(p, id);
    }
}

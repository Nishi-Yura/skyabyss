package jp.revati.skyabyss.story.finale;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// サーバーが止まったら、終章の静的な状態を片付ける (一人用でワールドを替えると、前のワールドの戦い・付いて歩く相手・待っている打ちが残らないように)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FinaleReset {
    private FinaleReset() {
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        Echoes.clear();
        BellIsle.reset();
        BellArena.resetAll();
        LampIsle.resetAll();
        ForgeIsle.resetAll();
        PromiseIsle.reset();
        SeatArena.resetAll();
        FinaleEnding.resetAll();
        FinaleWorld.resetAll();
        jp.revati.skyabyss.story.finale.gear.DivineGearEvents.resetAll();
        jp.revati.skyabyss.story.finale.founder.FounderArena.resetAll();
    }
}

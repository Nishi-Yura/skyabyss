package jp.revati.skyabyss.npc;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 月のループの回数と、ループで会話の記録を巻き戻す入口 (器だけ。ループ本体は M4 の担当)。
// M4 は月が落ちたときに WorldLoop.advance(server) を呼ぶ。サーバー全体で一つのループ (01 のマルチの決まり)。
// その場にいなかった人は、次に入ったときに追いつく (NpcBonds.loopSeen と比べる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WorldLoop {
    private WorldLoop() {
    }

    public static int count(MinecraftServer server) {
        return NpcWorldData.get(server.overworld()).loop;
    }

    // ループを一つ進める。いま入っている全員の会話の記録を巻き戻す
    public static void advance(MinecraftServer server) {
        NpcWorldData data = NpcWorldData.get(server.overworld());
        data.loop++;
        data.setDirty();
        server.getPlayerList().getPlayers().forEach(WorldLoop::catchUp);
    }

    // 回数を直接決める (コマンド用)。記録の巻き戻しはしない (条件 loop を試すため)
    public static void set(MinecraftServer server, int loop) {
        NpcWorldData data = NpcWorldData.get(server.overworld());
        data.loop = Math.max(0, loop);
        data.setDirty();
    }

    // この人の記録を、今のループに合わせる。巻き戻したら true
    public static boolean catchUp(ServerPlayer player) {
        NpcBonds bonds = NpcBonds.of(player);
        int now = count(player.server);
        if (bonds.loopSeen >= now) {
            bonds.loopSeen = Math.min(bonds.loopSeen, now);
            return false;
        }
        bonds.onLoop();
        bonds.loopSeen = now;
        NpcBonds.sync(player);
        Quests.onLoop(player);
        return true;
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            catchUp(player);
        }
    }
}

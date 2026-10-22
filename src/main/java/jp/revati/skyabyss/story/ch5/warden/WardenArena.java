package jp.revati.skyabyss.story.ch5.warden;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssPressure;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 心臓の間の戦いの始まりと終わり (36 の 6.1・6.5)。
//   始まり   まだ倒していない間に、だれかが心臓の間 (印 heart から 26 以内・床の 2 下〜天井) に入ると、灯籠を全部消して番人を出す
//            体力は、そのとき間にいる人数で合わせる
//   終わり   倒すと、そのとき間にいる人に合図 abyss_heart。倒した印はワールドに一つ (再戦は A8 の悪夢)
//   やり直し 間からだれもいなくなって 30 秒たつと、番人は闇へ戻る (次に入ると最初から)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WardenArena {
    public static final String SIGNAL_HEART = "abyss_heart";
    static final double ROOM_RADIUS = 26;
    static final int ROOM_HEIGHT = 46;
    static final int EMPTY_RESET = 30 * 20;

    @Nullable
    private static UUID warden;
    private static int empty;

    static {
        AbyssPressure.voiceScale = p -> AbyssBeacons.litCount() == 0 && inRoom(p) && warden != null ? 2.0 : 1.0;
    }

    private WardenArena() {
    }

    public static boolean inRoom(Entity e) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null || !(e.level() instanceof ServerLevel l) || l.dimension() != AbyssWorld.LEVEL) {
            return false;
        }
        double dx = e.getX() - (heart.getX() + 0.5);
        double dz = e.getZ() - (heart.getZ() + 0.5);
        return dx * dx + dz * dz <= ROOM_RADIUS * ROOM_RADIUS && e.getY() >= heart.getY() - 2 && e.getY() <= heart.getY() + ROOM_HEIGHT;
    }

    public static List<ServerPlayer> inside(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && inRoom(p)) {
                out.add(p);
            }
        }
        return out;
    }

    @Nullable
    public static HeartWarden current(ServerLevel level) {
        if (warden == null) {
            return null;
        }
        Entity e = level.getEntity(warden);
        return e instanceof HeartWarden w && w.isAlive() ? w : null;
    }

    public static boolean cleared(MinecraftServer server) {
        return Data.get(server).cleared;
    }

    // 番人を出す (間に入ったとき・開発用のコマンド・自己テスト)
    @Nullable
    public static HeartWarden wake(ServerLevel level) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null) {
            return null;
        }
        HeartWarden old = current(level);
        if (old != null) {
            old.discard();
        }
        AbyssBeacons.reset(level);
        HeartWarden w = new HeartWarden(WardenSetup.HEART_WARDEN.get(), level);
        w.moveTo(heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 8.5, 180f, 0f);
        List<ServerPlayer> in = inside(level);
        w.scaleFor(Math.max(1, in.size()));
        level.addFreshEntity(w);
        warden = w.getUUID();
        empty = 0;
        for (ServerPlayer p : in) {
            Notice.narrate(p, "message.skyabyss.warden.wake");
        }
        return w;
    }

    // 番人が倒れたとき (HeartWarden.die から)
    static void fallen(ServerLevel level, HeartWarden w) {
        if (!w.getUUID().equals(warden)) {
            return;
        }
        warden = null;
        Data d = Data.get(level.getServer());
        d.cleared = true;
        d.setDirty();
        for (ServerPlayer p : inside(level)) {
            Quests.signal(p, SIGNAL_HEART);
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || AbyssBuild.mark("heart") == null) {
            return;
        }
        HeartWarden w = current(level);
        boolean someone = !inside(level).isEmpty();
        if (w == null) {
            warden = null;
            if (someone && !Data.get(server).cleared) {
                wake(level);
            }
            return;
        }
        empty = someone ? 0 : empty + 10;
        if (empty >= EMPTY_RESET) {
            w.discard();
            warden = null;
            AbyssBeacons.reset(level);
        }
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abyss")
                        .then(Commands.literal("warden")
                                .then(Commands.literal("wake").executes(c -> {
                                    ServerLevel level = c.getSource().getServer().getLevel(AbyssWorld.LEVEL);
                                    HeartWarden w = level == null ? null : wake(level);
                                    c.getSource().sendSuccess(() -> Component.literal(w == null ? "the heart room is not built" : "the warden wakes"), false);
                                    return w == null ? 0 : 1;
                                }))
                                .then(Commands.literal("reset").executes(c -> {
                                    Data d = Data.get(c.getSource().getServer());
                                    d.cleared = false;
                                    d.setDirty();
                                    c.getSource().sendSuccess(() -> Component.literal("the warden can wake again"), false);
                                    return 1;
                                })))));
    }

    // 倒した印 (ワールドに一つ)
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        boolean cleared;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_heart_warden");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.cleared = tag.getBoolean("Cleared");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("Cleared", cleared);
            return tag;
        }
    }
}

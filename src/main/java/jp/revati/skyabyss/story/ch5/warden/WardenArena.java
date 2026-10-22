package jp.revati.skyabyss.story.ch5.warden;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssEntrance;
import jp.revati.skyabyss.story.ch5.AbyssPressure;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.town.TownGuests;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 心臓の間の戦いの始まりと終わり (36 の 6.1・6.5)。
//   始まり   メインの段 warden の人が心臓の間 (印 heart から 26 以内・床の 2 下〜天井) に入ると、灯籠を全部消して番人を出す
//            体力は、そのとき間にいる人数で合わせる
//   終わり   倒すと、そのとき間にいる人に合図 abyss_heart。倒した印はワールドに一つ (台の鼓動・再戦は A8 の悪夢)
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

    // 間の中に、まだ番人を倒していない人 (メインの段 warden) がいるか。戦いは組ごと (倒した人だけなら目覚めない)
    static boolean needsFight(ServerLevel level) {
        for (ServerPlayer p : inside(level)) {
            if ("warden".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST))) {
                return true;
            }
        }
        return false;
    }

    // だれかが一度でも倒したか (A8 の悪夢と、台の鼓動)
    public static boolean cleared(MinecraftServer server) {
        return Data.get(server).cleared;
    }

    // 番人を出す (間に入ったとき・開発用のコマンド・自己テスト)。普通の戦い
    @Nullable
    public static HeartWarden wake(ServerLevel level) {
        return wake(level, NightmareTier.NORMAL);
    }

    // 番人を段つきで出す (普通の戦いは NORMAL。悪夢は Nightmare が段を決めて呼ぶ)。
    // 出す前に、前の番人・写し・灯籠・悪夢の一回分を片づける
    @Nullable
    public static HeartWarden wake(ServerLevel level, NightmareTier tier) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null) {
            return null;
        }
        Nightmare.abort(level);
        clearRoom(level);
        HeartWarden w = new HeartWarden(WardenSetup.HEART_WARDEN.get(), level);
        w.setTier(tier);
        w.moveTo(heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 8.5, 180f, 0f);
        List<ServerPlayer> in = inside(level);
        w.scaleFor(Math.max(1, in.size()));
        level.addFreshEntity(w);
        warden = w.getUUID();
        empty = 0;
        for (ServerPlayer p : in) {
            if (tier.nightmare()) {
                Notice.narrate(p, "message.skyabyss.abyss_nm.wake", tier.n(), Component.translatable(Nightmare.nameKey(tier.n())), Component.translatable(Nightmare.ruleKey(tier.n())));
            } else {
                Notice.narrate(p, "message.skyabyss.warden.wake");
            }
        }
        return w;
    }

    // 間を空にする: 番人と写しを消し、灯籠を消して置き直す (番人の bar は番人が消えるときに外れる)。
    // 悪夢の後片付けもここ (終わり・空の間・建て直し)。番人の籍 (warden) も外す
    static void clearRoom(ServerLevel level) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart != null) {
            for (HeartWarden h : level.getEntitiesOfClass(HeartWarden.class, new AABB(heart).inflate(64), e -> true)) {
                h.discard();
            }
        }
        warden = null;
        AbyssBeacons.reset(level);
    }

    // 番人がいないのに間に生きている番人が残っているとき (読み込み直し・途中の消えた番) を片づける
    static void orphans(ServerLevel level) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null) {
            return;
        }
        for (HeartWarden h : level.getEntitiesOfClass(HeartWarden.class, new AABB(heart).inflate(64), HeartWarden::isAlive)) {
            h.discard();
        }
    }

    // 番人が倒れたとき (HeartWarden.die から)
    static void fallen(ServerLevel level, HeartWarden w) {
        if (!w.getUUID().equals(warden)) {
            return;
        }
        warden = null;
        // 悪夢の段は、段の記録と報酬だけ (五章の本戦の合図・倒した印は普通の戦いのもの)
        if (Nightmare.active()) {
            Nightmare.won(level);
            return;
        }
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
            orphans(level);
            // 悪夢の最中は、普通の戦いを出さない (間にいる別の組が段 warden でも)
            if (someone && needsFight(level) && !Nightmare.active()) {
                wake(level);
            }
            return;
        }
        empty = someone ? 0 : empty + 10;
        if (empty >= EMPTY_RESET) {
            // 間が空いた (倒れた・離れた) ので、悪夢も普通の戦いも闇へ戻す。悪夢は記録せず、次は同じ段から
            Nightmare.abort(level);
            clearRoom(level);
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
                                })))
                        // 開発用 (製品版では出ない): 番人の立つ所に光の術の灯りを置く / 消す。光の中の絵 (LIT) を撮るため
                        .then(Commands.literal("dev").requires(s -> !FMLEnvironment.production)
                                .then(Commands.literal("lamp").executes(c -> {
                                    ServerLevel level = c.getSource().getServer().getLevel(AbyssWorld.LEVEL);
                                    HeartWarden w = level == null ? null : current(level);
                                    if (w != null) {
                                        AbyssLight.add(level, w.blockPosition(), 9, level.getGameTime() + 12000, DEV_OWNER);
                                    }
                                    return w == null ? 0 : 1;
                                }))
                                .then(Commands.literal("dark").executes(c -> {
                                    AbyssLight.clear();
                                    return 1;
                                })))));
    }

    private static final UUID DEV_OWNER = new UUID(0L, 1L);

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

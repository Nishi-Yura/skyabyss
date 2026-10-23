package jp.revati.skyabyss.story.ch5;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.StorySetup;
import jp.revati.skyabyss.town.TownCellar;
import jp.revati.skyabyss.town.TownData;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 五章の入口「足もとの鼓動」(docs/design/34 の 4.7)。メイン main_abyss の段 door・shaft を進める。
//   封じた戸   都の設計図の印 abyss_door (戸の下の段) とその上の 1 段。メインが段 door 以降の人が、天の鍵 (持ち物か旅嚢) を持って
//              戸 (またはそのまわり 1) を右クリックすると開く。一度開けたらワールドで開いたまま (TownData.abyssOpen)。都を建て直して
//              戸が戻っていたら、人が近くに来たとき開け直す。開いた戸の前 3 に段 door の人が来ても合図 ch5_abyss_door
//   酒場の壁   メインが始まったら、酒場の地下の奥の壁 (TownCellar) を開ける (酔いどれの頼みをしていない人も戸まで行けるように)
//   踊り場     印 abyss_landing_1・abyss_landing_2 の 6 以内に来た人に一度ずつ一言 (人ごとの印 skyabyss_ch5_landing_<n>)。
//              いるあいだは合図 ch5_landing_<n> (サブ ch5/shaft_landings「踊り場の荷」。一人ずつ)
//   底の境     印 abyss_gate の 6 以内で合図 ch5_threshold と一言 (段 shaft の人)。門を開けて深淵へ入るのは AbyssGate
//   確かめ     SELFTEST ch5 entrance (story/ch5/AbyssEntranceSelfTest)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssEntrance {
    public static final String QUEST = "main_abyss";
    public static final String DOOR = "abyss_door";
    public static final String GATE = "abyss_gate";
    public static final String[] LANDINGS = {"abyss_landing_1", "abyss_landing_2"};
    static final String SIGNAL_DOOR = "ch5_abyss_door";
    static final String SIGNAL_THRESHOLD = "ch5_threshold";
    static final String SIGNAL_LANDING = "ch5_landing_";
    private static final double NEAR = 6.0;

    private AbyssEntrance() {
    }

    // ---------------------------------------------------------------- 戸
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != Level.OVERWORLD) {
            return;
        }
        MinecraftServer server = p.getServer();
        BlockPos door = server == null ? null : TownGuests.mark(server, DOOR);
        if (door == null || !nearDoor(event.getPos(), door)) {
            return;
        }
        String stage = TownGuests.stageOf(p, QUEST);
        if (!readyFor(stage) || !holdsKey(p)) {
            Notice.hint(p, "message.skyabyss.ch5.door_locked");
            return;
        }
        open(server, p);
        event.setCanceled(true);
    }

    static boolean nearDoor(BlockPos clicked, BlockPos door) {
        return Math.abs(clicked.getX() - door.getX()) <= 1 && Math.abs(clicked.getZ() - door.getZ()) <= 1
                && clicked.getY() >= door.getY() - 1 && clicked.getY() <= door.getY() + 2;
    }

    // 段 door か、そのあと (戸を開けた人がもう一度来たとき)
    static boolean readyFor(@Nullable String stage) {
        return stage != null && !"rumor".equals(stage) && !"prepare".equals(stage);
    }

    public static boolean holdsKey(ServerPlayer p) {
        return Pouch.countAll(p, StorySetup.SKY_KEY.get()) > 0;
    }

    // 戸を開ける (記録して、読み込まれていれば崩す)。開けた人に合図
    public static void open(MinecraftServer server, @Nullable ServerPlayer by) {
        TownData data = TownData.get(server);
        boolean first = !data.abyssOpen;
        if (first) {
            data.abyssOpen = true;
            data.setDirty();
        }
        BlockPos door = TownGuests.mark(server, DOOR);
        if (door != null) {
            carve(server.overworld(), door, first);
        }
        if (by != null) {
            if (first) {
                Notice.narrate(by, "message.skyabyss.ch5.door_open");
            }
            Quests.signalWithParty(by.serverLevel(), List.of(by), SIGNAL_DOOR);
        }
    }

    public static boolean isOpen(MinecraftServer server) {
        return TownData.get(server).abyssOpen;
    }

    private static void carve(ServerLevel level, BlockPos door, boolean loud) {
        if (!level.isLoaded(door)) {
            return;
        }
        for (BlockPos p : new BlockPos[]{door, door.above()}) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), 3);
            }
        }
        if (loud) {
            level.playSound(null, door, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 0.6f);
            level.playSound(null, door, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 0.5f);
        }
    }

    // ---------------------------------------------------------------- 見回り (1 秒ごと)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) {
            return;
        }
        ServerLevel level = server.overworld();
        BlockPos door = null;
        boolean looked = false;
        for (ServerPlayer p : java.util.List.copyOf(level.players())) {
            String stage = TownGuests.stageOf(p, QUEST);
            if (stage == null) {
                continue;
            }
            if (!looked) {
                door = TownGuests.mark(server, DOOR);
                looked = true;
                if (door == null) {
                    return;
                }
                keep(server, level, door);
            }
            check(server, p, stage, door);
        }
    }

    // 開いているはずの戸が戻っていれば崩す。メインが始まった人がいれば、酒場の壁も開ける
    private static void keep(MinecraftServer server, ServerLevel level, BlockPos door) {
        if (!TownCellar.isOpen(server)) {
            TownCellar.open(server);
        }
        if (isOpen(server) && level.isLoaded(door) && !(level.getBlockState(door).isAir() && level.getBlockState(door.above()).isAir())) {
            carve(level, door, false);
        }
    }

    static void check(MinecraftServer server, ServerPlayer p, String stage, BlockPos door) {
        if ("door".equals(stage) && isOpen(server) && p.blockPosition().closerThan(door, 3.5) && holdsKey(p)) {
            Quests.signalWithParty(p.serverLevel(), List.of(p), SIGNAL_DOOR);
        }
        for (int i = 0; i < LANDINGS.length; i++) {
            BlockPos at = TownGuests.mark(server, LANDINGS[i]);
            if (at != null && p.blockPosition().closerThan(at, NEAR)) {
                Quests.signal(p, SIGNAL_LANDING + (i + 1));
                if (once(p, "skyabyss_ch5_landing_" + (i + 1))) {
                    Notice.narrate(p, i == 0 ? "message.skyabyss.ch5.landing_workshop" : "message.skyabyss.ch5.landing_camp");
                }
            }
        }
        BlockPos gate = TownGuests.mark(server, GATE);
        if (gate != null && p.blockPosition().closerThan(gate, NEAR)) {
            if ("shaft".equals(stage)) {
                Quests.signalWithParty(p.serverLevel(), List.of(p), SIGNAL_THRESHOLD);
            }
            if (once(p, "skyabyss_ch5_threshold")) {
                Notice.narrate(p, "message.skyabyss.ch5.threshold");
                p.serverLevel().playSound(null, gate, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.5f, 0.5f);
            }
        }
    }

    // 人ごとに一度だけ真
    static boolean once(ServerPlayer p, String key) {
        CompoundTag tag = p.getPersistentData();
        if (tag.getBoolean(key)) {
            return false;
        }
        tag.putBoolean(key, true);
        return true;
    }
}

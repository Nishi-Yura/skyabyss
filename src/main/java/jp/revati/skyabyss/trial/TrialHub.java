package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 試練を越えたあとの流れ (docs/design/29 の 4.6、ユーザーの試遊の声)。
//  1. 石が鳴ると (TrialSteles.onRung)、そのグループのその試練の中にいる人を、少し待って (RETURN_DELAY) 鼓動の間へ戻す。
//     試練ごとに、中にいる人の数えだけが動く: ほかの試練にいる仲間は動かさない (分かれて進める)
//  2. 戻った人は、光った碑の場面 (Scenes.TABLET_REVEAL。短いカメラ・音) を見る。碑はそのグループの画面でだけ光る (HubTablets)
//  3. 越えた試練は、そのグループには封じられる: 中へ入ると、鼓動の間へ戻される (SEAL_EVERY tick ごとに見る)。ほかのグループには開いたまま。
//     封は、そのグループの試練が戻るとき (心臓を止めたあと) に石の記録といっしょに消える
//  記録 (石が鳴った印) はグループごと (パーティ・一人) の BackGroupData にあり、共有の背でも混ざらない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TrialHub {
    public static final int RETURN_DELAY = 80;
    public static final int SEAL_EVERY = 10;
    private static final int BOUNCE_COOLDOWN = 60;

    private record Pending(UUID group, String titan, String key, long due) {
    }

    private static final Map<String, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> BOUNCED = new HashMap<>();
    // 範囲の入れ物 (岩鯨の新作のセル以外は固定)
    private static final Map<String, List<AABB>> STATIC = new HashMap<>();

    private TrialHub() {
    }

    private static String pendingKey(UUID group, String titan, String key) {
        return group + "|" + TrialRegions.signalTitan(titan) + "|" + key;
    }

    // 石が鳴った (原型の石も新作の石も)。同じ試練・同じグループの二重の呼びは一つにまとめる
    public static void onCleared(ServerLevel back, UUID group, String titan, String key, @Nullable ServerPlayer by) {
        String progress = progressName(titan);
        if (progress == null || Titans.backOf(progress) != back.dimension()) {
            return;
        }
        String pk = pendingKey(group, progress, key);
        if (PENDING.containsKey(pk)) {
            return;
        }
        PENDING.put(pk, new Pending(group, progress, key, back.getGameTime() + RETURN_DELAY));
        // 碑は鳴った瞬間から (その場で光を見せる)
        if (HubTablets.install(back, progress) >= 0) {
            HubTablets.refresh(back, group);
        }
    }

    @Nullable
    private static String progressName(String titan) {
        return switch (titan) {
            case "whale", Titans.STONE_WHALE -> Titans.STONE_WHALE;
            case "tide", Titans.TIDE_SHELL -> Titans.TIDE_SHELL;
            case "storm", Titans.THUNDER_WING -> Titans.THUNDER_WING;
            case Titans.EMBER -> Titans.EMBER;
            default -> null;
        };
    }

    public static boolean pending(UUID group, String titan, String key) {
        return PENDING.containsKey(pendingKey(group, titan, key));
    }

    // その人が、グループの鍵のその試練の中にいるか
    public static boolean inside(ServerLevel back, UUID group, String titan, String key, ServerPlayer p) {
        List<AABB> regions = regions(back, titan, key, group);
        return TrialRegions.inside(regions, p.position());
    }

    private static List<AABB> regions(ServerLevel back, String titan, String key, UUID group) {
        if (Titans.STONE_WHALE.equals(titan)) {
            return TrialRegions.of(back, titan, key, group);
        }
        return STATIC.computeIfAbsent(titan + "|" + key, k -> TrialRegions.of(back, titan, key, null));
    }

    // 鼓動の間へ戻す (静かに。越えた直後の戻りと、封じられた試練への入り直しの戻りで使う)。戻せたか
    public static boolean sendToHub(ServerLevel back, ServerPlayer player, String titan, boolean reveal, @Nullable String key) {
        BlockPos hub = ReturnBeacons.hubOf(titan);
        if (hub == null) {
            return false;
        }
        player.stopRiding();
        player.teleportTo(back, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, player.getYRot(), player.getXRot());
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            // 試験の偽の人は通信がないので、位置だけ直接動かす
            player.setPos(hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        back.playSound(null, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (reveal && key != null) {
            reveal(back, player, titan, key);
        }
        return true;
    }

    // 光った碑を見せる (場面。場面中で始められなければ音と一行だけ)
    public static void reveal(ServerLevel back, ServerPlayer player, String titan, String key) {
        HubTablets.Spot spot = HubTablets.spotOf(back, titan, key);
        Notice.narrate(player, "message.skyabyss.trial_hub.returned");
        if (spot == null) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(spot.pos()).add(0, 0.5, 0);
        back.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.5f, 0.8f);
        back.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.2f, 0.7f);
        Cinematics.start(player, Scenes.TABLET_REVEAL, null, at);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        String titan = Titans.titanOfBack(level.dimension());
        if (titan == null) {
            return;
        }
        long now = level.getGameTime();
        if (now % SEAL_EVERY != 0) {
            return;
        }
        List<ServerPlayer> players = Players.snapshot(level.players());
        if (players.isEmpty() && PENDING.isEmpty()) {
            return;
        }
        // 碑を立てる・立て直す (人がいるあいだ 10 秒に一度)
        if (now % 200 == 0 && !players.isEmpty()) {
            // 順位の石と飾り板 (転生。docs/design/31 の C11・C21)
            jp.revati.skyabyss.rebirth.RebirthBoards.install(level, titan);
        }
        if (now % 200 == 0 && !players.isEmpty() && HubTablets.install(level, titan) > 0) {
            for (UUID g : groupsOf(players)) {
                HubTablets.refresh(level, g);
            }
        }
        tickPending(level, now, g -> Groups.online(level, g));
        tickSeal(level, titan, players, now);
    }

    // 戻す時刻になった試練 (online = そのグループの、その背にいる人の取り方。試験では偽の人を渡す)
    static void tickPending(ServerLevel level, long now, java.util.function.Function<UUID, List<ServerPlayer>> online) {
        for (Pending p : new ArrayList<>(PENDING.values())) {
            if (now < p.due() || Titans.backOf(p.titan()) != level.dimension()) {
                continue;
            }
            PENDING.remove(pendingKey(p.group(), p.titan(), p.key()));
            List<ServerPlayer> moved = new ArrayList<>();
            for (ServerPlayer m : online.apply(p.group())) {
                if (m.isAlive() && !m.isSpectator() && inside(level, p.group(), p.titan(), p.key(), m)) {
                    moved.add(m);
                }
            }
            HubTablets.install(level, p.titan());
            HubTablets.refresh(level, p.group());
            for (ServerPlayer m : moved) {
                sendToHub(level, m, p.titan(), true, p.key());
            }
            SkyAbyss.LOGGER.info("Trial {} of the {} cleared by group {}: {} member(s) returned to the hub", p.key(), p.titan(), p.group(), moved.size());
        }
    }

    // 封じ: 越えた試練の中へ入った人は、鼓動の間へ戻す。戻した人の数
    static int tickSeal(ServerLevel level, String titan, List<ServerPlayer> players, long now) {
        int bounced = 0;
        for (ServerPlayer p : players) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
                continue;
            }
            UUID group = Groups.keyOf(p);
            for (String key : TrialRegions.keys(titan)) {
                if (pending(group, titan, key) || !HubTablets.cleared(level, group, titan, key) || !inside(level, group, titan, key, p)) {
                    continue;
                }
                Long last = BOUNCED.get(p.getUUID());
                if (last != null && now - last < BOUNCE_COOLDOWN) {
                    break;
                }
                BOUNCED.put(p.getUUID(), now);
                if (sendToHub(level, p, titan, false, null)) {
                    Notice.bar(p, ModText.hint("message.skyabyss.trial_hub.sealed"));
                    bounced++;
                }
                break;
            }
        }
        return bounced;
    }

    private static List<UUID> groupsOf(List<ServerPlayer> players) {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer p : players) {
            UUID g = Groups.keyOf(p);
            if (!out.contains(g)) {
                out.add(g);
            }
        }
        return out;
    }

    // 試験用: 戻しを待っている数
    public static int pendingCount() {
        return PENDING.size();
    }

    // 試験用: 待ちの時刻を今にして、すぐ動かす
    public static void flushPending(ServerLevel back) {
        for (Pending p : new ArrayList<>(PENDING.values())) {
            PENDING.put(pendingKey(p.group(), p.titan(), p.key()), new Pending(p.group(), p.titan(), p.key(), 0));
        }
    }

    public static void clearAll() {
        PENDING.clear();
        BOUNCED.clear();
        STATIC.clear();
        HubTablets.clearCache();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    // 登録: 碑の光の上書きを GroupOverlays へ (SkyAbyss の起動で一度)
    public static void register() {
        GroupOverlays.register(HubTablets::overlay);
    }
}

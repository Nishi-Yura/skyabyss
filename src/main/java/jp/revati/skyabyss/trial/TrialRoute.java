package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.util.Players;

// 巨神の順路 (docs/design/32 の 2 章): 歩 → 智 → 闘 → (中ボス) → 心臓。
//  - 入口 (到着の台から体へ入る口) に、その背にいるグループ全員が入ると 5 秒の予告のあと段 1 へ運ぶ (2.2)
//  - 試練を越える (ゴールの光る印) と 4 秒後に、その背にいるグループ全員を次の段の入口の休み場へ運ぶ (2.3)。三つ越えたら心臓の前室へ
//  - あきらめは焚き火で提案し、全員の「はい」で成立。成立するとグループ全員が背の外へ出される (2.4)
//  - 段の記録はグループの記録の keep.trial.step (0 = まだ / 1〜3 = 試練 / 4 = 心臓)。keep. なので試練のリセットでは消えず、
//    順路を終えるとき (心臓を止めた・あきらめ・グループのリセット) に明示的に消す
// 原型の三つ (周回 1) のための最初の版。周回の区画 (32 の 5 章) は R2 で足す
public final class TrialRoute {
    private TrialRoute() {
    }

    public static final String STEP = "keep.trial.step";
    public static final String ROUTE = "keep.trial.route";
    // 越えてから次へ運ぶまで (29 の 4.6 の 4 秒)
    public static final int CARRY_DELAY = 80;
    // 入口に全員がそろってから始まるまでの予告
    public static final int ENTRY_WAIT = 100;
    // あきらめの提案が流れるまで / 次の提案まで
    public static final int ABANDON_WAIT = 20 * 30;
    public static final int ABANDON_COOLDOWN = 20 * 120;
    public static final int LAST_STEP = 4;

    private record Pending(UUID group, String titan, int step, long due) {
    }

    private record Abandon(UUID group, String titan, UUID by, Set<UUID> yes, long until) {
    }

    private static final Map<String, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Abandon> ABANDON = new HashMap<>();
    private static final Map<UUID, Long> ABANDON_LAST = new HashMap<>();
    private static final Map<UUID, Long> ENTRY_START = new HashMap<>();

    // ---------------------------------------------------------------- 順路の形 (巨神ごとの三つの枠の鍵と、入口の休み場)
    // 枠の順 (歩・智・闘) の石の鍵 (TrialSteles / TrialRegions の key)
    public static List<String> keysOf(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> List.of("0", "2", "1");
            case Titans.TIDE_SHELL, Titans.THUNDER_WING -> List.of("west", "east", "front");
            case Titans.EMBER -> List.of("west", "east", "south");
            default -> List.of();
        };
    }

    // その段の入口の休み場 (段 1〜3 は試練の前室、段 4 は心臓の前室)
    @Nullable
    public static String restOf(String titan, int step) {
        if (step >= LAST_STEP) {
            return "heart";
        }
        List<String> keys = keysOf(titan);
        if (step < 1 || step > keys.size()) {
            return null;
        }
        String key = keys.get(step - 1);
        return switch (titan) {
            case Titans.STONE_WHALE -> switch (key) {
                case "0" -> "w0";
                case "2" -> TitanBackTrials.MAZE_ENTRY;
                default -> "e0";
            };
            case Titans.TIDE_SHELL -> switch (key) {
                case "west" -> "g0";
                case "east" -> "s0";
                default -> "f0";
            };
            case Titans.THUNDER_WING -> switch (key) {
                case "west" -> "w0";
                case "east" -> "e0";
                default -> "f0";
            };
            case Titans.EMBER -> switch (key) {
                case "west" -> "w0";
                case "east" -> "e0";
                default -> "s0";
            };
            default -> null;
        };
    }

    // 休み場の立つ位置 (各背の休み場の表から。立てる場所を探す)
    @Nullable
    public static BlockPos spawnOf(ServerLevel back, String titan, String rest) {
        return switch (titan) {
            case Titans.STONE_WHALE -> TitanBackTrials.restSpawn(back, rest);
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackTrials.restSpawn(back, rest);
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackTrials.restSpawn(back, rest);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackTrials.restSpawn(back, rest);
            default -> null;
        };
    }

    // 入口の箱 (到着の台から体へ入る口。docs/design/32 の 2.2)
    @Nullable
    public static AABB entryBox(String titan) {
        return switch (titan) {
            // 登り口の階段の最初の 5 段 (台の北寄りから東へ)
            case Titans.STONE_WHALE -> {
                int y = TitanBackLayout.platformY() + 1;
                yield new AABB(TitanBackLayout.STAIR_X0, y, TitanBackLayout.STAIR_Z0,
                        TitanBackLayout.STAIR_X0 + 5, y + TitanBackLayout.STAIR_HEIGHT + 2, TitanBackLayout.STAIR_Z1 + 1);
            }
            // 潮門の内側の段
            case Titans.TIDE_SHELL -> new AABB(-111, 63, 82, -104, 71, 89);
            // 尾の広場の北の端 (体へ入る口)
            case Titans.THUNDER_WING -> new AABB(-5, 146, 103, 6, 153, 109);
            // 口の奥の喉の降り口
            case Titans.EMBER -> new AABB(-4, 77, -129, 5, 84, -121);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- 記録
    public static int step(BackGroupData.Record r) {
        return Math.max(0, r.getInt(STEP));
    }

    public static int step(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return r == null ? 0 : step(r);
    }

    public static boolean active(ServerLevel back, UUID group) {
        return step(back, group) > 0;
    }

    public static void reset(BackGroupData.Record r) {
        r.remove(STEP);
        r.remove(ROUTE);
    }

    // 順路を終える (心臓を止めた・あきらめ・グループのリセット)
    public static void finish(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r != null) {
            reset(r);
        }
        PENDING.remove(group.toString());
        ABANDON.remove(group);
        ENTRY_START.remove(group);
    }

    private static String pendingKey(UUID group) {
        return group.toString();
    }

    // ---------------------------------------------------------------- 始める・進める・運ぶ
    public static void start(ServerLevel back, UUID group, String titan) {
        BackGroupData.Record r = GroupProgress.of(back, group);
        r.putInt(STEP, 1);
        r.putString(ROUTE, String.join(",", keysOf(titan)));
        ENTRY_START.remove(group);
        SkyAbyss.LOGGER.info("Route of the {} started for group {}", titan, group);
        carry(back, group, titan, 1);
    }

    // 試練 key を越えた (TrialSteles.onRung から)。次の段を記し、CARRY_DELAY のあとに運ぶ
    public static void onCleared(ServerLevel back, UUID group, String titan, String key) {
        List<String> keys = keysOf(titan);
        int idx = keys.indexOf(key);
        if (idx < 0) {
            return;
        }
        BackGroupData.Record r = GroupProgress.of(back, group);
        int next = Math.min(LAST_STEP, Math.max(step(r), idx + 1) + 1);
        r.putInt(STEP, next);
        if (!r.has(ROUTE)) {
            r.putString(ROUTE, String.join(",", keys));
        }
        PENDING.put(pendingKey(group), new Pending(group, titan, next, back.getGameTime() + CARRY_DELAY));
        for (ServerPlayer p : members(back, group)) {
            Notice.bar(p, ModText.progress(next >= LAST_STEP ? "message.skyabyss.route.next_heart" : "message.skyabyss.route.next", trialName(titan, keys.get(Math.min(next, 3) - 1))));
        }
    }

    public static Component trialName(String titan, String key) {
        return Component.translatable("trial.skyabyss." + TrialRegions.signalTitan(titan) + "_" + key);
    }

    // その背にいる人の一覧 (自己テストは偽の人の一覧に差し替える。偽の人は level.players() に入らないため)
    public static java.util.function.Function<ServerLevel, List<ServerPlayer>> PLAYERS = l -> Players.snapshot(l.players());

    // その背にいる、グループの生きている人 (観戦者を除く)
    public static List<ServerPlayer> members(ServerLevel back, UUID group) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : PLAYERS.apply(back)) {
            if (p.isAlive() && !p.isSpectator() && p.level() == back && Groups.isMember(p, group)) {
                list.add(p);
            }
        }
        return list;
    }

    // 段 step の入口の休み場へ、その背にいるグループ全員を運ぶ。運んだ人数
    public static int carry(ServerLevel back, UUID group, String titan, int step) {
        String rest = restOf(titan, step);
        BlockPos at = rest == null ? null : spawnOf(back, titan, rest);
        if (at == null) {
            SkyAbyss.LOGGER.warn("Route of the {}: no rest for step {}", titan, step);
            return 0;
        }
        int n = 0;
        for (ServerPlayer p : members(back, group)) {
            move(back, p, at);
            n++;
            Notice.narrate(p, step >= LAST_STEP ? "message.skyabyss.route.heart" : "message.skyabyss.route.step" + step,
                    step >= LAST_STEP ? Component.empty() : trialName(titan, keysOf(titan).get(step - 1)));
        }
        back.playSound(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        SkyAbyss.LOGGER.info("Route of the {}: group {} carried to step {} ({}) x{}", titan, group, step, rest, n);
        return n;
    }

    static void move(ServerLevel back, ServerPlayer p, BlockPos at) {
        p.stopRiding();
        p.teleportTo(back, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), p.getXRot());
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            // 試験の偽の人は通信がないので、位置だけ直接動かす
            p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // ---------------------------------------------------------------- tick (TrialHub の tick から。SEAL_EVERY ごと)
    public static void tick(ServerLevel back, String titan, List<ServerPlayer> players, long now) {
        for (Pending p : new ArrayList<>(PENDING.values())) {
            if (now < p.due() || Titans.backOf(p.titan()) != back.dimension()) {
                continue;
            }
            PENDING.remove(pendingKey(p.group()));
            carry(back, p.group(), p.titan(), p.step());
        }
        tickEntry(back, titan, players, now);
        tickAbandon(back, now);
    }

    private static void tickEntry(ServerLevel back, String titan, List<ServerPlayer> players, long now) {
        AABB box = entryBox(titan);
        if (box == null || players.isEmpty()) {
            return;
        }
        Set<UUID> groups = new LinkedHashSet<>();
        for (ServerPlayer p : players) {
            if (p.isAlive() && !p.isSpectator()) {
                groups.add(Groups.keyOf(p));
            }
        }
        for (UUID group : groups) {
            if (active(back, group)) {
                continue;
            }
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer p : players) {
                if (p.isAlive() && !p.isSpectator() && Groups.isMember(p, group) && box.contains(p.position())) {
                    inside.add(p);
                }
            }
            if (inside.isEmpty()) {
                ENTRY_START.remove(group);
                continue;
            }
            List<ServerPlayer> all = members(back, group);
            if (inside.size() < all.size()) {
                ENTRY_START.remove(group);
                for (ServerPlayer p : inside) {
                    Notice.bar(p, ModText.progress("message.skyabyss.route.waiting", inside.size(), all.size()));
                }
                continue;
            }
            long t0 = ENTRY_START.computeIfAbsent(group, g -> now);
            long left = ENTRY_WAIT - (now - t0);
            if (left <= 0) {
                start(back, group, titan);
                continue;
            }
            for (ServerPlayer p : inside) {
                Notice.bar(p, ModText.progress("message.skyabyss.route.starting", (left + 19) / 20));
            }
        }
    }

    // ---------------------------------------------------------------- あきらめ (全員の同意)
    // 焚き火から。提案した人は「はい」。一人のグループならその場で成立
    public static boolean propose(ServerPlayer by) {
        if (!(by.level() instanceof ServerLevel back)) {
            return false;
        }
        String titan = Titans.titanOfBack(back.dimension());
        UUID group = Groups.keyOf(by);
        if (titan == null || !active(back, group)) {
            return false;
        }
        long now = back.getGameTime();
        if (ABANDON.containsKey(group)) {
            Notice.hint(by, "message.skyabyss.route.abandon_pending");
            return true;
        }
        Long last = ABANDON_LAST.get(group);
        if (last != null && now - last < ABANDON_COOLDOWN) {
            Notice.hint(by, "message.skyabyss.route.abandon_cooldown", (ABANDON_COOLDOWN - (now - last) + 19) / 20);
            return true;
        }
        Set<UUID> yes = new LinkedHashSet<>();
        yes.add(by.getUUID());
        Abandon a = new Abandon(group, titan, by.getUUID(), yes, now + ABANDON_WAIT);
        ABANDON.put(group, a);
        ABANDON_LAST.put(group, now);
        if (settle(back, a)) {
            return true;
        }
        Component ask = ModText.progress("message.skyabyss.route.abandon_ask", by.getGameProfile().getName());
        Component yesBtn = Component.translatable("message.skyabyss.route.abandon_yes")
                .withStyle(Style.EMPTY.withColor(0x9FE8FF).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyroute yes")));
        Component noBtn = Component.translatable("message.skyabyss.route.abandon_no")
                .withStyle(Style.EMPTY.withColor(0xD9779A).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyroute no")));
        for (ServerPlayer p : members(back, group)) {
            if (!p.getUUID().equals(by.getUUID())) {
                p.sendSystemMessage(Component.empty().append(ask).append(" ").append(yesBtn).append(" ").append(noBtn));
                Notice.bar(p, ask);
            }
        }
        Notice.hint(by, "message.skyabyss.route.abandon_proposed");
        return true;
    }

    // /skyroute yes|no
    public static boolean reply(ServerPlayer p, boolean yes) {
        if (!(p.level() instanceof ServerLevel back)) {
            return false;
        }
        UUID group = Groups.keyOf(p);
        Abandon a = ABANDON.get(group);
        if (a == null) {
            Notice.hint(p, "message.skyabyss.route.abandon_none");
            return false;
        }
        if (!yes) {
            ABANDON.remove(group);
            for (ServerPlayer m : members(back, group)) {
                Notice.hint(m, "message.skyabyss.route.abandon_refused", p.getGameProfile().getName());
            }
            return true;
        }
        a.yes().add(p.getUUID());
        return settle(back, a);
    }

    // 全員が「はい」なら成立
    private static boolean settle(ServerLevel back, Abandon a) {
        List<ServerPlayer> all = members(back, a.group());
        for (ServerPlayer m : all) {
            if (!a.yes().contains(m.getUUID())) {
                return false;
            }
        }
        ABANDON.remove(a.group());
        abandon(back, a.group(), a.titan());
        return true;
    }

    // 順路を終え、その背にいるグループ全員を背の外へ出す (地上へ降ろす。地上の着地は Titans.sendDown)
    public static int abandon(ServerLevel back, UUID group, String titan) {
        List<ServerPlayer> all = members(back, group);
        finish(back, group);
        for (ServerPlayer m : all) {
            Notice.narrate(m, "message.skyabyss.route.abandoned");
            try {
                Titans.sendDown(m, titan);
            } catch (RuntimeException e) {
                // 通信のない偽の人 (自己テスト) など。ほかの人は続けて降ろす
                SkyAbyss.LOGGER.warn("Route abandon: could not send {} down: {}", m.getGameProfile().getName(), e.toString());
            }
        }
        SkyAbyss.LOGGER.info("Route of the {} abandoned by group {} ({} sent down)", titan, group, all.size());
        return all.size();
    }

    private static void tickAbandon(ServerLevel back, long now) {
        for (Abandon a : new ArrayList<>(ABANDON.values())) {
            if (Titans.backOf(a.titan()) != back.dimension()) {
                continue;
            }
            if (now >= a.until()) {
                ABANDON.remove(a.group());
                for (ServerPlayer m : members(back, a.group())) {
                    Notice.hint(m, "message.skyabyss.route.abandon_expired");
                }
            }
        }
    }

    // ---------------------------------------------------------------- 自己テスト・開発用
    public static int pendingCount() {
        return PENDING.size();
    }

    public static boolean abandonPending(UUID group) {
        return ABANDON.containsKey(group);
    }

    public static void clearAll() {
        PENDING.clear();
        ABANDON.clear();
        ABANDON_LAST.clear();
        ENTRY_START.clear();
    }

    public static List<ServerPlayer> snapshot(ServerLevel back) {
        return Players.snapshot(back.players());
    }
}

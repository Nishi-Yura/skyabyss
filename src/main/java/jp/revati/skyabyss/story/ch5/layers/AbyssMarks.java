package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

// 層の印・門・近道・上り下りの鎖 (docs/design/36 の 8 章)。
//   印の台 (mark_<層>_<i>。灰の骨の台): 右クリックで印を取る。巣・縄張りの道 (REQUIRE) は払ってから。取ると本人とパーティの全員が持つ
//     (記録 LayerData の "mark:<層>_<i>"、合図 abyss_mark_<層>_<i> をパーティへ)。はじめて取られた印の近道の格子 (scut_<層>_<i>) が開く (ワールドで共有)
//   門 (gate_<層>。野営の北の格子の門): 印が三つの人 (またはメインの段が印の段を越えた人) が 5 以内に来ると格子が上がる (gateTick)。
//     その人が離れて 3 秒たち、門の列にだれもいなければ下りる。右クリックでも向こうへ運ぶ。内からはだれでも野営へ戻れる
//     (2026-10-23 の試遊「印をおいても鉄格子のところがあかない」: 前は封じ石を右クリックしたときだけ運んでいて、見た目は開かなかった)
//   下りの鎖 (descend_<層>。主の間の奥): 主を倒した人だけ、次の層の野営 (arrive_<次>。灰の次は心臓の控えの間 heart_in) へ
//   上りの鎖 (ascend_<層>。野営の南西。骨の層は地上への鎖 exit で AbyssGate の係): 前の層の野営へ。心臓の控えの間の ascend_heart は灰の野営へ
//   門の上の三つの台座 (socket_<層>_<1..3>) は、持つ印の数だけ本人にだけ光る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssMarks {
    public static final String SIGNAL_MARK = "abyss_mark_";
    // 印を取る前に払う物 (人ごとの記録)
    //   形 B (塩・灰。AbyssEscort) の印は、その待ち場を確かにした人 (とパーティ) だけ ("clear:wp_<層>_<k>")
    static final Map<String, String> REQUIRE = Map.of("bone_2", "clear:nest_bone", "storm_3", "clear:nest_storm",
            "salt_1", "clear:wp_salt_1", "salt_2", "clear:wp_salt_2", "salt_3", "clear:wp_salt_3",
            "ash_1", "clear:wp_ash_1", "ash_2", "clear:wp_ash_2", "ash_3", "clear:wp_ash_3");
    static final DustParticleOptions SOCKET = new DustParticleOptions(new Vector3f(0.55f, 0.9f, 1f), 1.2f);

    private AbyssMarks() {
    }

    // ---------------------------------------------------------------- 印
    public static boolean hasMark(ServerPlayer p, String layer, int i) {
        return LayerData.get(p.server).has(p.getUUID(), "mark:" + layer + "_" + i) || Layers.pastMarks(p, layer);
    }

    public static int count(ServerPlayer p, String layer) {
        if (Layers.pastMarks(p, layer)) {
            return 3;
        }
        return LayerData.get(p.server).marks(p.getUUID(), layer);
    }

    // 形 B は印三つに加えて、連れて下りる人を門の前まで送ったこと (AbyssEscort)
    public static boolean canPassGate(ServerPlayer p, String layer) {
        if (AbyssEscort.expedition(layer)) {
            return Layers.pastMarks(p, layer) || count(p, layer) >= 3 && LayerData.get(p.server).has(p.getUUID(), "escort:" + layer);
        }
        return count(p, layer) >= 3;
    }

    static void closedHint(ServerPlayer p, String layer) {
        if (AbyssEscort.expedition(layer) && count(p, layer) >= 3) {
            Notice.hint(p, "message.skyabyss.abyss_v2.gate_closed_escort", AbyssEscort.npcName(layer));
        } else {
            Notice.hint(p, "message.skyabyss.abyss_v2.gate_closed", count(p, layer));
        }
    }

    public static boolean lordBeaten(ServerPlayer p, String layer) {
        return LayerData.get(p.server).has(p.getUUID(), "lord:" + layer) || Layers.pastLord(p, layer);
    }

    // 印を取る (台で・開発用)。取れたら true。払う物が残っていれば知らせて false
    public static boolean take(ServerPlayer p, String layer, int i) {
        String key = layer + "_" + i;
        LayerData d = LayerData.get(p.server);
        String need = REQUIRE.get(key);
        if (need != null && !d.has(p.getUUID(), need)) {
            Notice.hint(p, "message.skyabyss.abyss_v2.mark_guarded." + need.substring(need.indexOf(':') + 1));
            return false;
        }
        if (d.has(p.getUUID(), "mark:" + key)) {
            // 仲間が取った印でも、近道の格子は開ける (開いていなかった時の直し)
            openShortcut(p.serverLevel(), layer, i);
            Notice.bar(p, Component.translatable("message.skyabyss.abyss_v2.mark_have", count(p, layer)));
            return false;
        }
        ServerLevel level = p.serverLevel();
        // 初めて記した人に経験値 (人ごとに一度。取った人は満額、オンラインの仲間は分け前)。印は仲間と共有なので、記す前に数える
        firstXp(level, List.of(p), lacking(p, "mark:" + key), MARK_XP, "growth.skyabyss.reason.abyss_mark");
        for (UUID who : withParty(p)) {
            d.set(who, "mark:" + key);
        }
        Quests.signalWithParty(level, List.of(p), SIGNAL_MARK + key);
        int n = count(p, layer);
        Notice.gain(p, Component.translatable("message.skyabyss.abyss_v2.mark_got", Component.translatable("abyss_v2.skyabyss.mark." + key), n));
        for (ServerPlayer mate : Parties.onlineMembers(p.server, Parties.of(p))) {
            if (mate != p) {
                Notice.hint(mate, "message.skyabyss.abyss_v2.mark_mate", p.getDisplayName(), Component.translatable("abyss_v2.skyabyss.mark." + key));
            }
        }
        BlockPos m = AbyssBuild.mark("mark_" + key);
        if (m != null) {
            level.playSound(null, m, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1f, 0.8f);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX() + 0.5, m.getY() + 1.1, m.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
        }
        openShortcut(level, layer, i);
        if (n >= 3 && !AbyssEscort.expedition(layer)) {
            Notice.objective(p, "message.skyabyss.abyss_v2.marks_all");
        }
        return true;
    }

    // 本人とパーティの全員 (いない人も)
    // 印を初めて記したときの経験値 (層に 3 つ × 4 層。docs/design/36 の 4.2・8 章)
    public static final int MARK_XP = 60;

    // p とオンラインの仲間のうち、まだ flag を持っていない人
    static List<ServerPlayer> lacking(ServerPlayer p, String flag) {
        LayerData d = LayerData.get(p.server);
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : withParty(p)) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(u);
            if (m != null && !d.has(u, flag) && !out.contains(m)) {
                out.add(m);
            }
        }
        return out;
    }

    // 初めての経験値: present (その場にいた人) は満額、ほかの仲間は分け前 (Parties.shareRate)
    static void firstXp(ServerLevel level, List<ServerPlayer> present, List<ServerPlayer> who, int xp, String reason) {
        double rate = Parties.shareRate(level.getServer());
        for (ServerPlayer m : who) {
            int amount = present.contains(m) ? xp : (int) Math.round(xp * rate);
            if (amount > 0) {
                jp.revati.skyabyss.rpg.Growth.addXp(m, amount, reason);
            }
        }
    }

    static List<UUID> withParty(ServerPlayer p) {
        List<UUID> out = new ArrayList<>();
        out.add(p.getUUID());
        PartyData.Party party = Parties.of(p);
        if (party != null) {
            for (UUID u : party.members) {
                if (!out.contains(u)) {
                    out.add(u);
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 近道
    public static void openShortcut(ServerLevel level, String layer, int i) {
        String key = "scut_" + layer + "_" + i;
        LayerData d = LayerData.get(level.getServer());
        boolean first = d.setOpen(key);
        clearBox(level, key, Blocks.IRON_BARS);
        if (first) {
            BlockPos a = AbyssBuild.mark(key + "_a");
            if (a != null) {
                level.playSound(null, a, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.2f, 0.7f);
            }
        }
    }

    // 箱の印の中の、その種のブロックを空気に (only = null なら全部)
    static int clearBox(ServerLevel level, String name, @Nullable Block only) {
        BlockPos a = AbyssBuild.mark(name + "_a");
        BlockPos b = AbyssBuild.mark(name + "_b");
        if (a == null || b == null || !level.isLoaded(a) || !level.isLoaded(b)) {
            return 0;
        }
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            var st = level.getBlockState(q);
            if (!st.isAir() && (only == null || st.is(only))) {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 右クリック
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (use(p, event.getPos())) {
            event.setCanceled(true);
        }
    }

    // 試せる形: 右クリックした所 (使ったら true)
    public static boolean use(ServerPlayer p, BlockPos at) {
        for (String layer : Layers.ORDER) {
            for (int i = 1; i <= 3; i++) {
                BlockPos m = AbyssBuild.mark("mark_" + layer + "_" + i);
                if (m != null && (at.equals(m) || at.equals(m.below()))) {
                    take(p, layer, i);
                    return true;
                }
            }
            BlockPos g = AbyssBuild.mark("gate_" + layer);
            if (g != null && Math.abs(at.getX() - g.getX()) <= 1 && at.getY() >= g.getY() && at.getY() <= g.getY() + 2
                    && (at.getZ() == g.getZ() || at.getZ() == g.getZ() - 1)) {
                gate(p, layer);
                return true;
            }
            if (chain(at, "descend_" + layer, 10)) {
                descend(p, layer);
                return true;
            }
            if (chain(at, "back_" + layer, 6)) {
                go(p, "arrive_" + layer, "message.skyabyss.abyss_v2.back");
                return true;
            }
            if (chain(at, "ascend_" + layer, 7)) {
                String prev = Layers.prev(layer);
                if (prev != null) {
                    go(p, "arrive_" + prev, "message.skyabyss.abyss_v2.ascend");
                }
                return true;
            }
        }
        if (chain(at, "ascend_heart", 6)) {
            go(p, "arrive_ash", "message.skyabyss.abyss_v2.ascend");
            return true;
        }
        return false;
    }

    static boolean chain(BlockPos at, String mark, int up) {
        BlockPos m = AbyssBuild.mark(mark);
        return m != null && at.getX() == m.getX() && at.getZ() == m.getZ() && at.getY() >= m.getY() && at.getY() <= m.getY() + up;
    }

    // 門: 野営の側からは印が三つで向こうへ、向こうの側からは野営へ
    public static boolean gate(ServerPlayer p, String layer) {
        BlockPos g = AbyssBuild.mark("gate_" + layer);
        if (g == null) {
            return false;
        }
        boolean campSide = p.getZ() > g.getZ() + 0.5;
        if (!campSide) {
            go(p, "gate_" + layer + "_out", AbyssEscort.expedition(layer) ? "message.skyabyss.abyss_v2.gate_back_b" : "message.skyabyss.abyss_v2.gate_back");
            return true;
        }
        if (!canPassGate(p, layer)) {
            closedHint(p, layer);
            p.serverLevel().playSound(null, g, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.8f, 0.6f);
            return false;
        }
        p.serverLevel().playSound(null, g, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1f, 0.7f);
        go(p, "gate_" + layer + "_in", "message.skyabyss.abyss_v2.gate_open");
        return true;
    }

    public static boolean descend(ServerPlayer p, String layer) {
        if (!lordBeaten(p, layer)) {
            Notice.hint(p, "message.skyabyss.abyss_v2.descend_closed");
            return false;
        }
        String next = Layers.next(layer);
        go(p, next == null ? "heart_in" : "arrive_" + next, "message.skyabyss.abyss_v2.descend");
        return true;
    }

    // 印の所へ運ぶ (同じ深淵の中)。向きは北 (野営の真ん中・門の方)
    static void go(ServerPlayer p, String mark, @Nullable String line) {
        BlockPos m = AbyssBuild.mark(mark);
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        if (m == null || level == null) {
            return;
        }
        p.teleportTo(level, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, 180f, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(m.getX() + 0.5, m.getY(), m.getZ() + 0.5, 180f, 0f);
        }
        if (line != null) {
            Notice.bar(p, Component.translatable(line));
        }
    }

    // ---------------------------------------------------------------- 格子の門
    static final double GATE_NEAR = 5.0;
    static final int GATE_LINGER = 60;
    private static final Map<String, Long> GATE_UNTIL = new java.util.HashMap<>();
    private static final Map<UUID, Long> GATE_HINTED = new java.util.HashMap<>();

    // 門の格子のマス (幅 3・高さ 3)
    static List<BlockPos> gateBars(BlockPos g) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                out.add(g.offset(dx, dy, 0));
            }
        }
        return out;
    }

    // 門の列とその奥 (人が立っていれば下ろさない)
    static boolean inGateway(BlockPos g, BlockPos at) {
        return Math.abs(at.getX() - g.getX()) <= 1 && at.getY() >= g.getY() - 1 && at.getY() <= g.getY() + 2 && (at.getZ() == g.getZ() || at.getZ() == g.getZ() - 1);
    }

    public static boolean gateRaised(ServerLevel level, String layer) {
        BlockPos g = AbyssBuild.mark("gate_" + layer);
        return g != null && level.isLoaded(g) && !level.getBlockState(g.above()).is(Blocks.IRON_BARS);
    }

    // 10 tick ごと: 印が三つの人が近ければ上げる。だれもいなくなって GATE_LINGER たてば下ろす
    static void gateTick(ServerLevel level, long now) {
        gateTick(level, now, level.players());
    }

    static void gateTick(ServerLevel level, long now, List<ServerPlayer> players) {
        for (String layer : Layers.ORDER) {
            BlockPos g = AbyssBuild.mark("gate_" + layer);
            if (g == null || !level.isLoaded(g)) {
                continue;
            }
            boolean want = false;
            boolean occupied = false;
            for (ServerPlayer p : players) {
                if (p.isSpectator() || !p.isAlive()) {
                    continue;
                }
                BlockPos at = p.blockPosition();
                if (inGateway(g, at)) {
                    occupied = true;
                }
                boolean near = Math.abs(at.getY() - g.getY()) <= 4 && Math.hypot(p.getX() - (g.getX() + 0.5), p.getZ() - (g.getZ() + 0.5)) <= GATE_NEAR;
                if (!near) {
                    continue;
                }
                if (canPassGate(p, layer)) {
                    want = true;
                } else if (p.getZ() > g.getZ() + 0.5 && Math.hypot(p.getX() - (g.getX() + 0.5), p.getZ() - (g.getZ() + 0.5)) <= 2.6) {
                    Long last = GATE_HINTED.get(p.getUUID());
                    if (last == null || now - last > 200) {
                        GATE_HINTED.put(p.getUUID(), now);
                        closedHint(p, layer);
                    }
                }
            }
            boolean raised = gateRaised(level, layer);
            if (want) {
                GATE_UNTIL.put(layer, now + GATE_LINGER);
                if (!raised) {
                    setGate(level, g, true);
                }
            } else if (raised && !occupied && GATE_UNTIL.getOrDefault(layer, 0L) <= now) {
                setGate(level, g, false);
            }
        }
    }

    static void setGate(ServerLevel level, BlockPos g, boolean open) {
        for (BlockPos b : gateBars(g)) {
            if (open) {
                if (level.getBlockState(b).is(Blocks.IRON_BARS)) {
                    level.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.sendParticles(SOCKET, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 3, 0.25, 0.25, 0.1, 0);
                }
            } else if (level.getBlockState(b).isAir()) {
                level.setBlock(b, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (!open) {
            for (BlockPos b : gateBars(g)) {
                var st = level.getBlockState(b);
                if (st.is(Blocks.IRON_BARS)) {
                    level.setBlock(b, Block.updateFromNeighbourShapes(st, level, b), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.playSound(null, g, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.4f, 0.6f);
        if (open) {
            level.playSound(null, g, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1f, 0.7f);
        }
    }

    // ---------------------------------------------------------------- 見回り (台座の光・開いた近道を開いたままに)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 4 && server.getTickCount() % 20 != 9) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty() || !AbyssBuild.built(server)) {
            return;
        }
        if (server.getTickCount() % 10 == 4) {
            gateTick(level, level.getGameTime());
            return;
        }
        LayerData d = LayerData.get(server);
        for (ServerPlayer p : List.copyOf(level.players())) {
            int s = Layers.sectionAt(p.blockPosition());
            if (s >= Layers.HEART) {
                continue;
            }
            String layer = Layers.ORDER.get(s);
            if (Layers.inCamp(layer, p.blockPosition())) {
                // 開いた近道は、野営にだれかいる間は開いたまま (建て直し・読み込みの前に開けた分も)
                for (int i = 1; i <= 3; i++) {
                    if (d.isOpen("scut_" + layer + "_" + i)) {
                        clearBox(level, "scut_" + layer + "_" + i, Blocks.IRON_BARS);
                    }
                }
                int n = count(p, layer);
                for (int i = 1; i <= 3; i++) {
                    BlockPos so = AbyssBuild.mark("socket_" + layer + "_" + i);
                    if (so != null && i <= n) {
                        level.sendParticles(p, SOCKET, true, so.getX() + 0.5, so.getY() + 0.5, so.getZ() + 0.5, 4, 0.15, 0.15, 0.15, 0);
                    }
                }
            }
            // 取れる印の台に、ほのかな魂の火 (本人だけ)
            for (int i = 1; i <= 3; i++) {
                BlockPos m = AbyssBuild.mark("mark_" + layer + "_" + i);
                if (m != null && m.closerThan(p.blockPosition(), 24) && !d.has(p.getUUID(), "mark:" + layer + "_" + i)) {
                    level.sendParticles(p, ParticleTypes.SOUL_FIRE_FLAME, true, m.getX() + 0.5, m.getY() + 1.0, m.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
        if (server.getTickCount() % 200 == 9) {
            for (String key : List.copyOf(d.open)) {
                if (key.startsWith("scut_")) {
                    clearBox(level, key, Blocks.IRON_BARS);
                }
            }
        }
    }
}

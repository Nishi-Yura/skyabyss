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
//   門 (gate_<層>。野営の北の封じ石): 印が三つの人 (またはメインの段が印の段を越えた人) だけ、右クリックで向こうへ通る。内からはだれでも野営へ戻れる
//   下りの鎖 (descend_<層>。主の間の奥): 主を倒した人だけ、次の層の野営 (arrive_<次>。灰の次は心臓の控えの間 heart_in) へ
//   上りの鎖 (ascend_<層>。野営の南西。骨の層は地上への鎖 exit で AbyssGate の係): 前の層の野営へ。心臓の控えの間の ascend_heart は灰の野営へ
//   門の上の三つの台座 (socket_<層>_<1..3>) は、持つ印の数だけ本人にだけ光る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssMarks {
    public static final String SIGNAL_MARK = "abyss_mark_";
    // 印を取る前に払う物 (人ごとの記録)
    static final Map<String, String> REQUIRE = Map.of("bone_2", "clear:nest_bone", "salt_3", "clear:terr_salt", "storm_3", "clear:nest_storm",
            "ash_3", "clear:terr_ash");
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

    public static boolean canPassGate(ServerPlayer p, String layer) {
        return count(p, layer) >= 3;
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
            Notice.bar(p, Component.translatable("message.skyabyss.abyss_v2.mark_have", count(p, layer)));
            return false;
        }
        for (UUID who : withParty(p)) {
            d.set(who, "mark:" + key);
        }
        ServerLevel level = p.serverLevel();
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
        if (n >= 3) {
            Notice.objective(p, "message.skyabyss.abyss_v2.marks_all");
        }
        return true;
    }

    // 本人とパーティの全員 (いない人も)
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
            go(p, "gate_" + layer + "_out", "message.skyabyss.abyss_v2.gate_back");
            return true;
        }
        int n = count(p, layer);
        if (n < 3) {
            Notice.hint(p, "message.skyabyss.abyss_v2.gate_closed", n);
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
        if (line != null) {
            Notice.bar(p, Component.translatable(line));
        }
    }

    // ---------------------------------------------------------------- 見回り (台座の光・開いた近道を開いたままに)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 9) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty() || !AbyssBuild.built(server)) {
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

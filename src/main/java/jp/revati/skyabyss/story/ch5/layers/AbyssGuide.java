package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.warden.WardenArena;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 深淵の道案内 (2026-10-23 の試遊「深淵はまだまだクオリティが低すぎる。誘導等含め」)。
//   目的の帯: 深淵の中ではいつも、次に行く所を一言と矢印・距離で出す (AbyssSections が Section の payload で送る。戦いの一言があればそちら)
//     層の中: 生き残りと話す → 印を探す (近い順。巣・縄張りに守られていれば先に払う) → 北の格子の門 → 主の間 → 下りの鎖で次の野営
//     心臓:   心臓の間へ → 番人 → 心臓の台 → 地上へ (骨の野営の白い磁石石)
//   道の碑: 野営の道の口の碑を右クリックすると、その道の様子 (印を取ったか・守りの巣) を知らせる
//   生き残りの一言: 野営で生き残りの 6 以内に来ると、今の段に合う一言を一度ずつ (印の探し方・巣・門・主の倒し方)
//   はじめての手引き: 深淵の中の出来事ごとに一度だけ (帰りの磁石石・三本の道と印・近道・碑・主の間)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssGuide {
    public static final String NS = "message.skyabyss.abyss_v3.";
    static final String GUIDE = "guide.skyabyss.abyss_v3.";
    static final String[] SURVIVOR = {"survivor_bone", "survivor_salt", "survivor_storm", "survivor_ash"};

    // 道案内の一行: 翻訳キー・引数 (先頭 "#" は文字のまま)・行き先
    public record Line(String key, List<String> args, @Nullable BlockPos target) {
        static Line of(String key, @Nullable BlockPos target, String... args) {
            return new Line(GUIDE + key, List.of(args), target);
        }
    }

    private static final Map<UUID, String> SAID = new HashMap<>();

    private AbyssGuide() {
    }

    // ---------------------------------------------------------------- 道案内 (純粋に近い計算。記録と印から)
    public static Line lineFor(ServerPlayer p) {
        BlockPos at = p.blockPosition();
        int s = Layers.sectionAt(at);
        if (s >= Layers.HEART) {
            return heart(p);
        }
        String layer = Layers.ORDER.get(s);
        BlockPos gate = AbyssBuild.mark("gate_" + layer);
        boolean exp = AbyssEscort.expedition(layer);
        // 形 B の格子の門は下り道の終わり (主の間の前)。門の向こうは主の間とその手前の通り道だけ
        boolean beyondGate = gate != null && at.getZ() < gate.getZ() && !Layers.inCamp(layer, at)
                && (!exp || Layers.inArena(layer, at) || Math.abs(at.getX() - gate.getX()) <= 2 && at.getZ() > gate.getZ() - 4);
        if (AbyssMarks.lordBeaten(p, layer)) {
            String next = Layers.next(layer);
            return Line.of(next == null ? "descend_heart" : "descend", AbyssBuild.mark("descend_" + layer),
                    next == null ? "chapter.skyabyss.abyss_v2.heart.title" : "chapter.skyabyss.abyss_v2." + next + ".title");
        }
        if (AbyssMarks.canPassGate(p, layer)) {
            if (Layers.inArena(layer, at)) {
                return Line.of("lord", AbyssBuild.mark("arena_" + layer), "abyss_v2.skyabyss.lord." + layer);
            }
            if (beyondGate) {
                return Line.of("arena", AbyssBuild.mark("arena_" + layer), "abyss_v2.skyabyss.lord." + layer);
            }
            return Line.of(exp ? "gate_b" : "gate", gate);
        }
        if ((layer + "_camp").equals(Layers.stage(p))) {
            AbyssEscort.Run r = AbyssEscort.run(layer);
            if (r != null && "ash".equals(layer)) {
                // 灰の隊長は道に出ている: 道の人のところへ (加わると話したことになる)
                return Line.of("survivor_road", BlockPos.containing(r.pos()), "entity.skyabyss.attendant.survivor_" + layer);
            }
            return Line.of("survivor", AbyssBuild.mark("survivor_" + layer), "entity.skyabyss.attendant.survivor_" + layer);
        }
        if (exp) {
            return escortLine(p, layer);
        }
        int have = AbyssMarks.count(p, layer);
        int best = nextMark(p, layer);
        if (best < 0) {
            return Line.of("gate", gate);
        }
        String key = layer + "_" + best;
        String need = AbyssMarks.REQUIRE.get(key);
        LayerData d = LayerData.get(p.server);
        BlockPos mark = AbyssBuild.mark("mark_" + key);
        if (need != null && !d.has(p.getUUID(), need)) {
            String place = need.substring(need.indexOf(':') + 1);
            BlockPos guard = AbyssBuild.mark(place);
            return Line.of(place.startsWith("nest") ? "nest" : "terr", guard != null ? guard : mark, "abyss_v2.skyabyss.mark." + key, "#" + have);
        }
        return Line.of("mark", mark, "abyss_v2.skyabyss.mark." + key, "#" + have);
    }

    // 形 B: 護りの下り道の様子 (AbyssEscort)
    static Line escortLine(ServerPlayer p, String layer) {
        AbyssEscort.Run r = AbyssEscort.run(layer);
        String npc = "entity.skyabyss.attendant." + AbyssEscort.ROLE.get(layer);
        String have = "#" + AbyssMarks.count(p, layer);
        if (r == null) {
            return Line.of("escort_meet", AbyssBuild.mark("escstart_" + layer), npc);
        }
        BlockPos np = BlockPos.containing(r.pos());
        return switch (r.state()) {
            case WALK -> Line.of("escort_follow", np, npc, have);
            case WAIT -> switch (r.task()) {
                case STAKE -> Line.of("escort_stake", AbyssBuild.mark("stake_salt"), npc);
                case BEACON -> Line.of("escort_beacon_" + layer, AbyssBuild.mark("beacon_" + layer), npc);
                case HOLD -> Line.of("escort_hold", np, npc, "#" + Math.max(0, (r.holdUntil - p.level().getGameTime() + 19) / 20));
                case SEAL -> Line.of("escort_seal", AbyssBuild.mark("seal_ash"), npc);
                case CLEAR -> Line.of("escort_clear", AbyssBuild.mark("wp_" + layer + "_3"), npc);
            };
            case ARMED -> Line.of("escort_ready", np, npc);
            case ESCAPE -> Line.of("escort_run_" + layer, AbyssBuild.mark("gate_" + layer), npc);
            case DONE -> Line.of("gate_b", AbyssBuild.mark("gate_" + layer));
        };
    }

    // まだ取っていない印のうち、いちばん近いもの (無ければ -1)
    static int nextMark(ServerPlayer p, String layer) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 1; i <= 3; i++) {
            if (AbyssMarks.hasMark(p, layer, i)) {
                continue;
            }
            BlockPos m = AbyssBuild.mark("mark_" + layer + "_" + i);
            if (m == null) {
                continue;
            }
            double dd = m.distSqr(p.blockPosition());
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        return best;
    }

    static Line heart(ServerPlayer p) {
        BlockPos heart = AbyssBuild.mark("heart");
        String st = Layers.stage(p);
        if ("descend".equals(st)) {
            return Line.of("heart_room", heart);
        }
        if ("warden".equals(st) || (!WardenArena.cleared(p.server) && WardenArena.current(p.serverLevel()) != null)) {
            return Line.of("warden", heart);
        }
        if ("heart".equals(st)) {
            return Line.of("heart_touch", heart);
        }
        return Line.of("home", AbyssBuild.mark("ascend_heart"));
    }

    // ---------------------------------------------------------------- はじめての手引きと生き残りの一言 (AbyssSections の見回りから 0.5 秒ごと)
    static void tick(ServerPlayer p) {
        BlockPos at = p.blockPosition();
        int s = Layers.sectionAt(at);
        if (s >= Layers.HEART) {
            return;
        }
        String layer = Layers.ORDER.get(s);
        boolean camp = Layers.inCamp(layer, at);
        if (camp && "bone".equals(layer)) {
            tip(p, "home");
        }
        boolean exp = AbyssEscort.expedition(layer);
        if (camp && exp) {
            tip(p, "escort");
        } else if (camp) {
            tip(p, "board");
        }
        if (!exp && !camp && !Layers.inArena(layer, at) && AbyssMarks.count(p, layer) == 0 && LayerData.get(p.server).has(p.getUUID(), "tip:board")) {
            tip(p, "marks");
        }
        if (!exp && AbyssMarks.count(p, layer) >= 1) {
            tip(p, "shortcut");
        }
        if (Layers.inArena(layer, at)) {
            tip(p, "arena");
        }
        // 生き残りの一言
        BlockPos sv = AbyssBuild.mark(SURVIVOR[s]);
        if (sv != null && camp && sv.closerThan(at, 6)) {
            String state = survivorState(p, layer);
            String key = layer + "." + state;
            if (!"none".equals(state) && !key.equals(SAID.get(p.getUUID())) && LayerData.get(p.server).set(p.getUUID(), "said:" + key)) {
                SAID.put(p.getUUID(), key);
                Notice.narrate(p, NS + "survivor." + key);
            }
        }
    }

    // 生き残りが言うこと: 段に合わせて (話す前は言わない。会話が先)
    static String survivorState(ServerPlayer p, String layer) {
        if ((layer + "_camp").equals(Layers.stage(p))) {
            return "none";
        }
        if (AbyssMarks.lordBeaten(p, layer)) {
            return "after";
        }
        if (AbyssMarks.canPassGate(p, layer)) {
            return "lord";
        }
        if (AbyssEscort.expedition(layer)) {
            return "marks";
        }
        int best = nextMark(p, layer);
        if (best > 0) {
            String need = AbyssMarks.REQUIRE.get(layer + "_" + best);
            if (need != null && !LayerData.get(p.server).has(p.getUUID(), need)) {
                return "guard";
            }
        }
        return "marks";
    }

    static void tip(ServerPlayer p, String name) {
        if (LayerData.get(p.server).set(p.getUUID(), "tip:" + name)) {
            Notice.hint(p, NS + "tip." + name);
        }
    }

    // ---------------------------------------------------------------- 道の碑 (右クリックで道の様子)
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        for (String layer : Layers.ORDER) {
            for (int i = 1; i <= 3; i++) {
                BlockPos m = AbyssBuild.mark("routesign_" + layer + "_" + i);
                if (m != null && m.equals(event.getPos())) {
                    board(p, layer, i);
                    return;
                }
            }
        }
    }

    // 道の様子: 印を取ったか、守りの巣、近道
    public static Component boardLine(ServerPlayer p, String layer, int i) {
        String key = layer + "_" + i;
        Component name = Component.translatable("abyss_v2.skyabyss.mark." + key);
        if (AbyssMarks.hasMark(p, layer, i)) {
            return Component.translatable(NS + "board.done", name);
        }
        String need = AbyssMarks.REQUIRE.get(key);
        if (need != null && !LayerData.get(p.server).has(p.getUUID(), need)) {
            return Component.translatable(NS + "board.guarded", name, Component.translatable(NS + "guard." + need.substring(need.indexOf(':') + 1)));
        }
        return Component.translatable(NS + "board.open", name);
    }

    static void board(ServerPlayer p, String layer, int i) {
        List<Component> lines = new ArrayList<>();
        if (AbyssEscort.expedition(layer)) {
            // 形 B: 下り口の碑に、三つの待ち場の様子
            for (int k = 1; k <= 3; k++) {
                Component name = Component.translatable("abyss_v2.skyabyss.mark." + layer + "_" + k);
                lines.add(Component.translatable(NS + (AbyssMarks.hasMark(p, layer, k) ? "board_b.done" : "board_b.open"), name));
            }
        } else {
            lines.add(boardLine(p, layer, i));
        }
        Notice.hint(p, NS + "board.head", Component.translatable("sign.skyabyss.abyss_route." + layer + "_" + i + ".1"),
                Component.translatable("sign.skyabyss.abyss_route." + layer + "_" + i + ".2"), AbyssMarks.count(p, layer));
        for (Component c : lines) {
            Notice.send(p, Notice.Kind.HINT, c);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SAID.remove(event.getEntity().getUUID());
    }

}

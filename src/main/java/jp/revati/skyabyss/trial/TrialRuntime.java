package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.ResonanceStoneBlock;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.TrialSteles;
import jp.revati.skyabyss.titan.vow.Vows;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 試練のプールを動かす (docs/design/29)。背中のディメンションで 5 tick ごとに:
//   1. 動いているグループごとに、いまの周の引き (TrialData.current) を見て、入口の戸をグループにだけ開け閉めする (40 tick ごと)
//   2. 引いた試練 (と今週の試練) のセルの中にグループの人がいれば、セルを動かす (arm → tick)。誰もいなくなって 5 秒で disarm
//   3. セルの石の差し口 (useStone)・仕掛けの右クリック・戻りの光る床・倒れた回数 (任意の目標) を扱う
// 原型の石は、TrialSteles.onRung から onStoneRung が呼ばれて、図鑑・報いに数える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TrialRuntime {
    private TrialRuntime() {
    }

    // 動かしているセル (グループ + 試練の名前)。メモリだけ
    private static final Map<String, TrialCell.Run> RUNS = new HashMap<>();
    // 5 秒 (100 tick) セルの外にいたら disarm
    private static final int DISARM_AFTER = 100;
    // 原型の石の hook を止める (新作の石が TrialSteles.onRung を通すとき、原型を二重に数えない)
    private static boolean suppressLegacy;

    static {
        GroupOverlays.register(TrialRuntime::overlay);
        // 鼓動の間の試練の碑 (越えた試練の碑がそのグループの画面で光る)
        TrialHub.register();
        BackGroupData.IGNORED_OVERRIDE = TrialRuntime::isEntranceKey;
    }

    private static java.util.Set<Long> entranceKeys;

    // 入口の戸 (引いた三つだけ開ける) の位置か。人が背にいるだけで戸の上書きが書かれるので、進みには数えない
    static synchronized boolean isEntranceKey(long key) {
        if (entranceKeys == null) {
            java.util.Set<Long> set = new java.util.HashSet<>();
            for (TrialCell c : TrialCells.all()) {
                for (BlockPos p : c.ports().entrance()) {
                    set.add(p.asLong());
                }
            }
            entranceKeys = set;
        }
        return entranceKeys.contains(key);
    }

    private static String key(UUID group, String id) {
        return group + ":" + id;
    }

    public static TrialCell.Run runOf(UUID group, String id) {
        return RUNS.get(key(group, id));
    }

    public static List<TrialCell.Run> runsOf(UUID group) {
        List<TrialCell.Run> out = new ArrayList<>();
        for (TrialCell.Run r : RUNS.values()) {
            if (r.group.equals(group)) {
                out.add(r);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 戸
    // 原型の入口 (既定で開く) と新作の入口 (既定で閉じる) のブロックを置く。建てたあと・起動のあと・建て直しのあとに呼ぶ (何度でも)
    public static void installGates(ServerLevel back) {
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null || !TrialCatalog.deckPlaced(titan)) {
            return;
        }
        for (TrialCell cell : TrialCells.of(titan)) {
            TrialCell.Ports p = cell.ports();
            if (!p.entrance().isEmpty()) {
                GroupGates.place(back, p.entrance(), p.entranceDefaultOpen());
            }
            if (!p.exitGate().isEmpty()) {
                GroupGates.place(back, p.exitGate(), false);
            }
        }
    }

    // 引いた試練 (と今週の試練) の入口だけを、そのグループに開ける。ほかには閉じて見える
    public static void syncGates(ServerLevel back, UUID group, String titan, TrialDraw.Draw draw) {
        if (!TrialCatalog.deckPlaced(titan)) {
            return;
        }
        Def weekly = TrialData.weekly(titan, TrialData.worldSeed(back), TrialData.week());
        for (TrialCell cell : TrialCells.of(titan)) {
            TrialCell.Ports p = cell.ports();
            if (p.entrance().isEmpty()) {
                continue;
            }
            boolean want = draw.has(cell.def().id()) || weekly != null && weekly.id().equals(cell.def().id());
            String cellKey = draw.has(cell.def().id()) ? TrialRegions.keyOfDef(titan, cell.def(), draw) : null;
            if (want && cellKey != null && HubTablets.cleared(back, group, titan, cellKey)) {
                // 越えた試練 (版 22): 入口は試練済みの封印で閉じたまま (見かけは TrialHub.sealOverlay)
                GroupGates.seal(back, group, p.entrance());
            } else if (want) {
                GroupGates.open(back, group, p.entrance());
            } else {
                GroupGates.seal(back, group, p.entrance());
            }
            // 出口の戸は、そのセルが「済んだ」とみなせるあいだだけ開く (セルが決める)
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    private static boolean hasWork(String titan) {
        for (TrialCell c : TrialCells.of(titan)) {
            if (c.runtime() || !c.ports().entrance().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            onLevelTick0(event);
        } finally {
            jp.revati.skyabyss.dev.Prof.end("server.trialRuntime", prof);
        }
    }

    static void onLevelTick0(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || TrialBacks.of(Titans.titanOfBack(level.dimension())) == null) {
            return;
        }
        long now = level.getGameTime();
        if (now % 5 != 0) {
            return;
        }
        String titan = Titans.titanOfBack(level.dimension());
        if (titan == null || !hasWork(titan) || !TitanBackData.get(level).isBuilt()) {
            return;
        }
        Map<UUID, List<ServerPlayer>> byGroup = new HashMap<>();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isAlive() && !p.isSpectator()) {
                byGroup.computeIfAbsent(Groups.keyOf(p), k -> new ArrayList<>()).add(p);
            }
        }
        for (Map.Entry<UUID, List<ServerPlayer>> e : byGroup.entrySet()) {
            UUID group = e.getKey();
            List<ServerPlayer> members = e.getValue();
            TrialDraw.Draw draw = TrialData.current(level, group);
            if (now % 40 == 0) {
                syncGates(level, group, titan, draw);
                markSeen(level, members, draw);
            }
            for (TrialCell cell : liveCells(level, titan, draw)) {
                if (!cell.runtime()) {
                    continue;
                }
                List<ServerPlayer> inside = new ArrayList<>();
                for (ServerPlayer p : members) {
                    if (cell.ports().area().contains(p.position())) {
                        inside.add(p);
                    }
                }
                if (inside.isEmpty()) {
                    continue;
                }
                TrialCell.Run run = armed(level, group, cell, draw, now);
                run.lastPresent = now;
                if (!draw.has(cell.def().id())) {
                    // 引いていない試練 = 今週の試練 (版 22: 初めて入った人に一行)
                    for (ServerPlayer p : inside) {
                        TrialIntro.onWeeklyEntered(p);
                    }
                }
                cell.tick(level, run, inside, now);
                tickPad(level, run, inside);
                autoRing(level, group, cell, run, draw, inside, now);
            }
        }
        // 誰もいなくなったセルを止める
        for (TrialCell.Run run : new ArrayList<>(RUNS.values())) {
            if (now - run.lastPresent > DISARM_AFTER) {
                stop(level, run);
            }
        }
    }

    // いまそのグループに動かしてよいセル: 引いた三つ + 今週の試練
    private static List<TrialCell> liveCells(ServerLevel back, String titan, TrialDraw.Draw draw) {
        List<TrialCell> out = new ArrayList<>();
        for (TrialDraw.Pick p : draw.picks()) {
            TrialCell c = TrialCells.get(p.cellId());
            if (c != null) {
                out.add(c);
            }
        }
        Def weekly = TrialData.weekly(titan, TrialData.worldSeed(back), TrialData.week());
        if (weekly != null && !draw.has(weekly.id())) {
            TrialCell c = TrialCells.get(weekly.id());
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static Mods modsFor(ServerLevel back, UUID group, String titan, Def def, TrialDraw.Draw draw) {
        TrialDraw.Pick pick = draw.pick(def.id());
        if (pick != null) {
            return pick.mods();
        }
        // 今週の試練 (引いていない): 全グループに同じ修飾 (種 = 世界の種 + 週 + 巨神)
        long h = TrialDraw.seed(TrialData.worldSeed(back), new UUID(TrialData.week(), 0x7EEC), titan, 1);
        Mods weekly = TrialDraw.rollMods(def, new Random(h ^ def.id().hashCode()));
        if (jp.revati.skyabyss.abyss.Abyss.groupOn(back, group)) {
            weekly = TrialDraw.abyssify(def, weekly, new Random(h ^ def.id().hashCode() ^ 0xABE55L));
        }
        return weekly;
    }

    public static TrialCell.Run armed(ServerLevel back, UUID group, TrialCell cell, TrialDraw.Draw draw, long now) {
        String k = key(group, cell.def().id());
        TrialCell.Run run = RUNS.get(k);
        if (run != null) {
            return run;
        }
        String titan = cell.def().titan();
        Mods mods = modsFor(back, group, titan, cell.def(), draw);
        BackGroupData.Record r = GroupProgress.of(back, group);
        long seed = TrialDraw.seed(TrialData.worldSeed(back), group, titan, TrialData.cycle(r)) ^ cell.def().id().hashCode() ^ now;
        run = new TrialCell.Run(group, cell, mods, seed, now);
        run.cleared = TrialData.isCleared(r, cell.def().id()) && TrialBacks.of(titan).isRung(back, group, cell.def(), draw.has(cell.def().id()));
        RUNS.put(k, run);
        cell.arm(back, run);
        GroupOverlays.refresh(back, group);
        return run;
    }

    private static void stop(ServerLevel back, TrialCell.Run run) {
        RUNS.remove(key(run.group, run.cell.def().id()));
        run.cell.disarm(back, run);
        GroupOverlays.refresh(back, run.group);
    }

    // 試練が戻った (リセット) ・グループが去った: そのグループのセルを止める
    public static void stopGroup(ServerLevel back, UUID group) {
        for (TrialCell.Run run : runsOf(group)) {
            stop(back, run);
        }
    }

    private static void markSeen(ServerLevel back, List<ServerPlayer> members, TrialDraw.Draw draw) {
        TrialCodex codex = TrialCodex.get(back.getServer());
        for (ServerPlayer p : members) {
            for (TrialDraw.Pick pick : draw.picks()) {
                codex.markSeen(p.getUUID(), pick.cellId());
            }
        }
    }

    // 戻りの光る床: 終えたセルでは、立つと広間の休み場へ戻る
    private static void tickPad(ServerLevel back, TrialCell.Run run, List<ServerPlayer> inside) {
        BlockPos pad = run.cell.ports().pad();
        if (pad == null || !run.cleared) {
            return;
        }
        for (ServerPlayer p : inside) {
            if (p.blockPosition().getX() == pad.getX() && p.blockPosition().getZ() == pad.getZ() && p.blockPosition().getY() == pad.getY() + 1) {
                String rest = run.cell.ports().returnRest();
                if (!rest.isEmpty()) {
                    TrialBacks.of(run.cell.def().titan()).returnTo(p, rest);
                    back.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 見かけ (グループごとの上書き)
    private static void overlay(ServerLevel level, UUID group, java.util.function.BiConsumer<BlockPos, BlockState> out) {
        String titan = Titans.titanOfBack(level.dimension());
        TrialBacks.Back backKind = TrialBacks.of(titan);
        if (backKind == null) {
            return;
        }
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        if (r == null) {
            return;
        }
        TrialDraw.Draw draw = r.has(TrialData.DRAW) ? TrialDraw.Draw.decode(r.getString(TrialData.DRAW)) : null;
        // 石: 鳴らしたグループにだけ光る
        for (TrialCell cell : TrialCells.of(titan)) {
            BlockPos stone = cell.ports().stone();
            if (!cell.runtime() || stone == null || draw == null) {
                continue;
            }
            if (backKind.isRung(level, group, cell.def(), draw.has(cell.def().id()))) {
                out.accept(stone, backKind.lit());
            }
        }
        for (TrialCell.Run run : runsOf(group)) {
            run.cell.overlay(level, run, out);
        }
    }

    public static void refresh(ServerLevel back, TrialCell.Run run) {
        GroupOverlays.refresh(back, run.group);
    }

    // ---------------------------------------------------------------- 右クリック (仕掛け)
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel back) || TrialBacks.of(Titans.titanOfBack(back.dimension())) == null
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        String backTitan = Titans.titanOfBack(back.dimension());
        for (TrialCell cell : TrialCells.of(backTitan)) {
            if (!cell.runtime() || !cell.interactive().contains(pos)) {
                continue;
            }
            UUID group = Groups.keyOf(sp);
            TrialDraw.Draw draw = TrialData.current(back, group);
            if (!liveCells(back, backTitan, draw).contains(cell)) {
                return;
            }
            long now = back.getGameTime();
            TrialCell.Run run = armed(back, group, cell, draw, now);
            run.lastPresent = now;
            if (cell.onUse(back, run, sp, pos, sp.isShiftKeyDown(), now)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
    }

    // 任意の目標「無傷で」: セルの中で倒れかけた人を数える (セルが読む)
    @SubscribeEvent
    static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel back) || TrialBacks.of(Titans.titanOfBack(back.dimension())) == null
                || event.getNewDamage() <= 0) {
            return;
        }
        UUID group = Groups.keyOf(sp);
        for (TrialCell.Run run : runsOf(group)) {
            if (run.cell.ports().area().contains(sp.position())) {
                run.put("hits", run.getInt("hits") + 1);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        RUNS.clear();
    }

    // ---------------------------------------------------------------- 石の差し口 (新作)
    // ResonanceStoneBlock から。新作の石なら扱って結果を返し、そうでなければ null
    @Nullable
    public static InteractionResult useStone(ServerLevel back, ServerPlayer sp, BlockPos pos) {
        TrialCell cell = TrialCells.atStone(pos);
        if (cell == null) {
            return null;
        }
        String titan = cell.def().titan();
        TrialBacks.Back kind = TrialBacks.of(titan);
        if (kind == null || !titan.equals(Titans.titanOfBack(back.dimension()))) {
            return null;
        }
        UUID group = Groups.keyOf(sp);
        TrialDraw.Draw draw = TrialData.current(back, group);
        boolean drawn = draw.has(cell.def().id());
        Def weekly = TrialData.weekly(titan, TrialData.worldSeed(back), TrialData.week());
        boolean isWeekly = weekly != null && weekly.id().equals(cell.def().id());
        if (!drawn && !isWeekly) {
            Notice.bar(sp, ModText.deny("message.skyabyss.trial.stone_not_yours"));
            return InteractionResult.SUCCESS;
        }
        TrialCell.Run run = RUNS.get(key(group, cell.def().id()));
        if (run == null || !run.cleared) {
            Notice.bar(sp, ModText.deny("message.skyabyss.stone_silent"));
            return InteractionResult.SUCCESS;
        }
        if (kind.isRung(back, group, cell.def(), drawn)) {
            if (drawn) {
                kind.tellWho(sp, cell.def());
            }
            return InteractionResult.SUCCESS;
        }
        ringCell(back, group, cell, run, drawn, sp, pos, kind);
        return InteractionResult.SUCCESS;
    }

    // 石を鳴らして、試練を越えたことにする (石の右クリックと、自動 (autoRing) の両方)
    private static void ringCell(ServerLevel back, UUID group, TrialCell cell, TrialCell.Run run, boolean drawn, ServerPlayer by, BlockPos pos, TrialBacks.Back kind) {
        Mods mods = run.mods;
        kind.ring(back, group, cell.def(), drawn, by, pos);
        boolean goalDone = mods.goal() >= 0 && run.goalOk;
        onCellCleared(back, group, cell.def(), mods, goalDone, by);
        kind.afterCleared(back, group, cell.def(), drawn, by);
    }

    // 版 22: 試練を終えたら、石を押さなくても越えたことになる。セルが「終えた」(run.cleared) と、少し (AUTO_RING_DELAY tick。終わりの演出を見せる) 待って石が鳴り、
    // そのあとは石を押したときと同じ (TrialHub が 80 tick 後に、中にいる仲間を鼓動の間へ戻し、入口を試練済みの封印で閉じる)。石は押しても同じ (二重には数えない)
    public static final int AUTO_RING_DELAY = 30;

    private static void autoRing(ServerLevel back, UUID group, TrialCell cell, TrialCell.Run run, TrialDraw.Draw draw, List<ServerPlayer> inside, long now) {
        BlockPos stone = cell.ports().stone();
        if (!run.cleared || stone == null || inside.isEmpty()) {
            return;
        }
        TrialBacks.Back kind = TrialBacks.of(cell.def().titan());
        boolean drawn = draw.has(cell.def().id());
        if (kind == null || kind.isRung(back, group, cell.def(), drawn)) {
            return;
        }
        long at = run.getLong("autoRingAt");
        if (at == 0) {
            run.put("autoRingAt", Math.max(1L, now));
            return;
        }
        if (now - at < AUTO_RING_DELAY) {
            return;
        }
        ringCell(back, group, cell, run, drawn, inside.get(0), stone, kind);
    }

    // 石の知らせの数え (TrialSteles.onRung) が、原型の石として数えないようにして動かす (新作の石は自分で数える)
    public static void withLegacySuppressed(Runnable action) {
        suppressLegacy = true;
        try {
            action.run();
        } finally {
            suppressLegacy = false;
        }
    }

    // ---------------------------------------------------------------- 原型の石 (TrialSteles.onRung から)
    public static void onStoneRung(ServerLevel back, UUID group, String steleTitan, String trial, @Nullable ServerPlayer by) {
        if (suppressLegacy) {
            return;
        }
        Def def = TrialCatalog.legacyOfStone(steleTitan, trial);
        if (def == null) {
            return;
        }
        TrialDraw.Draw draw = TrialData.current(back, group);
        TrialDraw.Pick pick = draw.pick(def.id());
        onCellCleared(back, group, def, pick == null ? Mods.PLAIN : pick.mods(), false, by);
    }

    // ---------------------------------------------------------------- 済ませた: 図鑑・報い・称号
    public static void onCellCleared(ServerLevel back, UUID group, Def def, Mods mods, boolean goalDone, @Nullable ServerPlayer by) {
        BackGroupData.Record r = GroupProgress.of(back, group);
        TrialData.markCleared(r, def.id());
        TrialCodex codex = TrialCodex.get(back.getServer());
        long week = TrialData.week();
        Def weeklyDef = TrialData.weekly(def.titan(), TrialData.worldSeed(back), week);
        boolean isWeekly = weeklyDef != null && weeklyDef.id().equals(def.id());
        Set<String> modKeys = modKeys(def, mods);
        for (ServerPlayer p : Groups.online(back, group)) {
            if (p.isSpectator()) {
                continue;
            }
            UUID id = p.getUUID();
            boolean first = codex.markCleared(id, def.id(), modKeys, goalDone);
            int mats = TrialDraw.materialCount(def, mods, goalDone, false);
            boolean weeklyBonus = isWeekly && codex.weeklyDone(id, def.titan()) != week;
            ItemStack stack = new ItemStack(Vows.materialOf(def.titan()), mats + (weeklyBonus ? mats : 0));
            jp.revati.skyabyss.pouch.PouchRouting.placeBack(p, stack);
            Component name = Component.translatable(def.nameKey());
            Notice.gain(p, ModText.progress("message.skyabyss.trial.cleared", name, stack.getCount()));
            int shards = 0;
            if (first) {
                shards++;
            }
            if (weeklyBonus) {
                shards++;
                codex.setWeeklyDone(id, def.titan(), week);
                Notice.hint(p, "message.skyabyss.trial.weekly_done");
            }
            if ((mods.rarity() == Rarity.EXTREME && back.random.nextFloat() < 0.08f)
                    || (mods.rarity() == Rarity.ABYSS && back.random.nextFloat() < jp.revati.skyabyss.abyss.Abyss.SHARD_CHANCE)) {
                shards++;
            }
            if (shards > 0) {
                jp.revati.skyabyss.pouch.PouchRouting.placeBack(p, new ItemStack(Vows.resonanceShardOf(def.titan()), shards));
                Notice.gain(p, ModText.progress("message.skyabyss.trial.shard", shards));
            }
            // 技の修飾ルーン・属性の欠片 (item/extra/GearFreedomLoot)
            for (ItemStack extra : jp.revati.skyabyss.item.extra.GearFreedomLoot.trialDrops(def.titan(), first, mods.rarity() == Rarity.EXTREME, back.random)) {
                jp.revati.skyabyss.pouch.PouchRouting.placeBack(p, extra);
                Notice.gain(p, ModText.progress("message.skyabyss.trial.extra", extra.getHoverName()));
            }
            if (goalDone) {
                Notice.hint(p, "message.skyabyss.trial.goal_done");
            }
            if (first) {
                Notice.hint(p, "message.skyabyss.trial.first", name);
            }
            awardTitles(p, codex, def, mods);
        }
        // 転生者の結晶の出・導きの欠片 (未転生の仲間と遊んだとき)・仲間の経験値 (docs/design/31 の B05)
        jp.revati.skyabyss.rebirth.Rebirth.onTrialCleared(Groups.online(back, group));
    }

    // 出会った修飾の印 (図鑑の「出会った修飾」の数え)
    public static Set<String> modKeys(Def def, Mods mods) {
        Set<String> keys = new LinkedHashSet<>();
        Trial.Space sp = def.space();
        if (sp.layouts() > 1) {
            keys.add("layout:" + mods.layout());
        }
        for (int i = 0; i < sp.numbers().size(); i++) {
            keys.add("number:" + sp.numbers().get(i) + ":" + mods.number(i));
        }
        if (mods.enemy() >= 0 && sp.enemySets() > 1) {
            keys.add("enemy:" + mods.enemy());
        }
        for (int h : mods.hazards()) {
            keys.add("hazard:" + sp.hazards().get(h));
        }
        if (mods.goal() >= 0) {
            keys.add("goal:" + sp.goals().get(mods.goal()));
        }
        if (mods.extreme() != Trial.Extreme.NONE) {
            keys.add("extreme:" + mods.extreme().id());
        }
        return keys;
    }

    // 図鑑の「全部」の数 (その試練に出る修飾の印の総数)
    public static int modTotal(Def def) {
        Trial.Space sp = def.space();
        int n = sp.layouts() > 1 ? sp.layouts() : 0;
        n += sp.numbers().size() * 3;
        n += sp.enemySets() > 1 ? sp.enemySets() : 0;
        n += sp.hazards().size() + sp.goals().size() + sp.extremes().size();
        return n;
    }

    private static void awardTitles(ServerPlayer p, TrialCodex codex, Def def, Mods mods) {
        UUID id = p.getUUID();
        List<String> earned = new ArrayList<>();
        if (codex.totalCleared(id) >= 1) {
            earned.add("first");
        }
        boolean all = true;
        for (Def d : TrialCatalog.of(def.titan())) {
            if (codex.peek(id, d.id()).cleared == 0) {
                all = false;
                break;
            }
        }
        if (all) {
            earned.add("full." + def.titan());
        }
        if (codex.totalGoals(id) >= 10) {
            earned.add("goals10");
        }
        if (mods.rarity() == Rarity.EXTREME || mods.rarity() == Rarity.ABYSS) {
            earned.add("extreme");
        }
        if (mods.rarity() == Rarity.ABYSS) {
            earned.add("abyss");
        }
        for (String t : earned) {
            if (codex.addTitle(id, t)) {
                Notice.unlock(p, "message.skyabyss.trial.title", Component.translatable("trial.skyabyss.title." + t));
            }
        }
    }

    // ---------------------------------------------------------------- 心臓を止めた: 周を進める
    public static void onHeartStopped(ServerLevel back, java.util.Collection<UUID> fighters) {
        for (UUID player : fighters) {
            UUID group = Groups.keyOf(back.getServer(), player);
            BackGroupData.Record r = GroupProgress.of(back, group);
            if (heartCounted.add(group + "@" + r.epoch())) {
                TrialData.onHeartStopped(r);
            }
        }
    }

    // 同じ周の同じ心臓で二重に数えない (グループごとに epoch が同じうちは一度)
    private static final Set<String> heartCounted = new LinkedHashSet<>();

    // 試練が戻った (resetGroup): そのグループのセルを止める。引きは keep.* なので残る
    public static void onGroupReset(ServerLevel back, UUID group) {
        heartCounted.removeIf(s -> s.startsWith(group + "@"));
        stopGroup(back, group);
        // 戸の上書きが消えるので、引いた試練の戸を開け直す
        String titan = Titans.titanOfBack(back.dimension());
        if (titan != null && hasWork(titan)) {
            syncGates(back, group, titan, TrialData.current(back, group));
        }
    }

    // 自己テスト・開発用: 状態を全部捨てる
    public static void clearAll() {
        RUNS.clear();
        heartCounted.clear();
    }
}

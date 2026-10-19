package jp.revati.skyabyss.trial.whale;

import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.Y;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.trial.RingArenaCell;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

// W12 鯨骨の闘技場 (闘・戦う・★★★。docs/design/29 の 3.1)。環状の骨の闘技場で三つの波と、番兵長の影に挑む。波のたびに床が崩れて足場が狭まる。
// 仕組みは RingArenaCell (円い床・崩れる順・幕で半分・橋)。ここは敵 (剣と盾 / 射手つき。最後の波は番兵長の影つき) と、場所だけを決める。
// 修飾: 配置 3 (崩れる順)・数字 2 (波の敵の数・崩れる前の予告)・敵の組 2 (剣と盾 / 射手つき)・障害 (落石)・任意の目標 (一度も落ちずに)
public final class W12Arena extends RingArenaCell {
    private final Def def = TrialCatalog.get("W12");
    private final Ports ports = new Ports(WhaleDeck.W12_GATE, false, List.of(), WhaleDeck.W12_STONE, WhaleDeck.W12_PAD, WhaleDeck.REST_S1,
            WhaleDeck.W12.aabb(), WhaleDeck.rests().stream().filter(r -> r.id().equals(WhaleDeck.REST_W12)).toList());

    // 入口の橋 (控えの北から円の北へ。z 136・137)・出口の橋 (円の南から控えの南へ。z 159・160)
    public static final List<BlockPos> BRIDGE_IN = bridge(136);
    public static final List<BlockPos> BRIDGE_OUT = bridge(159);

    private static List<BlockPos> bridge(int z) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = 22; x <= 26; x++) {
            for (int dz = 0; dz < 2; dz++) {
                out.add(new BlockPos(x, Y, z + dz));
            }
        }
        return out;
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    @Override
    protected int cx() {
        return WhaleDeck.W12_CX;
    }

    @Override
    protected int cz() {
        return WhaleDeck.W12_CZ;
    }

    @Override
    protected int floorY() {
        return Y;
    }

    @Override
    protected List<BlockPos> bridgeIn() {
        return BRIDGE_IN;
    }

    @Override
    protected List<BlockPos> bridgeOut() {
        return BRIDGE_OUT;
    }

    @Override
    protected String msg() {
        return "message.skyabyss.trial.w12";
    }

    @Override
    protected BlockPos stone() {
        return WhaleDeck.W12_STONE;
    }

    @Override
    protected void sendBack(ServerLevel back, ServerPlayer p) {
        TitanBackTrials.returnToCheckpoint(p, WhaleDeck.REST_W12);
    }

    @Override
    protected List<Mob> spawnWave(ServerLevel back, Run run, int wave, int waves, int count, List<BlockPos> spots) {
        List<Mob> out = new ArrayList<>();
        boolean archers = run.mods.enemy() == 1;
        int players = Math.max(1, run.getInt("players"));
        for (int j = 0; j < count; j++) {
            String kind = archers ? (j % 2 == 0 ? "archer" : "sentinel") : (j % 3 == 0 ? "crawler" : "sentinel");
            Mob m = TitanBackTrials.spawnGuard(back, kind, spots.get(j % spots.size()), run.group);
            if (m != null) {
                out.add(m);
            }
        }
        if (wave == waves && !run.mods.quiet()) {
            // 番兵長の影: 人数で体力が増える (心臓と同じ式)
            Mob shadow = TitanBackTrials.spawnGuard(back, "captain", new BlockPos(cx(), Y + 1, cz()), run.group);
            if (shadow instanceof WhaleboneCaptain) {
                var health = shadow.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(Math.min(1024, health.getBaseValue() * 0.6 * StoneWhaleHeart.scaleFor(players)));
                    shadow.setHealth(shadow.getMaxHealth());
                }
            }
            if (shadow != null) {
                out.add(shadow);
            }
        }
        return out;
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = auditArena(back, "W12");
        for (BlockPos p : BRIDGE_IN) {
            if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                bad.add("W12 entry bridge missing at " + p);
                break;
            }
        }
        for (BlockPos p : BRIDGE_OUT) {
            if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                bad.add("W12 exit bridge missing at " + p);
                break;
            }
        }
        if (!back.getBlockState(WhaleDeck.W12_STONE).is(jp.revati.skyabyss.registry.ModBlocks.RESONANCE_STONE.get())) {
            bad.add("W12 stone missing");
        }
        return bad;
    }
}

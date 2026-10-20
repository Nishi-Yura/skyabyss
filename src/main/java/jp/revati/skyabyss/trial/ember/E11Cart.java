package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.CinderRoller;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// E11 灰荷車の護送 (闘・護る・★★。docs/design/29 の 3.4)。灰を積んだ荷車を、道の奥の火の種の炉 (石) まで押す。道のわきから灰被りが襲う。
//   決まりは一つ: **荷車は、そばに人がいて、敵が近くにいないあいだだけ進む**。敵が荷車のそばにいると、荷車は傷む。傷みきると、最後の襲撃の所へ戻る。
//   ひねり: ① 襲撃の来る側 (配置ごと: 北・南・両側)・② 襲撃の回数が多い (数字)・③ 火の粉蛾が荷車を囲む (障害)。
//   一人でも: 押して、敵が寄ったら止めて倒す (押す人と守る人は要らない)。人数が増えると、襲撃の群れが一体ずつ増える (3 人から・4 人から)。
//   押す人は荷車の二マス以内。守る人は敵の前に立つ。荷車は止めると静まる。
// 修飾: 配置 3 (襲撃の来る側)・数字 2 (襲撃の回数 2・3・4 / 押す速さ 40・30・24 tick)・敵の組 2 (灰被りの群れ / 大燠虫つき)・障害 (火の粉蛾)・任意の目標 (荷車を無傷で)
public final class E11Cart implements TrialCell {
    // 道 (x = -27 + i、z = 114)。荷車は index 0 から始まり、index 10 (x = -17) で炉に着く
    public static final int ROAD_Z = 114;
    public static final int ROAD_X0 = -27;
    public static final int LAST = 10;
    static final int[][] RAIDS = {{3, 7}, {2, 5, 8}, {2, 4, 6, 8}};
    static final int[] PUSH_TICKS = {40, 30, 24};
    static final double CART_MAX = 100;
    static final double PIN_DAMAGE = 3.0;
    static final double PIN_RANGE = 4.0;
    static final double PUSH_RANGE = 2.0;
    static final int RAID_LEAD = 40;

    public static BlockPos cartAt(int i) {
        return new BlockPos(ROAD_X0 + i, Y + 1, ROAD_Z);
    }

    private final Def def = TrialCatalog.get("E11");
    private final Ports ports = new Ports(EmberDeck.E11_GATE, false, List.of(), EmberDeck.E11_STONE, EmberDeck.E11_PAD, "k_e11", EmberDeck.SW.aabb(),
            List.of(EmberCellKit.rest("k_e11")));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // 襲撃の来る側 (配置ごと): 0 北 (奥の壁ぎわ) / 1 南 / 2 両側 (交互)
    static int side(int layout, int raid) {
        return switch (layout) {
            case 1 -> 1;
            case 2 -> raid % 2;
            default -> 0;
        };
    }

    static int raids(Run run) {
        return RAIDS[run.mods.number(0)].length;
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("index", 0);
        run.put("cp", 0);
        run.put("hp", CART_MAX);
        run.put("prog", 0);
        run.put("raid", 0);
        run.put("broken", 0);
        run.put("lastIndex", -1);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        for (UUID id : mobs(run)) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        mobs(run).clear();
    }

    // 生きている、この群れの敵の数 (近くにいるかの判定に使う)
    private static List<Entity> alive(ServerLevel back, Run run) {
        List<Entity> out = new ArrayList<>();
        List<UUID> list = mobs(run);
        for (int i = list.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(list.get(i));
            if (e == null || !e.isAlive()) {
                list.remove(i);
            } else {
                out.add(e);
            }
        }
        return out;
    }

    private void raid(ServerLevel back, Run run, int players, int index) {
        int r = run.getInt("raid");
        int ahead = Math.min(LAST, index + 3);
        int x = ROAD_X0 + ahead;
        int z = side(run.mods.layout(), r) == 0 ? 111 : 117;
        int count = 2 + (players >= 3 ? 1 : 0);
        for (int k = 0; k < count; k++) {
            AshThrall t = EmberCellKit.spawn(back, run.group, EmberMobSetup.ASH_THRALL.get(), x + 0.5 + (k % 2), Y + 1, z + 0.5 + (k / 2) * (z == 111 ? 1 : -1));
            if (t != null) {
                mobs(run).add(t.getUUID());
            }
        }
        if (run.mods.enemy() == 1) {
            CinderRoller roller = EmberCellKit.spawn(back, run.group, EmberMobSetup.CINDER_ROLLER.get(), x + 0.5, Y + 1, z + 0.5 + (z == 111 ? 2 : -2));
            if (roller != null) {
                mobs(run).add(roller.getUUID());
            }
        }
        if (run.mods.hasHazard(0)) {
            for (int k = 0; k < 2; k++) {
                Mob m = EmberCellKit.spawn(back, run.group, EmberMobSetup.EMBER_MOTH.get(), x + 0.5, Y + 4, 114.5 + k - 0.5);
                if (m != null) {
                    mobs(run).add(m.getUUID());
                }
            }
        }
        run.put("raid", r + 1);
        run.put("cp", index);
        TrialFx.sound(back, run.group, cartAt(index), EmberTrialSetup.BELLOWS.get(), 1.4f, 0.7f);
        TrialFx.bar(Groups.online(back, run.group), ModText.battle("message.skyabyss.trial.e11.raid", r + 1, raids(run)));
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e11.start"));
        }
        if (run.cleared) {
            return;
        }
        int index = run.getInt("index");
        double hp = (Double) run.data.getOrDefault("hp", CART_MAX);
        List<Entity> foes = alive(back, run);
        // 敵が荷車のそばにいる: 傷む (押せない)
        BlockPos cart = cartAt(index);
        boolean pinned = false;
        for (Entity e : foes) {
            double dx = e.getX() - (cart.getX() + 0.5);
            double dz = e.getZ() - (cart.getZ() + 0.5);
            if (dx * dx + dz * dz <= PIN_RANGE * PIN_RANGE) {
                pinned = true;
                break;
            }
        }
        if (pinned) {
            hp = Math.max(0, hp - PIN_DAMAGE);
            if (run.mods.goal() == 0 && hp < CART_MAX) {
                run.goalOk = false;
            }
        }
        // 押す: そばに人がいて、傷んでいないとき (5 tick ごと)
        boolean pushed = false;
        if (!pinned) {
            for (ServerPlayer p : inside) {
                if (p.isAlive() && Math.abs(p.getX() - (cart.getX() + 0.5)) <= PUSH_RANGE && Math.abs(p.getZ() - (cart.getZ() + 0.5)) <= PUSH_RANGE) {
                    pushed = true;
                    break;
                }
            }
        }
        if (pushed && index < LAST) {
            int prog = run.getInt("prog") + 5;
            if (prog >= PUSH_TICKS[run.mods.number(1)]) {
                prog = 0;
                index++;
                run.put("index", index);
            }
            run.put("prog", prog);
        }
        // 襲撃: 荷車が回数の所まで来たら、先の道へ群れ
        int r = run.getInt("raid");
        int[] at = RAIDS[run.mods.number(0)];
        if (r < at.length && index >= at[r] && now >= run.getLong("raidAt")) {
            run.put("raidAt", now + RAID_LEAD);
            raid(back, run, EmberCellKit.entrants(inside.size()), index);
        }
        // 荷車が壊れた: 最後の襲撃の所へ戻る (群れは残る)
        if (hp <= 0) {
            hp = CART_MAX;
            run.put("index", run.getInt("cp"));
            run.put("prog", 0);
            run.put("broken", run.getInt("broken") + 1);
            if (run.mods.goal() == 0) {
                run.goalOk = false;
            }
            TrialFx.sound(back, run.group, cartAt(index), EmberTrialSetup.ANVIL_STRIKE.get(), 1.0f, 0.7f);
            TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.e11.broken"));
        }
        run.data.put("hp", hp);
        int nowIndex = run.getInt("index");
        if (nowIndex != run.getInt("lastIndex") || pinned != Boolean.TRUE.equals(run.data.get("pinned"))) {
            run.put("lastIndex", nowIndex);
            run.put("pinned", pinned);
            TrialRuntime.refresh(back, run);
        }
        if (nowIndex >= LAST) {
            run.cleared = true;
            run.clearedAt = now;
            TrialFx.sound(back, run.group, EmberDeck.E11_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
            TrialFx.bar(Groups.online(back, run.group), ModText.progress("message.skyabyss.trial.e11.arrived"));
            TrialRuntime.refresh(back, run);
        }
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        for (int i = 0; i <= LAST; i++) {
            out.accept(new BlockPos(ROAD_X0 + i, Y, ROAD_Z), Blocks.SMOOTH_BASALT.defaultBlockState());
        }
        double hp = (Double) run.data.getOrDefault("hp", CART_MAX);
        BlockState cart = hp < CART_MAX / 2 ? Blocks.RED_CARPET.defaultBlockState() : Blocks.ORANGE_CARPET.defaultBlockState();
        out.accept(cartAt(run.getInt("index")), cart);
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i <= LAST; i++) {
            BlockPos p = new BlockPos(ROAD_X0 + i, Y, ROAD_Z);
            if (!EmberDeck.SW.contains(p) || back.getBlockState(p).isAir()) {
                bad.add("E11 road tile " + i + " is missing");
                break;
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(EmberDeck.E11_STONE))) {
            bad.add("E11 stone missing");
        }
        return bad;
    }
}

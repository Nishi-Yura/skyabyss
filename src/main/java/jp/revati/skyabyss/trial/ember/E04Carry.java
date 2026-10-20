package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.back.EmberKit;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrials;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// E04 熾の運び (歩・運ぶ・★★★。docs/design/29 の 3.4)。消えかけの熾 (熾り石の火。持つ人が一つだけ持つ) を、道に沿って奥の火床 (石) まで運ぶ。
//   決まりは一つ: **熾は水に入ると消える**。持つ人は熱がたまる (1 秒に +1)。
//   風 (障害): 予告の粒のあと、道の上の人を横へ押す。しゃがんでいるか、そばに仲間がいれば熾は押されない (庇う)。
//   ひねり: ① 道が曲がる (配置 3 通り) ・② 水の溜まりが道の上に (水は一つ分。跳んで越える)・③ 風の間隔が詰まる。
//   一人でも: 熾を持ったまま、しゃがんで風をやり過ごし、水は跳ぶ。人数が増えると風の間隔が縮む (1 人増えるごとに 10%。下限 70%)。
//   熾を取るのは、右クリックの石 (熾り石)。持っているあいだ、ほかの人は取れない。水で消えたら、また石から取れる。
// 修飾: 配置 3 (道の曲がり方・水の場所)・数字 2 (風の間隔 160・120・90 tick / 水の溜まり 1・2・3)・障害 (風)・任意の目標 (一度も消さずに)
public final class E04Carry implements TrialCell {
    // 道 (配置ごと。各点は (x, z)。道の上の床は見える色で光る)
    static final int[][][] ROUTE = {
            // 0: 横に行って、奥で南へ
            {{18, 112}, {19, 112}, {20, 112}, {21, 112}, {22, 112}, {23, 112}, {24, 112}, {25, 112}, {26, 112}, {27, 112}, {28, 112}, {29, 112}, {30, 112},
                    {30, 113}, {30, 114}, {30, 115}, {30, 116}},
            // 1: 南へ下りてから東へ
            {{18, 112}, {19, 112}, {20, 112}, {21, 112}, {22, 112}, {23, 112}, {24, 112}, {24, 113}, {24, 114}, {24, 115}, {24, 116}, {25, 116}, {26, 116},
                    {27, 116}, {28, 116}, {29, 116}, {30, 116}},
            // 2: 段を二つ下りる
            {{18, 112}, {19, 112}, {20, 112}, {21, 112}, {22, 112}, {22, 113}, {22, 114}, {23, 114}, {24, 114}, {25, 114}, {26, 114}, {27, 114}, {28, 114},
                    {29, 114}, {30, 114}, {30, 115}, {30, 116}},
    };
    // 水の溜まりになりうる道の番号 (配置ごと。数字が 1・2・3 のとき、先頭から使う)
    static final int[][] POOLS = {{5, 10, 14}, {3, 8, 13}, {2, 9, 15}};
    static final int[] GUST_TICKS = {160, 120, 90};
    static final int GUST_WARN = 24;
    static final int GUST_LEAD = 60;
    static final double GUST_PUSH = 0.6;
    static final double SHIELD_RANGE = 2.5;

    // 熾り石 (取る所) = 道の始まりのそば。火床 (石) = 道の終わりのそば
    public static final BlockPos BRAZIER = new BlockPos(17, Y + 1, 112);
    static final int FINISH_X = 30;
    static final int FINISH_Z = 115;

    private final Def def = TrialCatalog.get("E04");
    private final Ports ports = new Ports(EmberDeck.E04_GATE, false, List.of(), EmberDeck.E04_STONE, EmberDeck.E04_PAD, "k_e04", aabb(),
            List.of(EmberCellKit.rest("k_e04")));

    static net.minecraft.world.phys.AABB aabb() {
        EmberBackLayout.Box b = EmberDeck.SE;
        return b.aabb();
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // 道の点 (配置・番号)
    public static int[] point(int layout, int i) {
        return ROUTE[layout][i];
    }

    public static int routeLength(int layout) {
        return ROUTE[layout].length;
    }

    // この配置・数字での水の溜まりの場所 (道の番号)
    public static List<Integer> pools(Run run) {
        int n = run.mods.number(1) + 1;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(POOLS[run.mods.layout()][i]);
        }
        return out;
    }

    static boolean inPool(Run run, BlockPos below) {
        for (int idx : pools(run)) {
            int[] c = point(run.mods.layout(), idx);
            if (below.getX() == c[0] && below.getZ() == c[1] && below.getY() == Y) {
                return true;
            }
        }
        return false;
    }

    public static int gustPeriod(Run run, int players) {
        int base = GUST_TICKS[run.mods.number(0)];
        return Math.max(70, (int) Math.round(base * Math.max(0.7, 1.0 - 0.1 * (EmberCellKit.entrants(players) - 1))));
    }

    static boolean isCarrier(ServerPlayer p) {
        return EmberTrials.count(p, EmberTrialSetup.LIVE_COAL.get()) > 0;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("gustAt", back.getGameTime() + GUST_LEAD);
        run.put("gustDir", 1);
        run.put("warned", false);
        run.put("period", gustPeriod(run, 1));
        run.put("lastCoal", 0L);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        // 持ったままの熾は消す (取った人が部屋を出て、少したったとき)
        EmberTrials.stripGroup(back, run.group, EmberTrialSetup.LIVE_COAL.get());
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        if (!pos.equals(BRAZIER) || run.cleared) {
            return false;
        }
        boolean carried = false;
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            if (Groups.isMember(p, run.group) && isCarrier(p)) {
                carried = true;
            }
        }
        if (carried) {
            TrialFx.bar(List.of(player), ModText.hint("message.skyabyss.trial.e04.held"));
            return true;
        }
        player.getInventory().add(new ItemStack(EmberTrialSetup.LIVE_COAL.get()));
        TrialFx.sound(back, run.group, BRAZIER, SoundEvents.FIRECHARGE_USE, 1.0f, 1.0f);
        TrialFx.bar(List.of(player), ModText.objective("message.skyabyss.trial.e04.taken"));
        return true;
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.e04.start"));
        }
        if (run.cleared) {
            return;
        }
        // 熾を持つ人 (一人)。持つと熱がたまる (1 秒に +1)
        ServerPlayer carrier = null;
        for (ServerPlayer p : inside) {
            if (p.isAlive() && isCarrier(p)) {
                carrier = p;
                break;
            }
        }
        if (carrier != null) {
            if (now - run.getLong("lastCoal") >= 20) {
                run.put("lastCoal", now);
                EmberHeat.add(carrier, 1f, EmberHeat.Reason.LIVE_COAL);
            }
            BlockPos below = carrier.blockPosition().below();
            if (inPool(run, below)) {
                // 水に入った: 熾は消える (石からもう一度)
                EmberTrials.strip(carrier, EmberTrialSetup.LIVE_COAL.get());
                TrialFx.sound(back, run.group, below, EmberTrialSetup.VENT_HISS.get(), 1.0f, 1.0f);
                TrialFx.particles(back, run.group, ParticleTypes.CLOUD, carrier.getX(), carrier.getY() + 1, carrier.getZ(), 10, 0.4, 0.4, 0.4, 0.05);
                TrialFx.bar(inside, ModText.deny("message.skyabyss.trial.e04.doused"));
                run.put("douses", run.getInt("douses") + 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
            } else if (carrier.getX() >= FINISH_X - 0.5 && carrier.getZ() >= FINISH_Z - 0.5) {
                EmberTrials.strip(carrier, EmberTrialSetup.LIVE_COAL.get());
                run.cleared = true;
                run.clearedAt = now;
                TrialFx.sound(back, run.group, EmberDeck.E04_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.e04.reached"));
                TrialRuntime.refresh(back, run);
                return;
            }
        }
        // 風 (障害のあるときだけ。数字の間隔で来る)
        if (run.mods.hasHazard(0)) {
            gust(back, run, inside, carrier, now);
        }
        if (run.mods.goal() == 0 && run.getInt("douses") > 0) {
            run.goalOk = false;
        }
    }

    private void gust(ServerLevel back, Run run, List<ServerPlayer> inside, ServerPlayer carrier, long now) {
        long gustAt = run.getLong("gustAt");
        int dir = run.getInt("gustDir");
        if (now >= gustAt - GUST_WARN && now < gustAt) {
            if (!Boolean.TRUE.equals(run.data.get("warned"))) {
                run.put("warned", true);
                TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.e04.gust_warn"));
            }
            for (ServerPlayer p : inside) {
                TrialFx.particles(back, run.group, ParticleTypes.CLOUD, p.getX() - dir * 1.2, p.getY() + 1, p.getZ(), 2, 0.1, 0.3, 0.3, 0.05 * dir);
            }
        } else if (now >= gustAt) {
            if (carrier != null && !shielded(carrier, inside)) {
                Vec3 push = carrier.getDeltaMovement();
                carrier.setDeltaMovement(push.x + GUST_PUSH * dir, 0.15, push.z);
                carrier.hurtMarked = true;
                TrialFx.bar(List.of(carrier), ModText.deny("message.skyabyss.trial.e04.pushed"));
            }
            run.put("warned", false);
            run.put("gustAt", now + gustPeriod(run, inside.size()));
            run.put("gustDir", -dir);
        }
    }

    // 庇われているか: しゃがんでいる / そばに仲間がいる
    static boolean shielded(ServerPlayer carrier, List<ServerPlayer> inside) {
        if (carrier.isShiftKeyDown()) {
            return true;
        }
        for (ServerPlayer p : inside) {
            if (p != carrier && p.isAlive() && p.distanceTo(carrier) <= SHIELD_RANGE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int n = routeLength(run.mods.layout());
        for (int i = 0; i < n; i++) {
            int[] c = point(run.mods.layout(), i);
            out.accept(new BlockPos(c[0], Y, c[1]), Blocks.SMOOTH_BASALT.defaultBlockState());
        }
        for (int idx : pools(run)) {
            int[] c = point(run.mods.layout(), idx);
            out.accept(new BlockPos(c[0], Y + 1, c[1]), Blocks.BLUE_CARPET.defaultBlockState());
        }
        EmberCellKit.padGlow(ports.pad(), run.cleared, out);
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < ROUTE.length; l++) {
            for (int[] c : ROUTE[l]) {
                if (!EmberDeck.SE.contains(c[0], Y + 1, c[1])) {
                    bad.add("E04 route point " + c[0] + "," + c[1] + " is outside the room (layout " + l + ")");
                }
            }
            for (int idx : POOLS[l]) {
                if (idx <= 0 || idx >= ROUTE[l].length - 1) {
                    bad.add("E04 pool index " + idx + " is at an end (layout " + l + ")");
                }
            }
        }
        if (back.getBlockState(BRAZIER).isAir()) {
            bad.add("E04 brazier is missing");
        }
        if (!back.getBlockState(EmberDeck.E04_STONE).is(jp.revati.skyabyss.titan.ember.back.EmberBackSetup.KINDLE_STONE.get())) {
            bad.add("E04 stone missing");
        }
        return bad;
    }
}

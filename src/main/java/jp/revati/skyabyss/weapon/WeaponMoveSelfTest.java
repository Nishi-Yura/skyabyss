package jp.revati.skyabyss.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 武器の動きの自己テスト (docs/review/gear_style の絵コンテ → weapon/MoveSet):
//  1. データ: 17 品すべてに動きがある。キーの並び・段の数 (1〜3)・ヒットストップ (3 以下)・溜め / 打ち抜き / 残身 / 戻りがそろっている
//  2. 絵と当たりの同期: 各段に「当たる」key があり、その時刻が武器の strike と同じ。振りの長さ 6〜16 tick のどれでも、
//     サーバーが当てる tick (strikeTick) と、クライアントの動きの当たる key の tick が一致する
//  3. 連撃の時間: 段の数え方 (猶予内ならつづく・超えたら戻る・最後のあとは 1 段目)、品の攻撃速度の通常のリズムで連撃がつづく
//  4. 釣り合い: 当たる tick は次のクリックが通る (クールダウン) より前、振りはクールダウン内に終わる。1 分の与ダメージの回数は預けても変わらない (±5% 以内)
//  5. サーバーの流れ: 偽のプレイヤーで預けたクリックが、strikeTick に 1 度だけ当たる (預ける前は当たらない)
public final class WeaponMoveSelfTest {
    private WeaponMoveSelfTest() {
    }

    private static final List<String> FAILS = new ArrayList<>();

    private static void check(boolean ok, String msg) {
        if (!ok) {
            FAILS.add(msg);
        }
    }

    private static double attackSpeedOf(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id));
        double speed = 4.0;
        for (var e : new ItemStack(item).getAttributeModifiers().modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_SPEED)) {
                speed += e.modifier().amount();
            }
        }
        return speed;
    }

    public static void runSelfTest(MinecraftServer server) {
        FAILS.clear();
        int steps = 0;
        int combos = 0;
        for (String id : MoveSets.ITEMS) {
            MoveSet ms = MoveSets.get(id);
            check(ms != null, id + ": no moveset");
            if (ms == null) {
                continue;
            }
            check(!ms.steps.isEmpty() && ms.steps.size() <= 3, id + ": combo must have 1..3 steps, has " + ms.steps.size());
            boolean swings = ms.strike > 0f;
            double speed = swings ? attackSpeedOf(id) : 0.0;
            for (int si = 0; si < ms.steps.size(); si++) {
                MoveSet.Step st = ms.steps.get(si);
                String tag = id + "#" + si + "(" + st.name + ")";
                steps++;
                List<Float> times = st.keyTimes();
                check(times.get(0) == 0f && times.get(times.size() - 1) == 1f, tag + ": keys must run from t=0 to t=1");
                check(st.hitstop >= 0 && st.hitstop <= MoveSet.MAX_HITSTOP, tag + ": hitstop out of range " + st.hitstop);
                check(st.shake >= 0f && st.shake <= 1f, tag + ": shake out of range");
                if (!swings) {
                    continue;
                }
                // 絵と当たりの同期
                check(st.hitAt >= 0f, tag + ": no hit key");
                check(Math.abs(st.hitAt - ms.strike) < 0.005f, tag + ": hit key t=" + st.hitAt + " differs from strike " + ms.strike);
                for (int d = MoveSet.MIN_TICKS; d <= MoveSet.MAX_TICKS; d++) {
                    int server_ = ms.strikeTick(d);
                    int client = Math.max(1, Math.min(d - 1, Math.round(st.hitAt * d)));
                    check(server_ == client, tag + ": server strike tick " + server_ + " != client hit tick " + client + " at duration " + d);
                    check(server_ >= 1 && server_ < d, tag + ": strike tick " + server_ + " outside the swing of " + d);
                }
                // 溜め → 打ち抜き → 残身 → 戻り
                check(times.stream().anyMatch(t -> t > 0.05f && t <= st.hitAt - 0.1f), tag + ": no anticipation key before the strike");
                check(times.stream().anyMatch(t -> t >= st.hitAt + 0.08f && t < 1f), tag + ": no follow-through key after the strike");
                if (st.has(MoveSet.TRAIL)) {
                    check(st.get(MoveSet.TRAIL, st.hitAt, 0f) >= 0.7f, tag + ": trail should be strong at the strike");
                    check(st.get(MoveSet.TRAIL, 0f, 1f) <= 0.01f && st.get(MoveSet.TRAIL, 1f, 1f) <= 0.01f, tag + ": trail must be off at both ends");
                }
                // 重い武器ほど溜めが長い (全体の 40% 前後)・軽い武器は短い
                float anticipation = 0f;
                for (float t : times) {
                    if (t < st.hitAt - 0.001f) {
                        anticipation = Math.max(anticipation, t);
                    }
                }
                check(anticipation >= 0.15f, tag + ": anticipation ends too early at " + anticipation);
            }
            if (!swings) {
                continue;
            }
            // 連撃の時間と釣り合い
            int duration = ms.durationFor(speed);
            int cooldown = (int) Math.ceil(20.0 / speed);
            int strike = ms.strikeTick(duration);
            check(strike < cooldown, id + ": strike tick " + strike + " is not before the next full-strength click at " + cooldown);
            check(duration <= Math.max(cooldown, MoveSet.MIN_TICKS), id + ": swing " + duration + " ticks outlasts the cooldown " + cooldown);
            int prev = -1;
            boolean chain = true;
            for (int n = 0; n < ms.steps.size() * 2 + 1; n++) {
                int next = ComboTracker.next(prev, n == 0 ? Integer.MAX_VALUE : cooldown, duration, ms.window, ms.steps.size());
                if (n == 0) {
                    chain &= next == 0;
                } else {
                    chain &= next == (prev + 1) % ms.steps.size();
                }
                prev = next;
            }
            check(chain, id + ": a click on the cooldown rhythm does not keep the combo going");
            // 預けても 1 分の回数は変わらない
            int plain = 0;
            int delayed = 0;
            for (int t = 0; t < 1200; t += cooldown) {
                plain++;
                if (t + strike < 1200) {
                    delayed++;
                }
            }
            check(Math.abs(delayed - plain) <= Math.max(1, plain / 20), id + ": hits per minute change " + plain + " -> " + delayed);
            combos++;
        }
        // 連撃の数え方
        check(ComboTracker.next(-1, 0, 12, 12, 3) == 0, "combo: first swing is step 0");
        check(ComboTracker.next(0, 10, 12, 12, 3) == 1 && ComboTracker.next(1, 10, 12, 12, 3) == 2, "combo: continues inside the window");
        check(ComboTracker.next(2, 10, 12, 12, 3) == 0, "combo: wraps after the last step");
        check(ComboTracker.next(1, 12 + 12 + 1, 12, 12, 3) == 0, "combo: resets after the window");
        check(ComboTracker.next(1, 5, 12, 12, 1) == 0, "combo: single-step weapons stay on step 0");
        // 絵コンテの型
        MoveSet gs = MoveSets.get("whalebone_greatsword");
        check(gs != null && gs.step(0).hitstop == 3, "greatsword overhead cleave must freeze 3 ticks");
        MoveSet fang = MoveSets.get("waterway_fang");
        check(fang != null && fang.steps.size() >= 2 && fang.step(0).hitstop <= 1, "fang: fast X slashes with a short freeze");
        FAILS.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST weapon moves FAILED: {}", m));
        if (FAILS.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST weapon moves PASSED ({} items, {} steps, {} swinging weapons: hit key = strike tick at every swing length 6..16, combo chain, no DPS shift)",
                    MoveSets.ITEMS.size(), steps, combos);
        }
    }

    // ---------------------------------------------------------------- サーバーの流れ (偽のプレイヤーで 1 本ずつ)

    private static FakePlayer fake;
    private static Zombie dummy;
    private static int tick;
    private static int expected;
    private static float healthBefore;
    private static int strikeTickSeen = -1;
    private static boolean started;
    private static int index;
    private static final String[] FLOW_ITEMS = {"whalebone_greatsword", "waterway_fang", "captain_pike"};

    // 毎 tick 呼ぶ。全部の品を試し終えたら true
    public static boolean runFlow(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (index >= FLOW_ITEMS.length) {
            WeaponStrikes.testAllowFake = false;
            return true;
        }
        String id = FLOW_ITEMS[index];
        MoveSet ms = MoveSets.get(id);
        if (!started) {
            WeaponStrikes.testAllowFake = true;
            if (fake == null) {
                fake = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("skyabyss-weapon-test".getBytes()), "weapon_test"));
            }
            BlockPos p = level.getSharedSpawnPos().above(2);
            fake.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            fake.setYRot(0f);
            fake.setXRot(0f);
            fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id))));
            if (dummy == null || dummy.isRemoved()) {
                dummy = EntityType.ZOMBIE.create(level);
                dummy.setNoAi(true);
                dummy.setPersistenceRequired();
                dummy.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.COMMAND, null);
                level.addFreshEntity(dummy);
            }
            // 正面 (視線 = 南 = +z) 1.5 ブロック
            dummy.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 2.0);
            dummy.setHealth(dummy.getMaxHealth());
            dummy.invulnerableTime = 0;
            dummy.hurtTime = 0;
            healthBefore = dummy.getHealth();
            int duration = ms.durationFor(fake.getAttributeValue(Attributes.ATTACK_SPEED));
            expected = ms.strikeTick(duration);
            fake.attackStrengthTicker = 100;
            fake.attack(dummy);
            // 預けたので、この tick には当たっていない
            if (dummy.getHealth() < healthBefore) {
                FAILS.add(id + ": damage landed on the click tick, not at the strike");
            }
            if (WeaponStrikes.pending() != 1) {
                FAILS.add(id + ": click was not held back (pending " + WeaponStrikes.pending() + ")");
            }
            tick = 0;
            strikeTickSeen = -1;
            started = true;
            return false;
        }
        tick++;
        if (strikeTickSeen < 0 && dummy.getHealth() < healthBefore) {
            strikeTickSeen = tick;
        }
        if (tick >= expected + 6) {
            // 観測の順番で ±1 tick のずれはありうる (ほかの listener が同じ tick の前後どちらで走るか)
            check(strikeTickSeen >= 0, id + ": held click never landed");
            check(strikeTickSeen < 0 || Math.abs(strikeTickSeen - expected) <= 1, id + ": landed at tick " + strikeTickSeen + ", expected " + expected);
            check(WeaponStrikes.pending() == 0, id + ": click still pending after the strike");
            SkyAbyss.LOGGER.info("SELFTEST weapon strike {}: click held, landed {} ticks later (expected {})", id, strikeTickSeen, expected);
            started = false;
            index++;
        }
        return false;
    }

    public static void finishFlow() {
        if (dummy != null) {
            dummy.discard();
            dummy = null;
        }
        FAILS.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST weapon strike FAILED: {}", m));
        if (FAILS.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST weapon strike PASSED (held click lands on the strike tick for {} weapons, once)", FLOW_ITEMS.length);
        }
        FAILS.clear();
        index = 0;
        started = false;
    }
}

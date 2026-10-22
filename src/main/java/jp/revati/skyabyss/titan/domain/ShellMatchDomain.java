package jp.revati.skyabyss.titan.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 潮殻の心臓の領域「合わせ貝」(docs/design/08 の 5.5)。床に貝の板が 12 枚ならび、それぞれ四つの印 (巻貝 ▲・二枚貝 ◆・ヒトデ ★・真珠 ●) のどれか。
// 心臓の上に印がひとつ浮かぶ。その印の板を、全部叩く (叩くと板が光って止まる)。違う印を叩くと、光っていた板が全部消える (傷はない)。
// 印の板を叩きおえると、板の印は配りなおされて、次の印が出る。印を (3 + 巡) 回合わせると一巡。水は使わない。
// 決まりの傷: 貝の打ち下ろし (人のそばの板を丸く叩く) と、貝の薙ぎ (部屋を横切る帯)。どちらも赤い予告つき。傷 8
public final class ShellMatchDomain extends HeartDomain {
    public static final String ID = "shell_match";
    public static final int PANELS = 12;
    public static final int MARK_KINDS = 4;
    public static final int PER_MARK = PANELS / MARK_KINDS;
    public static final float RING_DAMAGE = 8f;
    public static final double FLOOR_REACH = 1.9;
    public static final int FIRST = 60;
    public static final int SLAM_WARN = 30;
    public static final int SLAM_ACTIVE = 8;
    public static final double SLAM_RADIUS = 2.4;
    public static final int SWEEP_WARN = 36;
    public static final int SWEEP_ACTIVE = 24;
    public static final int SHUFFLE_DELAY = 26;

    // 印 0 巻貝 ▲ 水色 / 1 二枚貝 ◆ 紫 / 2 ヒトデ ★ 黄 / 3 真珠 ● 白。色だけでなく形でも見分けられる (赤は予告の色なので使わない)
    public static final String[] GLYPH = {"▲", "◆", "★", "●"};
    public static final int[] RGB = {0x4DC3FF, 0xA070E0, 0xF2D23C, 0xF0F4F8};
    private static final Block[] TILE = {Blocks.LIGHT_BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.WHITE_CONCRETE};

    private int[] mark = new int[PANELS];
    private int current = -1;
    private int lastCurrent = -1;
    private int marksDone;
    private int marksNeeded = 3;
    private int shuffle;
    private int cycle;
    private final jp.revati.skyabyss.combat.AntiStrafe strafe = new jp.revati.skyabyss.combat.AntiStrafe();
    private UUID icon;
    private int iconKey = -2;

    public ShellMatchDomain(Mob heart, double cx, double cz, double floorY, double radius, Predicate<ServerPlayer> victims,
            BiConsumer<ServerPlayer, Float> striker, Supplier<Collection<ServerPlayer>> players) {
        super(heart, ID, cx, cz, floorY, radius, victims, striker, players, MobSounds.DOMAIN_RISING_TIDE.get());
    }

    // ---------------------------------------------------------------- 状態の見え口 (試験・画面)
    public int currentMark() {
        return current;
    }

    public int markOf(int index) {
        return index >= 0 && index < PANELS ? mark[index] : -1;
    }

    public boolean shuffling() {
        return shuffle > 0;
    }

    public int marksNeeded() {
        return marksNeeded;
    }

    public int marksDone() {
        return marksDone;
    }

    // いまの印の板のうち、まだ叩いていないもの (index)。巡の合間・配りなおし中は空
    public List<Integer> pending() {
        List<Integer> out = new ArrayList<>();
        if (shuffle > 0 || current < 0) {
            return out;
        }
        for (Node n : nodes) {
            if (!n.done && mark[n.index] == current) {
                out.add(n.index);
            }
        }
        return out;
    }

    @Override
    public int nodeCount() {
        return PANELS;
    }

    @Override
    public int propsPerNode() {
        return 2;
    }

    @Override
    protected boolean nodeColumns() {
        return false;
    }

    @Override
    protected float boxWidth() {
        return 2.0f;
    }

    @Override
    protected float boxHeight() {
        return 1.2f;
    }

    // ---------------------------------------------------------------- 宣言・決まり
    @Override
    protected void onDeclare(ServerLevel level) {
        level.playSound(null, cx, floorY + 2, cz, MobSounds.TIDE_HEART_HURT.get(), SoundSource.HOSTILE, 2f, 0.5f);
    }

    @Override
    protected void declareEffects(ServerLevel level, int t) {
        if (t % 4 == 0) {
            double r = radius * (1 - t / (double) DECLARE_TICKS);
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(a) * r, floorY + 0.5, cz + Math.sin(a) * r, 1, 0.2, 0.1, 0.2, 0.02);
            }
        }
    }

    private int period() {
        return fast(Math.max(54, 84 - 14 * round()));
    }

    @Override
    protected void rule(ServerLevel level, int t) {
        if (t < FIRST) {
            return;
        }
        if ((t - FIRST) % period() != 0) {
            return;
        }
        List<ServerPlayer> who = victimsNow();
        if (who.isEmpty() || nodes.isEmpty()) {
            return;
        }
        Collections.shuffle(who, new java.util.Random(heart.getRandom().nextLong()));
        int c = cycle++;
        // 回り続ける人 (AntiStrafe): 打ち下ろしは先読みした先へ、薙ぎは背後から追う。止まって板を叩く人には出ない
        List<ServerPlayer> strafers = new ArrayList<>();
        for (ServerPlayer pl : who) {
            if (strafe.direction(pl) != 0) {
                strafers.add(pl);
            }
        }
        if (c % 2 == 0 || !strafers.isEmpty()) {
            // 貝の打ち下ろし: 人のそばの板を丸く叩く。人数ぶん (二巡目からは倍、四つまで)
            BossCue.send(heart, "tide", "shell_slam", SLAM_WARN);
            int count = Math.min(4, who.size() * (round() >= 1 ? 2 : 1));
            List<Integer> used = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (i < who.size() && strafe.direction(who.get(i)) != 0) {
                    Vec3 at = strafe.aim(who.get(i), SLAM_WARN);
                    slam(level, new Vec3(at.x, floorY, at.z));
                    continue;
                }
                int idx = i < who.size() ? nearestPanel(who.get(i).position(), used) : heart.getRandom().nextInt(nodes.size());
                if (idx < 0 || used.contains(idx)) {
                    continue;
                }
                used.add(idx);
                slam(level, nodes.get(idx).pos);
            }
        }
        if (c % 2 == 1 || !strafers.isEmpty()) {
            // 貝の薙ぎ: 部屋を横切る帯。人のいる向きから始まる。回り続ける人には背後から回り込む
            BossCue.send(heart, "tide", "shell_sweep", SWEEP_WARN);
            if (!strafers.isEmpty()) {
                ServerPlayer p = strafers.get(0);
                int dir = strafe.direction(p);
                sweep(level, strafe.angleAt(p, SWEEP_WARN) - dir * 0.8, dir * 2.0);
            } else {
                ServerPlayer target = who.get(0);
                double a = Math.atan2(target.getZ() - cz, target.getX() - cx);
                int dir = heart.getRandom().nextBoolean() ? 1 : -1;
                sweep(level, a - dir * 0.6, dir * 1.9);
            }
        }
    }

    private List<ServerPlayer> victimsNow() {
        List<ServerPlayer> who = new ArrayList<>(inRoom());
        who.removeIf(pl -> !victims.test(pl) || pl.isSpectator() || !pl.isAlive());
        return who;
    }

    private int nearestPanel(Vec3 at, List<Integer> used) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (Node n : nodes) {
            if (used.contains(n.index)) {
                continue;
            }
            double d = n.pos.distanceToSqr(at.x, n.pos.y, at.z);
            if (d < bd) {
                bd = d;
                best = n.index;
            }
        }
        return best;
    }

    private void slam(ServerLevel level, Vec3 at) {
        tg(level, TelegraphShape.circle(at.x, at.z, SLAM_RADIUS), SLAM_WARN)
                .reach(FLOOR_REACH).active(SLAM_ACTIVE).jumpClear(0.7)
                .onImpact(s -> {
                    s.playSound(null, at.x, floorY, at.z, MobSounds.TIDE_HEART_HURT.get(), SoundSource.HOSTILE, 1.2f, 0.8f);
                    s.sendParticles(ParticleTypes.CRIT, at.x, floorY + 0.5, at.z, 30, SLAM_RADIUS * 0.4, 0.2, SLAM_RADIUS * 0.4, 0.3);
                })
                .onHit((s, p) -> striker.accept(p, RING_DAMAGE))
                .start();
    }

    private void sweep(ServerLevel level, double start, double span) {
        tg(level, TelegraphShape.sweep(cx, cz, start, span, radius, 1.6), SWEEP_WARN)
                .reach(FLOOR_REACH).active(SWEEP_ACTIVE).jumpClear(0.7)
                .onImpact(s -> s.playSound(null, cx, floorY, cz, MobSounds.TIDE_HEART_HURT.get(), SoundSource.HOSTILE, 1.4f, 0.6f))
                .onHit((s, p) -> striker.accept(p, RING_DAMAGE))
                .start();
    }

    // 巡の合間: 人のそばを丸く叩く (二人まで)
    @Override
    protected void interludeAttack(ServerLevel level) {
        List<ServerPlayer> who = victimsNow();
        BossCue.send(heart, "tide", "shell_slam", SLAM_WARN);
        for (int i = 0; i < Math.min(2, who.size()); i++) {
            slam(level, who.get(i).position());
        }
    }

    @Override
    protected void ambient(ServerLevel level, int t) {
        double a = heart.getRandom().nextDouble() * Math.PI * 2;
        double r = heart.getRandom().nextDouble() * radius;
        level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(a) * r, floorY + 1.0, cz + Math.sin(a) * r, 1, 0.4, 0.3, 0.4, 0.005);
    }

    @Override
    protected void onEnd(ServerLevel level, boolean shattered) {
        level.playSound(null, cx, floorY + 2, cz, MobSounds.TIDE_HEART_HURT.get(), SoundSource.HOSTILE, 2.5f, shattered ? 1.4f : 0.8f);
    }

    // ---------------------------------------------------------------- 仕掛け: 貝の板
    // 内側に 4 枚 (半径 3.8。斜め)、外側に 8 枚 (半径 9.6。岩の島 (中心 8.5・斜め) の間)
    @Override
    protected List<Vec3> nodeSpots() {
        List<Vec3> out = new ArrayList<>();
        double k = radius / 13.5;
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * (0.25 + 0.5 * i);
            out.add(new Vec3(cx + Math.cos(a) * 3.8 * k, floorY, cz + Math.sin(a) * 3.8 * k));
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            out.add(new Vec3(cx + Math.cos(a) * 9.6 * k, floorY, cz + Math.sin(a) * 9.6 * k));
        }
        return out;
    }

    @Override
    protected void puzzleBegin(ServerLevel level) {
        marksNeeded = 3 + Math.min(round(), 2);
        marksDone = 0;
        shuffle = 0;
        cycle = 0;
        lastCurrent = -1;
        iconKey = -2;
        deal();
        pickNext(level);
    }

    // 四つの印を三枚ずつ、板へ配りなおす
    private void deal() {
        List<Integer> bag = new ArrayList<>();
        for (int m = 0; m < MARK_KINDS; m++) {
            for (int i = 0; i < PER_MARK; i++) {
                bag.add(m);
            }
        }
        Collections.shuffle(bag, new java.util.Random(heart.getRandom().nextLong()));
        mark = new int[PANELS];
        for (int i = 0; i < PANELS; i++) {
            mark[i] = bag.get(i);
        }
        for (Node n : nodes) {
            n.done = false;
            n.propKey = "";
        }
    }

    // 次の印 (直前と同じにならない)
    private void pickNext(ServerLevel level) {
        int m;
        do {
            m = heart.getRandom().nextInt(MARK_KINDS);
        } while (m == lastCurrent);
        current = m;
        lastCurrent = m;
        updateIcon(level);
        tell(level, "message.skyabyss.domain.shell_match.next." + m, marksDone + 1, marksNeeded);
    }

    private int litOfCurrent() {
        int c = 0;
        for (Node n : nodes) {
            if (n.done && mark[n.index] == current) {
                c++;
            }
        }
        return c;
    }

    @Override
    protected boolean strike(ServerLevel level, Node node, ServerPlayer player) {
        if (shuffle > 0 || node.done || current < 0 || node.index >= PANELS) {
            return true;
        }
        if (mark[node.index] == current) {
            node.done = true;
            node.flash = 8;
            node.flashRgb = 0;
            int lit = litOfCurrent();
            tone(level, node.pos, SoundEvents.AMETHYST_BLOCK_CHIME, 2.5f, 0.7f + 0.25f * lit);
            tone(level, node.pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.9f + 0.2f * lit);
            burst(level, node, ParticleTypes.END_ROD, 12, 0.35, 0.04);
            if (lit >= PER_MARK) {
                marksDone++;
                shuffle = SHUFFLE_DELAY;
                tone(level, new Vec3(cx, floorY, cz), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.3f);
                if (marksDone < marksNeeded) {
                    tell(level, "message.skyabyss.domain.shell_match.match", marksDone, marksNeeded);
                }
            } else {
                tell(level, "message.skyabyss.domain.shell_match.step", lit, PER_MARK);
            }
        } else {
            // 違う印: 光っていた板が消える (傷はない)
            for (Node n : nodes) {
                if (n.done) {
                    n.done = false;
                    n.flash = 8;
                    n.flashRgb = 0x8A929C;
                    burst(level, n, ParticleTypes.SMOKE, 4, 0.2, 0.01);
                }
            }
            node.flash = 10;
            node.flashRgb = 0x8A929C;
            tone(level, node.pos, SoundEvents.NOTE_BLOCK_BASS.value(), 1.6f, 0.5f);
            tell(level, "message.skyabyss.domain.shell_match.wrong", current);
        }
        return true;
    }

    @Override
    protected void puzzleTick(ServerLevel level, int t) {
        strafe.update(cx, cz, inRoom());
        if (shuffle > 0) {
            shuffle--;
            if (shuffle == 0 && marksDone < marksNeeded) {
                deal();
                for (Node n : nodes) {
                    n.flash = 10;
                    n.flashRgb = 0xFFFFFF;
                }
                pickNext(level);
                tone(level, new Vec3(cx, floorY, cz), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 1.1f);
            }
        }
        if (t % 20 == 0 && icon != null && level.getEntity(icon) == null) {
            icon = null;
            iconKey = -2;
            updateIcon(level);
        }
    }

    @Override
    public int solvedCount() {
        return Math.min(NODES, marksNeeded <= 0 ? 0 : marksDone * NODES / marksNeeded);
    }

    @Override
    protected boolean complete() {
        return !nodes.isEmpty() && marksDone >= marksNeeded;
    }

    @Override
    protected Hint standing() {
        if (shuffle > 0 || current < 0) {
            return new Hint("gui.skyabyss.domain.shell_match.shuffle", marksDone, marksNeeded);
        }
        return new Hint("gui.skyabyss.domain.shell_match.find." + current, PER_MARK - litOfCurrent(), PER_MARK);
    }

    // ---------------------------------------------------------------- 見える物 (板 = 平たい箱 + 板の上の印の字)
    @Override
    protected boolean drawNode(ServerLevel level, Node n, int t) {
        int m = n.index < PANELS ? mark[n.index] : 0;
        int rgb = n.flash > 0 && n.flashRgb != 0 ? n.flashRgb : RGB[m];
        boolean lit = n.done;
        String key = m + "/" + lit + "/" + rgb;
        if (key.equals(n.propKey) && n.props.size() == 2) {
            return true;
        }
        n.propKey = key;
        Block block = lit ? Blocks.SEA_LANTERN : TILE[m];
        float h = lit ? 0.45f : 0.2f;
        String json = glyphJson(m);
        if (n.props.size() < 2) {
            removeProps(level, n);
            n.props.add(spawnDisplay(level, n.pos, block, rgb, -0.95f, 0f, -0.95f, 1.9f, h, 1.9f));
            n.props.add(spawnText(level, n.pos.add(0, 1.4, 0), json, 8f));
        } else {
            retarget(level, n.props.get(0), block, rgb, -0.95f, 0f, -0.95f, 1.9f, h, 1.9f);
            retext(level, n.props.get(1), json);
        }
        return true;
    }

    private static String glyphJson(int m) {
        return "{\"text\":\"" + GLYPH[m] + "\",\"color\":\"#" + String.format("%06X", RGB[m]) + "\",\"bold\":true}";
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) {
            l.add(FloatTag.valueOf(f));
        }
        return l;
    }

    private UUID spawnText(ServerLevel level, Vec3 pos, String json, float scale) {
        Display.TextDisplay d = EntityType.TEXT_DISPLAY.create(level);
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        tag.putString("text", json);
        tag.putString("billboard", "center");
        tag.putInt("background", 0);
        tag.putInt("line_width", 400);
        tag.putBoolean("see_through", false);
        tag.putFloat("view_range", 3f);
        tag.putFloat("shadow_radius", 0f);
        CompoundTag tr = new CompoundTag();
        tr.put("scale", floats(scale, scale, scale));
        tr.put("translation", floats(0, 0, 0));
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", tr);
        CompoundTag br = new CompoundTag();
        br.putInt("block", 15);
        br.putInt("sky", 15);
        tag.put("brightness", br);
        d.load(tag);
        d.setPos(pos.x, pos.y, pos.z);
        d.addTag(NODE_TAG);
        d.setNoGravity(true);
        level.addFreshEntity(d);
        return d.getUUID();
    }

    private void retext(ServerLevel level, UUID id, String json) {
        Entity e = level.getEntity(id);
        if (e instanceof Display.TextDisplay d) {
            CompoundTag tag = d.saveWithoutId(new CompoundTag());
            tag.putString("text", json);
            d.load(tag);
        }
    }

    // 心臓の上の大きな印
    private void updateIcon(ServerLevel level) {
        if (current < 0) {
            return;
        }
        if (icon == null || level.getEntity(icon) == null) {
            icon = spawnText(level, new Vec3(cx, floorY + 9.8, cz), glyphJson(current), 14f);
        } else if (iconKey != current) {
            retext(level, icon, glyphJson(current));
        }
        iconKey = current;
    }

    @Override
    protected void onNodesRemoved(ServerLevel level) {
        if (icon != null) {
            Entity e = level.getEntity(icon);
            if (e != null) {
                e.discard();
            }
            icon = null;
        }
        iconKey = -2;
        current = -1;
    }

    // 試験用: 心臓の上の印の生き物
    public UUID iconId() {
        return icon;
    }
}

package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.town.TownGuestEntity;
import jp.revati.skyabyss.town.TownSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 島 4「約束の島」(docs/design/50 の 4.2.4)。地形は data/skyabyss/finale/isle4.json (tools/gen/world/build_cloud_isle4.py。FinaleWorld の版 5)。
//   前の自分たち  四人の天幕の前 (印 self_<k>) に、月の四人の幻 (TownGuestEntity moon_past<k>) が立つ。話して「並ぶ」と合図 finale_promise_<k>
//                 (会話 dialogue/ch6/guest_moon_past<k>.json の fin_promise)。並んだ人が島と主の間にいる間、その人に付いて歩き、術で助ける:
//                   1 谷の私 = 盾の円 (まわり SHIELD_R の内の人は、敵からの傷が半分・押されない。主の審きの光も通らない)
//                   2 崖の私 = 引きつける (TAUNT_EVERY ごとに、敵の群れの方へ前に出て、まわりの敵の狙いを TAUNT_TICKS のあいだ自分へ)
//                   3 野の私 = 倒れた人を起こす (一人でも倒れる。RAISE_TICKS で起こす。一人ごとに RAISE_COOLDOWN に一度まで。Downed が聞く)
//                   4 淵の私 = 癒す (まわり SHIELD_R の内の人を 1 秒ごとに 1)
//                 付いて歩く相手 (先頭) は、主の間にいる人を先に選ぶ。並んだ人が島にいなければ天幕の前へ帰る
//   扉        月見の岩の扉 (印 door): 四人と並んだ人 (頼みの段 door 以降) を主の間へ。主の間の帰りの台で扉の前へ
//   光の橋    炉の島の主の間から下る橋 (印 bridge_gate)。炉の島の頼みを終えていない人は大炉の上の入口へ戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PromiseIsle {
    public static final String NAME = "isle4";
    public static final String QUEST = "main_finale_promise";
    public static final String TAG = "skyabyss_finale_selves";
    public static final double SHIELD_R = 3.5;
    static final int TAUNT_EVERY = 240;
    static final int TAUNT_TICKS = 120;
    static final double TAUNT_R = 14;
    static final int RAISE_TICKS = 60;
    static final int RAISE_COOLDOWN = 1200;
    static final double ARRIVE_NEAR = 10;
    static final double REGION_NEAR = 64;
    // 先頭 (k → 人)・崖の私の引きつけの終わり・起こしている人 (人 → 起きる時刻)・起こす術の次に使える時刻
    private static final Map<Integer, UUID> LEAD = new HashMap<>();
    private static final Map<UUID, Long> RAISING = new HashMap<>();
    private static final Map<UUID, Long> RAISE_READY = new HashMap<>();
    private static final java.util.Set<String> SEEN = new java.util.HashSet<>();
    private static long tauntUntil;
    private static UUID tauntLead;
    private static long nextTaunt;

    private PromiseIsle() {
    }

    public static FinaleTerrain terrain() {
        return FinaleTerrain.of(NAME);
    }

    public static BlockPos mark(String key) {
        return terrain().mark(key);
    }

    public static boolean onIsland(Vec3 p) {
        BlockPos c = mark("center");
        return Math.hypot((p.x - c.getX()) / 40.0, (p.z - c.getZ()) / 34.0) < 1.0 && p.y < mark("arena").getY() - 6;
    }

    // 島と主の間 (前の自分たちが付いて歩く所)
    public static boolean inRegion(Vec3 p) {
        return onIsland(p) || PromiseArena.box().contains(p) || SeatArena.box().contains(p);
    }

    static AABB area() {
        BlockPos c = mark("center");
        return new AABB(c).inflate(48, 40, 54).move(0, 10, -8);
    }

    // ---------------------------------------------------------------- 前の自分たち
    public static String guest(int k) {
        return "moon_past" + k;
    }

    public static List<TownGuestEntity> selves(ServerLevel level) {
        return level.getEntities(TownSetup.GUEST.get(), area(), e -> e.isAlive() && e.getTags().contains(TAG));
    }

    public static TownGuestEntity self(ServerLevel level, int k) {
        for (TownGuestEntity g : selves(level)) {
            if (guest(k).equals(g.guestId())) {
                return g;
            }
        }
        return null;
    }

    // その人が k と並んだか: 頼みを終えた・段 selves より先・段 selves で k の目的が済んだ
    public static boolean joined(ServerPlayer p, int k) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null) {
            return false;
        }
        if (e.done) {
            return true;
        }
        QuestDef def = QuestManager.quest(QUEST);
        int at = stageIndex(def, "selves");
        if (at < 0) {
            return false;
        }
        if (e.stage > at) {
            return true;
        }
        return e.stage == at && e.progress != null && e.progress.length >= k && e.progress[k - 1] > 0;
    }

    static int stageIndex(QuestDef def, String id) {
        for (int i = 0; def != null && i < def.stages().size(); i++) {
            if (def.stages().get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // 天幕の前に四人を立たせる (いなければ。島のそばに人がいるときだけ)
    static void standUp(ServerLevel level) {
        List<TownGuestEntity> all = selves(level);
        for (int k = 1; k <= 4; k++) {
            TownGuestEntity keep = null;
            for (TownGuestEntity g : all) {
                if (guest(k).equals(g.guestId())) {
                    if (keep == null) {
                        keep = g;
                    } else {
                        g.discard();
                    }
                }
            }
            if (keep != null) {
                continue;
            }
            TownGuestEntity g = TownSetup.GUEST.get().create(level);
            if (g == null) {
                continue;
            }
            BlockPos at = mark("self_" + k);
            BlockPos tent = mark("tent_" + k);
            g.setGuest(guest(k));
            g.setCustomName(Component.translatable("entity.skyabyss.finale_self_" + k));
            float yaw = (float) Math.toDegrees(Math.atan2(-(at.getX() - tent.getX()), at.getZ() - tent.getZ()));
            g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
            g.setHome(at);
            g.addTag(TAG);
            level.addFreshEntity(g);
        }
    }

    // 先頭を決めて付いて歩かせる。並んだ人が島にいなければ天幕の前へ
    static void lead(ServerLevel level) {
        List<ServerPlayer> here = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && inRegion(p.position())) {
                here.add(p);
            }
        }
        // 新しく並んだ人 (会話の合図は頼みの記録に残る。それを見て拾う)
        for (ServerPlayer p : here) {
            for (int k = 1; k <= 4; k++) {
                if (joined(p, k) && SEEN.add(p.getUUID() + "/" + k)) {
                    onJoined(p, k);
                }
            }
        }
        List<ServerPlayer> inArena = PromiseArena.present(level);
        for (int k = 1; k <= 4; k++) {
            TownGuestEntity g = self(level, k);
            if (g == null) {
                continue;
            }
            ServerPlayer cur = null;
            UUID id = LEAD.get(k);
            for (ServerPlayer p : here) {
                if (p.getUUID().equals(id) && joined(p, k)) {
                    cur = p;
                }
            }
            // 主の間で戦っている人を先に (誰が扉を通っても四人が来る)
            if (cur == null || !inArena.contains(cur)) {
                for (ServerPlayer p : inArena) {
                    if (joined(p, k)) {
                        cur = p;
                        break;
                    }
                }
            }
            if (cur == null) {
                double best = Double.MAX_VALUE;
                for (ServerPlayer p : here) {
                    double d = p.distanceToSqr(g);
                    if (joined(p, k) && d < best) {
                        best = d;
                        cur = p;
                    }
                }
            }
            if (cur == null) {
                LEAD.remove(k);
                if (g.follow() != null) {
                    g.setFollow(null);
                    BlockPos at = mark("self_" + k);
                    g.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                }
                continue;
            }
            LEAD.put(k, cur.getUUID());
            if (k == 2 && level.getGameTime() < tauntUntil) {
                continue;
            }
            if (!cur.getUUID().equals(g.follow())) {
                g.setFollow(cur.getUUID());
            }
            // 主の間と島のあいだは扉で飛ぶので、離れすぎたらそばへ
            if (g.distanceToSqr(cur) > 20 * 20) {
                g.teleportTo(cur.getX(), cur.getY(), cur.getZ());
                level.sendParticles(ParticleTypes.END_ROD, cur.getX(), cur.getY() + 1, cur.getZ(), 12, 0.4, 0.8, 0.4, 0.04);
            }
        }
    }

    // 付いて歩いている (術を使う) 前の自分
    public static TownGuestEntity active(ServerLevel level, int k) {
        // 巡礼者の座の第 3 形態で加勢している四人を先に (SeatArena)
        TownGuestEntity seat = SeatArena.ally(level, k);
        if (seat != null) {
            return seat;
        }
        TownGuestEntity g = self(level, k);
        return g != null && LEAD.containsKey(k) ? g : null;
    }

    // 術を使う前の自分の先頭 (座の加勢なら付いて歩いている人)
    static UUID leadId(ServerLevel level, int k) {
        TownGuestEntity seat = SeatArena.ally(level, k);
        return seat != null ? SeatArena.leadOf(seat) : LEAD.get(k);
    }

    // ---------------------------------------------------------------- 術
    // 盾の円の内か (谷の私のまわり)
    public static boolean shielded(Player p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL) {
            return false;
        }
        TownGuestEntity g = active(level, 1);
        return g != null && Math.hypot(p.getX() - g.getX(), p.getZ() - g.getZ()) <= SHIELD_R && Math.abs(p.getY() - g.getY()) < 3;
    }

    // 崖の私が引きつけている間はその人 (主の先触れの印もそちらへ落ちる)
    public static TownGuestEntity taunting(ServerLevel level) {
        return level.getGameTime() < tauntUntil ? active(level, 2) : null;
    }

    static void arts(ServerLevel level) {
        long now = level.getGameTime();
        // 谷の私: 盾の円の輪
        TownGuestEntity valley = active(level, 1);
        if (valley != null && now % 10 == 0) {
            for (int i = 0; i < 20; i++) {
                double a = i / 20.0 * Math.PI * 2 + now * 0.02;
                level.sendParticles(ParticleTypes.END_ROD, valley.getX() + Math.cos(a) * SHIELD_R, valley.getY() + 0.2, valley.getZ() + Math.sin(a) * SHIELD_R, 1, 0, 0, 0, 0);
            }
        }
        // 淵の私: 癒す
        TownGuestEntity rift = active(level, 4);
        if (rift != null && now % 20 == 0) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, rift.getBoundingBox().inflate(SHIELD_R, 2, SHIELD_R),
                    p -> p.isAlive() && !p.isSpectator() && !Downed.isDowned(p))) {
                if (p.getHealth() < p.getMaxHealth() && Math.hypot(p.getX() - rift.getX(), p.getZ() - rift.getZ()) <= SHIELD_R) {
                    p.heal(1.0f);
                    level.sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
                }
            }
        }
        taunt(level, now);
        raise(level, now);
        // 幻の光 (付いて歩く四人)
        if (now % 20 == 0) {
            for (int k = 1; k <= 4; k++) {
                TownGuestEntity g = active(level, k);
                if (g != null) {
                    level.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + 1.0, g.getZ(), 2, 0.25, 0.6, 0.25, 0.01);
                }
            }
        }
    }

    // 崖の私: 敵の群れの方へ前に出て、まわりの敵の狙いを自分へ
    static void taunt(ServerLevel level, long now) {
        TownGuestEntity cliff = active(level, 2);
        if (cliff == null) {
            return;
        }
        if (now < tauntUntil) {
            if (now % 20 == 0) {
                for (Mob m : level.getEntitiesOfClass(Mob.class, cliff.getBoundingBox().inflate(TAUNT_R), m -> m instanceof CloudMob && m.isAlive()
                        && !(m instanceof jp.revati.skyabyss.story.finale.mob.PilgrimPlume))) {
                    m.setTarget(cliff);
                }
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, cliff.getX(), cliff.getY() + 2.2, cliff.getZ(), 1, 0.2, 0.1, 0.2, 0);
            }
            if (now + 1 == tauntUntil && tauntLead != null) {
                cliff.setFollow(tauntLead);
            }
            return;
        }
        if (now < nextTaunt) {
            return;
        }
        UUID lead = leadId(level, 2);
        ServerPlayer p = lead == null ? null : (ServerPlayer) level.getPlayerByUUID(lead);
        if (p == null) {
            return;
        }
        List<CloudMob> foes = level.getEntitiesOfClass(CloudMob.class, p.getBoundingBox().inflate(TAUNT_R), m -> m.isAlive()
                && !(m instanceof jp.revati.skyabyss.story.finale.mob.PilgrimPlume) && m.getTarget() instanceof Player);
        Entity lord = !PromiseArena.lords(level).isEmpty() && PromiseArena.present(level).contains(p) ? PromiseArena.lords(level).get(0)
                : SeatArena.box().contains(p.position()) ? SeatArena.boss(level) : null;
        if (foes.isEmpty() && lord == null) {
            return;
        }
        // 敵の真ん中の方へ 4 前に出る (主の間なら主の方へ)
        Vec3 c = Vec3.ZERO;
        for (CloudMob m : foes) {
            c = c.add(m.position());
        }
        c = foes.isEmpty() ? lord.position() : c.scale(1.0 / foes.size());
        Vec3 dir = new Vec3(c.x - p.getX(), 0, c.z - p.getZ());
        dir = dir.lengthSqr() < 0.01 ? new Vec3(0, 0, -1) : dir.normalize();
        Vec3 at = p.position().add(dir.scale(4));
        BlockPos ground = BlockPos.containing(at);
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos q = ground.above(dy);
            if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir() && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)) {
                at = new Vec3(at.x, q.getY(), at.z);
                break;
            }
        }
        tauntLead = lead;
        cliff.setFollow(null);
        cliff.clearRestriction();
        cliff.getNavigation().stop();
        cliff.teleportTo(at.x, at.y, at.z);
        tauntUntil = now + TAUNT_TICKS;
        nextTaunt = now + TAUNT_EVERY;
        level.playSound(null, cliff.blockPosition(), CloudMobsSetup.SELF_TAUNT.get(), SoundSource.NEUTRAL, 1.4f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 24, 0.5, 0.8, 0.5, 0.06);
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, cliff.getBoundingBox().inflate(24), o -> o.isAlive())) {
            Notice.bar(o, Component.translatable("message.skyabyss.isle4.taunt"));
        }
    }

    // 野の私: 倒れた人のそばへ行って起こす
    static void raise(ServerLevel level, long now) {
        TownGuestEntity field = active(level, 3);
        RAISING.entrySet().removeIf(e -> {
            ServerPlayer p = (ServerPlayer) level.getPlayerByUUID(e.getKey());
            if (p == null || !p.isAlive() || !Downed.isDowned(p)) {
                return true;
            }
            if (now % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.6, p.getZ(), 3, 0.4, 0.3, 0.4, 0.02);
            }
            if (now < e.getValue()) {
                return false;
            }
            Downed.raise(p);
            level.playSound(null, p.blockPosition(), CloudMobsSetup.SELF_RAISE.get(), SoundSource.NEUTRAL, 1.4f, 1.0f);
            Notice.bar(p, Component.translatable("message.skyabyss.isle4.raised"));
            return true;
        });
        if (field == null) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (Downed.isDowned(p) && !RAISING.containsKey(p.getUUID()) && inRegion(p.position()) && p.distanceToSqr(field) < 40 * 40
                    && RAISE_READY.getOrDefault(p.getUUID(), 0L) <= now) {
                RAISING.put(p.getUUID(), now + RAISE_TICKS);
                RAISE_READY.put(p.getUUID(), now + RAISE_COOLDOWN);
                field.setFollow(null);
                field.clearRestriction();
                field.teleportTo(p.getX() + 0.8, p.getY(), p.getZ() + 0.4);
                field.getLookControl().setLookAt(p);
                Notice.narrate(p, "message.skyabyss.isle4.raising");
                UUID lead = leadId(level, 3);
                if (lead != null) {
                    jp.revati.skyabyss.story.ch1.Tasks.later(RAISE_TICKS + 5, () -> {
                        if (field.isAlive()) {
                            field.setFollow(lead);
                        }
                    });
                }
            }
        }
    }

    // 一人でも倒れてよいか (Downed.mayDown が聞く): 島か主の間にいて、野の私が付いて歩いていて、起こす術を使える
    public static boolean canRaise(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL || !inRegion(p.position())) {
            return false;
        }
        TownGuestEntity field = active(level, 3);
        return field != null && p.distanceToSqr(field) < 40 * 40 && RAISE_READY.getOrDefault(p.getUUID(), 0L) <= level.getGameTime();
    }

    // 倒れたままでよいか (Downed.tick が聞く): 野の私が起こしに来ている・起こせる
    public static boolean holds(ServerPlayer p) {
        return RAISING.containsKey(p.getUUID()) || canRaise(p);
    }

    // 盾の円の内は、敵からの傷が半分 (主の審きの光は PilgrimPlume が別に止める)
    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FinaleWorld.in(p) && event.getSource().getEntity() instanceof Mob && shielded(p)) {
            event.setAmount(event.getAmount() * 0.5f);
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.5, 0.4, 0.04);
        }
    }

    @SubscribeEvent
    static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FinaleWorld.in(p) && shielded(p)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL
                || !FinaleWorld.built(level.getServer()) || FinaleWorld.version(level.getServer()) < 5) {
            return;
        }
        long now = level.getGameTime();
        boolean near = false;
        BlockPos c = mark("center");
        for (ServerPlayer p : level.players()) {
            near |= !p.isSpectator() && p.distanceToSqr(c.getX(), c.getY(), c.getZ()) < REGION_NEAR * REGION_NEAR;
        }
        if (now % 10 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    gate(p);
                }
            }
        }
        if (!near) {
            return;
        }
        if (now % 20 == 0) {
            standUp(level);
            lead(level);
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator()) {
                    visit(p);
                }
            }
            PromiseArena.tick(level);
        }
        // 座で四人が加勢している間は、術は座の見張り (SeatArena) が使う
        if (SeatArena.form() != 3 && SeatArena.form() != 4) {
            arts(level);
        }
        if (now % 40 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && !p.isCreative()) {
                    PromiseArena.spawn(level, p);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 光の橋の関所 (炉の島の主の間 → 島 4)
    static boolean inGate(Vec3 p) {
        BlockPos g = mark("bridge_gate");
        return Math.abs(p.x - (g.getX() + 0.5)) <= 2.5 && p.z <= g.getZ() + 4 && p.z >= g.getZ() - 12 && Math.abs(p.y - g.getY()) <= 4;
    }

    public static boolean bridgeOpen(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(ForgeIsle.QUEST);
        return e != null && e.done;
    }

    static void gate(ServerPlayer p) {
        if (!inGate(p.position()) || bridgeOpen(p)) {
            return;
        }
        BlockPos to = ForgeIsle.mark("arena_entry");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
        }
        p.resetFallDistance();
        Notice.narrate(p, "message.skyabyss.cloudsea.bridge_shut");
    }

    // ---------------------------------------------------------------- 着く所・天幕の記憶
    static void visit(ServerPlayer p) {
        BlockPos arrive = mark("arrive");
        if (p.distanceToSqr(arrive.getX() + 0.5, arrive.getY(), arrive.getZ() + 0.5) < ARRIVE_NEAR * ARRIVE_NEAR) {
            Quests.signal(p, "finale_isle4_arrive");
        }
    }

    // 並んだとき (会話の合図のあと、lead が拾う): 天幕の記憶の頁と、付いて歩く先頭。知らせは並んでいる段のあいだだけ
    static void onJoined(ServerPlayer p, int k) {
        if (!(p.level() instanceof ServerLevel level)) {
            return;
        }
        if (Lore.unlock(p, "finale/promise_echo_" + k, true)) {
            Notice.narrate(p, "message.skyabyss.isle4.echo." + k);
        }
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null || e.done || e.stage != stageIndex(QuestManager.quest(QUEST), "selves") && e.stage != stageIndex(QuestManager.quest(QUEST), "door")) {
            return;
        }
        LEAD.put(k, p.getUUID());
        TownGuestEntity g = self(level, k);
        if (g != null) {
            g.setFollow(p.getUUID());
            level.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + 1, g.getZ(), 30, 0.4, 0.9, 0.4, 0.06);
        }
        level.playSound(null, p.blockPosition(), CloudMobsSetup.SELF_JOIN.get(), SoundSource.NEUTRAL, 1.2f, 1.0f);
        Notice.bar(p, Component.translatable("message.skyabyss.isle4.joined." + k));
    }

    // ---------------------------------------------------------------- 右クリック (扉・帰りの台)
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !FinaleWorld.in(p)
                || FinaleWorld.version(p.server) < 5) {
            return;
        }
        BlockPos pos = event.getPos();
        ServerLevel level = p.serverLevel();
        if (pos.equals(mark("door"))) {
            event.setCanceled(true);
            if (!doorOpen(p)) {
                Notice.narrate(p, "message.skyabyss.isle4.door.shut");
                return;
            }
            BlockPos to = mark("arena_entry");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 180f, 0f);
            Quests.signal(p, "finale_promise_door");
            PromiseArena.arrive(level, p);
        } else if (pos.equals(mark("arena_back"))) {
            event.setCanceled(true);
            BlockPos to = mark("door_front");
            p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0f, 0f);
        }
    }

    // 扉が開くか: 頼みの段 door 以降 (四人と並んだ)・頼みを終えた人
    public static boolean doorOpen(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        if (e == null) {
            return false;
        }
        return e.done || e.stage >= stageIndex(QuestManager.quest(QUEST), "door");
    }

    // 開発用 (/skyabyss cloudsea selves): 四人と並んだことにして (頼みを段 door へ)、そばへ呼ぶ
    public static boolean join(ServerPlayer p) {
        if (!FinaleWorld.in(p)) {
            return false;
        }
        if (QuestLog.of(p).entry(QUEST) == null) {
            Quests.start(p, QUEST);
        }
        Quests.setStage(p, QUEST, "door");
        standUp(p.serverLevel());
        for (int k = 1; k <= 4; k++) {
            LEAD.put(k, p.getUUID());
            TownGuestEntity g = self(p.serverLevel(), k);
            if (g != null) {
                g.setFollow(p.getUUID());
                g.teleportTo(p.getX() + k - 2.5, p.getY(), p.getZ() + 1);
            }
        }
        return true;
    }

    // 自己テスト向け: 先頭と術の時刻を片付ける
    static void reset() {
        LEAD.clear();
        RAISING.clear();
        RAISE_READY.clear();
        SEEN.clear();
        tauntUntil = 0;
        nextTaunt = 0;
    }
}

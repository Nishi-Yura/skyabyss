package jp.revati.skyabyss.abyss.arts;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssPressure;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import jp.revati.skyabyss.abyss.arts.net.ArtCooldownPayload;

// 深淵術 (docs/design/36 の 5 節)。覚える・上げる・使う。サーバーで決める (鍵は押した知らせだけ)。
// 覚えるのは溜まりの印の 6 ブロック以内 (一度だけ)。灰の溜まり (pool_ash) で深淵術師の木を開き、しゃがみながら鍵で上げる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssArts {
    // 近さ (ブロック) と、周期 (tick)。覚える・木を開く確かめは 10 tick ごと
    static final double POOL_REACH = 6;
    static final int CHECK_EVERY = 10;
    // 木の名前 (SkillTree.ABYSS の id)
    public static final String TREE_ID = SkillTree.ABYSS.id;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);

    // 深淵術の記録 (持ち主と同期。死んでも持ち越す)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ArtData>> DATA = ATTACHMENTS.register("abyss_arts",
            () -> AttachmentType.serializable(ArtData::new)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, ArtData.STREAM_CODEC)
                    .build());

    // 重さの術の緩み (人ごと・サーバーだけ。終わりの時刻と値)
    private record Relief(long until, double value) {
    }

    private static final Map<UUID, Relief> RELIEF = new HashMap<>();
    // 術ごとの待ちの終わり (ゲームの tick)
    private static final Map<UUID, long[]> COOLDOWN = new HashMap<>();

    public enum Result {
        OK, NOT_LEARNED, COOLDOWN, NO_MANA, FAILED, RANKED, NOT_NEAR_ASH, NO_TREE, NO_POINTS, MAX_RANK
    }

    private AbyssArts() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        // 圧 (story/ch5/AbyssPressure) へつなぐ。重さの緩みと、闇の声が見る光
        AbyssPressure.weightRelief = AbyssArts::weightRelief;
        AbyssPressure.lightBonus = p -> AbyssLight.lampAt(p.level(), p.blockPosition());
    }

    public static ArtData data(Player p) {
        return p.getData(DATA.get());
    }

    // 重さの術の緩み (0〜1)。使っていなければ 0。上昇の重み (AbyssPressure) が読む
    public static double weightRelief(ServerPlayer p) {
        Relief r = RELIEF.get(p.getUUID());
        return r == null || p.level().getGameTime() >= r.until ? 0 : r.value;
    }

    // 待ちの残り (tick。0 = 使える)
    public static long cooldownLeft(ServerPlayer p, AbyssArt a, long now) {
        long[] c = COOLDOWN.get(p.getUUID());
        return c == null ? 0 : Math.max(0, c[a.ordinal()] - now);
    }

    // 溜まりの印の近く (6 ブロック以内・深淵の中)
    public static boolean nearPool(ServerPlayer p, String pool) {
        if (!AbyssWorld.in(p)) {
            return false;
        }
        BlockPos at = AbyssBuild.mark(pool);
        return at != null && p.blockPosition().distSqr(at) <= POOL_REACH * POOL_REACH;
    }

    // 覚える (溜まりに入ったとき・開発用)。覚えた新しい術なら true
    public static boolean learn(ServerPlayer p, AbyssArt a) {
        ArtData d = data(p);
        if (d.has(a)) {
            return false;
        }
        d.ranks[a.ordinal()] = 1;
        p.syncData(DATA.get());
        Notice.alert(p, "message.skyabyss.art.learned", Component.translatable(a.nameKey()));
        return true;
    }

    // 灰の溜まりで深淵術師の木を開く (開発用も同じ)。開いたら true
    public static boolean openTree(ServerPlayer p) {
        SkillData sd = SkillManager.get(p);
        if (sd.unlockedTrees.contains(TREE_ID)) {
            return false;
        }
        sd.unlockedTrees.add(TREE_ID);
        SkillManager.sync(p);
        Notice.alert(p, "message.skyabyss.art.tree_open");
        return true;
    }

    // 鍵を押した (サーバーで決める)。しゃがみながら、灰の溜まりの近くなら上げる。ほかは使う
    public static Result press(ServerPlayer p, AbyssArt a) {
        if (p.isShiftKeyDown() && nearPool(p, "pool_ash")) {
            return up(p, a);
        }
        return cast(p, a);
    }

    // ランクを一つ上げる (ランク 1 → 2 は点 1、2 → 3 は点 2)。木を開いたあとだけ
    public static Result up(ServerPlayer p, AbyssArt a) {
        ArtData d = data(p);
        int rank = d.rank(a);
        if (rank == 0) {
            deny(p, "message.skyabyss.art.not_learned", a);
            return Result.NOT_LEARNED;
        }
        if (rank >= AbyssArt.MAX_RANK) {
            deny(p, "message.skyabyss.art.max_rank", a);
            return Result.MAX_RANK;
        }
        SkillData sd = SkillManager.get(p);
        if (!sd.treeOpen(SkillTree.ABYSS)) {
            deny(p, "message.skyabyss.art.need_tree");
            return Result.NO_TREE;
        }
        int cost = rank;
        if (sd.unspentPoints(SkillManager.level(p)) < cost) {
            deny(p, "message.skyabyss.art.no_points");
            return Result.NO_POINTS;
        }
        d.ranks[a.ordinal()] = rank + 1;
        sd.artPoints += cost;
        SkillManager.sync(p);
        p.syncData(DATA.get());
        p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.2f);
        Notice.alert(p, "message.skyabyss.art.ranked", Component.translatable(a.nameKey()), rank + 1);
        return Result.RANKED;
    }

    // 術を使う。ランクと待ち・マナを見て、効きを出す
    public static Result cast(ServerPlayer p, AbyssArt a) {
        if (!p.isAlive() || p.isSpectator()) {
            return Result.FAILED;
        }
        int rank = data(p).rank(a);
        if (rank == 0) {
            deny(p, "message.skyabyss.art.not_learned", a);
            return Result.NOT_LEARNED;
        }
        long now = p.level().getGameTime();
        if (cooldownLeft(p, a, now) > 0) {
            return Result.COOLDOWN;
        }
        SkillManager.settleMana(p);
        SkillData sd = SkillManager.get(p);
        if (sd.mana + 1e-4 < a.mana) {
            Notice.bar(p, ModText.deny("message.skyabyss.skill.no_mana"));
            p.playNotifySound(jp.revati.skyabyss.rpg.skill.Skills.SOUND_FAIL.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
            return Result.NO_MANA;
        }
        int r = rank - 1;
        switch (a) {
            case LIGHT -> light(p, r);
            case WEIGHT -> weight(p, r);
            case HEARTBEAT -> heartbeat(p, r);
        }
        sd.mana = (float) Math.max(0, sd.mana - a.mana);
        SkillManager.sendMana(p, true);
        setCooldown(p, a, now + a.cooldownTicks);
        return Result.OK;
    }

    private static void setCooldown(ServerPlayer p, AbyssArt a, long until) {
        COOLDOWN.computeIfAbsent(p.getUUID(), k -> new long[AbyssArt.values().length])[a.ordinal()] = until;
        ArtCooldownPayload.send(p, a.ordinal(), until);
    }

    // 灯り: 狙った所 (8 ブロックまで。なければ前 4 ブロック) に置く。その灯りの中の敵は輪郭が見える。使った人は夜目
    private static void light(ServerPlayer p, int r) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(8));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        BlockPos at = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? BlockPos.containing(eye.add(look.scale(4)))
                : hit.getBlockPos().relative(hit.getDirection());
        double radius = AbyssArt.LIGHT_RADIUS[r];
        long until = p.server.overworld().getGameTime() + AbyssArt.LIGHT_LIFE_SECONDS[r] * 20L;
        AbyssLight.add(level, at, radius, until, p.getUUID());
        Vec3 c = at.getCenter();
        AABB box = new AABB(c, c).inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive() && e.position().distanceTo(c) < radius)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, AbyssArt.LIGHT_GLOW_TICKS, 0, false, false));
        }
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, AbyssArt.LIGHT_NIGHT_VISION_TICKS, 0, false, false));
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.4f);
        Notice.bar(p, Component.translatable("message.skyabyss.art.light_placed"));
    }

    // 重さ: 上昇の重みを緩める (ランクの値・時間)。使った人だけ
    private static void weight(ServerPlayer p, int r) {
        long now = p.level().getGameTime();
        RELIEF.put(p.getUUID(), new Relief(now + AbyssArt.WEIGHT_LIFE_SECONDS[r] * 20L, AbyssArt.WEIGHT_RELIEF[r]));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.7f);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 16, 0.4, 0.1, 0.4, 0.02);
        Notice.bar(p, Component.translatable("message.skyabyss.art.weight_on", (int) Math.round(AbyssArt.WEIGHT_RELIEF[r] * 100)));
    }

    // 鼓動: 24 ブロック内の敵の輪郭を見せる (壁の向こうも)。音は使った人だけで、近いほど大きい。8 ブロック内の人を癒す
    private static void heartbeat(ServerPlayer p, int r) {
        ServerLevel level = p.serverLevel();
        AABB box = p.getBoundingBox().inflate(AbyssArt.HEAR_RADIUS);
        double nearest = Double.MAX_VALUE;
        int heard = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive())) {
            double d = e.distanceTo(p);
            if (d > AbyssArt.HEAR_RADIUS) {
                continue;
            }
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, AbyssArt.HEAR_GLOW_TICKS, 0, false, false));
            nearest = Math.min(nearest, d);
            heard++;
        }
        if (heard > 0) {
            float vol = (float) Mth.clamp(1 - nearest / AbyssArt.HEAR_RADIUS, 0.3, 1.0);
            p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, vol, 1f);
        }
        int healed = 0;
        for (ServerPlayer ally : java.util.List.copyOf(level.players())) {
            if (ally.isAlive() && ally.distanceTo(p) <= AbyssArt.HEAL_RADIUS) {
                ally.heal(AbyssArt.HEAL[r]);
                healed++;
            }
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 0.6f);
        Notice.bar(p, Component.translatable("message.skyabyss.art.heartbeat", heard, healed));
    }

    private static void deny(ServerPlayer p, String key, AbyssArt a) {
        Notice.bar(p, ModText.deny(key, Component.translatable(a.nameKey())));
        p.playNotifySound(jp.revati.skyabyss.rpg.skill.Skills.SOUND_FAIL.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    private static void deny(ServerPlayer p, String key) {
        Notice.bar(p, ModText.deny(key));
        p.playNotifySound(jp.revati.skyabyss.rpg.skill.Skills.SOUND_FAIL.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    // 溜まりの確かめ (10 tick ごと): 近くの溜まりの術を覚える。灰の溜まりでは木を開く
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        AbyssLight.tick(server.overworld().getGameTime());
        if (server.getTickCount() % CHECK_EVERY != 0) {
            return;
        }
        for (ServerPlayer p : java.util.List.copyOf(server.getPlayerList().getPlayers())) {
            reconcile(p);
            if (!AbyssWorld.in(p) || p.isSpectator() || !p.isAlive()) {
                continue;
            }
            for (AbyssArt a : AbyssArt.values()) {
                if (!data(p).has(a) && nearPool(p, a.pool)) {
                    learn(p, a);
                }
            }
            if (nearPool(p, "pool_ash")) {
                openTree(p);
            }
        }
    }

    // 術のランクに使ったはずの点 (ランク r → r + 1 は r 点。ランク R までで R (R - 1) / 2)
    static int spent(ArtData d) {
        int sum = 0;
        for (int r : d.ranks) {
            if (r > 1) {
                sum += r * (r - 1) / 2;
            }
        }
        return sum;
    }

    // 技を振り直して点が戻ったら (SkillData.clearSkills が artPoints を 0 に)、術のランクを 1 へ戻す (覚えた術は残す)
    public static boolean reconcile(ServerPlayer p) {
        ArtData d = data(p);
        SkillData sd = SkillManager.get(p);
        if (sd.artPoints >= spent(d)) {
            return false;
        }
        for (int i = 0; i < d.ranks.length; i++) {
            if (d.ranks[i] > 1) {
                d.ranks[i] = 1;
            }
        }
        sd.artPoints = 0;
        SkillManager.sync(p);
        p.syncData(DATA.get());
        return true;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            RELIEF.remove(p.getUUID());
            COOLDOWN.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        RELIEF.clear();
        COOLDOWN.clear();
        AbyssLight.clear();
    }
}

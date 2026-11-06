package jp.revati.skyabyss.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import jp.revati.skyabyss.util.PlayerMaps;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 魔法使いの通常攻撃の魔弾 (2026-11-06 の声「魔法使いが弱すぎるので、通常攻撃もある程度、遠距離攻撃出来てもいいのでは」)。
// 杖 (職が魔法使いの武器) を持って、手の届く敵のいない所を左クリックすると、見ている向きへ RANGE ブロックまで光の弾が走り、最初に当たった敵に
// 近接の一撃と同じ傷を入れる (combat/MeleeHits を通す: 溜め²・同じ敵を殴れる間・満ちた一撃だけの効き目と回復。武器の効き目もそのまま)。
// 手の届く敵を押したときは、今までどおり杖で打つ (weapon/WeaponStrikes)。壁は抜けない。撃つたびに溜めは空になる。
// 手元は client/weapon/StaffBoltClient が左クリックを見て、この合図 (Cast) を送る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StaffBolts {
    public static final double RANGE = 14.0;
    // 続けて撃てる間 (tick)。左クリックを押し続けても弾が出続けないように
    static final int MIN_GAP = 4;
    public static final int MIN_GAP_CLIENT = MIN_GAP;
    // 当たりの太さ (敵の当たり箱をこれだけ広げて、細い光の線でも当てやすくする)
    static final double FAT = 0.45;

    private static final Map<UUID, Long> LAST = PlayerMaps.track(new HashMap<>());

    private StaffBolts() {
    }

    // 人 → サーバー: 魔弾を撃つ
    public record Cast() implements CustomPacketPayload {
        public static final Type<Cast> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "staff_bolt"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cast> STREAM_CODEC = StreamCodec.unit(new Cast());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("staff-1").playToServer(Cast.TYPE, Cast.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer p) {
                cast(p);
            }
        }));
    }

    // 撃つ。当てた敵 (外れたら null)
    @Nullable
    public static Entity cast(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || Job.of(p.getMainHandItem()) != Job.MAGE) {
            return null;
        }
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < MIN_GAP) {
            return null;
        }
        LAST.put(p.getUUID(), now);
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        Vec3 far = eye.add(look.scale(RANGE));
        HitResult wall = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, end, new AABB(eye, end).inflate(1.0),
                e -> SkillActions.isFoe(p, e), (float) FAT);
        Entity target = hit == null ? null : hit.getEntity();
        Vec3 stop = target == null ? end : hit.getLocation();
        trail(level, eye.add(look.scale(0.6)).add(0, -0.25, 0), stop);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.6f + p.getRandom().nextFloat() * 0.2f);
        if (target != null) {
            jp.revati.skyabyss.weapon.WeaponStrikes.attackNow(p, target);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, stop.x, stop.y, stop.z, 8, 0.2, 0.2, 0.2, 0.15);
        }
        // 撃つたびに溜めは空になる (振りと同じ)
        p.resetAttackStrengthTicker();
        return target;
    }

    // 光の線 (0.5 ブロックおき。見ている人みなに見える)
    static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int n = Math.max(1, (int) Math.ceil(d.length() / 0.5));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale(i / (double) n));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    // 自己テスト用: 続けて撃てる間を忘れる
    static void forgetForTest(ServerPlayer p) {
        LAST.remove(p.getUUID());
    }
}

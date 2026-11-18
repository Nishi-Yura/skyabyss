package jp.revati.skyabyss.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.fx.FxParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

// 攻撃と技の粒の動き (docs/design/59 の 2 章。種類は fx/FxParticles)。絵は 2 色のドットなので、色は付けない (白のまま)。
//   破片 SHARD: 重さで落ち、床で止まって転がる。回りながら終わりに薄れる
//   光の粒 GLOW: 重さなし、ゆっくり止まりながら縮んで薄れる。暗い所でも明るい
//   煙 SMOKE:   ゆっくり昇り、ふくらみながら薄れる
// 上限: 画面にある攻撃の粒は MAX_ALIVE まで。超えたら作らない (粒の設定が「減らす」「最小」なら上限も下げる)。
// 目の近く (FADE_NEAR〜FADE_FAR) では薄れ、もっと近いと描かない (目の前を塞がない)
public class FxParticle extends TextureSheetParticle {
    public static final int MAX_ALIVE = 300;
    private static final double FADE_FAR = 2.6;
    private static final double FADE_NEAR = 1.0;

    private static int alive;

    private final FxParticles.Kind kind;
    private final float spin;
    private final float size0;

    FxParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, FxParticles.Kind kind) {
        super(level, x, y, z, 0, 0, 0);
        this.kind = kind;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        switch (kind) {
            case SHARD -> {
                gravity = 0.9f;
                friction = 0.96f;
                hasPhysics = true;
                lifetime = 18 + random.nextInt(14);
                quadSize = 0.09f + random.nextFloat() * 0.06f;
                spin = (random.nextFloat() - 0.5f) * 0.5f;
            }
            case GLOW -> {
                gravity = 0f;
                friction = 0.88f;
                hasPhysics = false;
                lifetime = 12 + random.nextInt(10);
                quadSize = 0.07f + random.nextFloat() * 0.05f;
                spin = (random.nextFloat() - 0.5f) * 0.1f;
            }
            default -> {
                gravity = -0.02f;
                friction = 0.9f;
                hasPhysics = false;
                lifetime = 24 + random.nextInt(16);
                quadSize = 0.14f + random.nextFloat() * 0.08f;
                spin = (random.nextFloat() - 0.5f) * 0.06f;
            }
        }
        this.size0 = quadSize;
        pickSprite(sprites);
        alive++;
    }

    public static int alive() {
        return alive;
    }

    @Override
    public void remove() {
        if (!removed) {
            alive = Math.max(0, alive - 1);
        }
        super.remove();
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        float t = (float) age / lifetime;
        switch (kind) {
            case SHARD -> {
                if (onGround) {
                    xd *= 0.6;
                    zd *= 0.6;
                } else {
                    roll += spin;
                }
                alpha = t < 0.7f ? 1f : Math.max(0f, 1f - (t - 0.7f) / 0.3f);
            }
            case GLOW -> {
                roll += spin;
                quadSize = size0 * (1f - 0.6f * t);
                alpha = t < 0.5f ? 1f : Math.max(0f, 1f - (t - 0.5f) / 0.5f);
            }
            default -> {
                roll += spin;
                quadSize = size0 * (1f + 1.4f * t);
                alpha = 0.8f * (t < 0.2f ? t / 0.2f : Math.max(0f, 1f - (t - 0.2f) / 0.8f));
            }
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 eye = camera.getPosition();
        double px = Mth.lerp(partialTicks, xo, x) - eye.x;
        double py = Mth.lerp(partialTicks, yo, y) - eye.y;
        double pz = Mth.lerp(partialTicks, zo, z) - eye.z;
        double d = Math.sqrt(px * px + py * py + pz * pz);
        if (d < FADE_NEAR) {
            return;
        }
        float keep = alpha;
        if (d < FADE_FAR) {
            alpha *= (float) ((d - FADE_NEAR) / (FADE_FAR - FADE_NEAR));
        }
        super.render(buffer, camera, partialTicks);
        alpha = keep;
    }

    @Override
    protected int getLightColor(float partialTick) {
        // 光の粒は暗い所でも明るい。破片と煙はその場の明るさ
        return kind == FxParticles.Kind.GLOW ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // 画面の上限 (粒の設定で下げる)
    static int cap() {
        return switch (net.minecraft.client.Minecraft.getInstance().options.particles().get()) {
            case ALL -> MAX_ALIVE;
            case DECREASED -> MAX_ALIVE / 2;
            case MINIMAL -> MAX_ALIVE / 5;
        };
    }

    public record Provider(SpriteSet sprites, FxParticles.Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            if (alive >= cap()) {
                return null;
            }
            return new FxParticle(level, x, y, z, dx, dy, dz, sprites, kind);
        }
    }

    public static void register(RegisterParticleProvidersEvent event) {
        for (var e : FxParticles.all().entrySet()) {
            for (var k : e.getValue().entrySet()) {
                FxParticles.Kind kind = k.getKey();
                event.registerSpriteSet(k.getValue().get(), sprites -> new Provider(sprites, kind));
            }
        }
    }
}

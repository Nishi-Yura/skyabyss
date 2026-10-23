package jp.revati.skyabyss.client.combat;

import com.mojang.blaze3d.vertex.VertexConsumer;

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

// 雷翼の心臓の「羽の雨」などの羽の粒 (AttackFx の雷の落ちる粒)。絵は textures/particle/storm_feather_*.png (tools/gen/ui/gen_storm_feather_particle.py)。
// 前は end_rod (白く光る四角) で、頭の上から降るので目の前を大きな白い四角が横切っていた。
//   大きさ: 0.16〜0.24 ブロックの小さな羽 (end_rod の 1/2 ほど)
//   動き:   与えられた速さで降りながら、左右に揺れて回る (羽らしく、まっすぐ落ちない)
//   近く:   目から 3 ブロックより近いと薄れ、1.2 ブロックより近いと描かない (目の前を塞がない)
public class StormFeatherParticle extends TextureSheetParticle {
    private static final double FADE_FAR = 3.0;
    private static final double FADE_NEAR = 1.2;
    private final float swayPhase;
    private final float swaySpeed;
    private final float spin;

    StormFeatherParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.friction = 0.96f;
        this.gravity = 0;
        this.hasPhysics = false;
        this.lifetime = 26 + random.nextInt(12);
        this.quadSize = 0.08f + random.nextFloat() * 0.04f;
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.swaySpeed = 0.25f + random.nextFloat() * 0.15f;
        this.spin = (random.nextFloat() - 0.5f) * 0.18f;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        // 光の色を少しだけ揺らす (白〜氷の青)
        float tint = 0.9f + random.nextFloat() * 0.1f;
        setColor(tint, tint, 1f);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        // 降りながら左右に揺れる (速さに足すので、与えられた向きは保つ)
        float s = Mth.sin(age * swaySpeed + swayPhase);
        xd += s * 0.012;
        zd += Mth.cos(age * swaySpeed * 0.8f + swayPhase) * 0.01;
        // 降る速さは羽らしく、だんだん遅くなる (下限つき)
        yd = Math.max(yd, -0.55);
        float t = (float) age / lifetime;
        alpha = t < 0.75f ? 1f : Math.max(0f, 1f - (t - 0.75f) / 0.25f);
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
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // 雷の光をうっすら帯びる (暗い部屋でも羽の形が読める)
    @Override
    protected int getLightColor(float partialTick) {
        return 0xD000D0;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new StormFeatherParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}

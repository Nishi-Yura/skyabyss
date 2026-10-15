package jp.revati.skyabyss.rpg.client.skill;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

// 技の光の粒。どちらも自分で光り (暗がりでも見える)、小さく、すぐ消える (控えめに)。
// 絵は textures/particle/{rune_spark,sky_mote}_*.png (tools/gen_skill_assets.py)
public final class SkillParticles {
    private SkillParticles() {
    }

    static class Glow extends TextureSheetParticle {
        private final SpriteSet sprites;

        Glow(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
                int life, float size, float friction, float rise) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
            this.xd = dx;
            this.yd = dy + rise;
            this.zd = dz;
            this.friction = friction;
            this.gravity = 0;
            this.hasPhysics = false;
            this.lifetime = life + random.nextInt(Math.max(1, life / 3));
            this.quadSize = size * (0.8f + random.nextFloat() * 0.4f);
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            alpha = t < 0.6f ? 1f : Math.max(0f, 1f - (t - 0.6f) / 0.4f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }
    }

    // 碑文の青緑の火花: 少し浮き上がって消える
    public record RuneSparkProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new Glow(level, x, y, z, dx, dy, dz, sprites, 12, 0.11f, 0.86f, 0.01f);
        }
    }

    // 空の淡い粒: 与えられた速さのまま流れる (驟雨では真下へ降る)
    public record SkyMoteProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            Glow g = new Glow(level, x, y, z, dx, dy, dz, sprites, 14, 0.09f, 0.97f, 0f);
            if (dy < -0.5) {
                // 降る光は、地面に着くころに消える長さ
                g.setLifetime(6 + level.random.nextInt(3));
            }
            return g;
        }
    }
}

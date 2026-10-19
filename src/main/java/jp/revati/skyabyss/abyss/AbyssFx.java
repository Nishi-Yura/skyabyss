package jp.revati.skyabyss.abyss;

import jp.revati.skyabyss.entity.HeartBoss;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 深淵の心臓の見た目の粒 (docs/design/31 の B04)。共通は「内へ吸い込まれる紫の粒 (REVERSE_PORTAL)」と暗い紫の塵、心臓ごとに一種類足す。
// 暗い染めはクライアントの描画 (LowPolyGeoRenderer) 側。予告の邪魔にならないよう、粒は核のまわりだけ・少なめ。
public final class AbyssFx {
    public static final ParticleOptions DUST = new DustParticleOptions(new Vector3f(0.30f, 0.08f, 0.48f), 1.5f);

    private AbyssFx() {
    }

    public static void aura(ServerLevel server, HeartBoss heart, String theme) {
        Vec3 core = heart.focusPoint();
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 5, 1.8, 1.4, 1.8, 0.03);
        server.sendParticles(DUST, core.x, core.y - 0.5, core.z, 3, 2.2, 1.6, 2.2, 0.0);
        ParticleOptions extra = switch (theme) {
            case "tide" -> ParticleTypes.SQUID_INK;
            case "storm" -> ParticleTypes.ELECTRIC_SPARK;
            case "ember" -> ParticleTypes.SOUL_FIRE_FLAME;
            default -> ParticleTypes.SQUID_INK;
        };
        server.sendParticles(extra, core.x, core.y, core.z, 2, 1.6, 1.2, 1.6, 0.02);
    }

    // 技の落ちる点の粒 (予告が満ちて当たる瞬間)
    public static void burst(ServerLevel server, double x, double y, double z) {
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.6, z, 14, 0.5, 0.6, 0.5, 0.12);
        server.sendParticles(DUST, x, y + 0.4, z, 10, 0.6, 0.5, 0.6, 0.0);
    }
}

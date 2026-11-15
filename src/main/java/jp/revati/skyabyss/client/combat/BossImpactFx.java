package jp.revati.skyabyss.client.combat;

import org.joml.Vector3f;

import jp.revati.skyabyss.client.entity.PaperCutLayer;
import jp.revati.skyabyss.fx.FxParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 心臓の後のボスの技が当たった瞬間 (docs/design/59「攻撃の見せ方の決まり」の 当たった瞬間)。
// 床を走る輪は光 (AttackLightFx の当たりの輪) の係なので、ここは主ごとの音と、主の属性の少しの粒 (AttackFx.burst) だけ:
//   深みの王 (KING)・始祖 (FOUNDER) = 紙、心臓の番人 (WARDEN)・淵の主 (LORD) = 深淵、月の主 (MOON)・塔 (tower_*) = 月
// 粒はクライアントだけで出す (サーバーからは送らない)
public final class BossImpactFx {
    private BossImpactFx() {
    }

    // 主の粒を出したら true (呼ぶ側はふつうの粒を重ねない)
    static boolean burst(ClientLevel level, TelegraphClient.Decal d, double cx, double y0, double cz, double radius) {
        if (d.owner < 0) {
            return false;
        }
        Entity e = level.getEntity(d.owner);
        if (!(e instanceof LivingEntity le)) {
            return false;
        }
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (path.startsWith("tower_")) {
            AttackFx.burst(level, d, FxParticles.Element.MOON, 1f);
            level.playLocalSound(cx, y0, cz, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.2f, 0.6f, false);
            return true;
        }
        switch (PaperCutLayer.style(le)) {
            case KING -> {
                AttackFx.burst(level, d, FxParticles.Element.PAPER, 1f);
                level.playLocalSound(cx, y0, cz, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0f, 0.6f, false);
            }
            case WARDEN -> {
                AttackFx.burst(level, d, FxParticles.Element.ABYSS, 1f);
                level.playLocalSound(cx, y0, cz, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f, false);
            }
            case FOUNDER -> {
                AttackFx.burst(level, d, FxParticles.Element.PAPER, 1f);
                level.playLocalSound(cx, y0, cz, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 0.8f, 1.3f, false);
            }
            case LORD -> AttackFx.burst(level, d, FxParticles.Element.ABYSS, 1f);
            case MOON -> AttackFx.burst(level, d, FxParticles.Element.MOON, 1f);
            default -> {
                return false;
            }
        }
        return true;
    }

    static Vector3f lordColor(LivingEntity e) {
        int rgb = PaperCutLayer.lordRgbOf(e);
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }
}

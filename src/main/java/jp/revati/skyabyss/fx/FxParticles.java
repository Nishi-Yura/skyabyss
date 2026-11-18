package jp.revati.skyabyss.fx;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.AttackLook;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 攻撃と技の粒 (docs/design/59 の 2 章)。攻撃にはバニラの粒を使わず、これだけを使う。
// 属性 7 x 種類 3 = 21 種 (skyabyss:fx_<属性>_<種類>)。絵は textures/particle/fx/<属性>_<種類>_<0..3>.png (ドット 8x8、2 色。
// tools/gen/ui/gen_fx_particles.py)。動き・上限はクライアントの client/fx/FxParticle。
public final class FxParticles {
    private FxParticles() {
    }

    // 属性: 石 (岩鯨)・水 (潮殻)・雷 (雷翼)・熾 (余燼)・深淵・月・紙 (心臓のあとの主)
    public enum Element {
        STONE, WATER, STORM, EMBER, ABYSS, MOON, PAPER;

        public static Element of(AttackLook.Element e) {
            return switch (e) {
                case WATER -> WATER;
                case STORM -> STORM;
                case EMBER -> EMBER;
                default -> STONE;
            };
        }
    }

    // 種類: 破片 (重さで落ちて転がる)・光の粒 (浮いて薄れる)・煙 (ふくらんでゆっくり昇る)
    public enum Kind {
        SHARD, GLOW, SMOKE
    }

    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, SkyAbyss.MODID);
    private static final Map<Element, Map<Kind, DeferredHolder<ParticleType<?>, SimpleParticleType>>> TYPES = new EnumMap<>(Element.class);

    static {
        for (Element e : Element.values()) {
            Map<Kind, DeferredHolder<ParticleType<?>, SimpleParticleType>> m = new EnumMap<>(Kind.class);
            for (Kind k : Kind.values()) {
                m.put(k, PARTICLES.register(name(e, k), () -> new SimpleParticleType(false)));
            }
            TYPES.put(e, m);
        }
    }

    public static String name(Element e, Kind k) {
        return "fx_" + e.name().toLowerCase(Locale.ROOT) + "_" + k.name().toLowerCase(Locale.ROOT);
    }

    public static SimpleParticleType type(Element e, Kind k) {
        return TYPES.get(e).get(k).get();
    }

    // 種類と属性を type から引く (クライアントの登録用)
    public static Map<Element, Map<Kind, DeferredHolder<ParticleType<?>, SimpleParticleType>>> all() {
        return TYPES;
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}

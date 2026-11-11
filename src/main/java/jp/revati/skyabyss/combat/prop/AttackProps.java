package jp.revati.skyabyss.combat.prop;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 技の形 (AttackProp) を出す入り口。形の種類ごとに一つの実体の型 (skyabyss:prop_<種類>)、模型は lowpoly/prop_<種類>.json
// (tools/gen/mobs/attack_props/build_props.py が書く)。
//   rise(...)  予告の打つ瞬間 (onImpact) に呼ぶ: その場の床から突き出る
//   fall(...)  予告の始まりに呼ぶ: warn tick 後にちょうど落ちてくる (落ちる長さ fallTicks、それまでは見えない)
//   toss(...)  予告の始まりに呼ぶ: from から to へ弧を描いて warn tick で届く
public final class AttackProps {
    // 種類 (模型の名前 prop_<種類>)
    public static final String[] KINDS = {
            "bone_spike", "rock", "bone_shard", "tentacle", "ink_blob", "crystal_spike", "fireball", "lava_pillar",
            "ice_spike", "icicle", "water_spout", "coral_spear", "lightning", "ash_ball", "shell_spike", "pearl",
            // 2026-11-12: 野の敵の技 (矢・矢の雨・風の刃・胞子・鬼火)
            "arrow", "arrow_volley", "wind_blade", "spore", "wisp_orb", "net", "whirlwind",
            // 月と雲の主の技 (光の柱・羽根・秤の錘)
            "light_pillar", "feather", "weight"};
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<AttackProp>>> TYPES = new LinkedHashMap<>();

    static {
        for (String k : KINDS) {
            TYPES.put(k, ENTITIES.register("prop_" + k, () -> EntityType.Builder.<AttackProp>of((t, l) -> new AttackProp(t, l, k), MobCategory.MISC)
                    .sized(0.5f, 0.5f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(20).build(SkyAbyss.MODID + ":prop_" + k)));
        }
    }

    private AttackProps() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    @Nullable
    private static AttackProp make(ServerLevel level, String kind, @Nullable Entity owner) {
        var holder = TYPES.get(kind);
        if (holder == null) {
            return null;
        }
        AttackProp p = new AttackProp(holder.get(), level, kind);
        if (owner instanceof jp.revati.skyabyss.titan.group.GroupOwned g) {
            p.setGroupId(g.groupId());
        }
        return p;
    }

    private static void spawn(ServerLevel level, AttackProp p) {
        level.addFreshEntity(p);
    }

    // 落ちてくる物の形を、主の属性の塊から選ぶ (骨・石 → 岩、潮 → 真珠、熔岩 → 火の玉、氷 → 氷柱、晶 → 岩)
    public static String fallKind(net.minecraft.world.level.block.state.BlockState b) {
        var blk = b.getBlock();
        if (blk == net.minecraft.world.level.block.Blocks.PRISMARINE || blk == net.minecraft.world.level.block.Blocks.DARK_PRISMARINE) {
            return "pearl";
        }
        if (blk == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK || blk == net.minecraft.world.level.block.Blocks.BLACKSTONE) {
            return "fireball";
        }
        if (blk == net.minecraft.world.level.block.Blocks.PACKED_ICE || blk == net.minecraft.world.level.block.Blocks.BLUE_ICE || blk == net.minecraft.world.level.block.Blocks.ICE) {
            return "icicle";
        }
        return "rock";
    }

    // 床から突き出る物の形 (骨 → 骨の棘、潮 → 貝の棘、熔岩 → 熔岩の柱、氷 → 氷の棘、晶 → 晶の棘)
    public static String spikeKind(net.minecraft.world.level.block.state.BlockState b) {
        var blk = b.getBlock();
        if (blk == net.minecraft.world.level.block.Blocks.PRISMARINE || blk == net.minecraft.world.level.block.Blocks.DARK_PRISMARINE) {
            return "shell_spike";
        }
        if (blk == net.minecraft.world.level.block.Blocks.MAGMA_BLOCK || blk == net.minecraft.world.level.block.Blocks.BLACKSTONE) {
            return "lava_pillar";
        }
        if (blk == net.minecraft.world.level.block.Blocks.PACKED_ICE || blk == net.minecraft.world.level.block.Blocks.BLUE_ICE || blk == net.minecraft.world.level.block.Blocks.ICE) {
            return "ice_spike";
        }
        if (blk == net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK) {
            return "crystal_spike";
        }
        return "bone_spike";
    }

    // 投げる物の形 (骨 → 骨の欠片、潮 → 墨、熔岩 → 火の玉、氷 → 氷柱、そのほか → 岩)
    public static String tossKind(net.minecraft.world.level.block.state.BlockState b) {
        var blk = b.getBlock();
        if (blk == net.minecraft.world.level.block.Blocks.BONE_BLOCK) {
            return "bone_shard";
        }
        if (blk == net.minecraft.world.level.block.Blocks.PRISMARINE || blk == net.minecraft.world.level.block.Blocks.DARK_PRISMARINE) {
            return "pearl";
        }
        return fallKind(b);
    }

    // ---- 予告の見た目 (combat/AttackLook) から自動で出す (心臓・番人・領域の技など、look を付けた円の技すべて)
    //   落ちてくる技 (Travel.FALL)   → 始まりに空から落ちる形 (雷は打つ瞬間に雷の柱)
    //   弧を描く技 (Travel.ARC)      → 始まりに主から弧を描いて飛ぶ形
    //   柱の技 (Impact.COLUMN)      → 打つ瞬間に床から突き出る形
    //   雷の技 (Travel.BOLT)        → 打つ瞬間に雷の柱
    private static float sizeOf(jp.revati.skyabyss.combat.TelegraphShape shape) {
        double[] b = shape.bounds();
        double r = Math.max(b[2] - b[0], b[3] - b[1]) / 2;
        return (float) Math.max(0.8, Math.min(2.4, r / 2.0));
    }

    public static void lookStart(ServerLevel level, jp.revati.skyabyss.combat.AttackLook look, jp.revati.skyabyss.combat.TelegraphShape shape, double floor, int warn,
                                 @Nullable Entity owner) {
        if (look == null || look == jp.revati.skyabyss.combat.AttackLook.NONE) {
            return;
        }
        if (shape instanceof jp.revati.skyabyss.combat.TelegraphShape.Line l) {
            // 撃ち出す帯の技 (Travel.SHOTS): 帯の根元から先へ、属性の弾が飛ぶ
            if (look.travel == jp.revati.skyabyss.combat.AttackLook.Travel.SHOTS) {
                String kind = switch (look.element) {
                    case WATER -> "coral_spear";
                    case EMBER -> "fireball";
                    case STORM -> "crystal_spike";
                    default -> "bone_shard";
                };
                double y0 = owner != null ? owner.getY() + owner.getBbHeight() * 0.5 : floor + 2;
                Vec3 from = new Vec3(l.x(), y0, l.z());
                Vec3 to = new Vec3(l.x() + Math.cos(l.angle()) * l.length(), floor + 0.8, l.z() + Math.sin(l.angle()) * l.length());
                toss(level, kind, owner, from, to, warn, Math.min(warn, 10), 1f, 1.6f, 6);
            }
            return;
        }
        if (!(shape instanceof jp.revati.skyabyss.combat.TelegraphShape.Circle)) {
            return;
        }
        // 主そのものが跳んで落ちる技・自前の弾がある技は除く
        if (look == jp.revati.skyabyss.combat.AttackLook.WHALE_LEAP || look == jp.revati.skyabyss.combat.AttackLook.EMBER_PELLET) {
            return;
        }
        Vec3 c = new Vec3(shape.centerX(), floor, shape.centerZ());
        var el = look.element;
        if (look.travel == jp.revati.skyabyss.combat.AttackLook.Travel.FALL && el != jp.revati.skyabyss.combat.AttackLook.Element.STORM) {
            String kind = switch (el) {
                case WATER -> "pearl";
                case EMBER -> "fireball";
                default -> "rock";
            };
            fall(level, kind, owner, c, warn, 14, 16f, sizeOf(shape), 14);
        } else if (look.travel == jp.revati.skyabyss.combat.AttackLook.Travel.ARC && owner != null && el != jp.revati.skyabyss.combat.AttackLook.Element.STORM) {
            String kind = switch (el) {
                case EMBER -> "ash_ball";
                case WATER -> "pearl";
                default -> "rock";
            };
            Vec3 from = owner.position().add(0, owner.getBbHeight() * 0.7, 0);
            toss(level, kind, owner, from, c, warn, Math.min(warn, 20), 8f, Math.min(1.6f, sizeOf(shape)), 10);
        }
    }

    public static void lookImpact(ServerLevel level, jp.revati.skyabyss.combat.AttackLook look, jp.revati.skyabyss.combat.TelegraphShape shape, double floor,
                                  @Nullable Entity owner) {
        if (look == null || look == jp.revati.skyabyss.combat.AttackLook.NONE) {
            return;
        }
        if (shape instanceof jp.revati.skyabyss.combat.TelegraphShape.Line l) {
            // 裂け目の技 (Impact.FISSURE) は帯に沿って棘が突き出る。光線の技 (Travel.BEAM) は帯に沿って属性の跡が残る (小さく、間をあけて)
            boolean fissure = look.impact == jp.revati.skyabyss.combat.AttackLook.Impact.FISSURE;
            boolean beam = look.travel == jp.revati.skyabyss.combat.AttackLook.Travel.BEAM;
            if (!fissure && !beam) {
                return;
            }
            String kind = switch (look.element) {
                case WATER -> beam ? "water_spout" : "shell_spike";
                case STORM -> beam ? "lightning" : "crystal_spike";
                case EMBER -> "lava_pillar";
                default -> "bone_spike";
            };
            double step = fissure ? 2.6 : 4.5;
            float size = fissure ? 0.75f : (kind.equals("lightning") ? 0.7f : 0.5f);
            int n = 0;
            for (double d = 2.5; d < l.length() && n < 12; d += step, n++) {
                Vec3 at = new Vec3(l.x() + Math.cos(l.angle()) * d, floor, l.z() + Math.sin(l.angle()) * d);
                rise(level, kind, owner, at, (float) Math.toDegrees(l.angle()) + n * 53f, size * (0.85f + 0.3f * level.random.nextFloat()),
                        kind.equals("lightning") ? 5 : 14);
            }
            return;
        }
        if (!(shape instanceof jp.revati.skyabyss.combat.TelegraphShape.Circle)) {
            return;
        }
        Vec3 c = new Vec3(shape.centerX(), floor, shape.centerZ());
        var el = look.element;
        boolean storm = el == jp.revati.skyabyss.combat.AttackLook.Element.STORM;
        if (look.travel == jp.revati.skyabyss.combat.AttackLook.Travel.BOLT || (storm && look.travel == jp.revati.skyabyss.combat.AttackLook.Travel.FALL)) {
            rise(level, "lightning", owner, c, level.random.nextFloat() * 360f, Math.min(1.6f, sizeOf(shape)), 6);
        } else if (look.impact == jp.revati.skyabyss.combat.AttackLook.Impact.COLUMN) {
            String kind = switch (el) {
                case WATER -> "shell_spike";
                case STORM -> "crystal_spike";
                case EMBER -> "lava_pillar";
                default -> "bone_spike";
            };
            rise(level, kind, owner, c, level.random.nextFloat() * 360f, sizeOf(shape), 18);
        }
    }

    // 床から突き出る形を、delay tick 後に出す (予告の始まりに呼んで、打つ瞬間に合わせる)
    public static void riseLater(ServerLevel level, String kind, @Nullable Entity owner, Vec3 at, float yaw, float size, int linger, int delay) {
        AttackProp p = make(level, kind, owner);
        if (p == null) {
            return;
        }
        p.setup(AttackProp.RISE, at, at, Math.max(0, delay), AttackProp.RISE_IN, delay + linger, size, 0f, yaw);
        spawn(level, p);
    }

    // 深淵の属性 (層の主・淵の王): 0 骨 / 1 塩 / 2 雷 / 3 灰 / 4 影 の、突き出る形と落ちる形
    public static String abyssSpike(int el) {
        return switch (el) {
            case 1 -> "shell_spike";
            case 2 -> "lightning";
            case 3 -> "lava_pillar";
            case 4 -> "tentacle";
            default -> "bone_spike";
        };
    }

    public static String abyssFall(int el) {
        return switch (el) {
            case 1 -> "pearl";
            case 3 -> "fireball";
            case 4 -> "ink_blob";
            default -> "rock";
        };
    }

    // 床から突き出る (打つ瞬間に呼ぶ)。linger tick 留まって沈む
    public static void rise(ServerLevel level, String kind, @Nullable Entity owner, Vec3 at, float yaw, float size, int linger) {
        AttackProp p = make(level, kind, owner);
        if (p == null) {
            return;
        }
        p.setup(AttackProp.RISE, at, at, 0, AttackProp.RISE_IN, linger, size, 0f, yaw);
        spawn(level, p);
    }

    // 空から落ちてくる (予告の始まりに呼ぶ)。warn tick 後に着く。落ちる長さは fallTicks (それまでは見えない)
    public static void fall(ServerLevel level, String kind, @Nullable Entity owner, Vec3 at, int warn, int fallTicks, float height, float size, int linger) {
        AttackProp p = make(level, kind, owner);
        if (p == null) {
            return;
        }
        int ft = Math.max(2, Math.min(warn, fallTicks));
        p.setup(AttackProp.FALL, at, at, warn - ft, ft, linger, size, height, level.random.nextFloat() * 360f);
        spawn(level, p);
    }

    // from から to へ弧を描いて飛ぶ (予告の始まりに呼ぶ)。warn tick 後に着く。飛ぶ長さは flight (それまでは見えない)
    public static void toss(ServerLevel level, String kind, @Nullable Entity owner, Vec3 from, Vec3 to, int warn, int flight, float arc, float size, int linger) {
        AttackProp p = make(level, kind, owner);
        if (p == null) {
            return;
        }
        int ft = Math.max(2, Math.min(warn, flight));
        Vec3 d = to.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        p.setup(AttackProp.TOSS, from, to, warn - ft, ft, linger, size, arc, yaw);
        spawn(level, p);
    }
}

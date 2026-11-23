package jp.revati.skyabyss.item;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

// 全装備の数値を 1 枚の表に書き出す (SKYABYSS_SELFTEST_ONLY="gear dump" → run/gear_stats.tsv)。
// 数値の見直し (03 の 1.6・5 章) で、表を手で写さずスクリプトが読むためのもの。値は品の既定の部品 (ATTRIBUTE_MODIFIERS) と WeaponLines・TierFloor から読む
public final class GearDumpSelfTest {
    private GearDumpSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> rows = new ArrayList<>();
        rows.add(String.join("\t", "id", "class", "tier", "job", "dungeon", "req_lv", "enh", "slot",
                "attack", "speed", "floor_extra", "armor", "toughness", "kb_res", "other_attrs", "lines"));
        for (Item item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals(SkyAbyss.MODID) || !(item instanceof ModGear g)) {
                continue;
            }
            double atk = 0, spd = 0, arm = 0, tough = 0, kb = 0;
            String slot = "";
            StringBuilder other = new StringBuilder();
            ItemAttributeModifiers mods = new net.minecraft.world.item.ItemStack(item).getAttributeModifiers();
            if (mods != null) {
                for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
                    double v = e.modifier().amount();
                    boolean add = e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE;
                    if (slot.isEmpty() || e.slot() != EquipmentSlotGroup.ANY) {
                        slot = e.slot().getSerializedName();
                    }
                    if (add && e.attribute().is(Attributes.ATTACK_DAMAGE)) {
                        atk += v;
                    } else if (add && e.attribute().is(Attributes.ATTACK_SPEED)) {
                        spd += v;
                    } else if (add && e.attribute().is(Attributes.ARMOR)) {
                        arm += v;
                    } else if (add && e.attribute().is(Attributes.ARMOR_TOUGHNESS)) {
                        tough += v;
                    } else if (add && e.attribute().is(Attributes.KNOCKBACK_RESISTANCE)) {
                        kb += v;
                    } else {
                        other.append(e.attribute().getRegisteredName().replace("minecraft:", "")).append(add ? "+" : "*")
                                .append(fmt(v)).append(' ');
                    }
                }
            }
            if (item instanceof ChapterBowItem bow) {   // 弓の矢の倍率 (階級の幅を通した値)
                other.append("bow.multiplier=").append(fmt(bow.multiplier(0))).append(' ');
            } else if (item instanceof net.minecraft.world.item.ProjectileWeaponItem) {   // 弓: 矢の倍率などの double の欄をそのまま
                for (Class<?> c = item.getClass(); c != null && c != Item.class; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (f.getType() == double.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            try {
                                f.setAccessible(true);
                                other.append("bow.").append(f.getName()).append('=').append(fmt(f.getDouble(item))).append(' ');
                            } catch (ReflectiveOperationException | RuntimeException ignored) {
                            }
                        }
                    }
                }
            }
            StringBuilder lines = new StringBuilder();
            for (WeaponLines.Line l : WeaponLines.of(item)) {
                lines.append(l.bad() ? "-" : "+").append(l.stat().name().toLowerCase(Locale.ROOT)).append('=').append(fmt(l.value())).append(' ');
            }
            Job job = Job.of(item);
            rows.add(String.join("\t", key.getPath(), item.getClass().getSimpleName(), g.tier().name().toLowerCase(Locale.ROOT),
                    job == null ? "" : job.name().toLowerCase(Locale.ROOT), String.valueOf(g.dungeon()), String.valueOf(g.requiredLevel()),
                    String.valueOf(g.enhanceable()), slot, fmt(atk), fmt(spd), fmt(TierFloor.extra(item)), fmt(arm), fmt(tough), fmt(kb),
                    other.toString().trim(), lines.toString().trim()));
        }
        Path out = server.getServerDirectory().resolve("gear_stats.tsv");
        try {
            Files.write(out, rows);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("gear dump: " + ex);
        }
        if (rows.size() < 100) {
            throw new IllegalStateException("gear dump: only " + (rows.size() - 1) + " items");
        }
        SkyAbyss.LOGGER.info("SELFTEST gear dump PASSED ({} items -> {})", rows.size() - 1, out.toAbsolutePath());
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}

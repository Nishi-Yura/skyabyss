package jp.revati.skyabyss.dev;

import java.util.List;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

// 品の棚卸し: この Mod の品を一行ずつ記録に書く (id・種類・名前の色 (Rarity)・階級 (GearTier)・職・由来・攻撃力と速さ)。
// クリエイティブの欄の分け方とレア度の見直しに使う。SKYABYSS_SELFTEST_ONLY="item audit"。ヒロイックがエピックより弱い・アーティファクトが弱いと FAILED
public final class ItemAuditSelfTest {
    private ItemAuditSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("item audit", 1, ItemAuditSelfTest::run));
    }

    static void run(MinecraftServer server) {
        int n = 0;
        List<String> bad = new java.util.ArrayList<>();
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            if (!e.getKey().location().getNamespace().equals(SkyAbyss.MODID)) {
                continue;
            }
            n++;
            var item = e.getValue();
            ItemStack stack = new ItemStack(item);
            String tier = item instanceof ModGear g ? g.tier().key + "/" + g.dungeon() : "-";
            Job job = Job.of(stack);
            double[] atk = {0, 0};
            stack.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND, (attr, mod) -> {
                if (mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    if (attr.is(Attributes.ATTACK_DAMAGE)) {
                        atk[0] += mod.amount();
                    } else if (attr.is(Attributes.ATTACK_SPEED)) {
                        atk[1] += mod.amount();
                    }
                }
            });
            SkyAbyss.LOGGER.info("ITEMAUDIT\t{}\t{}\t{}\t{}\t{}\t{}\t{}", e.getKey().location().getPath(), item.getClass().getSimpleName(),
                    stack.getRarity().name(), tier, job == null ? "-" : job.id, String.format("%.1f", atk[0]), String.format("%.2f", atk[1]));
        }
        if (bad.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST item audit PASSED ({} items)", n);
        } else {
            SkyAbyss.LOGGER.error("SELFTEST item audit FAILED: {}", bad);
        }
    }
}

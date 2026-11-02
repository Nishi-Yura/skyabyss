package jp.revati.skyabyss.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.GearTierFrames;
import jp.revati.skyabyss.item.GearTier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 公式サイトの wiki の元 (tools/site/wiki.py が読む)。この mod の品と生き物を、ゲームの中の見え方のまま JSON に書く。
// 撮影の台本の `wikidump` から呼ぶ (クライアント。品の説明はクライアントの ItemTooltipEvent で足される行もあるので、遊ぶ人の画面と同じ行になる)。
// 品: id・名前・クラス・重ねる数・階級・防具の部位・説明の行 (文字と色の切れはし)。生き物: id・名前・分類・クラス・大きさ・基の属性。
public final class WikiDump {
    private static final List<Holder<Attribute>> STATS = List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS, Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.KNOCKBACK_RESISTANCE, Attributes.FOLLOW_RANGE);

    private WikiDump() {
    }

    public static String write(Path file) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        Item.TooltipContext ctx = Item.TooltipContext.of(mc.level);
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().startsWith(SkyAbyss.MODID)) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("name", stack.getHoverName().getString());
            o.addProperty("class", item.getClass().getName());
            o.addProperty("stack", stack.getMaxStackSize());
            GearTier tier = GearTierFrames.tierOf(stack);
            if (tier != null) {
                o.addProperty("tier", tier.key);
            }
            if (item instanceof ArmorItem armor) {
                o.addProperty("armor_slot", armor.getType().getName());
            }
            JsonArray tip = new JsonArray();
            try {
                for (Component line : stack.getTooltipLines(ctx, mc.player, TooltipFlag.Default.NORMAL)) {
                    tip.add(segments(line));
                }
            } catch (RuntimeException e) {
                o.addProperty("tooltip_error", e.toString());
            }
            o.add("tooltip", tip);
            items.add(o);
        }
        root.add("items", items);

        JsonArray mobs = new JsonArray();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().startsWith(SkyAbyss.MODID)) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("name", type.getDescription().getString());
            o.addProperty("category", type.getCategory().getName());
            o.addProperty("width", type.getWidth());
            o.addProperty("height", type.getHeight());
            try {
                Entity e = type.create(mc.level);
                if (e != null) {
                    o.addProperty("class", e.getClass().getName());
                    o.addProperty("living", e instanceof LivingEntity);
                    if (e instanceof LivingEntity le) {
                        // 基の属性 (作ったばかりの体の値。深さ・人数・段で上がる分は入らない)
                        JsonObject stats = new JsonObject();
                        for (Holder<Attribute> a : STATS) {
                            if (le.getAttributes().hasAttribute(a)) {
                                stats.addProperty(a.unwrapKey().map(k -> k.location().getPath()).orElse("?"), le.getAttributeBaseValue(a));
                            }
                        }
                        o.add("stats", stats);
                    }
                    e.discard();
                }
            } catch (RuntimeException ex) {
                o.addProperty("class_error", ex.toString());
            }
            mobs.add(o);
        }
        root.add("mobs", mobs);

        // 技: 段ごとの効果の文 (数字は SkillRules.describeArgs が入れる。技の画面と同じ文)
        JsonArray skills = new JsonArray();
        for (jp.revati.skyabyss.rpg.skill.Skill s : jp.revati.skyabyss.rpg.skill.Skill.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("tree", s.tree.name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("name", Component.translatable(s.nameKey()).getString());
            o.addProperty("icon", s.iconIndex());
            o.addProperty("rebirth_at", s.rebirthAt());
            JsonArray ranks = new JsonArray();
            for (int r = 1; r <= Math.max(1, s.maxRank); r++) {
                ranks.add(Component.translatable(s.effectKey(), jp.revati.skyabyss.rpg.skill.SkillRules.describeArgs(s, r)).getString());
            }
            o.add("effect", ranks);
            skills.add(o);
        }
        root.add("skills", skills);

        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
        return items.size() + " items, " + mobs.size() + " mobs";
    }

    // 一行を、色と太字・斜体の切れはしに分ける
    private static JsonArray segments(Component line) {
        JsonArray out = new JsonArray();
        line.visit((Style style, String text) -> {
            if (!text.isEmpty()) {
                JsonObject s = new JsonObject();
                s.addProperty("t", text);
                TextColor c = style.getColor();
                if (c != null) {
                    s.addProperty("c", String.format("%06X", c.getValue()));
                }
                if (style.isBold()) {
                    s.addProperty("b", true);
                }
                if (style.isItalic()) {
                    s.addProperty("i", true);
                }
                out.add(s);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}

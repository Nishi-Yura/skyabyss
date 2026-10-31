package jp.revati.skyabyss.endgame;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.NamedFoes;
import jp.revati.skyabyss.world.continent.RegionSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 図鑑の完成の報い (docs/design/54 の 3.4)。
//   装備の図鑑: 巨神ごとの装備の表 (TitanGearSets の図鑑の品。手にした記録は TitanMemoryData.owned)。一つの巨神の表をすべて手にすると
//               鐘銭 SET_COINS と称号「〜の装備をそろえた」(岩鯨・潮殻・雷翼・余燼)。表のあるすべての巨神 (深淵・月を含む) で「図鑑を満たした者」
//   敵の図鑑: この MOD の敵を倒すと記す (人ごと。死んでも消えない)。数えるのは土地の湧きの表 (RegionSpawner) と名のある強敵の敵。
//             すべて倒すと鐘銭 FOES_COINS と称号「大陸の敵を知る者」
//   見る: /skyabyss_codex (チャットに巨神ごとの数と敵の数)。旅帳の案内にも書く。見た目 (旗・名札の色) は形を見せてから足す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Codex {
    public static final String FOES = "skyabyss_codex_foes";
    public static final String DONE = "skyabyss_codex_done";
    public static final int SET_COINS = 1000;
    public static final int FOES_COINS = 1000;
    // 称号のある巨神 (表のあるすべての巨神は "all" で数える)
    public static final List<String> TITLED = List.of("stone_whale", "tide_shell", "thunder_wing", "ember");

    private Codex() {
    }

    // ---------------------------------------------------------------- 装備の図鑑
    // 図鑑の品 (登録されているものだけ)。表のない巨神 (塔・神器) は空
    public static List<String> pieces(TitanGearSet set) {
        return set.collection().stream().filter(TitanGearSet::registered).toList();
    }

    public static int owned(ServerPlayer p, TitanGearSet set) {
        TitanMemoryData.Record r = TitanMemoryData.get(p.server).peek(p.getUUID(), set.titan);
        if (r == null) {
            return 0;
        }
        return (int) pieces(set).stream().filter(r.owned::contains).count();
    }

    public static boolean setDone(ServerPlayer p, TitanGearSet set) {
        List<String> all = pieces(set);
        return !all.isEmpty() && owned(p, set) >= all.size();
    }

    // ---------------------------------------------------------------- 敵の図鑑
    // 数える敵 (土地の湧きの表と名のある強敵。この MOD の敵だけ)
    public static Set<String> foes() {
        Set<String> out = new LinkedHashSet<>();
        for (var region : RegionSpawner.table()) {
            for (var e : region) {
                if (e.entity().startsWith(SkyAbyss.MODID + ":")) {
                    out.add(e.entity());
                }
            }
        }
        for (int r = 0; r < RegionSpawner.REGIONS.length; r++) {
            NamedFoes.Def d = NamedFoes.def(r);
            if (d != null && d.entity().startsWith(SkyAbyss.MODID + ":")) {
                out.add(d.entity());
            }
        }
        return out;
    }

    public static Set<String> defeated(Player p) {
        Set<String> out = new LinkedHashSet<>();
        for (Tag t : persisted(p).getList(FOES, Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // 倒した敵を記す (初めてなら true)
    public static boolean defeat(ServerPlayer p, String entity) {
        Set<String> seen = defeated(p);
        if (!seen.add(entity)) {
            return false;
        }
        ListTag l = new ListTag();
        seen.forEach(s -> l.add(StringTag.valueOf(s)));
        persisted(p).put(FOES, l);
        check(p);
        return true;
    }

    public static int foesDefeated(ServerPlayer p) {
        Set<String> seen = defeated(p);
        return (int) foes().stream().filter(seen::contains).count();
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || dead.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) {
            return;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        if (key.getNamespace().equals(SkyAbyss.MODID)) {
            defeat(p, key.toString());
        }
    }

    // ---------------------------------------------------------------- 完成の報い
    static Set<String> done(Player p) {
        Set<String> out = new LinkedHashSet<>();
        for (Tag t : persisted(p).getList(DONE, Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    static void markDone(Player p, Set<String> d) {
        ListTag l = new ListTag();
        d.forEach(s -> l.add(StringTag.valueOf(s)));
        persisted(p).put(DONE, l);
    }

    // 新しくそろったものがあれば報いを渡す (装備の表は手にした記録が別の所で増えるので、ときどき見る)
    public static void check(ServerPlayer p) {
        Set<String> d = done(p);
        boolean changed = false;
        boolean allSets = true;
        for (TitanGearSet set : TitanGearSets.all()) {
            if (pieces(set).isEmpty()) {
                continue;
            }
            if (!setDone(p, set)) {
                allSets = false;
                continue;
            }
            if (d.add("set:" + set.titan)) {
                changed = true;
                Coins.give(p, SET_COINS);
                Notice.unlock(p, "message.skyabyss.codex.set_done", Component.translatable("endgame.skyabyss.codex.set." + set.titan), SET_COINS);
            }
        }
        if (allSets && d.add("set:all")) {
            changed = true;
            Notice.unlock(p, "message.skyabyss.codex.all_done");
        }
        Set<String> all = foes();
        if (!all.isEmpty() && defeated(p).containsAll(all) && d.add("foes:all")) {
            changed = true;
            Coins.give(p, FOES_COINS);
            Notice.unlock(p, "message.skyabyss.codex.foes_done", FOES_COINS);
        }
        if (changed) {
            markDone(p, d);
            jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && (p.tickCount + (p.getId() & 0xFF)) % 400 == 0) {
            check(p);
        }
    }

    // 称号の事実の印 ("codex:<巨神>"・"codex:all"・"foes:all")
    public static Set<String> marks(ServerPlayer p) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : done(p)) {
            if (s.startsWith("set:")) {
                out.add("codex:" + s.substring(4));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 見る
    public static void show(ServerPlayer p) {
        p.sendSystemMessage(Component.translatable("message.skyabyss.codex.head").withStyle(ChatFormatting.GOLD));
        for (TitanGearSet set : TitanGearSets.all()) {
            List<String> all = pieces(set);
            if (all.isEmpty()) {
                continue;
            }
            int n = owned(p, set);
            p.sendSystemMessage(Component.translatable("message.skyabyss.codex.set_line", Component.translatable("endgame.skyabyss.codex.set." + set.titan), n, all.size())
                    .withStyle(n >= all.size() ? ChatFormatting.AQUA : ChatFormatting.WHITE));
        }
        int foes = foes().size();
        int seen = foesDefeated(p);
        p.sendSystemMessage(Component.translatable("message.skyabyss.codex.foes_line", seen, foes).withStyle(seen >= foes ? ChatFormatting.AQUA : ChatFormatting.WHITE));
        p.sendSystemMessage(Component.translatable("message.skyabyss.codex.foot").withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_codex").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            check(p);
            show(p);
            return 1;
        }));
    }

    // 自己テスト用
    public static void clearForTest(Player p) {
        persisted(p).remove(FOES);
        persisted(p).remove(DONE);
    }
}

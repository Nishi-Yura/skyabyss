package jp.revati.skyabyss.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.endgame.RegionPoints;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 釣り (docs/design/54 の 6.2.4.1)。釣り竿 (FishingSetup.ROD) のふつうの釣りで、釣れたものをその土地の魚に替える (バニラの魚・がらくた・宝・経験の玉は出ない)。
//   魚の図鑑: 種ごとに一番大きい一匹 (人の永続データ BEST)。全部そろうと CODEX_COINS と称号 (印 "fish:all")
//   週の釣り大会: 週のあいだに釣った一匹の点 (Fish.score) の一番を人ごとに数え (Week)、週が替わると上位 3 人に PRIZE (1 位は称号の印 "fish:champion")
//   見る: /skyabyss_fishing (図鑑の数・今週の番付)、食べ物屋の「釣り大会の番付」
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Fishing {
    public static final String BEST = "skyabyss_fish";
    public static final String CHAMPION = "skyabyss_fish_champion";
    public static final String DONE = "skyabyss_fish_done";
    public static final int CODEX_COINS = 1000;
    public static final int[] PRIZE = {3000, 1500, 800};

    private Fishing() {
    }

    // ---------------------------------------------------------------- 釣れた
    @SubscribeEvent
    static void onFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        event.setCanceled(true);
        event.damageRodBy(0);
        FishingHook hook = event.getHookEntity();
        int region = ContinentMap.get().inside(hook.getBlockX(), hook.getBlockZ()) ? ContinentMap.get().region(hook.getBlockX(), hook.getBlockZ()) : ContinentMap.MEADOW;
        Fish.Species s = Fish.roll(region, level.random);
        int size = Fish.size(s, level.random);
        ItemStack stack = FishingSetup.stack(s, size);
        ItemEntity item = new ItemEntity(level, hook.getX(), hook.getY(), hook.getZ(), stack);
        double dx = p.getX() - hook.getX();
        double dy = p.getY() - hook.getY();
        double dz = p.getZ() - hook.getZ();
        item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
        level.addFreshEntity(item);
        caught(p, s, size);
    }

    // 釣った一匹を数える (図鑑・大会・知らせ)。自己テストと開発用の命令からも
    public static void caught(ServerPlayer p, Fish.Species s, int size) {
        CompoundTag best = p.getPersistentData().getCompound(BEST);
        int before = best.getInt(s.id());
        boolean first = !best.contains(s.id());
        if (size > before) {
            best.putInt(s.id(), size);
            p.getPersistentData().put(BEST, best);
        }
        Component name = Component.translatable("item.skyabyss." + s.itemId());
        String cm = String.format("%.1f", size / 10.0);
        if (first) {
            Notice.unlock(p, "message.skyabyss.fishing.new", name, cm, codexCount(p), Fish.ALL.size());
        } else if (size > before) {
            Notice.gain(p, "message.skyabyss.fishing.record", name, cm);
        } else {
            Notice.hint(p, "message.skyabyss.fishing.caught", name, cm);
        }
        if (s.rarity() == 'R' || s.rarity() == 'L') {
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, s.rarity() == 'L' ? 0.8f : 1.3f);
        }
        Week.get(p.server).enter(p, s, size);
        if (codexCount(p) >= Fish.ALL.size() && !p.getPersistentData().getBoolean(DONE)) {
            p.getPersistentData().putBoolean(DONE, true);
            Coins.give(p, CODEX_COINS);
            Notice.unlock(p, "message.skyabyss.fishing.codex_done", CODEX_COINS);
        }
        jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
    }

    public static int codexCount(Player p) {
        return p.getPersistentData().getCompound(BEST).getAllKeys().size();
    }

    public static int best(Player p, String id) {
        return p.getPersistentData().getCompound(BEST).getInt(id);
    }

    // 称号の事実の印 (Rebirths.facts)
    public static Set<String> marks(ServerPlayer p) {
        Set<String> out = new HashSet<>();
        if (codexCount(p) >= Fish.ALL.size()) {
            out.add("fish:all");
        }
        if (p.getPersistentData().getBoolean(CHAMPION)) {
            out.add("fish:champion");
        }
        return out;
    }

    // ---------------------------------------------------------------- 週の釣り大会
    public record Entry(UUID player, String name, String fish, int size, int score) {
    }

    public static final class Week extends SavedData {
        private static final String NAME = "skyabyss_fishing_week";
        String week = "";
        final Map<UUID, Entry> entries = new HashMap<>();
        // 週が替わったときの賞 (人 → 順位 1〜3)。その人が来たときに渡す
        final Map<UUID, Integer> prizes = new HashMap<>();
        // 先週の上位 (番付に出す)
        final List<Entry> last = new ArrayList<>();

        public static Week get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Week::new, Week::load), NAME);
        }

        public void roll() {
            String now = RegionPoints.weekId();
            if (now.equals(week)) {
                return;
            }
            boolean first = week.isEmpty();
            week = now;
            if (!first) {
                last.clear();
                last.addAll(top(3));
                for (int i = 0; i < last.size(); i++) {
                    prizes.put(last.get(i).player(), i + 1);
                }
            }
            entries.clear();
            setDirty();
        }

        public void enter(ServerPlayer p, Fish.Species s, int size) {
            roll();
            int score = Fish.score(s, size);
            Entry old = entries.get(p.getUUID());
            if (old == null || score > old.score()) {
                entries.put(p.getUUID(), new Entry(p.getUUID(), p.getGameProfile().getName(), s.id(), size, score));
                setDirty();
            }
        }

        public List<Entry> top(int n) {
            List<Entry> out = new ArrayList<>(entries.values());
            out.sort((a, b) -> Integer.compare(b.score(), a.score()));
            return out.subList(0, Math.min(n, out.size()));
        }

        @Nullable
        public Entry of(UUID u) {
            return entries.get(u);
        }

        // 賞を渡す (来ていれば)。渡したら true
        boolean pay(ServerPlayer p) {
            Integer place = prizes.remove(p.getUUID());
            if (place == null) {
                return false;
            }
            Coins.give(p, PRIZE[Math.max(0, Math.min(PRIZE.length - 1, place - 1))]);
            if (place == 1) {
                p.getPersistentData().putBoolean(CHAMPION, true);
            }
            Notice.unlock(p, "message.skyabyss.fishing.prize", place, PRIZE[Math.max(0, Math.min(PRIZE.length - 1, place - 1))]);
            jp.revati.skyabyss.rebirth.Rebirths.updateTitles(p);
            setDirty();
            return true;
        }

        static Week load(CompoundTag tag, HolderLookup.Provider registries) {
            Week w = new Week();
            w.week = tag.getString("week");
            for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
                Entry e = read((CompoundTag) t);
                w.entries.put(e.player(), e);
            }
            for (Tag t : tag.getList("last", Tag.TAG_COMPOUND)) {
                w.last.add(read((CompoundTag) t));
            }
            CompoundTag pz = tag.getCompound("prizes");
            for (String k : pz.getAllKeys()) {
                w.prizes.put(UUID.fromString(k), pz.getInt(k));
            }
            return w;
        }

        static Entry read(CompoundTag c) {
            return new Entry(c.getUUID("p"), c.getString("name"), c.getString("fish"), c.getInt("size"), c.getInt("score"));
        }

        static CompoundTag write(Entry e) {
            CompoundTag c = new CompoundTag();
            c.putUUID("p", e.player());
            c.putString("name", e.name());
            c.putString("fish", e.fish());
            c.putInt("size", e.size());
            c.putInt("score", e.score());
            return c;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putString("week", week);
            ListTag es = new ListTag();
            entries.values().forEach(e -> es.add(write(e)));
            tag.put("entries", es);
            ListTag ls = new ListTag();
            last.forEach(e -> ls.add(write(e)));
            tag.put("last", ls);
            CompoundTag pz = new CompoundTag();
            prizes.forEach((u, n) -> pz.putInt(u.toString(), n));
            tag.put("prizes", pz);
            return tag;
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 != 51) {
            return;
        }
        Week w = Week.get(event.getServer());
        w.roll();
        if (!w.prizes.isEmpty()) {
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                w.pay(p);
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Week.get(p.server).pay(p);
        }
    }

    // ---------------------------------------------------------------- 見る
    public static void show(ServerPlayer p) {
        Week w = Week.get(p.server);
        w.roll();
        p.sendSystemMessage(Component.translatable("message.skyabyss.fishing.head", codexCount(p), Fish.ALL.size()).withStyle(ChatFormatting.GOLD));
        List<Entry> top = w.top(5);
        if (top.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.fishing.board_empty").withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < top.size(); i++) {
            Entry e = top.get(i);
            p.sendSystemMessage(Component.translatable("message.skyabyss.fishing.board_line", i + 1, e.name(), Component.translatable("item.skyabyss.fish_" + e.fish()),
                    String.format("%.1f", e.size() / 10.0), e.score() / 10.0).withStyle(i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        }
        Entry mine = w.of(p.getUUID());
        if (mine != null) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.fishing.board_mine", Component.translatable("item.skyabyss.fish_" + mine.fish()),
                    String.format("%.1f", mine.size() / 10.0), mine.score() / 10.0).withStyle(ChatFormatting.AQUA));
        }
        if (!w.last.isEmpty()) {
            Entry e = w.last.get(0);
            p.sendSystemMessage(Component.translatable("message.skyabyss.fishing.board_last", e.name(), Component.translatable("item.skyabyss.fish_" + e.fish()),
                    String.format("%.1f", e.size() / 10.0)).withStyle(ChatFormatting.GRAY));
        }
        p.sendSystemMessage(Component.translatable("message.skyabyss.fishing.board_foot", PRIZE[0], PRIZE[1], PRIZE[2]).withStyle(ChatFormatting.DARK_GRAY));
    }

    // /skyabyss_fishing と、開発用 /skyabyss fishing catch [id] (釣れたことにする)・roll (週を替える)・reset
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_fishing").executes(c -> {
            show(c.getSource().getPlayerOrException());
            return 1;
        }));
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("fishing")
                        .then(Commands.literal("catch").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int region = ContinentMap.get().inside(p.getBlockX(), p.getBlockZ()) ? ContinentMap.get().region(p.getBlockX(), p.getBlockZ()) : ContinentMap.MEADOW;
                            Fish.Species s = Fish.roll(region, p.getRandom());
                            int size = Fish.size(s, p.getRandom());
                            p.getInventory().add(FishingSetup.stack(s, size));
                            caught(p, s, size);
                            return 1;
                        }).then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Fish.Species s = Fish.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id"));
                            if (s == null) {
                                return 0;
                            }
                            int size = Fish.size(s, p.getRandom());
                            p.getInventory().add(FishingSetup.stack(s, size));
                            caught(p, s, size);
                            return 1;
                        })))
                        .then(Commands.literal("roll").executes(c -> {
                            Week w = Week.get(c.getSource().getServer());
                            w.week = "dev";
                            w.roll();
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getPersistentData().remove(BEST);
                            p.getPersistentData().remove(DONE);
                            p.getPersistentData().remove(CHAMPION);
                            return 1;
                        }))));
    }
}

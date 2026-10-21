package jp.revati.skyabyss.facility.guild;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.Repeats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// ギルドの週替わりの頼み (docs/design/35 の 6.5)。ギルドごとに一週 (7 日。Repeats.WEEK_DAYS) に一つ、小さな表 (POOL) から決まる。
//   納める頼み: 組合の受付の書記のギルドの画面で「納める」を押すと、持ち物から足りない分だけ取る
//   狩る頼み  : ギルドの人が敵 (Enemy) を倒すたびに 1 つ進む (どこで倒しても)
//   人ごとの納めた量 (contrib) を数え、画面に多い順に出す。目標に届くと、一つでも納めた人みなに報い (REWARD_COINS と共鳴の欠片 1)。
//   その場にいない人は、次に入ったときに受け取る (rewardsDue)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GuildRequests {
    private GuildRequests() {
    }

    // 頼みの型。kill = 狩る頼み (item は無視)
    public record Req(String id, int target, boolean kill, Item icon, Predicate<ItemStack> item) {
        public String nameKey() {
            return "gui.skyabyss.guild.req." + id;
        }
    }

    static Item mod(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path));
    }

    static Predicate<ItemStack> any(String... paths) {
        return s -> {
            for (String p : paths) {
                if (s.is(mod(p))) {
                    return true;
                }
            }
            return false;
        };
    }

    // 頼みの表 (並びは変えない。週と ギルドの ID で選ぶ)
    public static final List<Req> POOL = List.of(
            new Req("bones", 96, false, Items.BONE, any("bone_chip")),
            new Req("iron", 48, false, Items.IRON_INGOT, s -> s.is(Items.IRON_INGOT)),
            new Req("titan", 24, false, Items.PRISMARINE_SHARD, any("stone_whale_shard", "tide_shell_shard", "thunder_wing_shard", "ember_shard")),
            new Req("bread", 64, false, Items.BREAD, s -> s.is(Items.BREAD)),
            new Req("resonance", 12, false, Items.AMETHYST_SHARD, any("resonance_shard")),
            new Req("hunt", 150, true, Items.IRON_SWORD, s -> false));

    // 報い (一人ぶん)
    public static final int REWARD_COINS = 20;
    public static final int REWARD_SHARDS = 1;

    public static long week(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime() / 24000L, Repeats.WEEK_DAYS);
    }

    // その週の頼み (純粋。ギルドの ID と週で決まる)
    public static Req pick(String guildId, long week) {
        int i = (int) Math.floorMod(week * 31 + guildId.hashCode(), POOL.size());
        return POOL.get(i);
    }

    @Nullable
    public static Req byId(String id) {
        for (Req r : POOL) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    // 今週の頼み。週が替わっていれば新しい頼みにして、進みと納めた量を空にする
    public static Req current(MinecraftServer server, GuildData.Guild g) {
        long w = week(server);
        if (g.reqWeek != w || byId(g.reqId) == null) {
            Req r = pick(g.id, w);
            g.reqWeek = w;
            g.reqId = r.id();
            g.reqProgress = 0;
            g.reqDone = false;
            g.contrib.clear();
            GuildData.get(server).dirty();
        }
        return byId(g.reqId);
    }

    // 持ち物から納める。納めた数を返す (0 = 何もない)
    public static Guilds.Result deliver(ServerPlayer player) {
        GuildData.Guild g = Guilds.of(player);
        if (g == null) {
            return Guilds.Result.NOT_IN_GUILD;
        }
        Req r = current(player.server, g);
        if (g.reqDone) {
            return Guilds.Result.REQ_DONE;
        }
        if (r.kill()) {
            return Guilds.Result.NOTHING_TO_GIVE;
        }
        int need = r.target() - g.reqProgress;
        Inventory inv = player.getInventory();
        int taken = 0;
        for (int i = 0; i < inv.getContainerSize() && taken < need; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && r.item().test(s)) {
                int k = Math.min(need - taken, s.getCount());
                s.shrink(k);
                taken += k;
            }
        }
        if (taken <= 0) {
            return Guilds.Result.NOTHING_TO_GIVE;
        }
        inv.setChanged();
        add(player.server, g, player.getUUID(), taken);
        return Guilds.Result.DELIVERED;
    }

    // 進める (納める・狩る)。目標に届けば報いを配る
    static void add(MinecraftServer server, GuildData.Guild g, UUID who, int n) {
        Req r = current(server, g);
        if (g.reqDone || n <= 0) {
            return;
        }
        int k = Math.min(n, r.target() - g.reqProgress);
        g.reqProgress += k;
        g.contrib.merge(who, k, Integer::sum);
        GuildData.get(server).dirty();
        if (g.reqProgress >= r.target()) {
            complete(server, g);
        }
    }

    static void complete(MinecraftServer server, GuildData.Guild g) {
        g.reqDone = true;
        SkyAbyss.LOGGER.info("[guild] {} finished the weekly request {}", g.name, g.reqId);
        for (Map.Entry<UUID, Integer> e : g.contrib.entrySet()) {
            if (e.getValue() <= 0 || !g.members.contains(e.getKey())) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && !(p instanceof FakePlayer)) {
                reward(p, g);
            } else {
                g.rewardsDue.merge(e.getKey(), 1, Integer::sum);
            }
        }
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                Notice.narrate(p, "message.skyabyss.guild.req_complete", g.name);
            }
        }
        GuildData.get(server).dirty();
    }

    static void reward(ServerPlayer p, GuildData.Guild g) {
        Coins.give(p, REWARD_COINS);
        Quests.give(p, new ItemStack(mod("resonance_shard"), REWARD_SHARDS));
        Notice.gain(p, "message.skyabyss.guild.req_reward", REWARD_COINS, REWARD_SHARDS);
    }

    // 狩る頼み: ギルドの人が敵を倒した
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer killer) || killer instanceof FakePlayer) {
            return;
        }
        GuildData.Guild g = Guilds.of(killer);
        if (g == null) {
            return;
        }
        Req r = current(killer.server, g);
        if (r.kill() && !g.reqDone) {
            add(killer.server, g, killer.getUUID(), 1);
        }
    }

    // いなかった間に済んだ頼みの報いを渡す
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        GuildData.Guild g = Guilds.of(p);
        if (g == null) {
            return;
        }
        Integer due = g.rewardsDue.remove(p.getUUID());
        if (due != null && due > 0) {
            for (int i = 0; i < due; i++) {
                reward(p, g);
            }
            GuildData.get(p.server).dirty();
        }
    }
}

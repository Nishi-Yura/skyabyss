package jp.revati.skyabyss.tower;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.party.PartyData;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// あいだの塔の受付 (docs/design/54 の 2.2)。都の広場 (案内人の印 npc_guide_plaza から 2〜4 ブロックの空いた床。BoxOpeners.spotNear)。
//   見え方  受付に塔守 (係の人の役 TOWER_KEEPER。話すと受付の一覧・塔の店・記録の板) が立つ。資格のある人 (終章の結末を選んだ人) にだけ、受付から光の柱 (その人にだけ送る粒)
//   隊      レイド隊 1〜12 人。隊長が「隊を作る」(受付のそばの同じ組の人も入る)、ほかの人は「隊に入る」。隊長の「塔へ入る」で、
//           受付のそば (RANGE) にいる、資格があり今日まだ入っていない人が全員入る (Towers.start)
//   チャット 押せる文字の一覧 (/skyabyss_tower menu|create|join <隊長>|quit|start|leave)。どこでも開けるが、作る・入る・始めるは受付のそばだけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TowerDesk {
    public static final String TAG = "skyabyss_tower_desk";
    static final String COMMAND = "skyabyss_tower";
    static final double RANGE = 12;
    static final String ANCHOR = "npc_guide_plaza";
    private static BlockPos spot;
    // 隊長 → 隊の人 (隊長が先頭)
    static final Map<UUID, LinkedHashSet<UUID>> SQUADS = new LinkedHashMap<>();

    private TowerDesk() {
    }

    // 受付の場所 (広場の案内人のそば。都が無ければ null)
    public static BlockPos spot(MinecraftServer server) {
        if (spot == null) {
            BlockPos anchor = jp.revati.skyabyss.facility.TownGuide.guideSpots(server).get(ANCHOR);
            if (anchor != null) {
                spot = jp.revati.skyabyss.loot.box.BoxOpeners.spotNear(server.overworld(), anchor);
            }
        }
        return spot;
    }

    static void resetAll() {
        spot = null;
        SQUADS.clear();
    }

    // 隊長が選んだ週の賭け (隊長 → 賭けの id)
    static final Map<UUID, LinkedHashSet<String>> WAGERS = new java.util.HashMap<>();

    static void toggleWager(ServerPlayer p, String id) {
        if (!SQUADS.containsKey(p.getUUID()) || !TowerWagers.thisWeek().contains(id)) {
            return;
        }
        LinkedHashSet<String> s = WAGERS.computeIfAbsent(p.getUUID(), k -> new LinkedHashSet<>());
        if (!s.remove(id)) {
            if (s.size() >= TowerWagers.MAX_PICK) {
                Notice.hint(p, "message.skyabyss.tower.wager.max", TowerWagers.MAX_PICK);
                return;
            }
            s.add(id);
        }
        menu(p);
    }

    // 記録の板: 人数ごとの最速と、今週の組合の順位
    public static void records(ServerPlayer p) {
        TowerRecords r = TowerRecords.get(p.server);
        p.sendSystemMessage(Component.translatable("message.skyabyss.tower.records.head").withStyle(s -> s.withColor(0xF2D78A)));
        boolean any = false;
        for (int n = 1; n <= Towers.MAX_SQUAD; n++) {
            TowerRecords.Best b = r.best(n);
            if (b != null) {
                any = true;
                p.sendSystemMessage(Component.translatable("message.skyabyss.tower.records.best", n, Towers.time(b.ticks()), String.join("、", b.names()), b.day()));
            }
        }
        if (!any) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.tower.records.none"));
        }
        p.sendSystemMessage(Component.translatable("message.skyabyss.tower.records.guild_head").withStyle(s -> s.withColor(0xF2D78A)));
        var top = r.ranking(5);
        for (int i = 0; i < top.size(); i++) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.tower.records.guild", i + 1, r.guildName(top.get(i).getKey()), top.get(i).getValue()));
        }
        if (top.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.tower.records.none"));
        }
        // 先週の上位 3 組合 (塔の誉れ。endgame/GuildWeek)
        var week = jp.revati.skyabyss.endgame.GuildWeek.get(p.server);
        var honors = new java.util.ArrayList<>(week.honors().entrySet());
        honors.sort(java.util.Map.Entry.comparingByValue());
        for (var h : honors) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.guild_week.honor_line", h.getValue(), week.name(h.getKey())).withStyle(s -> s.withColor(0xF2D78A)));
        }
    }

    // 塔の店 (data/skyabyss/shop/tower.json)
    public static final String SHOP = "tower";

    static boolean near(ServerPlayer p) {
        BlockPos s = spot(p.server);
        return s != null && p.level().dimension() == Level.OVERWORLD && p.distanceToSqr(Vec3.atBottomCenterOf(s)) <= RANGE * RANGE;
    }

    // 都の受付へ戻す (受付が無ければ大鐘楼の門のそば)
    public static void sendBack(ServerPlayer p) {
        BlockPos s = spot(p.server);
        if (s == null) {
            jp.revati.skyabyss.story.ch6.MoonWorld.leave(p);
            return;
        }
        p.teleportTo(p.server.overworld(), s.getX() + 0.5, s.getY(), s.getZ() + 1.5, 180f, 0f);
        p.resetFallDistance();
    }

    // ---------------------------------------------------------------- 受付の印と光の柱
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0 || p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos s = spot(p.server);
        if (s == null || p.distanceToSqr(Vec3.atBottomCenterOf(s)) > 96 * 96) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (p.tickCount % 40 == 0) {
            ensureDesk(level, s);
        }
        if (Towers.eligible(p)) {
            // その人にだけ送る光の柱
            for (int i = 0; i < 6; i++) {
                level.sendParticles(p, ParticleTypes.END_ROD, true, s.getX() + 0.5, s.getY() + 1 + i * 2.5, s.getZ() + 0.5, 2, 0.15, 1.0, 0.15, 0.0);
            }
            level.sendParticles(p, TowerArena.VIOLET, true, s.getX() + 0.5, s.getY() + 0.3, s.getZ() + 0.5, 6, 0.6, 0.1, 0.6, 0.0);
        }
    }

    static void ensureDesk(ServerLevel level, BlockPos s) {
        // 前の版の見えない受付 (Interaction) は片付ける
        for (Entity e : level.getEntitiesOfClass(Interaction.class, new AABB(s).inflate(4), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        if (jp.revati.skyabyss.facility.Attendants.find(level, jp.revati.skyabyss.facility.AttendantRole.TOWER_KEEPER, s) == null) {
            jp.revati.skyabyss.facility.Attendants.ensureAll(level, Map.of(jp.revati.skyabyss.facility.AttendantRole.TOWER_KEEPER.mark(), s));
        }
    }

    // 塔の店を開く (受付のそばで、資格のある人だけ)
    public static boolean openShop(ServerPlayer p) {
        if (!Towers.eligible(p) || !near(p)) {
            Notice.hint(p, Towers.eligible(p) ? "message.skyabyss.tower.desk.far" : "message.skyabyss.tower.not_yet");
            return false;
        }
        return jp.revati.skyabyss.facility.ShopService.open(p, SHOP, null);
    }

    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getTarget().getTags().contains(TAG)) {
            event.setCanceled(true);
            menu(p);
        }
    }

    // ---------------------------------------------------------------- 隊
    static UUID squadOf(UUID player) {
        for (Map.Entry<UUID, LinkedHashSet<UUID>> e : SQUADS.entrySet()) {
            if (e.getValue().contains(player)) {
                return e.getKey();
            }
        }
        return null;
    }

    static Component button(String key, String cmd, int color, Object... args) {
        return Component.translatable(key, args).withStyle(Style.EMPTY.withColor(color).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " " + cmd)));
    }

    static String name(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p == null ? "?" : p.getGameProfile().getName();
    }

    // 受付の一覧 (チャット)
    public static void menu(ServerPlayer p) {
        if (!Towers.eligible(p)) {
            Notice.hint(p, "message.skyabyss.tower.not_yet");
            return;
        }
        if (Towers.runOf(p) != null) {
            p.sendSystemMessage(button("message.skyabyss.tower.btn.leave", "leave", 0xC8A0E0));
            return;
        }
        Notice.narrate(p, Towers.usedToday(p) ? "message.skyabyss.tower.desk.used" : "message.skyabyss.tower.desk.hello");
        UUID mine = squadOf(p.getUUID());
        MutableComponent line = Component.empty();
        if (mine != null) {
            LinkedHashSet<UUID> s = SQUADS.get(mine);
            List<String> names = new ArrayList<>();
            for (UUID u : s) {
                names.add(name(p.server, u));
            }
            p.sendSystemMessage(Component.translatable("message.skyabyss.tower.desk.squad", name(p.server, mine), s.size(), Towers.MAX_SQUAD, String.join("、", names)));
            if (mine.equals(p.getUUID())) {
                line.append(button("message.skyabyss.tower.btn.start", "start", 0xF2D78A)).append("  ");
                // 今週の賭け (押すと選ぶ / 外す。三つまで)
                java.util.Set<String> chosen = WAGERS.getOrDefault(mine, new LinkedHashSet<>());
                MutableComponent w = Component.translatable("message.skyabyss.tower.wager.head", Math.round(TowerWagers.SHARD_BONUS * 100));
                for (String id : TowerWagers.thisWeek()) {
                    boolean on = chosen.contains(id);
                    w.append(" ").append(Component.literal(on ? "[✔ " : "[").append(Component.translatable("message.skyabyss.tower.wager." + id)).append("]")
                            .withStyle(Style.EMPTY.withColor(on ? 0xFF9A6A : 0xB8A0D8).withUnderlined(true)
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable("message.skyabyss.tower.wager." + id + ".desc")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " wager " + id))));
                }
                p.sendSystemMessage(w);
            }
            line.append(button("message.skyabyss.tower.btn.quit", "quit", 0xB0B0B8));
        } else if (!Towers.usedToday(p)) {
            line.append(button("message.skyabyss.tower.btn.create", "create", 0xF2D78A));
            for (Map.Entry<UUID, LinkedHashSet<UUID>> e : SQUADS.entrySet()) {
                if (e.getValue().size() < Towers.MAX_SQUAD) {
                    String leader = name(p.server, e.getKey());
                    line.append("  ").append(button("message.skyabyss.tower.btn.join", "join " + leader, 0x9CC8F0, leader, e.getValue().size(), Towers.MAX_SQUAD));
                }
            }
        }
        line.append(line.getSiblings().isEmpty() ? Component.empty() : Component.literal("  "))
                .append(button("message.skyabyss.tower.btn.shop", "shop", 0xC8B4FF)).append("  ")
                .append(button("message.skyabyss.tower.btn.records", "records", 0x9CC8F0));
        p.sendSystemMessage(line);
    }

    static boolean create(ServerPlayer p) {
        if (!Towers.eligible(p) || !near(p) || Towers.usedToday(p) || squadOf(p.getUUID()) != null || Towers.runOf(p) != null) {
            Notice.hint(p, Towers.usedToday(p) ? "message.skyabyss.tower.desk.used" : "message.skyabyss.tower.desk.far");
            return false;
        }
        LinkedHashSet<UUID> s = new LinkedHashSet<>();
        s.add(p.getUUID());
        // 受付のそばの同じ組の人も入る
        PartyData.Party party = Parties.of(p);
        if (party != null) {
            for (UUID u : party.members) {
                ServerPlayer m = p.server.getPlayerList().getPlayer(u);
                if (m != null && m != p && near(m) && Towers.eligible(m) && !Towers.usedToday(m) && squadOf(u) == null && s.size() < Towers.MAX_SQUAD) {
                    s.add(u);
                    Notice.hint(m, "message.skyabyss.tower.desk.joined", p.getGameProfile().getName());
                }
            }
        }
        SQUADS.put(p.getUUID(), s);
        menu(p);
        return true;
    }

    static boolean join(ServerPlayer p, String leaderName) {
        ServerPlayer leader = p.server.getPlayerList().getPlayerByName(leaderName);
        if (leader == null || !SQUADS.containsKey(leader.getUUID()) || !Towers.eligible(p) || !near(p) || Towers.usedToday(p) || squadOf(p.getUUID()) != null) {
            Notice.hint(p, "message.skyabyss.tower.desk.cannot_join");
            return false;
        }
        LinkedHashSet<UUID> s = SQUADS.get(leader.getUUID());
        if (s.size() >= Towers.MAX_SQUAD) {
            Notice.hint(p, "message.skyabyss.tower.desk.squad_full");
            return false;
        }
        // 同じ組の人も一緒に
        s.add(p.getUUID());
        PartyData.Party party = Parties.of(p);
        if (party != null) {
            for (UUID u : party.members) {
                ServerPlayer m = p.server.getPlayerList().getPlayer(u);
                if (m != null && m != p && near(m) && Towers.eligible(m) && !Towers.usedToday(m) && squadOf(u) == null && s.size() < Towers.MAX_SQUAD) {
                    s.add(u);
                }
            }
        }
        for (UUID u : s) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(u);
            if (m != null) {
                Notice.hint(m, "message.skyabyss.tower.desk.joined", p.getGameProfile().getName());
            }
        }
        return true;
    }

    static void quit(ServerPlayer p) {
        UUID leader = squadOf(p.getUUID());
        if (leader == null) {
            return;
        }
        if (leader.equals(p.getUUID())) {
            for (UUID u : SQUADS.remove(leader)) {
                ServerPlayer m = p.server.getPlayerList().getPlayer(u);
                if (m != null) {
                    Notice.hint(m, "message.skyabyss.tower.desk.disbanded");
                }
            }
        } else {
            SQUADS.get(leader).remove(p.getUUID());
            Notice.hint(p, "message.skyabyss.tower.desk.left");
        }
    }

    // 隊長が始める: 受付のそばにいて、資格があり、今日まだ入っていない人だけが入る
    static boolean start(ServerPlayer p) {
        LinkedHashSet<UUID> s = SQUADS.get(p.getUUID());
        if (s == null || !near(p)) {
            Notice.hint(p, "message.skyabyss.tower.desk.far");
            return false;
        }
        List<ServerPlayer> go = new ArrayList<>();
        for (UUID u : s) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(u);
            if (m == null) {
                continue;
            }
            if (!near(m) || !Towers.eligible(m) || Towers.usedToday(m)) {
                Notice.hint(m, "message.skyabyss.tower.desk.left_behind");
                continue;
            }
            go.add(m);
        }
        if (go.isEmpty()) {
            return false;
        }
        SQUADS.remove(p.getUUID());
        java.util.Set<String> wagers = WAGERS.getOrDefault(p.getUUID(), new LinkedHashSet<>());
        WAGERS.remove(p.getUUID());
        for (ServerPlayer m : go) {
            m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2f, 0.7f);
        }
        return Towers.start(p.server, go, false, wagers) != null;
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND)
                .then(Commands.literal("menu").executes(c -> {
                    menu(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("create").executes(c -> create(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("join").then(Commands.argument("leader", StringArgumentType.word())
                        .executes(c -> join(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "leader")) ? 1 : 0)))
                .then(Commands.literal("quit").executes(c -> {
                    quit(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("start").executes(c -> start(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("wager").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    toggleWager(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id"));
                    return 1;
                })))
                .then(Commands.literal("records").executes(c -> {
                    records(c.getSource().getPlayerOrException());
                    return 1;
                }))
                // 塔の店 (塔の欠片で塔の装備・塔の箱。受付のそばで)
                .then(Commands.literal("shop").executes(c -> openShop(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (Towers.runOf(p) != null) {
                        Towers.leave(p);
                    }
                    return 1;
                })));
    }
}

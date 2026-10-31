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
//   見え方  資格のある人 (終章の結末を選んだ人) にだけ、受付から光の柱 (その人にだけ送る粒)。触れる所は見えない Interaction (塔守の姿は形を見せてから)
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
        AABB box = new AABB(s).inflate(2);
        List<Entity> have = level.getEntitiesOfClass(Entity.class, box, e -> e.getTags().contains(TAG));
        if (!have.isEmpty()) {
            return;
        }
        Interaction it = EntityType.INTERACTION.create(level);
        if (it == null) {
            return;
        }
        it.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0f, 0f);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        it.saveWithoutId(tag);
        tag.putFloat("width", 1.2f);
        tag.putFloat("height", 2.4f);
        tag.putBoolean("response", true);
        it.load(tag);
        it.addTag(TAG);
        level.addFreshEntity(it);
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
                .append(button("message.skyabyss.tower.btn.shop", "shop", 0xC8B4FF));
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
        for (ServerPlayer m : go) {
            m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2f, 0.7f);
        }
        return Towers.start(p.server, go, false) != null;
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
                // 塔の店 (塔の欠片で塔の装備・塔の箱。受付のそばで)
                .then(Commands.literal("shop").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!Towers.eligible(p) || !near(p)) {
                        Notice.hint(p, "message.skyabyss.tower.desk.far");
                        return 0;
                    }
                    return jp.revati.skyabyss.facility.ShopService.open(p, SHOP, null) ? 1 : 0;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (Towers.runOf(p) != null) {
                        Towers.leave(p);
                    }
                    return 1;
                })));
    }
}

package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.NamedFoes;
import jp.revati.skyabyss.world.continent.RegionSpawner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 狩人の手帳 (2026-11-03 の案「狩人の手帳」。Monster Hunter の調査の段、Wynncraft の Bestiary。docs/design/54 の 3.5)。
// この MOD の敵を型ごとに、倒した数を記す (人ごと。死んでも消えない)。数に応じて頁が開く:
//   HABITAT 体  名前と出る所 (土地の湧きの表の土地と昼夜。名のある強敵はその土地。表にない敵は「決まった所だけ」)
//   STATS 体    強さ (最後に倒した一体の体力と攻撃。画面と同じ 10 倍で見せる)
//   FAMILIAR 体 狩りの慣れ: その型に与える傷 ×FAMILIAR_BONUS
// ボスの技の名前 (BossCue) は、名前を見た人の手帳に、技を出した型ごとに記す (心臓・船長も同じ)。
// 見る: 旅の記録の図鑑の枠を押す (endgame/client/HunterNotesScreen。Request → Snapshot)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class HunterNotes {
    public static final String KILLS = "skyabyss_hunt_kills";
    public static final String MOVES = "skyabyss_hunt_moves";
    public static final int HABITAT = 1;
    public static final int STATS = 5;
    public static final int FAMILIAR = 25;
    public static final double FAMILIAR_BONUS = 1.05;

    private HunterNotes() {
    }

    private static CompoundTag kills(Player p) {
        CompoundTag root = Codex.persisted(p);
        if (!root.contains(KILLS, Tag.TAG_COMPOUND)) {
            root.put(KILLS, new CompoundTag());
        }
        return root.getCompound(KILLS);
    }

    private static CompoundTag moves(Player p) {
        CompoundTag root = Codex.persisted(p);
        if (!root.contains(MOVES, Tag.TAG_COMPOUND)) {
            root.put(MOVES, new CompoundTag());
        }
        return root.getCompound(MOVES);
    }

    public static int killsOf(Player p, String id) {
        return kills(p).getCompound(id).getInt("n");
    }

    // 一体倒した。hp・atk はその一体の値 (強さの頁に出す)。新しい数を返す
    public static int addKill(ServerPlayer p, String id, float hp, float atk) {
        CompoundTag e = kills(p).getCompound(id);
        int n = e.getInt("n") + 1;
        e.putInt("n", n);
        e.putFloat("hp", hp);
        e.putFloat("atk", atk);
        kills(p).put(id, e);
        if (n == STATS || n == FAMILIAR) {
            Component name = name(id);
            if (n == STATS) {
                Notice.hint(p, "message.skyabyss.hunt.page", name, Component.translatable("gui.skyabyss.hunt.page.stats"));
            } else {
                Notice.gain(p, "message.skyabyss.hunt.familiar", name);
            }
        }
        return n;
    }

    // 技の名前を見た (move = 「ボスの鍵.技の鍵」。gui.skyabyss.attack.<move> で読む)
    public static void sawMove(ServerPlayer p, String id, String move) {
        ListTag l = moves(p).getList(id, Tag.TAG_STRING);
        for (Tag t : l) {
            if (t.getAsString().equals(move)) {
                return;
            }
        }
        l.add(StringTag.valueOf(move));
        moves(p).put(id, l);
    }

    public static List<String> movesOf(Player p, String id) {
        List<String> out = new ArrayList<>();
        for (Tag t : moves(p).getList(id, Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    // 慣れた型に与える傷の倍率
    public static double bonus(Player p, String id) {
        return killsOf(p, id) >= FAMILIAR ? FAMILIAR_BONUS : 1.0;
    }

    static Component name(String id) {
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
        return t.getDescription();
    }

    static String idOf(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    // ---------------------------------------------------------------- 記す・効く
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || dead.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) {
            return;
        }
        String id = idOf(dead);
        if (id.startsWith(SkyAbyss.MODID + ":")) {
            var atk = dead.getAttribute(Attributes.ATTACK_DAMAGE);
            addKill(p, id, dead.getMaxHealth(), atk == null ? 0f : (float) atk.getValue());
        }
    }

    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && !(event.getEntity() instanceof Player)) {
            double b = bonus(p, idOf(event.getEntity()));
            if (b != 1.0) {
                event.setAmount((float) (event.getAmount() * b));
            }
        }
    }

    // ---------------------------------------------------------------- 出る所
    // 土地の番号 (RegionSpawner.REGIONS) と昼夜 (0 昼・1 夜・2 両方)。表にも名のある強敵にもなければ regions は空
    public record Habitat(List<Integer> regions, int time, boolean named) {
    }

    public static Habitat habitat(String id) {
        Set<Integer> regions = new LinkedHashSet<>();
        boolean day = false;
        boolean night = false;
        List<List<RegionSpawner.Entry>> table = RegionSpawner.table();
        for (int r = 0; r < table.size(); r++) {
            for (RegionSpawner.Entry e : table.get(r)) {
                if (e.entity().equals(id)) {
                    regions.add(r);
                    day |= e.time() != RegionSpawner.Time.NIGHT;
                    night |= e.time() != RegionSpawner.Time.DAY;
                }
            }
        }
        boolean named = false;
        for (int r = 0; r < RegionSpawner.REGIONS.length; r++) {
            NamedFoes.Def d = NamedFoes.def(r);
            if (d != null && d.entity().equals(id)) {
                regions.add(r);
                named = true;
                day = night = true;
            }
        }
        return new Habitat(new ArrayList<>(regions), day && night ? 2 : night ? 1 : 0, named);
    }

    // ---------------------------------------------------------------- 画面へ
    // 敵の図鑑の型すべて (大陸の敵が先) と、技だけ見た型。倒していない型は id と数 0 だけ
    public static CompoundTag snapshot(ServerPlayer p) {
        Set<String> ids = new LinkedHashSet<>(Codex.foes());
        ids.addAll(moves(p).getAllKeys());
        ListTag list = new ListTag();
        for (String id : ids) {
            CompoundTag e = new CompoundTag();
            e.putString("id", id);
            CompoundTag k = kills(p).getCompound(id);
            int n = k.getInt("n");
            e.putInt("n", n);
            if (n >= HABITAT) {
                Habitat h = habitat(id);
                ListTag rs = new ListTag();
                h.regions().forEach(r -> rs.add(StringTag.valueOf(RegionSpawner.REGIONS[r])));
                e.put("regions", rs);
                e.putInt("time", h.time());
                e.putBoolean("named", h.named());
            }
            if (n >= STATS) {
                e.putFloat("hp", k.getFloat("hp"));
                e.putFloat("atk", k.getFloat("atk"));
            }
            ListTag mv = moves(p).getList(id, Tag.TAG_STRING);
            if (!mv.isEmpty()) {
                e.put("moves", mv.copy());
            }
            list.add(e);
        }
        CompoundTag t = new CompoundTag();
        t.put("foes", list);
        return t;
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "hunt_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "hunt"));
        public static final StreamCodec<FriendlyByteBuf, Snapshot> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(Snapshot::new, Snapshot::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.endgame.client.HunterNotesScreen.receive(payload)))
                .playToServer(Request.TYPE, Request.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) {
                        PacketDistributor.sendToPlayer(sp, new Snapshot(snapshot(sp)));
                    }
                }));
    }

    // 開発・撮影: /skyabyss_hunt add <型> <数> (体力と攻撃はその型の素の値) と /skyabyss_hunt move <型> <ボスの鍵.技の鍵>
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("skyabyss_hunt").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("add")
                        .then(net.minecraft.commands.Commands.argument("type", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .then(net.minecraft.commands.Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 999)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    String id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "type").toString();
                                    EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
                                    float hp = 20f;
                                    float atk = 0f;
                                    if (t.create(p.serverLevel()) instanceof LivingEntity le) {
                                        hp = le.getMaxHealth();
                                        var a = le.getAttribute(Attributes.ATTACK_DAMAGE);
                                        atk = a == null ? 0f : (float) a.getValue();
                                        le.discard();
                                    }
                                    int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count");
                                    for (int i = 0; i < n; i++) {
                                        addKill(p, id, hp, atk);
                                    }
                                    return n;
                                }))))
                .then(net.minecraft.commands.Commands.literal("move")
                        .then(net.minecraft.commands.Commands.argument("type", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .then(net.minecraft.commands.Commands.argument("move", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                                    sawMove(c.getSource().getPlayerOrException(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "type").toString(),
                                            com.mojang.brigadier.arguments.StringArgumentType.getString(c, "move"));
                                    return 1;
                                })))));
    }

    // 自己テスト用
    public static void clearForTest(Player p) {
        Codex.persisted(p).remove(KILLS);
        Codex.persisted(p).remove(MOVES);
    }
}

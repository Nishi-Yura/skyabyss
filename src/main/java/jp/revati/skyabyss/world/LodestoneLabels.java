package jp.revati.skyabyss.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.dungeon.Dungeon;
import jp.revati.skyabyss.dungeon.DungeonRun;
import jp.revati.skyabyss.dungeon.Dungeons;
import jp.revati.skyabyss.world.continent.ContinentPois;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 天然磁石の名札 (2026-11-03 の声「ロードストーンに右クリしてtpとかよく使うけど、そこの上に名札おいて、何が起こるか、を書いておいたほうがいい」)。
// 右クリックで何かが起こる天然磁石の上に、二行の名札 (題と、押すと何が起こるか) を浮かべる。人が NEAR まで来たら置き、FAR より離れたら外す。
// 名札は保存しない一時の text_display (印 TAG。起動のときに残っていた物は外す)。石ごとの文は describe が決める:
//   地下のダンジョンの層の門 (入口の間) と各層の帰り石・月への門と月の帰り石・雲海の帰りの台・巨神の背の錨石・深淵の記録の石
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LodestoneLabels {
    public static final String TAG = "skyabyss_lodestone_label";
    static final int NEAR = 14;
    static final int FAR = 28;
    static final int SCAN_Y = 6;
    static final float SCALE = 0.85f;

    private record Label(GlobalPos at, UUID title, UUID line) {
    }

    private static final Map<GlobalPos, Label> SHOWN = new HashMap<>();

    private LodestoneLabels() {
    }

    // 石の題と、押すと何が起こるか (翻訳の鍵と引数)。その石に役目がなければ null
    public record Text(Component title, Component line) {
    }

    @Nullable
    public static Text describe(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        MinecraftServer server = p.server;
        if (level.dimension() == Level.OVERWORLD) {
            for (Dungeon d : Dungeon.values()) {
                BlockPos gate = ContinentPois.extraOf(level, d.poiKind(), "gate");
                if (gate != null && gate.distManhattan(pos) <= 1) {
                    return new Text(Component.translatable("label.skyabyss.stone.dungeon_gate"),
                            Component.translatable("label.skyabyss.stone.dungeon_gate.sub", Component.translatable(d.nameKey())));
                }
            }
            if (pos.equals(jp.revati.skyabyss.story.ch6.MoonGate.gatePos(server))) {
                return new Text(Component.translatable("label.skyabyss.stone.moon_gate"), Component.translatable("label.skyabyss.stone.moon_gate.sub"));
            }
            return null;
        }
        if (jp.revati.skyabyss.dungeon.DungeonWorld.in(p)) {
            DungeonRun run = Dungeons.runOf(p);
            int f = run == null ? -1 : run.floorOf(p);
            if (run != null && f >= 0 && pos.equals(run.mark(f, "leave"))) {
                return new Text(Component.translatable("label.skyabyss.stone.return"), Component.translatable("label.skyabyss.stone.dungeon_leave.sub"));
            }
            return null;
        }
        if (jp.revati.skyabyss.story.ch6.MoonWorld.in(p) && pos.equals(jp.revati.skyabyss.story.ch6.MoonWorld.returnStone())) {
            return new Text(Component.translatable("label.skyabyss.stone.return"), Component.translatable("label.skyabyss.stone.moon_return.sub"));
        }
        if (jp.revati.skyabyss.story.finale.FinaleWorld.in(p) && pos.equals(jp.revati.skyabyss.story.finale.FinaleWorld.arrival().mark("return"))) {
            return new Text(Component.translatable("label.skyabyss.stone.return"), Component.translatable("label.skyabyss.stone.cloud_return.sub"));
        }
        if (level.dimension() == jp.revati.skyabyss.titan.TitanTravel.TITAN_BACK && pos.equals(jp.revati.skyabyss.titan.dungeon.TitanBackLayout.anchor())) {
            return new Text(Component.translatable("label.skyabyss.stone.anchor"), Component.translatable("label.skyabyss.stone.anchor.sub"));
        }
        if (jp.revati.skyabyss.story.ch5.AbyssWorld.in(p) && pos.equals(jp.revati.skyabyss.story.ch5.depths.DepthRun.heartStone())) {
            return new Text(Component.translatable("label.skyabyss.stone.depth_record"), Component.translatable("label.skyabyss.stone.depth_record.sub"));
        }
        return null;
    }

    // ---------------------------------------------------------------- 見回り (2 秒ごと)
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 21) {
            return;
        }
        Set<GlobalPos> want = new HashSet<>();
        Map<GlobalPos, Text> texts = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) {
                continue;
            }
            ServerLevel level = p.serverLevel();
            BlockPos c = p.blockPosition();
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int dx = -NEAR; dx <= NEAR; dx++) {
                for (int dz = -NEAR; dz <= NEAR; dz++) {
                    for (int dy = -SCAN_Y; dy <= SCAN_Y; dy++) {
                        q.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        if (!level.getBlockState(q).is(Blocks.LODESTONE)) {
                            continue;
                        }
                        GlobalPos g = GlobalPos.of(level.dimension(), q.immutable());
                        if (want.contains(g) || texts.containsKey(g)) {
                            want.add(g);
                            continue;
                        }
                        Text t = describe(p, q.immutable());
                        if (t != null) {
                            want.add(g);
                            texts.put(g, t);
                        }
                    }
                }
            }
        }
        // 置く (まだない物)
        for (GlobalPos g : want) {
            if (!SHOWN.containsKey(g) && texts.containsKey(g)) {
                ServerLevel level = server.getLevel(g.dimension());
                if (level != null) {
                    Label l = spawn(level, g, texts.get(g));
                    if (l != null) {
                        SHOWN.put(g, l);
                    }
                }
            }
        }
        // 外す (誰も FAR の中にいない物)
        for (var it = SHOWN.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            if (want.contains(e.getKey()) || anyoneNear(server, e.getKey())) {
                continue;
            }
            remove(server, e.getValue());
            it.remove();
        }
    }

    static boolean anyoneNear(MinecraftServer server, GlobalPos g) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == g.dimension() && p.blockPosition().distSqr(g.pos()) <= (double) FAR * FAR) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static Label spawn(ServerLevel level, GlobalPos g, Text t) {
        Display.TextDisplay title = text(level, g.pos(), 1.75, t.title(), 0xFFF2C14E, 1.0f);
        Display.TextDisplay line = text(level, g.pos(), 1.45, t.line(), 0xFFE9E0C8, 0.8f);
        if (title == null || line == null) {
            return null;
        }
        return new Label(g, title.getUUID(), line.getUUID());
    }

    @Nullable
    static Display.TextDisplay text(ServerLevel level, BlockPos at, double lift, Component body, int color, float scale) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", Component.Serializer.toJson(body.copy().withColor(color & 0xFFFFFF), level.registryAccess()));
        tag.putString("billboard", "vertical");
        tag.putString("alignment", "center");
        tag.putInt("background", 0x700A0C10);
        tag.putInt("line_width", 260);
        tag.putFloat("view_range", 1.0f);
        net.minecraft.nbt.CompoundTag light = new net.minecraft.nbt.CompoundTag();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        tag.put("brightness", light);
        net.minecraft.nbt.CompoundTag tf = new net.minecraft.nbt.CompoundTag();
        tf.put("left_rotation", jp.revati.skyabyss.opening.site.FacilityLabels.floats(0f, 0f, 0f, 1f));
        tf.put("right_rotation", jp.revati.skyabyss.opening.site.FacilityLabels.floats(0f, 0f, 0f, 1f));
        tf.put("translation", jp.revati.skyabyss.opening.site.FacilityLabels.floats(0f, 0f, 0f));
        float k = SCALE * scale;
        tf.put("scale", jp.revati.skyabyss.opening.site.FacilityLabels.floats(k, k, k));
        tag.put("transformation", tf);
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Entity e = EntityType.loadEntityRecursive(tag, level, en -> {
            en.moveTo(at.getX() + 0.5, at.getY() + lift, at.getZ() + 0.5, 0f, 0f);
            return en;
        });
        return e instanceof Display.TextDisplay d && level.addFreshEntity(d) ? d : null;
    }

    static void remove(MinecraftServer server, Label l) {
        ServerLevel level = server.getLevel(l.at().dimension());
        if (level == null) {
            return;
        }
        for (UUID id : new UUID[] {l.title(), l.line()}) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
    }

    // 前の起動で残った名札は外す (保存されたまま読み込まれた物)
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        for (Label l : new ArrayList<>(SHOWN.values())) {
            remove(event.getServer(), l);
        }
        SHOWN.clear();
    }

    // 自己テスト用
    static int shown() {
        return SHOWN.size();
    }

    static List<GlobalPos> shownAt() {
        return new ArrayList<>(SHOWN.keySet());
    }
}

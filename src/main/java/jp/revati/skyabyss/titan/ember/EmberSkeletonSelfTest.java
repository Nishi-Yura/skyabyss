package jp.revati.skyabyss.titan.ember;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.TitanGate;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼の骨組みの自己テスト (13 の 11.1節の順 1 の「確かめ方」): ディメンションと灰の原、背中の一覧と口、傷の種類 (炎に入れない)、
// 熱の API、部屋の箱 (重ならない・体の中・休み場が箱の中)、ID が重ならない、共通の TitanGate の待ちと 0.5 秒。
// 体 (body.bin) がまだ無いので、到着点は灰の原の上になる (B が体を建てたら舌の上)
public final class EmberSkeletonSelfTest {
    private EmberSkeletonSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ember skeleton (load)", EmberSkeletonSelfTest::load),
                SelfTestRunner.Step.once("ember skeleton", 10, EmberSkeletonSelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back != null) {
            BlockPos a = EmberBackLayout.ARRIVAL;
            back.setChunkForced(a.getX() >> 4, a.getZ() >> 4, true);
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            check(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember skeleton crashed", e);
            f.add("crashed: " + e);
        }
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back != null) {
            BlockPos a = EmberBackLayout.ARRIVAL;
            back.setChunkForced(a.getX() >> 4, a.getZ() >> 4, false);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember skeleton PASSED (dimension and ash plain, back list and bound back, damage types not fire, heat API, room boxes, unique IDs, titan gate, titan board ember stands on the ash plain)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST ember skeleton FAILED: {}", String.join("; ", f));
        }
    }

    private static void expect(boolean ok, String failure, List<String> f) {
        if (!ok) {
            f.add(failure);
        }
    }

    private static void check(MinecraftServer server, List<String> f) {
        // ---- ディメンションと灰の原
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back == null) {
            f.add("the ember_back dimension is missing");
            return;
        }
        BlockPos a = EmberBackLayout.ARRIVAL;
        // 灰の原は体の外 (口の先の北) で見る。体が建ったあとは到着点の上に頭がある
        BlockPos plainAt = new BlockPos(0, 0, EmberBackLayout.BODY.minZ() - 20);
        back.getChunk(plainAt.getX() >> 4, plainAt.getZ() >> 4);
        int surface = back.getHeight(Heightmap.Types.WORLD_SURFACE, plainAt.getX(), plainAt.getZ());
        expect(surface == EmberBackLayout.PLAIN_TOP + 1, "the ash plain surface is " + surface + ", expected " + (EmberBackLayout.PLAIN_TOP + 1), f);
        expect(back.getBlockState(new BlockPos(plainAt.getX(), EmberBackLayout.PLAIN_TOP, plainAt.getZ())).is(Blocks.SMOOTH_BASALT), "the plain top is not smooth basalt", f);
        expect(back.getBlockState(new BlockPos(plainAt.getX(), 0, plainAt.getZ())).is(Blocks.BEDROCK), "the plain has no bedrock", f);
        expect(back.getMaxBuildHeight() > EmberBackLayout.BODY.maxY() && back.getMinBuildHeight() < EmberBackLayout.BODY.minY(),
                "the body box does not fit in the dimension height", f);
        expect(back.getBiome(new BlockPos(0, 64, 0)).is(EmberIds.BACK_BIOME), "the ember_back biome is not used", f);
        Vec3 arrival = EmberTravel.arrival(back);
        if (back.getBlockState(a).isAir()) {
            expect(Math.abs(arrival.y - (EmberBackLayout.PLAIN_TOP + 1)) < 1e-6, "without the body the arrival is not on the ash plain: " + arrival, f);
        }

        // ---- 背中の一覧と口 (TitanBacks、Titans.bindBack)
        expect(TitanBacks.isBack(EmberIds.BACK) && TitanBacks.isBack(TideTravel.TIDE_BACK) && !TitanBacks.isBack(Level.OVERWORLD), "the back list is wrong", f);
        expect(Titans.EMBER.equals(Titans.titanOfBack(EmberIds.BACK)) && Titans.hasBack(Titans.EMBER), "the ember back is not bound (Titans.bindBack)", f);
        expect(EmberIds.TITAN.equals(Titans.EMBER), "EmberIds.TITAN is not Titans.EMBER", f);
        expect(TitanBacks.extraBanned(EmberIds.BACK, new ItemStack(Items.STRIDER_SPAWN_EGG)) && !TitanBacks.extraBanned(Level.OVERWORLD, new ItemStack(Items.STRIDER_SPAWN_EGG))
                && !TitanBacks.extraBanned(EmberIds.BACK, new ItemStack(Items.BREAD)) && !TitanBacks.extraBanned(TideTravel.TIDE_BACK, new ItemStack(Items.STRIDER_SPAWN_EGG)),
                "the ember back bans are wrong", f);

        // ---- 傷の種類: どれも炎ではない (火炎耐性で防げない)。焦げと呑まれたは防具を通る
        Registry<DamageType> types = server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (var key : List.of(EmberIds.HEAT, EmberIds.HEAT_BURST, EmberIds.ENGULFED)) {
            var holder = types.getHolder(key);
            if (holder.isEmpty()) {
                f.add("damage type " + key.location() + " is missing");
                continue;
            }
            Holder<DamageType> h = holder.get();
            expect(!h.is(DamageTypeTags.IS_FIRE), key.location() + " is in #is_fire", f);
            boolean bypass = h.is(DamageTypeTags.BYPASSES_ARMOR);
            expect(bypass == (key != EmberIds.HEAT_BURST), key.location() + " bypasses armor: " + bypass, f);
        }

        // ---- 熱の API
        FakePlayer p = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "ember_test_heat"));
        expect(EmberHeat.affects(p), "a survival player on the ember back is not affected by heat", f);
        expect(EmberHeat.get(p) == 0f, "heat does not start at 0", f);
        expect(EmberHeat.add(p, 30, EmberHeat.Reason.VENT) == 30f, "add 30 did not give 30", f);
        expect(EmberHeat.cool(p, 10) == 20f, "cool 10 did not give 20", f);
        expect(EmberHeat.band(p) == EmberHeat.Band.NORMAL && !EmberHeat.flushed(p), "20 is not normal", f);
        EmberHeat.set(p, 60);
        expect(EmberHeat.band(p) == EmberHeat.Band.FLUSHED && EmberHeat.flushed(p), "60 is not flushed", f);
        EmberHeat.set(p, 90);
        expect(EmberHeat.band(p) == EmberHeat.Band.SCORCHING, "90 is not scorching", f);
        expect(EmberHeat.add(p, 500, EmberHeat.Reason.HEART) == EmberHeat.MAX && EmberHeat.band(p) == EmberHeat.Band.SCORCHED, "heat is not capped at 100", f);
        expect(EmberHeat.add(p, -40, EmberHeat.Reason.OTHER) == 60f, "a negative add does not cool", f);
        expect(EmberHeat.cool(p, 1000) == 0f, "heat goes below 0", f);
        EmberHeat.set(p, 40);
        EmberHeat.reset(p);
        expect(EmberHeat.get(p) == 0f, "reset does not give 0", f);
        FakePlayer ground = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "ember_test_ground"));
        expect(!EmberHeat.affects(ground) && EmberHeat.add(ground, 50, EmberHeat.Reason.VENT) == 0f, "heat builds outside the ember back", f);

        // ---- 部屋の箱: 重ならない、体の箱の中 (心臓の間と前室は体の外の区画 0 番の枠の封じた一室。trial/raid/InstanceArenas)、
        // 休み場は床の上が空いているはずの箱の中
        List<Map.Entry<String, EmberBackLayout.Box>> rooms = new ArrayList<>(EmberBackLayout.ROOMS.entrySet());
        for (int i = 0; i < rooms.size(); i++) {
            EmberBackLayout.Box bi = rooms.get(i).getValue();
            String key = rooms.get(i).getKey();
            if (key.equals("heart") || key.equals("heart_ante")) {
                expect(jp.revati.skyabyss.trial.TrialSlot.inInstance(bi.minX(), bi.minZ()) && jp.revati.skyabyss.trial.TrialSlot.inInstance(bi.maxX(), bi.maxZ())
                        && !bi.intersects(EmberBackLayout.BODY), "room " + key + " is not a sealed room in the instance area", f);
            } else {
                expect(bi.inside(EmberBackLayout.BODY), "room " + key + " is outside the body box", f);
            }
            for (int j = i + 1; j < rooms.size(); j++) {
                expect(!bi.intersects(rooms.get(j).getValue()), "rooms " + rooms.get(i).getKey() + " and " + rooms.get(j).getKey() + " overlap", f);
            }
        }
        for (String name : List.of("west", "east", "smith", "south", "heart", "heart_ante", "hub")) {
            expect(EmberBackLayout.ROOMS.containsKey(name), "room box " + name + " is missing", f);
        }
        expect(EmberBackLayout.RETURN_STONE.inside(EmberBackLayout.MOUTH), "the return stone is outside the mouth", f);
        expect(!EmberBackLayout.RETURN_STONE.contains(EmberBackLayout.ARRIVAL.above()), "the arrival is on the return stone", f);
        expect(EmberBackLayout.HUB.contains(EmberBackLayout.HUB_CENTER) && EmberBackLayout.SMITH.contains(EmberBackLayout.SMITH_CENTER)
                && EmberBackLayout.HEART.contains(EmberBackLayout.HEART_CENTER), "a room center is outside its box", f);
        List<String> restNames = new ArrayList<>();
        for (EmberBackLayout.Rest r : EmberBackLayout.RESTS) {
            restNames.add(r.name());
        }
        expect(restNames.equals(EmberIds.RESTS), "the rests are not EmberIds.RESTS: " + restNames, f);
        String[][] restIn = {{"heart", "heart_ante"}};
        for (String[] ri : restIn) {
            expect(EmberBackLayout.ROOMS.get(ri[1]).contains(EmberBackLayout.rest(ri[0]).floor()), "rest " + ri[0] + " is not in " + ri[1], f);
        }

        // ---- ID が重ならない (EmberIds の文字列の定数。lang の頭・名前空間の名前を除く)
        Set<String> seen = new HashSet<>();
        try {
            for (Field field : EmberIds.class.getFields()) {
                if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers()) || field.getName().startsWith("LANG_")
                        || List.of("TITAN", "BACK_NAME", "BODY_BIN", "WEST", "EAST", "SOUTH", "LORE_FOUND", "LORE_TITAN", "LORE_RUINS", "LORE_SMITH",
                                "LORE_STILLED", "LORE_LEDGER").contains(field.getName())) {
                    continue;
                }
                String v = (String) field.get(null);
                expect(v.matches("[a-z0-9_]+"), "ID " + field.getName() + " is not a plain path: " + v, f);
                expect(seen.add(v), "ID " + v + " is used twice in EmberIds", f);
            }
        } catch (IllegalAccessException e) {
            f.add("could not read EmberIds: " + e);
        }
        int codex = 10 + jp.revati.skyabyss.item.ChapterGear.EMBER.size();
        expect(EmberIds.CODEX.size() == codex && new HashSet<>(EmberIds.CODEX).size() == codex, "the codex is not 10 + the job weapons, distinct", f);
        expect(EmberIds.CODEX.containsAll(EmberIds.FIRST_CLEAR_CHOICES), "a first clear choice is not in the codex", f);

        // ---- 共通の TitanGate: 乗る資格、待ち、戻る段の 0.5 秒 (2 tick ごとに数えて 10 tick)
        TitanGate gate = EmberTravel.PASSAGE;
        FakePlayer g = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "ember_test_gate"));
        boolean gateOn = jp.revati.skyabyss.progress.Progression.gateOn(server);
        expect(!gateOn || !gate.checkBoard(g), "a player who has not stopped the thunder wing may board the ember", f);
        expect(!gateOn || gate.coolingDown(g), "a refused player is not made to wait", f);
        g.getPersistentData().remove("skyabyss_ember_cooldown");
        ProgressData data = ProgressData.get(server);
        for (String t : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING)) {
            data.markPersonal(g.getUUID(), t);
        }
        expect(gate.checkBoard(g) && !gate.coolingDown(g), "a player who stopped the thunder wing may not board the ember", f);
        int fired = 0;
        for (int i = 0; i < 5; i++) {
            fired += gate.dwell(g, true, 2) ? 1 : 0;
        }
        expect(fired == 1, "the return stone does not fire once after 0.5 s: " + fired, f);
        gate.dwell(g, true, 2);
        gate.dwell(g, false, 2);
        boolean early = false;
        for (int i = 0; i < 4; i++) {
            early |= gate.dwell(g, true, 2);
        }
        expect(!early, "stepping off the return stone does not restart the count", f);
        gate.startCooldown(g);
        expect(gate.coolingDown(g) && !gate.dwell(g, true, 20), "the return stone counts while cooling down", f);
        gate.forget(g.getUUID());

        // ---- 「/skyabyss titan board ember」で灰の原 (体が建てば舌の上) に立つ。地上から、資格のある人がコマンドを打つ
        ServerLevel overworld = server.overworld();
        FakePlayer r = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "ember_test_board"));
        for (String t : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING)) {
            data.markPersonal(r.getUUID(), t);
        }
        r.moveTo(0.5, 200, 0.5);
        try {
            server.getCommands().performPrefixedCommand(r.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "skyabyss titan board ember");
            Vec3 want = EmberTravel.arrival(back);
            expect(r.level().dimension() == EmberIds.BACK, "titan board ember did not move the player to the ember back: " + r.level().dimension().location(), f);
            // 偽のプレイヤーは接続が無いので、ディメンションを移る位置合わせ (connection.teleport) が効かない。立つ場所は到着点で見る
            BlockPos at = BlockPos.containing(want);
            expect(!back.getBlockState(at.below()).isAir() && back.getBlockState(at).isAir() && back.getBlockState(at.above()).isAir(),
                    "the arrival " + want + " is not standing room on the ash plain (" + back.getBlockState(at.below()) + " below)", f);
        } finally {
            if (r.level() instanceof ServerLevel at) {
                at.removePlayerImmediately(r, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            EmberTravel.PASSAGE.forget(r.getUUID());
            for (String t : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING)) {
                data.clearPersonal(r.getUUID(), t);
            }
        }
        for (String t : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING)) {
            data.clearPersonal(g.getUUID(), t);
        }
    }
}

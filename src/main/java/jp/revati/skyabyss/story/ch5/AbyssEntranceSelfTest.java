package jp.revati.skyabyss.story.ch5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.StorySetup;
import jp.revati.skyabyss.town.TownData;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST ch5 entrance: 五章の入口。
//   データ: main_abyss の段 (rumor・prepare・door・shaft・gate・層ごとの camp/marks/lord・descend・warden・heart・report)、支度 ch5/black_iron (ブレイズロッド 3・黒曜石 2 → 鍛冶師。報酬に鍛冶型)
//   流れ: 印を出発点のそばへ差し替え、戸の 2 マスを強化深層岩に。鍵なし・段の前は開かない → 段 door で鍵を持つと開く (記録・合図で段 shaft)
//        → 底の境の印のそばで合図 (段 gate) → 鍵なしでは門は開かない → 鍵で格子が溶ける → 門の列で合図 (段 bone_camp)。踊り場の一言は人ごとに一度
public final class AbyssEntranceSelfTest {
    private AbyssEntranceSelfTest() {
    }

    // 出発点のまわりを読み込む (読み込みは次の tick から効く)。外すのは run の終わり
    public static void load(MinecraftServer server) {
        force(server.overworld(), true);
    }

    private static void force(ServerLevel level, boolean on) {
        BlockPos s = level.getSharedSpawnPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced((s.getX() >> 4) + dx, (s.getZ() >> 4) + dz, on);
            }
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        TownData data = TownData.get(server);
        boolean wasOpen = data.abyssOpen;
        boolean gateWas = data.abyssGateOpen;
        boolean cellarWas = data.cellarOpen;
        try {
            data(f);
            BlockPos s = level.getSharedSpawnPos();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, s.getX(), s.getZ());
            BlockPos door = new BlockPos(s.getX() + 6, y + 20, s.getZ());
            BlockPos land1 = door.offset(10, 0, 0);
            BlockPos land2 = door.offset(-10, 0, 0);
            BlockPos gate = door.offset(0, 0, 10);
            Map<String, BlockPos> marks = new HashMap<>();
            marks.put(AbyssEntrance.DOOR, door);
            marks.put(AbyssEntrance.LANDINGS[0], land1);
            marks.put(AbyssEntrance.LANDINGS[1], land2);
            marks.put(AbyssEntrance.GATE, gate);
            TownGuests.setTestMarks(marks);
            data.abyssOpen = false;
            data.abyssGateOpen = false;
            level.setBlock(door, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 3);
            level.setBlock(door.above(), Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 3);

            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ch5_test"));
            Quests.forgetAll(p);
            p.getInventory().clearContent();
            p.moveTo(door.getX() + 0.5, door.getY(), door.getZ() - 2.5);
            check(f, Quests.start(p, AbyssEntrance.QUEST), "main_abyss did not start");
            check(f, "rumor".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)), "main_abyss should start at rumor: " + TownGuests.stageOf(p, AbyssEntrance.QUEST));
            check(f, AbyssEntrance.nearDoor(door.above(), door) && AbyssEntrance.nearDoor(door.east(), door) && !AbyssEntrance.nearDoor(door.east(3), door),
                    "nearDoor does not match the door and its neighbours");
            check(f, !AbyssEntrance.readyFor("rumor") && !AbyssEntrance.readyFor("prepare") && AbyssEntrance.readyFor("door"), "readyFor is wrong");
            // 段 door まで進める (噂・支度は会話とクエストの段)
            for (int i = 0; i < 4 && !"door".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)); i++) {
                Quests.forceStage(p, AbyssEntrance.QUEST);
            }
            check(f, "door".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)), "could not reach the door stage: " + TownGuests.stageOf(p, AbyssEntrance.QUEST));
            // 鍵なしでは見回りで開かない
            AbyssEntrance.check(server, p, "door", door);
            check(f, !AbyssEntrance.isOpen(server) && !level.getBlockState(door).isAir(), "the door opened without the key");
            // 鍵を持って開ける
            p.getInventory().add(new ItemStack(StorySetup.SKY_KEY.get()));
            check(f, AbyssEntrance.holdsKey(p), "the sky key in the inventory was not seen");
            AbyssEntrance.open(server, p);
            Quests.poll(p, true);
            check(f, AbyssEntrance.isOpen(server) && level.getBlockState(door).isAir() && level.getBlockState(door.above()).isAir(),
                    "opening did not record or carve the door");
            check(f, "shaft".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)), "opening the door did not advance to shaft: " + TownGuests.stageOf(p, AbyssEntrance.QUEST));
            // 踊り場の一言は一度だけ
            p.moveTo(land1.getX() + 0.5, land1.getY(), land1.getZ() + 0.5);
            AbyssEntrance.check(server, p, "shaft", door);
            check(f, p.getPersistentData().getBoolean("skyabyss_ch5_landing_1") && !AbyssEntrance.once(p, "skyabyss_ch5_landing_1"),
                    "the first landing line was not recorded once");
            // 底の境
            p.moveTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
            AbyssEntrance.check(server, p, "shaft", door);
            Quests.poll(p, true);
            check(f, "gate".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)), "reaching the threshold did not advance to gate: " + TownGuests.stageOf(p, AbyssEntrance.QUEST));
            // 門: 都は回して建つので、格子の列は印の東 1 (枠の柱は列の両端の外、幅 5・高さ 6) にして確かめる
            //   (2026-10-23 の試遊「鉄格子全部消えなかった」: 前は列を北と決め打ちしていて、回った都では一部しか溶けなかった)
            AbyssGate.forgetFacing();
            BlockPos row = gate.east();
            for (int a = -3; a <= 3; a++) {
                for (int dy = 0; dy <= 6; dy++) {
                    BlockPos q = row.south(a).above(dy);
                    boolean pillar = Math.abs(a) == 3;
                    level.setBlock(q, pillar ? Blocks.REINFORCED_DEEPSLATE.defaultBlockState()
                            : dy == 6 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState(), 3);
                }
            }
            level.setBlock(gate.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            AbyssGate.Facing fa = AbyssGate.facing(server, level, gate);
            check(f, fa.toward() == net.minecraft.core.Direction.EAST && fa.half() == 2, "the gate facing was not found from the pillars: " + fa);
            check(f, AbyssGate.barsLeft(level, gate) == 30, "the test gate should have 30 bars: " + AbyssGate.barsLeft(level, gate));
            check(f, AbyssGate.inGate(gate.east(), gate, fa) && AbyssGate.inGate(gate.east().south(2), gate, fa) && AbyssGate.inGate(gate.east().north(2), gate, fa)
                    && !AbyssGate.inGate(gate, gate, fa) && !AbyssGate.inGate(gate.east().south(3), gate, fa) && !AbyssGate.inGate(gate.north(), gate, fa),
                    "inGate does not match the bar row");
            ItemStack key = p.getInventory().removeItem(p.getInventory().findSlotMatchingItem(new ItemStack(StorySetup.SKY_KEY.get())), 1);
            AbyssGate.check(server, p, gate, false);
            check(f, !AbyssGate.isOpen(server) && AbyssGate.barsLeft(level, gate) == 30, "the gate opened without the key");
            p.getInventory().add(key);
            AbyssGate.check(server, p, gate, false);
            check(f, AbyssGate.isOpen(server) && AbyssGate.barsLeft(level, gate) == 0, "the key did not melt every bar: " + AbyssGate.barsLeft(level, gate) + " left");
            // 開いたあとに格子が戻っても (都の建て直し・古い記録)、近くに来れば全部溶け直す
            level.setBlock(bar(gate, 2, 5), Blocks.IRON_BARS.defaultBlockState(), 3);
            level.setBlock(bar(gate, -2, 0), Blocks.IRON_BARS.defaultBlockState(), 3);
            AbyssGate.forgetFacing();
            AbyssGate.check(server, p, gate, false);
            check(f, AbyssGate.barsLeft(level, gate) == 0, "bars that came back after opening were not melted again: " + AbyssGate.barsLeft(level, gate));
            p.moveTo(gate.getX() + 1.5, gate.getY(), gate.getZ() + 0.5);
            AbyssGate.check(server, p, gate, false);
            Quests.poll(p, true);
            check(f, "bone_camp".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)), "passing the gate did not advance to bone_camp: " + TownGuests.stageOf(p, AbyssEntrance.QUEST));
            party(server, level, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch5 entrance crashed", e);
            f.add("crashed: " + e);
        } finally {
            TownGuests.setTestMarks(null);
            AbyssGate.forgetFacing();
            data.abyssOpen = wasOpen;
            data.abyssGateOpen = gateWas;
            data.cellarOpen = cellarWas;
            force(level, false);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch5 entrance PASSED (quests, prep reward, door needs the stage and the key, carve and record, landing lines once, threshold signal, gate needs the key, every bar of a turned gate melts and melts again, gate row signal)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch5 entrance FAILED: {}", m));
        }
    }

    // パーティ: 居合わせた一人の合図で、離れた仲間の同じ段も進む (五章のメインの合図は全部 party)
    private static void party(MinecraftServer server, ServerLevel level, List<String> f) {
        QuestDef main = QuestManager.quest(AbyssEntrance.QUEST);
        if (main != null) {
            for (QuestDef.Stage st : main.stages()) {
                for (QuestDef.Objective ob : st.objectives()) {
                    if (ob.type().equals("signal")) {
                        check(f, ob.party(), "main_abyss " + st.id() + "/" + ob.id() + " is not a party objective");
                    }
                }
            }
        }
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ch5_party_a"));
        FakePlayer b = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ch5_party_b"));
        jp.revati.skyabyss.party.PartyData pd = jp.revati.skyabyss.party.PartyData.get(server);
        jp.revati.skyabyss.party.PartyData.Party party = pd.create(a.getUUID(), "a");
        pd.add(party, b.getUUID(), "b");
        try {
            Map<UUID, net.minecraft.server.level.ServerPlayer> online = Map.of(a.getUUID(), a, b.getUUID(), b);
            for (FakePlayer q : List.of(a, b)) {
                Quests.forgetAll(q);
                Quests.start(q, AbyssEntrance.QUEST);
                for (int i = 0; i < 24 && !"warden".equals(TownGuests.stageOf(q, AbyssEntrance.QUEST)); i++) {
                    Quests.forceStage(q, AbyssEntrance.QUEST);
                }
            }
            b.moveTo(a.getX() + 50, a.getY(), a.getZ());
            Quests.signalWithParty(level, List.of(a), "abyss_heart", online::get);
            Quests.poll(a, true);
            Quests.poll(b, true);
            check(f, "heart".equals(TownGuests.stageOf(a, AbyssEntrance.QUEST)), "the actor did not finish the warden stage");
            check(f, "heart".equals(TownGuests.stageOf(b, AbyssEntrance.QUEST)), "a party mate 50 blocks away did not finish the warden stage: " + TownGuests.stageOf(b, AbyssEntrance.QUEST));
        } finally {
            pd.disband(party);
        }
    }

    private static void data(List<String> f) {
        QuestDef main = QuestManager.quest(AbyssEntrance.QUEST);
        check(f, main != null, "main_abyss is not loaded");
        if (main != null) {
            List<String> ids = main.stages().stream().map(QuestDef.Stage::id).toList();
            List<String> want = new ArrayList<>(List.of("rumor", "prepare", "door", "shaft"));
            want.addAll(jp.revati.skyabyss.story.ch5.layers.Layers.stagesForTest());
            check(f, ids.equals(want), "main_abyss stages: " + ids);
            check(f, "main".equals(main.category()) && "chapter2".equals(main.chapter()), "main_abyss should be a chapter-5 main quest");
        }
        QuestDef iron = QuestManager.quest("ch5/black_iron");
        check(f, iron != null, "ch5/black_iron is not loaded");
        check(f, QuestManager.loadErrors().stream().noneMatch(e -> e.contains("main_abyss") || e.contains("black_iron")),
                "load errors for the chapter-5 quests: " + QuestManager.loadErrors());
    }

    private static BlockPos bar(BlockPos gate, int along, int dy) {
        return gate.east().south(along).above(dy);
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}

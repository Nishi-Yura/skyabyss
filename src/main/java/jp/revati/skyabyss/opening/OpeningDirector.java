package jp.revati.skyabyss.opening;

import jp.revati.skyabyss.util.Players;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LorePageItem;
import jp.revati.skyabyss.lore.LoreSetup;
import jp.revati.skyabyss.lore.LoreState;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.NpcSpawner;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.opening.OpeningLogic.Env;
import jp.revati.skyabyss.opening.OpeningLogic.Facts;
import jp.revati.skyabyss.opening.OpeningLogic.Fx;
import jp.revati.skyabyss.opening.OpeningNetwork.CuePayload;
import jp.revati.skyabyss.opening.OpeningNetwork.ObjectivePayload;
import jp.revati.skyabyss.opening.OpeningSites.Kind;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.sky.SkyEventData;
import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.sky.SkyPass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 序盤の拍子 B0〜B10 (docs/design/17 の 3 章) を、人ごとに必ず起こす。判断は OpeningLogic、ここは世界と人への働きかけ。
// 場所は OpeningSites から (まだ建っていない世界でも詰まらない)。最初の影・書・岩鯨の門・語り部の案内は OpeningBeats / SkyEvents /
// TitanEvents の保証のまま、その上に積む。
// ホラーの決まり (01、06、17 の 6 章): ジャンプスケアなし (音は 5 秒かけて消す・戻す)、ダメージ・拘束なし、戦闘中・体力が半分以下・
// 敵が 8 ブロック以内・水中・睡眠中は後ろへずらす、足音と歌は一度だけ、horrorEventFrequency 0 では起こさない、本人にだけ。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class OpeningDirector {
    // 見回りの間隔 (tick)。人ごとにずらす
    private static final int INTERVAL = 20;
    // 目的の帯を送り直す間隔 (目印までの距離は帯が自分で測るので、目的が変わったときと、たまの再送だけ)
    private static final long RESEND = 600L;
    // 戦闘中とみなす、最後に傷を受けた (与えた) あとの tick
    private static final int COMBAT_TICKS = 200;
    // 敵が近いとみなす距離
    private static final double HOSTILE_NEAR = 8.0;
    // 足音の間、動物が固まる範囲
    private static final double FREEZE_RADIUS = 24.0;
    // 主のクエスト
    static final String MAIN_QUEST = "main_song";

    // 足音の間、近くの動物を止める (プレイヤー → 終わる時刻)。再起動で消えてよい
    private static final Map<UUID, Long> FREEZE = new HashMap<>();
    // 空の影が去る時刻 (進行中のものから覚える。再起動で消えてよい)
    private static long lastPassEnd = -1;
    // 落とした破れた頁 (プレイヤー → 頁のエンティティ)。再起動で消えるので、頁の実体にも持ち主の印 (PAGE_OWNER_TAG) を書き、
    // 読んだ・見送ったときは印で探して消す (再起動のあとでも地面に残らない)
    private static final Map<UUID, UUID> PAGES = new HashMap<>();
    static final String PAGE_OWNER_TAG = "skyabyss_opening_page_owner";

    private OpeningDirector() {
    }

    // 自己テスト用: 再起動で覚えている頁の ID が消えた状態にする
    static void forgetPagesForTest() {
        PAGES.clear();
    }

    // その人のために落とした頁 (地面にあるもの) を消す。覚えている実体と、読み込まれている中の印つきの実体の両方。消した数を返す
    public static int discardDroppedPages(MinecraftServer server, UUID owner) {
        int n = 0;
        UUID id = PAGES.remove(owner);
        if (id != null) {
            var entity = server.overworld().getEntity(id);
            if (entity != null) {
                entity.discard();
                n++;
            }
        }
        for (var e : com.google.common.collect.Lists.newArrayList(server.overworld().getAllEntities())) { // discard で一覧から外れるので写しを回す
            if (e instanceof ItemEntity item && !item.isRemoved() && item.getPersistentData().hasUUID(PAGE_OWNER_TAG)
                    && item.getPersistentData().getUUID(PAGE_OWNER_TAG).equals(owner)) {
                item.discard();
                n++;
            }
        }
        return n;
    }

    public static OpeningState state(net.minecraft.world.entity.player.Player player) {
        return player.getData(OpeningSetup.STATE);
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            SkyPass pass = SkyEvents.activePass();
            if (pass != null) {
                lastPassEnd = pass.startTick() + pass.duration();
            }
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || !player.isAlive() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long freeze = FREEZE.get(player.getUUID());
        if (freeze != null) {
            if (now > freeze) {
                FREEZE.remove(player.getUUID());
            } else if (now % 10 == 0) {
                freezeAnimals(level, player);
            }
        }
        if ((now + (player.getId() & 0xFF)) % INTERVAL != 0) {
            return;
        }
        run(level, player);
    }

    // 野営地の帳面 (置かれた伝承の書。タグ付き) は、ふつうの拾い方では拾えない。近づいたとき OpeningLogic が「拾った」ことにして、
    // 人ごとに伝承の書として渡す (書の項目と最初の頁が記される)。帳面そのものは野営地に残り、あとから来た人も拾える
    @SubscribeEvent
    static void onItemPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        if (event.getItemEntity().getTags().contains(jp.revati.skyabyss.opening.site.CampBuilder.NOTEBOOK_TAG)) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    @SubscribeEvent
    static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            state(player).sentObjective = null;
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FREEZE.clear();
        PAGES.clear();
        lastPassEnd = -1;
    }

    // 自己テスト用: 古いセーブを読み込んだ直後の人にする (拍子の記録は空、「コマンドで消した直後」ではない)
    public static void resetAsLoadedSave(ServerPlayer player) {
        reset(player);
        state(player).fresh = false;
    }

    // 一人の見回り (テストからも呼べる)
    public static void run(ServerLevel level, ServerPlayer player) {
        OpeningState st = state(player);
        long now = level.getGameTime();
        Env env = env(level, player);
        Facts facts = facts(level, player, st, env);
        placeBeats(player, facts);
        Fx fx = new Live(level, player, st);
        OpeningLogic.step(st, env, facts, fx);
        if (st.awakeAt >= 0) {
            st.fresh = false;
        }
        syncObjective(level, player, st, facts, env, now);
        boneForeshadow(level, player, st, env);
    }

    // 序盤の場所に着いたことを進捗へ (半径は OpeningSites.Kind と同じ)
    private static void placeBeats(ServerPlayer player, Facts facts) {
        if (facts.campDist() >= 0 && facts.campDist() <= Kind.CAMP.radius) {
            jp.revati.skyabyss.advancement.Beats.fire(player, "place:camp");
        }
        if (facts.shelterDist() >= 0 && facts.shelterDist() <= Kind.SHELTER.radius) {
            jp.revati.skyabyss.advancement.Beats.fire(player, "place:shelter");
        }
        if (facts.footprintNear()) {
            jp.revati.skyabyss.advancement.Beats.fire(player, "place:footprint");
        }
        if (facts.slabNear()) {
            jp.revati.skyabyss.advancement.Beats.fire(player, "place:ribs");
        }
    }

    static Env env(ServerLevel level, ServerPlayer player) {
        boolean sky = level.canSeeSky(player.blockPosition().above());
        boolean overworld = level.dimension() == Level.OVERWORLD;
        boolean awake = player.isAlive() && !player.isSleeping() && !player.isSpectator();
        boolean deep = !sky && player.getY() < level.getSeaLevel() - 24;
        boolean danger = danger(level, player);
        return new Env(level.getGameTime(), level.getDayTime(), overworld, awake, sky, deep, player.isUnderWater(), danger,
                Config.HORROR_EVENT_FREQUENCY.get() > 0, SkyEvents.activePassFor(player) != null);
    }

    // 戦っている・傷が深い・敵が近い (17 の 6 章: 殺さない。拍子は後ろへずらす)
    static boolean danger(ServerLevel level, ServerPlayer player) {
        int hurt = player.getLastHurtByMobTimestamp();
        int hit = player.getLastHurtMobTimestamp();
        if ((hurt > 0 && player.tickCount - hurt < COMBAT_TICKS) || (hit > 0 && player.tickCount - hit < COMBAT_TICKS)) {
            return true;
        }
        if (player.getHealth() <= player.getMaxHealth() * 0.5f) {
            return true;
        }
        return !level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(HOSTILE_NEAR), m -> m.isAlive()).isEmpty();
    }

    static Facts facts(ServerLevel level, ServerPlayer player, OpeningState st, Env env) {
        MinecraftServer server = level.getServer();
        LoreState lore = Lore.state(player);
        BlockPos pos = player.blockPosition();
        boolean planned = OpeningSites.planned(server);
        Optional<BlockPos> camp = planned ? OpeningSites.camp(server) : Optional.empty();
        boolean campBuilt = camp.isPresent() && OpeningSites.built(server, Kind.CAMP);
        double notebook = -1;
        if (campBuilt) {
            Optional<BlockPos> nb = OpeningSites.notebook(server);
            if (nb.isPresent() && Math.abs(nb.get().getY() - pos.getY()) <= 3) {
                notebook = OpeningSites.horizontal(nb.get(), pos);
            }
        }
        double campDist = campBuilt ? OpeningSites.horizontal(camp.get(), pos) : -1;
        double shelterDist = -1;
        if (planned && OpeningSites.built(server, Kind.SHELTER)) {
            Optional<BlockPos> sh = OpeningSites.shelter(server);
            if (sh.isPresent()) {
                shelterDist = OpeningSites.horizontal(sh.get(), pos);
            }
        }
        boolean footNear = false;
        if (planned && OpeningSites.built(server, Kind.FOOTPRINT)) {
            footNear = OpeningSites.insideFootprint(server, pos, OpeningLogic.FOOTPRINT_MARGIN);
        }
        boolean slabNear = false;
        if (planned && OpeningSites.built(server, Kind.RIBS)) {
            Optional<BlockPos> slab = OpeningSites.slab(server);
            slabNear = slab.isPresent() && OpeningSites.horizontal(slab.get(), pos) <= OpeningLogic.SLAB_NEAR;
        }
        SkyEventData sky = SkyEventData.get(level);
        long now = env.now();
        long passEnd = Math.max(lastPassEnd, SkyEvents.personalEnd(player));
        boolean passEnded = passEnd >= 0 && st.awakeAt >= 0 && passEnd >= st.awakeAt && now >= passEnd
                && now - passEnd <= OpeningLogic.PASS_END_WINDOW;
        boolean legacy = !st.fresh && (!lore.unlocked().isEmpty() || lore.bookGiven() || NpcBonds.of(player).hasSubstance()
                || !QuestLog.of(player).quests().isEmpty()
                // 巨神をすでに止めた人 (記録が消えていても、序盤は終えている。章の移行: chapter_per_titan_plan 2.3)
                || jp.revati.skyabyss.progress.Progression.stage(player) > 0);
        return new Facts(legacy, Lore.hasBook(player), campBuilt, notebook, campDist, shelterDist, footNear, slabNear,
                OpeningBeats.metStoryteller(player), metIsana(player), lore.has("prologue/camp"), lore.has("prologue/footprints"),
                lore.has("prologue/shadow"), lore.has("prologue/bone_hill"), lore.has("prologue/the_book_margin"),
                lore.has("chapter1/stone_whale"), passEnded, sky.passCount(), sky.firstPassTick(), mainStage(player));
    }

    static boolean metIsana(ServerPlayer player) {
        NpcBonds.Bond bond = NpcBonds.of(player).bonds().get(NpcSetup.WHALE_CHASER_ID);
        return bond != null && (bond.talks() > 0 || bond.flags().contains("met") || bond.echoes().contains("met"));
    }

    // メインのクエストの今の段の ID。始まっていなければ空、終わっていれば "done"
    static String mainStage(ServerPlayer player) {
        QuestLog.Entry entry = QuestLog.of(player).entry(MAIN_QUEST);
        QuestDef def = QuestManager.quest(MAIN_QUEST);
        if (entry == null || def == null) {
            return "";
        }
        if (entry.done || entry.stage >= def.stages().size()) {
            return "done";
        }
        return def.stages().get(entry.stage).id();
    }

    // ---------------------------------------------------------------- 目的の帯
    private static void syncObjective(ServerLevel level, ServerPlayer player, OpeningState st, Facts facts, Env env, long now) {
        String id = OpeningLogic.objective(st, facts, env);
        // 目印は人ごと・今の目的ごとに決める (宿りの鐘と鯨追いの焚き火を取り違えない)。目印が変わったら送り直す
        BlockPos target = target(level, id);
        // 天と淵の大陸: はじまりの島にいるあいだ (渡し船に乗る前)、都の側を指す目的は船着き場を指す (帳面を拾う前の目的はそのまま)
        if (!id.isEmpty() && !id.equals("examine_camp") && !jp.revati.skyabyss.world.continent.ContinentOutskirts.ferried(player)
                && level.getChunkSource().getGenerator() instanceof jp.revati.skyabyss.world.continent.ContinentChunkGenerator) {
            // はじまりの島の谷の道 (足跡 → 峠の野盗 → 休み場 → 祠 → 渡し守。world/continent/IslandTrail)
            Object[] island = jp.revati.skyabyss.world.continent.IslandTrail.objective(player);
            BlockPos ferry = jp.revati.skyabyss.world.continent.ContinentOutskirts.ferryPos(level);
            if (island != null) {
                id = (String) island[0];
                target = (BlockPos) island[1];
            } else if (ferry != null) {
                id = "take_ferry";
                target = ferry;
            }
        }
        // 天と淵の大陸: 鯨追いは鯨見の町の見張り台のそば (00 の 278)
        if (id.equals("find_isana") && level.getChunkSource().getGenerator() instanceof jp.revati.skyabyss.world.continent.ContinentChunkGenerator) {
            id = "find_isana_town";
            BlockPos spot = jp.revati.skyabyss.world.continent.ContinentTowns.chaserSpot(level.getServer());
            target = spot != null ? spot : jp.revati.skyabyss.world.continent.ContinentLayout.get().town("whale");
        }
        long tl = target == null ? Long.MIN_VALUE : target.asLong();
        if (id.equals(st.sentObjective) && tl == st.sentTarget && now - st.sentAt < RESEND) {
            return;
        }
        st.sentObjective = id;
        st.sentTarget = tl;
        st.sentAt = now;
        PacketDistributor.sendToPlayer(player, ObjectivePayload.of(id, target));
    }

    // 目的ごとの目印 (場所のない目的は null)。野営地・宿りの鐘・鯨追いの焚き火・骨の丘は、それぞれ別の場所を指す
    @Nullable
    public static BlockPos target(ServerLevel level, String id) {
        MinecraftServer server = level.getServer();
        if (!OpeningSites.planned(server)) {
            return null;
        }
        return switch (id) {
            case "examine_camp" -> OpeningSites.notebook(server).or(() -> OpeningSites.camp(server)).orElse(null);
            case "follow_smoke" -> OpeningSites.smoke(server).or(() -> OpeningSites.shelter(server)).orElse(null);
            case "ask_storyteller" -> OpeningSites.bell(server).or(() -> OpeningSites.shelter(server)).orElse(null);
            // 鯨追いは宿りの鐘から 18〜34 ブロック離れた焚き火のそばにいる。鐘の位置ではなく、その焚き火 (まだ立っていなければ目印なし)
            case "find_isana" -> OpeningSites.bell(server).or(() -> OpeningSites.shelter(server))
                    .map(bell -> NpcSpawner.chaserSpot(server.overworld(), bell)).orElse(null);
            case "bone_hill" -> OpeningSites.ribs(server).orElse(null);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- 骨の丘の前触れ (17 の 3.5節)
    // 丘が急に目の前に現れないように、イサナのあとで骨の丘へ近づくにつれて字幕を一度ずつ: 風のにおい (方角) → 遠い稜線の白 → 足もとの欠片 (低い地鳴り) → 風が止む
    static final double BONE_FAR = 220;
    static final double BONE_MID = 130;
    static final double BONE_NEAR = 70;

    static void boneForeshadow(ServerLevel level, ServerPlayer player, OpeningState st, Env env) {
        if (st.legacy() || st.awakeAt() < 0 || !env.overworld() || !env.awake() || !st.done(OpeningBeat.ISANA) || st.done(OpeningBeat.BONES)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (!OpeningSites.planned(server) || !OpeningSites.built(server, Kind.RIBS)) {
            return;
        }
        Optional<BlockPos> ribs = OpeningSites.ribs(server);
        if (ribs.isEmpty()) {
            return;
        }
        double d = OpeningSites.horizontal(ribs.get(), player.blockPosition());
        int want = d <= BONE_NEAR ? 4 : d <= BONE_MID ? 3 : d <= BONE_FAR ? 2 : OpeningLogic.boneOffer(st, env) ? 1 : 0;
        if (want <= st.boneCue) {
            return;
        }
        st.boneCue = want;
        switch (want) {
            case 1 -> {
                float yaw = OpeningSites.yawTo(player.blockPosition(), ribs.get());
                // yaw 0 = 南 (message.skyabyss.guide.dir は北 = 0 の 8 方位。日本語に決め打ちしない: 英語の画面に「to the 北西」と出ていた)
                Notice.narrate(player, "opening.skyabyss.bone_rumor", Component.translatable("message.skyabyss.guide.dir." + Math.floorMod(4 + Math.round(yaw / 45f), 8)));
            }
            case 2 -> Notice.narrate(player, "opening.skyabyss.bone_far");
            case 3 -> {
                Notice.narrate(player, "opening.skyabyss.bone_mid");
                // 丘のほうから、ごく低い地鳴り (小さく、一度だけ)
                BlockPos at = ribs.get();
                player.connection.send(new ClientboundSoundPacket(OpeningSetup.RUMBLE, SoundSource.AMBIENT, at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                        0.5f, 0.8f, level.random.nextLong()));
            }
            default -> Notice.narrate(player, "opening.skyabyss.bone_near");
        }
    }

    // ---------------------------------------------------------------- 岩鯨 (17 の 3.9節)
    // 岩鯨を迎えられる人 (影・歌・イサナを済ませた人) がオーバーワールドにいるか
    public static boolean anyoneReady(ServerLevel level) {
        return readyPlayer(level) != null;
    }

    // 岩鯨を迎えられる人: 影・歌・イサナを済ませ、一章の支度も済ませた人 (34 の 4.2。仲間が済ませていてもよい)
    @Nullable
    public static ServerPlayer readyPlayer(ServerLevel level) {
        return pick(level, true);
    }

    // 支度だけを済ませた人 (5 日目の詰み止めのあとは、拍子を問わずこの人のもとへ)
    @Nullable
    public static ServerPlayer preparedPlayer(ServerLevel level) {
        return pick(level, false);
    }

    @Nullable
    private static ServerPlayer pick(ServerLevel level, boolean beats) {
        ServerPlayer any = null;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isAlive() && !p.isSpectator() && (!beats || OpeningLogic.whaleReady(state(p))) && jp.revati.skyabyss.story.ch1.Ch1Trails.callReady(p)) {
                // まだ岩鯨を見ていない人を先に (最初の一人の近くにだけ出て、遅れて来た人が見損ねないように)
                if (!Lore.state(p).has("chapter1/stone_whale")) {
                    return p;
                }
                if (any == null) {
                    any = p;
                }
            }
        }
        return any;
    }

    // 見る者を、最初の二晩は出さない (足音と歌を邪魔しないため。17 の 3.8節)
    public static boolean quietNight(ServerLevel level, ServerPlayer player) {
        OpeningState st = state(player);
        if (st.legacy() || st.awakeAt() < 0) {
            return false;
        }
        long now = level.getGameTime();
        long day = Math.floorDiv(level.getDayTime(), OpeningLogic.DAY);
        return st.cueUntil > now || day <= st.awakeDay() + 1;
    }

    // 語り部などと話している間、場面の間は、破れた頁を落とさない (読めず、見えもしない。画面を閉じたあとの見回りで落ちる)
    public static boolean pageBusy(ServerPlayer player) {
        return jp.revati.skyabyss.npc.DialogueService.currentNode(player) != null || jp.revati.skyabyss.cinematic.Cinematics.active(player);
    }

    // 書を持っている人の「空の影を見た」は、頁を読んで記す (LoreEvents から。17 の 判断 2 A)。持っていなければ今までどおり
    public static boolean deferShadowToPage(ServerPlayer player) {
        OpeningState st = state(player);
        return !st.legacy() && st.awakeAt() >= 0 && Lore.hasBook(player);
    }

    // ---------------------------------------------------------------- 章の小要素 (story/ch1) 向けの窓口 (追加のみ)
    // 目覚め・足音・歌の画面の演出の最中か。小さな異変などを重ねないために (「一度に一つ」。17 の 1.2)
    public static boolean cueBusy(ServerPlayer player) {
        OpeningState st = state(player);
        return st.cueUntil > player.level().getGameTime() || FREEZE.getOrDefault(player.getUUID(), -1L) > player.level().getGameTime();
    }

    // 小さな異変・予兆を起こしてよい静かな状態か: 起きていて、戦っておらず、体力が十分で、敵が近くになく、水中でなく、影の最中でも演出の最中でもない
    public static boolean quiet(ServerLevel level, ServerPlayer player) {
        return env(level, player).calm() && !cueBusy(player);
    }

    // ---------------------------------------------------------------- 足音の間、動物が固まる
    private static void freezeAnimals(ServerLevel level, ServerPlayer player) {
        for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(FREEZE_RADIUS))) {
            animal.getNavigation().stop();
            animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 6, false, false));
        }
    }

    // 目を覚ます: まぶたの開く合図と、開幕のカメラの場面 (36 秒。スキップできる)。最初の目的 (煙の立つ方) へ向きを合わせて、その向きで操作へ戻す。
    // docs/design/20_cinematics.md。開幕の語りのあと (StoryReels.onDone)
    static void wakeUp(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        state(player).cueUntil = level.getGameTime() + 300;
        PacketDistributor.sendToPlayer(player, new CuePayload(OpeningNetwork.CUE_AWAKE, level.getGameTime(), 0f));
        BlockPos goal = target(level, "follow_smoke");
        jp.revati.skyabyss.cinematic.Cinematics.start(player, jp.revati.skyabyss.cinematic.Scenes.OPENING, null,
                goal == null ? null : Vec3.atBottomCenterOf(goal));
    }

    static {
        jp.revati.skyabyss.story.reel.StoryReels.onDone(jp.revati.skyabyss.story.reel.StoryReels.OPENING, OpeningDirector::wakeUp);
    }

    // ---------------------------------------------------------------- 本物の働きかけ
    static final class Live implements Fx {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final OpeningState st;

        Live(ServerLevel level, ServerPlayer player, OpeningState st) {
            this.level = level;
            this.player = player;
            this.st = st;
        }

        @Override
        public void awake() {
            // 目を覚ます前に、開幕の語り (ドット絵の紙芝居) を流す。見終わる (飛ばす) と wakeUp が走る。流さない場合はすぐ wakeUp
            jp.revati.skyabyss.story.reel.StoryReels.play(player, jp.revati.skyabyss.story.reel.StoryReels.OPENING);
        }

        @Override
        public void giveNotebook() {
            // 帳面を拾う = 伝承の書を手に入れる (最初の頁「忘れないうちに書け」が記される)
            Lore.unlock(player, Lore.BOOK_ENTRY, true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9f, 0.7f);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
        }

        @Override
        public void campLore() {
            Lore.unlock(player, "prologue/camp", true);
        }

        @Override
        public void ringBell() {
            Optional<BlockPos> bell = OpeningSites.bell(level.getServer()).or(() -> OpeningSites.shelter(level.getServer()));
            if (bell.isEmpty()) {
                return;
            }
            // 風のない朝の小さな音 (近いほど大きい。聞こえる距離は sounds.json の減衰距離)
            BlockPos at = bell.get();
            player.connection.send(new ClientboundSoundPacket(OpeningSetup.BELL, SoundSource.AMBIENT, at.getX() + 0.5, at.getY() + 1.0,
                    at.getZ() + 0.5, 1.0f, 1.0f, level.random.nextLong()));
        }

        @Override
        public void footprintsLore() {
            Lore.unlock(player, "prologue/footprints", true);
        }

        @Override
        public void startFootsteps(long now) {
            float yaw = level.random.nextFloat() * 360f;
            PacketDistributor.sendToPlayer(player, new CuePayload(OpeningNetwork.CUE_FOOTSTEPS, now, yaw));
            // 近くの人 (仲間を含む) にも、同じ時刻・向きで音と揺れだけ聞かせる。その人の拍子は進めない
            for (net.minecraft.server.level.ServerPlayer other : Players.snapshot(level.players())) {
                if (other != player && !other.isSpectator() && other.distanceToSqr(player) <= OpeningNetwork.FOOTSTEPS_NEAR_RANGE * OpeningNetwork.FOOTSTEPS_NEAR_RANGE) {
                    PacketDistributor.sendToPlayer(other, new CuePayload(OpeningNetwork.CUE_FOOTSTEPS_NEAR, now, yaw));
                }
            }
            FREEZE.put(player.getUUID(), now + OpeningLogic.STEPS_TOTAL);
        }

        @Override
        public void footstepsLore() {
            Lore.unlock(player, "prologue/footsteps", true);
        }

        @Override
        public void startSong(long now) {
            PacketDistributor.sendToPlayer(player, new CuePayload(OpeningNetwork.CUE_SONG, now, 0f));
        }

        @Override
        public void songLore() {
            Lore.unlock(player, "prologue/sky_song", true);
        }

        @Override
        public void dropShadowPage() {
            if (player.level().dimension() != Level.OVERWORLD || player.isInWaterOrBubble() || player.isInLava() || player.isPassenger()) {
                // 落ちても拾えない所: 荷物の中へ
                giveShadowPage();
                return;
            }
            Vec3 look = player.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() < 1.0e-4) {
                look = new Vec3(0, 0, 1);
            }
            Vec3 at = player.position().add(look.normalize().scale(2.0)).add(0, 4.0, 0);
            BlockPos head = BlockPos.containing(at);
            while (head.getY() > player.getBlockY() + 1 && !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                head = head.below();
                at = new Vec3(at.x, head.getY() + 0.2, at.z);
            }
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, LorePageItem.of("shadow"));
            item.setDeltaMovement(0, 0, 0);
            item.setPickUpDelay(10);
            item.setUnlimitedLifetime();
            // 落ちてきた本人だけが拾える (マルチで取り合いにならない)
            item.setTarget(player.getUUID());
            item.getPersistentData().putUUID(PAGE_OWNER_TAG, player.getUUID());
            level.addFreshEntity(item);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.01);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9f, 0.9f);
            PAGES.put(player.getUUID(), item.getUUID());
            Notice.narrate(player, "message.skyabyss.beats.page_falls");
        }

        private void giveShadowPage() {
            ItemStack page = LorePageItem.of("shadow");
            if (!player.getInventory().add(page)) {
                player.drop(page, false);
            }
            Notice.narrate(player, "message.skyabyss.beats.page_appeared");
        }

        @Override
        public boolean pageBusy() {
            return OpeningDirector.pageBusy(player);
        }

        @Override
        public void readShadowPage() {
            // 読まれないまま長く経った: 地面の頁は消し、荷物の頁は読んだことにして記す (メインの段が止まらないように)
            discardDroppedPages(level.getServer(), player.getUUID());
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(LoreSetup.LORE_PAGE.get()) && LorePageItem.source(stack).equals("shadow")) {
                    stack.shrink(1);
                    break;
                }
            }
            Lore.unlock(player, "prologue/shadow", true);
        }

        @Override
        public void startPersonalShadow() {
            SkyEvents.startPersonal(level, player);
        }

        @Override
        public void bonesLore() {
            Lore.unlock(player, "prologue/bone_hill", true);
        }

        @Override
        public void marginPage() {
            if (Lore.unlock(player, "prologue/the_book_margin", true)) {
                Notice.narrate(player, "opening.skyabyss.margin_line");
            }
        }

        @Override
        public int random(int min, int max) {
            return min + level.random.nextInt(Math.max(1, max - min + 1));
        }
    }

    // ---------------------------------------------------------------- 開発用 (コマンド)
    public static String loreOf(OpeningBeat beat) {
        return switch (beat) {
            case CAMP -> "prologue/camp";
            case FOOTPRINTS -> "prologue/footprints";
            case SHADOW -> "prologue/shadow";
            case FOOTSTEPS -> "prologue/footsteps";
            case BONES -> "prologue/bone_hill";
            case SONG -> "prologue/sky_song";
            default -> "";
        };
    }

    // その拍子を今すぐ起こし、前の拍子は済んだことにする (OP のコマンド)
    public static void force(ServerPlayer player, OpeningBeat beat) {
        ServerLevel level = player.serverLevel();
        OpeningState st = state(player);
        long now = level.getGameTime();
        if (st.awakeAt < 0) {
            st.awakeAt = now;
            st.awakeDay = Math.floorDiv(level.getDayTime(), OpeningLogic.DAY);
        }
        st.legacy = false;
        for (OpeningBeat b : OpeningBeat.values()) {
            if (b.ordinal() < beat.ordinal()) {
                st.markDone(b);
            }
        }
        Live fx = new Live(level, player, st);
        switch (beat) {
            case AWAKE -> fx.awake();
            case CAMP -> {
                fx.giveNotebook();
                fx.campLore();
            }
            case TRAIL -> fx.ringBell();
            case FOOTPRINTS -> fx.footprintsLore();
            case SHADOW -> fx.startPersonalShadow();
            case FOOTSTEPS -> {
                st.stepsDoneNight = OpeningLogic.nightId(level.getDayTime());
                st.cueUntil = now + OpeningLogic.STEPS_TOTAL;
                st.stepsLoreAt = now + OpeningLogic.STEPS_LORE;
                fx.startFootsteps(now);
            }
            case BONES -> fx.bonesLore();
            case SONG -> {
                st.songDoneNight = OpeningLogic.nightId(level.getDayTime());
                st.cueUntil = now + OpeningLogic.SONG_TOTAL;
                st.songLoreAt = now + OpeningLogic.SONG_LORE;
                fx.startSong(now);
            }
            default -> {
            }
        }
        st.markDone(beat);
        if (beat == OpeningBeat.WHALE) {
            for (OpeningBeat b : OpeningBeat.values()) {
                st.markDone(b);
            }
        }
        st.sentObjective = null;
    }

    // その拍子とそれ以後を「まだ」に戻し、記した頁も消す (OP のコマンド)
    public static void rewind(ServerPlayer player, OpeningBeat beat) {
        OpeningState st = state(player);
        for (OpeningBeat b : OpeningBeat.values()) {
            if (b.ordinal() >= beat.ordinal()) {
                String lore = loreOf(b);
                if (!lore.isEmpty()) {
                    Lore.lock(player, lore);
                }
            }
        }
        st.rewindTo(beat);
        FREEZE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new CuePayload(OpeningNetwork.CUE_STOP, player.serverLevel().getGameTime(), 0f));
        st.sentObjective = null;
    }

    // 全部なかったことにする。次の見回りで B0 から (OP のコマンド)
    public static void reset(ServerPlayer player) {
        rewind(player, OpeningBeat.AWAKE);
        state(player).reset();
    }

    public static List<OpeningBeat> doneList(ServerPlayer player) {
        return List.copyOf(state(player).doneBeats());
    }
}

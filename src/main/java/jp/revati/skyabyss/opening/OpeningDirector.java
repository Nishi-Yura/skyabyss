package jp.revati.skyabyss.opening;

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
    // 落とした破れた頁 (プレイヤー → 頁のエンティティ)
    private static final Map<UUID, UUID> PAGES = new HashMap<>();

    private OpeningDirector() {
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

    // 一人の見回り (テストからも呼べる)
    public static void run(ServerLevel level, ServerPlayer player) {
        OpeningState st = state(player);
        long now = level.getGameTime();
        Env env = env(level, player);
        Facts facts = facts(level, player, st, env);
        Fx fx = new Live(level, player, st);
        OpeningLogic.step(st, env, facts, fx);
        if (st.awakeAt >= 0) {
            st.fresh = false;
        }
        syncObjective(level, player, st, facts, env, now);
    }

    static Env env(ServerLevel level, ServerPlayer player) {
        boolean sky = level.canSeeSky(player.blockPosition().above());
        boolean overworld = level.dimension() == Level.OVERWORLD;
        boolean awake = player.isAlive() && !player.isSleeping() && !player.isSpectator();
        boolean deep = !sky && player.getY() < level.getSeaLevel() - 24;
        boolean danger = danger(level, player);
        return new Env(level.getGameTime(), level.getDayTime(), overworld, awake, sky, deep, player.isUnderWater(), danger,
                Config.HORROR_EVENT_FREQUENCY.get() > 0, SkyEvents.activePass() != null, !level.isRaining() && !level.isThundering());
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
            for (BlockPos p : OpeningSites.footprints(server)) {
                if (OpeningSites.horizontal(p, pos) <= OpeningLogic.FOOTPRINT_NEAR) {
                    footNear = true;
                    break;
                }
            }
        }
        boolean slabNear = false;
        if (planned && OpeningSites.built(server, Kind.RIBS)) {
            Optional<BlockPos> slab = OpeningSites.slab(server);
            slabNear = slab.isPresent() && OpeningSites.horizontal(slab.get(), pos) <= OpeningLogic.SLAB_NEAR;
        }
        SkyEventData sky = SkyEventData.get(level);
        long now = env.now();
        boolean passEnded = lastPassEnd >= 0 && st.awakeAt >= 0 && lastPassEnd >= st.awakeAt && now >= lastPassEnd
                && now - lastPassEnd <= OpeningLogic.PASS_END_WINDOW;
        boolean legacy = !st.fresh && (!lore.unlocked().isEmpty() || lore.bookGiven() || !NpcBonds.of(player).bonds().isEmpty()
                || !QuestLog.of(player).quests().isEmpty());
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
        if (id.equals(st.sentObjective) && now - st.sentAt < RESEND) {
            return;
        }
        st.sentObjective = id;
        st.sentAt = now;
        PacketDistributor.sendToPlayer(player, ObjectivePayload.of(id, target(level.getServer(), id)));
    }

    // 目的ごとの目印 (場所のない目的は null)
    @Nullable
    public static BlockPos target(MinecraftServer server, String id) {
        if (!OpeningSites.planned(server)) {
            return null;
        }
        return switch (id) {
            case "examine_camp" -> OpeningSites.notebook(server).or(() -> OpeningSites.camp(server)).orElse(null);
            case "follow_smoke" -> OpeningSites.smoke(server).or(() -> OpeningSites.shelter(server)).orElse(null);
            case "ask_storyteller", "find_isana" -> OpeningSites.bell(server).or(() -> OpeningSites.shelter(server)).orElse(null);
            case "bone_hill" -> OpeningSites.ribs(server).orElse(null);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- 岩鯨 (17 の 3.9節)
    // 岩鯨を迎えられる人 (影・歌・イサナを済ませた人) がオーバーワールドにいるか
    public static boolean anyoneReady(ServerLevel level) {
        return readyPlayer(level) != null;
    }

    @Nullable
    public static ServerPlayer readyPlayer(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && OpeningLogic.whaleReady(state(p))) {
                return p;
            }
        }
        return null;
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

    // 書を持っている人の「空の影を見た」は、頁を読んで記す (LoreEvents から。17 の 判断 2 A)。持っていなければ今までどおり
    public static boolean deferShadowToPage(ServerPlayer player) {
        OpeningState st = state(player);
        return !st.legacy() && st.awakeAt() >= 0 && Lore.hasBook(player);
    }

    // ---------------------------------------------------------------- 足音の間、動物が固まる
    private static void freezeAnimals(ServerLevel level, ServerPlayer player) {
        for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(FREEZE_RADIUS))) {
            animal.getNavigation().stop();
            animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 6, false, false));
        }
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
            st.cueUntil = level.getGameTime() + 300;
            PacketDistributor.sendToPlayer(player, new CuePayload(OpeningNetwork.CUE_AWAKE, level.getGameTime(), 0f));
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
            PacketDistributor.sendToPlayer(player, new CuePayload(OpeningNetwork.CUE_FOOTSTEPS, now, level.random.nextFloat() * 360f));
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
        public void readShadowPage() {
            // 読まれないまま長く経った: 地面の頁は消し、荷物の頁は読んだことにして記す (メインの段が止まらないように)
            UUID id = PAGES.remove(player.getUUID());
            if (id != null) {
                var entity = level.getServer().overworld().getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
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
            SkyEvents.startFor(level, player);
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
    static String loreOf(OpeningBeat beat) {
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

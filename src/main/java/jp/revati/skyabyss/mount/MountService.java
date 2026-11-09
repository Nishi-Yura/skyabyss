package jp.revati.skyabyss.mount;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.titan.TitanBacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 乗り物の呼び出しと厩舎の売り買い (docs/design/38)。決めるのはすべてサーバー。
//   呼ぶ: 笛で目の前に呼ぶ。一人に一頭。背中・深淵・心臓の部屋では呼べない (入ると自動で帰す)
//   帰す: 笛をもう一度、持ち主が去る (ログアウト・別のディメンション)、遠く (96 ブロック) 離れる、倒れる
//   買う: 見た目は一度だけ (章が届いたもの)、性能は速さ・跳び・体力を 1 段ずつ。鐘銭で払う。買った見た目は今の見た目になり、出ている乗り物にもすぐ当たる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MountService {
    public static final double REACH = 8.0;
    public static final double FAR = 96.0;
    // 絆 I の駆け寄る速さ
    static final double RUN_TO = 1.8;
    // 一度に数える距離の上限 (乗ったまま運ばれた・場所を移された分を数えない)
    static final double RIDE_STEP_MAX = 25;
    // 乗っている人の、最後に数えた所
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    // 駆け寄っている乗り物 (持ち主 → 呼んだ時)。道が見つからなくても毎 tick 持ち主の方へ進め、RUN_MAX で着かなければそばへ移す
    private static final Map<UUID, Long> APPROACH = new HashMap<>();
    static final int RUN_MAX = 100;
    static final double RUN_STOP = 2.6;

    @SubscribeEvent
    static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (APPROACH.isEmpty()) {
            return;
        }
        for (var it = APPROACH.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            MountEntity m = p == null ? null : ACTIVE.get(e.getKey());
            if (p == null || m == null || m.isRemoved() || m.isVehicle() || m.level() != p.level()) {
                it.remove();
                continue;
            }
            long t = m.level().getGameTime() - e.getValue();
            double d = m.distanceTo(p);
            if (d <= RUN_STOP) {
                m.getNavigation().stop();
                it.remove();
                continue;
            }
            if (t >= RUN_MAX) {
                Vec3 at = spot(p.serverLevel(), p, m);
                m.teleportTo(at.x, at.y, at.z);
                m.getNavigation().stop();
                it.remove();
                continue;
            }
            // 道を引き直す (着かないうちに道が切れたとき) と、向きと進み
            if (t % 10 == 0 || m.getNavigation().isDone()) {
                m.getNavigation().moveTo(p, RUN_TO);
            }
            m.getMoveControl().setWantedPosition(p.getX(), p.getY(), p.getZ(), RUN_TO);
        }
    }

    public enum Result { OK, NO_COINS, LOCKED, OWNED, NOT_OWNED, MAXED, CLOSED }

    private static final Map<UUID, MountEntity> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> SESSIONS = new HashMap<>();
    // 自己テスト用: 章の上書き (0 = 上書きなし)
    private static int chapterOverride;

    private MountService() {
    }

    public static MountData data(ServerPlayer player) {
        return player.getData(MountSetup.DATA);
    }

    // 店の章: 四つの心臓までは止めた数 + 1 (Coins.chapter)。そのあとは物語の章 (五章 = 6・六章 = 7・終章を越えた = 8。MountLook.chapter)
    public static int chapter(ServerPlayer player) {
        if (chapterOverride > 0) {
            return chapterOverride;
        }
        int c = Coins.chapter(player);
        if (c < 5 || player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return c;
        }
        if (jp.revati.skyabyss.story.finale.FinaleEnding.chosen(player) != null) {
            return 8;
        }
        jp.revati.skyabyss.story.Chapters ch = jp.revati.skyabyss.story.Chapters.of(player);
        return ch.ordinal() >= jp.revati.skyabyss.story.Chapters.MOON.ordinal() ? 7 : ch == jp.revati.skyabyss.story.Chapters.ABYSS ? 6 : 5;
    }

    static void setChapterOverride(int c) {
        chapterOverride = c;
    }

    // ---------------------------------------------------------------- 呼ぶ・帰す

    // 呼べない所なら、その理由の翻訳キー。呼べるなら null
    @Nullable
    public static String refusal(ServerPlayer player) {
        if (TitanBacks.isBack(player.level().dimension())) {
            return "message.skyabyss.mount.refuse_back";
        }
        if (player.level().dimension() == AbyssWorld.LEVEL) {
            return "message.skyabyss.mount.refuse_abyss";
        }
        if (player.isPassenger() && !(player.getVehicle() instanceof MountEntity)) {
            return "message.skyabyss.mount.refuse_riding";
        }
        return null;
    }

    @Nullable
    public static MountEntity active(ServerPlayer player) {
        MountEntity m = ACTIVE.get(player.getUUID());
        if (m != null && (m.isRemoved() || !m.isAlive())) {
            ACTIVE.remove(player.getUUID());
            return null;
        }
        return m;
    }

    // 笛: 出ていれば帰し、いなければ呼ぶ
    public static void toggle(ServerPlayer player) {
        if (active(player) != null) {
            dismiss(player, true);
        } else {
            summon(player);
        }
    }

    @Nullable
    public static MountEntity summon(ServerPlayer player) {
        String no = refusal(player);
        if (no != null) {
            Notice.hint(player, no);
            return null;
        }
        dismiss(player, false);
        ServerLevel level = player.serverLevel();
        MountEntity m = MountSetup.MOUNT.get().create(level);
        if (m == null) {
            return null;
        }
        m.setOwner(player);
        MountData data = data(player);
        m.apply(data, true);
        m.setCustomName(displayName(data, m.look()));
        // 絆 I から: 少し離れた所に現れ、駆け寄ってくる
        Vec3 far = data.bond() >= 1 ? farSpot(level, player, m) : null;
        Vec3 at = far != null ? far : spot(level, player, m);
        m.moveTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
        if (!level.addFreshEntity(m) && far != null) {
            // 離れた所に出せなかった (区画の端など): いつもどおり目の前に
            far = null;
            at = spot(level, player, m);
            m.moveTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
            level.addFreshEntity(m);
        }
        SkyAbyss.LOGGER.debug("[mount] {} summoned {} at {} (far {})", player.getGameProfile().getName(), m.look().id, at, far != null);
        ACTIVE.put(player.getUUID(), m);
        LAST.remove(player.getUUID());
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_FLUTE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.2f);
        if (far != null) {
            APPROACH.put(player.getUUID(), level.getGameTime());
            m.getNavigation().moveTo(player, RUN_TO);
            Notice.hint(player, "message.skyabyss.mount.runs_to", displayName(data, m.look()));
        } else {
            Notice.hint(player, "message.skyabyss.mount.called", displayName(data, m.look()));
        }
        return m;
    }

    // 呼んだ乗り物の名前 (付けた名前があればそれ)
    public static net.minecraft.network.chat.Component displayName(MountData data, MountLook look) {
        return data.name().isEmpty() ? net.minecraft.network.chat.Component.translatable(look.nameKey()) : net.minecraft.network.chat.Component.literal(data.name());
    }

    // 絆 I: 後ろ 9〜13 の、入れる所 (なければ null = いつもどおり目の前)
    @Nullable
    private static Vec3 farSpot(ServerLevel level, ServerPlayer player, MountEntity m) {
        Vec3 back = Vec3.directionFromRotation(0, player.getYRot() + 180f).normalize();
        for (double d : new double[] {11, 9, 13}) {
            for (double side : new double[] {0, 3, -3}) {
                Vec3 p = player.position().add(back.scale(d)).add(back.yRot((float) (Math.PI / 2)).scale(side));
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
                Vec3 q = new Vec3(p.x, y, p.z);
                if (Math.abs(y - player.getY()) <= 3 && level.noCollision(m, m.getDimensions(m.getPose()).makeBoundingBox(q))) {
                    return q;
                }
            }
        }
        return null;
    }

    // 目の前 2 ブロックから、近い順に、入れる所を探す
    private static Vec3 spot(ServerLevel level, ServerPlayer player, MountEntity m) {
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        for (double d : new double[] {2.5, 3.5, 1.8, 4.5}) {
            for (int dy = 0; dy <= 2; dy++) {
                Vec3 p = player.position().add(look.scale(d)).add(0, dy, 0);
                if (level.noCollision(m, m.getDimensions(m.getPose()).makeBoundingBox(p))) {
                    return p;
                }
            }
        }
        return player.position().add(0, 0.2, 0);
    }

    public static void dismiss(ServerPlayer player, boolean notify) {
        MountEntity m = ACTIVE.remove(player.getUUID());
        if (m != null && !m.isRemoved()) {
            m.ejectPassengers();
            m.discard();
            if (notify) {
                Notice.hint(player, "message.skyabyss.mount.dismissed");
            }
        }
    }

    // 性能や見た目を買ったあと、出ている乗り物に当てる
    static void refresh(ServerPlayer player) {
        MountEntity m = active(player);
        if (m != null) {
            m.apply(data(player), false);
        }
    }

    // ---------------------------------------------------------------- 買う・選ぶ

    public static Result buyLook(ServerPlayer player, MountLook look) {
        MountData d = data(player);
        if (d.owns(look)) {
            Notice.hint(player, "message.skyabyss.mount.owned");
            return Result.OWNED;
        }
        if (chapter(player) < look.chapter) {
            Notice.hint(player, look.lockedKey("message.skyabyss.mount.locked"), look.chapter - 1);
            return Result.LOCKED;
        }
        if (!Coins.take(player, look.price)) {
            Notice.hint(player, "message.skyabyss.mount.no_coins");
            return Result.NO_COINS;
        }
        d.own(look);
        d.choose(look);
        refresh(player);
        Notice.hint(player, "message.skyabyss.mount.bought_look", net.minecraft.network.chat.Component.translatable(look.nameKey()));
        return Result.OK;
    }

    public static Result chooseLook(ServerPlayer player, MountLook look) {
        MountData d = data(player);
        if (!d.owns(look)) {
            return Result.NOT_OWNED;
        }
        d.choose(look);
        refresh(player);
        return Result.OK;
    }

    public static Result buyTier(ServerPlayer player, int row) {
        // row = 店の行 (MountStats.KEYS の順)
        if (row < 0 || row >= MountStats.KEYS.length) {
            return Result.CLOSED;
        }
        int kind = MountStats.ROW_KIND[row];
        MountData d = data(player);
        int next = d.tier(kind) + 1;
        if (next > MountStats.tiers(kind)) {
            Notice.hint(player, "message.skyabyss.mount.maxed");
            return Result.MAXED;
        }
        if (!Coins.take(player, MountStats.price(kind, next))) {
            Notice.hint(player, "message.skyabyss.mount.no_coins");
            return Result.NO_COINS;
        }
        d.setTier(kind, next);
        refresh(player);
        Notice.hint(player, "message.skyabyss.mount.bought_tier", net.minecraft.network.chat.Component.translatable("mount.skyabyss.stat." + MountStats.KEYS[row]), next);
        return Result.OK;
    }

    // 笛を持っていなければ渡す (厩舎の番がくれる。なくしてももらい直せる)
    public static boolean giveWhistle(ServerPlayer player) {
        if (hasWhistle(player)) {
            Notice.hint(player, "message.skyabyss.mount.has_whistle");
            return false;
        }
        ItemStack stack = new ItemStack(MountSetup.WHISTLE.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        Notice.hint(player, "message.skyabyss.mount.whistle_given");
        return true;
    }

    public static boolean hasWhistle(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(MountSetup.WHISTLE.get())) {
                return true;
            }
        }
        // 旅袋に入っていても持っている (Y で呼べる)
        return jp.revati.skyabyss.pouch.Pouch.count(player, MountSetup.WHISTLE.get()) > 0;
    }

    // ---------------------------------------------------------------- 厩舎の画面

    public static boolean open(ServerPlayer player, @Nullable Entity npc) {
        SESSIONS.put(player.getUUID(), npc == null ? -1 : npc.getId());
        send(player, MountNetwork.view(player));
        return true;
    }

    public static boolean isOpen(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    static void closed(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    static void act(ServerPlayer player, int action, int arg) {
        if (!isOpen(player)) {
            return;
        }
        switch (action) {
            case MountNetwork.BUY_LOOK -> buyLook(player, MountLook.byOrdinal(arg));
            case MountNetwork.CHOOSE_LOOK -> chooseLook(player, MountLook.byOrdinal(arg));
            case MountNetwork.BUY_TIER -> buyTier(player, arg);
            case MountNetwork.WHISTLE -> giveWhistle(player);
            default -> {
            }
        }
        send(player, MountNetwork.view(player));
    }

    static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    // ---------------------------------------------------------------- 見張り

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        Integer npcId = SESSIONS.get(player.getUUID());
        if (npcId != null && npcId >= 0) {
            Entity npc = player.level().getEntity(npcId);
            if (!player.isAlive() || npc == null || npc.distanceToSqr(player) > REACH * REACH) {
                SESSIONS.remove(player.getUUID());
                send(player, new MountNetwork.Close());
            }
        }
        watch(player);
    }

    // 出ている乗り物の見張り: 持ち主が別のディメンションへ行った・遠く離れた・呼べない所に入ったら帰す
    static void watch(ServerPlayer player) {
        MountEntity m = active(player);
        if (m == null) {
            LAST.remove(player.getUUID());
            return;
        }
        if (player.getVehicle() == m) {
            ride(player, m);
        } else {
            LAST.remove(player.getUUID());
        }
        if (m.level() != player.level() || m.distanceToSqr(player) > FAR * FAR) {
            dismiss(player, false);
        } else if (refusal(player) != null) {
            dismiss(player, true);
        }
    }

    // 乗っている距離を数える (10 tick ごと)。絆の段が上がったら知らせ、出ている乗り物に当てる
    static void ride(ServerPlayer player, MountEntity m) {
        Vec3 now = player.position();
        Vec3 last = LAST.put(player.getUUID(), now);
        if (last == null) {
            return;
        }
        double d = Math.hypot(now.x - last.x, now.z - last.z);
        if (d <= 0 || d > RIDE_STEP_MAX) {
            return;
        }
        addRide(player, d);
    }

    public static void addRide(ServerPlayer player, double blocks) {
        MountData data = data(player);
        int before = data.bond();
        data.ride(blocks);
        int after = data.bond();
        if (after > before) {
            MountEntity m = active(player);
            if (m != null) {
                m.apply(data, false);
            }
            if (!(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                Notice.unlock(player, "message.skyabyss.mount.bond." + after, displayName(data, data.chosen()));
                player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_FLUTE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.4f);
            }
        }
    }

    // 名前を付ける (絆 II から。16 文字まで、色の記号は外す)。付けたら true
    public static boolean rename(ServerPlayer player, String raw) {
        MountData data = data(player);
        if (data.bond() < 2) {
            Notice.hint(player, "message.skyabyss.mount.name_locked");
            return false;
        }
        String n = raw == null ? "" : net.minecraft.ChatFormatting.stripFormatting(raw).replace("\n", "").trim();
        if (n.length() > MountData.NAME_MAX) {
            n = n.substring(0, MountData.NAME_MAX);
        }
        data.setName(n);
        MountEntity m = active(player);
        if (m != null) {
            m.setCustomName(displayName(data, m.look()));
        }
        Notice.gain(player, n.isEmpty() ? "message.skyabyss.mount.name_cleared" : "message.skyabyss.mount.named", n);
        if (isOpen(player)) {
            send(player, MountNetwork.view(player));
        }
        return true;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dismiss(player, false);
            SESSIONS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        SESSIONS.clear();
    }

    // 開発用 (OP): 前に全部の見た目を一列に並べる (撮影)。持ち主なし、2 秒で自然に消えない
    public static int demo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MountLook[] all = MountLook.values();
        Vec3 fwd = Vec3.directionFromRotation(0, player.getYRot()).normalize();
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        for (int i = 0; i < all.length; i++) {
            MountEntity m = MountSetup.MOUNT.get().create(level);
            if (m == null) {
                continue;
            }
            m.setLook(all[i]);
            Vec3 at = player.position().add(fwd.scale(9.0)).add(side.scale((i - (all.length - 1) / 2.0) * 3.4));
            m.moveTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
            m.setPersistenceRequired();
            level.addFreshEntity(m);
        }
        return all.length;
    }
}

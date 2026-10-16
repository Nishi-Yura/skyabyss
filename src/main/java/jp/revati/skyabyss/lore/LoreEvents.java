package jp.revati.skyabyss.lore;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 伝承が記されるきっかけを、既存の出来事から拾う。ほかの担当のコード (岩鯨・試練・空の影) には手を入れず、
// イベントと公開されている値 (碑文の位置、進行中の空の影) だけを見る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LoreEvents {
    // 空の影を見た人に、書が落ちてくるまでの間 (影が去ってから 3 秒)
    private static final long DROP_DELAY = 60;
    // 落ちてきた書を拾わないまま 2 分たったら、荷物の中へ移す
    private static final long DROP_TIMEOUT = 2400;
    // 碑文を「読んだ」とみなす距離と、目を向けている度合い (視線と碑文への向きの内積)
    private static final double SIGN_READ_DISTANCE = 4.5;
    private static final double SIGN_READ_FACING = 0.8;
    // 空の影を「見た」とみなす、影の中心からの水平距離
    private static final double SHADOW_WITNESS_DISTANCE = 150;
    // 岩鯨の背の番兵が破れた頁を落とす確率 (倒した人だけが拾える)
    private static final float GUARD_PAGE_CHANCE = 0.05f;
    // 村の宝箱に破れた頁が入る確率
    private static final float VILLAGE_PAGE_CHANCE = 0.25f;

    // 落とした書 (プレイヤー → 書のエンティティと落とした時刻)。再起動で消えてもよい (そのときは Lore.ensureBook が拾う)
    private record Drop(UUID item, long tick) {
    }

    private static final Map<UUID, Drop> DROPS = new HashMap<>();

    private LoreEvents() {
    }

    // ---------------------------------------------------------------- 同期
    // 参加したとき (参加者一人に対しても呼ばれる) と /reload のあと、章と項目と記録を送る
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(Lore::sync);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        DROPS.clear();
    }

    static void forgetDrop(ServerPlayer player) {
        DROPS.remove(player.getUUID());
    }

    static boolean hasPendingDrop(ServerPlayer player) {
        return DROPS.containsKey(player.getUUID());
    }

    // ---------------------------------------------------------------- 碑文
    // 看板を右クリックしたら、書いてある文のキーで「碑文を読んだ」とする (蝋引きの看板でも届く)
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getBlockEntity(event.getPos()) instanceof SignBlockEntity sign) {
            String key = signKey(sign.getFrontText().getMessage(0, false));
            if (key == null) {
                key = signKey(sign.getBackText().getMessage(0, false));
            }
            if (key != null) {
                String read = key;
                Lore.fire(player, "sign", t -> t.target().equals(read));
            }
        }
    }

    // 看板の 1 行目が "<キー>.1" の翻訳文なら、そのキー (末尾の .1 を除く)
    @Nullable
    static String signKey(Component line) {
        if (line.getContents() instanceof TranslatableContents tc && tc.getKey().endsWith(".1")) {
            return tc.getKey().substring(0, tc.getKey().length() - 2);
        }
        return null;
    }

    // 岩鯨の背の碑文は、近くで目を向けるだけでも読める (光る字は遠くからでも見えるので、右クリックを知らない人のため)
    private static void readNearbyInscriptions(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        java.util.List<java.util.Map.Entry<String, net.minecraft.core.BlockPos>> signs = new java.util.ArrayList<>();
        if (player.level().dimension() == TitanTravel.TITAN_BACK) {
            TitanBackLayout.LORE_SIGNS.forEach(i -> signs.add(java.util.Map.entry(i.key(), i.pos())));
        } else {
            jp.revati.skyabyss.titan.tide.back.TideBackLayout.LORE_SIGNS.forEach(i -> signs.add(java.util.Map.entry(i.key(), i.pos())));
        }
        for (java.util.Map.Entry<String, net.minecraft.core.BlockPos> inscription : signs) {
            Vec3 at = Vec3.atCenterOf(inscription.getValue());
            Vec3 to = at.subtract(eye);
            double distance = to.length();
            if (distance > SIGN_READ_DISTANCE || distance < 1.0e-3) {
                continue;
            }
            if (to.scale(1.0 / distance).dot(look) < SIGN_READ_FACING) {
                continue;
            }
            // 壁の向こうの碑文は読めない (看板には当たり判定がないので、看板の中心まで何にも当たらなければ見えている)
            var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, at,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(inscription.getValue())) {
                Lore.fire(player, "sign", t -> t.target().equals(inscription.getKey()));
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick の見回り (人ごとに間引く)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        int phase = player.getId() & 0xFF;
        LoreState state = Lore.state(player);
        if ((now + phase) % 5 == 0 && (level.dimension() == TitanTravel.TITAN_BACK || level.dimension() == jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK)
                && anyLocked(state, "sign")) {
            readNearbyInscriptions(player);
        }
        if ((now + phase) % 10 == 0 && level.dimension() == Level.OVERWORLD) {
            watchSky(player, level, state, now);
        }
        if ((now + phase) % 20 == 0) {
            checkItems(player, state);
            String dim = level.dimension().location().toString();
            Lore.fire(player, "dimension", t -> t.target().equals(dim));
            tickBookDrop(player, level, state, now);
        }
        if ((now + phase) % 40 == 0 && anyLocked(state, "near_entity")) {
            checkNearEntities(player, level);
        }
    }

    // 空の影: 空の見える所で、影の中心から一定の距離にいれば「見た」。
    // 初めて見た人には、影が去ったあと書が落ちてくるよう予定を入れる (記すより先に入れ、Lore.ensureBook が先に渡さないように)
    private static void watchSky(ServerPlayer player, ServerLevel level, LoreState state, long now) {
        SkyPass pass = SkyEvents.activePass();
        if (pass == null || !pass.isActive(now) || !anyLocked(state, "sky_shadow")) {
            return;
        }
        double age = pass.age(now, 0f);
        if (pass.storm(age) < 0.25f) {
            return;
        }
        double dx = player.getX() - pass.centerX(age);
        double dz = player.getZ() - pass.centerZ(age);
        if (dx * dx + dz * dz > SHADOW_WITNESS_DISTANCE * SHADOW_WITNESS_DISTANCE || !level.canSeeSky(player.blockPosition().above())) {
            return;
        }
        if (!state.bookGiven && state.bookDropAt < 0) {
            state.bookDropAt = pass.startTick() + pass.duration() + DROP_DELAY;
        }
        Lore.fire(player, "sky_shadow", t -> true);
    }

    static void checkItems(ServerPlayer player, LoreState state) {
        for (LoreEntry entry : LoreManager.catalog().entries()) {
            if (!entry.unlockable() || state.has(entry.id())) {
                continue;
            }
            for (LoreTrigger t : entry.unlock()) {
                if (t.type().equals("item")) {
                    Item item = item(t.target());
                    if (item != null && (Lore.contains(player.getInventory().items, item) || Lore.contains(player.getInventory().offhand, item))) {
                        Lore.unlock(player, entry.id(), true);
                        break;
                    }
                }
            }
        }
    }

    private static void checkNearEntities(ServerPlayer player, ServerLevel level) {
        Set<String> targets = new LinkedHashSet<>();
        LoreState state = Lore.state(player);
        double radius = 0;
        for (LoreEntry entry : LoreManager.catalog().entries()) {
            if (!entry.unlockable() || state.has(entry.id())) {
                continue;
            }
            for (LoreTrigger t : entry.unlock()) {
                if (t.type().equals("near_entity")) {
                    targets.add(t.target());
                    radius = Math.max(radius, t.radius());
                }
            }
        }
        for (String target : targets) {
            EntityType<?> type = entityType(target);
            if (type == null) {
                continue;
            }
            for (Entity entity : level.getEntities(type, player.getBoundingBox().inflate(radius), Entity::isAlive)) {
                double d = entity.distanceTo(player);
                Lore.fire(player, "near_entity", t -> t.target().equals(target) && d <= t.radius());
            }
        }
    }

    // ---------------------------------------------------------------- 書が空から落ちてくる
    private static void tickBookDrop(ServerPlayer player, ServerLevel level, LoreState state, long now) {
        Drop drop = DROPS.get(player.getUUID());
        if (drop != null) {
            if (state.has(Lore.BOOK_ENTRY)) {
                DROPS.remove(player.getUUID());
            } else if (now - drop.tick() > DROP_TIMEOUT) {
                // 拾われないまま時間がたった: 落ちた書は消し、荷物の中へ
                DROPS.remove(player.getUUID());
                Entity item = level.getServer().overworld().getEntity(drop.item());
                if (item != null) {
                    item.discard();
                }
                Lore.giveBook(player, "message.skyabyss.lore.book_appeared");
            }
            return;
        }
        if (state.bookDropAt < 0 || now < state.bookDropAt) {
            return;
        }
        if (state.bookGiven) {
            state.bookDropAt = -1;
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            // 影が去るまでに地上を離れていた: 荷物の中へ
            Lore.giveBook(player, "message.skyabyss.lore.book_appeared");
            return;
        }
        dropBook(player, level, now);
    }

    static void dropBook(ServerPlayer player, ServerLevel level, long now) {
        LoreState state = Lore.state(player);
        state.bookGiven = true;
        state.bookDropAt = -1;
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0e-4) {
            look = new Vec3(0, 0, 1);
        }
        Vec3 at = player.position().add(look.normalize().scale(2.0)).add(0, 4.0, 0);
        // 天井があれば、その下に出す
        BlockPos head = BlockPos.containing(at);
        while (head.getY() > player.getBlockY() + 1 && !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
            head = head.below();
            at = new Vec3(at.x, head.getY() + 0.2, at.z);
        }
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new net.minecraft.world.item.ItemStack(LoreSetup.LORE_BOOK.get()));
        item.setDeltaMovement(0, 0, 0);
        item.setPickUpDelay(10);
        item.setUnlimitedLifetime();
        // 落ちてきた本人だけが拾える (マルチで取り合いにならない)
        item.setTarget(player.getUUID());
        level.addFreshEntity(item);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.6f);
        player.sendSystemMessage(ModText.lore("message.skyabyss.lore.book_falls"));
        DROPS.put(player.getUUID(), new Drop(item.getUUID(), now));
    }

    // ---------------------------------------------------------------- 倒したとき
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        creditKill(level, dead, killer);
        // 岩鯨の背の番兵は、まれに破れた頁を落とす (倒した人だけが拾える)
        if (killer != null && dead instanceof GuardMob && level.dimension() == TitanTravel.TITAN_BACK
                && level.random.nextFloat() < GUARD_PAGE_CHANCE && !LorePageItem.candidates(killer, "titan_back").isEmpty()) {
            ItemEntity page = new ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), LorePageItem.of("titan_back"));
            page.setTarget(killer.getUUID());
            page.setDefaultPickUpDelay();
            level.addFreshEntity(page);
        }
    }

    static void creditKill(ServerLevel level, LivingEntity dead, @Nullable ServerPlayer killer) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        String id = key.toString();
        double radius = -1;
        for (LoreEntry entry : LoreManager.catalog().entries()) {
            for (LoreTrigger t : entry.unlock()) {
                if (t.type().equals("kill") && t.target().equals(id)) {
                    radius = Math.max(radius, t.radius());
                }
            }
        }
        if (radius < 0) {
            return;
        }
        // 倒れて復活を待っている人も、その場にいたなら数える (観戦者は除く)
        Set<ServerPlayer> witnesses = new LinkedHashSet<>(level.getEntitiesOfClass(ServerPlayer.class,
                dead.getBoundingBox().inflate(radius), p -> !p.isSpectator()));
        if (killer != null && killer.level() == level) {
            witnesses.add(killer);
        }
        for (ServerPlayer player : witnesses) {
            double d = player.distanceTo(dead);
            boolean isKiller = player == killer;
            Lore.fire(player, "kill", t -> t.target().equals(id) && (isKiller || d <= t.radius()));
        }
    }

    // ---------------------------------------------------------------- 村の宝箱
    // 村の家や鍛冶屋の宝箱に、ときどき破れた頁が入っている (序章の村の言い伝え)
    @SubscribeEvent
    static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!name.getNamespace().equals("minecraft") || !name.getPath().startsWith("chests/village/")) {
            return;
        }
        int page = Math.round(VILLAGE_PAGE_CHANCE * 100);
        event.getTable().addPool(LootPool.lootPool()
                .name("skyabyss_lore_page")
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(LoreSetup.LORE_PAGE.get())
                        .apply(SetComponentsFunction.setComponent(LoreSetup.PAGE_SOURCE.get(), "village"))
                        .setWeight(page))
                .add(EmptyLootItem.emptyItem().setWeight(100 - page))
                .build());
    }

    // ---------------------------------------------------------------- 道具
    // まだ記されていない項目に、その種類の条件が一つでもあるか (見回りを省くため)
    static boolean anyLocked(LoreState state, String type) {
        for (LoreEntry entry : LoreManager.catalog().entries()) {
            if (entry.unlockable() && !state.has(entry.id())) {
                for (LoreTrigger t : entry.unlock()) {
                    if (t.type().equals(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    @Nullable
    static EntityType<?> entityType(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
    }
}

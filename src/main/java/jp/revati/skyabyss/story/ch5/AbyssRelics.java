package jp.revati.skyabyss.story.ch5;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.mobs.AbyssMob;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 前の私の落とし物 (docs/design/36 の 9 章・決定 257)。深淵の墓 12 (層ごと 3) の遺品。
//   墓を弔うと、その墓の遺品を一つ渡す (一度だけ。前に弔った人も、次にその墓を弔ったとき受け取る)
//   遺品は持ち物にあるあいだ効く。種類の数で効果が増える (表は docs/design/36 の 9 章)
//   十二の墓をすべて弔うと、伝承の頁 ch5/graves_all が記される (六章の手がかり)
//   墓の印と文は AbyssFinds・tools/gen/ch5/gen_ch5_entrance.py。遺品の絵は tools/abyss/gen_abyss_relic_art.py、文は tools/abyss/gen_abyss_relic_texts.py
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssRelics {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 十二の墓をすべて弔ったときの伝承 (data/skyabyss/lore/entry/ch5/graves_all.json)
    public static final String LORE_ALL = "ch5/graves_all";
    private static final String GRAVE = "grave_";
    private static final String GOT = "relic_grave_";

    // 種類の数で効く効果。needed 種類そろうと効く (それより少ないと効かない)
    public enum Bonus {
        // 夜目: 灯りの届かない所も見える (夜目の効果)
        SIGHT(2),
        // 足音の警告: 近くの深淵の敵が来ると、足もとで鈴が鳴る
        FOOTSTEPS(4),
        // 軽い足: 跳ぶと少し高く跳ぶ (跳躍の効果)
        LIGHT_STEP(6);

        public final int needed;

        Bonus(int needed) {
            this.needed = needed;
        }
    }

    // 遺品の表。層と墓の番号から一つ決まる
    public record Relic(String layer, int n, DeferredItem<Item> item) {
        public String id() {
            return layer + "_" + n;
        }
    }

    // 遺品の品 (12)。持ち物に一つずつ、重ねない
    public static final List<Relic> RELICS = buildRelics();

    private AbyssRelics() {
    }

    private static List<Relic> buildRelics() {
        List<Relic> list = new ArrayList<>();
        for (String layer : AbyssFinds.LAYERS) {
            for (int n = 1; n <= AbyssFinds.GRAVES; n++) {
                String id = layer + "_" + n;
                DeferredItem<Item> item = ITEMS.register("abyss_relic_" + id,
                        () -> new AbyssRelicItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "item.skyabyss.abyss_relic_" + id + ".desc"));
                list.add(new Relic(layer, n, item));
            }
        }
        return List.copyOf(list);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(AbyssRelics::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (Relic r : RELICS) {
                event.accept(r.item().get());
            }
        }
    }

    // 墓の番号から遺品 (層か番号が合わなければ null)
    public static Relic relicOf(String layer, int n) {
        for (Relic r : RELICS) {
            if (r.layer().equals(layer) && r.n() == n) {
                return r;
            }
        }
        return null;
    }

    // 墓を弔った (AbyssFinds.find から、墓のときに一度呼ぶ)。遺品を一度だけ渡し、十二の墓がそろえば伝承を記す
    public static void onGrave(ServerPlayer p, String layer, int n) {
        CompoundTag f = AbyssFinds.finds(p);
        Relic r = relicOf(layer, n);
        if (r != null && !f.getBoolean(GOT + r.id())) {
            f.putBoolean(GOT + r.id(), true);
            ItemStack s = new ItemStack(r.item().get());
            if (!p.getInventory().add(s)) {
                p.drop(s, false);
            }
            Notice.gain(p, "message.skyabyss.abyss.relic.got", s.getHoverName());
        }
        if (allMourned(f, layer, n) && Lore.unlock(p, LORE_ALL, true)) {
            Notice.narrate(p, "message.skyabyss.abyss.relic.all");
        }
    }

    // 十二の墓がそろったか。今弔った墓 (layer, n) は、まだ印が付く前でもそろった数に入れる
    private static boolean allMourned(CompoundTag f, String layer, int n) {
        for (String l : AbyssFinds.LAYERS) {
            for (int i = 1; i <= AbyssFinds.GRAVES; i++) {
                boolean now = l.equals(layer) && i == n;
                if (!now && !f.getBoolean(GRAVE + l + "_" + i)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 持ち物にある遺品の種類の数 (同じ品は一つと数える)
    public static int setCount(Player p) {
        Set<Item> held = new HashSet<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            Item item = inv.getItem(i).getItem();
            if (item instanceof AbyssRelicItem) {
                held.add(item);
            }
        }
        return held.size();
    }

    // その効果が効いているか
    public static boolean bonus(Player p, Bonus b) {
        return setCount(p) >= b.needed;
    }

    // 声がたまる速さの倍率 (AbyssPressure が掛ける)。夜目 (二つ) のあいだは遅くなる。効いていなければ 1
    public static double voiceFactor(Player p) {
        return bonus(p, Bonus.SIGHT) ? 0.75 : 1.0;
    }

    // 深淵の中で、効果の数に応じて効かせる (20 tick ごと。効果は 260 tick 持つので切れ目は出ない)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p) || p.tickCount % 20 != 7) {
            return;
        }
        if (bonus(p, Bonus.SIGHT)) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
        }
        if (bonus(p, Bonus.LIGHT_STEP)) {
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 260, 0, true, false, false));
        }
        if (bonus(p, Bonus.FOOTSTEPS) && !p.serverLevel().getEntitiesOfClass(AbyssMob.class, p.getBoundingBox().inflate(10)).isEmpty()) {
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }
}

package jp.revati.skyabyss.facility.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

// 取引所の記録 (docs/design/35 の 6.11。オーバーワールドに保存)。
//   出品 (Listing): 出す人の品は出したときに取引所が預かる (give)。ほしいもの (want) は品の型 (数入り)。鐘銭も品 (bell_coin) として want に入る
//   申請 (Application): 申し出る品 (offer。数入りの型)。申請しても品は動かない (受けた瞬間に、両方の手もとから入れ替える)
//   受け取り箱 (mail): 取り下げ・期限切れで戻る品、いない間に届いた品。取引所の画面の「受け取り」から持ち物へ
//   知らせ (notes): いない人への知らせ (文の鍵と引数)。次に入ったときに出す
public final class TradeData extends SavedData {
    private static final String NAME = "skyabyss_trade";
    private static final Factory<TradeData> FACTORY = new Factory<>(TradeData::new, TradeData::load);

    public static final class Application {
        public final UUID applicant;
        public String name;
        public final List<ItemStack> offer = new ArrayList<>();
        public long time;

        Application(UUID applicant) {
            this.applicant = applicant;
        }
    }

    public static final class Listing {
        public final int id;
        public UUID seller;
        public String sellerName = "";
        public final List<ItemStack> give = new ArrayList<>();
        public final List<ItemStack> want = new ArrayList<>();
        public long created;
        public long expires;
        // 申請した順
        public final Map<UUID, Application> apps = new LinkedHashMap<>();

        Listing(int id) {
            this.id = id;
        }
    }

    public record Note(String key, List<String> args) {
    }

    final Map<Integer, Listing> listings = new LinkedHashMap<>();
    final Map<UUID, List<ItemStack>> mail = new LinkedHashMap<>();
    final Map<UUID, List<Note>> notes = new LinkedHashMap<>();
    int nextId = 1;

    public static TradeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public List<Listing> all() {
        return new ArrayList<>(listings.values());
    }

    @Nullable
    public Listing byId(int id) {
        return listings.get(id);
    }

    public List<Listing> of(UUID seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (l.seller.equals(seller)) {
                out.add(l);
            }
        }
        return out;
    }

    // その人が出している申請
    public List<Listing> appliedBy(UUID applicant) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (l.apps.containsKey(applicant)) {
                out.add(l);
            }
        }
        return out;
    }

    Listing create(UUID seller, String name, long now, long expires) {
        Listing l = new Listing(nextId++);
        l.seller = seller;
        l.sellerName = name;
        l.created = now;
        l.expires = expires;
        listings.put(l.id, l);
        setDirty();
        return l;
    }

    void remove(Listing l) {
        listings.remove(l.id);
        setDirty();
    }

    public List<ItemStack> mail(UUID who) {
        return mail.getOrDefault(who, List.of());
    }

    void addMail(UUID who, ItemStack s) {
        if (!s.isEmpty()) {
            mail.computeIfAbsent(who, k -> new ArrayList<>()).add(s.copy());
            setDirty();
        }
    }

    public List<Note> notes(UUID who) {
        return notes.getOrDefault(who, List.of());
    }

    void addNote(UUID who, String key, List<String> args) {
        List<Note> list = notes.computeIfAbsent(who, k -> new ArrayList<>());
        list.add(new Note(key, List.copyOf(args)));
        // 古い知らせは 30 まで
        while (list.size() > 30) {
            list.remove(0);
        }
        setDirty();
    }

    List<Note> takeNotes(UUID who) {
        List<Note> list = notes.remove(who);
        if (list != null) {
            setDirty();
        }
        return list == null ? List.of() : list;
    }

    // 自己テスト用
    void clearForTest() {
        listings.clear();
        mail.clear();
        notes.clear();
        setDirty();
    }

    void restoreFrom(TradeData other) {
        listings.clear();
        listings.putAll(other.listings);
        mail.clear();
        mail.putAll(other.mail);
        notes.clear();
        notes.putAll(other.notes);
        nextId = Math.max(nextId, other.nextId);
        setDirty();
    }

    TradeData roundTrip(HolderLookup.Provider registries) {
        return load(save(new CompoundTag(), registries), registries);
    }

    public void dirty() {
        setDirty();
    }

    // ---------------------------------------------------------------- 保存
    private static ListTag saveItems(List<ItemStack> items, HolderLookup.Provider registries) {
        ListTag out = new ListTag();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                // 数が 99 を越える型 (ほしい数) もあるので、数は別に持つ
                CompoundTag t = new CompoundTag();
                t.put("item", s.copyWithCount(1).save(registries));
                t.putInt("n", s.getCount());
                out.add(t);
            }
        }
        return out;
    }

    private static void loadItems(ListTag list, List<ItemStack> into, HolderLookup.Provider registries) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ItemStack s = ItemStack.parseOptional(registries, t.getCompound("item"));
            if (!s.isEmpty()) {
                s.setCount(Math.max(1, t.getInt("n")));
                into.add(s);
            }
        }
    }

    static TradeData load(CompoundTag tag, HolderLookup.Provider registries) {
        TradeData d = new TradeData();
        d.nextId = Math.max(1, tag.getInt("next"));
        ListTag ls = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size(); i++) {
            CompoundTag t = ls.getCompound(i);
            Listing l = new Listing(t.getInt("id"));
            l.seller = t.getUUID("seller");
            l.sellerName = t.getString("name");
            l.created = t.getLong("created");
            l.expires = t.getLong("expires");
            loadItems(t.getList("give", Tag.TAG_COMPOUND), l.give, registries);
            loadItems(t.getList("want", Tag.TAG_COMPOUND), l.want, registries);
            ListTag as = t.getList("apps", Tag.TAG_COMPOUND);
            for (int k = 0; k < as.size(); k++) {
                CompoundTag a = as.getCompound(k);
                Application app = new Application(a.getUUID("u"));
                app.name = a.getString("n");
                app.time = a.getLong("time");
                loadItems(a.getList("offer", Tag.TAG_COMPOUND), app.offer, registries);
                l.apps.put(app.applicant, app);
            }
            d.listings.put(l.id, l);
            d.nextId = Math.max(d.nextId, l.id + 1);
        }
        ListTag ms = tag.getList("mail", Tag.TAG_COMPOUND);
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag t = ms.getCompound(i);
            List<ItemStack> items = new ArrayList<>();
            loadItems(t.getList("items", Tag.TAG_COMPOUND), items, registries);
            if (!items.isEmpty()) {
                d.mail.put(t.getUUID("u"), items);
            }
        }
        ListTag ns = tag.getList("notes", Tag.TAG_COMPOUND);
        for (int i = 0; i < ns.size(); i++) {
            CompoundTag t = ns.getCompound(i);
            List<Note> list = new ArrayList<>();
            ListTag nl = t.getList("list", Tag.TAG_COMPOUND);
            for (int k = 0; k < nl.size(); k++) {
                CompoundTag n = nl.getCompound(k);
                List<String> args = new ArrayList<>();
                for (Tag a : n.getList("args", Tag.TAG_STRING)) {
                    args.add(a.getAsString());
                }
                list.add(new Note(n.getString("key"), args));
            }
            if (!list.isEmpty()) {
                d.notes.put(t.getUUID("u"), list);
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", nextId);
        ListTag ls = new ListTag();
        for (Listing l : listings.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", l.id);
            t.putUUID("seller", l.seller);
            t.putString("name", l.sellerName);
            t.putLong("created", l.created);
            t.putLong("expires", l.expires);
            t.put("give", saveItems(l.give, registries));
            t.put("want", saveItems(l.want, registries));
            ListTag as = new ListTag();
            for (Application a : l.apps.values()) {
                CompoundTag at = new CompoundTag();
                at.putUUID("u", a.applicant);
                at.putString("n", a.name == null ? "" : a.name);
                at.putLong("time", a.time);
                at.put("offer", saveItems(a.offer, registries));
                as.add(at);
            }
            t.put("apps", as);
            ls.add(t);
        }
        tag.put("listings", ls);
        ListTag ms = new ListTag();
        mail.forEach((u, items) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            t.put("items", saveItems(items, registries));
            ms.add(t);
        });
        tag.put("mail", ms);
        ListTag ns = new ListTag();
        notes.forEach((u, list) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            ListTag nl = new ListTag();
            for (Note n : list) {
                CompoundTag nt = new CompoundTag();
                nt.putString("key", n.key());
                ListTag args = new ListTag();
                for (String a : n.args()) {
                    args.add(StringTag.valueOf(a));
                }
                nt.put("args", args);
                nl.add(nt);
            }
            t.put("list", nl);
            ns.add(t);
        });
        tag.put("notes", ns);
        return tag;
    }
}

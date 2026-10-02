package faygolover.rpmedicine.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Список id и тегов ({@code #namespace:path}) для сопоставления с объектом реестра.
 * Пустой список означает «условие не задано».
 */
public final class Matcher<T> {
    private final List<ResourceLocation> ids = new ArrayList<>();
    private final List<TagKey<T>> tags = new ArrayList<>();

    public static <T> Matcher<T> parse(JsonElement el, ResourceKey<? extends Registry<T>> registry) {
        Matcher<T> m = new Matcher<>();
        if (el == null || el.isJsonNull()) return m;
        JsonArray arr;
        if (el.isJsonArray()) arr = el.getAsJsonArray();
        else {
            arr = new JsonArray();
            arr.add(el);
        }
        for (JsonElement e : arr) {
            String s = e.getAsString().trim();
            if (s.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(s.substring(1));
                if (rl != null) m.tags.add(TagKey.create(registry, rl));
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(s);
                if (rl != null) m.ids.add(rl);
            }
        }
        return m;
    }

    public boolean isEmpty() {
        return ids.isEmpty() && tags.isEmpty();
    }

    public boolean matches(Holder<T> holder) {
        for (ResourceLocation id : ids) if (holder.is(id)) return true;
        for (TagKey<T> t : tags) if (holder.is(t)) return true;
        return false;
    }
}

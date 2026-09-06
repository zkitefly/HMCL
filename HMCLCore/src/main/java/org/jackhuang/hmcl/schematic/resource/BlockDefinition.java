/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.schematic.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A parsed `blockstates/<block>.json` definition that maps block state properties to models.
///
/// The structure mirrors the vanilla model definition format: either a `variants` object with
/// comma-separated property strings, or a `multipart` list with `when` conditions.
@NotNullByDefault
final class BlockDefinition {

    /// A single model variant, optionally rotated around the X or Y axis.
    static final class ModelVariant {
        final String model;
        final int x;
        final int y;

        ModelVariant(String model, int x, int y) {
            this.model = model;
            this.x = x;
            this.y = y;
        }
    }

    /// A `when` condition of a multipart part, composed of OR/AND groups or property states.
    private static final class Condition {
        private final @Nullable List<Condition> or;
        private final @Nullable List<Condition> and;
        private final @Nullable Map<String, String> states;

        private Condition(@Nullable List<Condition> or, @Nullable List<Condition> and, @Nullable Map<String, String> states) {
            this.or = or;
            this.and = and;
            this.states = states;
        }

        static Condition or(List<Condition> children) {
            return new Condition(children, null, null);
        }

        static Condition and(List<Condition> children) {
            return new Condition(null, children, null);
        }

        static Condition states(Map<String, String> states) {
            return new Condition(null, null, states);
        }

        boolean matches(Map<String, String> props) {
            if (or != null) {
                for (Condition child : or) {
                    if (child.matches(props)) {
                        return true;
                    }
                }
                return false;
            }
            if (and != null) {
                for (Condition child : and) {
                    if (!child.matches(props)) {
                        return false;
                    }
                }
                return true;
            }
            if (states != null) {
                for (Map.Entry<String, String> entry : states.entrySet()) {
                    String value = props.get(entry.getKey());
                    boolean matches = false;
                    for (String candidate : entry.getValue().split("\\|")) {
                        if (candidate.equals(value)) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static final class MultiPart {
        final @Nullable Condition when;
        final List<ModelVariant> apply;

        MultiPart(@Nullable Condition when, List<ModelVariant> apply) {
            this.when = when;
            this.apply = apply;
        }
    }

    private final @Nullable Map<String, List<ModelVariant>> variants;
    private final @Nullable List<MultiPart> multipart;

    private BlockDefinition(@Nullable Map<String, List<ModelVariant>> variants, @Nullable List<MultiPart> multipart) {
        this.variants = variants;
        this.multipart = multipart;
    }

    /// Parses a blockstate definition.
    ///
    /// @param json the root object of a `blockstates/<block>.json` file
    /// @return the parsed definition, or `null` when it contains neither `variants` nor `multipart`
    static @Nullable BlockDefinition parse(JsonObject json) {
        JsonElement variantsElement = json.get("variants");
        JsonElement multipartElement = json.get("multipart");
        if (variantsElement != null && variantsElement.isJsonObject()) {
            Map<String, List<ModelVariant>> variants = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : variantsElement.getAsJsonObject().entrySet()) {
                variants.put(entry.getKey(), parseVariants(entry.getValue()));
            }
            return new BlockDefinition(variants, null);
        }
        if (multipartElement != null && multipartElement.isJsonArray()) {
            List<MultiPart> multipart = new ArrayList<>();
            for (JsonElement partElement : multipartElement.getAsJsonArray()) {
                JsonObject part = partElement.getAsJsonObject();
                multipart.add(new MultiPart(parseCondition(part.get("when")), parseVariants(part.get("apply"))));
            }
            return new BlockDefinition(null, multipart);
        }
        return null;
    }

    /// Returns the model variants that match the given block state properties, or `null` when
    /// no variant matches.
    ///
    /// For `variants` definitions, the first key whose `key=value` pairs all match is used; an
    /// empty-string key acts as the fallback. For `multipart` definitions, every part whose
    /// `when` condition matches contributes its first variant.
    ///
    /// @param props the block state properties, e.g. `{facing: north}`
    /// @return the matching variants, or `null` when nothing matches
    @Nullable List<ModelVariant> getModelVariants(Map<String, String> props) {
        if (variants != null) {
            for (Map.Entry<String, List<ModelVariant>> entry : variants.entrySet()) {
                if (matchesVariant(entry.getKey(), props)) {
                    return entry.getValue();
                }
            }
            return variants.get("");
        }
        if (multipart != null) {
            List<ModelVariant> result = new ArrayList<>();
            for (MultiPart part : multipart) {
                if (part.when == null || part.when.matches(props)) {
                    if (!part.apply.isEmpty()) {
                        result.add(part.apply.get(0));
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    /// Returns all model ids referenced by this definition, used to preload the models.
    Set<String> getAllModelIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (variants != null) {
            for (List<ModelVariant> list : variants.values()) {
                for (ModelVariant variant : list) {
                    ids.add(variant.model);
                }
            }
        }
        if (multipart != null) {
            for (MultiPart part : multipart) {
                for (ModelVariant variant : part.apply) {
                    ids.add(variant.model);
                }
            }
        }
        return ids;
    }

    /// Returns whether a comma-separated variant key matches the given properties.
    private static boolean matchesVariant(String variant, Map<String, String> props) {
        for (String pair : variant.split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (!value.equals(props.get(key))) {
                return false;
            }
        }
        return true;
    }

    /// Parses a variant entry that may be a single object or an array of weighted objects.
    private static List<ModelVariant> parseVariants(@Nullable JsonElement element) {
        List<ModelVariant> result = new ArrayList<>();
        if (element == null) {
            return result;
        }
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    result.add(parseVariant(e.getAsJsonObject()));
                }
            }
        } else if (element.isJsonObject()) {
            result.add(parseVariant(element.getAsJsonObject()));
        }
        return result;
    }

    private static ModelVariant parseVariant(JsonObject obj) {
        JsonElement model = obj.get("model");
        String modelId = model != null && model.isJsonPrimitive() ? model.getAsString() : "";
        int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
        int y = obj.has("y") ? obj.get("y").getAsInt() : 0;
        return new ModelVariant(modelId, x, y);
    }

    /// Parses a multipart `when` condition into a [Condition] tree.
    private static @Nullable Condition parseCondition(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        JsonElement or = obj.get("OR");
        if (or != null && or.isJsonArray()) {
            List<Condition> children = new ArrayList<>();
            for (JsonElement e : or.getAsJsonArray()) {
                children.add(parseCondition(e));
            }
            return Condition.or(children);
        }
        JsonElement and = obj.get("AND");
        if (and != null && and.isJsonArray()) {
            List<Condition> children = new ArrayList<>();
            for (JsonElement e : and.getAsJsonArray()) {
                children.add(parseCondition(e));
            }
            return Condition.and(children);
        }
        Map<String, String> states = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                states.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return Condition.states(states);
    }
}

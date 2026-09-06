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
package org.jackhuang.hmcl.schematic;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// A block state stored in a schematic, such as `minecraft:stone` or `minecraft:lever[facing=west,powered=false]`.
///
/// The block state name is the full identifier of the block, and the properties are the
/// block state properties written by Litematica, used to distinguish variants of the same block.
@NotNullByDefault
public final class SchematicBlockState {

    private static final String DEFAULT_NAMESPACE = "minecraft:";

    /// Creates a block state from the given identifier and properties.
    ///
    /// @param name       the full block identifier, e.g. `minecraft:lever`
    /// @param properties the block state properties, may be empty but must not contain null values
    public SchematicBlockState(String name, @Unmodifiable Map<String, String> properties) {
        this.name = Objects.requireNonNull(name, "name");
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    private final String name;

    private final @Unmodifiable Map<String, String> properties;

    /// Returns the full block identifier, e.g. `minecraft:lever`.
    public String getName() {
        return name;
    }

    /// Returns the block state properties in an unmodifiable view.
    public @Unmodifiable Map<String, String> getProperties() {
        return properties;
    }

    /// Returns whether this block state is any kind of air block (`air`, `cave_air` or `void_air`).
    public boolean isAir() {
        String unqualified = name.startsWith(DEFAULT_NAMESPACE)
                ? name.substring(DEFAULT_NAMESPACE.length())
                : name;
        return unqualified.equals("air") || unqualified.equals("cave_air") || unqualified.equals("void_air");
    }

    /// Returns the string representation used for color lookup, e.g. `minecraft:lever[facing=west]`.
    ///
    /// Property keys are sorted so that the representation is deterministic.
    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return name;
        }

        StringBuilder builder = new StringBuilder(name).append('[');
        properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.append(entry.getKey()).append('=').append(entry.getValue()).append(','));
        builder.setCharAt(builder.length() - 1, ']');
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchematicBlockState that)) return false;
        return name.equals(that.name) && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + properties.hashCode();
    }
}

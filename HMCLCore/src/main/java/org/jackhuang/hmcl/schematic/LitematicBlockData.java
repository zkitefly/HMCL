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

import org.glavo.nbt.io.NBTCodec;
import org.glavo.nbt.tag.*;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/// The block data of a Litematic file, decoded from its regions.
///
/// Unlike [LitematicFile] which only reads metadata, this class additionally parses the
/// `BlockStatePalette` and the packed `BlockStates` arrays of every region so that the
/// schematic can be rendered.
@NotNullByDefault
public final class LitematicBlockData {

    /// Loads and decodes the block data of the given Litematic file.
    ///
    /// @param file the path of the `.litematic` file
    /// @return the decoded block data
    /// @throws IOException if the file is not a valid Litematic file or its block data is malformed
    public static LitematicBlockData load(Path file) throws IOException {
        CompoundTag root;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            root = NBTCodec.of().readTag(in, TagType.COMPOUND);
        }

        CompoundTag regionsTag;
        Tag regions = root.get("Regions");
        if (regions instanceof CompoundTag compoundTag) {
            regionsTag = compoundTag;
        } else {
            throw new IOException("Regions tag not found");
        }

        List<SchematicBlockState> palette = new ArrayList<>();
        List<Region> regionsOut = new ArrayList<>();
        int totalBlocks = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Tag tag : regionsTag) {
            if (!(tag instanceof CompoundTag region)) {
                continue;
            }

            int[] position = readInt3(region.get("Position"));
            int[] size = readInt3(region.get("Size"));
            int sizeX = Math.abs(size[0]);
            int sizeY = Math.abs(size[1]);
            int sizeZ = Math.abs(size[2]);

            Tag paletteTag = region.get("BlockStatePalette");
            Tag blockStatesTag = region.get("BlockStates");
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0
                    || !(paletteTag instanceof ListTag<?>)
                    || !(blockStatesTag instanceof LongArrayTag)) {
                continue;
            }

            ListTag<?> paletteList = (ListTag<?>) paletteTag;
            int paletteSize = paletteList.size();
            if (paletteSize <= 0) {
                throw new IOException("Empty block state palette in region " + tag.getName());
            }

            List<SchematicBlockState> regionPalette = new ArrayList<>(paletteSize);
            boolean[] airFlags = new boolean[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                if (!(paletteList.getTag(i) instanceof CompoundTag entry)) {
                    throw new IOException("Invalid block state palette in region " + tag.getName());
                }
                String name = entry.getStringOrEmpty("Name");
                Map<String, String> properties = new LinkedHashMap<>();
                if (entry.get("Properties") instanceof CompoundTag propertiesTag) {
                    for (Tag property : propertiesTag) {
                        if (property instanceof StringTag value) {
                            properties.put(property.getName(), value.get());
                        }
                    }
                }
                SchematicBlockState state = new SchematicBlockState(name, properties);
                regionPalette.add(state);
                airFlags[i] = state.isAir();
            }

            int paletteOffset = palette.size();
            palette.addAll(regionPalette);

            long[] packed = ((LongArrayTag) blockStatesTag).getArray();
            int[] blockStates = unpackBlockStates(packed, sizeX, sizeY, sizeZ, paletteSize);

            int regionBlocks = 0;
            for (int value : blockStates) {
                if (!airFlags[value]) {
                    regionBlocks++;
                }
            }
            totalBlocks += regionBlocks;

            minX = Math.min(minX, position[0]);
            minY = Math.min(minY, position[1]);
            minZ = Math.min(minZ, position[2]);
            maxX = Math.max(maxX, position[0] + sizeX);
            maxY = Math.max(maxY, position[1] + sizeY);
            maxZ = Math.max(maxZ, position[2] + sizeZ);

            regionsOut.add(new Region(position[0], position[1], position[2], sizeX, sizeY, sizeZ, blockStates, paletteOffset));
        }

        if (regionsOut.isEmpty()) {
            throw new IOException("No valid regions found in " + file);
        }

        return new LitematicBlockData(
                Collections.unmodifiableList(palette),
                Collections.unmodifiableList(regionsOut),
                totalBlocks,
                minX, minY, minZ,
                maxX, maxY, maxZ);
    }

    /// Reads an `Int3` tag that may be stored either as a compound (`{x, y, z}`) or as an array/list.
    private static int[] readInt3(@Nullable Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            return new int[]{
                    compoundTag.getIntOrZero("x"),
                    compoundTag.getIntOrZero("y"),
                    compoundTag.getIntOrZero("z")
            };
        }
        if (tag instanceof IntArrayTag intArrayTag) {
            int[] array = intArrayTag.getArray();
            return new int[]{
                    array.length > 0 ? array[0] : 0,
                    array.length > 1 ? array[1] : 0,
                    array.length > 2 ? array[2] : 0
            };
        }
        if (tag instanceof ListTag<?> listTag) {
            int[] result = new int[3];
            for (int i = 0; i < Math.min(listTag.size(), 3); i++) {
                if (listTag.getTag(i) instanceof IntTag intTag) {
                    result[i] = intTag.get();
                }
            }
            return result;
        }
        return new int[3];
    }

    /// Decodes the Litematica packed `BlockStates` long array into per-block palette indices.
    ///
    /// Block index follows the Litematica layout: `x + z * sizeX + y * sizeX * sizeZ`.
    private static int[] unpackBlockStates(long[] packed, int sizeX, int sizeY, int sizeZ, int paletteSize) throws IOException {
        int volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        } catch (ArithmeticException e) {
            throw new IOException("Region volume is too large", e);
        }

        int bitsPerBlock = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        long mask = (1L << bitsPerBlock) - 1;
        int[] blockStates = new int[volume];

        for (int i = 0; i < volume; i++) {
            int bitIndex = i * bitsPerBlock;
            int longIndex = bitIndex >>> 6;
            int bitOffset = bitIndex & 63;

            if (longIndex >= packed.length) {
                throw new IOException("BlockStates array is too short");
            }

            long value = packed[longIndex] >>> bitOffset;
            if (bitOffset + bitsPerBlock > 64 && longIndex + 1 < packed.length) {
                value |= packed[longIndex + 1] << (64 - bitOffset);
            }

            int paletteIndex = (int) (value & mask);
            if (paletteIndex >= paletteSize) {
                throw new IOException("Block state index out of range");
            }
            blockStates[i] = paletteIndex;
        }

        return blockStates;
    }

    private final @Unmodifiable List<SchematicBlockState> palette;

    private final @Unmodifiable List<Region> regions;

    private final int totalBlocks;

    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    private LitematicBlockData(@Unmodifiable List<SchematicBlockState> palette,
                               @Unmodifiable List<Region> regions,
                               int totalBlocks,
                               int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ) {
        this.palette = palette;
        this.regions = regions;
        this.totalBlocks = totalBlocks;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /// Returns the shared block state palette of all regions.
    public @Unmodifiable List<SchematicBlockState> getPalette() {
        return palette;
    }

    /// Returns the regions of this schematic in an unmodifiable view.
    public @Unmodifiable List<Region> getRegions() {
        return regions;
    }

    /// Returns the total number of non-air blocks in this schematic.
    public int getTotalBlocks() {
        return totalBlocks;
    }

    /// Returns the minimum corner of the bounding box enclosing all regions, X coordinate.
    public int getMinX() {
        return minX;
    }

    /// Returns the minimum corner of the bounding box enclosing all regions, Y coordinate.
    public int getMinY() {
        return minY;
    }

    /// Returns the minimum corner of the bounding box enclosing all regions, Z coordinate.
    public int getMinZ() {
        return minZ;
    }

    /// Returns the maximum corner of the bounding box enclosing all regions, X coordinate.
    public int getMaxX() {
        return maxX;
    }

    /// Returns the maximum corner of the bounding box enclosing all regions, Y coordinate.
    public int getMaxY() {
        return maxY;
    }

    /// Returns the maximum corner of the bounding box enclosing all regions, Z coordinate.
    public int getMaxZ() {
        return maxZ;
    }

    /// Returns the size of the bounding box enclosing all regions along the X axis.
    public int getSizeX() {
        return maxX - minX;
    }

    /// Returns the size of the bounding box enclosing all regions along the Y axis.
    public int getSizeY() {
        return maxY - minY;
    }

    /// Returns the size of the bounding box enclosing all regions along the Z axis.
    public int getSizeZ() {
        return maxZ - minZ;
    }

    /// A single region of a Litematic schematic.
    public static final class Region {

        private final int positionX;
        private final int positionY;
        private final int positionZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int[] blockStates;
        private final int paletteOffset;

        private Region(int positionX, int positionY, int positionZ,
                       int sizeX, int sizeY, int sizeZ,
                       int[] blockStates, int paletteOffset) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.positionZ = positionZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blockStates = blockStates;
            this.paletteOffset = paletteOffset;
        }

        /// Returns the origin of the region along the X axis, in schematic coordinates.
        public int getPositionX() {
            return positionX;
        }

        /// Returns the origin of the region along the Y axis, in schematic coordinates.
        public int getPositionY() {
            return positionY;
        }

        /// Returns the origin of the region along the Z axis, in schematic coordinates.
        public int getPositionZ() {
            return positionZ;
        }

        /// Returns the size of the region along the X axis.
        public int getSizeX() {
            return sizeX;
        }

        /// Returns the size of the region along the Y axis.
        public int getSizeY() {
            return sizeY;
        }

        /// Returns the size of the region along the Z axis.
        public int getSizeZ() {
            return sizeZ;
        }

        /// Returns the palette index of the block at the given coordinates.
        ///
        /// The index uses the Litematica layout `x + z * sizeX + y * sizeX * sizeZ`.
        /// The returned value is an index into the shared palette of [LitematicBlockData#getPalette].
        ///
        /// @throws IndexOutOfBoundsException if the coordinates are outside the region
        public int getBlockStateIndex(int x, int y, int z) {
            return blockStates[x + z * sizeX + y * sizeX * sizeZ] + paletteOffset;
        }

        /// Returns the offset of this region's palette within the shared palette of [LitematicBlockData#getPalette].
        public int getPaletteOffset() {
            return paletteOffset;
        }

        /// Returns the raw palette index (before the shared palette offset) of a block, or -1 if out of bounds.
        ///
        /// @param x the X coordinate in the region, relative to the region origin
        /// @param y the Y coordinate in the region, relative to the region origin
        /// @param z the Z coordinate in the region, relative to the region origin
        public int getRawBlockStateIndex(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                return -1;
            }
            return blockStates[x + z * sizeX + y * sizeX * sizeZ];
        }
    }
}

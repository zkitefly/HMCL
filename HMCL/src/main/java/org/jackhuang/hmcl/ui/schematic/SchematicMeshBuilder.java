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
package org.jackhuang.hmcl.ui.schematic;

import javafx.collections.FXCollections;
import javafx.collections.ObservableFloatArray;
import javafx.collections.ObservableIntegerArray;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import org.jackhuang.hmcl.schematic.BlockColors;
import org.jackhuang.hmcl.schematic.LitematicBlockData;
import org.jackhuang.hmcl.schematic.SchematicBlockState;
import org.jackhuang.hmcl.schematic.resource.BlockCulling;
import org.jackhuang.hmcl.schematic.resource.MinecraftResourcePack;
import org.jackhuang.hmcl.schematic.resource.ModelFace;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

/// Builds JavaFX triangle meshes from decoded Litematic block data.
///
/// The geometry is emitted in chunks so that large schematics can be displayed progressively
/// while the remaining chunks are still being generated on a background thread. Faces that are
/// hidden by a neighboring opaque block are culled, so that only the outer hull of the structure
/// is drawn, which keeps the vertex count low enough for interactive preview.
///
/// When a [MinecraftResourcePack] is supplied, blocks are rendered with their real textures
/// from the corresponding Minecraft version; blocks that cannot be resolved fall back to the
/// solid-color rendering.
@NotNullByDefault
public final class SchematicMeshBuilder {

    /// Thrown when the schematic needs more vertices than the configured budget allows.
    public static final class SchematicTooLargeException extends RuntimeException {
        private final int maxVertices;
        private final int requiredVertices;

        private SchematicTooLargeException(int maxVertices, int requiredVertices) {
            super("Schematic requires " + requiredVertices + " vertices, exceeding the budget of " + maxVertices);
            this.maxVertices = maxVertices;
            this.requiredVertices = requiredVertices;
        }

        /// Returns the configured vertex budget.
        public int getMaxVertices() {
            return maxVertices;
        }

        /// Returns the number of vertices the schematic would need.
        public int getRequiredVertices() {
            return requiredVertices;
        }
    }

    /// Receives the build progress.
    @FunctionalInterface
    public interface Progress {
        /// @param progress the current progress in the range [0.0, 1.0]
        void onProgress(float progress);
    }

    /// The default chunk size in vertices.
    public static final int DEFAULT_CHUNK_VERTICES = 200_000;

    /// The default total vertex budget.
    public static final int DEFAULT_MAX_VERTICES = 4_000_000;

    /// The maximum dimension of any bounding box axis, large enough for any practical schematic.
    private static final int MAX_AXIS_SIZE = 1 << 20;

    /// The four vertices of each of the six cube faces, relative to the block origin.
    ///
    /// The winding order is counter-clockwise when viewed from outside, so that the
    /// computed face normal always points outwards.
    private static final float[][] FACES = {
            // +X
            {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},
            // -X
            {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
            // +Y
            {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},
            // -Y
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
            // +Z
            {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
            // -Z
            {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},
    };

    /// The outward normal of each face.
    private static final float[][] NORMALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /// The neighbor offset of each face, used for hidden-face culling.
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    /// The chunk accumulator key of the textured faces sharing the atlas material.
    private static final Object ATLAS_KEY = new Object();

    /// The width and height of the procedurally generated per-color textures.
    private static final int TEXTURE_SIZE = 16;

    private final int chunkVertices;
    private final int maxVertices;
    private final Map<Integer, PhongMaterial> materials = new HashMap<>();
    private final @Nullable MinecraftResourcePack resourcePack;
    private final @Nullable PhongMaterial atlasMaterial;

    /// Creates a builder with the given quality settings that renders solid-color blocks.
    ///
    /// @param chunkVertices the maximum number of vertices a single chunk may contain
    /// @param maxVertices   the total number of vertices the builder is allowed to produce
    public SchematicMeshBuilder(int chunkVertices, int maxVertices) {
        this(chunkVertices, maxVertices, null);
    }

    /// Creates a builder with the given quality settings and an optional texture resource pack.
    ///
    /// @param chunkVertices the maximum number of vertices a single chunk may contain
    /// @param maxVertices   the total number of vertices the builder is allowed to produce
    /// @param resourcePack  the resource pack with real block textures, or null for solid colors
    public SchematicMeshBuilder(int chunkVertices, int maxVertices, @Nullable MinecraftResourcePack resourcePack) {
        this.chunkVertices = chunkVertices;
        this.maxVertices = maxVertices;
        this.resourcePack = resourcePack != null && !resourcePack.isEmpty() ? resourcePack : null;
        Image atlasImage = this.resourcePack != null ? this.resourcePack.getAtlasImage() : null;
        this.atlasMaterial = atlasImage != null
                ? new PhongMaterial(Color.WHITE, atlasImage, null, null, null)
                : null;
    }

    /// Builds the whole schematic into chunk groups.
    ///
    /// This method must be called on a background thread. Each completed chunk is handed to
    /// `chunkConsumer`, and the overall progress is reported through `progress`.
    ///
    /// @param data          the decoded block data
    /// @param progress      receives the build progress, ranging from 0.0 to 1.0; may be null
    /// @param chunkConsumer receives one group per completed chunk
    /// @throws SchematicTooLargeException if the total vertex budget is exceeded
    public void build(LitematicBlockData data, @Nullable Progress progress, Consumer<Group> chunkConsumer) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(chunkConsumer, "chunkConsumer");

        int minX = data.getMinX(), minY = data.getMinY(), minZ = data.getMinZ();
        int maxX = data.getMaxX(), maxY = data.getMaxY(), maxZ = data.getMaxZ();
        if (data.getSizeX() >= MAX_AXIS_SIZE || data.getSizeY() >= MAX_AXIS_SIZE || data.getSizeZ() >= MAX_AXIS_SIZE) {
            throw new SchematicTooLargeException(maxVertices, Integer.MAX_VALUE);
        }

        // Occupancy pass: collect every non-air block so that hidden faces can be culled
        // across region borders as well as inside a single region. In textured mode, only
        // opaque full cubes hide the faces of their neighbors.
        HashSet<Long> occupied = new HashSet<>();
        HashSet<Long> fullCubes = resourcePack != null ? new HashSet<>() : null;
        int totalBlocks = 0;
        for (LitematicBlockData.Region region : data.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        int paletteIndex = region.getRawBlockStateIndex(x, y, z);
                        if (paletteIndex >= 0) {
                            SchematicBlockState state = data.getPalette().get(paletteIndex + region.getPaletteOffset());
                            if (!state.isAir()) {
                                long packed = pack(region.getPositionX() + x - minX, region.getPositionY() + y - minY, region.getPositionZ() + z - minZ);
                                occupied.add(packed);
                                if (fullCubes != null && resourcePack.isFullCube(state)) {
                                    fullCubes.add(packed);
                                }
                                totalBlocks++;
                            }
                        }
                    }
                }
            }
            if (progress != null) {
                progress.onProgress(0.4f);
            }
        }

        if (totalBlocks == 0) {
            chunkConsumer.accept(new Group());
            return;
        }

        // Geometry pass: emit the visible faces of every solid block.
        Map<Object, MeshData> chunk = new HashMap<>();
        int chunkVertexCount = 0;
        int totalVertices = 0;
        int processed = 0;

        for (LitematicBlockData.Region region : data.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        int paletteIndex = region.getRawBlockStateIndex(x, y, z);
                        if (paletteIndex < 0) {
                            continue;
                        }
                        SchematicBlockState state = data.getPalette().get(paletteIndex + region.getPaletteOffset());
                        if (state.isAir()) {
                            continue;
                        }

                        int wx = region.getPositionX() + x;
                        int wy = region.getPositionY() + y;
                        int wz = region.getPositionZ() + z;

                        int emitted = 0;
                        if (resourcePack != null) {
                            emitted = addTexturedBlock(chunk, resourcePack, state, wx, wy, wz,
                                    minX, minY, minZ, maxX, maxY, maxZ, fullCubes);
                        } else {
                            emitted = addSolidBlock(chunk, state, wx, wy, wz,
                                    minX, minY, minZ, maxX, maxY, maxZ, occupied);
                        }

                        chunkVertexCount += emitted;
                        totalVertices += emitted;
                        if (totalVertices > maxVertices) {
                            throw new SchematicTooLargeException(maxVertices, totalVertices);
                        }
                        if (chunkVertexCount >= chunkVertices) {
                            flushChunk(chunk, chunkConsumer);
                            chunkVertexCount = 0;
                        }

                        if (progress != null && (++processed & 0x1FFF) == 0) {
                            progress.onProgress(0.4f + 0.6f * processed / totalBlocks);
                        }
                    }
                }
            }
        }
        flushChunk(chunk, chunkConsumer);
        if (progress != null) {
            progress.onProgress(1.0f);
        }
    }

    /// Adds the real-textured faces of a block, falling back to a solid cube when the block
    /// cannot be resolved. Returns the number of vertices emitted.
    private int addTexturedBlock(Map<Object, MeshData> chunk, MinecraftResourcePack pack, SchematicBlockState state,
                                 int wx, int wy, int wz, int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ, HashSet<Long> fullCubes) {
        BlockCulling culling = computeCulling(wx, wy, wz, minX, minY, minZ, maxX, maxY, maxZ, fullCubes);
        List<ModelFace> faces = pack.getFaces(state, culling);
        int vertices = 0;
        if (faces != null) {
            for (ModelFace face : faces) {
                addTexturedFace(chunk, face, wx, wy, wz);
                vertices += 4;
            }
        }
        if (vertices == 0) {
            for (int face = 0; face < 6; face++) {
                int nx = wx + NEIGHBORS[face][0];
                int ny = wy + NEIGHBORS[face][1];
                int nz = wz + NEIGHBORS[face][2];
                if (nx < minX || nx >= maxX || ny < minY || ny >= maxY || nz < minZ || nz >= maxZ
                        || fullCubes.contains(pack(nx - minX, ny - minY, nz - minZ))) {
                    continue; // This face is hidden by a neighboring full cube.
                }
                addFace(chunk, BlockColors.getColor(state.getName()), wx, wy, wz, face);
                vertices += 4;
            }
        }
        return vertices;
    }

    /// Adds the six solid-colored faces of a block, culling faces hidden by any solid neighbor.
    /// Returns the number of vertices emitted.
    private int addSolidBlock(Map<Object, MeshData> chunk, SchematicBlockState state,
                              int wx, int wy, int wz, int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ, HashSet<Long> occupied) {
        int vertices = 0;
        for (int face = 0; face < 6; face++) {
            int nx = wx + NEIGHBORS[face][0];
            int ny = wy + NEIGHBORS[face][1];
            int nz = wz + NEIGHBORS[face][2];
            if (nx < minX || nx >= maxX || ny < minY || ny >= maxY || nz < minZ || nz >= maxZ
                    || occupied.contains(pack(nx - minX, ny - minY, nz - minZ))) {
                continue; // This face is hidden by a neighboring block.
            }
            addFace(chunk, BlockColors.getColor(state.getName()), wx, wy, wz, face);
            vertices += 4;
        }
        return vertices;
    }

    /// Computes which directions of the block at `(wx, wy, wz)` are hidden by a full cube.
    private static BlockCulling computeCulling(int wx, int wy, int wz, int minX, int minY, int minZ,
                                               int maxX, int maxY, int maxZ, HashSet<Long> fullCubes) {
        return new BlockCulling(
                isFullCubeAt(wx, wy + 1, wz, minX, minY, minZ, maxX, maxY, maxZ, fullCubes),
                isFullCubeAt(wx, wy - 1, wz, minX, minY, minZ, maxX, maxY, maxZ, fullCubes),
                isFullCubeAt(wx, wy, wz - 1, minX, minY, minZ, maxX, maxY, maxZ, fullCubes),
                isFullCubeAt(wx, wy, wz + 1, minX, minY, minZ, maxX, maxY, maxZ, fullCubes),
                isFullCubeAt(wx + 1, wy, wz, minX, minY, minZ, maxX, maxY, maxZ, fullCubes),
                isFullCubeAt(wx - 1, wy, wz, minX, minY, minZ, maxX, maxY, maxZ, fullCubes));
    }

    /// Returns whether the block at a world position is an opaque full cube, or false when the
    /// position is outside the schematic bounds.
    private static boolean isFullCubeAt(int wx, int wy, int wz, int minX, int minY, int minZ,
                                        int maxX, int maxY, int maxZ, HashSet<Long> fullCubes) {
        if (wx < minX || wx >= maxX || wy < minY || wy >= maxY || wz < minZ || wz >= maxZ) {
            return false;
        }
        return fullCubes.contains(pack(wx - minX, wy - minY, wz - minZ));
    }

    /// Adds a solid-colored cube face to the chunk accumulator, grouped by block color.
    ///
    /// The four vertices of the face map the full [0, 1] texture range so that the
    /// per-color procedural texture covers the whole face.
    private static void addFace(Map<Object, MeshData> chunk, int color, int wx, int wy, int wz, int face) {
        MeshData data = chunk.computeIfAbsent(color, ignored -> new MeshData());
        float[] offsets = FACES[face];
        float[] normal = NORMALS[face];
        int base = data.positions.size() / 3;
        for (int i = 0; i < 4; i++) {
            data.positions.addAll(wx + offsets[i * 3], wy + offsets[i * 3 + 1], wz + offsets[i * 3 + 2]);
            data.normals.addAll(normal[0], normal[1], normal[2]);
        }
        data.uvs.addAll(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f);
        data.faces.addAll(
                base, base, 0, base + 1, base + 1, 0, base + 2, base + 2, 0,
                base, base, 0, base + 2, base + 2, 0, base + 3, base + 3, 0);
        data.smoothing.addAll(0, 0);
    }

    /// Adds a textured quad from a block model to the chunk accumulator.
    ///
    /// Model positions are expressed in 16-pixel space, so they are scaled into block space and
    /// offset by the block position. Texture coordinates already reference the atlas in
    /// normalized space.
    private static void addTexturedFace(Map<Object, MeshData> chunk, ModelFace face, int wx, int wy, int wz) {
        MeshData data = chunk.computeIfAbsent(ATLAS_KEY, ignored -> new MeshData());
        float[] positions = face.getPositions();
        float[] uvs = face.getUvs();
        int base = data.positions.size() / 3;
        for (int i = 0; i < 4; i++) {
            data.positions.addAll(
                    wx + positions[i * 3] / 16.0f,
                    wy + positions[i * 3 + 1] / 16.0f,
                    wz + positions[i * 3 + 2] / 16.0f);
            data.normals.addAll(face.getNormalX(), face.getNormalY(), face.getNormalZ());
        }
        data.uvs.addAll(uvs[0], uvs[1], uvs[2], uvs[3], uvs[4], uvs[5], uvs[6], uvs[7]);
        data.faces.addAll(
                base, base, 0, base + 1, base + 1, 0, base + 2, base + 2, 0,
                base, base, 0, base + 2, base + 2, 0, base + 3, base + 3, 0);
        data.smoothing.addAll(0, 0);
    }

    /// Converts the accumulated face data of a chunk into `MeshView` nodes and forwards
    /// the chunk group to `chunkConsumer`. The accumulator is cleared afterwards.
    private void flushChunk(Map<Object, MeshData> chunk, Consumer<Group> chunkConsumer) {
        if (chunk.isEmpty()) {
            return;
        }
        Group group = new Group();
        for (Map.Entry<Object, MeshData> entry : chunk.entrySet()) {
            MeshData data = entry.getValue();
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            mesh.getPoints().setAll(data.positions);
            mesh.getNormals().setAll(data.normals);
            mesh.getTexCoords().setAll(data.uvs);
            mesh.getFaces().setAll(data.faces);
            mesh.getFaceSmoothingGroups().setAll(data.smoothing);

            MeshView view = new MeshView(mesh);
            if (entry.getKey() == ATLAS_KEY && atlasMaterial != null) {
                view.setMaterial(atlasMaterial);
            } else {
                view.setMaterial(materials.computeIfAbsent((Integer) entry.getKey(), SchematicMeshBuilder::createMaterial));
            }
            group.getChildren().add(view);
        }
        chunk.clear();
        chunkConsumer.accept(group);
    }

    /// Creates a material with a deterministic pixelated texture derived from the base color.
    ///
    /// The diffuse map mimics the low-resolution look of Minecraft textures so that the
    /// preview does not rely on a bundle of real block textures.
    private static PhongMaterial createMaterial(int argb) {
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(createTexture(argb));
        return material;
    }

    /// Generates a 16x16 pixel texture for the given base color.
    ///
    /// A checkerboard of slightly lighter and darker 4x4 cells, combined with per-pixel
    /// noise, gives every face a subtle tileable pattern. The noise is seeded by the color
    /// so the same block always receives the same texture.
    private static Image createTexture(int argb) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;

        WritableImage image = new WritableImage(TEXTURE_SIZE, TEXTURE_SIZE);
        PixelWriter writer = image.getPixelWriter();
        Random random = new Random(argb);
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                double noise = (random.nextDouble() - 0.5) * 0.16;
                double cell = ((x / 4 + y / 4) & 1) == 0 ? 0.05 : -0.05;
                double shade = 1.0 + noise + cell;
                writer.setArgb(x, y, 0xFF000000
                        | clamp255((int) (red * shade)) << 16
                        | clamp255((int) (green * shade)) << 8
                        | clamp255((int) (blue * shade)));
            }
        }
        return image;
    }

    /// Clamps a byte-sized color component into the valid range.
    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /// Packs a non-negative world coordinate into a single long for hash-set lookups.
    ///
    /// Coordinates must be smaller than 2^21 per axis, which is far beyond any real schematic.
    private static long pack(long x, long y, long z) {
        return (x << 42) | (y << 21) | z;
    }

    /// Accumulates the raw face data of one chunk. Each color in a chunk gets its own mesh,
    /// so every mesh can use a single `PhongMaterial` and a single texture.
    private static final class MeshData {
        final ObservableFloatArray positions = FXCollections.observableFloatArray();
        final ObservableFloatArray normals = FXCollections.observableFloatArray();
        final ObservableFloatArray uvs = FXCollections.observableFloatArray();
        final ObservableIntegerArray faces = FXCollections.observableIntegerArray();
        final ObservableIntegerArray smoothing = FXCollections.observableIntegerArray();
    }
}

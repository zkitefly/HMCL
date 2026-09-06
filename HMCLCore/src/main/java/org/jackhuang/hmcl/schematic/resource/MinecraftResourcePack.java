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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.jackhuang.hmcl.schematic.SchematicBlockState;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/// Loads block definitions, models and textures from a Minecraft client jar.
///
/// Only the block states that actually occur in a schematic are loaded, so opening a large jar
/// stays fast. The referenced block textures are packed into a single JavaFX [WritableImage]
/// atlas; the normalized UV regions are handed out through [MinecraftResourcePack#getFaces].
///
/// Blocks that cannot be resolved (modded blocks, or versions whose resource layout differs)
/// simply yield no faces and fall back to the solid-color rendering of the mesh builder.
@NotNullByDefault
public final class MinecraftResourcePack {

    private static final String NAMESPACE = "minecraft:";

    /// The grid cell size of the texture atlas; vanilla block textures are 16x16 pixels.
    private static final int CELL_SIZE = 16;

    /// The size of the square texture atlas image.
    private static final int ATLAS_SIZE = 1024;

    /// The maximum number of textures that fit into the atlas grid.
    private static final int MAX_TEXTURES = (ATLAS_SIZE / CELL_SIZE) * (ATLAS_SIZE / CELL_SIZE);

    /// Blocks that are rendered with an apparently solid model but still let neighboring faces
    /// show through, e.g. glass and leaves. They must not occlude adjacent blocks.
    private static final Set<String> NON_OPAQUE;

    static {
        Set<String> set = new HashSet<>();
        set.add("glass");
        set.add("tinted_glass");
        set.add("ice");
        set.add("packed_ice");
        set.add("blue_ice");
        set.add("frosted_ice");
        set.add("slime_block");
        set.add("honey_block");
        set.add("oak_leaves");
        set.add("spruce_leaves");
        set.add("birch_leaves");
        set.add("jungle_leaves");
        set.add("acacia_leaves");
        set.add("dark_oak_leaves");
        set.add("mangrove_leaves");
        set.add("azalea_leaves");
        set.add("flowering_azalea_leaves");
        for (String color : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            set.add(color + "_stained_glass");
            set.add(color + "_stained_glass_pane");
        }
        NON_OPAQUE = Set.copyOf(set);
    }

    /// The rotation origin of a block model variant, the center of the 16x16x16 model space.
    private static final float[] ROTATION_ORIGIN = {8.0f, 8.0f, 8.0f};

    private final Map<String, BlockDefinition> definitions = new HashMap<>();
    private final Map<String, BlockModel> models = new HashMap<>();
    private final Map<String, float[]> atlasUvs = new HashMap<>();
    private final Map<String, float[]> tints = new HashMap<>();
    private final Map<String, List<BlockDefinition.ModelVariant>> variantsCache = new HashMap<>();
    private final Map<String, Boolean> fullCubeCache = new HashMap<>();

    /// The atlas pixels, drawn on the background thread and uploaded later on the JavaFX thread.
    private final BufferedImage atlasBuffer;

    /// The JavaFX image uploaded from [atlasBuffer]; null until [uploadAtlas] runs.
    private @Nullable WritableImage atlasImage;

    private MinecraftResourcePack() {
        this.atlasBuffer = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    /// Loads the resources required to render the given block states from a client jar.
    ///
    /// @param versionJar the client jar of the game version, e.g. `versions/1.20.1/1.20.1.jar`
    /// @param palette    the block states that occur in the schematic
    /// @return the loaded resource pack; never null but may be empty
    /// @throws IOException when the jar cannot be opened or read
    public static MinecraftResourcePack load(Path versionJar, @Unmodifiable List<SchematicBlockState> palette) throws IOException {
        try (ZipFile zip = new ZipFile(versionJar.toFile())) {
            return load(zip, palette);
        }
    }

    private static MinecraftResourcePack load(ZipFile zip, List<SchematicBlockState> palette) {
        MinecraftResourcePack pack = new MinecraftResourcePack();

        // Load the block definitions of every block used by the schematic.
        for (SchematicBlockState state : palette) {
            if (state.isAir()) {
                continue;
            }
            if (pack.definitions.containsKey(state.getName())) {
                continue;
            }
            BlockDefinition definition = readDefinition(zip, stripNamespace(state.getName()));
            if (definition != null) {
                pack.definitions.put(state.getName(), definition);
            }
        }

        // Load every model referenced by the loaded definitions, including parent models.
        Set<String> loaded = new HashSet<>();
        List<String> queue = new ArrayList<>();
        for (BlockDefinition definition : pack.definitions.values()) {
            for (String modelId : definition.getAllModelIds()) {
                if (loaded.add(modelId)) {
                    queue.add(modelId);
                }
            }
        }
        for (int i = 0; i < queue.size(); i++) {
            loadModel(pack.models, zip, queue, i, loaded);
        }

        // Merge parent chains so every model is self-contained.
        for (BlockModel model : pack.models.values()) {
            model.flatten(pack.models);
        }

        // Compute the default tint of every block, then collect the textures needed by its
        // matched models and bake them into the atlas.
        Map<String, float[]> textures = new HashMap<>();
        for (SchematicBlockState state : palette) {
            if (state.isAir()) {
                continue;
            }
            BlockDefinition definition = pack.definitions.get(state.getName());
            if (definition == null) {
                continue;
            }
            float[] tint = defaultTint(state.getName());
            if (tint != null) {
                pack.tints.put(state.getName(), tint);
            }
            for (String modelId : definition.getAllModelIds()) {
                BlockModel model = pack.models.get(modelId);
                if (model == null) {
                    continue;
                }
                for (BlockModel.TextureRef ref : model.getTextureRefs()) {
                    textures.putIfAbsent(ref.path, EMPTY_UV);
                    if (ref.tintIndex >= 0 && tint != null) {
                        textures.putIfAbsent(ref.path + "@" + BlockModel.rgbHex(tint), EMPTY_UV);
                    }
                }
            }
        }

        pack.buildAtlas(zip, textures);
        return pack;
    }

    /// A sentinel value used to collect texture keys before their UV region is known.
    private static final float[] EMPTY_UV = new float[4];

    /// Returns whether no resources could be loaded at all.
    ///
    /// @return true when the pack has no block definitions
    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    /// Uploads the atlas pixels into a JavaFX image.
    ///
    /// JavaFX images are GPU-bound resources, so this must run on the JavaFX Application Thread.
    /// It is idempotent: calling it again has no effect.
    public void uploadAtlas() {
        if (atlasImage != null) {
            return;
        }
        WritableImage image = new WritableImage(ATLAS_SIZE, ATLAS_SIZE);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < ATLAS_SIZE; y++) {
            for (int x = 0; x < ATLAS_SIZE; x++) {
                writer.setArgb(x, y, atlasBuffer.getRGB(x, y));
            }
        }
        atlasImage = image;
    }

    /// Returns the atlas image containing all loaded block textures.
    ///
    /// The image is only available after [uploadAtlas] has been called on the JavaFX thread.
    ///
    /// @return the atlas image, or `null` before [uploadAtlas] runs
    public @Nullable WritableImage getAtlasImage() {
        return atlasImage;
    }

    /// Returns the visible faces of the given block state.
    ///
    /// Faces whose `cullface` direction is hidden by a neighboring full cube are skipped, and
    /// faces whose texture is missing from the atlas are skipped as well.
    ///
    /// @param state  the block state to render
    /// @param culling which directions are hidden by neighboring full cubes
    /// @return the generated faces in model space, or `null` when the block cannot be resolved
    public @Nullable List<ModelFace> getFaces(SchematicBlockState state, BlockCulling culling) {
        List<BlockDefinition.ModelVariant> variants = getVariants(state);
        if (variants == null) {
            return null;
        }
        float[] tint = tints.get(state.getName());
        List<ModelFace> result = new ArrayList<>();
        for (BlockDefinition.ModelVariant variant : variants) {
            BlockModel model = models.get(variant.model);
            if (model == null) {
                continue;
            }
            // Cull faces in model space: when the variant rotates the model, the world-space
            // culling must be rotated back into the model's coordinate system.
            BlockCulling modelCulling = (variant.x == 0 && variant.y == 0)
                    ? culling : culling.rotate(variant.x, variant.y);
            List<ModelFace> faces = model.getFaces(atlasUvs::get, tint, modelCulling);
            if (faces != null) {
                for (ModelFace face : faces) {
                    result.add(applyVariantRotation(face, variant.x, variant.y));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    /// Returns the model variants matching the block state, resolved once per state.
    private @Nullable List<BlockDefinition.ModelVariant> getVariants(SchematicBlockState state) {
        return variantsCache.computeIfAbsent(state.toString(), ignored -> {
            BlockDefinition definition = definitions.get(state.getName());
            if (definition == null) {
                return null;
            }
            List<BlockDefinition.ModelVariant> variants = definition.getModelVariants(state.getProperties());
            return variants == null || variants.isEmpty() ? null : List.copyOf(variants);
        });
    }

    /// Returns whether the given block state renders as an opaque full cube.
    ///
    /// Only full cubes occlude the faces of adjacent blocks.
    ///
    /// @param state the block state to test
    /// @return true when every matched model is a full cube and the block is opaque
    public boolean isFullCube(SchematicBlockState state) {
        Boolean cached = fullCubeCache.get(state.toString());
        if (cached != null) {
            return cached;
        }
        boolean result = computeFullCube(state);
        fullCubeCache.put(state.toString(), result);
        return result;
    }

    private boolean computeFullCube(SchematicBlockState state) {
        if (NON_OPAQUE.contains(stripNamespace(state.getName()))) {
            return false;
        }
        List<BlockDefinition.ModelVariant> variants = getVariants(state);
        if (variants == null) {
            return false;
        }
        for (BlockDefinition.ModelVariant variant : variants) {
            BlockModel model = models.get(variant.model);
            if (model == null || !model.isFullCube()) {
                return false;
            }
        }
        return true;
    }

    /// Reads and parses a `blockstates/<id>.json` file.
    private static @Nullable BlockDefinition readDefinition(ZipFile zip, String id) {
        ZipEntry entry = zip.getEntry(ASSETS + "blockstates/" + id + ".json");
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return BlockDefinition.parse(root);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /// Loads one model and queues its parents. `queue` is grown in place so every referenced
    /// model is eventually processed.
    private static void loadModel(Map<String, BlockModel> models, ZipFile zip, List<String> queue, int index, Set<String> loaded) {
        String modelId = queue.get(index);
        if (models.containsKey(modelId)) {
            return;
        }
        ZipEntry entry = zip.getEntry(ASSETS + "models/" + modelId + ".json");
        if (entry == null) {
            return; // missing model; nothing to load
        }
        BlockModel model;
        try (InputStream in = zip.getInputStream(entry)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            model = BlockModel.parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            return;
        }
        models.put(modelId, model);
        String parent = model.getParent();
        if (parent != null && !"builtin/generated".equals(parent) && !"builtin/entity".equals(parent) && loaded.add(parent)) {
            queue.add(parent);
        }
    }

    /// Decodes every needed texture and packs it into the atlas buffer.
    ///
    /// Textures that cannot be found in the jar are skipped entirely; the faces that would use
    /// them are omitted by [BlockModel#getFaces] and the block falls back to a solid cube.
    private void buildAtlas(ZipFile zip, Map<String, float[]> textures) {
        if (textures.isEmpty()) {
            return;
        }
        int index = 0;
        for (String key : textures.keySet()) {
            if (index >= MAX_TEXTURES) {
                break;
            }
            BufferedImage image = readTexture(zip, key);
            if (image == null) {
                continue;
            }
            int cellX = index % (ATLAS_SIZE / CELL_SIZE);
            int cellY = index / (ATLAS_SIZE / CELL_SIZE);
            float[] uv = new float[4];
            uv[0] = cellX * CELL_SIZE / (float) ATLAS_SIZE;
            uv[1] = cellY * CELL_SIZE / (float) ATLAS_SIZE;
            uv[2] = (cellX + 1) * CELL_SIZE / (float) ATLAS_SIZE;
            uv[3] = (cellY + 1) * CELL_SIZE / (float) ATLAS_SIZE;
            atlasUvs.put(key, uv);
            index++;

            int tint = parseTintSuffix(key);
            writeCell(atlasBuffer, image, cellX * CELL_SIZE, cellY * CELL_SIZE, tint);
        }
    }

    /// Reads and decodes a texture file, applying an optional tint suffix of the key.
    private static @Nullable BufferedImage readTexture(ZipFile zip, String key) {
        String path = stripTintSuffix(key);
        // Modern (1.13+) model textures already include the subfolder, e.g. "block/dirt".
        ZipEntry entry = zip.getEntry(ASSETS + "textures/" + path + ".png");
        if (entry == null) {
            // Older versions may reference textures by their bare name, e.g. "dirt".
            for (String prefix : new String[]{"block/", "blocks/", "item/"}) {
                entry = zip.getEntry(ASSETS + "textures/" + prefix + path + ".png");
                if (entry != null) {
                    break;
                }
            }
        }
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /// Draws one texture into a 16x16 atlas cell with nearest-neighbor scaling, applying the
    /// given tint. Animated textures (taller than 16 pixels) only contribute their first frame.
    private static void writeCell(BufferedImage target, BufferedImage image, int originX, int originY, int tint) {
        for (int y = 0; y < CELL_SIZE; y++) {
            int sy = Math.min(image.getHeight() - 1, y * image.getHeight() / CELL_SIZE);
            for (int x = 0; x < CELL_SIZE; x++) {
                int sx = Math.min(image.getWidth() - 1, x * image.getWidth() / CELL_SIZE);
                int argb = image.getRGB(sx, sy);
                if (tint != 0) {
                    argb = tintPixels(argb, tint);
                }
                target.setRGB(originX + x, originY + y, argb);
            }
        }
    }

    /// Multiplies the RGB channels of a pixel by a tint, keeping the alpha channel.
    private static int tintPixels(int argb, int tint) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 255;
        int g = ((argb >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (tint & 0xFF) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /// Removes the `@rrggbb` tint suffix of an atlas key.
    private static String stripTintSuffix(String key) {
        int at = key.indexOf('@');
        return at < 0 ? key : key.substring(0, at);
    }

    /// Parses the `@rrggbb` tint suffix of an atlas key into an RGB integer, or 0 when absent.
    private static int parseTintSuffix(String key) {
        int at = key.indexOf('@');
        if (at < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(key.substring(at + 1), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /// Rotates the faces of a model variant around the center of the block.
    private static ModelFace applyVariantRotation(ModelFace face, int x, int y) {
        if (x == 0 && y == 0) {
            return face;
        }
        float[] src = face.getPositions();
        float[] rotated = new float[12];
        float ox = ROTATION_ORIGIN[0], oy = ROTATION_ORIGIN[1], oz = ROTATION_ORIGIN[2];
        for (int i = 0; i < 4; i++) {
            float px = src[i * 3] - ox, py = src[i * 3 + 1] - oy, pz = src[i * 3 + 2] - oz;
            if (y != 0) {
                double rad = Math.toRadians(y);
                double c = Math.cos(rad), s = Math.sin(rad);
                float x1 = (float) (px * c + pz * s);
                pz = (float) (-px * s + pz * c);
                px = x1;
            }
            if (x != 0) {
                double rad = Math.toRadians(x);
                double c = Math.cos(rad), s = Math.sin(rad);
                float y1 = (float) (py * c - pz * s);
                pz = (float) (py * s + pz * c);
                py = y1;
            }
            rotated[i * 3] = px + ox;
            rotated[i * 3 + 1] = py + oy;
            rotated[i * 3 + 2] = pz + oz;
        }
        return new ModelFace(rotated, face.getUvs());
    }

    /// Returns the default foliage/water tint of a block, or `null` when the block is untinted.
    ///
    /// @param blockId the full block identifier
    /// @return normalized RGB tint, or null
    private static @Nullable float[] defaultTint(String blockId) {
        int rgb;
        switch (stripNamespace(blockId)) {
            case "grass_block":
            case "short_grass":
            case "grass":
            case "tall_grass":
            case "fern":
            case "large_fern":
            case "sugar_cane":
            case "vine":
            case "lily_pad":
            case "sweet_berry_bush":
            case "kelp":
            case "seagrass":
            case "tall_seagrass":
            case "pumpkin_stem":
            case "melon_stem":
            case "attached_pumpkin_stem":
            case "attached_melon_stem":
            case "cocoa":
            case "mangrove_propagule":
                rgb = 0x7CBB42;
                break;
            case "oak_leaves":
            case "jungle_leaves":
            case "acacia_leaves":
            case "dark_oak_leaves":
            case "azalea_leaves":
            case "flowering_azalea_leaves":
            case "mangrove_leaves":
            case "nether_wart":
                rgb = 0x48B518;
                break;
            case "spruce_leaves":
                rgb = 0x6E7C59;
                break;
            case "birch_leaves":
                rgb = 0x80A755;
                break;
            case "water":
            case "bubble_column":
                rgb = 0x3F76E4;
                break;
            case "redstone_wire":
                rgb = 0xFF0000;
                break;
            default:
                return null;
        }
        return new float[]{((rgb >>> 16) & 0xFF) / 255.0f, ((rgb >>> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f};
    }

    /// Removes the `minecraft:` namespace prefix from a block identifier.
    private static String stripNamespace(String name) {
        return name.startsWith(NAMESPACE) ? name.substring(NAMESPACE.length()) : name;
    }

    /// The resource root of the client jar.
    private static final String ASSETS = "assets/minecraft/";
}

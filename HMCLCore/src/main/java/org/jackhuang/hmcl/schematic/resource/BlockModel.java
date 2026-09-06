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
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// A parsed block model (a `models/block/*.json` file).
///
/// The model is a set of cuboid elements with textured faces in a 16x16x16 pixel space. Parent
/// models are merged recursively by [flatten] so that every model is self-contained afterwards.
@NotNullByDefault
final class BlockModel {

    /// A reference to a texture used by a face, together with its tint index.
    static final class TextureRef {
        final String path;
        final int tintIndex;

        TextureRef(String path, int tintIndex) {
            this.path = path;
            this.tintIndex = tintIndex;
        }
    }

    /// A cuboid element with optional rotation and per-direction faces.
    static final class Element {
        final float[] from;
        final float[] to;
        final @Nullable Rotation rotation;
        final Map<String, Face> faces;

        Element(float[] from, float[] to, @Nullable Rotation rotation, Map<String, Face> faces) {
            this.from = from;
            this.to = to;
            this.rotation = rotation;
            this.faces = faces;
        }
    }

    /// An element rotation around its origin, optionally rescaling the perpendicular axes.
    static final class Rotation {
        final float[] origin;
        final String axis;
        final float angle;
        final boolean rescale;

        Rotation(float[] origin, String axis, float angle, boolean rescale) {
            this.origin = origin;
            this.axis = axis;
            this.angle = angle;
            this.rescale = rescale;
        }
    }

    /// A single face of an element.
    static final class Face {
        final String texture;
        final @Nullable float[] uv;
        final @Nullable String cullface;
        final int rotation;
        final int tintIndex;

        Face(String texture, @Nullable float[] uv, @Nullable String cullface, int rotation, int tintIndex) {
            this.texture = texture;
            this.uv = uv;
            this.cullface = cullface;
            this.rotation = rotation;
            this.tintIndex = tintIndex;
        }
    }

    /// The texture coordinate index permutation for each of the four face rotation values.
    ///
    /// Each permutation maps the eight UV values (u0 v0 u1 v1 u2 v2 u3 v3) into the rotated order.
    private static final int[][] FACE_ROTATIONS = {
            {0, 3, 2, 3, 2, 1, 0, 1},
            {2, 3, 2, 1, 0, 1, 0, 3},
            {2, 1, 0, 1, 0, 3, 2, 3},
            {0, 1, 0, 3, 2, 3, 2, 1},
    };

    /// The scale applied to the perpendicular axes when an element rotation requests `rescale`.
    private static final double SQRT2 = Math.sqrt(2);

    /// The six cardinal directions of the element faces.
    private static final String[] DIRECTIONS = {"up", "down", "north", "south", "east", "west"};

    private @Nullable String parent;
    private final Map<String, String> textures = new LinkedHashMap<>();
    private final List<Element> elements = new ArrayList<>();

    private BlockModel() {
    }

    /// Parses a model from its JSON object.
    ///
    /// @param json the root object of a model file
    /// @return the parsed model, or `null` when the object is not a valid model
    static @Nullable BlockModel parse(JsonObject json) {
        if (json == null) {
            return null;
        }
        BlockModel model = new BlockModel();
        JsonElement parent = json.get("parent");
        if (parent != null && parent.isJsonPrimitive()) {
            model.parent = parent.getAsString();
        }
        JsonElement textures = json.get("textures");
        if (textures != null && textures.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : textures.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    model.textures.put(entry.getKey(), value.getAsString());
                } else if (value.isJsonObject() && value.getAsJsonObject().has("sprite")) {
                    model.textures.put(entry.getKey(), value.getAsJsonObject().get("sprite").getAsString());
                }
            }
        }
        JsonElement elements = json.get("elements");
        if (elements != null && elements.isJsonArray()) {
            for (JsonElement element : elements.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    Element parsed = parseElement(element.getAsJsonObject());
                    if (parsed != null) {
                        model.elements.add(parsed);
                    }
                }
            }
        }
        return model;
    }

    /// Returns the parent model id, or `null` when this model has no parent.
    @Nullable String getParent() {
        return parent;
    }

    /// Merges the parent chain into this model so that it becomes self-contained.
    ///
    /// Parent elements and textures are copied in when this model does not define them, following
    /// Minecraft's model inheritance rules.
    ///
    /// @param models the model registry used to resolve parents
    void flatten(Map<String, BlockModel> models) {
        if (parent == null || parent.isEmpty()) {
            return;
        }
        if ("builtin/generated".equals(parent) || "builtin/entity".equals(parent)) {
            parent = null;
            return;
        }
        BlockModel parentModel = models.get(parent);
        if (parentModel == null) {
            parent = null;
            return;
        }
        parentModel.flatten(models);
        if (elements.isEmpty() && !parentModel.elements.isEmpty()) {
            elements.addAll(parentModel.elements);
        }
        for (Map.Entry<String, String> entry : parentModel.textures.entrySet()) {
            textures.putIfAbsent(entry.getKey(), entry.getValue());
        }
        parent = null;
    }

    /// Returns all textures referenced by the faces of this model, resolved through the texture table.
    List<TextureRef> getTextureRefs() {
        List<TextureRef> refs = new ArrayList<>();
        for (Element element : elements) {
            for (Face face : element.faces.values()) {
                String path = resolveTexture(face.texture);
                if (!path.isEmpty()) {
                    refs.add(new TextureRef(path, face.tintIndex));
                }
            }
        }
        return refs;
    }

    /// Returns whether this model renders as a full opaque cube: every element covers the whole
    /// 16x16x16 space, has no rotation, and exposes cullfaces on all six sides.
    ///
    /// Full cubes act as opaque neighbors that hide the faces of adjacent blocks.
    boolean isFullCube() {
        if (elements.isEmpty()) {
            return false;
        }
        for (Element element : elements) {
            if (element.rotation != null) {
                return false;
            }
            for (int i = 0; i < 3; i++) {
                if (element.from[i] != 0.0f || element.to[i] != 16.0f) {
                    return false;
                }
            }
            for (String direction : DIRECTIONS) {
                Face face = element.faces.get(direction);
                if (face == null || face.cullface == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /// Generates the visible faces of this model.
    ///
    /// Faces whose `cullface` direction is hidden by `culling` are skipped, as are faces whose
    /// texture is missing from the atlas.
    ///
    /// @param uvProvider maps a texture key (a texture path, suffixed with `@tint` when tinted) to
    ///                   normalized atlas UVs, or `null` when the texture is unavailable
    /// @param tint       the RGB tint applied to `tintindex` faces, or `null` when no tint applies
    /// @param culling    which directions are hidden by neighboring opaque blocks
    /// @return the generated faces, or `null` when the model has no renderable faces
    @Nullable List<ModelFace> getFaces(Function<String, @Nullable float[]> uvProvider, @Nullable float[] tint, BlockCulling culling) {
        List<ModelFace> result = new ArrayList<>();
        for (Element element : elements) {
            collectElementFaces(element, uvProvider, tint, culling, result);
        }
        return result.isEmpty() ? null : result;
    }

    /// Collects the faces of a single element, applying its optional rotation.
    private void collectElementFaces(Element element, Function<String, @Nullable float[]> uvProvider,
                                     @Nullable float[] tint, BlockCulling culling, List<ModelFace> out) {
        float x0 = element.from[0], y0 = element.from[1], z0 = element.from[2];
        float x1 = element.to[0], y1 = element.to[1], z1 = element.to[2];

        List<float[]> vertsList = new ArrayList<>();
        List<float[]> uvsList = new ArrayList<>();

        // Each face is defined by its default UV coordinates (in 16-pixel space) and the four
        // vertices of its quad, with the winding order counter-clockwise from the outside.
        addElementFace(vertsList, uvsList, element, "up", uvProvider, tint,
                new float[]{x0, 16 - z1, x1, 16 - z0},
                new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0},
                culling);
        addElementFace(vertsList, uvsList, element, "down", uvProvider, tint,
                new float[]{16 - z1, 16 - x1, 16 - z0, 16 - x0},
                new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                culling);
        addElementFace(vertsList, uvsList, element, "south", uvProvider, tint,
                new float[]{x0, 16 - y1, x1, 16 - y0},
                new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                culling);
        addElementFace(vertsList, uvsList, element, "north", uvProvider, tint,
                new float[]{16 - x1, 16 - y1, 16 - x0, 16 - y0},
                new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
                culling);
        addElementFace(vertsList, uvsList, element, "east", uvProvider, tint,
                new float[]{16 - z1, 16 - y1, 16 - z0, 16 - y0},
                new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                culling);
        addElementFace(vertsList, uvsList, element, "west", uvProvider, tint,
                new float[]{z0, 16 - y1, z1, 16 - y0},
                new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                culling);

        if (element.rotation != null && !vertsList.isEmpty()) {
            for (float[] verts : vertsList) {
                for (int i = 0; i < 4; i++) {
                    float[] rotated = rotate(verts[i * 3], verts[i * 3 + 1], verts[i * 3 + 2], element.rotation);
                    verts[i * 3] = rotated[0];
                    verts[i * 3 + 1] = rotated[1];
                    verts[i * 3 + 2] = rotated[2];
                }
            }
        }

        for (int i = 0; i < vertsList.size(); i++) {
            out.add(new ModelFace(vertsList.get(i), uvsList.get(i)));
        }
    }

    /// Adds one face of an element when it exists, is not culled, and its texture is available.
    private void addElementFace(List<float[]> vertsList, List<float[]> uvsList, Element element, String direction,
                                Function<String, @Nullable float[]> uvProvider, @Nullable float[] tint,
                                float[] defaultUv, float[] verts, BlockCulling culling) {
        Face face = element.faces.get(direction);
        if (face == null) {
            return;
        }
        if (face.cullface != null && culling.get(face.cullface)) {
            return;
        }
        String path = resolveTexture(face.texture);
        if (path.isEmpty()) {
            return;
        }
        String key = face.tintIndex >= 0 && tint != null ? path + "@" + rgbHex(tint) : path;
        float[] texUv = uvProvider.apply(key);
        if (texUv == null) {
            return;
        }
        float u0 = texUv[0], v0 = texUv[1];
        float du = (texUv[2] - texUv[0]) / 16.0f;
        float dv = (texUv[3] - texUv[1]) / 16.0f;
        float[] uv = face.uv != null ? face.uv : defaultUv;
        float[] scaled = new float[4];
        for (int i = 0; i < 4; i++) {
            scaled[i] = uv[i] * (i % 2 == 0 ? du : dv);
        }
        int[] rotation = FACE_ROTATIONS[face.rotation % 4];
        float[] quadUv = new float[8];
        for (int i = 0; i < 4; i++) {
            quadUv[i * 2] = u0 + scaled[rotation[i * 2]];
            quadUv[i * 2 + 1] = v0 + scaled[rotation[i * 2 + 1]];
        }
        vertsList.add(verts);
        uvsList.add(quadUv);
    }

    /// Resolves a face texture reference through the texture table, removing any `#` indirections
    /// and namespace prefixes.
    private String resolveTexture(String textureRef) {
        String ref = textureRef;
        while (ref.startsWith("#")) {
            String value = textures.get(ref.substring(1));
            if (value == null || value.isEmpty()) {
                return "";
            }
            ref = value;
        }
        if (ref.startsWith("minecraft:")) {
            ref = ref.substring("minecraft:".length());
        }
        return ref;
    }

    /// Formats a normalized RGB tint as a zero-padded lowercase hex suffix for atlas texture keys.
    static String rgbHex(float[] tint) {
        int r = (int) (tint[0] * 255) & 0xFF;
        int g = (int) (tint[1] * 255) & 0xFF;
        int b = (int) (tint[2] * 255) & 0xFF;
        return String.format("%06x", (r << 16) | (g << 8) | b);
    }

    /// Rotates a point around the rotation origin.
    private static float[] rotate(float x, float y, float z, Rotation rotation) {
        float ox = rotation.origin[0], oy = rotation.origin[1], oz = rotation.origin[2];
        x -= ox;
        y -= oy;
        z -= oz;
        double rad = Math.toRadians(rotation.angle);
        double c = Math.cos(rad), s = Math.sin(rad);
        switch (rotation.axis) {
            case "x": {
                double y1 = y * c - z * s;
                z = (float) (y * s + z * c);
                y = (float) y1;
                break;
            }
            case "y": {
                double x1 = x * c + z * s;
                z = (float) (-x * s + z * c);
                x = (float) x1;
                break;
            }
            case "z": {
                double x1 = x * c - y * s;
                y = (float) (x * s + y * c);
                x = (float) x1;
                break;
            }
            default:
                break;
        }
        if (rotation.rescale) {
            switch (rotation.axis) {
                case "x":
                    y *= SQRT2;
                    z *= SQRT2;
                    break;
                case "y":
                    x *= SQRT2;
                    z *= SQRT2;
                    break;
                case "z":
                    x *= SQRT2;
                    y *= SQRT2;
                    break;
                default:
                    break;
            }
        }
        return new float[]{x + ox, y + oy, z + oz};
    }

    private static @Nullable Element parseElement(JsonObject json) {
        JsonElement from = json.get("from");
        JsonElement to = json.get("to");
        if (from == null || to == null || !from.isJsonArray() || !to.isJsonArray()) {
            return null;
        }
        float[] f = parseVec3(from);
        float[] t = parseVec3(to);
        if (f == null || t == null) {
            return null;
        }

        @Nullable Rotation rotation = null;
        JsonElement rotationElement = json.get("rotation");
        if (rotationElement != null && rotationElement.isJsonObject()) {
            JsonObject ro = rotationElement.getAsJsonObject();
            JsonElement originElement = ro.get("origin");
            JsonElement axisElement = ro.get("axis");
            if (originElement != null && originElement.isJsonArray() && axisElement != null && axisElement.isJsonPrimitive()) {
                float[] origin = parseVec3(originElement);
                if (origin != null) {
                    float angle = ro.has("angle") ? ro.get("angle").getAsFloat() : 0.0f;
                    boolean rescale = ro.has("rescale") && ro.get("rescale").getAsBoolean();
                    rotation = new Rotation(origin, axisElement.getAsString(), angle, rescale);
                }
            }
        }

        Map<String, Face> faces = new LinkedHashMap<>();
        JsonElement facesElement = json.get("faces");
        if (facesElement != null && facesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : facesElement.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    Face face = parseFace(entry.getValue().getAsJsonObject());
                    if (face != null) {
                        faces.put(entry.getKey(), face);
                    }
                }
            }
        }
        return new Element(f, t, rotation, faces);
    }

    private static @Nullable Face parseFace(JsonObject json) {
        JsonElement texture = json.get("texture");
        if (texture == null || !texture.isJsonPrimitive()) {
            return null;
        }
        @Nullable float[] uv = null;
        JsonElement uvElement = json.get("uv");
        if (uvElement != null && uvElement.isJsonArray()) {
            uv = parseVec4(uvElement);
        }
        String cullface = json.has("cullface") && json.get("cullface").isJsonPrimitive()
                ? json.get("cullface").getAsString() : null;
        int rotation = json.has("rotation") ? json.get("rotation").getAsInt() : 0;
        int tintIndex = json.has("tintindex") ? json.get("tintindex").getAsInt() : -1;
        return new Face(texture.getAsString(), uv, cullface, rotation, tintIndex);
    }

    /// Parses a JSON array of three numbers.
    private static @Nullable float[] parseVec3(JsonElement element) {
        if (!element.isJsonArray() || element.getAsJsonArray().size() < 3) {
            return null;
        }
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!element.getAsJsonArray().get(i).isJsonPrimitive()) {
                return null;
            }
            result[i] = element.getAsJsonArray().get(i).getAsFloat();
        }
        return result;
    }

    /// Parses a JSON array of four numbers.
    private static @Nullable float[] parseVec4(JsonElement element) {
        if (!element.isJsonArray() || element.getAsJsonArray().size() < 4) {
            return null;
        }
        float[] result = new float[4];
        for (int i = 0; i < 4; i++) {
            if (!element.getAsJsonArray().get(i).isJsonPrimitive()) {
                return null;
            }
            result[i] = element.getAsJsonArray().get(i).getAsFloat();
        }
        return result;
    }
}

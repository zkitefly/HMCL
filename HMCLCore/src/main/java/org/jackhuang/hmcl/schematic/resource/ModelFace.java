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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// A single quad generated from a Minecraft block model.
///
/// Positions are expressed in 16-pixel model space (0-16). They must be divided by 16 and offset
/// by the block position before being used in a mesh. Texture coordinates reference the texture
/// atlas of [MinecraftResourcePack] in normalized space.
@NotNullByDefault
public final class ModelFace {

    /// Four vertices in model space; an array of 12 floats, three per vertex.
    @Unmodifiable
    private final float[] positions;

    /// Four texture coordinates; an array of 8 floats, two per vertex.
    @Unmodifiable
    private final float[] uvs;

    private final float normalX;
    private final float normalY;
    private final float normalZ;

    /// Creates a face from its vertices and texture coordinates, computing the face normal.
    ///
    /// @param positions 12 floats, three per vertex; the winding order must be counter-clockwise
    ///                  when viewed from the outside
    /// @param uvs       8 floats, two per vertex
    ModelFace(float[] positions, float[] uvs) {
        this.positions = positions;
        this.uvs = uvs;
        float[] normal = computeNormal(positions);
        this.normalX = normal[0];
        this.normalY = normal[1];
        this.normalZ = normal[2];
    }

    /// Returns the four vertices of this face in model space.
    ///
    /// @return an array of 12 floats, three per vertex; do not modify it
    @Unmodifiable
    public float[] getPositions() {
        return positions;
    }

    /// Returns the four texture coordinates of this face.
    ///
    /// @return an array of 8 floats, two per vertex; do not modify it
    @Unmodifiable
    public float[] getUvs() {
        return uvs;
    }

    /// Returns the X component of the face normal.
    public float getNormalX() {
        return normalX;
    }

    /// Returns the Y component of the face normal.
    public float getNormalY() {
        return normalY;
    }

    /// Returns the Z component of the face normal.
    public float getNormalZ() {
        return normalZ;
    }

    /// Computes the normalized outward normal of a quad from its four vertices.
    private static float[] computeNormal(float[] p) {
        float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
        float bx = p[9] - p[0], by = p[10] - p[1], bz = p[11] - p[2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0.0f) {
            return new float[]{0.0f, 1.0f, 0.0f};
        }
        return new float[]{nx / length, ny / length, nz / length};
    }
}

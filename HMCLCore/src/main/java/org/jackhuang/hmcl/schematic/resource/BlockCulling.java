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

/// Tracks which directions of a block are hidden by opaque neighboring blocks.
///
/// The directions follow Minecraft's model convention: `up` and `down` are the Y axis, `north`
/// is -Z, `south` is +Z, `west` is -X and `east` is +X.
@NotNullByDefault
public final class BlockCulling {

    /// A culling set where nothing is culled.
    public static final BlockCulling NONE = new BlockCulling(false, false, false, false, false, false);

    /// The direction vectors of each axis in the order `up, down, north, south, east, west`.
    private static final float[][] DIRECTIONS = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0},
    };

    public final boolean up;
    public final boolean down;
    public final boolean north;
    public final boolean south;
    public final boolean east;
    public final boolean west;

    /// Creates a culling set.
    ///
    /// @param up    whether the up face is hidden
    /// @param down  whether the down face is hidden
    /// @param north whether the north face is hidden
    /// @param south whether the south face is hidden
    /// @param east  whether the east face is hidden
    /// @param west  whether the west face is hidden
    public BlockCulling(boolean up, boolean down, boolean north, boolean south, boolean east, boolean west) {
        this.up = up;
        this.down = down;
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
    }

    /// Returns whether the face facing `direction` is hidden.
    ///
    /// @param direction one of `up`, `down`, `north`, `south`, `east`, `west`
    public boolean get(String direction) {
        return switch (direction) {
            case "up" -> up;
            case "down" -> down;
            case "north" -> north;
            case "south" -> south;
            case "east" -> east;
            case "west" -> west;
            default -> false;
        };
    }

    /// Returns a culling set rotated by the given model variant rotation.
    ///
    /// A model variant can rotate the model around its center by multiples of 90 degrees. The
    /// culling directions must follow the rotation so that a face marked `cullface: north`
    /// checks the neighbor that faces north after the rotation.
    ///
    /// @param x the rotation around the X axis in degrees (0, 90, 180, 270)
    /// @param y the rotation around the Y axis in degrees (0, 90, 180, 270)
    /// @return the rotated culling set
    public BlockCulling rotate(int x, int y) {
        if ((x % 360) == 0 && (y % 360) == 0) {
            return this;
        }
        boolean[] values = {up, down, north, south, east, west};
        boolean[] rotated = new boolean[6];
        for (int i = 0; i < 6; i++) {
            float[] d = DIRECTIONS[i];
            float[] world = rotateDirection(d[0], d[1], d[2], x, y);
            rotated[i] = values[closestDirection(world)];
        }
        return new BlockCulling(rotated[0], rotated[1], rotated[2], rotated[3], rotated[4], rotated[5]);
    }

    /// Rotates a direction by the inverse of the model variant rotation: `-x` around the X axis,
    /// then `-y` around the Y axis.
    private static float[] rotateDirection(float x, float y, float z, int rx, int ry) {
        double ax = -Math.toRadians(rx);
        double ay = -Math.toRadians(ry);
        double c = Math.cos(ax), s = Math.sin(ax);
        double y1 = y * c - z * s;
        double z1 = y * s + z * c;
        c = Math.cos(ay);
        s = Math.sin(ay);
        double x2 = x * c + z1 * s;
        double z2 = -x * s + z1 * c;
        return new float[]{(float) x2, (float) y1, (float) z2};
    }

    /// Returns the index of the cardinal direction closest to the given vector.
    private static int closestDirection(float[] d) {
        int best = 0;
        float bestDot = -2.0f;
        for (int i = 0; i < 6; i++) {
            float dot = d[0] * DIRECTIONS[i][0] + d[1] * DIRECTIONS[i][1] + d[2] * DIRECTIONS[i][2];
            if (dot > bestDot) {
                bestDot = dot;
                best = i;
            }
        }
        return best;
    }
}

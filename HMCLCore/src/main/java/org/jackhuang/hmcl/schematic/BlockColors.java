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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/// Maps Minecraft block identifiers to base colors used by the schematic preview renderer.
///
/// The colors approximate the vanilla block colors so that a structure can be recognized at a glance.
/// Blocks without an entry fall back to a deterministic color derived from the block identifier.
@NotNullByDefault
public final class BlockColors {

    private static final String DEFAULT_NAMESPACE = "minecraft:";

    /// Unknown blocks derive their color from a stable hash so the same block always gets the same color.
    private static int fallbackColor(String name) {
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = 31 * hash + name.charAt(i);
        }
        float hue = ((hash >>> 8) & 0xFFFF) / 65535.0f;
        float saturation = 0.45f + ((hash >>> 24) & 0x1F) / 64.0f;
        float brightness = 0.55f + (hash & 0x1F) / 64.0f;
        return java.awt.Color.HSBtoRGB(hue, Math.min(saturation, 1.0f), Math.min(brightness, 1.0f)) | 0xFF000000;
    }

    private BlockColors() {
    }

    private static void put(Map<String, Integer> map, String name, int rgb) {
        map.put(name, rgb | 0xFF000000);
    }

    /// Returns the ARGB color for the given block identifier.
    ///
    /// @param blockId the full block identifier, e.g. `minecraft:stone`
    /// @return an ARGB color value (alpha is always fully opaque)
    public static int getColor(String blockId) {
        String key = blockId.startsWith(DEFAULT_NAMESPACE) ? blockId.substring(DEFAULT_NAMESPACE.length()) : blockId;
        Integer color = COLORS.get(key);
        return color != null ? color : fallbackColor(blockId);
    }

    private static final Map<String, Integer> COLORS;

    static {
        Map<String, Integer> c = new HashMap<>();

        // Stone & ores
        put(c, "stone", 0x8A8A8A);
        put(c, "granite", 0x97744E);
        put(c, "polished_granite", 0x9B7B53);
        put(c, "diorite", 0xBEBEBE);
        put(c, "polished_diorite", 0xC8C8C8);
        put(c, "andesite", 0x858585);
        put(c, "polished_andesite", 0x939393);
        put(c, "deepslate", 0x4F4F4F);
        put(c, "cobbled_deepslate", 0x4A4A4A);
        put(c, "polished_deepslate", 0x525252);
        put(c, "deepslate_bricks", 0x4C4C4C);
        put(c, "cracked_deepslate_bricks", 0x4C4C4C);
        put(c, "deepslate_tiles", 0x4A4A4A);
        put(c, "cracked_deepslate_tiles", 0x4A4A4A);
        put(c, "chiseled_deepslate", 0x4F4F4F);
        put(c, "cobblestone", 0x707070);
        put(c, "mossy_cobblestone", 0x6F7A5A);
        put(c, "stone_bricks", 0x7F7F7F);
        put(c, "mossy_stone_bricks", 0x74805E);
        put(c, "cracked_stone_bricks", 0x7C7C7C);
        put(c, "chiseled_stone_bricks", 0x828282);
        put(c, "smooth_stone", 0x8B8B8B);
        put(c, "bedrock", 0x3F3F3F);
        put(c, "tuff", 0x5F6060);
        put(c, "calcite", 0xC4C9C4);
        put(c, "dripstone_block", 0x9A7C5A);
        put(c, "pointed_dripstone", 0x8F7254);
        put(c, "amethyst_block", 0x8C5AC8);
        put(c, "budding_amethyst", 0x7D4FAF);
        put(c, "amethyst_cluster", 0x9372C8);

        put(c, "coal_ore", 0x5F5F5F);
        put(c, "deepslate_coal_ore", 0x3E3E3E);
        put(c, "iron_ore", 0xB08A68);
        put(c, "deepslate_iron_ore", 0x9A7E63);
        put(c, "copper_ore", 0x8FA36A);
        put(c, "deepslate_copper_ore", 0x7C8A5C);
        put(c, "gold_ore", 0xC8B05A);
        put(c, "deepslate_gold_ore", 0xA8904E);
        put(c, "redstone_ore", 0x8A3F3F);
        put(c, "deepslate_redstone_ore", 0x773434);
        put(c, "emerald_ore", 0x4FA84F);
        put(c, "deepslate_emerald_ore", 0x3F8C3F);
        put(c, "lapis_ore", 0x3457A3);
        put(c, "deepslate_lapis_ore", 0x2C4A8C);
        put(c, "diamond_ore", 0x63C8D8);
        put(c, "deepslate_diamond_ore", 0x53A8B8);
        put(c, "nether_quartz_ore", 0x7F6A5A);
        put(c, "nether_gold_ore", 0x8A5A3F);

        put(c, "raw_iron_block", 0xB8916E);
        put(c, "raw_gold_block", 0xC8A45A);
        put(c, "raw_copper_block", 0x9C6B52);
        put(c, "iron_block", 0xD8D8D8);
        put(c, "gold_block", 0xE8C83F);
        put(c, "diamond_block", 0x6FE8E8);
        put(c, "emerald_block", 0x3FC83F);
        put(c, "lapis_block", 0x2C4AA8);
        put(c, "redstone_block", 0xC81F1F);
        put(c, "netherite_block", 0x3F3A3A);
        put(c, "copper_block", 0xC87A5A);
        put(c, "exposed_copper", 0xA86E58);
        put(c, "weathered_copper", 0x6F9C8C);
        put(c, "oxidized_copper", 0x4A9C8C);
        put(c, "cut_copper", 0xC87A5A);
        put(c, "exposed_cut_copper", 0xA86E58);
        put(c, "weathered_cut_copper", 0x6F9C8C);
        put(c, "oxidized_cut_copper", 0x4A9C8C);
        put(c, "copper_grate", 0xC87A5A);

        // Dirt, sand & ground
        put(c, "dirt", 0x8A5A32);
        put(c, "coarse_dirt", 0x6F4F2C);
        put(c, "rooted_dirt", 0x8A5A32);
        put(c, "grass_block", 0x6FB83F);
        put(c, "podzol", 0x5A4A28);
        put(c, "mycelium", 0x7A7A6F);
        put(c, "mud", 0x4A4A5A);
        put(c, "clay", 0x9C7A70);
        put(c, "mud_bricks", 0x6E5C53);
        put(c, "packed_mud", 0x5A4C46);
        put(c, "gravel", 0x80807F);
        put(c, "sand", 0xD8CF9E);
        put(c, "red_sand", 0xB8803F);
        put(c, "sandstone", 0xC8B890);
        put(c, "smooth_sandstone", 0xC8B890);
        put(c, "cut_sandstone", 0xC8B890);
        put(c, "chiseled_sandstone", 0xC8B890);
        put(c, "red_sandstone", 0xB06B3F);
        put(c, "smooth_red_sandstone", 0xB06B3F);
        put(c, "cut_red_sandstone", 0xB06B3F);
        put(c, "chiseled_red_sandstone", 0xB06B3F);
        put(c, "suspicious_sand", 0xD8CF9E);
        put(c, "suspicious_gravel", 0x80807F);

        // Wood & planks
        put(c, "oak_log", 0x6B4F2B);
        put(c, "oak_wood", 0x6B4F2B);
        put(c, "stripped_oak_log", 0xA88A5A);
        put(c, "stripped_oak_wood", 0xA88A5A);
        put(c, "oak_planks", 0xA88A5A);
        put(c, "birch_log", 0xC8C4B0);
        put(c, "birch_wood", 0xC8C4B0);
        put(c, "stripped_birch_log", 0xC8C4B0);
        put(c, "birch_planks", 0xC8C4B0);
        put(c, "spruce_log", 0x3F2F22);
        put(c, "spruce_wood", 0x3F2F22);
        put(c, "stripped_spruce_log", 0x6B563F);
        put(c, "spruce_planks", 0x6B563F);
        put(c, "jungle_log", 0x5C4A2C);
        put(c, "jungle_wood", 0x5C4A2C);
        put(c, "stripped_jungle_log", 0x9C744A);
        put(c, "jungle_planks", 0x9C744A);
        put(c, "acacia_log", 0x5A5148);
        put(c, "acacia_wood", 0x5A5148);
        put(c, "stripped_acacia_log", 0xB06B3F);
        put(c, "acacia_planks", 0xB06B3F);
        put(c, "dark_oak_log", 0x3A2A1A);
        put(c, "dark_oak_wood", 0x3A2A1A);
        put(c, "stripped_dark_oak_log", 0x4F3F2C);
        put(c, "dark_oak_planks", 0x4F3F2C);
        put(c, "cherry_log", 0x4A2F33);
        put(c, "cherry_wood", 0x4A2F33);
        put(c, "stripped_cherry_log", 0xE0A5B8);
        put(c, "cherry_planks", 0xE0A5B8);
        put(c, "mangrove_log", 0x4F3528);
        put(c, "mangrove_wood", 0x4F3528);
        put(c, "stripped_mangrove_log", 0x7A5C44);
        put(c, "mangrove_planks", 0x7A5C44);
        put(c, "crimson_stem", 0x5A303C);
        put(c, "crimson_hyphae", 0x5A303C);
        put(c, "stripped_crimson_stem", 0x6A3C48);
        put(c, "crimson_planks", 0x6A3C48);
        put(c, "warped_stem", 0x3F5A4F);
        put(c, "warped_hyphae", 0x3F5A4F);
        put(c, "stripped_warped_stem", 0x3F6B5F);
        put(c, "warped_planks", 0x3F6B5F);
        put(c, "bamboo_planks", 0xA8B84A);
        put(c, "bamboo_mosaic", 0xA8B84A);
        put(c, "bamboo_block", 0x8A9C3F);
        put(c, "bamboo", 0x5F8A3F);
        put(c, "bookshelf", 0x8F6B47);
        put(c, "chiseled_bookshelf", 0x8F6B47);
        put(c, "crafting_table", 0x9C7A50);
        put(c, "fletching_table", 0xA88A5A);
        put(c, "cartography_table", 0xA88A5A);
        put(c, "smithing_table", 0x2F2F2F);
        put(c, "stonecutter", 0x808080);
        put(c, "grindstone", 0x808080);
        put(c, "loom", 0xA88A5A);
        put(c, "barrel", 0x8F6B47);
        put(c, "composter", 0x8F6B47);
        put(c, "note_block", 0x8F6B47);
        put(c, "jukebox", 0x8F6B47);
        put(c, "lectern", 0x8F6B47);
        put(c, "bee_nest", 0xC8A54A);
        put(c, "beehive", 0xC8A54A);
        put(c, "chest", 0x9C7A3F);
        put(c, "trapped_chest", 0x9C7A3F);
        put(c, "ender_chest", 0x2C2C3F);
        put(c, "decorated_pot", 0x9C5F4F);

        // Leaves & plants
        put(c, "oak_leaves", 0x59A628);
        put(c, "spruce_leaves", 0x3F7A2F);
        put(c, "birch_leaves", 0x7FAE3F);
        put(c, "jungle_leaves", 0x4F9C3F);
        put(c, "acacia_leaves", 0x7FAE3F);
        put(c, "dark_oak_leaves", 0x4A8A2F);
        put(c, "azalea_leaves", 0x4F8A3F);
        put(c, "flowering_azalea_leaves", 0x4F8A3F);
        put(c, "mangrove_leaves", 0x4F7A3F);
        put(c, "cherry_leaves", 0xE8A8C0);
        put(c, "azalea", 0x4F8A3F);
        put(c, "flowering_azalea", 0x4F8A3F);
        put(c, "moss_block", 0x5FA02F);
        put(c, "moss_carpet", 0x5FA02F);
        put(c, "vine", 0x5FA02F);
        put(c, "sugar_cane", 0x6FAE3F);
        put(c, "kelp", 0x3F7A2F);
        put(c, "seagrass", 0x3F7A3F);
        put(c, "grass", 0x6FB83F);
        put(c, "tall_grass", 0x6FB83F);
        put(c, "fern", 0x6FB83F);
        put(c, "large_fern", 0x6FB83F);
        put(c, "nether_wart_block", 0x6F2A3F);
        put(c, "warped_wart_block", 0x2F5A4F);
        put(c, "shroomlight", 0xE8A83F);
        put(c, "cactus", 0x4F8A3F);
        put(c, "cocoa", 0x6B4F2B);
        put(c, "sea_pickle", 0x4F8A3F);
        put(c, "chorus_flower", 0x9C7A9C);
        put(c, "chorus_plant", 0x7F5F7F);
        put(c, "wheat", 0xC8B84A);
        put(c, "carrots", 0xE8883F);
        put(c, "potatoes", 0x6F4A2C);
        put(c, "beetroots", 0xB02E26);
        put(c, "sweet_berry_bush", 0x3F8A3F);

        // Wool, concrete, terracotta & glass
        put(c, "white_wool", 0xE6E6E6);
        put(c, "orange_wool", 0xEB8844);
        put(c, "magenta_wool", 0xC3549D);
        put(c, "light_blue_wool", 0x6689D3);
        put(c, "yellow_wool", 0xE5E532);
        put(c, "lime_wool", 0x61CD41);
        put(c, "pink_wool", 0xD88AA1);
        put(c, "gray_wool", 0x414141);
        put(c, "light_gray_wool", 0x9A9A9A);
        put(c, "cyan_wool", 0x158991);
        put(c, "purple_wool", 0x813F9C);
        put(c, "blue_wool", 0x3D44AA);
        put(c, "brown_wool", 0x664C33);
        put(c, "green_wool", 0x56681D);
        put(c, "red_wool", 0xB02E26);
        put(c, "black_wool", 0x171615);

        put(c, "white_carpet", 0xE6E6E6);
        put(c, "orange_carpet", 0xEB8844);
        put(c, "magenta_carpet", 0xC3549D);
        put(c, "light_blue_carpet", 0x6689D3);
        put(c, "yellow_carpet", 0xE5E532);
        put(c, "lime_carpet", 0x61CD41);
        put(c, "pink_carpet", 0xD88AA1);
        put(c, "gray_carpet", 0x414141);
        put(c, "light_gray_carpet", 0x9A9A9A);
        put(c, "cyan_carpet", 0x158991);
        put(c, "purple_carpet", 0x813F9C);
        put(c, "blue_carpet", 0x3D44AA);
        put(c, "brown_carpet", 0x664C33);
        put(c, "green_carpet", 0x56681D);
        put(c, "red_carpet", 0xB02E26);
        put(c, "black_carpet", 0x171615);

        put(c, "white_concrete", 0xCFD4D3);
        put(c, "orange_concrete", 0xDD723B);
        put(c, "magenta_concrete", 0xA43B9B);
        put(c, "light_blue_concrete", 0x2D879C);
        put(c, "yellow_concrete", 0xE6D53A);
        put(c, "lime_concrete", 0x5DA12F);
        put(c, "pink_concrete", 0xC990A0);
        put(c, "gray_concrete", 0x34393D);
        put(c, "light_gray_concrete", 0x7D7D73);
        put(c, "cyan_concrete", 0x197982);
        put(c, "purple_concrete", 0x5B3D8C);
        put(c, "blue_concrete", 0x2F3292);
        put(c, "brown_concrete", 0x664332);
        put(c, "green_concrete", 0x556D1C);
        put(c, "red_concrete", 0x912D27);
        put(c, "black_concrete", 0x171716);

        put(c, "white_concrete_powder", 0xE0E0D8);
        put(c, "orange_concrete_powder", 0xE8B08A);
        put(c, "magenta_concrete_powder", 0xC890C0);
        put(c, "light_blue_concrete_powder", 0x8AB8D8);
        put(c, "yellow_concrete_powder", 0xE8E0A0);
        put(c, "lime_concrete_powder", 0xA8D89A);
        put(c, "pink_concrete_powder", 0xE0A8B8);
        put(c, "gray_concrete_powder", 0x808080);
        put(c, "light_gray_concrete_powder", 0xB8B8B0);
        put(c, "cyan_concrete_powder", 0x8AC0C0);
        put(c, "purple_concrete_powder", 0xA08AC0);
        put(c, "blue_concrete_powder", 0x7A8AC0);
        put(c, "brown_concrete_powder", 0xA08870);
        put(c, "green_concrete_powder", 0x8AA870);
        put(c, "red_concrete_powder", 0xB87870);
        put(c, "black_concrete_powder", 0x4A4A4A);

        put(c, "terracotta", 0x96604B);
        put(c, "white_terracotta", 0xC9B7A2);
        put(c, "orange_terracotta", 0xC4784A);
        put(c, "magenta_terracotta", 0xA45899);
        put(c, "light_blue_terracotta", 0x6E87A5);
        put(c, "yellow_terracotta", 0xB89D58);
        put(c, "lime_terracotta", 0x7A8648);
        put(c, "pink_terracotta", 0xA77C7C);
        put(c, "gray_terracotta", 0x746D62);
        put(c, "light_gray_terracotta", 0x8A8278);
        put(c, "cyan_terracotta", 0x7C7D70);
        put(c, "purple_terracotta", 0x76546B);
        put(c, "blue_terracotta", 0x655F83);
        put(c, "brown_terracotta", 0x734F3F);
        put(c, "green_terracotta", 0x58593D);
        put(c, "red_terracotta", 0x97544A);
        put(c, "black_terracotta", 0x372420);

        put(c, "glass", 0xE0F0F0);
        put(c, "tinted_glass", 0x3F4A4F);
        put(c, "white_stained_glass", 0xE6F0F0);
        put(c, "orange_stained_glass", 0xF0C9A0);
        put(c, "magenta_stained_glass", 0xD8A0C8);
        put(c, "light_blue_stained_glass", 0xA8D0E8);
        put(c, "yellow_stained_glass", 0xE8E0A0);
        put(c, "lime_stained_glass", 0xB8E0A0);
        put(c, "pink_stained_glass", 0xE8B8C8);
        put(c, "gray_stained_glass", 0x808890);
        put(c, "light_gray_stained_glass", 0xB8C0C8);
        put(c, "cyan_stained_glass", 0xA8E0E0);
        put(c, "purple_stained_glass", 0xB8A0D8);
        put(c, "blue_stained_glass", 0xA0B0E8);
        put(c, "brown_stained_glass", 0xB8A088);
        put(c, "green_stained_glass", 0xA8D0A0);
        put(c, "red_stained_glass", 0xE8A0A0);
        put(c, "black_stained_glass", 0x505860);
        put(c, "glass_pane", 0xE0F0F0);
        put(c, "white_stained_glass_pane", 0xE6F0F0);
        put(c, "orange_stained_glass_pane", 0xF0C9A0);
        put(c, "magenta_stained_glass_pane", 0xD8A0C8);
        put(c, "light_blue_stained_glass_pane", 0xA8D0E8);
        put(c, "yellow_stained_glass_pane", 0xE8E0A0);
        put(c, "lime_stained_glass_pane", 0xB8E0A0);
        put(c, "pink_stained_glass_pane", 0xE8B8C8);
        put(c, "gray_stained_glass_pane", 0x808890);
        put(c, "light_gray_stained_glass_pane", 0xB8C0C8);
        put(c, "cyan_stained_glass_pane", 0xA8E0E0);
        put(c, "purple_stained_glass_pane", 0xB8A0D8);
        put(c, "blue_stained_glass_pane", 0xA0B0E8);
        put(c, "brown_stained_glass_pane", 0xB8A088);
        put(c, "green_stained_glass_pane", 0xA8D0A0);
        put(c, "red_stained_glass_pane", 0xE8A0A0);
        put(c, "black_stained_glass_pane", 0x505860);

        // Nether, End & other structures
        put(c, "netherrack", 0x6F3F3F);
        put(c, "nether_bricks", 0x2F1A1A);
        put(c, "cracked_nether_bricks", 0x2F1A1A);
        put(c, "chiseled_nether_bricks", 0x2F1A1A);
        put(c, "red_nether_bricks", 0x3F1F1F);
        put(c, "blackstone", 0x2F2525);
        put(c, "polished_blackstone", 0x352B2B);
        put(c, "polished_blackstone_bricks", 0x352B2B);
        put(c, "cracked_polished_blackstone_bricks", 0x352B2B);
        put(c, "chiseled_polished_blackstone", 0x352B2B);
        put(c, "gilded_blackstone", 0x3A2A20);
        put(c, "basalt", 0x4A4A4A);
        put(c, "polished_basalt", 0x525252);
        put(c, "smooth_basalt", 0x4F4F4F);
        put(c, "obsidian", 0x1A1422);
        put(c, "crying_obsidian", 0x2A1F3F);
        put(c, "respawn_anchor", 0x3F2A3F);
        put(c, "ancient_debris", 0x4F3A2C);
        put(c, "quartz_block", 0x8A8A8A);
        put(c, "quartz_pillar", 0x8A8A8A);
        put(c, "smooth_quartz", 0x8A8A8A);
        put(c, "chiseled_quartz_block", 0x8A8A8A);
        put(c, "quartz_bricks", 0x8A8A8A);
        put(c, "prismarine", 0x5A9C8A);
        put(c, "prismarine_bricks", 0x5A9C8A);
        put(c, "dark_prismarine", 0x2F4A44);
        put(c, "sea_lantern", 0xC8E8F0);
        put(c, "purpur_block", 0x9C7A9C);
        put(c, "purpur_pillar", 0x9C7A9C);
        put(c, "purpur_slab", 0x9C7A9C);
        put(c, "end_stone", 0xD8E8A0);
        put(c, "end_stone_bricks", 0xD8E8A0);
        put(c, "end_rod", 0xC8C8C8);
        put(c, "magma_block", 0xB05A3F);
        put(c, "glowstone", 0xE8D57A);
        put(c, "soul_sand", 0x3F3226);
        put(c, "soul_soil", 0x332B21);
        put(c, "bone_block", 0xD8D8C8);
        put(c, "hay_block", 0xA88A2C);
        put(c, "dried_kelp_block", 0x2A3A2A);
        put(c, "target", 0xE8D8C8);
        put(c, "honeycomb_block", 0xE8A83F);
        put(c, "honey_block", 0xE8B84A);
        put(c, "slime_block", 0x6FC846);
        put(c, "sponge", 0xB8C838);
        put(c, "wet_sponge", 0x9CB828);
        put(c, "tnt", 0xE84428);
        put(c, "snow_block", 0xFFFFFF);
        put(c, "snow", 0xF0F0F0);
        put(c, "powder_snow", 0xF0F0F0);
        put(c, "ice", 0x9CB8F0);
        put(c, "packed_ice", 0x7F9CE0);
        put(c, "blue_ice", 0x4A6FBF);
        put(c, "frosted_ice", 0x9CB8F0);
        put(c, "pumpkin", 0xD87F2C);
        put(c, "carved_pumpkin", 0xD87F2C);
        put(c, "jack_o_lantern", 0xD87F2C);
        put(c, "melon", 0x5A8A2C);
        put(c, "brown_mushroom_block", 0x6B4F2B);
        put(c, "red_mushroom_block", 0xA03F2C);
        put(c, "mushroom_stem", 0xC8C8C8);
        put(c, "scaffolding", 0xC8C0A0);
        put(c, "lodestone", 0x8A8A8A);

        // Light, redstone & mechanisms
        put(c, "water", 0x3F76E4);
        put(c, "flowing_water", 0x3F76E4);
        put(c, "lava", 0xE84A12);
        put(c, "flowing_lava", 0xE84A12);
        put(c, "redstone_wire", 0xCC0000);
        put(c, "redstone_torch", 0xCC0000);
        put(c, "redstone_wall_torch", 0xCC0000);
        put(c, "repeater", 0x8A8A8A);
        put(c, "comparator", 0x8A8A8A);
        put(c, "redstone_lamp", 0xE8A04A);
        put(c, "observer", 0x5A5A5A);
        put(c, "piston", 0x5A5A5A);
        put(c, "sticky_piston", 0x6A8A5A);
        put(c, "piston_head", 0x5A5A5A);
        put(c, "moving_piston", 0x5A5A5A);
        put(c, "dispenser", 0x5F5F5F);
        put(c, "dropper", 0x5F5F5F);
        put(c, "furnace", 0x5F5F5F);
        put(c, "blast_furnace", 0x4A4A4A);
        put(c, "smoker", 0x4A4A4A);
        put(c, "torch", 0xE8C84A);
        put(c, "wall_torch", 0xE8C84A);
        put(c, "soul_torch", 0x5AC8C8);
        put(c, "lantern", 0xD8C84A);
        put(c, "soul_lantern", 0x5AC8C8);
        put(c, "campfire", 0x5A4A2A);
        put(c, "soul_campfire", 0x3A4A4A);
        put(c, "fire", 0xE8A020);
        put(c, "soul_fire", 0x5AC8C8);
        put(c, "lever", 0x7A7A5A);
        put(c, "stone_button", 0x8A8A8A);
        put(c, "oak_button", 0xA88A5A);
        put(c, "stone_pressure_plate", 0x8A8A8A);
        put(c, "oak_pressure_plate", 0xA88A5A);
        put(c, "light_weighted_pressure_plate", 0xC8A45A);
        put(c, "heavy_weighted_pressure_plate", 0xC8C8C8);
        put(c, "tripwire", 0x8A8A8A);
        put(c, "tripwire_hook", 0x8A8A8A);
        put(c, "rail", 0x8A8A8A);
        put(c, "powered_rail", 0x9C5F3F);
        put(c, "detector_rail", 0x6B6B6B);
        put(c, "activator_rail", 0x9C6B6B);
        put(c, "iron_bars", 0xA8A8A8);
        put(c, "iron_door", 0x9C9C9C);
        put(c, "iron_trapdoor", 0x9C9C9C);
        put(c, "chain", 0x9C9C9C);
        put(c, "oak_door", 0xA88A5A);
        put(c, "oak_trapdoor", 0xA88A5A);
        put(c, "spruce_door", 0x6B563F);
        put(c, "spruce_trapdoor", 0x6B563F);
        put(c, "birch_door", 0xC8C4B0);
        put(c, "birch_trapdoor", 0xC8C4B0);
        put(c, "jungle_door", 0x9C744A);
        put(c, "jungle_trapdoor", 0x9C744A);
        put(c, "acacia_door", 0xB06B3F);
        put(c, "acacia_trapdoor", 0xB06B3F);
        put(c, "dark_oak_door", 0x4F3F2C);
        put(c, "dark_oak_trapdoor", 0x4F3F2C);
        put(c, "mangrove_door", 0x7A5C44);
        put(c, "mangrove_trapdoor", 0x7A5C44);
        put(c, "cherry_door", 0xE0A5B8);
        put(c, "cherry_trapdoor", 0xE0A5B8);
        put(c, "bamboo_door", 0xA8B84A);
        put(c, "bamboo_trapdoor", 0xA8B84A);
        put(c, "crimson_door", 0x6A3C48);
        put(c, "crimson_trapdoor", 0x6A3C48);
        put(c, "warped_door", 0x3F6B5F);
        put(c, "warped_trapdoor", 0x3F6B5F);
        put(c, "crafter", 0x5A5A5A);
        put(c, "light", 0xE8D57A);
        put(c, "sculk", 0x0F2F2F);
        put(c, "sculk_catalyst", 0x0F3F3F);
        put(c, "sculk_sensor", 0x0F3F3F);
        put(c, "sculk_shrieker", 0x0F2F2F);
        put(c, "sculk_vein", 0x0F2F2F);
        put(c, "trial_spawner", 0x3F3A2A);
        put(c, "vault", 0x3F3A2A);
        put(c, "spawner", 0x1A1A2A);
        put(c, "anvil", 0x3F3F3F);
        put(c, "chipped_anvil", 0x3F3F3F);
        put(c, "damaged_anvil", 0x3F3F3F);
        put(c, "beacon", 0x59C8C8);
        put(c, "conduit", 0x2C4A8C);
        put(c, "end_portal_frame", 0x3F8A3F);
        put(c, "dragon_egg", 0x2A1A2A);

        // Coral
        put(c, "tube_coral_block", 0x3F6FD8);
        put(c, "brain_coral_block", 0xD85F8A);
        put(c, "bubble_coral_block", 0x8A5FD8);
        put(c, "fire_coral_block", 0xC83F3F);
        put(c, "horn_coral_block", 0xD8D83F);
        put(c, "dead_tube_coral_block", 0x8A8A8A);
        put(c, "dead_brain_coral_block", 0x8A8A8A);
        put(c, "dead_bubble_coral_block", 0x8A8A8A);
        put(c, "dead_fire_coral_block", 0x8A8A8A);
        put(c, "dead_horn_coral_block", 0x8A8A8A);
        put(c, "tube_coral", 0x3F6FD8);
        put(c, "brain_coral", 0xD85F8A);
        put(c, "bubble_coral", 0x8A5FD8);
        put(c, "fire_coral", 0xC83F3F);
        put(c, "horn_coral", 0xD8D83F);
        put(c, "tube_coral_fan", 0x3F6FD8);
        put(c, "brain_coral_fan", 0xD85F8A);
        put(c, "bubble_coral_fan", 0x8A5FD8);
        put(c, "fire_coral_fan", 0xC83F3F);
        put(c, "horn_coral_fan", 0xD8D83F);

        // Shulker boxes
        put(c, "shulker_box", 0x8F5B4F);
        put(c, "white_shulker_box", 0xDCDCD6);
        put(c, "orange_shulker_box", 0xC06328);
        put(c, "magenta_shulker_box", 0xB24C99);
        put(c, "light_blue_shulker_box", 0x6C9AC5);
        put(c, "yellow_shulker_box", 0xC5A83B);
        put(c, "lime_shulker_box", 0x7AB336);
        put(c, "pink_shulker_box", 0xD8A7A7);
        put(c, "gray_shulker_box", 0x404040);
        put(c, "light_gray_shulker_box", 0x909090);
        put(c, "cyan_shulker_box", 0x138B93);
        put(c, "purple_shulker_box", 0x7C4F91);
        put(c, "blue_shulker_box", 0x2E3C9B);
        put(c, "brown_shulker_box", 0x604A32);
        put(c, "green_shulker_box", 0x54652F);
        put(c, "red_shulker_box", 0xA32A24);
        put(c, "black_shulker_box", 0x171716);

        COLORS = Collections.unmodifiableMap(c);
    }
}

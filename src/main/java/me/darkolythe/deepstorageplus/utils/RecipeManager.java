package me.darkolythe.deepstorageplus.utils;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.io.File;
import java.util.*;

public class RecipeManager {

    private final DeepStoragePlus main;
    private final ItemList itemList;
    private final File recipesFile;
    private final List<String> registeredRecipeKeys = new ArrayList<>();
    private final Map<String, RecipeDefinition> loadedRecipes = new LinkedHashMap<>();

    public RecipeManager(DeepStoragePlus plugin, ItemList itemList) {
        this.main = plugin;
        this.itemList = itemList;
        this.recipesFile = new File(main.getDataFolder(), "recipes.yml");
        ensureRecipesFile();
        reloadRecipes();
    }

    public void reloadRecipes() {
        for (String key : registeredRecipeKeys) {
            Bukkit.removeRecipe(new NamespacedKey(main, key));
        }
        registeredRecipeKeys.clear();
        loadedRecipes.clear();

        loadedRecipes.putAll(loadConfiguredRecipes());
        for (RecipeDefinition definition : loadedRecipes.values()) {
            registerRecipe(definition.key(), definition.result(), definition.rows()[0], definition.rows()[1], definition.rows()[2], definition.choices());
        }

        main.getLogger().info("Loaded " + loadedRecipes.size() + " recipes from recipes.yml");
    }

    public List<RecipeDefinition> getRecipesByCategory(String category) {
        List<RecipeDefinition> result = new ArrayList<>();
        for (RecipeDefinition definition : loadedRecipes.values()) {
            if (definition.category().equalsIgnoreCase(category)) {
                result.add(definition);
            }
        }
        return result;
    }

    private void ensureRecipesFile() {
        if (!recipesFile.exists()) {
            main.saveResource("recipes.yml", false);
        }
    }

    private Map<String, RecipeDefinition> loadConfiguredRecipes() {
        Map<String, RecipeDefinition> recipes = new LinkedHashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection root = yaml.getConfigurationSection("recipes");
        if (root == null) {
            main.getLogger().warning("recipes.yml is missing 'recipes:' root section.");
            return recipes;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String category = section.getString("category", "tools").toLowerCase(Locale.ROOT);
            String resultToken = section.getString("result");
            ItemStack result = resolveItemToken(resultToken);
            if (result == null) {
                main.getLogger().warning("Skipping recipe '" + key + "': invalid result '" + resultToken + "'.");
                continue;
            }

            String[] rows = normalizeShape(section.getStringList("shape"));
            ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
            if (ingredientsSection == null) {
                main.getLogger().warning("Skipping recipe '" + key + "': missing ingredients.");
                continue;
            }

            Map<Character, RecipeChoice> choices = new HashMap<>();
            Map<Character, ItemStack> ingredientItems = new HashMap<>();
            boolean invalidIngredient = false;
            for (String symbolKey : ingredientsSection.getKeys(false)) {
                if (symbolKey.length() != 1) {
                    main.getLogger().warning("Skipping ingredient in recipe '" + key + "': symbol must be one character.");
                    invalidIngredient = true;
                    break;
                }
                char symbol = symbolKey.charAt(0);
                String ingredientToken = ingredientsSection.getString(symbolKey);
                ItemStack ingredientItem = resolveItemToken(ingredientToken);
                RecipeChoice choice = resolveChoiceToken(ingredientToken);
                if (ingredientItem == null || choice == null) {
                    main.getLogger().warning("Skipping recipe '" + key + "': invalid ingredient '" + symbolKey + "'='" + ingredientToken + "'.");
                    invalidIngredient = true;
                    break;
                }
                ingredientItems.put(symbol, ingredientItem);
                choices.put(symbol, choice);
            }

            if (invalidIngredient) {
                continue;
            }

            recipes.put(key, new RecipeDefinition(key, category, result, rows, ingredientItems, choices));
        }

        return recipes;
    }

    private String[] normalizeShape(List<String> shape) {
        String[] rows = new String[]{"   ", "   ", "   "};
        for (int i = 0; i < 3; i++) {
            if (shape != null && i < shape.size() && shape.get(i) != null) {
                String row = shape.get(i);
                rows[i] = row.length() >= 3 ? row.substring(0, 3) : String.format("%-3s", row);
            }
        }
        return rows;
    }

    private ItemStack resolveItemToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String normalized = token.trim();
        if (normalized.toLowerCase(Locale.ROOT).startsWith("item:")) {
            normalized = normalized.substring(5);
        }

        Optional<ItemStack> pluginItem = itemList.getItem(normalized);
        if (pluginItem.isPresent()) {
            return pluginItem.get().clone();
        }

        Material material = ItemList.parseConfiguredMaterial(normalized);
        if (material == null || material == Material.AIR) {
            return null;
        }
        return new ItemStack(material);
    }

    private RecipeChoice resolveChoiceToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String normalized = token.trim();
        boolean forcePluginItem = normalized.toLowerCase(Locale.ROOT).startsWith("item:");
        if (forcePluginItem) {
            normalized = normalized.substring(5);
        }

        Optional<ItemStack> pluginItem = itemList.getItem(normalized);
        if (pluginItem.isPresent()) {
            return exact(pluginItem.get());
        }

        if (forcePluginItem) {
            return null;
        }

        Material material = ItemList.parseConfiguredMaterial(normalized);
        if (material == null || material == Material.AIR) {
            return null;
        }

        return new RecipeChoice.MaterialChoice(material);
    }

    private void registerRecipe(String key, ItemStack result, String row1, String row2, String row3, Map<Character, RecipeChoice> ingredients) {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(main, key), result.clone());
        recipe.shape(row1, row2, row3);
        for (Map.Entry<Character, RecipeChoice> entry : ingredients.entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        Bukkit.getServer().addRecipe(recipe);
        registeredRecipeKeys.add(key);
    }


    private static RecipeChoice exact(ItemStack item) {
        return new RecipeChoice.ExactChoice(item.clone());
    }

    public record RecipeDefinition(
            String key,
            String category,
            ItemStack result,
            String[] rows,
            Map<Character, ItemStack> ingredientItems,
            Map<Character, RecipeChoice> choices
    ) {
    }
}

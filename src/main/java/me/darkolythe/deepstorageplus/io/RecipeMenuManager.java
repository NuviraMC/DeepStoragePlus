package me.darkolythe.deepstorageplus.io;

import me.darkolythe.deepstorageplus.DeepStoragePlus;
import me.darkolythe.deepstorageplus.utils.RecipeManager;
import me.darkolythe.deepstorageplus.utils.ItemList;
import me.darkolythe.deepstorageplus.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeMenuManager implements Listener {

    private final DeepStoragePlus plugin;
    private final NamespacedKey actionKey;
    private final RecipeManager recipeManager;

    public RecipeMenuManager(DeepStoragePlus plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "recipe_menu_action");
        this.recipeManager = recipeManager;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, getMainSize(), color(text("recipe_menu_main_title", "&3DSP Recipes")));
        fillWithPane(inv);

        addCategoryButton(inv, RecipeCategory.CELLS);
        addCategoryButton(inv, RecipeCategory.CONTAINERS);
        addCategoryButton(inv, RecipeCategory.TOOLS);
        addCategoryButton(inv, RecipeCategory.WIRELESS);

        player.openInventory(inv);
    }

    private void openCategoryMenu(Player player, RecipeCategory category, int page) {
        Inventory inv = Bukkit.createInventory(null, getCategorySize(), color(text("recipe_menu_category_title", "&3DSP Recipes: %category%").replace("%category%", category.displayName(this))));
        fillWithPane(inv);

        List<RecipePreview> recipes = recipesFor(category);
        List<Integer> slots = getIntList("recipe-menu.category.recipe-slots", Arrays.asList(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        ));

        int pageSize = Math.max(1, slots.size());
        int maxPage = Math.max(0, (recipes.size() - 1) / pageSize);
        int currentPage = Math.clamp(page, 0, maxPage);
        int start = currentPage * pageSize;

        for (int i = 0; i < pageSize && start + i < recipes.size() && i < slots.size(); i++) {
            RecipePreview preview = recipes.get(start + i);
            inv.setItem(slots.get(i), createRecipePreviewItem(preview, category, currentPage));
        }

        int backSlot = plugin.getConfig().getInt("recipe-menu.category.back-slot", 49);
        int prevSlot = plugin.getConfig().getInt("recipe-menu.category.prev-slot", 45);
        int nextSlot = plugin.getConfig().getInt("recipe-menu.category.next-slot", 53);

        inv.setItem(backSlot, createActionItem(
                resolveMenuMaterial("recipe-menu.category.back-material", Material.ARROW),
                color(text("recipe_menu_back_button", "&eBack")),
                "main",
                color(text("recipe_menu_back_lore", "&7Return to categories"))
        ));

        if (currentPage > 0) {
            inv.setItem(prevSlot, createActionItem(
                    resolveMenuMaterial("recipe-menu.category.prev-material", Material.SPECTRAL_ARROW),
                    color(text("recipe_menu_prev_button", "&ePrevious Page")),
                    "cat:" + category.id + ":" + (currentPage - 1),
                    color(text("recipe_menu_prev_lore", "&7Go to previous page"))
            ));
        }

        if (currentPage < maxPage) {
            inv.setItem(nextSlot, createActionItem(
                    resolveMenuMaterial("recipe-menu.category.next-material", Material.ARROW),
                    color(text("recipe_menu_next_button", "&eNext Page")),
                    "cat:" + category.id + ":" + (currentPage + 1),
                    color(text("recipe_menu_next_lore", "&7Go to next page"))
            ));
        }

        player.openInventory(inv);
    }

    private void openDetailMenu(Player player, RecipeCategory category, RecipePreview preview, int page) {
        String title = text("recipe_menu_detail_title", "&3Recipe: %recipe%")
                .replace("%recipe%", ChatColor.stripColor(preview.getDisplayName()));
        Inventory inv = Bukkit.createInventory(null, getDetailSize(), color(title));
        fillWithPane(inv);

        List<Integer> gridSlots = getIntList("recipe-menu.detail.grid-slots", Arrays.asList(10, 11, 12, 19, 20, 21, 28, 29, 30));
        int resultSlot = plugin.getConfig().getInt("recipe-menu.detail.result-slot", 24);
        int infoSlot = plugin.getConfig().getInt("recipe-menu.detail.info-slot", 15);
        int backSlot = plugin.getConfig().getInt("recipe-menu.detail.back-slot", 49);

        Map<Character, ItemStack> ingredientItems = preview.ingredientItems();
        for (int i = 0; i < Math.min(9, gridSlots.size()); i++) {
            int row = i / 3;
            int col = i % 3;
            char symbol = preview.rows[row].charAt(col);
            if (symbol == ' ') {
                continue;
            }
            ItemStack ingredient = ingredientItems.get(symbol);
            if (ingredient != null && ingredient.getType() != Material.AIR) {
                inv.setItem(gridSlots.get(i), ingredient.clone());
            }
        }

        inv.setItem(resultSlot, preview.result.clone());

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(color(text("recipe_menu_detail_info_title", "&bRecipe Details")));
            List<String> lore = new ArrayList<>();
            lore.add(color(text("recipe_menu_pattern_label", "&7Pattern:")));
            lore.add(ChatColor.DARK_GRAY + formatRow(preview.rows[0]));
            lore.add(ChatColor.DARK_GRAY + formatRow(preview.rows[1]));
            lore.add(ChatColor.DARK_GRAY + formatRow(preview.rows[2]));
            lore.add("");
            lore.add(color(text("recipe_menu_ingredients_label", "&7Ingredients:")));
            lore.addAll(preview.legendLines(this));
            infoMeta.setLore(lore);
            infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(infoSlot, info);

        inv.setItem(backSlot, createActionItem(
                resolveMenuMaterial("recipe-menu.detail.back-material", Material.ARROW),
                color(text("recipe_menu_back_button", "&eBack")),
                "cat:" + category.id + ":" + page,
                color(text("recipe_menu_detail_back_lore", "&7Return to recipe list"))
        ));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isRecipeMenu(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isEmpty()) {
            return;
        }

        if ("main".equals(action)) {
            openMainMenu(player);
            return;
        }

        if (action.startsWith("cat:")) {
            String[] parts = action.split(":");
            if (parts.length >= 2) {
                RecipeCategory category = RecipeCategory.fromId(parts[1]);
                int page = parts.length >= 3 ? parseInt(parts[2]) : 0;
                if (category != null) {
                    openCategoryMenu(player, category, page);
                }
            }
            return;
        }

        if (action.startsWith("detail:")) {
            String[] parts = action.split(":");
            if (parts.length < 4) {
                return;
            }
            RecipeCategory category = RecipeCategory.fromId(parts[1]);
            int page = parseInt(parts[2]);
            String recipeKey = parts[3];
            if (category == null) {
                return;
            }
            RecipePreview preview = findRecipe(category, recipeKey);
            if (preview != null) {
                openDetailMenu(player, category, preview, page);
            }
        }
    }

    public boolean isRecipeMenu(String title) {
        String mainTitle = color(text("recipe_menu_main_title", "&3DSP Recipes"));
        String categoryPrefix = color(text("recipe_menu_category_prefix", "&3DSP Recipes:"));
        String detailPrefix = color(text("recipe_menu_detail_prefix", "&3Recipe:"));
        return title.equals(mainTitle) || title.startsWith(categoryPrefix) || title.startsWith(detailPrefix);
    }

    private RecipePreview findRecipe(RecipeCategory category, String key) {
        for (RecipePreview preview : recipesFor(category)) {
            if (preview.key.equalsIgnoreCase(key)) {
                return preview;
            }
        }
        return null;
    }

    private void addCategoryButton(Inventory inv, RecipeCategory category) {
        FileConfiguration cfg = plugin.getConfig();
        String base = "recipe-menu.main.categories." + category.id + ".";
        int slot = cfg.getInt(base + "slot", category.defaultSlot);
        Material material = ItemList.resolveConfiguredMaterial(cfg.getString(base + "material"), category.defaultMaterial);
        String lore = cfg.getString(base + "lore", "&7Open category");

        inv.setItem(slot, createActionItem(
                material,
                category.displayName(this),
                "cat:" + category.id + ":0",
                color(lore)
        ));
    }

    private ItemStack createRecipePreviewItem(RecipePreview preview, RecipeCategory category, int page) {
        ItemStack item = preview.result.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<String> lore = new ArrayList<>();
        lore.add(color(text("recipe_menu_pattern_label", "&7Pattern:")));
        lore.add(ChatColor.DARK_GRAY + formatRow(preview.rows[0]));
        lore.add(ChatColor.DARK_GRAY + formatRow(preview.rows[1]));
        lore.add(ChatColor.DARK_GRAY + formatRow(preview.rows[2]));
        lore.add("");
        lore.add(color(text("recipe_menu_open_detail", "&eClick for detailed view")));

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING,
                "detail:" + category.id + ":" + page + ":" + preview.key);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createActionItem(Material material, String name, String action, String loreLine) {
        Material finalMaterial = material == null ? Material.PAPER : material;
        ItemStack item = new ItemStack(finalMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(name);
        meta.setLore(List.of(loreLine));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fillWithPane(Inventory inventory) {
        Material paneMaterial = resolveMenuMaterial("recipe-menu.filler.material", Material.GRAY_STAINED_GLASS_PANE);
        ItemStack pane = new ItemStack(paneMaterial);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(plugin.getConfig().getString("recipe-menu.filler.name", "&8 ")));
            pane.setItemMeta(meta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
    }

    private int getMainSize() {
        return normalizeSize(plugin.getConfig().getInt("recipe-menu.main.size", 27));
    }

    private int getCategorySize() {
        return normalizeSize(plugin.getConfig().getInt("recipe-menu.category.size", 54));
    }

    private int getDetailSize() {
        return normalizeSize(plugin.getConfig().getInt("recipe-menu.detail.size", 54));
    }

    private int normalizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        int mod = size % 9;
        return mod == 0 ? size : size + (9 - mod);
    }

    private List<Integer> getIntList(String path, List<Integer> fallback) {
        List<Integer> list = plugin.getConfig().getIntegerList(path);
        return list.isEmpty() ? fallback : list;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String formatRow(String row) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            builder.append(c == ' ' ? '-' : c);
            if (i < row.length() - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    private String text(String key, String fallback) {
        String value = LanguageManager.getValue(key);
        if (value == null || value.equals("[Invalid Translate Key]")) {
            return fallback;
        }
        return value;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private Material resolveMenuMaterial(String path, Material fallback) {
        return ItemList.resolveConfiguredMaterial(plugin.getConfig().getString(path), fallback);
    }

    private List<RecipePreview> recipesFor(RecipeCategory category) {
        List<RecipePreview> recipes = new ArrayList<>();

        for (RecipeManager.RecipeDefinition definition : recipeManager.getRecipesByCategory(category.id)) {
            Map<Character, Ingredient> ingredients = new HashMap<>();
            for (Map.Entry<Character, ItemStack> entry : definition.ingredientItems().entrySet()) {
                ItemStack ingredientItem = entry.getValue().clone();
                String label = ingredientDisplayName(ingredientItem);
                ingredients.put(entry.getKey(), new Ingredient(ingredientItem, "", label));
            }
            recipes.add(new RecipePreview(definition.key(), definition.result().clone(), definition.rows(), ingredients));
        }

        return recipes;
    }

    private String ingredientDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }


    private record Ingredient(ItemStack item, String textKey, String fallbackText) {
    }

    private record RecipePreview(String key, ItemStack result, String[] rows, Map<Character, Ingredient> ingredients) {
        String getDisplayName() {
            ItemMeta meta = result.getItemMeta();
            return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : result.getType().name();
        }

        Map<Character, ItemStack> ingredientItems() {
            Map<Character, ItemStack> map = new HashMap<>();
            for (Map.Entry<Character, Ingredient> entry : ingredients.entrySet()) {
                map.put(entry.getKey(), entry.getValue().item.clone());
            }
            return map;
        }

        List<String> legendLines(RecipeMenuManager manager) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<Character, Ingredient> entry : ingredients.entrySet()) {
                Ingredient ingredient = entry.getValue();
                String label = manager.text(ingredient.textKey, ingredient.fallbackText);
                lines.add(ChatColor.DARK_GRAY + String.valueOf(entry.getKey()) + " = " + ChatColor.GRAY + label);
            }
            return lines;
        }
    }

    private enum RecipeCategory {
        CELLS("cells", 10, Material.COMPARATOR, "recipe_menu_category_cells", "&bStorage Cells"),
        CONTAINERS("containers", 12, Material.CHEST, "recipe_menu_category_containers", "&aStorage Containers"),
        TOOLS("tools", 14, Material.BLAZE_ROD, "recipe_menu_category_tools", "&6Tools"),
        WIRELESS("wireless", 16, Material.REDSTONE, "recipe_menu_category_wireless", "&dWireless + Upgrades");

        private final String id;
        private final int defaultSlot;
        private final Material defaultMaterial;
        private final String titleKey;
        private final String defaultTitle;

        RecipeCategory(String id, int defaultSlot, Material defaultMaterial, String titleKey, String defaultTitle) {
            this.id = id;
            this.defaultSlot = defaultSlot;
            this.defaultMaterial = defaultMaterial;
            this.titleKey = titleKey;
            this.defaultTitle = defaultTitle;
        }

        String displayName(RecipeMenuManager manager) {
            return manager.color(manager.text(titleKey, defaultTitle));
        }

        static RecipeCategory fromId(String id) {
            for (RecipeCategory value : values()) {
                if (value.id.equalsIgnoreCase(id)) {
                    return value;
                }
            }
            return null;
        }
    }
}


package cn.davidma.tinymobfarm.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import cn.davidma.tinymobfarm.core.util.NBTHelper;

import net.minecraft.block.material.Material;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;;

public enum EnumMobFarm {

	WOOD("wood_farm", Material.WOOD, 1F, false, new int[] {2, 3, 3}),
	STONE("stone_farm", Material.ROCK, 1.5F, false, new int[] {1, 2, 3}),
	IRON("iron_farm", Material.IRON, 5F, true, new int[] {1, 2}),
	GOLD("gold_farm", Material.IRON, 5F, true, new int[] {1, 1, 2}),
	DIAMOND("diamond_farm", Material.IRON, 5F, true, new int[] {1}),
	EMERALD("emerald_farm", Material.IRON, 5F, true, new int[] {0, 1, 1}),
	INFERNAL("inferno_farm", Material.ROCK, 50F, true, new int[] {0, 0, 1}),
	ULTIMATE("ultimate_farm", Material.ROCK, 75F, true, new int[] {0});
	
	private String registryName;
	private Material material;
	private float hardness;
	private boolean canFarmHostile;
	private int[] defaultDamageChance;
	
	private EnumMobFarm(String registryName, Material material, float hardness, boolean canFarmHostile, int[] damageChance) {
		this.registryName = registryName;
		this.material = material;
		this.hardness = hardness;
		this.canFarmHostile = canFarmHostile;
		this.defaultDamageChance = damageChance;
	}
	
	public String getRegistryName() {
		return this.registryName;
	}
	
	public String getRegistryName(boolean mechanical) {
		return mechanical ? this.registryName + "_machine" : this.registryName;
	}
	
	public String getTranslationKey() {
		return String.format("tile.%s:%s.name", Reference.MOD_ID, this.registryName);
	}
	
	public String getTranslationKey(boolean mechanical) {
		return String.format("tile.%s:%s.name", Reference.MOD_ID, this.getRegistryName(mechanical));
	}
	
	public Material getMaterial() {
		return this.material;
	}
	
	public float getHardness() {
		return this.hardness;
	}
	
	public boolean isLassoValid(ItemStack lasso) {
		return NBTHelper.hasMob(lasso) && (this.canFarmHostile || !NBTHelper.hasHostileMob(lasso));
	}
	
	public int getMaxProgress() {
		return (int) (ConfigTinyMobFarm.MOB_FARM_SPEED[this.ordinal()] * 20);
	}
	
	public int getRandomDamage(Random rand) {
		Map<Integer, Integer> damageChance = this.getDamageChance();
		int roll = rand.nextInt(this.getTotalChance(damageChance));
		for (Map.Entry<Integer, Integer> entry: damageChance.entrySet()) {
			roll -= entry.getValue();
			if (roll < 0) return entry.getKey();
		}
		return 0;
	}
	
	public void addTooltip(List<String> tooltip, boolean mechanical) {
		if (mechanical) {
			tooltip.add(I18n.format("tinymobfarm.tooltip.mechanical"));
		}
		if (!this.canFarmHostile) {
			tooltip.add(TextFormatting.RED + I18n.format("tinymobfarm.tooltip.no_hostile"));
		}
		tooltip.add(I18n.format("tinymobfarm.tooltip.farm_rate_experience", ConfigTinyMobFarm.MOB_FARM_SPEED[this.ordinal()]));
		tooltip.add(I18n.format("tinymobfarm.tooltip.durability_info"));
		Map<Integer, Integer> damageChance = this.getDamageChance();
		int totalChance = this.getTotalChance(damageChance);
		for (Map.Entry<Integer, Integer> entry: damageChance.entrySet()) {
			int chance = (int) (entry.getValue() * 100.0 / totalChance);
			if (entry.getKey() == 0) tooltip.add(I18n.format("tinymobfarm.tooltip.no_durability", chance));
			else tooltip.add(I18n.format("tinymobfarm.tooltip.default_durability", chance, entry.getKey()));
		}
	}

	private Map<Integer, Integer> getDamageChance() {
		Map<Integer, Integer> damageChance = new LinkedHashMap<Integer, Integer>();
		if (this.ordinal() < ConfigTinyMobFarm.LASSO_DURABILITY_DAMAGE.length) {
			String config = ConfigTinyMobFarm.LASSO_DURABILITY_DAMAGE[this.ordinal()];
			try {
				for (String entry: config.split(",")) {
					String[] parts = entry.trim().split("=", 2);
					int damage = Integer.parseInt(parts[0].trim());
					int chance = Integer.parseInt(parts[1].trim());
					if (damage < 0 || chance <= 0) throw new IllegalArgumentException();
					damageChance.put(damage, chance);
				}
				if (!damageChance.isEmpty()) return damageChance;
			} catch (RuntimeException ignored) {
				// Fall back to the built-in defaults when a config entry is invalid.
			}
		}

		damageChance.clear();
		for (int damage: this.defaultDamageChance) {
			damageChance.put(damage, damageChance.containsKey(damage) ? damageChance.get(damage) + 1 : 1);
		}
		return damageChance;
	}

	private int getTotalChance(Map<Integer, Integer> damageChance) {
		int totalChance = 0;
		for (int chance: damageChance.values()) totalChance += chance;
		return Math.max(1, totalChance);
	}
}

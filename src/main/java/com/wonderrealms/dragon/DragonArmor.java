package com.wonderrealms.dragon;

import net.minecraft.world.item.ItemStack;

/**
 * Armor a tamed dragon can wear. Use it on your dragon to put it on (swapping out any it already wears), and use
 * shears to take it off again. It shows on the dragon, and adds to its armor, toughness and (netherite) its
 * resistance to being knocked back.
 */
public enum DragonArmor {
	IRON("iron", 6, 0, 0),
	GOLD("golden", 8, 0, 0),
	DIAMOND("diamond", 12, 2, 0),
	NETHERITE("netherite", 16, 3, 0.15),
	/** Forged from the Elder Dragon's scales over netherite armor. */
	ELDER("elder", 20, 4, 0.25);

	/** Item and texture name prefix: "iron" for iron_dragon_armor. */
	public final String id;
	public final double armor;
	public final double toughness;
	public final double knockbackResistance;

	DragonArmor(String id, double armor, double toughness, double knockbackResistance) {
		this.id = id;
		this.armor = armor;
		this.toughness = toughness;
		this.knockbackResistance = knockbackResistance;
	}

	/** 1-based, so 0 can mean "no armor" in the dragon's synced data. */
	public int index() {
		return this.ordinal() + 1;
	}

	public static DragonArmor byIndex(int index) {
		return index >= 1 && index <= values().length ? values()[index - 1] : null;
	}

	public static DragonArmor byId(String id) {
		for (DragonArmor armor : values()) {
			if (armor.id.equals(id)) {
				return armor;
			}
		}
		return null;
	}

	/** The armor this item is, or null if it isn't dragon armor. */
	public static DragonArmor of(ItemStack stack) {
		for (DragonArmor armor : values()) {
			if (stack.is(Dragons.ARMOR_ITEMS.get(armor))) {
				return armor;
			}
		}
		return null;
	}
}

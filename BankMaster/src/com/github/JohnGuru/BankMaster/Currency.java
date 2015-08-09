package com.github.JohnGuru.BankMaster;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Currency {
	private class CurrencyItem {
		public Material blockType;
		public BigDecimal denomination;
		
		public CurrencyItem () {
			denomination = BigDecimal.ONE;
			blockType = null;
		}
	}
	
	private static List<CurrencyItem> denoms;
	private static int decimals;
	private static String unit_name;
	private static String plural_name;
	public static MathContext context; // used in VaultServer
	
	public Currency() {
		denoms = new LinkedList<CurrencyItem>();
		decimals = 0;
		unit_name = "unit";
		plural_name = "units";
		context = new MathContext(12, RoundingMode.HALF_UP);
	}
	
	public void setDecimals(int ndec) {
		if (ndec >= 0 && ndec <= 2)
			decimals = ndec;
	}
	
	public static int getDecimals() {
		return decimals;
	}
	
	public static void setName(String for_one) {
		unit_name = new String(for_one);
		plural_name = new String(for_one);
	}
	
	public static void setName(String for_one, String for_many) {
		unit_name = new String(for_one);
		plural_name = new String(for_many);
	}
	
	public static String getName() {
		return unit_name;
	}
	
	public static String getPlural() {
		return plural_name;
	}

	
	/*
	 * addDenomination: accepts a string of the form "name,9"
	 * 		where name is a material name and 9 is the number of value units.
	 * 		It is CRITICAL that this list be maintained in descending order by denomination,
	 * 		with the largest-value blocks first.
	 */
	public void addDenomination(String spec) throws Exception {
		CurrencyItem item = new CurrencyItem();
		String[] parts = spec.split(",");
		if (parts.length != 2)
			throw new Exception("denomination format: " + spec);
		item.blockType = Material.valueOf(parts[0]);
		item.denomination = new BigDecimal(parts[1]);
		if (item.denomination.signum() <= 0)
			throw new Exception("invalid denomination " + parts[1]);
		
		int index = 0;
		for (CurrencyItem d : denoms) {
			if (item.denomination.compareTo(d.denomination) == 0)
				throw new Exception("Redundant denominations");
			if (item.denomination.compareTo(d.denomination) > 0) {
				denoms.add(index, item);
				break;
			}
			index++;
		}
		if (index >= denoms.size())
			denoms.add(item); // add at end of list.
	}
	
	static BigDecimal getDenom(Material m) {
		for (CurrencyItem item : denoms) {
			if (item.blockType == m) {
				return item.denomination;
			}
		}
		return BigDecimal.ZERO;
	}
	
	static boolean contains(Material m) {
		for (CurrencyItem item : denoms) {
			if (item.blockType == m)
				return true;
		}
		return false;
	}
	
	private static int indexOf(Material m) {
		int index = 0;
		for (CurrencyItem item : denoms) {
			if (m == item.blockType)
				return index;
			index++;
		}
		return index;
	}
	
	static BigDecimal valueOf(Inventory inv) {
		// for efficiency, count the total number of each type of material
		int tally[] = new int[denoms.size() + 1];
		for (ItemStack item : inv) {
			if (item != null)
				tally[indexOf(item.getType())] += item.getAmount();
		}
		// and now apply denomination to each tally
		BigDecimal value = new BigDecimal(0);
		int index = 0;
		for (CurrencyItem unit : denoms) {
			value = value.add(unit.denomination.multiply(new BigDecimal(tally[index++])));
		}
		value.setScale(getDecimals(), RoundingMode.HALF_UP);
		return value;
	}
	
	static ItemStack[] toBlocks(BigDecimal value, int slots) {
		ArrayList<ItemStack> stacks = new ArrayList<ItemStack>(slots);
		
		for (CurrencyItem i : denoms) {
			int blocks = value.divide(i.denomination, context).intValue();
			BigDecimal mult64 = i.denomination.multiply(new BigDecimal(64), context);
			while (blocks > 64 && stacks.size() < slots) {
				stacks.add(new ItemStack(i.blockType, 64));
				blocks -= 64;
				value = value.subtract(mult64);
			}
			if (blocks > 0 && stacks.size() < slots) {
				stacks.add(new ItemStack(i.blockType, blocks));
				value = value.subtract(i.denomination.multiply(new BigDecimal(blocks)));
			}
			if (stacks.size() == slots) // array is full
				break;
		}
		
		// the returned array must contain only valid slots
		ItemStack[] trimmed = new ItemStack[stacks.size()];
		return stacks.toArray(trimmed);
	}

}

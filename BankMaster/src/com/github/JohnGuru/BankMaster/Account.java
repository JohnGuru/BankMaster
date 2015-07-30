package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class Account implements InventoryHolder {
	private String name;
	private UUID uuid;
	long	lastUpdate;	// getTime() value of date&time of last interest update
	double	money;
	double	loans;
	int		XP;
	private Inventory purse;
	private boolean isCash;
	
	// Account state is maintained in an external .yml file
	private FileConfiguration config;
	private File configFile;
	
	// key names
	private static final String keyName = "player";
	private static final String keyUpdate = "bank.lastUpdate";
	private static final String keyMoney = "bank.money";
	private static final String keyXP = "bank.XP";
	private static final String keyLoans = "bank.loans";

	// Constructor for new account for Player P
	public Account(String pname, UUID uid) {
		name = pname;
		uuid = uid;
		lastUpdate = 0;
		money = 0;
		loans = 0;
		XP = 0;
		config = null;
		configFile = null;
		purse = null;
		isCash = true;
	}
	
	/*
	 * Required methods for InventoryHolder
	 */
	public Inventory getInventory() {
		return purse;
	}
	
	public boolean isCashInventory() {
		return isCash;
	}
	
	/*
	 * Create inventory loaded with player's money
	 * 
	 */
	public Inventory setCash() {
		purse = Bukkit.getServer().createInventory(this, 27, String.format("Account %.2f", money));
		isCash = true;
		
		// Limit the displayed funds to 8 stacks of emerald blocks

		// now load the inventory with a portion of the account
		long rem = (long)money;
		
		if (rem > 4544L) /* 7 stacks of emerald blocks + 8 stacks of emeralds */
			rem = 4544L;
		
		// remove the on-screen amount from the account's balance
		money -= rem;
		
		// now fill 15 slots of inventory
		for (int slot = 0; rem > 0; slot++) {
			int bump = 0;
			if (rem >= 576) {
				bump = 576;
				purse.setItem(slot, new ItemStack(Material.EMERALD_BLOCK,64));
			} else if (rem >= 64) {
				bump = 64;
				purse.setItem(slot, new ItemStack(Material.EMERALD,64));
			} else {
				bump = (int)rem;
				purse.setItem(slot, new ItemStack(Material.EMERALD, bump));
			}
			rem -= bump;
		}
		return purse;
	}
	
	/*
	 * Create a short inventory offering money to the player. The money is not
	 * taken from the player's account; it's new money. Note that if the player's
	 * loans are maxed out, we still open the window so he can repay the loan.
	 */
	public Inventory setLoan(double maxLoan) {
		purse = Bukkit.getServer().createInventory(this, 18, "Loans & Payments");
		isCash = false;
		
		long offered = (long)(maxLoan - loans);
		if (offered < 0)
			offered = 0;
		if (offered > 2496)
			offered = 2496;
		
		// Add the amount offered to account's current loans
		// whatever remains after the player's access will be deducted from the outstanding loan,
		// see InventoryCloseEvent for details.
		loans += offered;
		
		for (int slot = 0; offered > 0; slot++) {
			int bump = 0;
			if (offered >= 576) {
				bump = 576;
				purse.setItem(slot, new ItemStack(Material.EMERALD_BLOCK,64));
			} else if (offered >= 64) {
				bump = 64;
				purse.setItem(slot, new ItemStack(Material.EMERALD,64));
			} else {
				bump = (int)offered;
				purse.setItem(slot, new ItemStack(Material.EMERALD, bump));
			}
			offered -= bump;
		}		
		return purse;
	}
	
	public void discard() {
		// the inventory associated with this account is no longer open/valid
		purse = null;
	}
	
	/*
	 * Open the Account - loads the external player.yml into memory
	 */
	public void openAccount() {
		if (configFile == null) {
			configFile = new File(BankMaster.ourDataFolder, uuid + ".yml");
			// String status = configFile.exists() ? " exists" : " missing";
			// Bukkit.getLogger().info("configFile " + configFile.getName() + status);
		}
		if (config == null) {
			config = YamlConfiguration.loadConfiguration(configFile);
		}
		// initialize account values from account.yml
		lastUpdate = config.getLong(keyUpdate);
		money = config.getDouble(keyMoney);
		loans = config.getDouble(keyLoans);
		XP = config.getInt(keyXP);
		/*
		String msg = String.format("opened %s, money %.2f, loans %.2f", name, money, loans);
		Bukkit.getLogger().info(msg);
		*/
	}

	/*
	 * Write the account - saves the current account data to disk
	 */
	public void pushAccount(CommandSender sender) {

		if (configFile != null) {	//nothing to do if configFile never opened
		
			// update config file values
			config.set(keyName, name);
			config.set(keyUpdate, lastUpdate);
			config.set(keyMoney, money);
			config.set(keyLoans, loans);
			config.set(keyXP, XP);
	
	        try {
				config.save(configFile);
			} catch (IOException e) {
				Bukkit.getLogger().warning(e.getMessage());
				sender.sendMessage(ChatColor.RED + "Account file could not be updated");
			}
		}
	}
	
	// general handling functions
	
	public boolean isFor(UUID uid) {
		return (uuid.equals(uid) );
	}
	
	public void setEmpty() {
		money = 0;
		loans = 0;
	}
	
	public void setEmptyXP() {
		XP = 0;
	}
	
	public void deposit(double amt) {
		money += amt;
	}
	
	public boolean withdraw(double amt) {
		if (money >= amt) {
			money -= amt;
			return true;
		}
		return false;
	}
	
	public void audit(CommandSender sender) {
		// Display account status
		sender.sendMessage("Balance: " + money);
		sender.sendMessage("Total Loans: " + loans);
	}
	
	/*
	public void setBase() {
		base = money;
	}
	
	public double getBase() {
		return base;
	}
	*/
	
}

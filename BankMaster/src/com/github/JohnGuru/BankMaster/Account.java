package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class Account implements InventoryHolder {
	private OfflinePlayer owner;
	long	lastUpdate;	// getTime() value of date&time of last interest update
	double	money;
	double	loans;
	int		XP;
	private Inventory inventory;
	
	// Account state is maintained in an external .yml file
	private FileConfiguration config;
	private File configFile;
	
	// key names
	private static final String keyUpdate = "bank.lastUpdate";
	private static final String keyMoney = "bank.money";
	private static final String keyXP = "bank.XP";
	private static final String keyLoans = "bank.loans";

	// Constructor for new account for Player P
	public Account(OfflinePlayer p) {
		owner = p;
		lastUpdate = 0;
		money = 0;
		loans = 0;
		XP = 0;
		config = null;
		configFile = null;
		inventory = null;
	}
	
	/*
	 * Required methods for InventoryHolder
	 */
	public Inventory getInventory() {
		if (owner instanceof Player)
			inventory = ((Player) owner).getServer().createInventory(this, 27, "Account $" + money);
		else
			inventory = null;
		return inventory;
	}
	
	/*
	 * Open the Account - loads the external player.yml into memory
	 */
	public void openAccount() {
		if (configFile == null) {
			configFile = new File(BankMaster.ourDataFolder, owner.getName() + ".yml");
		}
		if (config == null) {
			config = YamlConfiguration.loadConfiguration(configFile);
		}
		// initialize account values from account.yml
		lastUpdate = config.getLong(keyUpdate);
		money = config.getDouble(keyMoney);
		loans = config.getDouble(keyLoans);
		XP = config.getInt(keyXP);
				
	}
	

	/*
	 * Write the account - saves the current account data to disk
	 */
	public void pushAccount() {
		if (configFile == null || config == null) {
			// this account was never opened, so nothing to do
			return;
		}
		// update config file values
		config.set(keyUpdate, lastUpdate);
		config.set(keyMoney, money);
		config.set(keyLoans, loans);
		config.set(keyXP, XP);
		
		try {
	        config.save(configFile);
	    } catch (IOException ex) {
	        BankMaster.plugin.getLogger().log(Level.SEVERE, "Could not save account to " + configFile, ex);
	    }

	}
	
	// general handling functions
	public OfflinePlayer getOwner() {
		return owner;
	}
	
	public boolean isFor(OfflinePlayer p) {
		return (owner.equals(p) );
	}
	
	public double getMoney() {
		return money;
	}
	
	public double getLoans() {
		return loans;
	}
	
	public int getXP() {
		return XP;
	}
	
	public void clear() {
		money = 0;
		loans = 0;
	}
	
	public void clearXP() {
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
	
	// Loan primitives
	public void borrow(double amt) {
		money += amt;
		loans += amt;
	}
	
	public boolean repay(double amt) {
		if (amt <= loans) {
			loans -= amt;
			return true;
		}
		return false;
	}
	
	public void audit(CommandSender sender) {
		// Display account status
		sender.sendMessage("Balance: " + money);
		sender.sendMessage("Total Loans: " + loans);
	}
	
}

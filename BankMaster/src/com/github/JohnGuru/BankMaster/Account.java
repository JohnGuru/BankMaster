package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class Account implements InventoryHolder {
	private String name;
	private String uuid;
	long	lastUpdate;	// getTime() value of date&time of last interest update
	double	money;
	double	loans;
	int		XP;
	private Inventory inventory;
	
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
	public Account(String pname, String uid) {
		name = pname;
		uuid = uid;
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
		inventory = BankMaster.plugin.getServer().createInventory(this, 27, "Account $" + money);
		return inventory;
	}
	
	/*
	 * Open the Account - loads the external player.yml into memory
	 */
	public void openAccount() {
		if (configFile == null) {
			configFile = new File(BankMaster.ourDataFolder, uuid + ".yml");
			if (!configFile.exists()) {
				// Maybe there's a config using player name
				configFile = new File(BankMaster.ourDataFolder, name + ".yml");
			}
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
		if (configFile == null) {
			// this account was never opened, so nothing to do
			return;
		}
		
		// update config file values
		config.set(keyName, name);
		config.set(keyUpdate, lastUpdate);
		config.set(keyMoney, money);
		config.set(keyLoans, loans);
		config.set(keyXP, XP);

		// during transition phase, the active File might be based on player name
		configFile = new File(BankMaster.ourDataFolder, uuid + ".yml");
		
		try {
	        config.save(configFile);
	    } catch (IOException ex) {
	        BankMaster.plugin.getLogger().log(Level.SEVERE, "Could not save account to " + configFile, ex);
	    }

	}
	
	// general handling functions
	
	public boolean isFor(String suid) {
		return (uuid.equals(suid) );
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
			money -= amt;
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

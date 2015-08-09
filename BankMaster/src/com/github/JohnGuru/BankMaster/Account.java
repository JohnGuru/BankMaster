package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class Account implements InventoryHolder {
	private String name;
	private UUID uuid;
	long	lastUpdate;	// getTime() value of date&time of last interest update
	BigDecimal money;
	BigDecimal loans;
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
		money = BigDecimal.ZERO;
		loans = BigDecimal.ZERO;
		XP = 0;
		config = null;
		configFile = null;
		purse = null;
		isCash = true;
	}
	
	/*
	 * account holder
	 */
	public String getName() {
		return name;
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
		purse = Bukkit.getServer().createInventory(this, 27, String.format("Account " + money));
		isCash = true;
		
		// now load the inventory with a portion of the account
		purse.setContents(Currency.toBlocks(money, 14));
		money = money.subtract(Currency.valueOf(purse));
		return purse;
	}
	
	/*
	 * Create a short inventory offering money to the player. The money is not
	 * taken from the player's account; it's new money. Note that if the player's
	 * loans are maxed out, we still open the window so he can repay the loan.
	 */
	public Inventory setLoan(BigDecimal maxLoan) {
		purse = Bukkit.getServer().createInventory(this, 18, "Loans & Payments");
		isCash = false;
		
		BigDecimal offered = maxLoan.subtract(loans);
		BigDecimal limit = new BigDecimal("2496"); // not to overload the inventory
		if (offered.compareTo(limit) > 0)
			offered = limit;
		if (offered.signum() < 0)
			offered = BigDecimal.ZERO;
		
		// Add the amount offered to account's current loans
		// whatever remains after the player's access will be deducted from the outstanding loan,
		// see InventoryCloseEvent for details.
		purse.setContents(Currency.toBlocks(offered, 9));
		loans = loans.add(Currency.valueOf(purse));
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
		}
		if (config == null) {
			config = YamlConfiguration.loadConfiguration(configFile);
		}
		// initialize account values from account.yml
		lastUpdate = config.getLong(keyUpdate);
		money = new BigDecimal(config.getString(keyMoney,"0.0"));
		loans = new BigDecimal(config.getString(keyLoans,"0.0"));
		XP = config.getInt(keyXP);
		money = money.setScale(Currency.getDecimals(), RoundingMode.HALF_UP);
		loans = loans.setScale(Currency.getDecimals(), RoundingMode.HALF_UP);

	}
	

	/*
	 * Write the account - saves the current account data to disk
	 */
	public void pushAccount(CommandSender sender) {

		if (configFile != null) {	//nothing to do if configFile never opened
		
			// update config file values
			money = money.setScale(Currency.getDecimals(), RoundingMode.HALF_UP);
			loans = loans.setScale(Currency.getDecimals(), RoundingMode.HALF_UP);
			config.set(keyName, name);
			config.set(keyUpdate, lastUpdate);
			config.set(keyMoney, money.toPlainString());
			config.set(keyLoans, loans.toPlainString());
			config.set(keyXP, XP);
	
	        try {
				config.save(configFile);
			} catch (IOException e) {
				Bukkit.getLogger().warning(e.getMessage());
				if (sender != null)
					sender.sendMessage(ChatColor.RED + "Account file could not be updated");
			}
		}
	}
	
	// general handling functions
	
	public boolean isFor(UUID uid) {
		return (uuid.equals(uid) );
	}
	
	public void setEmpty() {
		money = BigDecimal.ZERO;
		loans = BigDecimal.ZERO;
	}
	
	public void setEmptyXP() {
		XP = 0;
	}
	
	/*
	 * Transaction based methods
	 */
	public void deposit(BigDecimal amt) {
		money = money.add(amt);
	}
	
	public boolean withdraw(BigDecimal amt) {
		if (amt.compareTo(money) <= 0) {
			money = money.subtract(amt);
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

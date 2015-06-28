package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class Bank {
	private Map<String, Object> names;
	private List<Account> accounts;
	private boolean newNames;	// true if name list needs to be rewritten to config file
	private boolean namesLogging;
	double	rate;			// interest rate, money*rate
	double	maxMoney;		// max size of a bank account
	double	maxLoans;		// total amount the bank can loan
	double	maxSingleLoan;	// maximum single payer loan

	public Bank() {
		// Initialize the bank management parameters to defaults
		// Custom values will be set from a config.yml later
		newNames = false;
		namesLogging = true;
		names = null;
		accounts = new LinkedList<Account>();
		rate = 0;			// by default, interest is disabled
		maxLoans = 0;		// by default, the bank can't loan money
		maxSingleLoan = 0;	// by default, no player can borrow more than 0
		maxMoney = 10000000;  // default max account size
	}
	
	// Methods to set custom bank behavior from a config.yml
	
	/*
	 * Account names
	 * 		A table cross-referencing "official" player names to UUID values,
	 * 		used by bank admin commands
	 */
	public void loadNames() {
		ConfigurationSection section = BankMaster.plugin.getConfig().getConfigurationSection("names");
		if (section == null)
			names = new HashMap<String,Object>();
		else
			names = section.getValues(false);
		newNames = false;
	}
		
	public boolean saveNames() {
		if (newNames)
			BankMaster.plugin.getConfig().createSection("names", names);
		return newNames;
	}
	
	/*
	 * Daily interest rate is computed as the compounding rate that will
	 * double the player's account value in the specified number of days,
	 * r = 2 ^ (1/days)
	 */
	public boolean setInterest( int days ) {

		// Interest is disabled by setting the rate to 0
		if (days == 0) {
			rate = 0;
			BankMaster.plugin.getLogger().info("interest is disabled.");
			return true;
		}
		
		// Double-up days can't be negative or more than a year
		if (days < 0 || days > 365)
			return false;
		rate = Math.pow(2, 1.0/days);
		BankMaster.plugin.getLogger().info("BankMaster interest rate = " + rate);
		return true;
	}
	
	public void setMaxMoney( double max ) {
		if (max <= 100000000)
			maxMoney = max;
		else
			BankMaster.plugin.getLogger().log(Level.WARNING, "maxMoney exceeds 100,000,000");
	}
	
	public void setMaxLoans(double max) {
		maxLoans = max;
	}
	
	public void setMaxSingle(double max) {
		maxSingleLoan = max;
	}
	
	/*
	 * findAccount
	 * 		Returns the requested account if in the in-memory list,
	 * 		otherwise reads the account.yml file and adds to the list
	 */
	
	private Account findAccount(String name, String uid) {
		// If the account is in memory, return it
		for ( Account a : accounts) {
			if (a.isFor(uid))
				return a;
		}
		// spawn a new account object with default values
		Account acct = new Account(name, uid);
		// load account status from yml file, if it exists
		acct.openAccount();
		// append to the bank list
		if (accounts.add(acct))
			return acct;
		return null;
	}
	
	/*
	 * getAccount applies pending interest to the player's account,
	 * but only when accessed by the player, not an admin
	 */
	public Account getAccount(String name) {
		String uid = (String)names.get(name);
		if (uid == null)
			return null;
		return findAccount(name, uid);
	}
	
	/*
	 * The names list has to be updated to contain this name-UUID pair.
	 * If it already has an entry for this playerName with a different UUID,
	 * the mapping will be updated to remember this new instance.
	 * We don't attempt to resolve different users with the same name.
	 */
	public Account getAccount(Player p) {
		String uuid = p.getUniqueId().toString();
		String other = (String) names.get(p.getName());
		if (namesLogging) {
			/*
			 * following creates a log of names created for the bank
			 */
			File log = new File(BankMaster.ourDataFolder, "names.log");
			FileOutputStream app;
			try {
				app = new FileOutputStream(log, true);
				PrintStream out = new PrintStream(app);
				if (other == null) {
					// new definition of a player account
					out.format("Creating account for %s UUID(%s)\n", p.getName(), uuid);
				} else if (!uuid.equals(other)) {
					// redefining this player name
					out.format("Reassigned name %s to UUID(%s)\n", p.getName(), uuid);
				}
				out.flush();
				app.close();
			} catch (FileNotFoundException e) {
				// Note that we cannot maintain the log file and terminate logging
				BankMaster.plugin.getLogger().log(Level.WARNING, "Cannot write names.log");
				namesLogging = false;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				BankMaster.plugin.getLogger().log(Level.SEVERE, "Error writing names.log");
			}
		}
		if (other == null || !uuid.equals(other)) {
			newNames = true;
			names.put(p.getName(), uuid);
		}
		Account a = findAccount(p.getName(), uuid);
		if (a != null) {

			// update Account with pending interest

			Date today = new Date();
			long now = today.getTime() / 86400000 ;
			/* 'now' is an integral number of days since epoch.
			 * There are 86,400 seconds in a day, and 1000 'ticks' in a getTime() value.
			 * Interest is considered to compound at midnight
			 */
			
			// Update account with accumulated interest, if applicable
			
			if (a.lastUpdate > 0 && rate > 1.0) {
				long periods = now - a.lastUpdate;
				if (periods > 0) {
					long pennies = (long)((a.money + 0.005) * 100.0);
					double oldamt = (double)pennies / 100.0;
					double newamt = oldamt * Math.pow(rate, periods);
					if (newamt > maxMoney)
						newamt = maxMoney;
					pennies = (long)((newamt + 0.005) * 100.0);
					a.money = (double)pennies / 100.0;
					double interest = a.money - oldamt;
					
					BankMaster.msg(p, ChatColor.YELLOW, String.format("Interest applied: %.2f", interest));
					
				}
			}
			
			a.lastUpdate = now;
			
		}
		return a;
	}
	
	/*
	 * Transactional methods
	 */
	
	public void audit(CommandSender sender, String name ) {
		Account a = getAccount(name);
		if (a == null) {
			sender.sendMessage(ChatColor.RED + name + " is not a valid player name");
			return;
		}
		a.audit(sender); // display account status
	}
	
	

}

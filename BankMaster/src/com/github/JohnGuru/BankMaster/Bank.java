package com.github.JohnGuru.BankMaster;

import java.util.Date;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

public class Bank {
	private List<Account> accounts;
	double	rate;			// interest rate, money*rate
	double	maxMoney;		// max size of a bank account
	double	maxLoans;		// total amount the bank can loan
	double	maxSingleLoan;	// maximum single payer loan

	public Bank() {
		// Initialize the bank management parameters to defaults
		// Custom values will be set from a config.yml later
		accounts = new LinkedList<Account>();
		rate = 0;			// by default, interest is disabled
		maxLoans = 0;		// by default, the bank can't loan money
		maxSingleLoan = 0;	// by default, no player can borrow more than 0
		maxMoney = 10000000;  // default max account size
	}
	
	private Account findAccount(OfflinePlayer p) {
		// If the account is in memory, return it
		for ( Account a : accounts) {
			if (a.isFor(p))
				return a;
		}
		// spawn a new account object with default values
		Account acct = new Account(p);
		// load account status from yml file, if it exists
		acct.openAccount();
		// append to the bank list
		if (accounts.add(acct))
			return acct;
		return null;
	}
	
	// Methods to set custom bank behavior from a config.yml
	
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
	 * GetAccount
	 * 		applies accrued interest before any actions on the account
	 */
	public Account getAccount(CommandSender sender, String name ) {
		OfflinePlayer player = BankMaster.plugin.getServer().getOfflinePlayer(name);
		// A non-existing player is one who has never played before and is not online
		if (player.getFirstPlayed() == 0 && !player.isOnline())
			return null;
		return getAccount(sender, player);
	}
	
	public Account getAccount(CommandSender sender, OfflinePlayer p) {
		Account a = findAccount(p);
		if (a != null) {
			// update account with pending interest
			Date today = new Date();
			long now = today.getTime() / 86400000 ;
			/* 'period' is an integral number of days since epoch.
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
					StringBuilder sb = new StringBuilder();
					Formatter f = new Formatter(sb);
					f.format("Interest applied: %.2f", interest);
					BankMaster.msg(sender, ChatColor.YELLOW, f.toString());
					f.close();
					
				}
			}
			
			a.lastUpdate = now;
			
		}
		return a;
	}
	
	/*
	 * Transactional methods
	 */
	
	public void audit( CommandSender sender, String name ) {
		Account a = getAccount(sender, name);
		if (a == null) {
			sender.sendMessage(ChatColor.RED + name + " is not a valid player name");
			return;
		}
		a.audit(sender); // display account status
	}
	
	

}

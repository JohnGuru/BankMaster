package com.github.JohnGuru.BankMaster;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Bank {
	private List<Account> accounts;
	double	rate;			// interest rate, money*rate
	double	maxMoney;		// max size of a bank account
	double	maxLoans;		// total amount the bank can loan

	public Bank() {
		// Initialize the bank management parameters to defaults
		// Custom values will be set from a config.yml later
		accounts = new LinkedList<Account>();
		rate = 0;			// by default, interest is disabled
		maxLoans = 0;		// by default, the bank can't loan money
		maxMoney = 10000000;  // default max account size
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
		Bukkit.getLogger().info("BankMaster interest rate = " + rate);
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
	
	public double getMaxMoney() {
		return maxMoney;
	}
	
	public double getMaxLoans() {
		return maxLoans;
	}
	
	
	/*
	 * findAccount
	 * 		Returns the requested account if in the in-memory list,
	 * 		otherwise reads the account.yml file and adds to the list
	 */
	
	private Account findAccount(Player p) {
		// If the account is in memory, return it
		for ( Account a : accounts) {
			if (a.isFor(p.getUniqueId()))
				return a;
		}
		// spawn a new account object with default values
		Account acct = new Account(p.getName(), p.getUniqueId());
		// load account status from yml file, if it exists
		acct.openAccount();
		// append to the bank list
		accounts.add(acct);
		return acct;
	}
	
	/*
	 * getAccount called from an admin command:
	 *   This version does not apply interest to the player's account
	 */
	public Account getAccount(String name) {
		Player p = Bukkit.getPlayer(name);
		if (p == null)
			return null;
		return findAccount(p);
	}
	
	/*
	 * getAccount called for a Player
	 */
	public Account getAccount(Player p) {

		Account a = findAccount(p);
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
					double oldamt = a.money;
					a.money = oldamt * Math.pow(rate, periods);
					if (a.money > maxMoney)
						a.money = maxMoney;
					double interest = a.money - oldamt;
					
					p.sendMessage(ChatColor.YELLOW + String.format("Interest applied: %.2f", interest));
					
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
	
	/*
	 * borrow(Account) calculates how much the borrower can borrow with one click:
	 *   nothing, if loans are maxed out
	 *   unused balance of maxLoans, or, if more than maxSingleLoan,
	 *   maxSingleLoan
	 */
	public int borrow(Account acct) {
		double amt = maxLoans - acct.loans;
		if (amt <= 0)
			amt = 0;
		return (int)amt;
	}

}

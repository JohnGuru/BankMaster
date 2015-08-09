package com.github.JohnGuru.BankMaster;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Bank {
	private List<Account> accounts;
	private double	rate;			// interest rate, money*rate
	private BigDecimal maxMoney;		// max size of a bank account
	private BigDecimal maxLoans;		// total amount the bank can loan
	private MathContext context;		// standard math context

	public Bank() {
		// Initialize the bank management parameters to defaults
		// Custom values will be set from a config.yml later
		accounts = new LinkedList<Account>();
		rate = 0;			// by default, interest is disabled
		maxLoans = BigDecimal.ZERO;		// by default, the bank can't loan money
		maxMoney = new BigDecimal("10000000");  // default max account size
		context = new MathContext(12,RoundingMode.HALF_UP);
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
	
	public void setMaxMoney(String max) {
		BigDecimal bdmax;
		try {
			bdmax = new BigDecimal(max);
		}
		catch (Exception e) {
			Bukkit.getLogger().warning("config: maxMoney invalid number");
			return;
		}
		
		if (bdmax.compareTo(new BigDecimal("999999999")) < 0)
			maxMoney = bdmax;
		else
			Bukkit.getLogger().warning("maxMoney exceeds 999,999,999");
	}
	
	public void setMaxLoans(String max) {
		BigDecimal bdmax;
		try {
			bdmax = new BigDecimal(max);
		}
		catch (Exception e) {
			Bukkit.getLogger().warning("config: maxLoans invalid number");
			return;
		}
		
		if (bdmax.compareTo(new BigDecimal("999999999")) < 0)
			maxLoans = bdmax;
		else
			Bukkit.getLogger().warning("maxLoans exceeds 999,999,999");
	}
	
	public BigDecimal getMaxMoney() {
		return maxMoney;
	}
	
	public BigDecimal getMaxLoans() {
		return maxLoans;
	}
	
	
	/*
	 * findAccount
	 * 		Returns the requested account if in the in-memory list,
	 * 		otherwise reads the account.yml file and adds to the list
	 */
	
	public Account getAccount(Player p) {
		if (p == null)
			return null; // safety
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
		return getAccount(p);
	}
	
	/*
	 * getUpdatedAccount - apply accrued interest
	 */
	public Account getUpdatedAccount(Player p) {

		Account a = getAccount(p);
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
					BigDecimal oldamt = a.money;
					double newamt = oldamt.doubleValue() * Math.pow(rate, periods);
					a.money = new BigDecimal(newamt, context);
					a.money = a.money.setScale(Currency.getDecimals(), RoundingMode.HALF_UP);
					if (a.money.compareTo(maxMoney) > 0)
						a.money = maxMoney;
					BigDecimal interest = a.money.subtract(oldamt);
					
					p.sendMessage(ChatColor.YELLOW + "Interest applied: " + interest);
					
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
	public BigDecimal borrow(Account acct) {
		BigDecimal amt = maxLoans.subtract(acct.loans);
		if (amt.compareTo(BigDecimal.ZERO) < 0)
			amt = BigDecimal.ZERO;
		return amt;
	}

}

package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;

public class VaultServer implements Economy {
	
	private static String msg_impl = "Not implemented";
	private static String msg_offline = "Player not online";
	private static String msg_funds = "Insufficient funds";


	// ---------------------------------------------------------------------------------------------
	// Local functions which do the actual work
	// ---------------------------------------------------------------------------------------------

	@Override
	public String getName() {
		// Name of economy responder
		return "BankMaster";
	}

	@Override
	public boolean hasBankSupport() {
		// Does BankMaster provide support for multiple banks?
		return false;
	}

	@Override
	public boolean isEnabled() {
		// This information is retained by the system plugin loader.
		// Our role is to provide the handle of the plugin, so it can be tested.
		return (BankMaster.plugin != null && BankMaster.plugin.isEnabled());
	}

	@Override
	public String currencyNamePlural() {
		// Use the name set from the config, if any
		return Currency.getPlural();
	}

	@Override
	public String currencyNameSingular() {
		// Use the name set from the config, if any 
		return Currency.getName();
	}

	@Override
	public int fractionalDigits() {
		// We have the same information available
		return Currency.getDecimals();
	}

	@Override
	public String format(double amount) {
		// Currency setDecimals() forces fractionalDigits to 0,1,2
		
		final String[] formats = { "%.0f", "%.1f", "%.2f" };
		return String.format(formats[Currency.getDecimals()], amount);
	}
	
	private double our_getBalance(Player player) {
		Account acct = BankMaster.bank.getAccount(player);
		if (acct == null)
			return 0;
		return acct.money.doubleValue();
	}
	
	private boolean our_checkBalance(Player player, double amount) {
		Account acct = BankMaster.bank.getAccount(player);
		if (acct == null)
			return false;
		BigDecimal query = new BigDecimal(amount, Currency.context);
		return (acct.money.compareTo(query) >= 0);
	}
	
	private boolean our_hasAccount(OfflinePlayer p) {
		if (p == null)
			return false;
		File configFile = new File(BankMaster.ourDataFolder, p.getUniqueId().toString() + ".yml");
		return configFile.exists();
	}

	private EconomyResponse our_depositPlayer(Player p, double amount) {
		if (p == null)
			return new EconomyResponse(amount, 0, ResponseType.FAILURE, msg_offline);
		/*
		 * This looks complicated because we need to return how much of the deposit
		 * we actually serviced -- in case of maxMoney limit.
		 */
		Account acct = BankMaster.bank.getAccount(p);
		double oldbal = acct.money.doubleValue();
		BigDecimal newbal = acct.money.add(new BigDecimal(amount));
		newbal = newbal.setScale(Currency.getDecimals(), RoundingMode.HALF_UP);
		if (newbal.compareTo(BankMaster.bank.getMaxMoney()) > 0)
			acct.money = BankMaster.bank.getMaxMoney();
		else
			acct.money = newbal;
		double handled = acct.money.doubleValue() - oldbal;
		acct.pushAccount(null);
		return new EconomyResponse(handled, acct.money.doubleValue(), ResponseType.SUCCESS, null);
	}
	
	// withdraw money from account
	// by convention, cannot complete if the player doesn't have enough money
	// The request will not be partially executed
	
	private EconomyResponse our_withdrawPlayer(Player p, double amount) {
		if (p == null)
			return new EconomyResponse(amount, 0, ResponseType.FAILURE, msg_offline);
		Account acct = BankMaster.bank.getAccount(p);
		BigDecimal order = new BigDecimal(amount, Currency.context);
		order = order.setScale(Currency.getDecimals(), RoundingMode.HALF_UP);
		if (order.compareTo(acct.money) < 0)
			return new EconomyResponse(amount, 0, ResponseType.FAILURE, msg_funds);
		acct.money = acct.money.subtract(order);
		acct.pushAccount(null);
		return new EconomyResponse(amount, acct.money.doubleValue(), ResponseType.SUCCESS, null);
	}
	
	
	// ---------------------------------------------------------------------------------------------
	// Interface calls
	// ---------------------------------------------------------------------------------------------

	/*
	 * (non-Javadoc)
	 * @see net.milkbowl.vault.economy.Economy#createPlayerAccount(org.bukkit.OfflinePlayer)
	 * 	These functions do not actually create a player account, they only test
	 * 	if an account exists. An account can -only- be opened by the player himself
	 * 	when he's online.
	 */
	@Override
	public boolean createPlayerAccount(OfflinePlayer player) {
		if (player == null)
			return false;
		File configFile = new File(BankMaster.ourDataFolder, player.getUniqueId().toString() + ".yml");
		return configFile.exists();
	}
	
	@Override
	@Deprecated
	public boolean createPlayerAccount(String name) {
		return createPlayerAccount(Bukkit.getOfflinePlayer(name));
	}

	@Override
	@Deprecated
	public boolean createPlayerAccount(String name, String worldname) {
		// Accounts are world-independent
		return createPlayerAccount(name);
	}

	@Override
	public boolean createPlayerAccount(OfflinePlayer player, String worldname) {
		// Accounts are world-independent
		return createPlayerAccount(player);
	}

	/*
	 * Deposit an amount in the player's account
	 */
	@Override //@Deprecated
	public EconomyResponse depositPlayer(String name, double amount) {
		// Fully supported when player 'name' is online
		return our_depositPlayer(Bukkit.getPlayer(name), amount);
	}

	@Override
	public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
		// promote offline player to active player
		return our_depositPlayer(player.getPlayer(), amount);
	}

	@Override //@Deprecated
	public EconomyResponse depositPlayer(String name, String worldname, double amount) {
		// Banking functions are world-independent
		return our_depositPlayer(Bukkit.getPlayer(name), amount);
	}

	@Override
	public EconomyResponse depositPlayer(OfflinePlayer player, String worldname, double amount) {
		// Convert to online player
		return our_depositPlayer(player.getPlayer(), amount);
	}

	/*
	 * Balance Inquiry - getBalance()
	 */
	@Override //@Deprecated
	public double getBalance(String name) {
		// Player must be online
		return our_getBalance(Bukkit.getPlayerExact(name));
	}

	@Override
	public double getBalance(OfflinePlayer p) {
		// Player must be online
		return our_getBalance(p.getPlayer());
	}

	@Override //@Deprecated
	public double getBalance(String name, String worldname) {
		// ignore worldname
		return our_getBalance(Bukkit.getPlayerExact(name));
	}

	@Override
	public double getBalance(OfflinePlayer p, String worldname) {
		// ignore worldname
		return our_getBalance(p.getPlayer());
	}

	/*
	 * (non-Javadoc)
	 * @see net.milkbowl.vault.economy.Economy#has(java.lang.String, double)
	 * 	'Name' is supported when the player is online
	 */
	@Override //	@Deprecated
	public boolean has(String name, double amount) {
		return our_checkBalance(Bukkit.getPlayerExact(name), amount);
	}

	@Override
	public boolean has(OfflinePlayer player, double amount) {
		return our_checkBalance(player.getPlayer(), amount);
	}

	@Override //@Deprecated
	public boolean has(String name, String worldname, double amount) {
		return our_checkBalance(Bukkit.getPlayerExact(name), amount);
	}

	@Override
	public boolean has(OfflinePlayer player, String worldname, double amount) {
		return our_checkBalance(player.getPlayer(), amount);
	}

	/*
	 * (non-Javadoc)
	 * @see net.milkbowl.vault.economy.Economy#hasAccount(java.lang.String)
	 * 
	 * Checks whether an account exists for player. The account exists
	 * if an external yml file exists for that player
	 * 
	 * Unlike functions that modify the account, these functions will try
	 * to service offline player inquiries.
	 */
	@Override
	@Deprecated
	public boolean hasAccount(String name) {
		OfflinePlayer p = Bukkit.getOfflinePlayer(name); 
		return our_hasAccount(p);
	}

	@Override
	public boolean hasAccount(OfflinePlayer player) {
		return our_hasAccount(player);
	}

	@Override
	@Deprecated
	public boolean hasAccount(String name, String worldname) {
		// Accounts are world insensitive
		return hasAccount(name);
	}

	@Override
	public boolean hasAccount(OfflinePlayer player, String worldname) {
		// Accounts are world insensitive
		return our_hasAccount(player);
	}

	@Override //@Deprecated
	public EconomyResponse withdrawPlayer(String name, double amount) {
		// String name trusted only if that player is online
		return our_withdrawPlayer(Bukkit.getPlayer(name), amount);
	}

	@Override
	public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
		// Requires the player to be current online
		return our_withdrawPlayer(player.getPlayer(), amount);
	}

	@Override
	@Deprecated
	public EconomyResponse withdrawPlayer(String name, String worldname, double amount) {
		// ignore world
		return our_withdrawPlayer(Bukkit.getPlayer(name), amount);
	}

	@Override
	public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldname, double amount) {
		// ignore world
		return our_withdrawPlayer(player.getPlayer(), amount);
	}
	
	//---------------------------------------------------------------------------------------
	// Bank functions not implemented
	//---------------------------------------------------------------------------------------

	@Override
	public List<String> getBanks() {
		return null;
	}

	@Override
	public EconomyResponse deleteBank(String bank) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}
	
	@Override
	public EconomyResponse bankBalance(String bank) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	public EconomyResponse bankDeposit(String bank, double amount) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	public EconomyResponse bankHas(String bank, double amount) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	public EconomyResponse bankWithdraw(String bank, double amount) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	@Deprecated
	public EconomyResponse createBank(String bank, String playername) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	public EconomyResponse createBank(String bank, OfflinePlayer player) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	@Deprecated
	public EconomyResponse isBankOwner(String bankname, String playername) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	public EconomyResponse isBankOwner(String bankname, OfflinePlayer player) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	@Deprecated
	public EconomyResponse isBankMember(String bankname, String playername) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}

	@Override
	public EconomyResponse isBankMember(String bankname, OfflinePlayer player) {
		// bank functions not available
		return new EconomyResponse(0,0, ResponseType.NOT_IMPLEMENTED, msg_impl);
	}
}

package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

public final class BankMaster extends JavaPlugin {
	public static File ourDataFolder;
	public static JavaPlugin plugin;
	
	// the bank object collects functionality applying to all accounts
	public static Bank bank = null;
	
	// the signList gives the world locations of all active bank signs
	public ArrayList<String> signList;
	private boolean signListChanged; // if true, the list has to be written out
	
	// the currency collection identifies the types of currencies in use
	public Currency currency; // Types of currency items and their scaling values
	
	private static String money_sign;
	private static final String default_money_sign = "Money Bank";

	private static String ATM_sign;
	private static String default_ATM_sign = "ATM";
	
	private static String loans_sign;
	private static String default_Loans_sign = "Loans";
	
	private static String xp_sign;
	private static final String default_xp_sign = "XP Bank";
	
	//permissions strings
	private static final String admin = "bankmaster.admin";
	private static final String user = "bankmaster.use";
	
	//config keys
	private static final String key_moneysign = "bank.moneySign";
	private static final String key_xpsign = "bank.XPSign";
	private static final String key_atmsign = "bank.ATMSign";
	private static final String key_loansign = "bank.LoanSign";
	
	private static final String interestDays = "bank.interestDays";
	private static final String maxLoans = "bank.maxLoans";
	private static final String maxMoney = "bank.maxMoney";
	private static final String banksigns = "bank.signs";
	
	// config parameters for currency
	private static final String currency_decimals = "bank.currency.decimals";
	private static final String currency_denominations = "bank.currency.denominations";
	
	private static String bank_command = "bank";
	private static String pay_command = "pay";
	private static String audit = "audit";
	private static String master = "bankmaster";
	private static String add_cmd = "add";
	private static String rem_cmd = "rem";
	private static String set_cmd = "set";
	// private static String setXP = "setxp";
	
	
	@Override
	public void onEnable() {
		FileConfiguration conf = getConfig();
		
		ourDataFolder = getDataFolder();
		plugin = this;
		bank = new Bank();
		
		// Process config.yml, hook vault
		
		saveDefaultConfig();
		money_sign = conf.getString(key_moneysign, default_money_sign);
		ATM_sign   = conf.getString(key_atmsign, default_ATM_sign);
		loans_sign = conf.getString(key_loansign, default_Loans_sign);
		xp_sign    = conf.getString(key_xpsign, default_xp_sign);
		
		
		// initialize currency items
		
		currency = new Currency();
		currency.setDecimals(conf.getInt(currency_decimals));
		List<String> denoms = conf.getStringList(currency_denominations);
		
		boolean errors = false;
		if (denoms.isEmpty()) {
			getLogger().warning("config: No currency denominations");
			errors = true;
		}
		for (String s : denoms) {
			try {
				currency.addDenomination(s);
			} catch (Exception e) {
				getLogger().warning("config: invalid denomination " + s);
				errors = true;
			}
		}
		
		if (errors) {
			getLogger().log(Level.SEVERE, "Invalid currency configuration, banking disabled.");
			return;
		}
		
		// initialize banking parameters
		
		bank.setMaxMoney(conf.getString(maxMoney) );
		bank.setMaxLoans(conf.getString(maxLoans) );
		if ( !bank.setInterest(conf.getInt(interestDays)) ) {
			getLogger().log(Level.SEVERE, "Invalid expression in " + interestDays);
		}
		
		// hook to Vault
		
		if (!setupEconomy() ) {
            getLogger().warning("Vault plugin not found, bank access may be limited");
        }
		
		// process sign list bank.signs
		
		signList = new ArrayList<String>(25);
		signListChanged = false;
		// if the plugin is reloaded, we have to discard the current signList
		
		List<String> signspec = conf.getStringList(banksigns);
		if (!signspec.isEmpty()) {
			int valid_signs = 0;
			for (String spec : signspec) {
				if (setupSign(spec)) {
					valid_signs++;
				} else {
					// force update of config sign list
					signListChanged = true;
					/*
					 * We don't waste server time processing a BlockBreakEvent for a bank sign-
					 * The player won't be able to click on it, so it won't be activated again during this session
					 * and it won't be found during next startup, so
					 * it will be dropped from the sign list then.
					 */
				}
			}
			getLogger().info("opened " + valid_signs + " bank signs");
		}
		
		// Start listeners on sign click
		getServer().getPluginManager().registerEvents(new SignListener(), this);

	}
	
	
	// Hook to Vault plugin service

	private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        final ServicesManager sm = getServer().getServicesManager();
        sm.register(Economy.class, new VaultServer(), this, ServicePriority.Highest);
        getLogger().info("Registered Vault interface.");
        return true;
    }

	@Override
	public void onDisable() {
		
		// Update config with new bank signs
		
		if (signList.size() > 0 && signListChanged ) {
			// String[] signs = new String[signList.size()];
			// signList.values().toArray(signs);
			getConfig().set(banksigns, signList);
		
			saveConfig();
		}
		
		ourDataFolder = null;
		currency = null;
		
		// Now discard all listeners on exit
		HandlerList.unregisterAll(this);
	}
	

	/*
	 * Event Listeners for right click on a sign
	 */
	public final class SignListener implements Listener {
		
		@EventHandler(priority = EventPriority.LOW)
		public void playerInteract(PlayerInteractEvent ev) {
			
			if (ev.isCancelled())
				return; // ignore if already handled

			if (ev.getAction() != Action.RIGHT_CLICK_BLOCK)
				return; // ignore if not a right-click

			BlockState bs = ev.getClickedBlock().getState();
			Material bt = bs.getType();
			
			// Check for right-click on a sign
			if (bt == Material.WALL_SIGN) {
				// It's a sign, but is it a valid bank sign
				Sign s = (Sign) bs;
				if (s.hasMetadata(key_moneysign)) {
					
					List<MetadataValue> values = s.getMetadata(key_moneysign);
					for (MetadataValue m : values) {
						if (m.asString().equals(money_sign)) {
							MoneyBank(ev.getPlayer());
							break;
						}
						if (m.asString().equals(ATM_sign)) {
							MoneyBank(ev.getPlayer());
							break;
						}
						if (m.asString().equals(loans_sign)) {
							LoanBank(ev.getPlayer());
							break;
						}
						if (m.asString().equals(xp_sign)) {
							XPBank(ev.getPlayer());
							break;
						}
						Bukkit.getLogger().info("found metadatavalue " + m.asString() );
					}
					
					ev.setCancelled(true);
				}
			}
		}
		
		/*
		 * InventoryClickEvent:
		 * 	moving emeralds or emerald blocks to the player's inventory is allowed
		 *	moving emeralds or emerald blocks to the account inventory is allowed
		 *	moving other types of blocks is NOT allowed, and the event will be cancelled
		 */
		
		@EventHandler
		public void clickItemstack(InventoryClickEvent ev) {
			/*
			 * two possible outcomes here:
			 * user has clicked an empty stack or a currency item: allow the click
			 * user has clicked an unrecognized item type: disable the click so he can't manipulate the itemstack
			 * user has clicked a Borrow block: put emerald blocks up to maxSingleLoan on the curosr
			 * user has clicked a Repay block: deduct cursor emeralds from loans
			 */
			if (ev.getInventory().getHolder() instanceof Account) {
				ItemStack item = ev.getCurrentItem();
				if (item == null)
					return; // shouldn't happen but let's be safe
				Material itemtype = item.getType();
				if (itemtype == Material.AIR || Currency.contains(itemtype))
					return;


			/*
			 * we don't recognize this type of item, so ignore the click, player can't manipulate it
			 * in an account inventory
			 */
			ev.setCancelled(true);
			}
			
		}
		
		/*
		 * InventoryCloseEvent:
		 * 	Copy money in the inventory back into the account
		 */
		@EventHandler
		public void inventoryClose(InventoryCloseEvent ev) {
			if (ev.getInventory().getHolder() instanceof Account) {
				Account acct = (Account)ev.getInventory().getHolder();
				
				// tally the amount of cash in the top inventory
				BigDecimal onhand = Currency.valueOf(ev.getInventory());
				
				if (acct.isCashInventory()) {
					acct.money = acct.money.add(onhand);
				}
				else {
					acct.loans = acct.loans.subtract(onhand);
					if (acct.loans.signum() < 0) {
						acct.money.subtract(acct.loans);
						acct.loans = BigDecimal.ZERO;
					}
				}
				acct.discard();
				acct.pushAccount(ev.getPlayer());
			}


		}
		
		/*
		 * SignChangeEvent:
		 * 		detect admin placing new bank sign
		 */
		@EventHandler
		public void signChange(SignChangeEvent ev) {
			BlockState bs = ev.getBlock().getState();
			if (bs.getType() == Material.WALL_SIGN) {

				if (ev.getLine(0).equalsIgnoreCase(money_sign)) {
					// Money Bank sign created
					newBankSign(ev, money_sign);
				}
				else if (ev.getLine(0).equalsIgnoreCase(ATM_sign)) {
					// ATM Sign created
					newBankSign(ev, ATM_sign);
				}

				else if (ev.getLine(0).equalsIgnoreCase(loans_sign)) {
					// Loans sign created
					newBankSign(ev, loans_sign);
				}

				else if (ev.getLine(0).equalsIgnoreCase(xp_sign)) {
					// XP Bank sign created
					newBankSign(ev, xp_sign);
				}
				else if (bs.hasMetadata(key_moneysign)) {
					// attempted change of sign type
					ev.getPlayer().sendMessage(ChatColor.RED + "Active bank sign cannot be destroyed");
					ev.setCancelled(true);
				}
			}
		} /* --------------------------------------- */
	}
	
	/*
	 * MoneyBank()
	 * 		Allow the player to add or subtract money from his inventory
	 */
	private void MoneyBank(Player p) {
		// Access the required account. If none exists, we get an account with zero contents
		Account a = bank.getUpdatedAccount(p);
				
		// now display the space and let the user update it
		// InventoryView workspace = p.openInventory(work);
		p.openInventory(a.setCash());
	}
	
	/*
	 * LoanBank()
	 * 		Opens an Inventory View with the player's account on top, and
	 * 		a bank inventory on the bottom. The bank inventory contains as much money
	 * 		as the player can borrow, or what will fit in the window. The player
	 * 		can move some of the bank's money to his account inventory, to borrow,
	 * 		or move some of the money in his account inventory to the bank, to repay.
	 */
	private void LoanBank(Player p) {
		// First, create the InventoryView
		Account a = bank.getAccount(p);
		
		// Create an inventory (type Chest) for the Bank's teller window
		p.openInventory(a.setLoan(bank.getMaxLoans()));
	}
	
	/*
	 * XPBank()
	 * 		Add the player's XP to the bank
	 *
	 */
	private void XPBank(Player p) {
		// TODO Add inventory to banked XP
		getLogger().info("Called XP Bank for " + p.getName());
	}

	
	/*
	 * Command handler: we have two commands,
	 * 	"bank" requires BankMaster.use permission
	 */
	public boolean onCommand(CommandSender sender,
			Command cmd, String label, String[] args) {
		
		if (sender instanceof Player) {
			Player thisPlayer = (Player)sender;
			// Only player commands
			
			/********** Bank Command *************************************************/
			
			/*
			 * a player can invoke player commands (with bankmaster.use permission)
			 * or admin commands (with bankmaster.admin permission)
			 */
			
			if (cmd.getName().equalsIgnoreCase(bank_command)) {
				
				// requires bank.use permission
				if (!thisPlayer.hasPermission(user)) {
					sender.sendMessage(ChatColor.RED + "No permission");
					return true;
				}
				if (args.length == 0) {
					Account a = bank.getAccount(thisPlayer);
					if (a == null) {
						sender.sendMessage(ChatColor.RED + "You have no bank account yet.");
					}
					else { /* Balance inquiry */
						sender.sendMessage(ChatColor.YELLOW + "Balance: " + a.money + ", Loans: " + a.loans);
						a.pushAccount(sender);
					}
					return true;
				}
				if (args.length == 3) {
					// bank pay <player> amt
					if (args[0].equalsIgnoreCase(pay_command)) {
						// TODO process pay(player,amt)
						thisPlayer.sendMessage("bank pay " + args[2] + " to " + args[1]);
						return true;
					}
				}
				return false;
			}
			
			if (!thisPlayer.hasPermission(admin))
				return false; // only admin commands remain
			
		}
		// Only admin commands allowed from here on
	
		if (cmd.getName().equalsIgnoreCase(audit)) {
			if (args.length != 1) // requires <player>
				return false; 
			bank.audit(sender, args[0]);
			return true;
		}

		/********** BankMaster Command *************************************************/

		if (cmd.getName().equalsIgnoreCase(master)) {
			if (args.length != 3) // requires func <player> <amount>
				return false; 
			Account acct = bank.getAccount(args[1]);
			if (acct == null) {
				sender.sendMessage(ChatColor.RED + args[1] + " is not a valid account or player not online");
				return true;
			}
			BigDecimal amt;
			amt = BigDecimal.ZERO;
			try {
				amt = new BigDecimal(args[2]);
			}
			catch (NumberFormatException e) {
				sender.sendMessage(ChatColor.RED + "Invalid number format");
				return true;
			}
			if (args[0].equalsIgnoreCase(set_cmd)) {
				acct.setEmpty();
				acct.deposit(amt);
				acct.audit(sender);
			}
			else if (args[0].equalsIgnoreCase(add_cmd)) {
				acct.deposit(amt);
				acct.audit(sender);
			}
			else if (args[0].equalsIgnoreCase(rem_cmd)) {
				if (acct.withdraw(amt))
					acct.audit(sender);
				else
					sender.sendMessage(ChatColor.RED + "Insufficient funds");
			}
			else // invalid command
				return false;
			
			acct.pushAccount(sender);
			return true;

		}
			
		return false; // unrecognized command
	}
	
	
	/*
	 * Methods for handling the config list of valid bank signs
	 * 		The external config.yml contains a bank.locations: key that lists
	 * 		as strings, all the locations of active, valid bank signs.
	 * 
	 * 		During play, if an admin creates a new bank sign, it is added to this list.
	 * 		If an admin breaks the sign, it is removed from the list.
	 * 
	 * 		When the plugin is disabled, the current list is written out.
	 * 		When the plugin is enabled, the list is read in and Metadata set for each valid sign.
	 * 		When the sign is right clicked, if it has this Metadata, then it is considered a valid bank sign.
	 */

	// **** setupSign
	//		reactivate a bank sign from config data
	
	private boolean setupSign(String spec) {
		String[] part = spec.split(",");
		if (part.length == 4) {
			String worldname = part[0];
			int x = Integer.valueOf(part[1]);
			int y = Integer.valueOf(part[2]);
			int z = Integer.valueOf(part[3]);
			
			World w = getServer().getWorld(worldname);
			if (w == null) {
				getLogger().log(Level.WARNING, "Invalid world in sign location: " + spec);
				return false;
			}
			
			// Location loc = new Location(w, x, y, z);
			Block bs = w.getBlockAt(x,y,z);
			if (bs != null && bs.getType() == Material.WALL_SIGN) {
				Sign s = (Sign)bs.getState();
				String line1 = s.getLine(0);
				if (line1.endsWith(money_sign)) {
					bs.setMetadata(key_moneysign, new FixedMetadataValue(this, money_sign));
					signList.add(spec);
					return true;
				}
				if (line1.endsWith(ATM_sign)) {
					// ATM sign
					bs.setMetadata(key_moneysign, new FixedMetadataValue(this, ATM_sign));
					signList.add(spec);
					return true;
				}
				if (line1.endsWith(loans_sign)) {
					// Loans sign
					bs.setMetadata(key_moneysign, new FixedMetadataValue(this, loans_sign));
					signList.add(spec);
					return true;
				}
				if (line1.endsWith(xp_sign)) {
					// XP sign
					bs.setMetadata(key_moneysign, new FixedMetadataValue(this, xp_sign));
					signList.add(spec);
					return true;
				}
				
			}
		} else
			getLogger().log(Level.WARNING, "Invalid sign location: " + spec);
		
		return false;
	}
	
	
	// **** newBankSign:
	//		Add this to the list of bank signs
	
	private boolean newBankSign(SignChangeEvent ev, String signType) {
		
		if (!ev.getPlayer().hasPermission(admin)) {
			ev.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to create banks");
			return false;
		}
		ev.setLine(0, ChatColor.DARK_GREEN + signType);
		ev.setLine(1, ChatColor.DARK_GREEN + "~~~~~~~~~~");
		ev.setLine(2, "");

		Location loc = ev.getBlock().getLocation();
		String buf = String.format("%s,%d,%d,%d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
				
		getLogger().info("new bank sign: " + buf);
		signList.add(buf);
		signListChanged = true;
		
		ev.getBlock().setMetadata(key_moneysign, new FixedMetadataValue(plugin, signType));
		return true;
	}
}

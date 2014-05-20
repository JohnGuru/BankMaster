package com.github.JohnGuru.BankMaster;

import java.io.File;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Hashtable;
import java.util.logging.Level;

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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public final class BankMaster extends JavaPlugin {
	public static File ourDataFolder;
	public static JavaPlugin plugin;
	public Bank bank = null;
	public Map<Location,String> signList;
	public Map<Material,Double> currency; // Types of currency items and their scaling values
	private boolean signListChanged;
	
	private static String money_sign;
	private static final String default_money_sign = "Money Bank";
	//private static String xp_sign;
	//private static final String default_xp_sign = "XP Bank";
	
	private static final String key_moneysign = "bank.moneySign";
	private static final String key_xpsign = "bank.XPSign";
	
	private static final String admin = "bankmaster.admin";
	private static final String user = "bankmaster.use";
	private static final String interestDays = "bank.interestDays";
	private static final String maxLoans = "bank.maxLoans";
	private static final String maxSingleLoan = "bank.maxSingleLoan";
	private static final String maxMoney = "bank.maxMoney";
	private static final String banksigns = "bank.signs";
	
	private static String bank_command = "bank";
	private static String pay_command = "pay";
	private static String audit = "audit";
	private static String deposit = "deposit";
	private static String deduct = "deduct";
	private static String setmoney = "setmoney";
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
		//xp_sign = conf.getString(key_xpsign, default_xp_sign);
		
		if ( !bank.setInterest(conf.getInt(interestDays)) ) {
			getLogger().log(Level.SEVERE, "Invalid expression in " + interestDays);
		}
		bank.setMaxMoney(conf.getDouble(maxMoney) );
		bank.setMaxLoans(conf.getDouble(maxLoans) );
		bank.setMaxSingle(conf.getDouble(maxSingleLoan) );
		
		// initialize currency items
		currency = new Hashtable<Material,Double>();
		currency.put(Material.EMERALD_BLOCK, 9.0);
		currency.put(Material.EMERALD, 1.0);
		
		// process sign list bank.signs
		
		signList = new Hashtable<Location,String>(25);
		signListChanged = false;
		// if we're going to re-process the sign list in the config.yml, we have to clear it first
		
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
	
	@Override
	public void onDisable() {
		
		// Update config with new bank signs
		
		if (signList.size() > 0 && signListChanged ) {
			String[] signs = new String[signList.size()];
			signList.values().toArray(signs);
			getConfig().set(banksigns, signs);
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
					MoneyBank(ev.getPlayer());
					ev.setCancelled(true);
				}
				else if (s.hasMetadata(key_xpsign)) {
					XPBank(ev.getPlayer());
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
			
			if (ev.getInventory().getHolder() instanceof Account) {
				ItemStack item = ev.getCurrentItem();
				if (item != null && (item.getType() != Material.AIR) && !currency.containsKey(item.getType())) {
					ev.setCancelled(true);
					return;
				}
				item = ev.getCursor();
				if (item != null && (item.getType() != Material.AIR) && !currency.containsKey(item.getType())) {
					ev.setCancelled(true);
					return;
				}
			}
			
		}
		
		/*
		 * InventoryCloseEvent:
		 * 	Copy money in the inventory back into the account
		 */
		@EventHandler
		public void inventoryClose(InventoryCloseEvent ev) {
			if ( !(ev.getInventory().getHolder() instanceof Account))
				return;
			Account acct = (Account)ev.getInventory().getHolder();
			for (ItemStack item : ev.getInventory()) {
				if (item != null)
					switch (item.getType()) {
					case EMERALD_BLOCK:
						acct.deposit(item.getAmount() * 9);
						break;
					case EMERALD:
						acct.deposit(item.getAmount());
						break;
					default:
						break;
					}
			}
			ev.getInventory().clear();
			acct.pushAccount();
		}
		
		/*
		 * SignChangeEvent:
		 * 		detect admin placing new bank sign
		 */
		@EventHandler
		public void signChange(SignChangeEvent ev) {
			BlockState bs = ev.getBlock().getState();
			if (bs.getType() == Material.WALL_SIGN) {
				Sign s = (Sign)bs;
				if (ev.getLine(0).equalsIgnoreCase(money_sign)) {
					if (ev.getPlayer().hasPermission(admin)) {
						newBankSign(s);
						ev.setLine(0, ChatColor.AQUA + money_sign);
						ev.setLine(1, ChatColor.AQUA + "~~~~~~~~~~");
						ev.setLine(2, ChatColor.AQUA + "Ready");

					}
					else {
						ev.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to create banks");
					}
				}
			}
		}
	}
	
	/*
	 * MoneyBank()
	 * 		Allow the player to add or subtract money from his inventory
	 */
	private void MoneyBank(Player p) {
		// Access the required account. If none exists, we get an account with zero contents
		Account a = bank.getAccount(p, p);
		Inventory inv = a.getInventory();
		
		// now load the inventory with a portion of the account
		long rem = (long)a.getMoney();
		
		// Limit the displayed funds to 8 stacks of emerald blocks
		
		if (rem > 4544L)
			rem = 4544L;
		
		// remove the on-screen amount from the account's balance
		a.withdraw(rem);
		
		// now let the user take the on-screen amount or any portion
		for (int slot = 0; rem > 0; slot++) {
			int bump = 0;
			if (rem >= 576) {
				bump = 576;
				inv.setItem(slot, new ItemStack(Material.EMERALD_BLOCK,64));
			} else if (rem >= 64) {
				bump = 64;
				inv.setItem(slot, new ItemStack(Material.EMERALD,64));
			} else {
				bump = (int)rem;
				inv.setItem(slot, new ItemStack(Material.EMERALD, bump));
			}
			rem -= bump;
		}
		
		// now display the space and let the user update it
		// InventoryView workspace = p.openInventory(work);
		p.openInventory(inv);
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
		Player thisPlayer;
		
		if (sender instanceof Player) {
			thisPlayer = (Player)sender;
			// Only player commands
			
			/********** Bank Command *************************************************/
			
			if (cmd.getName().equalsIgnoreCase(bank_command)) {
				
				// requires bank.use permission
				if (!thisPlayer.hasPermission(user)) {
					thisPlayer.sendMessage(ChatColor.RED + "No permission");
					return true;
				}
				if (args.length == 0) {
					Account a = bank.getAccount(sender, thisPlayer);
					if (a == null) {
						thisPlayer.sendMessage(ChatColor.RED + "No bank account found.");
					}
					else {
						thisPlayer.sendMessage(ChatColor.YELLOW + "Balance: " + a.getMoney());
						a.pushAccount();
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

		/********** setmoney Command *************************************************/

		if (cmd.getName().equalsIgnoreCase(setmoney)) {
			if (args.length != 2) // requires <player> <amount>
				return false; 
			Account acct = bank.getAccount(sender, args[0]);
			if (acct == null) {
				msg(sender, ChatColor.RED, args[0] + " is not a valid account name");
				return true;
			}
			double amt = Double.valueOf(args[1]);
			if (amt > 0) {
				acct.clear();
				acct.deposit(amt);
				acct.audit(sender);
				acct.pushAccount();
			} else {
				msg(sender, ChatColor.RED, "Invalid amount, must be greater than 0");
			}
			return true;
		}
		
		/********** deposit Command *************************************************/

		if (cmd.getName().equalsIgnoreCase(deposit)) {
			if (args.length != 2) // requires <player> <amount>
				return false; 
			Account acct = bank.getAccount(sender, args[0]);
			if (acct == null) {
				msg(sender, ChatColor.RED, args[0] + " is not a valid account name");
				return true;
			}
			double amt = Double.valueOf(args[1]);
			if (amt > 0) {
				acct.deposit(amt);
				acct.audit(sender);
				acct.pushAccount();
			} else {
				msg(sender, ChatColor.RED, "Invalid amount, must be greater than 0");
			}

			return true;
		}
		
		/********** deduct Command *************************************************/

		if (cmd.getName().equalsIgnoreCase(deduct)) {
			if (args.length != 2) // requires <player> <amount>
				return false; 
			Account acct = bank.getAccount(sender, args[0]);
			if (acct == null) {
				msg(sender, ChatColor.RED, args[0] + " is not a valid account name");
				return true;
			}
			double amt = Double.valueOf(args[1]);
			if (amt > 0) {
				if (acct.withdraw(amt)) {
					acct.audit(sender);
					acct.pushAccount();
				}
				else
					msg(sender, ChatColor.RED, "Amount is larger than account balance");
			} else {
				msg(sender, ChatColor.RED, "Invalid amount, must be greater than 0");
			}
			return true;
		}
		
		return false;
	}
	
	public static void msg(CommandSender sender, ChatColor color, String text) {
		
		if (sender instanceof Player) {
			((Player)sender).sendMessage(color + text);
		} else
			sender.sendMessage(text);
		
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
			double x = Double.valueOf(part[1]);
			double y = Double.valueOf(part[2]);
			double z = Double.valueOf(part[3]);
			
			World w = getServer().getWorld(worldname);
			if (worldname == null) {
				getLogger().log(Level.WARNING, "Invalid world in sign location: " + spec);
				return false;
			}
			
			Location loc = new Location(w, x, y, z);
			Block bs = loc.getBlock();
			if (bs != null && bs.getType() == Material.WALL_SIGN) {
				Sign s = (Sign)bs.getState();
				String line1 = s.getLine(0);
				if (line1.endsWith(money_sign)) {
					bs.setMetadata(key_moneysign, new FixedMetadataValue(this, "money"));
					signList.put(loc, spec);
					
					return true;
				}
			}
		} else
			getLogger().log(Level.WARNING, "Invalid sign location: " + spec);
		
		return false;
	}
	
	
	// **** newBankSign:
	//		Add this to the list of bank signs
	
	private void newBankSign(Sign sign) {
		Location loc = sign.getLocation();
		StringBuffer buf = new StringBuffer();
		Formatter f = new Formatter(buf);
		f.format("%s,%d,%d,%d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
				
		getLogger().info("new bank sign: " + buf);
		signList.put(loc, buf.toString());
		signListChanged = true;
		f.close();
		
		sign.setMetadata(key_moneysign, new FixedMetadataValue(this, "money"));
	}
}

BankMaster
==============

Banking service, maintains player accounts, accumulates daily interest
Admins create banks by posting a sign at the branch location

Commands: bank
	Syntax: /bank
	Displays the player's current account value

Admin Commands:
	audit <name>
	Displays the account status for player <name>

	bankmaster|master|bm set <name> <amount>
	Forces the current account value for player <name> to <amount>
	e.g., /setmoney george 521.50
	
	bankmaster|master|bm add <name> <amount>
	Adds the specified <amount> to player <name> account
	
	bankmaster|master|bm rem <name> <amount>
	Removes the specified <amount> from player <name> account
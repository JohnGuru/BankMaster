BankMaster
==============

Banking service, maintains player accounts, accumulates daily interest.
Supports player loans up to a config specified limit.
Admins create signs for Bank tellers, ATMs, Loans, XP Banks.
(XP Banks not yet supported.)

Players update their account using a GUI that allows them to move currency
(supported block types must be declared in the config) between their player
inventory and an upper bank (Account) inventory. To add money to an account,
just move blocks into the bank account inventory; to subtract money, move
the currency blocks from the bank into the player inventory. That's all
there is to it.

Loans are managed with a separate Loans sign. The GUI presents the loan
officer's inventory of available currency in the upper part, and the player's
inventory in the lower part. To borrow money, just move blocks from the
upper inventory into the player's inventory. To repay a loan, move blocks
in the opposite direction. Loans are not automatically repaid, and adding
to the player's bank account does not repay the loan. Payments can only be
transacted at a Loan sign.

XP banking is planned but not yet supported.
Command /bank pay <player> <amount> is planned but not yet supported.

Note that admin bankmaster commands (set,add,rem) can accept player names
instead of UUIDs, but the command will be rejected unless the player is
online, thus assuring that the playername refers to the correct account.
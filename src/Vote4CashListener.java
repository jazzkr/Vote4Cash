import com.vexsoftware.votifier.Votifier;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VoteListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

public class Vote4CashListener implements VoteListener, Listener {
	private static final Logger log = Logger.getLogger("Vote4CashListener");

	private static Votifier v = null;
	private static Economy econ = null;

	private static boolean broadcast;
	private static double reward;
	private static String msg;
	private static String broadcastMsg;
	private static String currencyS;
	private static String currencyP;

	private static File dataFolder;
	private static File propertiesFile;
	private static File dataFile;

	private static ArrayList<String> pending;

	public Vote4CashListener() {

		v = Votifier.getInstance();

		// Set up all files
		dataFolder = v.getDataFolder();
		propertiesFile = new File(dataFolder, "Vote4Cash.properties");
		dataFile = new File(dataFolder, "V4CPending.txt");

		// Create a new data file with an empty data array in it if there isn't one already
		if (!dataFile.exists()) {
			ArrayList<String> empty = new ArrayList<String>();
			save(empty);
		}

		if (v != null) {
			Properties pro = new Properties();

			// Create properties file
			if (!propertiesFile.exists()) {
				pro.setProperty("reward", "50");
				pro.setProperty("player-voted-msg", "[Vote4Cash] Thank you for voting %PLAYER%! To show our appreciation here is %REWARD% %CURRENCY%!");
				pro.setProperty("broadcast-msg", "[Server] [Vote4Cash] %PLAYER% has voted and received %REWARD% %CURRENCY%. Thank you %PLAYER%!");
				pro.setProperty("currency-name-singular", "dollar");
				pro.setProperty("currency-name-plural", "dollars");
				pro.setProperty("broadcast", "true");
				try {
					pro.store(new FileOutputStream(propertiesFile), "Vote4Cash Properties");
				} catch (Exception e) {
					log.severe("[Vote4Cash] Error creating new properties file!");
				}
			}

			// Load properties file
			try {
				pro.load(new FileInputStream(propertiesFile));
				reward = Double.parseDouble(pro.getProperty("reward"));
				msg = pro.getProperty("player-voted-msg");
				broadcastMsg = pro.getProperty("broadcast-msg");
				currencyS = pro.getProperty("currency-name-singular");
				currencyP = pro.getProperty("currency-name-plural");
				broadcast = Boolean.parseBoolean(pro.getProperty("broadcast"));
			} catch (Exception e) {
				log.severe("[Vote4Cash] Error reading existing properties file!");
			}

			// Hook to vault & get economy
			if (v.getServer().getPluginManager().getPlugin("Vault") != null) {
				try {
				RegisteredServiceProvider<Economy> economyProvider = v.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
				econ = economyProvider.getProvider();
				}
				catch(Exception e) {
					log.severe("[Vote4Cash] Error hooking to Vault! Vote4Cash Listener will not work!");
					log.severe("[Vote4Cash] Error is: "+e.getMessage()+" from "+e.getCause()+".");
				}
			} else {
				log.severe("[Vote4Cash] Could not find Vault! Vote4Cash Listener will not work!");
			}

			// Load pending players from data file
			pending = new ArrayList<String>();
			try {
				BufferedReader br = new BufferedReader(new FileReader(dataFile));
				br.readLine(); // Skip first line of text always
				String text;
				while ((text = br.readLine()) != null) {
					pending.add(text);
				}
				br.close();
			} catch (Exception e) {
				log.severe("[Vote4Cash] Error reading data file! Delayed payment will probably not work.");
			}

			// Register a player listener to do things on player log in
			v.getServer().getPluginManager().registerEvents(this, v);
		}
	}

	public void voteMade(Vote vote) {
		if (econ != null) {
			String player = vote.getUsername();

			// Check if they are on server for instant payment, otherwise put on waiting list if not already on it
			Player[] players = v.getServer().getOnlinePlayers();
			for (int i = 0; i < players.length; i++) {
				String playerName = players[i].getName();
				if (player.equalsIgnoreCase(playerName)) {
					pay(players[i]);
					return;
				}
			}

			// If they got this far they are not on server so check if they are already on pending list
			for (int i = 0; i < pending.size(); i++) {
				if (pending.get(i).equals(player)) {
					log.info("[Vote4Cash] " + player + " is not on the server but is already on the waiting list to receive money, will not submit again.");
					return;
				}
			}

			// Now that they are not on the pending list or on the server, make an entry for them in the pending list!
			log.info("[Vote4Cash] " + player + " was not found on the server, so he will be given money on next login.");
			pending.add(player);
			save(pending);
		}
	}

	//Put in custom variables
	public String formatOutput(String txt, String player) {
		String[] split = txt.split("%");
		String returnString = "";

		for (int i = 0; i < split.length; i++) {
			if (split[i].equalsIgnoreCase("PLAYER")) {
				split[i] = player;
			}
			if (split[i].equalsIgnoreCase("REWARD")) {
				split[i] = Double.toString(reward);
			}
			if (split[i].equalsIgnoreCase("CURRENCY")) {
				if (reward > 1) {
					split[i] = currencyP;
				} else {
					split[i] = currencyS;
				}
			}
			returnString = returnString + split[i];
		}
		return parseColors(returnString);
	}
	
	//Put in coloured text
	public String parseColors(String txt) {
		String[] split = txt.split("&");
		String returnString = "";

		for (int i = 0; i < split.length; i++) {
			if (split[i].startsWith("AQUA")) {
				returnString = returnString + (ChatColor.AQUA + (split[i].substring(4)));
			}
			else if (split[i].startsWith("BLACK")) {
				returnString = returnString + (ChatColor.BLACK + (split[i].substring(5)));
			}
			else if (split[i].startsWith("BLUE")) {
				returnString = returnString + (ChatColor.BLUE + (split[i].substring(4)));
			}
			else if (split[i].startsWith("DARK_AQUA")) {
				returnString = returnString + (ChatColor.DARK_AQUA + (split[i].substring(9)));
			}
			else if (split[i].startsWith("DARK_BLUE")) {
				returnString = returnString + (ChatColor.DARK_BLUE + (split[i].substring(9)));
			}
			else if (split[i].startsWith("DARK_GRAY")) {
				returnString = returnString + (ChatColor.DARK_GRAY + (split[i].substring(9)));
			}
			else if (split[i].startsWith("DARK_GREEN")) {
				returnString = returnString + (ChatColor.DARK_GREEN + (split[i].substring(10)));
			}
			else if (split[i].startsWith("DARK_PURPLE")) {
				returnString = returnString + (ChatColor.DARK_PURPLE + (split[i].substring(11)));
			}
			else if (split[i].startsWith("DARK_RED")) {
				returnString = returnString + (ChatColor.DARK_RED + (split[i].substring(8)));
			}
			else if (split[i].startsWith("GOLD")) {
				returnString = returnString + (ChatColor.GOLD + (split[i].substring(4)));
			}
			else if (split[i].startsWith("GRAY")) {
				returnString = returnString + (ChatColor.GRAY + (split[i].substring(4)));
			}
			else if (split[i].startsWith("GREEN")) {
				returnString = returnString + (ChatColor.GREEN + (split[i].substring(5)));
			}
			else if (split[i].startsWith("LIGHT_PURPLE")) {
				returnString = returnString + (ChatColor.LIGHT_PURPLE + (split[i].substring(12)));
			}
			else if (split[i].startsWith("RED")) {
				returnString = returnString + (ChatColor.RED + (split[i].substring(3)));
			}
			else if (split[i].startsWith("WHITE")) {
				returnString = returnString + (ChatColor.WHITE + (split[i].substring(5)));
			}
			else if (split[i].startsWith("YELLOW")) {
				returnString = returnString + (ChatColor.YELLOW + (split[i].substring(6)));
			}
			else {
				returnString = returnString + split[i];
			}
		}
		return returnString;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent e) {
		if (pending.isEmpty()) {
			return;
		}
		String player = e.getPlayer().getName();
		for (int i = 0; i < pending.size(); i++) {			
			if (player.equalsIgnoreCase(pending.get(i))) {
				log.info("[Vote4Cash] Found " + player + " in pending list. Paying now!");
				pay(e.getPlayer());
				pending.remove(i);
				save(pending);
				return;
			}
		}
	}

	public void pay(Player player) {
		//Transaction through vault
		EconomyResponse r = econ.depositPlayer(player.getName(), reward);
		
		if (r.transactionSuccess()) {
			//Message to player
			player.sendMessage(formatOutput(msg, player.getName()));
			//Message to console
			log.info("[Vote4Cash] " + player.getName() + " has just received " + reward + " " + (reward > 1 ? currencyP : currencyS) + " for voting.");
			//Message to server (if enabled)
			if (broadcast) {
				v.getServer().broadcastMessage(formatOutput(broadcastMsg, player.getName()));
			}
			return;
		} else {
			//Message to player
			player.sendMessage(ChatColor.RED + "[Vote4Cash] Error giving money:" + r.errorMessage);
			//Message to console
			log.info("[Vote4Cash] " + player.getName() + " could not be given money for voting. Here is the error: " + r.errorMessage);
			return;
		}
	}

	public void save(ArrayList<String> al) {
		if (dataFile.exists()) {
			dataFile.delete();
		}
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dataFile));
			bw.write("#This is the payment pending list for the Vote4Cash Listener, add or remove from this data as you see fit.");
			bw.newLine();
			for (int i = 0; i < al.size(); i++) {
				bw.write(al.get(i));
				bw.newLine();
			}
			bw.close();
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error saving list of pending players!");
		}
	}
}
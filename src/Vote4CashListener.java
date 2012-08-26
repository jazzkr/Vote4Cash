import com.vexsoftware.votifier.Votifier;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VoteListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
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
	private static boolean votenag;
	private static boolean collectHist;
	
	private static double reward;
	private static int nagInterval;
	private static int nagJoinDelay;
	private static String msg;
	private static String broadcastMsg;
	private static String nagMsg;
	private static String virginNagMsg;
	private static String currencyS;
	private static String currencyP;

	private static String[] exceptions = {"Test Notification", "Anonymous", ""};

	private static File dataFolder;
	private static File v4cFolder;
	private static File propertiesFile;
	private static File dataFile;
	private static File histFile;
	private static File lastVoteFile;

	private static ArrayList<String> pending;

	public Vote4CashListener() {

		v = Votifier.getInstance();

		// Set up all files
		dataFolder = v.getDataFolder();
		v4cFolder = new File(dataFolder, "Vote4Cash");
		if (!v4cFolder.exists()) v4cFolder.mkdir();
		propertiesFile = new File(v4cFolder, "Vote4Cash.properties");
		dataFile = new File(v4cFolder, "V4CPending.txt");
		histFile = new File(v4cFolder, "V4CHistory.txt");
		lastVoteFile = new File(v4cFolder, "V4CLastVote.txt");

		// Create a new data file with an empty data array in it if there isn't one already
		if (!dataFile.exists()) {
			ArrayList<String> empty = new ArrayList<String>();
			savePending(empty);
		}

		if (v != null) {			
			// Load or create properties file
			if (!propertiesFile.exists()) {
				createProperties();
			}
			loadProperties();

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

			pending = loadPending(); 

			// Register a player listener to do things on player log in
			v.getServer().getPluginManager().registerEvents(this, v);
		}
	}

	public void voteMade(Vote vote) {
		if (econ == null) return;
		if (v == null) return;

		String player = vote.getUsername();

		//Skip exceptions
		for (String ex: exceptions) {
			if (player.equals(ex)) {
				log.info("[Vote4Cash] Exception \""+ex+"\" ignored.");
				return;
			}
		}

		// Check if they are on server for instant payment, otherwise put on waiting list if not already on it
		Player[] players = v.getServer().getOnlinePlayers();
		for (int i = 0; i < players.length; i++) {
			String playerName = players[i].getName();
			if (player.equalsIgnoreCase(playerName)) {
				pay(players[i], 1);
				return;
			}
		}

		// Now that they are not on the server, make an entry for them in the pending list!
		log.info("[Vote4Cash] " + player + " was not found on the server, so they will be given money on next login.");
		pending.add(player);
		savePending(pending);
	}

	public void createProperties() {
		//Create new properties file by writing it line by line, to preserve order
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(propertiesFile));
			bw.write("#Vote4Cash Properties");
			bw.newLine();
			bw.write("reward=50");
			bw.newLine();
			bw.write("player-voted-msg=[Vote4Cash] Thank you for voting %PLAYER%! To show our appreciation here is %REWARD% %CURRENCY%!");
			bw.newLine();
			bw.write("broadcast-msg=[Vote4Cash] %PLAYER% has voted and received %REWARD% %CURRENCY% for voting %VOTES% time(s). Thank you %PLAYER%!");
			bw.newLine();
			bw.write("currency-name-singular=dollar");
			bw.newLine();
			bw.write("currency-name-plural=dollars");
			bw.newLine();
			bw.write("broadcast=true");
			bw.newLine();
			bw.write("nag-players=false");
			bw.newLine();
			bw.write("nag-interval-hours=24");
			bw.newLine();
			bw.write("nag-delay-on-login-seconds=5");
			bw.newLine();
			bw.write("nag-msg=[Vote4Cash] Hello %PLAYER%! This is just a reminder that your last vote was %HOURS% hours ago and you are now eligible to vote again.");
			bw.newLine();
			bw.write("never-voted-nag-msg=[Vote4Cash] Hello %Player%! Did you know you can get extra cash by voting for this server?");
			bw.newLine();
			bw.write("collect-history=false");
			bw.newLine();
			bw.close();
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error creating new properties file!");
		}
		loadProperties();
	}

	public void loadProperties() {
		Properties pro = new Properties();
		// Load properties file
		try {
			pro.load(new FileInputStream(propertiesFile));
			reward = Double.parseDouble(pro.getProperty("reward"));
			msg = pro.getProperty("player-voted-msg");
			broadcastMsg = pro.getProperty("broadcast-msg");
			currencyS = pro.getProperty("currency-name-singular");
			currencyP = pro.getProperty("currency-name-plural");
			broadcast = Boolean.parseBoolean(pro.getProperty("broadcast"));
			votenag = Boolean.parseBoolean(pro.getProperty("nag-players"));
			nagInterval = Integer.parseInt(pro.getProperty("nag-interval-hours"));
			nagJoinDelay = Integer.parseInt(pro.getProperty("nag-delay-on-login-seconds"));
			nagMsg = pro.getProperty("nag-msg");
			virginNagMsg = pro.getProperty("never-voted-nag-msg");
			collectHist = Boolean.parseBoolean(pro.getProperty("collect-history"));
			
			//Delete histFile or lastVoteFile if not in use (keep it simple!)
			if (!collectHist && histFile.exists()) {
				histFile.delete();
			}
			if (!votenag && lastVoteFile.exists()) {
				lastVoteFile.delete();
			}
			
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error reading existing properties file, generating new one...");
			createProperties();
		}
	}
	
	//Put in custom variables
	public String formatOutput(String txt, String player, double reward, int times, long hours) {
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
			if (split[i].equalsIgnoreCase("VOTES")) {
				split[i] = Integer.toString(times);
			}
			if (split[i].equalsIgnoreCase("HOURS")) {
				split[i] = Long.toString(hours);
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

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(final PlayerJoinEvent e) {
		//Load from file on each join
		pending = loadPending();
		loadProperties();

		String player = e.getPlayer().getName();

		//Player nag function
		if (votenag) {
			HashMap<String, String> lv = getLastVoteData();
			//If player has voted before...
			if (lv.containsKey(player)) {
				String date = lv.get(player);
				DateFormat df = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss");
				Date then;
				try {
					then = df.parse(date);
				} catch (ParseException ex) {
					log.severe("[Vote4Cash] Unable to parse date object from file!");
					then = new GregorianCalendar().getTime();
				}
				Date now = new GregorianCalendar().getTime();
				//Get difference in hours
				final long diff = ((((now.getTime() - then.getTime())/1000)/60)/60);
				//If enough time has gone by, nag player
				if (diff >= nagInterval) {
					Bukkit.getScheduler().scheduleSyncDelayedTask(v, new Runnable() {
						public void run() {
							log.info("[Vote4Cash] Nagging player "+e.getPlayer().getName()+", hasn't voted in "+diff+" hours.");
							e.getPlayer().sendMessage(formatOutput(nagMsg, e.getPlayer().getName(), 0, 0, diff));
						}
					}, (nagJoinDelay*20));
				}
			//If player has never voted before
			} else {
				e.getPlayer().sendMessage(formatOutput(virginNagMsg, player, 0, 0, 0));
			}
		}
		
		//If no pending, no further point
		if (pending.isEmpty()) return;
		
		//Make new pending to remove from
		ArrayList<String> newPending = new ArrayList<String>();
		newPending.addAll(pending);
		
		//Count all the times this name shows up in list
		int times = 0;
		for (String s: pending) {			
			if (player.equalsIgnoreCase(s)){
				times++;
				newPending.remove(s);
			}
		}
		
		if (times == 0) return;
		
		//If at least once then pay them
		log.info("[Vote4Cash] Found " + player + " in pending list. Paying now!");
		pay(e.getPlayer(), times);
		savePending(newPending);
	}

	public void pay(Player player, int times) {
		double deposit = reward*times;
		//Transaction through vault
		EconomyResponse r = econ.depositPlayer(player.getName(), deposit);

		if (r.transactionSuccess()) {
			//Message to player
			player.sendMessage(formatOutput(msg, player.getName(), deposit, times, 0));
			//Message to console
			log.info("[Vote4Cash] " + player.getName() + " has just received " + deposit + " " + (deposit > 1 ? currencyP : currencyS) + " for voting " + times + " " +(times > 1 ? "times" : "time") +"." );
			//Message to server (if enabled)
			if (broadcast) {
				v.getServer().broadcastMessage(formatOutput(broadcastMsg, player.getName(), deposit, times, 0));
			}
			//Finally log the vote with history file
			if (collectHist) {
				logVote(player.getName(), times);
			}
		} else {
			//Message to player
			player.sendMessage(ChatColor.RED + "[Vote4Cash] Error giving money: " + r.errorMessage);
			//Message to console
			log.warning("[Vote4Cash] " + player.getName() + " could not be given money for voting. Here is the error: " + r.errorMessage);
		}
	}

	public void savePending(ArrayList<String> al) {
		//Delete existing data file to write a new one
		if (dataFile.exists()) dataFile.delete();
		//Write information
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dataFile));
			bw.write("#This is the payment pending list for the Vote4Cash Listener, add or remove from this data as you see fit.");
			bw.newLine();
			for (String s: al) {
				bw.write(s);
				bw.newLine();
			}
			bw.close();
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error saving list of pending players!");
		}
	}

	public ArrayList<String> loadPending() {
		//New empty arraylist
		pending = new ArrayList<String>();

		//Return if no dataFile
		if (!dataFile.exists()) return pending;

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
		return pending;
	}

	public void logVote(String player, int times) {
		HashMap<String, Integer> history = getHistory();

		//Add new votes to hashmap
		int votes = 0;
		if (history.containsKey(player)) {
			votes = history.get(player);
		}
		//Update vote count
		votes = votes + times;
		history.put(player, votes);
		saveHistory(history);
		//Update last vote info
		setVoted(player);
	}

	public HashMap<String, Integer> getHistory() {
		//Empty hashmap
		HashMap<String, Integer> history = new HashMap<String, Integer>();

		if (!histFile.exists()) return history;

		try {
			BufferedReader br = new BufferedReader(new FileReader(histFile));
			String text;
			while ((text = br.readLine()) != null) {
				//String[0] = name & String[1] = votes
				String[] split = text.split(":");
				history.put(split[0], Integer.parseInt(split[1]));
			}
			br.close();
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error reading history file!");
		}

		return history;
	}

	public void saveHistory(HashMap<String, Integer>history) {
		if (histFile.exists()) histFile.delete();

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(histFile));
			for(String s: history.keySet()) {
				bw.write(s+":"+history.get(s));
				bw.newLine();
			}
			bw.close();
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error saving history file!");
		}
	}

	public void setVoted(String player) {
		Calendar gc = new GregorianCalendar();
		Date d = gc.getTime();
		DateFormat df = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss");
		String date = df.format(d);

		HashMap<String, String> lv = getLastVoteData();
		lv.put(player, date);
		saveLastVoteData(lv);
	}

	public HashMap<String, String> getLastVoteData() {
		HashMap<String, String> lv = new HashMap<String, String>();

		if (!lastVoteFile.exists()) return lv;

		try {
			BufferedReader br = new BufferedReader(new FileReader(lastVoteFile));
			String text;
			while ((text = br.readLine()) != null) {
				//String[0] = name & String[1] = date in dd-MM-yyy
				String[] split = text.split(":");
				lv.put(split[0], split[1]);
			}
			br.close();
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error reading last vote file!");
		}

		return lv;
	}

	public void saveLastVoteData(HashMap<String, String> lv) {
		if (lastVoteFile.exists()) lastVoteFile.delete();

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(lastVoteFile));
			for(String s: lv.keySet()) {
				bw.write(s+":"+lv.get(s));
				bw.newLine();
			}
			bw.close();
		} catch (Exception e) {
			log.severe("[Vote4Cash] Error saving last vote file!");
		}
	}
}
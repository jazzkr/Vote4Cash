import com.vexsoftware.votifier.Votifier;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VoteListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.plugin.RegisteredServiceProvider;

public class Vote4CashListener implements VoteListener {
	private static final Logger log = Logger.getLogger("Vote4CashListener");

	private static Votifier v = null;
	private static Economy econ = null;

	private static double reward;
	private static String msg;
	private static String currencyS;
	private static String currencyP;

	private static File dataFolder;
	private static File propertiesFile;

	public Vote4CashListener() {
		v = Votifier.getInstance();

		if (v != null) {
			Properties pro = new Properties();
			dataFolder = v.getDataFolder();
			propertiesFile = new File(dataFolder, "Vote4Cash.properties");
			
			//Create properties file
			if (!propertiesFile.exists()) {
				pro.setProperty("reward", "50");
				pro.setProperty("player-voted-msg", "Thank you for voting %PLAYER%! To show our appreciation here is %REWARD% %CURRENCY%!");
				pro.setProperty("currency-name-singular", "dollar");
				pro.setProperty("currency-name-plural", "dollars");
				try {
					pro.store(new FileOutputStream(propertiesFile), "Vote4Cash Properties");
				} catch (Exception e) {
					log.severe("[Vote4Cash] Error creating new properties file!");
				}
			}
			
			//Load properties file
			try {
				pro.load(new FileInputStream(propertiesFile));
				reward = Double.parseDouble(pro.getProperty("reward"));
				msg = pro.getProperty("player-voted-msg");
				currencyS = pro.getProperty("currency-name-singular");
				currencyP = pro.getProperty("currency-name-plural");
			} catch (Exception e) {
				log.severe("[Vote4Cash] Error reading existing properties file!");
			}
			
			//Hook to vault & get economy
			if (v.getServer().getPluginManager().getPlugin("Vault") != null) {
				RegisteredServiceProvider<Economy> economyProvider = v.getServer().getServicesManager().getRegistration(Economy.class);
				econ = economyProvider.getProvider();
			} else {
				log.severe("[Vote4Cash] Could not find Vault! Vote4Cash Listener will not work!");
			}
		}
	}

	public void voteMade(Vote vote) {
		if (econ != null) {
			String player = vote.getUsername();
			if (v.getServer().getPlayer(player) != null) {
				EconomyResponse r = econ.depositPlayer(player, reward);
				if (r.transactionSuccess()) {
					v.getServer().getPlayer(player).sendMessage(formatOutput(msg,player));
					log.info("[Vote4Cash] " + player + " just voted and received "+reward+" "+(reward>1?currencyP:currencyS)+".");
				} else {
					v.getServer().getPlayer(player).sendMessage("Error giving money:" + r.errorMessage);
					log.info("[Vote4Cash] " + player + " just voted but money was not given. Here is the error: " + r.errorMessage);
				}
			} else {
				log.info("[Vote4Cash] " + player + " was not found on the server, therefore could not be given money.");
			}
		}
	}
	
	public String formatOutput(String mess, String p) {
		String[] split = mess.split("%");
		String returnS = "";
		for (int i = 0; i < split.length; i++) {
			if (split[i].equals("PLAYER")) {
				split[i] = p;
			}
			if (split[i].equals("REWARD")) {
				split[i] = Double.toString(reward);
			}
			if (split[i].equals("CURRENCY")) {
				if (reward > 1) {
					split[i] = currencyP;
				}
				else {
					split[i] = currencyS;
				}
			}
			returnS = returnS + split[i];
		}
		return returnS;
	}
}
package com.booksaw.betterTeams;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import com.booksaw.betterTeams.events.BelowNameManagement.BelowNameType;
import com.booksaw.betterTeams.message.MessageManager;

/**
 * This class is used to manage a team and all of it's participants
 * 
 * @author booksaw
 *
 */
public class Team {

	/**
	 * This is used to track if the score has been changed for any users
	 */
	public static boolean scoreChanges = false;

	/**
	 * This is used to track if there have been any changes to the teams balance
	 */
	public static boolean moneyChanges = false;

	/**
	 * Used to store all active teams
	 */
	private static HashMap<UUID, Team> teamList = new HashMap<>();

	/**
	 * Used to get the team with the provided ID
	 * 
	 * @param ID the ID of the team
	 * @return the team with that ID [null - the team does not exist]
	 */
	@Nullable
	public static Team getTeam(UUID ID) {
		return teamList.get(ID);
	}

	private static boolean logChat;

	/**
	 * Used to get the team by it's display name
	 * 
	 * @param name the display name of the team
	 * @return the team with that display name [null - no team could be found]
	 */
	@Nullable
	public static Team getTeam(String name) {
		for (Entry<UUID, Team> temp : teamList.entrySet()) {
			if (temp.getValue().getName().equals(name)) {
				return temp.getValue();
			}
		}
		return null;
	}

	/**
	 * Used to find the team that a specified player is in, this is the highest time
	 * complexity search to find a team (O(n^2)) so only use when the other provided
	 * methods are not possible
	 * 
	 * @param player the player which is in a team
	 * @return the team they are in [null - they are not in a team]
	 */
	@Nullable
	public static Team getTeam(OfflinePlayer player) {
		for (Entry<UUID, Team> temp : teamList.entrySet()) {
			for (TeamPlayer teamPlayer : temp.getValue().getMembers()) {
				if (teamPlayer.getPlayer().getUniqueId().compareTo(player.getUniqueId()) == 0) {
					return temp.getValue();
				}
			}
		}
		return null;
	}

	/**
	 * This method is used at the start of the program to load all the teams into
	 * memory
	 */
	public static void loadTeams() {

		logChat = Main.plugin.getConfig().getBoolean("logTeamChat");
		teamList = new HashMap<UUID, Team>();
		for (String IDString : Main.plugin.getTeams().getStringList("teams")) {
			UUID ID = UUID.fromString(IDString);
			teamList.put(ID, new Team(ID));
		}

	}

	/**
	 * This method is used to create a new team with the specified name
	 * <p>
	 * Checks are not carried out to ensure that the name is available, so that
	 * should be done before this method is called
	 * </p>
	 * 
	 * @param name  the name of the new team
	 * @param owner the owner of the new team (the player who ran /team create)
	 */
	public static void createNewTeam(String name, Player owner) {

		UUID ID = UUID.randomUUID();
		// ensuring the ID is unique
		while (getTeam(ID) != null) {
			ID = UUID.randomUUID();
		}
		teamList.put(ID, new Team(name, ID, owner));

		// updating the list of teams
		List<String> teams = Main.plugin.getTeams().getStringList("teams");
		teams.add(ID.toString());
		Main.plugin.getTeams().set("teams", teams);

		Main.plugin.saveTeams();

		if (Main.plugin.nameManagement != null) {
			Main.plugin.nameManagement.displayBelowName(owner);
		}
	}

	/**
	 * @return a list of all teams
	 */
	public static HashMap<UUID, Team> getTeamList() {
		return teamList;
	}

	/**
	 * This method is used to sort all the teams into an arry ranking from hightest
	 * score to lowest
	 * 
	 * @return the array of teams in order of their rank
	 */
	public static Team[] sortTeams() {
		Team[] rankedTeams = new Team[teamList.size()];

		int count = teamList.size() - 1;
		// adding them to a list to sort
		for (Entry<UUID, Team> team : teamList.entrySet()) {
			if (team.getValue().getTeamRank() == -1) {
				rankedTeams[count--] = team.getValue();
			} else {
				if (team.getValue().getTeamRank() >= count || rankedTeams[team.getValue().getTeamRank()] != null) {
					rankedTeams[count--] = team.getValue();
				} else {
					rankedTeams[team.getValue().getTeamRank()] = team.getValue();
				}
			}
		}

		for (int i = 0; i < rankedTeams.length - 1; i++) {
			boolean changes = false;

			for (int j = 0; j < rankedTeams.length - i - 1; j++) {
				if (rankedTeams[j].getScore() < rankedTeams[j + 1].getScore()) {
					Team temp = rankedTeams[j];
					rankedTeams[j] = rankedTeams[j + 1];
					rankedTeams[j + 1] = temp;
					changes = true;
				}
			}

			if (!changes) {
				break;
			}
		}

		for (int i = 0; i < rankedTeams.length; i++) {
			rankedTeams[i].setTeamRank(i);
		}

		return rankedTeams;
	}

	/**
	 * The ID of the team (this is a unique identifier of the team which will never
	 * change)
	 */
	UUID ID;

	/**
	 * The name of the team, this can be changed after the creation of a teams, so
	 * do not store references to it
	 */
	private String name;

	/**
	 * The description of a team, used in /team info
	 */
	private String description;

	/**
	 * If the team is open or invite only
	 */
	private boolean open;

	/**
	 * The location of the teams home (/team home)
	 */
	private Location teamHome = null;

	/**
	 * This is a list of invited players to this team since the last restart of the
	 * server
	 */
	private List<UUID> invitedPlayers = new ArrayList<UUID>();

	private List<TeamPlayer> members;
	private List<UUID> bannedPlayers;

	/**
	 * The score for the team
	 */
	private int score;

	/**
	 * The money that the team has
	 */
	private double money;

	ChatColor color;
	/**
	 * the rank of the team
	 */
	private transient int rank;

	/**
	 * this is used to load a team from the configuration file
	 * 
	 * @param ID the ID of the team to load
	 */
	public Team(UUID ID) {

		this.ID = ID;
		FileConfiguration config = Main.plugin.getTeams();

		name = getString(config, "name");
		description = getString(config, "description");
		open = getBoolean(config, "open");
		score = getInteger(config, "score");
		money = getDouble(config, "money");
		String colorStr = getString(config, "color");
		if (colorStr == null) {
			this.color = ChatColor.GOLD;
			setValue(Main.plugin.getTeams(), "color", color.getChar());
			Main.plugin.saveTeams();
		} else {
			color = ChatColor.getByChar(getString(config, "color").charAt(0));
		}
		members = new ArrayList<>();
		for (String string : getStringList(config, "players")) {
			members.add(new TeamPlayer(string));
		}

		bannedPlayers = new ArrayList<>();
		for (String string : getStringList(config, "bans")) {
			bannedPlayers.add(UUID.fromString(string));
		}

		String teamHomeStr = getString(config, "home");
		if (teamHomeStr != null && !teamHomeStr.equals("")) {
			teamHome = getLocation(teamHomeStr);
		}
		rank = -1;

	}

	/**
	 * Creates a new team with the provided name
	 * <p>
	 * This is a private method as the creation of a new team should be done by the
	 * Team.createNewTeam(name) method
	 * </p>
	 * 
	 * @param name  The selected name for the team
	 * @param ID    The UUID of the team
	 * @param owner The owner of the team (whoever initiated the creation of the
	 *              team)
	 */
	private Team(String name, UUID ID, Player owner) {
		this.ID = ID;

		FileConfiguration config = Main.plugin.getTeams();
		setValue(config, "name", name);
		setValue(config, "description", "");
		this.name = name;
		this.description = "";
		setValue(config, "open", false);
		open = false;
		setValue(config, "score", 0);
		score = 0;
		setValue(config, "money", 0);
		money = 0;
		setValue(config, "home", "");
		rank = -1;
		color = ChatColor.GOLD;
		setValue(config, "color", color.getChar());

		members = new ArrayList<>();
		members.add(new TeamPlayer(owner, PlayerRank.OWNER));
		savePlayers(config);
		bannedPlayers = new ArrayList<>();

		/*
		 * do not need to save config as createNewTeam saves the config after more
		 * settings modified
		 */
	}

	/**
	 * Used to load a string attribute from file, this is used instead of the direct
	 * call to ensure any changes to the config reading system can be updated
	 * quickly
	 * 
	 * @param config    the config which stores the team's information
	 * @param attribute the reference that the value is stored under
	 * @return the loaded value
	 */
	private String getString(FileConfiguration config, String attribute) {
		return config.getString("team." + ID + "." + attribute);
	}

	/**
	 * Used to load a boolean attribute from file, this is used instead of the
	 * direct call to ensure any changes to the config reading system can be updated
	 * quickly
	 * 
	 * @param config    the config which stores the team's information
	 * @param attribute the reference that the value is stored under
	 * @return the loaded value
	 */
	private boolean getBoolean(FileConfiguration config, String attribute) {
		return config.getBoolean("team." + ID + "." + attribute);
	}

	/**
	 * Used to load a integer attribute from file, this is used instead of the
	 * direct call to ensure any changes to the config reading system can be updated
	 * quickly
	 * 
	 * @param config    the config which stores the team's information
	 * @param attribute the reference that the value is stored under
	 * @return the loaded value
	 */
	private int getInteger(FileConfiguration config, String attribute) {
		return config.getInt("team." + ID + "." + attribute);
	}

	/**
	 * Used to load a double attribute from file, this is used instead of the direct
	 * call to ensure any changes to the config reading system can be updated
	 * quickly
	 * 
	 * @param config    the config which stores the team's information
	 * @param attribute the reference that the value is stored under
	 * @return the loaded value
	 */
	private double getDouble(FileConfiguration config, String attribute) {
		return config.getDouble("team." + ID + "." + attribute);
	}

	/**
	 * Used to load a string list from file, this is used instead of the direct call
	 * to ensure any changes to the config reading system can be updated quickly
	 * 
	 * @param config    the config which stores the team's information
	 * @param attribute the reference that the value is stored under
	 * @return the loaded value
	 */
	private List<String> getStringList(FileConfiguration config, String attribute) {
		return config.getStringList("team." + ID + "." + attribute);
	}

	/**
	 * Used to store a value to file, this is used instead of the direct call to
	 * ensure any changes to the config reading system can be updated quickly
	 * 
	 * @param config    the configuration to save the value to
	 * @param attribute the attribute to save it under
	 * @param value     the value of the attribute
	 */
	private void setValue(FileConfiguration config, String attribute, Object value) {
		config.set("team." + ID + "." + attribute, value);
	}

	/**
	 * Used to get the current name of the team
	 * 
	 * @return the name of the team
	 */
	public String getName() {
		if (Main.plugin.getConfig().getBoolean("colorTeamName") && color != null) {
			return color + name;
		}
		return name;
	}

	/**
	 * Returns if the team is open
	 * 
	 * @return [true - anyone can join the team] [false - the team is invite only]
	 */
	public boolean isOpen() {
		return open;
	}

	public void setOpen(boolean open) {
		this.open = open;
		setValue(Main.plugin.getTeams(), "open", open);
		Main.plugin.saveTeams();
	}

	/**
	 * @return the location of the team's home
	 */
	public Location getTeamHome() {
		return teamHome;
	}

	/**
	 * @return The description of the team
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Used to change the team description
	 * 
	 * @param description the new team description
	 */
	public void setDescription(String description) {
		this.description = description;
		setValue(Main.plugin.getTeams(), "description", description);
		Main.plugin.saveTeams();
	}

	/**
	 * @return The color of the team
	 */
	public ChatColor getColor() {
		return color;
	}

	/**
	 * Used to change the team color
	 * 
	 * @param color the new team color
	 */
	public void setColor(ChatColor color) {
		this.color = color;
		setValue(Main.plugin.getTeams(), "color", color.getChar());
		Main.plugin.saveTeams();

		if (Main.plugin.nameManagement != null) {

			if (team != null) {
				for (TeamPlayer p : members) {
					if (p.getPlayer().isOnline()) {
						team.removeEntry(p.getPlayer().getName());
					}
				}
				team.unregister();
			}

			team = null;

			for (TeamPlayer p : members) {
				if (p.getPlayer().isOnline()) {
					Main.plugin.nameManagement.displayBelowName(p.getPlayer().getPlayer());
				}
			}
		}
	}

	public List<TeamPlayer> getMembers() {
		return members;
	}

	/**
	 * Used to save the members list to the configuration file
	 * 
	 * @param config the configuration file to store the members list to
	 */
	private void savePlayers(FileConfiguration config) {

		List<String> output = new ArrayList<>();

		for (TeamPlayer player : members) {
			output.add(player.toString());
		}

		setValue(config, "players", output);

	}

	/**
	 * Used to save the bans list to the configuration file
	 * 
	 * @param config the configuration file to store the ban list to
	 */
	private void saveBans(FileConfiguration config) {

		List<String> output = new ArrayList<>();

		for (UUID uuid : bannedPlayers) {
			output.add(uuid.toString());
		}

		setValue(config, "bans", output);

	}

	/**
	 * Used to remove the given player from the team, you must firstly be sure that
	 * the player is in this team (as it is not checked or caught in this method)
	 * 
	 * @param p the player to remove from the team
	 */
	public void removePlayer(OfflinePlayer p) {
		members.remove(getTeamPlayer(p));
		savePlayers(Main.plugin.getTeams());
		Main.plugin.saveTeams();

		if (team != null) {
			Main.plugin.nameManagement.remove(p.getPlayer());
		}
	}

	/**
	 * Used to remove the given teamPlayer from the team, you must firstly be sure
	 * that the player is in this team (as it is not checked or caught in this
	 * method)
	 * 
	 * @param p the player to remove from the team
	 */
	public void removePlayer(TeamPlayer p) {
		members.remove(p);
		savePlayers(Main.plugin.getTeams());
		Main.plugin.saveTeams();

		if (team != null) {
			Main.plugin.nameManagement.remove(p.getPlayer().getPlayer());
		}
	}

	/**
	 * Used to get the teamPlayer version of an included player
	 * 
	 * @param player the player to search for
	 * @return the team player object for that player [null - player is not in the
	 *         team]
	 */
	@Nullable
	public TeamPlayer getTeamPlayer(OfflinePlayer player) {
		for (TeamPlayer teamPlayer : members) {
			if (teamPlayer.getPlayer().getUniqueId().compareTo(player.getUniqueId()) == 0) {
				return teamPlayer;
			}
		}
		return null;
	}

	/**
	 * Used to get all players which have the specified rank within the team
	 * 
	 * @param rank the rank to search for
	 * @return a list of players which have that rank [emtpy list - no players have
	 *         that rank]
	 */
	public List<TeamPlayer> getRank(PlayerRank rank) {
		List<TeamPlayer> toReturn = new ArrayList<>();

		for (TeamPlayer player : members) {
			if (player.getRank() == rank) {
				toReturn.add(player);
			}
		}

		return toReturn;
	}

	/**
	 * This command is used to disband a team, BE CAREFUL, this is irreversible
	 */
	public void disband() {
		// removing it from the team list, the java GC will handle the reset
		teamList.remove(ID);

		// updating the list of teams
		List<String> teams = Main.plugin.getTeams().getStringList("teams");
		teams.remove(ID.toString());
		Main.plugin.getTeams().set("teams", teams);
		Main.plugin.getTeams().set("team." + ID, null);

		Main.plugin.saveTeams();

		if (Main.plugin.nameManagement != null) {

			for (TeamPlayer p : members) {
				if (p.getPlayer().isOnline()) {
					Main.plugin.nameManagement.remove(p.getPlayer().getPlayer());
				}
			}

			team.unregister();
			team = null;

		}
	}

	/**
	 * Used to check if a player is invited to this team
	 * 
	 * @param uuid the UUID of the player to check
	 * @return [true - the player is invited] [false - the player is not invited]
	 */
	public boolean isInvited(UUID uuid) {
		for (UUID temp : invitedPlayers) {
			if (temp.compareTo(uuid) == 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Used to create an invite for the included player to this team
	 * 
	 * @param uniqueId the UUID of the player being invited
	 */
	public void invite(UUID uniqueId) {
		invitedPlayers.add(uniqueId);

	}

	/**
	 * This is used when a player is joining the team
	 * 
	 * @param p the player who is joining the team
	 */
	public void join(Player p) {

		for (TeamPlayer player : members) {
			if (player.getPlayer().isOnline()) {
				MessageManager.sendMessageF((CommandSender) player.getPlayer().getPlayer(), "join.notify",
						p.getDisplayName());
			}
		}

		members.add(new TeamPlayer(p, PlayerRank.DEFAULT));
		savePlayers(Main.plugin.getTeams());
		Main.plugin.saveTeams();

		if (Main.plugin.nameManagement != null) {
			Main.plugin.nameManagement.displayBelowName(p);
		}

	}

	/**
	 * This is used to set the name of the team, it is important that you check that
	 * the name is unique before running this method
	 * 
	 * @param name the new team name
	 */
	public void setName(String name) {
		this.name = name;
		setValue(Main.plugin.getTeams(), "name", name);
		Main.plugin.saveTeams();

		if (Main.plugin.nameManagement != null) {

			if (team != null) {
				for (TeamPlayer p : members) {
					if (p.getPlayer().isOnline()) {
						team.removeEntry(p.getPlayer().getName());
					}
				}
				team.unregister();
			}

			team = null;

			for (TeamPlayer p : members) {
				if (p.getPlayer().isOnline()) {
					Main.plugin.nameManagement.displayBelowName(p.getPlayer().getPlayer());
				}
			}
		}
	}

	/**
	 * This method is used to promote a player to the next applicable rank, this
	 * method does not check the promotion is valid but instead only promotes the
	 * player, see PromoteCommand to see validation
	 * 
	 * @param promotePlayer the player to be promoted
	 */
	public void promotePlayer(TeamPlayer promotePlayer) {
		if (promotePlayer.getRank() == PlayerRank.DEFAULT) {
			promotePlayer.setRank(PlayerRank.ADMIN);
		} else {
			promotePlayer.setRank(PlayerRank.OWNER);
		}

		savePlayers(Main.plugin.getTeams());
	}

	/**
	 * This method is used to demote a player to the next applicable rank, this
	 * method does not check the demotion is valid but instead only promotes the
	 * player, see DemoteCommand to see validation
	 * 
	 * @param demotePlayer the player to be demoted
	 */
	public void demotePlayer(TeamPlayer demotePlayer) {
		if (demotePlayer.getRank() == PlayerRank.OWNER) {
			demotePlayer.setRank(PlayerRank.ADMIN);
		} else {
			demotePlayer.setRank(PlayerRank.DEFAULT);
		}

		savePlayers(Main.plugin.getTeams());
	}

	public void setTeamHome(Location teamHome) {
		this.teamHome = teamHome;
		setValue(Main.plugin.getTeams(), "home", getString(teamHome));
		Main.plugin.saveTeams();
	}

	public void deleteTeamHome() {
		teamHome = null;
		setValue(Main.plugin.getTeams(), "home", "");
		Main.plugin.saveTeams();
	}

	/**
	 * This method is used to convert a string into a location which can be stored
	 * for later use
	 * 
	 * @param loc the string to convert into a location
	 * @return the location which that string reference
	 */
	private Location getLocation(String loc) {
		String[] split = loc.split(":");
		return new Location(Bukkit.getWorld(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]),
				Double.parseDouble(split[3]), Float.parseFloat(split[4]), Float.parseFloat(split[5]));
	}

	/**
	 * This method is used to convert a location into a string which can be stored
	 * in a configuration file
	 * 
	 * @param loc the location to convert into a string
	 * @return the string which references that location
	 */
	private String getString(Location loc) {
		return loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ() + ":" + loc.getYaw()
				+ ":" + loc.getPitch();
	}

	/**
	 * This method is used to add a player to the list of players which are banned
	 * from the team
	 * 
	 * @param player the player to add to the list
	 */
	public void banPlayer(OfflinePlayer player) {
		bannedPlayers.add(player.getUniqueId());
		saveBans(Main.plugin.getTeams());
		Main.plugin.saveTeams();
	}

	/**
	 * This method is used to remove a player from the list of players which are
	 * banned from the team
	 * 
	 * @param player the player to remove from the list
	 */
	public void unbanPlayer(OfflinePlayer player) {
		// used to avoid concurrent modification error
		UUID store = null;
		for (UUID uuid : bannedPlayers) {
			if (player.getUniqueId().equals(uuid)) {
				store = uuid;
			}
		}
		bannedPlayers.remove(store);

		saveBans(Main.plugin.getTeams());
		Main.plugin.saveTeams();
	}

	/**
	 * This method searches the ban list to check if the player is banned
	 * 
	 * @param player the player to check
	 * @return [true - the player is banned] [false - the player isen't banned]
	 */
	public boolean isBanned(OfflinePlayer player) {
		for (UUID uuid : bannedPlayers) {
			if (player.getUniqueId().equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Used when a player sends a message to the team chat
	 * 
	 * @param sender  the player which sent the message to the team chat
	 * @param message the message to send to the team chat
	 */
	public void sendMessage(TeamPlayer sender, String message) {
		ChatColor returnTo = ChatColor.RESET;
		String toTest = MessageManager.getMessage("chat.syntax");
		int value = toTest.indexOf("%s");
		if (value > 2) {
			for (int i = value; i >= 0; i--) {
				if (toTest.charAt(i) == '�') {
					returnTo = ChatColor.getByChar(toTest.charAt(i + 1));
					if (toTest != null) {
						break;
					}
				}
			}
		}

		String fMessage = String.format(MessageManager.getMessage("chat.syntax"),
				sender.getPrefix(returnTo) + sender.getPlayer().getPlayer().getDisplayName(), message);

		for (TeamPlayer player : members) {
			if (player.getPlayer().isOnline()) {
				player.getPlayer().getPlayer().sendMessage(fMessage);
			}
		}

		for (CommandSender temp : Main.plugin.chatManagement.spy) {
			if (temp instanceof Player && getTeamPlayer((Player) temp) != null) {
				continue;
			}
			temp.sendMessage("[BetterTeams][" + getName() + "]" + message);
		}

		if (logChat) {
			Bukkit.getLogger().info("[BetterTeams]" + fMessage);
		}
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
		scoreChanges = true;
		setValue(Main.plugin.getTeams(), "score", score);
		Main.plugin.saveTeams();
	}

	public double getMoney() {
		return money;
	}

	public void setMoney(double money) {
		this.money = money;
		moneyChanges = true;
		setValue(Main.plugin.getTeams(), "money", money);
		Main.plugin.saveTeams();
	}

	/**
	 * @return the rank of the team (-1 if the team has not been ranked)
	 */
	public int getTeamRank() {
		return rank;
	}

	public void setTeamRank(int rank) {
		this.rank = rank;
	}

	org.bukkit.scoreboard.Team team;

	/**
	 * Used throughout all below name management (showing team name above player
	 * name)
	 * 
	 * @param board the scoreboard to add the team to
	 * @param type  the type of the scoreboard team (mainly for prefix / suffix)
	 * @return the team that has been created
	 */
	public org.bukkit.scoreboard.Team getScoreboardTeam(Scoreboard board, BelowNameType type) {
		if (team != null) {
			return team;
		}
		String name = String.format(color + MessageManager.getMessage("nametag.syntax"), getName());
		int attempt = 0;
		do {
			try {
				team = board.registerNewTeam(getName() + ((attempt > 0) ? attempt : ""));

			} catch (Exception e) {
				team = null;
				attempt++;
			}

		} while (team == null);
		if (type == BelowNameType.PREFIX) {
			team.setPrefix(name);
		} else if (type == BelowNameType.SUFFIX) {
			team.setSuffix(name);
		}
		return team;

	}

	public String getBalance() {
		NumberFormat formatter = NumberFormat.getCurrencyInstance();
		String moneyString = formatter.format(money);
		return moneyString.substring(1);
	}

	public void setTitle(TeamPlayer player, String title) {
		player.setTitle(title);
		savePlayers(Main.plugin.getTeams());
	}

}

/*
 * Copyright (c) 2018, Cameron <https://github.com/noremac201>
 * Copyright (c) 2018, Jacob M <https://github.com/jacoblairm>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.BaPB;

import com.google.inject.Provides;
import java.io.*;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Map;
import java.util.HashMap;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;
import net.runelite.client.chat.ChatClient;
import org.apache.commons.text.WordUtils;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

@PluginDescriptor(
	name = "Barbarian Assault Personal Bests",
	description = "Emulates the normal plugin as well as saving personal bests",
	tags = {"minigame", "overlay", "timer", "Personal Bests","Time"}
)
@Slf4j
public class BaPBPlugin extends Plugin
{
	private static final int BA_WAVE_NUM_INDEX = 2;
	private static final int START_WAVE = 1;
    private static final int PREMOVE_Y_THRESHOLD = 5300;
	private static final String ENDGAME_REWARD_NEEDLE_TEXT = "<br>5";
	private double currentpb; //This is to load overall pb
	private double rolecurrentpb; //This is to load role specific pb's and gets set when the role is determined
	private static final String BA_COMMAND_STRING = "!ba";

    // Constants for role-specific widget group IDs
    private static final int BA_ATTACKER_GROUP_ID = 485;
    private static final int BA_COLLECTOR_GROUP_ID = 486;
    private static final int BA_DEFENDER_GROUP_ID = 487;
    private static final int BA_HEALER_GROUP_ID = 488;

	private int gc;

	@Getter(AccessLevel.PACKAGE)
	private int inGameBit = 0;
    private int currentWave = 0;
    private Timers timers = new Timers();
    private Lobby lobby = new Lobby();
	private String round_role;
	private Boolean scanning;
	private int round_roleID;
	private String roundFormat;
    private Map<String, String> currentTeam = new HashMap<>();
    private Boolean isLeader = false;
	//defines all of my specific widgets and icon names could I do it better yes, but like it works
	private Integer BaRoleWidget = 256;
	private Integer BaScrollWidget = 159;
	private Integer leaderID = 8;
	private Integer player1ID = 9;
	private Integer player2ID = 10;
	private Integer player3ID = 11;
	private Integer player4ID = 12;
	private Integer leadericonID = 18;
	private Integer player1iconID = 19;
	private Integer player2iconID = 20;
	private Integer player3iconID = 21;
	private Integer player4iconID = 22;
	private Integer attackerIcon = 20561;
	private Integer defenderIcon = 20566;
	private Integer collectorIcon = 20563;
	private Integer healerIcon = 20569;
	private PrintWriter out;
	private BufferedWriter bw;
	private FileWriter fw;



	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BaPBConfig config;

    @Inject
    private BaPBService service;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private ChatClient chatClient;

	@Inject
	private ScheduledExecutorService executor;

	@Provides
	BaPBConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BaPBConfig.class);
	}


	StringBuilder str = new StringBuilder();
	@Override
	protected void startUp() throws Exception
	{
		File logFile = new File(RUNELITE_DIR, "barbarian-assault-pbs.csv");
		fw = new FileWriter(logFile, true);
		bw = new BufferedWriter(fw);
		out = new PrintWriter(bw);
		//configManager.setRSProfileConfiguration("BaPB", "Recent", roleToDouble("Leech " + "Defender"));

		chatCommandManager.registerCommandAsync(BA_COMMAND_STRING, this::baLookup, this::baSubmit);
		scanning = false;
		str = new StringBuilder();
        currentTeam.clear();
        timers.resetAll();
	}

	@Override
	protected void shutDown() throws Exception
	{
		chatCommandManager.unregisterCommand(BA_COMMAND_STRING);
		scanning = false;
		shutDownActions();
		out.close();
		bw.close();
		fw.close();
		str = new StringBuilder();
        currentTeam.clear();
        timers.resetAll();
        service.shutdown();
	}

	private void shutDownActions() throws IOException
	{
		out.flush();
		bw.flush();
		fw.flush();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) throws IOException//exception required to run .flush()
	{
		switch (event.getGroupId())
		{
			case InterfaceID.BARBASSAULT_WAVECOMPLETE:
			{
                Widget rewardWidget = client.getWidget(InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_QUEENREWARDS);
                double roundSeconds = timers.getRoundSeconds(isLeader);
                if (rewardWidget != null && rewardWidget.getText().contains(ENDGAME_REWARD_NEEDLE_TEXT) && roundSeconds > 0)
				{
                    timers.stopAll();

					if ((roundSeconds < rolecurrentpb || rolecurrentpb == 0.0) && config.Seperate())
					{
						configManager.setRSProfileConfiguration("BaPB", round_role, roundSeconds);
						log.debug("Personal best of: {} saved in {}",roundSeconds, round_role);
					}
					currentpb = getCurrentPB("Barbarian Assault");
					if ((roundSeconds < currentpb || currentpb == 0.0))
					{
						configManager.setRSProfileConfiguration("BaPB", "Barbarian Assault", roundSeconds);
						log.debug("Personal best of: {} saved in Barbarian Assault",roundSeconds);
					}
					//log.info(round_role);
					//log.info(String.valueOf(gameTime.getPBTime()));
					//log.info(String.valueOf(roleToDouble(round_role)));
					//log.info(String.valueOf((gameTime.getPBTime() + roleToDouble(round_role))));
					configManager.setRSProfileConfiguration("BaPB", "Recent", (roundSeconds + roleToDouble(round_role)));
					if(config.Logging())
					{
						str
							.append(Instant.now().toString())
							.append(",")
							.append(String.valueOf(roundSeconds));
						out.println(str);
						str = new StringBuilder();
						shutDownActions();//this guarantees the new line is written to disk(prevents having to do weird jank turn plugin on/off behavior)
					}
                    service.handleRoundEnd(currentTeam, roundFormat, timers, isLeader, client.getLocalPlayer().getName());
					roundFormat = null;
				}

				break;
			}
			case InterfaceID.BARBASSAULT_OVER_RECRUIT_PLAYER_NAMES: {
				scanning = true;
				roundFormat = null;
			}
			case 159: {//this is to set scanning true when scroll is used on someone
				scanning = true;
			}
			case 158: {//this is to set scanning true when scroll is used on someone
				scanning = true;
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event){
		if (event.getGroupId() == BaRoleWidget) scanning = false;//sets scanning to false when leaving w1 or leaving for any reason
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        
        int detectedWave = inWave();
        int detectedLobby = lobby.getLobbyId(wp);
        boolean goodPremove = isGoodPremove(wp);

        // Only scroller should calculate relative spawn point
        Lobby.RelativePoint relPoint = isLeader && config.SubmitQS() ? lobby.getRelativeCoordinates(wp, detectedLobby) : null;

        timers.updateState(detectedWave, detectedLobby, goodPremove, relPoint);
        timers.onGameTick();

		if(scanning) {
			final String player;
			player = client.getLocalPlayer().getName();
			Widget leader = client.getWidget(BaRoleWidget,leaderID);
			Widget leaderIcon = client.getWidget(BaRoleWidget, leadericonID);
			Widget player1 = client.getWidget(BaRoleWidget,player1ID);
			Widget player1Icon = client.getWidget(BaRoleWidget,player1iconID);
			Widget player2 = client.getWidget(BaRoleWidget,player2ID);
			Widget player2Icon = client.getWidget(BaRoleWidget,player2iconID);
			Widget player3 = client.getWidget(BaRoleWidget,player3ID);
			Widget player3Icon = client.getWidget(BaRoleWidget,player3iconID);
			Widget player4 = client.getWidget(BaRoleWidget, player4ID);
			Widget player4Icon = client.getWidget(BaRoleWidget, player4iconID);
			log.debug("Scanning Team");

			if ((player4Icon.getModelId() != leaderIcon.getModelId()) &&  (player4Icon.getModelId() != 65535) && (leaderIcon.getModelId() != 65535)){//this number is the blank icon
				log.debug("Scanning Complete");
				log.debug("Leader is {}", leader.getText());
				log.debug("Player1 is {}", player1.getText());
				log.debug("Player2 is {}", player2.getText());
				log.debug("Player3 is {}", player3.getText());
				log.debug("Player4 is {}", player4.getText());


				if(str.length() == 0  && config.Logging()){
					log.debug("Created Log start");
					str
						.append(leader.getText())
						.append(",")
						.append(IDfinder(leaderIcon.getModelId()))
						.append(",")
						.append(player1.getText())
						.append(",")
						.append(IDfinder(player1Icon.getModelId()))
						.append(",")
						.append(player2.getText())
						.append(",")
						.append(IDfinder(player2Icon.getModelId()))
						.append(",")
						.append(player3.getText())
						.append(",")
						.append(IDfinder(player3Icon.getModelId()))
						.append(",")
						.append(player4.getText())
						.append(",")
						.append(IDfinder(player4Icon.getModelId()))
						.append(",");
				}
				scanning = false;


				for (int i = 8; i < 13; i++) {
					String player_in_list = (client.getWidget(BaRoleWidget, i).getText());
					String playerRole = IDfinder(client.getWidget(BaRoleWidget, (i+10)).getModelId());
					if (player.compareTo(player_in_list) == 0){
						//this checks which location the client is in the scroll
					round_roleID = client.getWidget(BaRoleWidget, (i+10)).getModelId();
					round_role = IDfinder(round_roleID);
					log.debug("Your role has been identified as {}",round_role);
					}
				}




				if((leaderIcon.getModelId() == attackerIcon)&&(player1Icon.getModelId() == collectorIcon)&&(player2Icon.getModelId() == healerIcon)&&(player4Icon.getModelId() == defenderIcon)){
					round_role = "Leech "+round_role;
					log.debug("This has been identified as a leech run as {}",round_role);
					roundFormat = "leech";
				} else if ((leaderIcon.getModelId() == attackerIcon)&&(player1Icon.getModelId() == attackerIcon)&&(player2Icon.getModelId() == healerIcon)&&(player3Icon.getModelId() == collectorIcon)&&(player4Icon.getModelId() == defenderIcon)) {
                    log.debug("This has been identified as a five man run as {}",round_role);
                    roundFormat = "five_man";
                } else {
                    roundFormat = null;
                }


                if(player.contains(leader.getText())){
                    if (leaderIcon.getModelId() == attackerIcon && !"leech".equals(roundFormat)) {
                        round_role = "Main Attacker";
                        log.debug("You have been identified as Main Attacker");
                    }
                    isLeader = true;
				} else {
                    isLeader = false;
                }

                // Save team data for API call
                if(config.SubmitRuns())
                {
                    currentTeam.clear();

                    // Only save current team if leech / five_man
                    if ("five_man".equals(roundFormat) || "leech".equals(roundFormat)) {
                        // Change to Main/2nd Attackers for 5 man
                        if ("five_man".equals(roundFormat)) {
                            // We've already validated the team composition so just set them manually
                            currentTeam.put(leader.getText(), "Main Attacker");
                            currentTeam.put(player1.getText(), "2nd Attacker");
                        } else {
                            currentTeam.put(leader.getText(), IDfinder(leaderIcon.getModelId()));
                            currentTeam.put(player1.getText(), IDfinder(player1Icon.getModelId()));
                        }

                        currentTeam.put(player2.getText(), IDfinder(player2Icon.getModelId()));

                        // Player 3 omitted if leech
                        if ("five_man".equals(roundFormat)) {
                            currentTeam.put(player3.getText(), IDfinder(player3Icon.getModelId()));
                        }

                        currentTeam.put(player4.getText(), IDfinder(player4Icon.getModelId()));
                        log.debug("Current Team: {}", currentTeam);
                    } else {
                        log.debug("Not a valid leech or five man run");
                    }
                }


				if(config.Message())
				{
					chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage("Run identified as " + round_role + " good luck :)")
						.build());
				}
				//log.info(round_role);
				//log.info(String.valueOf(gameTime.getPBTime()));
				//log.info(String.valueOf(roleToDouble(round_role)));
				rolecurrentpb = getCurrentPB(round_role);
			}




        }
    }


	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE
			&& event.getMessage().startsWith("---- Wave:"))
		{
			String[] message = event.getMessage().split(" ");
			currentWave = Integer.parseInt(message[BA_WAVE_NUM_INDEX]);

			if (currentWave == START_WAVE)
			{
                timers.resetAll();
                timers.startRound();
			}
		}
	}

    private boolean isGoodPremove(WorldPoint wp)
    {
        return wp.getY() < PREMOVE_Y_THRESHOLD;
    }

    private int inWave()
    {
        // This method centralizes wave detection by combining multiple checks:
        // 1. Verifies inGameBit is 1 (player is in a BA game).
        // 2. Confirms the player is in an instanced region (BA is always instanced).
        // 3. Checks for a role-specific horn in the inventory.
        // 4. Ensures a role-specific widget is loaded (indicating the player has a role).
        // 5. Validates that currentWave is a valid number between 1 and 10.
        // Returns the wave number (1-10) if one condition is met; otherwise, returns 1.

        if ((inGameBit == 1 || hasRoleHorn() || hasRoleWidget()) && client.getTopLevelWorldView().isInstance()) {
            if (currentWave < 1 || currentWave > 10) {
                return 0;
            }
            return currentWave;
        }
        return 0;
    }

    private boolean hasRoleHorn()
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null)
        {
            return false;
        }

        for (Item item : inventory.getItems())
        {
            if (item == null)
            {
                continue;
            }

            int id = item.getId();
            if (id == net.runelite.api.gameval.ItemID.BARBASSAULT_ATT_HORN_01 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_ATT_HORN_02 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_ATT_HORN_03 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_ATT_HORN_04 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_ATT_HORN_05 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_DEFENDER_HORN ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_HORN_COLLECTOR ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_HEAL_HORN_01 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_HEAL_HORN_02 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_HEAL_HORN_03 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_HEAL_HORN_04 ||
                    id == net.runelite.api.gameval.ItemID.BARBASSAULT_HEAL_HORN_05)
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasRoleWidget()
    {
        return client.getWidget(BA_ATTACKER_GROUP_ID, 0) != null ||
                client.getWidget(BA_COLLECTOR_GROUP_ID, 0) != null ||
                client.getWidget(BA_DEFENDER_GROUP_ID, 0) != null ||
                client.getWidget(BA_HEALER_GROUP_ID, 0) != null;
    }

	private double getCurrentPB(String pbKey)
	{
		try
		{
			return configManager.getRSProfileConfiguration("BaPB", pbKey, double.class);
		}
		catch (Exception e)
		{
			return 0.0;
		}
	}

	void recentLookup(ChatMessage chatMessage, String message){
		ChatMessageType type = chatMessage.getType();
		String search = message.substring(BA_COMMAND_STRING.length() + 1);
		final String player;
		if (type.equals(ChatMessageType.PRIVATECHATOUT))
		{
			player = client.getLocalPlayer().getName();
		}
		else
		{
			player = Text.removeTags(chatMessage.getName())
				.replace('\u00A0', ' ');
		}




		net.runelite.http.api.chat.Task task;
		final double BaPb;
		try
		{
			BaPb = chatClient.getPb(player, "Recent");
		}
		catch (IOException ex)
		{
			log.debug("unable to retrieve PB", ex);
			return;
		}

		//now we grab the current role which was saved in the ._x of the double :)
        int hundredthsDigit = ((int) Math.round(BaPb * 100)) % 10;

		double roleDouble = hundredthsDigit / 100.0;

		log.debug(String.valueOf(roleDouble));
		String role = doubleToRole(roleDouble);

		int minutes = (int) (Math.floor(BaPb) / 60);
		int seconds = (int)BaPb % 60;
        int decimal = (int) ((BaPb * 10) % 10);

		final String time =  String.format("%d:%02d.%d", minutes, seconds, decimal);

		String response = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Recent ")
			.append(ChatColorType.NORMAL)
			.append(role)
			.append(" run: ")
			.append(ChatColorType.HIGHLIGHT)
			.append(time)
			.build();

		log.debug("Setting response {}", response);
		final MessageNode messageNode = chatMessage.getMessageNode();
		messageNode.setRuneLiteFormatMessage(response);
		client.refreshChat();
	}

	void baLookup(ChatMessage chatMessage, String message)
	{

		ChatMessageType type = chatMessage.getType();
		String search = message.substring(BA_COMMAND_STRING.length() + 1);
		final String player;
		if (type.equals(ChatMessageType.PRIVATECHATOUT))
		{
			player = client.getLocalPlayer().getName();
		}
		else
		{
			player = Text.removeTags(chatMessage.getName())
				.replace('\u00A0', ' ');
		}

		search = longBossName(search);

		if(search == "Recent"){
			recentLookup(chatMessage, message);
			return;
		}

		net.runelite.http.api.chat.Task task;
		final double BaPb;
		try
		{
			BaPb = chatClient.getPb(player, search);
		}
		catch (IOException ex)
		{
			log.debug("unable to retrieve PB", ex);
			return;
		}
		int minutes = (int) (Math.floor(BaPb) / 60);
		double seconds = BaPb % 60;

		// If the seconds is an integer, it is ambiguous if the pb is a precise
		// pb or not. So we always show it without the trailing .00.
		final String time = Math.floor(seconds) == seconds ?
			String.format("%d:%02d", minutes, (int) seconds) :
			String.format("%d:%05.2f", minutes, seconds);

		String response = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(search)
			.append(ChatColorType.NORMAL)
			.append(" personal best: ")
			.append(ChatColorType.HIGHLIGHT)
			.append(time)
			.build();

		log.debug("Setting response {}", response);
		final MessageNode messageNode = chatMessage.getMessageNode();
		messageNode.setRuneLiteFormatMessage(response);
		client.refreshChat();
	}
	private boolean baSubmit(ChatInput chatInput, String value)
	{
		int idx = value.indexOf(' ');
		final String boss = longBossName(value.substring(idx + 1));

		final double pb = configManager.getRSProfileConfiguration("BaPB", boss, double.class);

		if (pb <= 0)
		{
			return false;
		}

		final String playerName = client.getLocalPlayer().getName();

		executor.execute(() ->
		{
			try
			{
				chatClient.submitPb(playerName, boss, pb);
			}
			catch (Exception ex)
			{
				log.warn("unable to submit personal best", ex);
			}
			finally
			{
				chatInput.resume();
			}
		});

		return true;
	}

	private String IDfinder(int roleID){
		if (roleID == attackerIcon) return "Attacker";
		if (roleID == defenderIcon) return "Defender";
		if (roleID == collectorIcon) return "Collector";
		if (roleID == healerIcon) return "Healer";
		return "";
	}

	private String doubleToRole(double time){
        if(time == .01) return "Attacker";
		if(time == .02) return "Defender";
		if(time == .03) return "Collector";
		if(time == .04) return "Healer";
		if(time == .05) return "Leech Attacker";
		if(time == .06) return "Leech Defender";
		if(time == .07) return "Leech Collector";
		if(time == .08) return "Leech Healer";
		if(time == .09) return "Main Attacker";
		return "Please relaunch client and do another run to fix this bug";
	}

    private double roleToDouble(String role){
        if(role.equals("Attacker")) return .01;
        if(role.equals("Defender")) return .02;
        if(role.equals("Collector")) return .03;
        if(role.equals("Healer")) return .04;
        if(role.equals("Leech Attacker")) return .05;
        if(role.equals("Leech Defender")) return .06;
        if(role.equals("Leech Collector")) return .07;
        if(role.equals("Leech Healer")) return .08;
        if(role.equals("Main Attacker")) return .09;
        return .00;
    }


	private static String longBossName(String boss)
	{
		switch (boss.toLowerCase())
		{
			case "att":
			case "a":
			case "main":
				return "Main Attacker";

			case "2a":
				return "Attacker";

			case "la":
				return "Leech Attacker";

			case "heal":
			case "h":
				return "Healer";

			case "lh":
				return "Leech Healer";

			case "def":
			case "d":
				return "Defender";

			case "ld":
				return "Leech Defender";

			case "eggboi":
			case "coll":
			case "col":
			case "c":
				return "Collector";

			case "lc":
				return "Leech Collector";

			case "":
			case " ":
			case "ba":
				return "Barbarian Assault";

			case "r":
				return "Recent";

			case "gc":
				return "Gc";

			default:
				return WordUtils.capitalize(boss);
		}
	}
}
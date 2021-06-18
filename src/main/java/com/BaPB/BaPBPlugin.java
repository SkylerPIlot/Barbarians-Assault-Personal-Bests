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
import java.awt.Image;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;
import net.runelite.http.api.chat.ChatClient;
import org.apache.commons.text.WordUtils;

@PluginDescriptor(
	name = "Barbarian Assault Personal Bests",
	description = "Emulates the normal plugin as well as saving personal bests",
	tags = {"minigame", "overlay", "timer", "Personal Bests","Time"}
)
@Slf4j
public class BaPBPlugin extends Plugin
{
	private static final int BA_WAVE_NUM_INDEX = 2;
	private static final String START_WAVE = "1";
	private static final String ENDGAME_REWARD_NEEDLE_TEXT = "<br>5";
	private double currentpb; //This is to load overall pb
	private double rolecurrentpb; //This is to load role specific pb's and gets set when the role is determined
	private static final String BA_COMMAND_STRING = "!ba";

	@Getter(AccessLevel.PACKAGE)
	private int inGameBit = 0;
	private String currentWave = START_WAVE;
	private GameTimer gameTime;
	private String round_role;

	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BaPBConfig config;

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

	@Override
	protected void startUp() throws Exception
	{
		chatCommandManager.registerCommandAsync(BA_COMMAND_STRING, this::baLookup, this::baSubmit);
	}

	@Override
	protected void shutDown() throws Exception
	{
		chatCommandManager.unregisterCommand(BA_COMMAND_STRING);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case WidgetID.BA_REWARD_GROUP_ID:
			{
				Widget rewardWidget = client.getWidget(WidgetInfo.BA_REWARD_TEXT);

				if (rewardWidget != null && rewardWidget.getText().contains(ENDGAME_REWARD_NEEDLE_TEXT) && gameTime != null)
				{
					if ((gameTime.getPBTime() < rolecurrentpb || rolecurrentpb == 0.0) && config.Seperate())
					{
						configManager.setRSProfileConfiguration("BaPB", round_role, gameTime.getPBTime());
						log.info("Personal best of: {} saved in {}",gameTime.getPBTime(), round_role);
					}
					currentpb = getCurrentPB("Barbarian Assault");
					if ((gameTime.getPBTime() < currentpb || currentpb == 0.0))
					{
						configManager.setRSProfileConfiguration("BaPB", "Barbarian Assault", gameTime.getPBTime());
						log.info("Personal best of: {} saved in Barbarian Assault",gameTime.getPBTime());
					}
					configManager.setRSProfileConfiguration("BaPB", "Recent", gameTime.getPBTime());
					gameTime = null;
				}

				break;
			}
			case WidgetID.BA_ATTACKER_GROUP_ID:
			{
				round_role = "Attacker";
				rolecurrentpb = getCurrentPB(round_role);
				break;
			}
			case WidgetID.BA_DEFENDER_GROUP_ID:
			{
				round_role = "Defender";
				rolecurrentpb = getCurrentPB(round_role);
				break;
			}
			case WidgetID.BA_HEALER_GROUP_ID:
			{
				round_role = "Healer";
				rolecurrentpb = getCurrentPB(round_role);
				break;
			}
			case WidgetID.BA_COLLECTOR_GROUP_ID:
			{
				round_role = "Collector";
				rolecurrentpb = getCurrentPB(round_role);
				break;
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
			currentWave = message[BA_WAVE_NUM_INDEX];

			if (currentWave.equals(START_WAVE))
			{
				gameTime = new GameTimer();
			}
		}
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

	void baLookup(ChatMessage chatMessage, String message)
	{

		ChatMessageType type = chatMessage.getType();
		String search = longBossName(message.substring(BA_COMMAND_STRING.length() + 1));
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
		chatMessageManager.update(messageNode);
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
	private static String longBossName(String boss)
	{
		switch (boss.toLowerCase())
		{
			case "att":
			case "a":
				return "Attacker";

			case "heal":
			case "h":
				return "Healer";

			case "def":
			case "d":
				return "Defender";

			case "eggboi":
			case "coll":
			case "col":
			case "c":
				return "Collector";

			case "":
			case " ":
			case "ba":
				return "Barbarian Assault";

			case "r":
				return "Recent";

			default:
				return WordUtils.capitalize(boss);
		}
	}





}
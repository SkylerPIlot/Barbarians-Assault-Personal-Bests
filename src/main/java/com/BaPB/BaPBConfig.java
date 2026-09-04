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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("BaPb")
public interface

BaPBConfig extends Config
{
	@ConfigItem(
		position = 1,
		keyName = "baPB",
		name = "Save role PB different then Overall PB",
		description = "Turning this on will save your round time into a specific role round time as well as into the overall pb"
	)
	default boolean Seperate()
	{
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "msg",
		name = "Turn round msg on/off",
		description = "Turning this on will display a nice lil message at the start of the round"
	)
		default boolean Message() {return true;}
	@ConfigItem(
		position = 3,
		keyName = "log",
		name = "Logger",
		description = "Allows to log your times to a file in your .runelite file turn off/on to update file"
	)
	default boolean Logging() {return false;}

	@ConfigSection(
		name = "osrs-ba.com",
		description = "Configurations related to osrs-ba.com",
		position = 4,
		closedByDefault = true
	)
	String websiteSection = "websiteSection";

	@ConfigItem(
		position = 0,
		keyName = "sub_runs",
		name = "Submit Runs",
		warning = "This portion of the plugin submits data to a 3rd party website not controlled or verified by the RuneLite Developers.",
		description = "This will submit runs to osrs-ba.com",
		section = websiteSection
	)
	default boolean SubmitRuns() { return false; }

    @ConfigItem(
		position = 1,
		keyName = "sub_qs",
		name = "Submit QS Stats",
		description = "This will submit QS statistics to osrs-ba.com. Only used when \"Submit Runs\" is enabled.",
		section = websiteSection
    )
    default boolean SubmitQS() { return false; }

    @ConfigItem(
		position = 2,
		keyName = "sync_pbs",
		name = "Sync PBs",
		warning = "This portion of the plugin submits data to a 3rd party website not controlled or verified by the RuneLite Developers.",
		description = "Pulls PBs from osrs-ba.com rather than from your local machine. Helps keep your PBs accurate across multiple devices or accounts.",
		section = websiteSection
	)
	default boolean SyncPbs() { return false; }


    @ConfigItem(
		position = 3,
		keyName = "uuid_key",
		name = "UUID Key",
		description = "Key used for linking ALT accounts, you can get one by logging into osrs-ba.com/accounts.",
		section = websiteSection
	)
	default String uuid_key() { return null; }

}

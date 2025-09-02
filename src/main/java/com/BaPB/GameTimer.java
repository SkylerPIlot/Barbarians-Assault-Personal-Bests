/*
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

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

import static net.runelite.client.util.RSTimeUnit.GAME_TICKS;

@Slf4j
public class GameTimer
{
    private int roundTicks = 0;
    private boolean running = false;

    public void start()
    {
        roundTicks = 0;
        running = true;
    }

    public void stop()
    {
        running = false;
    }

    public void onGameTick()
    {
        if (running)
        {
            roundTicks++;
        }
    }

    public double getElapsedSeconds(Boolean isLeader)
    {
        // The number of ticks to remove is +1 if leader. May have to do with timing of loading in/out
        int numTicks = isLeader ? 2 : 1;
        int adjustedTicks = Math.max(0, roundTicks - numTicks);

        Duration duration = Duration.of(adjustedTicks, GAME_TICKS);
        return duration.toMillis() / 1000.0;
    }

}

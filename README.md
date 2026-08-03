# Barbarian Assault Personal Bests

A RuneLite plugin for automatically timing Barbarian Assault rounds, saving personal bests by role and team format, and sharing those times through the `!ba` chat command.

The plugin recognizes standard five-player, leech, and duo-healer team orders from the recruitment scroll. It can also record detailed run data locally or, if explicitly enabled, submit runs and synchronize PBs with [osrs-ba.com](https://osrs-ba.com/).

## Features

- Automatically times complete rounds and individual waves.
- Saves an overall PB and, optionally, a separate PB for the role you played.
- Detects five-player, leech, and duo-healer team formats and their specialized roles.
- Announces new role PBs in the RuneLite console and plays a notification sound.
- Exposes your saved times through one chat command with concise aliases.
- Remembers the most recently completed run and its role.
- Optionally writes team, role, timestamp, and round-time data to a local CSV file.
- Optionally submits round, wave, and queue-start statistics to osrs-ba.com.
- Optionally reads PBs from osrs-ba.com, keeping them consistent across RuneLite profiles or devices.

## Installation

1. Open RuneLite.
2. Open **Plugin Hub** from the configuration panel.
3. Search for **Barbarian Assault Personal Bests**.
4. Select **Install**.

No manual configuration is required for local PB tracking. Open the plugin's settings in RuneLite if you want role-specific PBs, local logging, or osrs-ba.com integration.

## How it works

Use the recruitment scroll normally and complete a full Barbarian Assault run. The plugin reads the finalized team order to determine your role and starts timing when wave 1 begins. At the end of wave 10 it saves the result when it improves your existing time.

Recognized team orders are:

| Format | Scroll order | Role handling |
| --- | --- | --- |
| Five-player | `AAHCD` | The leading attacker is **Main Attacker** and the second is **Attacker**. |
| Leech | `ACHLD` | PBs are stored as Leech Attacker, Healer, Collector, or Defender. |
| Duo healer | `AHHCD` | The healers are distinguished as **2nd Healer** and **Main Healer** by scroll position. |

Role-specific PB saving is enabled by default. Every recognized completed round is still eligible for the overall Barbarian Assault PB.

## Chat commands

Type `!ba <command>` in public, clan, or private chat. RuneLite replaces the message with the corresponding PB. Commands are not case-sensitive.

### Overall and recent runs

| Command | Aliases | Result |
| --- | --- | --- |
| `!ba` | `!ba ba` | Fastest overall Barbarian Assault round |
| `!ba recent` | `!ba r` | Most recently completed round, including its detected role |

### Five-player roles

| Command | Aliases | Result |
| --- | --- | --- |
| `!ba main attacker` | `!ba a`, `!ba att`, `!ba main` | Main Attacker PB |
| `!ba attacker` | `!ba 2a` | Second Attacker PB |
| `!ba healer` | `!ba h`, `!ba heal` | Healer PB |
| `!ba collector` | `!ba c`, `!ba col`, `!ba coll` | Collector PB |
| `!ba defender` | `!ba d`, `!ba def` | Defender PB |

### Leech roles

| Command | Result |
| --- | --- |
| `!ba la` | Leech Attacker PB |
| `!ba lh` | Leech Healer PB |
| `!ba lc` | Leech Collector PB |
| `!ba ld` | Leech Defender PB |

### Duo-healer roles

| Command | Aliases | Result |
| --- | --- | --- |
| `!ba dha` | — | Attacker PB |
| `!ba dh2` | `!ba 2h`, `!ba dh2h` | Second Healer PB |
| `!ba dhh` | `!ba dh`, `!ba mh` | Main Healer PB |
| `!ba dhc` | — | Collector PB |
| `!ba dhd` | — | Defender PB |

The full role names also work, such as `!ba leech defender` and `!ba dh collector`.

## Configuration

| Setting | Default | Description |
| --- | --- | --- |
| **Save role PB different then Overall PB** | On | Saves a separate PB for the detected role in addition to the overall PB. |
| **Turn round msg on/off** | On | Displays a message when a new round begins. |
| **logger** | Off | Appends completed runs to a CSV file in the RuneLite directory. |
| **Submit Runs** | Off | Sends supported completed runs to osrs-ba.com. |
| **Submit QS Stats** | Off | Includes queue-start statistics in submissions; only applies when Submit Runs is enabled. |
| **Sync PBs** | Off | Reads PBs from osrs-ba.com instead of the current local RuneLite profile. |
| **UUID Key** | Empty | Associates the submitting character with an osrs-ba.com account. Obtain the key from the site's account page. |

## Data and privacy

Local PBs are stored in your RuneLite profile configuration. When **logger** is enabled, the plugin appends records to:

```text
<RuneLite directory>/barbarian-assault-pbs.csv
```

The osrs-ba.com features are disabled by default and communicate with a third-party service that is not controlled or verified by the RuneLite developers.

When **Submit Runs** is enabled, supported runs may include:

- Character names and assigned roles for the team
- Team format, total time, submitting player, scroller status, and world region
- Per-wave timing, queue-start timing, reset and premove data
- NPC death times and the queen spawn time
- Queue-start spawn coordinates when **Submit QS Stats** is also enabled
- Your UUID key, if configured, for the submitting character

When **Sync PBs** is enabled, the plugin requests PB data for the current character from osrs-ba.com. Review the service before enabling either integration.

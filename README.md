This project is abandoned, as we no longer use IRC.

The aim of this project was to be a more reliable alternative to [discord-irc](https://github.com/reactiflux/discord-irc).

However, much like discord-irc, JDiscordIRC fails at this basic goal. There is a nasty deadlock bug, where something within a JDA handled event will eventually cause JDiscordIRC to hang.

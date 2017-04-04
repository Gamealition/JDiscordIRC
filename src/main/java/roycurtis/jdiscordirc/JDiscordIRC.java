package roycurtis.jdiscordirc;

import roycurtis.jdiscordirc.managers.BridgeManager;
import roycurtis.jdiscordirc.managers.ConfigManager;
import roycurtis.jdiscordirc.managers.DiscordManager;
import roycurtis.jdiscordirc.managers.IRCManager;

import java.util.logging.Logger;

/** Main application class; handles init, main loop and exit */
public class JDiscordIRC
{
    public final static Logger         LOGGER  = Logger.getLogger("JDiscordIRC");
    public final static BridgeManager  BRIDGE  = new BridgeManager();
    public final static ConfigManager  CONFIG  = new ConfigManager();
    public final static DiscordManager DISCORD = new DiscordManager();
    public final static IRCManager     IRC     = new IRCManager();

    private static boolean exiting = false;

    public static void main(String[] args)
    {
        LOGGER.info("### JDiscordIRC is starting...");
        setup();

        if (!exiting)
            loop();

        takedown();
        LOGGER.info("### JDiscord is finished");
    }

    private static void setup()
    {
        CONFIG.setup();
        DISCORD.setup();
        IRC.setup();
        BRIDGE.setup();
    }

    private static void loop()
    {
        while (!exiting)
        {
            DISCORD.update();
            IRC.update();
        }
    }

    private static void takedown()
    {
        BRIDGE.takedown();
        DISCORD.takedown();
        IRC.takedown();
    }
}

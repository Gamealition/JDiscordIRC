package roycurtis.jdiscordirc;

import roycurtis.jdiscordirc.managers.BridgeManager;
import roycurtis.jdiscordirc.managers.ConfigManager;
import roycurtis.jdiscordirc.managers.DiscordManager;
import roycurtis.jdiscordirc.managers.IRCManager;
import roycurtis.jdiscordirc.util.CurrentThread;

/** Main application class; handles init, main loop and exit */
public class JDiscordIRC
{
    public final static BridgeManager  BRIDGE  = new BridgeManager();
    public final static ConfigManager  CONFIG  = new ConfigManager();
    public final static DiscordManager DISCORD = new DiscordManager();
    public final static IRCManager     IRC     = new IRCManager();

    private static boolean exiting = false;

    public static void main(String[] args)
    {
        log("### JDiscordIRC is starting...");
        setup();

        if (!exiting)
            loop();

        takedown();
        log("### JDiscord is finished");
    }

    public static synchronized void log(String... msg)
    {
        System.out.println( String.join(" ", msg) );
    }

    private static void setup()
    {
        try
        {
            CONFIG.setup();
            DISCORD.setup();
            IRC.setup();
        }
        catch (Exception e)
        {
            log("[!] Exception during setup phase, exiting");
            e.printStackTrace();
            exiting = true;
        }
    }

    private static void loop()
    {
        while (!exiting)
        {
            BRIDGE.pump();
            CurrentThread.sleep(10);
        }
    }

    private static void takedown()
    {
        DISCORD.takedown();
        IRC.takedown();
    }
}

package roycurtis.jdiscordirc;

import roycurtis.jdiscordirc.managers.BridgeManager;
import roycurtis.jdiscordirc.managers.ConfigManager;
import roycurtis.jdiscordirc.managers.DiscordManager;
import roycurtis.jdiscordirc.managers.IRCManager;

/** Main application class; handles init, main loop and exit */
public class JDiscordIRC
{
    public final static BridgeManager  BRIDGE  = new BridgeManager();
    public final static ConfigManager  CONFIG  = new ConfigManager();
    public final static DiscordManager DISCORD = new DiscordManager();
    public final static IRCManager     IRC     = new IRCManager();

    private static boolean exiting = false;

    //<editor-fold desc="State management">
    public static synchronized void exit(String why)
    {
        log("### Starting exit: %s", why);
        exiting = true;
    }

    public static synchronized boolean isExiting()
    {
        return exiting;
    }
    //</editor-fold>

    public static synchronized void log(String msg, Object... parts)
    {
        System.out.println( String.format(msg, parts) );
    }

    //<editor-fold desc="Application init, main loop, takedown">
    public static void main(String[] args)
    {
        log("### JDiscordIRC is starting...");
        init();
        loop();
        takedown();
        log("### JDiscord is finished");
    }

    private static void init()
    {
        try
        {
            CONFIG.init();
            DISCORD.init();
            IRC.init();
        }
        catch (Exception e)
        {
            log("[!] Exception during setup phase, exiting");
            e.printStackTrace();
            exit("Could not complete setup");
        }
    }

    private static void loop()
    {
        while ( !isExiting() )
            BRIDGE.pump();
    }

    private static void takedown()
    {
        DISCORD.takedown();
        IRC.takedown();
    }
    //</editor-fold>
}

package roycurtis.jdiscordirc.managers;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;

import java.nio.charset.StandardCharsets;

import static roycurtis.jdiscordirc.JDiscordIRC.log;

public class IRCManager extends ListenerAdapter
{
    private PircBotX irc;
    private Thread   thread;

    /* Manager methods (main thread) */

    public void setup() throws Exception
    {
        log("[IRC] Connecting for first time...");
        log("THREAD: " + Thread.currentThread().getName());

        Configuration config = new Configuration.Builder()
            .setName("GDiscord")
            .setLogin("JDiscordIRC")
            .setRealName("JDiscordIRC alpha test")
            .setEncoding(StandardCharsets.UTF_8)
            .setAutoReconnect(true)
            .addServer("irc.us.gamesurge.net")
            .addAutoJoinChannel("#gamealition")
            .addListener(this)
            .buildConfiguration();

        irc    = new PircBotX(config);
        thread = new Thread(() -> {
            try
            {
                irc.startBot();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }, "PircBotX");

        thread.start();
    }

    public void takedown()
    {
        irc.sendIRC().quitServer("Going down");
    }

    /* Event handlers (out of main thread) */

    @Override
    public void onConnect(ConnectEvent event) throws Exception
    {
        log("[IRC] Connected");
    }

    @Override
    public void onConnectAttemptFailed(ConnectAttemptFailedEvent event) throws Exception
    {
        log("[IRC] Could not connect; retrying...");
    }

    @Override
    public void onDisconnect(DisconnectEvent event) throws Exception
    {
        log("[IRC] Disconnected");
    }

    @Override
    public void onJoin(JoinEvent event) throws Exception
    {
        User user = event.getUser();

        if (user != null)
            log("[IRC] Joined channel: " + event.getUser().getHostmask());
    }

    @Override
    public void onMessage(MessageEvent event) throws Exception
    {
        log("[IRC] Message: " + event.getMessage() + " (from " + event.getChannel().getName() + ")");
        log("[IRC] THREAD: " + Thread.currentThread().getName());
    }
}

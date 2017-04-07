package roycurtis.jdiscordirc.managers;

import com.google.common.base.Strings;
import org.pircbotx.Colors;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import roycurtis.jdiscordirc.JDiscordIRC;

import java.nio.charset.StandardCharsets;

import static roycurtis.jdiscordirc.JDiscordIRC.BRIDGE;
import static roycurtis.jdiscordirc.JDiscordIRC.IRC;
import static roycurtis.jdiscordirc.JDiscordIRC.log;

public class IRCManager extends ListenerAdapter
{
    // TODO: Make these config
    public static final String SERVER   = "irc.us.gamesurge.net";
    public static final String CHANNEL  = "#vprottest";
    public static final String NICKNAME = "GDiscord";

    private PircBotX bot;
    private Thread   thread;
    private boolean  wasConnected;

    //<editor-fold desc="Manager methods (main thread)">
    public void init() throws Exception
    {
        log("[IRC] Connecting for first time...");

        Configuration config = new Configuration.Builder()
            .setName(NICKNAME)
            .setLogin("JDiscordIRC")
            .setRealName("JDiscordIRC alpha test")
            .setEncoding(StandardCharsets.UTF_8)
            .setAutoNickChange(true)
            .setAutoReconnect(true)
            .setAutoReconnectAttempts(Integer.MAX_VALUE)
            .setAutoReconnectDelay(5000)
            .addServer(SERVER)
            .addListener(this)
            .buildConfiguration();

        bot    = new PircBotX(config);
        thread = new Thread(() -> {
            try                 { bot.startBot(); }
            catch (Exception e) { JDiscordIRC.exit("IRC bot crashed"); }
        }, "PircBotX");

        thread.start();
    }

    public boolean isAvailable()
    {
        if (bot == null)
            return false;
        else if ( !bot.isConnected() )
            return false;
        else
            return bot.getUserChannelDao().containsChannel(CHANNEL);
    }

    public void sendMessage(String msg, Object... parts)
    {
        if ( !isAvailable() )
        {
            log("[IRC] Rejecting message; IRC unavailable: %s", msg);
            return;
        }

        String fullMsg = String.format(msg, parts);
        log("IRC: %s", fullMsg);
        IRC.bot.send().message(CHANNEL, fullMsg);
    }

    public void sendAction(String who, String action)
    {
        if ( !isAvailable() )
        {
            log("[IRC] Rejecting action; IRC unavailable: %s", action);
            return;
        }

        log("IRC: %s %s", who, action);
        action = String.format("%s%s%s %s",
            Colors.BOLD, who, Colors.NORMAL,
            action
        );
        IRC.bot.send().action(CHANNEL, action);
    }

    public void setAway(String msg)
    {
        if ( !isAvailable() )
        {
            log("[IRC] Rejecting away; IRC unavailable: %s", msg);
            return;
        }

        if ( Strings.isNullOrEmpty(msg) )
            log("[IRC] Removing away message");
        else
            log("[IRC] Setting away message to: %s", msg);

        IRC.bot.sendRaw().rawLine("AWAY :" + msg);
    }
    //</editor-fold>

    //<editor-fold desc="Bot event handlers (IRC thread)">
    @Override
    public void onConnect(ConnectEvent event) throws Exception
    {
        // We won't send connect to bridge; all we care about is joining channel
        log("[IRC] Connected");
        // We don't use auto-join, because if the bot gets kicked we simply disconnect. The auto
        // reconnect doesn't honor channel auto-join.
        bot.send().joinChannel(CHANNEL);
    }

    @Override
    public void onConnectAttemptFailed(ConnectAttemptFailedEvent event) throws Exception
    {
        log("[IRC] Could not connect; retrying...");
    }

    @Override
    public void onDisconnect(DisconnectEvent event) throws Exception
    {
        if (wasConnected)
        {
            log( "[IRC] Lost connection (%s); reconnecting...", event.getDisconnectException() );
            wasConnected = false;
            BRIDGE.onIRCDisconnect();
        }
        else
            log( "[IRC] Could not connect (%s); retrying...", event.getDisconnectException() );
    }

    @Override
    public void onMessage(MessageEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        // Ignore own messages
        if ( user.equals( bot.getUserBot() ) )
            return;

        log( "[IRC] Message from %s: %s", user.getNick(), event.getMessage() );
        BRIDGE.onIRCMessage( user, event.getMessage() );
    }

    @Override
    public void onAction(ActionEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        // Ignore own messages
        if ( user.equals( bot.getUserBot() ) )
            return;

        log( "[IRC] Action from %s: %s", user.getNick(), event.getAction() );
        BRIDGE.onIRCAction( user, event.getAction() );
    }

    @Override
    public void onNotice(NoticeEvent event) throws Exception
    {
        log( "[IRC] Notice: %s", event.getMessage() );
    }

    @Override
    public void onPrivateMessage(PrivateMessageEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            log( "[IRC] Private from unknown: %s", event.getMessage() );
        else
            log( "[IRC] Private from %s: %s", user.getNick(), event.getMessage() );
    }

    @Override
    public void onJoin(JoinEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        if ( user.equals( bot.getUserBot() ) )
        {
            log( "[IRC] Joined channel successfully", user.getHostmask() );
            BRIDGE.onIRCConnect();
            wasConnected = true;
        }
        else
        {
            log( "[IRC] %s joined the channel", event.getUser().getHostmask() );
            BRIDGE.onIRCJoin(user);
        }
    }

    @Override
    public void onPart(PartEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        log( "[IRC] %s parted the channel (%s)", user.getHostmask(), event.getReason() );
        BRIDGE.onIRCPart( user, event.getReason() );
    }

    @Override
    public void onQuit(QuitEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        log( "[IRC] %s quit the server (%s)", user.getHostmask(), event.getReason() );
        BRIDGE.onIRCQuit( user, event.getReason() );
    }

    @Override
    public void onKick(KickEvent event) throws Exception
    {
        User target = event.getRecipient();
        User kicker = event.getUser();
        if (target == null || kicker == null)
            return;

        // Handle self-kick
        if ( target.equals( bot.getUserBot() ) )
        {
            log( "[IRC] Kicked off by %s (%s); disconnecting and reconnecting...",
                kicker.getNick(),
                event.getReason()
            );
            // No bridge message; let disconnect and auto-reconnect handle messages
            bot.send().quitServer();
            return;
        }

        log( "[IRC] %s kicked from channel by %s (%s)",
            target.getNick(),
            kicker.getNick(),
            event.getReason()
        );
        BRIDGE.onIRCKick( target, kicker, event.getReason() );
    }

    @Override
    public void onNickChange(NickChangeEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        log( "[IRC] %s changed nick to %s", event.getOldNick(), event.getNewNick() );
        BRIDGE.onIRCNickChange( event.getOldNick(), event.getNewNick() );
    }
    //</editor-fold>
}

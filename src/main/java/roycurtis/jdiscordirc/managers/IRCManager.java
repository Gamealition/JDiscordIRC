package roycurtis.jdiscordirc.managers;

import com.google.common.base.Strings;
import org.pircbotx.Colors;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import roycurtis.jdiscordirc.JDiscordIRC;

import java.nio.charset.StandardCharsets;

import static roycurtis.jdiscordirc.JDiscordIRC.BRIDGE;
import static roycurtis.jdiscordirc.JDiscordIRC.IRC;

public class IRCManager extends ListenerAdapter
{
    private static final Logger LOG = LoggerFactory.getLogger(IRCManager.class);

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
        LOG.info("Connecting for first time...");

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
            LOG.debug("Rejecting message; IRC unavailable: {}", msg);
            return;
        }

        String fullMsg = String.format(msg, parts);
        LOG.info( "Sent: {}", Colors.removeFormattingAndColors(fullMsg) );
        IRC.bot.send().message(CHANNEL, fullMsg);
    }

    public void sendAction(String who, String action)
    {
        if ( !isAvailable() )
        {
            LOG.debug("Rejecting action; IRC unavailable: {}", action);
            return;
        }

        LOG.info( "Sent: {} {}", who, Colors.removeFormattingAndColors(action) );
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
            LOG.debug("Rejecting away; IRC unavailable: {}", msg);
            return;
        }

        if ( Strings.isNullOrEmpty(msg) )
            LOG.debug("Removing away message");
        else
            LOG.debug("Setting away message to: {}", msg);

        IRC.bot.sendRaw().rawLine("AWAY :" + msg);
    }
    //</editor-fold>

    //<editor-fold desc="Bot event handlers (IRC thread)">
    @Override
    public void onConnect(ConnectEvent event) throws Exception
    {
        // We won't send connect to bridge; all we care about is joining channel
        LOG.info("Connected successfully");
        // We don't use auto-join, because if the bot gets kicked we simply disconnect. The auto
        // reconnect doesn't honor channel auto-join.
        bot.send().joinChannel(CHANNEL);
    }

    @Override
    public void onConnectAttemptFailed(ConnectAttemptFailedEvent event) throws Exception
    {
        LOG.warn("Could not connect; retrying...");
    }

    @Override
    public void onDisconnect(DisconnectEvent event) throws Exception
    {
        if (wasConnected)
        {
            LOG.warn( "Lost connection ({}); reconnecting...", event.getDisconnectException() );
            wasConnected = false;
            BRIDGE.onIRCDisconnect();
        }
        else
            LOG.warn( "Could not connect ({}); retrying...", event.getDisconnectException() );
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

        // Don't bother with IRC formatting
        String message = Colors.removeFormattingAndColors( event.getMessage() );

        LOG.trace("Message from {}: {}", user.getNick(), message);
        BRIDGE.onIRCMessage(user, message);
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

        // Don't bother with IRC formatting
        String action = Colors.removeFormattingAndColors( event.getAction() );

        LOG.trace("Action from {}: {}", user.getNick(), action);
        BRIDGE.onIRCAction(user, action);
    }

    @Override
    public void onNotice(NoticeEvent event) throws Exception
    {
        LOG.info( "Notice: {}", Colors.removeFormattingAndColors( event.getMessage() ) );
    }

    @Override
    public void onPrivateMessage(PrivateMessageEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            LOG.info("Private from unknown: {}",
                Colors.removeFormattingAndColors( event.getMessage() )
            );
        else
            LOG.info("Private from {}: {}",
                user.getNick(), Colors.removeFormattingAndColors( event.getMessage() )
            );
    }

    @Override
    public void onJoin(JoinEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        if ( user.equals( bot.getUserBot() ) )
        {
            LOG.trace( "Joined channel successfully", user.getHostmask() );
            BRIDGE.onIRCConnect();
            wasConnected = true;
        }
        else
        {
            LOG.trace( "{} joined the channel", event.getUser().getHostmask() );
            BRIDGE.onIRCJoin(user);
        }
    }

    @Override
    public void onPart(PartEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        LOG.trace( "{} parted the channel ({})", user.getHostmask(), event.getReason() );
        BRIDGE.onIRCPart( user, event.getReason() );
    }

    @Override
    public void onQuit(QuitEvent event) throws Exception
    {
        User user = event.getUser();
        if (user == null)
            return;

        LOG.trace( "{} quit the server ({})", user.getHostmask(), event.getReason() );
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
            LOG.warn( "Kicked off by {} ({}); disconnecting and reconnecting...",
                kicker.getNick(),
                event.getReason()
            );
            // No bridge message; let disconnect and auto-reconnect handle messages
            bot.send().quitServer();
            return;
        }

        LOG.trace( "{} kicked from channel by {} ({})",
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

        LOG.trace( "{} changed nick to {}", event.getOldNick(), event.getNewNick() );
        BRIDGE.onIRCNickChange( event.getOldNick(), event.getNewNick() );
    }
    //</editor-fold>
}

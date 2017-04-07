package roycurtis.jdiscordirc.managers;

import com.google.common.base.Strings;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.pircbotx.Colors;
import org.pircbotx.User;
import roycurtis.jdiscordirc.util.CurrentThread;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static roycurtis.jdiscordirc.JDiscordIRC.*;

/**
 * Acts as a coordinator of events between IRC and Discord. It forces the handling of incoming
 * events (from either IRC or Discord) to happen in the main thread, in a queued fashion. This is
 * to ensure a reliable timeline of events.
 *
 * For example: IRC might be sending a bunch of messages and Discord is busy processing them. During
 * which, IRC loses connection. With this system, Discord will not suddenly dump a disconnect
 * message in the middle of other messages (as has happened during testing, without this system).
 */
public class BridgeManager
{
    //<editor-fold desc="Manager methods (main thread)">
    private Queue<Runnable> queue = new ConcurrentLinkedQueue<>();

    /** Executes a task from the queue in the main thread, to maintain order */
    public void pump()
    {
        Runnable task = queue.poll();

        if (task == null)
        {
            CurrentThread.sleep(50);
            return;
        }

        try
        {
            task.run();
        }
        catch (Exception e)
        {
            log( "[!] Exception during bridge event: %s", e.getMessage() );
            e.printStackTrace();
        }

        log( "[Bridge] Pumped events; %d remain", queue.size() );
        CurrentThread.sleep(10);
    }
    //</editor-fold>

    //<editor-fold desc="IRC->Discord (via IRC thread)">
    public void onIRCConnect()
    {
        queue.add( () -> {
            DISCORD.sendMessage("Connected to IRC");
            DISCORD.setStatus(
                OnlineStatus.ONLINE,
                IRCManager.CHANNEL + " @ " + IRCManager.SERVER,
                "https://irc.gamealition.com"
            );

            // Courtesy message for those on IRC
            if ( DISCORD.isAvailable() )
            {
                IRC.setAway("");
                IRC.sendMessage("••• Now bridging chat between Discord and IRC");
            }
            else
            {
                IRC.setAway("Waiting for connection to Discord...");
                IRC.sendMessage("••• Bridging chat between Discord and IRC shortly...");
            }
        } );
    }

    public void onIRCDisconnect()
    {
        queue.add( () -> {
            DISCORD.sendMessage("••• Lost connection to IRC; reconnecting...");
            DISCORD.setStatus(
                OnlineStatus.DO_NOT_DISTURB,
                "Connecting to IRC...",
                "https://irc.gamealition.com"
            );
        } );
    }

    public void onIRCMessage(User user, String message)
    {
        queue.add( () -> DISCORD.sendMessage("<**%s**> %s", user.getNick(), message) );
    }

    public void onIRCAction(User user, String action)
    {
        queue.add( () -> DISCORD.sendMessage("_**%s** %s_", user.getNick(), action) );
    }

    public void onIRCJoin(User user)
    {
        queue.add( () -> DISCORD.sendMessage( "••• **%s** joined the channel", user.getNick() ) );
    }

    public void onIRCPart(User user, final String reason)
    {
        queue.add( () -> {
            String reasonPart = Strings.isNullOrEmpty(reason)
                ? ""
                : "(_" + reason + "_)";

            DISCORD.sendMessage("••• **%s** left the channel %s", user.getNick(), reasonPart);
        });
    }

    public void onIRCQuit(User user, String reason)
    {
        queue.add( () -> {
            String reasonPart = Strings.isNullOrEmpty(reason)
                ? ""
                : "(_" + reason + "_)";

            DISCORD.sendMessage("••• **%s** quit the server %s", user.getNick(), reasonPart);
        });
    }

    public void onIRCKick(User target, User kicker, String reason)
    {
        queue.add( () -> {
            String reasonPart = Strings.isNullOrEmpty(reason)
                ? ""
                : "(_" + reason + "_)";

            DISCORD.sendMessage("••• **%s** was kicked by **%s** %s",
                target.getNick(),
                kicker.getNick(),
                reason
            );
        });
    }

    public void onIRCNickChange(String oldNick, String newNick)
    {
        queue.add( () -> {
            DISCORD.sendMessage("••• **%s** changed nick to **%s**", oldNick, newNick);
        } );
    }
    //</editor-fold>

    //<editor-fold desc="Discord->IRC (via Discord thread)">
    public void onDiscordConnect()
    {
        queue.add( () -> {
            IRC.setAway("");
            IRC.sendMessage("Connected to Discord");

            // Courtesy message for those on Discord
            DISCORD.sendMessage( IRC.isAvailable()
                ? "••• Now bridging chat between Discord and IRC"
                : "••• Bridging chat between Discord and IRC shortly..."
            );
        } );
    }

    public void onDiscordDisconnect()
    {
        queue.add( () -> {
            IRC.setAway("Waiting for connection to Discord...");
            IRC.sendMessage("••• Lost connection to Discord; reconnecting. . .");
        });
    }

    public void onDiscordMessage(MessageReceivedEvent event)
    {
        queue.add( () -> {
            String msg = event.getMessage().getContent();
            String who = event.getMember().getEffectiveName();

            // Special handling for Discord action messages
            if ( msg.startsWith("_") && msg.endsWith("_") )
                IRC.sendAction( who, msg.substring( 1, msg.length() - 1 ) );
            else
                IRC.sendMessage("<%s%s%s> %s",
                    Colors.BOLD, who, Colors.NORMAL,
                    msg
                );
        });
    }

    public void onDiscordUserJoin(GuildMemberJoinEvent event)
    {
        queue.add( () -> {
            String who = event.getMember().getEffectiveName();

            IRC.sendMessage( "••• %s%s%s joined the server",
                Colors.BOLD, who, Colors.NORMAL
            );
        });
    }

    public void onDiscordUserLeave(GuildMemberLeaveEvent event)
    {
        queue.add( () -> {
            String who = event.getMember().getEffectiveName();

            IRC.sendMessage( "••• %s%s%s quit the server",
                Colors.BOLD, who, Colors.NORMAL
            );
        });
    }
    //</editor-fold>
}

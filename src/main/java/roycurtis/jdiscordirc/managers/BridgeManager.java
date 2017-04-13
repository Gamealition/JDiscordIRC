package roycurtis.jdiscordirc.managers;

import com.google.common.base.Strings;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import org.pircbotx.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static roycurtis.jdiscordirc.JDiscordIRC.DISCORD;
import static roycurtis.jdiscordirc.JDiscordIRC.IRC;

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
    private static final Logger LOG = LoggerFactory.getLogger(BridgeManager.class);

    //<editor-fold desc="Manager methods (main thread)">
    private Queue<Runnable> queue = new ConcurrentLinkedQueue<>();

    private boolean ircFirstTime     = true;
    private boolean discordFirstTime = true;
    private String  lastDiscordMessageSent;

    /** Executes a task from the queue in the main thread, to maintain order */
    public void pump()
    {
        Runnable task = queue.poll();

        if (task == null)
            return;

        try
        {
            task.run();
        }
        catch (Exception ex)
        {
            LOG.error( "Exception during bridge event: {}", ex.getMessage() );
        }

        LOG.trace( "[Bridge] Pumped events; {} remain", queue.size() );
    }
    //</editor-fold>

    //<editor-fold desc="Common methods (main thread)">
    /** Makes the Discord side set it status to reflect offline bridge */
    private void discordSetOfflineStatus()
    {
        DISCORD.setStatus(
            OnlineStatus.DO_NOT_DISTURB,
            "Connecting to IRC...",
            DISCORD.getUrl()
        );
    }

    /** Makes the Discord side set it status to reflect online bridge */
    private void discordSetOnlineStatus()
    {
        DISCORD.setStatus(
            OnlineStatus.ONLINE,
            IRC.getChannel() + " @ " + IRC.getServer(),
            DISCORD.getUrl()
        );
    }
    //</editor-fold>

    //<editor-fold desc="IRC->Discord (via IRC thread)">
    public void onIRCConnect()
    {
        queue.add( () -> {
            DISCORD.sendMessage("••• Connected to IRC");
            discordSetOnlineStatus();

            // Courtesy message for those on IRC
            if ( DISCORD.isAvailable() )
            {
                IRC.setAway("");
                IRC.sendMessage(ircFirstTime
                    ? "••• Now bridging chat between Discord and IRC"
                    : "••• Lost connection; restored bridge between Discord and IRC");
            }
            else
            {
                IRC.setAway("Waiting for connection to Discord...");
                IRC.sendMessage(ircFirstTime
                    ? "••• Waiting for connection to Discord..."
                    : "••• Lost connection to both Discord and IRC; reconnecting to Discord...");
            }

            ircFirstTime = false;
        });
    }

    public void onIRCDisconnect()
    {
        queue.add( () -> {
            DISCORD.sendMessage("••• Lost connection to IRC; reconnecting...");
            discordSetOfflineStatus();
        });
    }

    public void onIRCMessage(User user, String message)
    {
        queue.add( () -> DISCORD.sendMessageWithMentions("<**%s**> %s", user.getNick(), message) );
    }

    public void onIRCAction(User user, String action)
    {
        queue.add( () -> DISCORD.sendMessageWithMentions("_**%s** %s_", user.getNick(), action) );
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
                reasonPart
            );
        });
    }

    public void onIRCNickChange(String oldNick, String newNick)
    {
        queue.add( () ->
            DISCORD.sendMessage("••• **%s** changed nick to **%s**", oldNick, newNick)
        );
    }
    //</editor-fold>

    //<editor-fold desc="Discord->IRC (via Discord thread)">
    public void onDiscordConnect()
    {
        queue.add( () -> {
            IRC.setAway("");
            IRC.sendMessage("••• Connected to Discord");

            // Courtesy message for those on Discord
            if ( IRC.isAvailable() )
            {
                discordSetOnlineStatus();
                DISCORD.sendMessage(discordFirstTime
                    ? "••• Now bridging chat between Discord and IRC"
                    : "••• Lost connection; restored bridge between Discord and IRC"
                );
            }
            else
            {
                discordSetOfflineStatus();
                DISCORD.sendMessage(discordFirstTime
                    ? "••• Waiting for connection to IRC..."
                    : "••• Lost connection to both Discord and IRC; reconnecting to IRC..."
                );
            }

            if (!discordFirstTime && lastDiscordMessageSent != null)
            {
                DISCORD.sendMessage(
                    "••• Last message sent to IRC: \"%s\"", lastDiscordMessageSent
                );

                lastDiscordMessageSent = null;
            }

            discordFirstTime = false;
        });
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
            String   who      = event.getMember().getEffectiveName();
            String   msg      = event.getMessage().getContent().trim();
            String[] attaches = event.getMessage().getAttachments().stream()
                .map(Message.Attachment::getUrl)
                .toArray(String[]::new);

            if (msg.isEmpty() && attaches.length <= 0)
            {
                LOG.info("[Bridge] Skipping empty Discord message by {}", who);
                return;
            }

            // Handle file attachments (e.g. messages) as URLs
            if (attaches.length > 0)
                msg += " " + String.join(" ", attaches);

            String  finalMsg = EmojiParser.parseToAliases( msg.trim() );
            boolean isAction = false;

            // Special handling for Discord action messages
            if ( finalMsg.startsWith("_") && finalMsg.endsWith("_") )
            {
                finalMsg = finalMsg.substring( 1, finalMsg.length() - 1 );
                isAction = true;
            }

            String[] lines = finalMsg.split("\n");

            // Reject if too many lines
            if (lines.length > 3)
            {
                String start = StringUtils.substring(lines[0], 0, 10);
                DISCORD.sendMessage(
                    "**%s**: Your message (starting '%s...') has more than 3 lines, and has not"
                  + " been sent to IRC. Please try to split your message up, or remove newlines.",
                    event.getMember().getAsMention(), start
                );
                return;
            }

            for (String line : lines)
                if (isAction)
                {
                    if ( IRC.sendAction(who, line) )
                        lastDiscordMessageSent = who + " " + msg;
                }
                else
                {
                    if ( IRC.sendMessage("<%s> %s", who, line) )
                        lastDiscordMessageSent = who + ": " + msg;
                }
        });
    }

    public void onDiscordUserJoin(GuildMemberJoinEvent event)
    {
        queue.add( () -> {
            String who = event.getMember().getEffectiveName();

            IRC.sendMessage( "••• %s joined the server", who);
        });
    }

    public void onDiscordUserLeave(GuildMemberLeaveEvent event)
    {
        queue.add( () ->
            IRC.sendMessage( "••• %s quit the server", event.getMember().getEffectiveName() )
        );
    }

    public void onDiscordNickChange(String oldNick, String newNick)
    {
        queue.add( () -> IRC.sendMessage("••• %s changed nick to %s", oldNick, newNick) );
    }
    //</editor-fold>
}

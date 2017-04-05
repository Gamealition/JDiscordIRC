package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.pircbotx.User;

import static roycurtis.jdiscordirc.JDiscordIRC.*;

/** Acts as a coordinator of events between IRC and Discord */
public class BridgeManager
{
    //<editor-fold desc="Validators">
    private boolean isIRCAvailable()
    {
        if (IRC.bot == null)
            return false;
        else if ( !IRC.bot.isConnected() )
            return false;
        else
            return IRC.bot.getUserChannelDao().containsChannel("#vprottest");
    }

    private boolean isDiscordAvailable()
    {
        if (DISCORD.bot == null)
            return false;
        else return DISCORD.bot.getStatus() == JDA.Status.CONNECTED;
    }
    //</editor-fold>

    //<editor-fold desc="IRC->Discord">
    public void sendDiscordMessage(String msg, Object... parts)
    {
        if ( !isDiscordAvailable() )
        {
            log("[Bridge] Not forwarding IRC message; Discord unavailable: %s", msg);
            return;
        }

        String fullMsg = String.format(msg, parts);
        log("IRC->Discord: %s", fullMsg);
        DISCORD.bot
            .getTextChannelById("299214234645037056")
            .sendMessage(fullMsg)
            .complete();
    }

    public void onIRCConnect()
    {
        sendDiscordMessage("Connected to IRC");
    }

    public void onIRCDisconnect()
    {
        sendDiscordMessage("Lost connection to IRC; reconnecting. . .");
    }

    public void onIRCMessage(User user, String message)
    {
        sendDiscordMessage("%s: %s", user.getNick(), message);
    }

    public void onIRCJoin(User user)
    {
        sendDiscordMessage("%s joined the channel", user.getNick() );
    }

    public void onIRCPart(User user, String reason)
    {
        sendDiscordMessage("%s left the channel (%s)", user.getNick(), reason );
    }

    public void onIRCQuit(User user, String reason)
    {
        sendDiscordMessage("%s quit the server (%s)", user.getNick(), reason );
    }

    public void onIRCKick(User target, User kicker, String reason)
    {
        sendDiscordMessage("%s was kicked by %s (%s)",
            target.getNick(),
            kicker.getNick(),
            reason
        );
    }
    //</editor-fold>

    //<editor-fold desc="Discord->IRC">
    public void sendIRCMessage(String msg, Object... parts)
    {
        if ( !isIRCAvailable() )
        {
            log("[Bridge] Not forwarding Discord message; IRC unavailable: %s", msg);
            return;
        }

        String fullMsg = String.format(msg, parts);
        log("Discord->IRC: %s", fullMsg);
        IRC.bot.send().message("#vprottest", fullMsg);
    }

    public void onDiscordConnect()
    {
        sendIRCMessage("Connected to Discord");
    }

    public void onDiscordDisconnect()
    {
        sendIRCMessage("Lost connection to Discord; reconnecting. . .");
    }

    public void onDiscordMessage(MessageReceivedEvent event)
    {
        sendIRCMessage("%s: %s",
            event.getMember().getEffectiveName(),
            event.getMessage().getContent()
        );
    }

    public void onDiscordUserJoin(GuildMemberJoinEvent event)
    {
        sendIRCMessage("%s joined the server",
            event.getMember().getEffectiveName()
        );
    }

    public void onDiscordUserLeave(GuildMemberLeaveEvent event)
    {
        sendIRCMessage("%s quit the server",
            event.getMember().getEffectiveName()
        );
    }
    //</editor-fold>
}

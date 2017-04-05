package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.pircbotx.User;

import static roycurtis.jdiscordirc.JDiscordIRC.DISCORD;
import static roycurtis.jdiscordirc.JDiscordIRC.IRC;

/** Acts as a coordinator of events between IRC and Discord */
public class BridgeManager
{
    //<editor-fold desc="IRC->Discord">
    public void onIRCConnect()
    {
        DISCORD.sendMessage("Connected to IRC");
    }

    public void onIRCDisconnect()
    {
        DISCORD.sendMessage("Lost connection to IRC; reconnecting. . .");
    }

    public void onIRCMessage(User user, String message)
    {
        DISCORD.sendMessageAs(user.getNick(), "%s", message);
    }

    public void onIRCJoin(User user)
    {
        DISCORD.sendMessage("%s joined the channel", user.getNick() );
    }

    public void onIRCPart(User user, String reason)
    {
        DISCORD.sendMessage("%s left the channel (%s)", user.getNick(), reason );
    }

    public void onIRCQuit(User user, String reason)
    {
        DISCORD.sendMessage("%s quit the server (%s)", user.getNick(), reason );
    }

    public void onIRCKick(User target, User kicker, String reason)
    {
        DISCORD.sendMessage("%s was kicked by %s (%s)",
            target.getNick(),
            kicker.getNick(),
            reason
        );
    }
    //</editor-fold>

    //<editor-fold desc="Discord->IRC">
    public void onDiscordConnect()
    {
        IRC.sendMessage("Connected to Discord");
    }

    public void onDiscordDisconnect()
    {
        IRC.sendMessage("Lost connection to Discord; reconnecting. . .");
    }

    public void onDiscordMessage(MessageReceivedEvent event)
    {
        IRC.sendMessage("%s: %s",
            event.getMember().getEffectiveName(),
            event.getMessage().getContent()
        );
    }

    public void onDiscordUserJoin(GuildMemberJoinEvent event)
    {
        IRC.sendMessage("%s joined the server",
            event.getMember().getEffectiveName()
        );
    }

    public void onDiscordUserLeave(GuildMemberLeaveEvent event)
    {
        IRC.sendMessage("%s quit the server",
            event.getMember().getEffectiveName()
        );
    }
    //</editor-fold>
}

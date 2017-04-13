package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.DisconnectEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.ResumedEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.CloseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roycurtis.jdiscordirc.JDiscordIRC.BRIDGE;
import static roycurtis.jdiscordirc.JDiscordIRC.CONFIG;

public class DiscordManager extends ListenerAdapter
{
    private static final Logger  LOG     = LoggerFactory.getLogger(DiscordManager.class);
    private static final Pattern MENTION = Pattern.compile("\\B@([\\S]+)\\b");

    private JDA    bot;
    private String token;
    private String guildId;
    private String channelId;
    private String url;

    //<editor-fold desc="Manager methods (main thread)">
    public void init() throws Exception
    {
        LOG.info("Connecting for first time...");

        token     = CONFIG.get("discord.token");
        guildId   = CONFIG.get("discord.guild");
        channelId = CONFIG.get("discord.channel");
        url       = CONFIG.get("discord.url");

        bot = new JDABuilder(AccountType.BOT)
            .setAudioEnabled(false)
            .setToken(token)
            .addEventListener(this)
            .buildAsync();
    }

    public boolean isAvailable()
    {
        if (bot == null)
            return false;
        else return bot.getStatus() == JDA.Status.CONNECTED;
    }

    public void sendMessage(String msg, Object... parts)
    {
        if ( !isAvailable() )
        {
            LOG.debug("Rejecting message; Discord unavailable: {}", msg);
            return;
        }

        String fullMsg = String.format(msg, parts);
        LOG.info("Sent: {}", fullMsg);
        bot.getTextChannelById(channelId)
            .sendMessage(fullMsg)
            .complete();
    }

    public void sendMessageWithMentions(String msg, Object... parts)
    {
        if ( !isAvailable() )
        {
            LOG.debug("Rejecting message; Discord unavailable: {}", msg);
            return;
        }

        String      fullMsg = String.format(msg, parts);
        Matcher     matcher = MENTION.matcher(fullMsg);
        TextChannel channel = bot.getTextChannelById(channelId);
        Guild       guild   = bot.getGuildById(guildId);

        // Skip processing mentions if none seem to exist
        if ( !matcher.find() )
        {
            LOG.info("Sent: {}", fullMsg);
            channel.sendMessage(fullMsg).complete();
            return;
        }

        // Have to reset from above one-time use of find
        matcher.reset();
        StringBuffer buffer = new StringBuffer();

        // Iterate through any detected mentions and try to link to member
        while ( matcher.find() )
        {
            String       mention = matcher.group(1);
            List<Member> matches = guild.getMembersByEffectiveName(mention, true);

            // Skip if no matches, or ambiguous matches
            if (matches.size() < 1 || matches.size() > 1)
                continue;

            Member member = matches.get(0);

            // Skip if member is not actually in channel
            if ( !member.hasPermission(channel, Permission.MESSAGE_READ) )
                continue;

            // Convert match into a real mention, add to string
            matcher.appendReplacement( buffer, member.getAsMention() );
        }

        matcher.appendTail(buffer);
        fullMsg = buffer.toString();

        LOG.info("Sent: {}", fullMsg);
        channel.sendMessage(fullMsg).complete();
    }

    public String getUrl()
    {
        return url;
    }

    public void setStatus(OnlineStatus status, String game, String url)
    {
        bot.getPresence().setStatus(status);
        bot.getPresence().setGame( Game.of(game, url) );
    }
    //</editor-fold>

    //<editor-fold desc="Bot event handlers (Discord thread)">
    @Override
    public void onReady(ReadyEvent event)
    {
        LOG.info("Connected successfully");
        BRIDGE.onDiscordConnect();
    }

    @Override
    public void onReconnect(ReconnectedEvent event)
    {
        LOG.info("Reconnected");
        BRIDGE.onDiscordConnect();
    }

    @Override
    public void onResume(ResumedEvent event)
    {
        LOG.info("Reconnected");
        BRIDGE.onDiscordConnect();
    }

    @Override
    public void onDisconnect(DisconnectEvent event)
    {
        // Thanks to Minn for the following, for better disconnect explaination.
        // Cannot use event.getClientCloseFrame due to unknown issue with WebSocketFrame
        String    why;
        CloseCode code = event.getCloseCode();

        if (code != null)
            why = String.format( "%s: %s", code.getCode(), code.getMeaning() );
        else
            why = event.isClosedByServer()
                ? "closed by server"
                : "closed by client";

        LOG.warn("Lost connection ({}); reconnecting...", why);
        BRIDGE.onDiscordDisconnect();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // Ignore own messages
        if ( event.getAuthor().equals( bot.getSelfUser() ) )
            return;

        // Ignore messages from other channels
        if ( !event.getChannel().getId().contentEquals(channelId) )
            return;

        LOG.trace( "Message from {} with {} attachment(s): {}",
            event.getMember().getEffectiveName(),
            event.getMessage().getAttachments().size(),
            event.getMessage().getContent()
        );
        BRIDGE.onDiscordMessage(event);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event)
    {
        // Ignore from other servers
        if ( !event.getGuild().getId().contentEquals(guildId) )
            return;

        LOG.trace( "{} joined the server", event.getMember().getEffectiveName() );
        BRIDGE.onDiscordUserJoin(event);
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event)
    {
        // Ignore from other servers
        if ( !event.getGuild().getId().contentEquals(guildId) )
            return;

        LOG.trace( "{} quit the server", event.getMember().getEffectiveName() );
        BRIDGE.onDiscordUserLeave(event);
    }

    @Override
    public void onGuildMemberNickChange(GuildMemberNickChangeEvent event)
    {
        // Ignore from other servers
        if ( !event.getGuild().getId().contentEquals(guildId) )
            return;

        String oldNick = event.getPrevNick();
        String newNick = event.getNewNick();

        // Adding or removing a nickname means one or the other is null
        if (oldNick == null) oldNick = event.getMember().getUser().getName();
        if (newNick == null) newNick = event.getMember().getUser().getName();

        LOG.trace("{} changed nick to {}", oldNick, newNick);
        BRIDGE.onDiscordNickChange(oldNick, newNick);
    }
    //</editor-fold>
}

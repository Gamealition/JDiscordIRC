package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.events.DisconnectEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.ResumedEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import static roycurtis.jdiscordirc.JDiscordIRC.BRIDGE;
import static roycurtis.jdiscordirc.JDiscordIRC.log;

public class DiscordManager extends ListenerAdapter
{
    // TODO: Make these config
    public static final String GUILD   = "299214234645037056";
    public static final String CHANNEL = "299214234645037056";
    public static final String TOKEN   = "";

    private JDA bot;

    //<editor-fold desc="Manager methods (main thread)">
    public void init() throws Exception
    {
        log("[Discord] Connecting for first time...");

        bot = new JDABuilder(AccountType.BOT)
            .setAudioEnabled(false)
            .setToken(TOKEN)
            .setStatus(OnlineStatus.DO_NOT_DISTURB)
            .setGame( Game.of("Connecting to IRC...", "https://irc.gamealition.com") )
            .addListener(this)
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
            log("[Discord] Rejecting message; Discord unavailable: %s", msg);
            return;
        }

        String fullMsg = String.format(msg, parts);
        log("Discord: %s", fullMsg);

        bot.getTextChannelById(CHANNEL)
            .sendMessage(fullMsg)
            .complete();
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
        log("[Discord] Connected successfully");
        BRIDGE.onDiscordConnect();
    }

    @Override
    public void onReconnect(ReconnectedEvent event)
    {
        log("[Discord] Reconnected");
        BRIDGE.onDiscordConnect();
    }

    @Override
    public void onResume(ResumedEvent event)
    {
        log("[Discord] Reconnected");
        BRIDGE.onDiscordConnect();
    }

    @Override
    public void onDisconnect(DisconnectEvent event)
    {
        log( "[Discord] Lost connection (%s); reconnecting...", event.getCloseCode() );
        BRIDGE.onDiscordDisconnect();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // Ignore own messages
        if ( event.getAuthor().equals( bot.getSelfUser() ) )
            return;

        // Ignore messages from other channels
        if ( !event.getChannel().getId().contentEquals(CHANNEL) )
            return;

        log( "[Discord] Message from %s with %s attachment(s): %s",
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
        if ( !event.getGuild().getId().contentEquals(GUILD) )
            return;

        log( "[Discord] %s joined the server", event.getMember().getEffectiveName() );
        BRIDGE.onDiscordUserJoin(event);
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event)
    {
        // Ignore from other servers
        if ( !event.getGuild().getId().contentEquals(GUILD) )
            return;

        log( "[Discord] %s quit the server", event.getMember().getEffectiveName() );
        BRIDGE.onDiscordUserLeave(event);
    }
    //</editor-fold>
}

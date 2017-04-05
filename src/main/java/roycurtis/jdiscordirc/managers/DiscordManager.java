package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.events.DisconnectEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import static roycurtis.jdiscordirc.JDiscordIRC.BRIDGE;
import static roycurtis.jdiscordirc.JDiscordIRC.log;

public class DiscordManager extends ListenerAdapter
{
    protected JDA bot;

    //<editor-fold desc="Manager methods (main thread)">
    public void init() throws Exception
    {
        log("[Discord] Connecting for first time...");

        bot = new JDABuilder(AccountType.BOT)
            .setAudioEnabled(false)
            .setToken("")
            .addListener(this)
            .buildAsync();
    }

    public void takedown()
    {
        if (bot == null)
            return;

        bot.shutdown();
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
        if ( !event.getChannel().getId().contentEquals("299214234645037056") )
            return;

        log( "[Discord] Message by %s: %s",
            event.getMember().getEffectiveName(),
            event.getMessage().getRawContent()
        );
        BRIDGE.onDiscordMessage(event);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event)
    {
        log( "[Discord] %s joined the server", event.getMember().getEffectiveName() );
        BRIDGE.onDiscordUserJoin(event);
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event)
    {
        log( "[Discord] %s quit the server", event.getMember().getEffectiveName() );
        BRIDGE.onDiscordUserLeave(event);
    }
    //</editor-fold>
}

package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.events.DisconnectEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.ResumedEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.util.Objects;

import static roycurtis.jdiscordirc.JDiscordIRC.BRIDGE;
import static roycurtis.jdiscordirc.JDiscordIRC.log;

public class DiscordManager extends ListenerAdapter
{
    // TODO: Make these config
    private static final String TOKEN   = "";
    private static final String GUILD   = "299214234645037056";
    private static final String CHANNEL = "299214234645037056";

    private JDA    bot;
    private String nickname;

    //<editor-fold desc="Manager methods (main thread)">
    public void init() throws Exception
    {
        log("[Discord] Connecting for first time...");

        bot = new JDABuilder(AccountType.BOT)
            .setAudioEnabled(false)
            .setToken(TOKEN)
            .addListener(this)
            .buildAsync();
    }

    public void takedown()
    {
        if (bot == null)
            return;

        bot.shutdown();
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
        log("IRC->Discord: %s", fullMsg);

        setNickname(null);
        bot.getTextChannelById(CHANNEL)
            .sendMessage(fullMsg)
            .complete();
    }

    public void sendMessageAs(String who, String msg, Object... parts)
    {
        if ( !isAvailable() )
        {
            log("[Bridge] Not forwarding IRC message; Discord unavailable: %s", msg);
            return;
        }

        String fullMsg = String.format(msg, parts);
        log("IRC->Discord: %s: %s", who, fullMsg);

        setNickname(who);
        bot.getTextChannelById(CHANNEL)
            .sendMessage(fullMsg)
            .complete();
    }

    public void setNickname(String nickname)
    {
        // Nickname changing is slow; skip if same.
        // Uses Objects.equals as String.equals doesn't handle nulls properly
        if ( Objects.equals(this.nickname, nickname) )
            return;

        Member self = bot.getGuildById(GUILD).getSelfMember();

        bot.getGuildById(GUILD)
            .getController()
            .setNickname(self, nickname)
            .complete();

        this.nickname = nickname;
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

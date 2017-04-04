package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.events.DisconnectEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import static roycurtis.jdiscordirc.JDiscordIRC.log;

public class DiscordManager extends ListenerAdapter
{
    private JDA jda;

    /* Manager methods (main thread) */

    public void setup() throws Exception
    {
        log("[Discord] Connecting for first time...");
        log("THREAD: " + Thread.currentThread().getName());

        jda = new JDABuilder(AccountType.BOT)
            .setAudioEnabled(false)
            .setToken("")
            .addListener(this)
            .buildAsync();
    }

    public void takedown()
    {
        jda.shutdown();
    }

    /* Event handlers (out of main thread) */

    @Override
    public void onReady(ReadyEvent event)
    {
        log("[Discord] Connected");
    }

    @Override
    public void onReconnect(ReconnectedEvent event)
    {
        log("[Discord] Reconnected");
    }

    @Override
    public void onDisconnect(DisconnectEvent event)
    {
        log("[Discord] Lost connection! Auto-reconnecting...");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        log("[Discord] Message: " + event.getMessage().getRawContent() + " (from " + event.getChannel().getName() + ")");
        log("[Discord] THREAD: " + Thread.currentThread().getName());
    }
}

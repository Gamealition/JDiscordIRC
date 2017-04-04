package roycurtis.jdiscordirc.managers;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BridgeManager
{
    private Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /* Manager method (main thread) */

    public void pump()
    {
        Runnable task = tasks.poll();

        if (task == null)
            return;
    }

    /* Event handlers (out of main thread) */

    public void onDiscordMessage(MessageReceivedEvent event)
    {
        tasks.add(() -> {
            
        });
    }

}

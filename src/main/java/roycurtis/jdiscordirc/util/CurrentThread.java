package roycurtis.jdiscordirc.util;

public class CurrentThread
{
    /** A version of Thread.sleep without the unnecessary exception */
    public static void sleep(int mills)
    {
        try
        {
            java.lang.Thread.sleep(mills);
        }
        catch (InterruptedException ignored)
        {

        }
    }
}

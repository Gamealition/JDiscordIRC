package roycurtis.jdiscordirc.managers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import roycurtis.jdiscordirc.JDiscordIRC;

import java.io.*;
import java.util.Properties;

public class ConfigManager
{
    private static final Logger LOG         = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "config.properties";

    private Properties props = new Properties();

    public void init() throws Exception
    {
        ClassLoader loader = JDiscordIRC.class.getClassLoader();
        File        config = new File(CONFIG_FILE);

        if ( config.isFile() ) try ( InputStream stream = new FileInputStream(config) )
        {
            props.load(stream);
            LOG.debug("Loaded configuration from file");
        }
        else try (
            InputStream  inStream  = loader.getResourceAsStream(CONFIG_FILE);
            OutputStream outStream = new FileOutputStream(CONFIG_FILE) )
        {
            byte[] buffer = new byte[ inStream.available() ];

            inStream.read(buffer);
            outStream.write(buffer);
            outStream.flush();

            JDiscordIRC.exit("First-time setup; please fill in the config file and restart");
        }
    }

    public String get(String prop)
    {
        if ( !props.containsKey(prop) )
            throw new RuntimeException("Missing config: " + prop);

        return props.getProperty(prop);
    }
}

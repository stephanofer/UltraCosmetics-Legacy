package be.isach.ultracosmetics.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;

public class MessageResourceTest {

    @Test
    public void previewLabelsAreValidYaml() throws Exception {
        assertValidYaml("messages/joinmessages.yml");
        assertValidYaml("messages/messages_es.yml");
    }

    private void assertValidYaml(String path) throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull("Missing resource: " + path, stream);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            new YamlConfiguration().load(reader);
        }
    }
}

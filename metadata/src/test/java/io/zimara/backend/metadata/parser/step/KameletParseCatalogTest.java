package io.zimara.backend.metadata.parser.step;

import io.quarkus.test.junit.QuarkusTest;
import io.zimara.backend.metadata.ParseCatalog;
import io.zimara.backend.metadata.catalog.InMemoryCatalog;
import io.zimara.backend.model.Metadata;
import io.zimara.backend.model.step.Step;
import io.zimara.backend.model.step.kamelet.KameletStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@QuarkusTest
class KameletParseCatalogTest {

    @Test
    void getSteps() {
        ParseCatalog<Step> kameletParser =
                KameletParseCatalog.getParser(
                        "https://github.com/apache/camel-kamelets.git",
                        "v0.3.0");
        InMemoryCatalog<Step> catalog = new InMemoryCatalog<>();

        List<Step> steps = kameletParser.parse().join();
        Assertions.assertTrue(catalog.store(steps));
        Assertions.assertEquals(catalog.getAll().size(), steps.size());

        String name = "ftp-source";
        KameletStep step = (KameletStep) catalog.searchStepByName(name);
        Assertions.assertNotNull(step);
        Assertions.assertEquals(name, step.getId());
        Assertions.assertEquals(name, step.getName());
        Assertions.assertEquals("KAMELET", step.getSubType());
        Assertions.assertEquals("START", step.getType());
        Assertions.assertNotNull(step.getParameters());
        Assertions.assertEquals(8, step.getParameters().size());
        for (var p : step.getParameters()) {
            Assertions.assertNotNull(p.getType());
            Assertions.assertNotNull(p.getLabel());
            Assertions.assertNotNull(p.getId());
            Assertions.assertNotNull(p.getDefault());
        }
    }

    @Test
    void wrongUrlSilentlyFails() {
        ParseCatalog<Step> kameletParser =
                KameletParseCatalog.getParser(
                        "https://nothing/wrong/url.git",
                        "");

        List<Step> steps = kameletParser.parse().join();
        Assertions.assertNotNull(steps);
        Assertions.assertEquals(0, steps.size());
    }

    @Test
    void compareJarAndGit() {

        ParseCatalog<Step> kameletParserGit =
                KameletParseCatalog.getParser(
                        "https://github.com/apache/camel-kamelets.git",
                        "v0.4.0");
        List<Step> stepsGit = kameletParserGit.parse().join();


        String jarUrl = "https://repo1.maven.org/maven2/org/apache/camel/"
                + "kamelets/camel-kamelets/0.4.0/camel-kamelets-0.4.0.jar";


        ParseCatalog<Step> kameletParserJar =
                KameletParseCatalog.getParser(
                        jarUrl);
        List<Step> stepsJar = kameletParserJar.parse().join();

        Assertions.assertEquals(stepsJar.size(), stepsGit.size());

        Collections.sort(stepsJar, Comparator.comparing(Metadata::getId));
        Collections.sort(stepsGit, Comparator.comparing(Metadata::getId));

        Assertions.assertIterableEquals(stepsJar, stepsGit);
    }

}

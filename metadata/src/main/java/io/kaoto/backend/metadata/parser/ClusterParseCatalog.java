package io.kaoto.backend.metadata.parser;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.kaoto.backend.metadata.ParseCatalog;
import io.kaoto.backend.model.Metadata;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 🐱class ClusterParseCatalog
 * 🐱inherits ParseCatalog
 * Abstract implementation that extracts CustomResources from a cluster
 * and parses them to add to a catalog.
 */
public class ClusterParseCatalog<T extends Metadata>
        implements ParseCatalog<T> {

    private final Class<? extends CustomResource> cr;
    private Logger log = Logger.getLogger(ClusterParseCatalog.class);

    public void setKubernetesClient(final KubernetesClient kubernetesClient) {
        log.error(kubernetesClient);
        this.kubernetesClient = kubernetesClient;
    }
    private KubernetesClient kubernetesClient;

    private ProcessFile<T> yamlProcessFile;

    public ClusterParseCatalog(final Class<? extends CustomResource> cr) {
        this.cr = cr;
    }

    private List<T> getCRAndParse(final Class<? extends CustomResource> cr) {
        log.trace("Warming up repository from cluster.");

        List<T> metadataList =
                Collections.synchronizedList(new CopyOnWriteArrayList<>());
        final List<CompletableFuture<Void>> futureMd =
                Collections.synchronizedList(new CopyOnWriteArrayList<>());
        File dir = null;
        try {
            log.trace("Creating temporary folder.");
            dir = Files.createTempDirectory("kaoto-catalog").toFile();
            dir.setExecutable(true, true);
            dir.setReadable(true, true);
            dir.setWritable(true, true);
        } catch (IOException e) {
            log.error("Error trying to create temporary directory.", e);
        }

        try {
            var constructor = new Constructor(cr);
            Yaml yaml = new Yaml(constructor);

            final var resources =
                kubernetesClient.resources(cr)
                        .inAnyNamespace()
                        .list().getItems();

            for (CustomResource integration : resources) {
                try {
                    FileAttribute<Set<PosixFilePermission>> attr =
                            PosixFilePermissions.asFileAttribute(
                                    PosixFilePermissions
                                            .fromString("rwx------"));
                    Path tmp = Files.createTempFile(
                            dir.toPath(), "remote-", ".yaml", attr);
                    try (FileOutputStream fos =
                                 new FileOutputStream(tmp.toFile())) {

                        InputStreamReader isr =
                                new InputStreamReader(new ByteArrayInputStream(
                                        yaml.dumpAsMap(integration)
                                            .getBytes(StandardCharsets.UTF_8)));

                        IOUtils.copy(isr, fos, StandardCharsets.UTF_8);
                        tmp.toFile().deleteOnExit();
                    }
                } catch (IOException e) {
                    log.error("Error trying to create temporary file.", e);
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving elements from cluster.", e);
        }

        //Walk the expanded directory
        log.trace("Parsing all files in the cluster");
        this.yamlProcessFile.setFutureMetadata(futureMd);
        this.yamlProcessFile.setMetadataList(metadataList);
        try {
            Files.walkFileTree(dir.toPath(), this.yamlProcessFile);
        } catch (IOException e) {
            log.error("Error parsing elements on the cluster", e);
        }

        log.trace("Found " + futureMd.size() + " elements.");
        CompletableFuture.allOf(
                        futureMd.toArray(new CompletableFuture[0]))
                .join();

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            log.error("Error cleaning up catalog.", e);
        }

        return metadataList;
    }

    @Override
    public CompletableFuture<List<T>> parse() {
        CompletableFuture<List<T>> metadata = new CompletableFuture<>();
        metadata.completeAsync(() -> getCRAndParse(cr));
        return metadata;
    }

    @Override
    public void setFileVisitor(final ProcessFile<T> fileVisitor) {
        this.yamlProcessFile = fileVisitor;
    }
}

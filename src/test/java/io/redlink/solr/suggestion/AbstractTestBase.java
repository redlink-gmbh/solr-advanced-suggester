package io.redlink.solr.suggestion;

import io.redlink.utils.PathUtils;
import io.redlink.utils.ResourceLoaderUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.core.SolrCore;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractTestBase extends SolrTestCaseJ4 {

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected static SolrCore core;

    @BeforeClass
    public static void prepareIndex() throws Exception {
        System.setProperty("runtimeLib", "false");
        System.setProperty("solr.directoryFactory", "solr.MockDirectoryFactory");

        final File solrhome = temporaryFolder.newFolder("solrhome");
        final Path coreConfig = solrhome.toPath().resolve("core/conf");
        Files.createDirectories(coreConfig);
        PathUtils.copyRecursive(ResourceLoaderUtils.getResourceAsPath("solr-home/config").toAbsolutePath(), coreConfig);

        initCore("solrconfig.xml", "schema.xml", solrhome.getAbsolutePath(), "core");
        core = h.getCore();
    }
}

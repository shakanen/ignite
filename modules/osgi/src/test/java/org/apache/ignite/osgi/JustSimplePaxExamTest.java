package org.apache.ignite.osgi;

import java.io.File;
import javax.inject.Inject;
import org.apache.karaf.features.FeaturesService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.BundleContext;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class JustSimplePaxExamTest {

    /** Pax Exam will inject the Bundle Context here. */
    @Inject
    protected BundleContext bundleCtx;

    /** Pax Exam will inject the Karaf Features Service. */
    @Inject
    protected FeaturesService featuresSvc;

    @Configuration
    public Option[] configuration() {
        return options(
            // Specify which version of Karaf to use.
            karafDistributionConfiguration()
                .frameworkUrl(maven().groupId("org.apache.karaf").artifactId("apache-karaf")
                    .type("tar.gz")
                    .versionAsInProject()
                )
                .karafVersion(System.getProperty("karafVersion"))
                .useDeployFolder(false)
                .unpackDirectory(new File("target/paxexam/unpack")),
            junitBundles()
        );
    }

    @Test
    public void testSystemStarts() {
        Assert.assertNotNull("BundleContext should not be null", bundleCtx);
    }

}

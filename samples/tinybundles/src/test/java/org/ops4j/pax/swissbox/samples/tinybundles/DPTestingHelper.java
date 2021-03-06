package org.ops4j.pax.swissbox.samples.tinybundles;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import static org.junit.Assert.*;
import org.ops4j.io.StreamUtils;
import org.ops4j.pax.swissbox.tinybundles.dp.Constants;

/**
 * Utility methods to test deploymentpackages for wellformdness, correctness and integrity.
 */
public class DPTestingHelper
{

    public static InputStream flush( InputStream inp )
        throws IOException
    {
        File f = File.createTempFile( "dest", ".dp" );
        StreamUtils.copyStream( inp, new FileOutputStream( f ), true );
        System.out.println( "--> Flushed to " + f.getAbsolutePath() );
        return new FileInputStream( f );
    }

    /**
     * Checks mandatory fields and recommendations.
     *
     * @param inputStream DeploymentPackage to be tested.
     * @param fixPack     if set to true, additional fields are checked.
     *
     * @throws IOException Reading Errors
     */
    public static void verifyStandardHeaders( InputStream inputStream, boolean fixPack )
        throws IOException
    {
        JarInputStream jout = new JarInputStream( inputStream );
        Manifest man = jout.getManifest();
        assertEquals( "application/vnd.osgi.dp", man.getMainAttributes().getValue( "Content-Type" ) );
        assertNotNull( "Header " + Constants.DEPLOYMENTPACKAGE_SYMBOLICMAME + " must be set.", man.getMainAttributes().getValue( Constants.DEPLOYMENTPACKAGE_SYMBOLICMAME ) );
        assertNotNull( "Header " + Constants.DEPLOYMENTPACKAGE_VERSION + " must be set.", man.getMainAttributes().getValue( Constants.DEPLOYMENTPACKAGE_VERSION ) );

        if( fixPack )
        {
            String fixPackValue = man.getMainAttributes().getValue( Constants.DEPLOYMENTPACKAGE_FIXPACK );
            assertNotNull( "manifest should have a " + Constants.DEPLOYMENTPACKAGE_FIXPACK + " header set.", fixPackValue );
        }
        jout.close();
    }

    /**
     * Checks non missing fields.
     * So they must have name section, must not be "missing" (header) and need a valid entry in jar content.
     *
     * No other content must appear.
     *
     * @param inputStream     DeploymentPackage to be tested.
     * @param expectedEntries list of entries expected as non missing
     *
     * @throws IOException Reading Errors
     */
    public static void verifyNonMissing( InputStream inputStream, String... expectedEntries )
        throws IOException
    {
        // verify manifest entries:
        JarInputStream jout = new JarInputStream( inputStream );
        Manifest man = jout.getManifest();
        assertEquals( "application/vnd.osgi.dp", man.getMainAttributes().getValue( "Content-Type" ) );
        Map<String, Attributes> attributesMap = man.getEntries();
        Set<String> contentHeaders = new HashSet<String>();
        Collections.addAll( contentHeaders, expectedEntries );

        for( String key : attributesMap.keySet() )
        {
            if( !contentHeaders.remove( key ) )
            {
                // skip unchanged fix-pack entries
                if( !"true".equals( attributesMap.get( key ).getValue( Constants.DEPLOYMENTPACKAGE_MISSING ) ) )
                {
                    fail( "Unexpected section in manifest: " + key );
                }
            }
        }

        if( !contentHeaders.isEmpty() )
        {
            for( String s : contentHeaders )
            {
                System.err.println( "Missing Header in manifest: " + s );
                fail( "Missing Header in manifest!" );
            }
        }

        // verify content

        // assume the following content:
        Set<String> content = new HashSet<String>();
        Collections.addAll( content, expectedEntries );
        JarEntry entry = null;

        while( ( entry = jout.getNextJarEntry() ) != null )
        {
            if( !content.remove( entry.getName() ) )
            {
                fail( "Unexpected content in final output: " + entry.getName() );
            }
        }
        jout.close();
        if( !content.isEmpty() )
        {
            for( String s : content )
            {
                System.err.println( "Missing: " + s );
                fail( "Missing content in final output!" );
            }
        }
    }

    /**
     * Checks if bundles have correct (and matching) headers.
     * So it goes to the special things for bundles.
     *
     * @param inputStream
     * @param expectedEntries
     */
    public static void verifyBundleContents( InputStream inputStream, String... expectedEntries )
        throws IOException
    {
        // verify manifest entries:
        JarInputStream jout = new JarInputStream( inputStream );
        Manifest man = jout.getManifest();
        Map<String, Attributes> attributesMap = man.getEntries();
        Set<String> contentHeaders = new HashSet<String>();
        Collections.addAll( contentHeaders, expectedEntries );

        for( String key : attributesMap.keySet() )
        {
            if( contentHeaders.contains( key ) )
            {
                Attributes att = attributesMap.get( key );
                assertNotNull( "Bundle " + key + " does not have a Bundle-SymbolicName in DP Manifest",
                               att.getValue( Constants.BUNDLE_SYMBOLICNAME )
                );
            }
        }
    }

    public static void verifyMissing( InputStream inputStream, String... missing )
        throws IOException
    {
        // verify manifest entries:
        JarInputStream jout = new JarInputStream( inputStream );
        Manifest man = jout.getManifest();

        Map<String, Attributes> attributesMap = man.getEntries();
        Set<String> contentHeaders = new HashSet<String>();
        Collections.addAll( contentHeaders, missing );

        for( String key : attributesMap.keySet() )
        {
            Attributes att = attributesMap.get( key );

            if( contentHeaders.remove( key ) )
            {
                // its a found fix pack that must have a missing header:
                assertEquals( "Entry " + key + " does not have a " + Constants.DEPLOYMENTPACKAGE_MISSING + "=true in DP Manifest", "true",
                              att.getValue( Constants.DEPLOYMENTPACKAGE_MISSING )
                );
            }
            else
            {
                if( "true".equals( att.getValue( Constants.DEPLOYMENTPACKAGE_MISSING ) ) )
                {
                    fail( "Entry " + key + " was not expected to be missing." );
                }
            }
        }

        if( !contentHeaders.isEmpty() )
        {
            for( String s : contentHeaders )
            {
                System.err.println( "Requested Entries in manifest:: " + s );
                fail( "Requested Entries Missing!" );
            }
        }

        // verify content

        // assume the following content:
        Set<String> content = new HashSet<String>();
        Collections.addAll( content, missing );

        JarEntry entry = null;
        while( ( entry = jout.getNextJarEntry() ) != null )
        {
            if( content.remove( entry.getName() ) )
            {
                fail( "Unchanged Fix-Pack Entries must not appear in final output: " + entry.getName() );
            }
        }
        jout.close();

    }
}

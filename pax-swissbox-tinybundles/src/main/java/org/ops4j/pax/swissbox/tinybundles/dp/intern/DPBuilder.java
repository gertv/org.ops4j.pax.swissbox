/*
 * Copyright 2009 Toni Menzel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.swissbox.tinybundles.dp.intern;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ops4j.io.StreamUtils;
import org.ops4j.pax.swissbox.tinybundles.dp.Constants;
import org.ops4j.store.Store;

/**
 * @author Toni Menzel (tonit)
 * @since Jun 24, 2009
 */
public class DPBuilder
{

    private static Log LOG = LogFactory.getLog( DPBuilder.class );

    /**
     * Build a DeploymentPackage from manually set headers, stored content (cache) and the bucket.
     *
     * @param headers
     * @param cache
     * @param meta
     * @return The final deployment package as stream
     * @throws java.io.IOException
     */
    public InputStream build( Map<String, String> headers, final Store<InputStream> cache, final Bucket meta )
        throws IOException
    {
        // 1. Manifest
        Manifest manifestDP = new Manifest();
        // defaults
        manifestDP.getMainAttributes().putValue( "Manifest-Version", "1.0" );
        manifestDP.getMainAttributes().putValue( "Content-Type", "application/vnd.osgi.dp" );

        // user defined
        for( String key : headers.keySet() )
        {
            manifestDP.getMainAttributes().putValue( key, headers.get( key ) );
        }

        /**
         * First META-INF resources: META-INF/*.SF, META-INF/*.DSA, META-INF/*.RS
         * Then OSGi-INF
         * Then Bundles
         * Then Resources
         */
        final Map<String, Attributes> entries = manifestDP.getEntries();

        for( String name : meta.getEntries() )
        {
            // extract meta data..
            Attributes attr = new Attributes();
            // Fill attr from Wrapped Jar Content
            JarInputStream jout = null;
            try
            {
                jout = new JarInputStream( cache.load( meta.getHandle( name ) ) );
                Manifest man = jout.getManifest();
                // those m_headers are meant to show up in DP Manifest.
                if( meta.isType( name, DPContentType.BUNDLE ) )
                {
                    attr.putValue( Constants.BUNDLE_SYMBOLICNAME, man.getMainAttributes().getValue( Constants.BUNDLE_SYMBOLICNAME ) );
                    attr.putValue( Constants.BUNDLE_VERSION, man.getMainAttributes().getValue( Constants.BUNDLE_VERSION ) );
                }
                else
                {
                    attr.putValue( Constants.RESOURCE_PROCESSOR, "foo" );
                }

                if( meta.isMissing( name ) )
                {
                    attr.putValue( Constants.DEPLOYMENTPACKAGE_MISSING, "true" );
                }
            } finally
            {
                if( jout != null )
                {
                    try
                    {
                        jout.close();
                    } catch( Exception e )
                    {
                        LOG.debug( "Problem closing jar inputstream of " + name, e );
                    }
                }
            }
            entries.put( name, attr );


        }

        final PipedInputStream pin = new PipedInputStream();
        try
        {
            final PipedOutputStream pout = new PipedOutputStream( pin );
            final JarOutputStream jarOut = new JarOutputStream( pout, manifestDP );

            new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        for( String entry : entries.keySet() )
                        {
                            if( !meta.isMissing( entry ) )
                            {
                                copyResource( entry, cache.load( meta.getHandle( entry ) ), jarOut );
                            }
                        }


                    } catch( IOException ioE )
                    {
                        throw new RuntimeException( ioE );
                    } finally
                    {
                        try
                        {
                            if( jarOut != null )
                            {
                                jarOut.close();
                            }
                            pout.close();

                        }
                        catch( Exception e )
                        {
                            // be quiet.
                        }
                        LOG.debug( "Copy thread finished." );
                    }
                }
            }.start();
        } catch( Exception e )
        {
            LOG.error( "problem ! ", e );
        }
        return pin;
    }

    private void copyResource( String nameSection, InputStream inputStream, JarOutputStream jarOut )
        throws IOException
    {
        //LOG.info( "copying Artifact " + nameSection + " using handle: " + handle.getIdentification() );
        ZipEntry zipEntry = new JarEntry( nameSection );
        jarOut.putNextEntry( zipEntry );
        StreamUtils.copyStream( inputStream, jarOut, false );
        jarOut.closeEntry();
    }


}

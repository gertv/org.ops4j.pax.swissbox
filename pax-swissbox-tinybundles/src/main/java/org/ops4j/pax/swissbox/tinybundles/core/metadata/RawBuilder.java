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
package org.ops4j.pax.swissbox.tinybundles.core.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ops4j.io.StreamUtils;
import org.ops4j.pax.swissbox.tinybundles.core.BuildableBundle;
import org.ops4j.pax.swissbox.tinybundles.core.intern.Info;

/**
 * @author Toni Menzel (tonit)
 * @since Apr 20, 2009
 */
public class RawBuilder implements BuildableBundle
{

    private static Log LOG = LogFactory.getLog( RawBuilder.class );
    private static final String ENTRY_MANIFEST = "META-INF/MANIFEST.MF";
    private static final String BUILT_BY = "Built-By";
    private static final String TOOL = "Tool";
    private static final String CREATED_BY = "Created-By";

    public InputStream build( Map<String, URL> resources, Map<String, String> headers )
    {
        return make( resources, headers );
    }

    public InputStream make( final Map<String, URL> resources,
                             final Map<String, String> headers )
    {
        LOG.debug( "setResources()" );
        final PipedInputStream pin = new PipedInputStream();
        try
        {
            final PipedOutputStream pout = new PipedOutputStream( pin );
            final JarOutputStream jarOut = new JarOutputStream( pout );

            new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {

                        JarEntry entry = new JarEntry( ENTRY_MANIFEST );
                        jarOut.putNextEntry( entry );
                        getManifest( headers.entrySet() ).write( jarOut );
                        jarOut.closeEntry();

                        for( Map.Entry<String, URL> entryset : resources.entrySet() )
                        {
                            entry = new JarEntry( entryset.getKey() );
                            LOG.debug( "Copying resource " + entry.getName() );
                            jarOut.putNextEntry( entry );
                            InputStream inp = entryset.getValue().openStream();
                            StreamUtils.copyStream( inp, jarOut, false );
                            jarOut.closeEntry();
                        }
                    }
                    catch( IOException e )
                    {
                        LOG.error( "Problem!", e );
                    }
                    finally
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
                        LOG.info( "Copy thread finished." );
                    }
                }
            }.start();
        }
        catch( IOException e )
        {
            LOG.error( "Problem..", e );
        }

        return ( pin );
    }

    /**
     * The calculated manifest for this build output.
     * Relies on input given.
     *
     * @param headers headers will be merged into resulting manifest instance.
     *
     * @return a fresh manifest instance
     */
    private Manifest getManifest( Set<Map.Entry<String, String>> headers )
    {

        LOG.debug( "Creating manifest from added headers." );
        Manifest man = new Manifest();
        String cre = "pax-swissbox-tinybundles-" + Info.getPaxSwissboxTinybundlesVersion();

        man.getMainAttributes().putValue( "Manifest-Version", "1.0" );
        man.getMainAttributes().putValue( BUILT_BY, System.getProperty( "user.name" ) );
        man.getMainAttributes().putValue( CREATED_BY, cre );
        man.getMainAttributes().putValue( TOOL, cre );
        // SwissboxTinybundlesVersion
        man.getMainAttributes().putValue( "SwissboxTinybundlesVersion", cre );

        for( Map.Entry<String, String> entry : headers )
        {
            LOG.debug( entry.getKey() + " = " + entry.getValue() );

            man.getMainAttributes().putValue( entry.getKey(), entry.getValue() );
        }
        return man;
    }

    public InputStream make( InputStream inp )
    {
        return inp;
    }
}

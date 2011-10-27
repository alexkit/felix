/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.cm.impl;


import java.io.IOException;
import org.osgi.framework.Bundle;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationPermission;
import org.osgi.service.log.LogService;


/**
 * The <code>ConfigurationAdminImpl</code> is the per-bundle frontend to the
 * configuration manager. Instances of this class are created on-demand for
 * each bundle trying to get hold of the <code>ConfigurationAdmin</code>
 * service.
 */
public class ConfigurationAdminImpl implements ConfigurationAdmin
{

    // The configuration manager to which most of the tasks are delegated
    private ConfigurationManager configurationManager;

    // The bundle for which this instance has been created
    private Bundle bundle;


    ConfigurationAdminImpl( ConfigurationManager configurationManager, Bundle bundle )
    {
        this.configurationManager = configurationManager;
        this.bundle = bundle;
    }


    void dispose()
    {
        bundle = null;
        configurationManager = null;
    }


    Bundle getBundle()
    {
        return bundle;
    }


    //---------- ConfigurationAdmin interface ---------------------------------

    /* (non-Javadoc)
     * @see org.osgi.service.cm.ConfigurationAdmin#createFactoryConfiguration(java.lang.String)
     */
    public Configuration createFactoryConfiguration( String factoryPid ) throws IOException
    {
        return this.wrap( configurationManager.createFactoryConfiguration( this, factoryPid ) );
    }


    /* (non-Javadoc)
     * @see org.osgi.service.cm.ConfigurationAdmin#createFactoryConfiguration(java.lang.String, java.lang.String)
     */
    public Configuration createFactoryConfiguration( String factoryPid, String location ) throws IOException
    {
        // CM 1.4 / 104.13.2.3
        this.checkPermission( location );

        return this.wrap( configurationManager.createFactoryConfiguration( factoryPid, location ) );
    }


    /* (non-Javadoc)
     * @see org.osgi.service.cm.ConfigurationAdmin#getConfiguration(java.lang.String)
     */
    public Configuration getConfiguration( String pid ) throws IOException
    {
        ConfigurationImpl config = configurationManager.getConfiguration( pid, getBundle().getLocation() );

        if ( config.getBundleLocation() == null )
        {
            configurationManager.log( LogService.LOG_DEBUG, "Binding configuration {0} (isNew: {1}) to bundle {2}",
                new Object[]
                    { config.getPid(), Boolean.valueOf( config.isNew() ), getBundle().getLocation() } );

            config.setStaticBundleLocation( this.getBundle().getLocation() );
        }
        else if ( !config.getBundleLocation().equals( this.getBundle().getLocation() ) )
        {
            // CM 1.4 / 104.13.2.3
            this.checkPermission( config.getBundleLocation() );
        }

        return this.wrap( config );
    }


    /* (non-Javadoc)
     * @see org.osgi.service.cm.ConfigurationAdmin#getConfiguration(java.lang.String, java.lang.String)
     */
    public Configuration getConfiguration( String pid, String location ) throws IOException
    {
        // CM 1.4 / 104.13.2.3
        this.checkPermission( location );

        ConfigurationImpl config = configurationManager.getConfiguration( pid, location );
        if ( config.getBundleLocation() != null )
        {
            // CM 1.4 / 104.13.2.3
            this.checkPermission( config.getBundleLocation() );
        }

        return this.wrap( config );
    }


    /* (non-Javadoc)
     * @see org.osgi.service.cm.ConfigurationAdmin#listConfigurations(java.lang.String)
     */
    public Configuration[] listConfigurations( String filter ) throws IOException, InvalidSyntaxException
    {
        ConfigurationImpl ci[] = configurationManager.listConfigurations( this, filter );
        if ( ci == null || ci.length == 0 )
        {
            return null;
        }

        Configuration[] cfgs = new Configuration[ci.length];
        for ( int i = 0; i < cfgs.length; i++ )
        {
            cfgs[i] = this.wrap( ci[i] );
        }

        return cfgs;
    }


    //---------- Security checks ----------------------------------------------

    private Configuration wrap( ConfigurationImpl configuration )
    {
        return new ConfigurationAdapter( this, configuration );
    }


    /**
     * Returns <code>true</code> if the current access control context (call
     * stack) has the CONFIGURE permission.
     */
    boolean hasPermission(String name)
    {
        try
        {
            checkPermission(name);
            return true;
        }
        catch ( SecurityException se )
        {
            return false;
        }
    }


    /**
     * Checks whether the current access control context (call stack) has
     * the given permission for the given bundle location and throws a
     * <code>SecurityException</code> if this is not the case.
     *
     * @param name The bundle location to check for permission. If this
     *      is <code>null</code> or exactly matches the using bundle's
     *      location, permission is always granted.
     *
     * @throws SecurityException if the access control context does not
     *      have the appropriate permission
     */
    void checkPermission( String name )
    {
        // the caller's permission must be checked
        final SecurityManager sm = System.getSecurityManager();
        if ( sm != null )
        {
            // CM 1.4 / 104.11.1 Implicit permission
            if ( name != null && !name.equals( getBundle().getLocation() ) )
            {
                sm.checkPermission( new ConfigurationPermission( name, ConfigurationPermission.CONFIGURE ) );

                if ( configurationManager.isLogEnabled( LogService.LOG_DEBUG ) )
                {
                    configurationManager.log( LogService.LOG_DEBUG,
                        "Explicit Permission; grant CONFIGURE permission on configuration bound to {0} to bundle {1}",
                        new Object[]
                            { name, getBundle().getLocation() } );
                }
            }
            else if ( configurationManager.isLogEnabled( LogService.LOG_DEBUG ) )
            {
                configurationManager.log( LogService.LOG_DEBUG,
                    "Implicit Permission; grant CONFIGURE permission on configuration bound to {0} to bundle {1}",
                    new Object[]
                        { name, getBundle().getLocation() } );

            }
        }
        else if ( configurationManager.isLogEnabled( LogService.LOG_DEBUG ) )
        {
            configurationManager.log( LogService.LOG_DEBUG,
                "No SecurityManager installed; grant CONFIGURE permission on configuration bound to {0} to bundle {1}",
                new Object[]
                    { name, getBundle().getLocation() } );
        }
    }

}

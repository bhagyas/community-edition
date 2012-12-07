/*
 * Copyright (C) 2005-2011 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.filesys.config;

/**
 * The Class ClusterConfigBean.
 * 
 * @author mrogers
 * @since 4.0 
 */
public class ClusterConfigBean
{
    private boolean isClusterEnabled = false;
    private String configFile;
    private String clusterName;
    private String debugFlags;
    private int nearCacheTimeout;
    
    public void setClusterEnabled(boolean clusterEnabled)
    {
        this.isClusterEnabled = clusterEnabled;
    }
    
    public boolean getClusterEnabled()
    {
       return isClusterEnabled;
    }

    public void setClusterName(String clusterName)
    {
        this.clusterName = clusterName;
    }

    public String getClusterName()
    {
        return clusterName;
    }

    public void setConfigFile(String configFile)
    {
        this.configFile = configFile;
    }

    public String getConfigFile()
    {
        return configFile;
    }

    public void setDebugFlags(String debugFlags)
    {
        this.debugFlags = debugFlags;
    }

    public String getDebugFlags()
    {
        return debugFlags;
    }

    public void setNearCacheTimeout(int nearCacheTimeout)
    {
        this.nearCacheTimeout = nearCacheTimeout;
    }

    public int getNearCacheTimeout()
    {
        return nearCacheTimeout;
    }
}

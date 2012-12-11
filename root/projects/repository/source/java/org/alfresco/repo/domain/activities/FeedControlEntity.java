/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
package org.alfresco.repo.domain.activities;

import java.util.Date;

import org.alfresco.service.cmr.activities.FeedControl;

/**
 * Activity Feed Control DAO
 */
public class FeedControlEntity
{ 
    private Long id; // internal DB-generated id
    private String feedUserId;
    private String siteNetwork;
    private String appTool;
    
    private Date lastModified; // when inserted
    
    // TODO - review - deleted feed controls are not kept and available feed controls are currently retrieved during generation, hence
    // it is possible for a feed control to be applied even if lastModified is greater than postDate - could check the date !
    // it is also possible for a feed control to not be applied if it is deleted just after the post - would need to keep, at least until next generation
    
    public FeedControlEntity()
    {
    }
    
    public FeedControlEntity(String feedUserId)
    {
        this.feedUserId = feedUserId;
    }
    
    public FeedControlEntity(String feedUserId, FeedControl feedControl)
    {
        this.feedUserId = feedUserId;
        this.siteNetwork = feedControl.getSiteId();
        this.appTool = feedControl.getAppToolId();
        this.lastModified = new Date();
    }
    
    public FeedControl getFeedControl()
    {
        return new FeedControl(this.siteNetwork, this.appTool);
    }
    
    public Long getId()
    {
        return id;
    }
    
    public void setId(Long id)
    {
        this.id = id;
    }

    public String getSiteNetwork()
    {
        return siteNetwork;
    }

    public void setSiteNetwork(String siteNetwork)
    {
        this.siteNetwork = siteNetwork;
    }

    public String getAppTool()
    {
        return appTool;
    }

    public void setAppTool(String appTool)
    {
        this.appTool = appTool;
    }

    public String getFeedUserId()
    {
        return feedUserId;
    }

    public void setFeedUserId(String feedUserId)
    {
        this.feedUserId = feedUserId;
    }

    public Date getLastModified()
    {
        return lastModified;
    }

    public void setLastModified(Date lastModified)
    {
        this.lastModified = lastModified;
    }
}


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
package org.alfresco.repo.security.permissions;

public class SimpleAccessControlEntryContext implements AccessControlEntryContext
{
    /**
     * 
     */
    private static final long serialVersionUID = -5679179194140822827L;

    private String classContext;
    
    private String KVPContext;

    private String propertyContext;
    
    public String getClassContext()
    {
        return classContext;
    }

    public String getKVPContext()
    {
        return KVPContext;
    }

    public String getPropertyContext()
    {
        return propertyContext;
    }

    public void setClassContext(String classContext)
    {
        this.classContext = classContext;
    }

    public void setKVPContext(String context)
    {
        KVPContext = context;
    }

    public void setPropertyContext(String propertyContext)
    {
        this.propertyContext = propertyContext;
    }
    

}

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
package org.alfresco.util.schemacomp.validator;

import java.util.Set;

import org.alfresco.util.schemacomp.DiffContext;
import org.alfresco.util.schemacomp.model.DbObject;

/**
 * DbObject validators must implement this interface. DbValidator instances
 * are used by the ValidatingVisitor class.
 * 
 * @author Matt Ward
 */
public interface DbValidator
{
    void validate(DbObject reference, DbObject target, DiffContext ctx);
    
    void setProperty(String name, String value);
    
    String getProperty(String name);
    
    Set<String> getPropertyNames();
}

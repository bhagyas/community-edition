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
package org.alfresco.util.schemacomp.model;

import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.alfresco.util.schemacomp.DbProperty;
import org.alfresco.util.schemacomp.Result.Strength;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests for the PrimaryKey class.
 * 
 * @author Matt Ward
 */
public class PrimaryKeyTest extends DbObjectTestBase<PrimaryKey>
{
    private PrimaryKey thisPK;
    private PrimaryKey thatPK;

    @Before
    public void setUp()
    {
        thisPK = new PrimaryKey(
                    null,
                    "this_pk",
                    Arrays.asList("id", "name", "age"),
                    Arrays.asList(2, 1, 3));
        thatPK = new PrimaryKey(
                    null,
                    "that_pk",
                    Arrays.asList("a", "b"),
                    Arrays.asList(1, 2));        
    }
    
    @Override
    protected PrimaryKey getThisObject()
    {
        return thisPK;
    }

    @Override
    protected PrimaryKey getThatObject()
    {
        return thatPK;
    }

    @Override
    protected void doDiffTests()
    {
        inOrder.verify(comparisonUtils).compareSimpleCollections(
                    new DbProperty(thisPK, "columnNames"),
                    new DbProperty(thatPK, "columnNames"), 
                    ctx, 
                    Strength.ERROR);
        inOrder.verify(comparisonUtils).compareSimpleCollections(
                    new DbProperty(thisPK, "columnOrders"),
                    new DbProperty(thatPK, "columnOrders"), 
                    ctx, 
                    Strength.ERROR);
    }

    @Test
    public void acceptVisitor()
    {
       thisPK.accept(visitor);
       
       verify(visitor).visit(thisPK);
    }
}

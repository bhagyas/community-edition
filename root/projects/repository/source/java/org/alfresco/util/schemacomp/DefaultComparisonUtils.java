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

package org.alfresco.util.schemacomp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.alfresco.util.schemacomp.Difference.Where;
import org.alfresco.util.schemacomp.Result.Strength;
import org.alfresco.util.schemacomp.model.DbObject;
import org.alfresco.util.schemacomp.model.Schema;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

/**
 * A collection of utility methods for determining differences between two database schemas.
 * 
 * @author Matt Ward
 */
public class DefaultComparisonUtils implements ComparisonUtils
{    
    @Override
    public List<DbObject> findEquivalentObjects(DbObject rootObject, DbObject objToMatch)
    {
        EquivalentObjectSeeker objectSeeker = new EquivalentObjectSeeker(objToMatch);
        rootObject.accept(objectSeeker);
        
        return objectSeeker.getMatches();
    }



    @Override
    public void compareSimpleCollections(DbProperty leftProp,
                DbProperty rightProp, DiffContext ctx, Strength strength)
    {
        @SuppressWarnings("unchecked")
        Collection<? extends Object> leftCollection = (Collection<? extends Object>) leftProp.getPropertyValue();
        @SuppressWarnings("unchecked")
        Collection<? extends Object> rightCollection = (Collection<? extends Object>) rightProp.getPropertyValue();
        
        // TODO: Temporary code during refactoring
        ArrayList<? extends Object> leftList = new ArrayList<Object>(leftCollection);
        ArrayList<? extends Object> rightList = new ArrayList<Object>(rightCollection);
        
        Results differences = ctx.getComparisonResults();

        for (int leftIndex = 0; leftIndex < leftList.size(); leftIndex++)
        {
            Object leftObj = leftList.get(leftIndex);
            DbProperty leftIndexedProp = new DbProperty(leftProp.getDbObject(), leftProp.getPropertyName(), leftIndex);
            
            int rightIndex;
            if ((rightIndex = rightList.indexOf(leftObj)) != -1)
            {
                // The same valued object in the right hand collection as in the left.
                // Note: it isn't possible to determine a result of Where.IN_BOTH_BUT_DIFFERENCE
                // with a 'simple' value — as there is no way of knowing if the term represents the same value
                // (e.g. two strings {red_value, green_value}, are these meant to be the same or different?)
                DbProperty rightIndexedProp = new DbProperty(rightProp.getDbObject(), rightProp.getPropertyName(), rightIndex);
                differences.add(Where.IN_BOTH_NO_DIFFERENCE, leftIndexedProp, rightIndexedProp, strength);
            }
            else
            {
                // No equivalent object in the right hand collection.
                // Using rightIndexedProperty would result in index out of bounds error.
                differences.add(Where.ONLY_IN_REFERENCE, leftIndexedProp, rightProp, strength);
            }
        }

        // Identify objects in the right collection but not the left
        for (int rightIndex = 0; rightIndex < rightList.size(); rightIndex++)
        {
            Object rightObj = rightList.get(rightIndex);
            if (!leftCollection.contains(rightObj))
            {
                DbProperty rightIndexedProp = new DbProperty(rightProp.getDbObject(), rightProp.getPropertyName(), rightIndex);
                // No equivalent object in the left hand collection.
                differences.add(Where.ONLY_IN_TARGET, leftProp, rightIndexedProp, strength);
            }
        }
    }

    /**
     * Compare collections, reporting differences using the default {@link Difference.Strength}
     * 
     * @see #compareCollections(Collection, Collection, Results, Strength)
     */
    @Override
    public void compareCollections(Collection<? extends DbObject> leftCollection,
                Collection<? extends DbObject> rightCollection, DiffContext ctx)
    {
        compareCollections(leftCollection, rightCollection, ctx, null);
    }
    
    /**
     * Compare collections of {@link DbObject}s using their {@link DbObject#diff(DbObject, Results)} method.
     * Differences are reported using the specified {@link Difference.Strength}.
     * 
     * @param leftCollection
     * @param rightCollection
     * @param differences
     * @param strength
     */
    @Override
    public void compareCollections(Collection<? extends DbObject> leftCollection,
                Collection<? extends DbObject> rightCollection, DiffContext ctx, Strength strength)
    {
        Results differences = ctx.getComparisonResults();
        for (DbObject leftObj : leftCollection)
        {
            boolean foundMatch = false;
            
            for (DbObject rootObject : rightCollection)
            {                
                List<DbObject> matches = findEquivalentObjects(rootObject, leftObj);
                
                for (DbObject match : matches)
                {                        
                    // There is an equivalent object in the right hand collection as in the left.
                    leftObj.diff(match, ctx, strength);
                }
                
                if (matches.size() > 0)
                {
                    foundMatch = true;
                }
            }
            
            if (!foundMatch)
            {
                // No equivalent object in the target collection.
                differences.add(Where.ONLY_IN_REFERENCE, new DbProperty(leftObj, null), null, strength);
            }
        }

        // Identify objects in the right collection but not the left
        for (DbObject rightObj : rightCollection)
        {
            boolean foundMatch = false;
            
            for (DbObject rootObject : leftCollection)
            {
                List<DbObject> matches = findEquivalentObjects(rootObject, rightObj);
                if (matches.size() > 0)
                {
                    foundMatch = true;
                    break;
                }
            }
            
            if (!foundMatch)
            {
                // No equivalent object in the left hand collection.
                differences.add(Where.ONLY_IN_TARGET, null, new DbProperty(rightObj, null), strength);
            }
        }
    }

    /**
     * Compare two simple objects. Differences are reported using the default Result.Strength.
     * 
     * @see #compareSimple(Object, Object, Results, Strength)
     */
    @Override
    public void compareSimple(DbProperty left, DbProperty right, DiffContext ctx)
    {
        compareSimple(left, right, ctx, null);
    }
    
    /**
     * Compare two 'simple' (i.e. non-{@link DbObject} objects) using their {@link Object#equals(Object)} method
     * to decide if there is a difference. Differences are reported using the Result.Strength specified.
     * 
     * @param left
     * @param right
     * @param differences
     * @param strength
     */
    @Override
    public void compareSimple(DbProperty leftProperty, DbProperty rightProperty, DiffContext ctx, Strength strength)
    {
        
        Where where = null;
        
        Object left = leftProperty.getPropertyValue();
        checkNotDbObject(left);
        Object right = rightProperty.getPropertyValue();
        checkNotDbObject(right);
        
        if (left == right)
        {
            // Same object, or both nulls
            where = Where.IN_BOTH_NO_DIFFERENCE;
        }
        else if (left == null)
        {
            // right can't be null, or left == right would have been true
            where = Where.ONLY_IN_TARGET;
        }
        else if (right == null)
        {
            // left can't be null, or left == right would have been true
            where = Where.ONLY_IN_REFERENCE;
        }
        else
        {
            // neither are null
            if (left.equals(right))
            {
                where = Where.IN_BOTH_NO_DIFFERENCE;
            }
            else
            {
                where = Where.IN_BOTH_BUT_DIFFERENCE;
            }
        }
        
        ctx.getComparisonResults().add(where, leftProperty, rightProperty, strength);
    }


    /**
     * @param obj
     */
    private void checkNotDbObject(Object obj)
    {
        if (obj != null && DbObject.class.isAssignableFrom(obj.getClass()))
        {
            throw new IllegalArgumentException(
                        "Property value is a DbObject - this method shouldn't be used to compare this type: " + obj);
        }
    }
    
    
    public static class EquivalentObjectSeeker implements DbObjectVisitor
    {
        private final List<DbObject> matches = new ArrayList<DbObject>();
        private final DbObject objToMatch;
        
        public EquivalentObjectSeeker(DbObject objToMatch)
        {
            this.objToMatch = objToMatch;
        }
        
        @Override
        public void visit(DbObject dbObject)
        {
            if (objToMatch.sameAs(dbObject))
            {
                matches.add(dbObject);
            }
        }

        /**
         * @return the matches
         */
        public List<DbObject> getMatches()
        {
            return this.matches;
        }
    }
}

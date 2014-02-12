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
package org.alfresco.repo.domain.solr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.alfresco.repo.domain.node.Node;
import org.alfresco.repo.security.authentication.AuthenticationComponent;
import org.alfresco.repo.solr.Acl;
import org.alfresco.repo.solr.AclChangeSet;
import org.alfresco.repo.solr.NodeParameters;
import org.alfresco.repo.solr.Transaction;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.ApplicationContextHelper;
import org.junit.experimental.categories.Category;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Tests for the SOLR DAO
 *
 * @since 4.0
 */
@Category(OwnJVMTestsCategory.class)
public class SOLRDAOTest extends TestCase
{
    private ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) ApplicationContextHelper.getApplicationContext();

    private TransactionService transactionService;
    private AuthenticationComponent authenticationComponent;
    private SOLRDAO solrDAO;
    
    @Override
    public void setUp() throws Exception
    {
        solrDAO = (SOLRDAO)ctx.getBean("solrDAO");
        authenticationComponent = (AuthenticationComponent)ctx.getBean("authenticationComponent");
        transactionService = (TransactionService) ctx.getBean("TransactionService");
        
        authenticationComponent.setSystemUserAsCurrentUser();
    }
    
    private List<Node> getNodes(final NodeParameters nodeParameters)
    {
        return transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<List<Node>>()
        {
            @Override
            public List<Node> execute() throws Throwable
            {
                return solrDAO.getNodes(nodeParameters);
            }
        }, true);
    }
    
    private List<Acl> getAcls(final List<Long> aclChangeSetIds, final Long minAclId, final int maxResults)
    {
        return transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<List<Acl>>()
        {
            @Override
            public List<Acl> execute() throws Throwable
            {
                return solrDAO.getAcls(aclChangeSetIds, minAclId, maxResults);
            }
        }, true);
    }
    
    private List<Transaction> getTransactions(
            final Long minTxnId, final Long fromCommitTime,
            final Long maxTxnId, final Long toCommitTime,
            final int maxResults)
    {
        return transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<List<Transaction>>()
        {
            @Override
            public List<Transaction> execute() throws Throwable
            {
                return solrDAO.getTransactions(minTxnId, fromCommitTime, maxTxnId, toCommitTime, maxResults);
            }
        }, true);
    }
    
    private List<AclChangeSet> getAclChangeSets(
            final Long minAclChangeSetId, final Long fromCommitTime,
            final Long maxAclChangeSetId, final Long toCommitTime,
            final int maxResults)
    {
        return transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<List<AclChangeSet>>()
        {
            @Override
            public List<AclChangeSet> execute() throws Throwable
            {
                return solrDAO.getAclChangeSets(minAclChangeSetId, fromCommitTime, maxAclChangeSetId, toCommitTime, maxResults);
            }
        }, true);
    }
    
    public void testQueryChangeSets_NoLimit()
    {
        long startTime = System.currentTimeMillis() - (5 * 60000L);

        try
        {
            getAclChangeSets(null, startTime, null, null, 0);
            fail("Must have result limit");
        }
        catch (IllegalArgumentException e)
        {
            // Expected
        }
    }
    
    public void testQueryChangeSets_Time()
    {
        long startTime = System.currentTimeMillis() + (5 * 60000L);             // The future
        List<AclChangeSet> results = getAclChangeSets(null, startTime, null, null, 50);
        assertTrue("ChangeSet count not limited", results.size() == 0);
    }
    
    public void testQueryChangeSets_Limit()
    {
        List<AclChangeSet> results = getAclChangeSets(null, 0L, null, null, 50);
        assertTrue("Transaction count not limited", results.size() <= 50);
    }
    
    /**
     * Argument checks.
     */
    public void testQueryAcls_Arguments()
    {
        try
        {
            // No IDs
            getAcls(Collections.<Long>emptyList(), null, 50);
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e)
        {
            // Expected
        }
    }
    
    public void testQueryAcls_All()
    {
        // Do a query for some changesets
        List<AclChangeSet> aclChangeSets = getAclChangeSets(null, 0L, null, null, 50);
        
        // Choose some changesets with changes
        int aclTotal = 0;
        Iterator<AclChangeSet> aclChangeSetsIterator = aclChangeSets.iterator();
        while (aclChangeSetsIterator.hasNext())
        {
            AclChangeSet aclChangeSet = aclChangeSetsIterator.next();
            if (aclChangeSet.getAclCount() == 0)
            {
                aclChangeSetsIterator.remove();
            }
            else
            {
                aclTotal += aclChangeSet.getAclCount();
            }
        }
        // Stop if we don't have ACLs
        if (aclTotal == 0)
        {
            return;
        }
        
        List<Long> aclChangeSetIds = toIds(aclChangeSets);

        // Now use those to query for details
        List<Acl> acls = getAcls(aclChangeSetIds, null, 1000);

        // Check that the ACL ChangeSet IDs are correct
        Set<Long> aclChangeSetIdsSet = new HashSet<Long>(aclChangeSetIds);
        for (Acl acl : acls)
        {
            Long aclChangeSetId = acl.getAclChangeSetId();
            assertTrue("ACL ChangeSet ID not in original list", aclChangeSetIdsSet.contains(aclChangeSetId));
        }
    }
    
    public void testQueryAcls_Single()
    {
        List<AclChangeSet> aclChangeSets = getAclChangeSets(null, 0L, null, null, 1000);
        // Find one with multiple ALCs
        AclChangeSet aclChangeSet = null;
        for (AclChangeSet aclChangeSetLoop : aclChangeSets)
        {
            if (aclChangeSetLoop.getAclCount() > 1)
            {
                aclChangeSet = aclChangeSetLoop;
                break;
            }
        }
        if (aclChangeSet == null)
        {
            // Nothing to test: Very unlikely
            return;
        }
        
        // Loop a few times and check that the count is correct
        Long aclChangeSetId = aclChangeSet.getId();
        List<Long> aclChangeSetIds = Collections.singletonList(aclChangeSetId);
        int aclCount = aclChangeSet.getAclCount();
        int totalAclCount = 0;
        Long minAclId = null;
        while (true)
        {
            List<Acl> acls = getAcls(aclChangeSetIds, minAclId, 1);
            if (acls.size() == 0)
            {
                break;
            }
            assertEquals("Expected exactly one result", 1, acls.size());
            totalAclCount++;
            minAclId = acls.get(0).getId() + 1;
        }
        assertEquals("Expected to page to exact number of results", aclCount, totalAclCount);
    }
    
    private List<Long> toIds(List<AclChangeSet> aclChangeSets)
    {
        List<Long> ids = new ArrayList<Long>(aclChangeSets.size());
        for (AclChangeSet aclChangeSet : aclChangeSets)
        {
            ids.add(aclChangeSet.getId());
        }
        return ids;
    }
    
    public void testQueryTransactions_NoLimit()
    {
        long startTime = System.currentTimeMillis() - (5 * 60000L);

        try
        {
            getTransactions(null, startTime, null, null, 0);
            fail("Must have result limit");
        }
        catch (IllegalArgumentException e)
        {
            // Expected
        }
    }
    
    public void testQueryTransactions_Time()
    {
        long startTime = System.currentTimeMillis() + (5 * 60000L);             // The future
        List<Transaction> results = getTransactions(null, startTime, null, null, 50);
        assertTrue("Transaction count not limited", results.size() == 0);
    }
    
    public void testQueryTransactions_Limit()
    {
        List<Transaction> results = getTransactions(null, 0L, null, null, 50);
        assertTrue("Transaction count not limited", results.size() <= 50);
    }
    
    public void testGetNodesSimple()
    {
        long startTime = 0L;

        List<Transaction> txns = getTransactions(null, startTime, null, null, 500);

        List<Long> txnIds = toTxnIds(txns);

        NodeParameters nodeParameters = new NodeParameters();
        nodeParameters.setTransactionIds(txnIds);
        nodeParameters.setStoreProtocol(StoreRef.PROTOCOL_WORKSPACE);
        nodeParameters.setStoreIdentifier(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.getIdentifier());
        
        List<Node> nodes = getNodes(nodeParameters);
        assertTrue("Expect 'some' nodes associated with txns", nodes.size() > 0);
    }
    
    public void testGetNodesForStore()
    {
        List<Transaction> txns = getTransactions(null, null, null, null, 500);

        List<Long> txnIds = toTxnIds(txns);

        NodeParameters nodeParameters = new NodeParameters();
        nodeParameters.setTransactionIds(txnIds);
        
        List<Node> nodes = getNodes(nodeParameters);
        assertTrue("Expect 'some' nodes associated with txns", nodes.size() > 0);
    }
    
    public void testGetNodesForTxnRange()
    {
        List<Transaction> txns = getTransactions(null, null, null, null, 500);

        List<Long> txnIds = toTxnIds(txns);
        
        // Only works if there are transactions
        if (txnIds.size() < 2)
        {
            // Nothing to test
            return;
        }
        
        NodeParameters nodeParameters = new NodeParameters();
        nodeParameters.setFromTxnId(txnIds.get(0));
        nodeParameters.setToTxnId(txnIds.get(1));
        
        List<Node> nodes = getNodes(nodeParameters);
        assertTrue("Expect 'some' nodes associated with txns", nodes.size() > 0);
    }
    
    private List<Long> toTxnIds(List<Transaction> txns)
    {
        List<Long> txnIds = new ArrayList<Long>(txns.size());
        for(Transaction txn : txns)
        {
            txnIds.add(txn.getId());
        }
        
        return txnIds;
    }
}

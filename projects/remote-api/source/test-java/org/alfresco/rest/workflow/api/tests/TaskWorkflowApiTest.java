/*
 * Copyright (C) 2005-2012 Alfresco Software Limited.
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
package org.alfresco.rest.workflow.api.tests;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.activiti.engine.impl.util.ClockUtil;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.DelegationState;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.Task;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.repo.tenant.TenantUtil.TenantRunAsWork;
import org.alfresco.repo.workflow.WorkflowConstants;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.rest.api.tests.RepoService.TestNetwork;
import org.alfresco.rest.api.tests.RepoService.TestPerson;
import org.alfresco.rest.api.tests.client.PublicApiException;
import org.alfresco.rest.api.tests.client.RequestContext;
import org.alfresco.rest.api.tests.client.data.MemberOfSite;
import org.alfresco.rest.workflow.api.model.ProcessInfo;
import org.alfresco.rest.workflow.api.tests.WorkflowApiClient.ProcessesClient;
import org.alfresco.rest.workflow.api.tests.WorkflowApiClient.TasksClient;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.ISO8601DateFormat;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.springframework.http.HttpStatus;

/**
 * Task related Rest api tests using http client to communicate with the rest apis in the repository.
 * 
 * @author Tijs Rademakers
 * @author Frederik Heremans
 *
 */
public class TaskWorkflowApiTest extends EnterpriseWorkflowTestApi
{   
    @Test
    public void testGetTaskById() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        // Alter current engine date
        Calendar createdCal = Calendar.getInstance();
        createdCal.set(Calendar.MILLISECOND, 0);
        ClockUtil.setCurrentTime(createdCal.getTime());
        
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            // Set some task-properties not set by process-definition
            Calendar dueDateCal = Calendar.getInstance();
            dueDateCal.set(Calendar.MILLISECOND, 0);
            dueDateCal.add(Calendar.DAY_OF_YEAR, 1);
            Date dueDate = dueDateCal.getTime();
            
            task.setDescription("This is a test description");
            task.setAssignee(requestContext.getRunAsUser());
            task.setOwner("john");
            task.setDueDate(dueDate);
            activitiProcessEngine.getTaskService().saveTask(task);

            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Check resulting task
            JSONObject taskJSONObject = tasksClient.findTaskById(task.getId());
            assertNotNull(taskJSONObject);
            assertEquals(task.getId(), taskJSONObject.get("id"));
            assertEquals(processInstance.getId(), taskJSONObject.get("processId"));
            assertEquals(processInstance.getProcessDefinitionId(), taskJSONObject.get("processDefinitionId"));
            assertEquals("adhocTask", taskJSONObject.get("activityDefinitionId"));
            assertEquals("Adhoc Task", taskJSONObject.get("name"));
            assertEquals("This is a test description", taskJSONObject.get("description"));
            assertEquals(requestContext.getRunAsUser(), taskJSONObject.get("assignee"));
            assertEquals("john", taskJSONObject.get("owner"));
            assertEquals(dueDate, parseDate(taskJSONObject, "dueAt"));
            assertEquals(createdCal.getTime(), parseDate(taskJSONObject, "startedAt"));
            assertEquals(2l, taskJSONObject.get("priority"));
            assertEquals("wf:adhocTask", taskJSONObject.get("formResourceKey"));
            assertNull(taskJSONObject.get("endedAt"));
            assertNull(taskJSONObject.get("durationInMs"));
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskByIdAuthorization() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        // Start process by one user and try to access the task as the task assignee instead of the process
        // initiator to see if the assignee is authorized to get the task
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Try accessing task when NOT involved in the task
            try {
                tasksClient.findTaskById(task.getId());
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Set assignee, task should be accessible now
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), requestContext.getRunAsUser());
            JSONObject jsonObject = tasksClient.findTaskById(task.getId());
            assertNotNull(jsonObject);
            
            // Fetching task as admin should be possible
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            jsonObject = tasksClient.findTaskById(task.getId());
            assertNotNull(jsonObject);
            
            // Fetching the task as a admin from another tenant shouldn't be possible
            TestNetwork anotherNetwork = getOtherNetwork(requestContext.getNetworkId());
            tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + anotherNetwork.getId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            try {
                tasksClient.findTaskById(task.getId());
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    
    @Test
    public void testGetUnexistingTaskById() throws Exception
    {
        initApiClientWithTestUser();
        TasksClient tasksClient = publicApiClient.tasksClient();
        try 
        {
            tasksClient.findTaskById("unexisting");
            fail("Exception expected");
        }
        catch(PublicApiException expected)
        {
            assertEquals(HttpStatus.NOT_FOUND.value(), expected.getHttpResponse().getStatusCode());
            assertErrorSummary("The entity with id: unexisting was not found", expected.getHttpResponse());
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTask() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        
        try 
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();

            TasksClient tasksClient = publicApiClient.tasksClient();

            Calendar dueDateCal = Calendar.getInstance();
            dueDateCal.set(Calendar.MILLISECOND, 0);
            dueDateCal.add(Calendar.DAY_OF_YEAR, 1);
            Date dueDate = dueDateCal.getTime();
            
            JSONObject taskBody = new JSONObject();
            taskBody.put("name", "Updated name");
            taskBody.put("description", "Updated description");
            taskBody.put("dueAt", formatDate(dueDate));
            taskBody.put("priority", 1234);
            taskBody.put("assignee", "john");
            taskBody.put("owner", "james");
            
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "name", "description", "dueAt", "priority", "assignee", "owner"}));
            
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            assertEquals("Updated name", task.getName());
            assertEquals("Updated description", task.getDescription());
            assertEquals(1234, task.getPriority());
            assertEquals("john", task.getAssignee());
            assertEquals("james", task.getOwner());
            assertEquals(dueDate, task.getDueDate());
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTaskAuthorization() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try 
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Updating the task when NOT assignee/owner or initiator results in an error
            JSONObject taskBody = new JSONObject();
            taskBody.put("name", "Updated name");
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "name" }));
            try 
            {
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Set assignee to current user, update should succeed
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), requestContext.getRunAsUser());
            taskBody.put("name", "Updated name by assignee");
            
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            assertEquals("Updated name by assignee", task.getName());
            
            // Set owner to current user, update should succeed
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), null);
            activitiProcessEngine.getTaskService().setOwner(task.getId(), requestContext.getRunAsUser());
            taskBody.put("name", "Updated name by owner");
            
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            assertEquals("Updated name by owner", task.getName());
            
            // Update as process initiator
            taskBody.put("name", "Updated name by initiator");
            requestContext.setRunAsUser(initiator);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            assertEquals("Updated name by initiator", task.getName());
            
            // Update as administrator
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            
            taskBody.put("name", "Updated name by admin");
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            assertEquals("Updated name by admin", task.getName());
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateUnexistingTask() throws Exception
    {
        initApiClientWithTestUser();
        TasksClient tasksClient = publicApiClient.tasksClient();
        try 
        {
            JSONObject taskBody = new JSONObject();
            taskBody.put("name", "Updated name");
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "name" }));
            
            tasksClient.updateTask("unexisting", taskBody, selectedFields);
            fail("Exception expected");
        }
        catch(PublicApiException expected)
        {
            assertEquals(HttpStatus.NOT_FOUND.value(), expected.getHttpResponse().getStatusCode());
            assertErrorSummary("The entity with id: unexisting was not found", expected.getHttpResponse());
        }
    }
    
    @Test
    public void testUpdateTaskInvalidProperty() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Try updating an unexisting property
            try 
            {
                JSONObject taskBody = new JSONObject();
                List<String> selectedFields = new ArrayList<String>();
                selectedFields.add("unexisting");
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("The property selected for update does not exist for this resource: unexisting", expected.getHttpResponse());
            }
            
            // Try updating a readonly-property
            try 
            {
                JSONObject taskBody = new JSONObject();
                List<String> selectedFields = new ArrayList<String>();
                selectedFields.add("id");
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("The property selected for update is read-only: id", expected.getHttpResponse());
            }
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testClaimTask() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try 
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Claiming the task when NOT part of candidate-group results in an error
            JSONObject taskBody = new JSONObject();
            taskBody.put("state", "claimed");
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state", "assignee" }));
            try 
            {
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Set candidate for task, but keep assignee
            List<MemberOfSite> memberships = getTestFixture().getNetwork(requestContext.getNetworkId()).getSiteMemberships(requestContext.getRunAsUser());
            assertTrue(memberships.size() > 0);
            MemberOfSite memberOfSite = memberships.get(0);
            String group = "GROUP_site_" + memberOfSite.getSiteId() + "_" + memberOfSite.getRole().name();
            activitiProcessEngine.getTaskService().addCandidateGroup(task.getId(), group);
            
            // Claiming the task when part of candidate-group but another person has this task assigned results in conflict
            try 
            {
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.CONFLICT.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("The task is already claimed by another user.", expected.getHttpResponse());
            }
            
            // Claiming the task when part of candidate-group and NO assignee is currenlty set should work
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), null);
            taskBody = new JSONObject();
            taskBody.put("state", "claimed");
            JSONObject result = tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNotNull(result);
            assertEquals(requestContext.getRunAsUser(), activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
            // Re-claiming the same task with the current assignee shouldn't be a problem
            result = tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNotNull(result);
            assertEquals(requestContext.getRunAsUser(), activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
            // Claiming as a candidateUser should also work
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), null);
            activitiProcessEngine.getTaskService().deleteGroupIdentityLink(task.getId(), group, IdentityLinkType.CANDIDATE);
            activitiProcessEngine.getTaskService().addCandidateUser(task.getId(), requestContext.getRunAsUser());
            result = tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNotNull(result);
            assertEquals(requestContext.getRunAsUser(), activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
            // Claiming as a task owner should also work
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), null);
            activitiProcessEngine.getTaskService().setOwner(task.getId(), requestContext.getRunAsUser());
            activitiProcessEngine.getTaskService().deleteUserIdentityLink(task.getId(), requestContext.getRunAsUser(), IdentityLinkType.CANDIDATE);
            result = tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNotNull(result);
            assertEquals(requestContext.getRunAsUser(), activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
            // Claiming as admin should work
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), null);
            activitiProcessEngine.getTaskService().deleteUserIdentityLink(task.getId(), requestContext.getRunAsUser(), IdentityLinkType.CANDIDATE);
            result = tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNotNull(result);
            assertEquals(tenantAdmin, activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUnClaimTask() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        String user = requestContext.getRunAsUser();
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try 
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Unclaiming the task when NOT assignee, owner, initiator or admin results in error
            JSONObject taskBody = new JSONObject();
            taskBody.put("state", "unclaimed");
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state" }));
            try 
            {
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Unclaiming as process initiator
            requestContext.setRunAsUser(initiator);
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), null);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
            // Unclaiming as assignee
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), user);
            requestContext.setRunAsUser(user);
            assertNotNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
            // Unclaim as owner
            activitiProcessEngine.getTaskService().setOwner(task.getId(), user);
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), initiator);
            assertNotNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            
            // Unclaim as admin
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), initiator);
            activitiProcessEngine.getTaskService().deleteUserIdentityLink(task.getId(), requestContext.getRunAsUser(), IdentityLinkType.CANDIDATE);
            assertNotNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testCompleteTask() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        String user = requestContext.getRunAsUser();
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        ProcessInstance processCompleteAsAssignee = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        ProcessInstance processCompleteAsOwner = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        ProcessInstance processCompleteAsInitiator = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        ProcessInstance processCompleteAsAdmin = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try 
        {
            Task asAssigneeTask = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processCompleteAsAssignee.getId()).singleResult();
            Task asOwnerTask = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processCompleteAsOwner.getId()).singleResult();
            Task asInitiatorTask = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processCompleteAsInitiator.getId()).singleResult();
            Task asAdminTask = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processCompleteAsAdmin.getId()).singleResult();
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Unclaiming the task when NOT assignee, owner, initiator or admin results in error
            JSONObject taskBody = new JSONObject();
            taskBody.put("state", "completed");
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state" }));
            try 
            {
                tasksClient.updateTask(asAssigneeTask.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Completing as assignee initiator
            activitiProcessEngine.getTaskService().setAssignee(asAssigneeTask.getId(), user);
            tasksClient.updateTask(asAssigneeTask.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(asAssigneeTask.getId()).singleResult());
            
            // Completing as process initiator
            requestContext.setRunAsUser(initiator);
            activitiProcessEngine.getTaskService().setAssignee(asInitiatorTask.getId(), null);
            tasksClient.updateTask(asInitiatorTask.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(asInitiatorTask.getId()).singleResult());
            
            // Completing as owner
            requestContext.setRunAsUser(user);
            asOwnerTask.setOwner(user);
            activitiProcessEngine.getTaskService().saveTask(asOwnerTask);
            tasksClient.updateTask(asOwnerTask.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(asOwnerTask.getId()).singleResult());
            
            // Complete as admin
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            asAdminTask.setOwner(null);
            activitiProcessEngine.getTaskService().saveTask(asAdminTask);
            tasksClient.updateTask(asAdminTask.getId(), taskBody, selectedFields);
            assertNull(activitiProcessEngine.getTaskService().createTaskQuery().taskId(asAdminTask.getId()).singleResult());
        }
        finally
        {
            cleanupProcessInstance(processCompleteAsAssignee, processCompleteAsAdmin, processCompleteAsInitiator, processCompleteAsOwner);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testDelegateTask() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        String user = requestContext.getRunAsUser();
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try 
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Delegating as non-assignee/owner/initiator/admin should result in an error
            JSONObject taskBody = new JSONObject();
            taskBody.put("state", "delegated");
            taskBody.put("assignee", initiator);
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state",  "assignee"}));
            try 
            {
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Delegating (as assignee) and not passing in an asisgnee should result in an error
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), user);
            taskBody = new JSONObject();
            taskBody.put("state", "delegated");
            selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state" }));
            try 
            {
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("When delegating a task, assignee should be selected and provided in the request.", expected.getHttpResponse());
            }
            
            // Delegating as assignee
            taskBody.put("state", "delegated");
            taskBody.put("assignee", initiator);
            selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state", "assignee" }));
            assertNull(task.getDelegationState());
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.PENDING, task.getDelegationState());
            assertEquals(initiator, task.getAssignee());
            assertEquals(requestContext.getRunAsUser(), task.getOwner());
            
            // Delegating as owner
            task.setDelegationState(null);
            task.setOwner(requestContext.getRunAsUser());
            task.setAssignee(null);
            activitiProcessEngine.getTaskService().saveTask(task);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.PENDING, task.getDelegationState());
            assertEquals(initiator, task.getAssignee());
            assertEquals(requestContext.getRunAsUser(), task.getOwner());
            
            // Delegating as process initiator
            task.setDelegationState(null);
            task.setOwner(null);
            task.setAssignee(null);
            activitiProcessEngine.getTaskService().saveTask(task);
            requestContext.setRunAsUser(initiator);
            taskBody.put("assignee", user);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.PENDING, task.getDelegationState());
            assertEquals(user, task.getAssignee());
            assertEquals(initiator, task.getOwner());
            
            // Delegating as administrator
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            task.setDelegationState(null);
            task.setOwner(null);
            task.setAssignee(null);
            activitiProcessEngine.getTaskService().saveTask(task);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            requestContext.setRunAsUser(tenantAdmin);
            taskBody.put("assignee", user);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.PENDING, task.getDelegationState());
            assertEquals(user, task.getAssignee());
            assertEquals(tenantAdmin, task.getOwner());
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTask() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        String user = requestContext.getRunAsUser();
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try 
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Resolving as non-assignee/owner/initiator/admin should result in an error
            JSONObject taskBody = new JSONObject();
            taskBody.put("state", "resolved");
            taskBody.put("assignee", initiator);
            List<String> selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state",  "assignee"}));
            try 
            {
                tasksClient.updateTask(task.getId(), taskBody, selectedFields);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Resolving as assignee
            task.delegate(user);
            activitiProcessEngine.getTaskService().saveTask(task);
            taskBody.put("state", "resolved");
            taskBody.put("assignee", initiator);
            selectedFields = new ArrayList<String>();
            selectedFields.addAll(Arrays.asList(new String[] { "state", "assignee" }));
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.RESOLVED, task.getDelegationState());
            assertEquals(initiator, task.getAssignee());
            
            // Resolving as owner
            task.setDelegationState(null);
            task.setOwner(requestContext.getRunAsUser());
            task.setAssignee(null);
            activitiProcessEngine.getTaskService().saveTask(task);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.RESOLVED, task.getDelegationState());
            assertEquals(user, task.getAssignee());
            
            // Resolving as process initiator
            task.setDelegationState(null);
            task.setOwner(null);
            task.setAssignee(null);
            activitiProcessEngine.getTaskService().saveTask(task);
            requestContext.setRunAsUser(initiator);
            taskBody.put("assignee", user);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.RESOLVED, task.getDelegationState());
            
            // Resolving as administrator
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            task.setDelegationState(null);
            task.setOwner(initiator);
            task.setAssignee(null);
            activitiProcessEngine.getTaskService().saveTask(task);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            requestContext.setRunAsUser(tenantAdmin);
            taskBody.put("assignee", user);
            tasksClient.updateTask(task.getId(), taskBody, selectedFields);
            task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertEquals(DelegationState.RESOLVED, task.getDelegationState());
            assertEquals(initiator, task.getAssignee());
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTasks() throws Exception
    {
        // Alter current engine date
        Calendar createdCal = Calendar.getInstance();
        createdCal.set(Calendar.MILLISECOND, 0);
        ClockUtil.setCurrentTime(createdCal.getTime());
        
        RequestContext requestContext = initApiClientWithTestUser();
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            // Set some task-properties not set by process-definition
            Calendar dueDateCal = Calendar.getInstance();
            dueDateCal.set(Calendar.MILLISECOND, 0);
            dueDateCal.add(Calendar.DAY_OF_YEAR, 1);
            Date dueDate = dueDateCal.getTime();
            
            task.setDescription("This is a test description");
            task.setAssignee(requestContext.getRunAsUser());
            task.setOwner("john");
            task.setDueDate(dueDate);
            activitiProcessEngine.getTaskService().saveTask(task);

            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Check resulting task
            JSONObject taskListJSONObject = tasksClient.findTasks(null);
            assertNotNull(taskListJSONObject);
            JSONArray jsonEntries = (JSONArray) taskListJSONObject.get("entries");
            assertEquals(1, jsonEntries.size());
            
            JSONObject taskJSONObject = (JSONObject) ((JSONObject) jsonEntries.get(0)).get("entry");
            assertNotNull(taskJSONObject);
            assertEquals(task.getId(), taskJSONObject.get("id"));
            assertEquals(processInstance.getId(), taskJSONObject.get("processId"));
            assertEquals(processInstance.getProcessDefinitionId(), taskJSONObject.get("processDefinitionId"));
            assertEquals("adhocTask", taskJSONObject.get("activityDefinitionId"));
            assertEquals("Adhoc Task", taskJSONObject.get("name"));
            assertEquals("This is a test description", taskJSONObject.get("description"));
            assertEquals(requestContext.getRunAsUser(), taskJSONObject.get("assignee"));
            assertEquals("john", taskJSONObject.get("owner"));
            assertEquals(dueDate, parseDate(taskJSONObject, "dueAt"));
            assertEquals(createdCal.getTime(), parseDate(taskJSONObject, "startedAt"));
            assertEquals(2l, taskJSONObject.get("priority"));
            assertEquals("wf:adhocTask", taskJSONObject.get("formResourceKey"));
            assertNull(taskJSONObject.get("endedAt"));
            assertNull(taskJSONObject.get("durationInMs"));
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTasksWithParams() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        
        Calendar taskCreated = Calendar.getInstance();
        taskCreated.set(Calendar.MILLISECOND, 0);
        ClockUtil.setCurrentTime(taskCreated.getTime());
        String businessKey = UUID.randomUUID().toString();
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), businessKey);
        
        // Complete the adhoc task
        final Task completedTask = activitiProcessEngine.getTaskService().createTaskQuery()
            .processInstanceId(processInstance.getId()).singleResult();
        
        assertNotNull(completedTask);
        
        String anotherUserId = UUID.randomUUID().toString();
        
        Calendar completedTaskDue = Calendar.getInstance();
        completedTaskDue.add(Calendar.HOUR, 1);
        completedTaskDue.set(Calendar.MILLISECOND, 0);
        completedTask.setOwner(requestContext.getRunAsUser());
        completedTask.setPriority(3);
        completedTask.setDueDate(completedTaskDue.getTime());
        completedTask.setAssignee(anotherUserId);
        completedTask.setName("Another task name");
        completedTask.setDescription("This is another test description");
        activitiProcessEngine.getTaskService().saveTask(completedTask);
        
        // Complete task in correct tenant
        TenantUtil.runAsUserTenant(new TenantRunAsWork<Void>()
        {
            @Override
            public Void doWork() throws Exception
            {
                activitiProcessEngine.getTaskService().complete(completedTask.getId());
                return null;
            }
        }, requestContext.getRunAsUser(), requestContext.getNetworkId());
        
        // Active task is the second task in the adhoc-process (Verify task completed)
        Task activeTask = activitiProcessEngine.getTaskService().createTaskQuery()
            .processInstanceId(processInstance.getId()).singleResult();
        assertNotNull(activeTask);
    
        Calendar activeTaskDue = Calendar.getInstance();
        
        activeTaskDue.set(Calendar.MILLISECOND, 0);
        activeTask.setDueDate(activeTaskDue.getTime());
        activeTask.setName("Task name");
        activeTask.setDescription("This is a test description");
        activeTask.setOwner(requestContext.getRunAsUser());
        activeTask.setPriority(2);
        activeTask.setAssignee(requestContext.getRunAsUser());
        activitiProcessEngine.getTaskService().saveTask(activeTask);
        activitiProcessEngine.getTaskService().addCandidateUser(activeTask.getId(), anotherUserId);
        activitiProcessEngine.getTaskService().addCandidateGroup(activeTask.getId(), "sales");
       
        TasksClient tasksClient = publicApiClient.tasksClient();
        
        // Test status filtering - active
        Map<String, String> params = new HashMap<String, String>();
        params.put("where", "(status = 'active' AND processInstanceId = '" + processInstance.getId() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, false, activeTask.getId());
        
        // Test status filtering - completed
        params.clear();
        params.put("where", "(status = 'completed' AND processInstanceId = '" + processInstance.getId() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, false, completedTask.getId());
        
        // Test status filtering - any
        params.clear();
        params.put("where", "(status = 'any' AND processInstanceId = '" + processInstance.getId() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, false, activeTask.getId(), completedTask.getId());
        
        // Test status filtering - no value should default to 'active'
        params.clear();
        params.put("where", "(processInstanceId = '" + processInstance.getId() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, false, activeTask.getId());
        
        // Test status filtering - illegal status
        params.clear();
        params.put("where", "(status = 'alfrescorocks')");
        try
        {
            tasksClient.findTasks(params);
            fail("Exception expected");
        }
        catch(PublicApiException expected)
        {
            assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
            assertErrorSummary("Invalid status parameter: alfrescorocks", expected.getHttpResponse());
        }
        
        // Next, we test all filtering for active, complete and any tasks
        
        // Assignee filtering
        params.clear();
        params.put("where", "(status = 'active' AND assignee = '" + requestContext.getRunAsUser() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.put("where", "(status = 'completed' AND assignee = '" + anotherUserId + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.put("where", "(status = 'any' AND assignee = '" + anotherUserId + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        // Owner filtering
        params.clear();
        params.put("where", "(status = 'active' AND owner = '" + requestContext.getRunAsUser() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.put("where", "(status = 'completed' AND owner = '" + requestContext.getRunAsUser() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.put("where", "(status = 'any' AND owner = '" + requestContext.getRunAsUser() + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId(), completedTask.getId());
        
        // Candidate user filtering, only available for active tasks. When used with completed/any 400 is returned
        params.clear();
        params.put("where", "(status = 'active' AND candidateUser = '" + anotherUserId + "')");
        // No tasks expected since assignee is set
        assertEquals(0L, getResultSizeForTaskQuery(params, tasksClient));
        
        // Clear assignee
        activitiProcessEngine.getTaskService().setAssignee(activeTask.getId(), null);
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND candidateUser = '" + anotherUserId + "')");
        try
        {
            tasksClient.findTasks(params);
            fail("Exception expected");
        }
        catch(PublicApiException expected)
        {
            assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
            assertErrorSummary("Filtering on candidateUser is only allowed in combination with status-parameter 'active'", expected.getHttpResponse());
        }
        
        params.clear();
        params.put("where", "(status = 'any' AND candidateUser = '" + anotherUserId + "')");
        try
        {
            tasksClient.findTasks(params);
            fail("Exception expected");
        }
        catch(PublicApiException expected)
        {
            assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
            assertErrorSummary("Filtering on candidateUser is only allowed in combination with status-parameter 'active'", expected.getHttpResponse());
        }
        
        // Candidate group filtering, only available for active tasks. When used with completed/any 400 is returned
        params.clear();
        params.put("where", "(status = 'active' AND candidateGroup = 'sales' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND candidateGroup = 'sales' AND processInstanceId='" + processInstance.getId() +"')");
        try
        {
            tasksClient.findTasks(params);
            fail("Exception expected");
        }
        catch(PublicApiException expected)
        {
            assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
            assertErrorSummary("Filtering on candidateGroup is only allowed in combination with status-parameter 'active'", expected.getHttpResponse());
        }
        
        params.clear();
        params.put("where", "(status = 'any' AND candidateGroup = 'sales' AND processInstanceId='" + processInstance.getId() +"')");
        try
        {
            tasksClient.findTasks(params);
            fail("Exception expected");
        }
        catch(PublicApiException expected)
        {
            assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
            assertErrorSummary("Filtering on candidateGroup is only allowed in combination with status-parameter 'active'", expected.getHttpResponse());
        }
        
        // Name filtering
        params.clear();
        params.put("where", "(status = 'active' AND name = 'Task name' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND name = 'Another task name' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.clear();
        params.put("where", "(status = 'any' AND name = 'Another task name' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        // Description filtering
        params.clear();
        params.put("where", "(status = 'active' AND description = 'This is a test description' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND description = 'This is another test description' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.clear();
        params.put("where", "(status = 'any' AND description = 'This is another test description' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        // Priority filtering
        params.clear();
        params.put("where", "(status = 'active' AND priority = 2 AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.put("where", "(status = 'completed' AND priority = 3 AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.put("where", "(status = 'any' AND priority = 3 AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        // Process instance business-key filtering
        params.clear();
        params.put("where", "(status = 'active' AND processInstanceBusinessKey = '" + businessKey + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND processInstanceBusinessKey = '" + businessKey + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
         
        params.clear();
        params.put("where", "(status = 'any' AND processInstanceBusinessKey = '" + businessKey + "')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId(), activeTask.getId());
        
        // Activity definition id filtering
        params.clear();
        params.put("where", "(status = 'active' AND activityDefinitionId = 'verifyTaskDone' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND activityDefinitionId = 'adhocTask' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.clear();
        params.put("where", "(status = 'any' AND activityDefinitionId = 'adhocTask' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        // Process definition id filtering
        params.clear();
        params.put("where", "(status = 'active' AND processDefinitionId = '" + processInstance.getProcessDefinitionId() + 
                    "' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND processDefinitionId = '" + processInstance.getProcessDefinitionId() + 
                    "' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.clear();
        params.put("where", "(status = 'any' AND processDefinitionId = '" + processInstance.getProcessDefinitionId() + 
                    "' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId(), completedTask.getId());
        
        // Process definition name filerting 
        params.clear();
        params.put("where", "(status = 'active' AND processDefinitionName = 'Adhoc Activiti Process' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND processDefinitionName = 'Adhoc Activiti Process' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.clear();
        params.put("where", "(status = 'any' AND processDefinitionName = 'Adhoc Activiti Process' AND processInstanceId='" + processInstance.getId() +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId(), completedTask.getId());
        
        // Due date filtering
        params.clear();
        params.put("where", "(status = 'active' AND dueAt = '" + ISO8601DateFormat.format(activeTaskDue.getTime()) +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND dueAt = '" + ISO8601DateFormat.format(completedTaskDue.getTime()) +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.clear();
        params.put("where", "(status = 'any' AND dueAt = '" + ISO8601DateFormat.format(completedTaskDue.getTime()) +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        // Started filtering
        params.clear();
        params.put("where", "(status = 'active' AND startedAt = '" + ISO8601DateFormat.format(taskCreated.getTime()) +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, activeTask.getId());
        
        params.clear();
        params.put("where", "(status = 'completed' AND startedAt = '" + ISO8601DateFormat.format(taskCreated.getTime()) +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId());
        
        params.clear();
        params.put("where", "(status = 'any' AND startedAt = '" + ISO8601DateFormat.format(taskCreated.getTime()) +"')");
        assertTasksPresentInTaskQuery(params, tasksClient, completedTask.getId(), activeTask.getId());
    }
    
    @Test
    public void testGetTasksWithPaging() throws Exception 
    {
        RequestContext requestContext = initApiClientWithTestUser();
        // Start 6 processes
        List<ProcessInstance> startedProcesses = new ArrayList<ProcessInstance>();
        try
        {
            int numberOfTasks = 6;
            for (int i = 0; i < numberOfTasks; i++) 
            {
                startedProcesses.add(startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null));
            }
            
            String processDefinitionId = startedProcesses.get(0).getProcessDefinitionId();
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Test with existing processDefinitionId
            Map<String, String> params = new HashMap<String, String>();
            params.put("processDefinitionId", processDefinitionId);
            assertEquals(6, getResultSizeForTaskQuery(params, tasksClient));
            
            // Test with existing processDefinitionId and max items
            params.clear();
            params.put("maxItems", "3");
            params.put("processDefinitionId", processDefinitionId);
            assertEquals(3, getResultSizeForTaskQuery(params, tasksClient));
            
            // Test with existing processDefinitionId and skip count
            params.clear();
            params.put("skipCount", "2");
            params.put("processDefinitionId", processDefinitionId);
            assertEquals(4, getResultSizeForTaskQuery(params, tasksClient));
            
            // Test with existing processDefinitionId and max items and skip count
            params.clear();
            params.put("maxItems", "3");
            params.put("skipCount", "2");
            params.put("processDefinitionId", processDefinitionId);
            assertEquals(3, getResultSizeForTaskQuery(params, tasksClient));
            
            // Test with existing processDefinitionId and max items and skip count
            params.clear();
            params.put("maxItems", "3");
            params.put("skipCount", "4");
            params.put("processDefinitionId", processDefinitionId);
            assertEquals(2, getResultSizeForTaskQuery(params, tasksClient));
        }
        finally
        {
            cleanupProcessInstance(startedProcesses.toArray(new ProcessInstance[] {}));
        }
    }
    
    @Test
    public void testGetTasksAuthorization() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        // Start process by one user and try to access the task as the task assignee instead of the process
        // initiator to see if the assignee is authorized to get the task
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            Map<String, String> params = new HashMap<String, String>();
            params.put("processInstanceId", processInstance.getId());
            
            JSONObject resultingTasks = tasksClient.findTasks(params);
            assertNotNull(resultingTasks);
            JSONArray jsonEntries = (JSONArray) resultingTasks.get("entries");
            assertNotNull(jsonEntries);
            assertEquals(0, jsonEntries.size());
            
            // Set assignee, task should be in the result now
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), requestContext.getRunAsUser());
            
            resultingTasks = tasksClient.findTasks(params);
            assertNotNull(resultingTasks);
            jsonEntries = (JSONArray) resultingTasks.get("entries");
            assertNotNull(jsonEntries);
            assertEquals(1, jsonEntries.size());
            
            // Fetching task as admin should be possible
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            resultingTasks = tasksClient.findTasks(params);
            assertNotNull(resultingTasks);
            jsonEntries = (JSONArray) resultingTasks.get("entries");
            assertNotNull(jsonEntries);
            assertEquals(1, jsonEntries.size());
            
            // Fetching the task as a admin from another tenant shouldn't be possible
            TestNetwork anotherNetwork = getOtherNetwork(requestContext.getNetworkId());
            tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + anotherNetwork.getId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            resultingTasks = tasksClient.findTasks(params);
            assertNotNull(resultingTasks);
            jsonEntries = (JSONArray) resultingTasks.get("entries");
            assertNotNull(jsonEntries);
            assertEquals(0, jsonEntries.size());
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskCandidates() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            activitiProcessEngine.getTaskService().addCandidateUser(task.getId(), "testuser");
            activitiProcessEngine.getTaskService().addCandidateUser(task.getId(), "testuser2");
            activitiProcessEngine.getTaskService().addCandidateGroup(task.getId(), "testgroup");

            TasksClient tasksClient = publicApiClient.tasksClient();
            
            JSONObject taskCandidatesJSONObject = tasksClient.findTaskCandidates(task.getId());
            assertNotNull(taskCandidatesJSONObject);
            JSONArray candidateArrayJSON = (JSONArray) ((JSONObject) taskCandidatesJSONObject.get("list")).get("entries");
            assertEquals(3, candidateArrayJSON.size());
            
            boolean testUser1Found = false;
            boolean testUser2Found = false;
            boolean testGroupFound = false;
            
            for(int i=0; i < candidateArrayJSON.size(); i++) {
                JSONObject entry = (JSONObject) ((JSONObject) candidateArrayJSON.get(i)).get("entry");
                if("group".equals(entry.get("candidateType"))) 
                {
                    testGroupFound = true;
                    assertEquals("testgroup", entry.get("candidateId"));
                } else if("user".equals(entry.get("candidateType"))) 
                {
                    if("testuser".equals(entry.get("candidateId"))) 
                    {
                        testUser1Found = true;
                    } 
                    else if("testuser2".equals(entry.get("candidateId"))) 
                    {
                        testUser2Found = true;
                    }
                }
            }
            
            assertTrue(testUser1Found);
            assertTrue(testUser2Found);
            assertTrue(testGroupFound);
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskVariablesAuthentication() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        // Start process by one user and try to access the task variables as the task assignee instead of the process
        // initiator to see if the assignee is authorized to get the task
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Try accessing task variables when NOT involved in the task
            try {
                tasksClient.findTaskVariables(task.getId());
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Set assignee, task variables should be accessible now
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), requestContext.getRunAsUser());
            JSONObject jsonObject = tasksClient.findTaskVariables(task.getId());
            assertNotNull(jsonObject);
            
            // Fetching task variables as admin should be possible
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            jsonObject = tasksClient.findTaskVariables(task.getId());
            assertNotNull(jsonObject);
            
            // Fetching the task variables as a admin from another tenant shouldn't be possible
            TestNetwork anotherNetwork = getOtherNetwork(requestContext.getNetworkId());
            tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + anotherNetwork.getId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            try {
                tasksClient.findTaskVariables(task.getId());
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskVariables() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            Map<String, Object> actualLocalVariables = activitiProcessEngine.getTaskService().getVariablesLocal(task.getId());
            Map<String, Object> actualGlobalVariables = activitiProcessEngine.getRuntimeService().getVariables(processInstance.getId());
            assertEquals(5, actualGlobalVariables.size());
            assertEquals(7, actualLocalVariables.size());
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            JSONObject variables = tasksClient.findTaskVariables(task.getId());
            assertNotNull(variables);
            JSONObject list = (JSONObject) variables.get("list");
            assertNotNull(list);
            JSONArray entries = (JSONArray) list.get("entries");
            assertNotNull(entries);
            
            // Check pagination object for size
            JSONObject pagination = (JSONObject) list.get("pagination");
            assertNotNull(pagination);
            assertEquals(11L, pagination.get("count"));
            assertEquals(11L, pagination.get("totalItems"));
            assertEquals(0L, pagination.get("skipCount"));
            assertFalse((Boolean) pagination.get("hasMoreItems"));
            
            // Should contain one variable less than the actual variables in the engine, tenant-domain var is filtered out
            assertEquals(actualLocalVariables.size() + actualGlobalVariables.size() - 1, entries.size());
            
            List<JSONObject> localResults = new ArrayList<JSONObject>();
            List<JSONObject> globalResults = new ArrayList<JSONObject>();
            
            Set<String> expectedLocalVars = new HashSet<String>();
            expectedLocalVars.addAll(actualLocalVariables.keySet());
            
            Set<String> expectedGlobalVars = new HashSet<String>();
            expectedGlobalVars.addAll(actualGlobalVariables.keySet());
            expectedGlobalVars.remove(ActivitiConstants.VAR_TENANT_DOMAIN);
            
            // Add JSON entries to map for easy access when asserting values
            Map<String, JSONObject> entriesByName = new HashMap<String, JSONObject>();
            for(int i=0; i < entries.size(); i++) 
            {
                JSONObject entry = (JSONObject) ((JSONObject) entries.get(i)).get("entry");
                assertNotNull(entry);
                
                // Check if full entry is present
                assertNotNull(entry.get("scope"));
                assertNotNull(entry.get("name"));
                assertNotNull(entry.get("type"));
                if(!entry.get("name").equals("bpm_hiddenTransitions")) {
                    assertNotNull(entry.get("value"));
                }
                
                if("local".equals(entry.get("scope"))) 
                {
                    localResults.add(entry);
                    expectedLocalVars.remove(entry.get("name"));
                } 
                else if("global".equals(entry.get("scope"))) 
                {
                    globalResults.add(entry);
                    expectedGlobalVars.remove(entry.get("name"));
                }
                
                entriesByName.put((String) entry.get("name"), entry);
            }
            
            // Check correct count of globas vs. local
            assertEquals(4, globalResults.size());
            assertEquals(7, localResults.size());
            
            // Check if all variables are present
            assertEquals(0, expectedGlobalVars.size());
            assertEquals(0, expectedLocalVars.size());
            
            // Check if types are returned, based on content-model
            JSONObject var = entriesByName.get("bpm_percentComplete");
            assertNotNull(var);
            assertEquals("d:int", var.get("type"));
            assertEquals(0L, var.get("value"));
            
            var = entriesByName.get("bpm_reassignable");
            assertNotNull(var);
            assertEquals("d:boolean", var.get("type"));
            assertEquals(Boolean.TRUE, var.get("value"));
            
            var = entriesByName.get("bpm_status");
            assertNotNull(var);
            assertEquals("d:text", var.get("type"));
            assertEquals("Not Yet Started", var.get("value"));
            
            var = entriesByName.get("bpm_status");
            assertNotNull(var);
            assertEquals("d:text", var.get("type"));
            assertEquals("Not Yet Started", var.get("value"));
            
            var = entriesByName.get("bpm_assignee");
            assertNotNull(var);
            assertEquals("d:noderef", var.get("type"));
            
            final String userName = requestContext.getRunAsUser();
            String personNodeRef = TenantUtil.runAsUserTenant(new TenantRunAsWork<String>()
                        {
                @Override
                public String doWork() throws Exception
                {
                    ActivitiScriptNode person = getPersonNodeRef(userName);
                    return person.getNodeRef().toString();
                }
            }, requestContext.getRunAsUser(), requestContext.getNetworkId());
            assertEquals(personNodeRef, var.get("value"));
            
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTaskVariablesPresentInModel() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            Map<String, Object> actualLocalVariables = activitiProcessEngine.getTaskService().getVariablesLocal(task.getId());
            Map<String, Object> actualGlobalVariables = activitiProcessEngine.getRuntimeService().getVariables(processInstance.getId());
            assertEquals(5, actualGlobalVariables.size());
            assertEquals(7, actualLocalVariables.size());
            
            // Update a global value that is present in the model with type given
            JSONObject variableBody = new JSONObject();
            variableBody.put("name", "bpm_percentComplete");
            variableBody.put("value", 20);
            variableBody.put("type", "d:int");
            variableBody.put("scope", "global");
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            JSONObject resultEntry = tasksClient.updateTaskVariable(task.getId(), "bpm_percentComplete", variableBody);
            assertNotNull(resultEntry);
            JSONObject result = (JSONObject) resultEntry.get("entry");
            assertEquals("bpm_percentComplete", result.get("name"));
            assertEquals(20L, result.get("value"));
            assertEquals("d:int", result.get("type"));
            assertEquals("global", result.get("scope")); 
            assertEquals(20, activitiProcessEngine.getRuntimeService().getVariable(processInstance.getId(), "bpm_percentComplete"));
            
            // Update a local value that is present in the model with name and scope, omitting type to see if type is deducted from model
            variableBody = new JSONObject();
            variableBody.put("name", "bpm_percentComplete");
            variableBody.put("value", 30);
            variableBody.put("scope", "local");
            
            result = resultEntry = tasksClient.updateTaskVariable(task.getId(), "bpm_percentComplete", variableBody);
            assertNotNull(resultEntry);
            result = (JSONObject) resultEntry.get("entry");
            assertEquals("bpm_percentComplete", result.get("name"));
            assertEquals(30L, result.get("value"));
            assertEquals("d:int", result.get("type"));
            assertEquals("local", result.get("scope"));
            assertEquals(30, activitiProcessEngine.getTaskService().getVariable(task.getId(), "bpm_percentComplete"));
            // Global variable should remain unaffected
            assertEquals(20, activitiProcessEngine.getRuntimeService().getVariable(processInstance.getId(), "bpm_percentComplete"));
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTaskVariablesExplicitTyped() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            Map<String, Object> actualLocalVariables = activitiProcessEngine.getTaskService().getVariablesLocal(task.getId());
            Map<String, Object> actualGlobalVariables = activitiProcessEngine.getRuntimeService().getVariables(processInstance.getId());
            assertEquals(5, actualGlobalVariables.size());
            assertEquals(7, actualLocalVariables.size());
            
            // Set a new global value that is NOT present in the model with type given
            JSONObject variableBody = new JSONObject();
            variableBody.put("name", "newVariable");
            variableBody.put("value", 1234L);
            variableBody.put("type", "d:long");
            variableBody.put("scope", "global");
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            JSONObject resultEntry = tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
            assertNotNull(resultEntry);
            JSONObject result = (JSONObject) resultEntry.get("entry");
            assertEquals("newVariable", result.get("name"));
            assertEquals(1234L, result.get("value"));
            assertEquals("d:long", result.get("type"));
            assertEquals("global", result.get("scope")); 
            assertEquals(1234L, activitiProcessEngine.getRuntimeService().getVariable(processInstance.getId(), "newVariable"));
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTaskVariablesNoExplicitType() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            Map<String, Object> actualLocalVariables = activitiProcessEngine.getTaskService().getVariablesLocal(task.getId());
            Map<String, Object> actualGlobalVariables = activitiProcessEngine.getRuntimeService().getVariables(processInstance.getId());
            assertEquals(5, actualGlobalVariables.size());
            assertEquals(7, actualLocalVariables.size());
            
            // Raw number value
            JSONObject variableBody = new JSONObject();
            variableBody.put("name", "newVariable");
            variableBody.put("value", 1234);
            variableBody.put("scope", "global");
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            JSONObject resultEntry = tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
            assertNotNull(resultEntry);
            JSONObject result = (JSONObject) resultEntry.get("entry");
            assertEquals("newVariable", result.get("name"));
            assertEquals(1234L, result.get("value"));
            assertEquals("d:int", result.get("type"));
            assertEquals("global", result.get("scope")); 
            assertEquals(1234, activitiProcessEngine.getRuntimeService().getVariable(processInstance.getId(), "newVariable"));
            
            
            // Raw boolean value
            variableBody = new JSONObject();
            variableBody.put("name", "newVariable");
            variableBody.put("value", Boolean.TRUE);
            variableBody.put("scope", "local");
            
            resultEntry = tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
            assertNotNull(resultEntry);
            result = (JSONObject) resultEntry.get("entry");
            assertEquals("newVariable", result.get("name"));
            assertEquals(Boolean.TRUE, result.get("value"));
            assertEquals("d:boolean", result.get("type"));
            assertEquals("local", result.get("scope")); 
            assertEquals(Boolean.TRUE, activitiProcessEngine.getTaskService().getVariable(task.getId(), "newVariable"));
            
            
            // Raw string value
            variableBody = new JSONObject();
            variableBody.put("name", "newVariable");
            variableBody.put("value", "test value");
            variableBody.put("scope", "global");
            
            resultEntry = tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
            assertNotNull(resultEntry);
            result = (JSONObject) resultEntry.get("entry");
            assertEquals("newVariable", result.get("name"));
            assertEquals("test value", result.get("value"));
            assertEquals("d:text", result.get("type"));
            assertEquals("global", result.get("scope")); 
            assertEquals("test value", activitiProcessEngine.getRuntimeService().getVariable(processInstance.getId(), "newVariable"));
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTaskVariablesExceptions() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            // Update without name
            JSONObject variableBody = new JSONObject();
            variableBody.put("value", 1234);
            variableBody.put("scope", "global");
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            try 
            {
                tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Variable name is required.", expected.getHttpResponse());
            }
            
            // Update without scope
            variableBody = new JSONObject();
            variableBody.put("value", 1234);
            variableBody.put("name", "newVariable");
            
            try 
            {
                tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Variable scope is required and can only be 'local' or 'global'.", expected.getHttpResponse());
            }
            
            // Update in 'any' scope
            variableBody = new JSONObject();
            variableBody.put("value", 1234);
            variableBody.put("name", "newVariable");
            variableBody.put("scope", "any");
            
            try 
            {
                tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Variable scope is required and can only be 'local' or 'global'.", expected.getHttpResponse());
            }
            
            // Update in illegal scope
            variableBody = new JSONObject();
            variableBody.put("value", 1234);
            variableBody.put("name", "newVariable");
            variableBody.put("scope", "illegal");
            
            try 
            {
                tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Illegal value for variable scope: 'illegal'.", expected.getHttpResponse());
            }
            
            // Update using unsupported type
            variableBody = new JSONObject();
            variableBody.put("value", 1234);
            variableBody.put("name", "newVariable");
            variableBody.put("scope", "local");
            variableBody.put("type", "d:unexisting");
            
            try 
            {
                tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Unsupported type of variable: 'd:unexisting'.", expected.getHttpResponse());
            }
            
            // Update using unsupported type (invalid QName)
            variableBody = new JSONObject();
            variableBody.put("value", 1234);
            variableBody.put("name", "newVariable");
            variableBody.put("scope", "local");
            variableBody.put("type", " 12unexisting");
            
            try 
            {
                tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.BAD_REQUEST.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Unsupported type of variable: ' 12unexisting'.", expected.getHttpResponse());
            }
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTaskVariablesAuthentication() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        // Start process by one user and try to access the task variables as the task assignee instead of the process
        // initiator to see if the assignee is authorized to get the task
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try
        {
            JSONObject variableBody = new JSONObject();
            variableBody.put("name", "newVariable");
            variableBody.put("value", 1234);
            variableBody.put("scope", "global");
            
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Try updating task variables when NOT involved in the task
            try {
                tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            
            // Set assignee, task variables should be updatable now
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), requestContext.getRunAsUser());
            JSONObject jsonObject = tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
            assertNotNull(jsonObject);
            
            // Updating task variables as admin should be possible
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            jsonObject = tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
            assertNotNull(jsonObject);
            
            // Updating the task variables as a admin from another tenant shouldn't be possible
            TestNetwork anotherNetwork = getOtherNetwork(requestContext.getNetworkId());
            tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + anotherNetwork.getId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            try {
                jsonObject = tasksClient.updateTaskVariable(task.getId(), "newVariable", variableBody);
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskVariablesRawVariableTypes() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            Calendar dateCal = Calendar.getInstance();
            
            // Set all supported variables on the task to check the resulting types
            Map<String, Object> variablesToSet = new HashMap<String, Object>();
            variablesToSet.put("testVarString", "string");
            variablesToSet.put("testVarInteger", 1234);
            variablesToSet.put("testVarLong", 123456789L);
            variablesToSet.put("testVarDouble", 1234.5678D);
            variablesToSet.put("testVarFloat", 1234.0F);
            variablesToSet.put("testVarBoolean", Boolean.TRUE);
            variablesToSet.put("testVarDate", dateCal.getTime());
            variablesToSet.put("testVarQName", ContentModel.TYPE_AUTHORITY);
            variablesToSet.put("testVarNodeRef", new ActivitiScriptNode(new NodeRef("workspace:///testNode"), serviceRegistry));
            variablesToSet.put("testVarRawNodeRef", new NodeRef("workspace:///testNode"));
            
            activitiProcessEngine.getTaskService().setVariablesLocal(task.getId(), variablesToSet);
            Map<String, Object> actualLocalVariables = activitiProcessEngine.getTaskService().getVariablesLocal(task.getId());
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Get all local variables
            JSONObject variables = tasksClient.findTaskVariables(task.getId(), Collections.singletonMap("where", "(scope = local)"));
            assertNotNull(variables);
            JSONObject list = (JSONObject) variables.get("list");
            assertNotNull(list);
            JSONArray entries = (JSONArray) list.get("entries");
            assertNotNull(entries);
            
            // Check pagination object for size
            JSONObject pagination = (JSONObject) list.get("pagination");
            assertNotNull(pagination);
            assertEquals(actualLocalVariables.size(), ((Long)pagination.get("count")).intValue());
            assertEquals(actualLocalVariables.size(), ((Long)pagination.get("totalItems")).intValue());
            assertEquals(0L, pagination.get("skipCount"));
            assertFalse((Boolean) pagination.get("hasMoreItems"));
            
            assertEquals(actualLocalVariables.size(), entries.size());
            
            // Add JSON entries to map for easy access when asserting values
            Map<String, JSONObject> entriesByName = new HashMap<String, JSONObject>();
            for (int i = 0; i < entries.size(); i++)
            {
                JSONObject var = (JSONObject) entries.get(i);
                entriesByName.put((String) ((JSONObject) var.get("entry")).get("name"), (JSONObject) var.get("entry"));
            }
            
            // Check all values and types
            JSONObject var = entriesByName.get("testVarString");
            assertNotNull(var);
            assertEquals("d:text", var.get("type"));
            assertEquals("string", var.get("value"));
            
            var = entriesByName.get("testVarInteger");
            assertNotNull(var);
            assertEquals("d:int", var.get("type"));
            assertEquals(1234L, var.get("value"));
            
            var = entriesByName.get("testVarLong");
            assertNotNull(var);
            assertEquals("d:long", var.get("type"));
            assertEquals(123456789L, var.get("value"));
            
            var = entriesByName.get("testVarDouble");
            assertNotNull(var);
            assertEquals("d:double", var.get("type"));
            assertEquals(1234.5678D, var.get("value"));
            
            var = entriesByName.get("testVarFloat");
            assertNotNull(var);
            assertEquals("d:float", var.get("type"));
            assertEquals(1234.0D, var.get("value"));
            
            var = entriesByName.get("testVarBoolean");
            assertNotNull(var);
            assertEquals("d:boolean", var.get("type"));
            assertEquals(Boolean.TRUE, var.get("value"));
            
            var = entriesByName.get("testVarDate");
            assertNotNull(var);
            assertEquals("d:datetime", var.get("type"));
            assertEquals(dateCal.getTime(), parseDate(var, "value"));
            
            var = entriesByName.get("testVarQName");
            assertNotNull(var);
            assertEquals("d:qname", var.get("type"));
            assertEquals("cm:authority", var.get("value"));
            
            var = entriesByName.get("testVarRawNodeRef");
            assertNotNull(var);
            assertEquals("d:noderef", var.get("type"));
            assertEquals("workspace:///testNode", var.get("value"));
            
            var = entriesByName.get("testVarNodeRef");
            assertNotNull(var);
            assertEquals("d:noderef", var.get("type"));
            assertEquals("workspace:///testNode", var.get("value"));
               
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskVariablesScoped() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();

        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            Map<String, Object> actualLocalVariables = activitiProcessEngine.getTaskService().getVariablesLocal(task.getId());
            Map<String, Object> actualGlobalVariables = activitiProcessEngine.getRuntimeService().getVariables(processInstance.getId());
            assertEquals(5, actualGlobalVariables.size());
            assertEquals(7, actualLocalVariables.size());
            
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            JSONObject variables = tasksClient.findTaskVariables(task.getId(), Collections.singletonMap("where", "(scope = local)"));
            assertNotNull(variables);
            JSONObject list = (JSONObject) variables.get("list");
            assertNotNull(list);
            JSONArray entries = (JSONArray) list.get("entries");
            assertNotNull(entries);
            
            // Check pagination object for size
            JSONObject pagination = (JSONObject) list.get("pagination");
            assertNotNull(pagination);
            assertEquals(7L, pagination.get("count"));
            assertEquals(7L, pagination.get("totalItems"));
            assertEquals(0L, pagination.get("skipCount"));
            assertFalse((Boolean) pagination.get("hasMoreItems"));
            
            assertEquals(actualLocalVariables.size(), entries.size());
            
            Set<String> expectedLocalVars = new HashSet<String>();
            expectedLocalVars.addAll(actualLocalVariables.keySet());
            
            for(int i=0; i < entries.size(); i++) 
            {
                JSONObject entry = (JSONObject) ((JSONObject) entries.get(i)).get("entry");
                assertNotNull(entry);
                
                // Check if full entry is present with correct scope
                assertEquals("local", entry.get("scope"));
                assertNotNull(entry.get("name"));
                assertNotNull(entry.get("type"));
                if(!entry.get("name").equals("bpm_hiddenTransitions")) {
                    assertNotNull(entry.get("value"));
                }
                expectedLocalVars.remove(entry.get("name"));
            }
            
            assertEquals(0, expectedLocalVars.size());
            
            // Now check the global scope
            variables = tasksClient.findTaskVariables(task.getId(), Collections.singletonMap("where", "(scope = global)"));
            assertNotNull(variables);
            list = (JSONObject) variables.get("list");
            assertNotNull(list);
            entries = (JSONArray) list.get("entries");
            assertNotNull(entries);
            
            // Check pagination object for size
            pagination = (JSONObject) list.get("pagination");
            assertNotNull(pagination);
            assertEquals(4L, pagination.get("count"));
            assertEquals(4L, pagination.get("totalItems"));
            assertEquals(0L, pagination.get("skipCount"));
            assertFalse((Boolean) pagination.get("hasMoreItems"));
            
            // Should contain one variable less than the actual variables in the engine, tenant-domain var is filtered out
            assertEquals(actualGlobalVariables.size() - 1, entries.size());
            
            Set<String> expectedGlobalVars = new HashSet<String>();
            expectedGlobalVars.addAll(actualGlobalVariables.keySet());
            expectedGlobalVars.remove(ActivitiConstants.VAR_TENANT_DOMAIN);
            
            for(int i=0; i < entries.size(); i++) 
            {
                JSONObject entry = (JSONObject) ((JSONObject) entries.get(i)).get("entry");
                assertNotNull(entry);
                
                // Check if full entry is present with correct scope
                assertEquals("global", entry.get("scope"));
                assertNotNull(entry.get("name"));
                assertNotNull(entry.get("type"));
                assertNotNull(entry.get("value"));
                expectedGlobalVars.remove(entry.get("name"));
            }
            
            // Check if all variables are present
            assertEquals(0, expectedGlobalVars.size());
            
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testDeleteTaskVariable() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        activitiProcessEngine.getRuntimeService().setVariable(processInstance.getId(), "overlappingVariable", "Value set in process");
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            activitiProcessEngine.getTaskService().setVariableLocal(task.getId(), "myVariable", "This is a variable");
            activitiProcessEngine.getTaskService().setVariableLocal(task.getId(), "overlappingVariable", "Value set in task");
            
            // Delete a task-variable
            TasksClient tasksClient = publicApiClient.tasksClient();
            tasksClient.deleteTaskVariable(task.getId(), "myVariable");
            assertFalse(activitiProcessEngine.getTaskService().hasVariableLocal(task.getId(), "myVariable"));
            
            // Delete a task-variable that has the same name as a global process-variable - which should remain untouched after delete
            tasksClient.deleteTaskVariable(task.getId(), "overlappingVariable");
            assertFalse(activitiProcessEngine.getTaskService().hasVariableLocal(task.getId(), "overlappingVariable"));
            assertTrue(activitiProcessEngine.getRuntimeService().hasVariable(processInstance.getId(), "overlappingVariable"));
            assertEquals("Value set in process", activitiProcessEngine.getRuntimeService().getVariable(processInstance.getId(), "overlappingVariable"));
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testDeleteTaskVariableExceptions() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        activitiProcessEngine.getRuntimeService().setVariable(processInstance.getId(), "overlappingVariable", "Value set in process");
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
           
            TasksClient tasksClient = publicApiClient.tasksClient();
            
            // Delete a variable on an unexisting task
            try 
            {
                tasksClient.deleteTaskVariable("unexisting", "myVar");
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.NOT_FOUND.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("The entity with id: unexisting was not found", expected.getHttpResponse());
            }
            
            // Delete an unexisting variable on an existing task
            try 
            {
                tasksClient.deleteTaskVariable(task.getId(), "unexistingVarName");
                fail("Exception expected");
            }
            catch(PublicApiException expected)
            {
                assertEquals(HttpStatus.NOT_FOUND.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("The entity with id: unexistingVarName was not found", expected.getHttpResponse());
            }
            
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testDeleteTaskVariableAuthentication() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        
        String initiator = getOtherPersonInNetwork(requestContext.getRunAsUser(), requestContext.getNetworkId()).getId();
        
        // Start process by one user and try to access the task variables as the task assignee instead of the process
        // initiator to see if the assignee is authorized to get the task
        ProcessInstance processInstance = startAdhocProcess(initiator, requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            TasksClient tasksClient = publicApiClient.tasksClient();
            activitiProcessEngine.getTaskService().setVariableLocal(task.getId(), "existingVariable", "Value");
            
            // Try updating task variables when NOT involved in the task
            try {
                tasksClient.deleteTaskVariable(task.getId(), "existingVariable");
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
            assertTrue(activitiProcessEngine.getTaskService().hasVariableLocal(task.getId(), "existingVariable"));
            
            // Set assignee, task variables should be updatable now
            activitiProcessEngine.getTaskService().setAssignee(task.getId(), requestContext.getRunAsUser());
            tasksClient.deleteTaskVariable(task.getId(), "existingVariable");
            assertFalse(activitiProcessEngine.getTaskService().hasVariableLocal(task.getId(), "existingVariable"));
            activitiProcessEngine.getTaskService().setVariableLocal(task.getId(), "existingVariable", "Value");
            
            // Updating task variables as admin should be possible
            String tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + requestContext.getNetworkId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            tasksClient.deleteTaskVariable(task.getId(), "existingVariable");
            assertFalse(activitiProcessEngine.getTaskService().hasVariableLocal(task.getId(), "existingVariable"));
            activitiProcessEngine.getTaskService().setVariableLocal(task.getId(), "existingVariable", "Value");
            
            // Updating the task variables as a admin from another tenant shouldn't be possible
            TestNetwork anotherNetwork = getOtherNetwork(requestContext.getNetworkId());
            tenantAdmin = AuthenticationUtil.getAdminUserName() + "@" + anotherNetwork.getId();
            publicApiClient.setRequestContext(new RequestContext(TenantUtil.DEFAULT_TENANT, tenantAdmin));
            try {
                tasksClient.deleteTaskVariable(task.getId(), "existingVariable");
                fail("Exception expected");
            } catch(PublicApiException expected) {
                assertEquals(HttpStatus.FORBIDDEN.value(), expected.getHttpResponse().getStatusCode());
                assertErrorSummary("Permission was denied", expected.getHttpResponse());
            }
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskModel() throws Exception
    {
        RequestContext requestContext = initApiClientWithTestUser();
        ProcessInstance processInstance = startAdhocProcess(requestContext.getRunAsUser(), requestContext.getNetworkId(), null);
        try
        {
            Task task = activitiProcessEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertNotNull(task);
            
            JSONObject model = publicApiClient.tasksClient().findTaskFormModel(task.getId());
            assertNotNull(model);
            
            JSONArray entries = (JSONArray) model.get("entries");
            assertNotNull(entries);
            
            // Add all entries to a map, to make lookup easier
            Map<String, JSONObject> modelFieldsByName = new HashMap<String, JSONObject>();
            JSONObject entry = null;
            for(int i=0; i<entries.size(); i++) 
            {
                entry = (JSONObject) entries.get(i);
                assertNotNull(entry);
                entry = (JSONObject) entry.get("entry");
                assertNotNull(entry);
                modelFieldsByName.put((String) entry.get("name"), entry);
            }
            
            // Check well-known properties and their types
            
            // Validate bpm:description
            JSONObject modelEntry = modelFieldsByName.get("bpm_description");
            assertNotNull(modelEntry);
            assertEquals("Description", modelEntry.get("title"));
            assertEquals("{http://www.alfresco.org/model/bpm/1.0}description", modelEntry.get("qualifiedName"));
            assertEquals("d:text", modelEntry.get("dataType"));
            assertFalse((Boolean)modelEntry.get("required"));
            
            // Validate bpm:description
            modelEntry = modelFieldsByName.get("bpm_completionDate");
            assertNotNull(modelEntry);
            assertEquals("Completion Date", modelEntry.get("title"));
            assertEquals("{http://www.alfresco.org/model/bpm/1.0}completionDate", modelEntry.get("qualifiedName"));
            assertEquals("d:date", modelEntry.get("dataType"));
            assertFalse((Boolean)modelEntry.get("required"));
            
            // Validate cm:owner
            modelEntry = modelFieldsByName.get("cm_owner");
            assertNotNull(modelEntry);
            assertEquals("Owner", modelEntry.get("title"));
            assertEquals("{http://www.alfresco.org/model/content/1.0}owner", modelEntry.get("qualifiedName"));
            assertEquals("d:text", modelEntry.get("dataType"));
            assertFalse((Boolean)modelEntry.get("required"));
            
            // Validate bpm:priority
            modelEntry = modelFieldsByName.get("bpm_priority");
            assertNotNull(modelEntry);
            assertEquals("Priority", modelEntry.get("title"));
            assertEquals("{http://www.alfresco.org/model/bpm/1.0}priority", modelEntry.get("qualifiedName"));
            assertEquals("d:int", modelEntry.get("dataType"));
            assertEquals("2", modelEntry.get("defaultValue"));
            assertTrue((Boolean)modelEntry.get("required"));
            
            // Validate bpm:package
            modelEntry = modelFieldsByName.get("bpm_package");
            assertNotNull(modelEntry);
            assertEquals("Content Package", modelEntry.get("title"));
            assertEquals("{http://www.alfresco.org/model/bpm/1.0}package", modelEntry.get("qualifiedName"));
            assertEquals("bpm:workflowPackage", modelEntry.get("dataType"));
            assertFalse((Boolean)modelEntry.get("required"));
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    @Test
    public void testGetTaskItems() throws Exception
    {
        final RequestContext requestContext = initApiClientWithTestUser();
        
        // Create test-document and add to package
        NodeRef[] docNodeRefs = createTestDocuments(requestContext);
        ProcessInfo processInfo = startAdhocProcess(requestContext, docNodeRefs);
        
        ProcessInstance processInstance = activitiProcessEngine.getRuntimeService().createProcessInstanceQuery()
            .processInstanceId(processInfo.getId()).singleResult();
        
        assertNotNull(processInstance);
        
        try
        {
            final String newProcessInstanceId = processInstance.getId();
            ProcessesClient processesClient = publicApiClient.processesClient();
            JSONObject itemsJSON = processesClient.findProcessItems(newProcessInstanceId);
            assertNotNull(itemsJSON);
            JSONArray entriesJSON = (JSONArray) itemsJSON.get("entries");
            assertNotNull(entriesJSON);
            assertTrue(entriesJSON.size() == 2);
            boolean doc1Found = false;
            boolean doc2Found = false;
            for (Object entryObject : entriesJSON)
            {
                JSONObject entryObjectJSON = (JSONObject) entryObject;
                JSONObject entryJSON = (JSONObject) entryObjectJSON.get("entry");
                if (entryJSON.get("name").equals("Test Doc1")) {
                    doc1Found = true;
                    assertEquals(docNodeRefs[0].toString(), entryJSON.get("id"));
                    assertEquals("Test Doc1", entryJSON.get("name"));
                    assertEquals("Test Doc1 Title", entryJSON.get("title"));
                    assertEquals("Test Doc1 Description", entryJSON.get("description"));
                    assertNotNull(entryJSON.get("createdAt"));
                    assertEquals(requestContext.getRunAsUser(), entryJSON.get("createdBy"));
                    assertNotNull(entryJSON.get("modifiedAt"));
                    assertEquals(requestContext.getRunAsUser(), entryJSON.get("modifiedBy"));
                    assertNotNull(entryJSON.get("size"));
                    assertNotNull(entryJSON.get("mimeType"));
                } else {
                    doc2Found = true;
                    assertEquals(docNodeRefs[1].toString(), entryJSON.get("id"));
                    assertEquals("Test Doc2", entryJSON.get("name"));
                    assertEquals("Test Doc2 Title", entryJSON.get("title"));
                    assertEquals("Test Doc2 Description", entryJSON.get("description"));
                    assertNotNull(entryJSON.get("createdAt"));
                    assertEquals(requestContext.getRunAsUser(), entryJSON.get("createdBy"));
                    assertNotNull(entryJSON.get("modifiedAt"));
                    assertEquals(requestContext.getRunAsUser(), entryJSON.get("modifiedBy"));
                    assertNotNull(entryJSON.get("size"));
                    assertNotNull(entryJSON.get("mimeType"));
                }
            }
            assertTrue(doc1Found);
            assertTrue(doc2Found);
        }
        finally
        {
            cleanupProcessInstance(processInstance);
        }
    }
    
    protected ProcessInstance startAdhocProcess(final String user, final String networkId, final String businessKey)
    {
        return TenantUtil.runAsUserTenant(new TenantRunAsWork<ProcessInstance>()
        {
            @Override
            public ProcessInstance doWork() throws Exception
            {
                String processDefinitionKey = "@" + networkId + "@activitiAdhoc";
                // Set required variables for adhoc process and start
                Map<String, Object> variables = new HashMap<String, Object>();
                ActivitiScriptNode person = getPersonNodeRef(user);
                variables.put("bpm_assignee", person);
                variables.put("wf_notifyMe", Boolean.FALSE);
                variables.put(WorkflowConstants.PROP_INITIATOR, person);
                return activitiProcessEngine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, businessKey, variables);
            }
        }, user, networkId);
    }
    
    protected int getResultSizeForTaskQuery(Map<String, String> params, TasksClient tasksClient) throws Exception 
    {
        JSONObject taskListJSONObject = tasksClient.findTasks(params);
        assertNotNull(taskListJSONObject);
        JSONArray jsonEntries = (JSONArray) taskListJSONObject.get("entries");
        return jsonEntries.size();
    }
    
    protected void assertTasksPresentInTaskQuery(Map<String, String> params, TasksClient tasksClient, String ...taskIds) throws Exception 
    {
       assertTasksPresentInTaskQuery(params, tasksClient, true, taskIds);
    }
    
    protected void assertTasksPresentInTaskQuery(Map<String, String> params, TasksClient tasksClient, boolean countShouldMatch, String ...taskIds) throws Exception 
    {
        JSONObject taskListJSONObject = tasksClient.findTasks(params);
        assertNotNull(taskListJSONObject);
        JSONArray jsonEntries = (JSONArray) taskListJSONObject.get("entries");
        if(countShouldMatch) {
            assertEquals("Wrong amount of tasks returned by query", taskIds.length, jsonEntries.size());
        }
        
        List<String> tasksToFind = new ArrayList<String>();
        tasksToFind.addAll(Arrays.asList(taskIds));
        
        for(int i=0; i< jsonEntries.size(); i++) 
        {
            JSONObject entry = (JSONObject) ((JSONObject)jsonEntries.get(i)).get("entry");
            assertNotNull(entry);
            String taskId = (String) entry.get("id");
            tasksToFind.remove(taskId);
        }
        assertEquals("Not all tasks have been found in query response, missing: " + StringUtils.join(tasksToFind, ","), 
                    0, tasksToFind.size());
    }
    
    protected void cleanupProcessInstance(ProcessInstance... processInstances)
    {
        // Clean up process-instance regardless of test success/failure
        try 
        {
            for(ProcessInstance processInstance : processInstances)
            {
                if(processInstance != null)
                {
                    activitiProcessEngine.getRuntimeService().deleteProcessInstance(processInstance.getId(), null);
                    activitiProcessEngine.getHistoryService().deleteHistoricProcessInstance(processInstance.getId());
                }
            }
        }
        catch(Throwable t)
        {
            // Ignore error during cleanup to prevent swallowing potential assetion-exception
            log("Error while cleaning up process instance");
        }
    }
    
    protected TestNetwork getOtherNetwork(String usedNetworkId) throws Exception {
        Iterator<TestNetwork> networkIt = getTestFixture().getNetworksIt();
        while(networkIt.hasNext()) {
            TestNetwork network = networkIt.next();
            if(!usedNetworkId.equals(network.getId())) {
                return network;
            }
        }
        fail("Need more than one network to test permissions");
        return null;
    }
    
    protected TestPerson getOtherPersonInNetwork(String usedPerson, String networkId) throws Exception {
        TestNetwork usedNetwork = getTestFixture().getNetwork(networkId);
        for(TestPerson person : usedNetwork.getPeople()) {
            if(!person.getId().equals(usedPerson)) {
                return person;
            }
        }
        fail("Network doesn't have additonal users, cannot perform test");
        return null;
    }
}

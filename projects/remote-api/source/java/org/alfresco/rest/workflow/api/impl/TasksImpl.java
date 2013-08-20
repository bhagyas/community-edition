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
package org.alfresco.rest.workflow.api.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.activiti.engine.ActivitiTaskAlreadyClaimedException;
import org.activiti.engine.form.FormData;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.TaskQuery;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowObjectFactory;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.rest.antlr.WhereClauseParser;
import org.alfresco.rest.framework.core.exceptions.ConstraintViolatedException;
import org.alfresco.rest.framework.core.exceptions.EntityNotFoundException;
import org.alfresco.rest.framework.core.exceptions.InvalidArgumentException;
import org.alfresco.rest.framework.core.exceptions.PermissionDeniedException;
import org.alfresco.rest.framework.core.exceptions.UnsupportedResourceOperationException;
import org.alfresco.rest.framework.resource.parameters.CollectionWithPagingInfo;
import org.alfresco.rest.framework.resource.parameters.Paging;
import org.alfresco.rest.framework.resource.parameters.Parameters;
import org.alfresco.rest.framework.resource.parameters.where.QueryHelper;
import org.alfresco.rest.workflow.api.Processes;
import org.alfresco.rest.workflow.api.Tasks;
import org.alfresco.rest.workflow.api.impl.MapBasedQueryWalker.QueryVariableHolder;
import org.alfresco.rest.workflow.api.model.FormModelElement;
import org.alfresco.rest.workflow.api.model.Item;
import org.alfresco.rest.workflow.api.model.Task;
import org.alfresco.rest.workflow.api.model.TaskCandidate;
import org.alfresco.rest.workflow.api.model.TaskVariable;
import org.alfresco.rest.workflow.api.model.VariableScope;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.InvalidQNameException;
import org.alfresco.service.namespace.QName;

public class TasksImpl extends WorkflowRestImpl implements Tasks
{
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ANY = "any";
    private static final String STATUS_COMPLETED = "completed";
    /**
     * All properties that are read-only and cannot be updated on a single task-resource.
     */
    private static final List<String> TASK_READ_ONLY_PROPERTIES = Arrays.asList(
        "id", "processId", "processDefinitionId", "actibityDefinitionId", "startedAt", "endedAt", "durationInMs", "formResourceKey"
    );
    
    private static final Set<String> TASK_COLLECTION_EQUALS_QUERY_PROPERTIES = new HashSet<String>(Arrays.asList(
        "status", "assignee", "owner", "candidateUser", "candidateGroup", "name", "description", "priority", "processInstanceId",
        "processInstanceBusinessKey", "activityDefinitionId", "processDefinitionId", "processDefinitionName", "startedAt", "dueAt"
    ));
    
    private static final Set<String> TASK_COLLECTION_MATCHES_QUERY_PROPERTIES = new HashSet<String>(Arrays.asList(
        "name", "description", "activityDefinitionId"
    ));
    
    private static final Set<String> TASK_COLLECTION_GREATERTHAN_QUERY_PROPERTIES = new HashSet<String>(Arrays.asList(
        "startedAt", "dueAt"
    ));
    
    private static final Set<String> TASK_COLLECTION_GREATERTHANOREQUAL_QUERY_PROPERTIES = new HashSet<String>(Arrays.asList(
        "priority"
    ));
    
    private static final Set<String> TASK_COLLECTION_LESSTHAN_QUERY_PROPERTIES = new HashSet<String>(Arrays.asList(
        "startedAt", "dueAt"
    ));
    
    private static final Set<String> TASK_COLLECTION_LESSTHANOREQUAL_QUERY_PROPERTIES = new HashSet<String>(Arrays.asList(
        "priority"
    ));
    
    private RestVariableHelper restVariableHelper;
    private WorkflowObjectFactory workflowFactory;
    private WorkflowQNameConverter qNameConverter;
    private MessageService messageService;
    private Processes processes;
    
    public void setRestVariableHelper(RestVariableHelper restVariableHelper)
    {
        this.restVariableHelper = restVariableHelper;
    }
    
    public void setMessageService(MessageService messageService)
    {
        this.messageService = messageService;
    }
    
    public void setProcesses(Processes processes)
    {
        this.processes = processes;
    }
    
    @Override
    public CollectionWithPagingInfo<Task> getTasks(Parameters parameters)
    {
        Paging paging = parameters.getPaging();
        MapBasedQueryWalker propertyWalker = new MapBasedQueryWalker(TASK_COLLECTION_EQUALS_QUERY_PROPERTIES, 
                TASK_COLLECTION_MATCHES_QUERY_PROPERTIES);
        
        propertyWalker.setSupportedGreaterThanParameters(TASK_COLLECTION_GREATERTHAN_QUERY_PROPERTIES);
        propertyWalker.setSupportedGreaterThanOrEqualParameters(TASK_COLLECTION_GREATERTHANOREQUAL_QUERY_PROPERTIES);
        propertyWalker.setSupportedLessThanParameters(TASK_COLLECTION_LESSTHAN_QUERY_PROPERTIES);
        propertyWalker.setSupportedLessThanOrEqualParameters(TASK_COLLECTION_LESSTHANOREQUAL_QUERY_PROPERTIES);
        propertyWalker.enableVariablesSupport(namespaceService, dictionaryService);
        
        if(parameters.getQuery() != null)
        {
            QueryHelper.walk(parameters.getQuery(), propertyWalker);
        }
        
        String status = propertyWalker.getProperty("status", WhereClauseParser.EQUALS);
        String assignee = propertyWalker.getProperty("assignee", WhereClauseParser.EQUALS);
        String owner = propertyWalker.getProperty("owner", WhereClauseParser.EQUALS);
        String candidateUser = propertyWalker.getProperty("candidateUser", WhereClauseParser.EQUALS);
        String candidateGroup = propertyWalker.getProperty("candidateGroup", WhereClauseParser.EQUALS);
        String name = propertyWalker.getProperty("name", WhereClauseParser.EQUALS);
        String nameLike = propertyWalker.getProperty("name", WhereClauseParser.MATCHES);
        String description = propertyWalker.getProperty("description", WhereClauseParser.EQUALS);
        String descriptionLike = propertyWalker.getProperty("description", WhereClauseParser.MATCHES);
        Integer priority = propertyWalker.getProperty("priority", WhereClauseParser.EQUALS, Integer.class);
        Integer priorityGreaterThanOrEquals = propertyWalker.getProperty("priority", WhereClauseParser.GREATERTHANOREQUALS, Integer.class);
        Integer priorityLessThanOrEquals = propertyWalker.getProperty("priority", WhereClauseParser.LESSTHANOREQUALS, Integer.class);
        String processInstanceId = propertyWalker.getProperty("processInstanceId", WhereClauseParser.EQUALS);
        String processInstanceBusinessKey = propertyWalker.getProperty("processInstanceBusinessKey", WhereClauseParser.EQUALS);
        String activityDefinitionId = propertyWalker.getProperty("activityDefinitionId", WhereClauseParser.EQUALS);
        String activityDefinitionIdLike = propertyWalker.getProperty("activityDefinitionId", WhereClauseParser.MATCHES);
        String processDefinitionId = propertyWalker.getProperty("processDefinitionId", WhereClauseParser.EQUALS);
        String processDefinitionName = propertyWalker.getProperty("processDefinitionName", WhereClauseParser.EQUALS);
        Date startedAt = propertyWalker.getProperty("startedAt", WhereClauseParser.EQUALS, Date.class);
        Date startedAtGreaterThan = propertyWalker.getProperty("startedAt", WhereClauseParser.GREATERTHAN, Date.class);
        Date startedAtLessThan = propertyWalker.getProperty("startedAt", WhereClauseParser.LESSTHAN, Date.class);
        Date dueAt = propertyWalker.getProperty("dueAt", WhereClauseParser.EQUALS, Date.class);
        Date dueAtGreaterThan = propertyWalker.getProperty("dueAt", WhereClauseParser.GREATERTHAN, Date.class);
        Date dueAtLessThan = propertyWalker.getProperty("dueAt", WhereClauseParser.LESSTHAN, Date.class);

        List<Task> page = null;
        if (status == null || STATUS_ACTIVE.equals(status))
        {
            TaskQuery query = activitiProcessEngine
                    .getTaskService()
                    .createTaskQuery();
            
            if (assignee != null) query.taskAssignee(assignee);
            if (owner != null) query.taskOwner(owner);
            if (candidateUser != null) query.taskCandidateUser(candidateUser);
            if (candidateGroup != null) query.taskCandidateGroup(candidateGroup);
            if (name != null) query.taskName(name);
            if (nameLike != null) query.taskNameLike(nameLike);
            if (description != null) query.taskDescription(description);
            if (descriptionLike != null) query.taskDescriptionLike(descriptionLike);
            if (priority != null) query.taskPriority(priority);
            if (priorityGreaterThanOrEquals != null) query.taskMinPriority(priorityGreaterThanOrEquals);
            if (priorityLessThanOrEquals != null) query.taskMaxPriority(priorityLessThanOrEquals);
            if (processInstanceId != null) query.processInstanceId(processInstanceId);
            if (processInstanceBusinessKey != null) query.processInstanceBusinessKey(processInstanceBusinessKey);
            if (activityDefinitionId != null) query.taskDefinitionKey(activityDefinitionId);
            if (activityDefinitionIdLike != null) query.taskDefinitionKey(activityDefinitionIdLike);
            if (processDefinitionId != null) query.processDefinitionId(processDefinitionId);
            if (processDefinitionName != null) query.processDefinitionName(processDefinitionName);
            if (dueAt != null) query.dueDate(dueAt);
            if (dueAtGreaterThan != null) query.dueAfter(dueAtGreaterThan);
            if (dueAtLessThan != null) query.dueBefore(dueAtLessThan);
            if (startedAt != null) query.taskCreatedOn(startedAt);
            if (startedAtGreaterThan != null) query.taskCreatedAfter(startedAtGreaterThan);
            if (startedAtLessThan != null) query.taskCreatedBefore(startedAtLessThan);
            
            List<QueryVariableHolder> variableProperties = propertyWalker.getVariableProperties();
            if (variableProperties != null)
            {
                for (QueryVariableHolder queryVariableHolder : variableProperties)
                {
                    if (queryVariableHolder.getOperator() == WhereClauseParser.EQUALS)
                    {    
                        query.taskVariableValueEquals(queryVariableHolder.getPropertyName(), queryVariableHolder.getPropertyValue());
                    }
                    else if (queryVariableHolder.getOperator() == WhereClauseParser.NEGATION)
                    {
                        query.taskVariableValueNotEquals(queryVariableHolder.getPropertyName(), queryVariableHolder.getPropertyValue());
                    }
                    else
                    {
                        throw new InvalidArgumentException("variable " + queryVariableHolder.getPropertyName() + 
                                " can only be used with an =, not comparison type");
                    }
                }
            }
            
            // Add tenant-filtering
            if(tenantService.isEnabled()) {
                query.processVariableValueEquals(ActivitiConstants.VAR_TENANT_DOMAIN, TenantUtil.getCurrentDomain());
            }
            
            // Add involvment filtering if user is not admin
            if(!authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())) {
                query.taskInvolvedUser(AuthenticationUtil.getRunAsUser());
            }
            
            query.orderByDueDate().asc();
            
            List<org.activiti.engine.task.Task> tasks = query.listPage(paging.getSkipCount(), paging.getMaxItems());

            page = new ArrayList<Task>(tasks.size());
            for (org.activiti.engine.task.Task taskInstance: tasks) 
            {
                Task task = new Task(taskInstance);
                task.setFormResourceKey(getFormResourceKey(taskInstance));
                page.add(task);
            }
        }
        else if (STATUS_COMPLETED.equals(status) || STATUS_ANY.equals(status))
        {
            // Candidate user and group is only supported with STATUS_ACTIVE
            if(candidateUser != null)
            {
                throw new InvalidArgumentException("Filtering on candidateUser is only allowed in combination with status-parameter 'active'");
            }
            if(candidateGroup != null)
            {
                throw new InvalidArgumentException("Filtering on candidateGroup is only allowed in combination with status-parameter 'active'");
            }
            
            HistoricTaskInstanceQuery query = activitiProcessEngine
                    .getHistoryService()
                    .createHistoricTaskInstanceQuery();
            
            if (STATUS_COMPLETED.equals(status)) query.finished();
            if (assignee != null) query.taskAssignee(assignee);
            if (owner != null) query.taskOwner(owner);
            if (name != null) query.taskName(name);
            if (nameLike != null) query.taskNameLike(nameLike);
            if (description != null) query.taskDescription(description);
            if (descriptionLike != null) query.taskDescriptionLike(descriptionLike);
            if (priority != null) query.taskPriority(priority);
            if (processInstanceId != null) query.processInstanceId(processInstanceId);
            
            if (processInstanceBusinessKey != null) query.processInstanceBusinessKey(processInstanceBusinessKey);
            
            if (activityDefinitionId != null) query.taskDefinitionKey(activityDefinitionId);
            if (processDefinitionId != null) query.processDefinitionId(processDefinitionId);
            if (processDefinitionName != null) query.processDefinitionName(processDefinitionName);
            if (dueAt != null) query.taskDueDate(dueAt);
            if (startedAt != null) query.taskCreatedOn(startedAt);
            
            List<QueryVariableHolder> variableProperties = propertyWalker.getVariableProperties();
            if (variableProperties != null)
            {
                for (QueryVariableHolder queryVariableHolder : variableProperties)
                {
                    if (queryVariableHolder.getOperator() == WhereClauseParser.EQUALS)
                    {    
                        query.taskVariableValueEquals(queryVariableHolder.getPropertyName(), queryVariableHolder.getPropertyValue());
                    }
                    else if (queryVariableHolder.getOperator() == WhereClauseParser.NEGATION)
                    {
                        query.taskVariableValueNotEquals(queryVariableHolder.getPropertyName(), queryVariableHolder.getPropertyValue());
                    }
                    else
                    {
                        throw new InvalidArgumentException("variable " + queryVariableHolder.getPropertyName() + 
                                " can only be used with an =, not comparison type");
                    }
                }
            }
            
            // Add tenant filtering
            if(tenantService.isEnabled()) {
                query.processVariableValueEquals(ActivitiConstants.VAR_TENANT_DOMAIN, TenantUtil.getCurrentDomain());
            }
            
            // Add involvment filtering if user is not admin
            if(!authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())) {
                query.taskInvolvedUser(AuthenticationUtil.getRunAsUser());
            }
            
            query.orderByTaskDueDate().asc();
            
            List<HistoricTaskInstance> tasks = query.listPage(paging.getSkipCount(), paging.getMaxItems());

            page = new ArrayList<Task>(tasks.size());
            for (HistoricTaskInstance taskInstance: tasks) 
            {
                Task task = new Task(taskInstance);
                page.add(task);
            }
        } 
        else 
        {
            throw new InvalidArgumentException("Invalid status parameter: " + status);
        }
        
        return CollectionWithPagingInfo.asPaged(paging, page, false, page.size());
    }

    @Override
    public Task getTask(String taskId)
    {
        if(taskId == null) 
        {
            throw new InvalidArgumentException("Task id is required"); 
        }

        HistoricTaskInstance taskInstance = getValidHistoricTask(taskId, true);

        return new Task(taskInstance);
    }
    
    @Override
    public Task update(String taskId, Task task, Parameters parameters)
    {
        TaskStateTransition taskAction = null;
        
        List<String> selectedProperties = parameters.getSelectedProperties();
        if(selectedProperties.contains("state")) {
            taskAction = TaskStateTransition.getTaskActionFromString(task.getState());
        }
        
        // Fetch the task unfiltered, we check authorization below
        TaskQuery query = activitiProcessEngine.getTaskService().createTaskQuery().taskId(taskId);
        org.activiti.engine.task.Task taskInstance = query.singleResult();
        
        if(taskInstance == null) 
        {
            // Check if task exists in history, to be able to return appropriate error when trying to update an
            // existing completed task vs. an unexisting task vs. unauthorized
            boolean taskHasExisted = activitiProcessEngine.getHistoryService().createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .count() > 0;
            
            if(taskHasExisted)
            {
                throw new UnsupportedResourceOperationException("Task with id: " + taskId + " cannot be updated, it's completed");
            }
            else
            {
                throw new EntityNotFoundException(taskId);
            }
        }
        else
        {
            String user = AuthenticationUtil.getRunAsUser();
            
            // Check if user is either assignee, owner or admin
            boolean authorized = authorityService.isAdminAuthority(user)
                || user.equals(taskInstance.getOwner())
                || user.equals(taskInstance.getAssignee());
            
            Set<String> candidateGroups = new HashSet<String>();
            
            if(!authorized) 
            {
                // Check if user is initiator of the process this task is involved with
                List<IdentityLink> linksForTask = activitiProcessEngine.getTaskService().getIdentityLinksForTask(taskId);
                
                // In case the action is claim, we gather all candidate groups for this tasks, since we already have
                // the identity-links, there is no reason why we should check candidate using a DB-query
                for(IdentityLink link : linksForTask) {
                    if(user.equals(link.getUserId()) && IdentityLinkType.STARTER.equals(link.getType()))
                    {
                        authorized = true;
                        break;
                    }
                    if(taskAction == TaskStateTransition.CLAIMED && link.getGroupId() != null && link.getType().equals(IdentityLinkType.CANDIDATE)) {
                        candidateGroups.add(link.getGroupId());
                    }
                    if(taskAction == TaskStateTransition.CLAIMED && 
                            link.getUserId() != null && link.getType().equals(IdentityLinkType.CANDIDATE) &&
                            user.equals(link.getUserId())) {
                        
                        // User is a direct candidate for the task, authorized to claim
                        authorized = true;
                        break;
                    }
                }
            }
            
            // When claiming, a limited update (set assignee through claim) is allowed
            if(!authorized && taskAction == TaskStateTransition.CLAIMED)
            {
                Set<String> userGroups = authorityService.getAuthoritiesForUser(user);
                for(String group : candidateGroups)
                {
                    if(userGroups.contains(group)) 
                    {
                        authorized = true;
                        break;
                    }
                }
            }
            
            if(!authorized) 
            {
                // None of the above conditions are met, not authorized to update task
                throw new PermissionDeniedException();
            }
        }
        
        // Update fields if no action is required
        if(taskAction == null)
        {
            // Only update task in Activiti API if actual properties are changed
            if(updateTaskProperties(selectedProperties, task, taskInstance))
            {
                activitiProcessEngine.getTaskService().saveTask(taskInstance);
            }
        }
        else
        {
            // Perform actions associated to state transition 
            if(taskAction != null) {
                switch(taskAction) {
                    case CLAIMED:
                        try
                        {
                            activitiProcessEngine.getTaskService().claim(taskId, AuthenticationUtil.getRunAsUser());
                        }
                        catch(ActivitiTaskAlreadyClaimedException atace)
                        {
                            throw new ConstraintViolatedException("The task is already claimed by another user.");
                        }
                        break;
                    case COMPLETED:
                        activitiProcessEngine.getTaskService().complete(taskId);
                        break;
                    case DELEGATED:
                        if(selectedProperties.contains("assignee") && task.getAssignee() != null)
                        {
                            if(taskInstance.getAssignee() == null || !taskInstance.getAssignee().equals(AuthenticationUtil.getRunAsUser()))
                            {
                                // Alter assignee before delegating to preserve trail of who actually delegated
                                activitiProcessEngine.getTaskService().setAssignee(taskId, AuthenticationUtil.getRunAsUser());
                            }
                            activitiProcessEngine.getTaskService().delegateTask(taskId, task.getAssignee());
                        }
                        else
                        {
                            throw new InvalidArgumentException("When delegating a task, assignee should be selected and provided in the request.");
                        }
                        break;
                    case RESOLVED:
                        activitiProcessEngine.getTaskService().resolveTask(taskId);
                        break;
                        
                    case UNCLAIMED:
                        activitiProcessEngine.getTaskService().setAssignee(taskId, null);
                        break;
                }
            }
        }
        
        if(taskAction == TaskStateTransition.COMPLETED)
        {
            // Task completed, fetch from history instead of returning runtime instance
            return new Task(activitiProcessEngine.getHistoryService()
                        .createHistoricTaskInstanceQuery()
                        .taskId(taskId).singleResult());
        }
        else
        {
            // Task object updated, can return the object without fetching it agian 
            return new Task(taskInstance);
        }
    }
    
    @Override
    public CollectionWithPagingInfo<FormModelElement> getTaskFormModel(String taskId, Paging paging)
    {
        // Check if task can be accessed by the current user
        getValidTask(taskId, false);
        
        FormData formData = activitiProcessEngine.getFormService().getTaskFormData(taskId);
        if(formData == null)
        {
            throw new EntityNotFoundException(taskId);
        }
        
        // Lookup type definition for the task
        TypeDefinition taskType = workflowFactory.getTaskFullTypeDefinition(formData.getFormKey(), true);
        return getFormModelElements(taskType, paging);
    }
    
    @Override
    public CollectionWithPagingInfo<TaskVariable> getTaskVariables(String taskId, Paging paging, VariableScope scope) 
    {
        // Ensure the user is allowed to get variables for the task involved. 
        org.activiti.engine.task.Task taskInstance = getValidTask(taskId, true);
        String formKey = activitiProcessEngine.getFormService().getTaskFormKey(taskInstance.getProcessDefinitionId(), taskInstance.getTaskDefinitionKey());

        // Based on the scope, right variables are queried
        Map<String, Object> taskvariables = null;
        Map<String, Object> processVariables = null;
        
        if(scope == VariableScope.ANY || scope == VariableScope.LOCAL)
        {
            taskvariables = activitiProcessEngine.getTaskService().getVariablesLocal(taskId);
        }
        
        if((scope == VariableScope.ANY || scope == VariableScope.GLOBAL) && taskInstance.getExecutionId() != null)
        {
            processVariables = activitiProcessEngine.getRuntimeService().getVariables(taskInstance.getExecutionId());
        }
        
        // Convert raw variables to TaskVariables
        List<TaskVariable> page = restVariableHelper.getTaskVariables(taskvariables, processVariables, getWorkflowFactory().getTaskFullTypeDefinition(formKey, false));
        return CollectionWithPagingInfo.asPaged(paging, page, false, page.size());
    }
    
    @Override
    public TaskVariable updateTaskVariable(String taskId, TaskVariable taskVariable) 
    {
        org.activiti.engine.task.Task taskInstance = getValidTask(taskId, true);
        
        if(taskVariable.getName() == null)
        {
            throw new InvalidArgumentException("Variable name is required.");
        }
        
        if(taskVariable.getVariableScope() == null || taskVariable.getVariableScope() == VariableScope.ANY)
        {
            throw new InvalidArgumentException("Variable scope is required and can only be 'local' or 'global'.");
        }

        DataTypeDefinition dataTypeDefinition = null;
        if(taskVariable.getType() != null)
        {
            try
            {
                QName dataType = QName.createQName(taskVariable.getType(), namespaceService);
                dataTypeDefinition = dictionaryService.getDataType(dataType);
            }
            catch(InvalidQNameException iqne)
            {
                throw new InvalidArgumentException("Unsupported type of variable: '" + taskVariable.getType() +"'.");
            }
        } 
        else
        {
            // Revert to either the content-model type or the raw type provided by the request
            try 
            {
                String formKey = activitiProcessEngine.getFormService().getTaskFormKey(taskInstance.getProcessDefinitionId(), taskInstance.getTaskDefinitionKey());
                TypeDefinition typeDefinition = getWorkflowFactory().getTaskFullTypeDefinition(formKey, false);
                QName propQName = WorkflowQNameConverter.convertNameToQName(taskVariable.getName(), namespaceService);
                
                PropertyDefinition propDef = typeDefinition.getProperties().get(propQName);
                if(propDef != null)
                {
                    dataTypeDefinition = propDef.getDataType();
                }
                else
                {
                    AssociationDefinition assocDef = typeDefinition.getAssociations().get(propQName);
                    if(assocDef != null)
                    {
                        dataTypeDefinition = dictionaryService.getDataType(DataTypeDefinition.NODE_REF);
                    }
                }
            }
            catch(InvalidQNameException ignore)
            {
                // In case the property is not part of the model, it's possible that the property-name is not a valid.
                // This can be ignored safeley as it falls back to the raw type
            }
            
            if(dataTypeDefinition == null)
            {
                // Finall fallback to raw value when no type has been passed and not present in model
                dataTypeDefinition = dictionaryService.getDataType(restVariableHelper.extractTypeFromValue(taskVariable.getValue()));
            }
        }
        
        if(dataTypeDefinition == null)
        {
            throw new InvalidArgumentException("Unsupported type of variable: '" + taskVariable.getType() +"'.");
        }
        
        Object actualValue = DefaultTypeConverter.INSTANCE.convert(dataTypeDefinition, taskVariable.getValue());
        
        if (VariableScope.LOCAL.equals(taskVariable.getVariableScope()))
        {
            activitiProcessEngine.getTaskService().setVariableLocal(taskId, taskVariable.getName(), actualValue);
        }
        else if(VariableScope.GLOBAL.equals(taskVariable.getVariableScope()))
        {
            if(taskInstance.getExecutionId() != null)
            {
                activitiProcessEngine.getRuntimeService().setVariable(taskInstance.getExecutionId(), taskVariable.getName(), actualValue);
            }
            else
            {
                throw new InvalidArgumentException("Cannot set global variables on a task that is not part of a process.");
            }
        }
        
        // Set type so it's returned in case it was left empty
        taskVariable.setType(dataTypeDefinition.getName().toPrefixString(namespaceService));
        return taskVariable;
    }
    
    public void deleteTaskVariable(String taskId, String variableName)
    {
        if(variableName == null)
        {
            throw new InvalidArgumentException("Variable name is required.");
        }
        
        // Fetch task to check if user is authorized to perform the delete
        getValidTask(taskId, true);
        
        // Check if variable is present on the scope
        if(!activitiProcessEngine.getTaskService().hasVariableLocal(taskId, variableName))
        {
            throw new EntityNotFoundException(variableName);
        }
        activitiProcessEngine.getTaskService().removeVariableLocal(taskId, variableName);
    }
    
    @Override
    public CollectionWithPagingInfo<TaskCandidate> getTaskCandidates(String taskId, Paging paging) 
    {
        List<IdentityLink> links = activitiProcessEngine.getTaskService().getIdentityLinksForTask(taskId);
        List<TaskCandidate> page = new ArrayList<TaskCandidate>();
        if (links != null) 
        {
            for (IdentityLink identityLink : links)
            {
                if (IdentityLinkType.CANDIDATE.equals(identityLink.getType())) 
                {
                    page.add(new TaskCandidate(identityLink));
                }
            }
        }
        
        return CollectionWithPagingInfo.asPaged(paging, page, false, page.size());
    }
    
    @Override
    public Item createItem(String taskId, Item item)
    {
        org.activiti.engine.task.Task task = getValidTask(taskId, false);
      
        if(task.getProcessInstanceId() == null)
        {
            throw new UnsupportedResourceOperationException("Task is not part of process, no items available.");
        }
        return processes.createItem(task.getProcessInstanceId(), item);
    }
    
    @Override
    public void deleteItem(String taskId, String itemId)
    {
        org.activiti.engine.task.Task task = getValidTask(taskId, false);
        
        if(task.getProcessInstanceId() == null)
        {
            throw new UnsupportedResourceOperationException("Task is not part of process, no items available.");
        }
        processes.deleteItem(task.getProcessInstanceId(), itemId);
    }
    
    @Override
    public Item getItem(String taskId, String itemId)
    {
        org.activiti.engine.task.Task task = getValidTask(taskId, false);
        
        if(task.getProcessInstanceId() == null)
        {
            throw new UnsupportedResourceOperationException("Task is not part of process, no items available.");
        }
        return processes.getItem(task.getProcessInstanceId(), itemId);
    }
    
    @Override
    public CollectionWithPagingInfo<Item> getItems(String taskId, Paging paging)
    {
        org.activiti.engine.task.Task task = getValidTask(taskId, false);
        
        if(task.getProcessInstanceId() == null)
        {
            throw new UnsupportedResourceOperationException("Task is not part of process, no items available.");
        }
        return processes.getItems(task.getProcessInstanceId(), paging);
    }

    protected String getFormResourceKey(org.activiti.engine.task.Task task) {
        if (task.getProcessDefinitionId() != null)
        {
            return activitiProcessEngine.getFormService().getTaskFormKey(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        } 
        else 
        {
            // Standalone task, no form key available
            return null;
        }
    }
    
    /**
     * @return true, if at least one task property has been changed based on the given parameters.
     */
    protected boolean updateTaskProperties(List<String> selectedProperties, Task task, 
                org.activiti.engine.task.Task taskInstance)
    {
        boolean taskNeedsUpdate = false;
        for(String selected : selectedProperties) 
        {
            if(!"state".equals(selected))
            {
                // "name", "description", "dueAt", "priority", "assignee", "owner"
                taskNeedsUpdate = true;
                if("name".equals(selected))
                {
                    taskInstance.setName(task.getName());
                }
                else if("description".equals(selected))
                {
                    taskInstance.setDescription(task.getDescription());
                }
                else if("dueAt".equals(selected))
                {
                    taskInstance.setDueDate(task.getDueAt());
                }
                else if("priority".equals(selected))
                {
                    taskInstance.setPriority(task.getPriority());   
                }
                else if("assignee".equals(selected))
                {
                    taskInstance.setAssignee(task.getAssignee());
                }
                else if("owner".equals(selected))
                {
                    taskInstance.setOwner(task.getOwner());
                }
                else
                {
                    if(TASK_READ_ONLY_PROPERTIES.contains(selected))
                    {
                        // Trying to update a read-only -but existing- property
                        throw new InvalidArgumentException("The property selected for update is read-only: " + selected);
                    }
                    else
                    {
                        // Trying to update unexisting property
                        throw new InvalidArgumentException("The property selected for update does not exist for this resource: " + selected);
                    }
                }
            }
        }
        return taskNeedsUpdate;
    }
    
    /**
     * Get a valid {@link HistoricTaskInstance} based on the given task id. Checks if current logged
     * in user is assignee/owner/involved with the task. In case true was passed for "validIfClaimable", 
     * the task is also valid if the current logged in user is a candidate for claiming the task.
     *  
     * @throws EntityNotFoundException when the task was not found
     * @throws PermissionDeniedException when the current logged in user isn't allowed to access task.
     */
    protected HistoricTaskInstance getValidHistoricTask(String taskId, boolean validIfClaimable)
    {
        HistoricTaskInstanceQuery query = activitiProcessEngine.getHistoryService()
            .createHistoricTaskInstanceQuery()
            .taskId(taskId);
        
        if(authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())) 
        {
            // Admin is allowed to read all tasks in the current tenant
            if(tenantService.isEnabled()) {
                query.processVariableValueEquals(ActivitiConstants.VAR_TENANT_DOMAIN, TenantUtil.getCurrentDomain());
            }
        }
        else
        {
            // If non-admin user, involvement in the task is required (either owner, assignee or externally involved).
            query.taskInvolvedUser(AuthenticationUtil.getRunAsUser());
        }
        
        HistoricTaskInstance taskInstance =  query.singleResult();
        
        if(taskInstance == null) 
        {
            // Either the task doesn't exist or the user is not involved direcltly. We can differentiate by
            // checking if the task exists without applying the additional filtering
            taskInstance =  activitiProcessEngine.getHistoryService()
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
            
            if(taskInstance == null) 
            {
                // Full error message will be "Task with id: 'id' was not found" 
                throw new EntityNotFoundException(taskId); 
            }
            else
            {
                boolean isTaskClaimable = false;
                
                if(validIfClaimable)
                {
                    if(taskInstance.getEndTime() == null) 
                    {
                        // Task is not yet finished, so potentially claimable. If user is part of a "candidateGroup", the task is accessible to the
                        // user regardless of not being involved/owner/assignee
                        isTaskClaimable = activitiProcessEngine.getTaskService()
                        .createTaskQuery()
                        .taskCandidateGroupIn(new ArrayList<String>(authorityService.getAuthoritiesForUser(AuthenticationUtil.getRunAsUser())))
                        .taskId(taskId)
                        .count() == 1;
                    }
                }
                
                if(!isTaskClaimable)
                {
                    throw new PermissionDeniedException();
                }
            }
        }
        return taskInstance;
    }
    
    /**
     * Get a valid {@link org.activiti.engine.task.Task} based on the given task id. Checks if current logged
     * in user is assignee/owner/involved with the task. In case true was passed for "validIfClaimable", 
     * the task is also valid if the current logged in user is a candidate for claiming the task.
     *  
     * @throws EntityNotFoundException when the task was not found
     * @throws PermissionDeniedException when the current logged in user isn't allowed to access task.
     */
    protected org.activiti.engine.task.Task getValidTask(String taskId, boolean validIfClaimable)
    {
        if(taskId == null)
        {
            throw new InvalidArgumentException("Task id is required.");
        }
        
        TaskQuery query = activitiProcessEngine.getTaskService()
            .createTaskQuery()
            .taskId(taskId);
        
        if(authorityService.isAdminAuthority(AuthenticationUtil.getRunAsUser())) 
        {
            // Admin is allowed to read all tasks in the current tenant
            if(tenantService.isEnabled()) {
                query.processVariableValueEquals(ActivitiConstants.VAR_TENANT_DOMAIN, TenantUtil.getCurrentDomain());
            }
        }
        else
        {
            // If non-admin user, involvement in the task is required (either owner, assignee or externally involved).
            query.taskInvolvedUser(AuthenticationUtil.getRunAsUser());
        }
        
        org.activiti.engine.task.Task taskInstance =  query.singleResult();
        
        if(taskInstance == null) 
        {
            // Either the task doesn't exist or the user is not involved direcltly. We can differentiate by
            // checking if the task exists without applying the additional filtering
            taskInstance =  activitiProcessEngine.getTaskService()
                .createTaskQuery()
                .taskId(taskId)
                .singleResult();
            
            if(taskInstance == null) 
            {
                // Full error message will be "Task with id: 'id' was not found" 
                throw new EntityNotFoundException(taskId); 
            }
            else
            {
                boolean isTaskClaimable = false;
                
                if(validIfClaimable) 
                {
                    // Task is not yet finished, so potentially claimable. If user is part of a "candidateGroup", the task is accessible to the
                    // user regardless of not being involved/owner/assignee
                    isTaskClaimable = activitiProcessEngine.getTaskService()
                    .createTaskQuery()
                    .taskCandidateGroupIn(new ArrayList<String>(authorityService.getAuthoritiesForUser(AuthenticationUtil.getRunAsUser())))
                    .taskId(taskId)
                    .count() == 1;
                }
                
                if(!isTaskClaimable)
                {
                    throw new PermissionDeniedException();
                }
            }
        }
        return taskInstance;
    }
    
    protected WorkflowQNameConverter getQNameConverter()
    {
        if (qNameConverter == null)
        {
            qNameConverter = new WorkflowQNameConverter(namespaceService);
        }
        return qNameConverter;
    }
    
    protected WorkflowObjectFactory getWorkflowFactory()
    {
        if (workflowFactory == null) 
        {
            workflowFactory = new WorkflowObjectFactory(getQNameConverter(), tenantService, messageService, dictionaryService, 
                        ActivitiConstants.ENGINE_ID, WorkflowModel.TYPE_ACTIVTI_START_TASK);
        }
        return workflowFactory;
    }
    
    
    private enum TaskStateTransition {
        
        COMPLETED, CLAIMED, UNCLAIMED, DELEGATED, RESOLVED;
        
        /**
         * @return the {@link TaskStateTransition} for the given string
         * @throws InvalidArgumentException when no action exists for the given string
         */
        public static TaskStateTransition getTaskActionFromString(String action) 
        {
            for(TaskStateTransition taskAction : values())
            {
                if(taskAction.name().toLowerCase().equals(action))
                {
                    return taskAction;
                }
            }
            throw new InvalidArgumentException("The task state property has an invalid value: " + action);
        }
    }
}

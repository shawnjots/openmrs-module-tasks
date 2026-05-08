/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.TasksConfig;

import java.util.List;

/**
 * The main service of this module, which is exposed for other modules. See
 * moduleApplicationContext.xml on how it is wired up.
 */
public interface TasksService extends OpenmrsService {
	
	/**
	 * Returns a task by uuid. It can be called by any authenticated user. It is fetched in read only
	 * transaction.
	 * 
	 * @param uuid
	 * @return
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	Task getTaskByUuid(String uuid) throws APIException;
	
	/**
	 * Saves a task. It can be called by users with this module's privilege. It is executed in a
	 * transaction.
	 * 
	 * @param task
	 * @return
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_MANAGE_PRIVILEGE)
	Task saveTask(Task task) throws APIException;
	
	/**
	 * Returns all non-voided tasks for a patient, ordered by date created (newest first).
	 *
	 * @param patientId the patient id
	 * @return the non-voided tasks for the patient
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	List<Task> getTasksByPatientId(Integer patientId) throws APIException;
	
	/**
	 * Returns tasks for a patient, optionally including voided tasks; ordered by date created (newest
	 * first).
	 *
	 * @param patientId the patient id
	 * @param includeVoided whether to include voided tasks in the result
	 * @return the tasks for the patient
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	List<Task> getTasksByPatientId(Integer patientId, boolean includeVoided) throws APIException;
	
	/**
	 * Returns the non-voided tasks for a patient whose status is not CANCELLED or ENTEREDINERROR;
	 * COMPLETED and STOPPED tasks are still returned because they represent legitimate history that
	 * clients may want to display. Ordered by date created (newest first).
	 *
	 * @param patientId the patient id
	 * @return active tasks for the patient
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	List<Task> getActiveTasksByPatientId(Integer patientId) throws APIException;
	
	/**
	 * Voids a task with the provided reason.
	 * 
	 * @param task the task to void
	 * @param voidReason the reason for voiding
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_DELETE_PRIVILEGE)
	void voidTask(Task task, String voidReason) throws APIException;
	
	/**
	 * Permanently deletes a task from the database.
	 * 
	 * @param task the task to purge
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_DELETE_PRIVILEGE)
	void purgeTask(Task task) throws APIException;
	
	/**
	 * Returns a system task by uuid.
	 * 
	 * @param uuid the uuid of the system task
	 * @return the system task, or null if not found
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	SystemTask getSystemTaskByUuid(String uuid) throws APIException;
	
	/**
	 * Returns all system tasks, optionally including retired ones.
	 * 
	 * @param includeRetired whether to include retired system tasks
	 * @return list of system tasks
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	List<SystemTask> getAllSystemTasks(boolean includeRetired) throws APIException;
	
	/**
	 * Saves a system task. Used by the CSV loader to persist system tasks.
	 * 
	 * @param systemTask the system task to save
	 * @return the saved system task
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_MANAGE_PRIVILEGE)
	SystemTask saveSystemTask(SystemTask systemTask) throws APIException;
	
	/**
	 * Retires a system task with the provided reason.
	 * 
	 * @param systemTask the system task to retire
	 * @param retireReason the reason for retiring
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_MANAGE_PRIVILEGE)
	void retireSystemTask(SystemTask systemTask, String retireReason) throws APIException;
}

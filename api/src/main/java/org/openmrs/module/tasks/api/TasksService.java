/**
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
import org.openmrs.module.tasks.TasksConfig;
import org.openmrs.module.tasks.Task;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The main service of this module, which is exposed for other modules. See
 * moduleApplicationContext.xml on how it is wired up.
 */
public interface TasksService extends OpenmrsService {
	
	/**
	 * Returns a task by uuid. It can be called by any authenticated user. It is fetched in read
	 * only transaction.
	 * 
	 * @param uuid
	 * @return
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	@Transactional(readOnly = true)
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
	@Transactional
	Task saveTask(Task task) throws APIException;
	
	/**
	 * Returns all tasks for a patient. It can be called by any authenticated user. It is fetched in
	 * read only transaction.
	 * 
	 * @param patientId
	 * @return
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_VIEW_PRIVILEGE)
	@Transactional(readOnly = true)
	List<Task> getTasksByPatientId(Integer patientId) throws APIException;
	
	/**
	 * Voids a task with the provided reason.
	 * 
	 * @param task the task to void
	 * @param voidReason the reason for voiding
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_DELETE_PRIVILEGE)
	@Transactional
	void voidTask(Task task, String voidReason) throws APIException;
	
	/**
	 * Permanently deletes a task from the database.
	 * 
	 * @param task the task to purge
	 * @throws APIException
	 */
	@Authorized(TasksConfig.TASKS_DELETE_PRIVILEGE)
	@Transactional
	void purgeTask(Task task) throws APIException;
}

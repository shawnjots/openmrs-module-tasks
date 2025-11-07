/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api.impl;

import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.TasksService;
import org.openmrs.module.tasks.api.dao.TasksDao;

import java.util.List;
import java.util.Date;

public class TasksServiceImpl extends BaseOpenmrsService implements TasksService {
	
	TasksDao dao;
	
	/**
	 * Injected in moduleApplicationContext.xml
	 */
	public void setDao(TasksDao dao) {
		this.dao = dao;
	}
	
	@Override
	public Task getTaskByUuid(String uuid) throws APIException {
		return dao.getTaskByUuid(uuid);
	}
	
	@Override
	public Task saveTask(Task task) throws APIException {
		return dao.saveTask(task);
	}
	
	@Override
	public List<Task> getTasksByPatientId(Integer patientId) throws APIException {
		return dao.getTasksByPatientId(patientId);
	}
	
	@Override
	public void voidTask(Task task, String voidReason) throws APIException {
		if (task == null) {
			throw new APIException("Task cannot be null");
		}
		if (Boolean.TRUE.equals(task.getVoided())) {
			return;
		}
		if (voidReason == null || voidReason.trim().isEmpty()) {
			throw new APIException("Void reason is required");
		}
		task.setVoided(true);
		task.setVoidReason(voidReason);
		if (task.getDateVoided() == null) {
			task.setDateVoided(new Date());
		}
		dao.saveTask(task);
	}
	
	@Override
	public void purgeTask(Task task) throws APIException {
		if (task == null) {
			throw new APIException("Task cannot be null");
		}
		dao.deleteTask(task);
	}
}

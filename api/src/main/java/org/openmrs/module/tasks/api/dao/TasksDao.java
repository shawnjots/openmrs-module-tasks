/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api.dao;

import org.hibernate.criterion.Restrictions;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.tasks.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("tasks.TasksDao")
public class TasksDao {
	
	@Autowired
	DbSessionFactory sessionFactory;
	
	private DbSession getSession() {
		return sessionFactory.getCurrentSession();
	}
	
	public Task getTaskByUuid(String uuid) {
		return (Task) getSession().createCriteria(Task.class).add(Restrictions.eq("uuid", uuid)).uniqueResult();
	}
	
	public Task saveTask(Task task) {
		getSession().saveOrUpdate(task);
		return task;
	}
	
	@SuppressWarnings("unchecked")
	public List<Task> getTasksByPatientId(Integer patientId) {
		return getSession().createCriteria(Task.class).add(Restrictions.eq("patient.id", patientId))
		        .add(Restrictions.eq("voided", false)).list();
	}
}

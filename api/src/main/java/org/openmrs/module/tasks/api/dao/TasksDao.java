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

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.api.db.hibernate.HibernateUtil;
import org.openmrs.module.tasks.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("tasks.TasksDao")
public class TasksDao {
	
	@Autowired
	private SessionFactory sessionFactory;
	
	private Session getCurrentSession() {
		return sessionFactory.getCurrentSession();
	}
	
	public Task getTaskByUuid(String uuid) {
		return HibernateUtil.getUniqueEntityByUUID(sessionFactory, Task.class, uuid);
	}
	
	public Task saveTask(Task task) {
		getCurrentSession().saveOrUpdate(task);
		return task;
	}
	
	public void deleteTask(Task task) {
		getCurrentSession().delete(task);
	}
	
	public List<Task> getTasksByPatientId(Integer patientId) {
		return getCurrentSession()
		        .createQuery("from tasks.Task t where t.patient.patientId = :patientId and t.voided = false", Task.class)
		        .setParameter("patientId", patientId).getResultList();
	}
}

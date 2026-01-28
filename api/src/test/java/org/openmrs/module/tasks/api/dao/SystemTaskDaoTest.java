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

import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * Integration tests for SystemTask DAO methods.
 */
public class SystemTaskDaoTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	TasksDao dao;
	
	@Test
	public void saveSystemTask_shouldSaveAllPropertiesInDb() {
		SystemTask systemTask = new SystemTask();
		systemTask.setName("daily-vital-check");
		systemTask.setTitle("Daily Vital Check");
		systemTask.setDescription("Check patient vitals daily");
		systemTask.setPriority(Priority.HIGH);
		systemTask.setRationale("Routine monitoring for patient safety");
		
		dao.saveSystemTask(systemTask);
		
		Context.flushSession();
		Context.clearSession();
		
		SystemTask savedTask = dao.getSystemTaskByUuid(systemTask.getUuid());
		
		assertThat(savedTask, is(notNullValue()));
		assertThat(savedTask.getName(), is("daily-vital-check"));
		assertThat(savedTask.getTitle(), is("Daily Vital Check"));
		assertThat(savedTask.getDescription(), is("Check patient vitals daily"));
		assertThat(savedTask.getPriority(), is(Priority.HIGH));
		assertThat(savedTask.getRationale(), is("Routine monitoring for patient safety"));
		assertThat(savedTask.getRetired(), is(false));
	}
	
	@Test
	public void getAllSystemTasks_shouldReturnOnlyActiveSystemTasks() {
		SystemTask active1 = new SystemTask();
		active1.setName("active-task-1");
		active1.setTitle("Active Task 1");
		dao.saveSystemTask(active1);
		
		SystemTask active2 = new SystemTask();
		active2.setName("active-task-2");
		active2.setTitle("Active Task 2");
		dao.saveSystemTask(active2);
		
		SystemTask retired = new SystemTask();
		retired.setName("retired-task");
		retired.setTitle("Retired Task");
		retired.setRetired(true);
		retired.setRetireReason("No longer needed");
		dao.saveSystemTask(retired);
		
		Context.flushSession();
		Context.clearSession();
		
		List<SystemTask> activeTasks = dao.getAllSystemTasks(false);
		
		assertThat(activeTasks.size(), is(2));
		assertThat(activeTasks, hasItems(hasProperty("name", is("active-task-1")), hasProperty("name", is("active-task-2"))));
		assertThat(activeTasks, not(hasItem(hasProperty("name", is("retired-task")))));
	}
	
	@Test
	public void getAllSystemTasks_shouldIncludeRetiredWhenRequested() {
		SystemTask active = new SystemTask();
		active.setName("active-task");
		active.setTitle("Active Task");
		dao.saveSystemTask(active);
		
		SystemTask retired = new SystemTask();
		retired.setName("retired-task");
		retired.setTitle("Retired Task");
		retired.setRetired(true);
		retired.setRetireReason("No longer needed");
		dao.saveSystemTask(retired);
		
		Context.flushSession();
		Context.clearSession();
		
		List<SystemTask> allTasks = dao.getAllSystemTasks(true);
		
		assertThat(allTasks.size(), is(2));
		assertThat(allTasks, hasItems(hasProperty("name", is("active-task")), hasProperty("name", is("retired-task"))));
		
		List<SystemTask> activeTasks = dao.getAllSystemTasks(false);
		assertThat(activeTasks.size(), is(1));
	}
	
	@Test
	public void getSystemTaskByUuid_shouldReturnNullForUnknownUuid() {
		SystemTask result = dao.getSystemTaskByUuid("non-existent-uuid");
		assertThat(result, is(nullValue()));
	}
}

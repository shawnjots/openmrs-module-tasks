OpenMRS Tasks Module
==========================

Description
-----------
This module provides a FHIR v4 backend exposing the CarePlan endpoint. It was developed in tandem with the Task List feature in O3.

FHIR API Endpoints
------------------
This module exposes FHIR v4 CarePlan endpoints in two ways:

### Via FHIR2 Module Integration
The module integrates with the OpenMRS FHIR2 module to expose standard FHIR endpoints at `/ws/fhir2/R4/CarePlan`:

**Create CarePlan**
- **Endpoint:** `POST /ws/fhir2/R4/CarePlan`
- **Content-Type:** `application/json` or `application/fhir+json`
- **Description:** Creates a new CarePlan resource. Each CarePlan corresponds to a Task entity.
- **Request Body:** FHIR CarePlan resource in JSON format
- **Response:** Returns the created CarePlan with its ID
- **Required Fields:**
  - `subject`: Patient reference in format `Patient/{patientId}`
- **Optional Fields:**
  - `activity[0].detail.performer[0]`: Provider reference in format `Provider/{userId}` for task assignment

**Read CarePlan by ID**
- **Endpoint:** `GET /ws/fhir2/R4/CarePlan/{id}`
- **Description:** Retrieves a CarePlan resource by its UUID
- **Response:** Returns the CarePlan resource if found, or 404 if not found

**Search CarePlans by Patient**
- **Endpoint:** `GET /ws/fhir2/R4/CarePlan?subject=Patient/{patientId}`
- **Description:** Searches for all CarePlans associated with a specific patient
- **Query Parameters:**
  - `subject`: Patient reference in format `Patient/{patientId}` or just `{patientId}`
- **Response:** Returns a list of CarePlan resources matching the search criteria

### Via Direct REST Controller
The module also exposes endpoints directly at `/ws/rest/v1/tasks/careplan`:

**Create CarePlan**
- **Endpoint:** `POST /ws/rest/v1/tasks/careplan`
- **Content-Type:** `application/json`
- **Description:** Creates a new CarePlan resource
- **Request Body:** FHIR CarePlan resource in JSON format
- **Response:** Returns the created CarePlan as JSON with HTTP 201 (Created)
- **Error Response:** Returns a JSON error object on error

**Read CarePlan by ID**
- **Endpoint:** `GET /ws/rest/v1/tasks/careplan/{carePlanId}`
- **Description:** Retrieves a CarePlan by UUID
- **Response:** Returns the CarePlan as JSON, or 404 with a JSON error object if not found

**Update CarePlan**
- **Endpoint:** `PUT /ws/rest/v1/tasks/careplan/{carePlanId}`
- **Content-Type:** `application/json`
- **Description:** Replaces an existing CarePlan resource. PUT is a full replace; any field absent from the payload is reset on the underlying task.
- **Request Body:** FHIR CarePlan resource in JSON format
- **Response:** Returns the updated CarePlan as JSON
- **Error Response:** 404 if no CarePlan exists with the given UUID; 400 for malformed payloads or invalid references

**Get CarePlans by Patient**
- **Endpoint:** `GET /ws/rest/v1/tasks/careplan?subject=Patient/{patientId}`
- **Description:** Retrieves all CarePlans for a specific patient
- **Query Parameters:**
  - `subject` (required): Patient reference in format `Patient/{patientId}`
- **Response:** Returns a FHIR Bundle containing CarePlan resources
- **Error Response:** Returns a JSON error object on error

**Note:** All endpoints require authentication as per OpenMRS security configuration. The base URL for endpoints is relative to your OpenMRS server installation (e.g., `http://localhost:8080/openmrs`).

Building from Source
--------------------
You will need to have Java 8+ and Maven 3.x+ installed.  Use the command 'mvn package' to 
compile and package the module.  The .omod file will be in the omod/target folder.

Alternatively you can add the snippet provided in the [Creating Modules](https://wiki.openmrs.org/x/cAEr) page to your 
omod/pom.xml and use the mvn command:

    mvn package -P deploy-web -D deploy.path="../../openmrs-1.8.x/webapp/src/main/webapp"

It will allow you to deploy any changes to your web 
resources such as jsp or js files without re-installing the module. The deploy path says 
where OpenMRS is deployed.

Running Spotless
----------------
This project uses Spotless for code formatting. Spotless is embedded in the build process, so when you run `mvn clean package`, Spotless will automatically format your code according to the project's style guidelines.

If you want to run Spotless separately, you can use the following Maven commands:

To apply the formatting:

    mvn spotless:apply

This will automatically format your code according to the project's style guidelines. It's recommended to run this command before committing your changes.

To check if your code adheres to the style guidelines without making any changes, you can run:

    mvn spotless:check

If this command reports any violations, you can then run `mvn spotless:apply` to fix them.

Remember, in most cases, you don't need to run these commands separately as Spotless will run automatically during the build process with `mvn clean package`.

Installation
------------
1. Build the module to produce the .omod file.
2. Use the OpenMRS Administration > Manage Modules screen to upload and install the .omod file.

If uploads are not allowed from the web (changable via a runtime property), you can drop the omod
into the ~/.OpenMRS/modules folder.  (Where ~/.OpenMRS is assumed to be the Application 
Data Directory that the running openmrs is currently using.)  After putting the file in there 
simply restart OpenMRS/tomcat and the module will be loaded and started.

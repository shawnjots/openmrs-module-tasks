${moduleName}
==========================

Description
-----------
This is a very basic module which can be used as a starting point in creating a new module.

Module Structure Reference (openmrs-module-idgen)
--------------------------------------------------
The [openmrs-module-idgen](https://github.com/openmrs/openmrs-module-idgen/tree/master) module provides a good example of how to structure an OpenMRS module that exposes an API and creates custom database tables. This module does 2 of the 3 kinds of things this module is supposed to do: exposes an API and creates tables in the database. Below are notes on how it's structured.

### API Structure

The API code lives in the `api` directory of the module. The structure follows OpenMRS conventions with a layered architecture:

**Directory Structure:**
- `api/src/main/java/org/openmrs/module/[module-name]/api/` - Contains service interfaces (e.g., `IdentifierSourceService.java`)
- `api/src/main/java/org/openmrs/module/[module-name]/api/impl/` - Contains service implementations (e.g., `IdentifierSourceServiceImpl.java`)
- `api/src/main/java/org/openmrs/module/[module-name]/api/dao/` - Contains data access objects (DAOs) for database operations

**How the API Works:**

1. **Service Interface Layer:**
   - Defines interfaces that extend `OpenmrsService`
   - Methods are annotated with `@Authorized` for security and `@Transactional` for transaction management
   - Example: `IdentifierSourceService` interface defines methods like `saveIdentifierSource()`, `getIdentifierSource()` etc.

2. **Service Implementation Layer:**
   - Implements service interfaces, typically extending `BaseOpenmrsService`
   - Contains business logic and delegates database operations to DAOs
   - Example: `IdentifierSourceServiceImpl` implements `IdentifierSourceService`

3. **Data Access Layer (DAO):**
   - Handles direct database interactions using Hibernate
   - Uses `DbSessionFactory` and `DbSession` from OpenMRS to perform CRUD operations
   - Annotated with `@Repository` and uses `@Autowired` for dependency injection

4. **Spring Configuration:**
   - `api/src/main/resources/moduleApplicationContext.xml` wires everything together:
     - Creates service beans with transaction management using `TransactionProxyFactoryBean`
     - Wraps service methods with DB transactions and OpenMRS interceptors (for audit info like dateCreated, changedBy)
     - Registers services with OpenMRS context using `serviceContext` bean
   - This makes services accessible via `Context.getService(ServiceInterface.class)`

**Key Components:**
- Service interfaces define the contract
- Service implementations contain business logic
- DAOs handle database operations
- Spring XML configuration wires everything together and integrates with OpenMRS

### Custom Database Tables

Custom database tables are created and managed using **Liquibase**, a database version control tool.

**Location:**
- Liquibase scripts are typically located in `omod/src/main/resources/liquibase.xml` (or sometimes in `api/src/main/resources/liquibase.xml` depending on module structure)
- The `liquibase.xml` file contains changesets that define database schema changes

**How It Works:**

1. **Changesets:**
   - Each changeset has a unique ID (typically date-based, e.g., `idgen-2016-08-02-12-21`)
   - Contains a comment describing what the changeset does
   - Uses preconditions (like `preConditions onFail="MARK_RAN"`) to check if tables already exist before creating them

2. **Table Creation:**
   - Uses `<createTable>` element to define table structure
   - Defines columns with types, constraints (primary key, nullable, unique)
   - Can use auto-increment for primary keys
   - Adds foreign key constraints using `<addForeignKeyConstraint>`

3. **Example Structure:**
   ```xml
   <changeSet id="module-2016-08-02-12-21" author="developer">
       <preConditions onFail="MARK_RAN">
           <not><tableExists tableName="module_table"/></not>
       </preConditions>
       <comment>Creating the module_table table</comment>
       <createTable tableName="module_table">
           <column name="id" type="int" autoIncrement="true">
               <constraints primaryKey="true" nullable="false"/>
           </column>
           <column name="uuid" type="char(38)">
               <constraints nullable="false" unique="true"/>
           </column>
           <!-- additional columns -->
       </createTable>
       <addForeignKeyConstraint ... />
   </changeSet>
   ```

**Integration:**
- When the module is installed/started, OpenMRS automatically processes the `liquibase.xml` file
- Changesets are executed in order, with preconditions ensuring tables aren't created twice
- Hibernate mappings (if used) correspond to these Liquibase-defined tables

Building from Source
--------------------
You will need to have Java 21+ and Maven 2.x+ installed.  Use the command 'mvn package' to 
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

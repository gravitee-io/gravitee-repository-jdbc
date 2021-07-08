# ⚠️ DEPRECATED

This repository is no longer active, all the sources have been moved to a [new monorepo](https://github.com/gravitee-io/gravitee-api-management/tree/master/gravitee-apim-repository/gravitee-apim-repository-jdbc).

The new repository will be become the single GitHub repository for everything related to Gravitee.io API Management.

## Gravitee JDBC Repository

JDBC repository implementation that supports MySQL, MariaDB and PostgreSQL.

### Requirements

The minimum requirements are:
 * Maven3 
 * Jdk8

To use Gravitee.io snapshots, you need to declare the following repository in your maven settings:
`https://oss.sonatype.org/content/repositories/snapshots`

### Building

```shell
git clone https://github.com/gravitee-io/gravitee-repository-jdbc.git
cd gravitee-repository-jdbc
mvn clean package
```

### Testing

By default, unit tests are run with en embedded PostgreSQL, but sometimes it can be useful to run them against another database.
To do so, TestContainer has been set up, and you can use the following commands: 
 - MariaDB: `mvn clean install -DjdbcType=mariadb-tc`
 - MySQL: `mvn clean install -DjdbcType=mysql-tc`
 - PostgreSQL: `mvn clean install -DjdbcType=postgresql-tc`
 - SQLServer: `mvn clean install -DjdbcType=sqlserver-tc`

You can also run tests against other embedded databases:
- MariaDB: `mvn clean install -DjdbcType=mariadb-te`
- MySQL: `mvn clean install -DjdbcType=mysql-te`

### Installing

Unzip the gravitee-repository-jdbc-x.y.z-SNAPSHOT.zip in the gravitee home directory.

### Configuration

repository.jdbc options : 

| Parameter                                        |   default  |
| ------------------------------------------------ | ---------: |
| ....                                             |  ......... |

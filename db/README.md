# Database

SQL schema files for WebLager.

## Files

- `schema.sql` - Core tables: users, profiles, boxes, documents, files, logs.

## Setup

Before running the app, create the database and apply the schema:

```sql
CREATE DATABASE weblager;
USE weblager;
SOURCE schema.sql;
```

Or from CLI:

```bash
mysql -u weblager_user -p weblager < db/schema.sql
```

Then copy `src/main/resources/config.properties.template` to `src/main/resources/config.properties` and update credentials.


# SwiftDoc

A JavaFX desktop application for managing document scanning workflows. Operators scan physical documents via a REST API, pages are stored as TIFFs in a SQL Server database, and completed boxes are exported as multi-page TIFF files.

## Features

- **Scan sessions** — start a named box, pull TIFF pages from the scan API, auto-split documents on barcode detection
- **Sidebar tree** — live view of Boxes → Documents → Files; supports drag-and-drop reordering and cross-document moves
- **Document merging** — drag one document onto another to merge their pages
- **Per-page controls** — rotate (90° / custom angle), brightness adjustment, black & white conversion
- **Export** — export a box as multi-page TIFFs with profile-defined image processing applied
- **Scanning profiles** — per-client settings (split rule, rotation, brightness, B&W, duplicate detection)
- **Role-based access** — `ADMIN` manages users, clients, and profiles; `USER` scans and exports boxes assigned to their profiles
- **Audit log** — every file creation and deletion is recorded with user and timestamp

## Tech Stack

| Layer | Technology |
|---|---|
| UI | JavaFX 21 (FXML + CSS) |
| Icons | Ikonli / FontAwesome 5 |
| Database | Microsoft SQL Server (JDBC 12.6) |
| TIFF I/O | TwelveMonkeys ImageIO |
| Barcode | ZXing (CODE128 detection) |
| Build | Maven 3 (wrapper included) |
| Java | 21 |

## Project Structure

```
src/main/java/dk/easv/swiftdoc/
├── app/          # JavaFX entry point (HelloApplication, Launcher)
├── controller/   # FXML controllers
├── dal/          # DAOs, ScanApiClient, TIFF utilities, BarcodeDetector
├── db/           # DBConnection singleton, DatabaseMigrator
├── model/        # Domain models (Box, Document, File, User, …)
└── service/      # Business logic (ScanService, ExportService, AuthService, …)

src/main/resources/dk/easv/swiftdoc/
├── view/         # FXML layouts + app.css
└── config.properties.template
```

## Setup

### 1. Database

Create a SQL Server database and apply the schema:

```sql
CREATE DATABASE WebLagerDB;
```

Then run the schema file:

```bash
sqlcmd -S localhost -U sa -P your_password -d WebLagerDB -i db/schema_sqlserver.sql
```

The app also auto-migrates schema changes on first launch via `DatabaseMigrator`, so manual re-runs after updates are not required.

### 2. Configuration

Copy the template and fill in your SQL Server credentials:

```bash
cp src/main/resources/config.properties.template \
   src/main/resources/config.properties
```

Edit `config.properties`:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=WebLagerDB;encrypt=true;trustServerCertificate=true;
db.username=sa
db.password=your_password
```

### 3. Run

```bash
./mvnw clean javafx:run
```

Default credentials created on first boot: `admin / admin` and `user / user`.

## Data Model

```
Client
└── ScanningProfile   (split rule, rotation, brightness, B&W, duplicate detection)
    └── Box           (named by operator at session start)
        └── Document  (split on barcode; status: NEW → READY → EXPORTED)
            └── File  (single TIFF page, ordered by IncrementalId)
```

## Key Flows

**Scanning**
1. Operator opens *New Scan*, selects a profile, and names the box.
2. Each *Scan* press fetches a batch from the API, unzips it, and processes each TIFF.
3. A CODE128 barcode page triggers a document split; plain pages are appended to the current document.
4. *Finish Session* closes the box and moves it to history view.

**Export**
1. Right-click a box → *Export*.
2. `ExportService` loads all documents and files, applies profile image processing (rotation, brightness, B&W), and writes one multi-page TIFF per document.

**Admin panel**
- Manage clients and scanning profiles.
- Assign profiles to users (controls which boxes each user can see and scan).
- Create / deactivate user accounts.

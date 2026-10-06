# Hospital Management System — Java Swing + MySQL (JDBC)

A desktop hospital management application. No web technology is used anywhere:
plain Java, Swing for the UI, JDBC for the database.

Architecture: **UI → Service → DAO → MySQL**

---

## 1. Project structure

```
HospitalManagementSystem/
├── database/hospital.sql          MySQL schema + demo data
├── src/
│   ├── main/Main.java             entry point
│   ├── ui/                        all Swing screens
│   │   ├── LoginFrame.java        login window
│   │   ├── DashboardFrame.java    sidebar + header + content area
│   │   ├── DashboardPanel.java    statistic cards and live tables
│   │   ├── CrudPanel.java         shared base class for every data module
│   │   ├── PatientPanel.java  DoctorPanel.java  AppointmentPanel.java
│   │   ├── MedicalRecordPanel.java  BillingPanel.java  PharmacyPanel.java
│   │   ├── LaboratoryPanel.java  StaffPanel.java  ReportsPanel.java
│   │   ├── SettingsPanel.java   Refreshable.java
│   ├── model/                     Patient, Doctor, Appointment, MedicalRecord,
│   │                              Bill, Medicine, LaboratoryTest, Staff,
│   │                              Entity (interface), TableData
│   ├── dao/                       BaseDAO + PatientDAO, DoctorDAO, AppointmentDAO,
│   │                              MedicalRecordDAO, BillingDAO, MedicineDAO,
│   │                              LaboratoryDAO, StaffDAO, UserDAO, ReportDAO
│   ├── database/DBConnection.java single JDBC connection point
│   ├── service/                   ClassificationService, BillingService, SearchService
│   └── utils/                     UIUtils (theme + widgets), Field, Icons
└── README.md
```

---

## 2. Setup

**Step 1 — create the database**

```bash
mysql -u root -p < database/hospital.sql
```

**Step 2 — set your MySQL password**

Open `src/database/DBConnection.java` and edit:

```java
private static final String USER = "root";
private static final String PASS = "root";   // <-- your MySQL password
```

**Step 3 — get the JDBC driver**

Download `mysql-connector-j-9.x.x.jar` (MySQL Connector/J) and keep it next to the
project, e.g. in a `lib/` folder.

**Step 4 — compile and run**

Linux / macOS:
```bash
mkdir -p out
javac -d out $(find src -name "*.java")
java -cp out:lib/mysql-connector-j-9.1.0.jar main.Main
```

Windows (Command Prompt):
```bat
mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
java -cp out;lib\mysql-connector-j-9.1.0.jar main.Main
```

In NetBeans / Eclipse / IntelliJ: create a Java project, set `src` as the source
folder, add the connector jar to the project libraries, run `main.Main`.

**Login:** `admin` / `admin123` (also `reception` / `recep123`)

---

## 3. What each part does

| Layer | Responsibility |
|---|---|
| `ui` | Swing screens only. No SQL is ever written inside a button handler. |
| `service` | Business rules: patient classification, bill calculation, global search. |
| `dao` | All JDBC: `Connection`, `PreparedStatement`, `ResultSet`, exception handling. |
| `model` | Plain objects carrying one record each. |
| `database` | The only class that opens a connection. |
| `utils` | Theme colours, fonts, reusable buttons/fields/cards/tables, validation. |

### How the code stays short

`CrudPanel<T>` implements the whole pattern once — form layout, validation,
JTable loading, and the Add / Update / Delete / Search / View / Clear / Refresh
buttons. A module like `StaffPanel` then only declares its fields, its DAO and
how to build its model object, which is about 40 lines.

`BaseDAO<T>` does the same for the database: insert, update, delete, findAll,
findById, case-insensitive search and next-id generation are written once and
every DAO inherits them.

---

## 4. Rule-based patient classification (for the viva)

File: `src/service/ClassificationService.java`

This is **not** artificial intelligence. It is a plain scoring system made of
`if` conditions with fixed weights:

| Rule | Points |
|---|---|
| Critical symptom keyword (chest pain, stroke, bleeding, accident…) | +50 |
| Serious condition keyword (fracture, high fever, pneumonia…) | +25 |
| Routine / check-up keyword | −15 |
| Age under 5 or over 65 | +15 |
| Age 5–12 or 60–65 | +8 |
| Registered as an emergency case | +50 |

Final score → band:

| Score | Priority | Action |
|---|---|---|
| ≥ 50 | Emergency | Immediate attention |
| ≥ 30 | High Priority | Urgent attention |
| ≥ 12 | Medium Priority | Normal medical attention |
| < 12 | Low Priority | Routine attention |

A priority chosen manually by a doctor always overrides the computed value.
Press **Classify Patient** on the Patients screen: a dialog lists every rule
that fired, the score, the resulting band and the recommended action. The
priority is also shown on the dashboard in the *Patient Priority* table and in
the *Emergency Cases* card.

---

## 5. Billing calculation

File: `src/service/BillingService.java`

```
subtotal = consultation + room + medicine + laboratory + other
discount = subtotal × discount% ÷ 100
total    = subtotal − discount
```

The total is always recalculated from the charges before saving, so an edited
total in the form can never produce a wrong bill. **Generate Bill** shows the
full breakdown, **Print Bill** sends the receipt to a printer.

---

## 6. Validation rules

Handled centrally in `UIUtils.validate()`:

* required fields cannot be blank
* phone and emergency contact must be exactly 10 digits
* email must look like `name@domain.com`
* dates must be real dates in `yyyy-MM-dd`
* times must be `HH:mm` (24 hour)
* amounts must be positive (negatives are rejected)
* age must be 0–120
* duplicate primary key gives a clear "already exists" message
* every database error is caught and shown as a readable dialog

---

## 7. Buttons

Every button performs a real operation — none are decorative.

* Sidebar items switch the content area; **Logout** returns to the login window.
* Dashboard statistic cards are clickable and open the matching module; the
  Quick Actions card opens modules too.
* Header: global case-insensitive search across patients, doctors, appointments
  and bills; the bell shows live notifications (today's appointments, emergency
  patients, low stock, unpaid bills, expired medicines).
* Modules: Add, Update, Delete, View, Clear, Refresh, Search, plus
  module-specific ones — Emergency List, Available Only, Cancel Appointment,
  Mark Completed, Today Only, Generate Bill, Print Bill, Pending Bills,
  Low Stock, Expired Medicines, View Result, Pending Tests.
* Reports: Generate, Print, Export CSV with Today / This Week / This Month /
  Custom date filters.

---

## 8. Notes

* Passwords are stored in plain text to keep the demo readable. For a real
  system, hash them (for example with SHA-256 + salt) before storing.
* Dates are stored as `VARCHAR(15)` in `yyyy-MM-dd` so the app stays portable
  across MySQL versions and date formats; switching the columns to `DATE` works
  without changing the Java code.

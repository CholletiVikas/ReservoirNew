# Quick Start Guide

## 🚀 Getting Started in 5 Minutes

### Step 1: Get Your Google Credentials (2 minutes)

1. Go to: https://console.cloud.google.com/
2. Create a new project (or use existing one)
3. Search for "Google Sheets API" and enable it
4. Go to "Credentials" → Create Service Account
5. Create a key (JSON) and download it
6. Copy the downloaded file to your project root as `credentials.json`

### Step 2: Configure Your Spreadsheet (1 minute)

1. Create a new Google Sheet at: https://sheets.google.com
2. Share it with the service account email (found in your JSON file)
3. Copy the spreadsheet ID from the URL (the long string between `/d/` and `/edit`)

### Step 3: Update Configuration (1 minute)

Edit `src/main/resources/application.properties`:

```properties
google.sheets.spreadsheet.id=YOUR_SPREADSHEET_ID_HERE
google.sheets.credentials.path=credentials.json
```

### Step 4: Run the Application (1 minute)

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run
```

The application will start at: **http://localhost:8080**

---

## 📱 API Usage

### Get Reservoirs
```bash
curl http://localhost:8080/api/reservoirs
```

### Submit Reservoir Data
```bash
curl -X POST http://localhost:8080/api/submit \
  -H "Content-Type: application/json" \
  -d '{
    "reservoirName": "Ashwaraopally",
    "waterLevel": "45.5",
    "inflow": "100.2",
    "outflow": "85.3"
  }'
```

---

## ⚠️ Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| `Port 8080 already in use` | Change port in `application.properties`: `server.port=8081` |
| `Credentials file not found` | Ensure `credentials.json` is in project root with correct path |
| `Access Denied` error | Share the Google Sheet with service account email |
| `Sheets API not enabled` | Enable it in Google Cloud Console |
| Build fails | Run `mvn clean` then `mvn install` |

---

## 📁 Project Files

```
ReservoirNew/
├── credentials.json              ← Your Google credentials (CREATE THIS)
├── credentials.json.template     ← Template for reference
├── SETUP_GUIDE.md               ← Detailed setup instructions
├── FIX_SUMMARY.md               ← Technical fix details
├── pom.xml                      ← Dependencies
├── src/
│   ├── main/
│   │   ├── java/com/reservoir/
│   │   │   ├── ReservoirApplication.java
│   │   │   ├── controller/ReservoirController.java
│   │   │   ├── service/GoogleSheetsService.java
│   │   │   └── model/ReservoirSubmission.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── templates/index.html
│   │       └── static/
│   │           ├── css/style.css
│   │           └── js/script.js
│   └── test/
└── target/
    └── reservoir-submit-1.0.0.jar
```

---

## 🔧 Using Environment Variables (Advanced)

Instead of `credentials.json`, you can use environment variables:

```bash
export GOOGLE_SHEETS_ID=your-spreadsheet-id
export GOOGLE_CREDENTIALS_PATH=/path/to/credentials.json
mvn spring-boot:run
```

Or in production:

```bash
java -jar target/reservoir-submit-1.0.0.jar \
  --GOOGLE_SHEETS_ID=your-spreadsheet-id \
  --GOOGLE_CREDENTIALS_PATH=/etc/secrets/credentials.json
```

---

## ✅ Verification Checklist

- [ ] Google Cloud project created
- [ ] Google Sheets API enabled
- [ ] Service Account created with JSON key
- [ ] `credentials.json` placed in project root
- [ ] Google Sheet created and shared with service account
- [ ] Spreadsheet ID updated in `application.properties`
- [ ] Project builds: `mvn clean install`
- [ ] Application starts: `mvn spring-boot:run`
- [ ] Can access home page: http://localhost:8080
- [ ] Can get reservoirs: http://localhost:8080/api/reservoirs
- [ ] Can submit data: POST to http://localhost:8080/api/submit

---

## 📞 Support

For detailed troubleshooting, see:
- `SETUP_GUIDE.md` - Complete setup instructions
- `FIX_SUMMARY.md` - Technical details about fixes

---

**Status**: ✅ Application is ready to use!

# Reservoir Submission Application

A Spring Boot application for submitting reservoir data to Google Sheets.

## Prerequisites

- Java 21+
- Maven 3.8+
- Google Cloud Service Account credentials

## Configuration

### 1. Google Sheets Setup

1. Create a Google Cloud project at: https://console.cloud.google.com
2. Enable the Google Sheets API
3. Create a Service Account and download the JSON key file
4. Share your Google Sheet with the Service Account email

### 2. Application Configuration

#### Option A: Using Environment Variables (Recommended)

```bash
export GOOGLE_SHEETS_ID=your-spreadsheet-id
export GOOGLE_CREDENTIALS_PATH=/path/to/credentials.json
mvn spring-boot:run
```

#### Option B: Using application.properties

Edit `src/main/resources/application.properties`:

```properties
google.sheets.spreadsheet.id=your-spreadsheet-id
google.sheets.credentials.path=/path/to/credentials.json
```

#### Option C: Using credentials in project root

1. Copy your credentials JSON file to project root as `credentials.json`
2. Update the spreadsheet ID in `application.properties`

## Building the Project

```bash
mvn clean install
```

## Running the Application

### Development Mode
```bash
mvn spring-boot:run
```

### Production Mode
```bash
java -jar target/reservoir-submit-1.0.0.jar
```

## API Endpoints

- `GET /` - Home page
- `GET /api/reservoirs` - Get list of all reservoirs
- `POST /api/submit` - Submit reservoir data

### Submit Data Example

```json
POST /api/submit
{
  "reservoirName": "Ashwaraopally",
  "waterLevel": "45.5",
  "inflow": "100.2",
  "outflow": "85.3"
}
```

## Troubleshooting

### Issue: "Could not resolve placeholder 'google.sheets.spreadsheet.id'"

**Solution**: Ensure you have configured the environment variables or properties file with the required credentials.

### Issue: "Failed to load Google credentials"

**Solution**: 
1. Check that `credentials.json` exists at the configured path
2. Verify the credentials file has the correct permissions (readable)
3. Ensure the path is absolute or relative to the working directory

### Issue: "Access Denied" errors

**Solution**:
1. Verify the Google Sheet is shared with the Service Account email
2. Check that the Service Account has Editor permissions
3. Verify the Sheets API is enabled in Google Cloud Console

## Project Structure

```
ReservoirNew/
├── src/main/
│   ├── java/com/reservoir/
│   │   ├── ReservoirApplication.java      (Main application)
│   │   ├── controller/
│   │   │   └── ReservoirController.java   (API endpoints)
│   │   ├── service/
│   │   │   └── GoogleSheetsService.java   (Sheets API integration)
│   │   └── model/
│   │       └── ReservoirSubmission.java   (Data model)
│   └── resources/
│       ├── application.properties          (Configuration)
│       ├── templates/
│       │   └── index.html                 (UI)
│       ├── static/
│       │   ├── css/style.css
│       │   └── js/script.js
└── pom.xml                                 (Dependencies)
```

## Technologies Used

- Spring Boot 3.2.0
- Spring Web MVC
- Google Auth Library
- Jackson (JSON processing)
- Java HTTP Client (Java 11+)
- Maven

## Dependencies

- Spring Boot Web Starter
- Google Auth Library for OAuth2
- Google HTTP Client
- Jackson Databind
- Lombok (optional)

## License

MIT License

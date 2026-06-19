package com.reservoir.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GoogleSheetsService {
    
    private static final String APPLICATION_NAME = "Reservoir Submission";
    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/drive.readonly");
    private static final String SHEETS_API_BASE = "https://sheets.googleapis.com/v4/spreadsheets";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${google.sheets.spreadsheet.id}")
    private String spreadsheetId;
    
    @Value("${google.sheets.credentials.path:credentials.json}")
    private String credentialsPath;

    @Value("${google.sheets.master.template.name:Master Template}")
    private String masterTemplateName;

    private String accessToken;
    private long tokenExpiry = 0;
    
    private String getAccessToken() throws GeneralSecurityException, IOException {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken != null && now < tokenExpiry) {
            return accessToken;
        }

        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new FileInputStream(credentialsPath))
                    .createScoped(SCOPES);
            credentials.refresh();
            accessToken = credentials.getAccessToken().getTokenValue();
            tokenExpiry = now + credentials.getAccessToken().getExpirationTime().getTime() / 1000;
            return accessToken;
        } catch (IOException e) {
            throw new IOException("Failed to load Google credentials from path: " + credentialsPath + 
                    "\nPlease ensure credentials.json exists and is configured correctly. " +
                    "\nConfiguration: google.sheets.credentials.path=" + credentialsPath, e);
        }
    }
    
    public void submitReservoirData(String reservoirName, String waterLevel, String inflow, String outflow, String waterStorage)
            throws GeneralSecurityException, IOException, InterruptedException {
        LocalDate today = LocalDate.now();
        String sheetName = today.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        // Each day's tab is created as a replica of the master template.
        createSheetIfNotExists(sheetName);

        List<List<Object>> values = readSheetData(sheetName);
        int rowIndex = findReservoirRow(values, reservoirName);
        if (rowIndex == -1) {
            throw new IOException("Reservoir '" + reservoirName
                    + "' was not found in the master template sheet.");
        }

        // FRL Gross capacity in MCFT is the static value in column F (index 5).
        Double frlMcft = parseNumber(getCell(values.get(rowIndex), 5));
        Double storage = parseNumber(waterStorage);
        Double level = parseNumber(waterLevel);
        // inflow/outflow are optional and have no column in the master template,
        // so they are not written to the sheet.

        // G = Present Level (Mtrs)
        Object gVal = (level != null) ? level : (waterLevel == null ? "" : waterLevel);
        // H = Present Gross capacity (MCFT) = Water Storage input
        Object hVal = (storage != null) ? storage : (waterStorage == null ? "" : waterStorage);
        // I = Gross capacity (TMC) = H / 1000
        Object iVal = (storage != null) ? round(storage / 1000.0, 4) : "";
        // J = Percentage of Filling = (H / F) * 100
        Object jVal = (storage != null && frlMcft != null && frlMcft != 0)
                ? round(storage / frlMcft * 100.0, 2) : "";

        int sheetRow = rowIndex + 1; // sheet rows are 1-indexed
        String token = getAccessToken();
        String range = sheetName + "!G" + sheetRow + ":J" + sheetRow;
        List<List<Object>> data = new ArrayList<>();
        data.add(Arrays.asList(gVal, hVal, iVal, jVal));
        updateSheetData(range, data, token);

        // Colour the Remarks cell (column K) based on the percentage of filling.
        Double percentage = (jVal instanceof Double) ? (Double) jVal : null;
        colorRemarksCell(sheetName, rowIndex, percentage, token);
    }

    private Double parseNumber(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object getCell(List<Object> row, int index) {
        return (row != null && index < row.size()) ? row.get(index) : null;
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
    
    private void createSheetIfNotExists(String sheetName) throws GeneralSecurityException, IOException, InterruptedException {
        String token = getAccessToken();
        String url = SHEETS_API_BASE + "/" + spreadsheetId + "?fields=sheets.properties";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch spreadsheet: " + response.body());
        }

        JsonNode spreadsheet = objectMapper.readTree(response.body());
        JsonNode sheets = spreadsheet.get("sheets");

        boolean sheetExists = false;
        Integer masterSheetId = null;
        if (sheets != null) {
            for (JsonNode sheet : sheets) {
                JsonNode props = sheet.get("properties");
                String title = props.get("title").asText();
                if (title.equals(sheetName)) {
                    sheetExists = true;
                }
                if (title.trim().equalsIgnoreCase(masterTemplateName.trim())) {
                    masterSheetId = props.get("sheetId").asInt();
                }
            }
        }

        if (sheetExists) {
            return;
        }
        if (masterSheetId == null) {
            throw new IOException("Master template tab '" + masterTemplateName
                    + "' was not found in the spreadsheet. Please import the master template "
                    + "as a tab named exactly '" + masterTemplateName + "'.");
        }
        duplicateSheet(masterSheetId, sheetName, token);
        updateSheetTitleDate(sheetName, token);
    }

    /** Replaces the date shown in the title (row 1, cell A1) with today's date. */
    private void updateSheetTitleDate(String sheetName, String token)
            throws GeneralSecurityException, IOException, InterruptedException {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String a1 = readSingleCell(sheetName, "A1", token);

        String newTitle;
        if (a1 != null && a1.contains("DATED:")) {
            newTitle = a1.replaceAll("DATED:\\s*[^\\n]*", "DATED: " + today);
        } else if (a1 != null && !a1.isEmpty()) {
            newTitle = a1 + "\nDATED: " + today;
        } else {
            newTitle = "DATED: " + today;
        }

        List<List<Object>> data = new ArrayList<>();
        data.add(Collections.singletonList(newTitle));
        updateSheetData(sheetName + "!A1", data, token);
    }

    private String readSingleCell(String sheetName, String cell, String token)
            throws IOException, InterruptedException {
        String range = URLEncoder.encode(sheetName + "!" + cell, StandardCharsets.UTF_8);
        String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + range;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        JsonNode values = objectMapper.readTree(response.body()).get("values");
        if (values != null && values.isArray() && values.size() > 0
                && values.get(0).isArray() && values.get(0).size() > 0) {
            return values.get(0).get(0).asText();
        }
        return null;
    }

    private int getSheetId(String sheetName, String token) throws IOException, InterruptedException {
        String url = SHEETS_API_BASE + "/" + spreadsheetId + "?fields=sheets.properties";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch spreadsheet: " + response.body());
        }
        JsonNode sheets = objectMapper.readTree(response.body()).get("sheets");
        if (sheets != null) {
            for (JsonNode sheet : sheets) {
                JsonNode props = sheet.get("properties");
                if (props.get("title").asText().equals(sheetName)) {
                    return props.get("sheetId").asInt();
                }
            }
        }
        throw new IOException("Sheet tab '" + sheetName + "' was not found.");
    }

    /** Sets the background colour of the Remarks cell (column K) by % of filling. */
    private void colorRemarksCell(String sheetName, int rowIndex0, Double percentage, String token)
            throws IOException, InterruptedException {
        if (percentage == null) {
            return;
        }
        double red, green, blue;
        if (percentage < 50) {            // below 50% -> green
            red = 0.71; green = 0.84; blue = 0.66;
        } else if (percentage <= 85) {    // 50% to 85% -> yellow
            red = 1.0; green = 0.90; blue = 0.60;
        } else {                          // above 85% -> red
            red = 0.96; green = 0.61; blue = 0.61;
        }

        int sheetId = getSheetId(sheetName, token);

        ObjectNode color = objectMapper.createObjectNode();
        color.put("red", red);
        color.put("green", green);
        color.put("blue", blue);

        ObjectNode userEnteredFormat = objectMapper.createObjectNode();
        userEnteredFormat.set("backgroundColor", color);
        ObjectNode cell = objectMapper.createObjectNode();
        cell.set("userEnteredFormat", userEnteredFormat);

        ObjectNode gridRange = objectMapper.createObjectNode();
        gridRange.put("sheetId", sheetId);
        gridRange.put("startRowIndex", rowIndex0);
        gridRange.put("endRowIndex", rowIndex0 + 1);
        gridRange.put("startColumnIndex", 10); // column K
        gridRange.put("endColumnIndex", 11);

        ObjectNode repeatCell = objectMapper.createObjectNode();
        repeatCell.set("range", gridRange);
        repeatCell.set("cell", cell);
        repeatCell.put("fields", "userEnteredFormat.backgroundColor");

        ObjectNode request = objectMapper.createObjectNode();
        request.set("repeatCell", repeatCell);
        ArrayNode requests = objectMapper.createArrayNode();
        requests.add(request);
        ObjectNode body = objectMapper.createObjectNode();
        body.set("requests", requests);

        String url = SHEETS_API_BASE + "/" + spreadsheetId + ":batchUpdate";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to colour remarks cell: " + response.body());
        }
    }

    private void duplicateSheet(int sourceSheetId, String newSheetName, String token)
            throws IOException, InterruptedException {
        String url = SHEETS_API_BASE + "/" + spreadsheetId + ":batchUpdate";

        ObjectNode duplicate = objectMapper.createObjectNode();
        duplicate.put("sourceSheetId", sourceSheetId);
        duplicate.put("insertSheetIndex", 1);
        duplicate.put("newSheetName", newSheetName);

        ObjectNode request = objectMapper.createObjectNode();
        request.set("duplicateSheet", duplicate);

        ArrayNode requests = objectMapper.createArrayNode();
        requests.add(request);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("requests", requests);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            throw new IOException("Failed to duplicate master template: " + httpResponse.body());
        }
    }

    private List<List<Object>> readSheetData(String sheetName) throws GeneralSecurityException, IOException, InterruptedException {
        String token = getAccessToken();
        String range = URLEncoder.encode(sheetName, StandardCharsets.UTF_8);
        String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + range;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return new ArrayList<>();
        }

        JsonNode result = objectMapper.readTree(response.body());
        JsonNode values = result.get("values");

        List<List<Object>> data = new ArrayList<>();
        if (values != null && values.isArray()) {
            for (JsonNode row : values) {
                List<Object> rowData = new ArrayList<>();
                for (JsonNode cell : row) {
                    rowData.add(cell.asText());
                }
                data.add(rowData);
            }
        }
        return data;
    }
    
    private int findReservoirRow(List<List<Object>> values, String reservoirName) {
        // In the master template, the reservoir name is in column B (index 1).
        String target = reservoirName == null ? "" : reservoirName.trim();
        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.size() > 1 && row.get(1) != null
                    && row.get(1).toString().trim().equalsIgnoreCase(target)) {
                return i;
            }
        }
        return -1;
    }

    private void updateSheetData(String range, List<List<Object>> data, String token) throws IOException, InterruptedException {
        String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + URLEncoder.encode(range, StandardCharsets.UTF_8) + "?valueInputOption=RAW";

        ObjectNode body = objectMapper.createObjectNode();
        body.set("values", objectMapper.valueToTree(data));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to update sheet: " + response.body());
        }
    }
    
    /** Builds the overview for the given date: reservoirs with data vs. those with none. */
    public Map<String, Object> getOverview(LocalDate date) throws GeneralSecurityException, IOException, InterruptedException {
        String sheetName = getSheetName(date);
        String displayDate = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        List<List<Object>> values = readSheetData(sheetName);
        if (values.isEmpty()) {
            // No tab/submissions for that date -> use the master template as the reservoir list.
            values = readSheetData(masterTemplateName);
        }

        List<Map<String, String>> withData = new ArrayList<>();
        List<Map<String, String>> noData = new ArrayList<>();

        for (List<Object> row : values) {
            if (row.isEmpty()) {
                continue;
            }
            // Reservoir rows have a numeric Sl No in column A.
            if (parseNumber(getCell(row, 0)) == null) {
                continue;
            }
            String name = cellText(getCell(row, 1));   // B
            if (name.isEmpty()) {
                continue;
            }
            String level = cellText(getCell(row, 6));   // G - Present Level
            String storage = cellText(getCell(row, 7)); // H - Present Gross capacity (MCFT)

            Map<String, String> entry = new HashMap<>();
            entry.put("name", name);
            entry.put("level", level);
            entry.put("storage", storage);
            if (!level.isEmpty() || !storage.isEmpty()) {
                withData.add(entry);
            } else {
                noData.add(entry);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", displayDate);
        result.put("total", withData.size() + noData.size());
        result.put("withDataCount", withData.size());
        result.put("withData", withData);
        result.put("noData", noData);
        return result;
    }

    /** Downloads the given date's tab as an .xlsx, exactly as stored in Google Drive. */
    public byte[] downloadSheet(LocalDate date) throws GeneralSecurityException, IOException, InterruptedException {
        String sheetName = getSheetName(date);
        String token = getAccessToken();
        int gid = getSheetId(sheetName, token);

        String url = "https://docs.google.com/spreadsheets/d/" + spreadsheetId
                + "/export?format=xlsx&gid=" + gid;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("No sheet found for " + sheetName
                    + " (HTTP " + response.statusCode() + "). Data may not have been submitted on that date.");
        }
        return response.body();
    }

    public String getSheetName(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    private String cellText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public List<Map<String, String>> getReservoirList() {
        List<String> reservoirs = Arrays.asList(
                "Ashwaraopally", "Bommakur", "Cheetakodur", "Gandiramaram",
                "Inapur Tank", "Kanneboinagudem Tank", "Maa Reddy Cheruvu",
                "Mylaram Balancing Reservoir", "Nawabpet", "R.S. Ghanpur",
                "Tapaspally", "Veldanda Tank"
        );
        
        List<Map<String, String>> result = new ArrayList<>();
        for (String reservoir : reservoirs) {
            Map<String, String> map = new HashMap<>();
            map.put("name", reservoir);
            result.add(map);
        }
        return result;
    }
}

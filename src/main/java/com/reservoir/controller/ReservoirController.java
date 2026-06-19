package com.reservoir.controller;

import com.reservoir.model.ReservoirSubmission;
import com.reservoir.service.GoogleSheetsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
public class ReservoirController {

    private static final Logger log = LoggerFactory.getLogger(ReservoirController.class);

    @Autowired
    private GoogleSheetsService googleSheetsService;
    
    //@GetMapping("")
    //public String index() {
    //    return "index";
    //}
    
    @GetMapping("/api/reservoirs")
    @ResponseBody
    public ResponseEntity<List<Map<String, String>>> getReservoirs() {
        return ResponseEntity.ok(googleSheetsService.getReservoirList());
    }

    // Serves the overview page at /overview (redirects to the static HTML).
    @GetMapping("/overview")
    public String overview() {
        return "redirect:/overview.html";
    }

    @GetMapping("/api/overview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getOverview(@RequestParam(required = false) String date) {
        try {
            return ResponseEntity.ok(googleSheetsService.getOverview(parseDate(date)));
        } catch (GeneralSecurityException | IOException | InterruptedException e) {
            log.error("Failed to build overview", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error loading overview: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/api/download")
    @ResponseBody
    public ResponseEntity<byte[]> downloadToday(@RequestParam(required = false) String date) {
        try {
            LocalDate d = parseDate(date);
            byte[] file = googleSheetsService.downloadSheet(d);
            String fileName = googleSheetsService.getSheetName(d) + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(
                    org.springframework.http.ContentDisposition.attachment().filename(fileName).build());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(file);
        } catch (GeneralSecurityException | IOException | InterruptedException e) {
            log.error("Failed to download today's sheet", e);
            return ResponseEntity.badRequest()
                    .body(("Error downloading sheet: " + e.getMessage()).getBytes());
        }
    }
    
    @PostMapping("/api/submit")
    @ResponseBody
    public ResponseEntity<Map<String, String>> submitReservoir(@RequestBody ReservoirSubmission submission) {
        try {
            googleSheetsService.submitReservoirData(
                    submission.getReservoirName(),
                    submission.getWaterLevel(),
                    submission.getInflow(),
                    submission.getOutflow(),
                    submission.getWaterStorage()
            );

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Reservoir data submitted successfully");
            return ResponseEntity.ok(response);
        } catch (GeneralSecurityException | IOException | InterruptedException e) {
            log.error("Failed to submit reservoir data for '{}'", submission.getReservoirName(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error submitting data: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** Parses an ISO date (yyyy-MM-dd) from the date picker; defaults to today. */
    private LocalDate parseDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}

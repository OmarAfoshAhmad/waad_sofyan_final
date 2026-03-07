package com.waad.tba.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Business Days Calculator Service.
 * 
 * Calculates business days (excluding weekends and public holidays) for SLA tracking.
 * 
 * Weekends in Kuwait: Friday, Saturday
 * Public Holidays: Configurable list (can be moved to database in future)
 * 
 * @since Phase 1 - Financial Gaps Closure (SLA Implementation)
 */
@Slf4j
@Service
public class BusinessDaysCalculatorService {
    
    /**
     * Libya Public Holidays for 2026.
     * 
     * Future enhancement: SystemHolidays entity with year, date, name, active flag.
     */
    private static final List<LocalDate> PUBLIC_HOLIDAYS_2026 = List.of(
        LocalDate.of(2026, 2, 17),  // Revolution Day (February 17)
        LocalDate.of(2026, 4, 2),   // Isra and Mi'raj (estimated, varies by Islamic calendar)
        LocalDate.of(2026, 5, 1),   // Eid al-Fitr (estimated, 1st day)
        LocalDate.of(2026, 5, 2),   // Eid al-Fitr (estimated, 2nd day)
        LocalDate.of(2026, 5, 3),   // Eid al-Fitr (estimated, 3rd day)
        LocalDate.of(2026, 7, 9),   // Arafat Day (estimated)
        LocalDate.of(2026, 7, 10),  // Eid al-Adha (estimated, 1st day)
        LocalDate.of(2026, 7, 11),  // Eid al-Adha (estimated, 2nd day)
        LocalDate.of(2026, 7, 12),  // Eid al-Adha (estimated, 3rd day)
        LocalDate.of(2026, 7, 31),  // Islamic New Year (estimated)
        LocalDate.of(2026, 10, 9),  // Prophet's Birthday (estimated)
        LocalDate.of(2026, 10, 23), // Liberation Day
        LocalDate.of(2026, 12, 24)  // Independence Day
    );
    
    /**
     * Calculate the number of business days between two dates (inclusive of start, exclusive of end).
     * 
     * Business days exclude:
     * - Weekends (Friday, Saturday)
     * - Public holidays
     * 
     * @param start Start date (inclusive)
     * @param end End date (exclusive)
     * @return Number of business days between the dates
     * 
     * Example:
     * start = 2026-01-12 (Sunday)
     * end = 2026-01-28 (Wednesday)
     * Result = 10 business days (excluding weekends and holidays)
     */
    public int calculateBusinessDays(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            log.warn("⚠️ calculateBusinessDays called with null dates: start={}, end={}", start, end);
            return 0;
        }
        
        if (start.isAfter(end)) {
            log.warn("⚠️ calculateBusinessDays: start date {} is after end date {}", start, end);
            return 0;
        }
        
        int businessDays = 0;
        LocalDate current = start;
        
        while (current.isBefore(end)) {
            if (isBusinessDay(current)) {
                businessDays++;
            }
            current = current.plusDays(1);
        }
        
        log.debug("📅 Calculated {} business days between {} and {}", businessDays, start, end);
        return businessDays;
    }
    
    /**
     * Add N business days to a start date.
     * 
     * Skips weekends and public holidays while counting.
     * 
     * @param start Start date
     * @param daysToAdd Number of business days to add
     * @return Resulting date after adding business days
     * 
     * Example:
     * start = 2026-01-12 (Sunday)
     * daysToAdd = 10
     * Result = 2026-01-28 (Wednesday)
     * 
     * Calculation:
     * - Jan 12 (Sun) → Jan 13 (Mon): day 1
     * - Jan 13 (Mon) → Jan 14 (Tue): day 2
     * - Jan 14 (Tue) → Jan 15 (Wed): day 3
     * - Jan 15 (Wed) → Jan 16 (Thu): day 4
     * - Jan 16 (Thu) → Jan 17 (Fri): SKIP (Friday)
     * - Jan 17 (Fri) → Jan 18 (Sat): SKIP (Saturday)
     * - Jan 18 (Sat) → Jan 19 (Sun): day 5
     * - ... continue until 10 business days
     */
    public LocalDate addBusinessDays(LocalDate start, int daysToAdd) {
        if (start == null) {
            log.warn("⚠️ addBusinessDays called with null start date");
            return null;
        }
        
        if (daysToAdd < 0) {
            log.warn("⚠️ addBusinessDays called with negative days: {}", daysToAdd);
            return start;
        }
        
        if (daysToAdd == 0) {
            return start;
        }
        
        LocalDate result = start;
        int addedDays = 0;
        
        while (addedDays < daysToAdd) {
            result = result.plusDays(1);
            
            if (isBusinessDay(result)) {
                addedDays++;
            }
        }
        
        log.debug("📅 Added {} business days to {}: result = {}", daysToAdd, start, result);
        return result;
    }
    
    /**
     * Check if a date is a business day (not weekend, not holiday).
     * 
     * @param date Date to check
     * @return true if business day, false if weekend or holiday
     */
    public boolean isBusinessDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        
        // Libya weekend: Friday only
        if (dayOfWeek == DayOfWeek.FRIDAY) {
            return false;
        }
        
        // Check if public holiday
        if (isPublicHoliday(date)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if a date is a public holiday.
     * 
     * @param date Date to check
     * @return true if public holiday, false otherwise
     */
    public boolean isPublicHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        
        // For now, only check 2026 holidays
        if (date.getYear() == 2026) {
            return PUBLIC_HOLIDAYS_2026.contains(date);
        }
        
        // For other years, no holidays configured yet
        log.debug("⚠️ No public holidays configured for year {}, treating as regular day", date.getYear());
        return false;
    }
    
    /**
     * Get all public holidays for a specific year.
     * 
     * @param year Year to get holidays for
     * @return List of public holidays
     */
    public List<LocalDate> getPublicHolidays(int year) {
        if (year == 2026) {
            return new ArrayList<>(PUBLIC_HOLIDAYS_2026);
        }
        
        // No holidays configured for other years
        log.warn("⚠️ No public holidays configured for year {}", year);
        return new ArrayList<>();
    }
    
    /**
     * Calculate the expected completion date based on SLA days.
     * 
     * This is a convenience method that adds SLA business days to today's date.
     * 
     * @param submissionDate The date the claim was submitted
     * @param slaDays Number of business days SLA (e.g., 10)
     * @return Expected completion date
     * 
     * Example:
     * submissionDate = 2026-01-12
     * slaDays = 10
     * Result = 2026-01-28
     */
    public LocalDate calculateExpectedCompletionDate(LocalDate submissionDate, int slaDays) {
        if (submissionDate == null) {
            log.warn("⚠️ calculateExpectedCompletionDate called with null submission date");
            return null;
        }
        
        LocalDate expectedDate = addBusinessDays(submissionDate, slaDays);
        
        log.info("📅 Claim submitted on {}, SLA = {} days → Expected completion: {}", 
            submissionDate, slaDays, expectedDate);
        
        return expectedDate;
    }
    
    /**
     * Check if a claim is within SLA.
     * 
     * @param submissionDate Date claim was submitted
     * @param completionDate Date claim was completed (approved/rejected)
     * @param slaDays Maximum allowed business days
     * @return true if within SLA, false if exceeded
     */
    public boolean isWithinSla(LocalDate submissionDate, LocalDate completionDate, int slaDays) {
        if (submissionDate == null || completionDate == null) {
            log.warn("⚠️ isWithinSla called with null dates");
            return false;
        }
        
        int daysTaken = calculateBusinessDays(submissionDate, completionDate);
        boolean withinSla = daysTaken <= slaDays;
        
        log.debug("📊 SLA check: {} days taken vs {} SLA days → {}", 
            daysTaken, slaDays, withinSla ? "PASS ✅" : "FAIL ❌");
        
        return withinSla;
    }
}

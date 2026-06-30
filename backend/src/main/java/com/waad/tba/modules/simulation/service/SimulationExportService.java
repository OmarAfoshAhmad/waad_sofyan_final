package com.waad.tba.modules.simulation.service;

import com.waad.tba.modules.simulation.entity.CoverageSimulationItem;
import com.waad.tba.modules.simulation.entity.CoverageSimulationRun;
import com.waad.tba.modules.simulation.repository.CoverageSimulationItemRepository;
import com.waad.tba.modules.simulation.repository.CoverageSimulationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationExportService {

    private final CoverageSimulationRunRepository runRepository;
    private final CoverageSimulationItemRepository itemRepository;

    public byte[] exportSimulationToExcel(String simulationId) {
        CoverageSimulationRun run = runRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation snapshot not found"));

        List<CoverageSimulationItem> items = itemRepository.findBySimulationRunId(simulationId);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Simulation Results");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] columns = {
                    "رقم الخدمة", "اسم الخدمة", "كود الخدمة", "التصنيف الأصلي", "سعر الخدمة",
                    "كود التغطية", "حالة التغطية", "سبب التغطية", "تحذيرات",
                    "نسبة التغطية", "مساهمة المريض", "حصة الشركة", "مطلوب موافقة مسبقة", "الإجراء الموصى به"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (CoverageSimulationItem item : items) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getProviderServiceId() != null ? item.getProviderServiceId() : 0);
                row.createCell(1).setCellValue(item.getServiceName());
                row.createCell(2).setCellValue(item.getServiceCode());
                row.createCell(3).setCellValue(item.getSourceMainCategory());
                row.createCell(4).setCellValue(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
                row.createCell(5).setCellValue(item.getCategoryCode());
                row.createCell(6).setCellValue(item.getCoverageStatus());
                row.createCell(7).setCellValue(item.getCoverageReason());
                row.createCell(8).setCellValue(item.getWarningsJson());
                row.createCell(9).setCellValue(item.getCoveragePercent() != null ? item.getCoveragePercent() + "%" : "");
                row.createCell(10).setCellValue(item.getPatientShare() != null ? item.getPatientShare().doubleValue() : 0.0);
                row.createCell(11).setCellValue(item.getCompanyShare() != null ? item.getCompanyShare().doubleValue() : 0.0);
                row.createCell(12).setCellValue(item.isRequiresPreApproval() ? "نعم" : "لا");
                row.createCell(13).setCellValue(item.getRecommendedAction());
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Failed to export Excel for simulation {}", simulationId, e);
            throw new RuntimeException("Failed to generate Excel file");
        }
    }
}

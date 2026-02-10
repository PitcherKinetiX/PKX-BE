package com.pkx.domain.analysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pkx.common.exception.BusinessException;
import com.pkx.common.exception.ErrorCode;
import com.pkx.domain.analysis.entity.Analysis;
import com.pkx.domain.analysis.entity.AnalysisResult;
import com.pkx.domain.analysis.entity.JointMetrics;
import com.pkx.domain.analysis.repository.AnalysisRepository;
import com.pkx.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating PDF reports of analysis results.
 * Uses Apache PDFBox to create comprehensive analysis reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private final AnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // PDF Layout Constants
    private static final float MARGIN = 50;
    private static final float TITLE_FONT_SIZE = 24;
    private static final float HEADING_FONT_SIZE = 16;
    private static final float SUBHEADING_FONT_SIZE = 14;
    private static final float BODY_FONT_SIZE = 12;
    private static final float LINE_HEIGHT = 15;
    private static final float SECTION_SPACING = 25;

    /**
     * Generate PDF report for an analysis.
     *
     * @param analysisId The analysis ID
     * @param user The requesting user
     * @return PDF file as byte array
     */
    @Transactional(readOnly = true)
    public byte[] generateReport(Long analysisId, User user) {
        log.info("Generating PDF report for analysisId: {}", analysisId);

        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis not found"));

        // Verify ownership
        if (!analysis.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS, "Access denied");
        }

        // Check if analysis is completed
        if (analysis.getStatus() != Analysis.AnalysisStatus.COMPLETED || analysis.getResult() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Analysis is not completed yet");
        }

        try {
            return createPdfDocument(analysis);
        } catch (IOException e) {
            log.error("Failed to generate PDF report", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate PDF report", e);
        }
    }

    /**
     * Create PDF document with analysis results.
     */
    private byte[] createPdfDocument(Analysis analysis) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            AnalysisResult result = analysis.getResult();
            JointMetrics metrics = result.getJointMetrics();

            // Create first page
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = page.getMediaBox().getHeight() - MARGIN;

                // Header: Title
                yPosition = drawTitle(contentStream, "Pitcher KinetiX", yPosition);
                yPosition -= SECTION_SPACING;

                yPosition = drawSubheading(contentStream, "Pitching Mechanics Analysis Report", yPosition);
                yPosition -= SECTION_SPACING;

                // Analysis Information
                yPosition = drawSectionHeading(contentStream, "Analysis Information", yPosition);
                yPosition -= LINE_HEIGHT;

                yPosition = drawText(contentStream, "Analysis Date: " +
                        analysis.getCompletedAt().format(DATE_FORMATTER), yPosition, BODY_FONT_SIZE);
                yPosition = drawText(contentStream, "Video File: " + analysis.getVideoFilename(), yPosition, BODY_FONT_SIZE);
                yPosition = drawText(contentStream, "Duration: " +
                        String.format("%.1f seconds", analysis.getVideoDurationSeconds()), yPosition, BODY_FONT_SIZE);
                yPosition -= SECTION_SPACING;

                // Risk Assessment
                yPosition = drawSectionHeading(contentStream, "Risk Assessment", yPosition);
                yPosition -= LINE_HEIGHT;

                String riskGrade = result.getRiskGrade().name();
                String riskColor = getRiskGradeDescription(result.getRiskGrade());
                yPosition = drawText(contentStream, "Risk Grade: " + riskGrade + " (" + riskColor + ")",
                        yPosition, SUBHEADING_FONT_SIZE);
                yPosition -= SECTION_SPACING / 2;

                // Core Metrics
                yPosition = drawText(contentStream, "Core Performance Metrics:", yPosition, BODY_FONT_SIZE);
                yPosition = drawText(contentStream, "  • Overall Risk Score: " +
                        result.getOverallRiskScore() + "/100", yPosition, BODY_FONT_SIZE);
                yPosition = drawText(contentStream, "  • Consistency Score: " +
                        result.getConsistencyScore() + "/100", yPosition, BODY_FONT_SIZE);
                yPosition = drawText(contentStream, "  • Medical Risk Score: " +
                        result.getMedicalRiskScore() + "/100", yPosition, BODY_FONT_SIZE);
                yPosition -= SECTION_SPACING;

                // Risk Summary
                yPosition = drawSectionHeading(contentStream, "Summary", yPosition);
                yPosition -= LINE_HEIGHT;
                yPosition = drawMultiLineText(contentStream, result.getRiskSummary(), yPosition);
                yPosition -= SECTION_SPACING;

                // Critical Zone Warning
                if (result.getCriticalZoneDetected() != null && result.getCriticalZoneDetected()) {
                    yPosition = drawSectionHeading(contentStream, "⚠ Critical Zone Detected", yPosition);
                    yPosition -= LINE_HEIGHT;
                    yPosition = drawMultiLineText(contentStream, result.getCriticalZoneDescription(), yPosition);
                    yPosition -= SECTION_SPACING;
                }

                // Joint Metrics
                yPosition = drawSectionHeading(contentStream, "Joint Stress Analysis", yPosition);
                yPosition -= LINE_HEIGHT;

                if (metrics != null) {
                    yPosition = drawJointMetricsTable(contentStream, metrics, yPosition);
                    yPosition -= SECTION_SPACING;
                }

                // Check if we need a new page for recommendations
                if (yPosition < 200) {
                    contentStream.close();
                    PDPage newPage = new PDPage(PDRectangle.A4);
                    document.addPage(newPage);
                    try (PDPageContentStream newContentStream = new PDPageContentStream(document, newPage)) {
                        yPosition = newPage.getMediaBox().getHeight() - MARGIN;
                        yPosition = drawRecommendations(newContentStream, result, yPosition);
                    }
                } else {
                    yPosition = drawRecommendations(contentStream, result, yPosition);
                }

                // Footer
                drawFooter(contentStream, page, 1);
            }

            document.save(baos);
            log.info("PDF report generated successfully for analysisId: {}", analysis.getAnalysisId());
            return baos.toByteArray();
        }
    }

    /**
     * Draw title text.
     */
    private float drawTitle(PDPageContentStream contentStream, String text, float yPosition) throws IOException {
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_FONT_SIZE);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(text);
        contentStream.endText();
        return yPosition - TITLE_FONT_SIZE - 5;
    }

    /**
     * Draw subheading text.
     */
    private float drawSubheading(PDPageContentStream contentStream, String text, float yPosition) throws IOException {
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), SUBHEADING_FONT_SIZE);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(text);
        contentStream.endText();
        return yPosition - SUBHEADING_FONT_SIZE - 5;
    }

    /**
     * Draw section heading.
     */
    private float drawSectionHeading(PDPageContentStream contentStream, String text, float yPosition) throws IOException {
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADING_FONT_SIZE);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(text);
        contentStream.endText();
        return yPosition - HEADING_FONT_SIZE - 5;
    }

    /**
     * Draw regular text.
     */
    private float drawText(PDPageContentStream contentStream, String text, float yPosition, float fontSize) throws IOException {
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(text);
        contentStream.endText();
        return yPosition - fontSize - 3;
    }

    /**
     * Draw multi-line text with word wrap.
     */
    private float drawMultiLineText(PDPageContentStream contentStream, String text, float yPosition) throws IOException {
        if (text == null || text.isEmpty()) {
            return yPosition;
        }

        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);

        float maxWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String testLine = line + (line.length() > 0 ? " " : "") + word;
            float textWidth = new PDType1Font(Standard14Fonts.FontName.HELVETICA)
                    .getStringWidth(testLine) / 1000 * BODY_FONT_SIZE;

            if (textWidth > maxWidth && line.length() > 0) {
                yPosition = drawText(contentStream, line.toString(), yPosition, BODY_FONT_SIZE);
                line = new StringBuilder(word);
            } else {
                line.append(line.length() > 0 ? " " : "").append(word);
            }
        }

        if (line.length() > 0) {
            yPosition = drawText(contentStream, line.toString(), yPosition, BODY_FONT_SIZE);
        }

        return yPosition;
    }

    /**
     * Draw joint metrics table.
     */
    private float drawJointMetricsTable(PDPageContentStream contentStream, JointMetrics metrics, float yPosition) throws IOException {
        String[][] tableData = {
                {"Joint Area", "Stress Level", "Status"},
                {"Shoulder Stress", metrics.getShoulderStress().toString(), getStressStatus(metrics.getShoulderStress())},
                {"Elbow Load", metrics.getElbowLoad().toString(), getStressStatus(metrics.getElbowLoad())},
                {"Wrist Load", metrics.getWristLoad().toString(), getStressStatus(metrics.getWristLoad())},
                {"Spine Angle", metrics.getSpineAngle().toString(), getStressStatus(metrics.getSpineAngle())},
                {"Knee Stability", metrics.getKneeStability().toString(), getStressStatus(metrics.getKneeStability())},
                {"Hip Rotation", metrics.getHipRotation().toString(), getStressStatus(metrics.getHipRotation())}
        };

        float tableWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
        float[] columnWidths = {tableWidth * 0.4f, tableWidth * 0.3f, tableWidth * 0.3f};

        // Draw table header
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), BODY_FONT_SIZE);
        float xPosition = MARGIN;
        for (int col = 0; col < 3; col++) {
            contentStream.beginText();
            contentStream.newLineAtOffset(xPosition, yPosition);
            contentStream.showText(tableData[0][col]);
            contentStream.endText();
            xPosition += columnWidths[col];
        }
        yPosition -= LINE_HEIGHT;

        // Draw table rows
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);
        for (int row = 1; row < tableData.length; row++) {
            xPosition = MARGIN;
            for (int col = 0; col < 3; col++) {
                contentStream.beginText();
                contentStream.newLineAtOffset(xPosition, yPosition);
                contentStream.showText(tableData[row][col]);
                contentStream.endText();
                xPosition += columnWidths[col];
            }
            yPosition -= LINE_HEIGHT;
        }

        return yPosition;
    }

    /**
     * Draw recommendations section.
     */
    private float drawRecommendations(PDPageContentStream contentStream, AnalysisResult result, float yPosition) throws IOException {
        yPosition = drawSectionHeading(contentStream, "Recommendations", yPosition);
        yPosition -= LINE_HEIGHT;

        List<String> recommendations = parseRecommendations(result.getRecommendations());

        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);
        for (int i = 0; i < recommendations.size(); i++) {
            String recommendation = (i + 1) + ". " + recommendations.get(i);
            yPosition = drawMultiLineText(contentStream, recommendation, yPosition);
            yPosition -= 5;
        }

        return yPosition;
    }

    /**
     * Draw footer with page number.
     */
    private void drawFooter(PDPageContentStream contentStream, PDPage page, int pageNumber) throws IOException {
        float footerY = MARGIN - 20;
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        contentStream.beginText();
        contentStream.newLineAtOffset(page.getMediaBox().getWidth() / 2 - 20, footerY);
        contentStream.showText("Page " + pageNumber);
        contentStream.endText();

        contentStream.beginText();
        contentStream.newLineAtOffset(MARGIN, footerY);
        contentStream.showText("Generated by Pitcher KinetiX");
        contentStream.endText();
    }

    /**
     * Get risk grade description.
     */
    private String getRiskGradeDescription(AnalysisResult.RiskGrade grade) {
        return switch (grade) {
            case GOOD -> "Low Risk - Excellent Form";
            case NORMAL -> "Moderate Risk - Minor Improvements Needed";
            case CAUTION -> "Elevated Risk - Attention Required";
            case DANGER -> "High Risk - Immediate Action Needed";
        };
    }

    /**
     * Get stress status based on score.
     */
    private String getStressStatus(Integer score) {
        if (score < 30) return "Low";
        if (score < 60) return "Moderate";
        if (score < 80) return "High";
        return "Critical";
    }

    /**
     * Parse recommendations JSON to list.
     */
    private List<String> parseRecommendations(String recommendationsJson) {
        if (recommendationsJson == null || recommendationsJson.isEmpty()) {
            return List.of("No specific recommendations available.");
        }

        try {
            return objectMapper.readValue(recommendationsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse recommendations JSON", e);
            return List.of("Error loading recommendations.");
        }
    }
}

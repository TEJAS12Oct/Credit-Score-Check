package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.dto.response.BoltPeCreditReport;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/** Generates a dynamic PDF from the stored BoltPe SubmitOtpResponse. */
@Service
public class CreditScorePdfService {
    private static final String CREDIT_ACCOUNTS = "creditAccounts";
    private static final String APPLICATION = "application";
    private static final String PIN_CODE = "pinCode";
    private static final String CREDIT_LIMIT_AMOUNT = "creditLimitAmount";
    private static final String NOT_REPORTED = "Not Reported";
    private static final String REPORT_TITLE = "Credit Score Report";
    private static final String ACCOUNT_HISTORY = "caisAccountHistory";
    private static final String ACCOUNT_ADDRESS = "caisHolderAddressDetails";

    private static final Color DARK = Color.BLACK;
    private static final Color MUTED = new Color(96, 96, 96);
    private static final Color LINE = new Color(222, 222, 222);
    private static final Color GREEN = new Color(71, 186, 64);
    private static final Color RED = new Color(203, 25, 31);
    private static final Color ORANGE = new Color(199, 80, 37);

    private static final String DISCLAIMER = "Report data is provided by the credit bureau; BoltPe does not calculate credit scores.";
    private PDDocument doc;
    private Instant generatedAt;
    private PDPageContentStream cs;
    private float y;
    private PDFont regular;
    private PDFont bold;
    private PDImageXObject boltPeLogo;

    /** Synchronized because the drawing helpers share a per-render document context. */
    public synchronized byte[] render(SubmitOtpResponse data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Credit score response cannot be null");
        }
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            doc = document;
            generatedAt = Instant.now();
            try {
                initializeFonts();
                initializeLogo();
                renderOverview(data);
                renderPersonalInformation(data);
                List<JsonNode> creditAccounts = accounts(data);
                renderCreditLimits(creditAccounts);
                renderAccountDetails(creditAccounts);
                renderPaymentHistory(creditAccounts);
                renderEnquiries(data);
                renderAddresses(data, creditAccounts);
                closePage();
                addPageFooters();
                doc.save(output);
                return output.toByteArray();
            } finally {
                closePage();
                doc = null;
                regular = null;
                bold = null;
                boltPeLogo = null;
                generatedAt = null;
            }
        }
    }

    private void initializeFonts() throws IOException {
        try (InputStream normal = getClass().getResourceAsStream("/fonts/HankenGrotesk-Regular.ttf");
             InputStream heavy = getClass().getResourceAsStream("/fonts/HankenGrotesk-Bold.ttf")) {
            if (normal == null || heavy == null) {
                regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                return;
            }
            regular = PDType0Font.load(doc, normal);
            bold = PDType0Font.load(doc, heavy);
        }
    }


    private void initializeLogo() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/images/boltpe-logo.png")) {
            if (input != null) {
                boltPeLogo = PDImageXObject.createFromByteArray(doc, input.readAllBytes(), "BoltPe logo");
            }
        }
    }

    private void renderOverview(SubmitOtpResponse data) throws IOException {
        newPage();
        centered(REPORT_TITLE, y, 23, bold, DARK);
        y -= 23;
        centered("A clear, visual summary of the reported credit profile", y, 11, regular, DARK);
        y -= 40;
        scorePanel(data);
        y -= 51;
        section("Credit Overview", "Reported credit account summary");
        SubmitOtpResponse.CreditSummary summary = data.summary();
        cards(new String[]{"Total Accounts", "Active Accounts", "Closed Accounts", "Defaults"},
                new String[]{num(summary == null ? null : summary.totalAccounts()),
                        num(summary == null ? null : summary.activeAccounts()),
                        num(summary == null ? null : summary.closedAccounts()), defaults(data)});
        y -= 48;
        section("Outstanding Balance", "Reported secured and unsecured balances");
        JsonNode outstanding = path(data, CREDIT_ACCOUNTS, "totalOutstandingBalance");
        cards(new String[]{"Secured", "Unsecured", "Total Outstanding"},
                new String[]{money(field(outstanding, "outstandingBalanceSecured")),
                        money(field(outstanding, "outstandingBalanceUnSecured")),
                        money(summary == null ? null : summary.totalOutstanding())});
    }

    private void renderPersonalInformation(SubmitOtpResponse data) throws IOException {
        newPage();
        section("Personal Information", "Reported applicant details");
        JsonNode applicant = path(data, APPLICATION, "currentApplicationDetails", "currentApplicantDetails");
        JsonNode address = applicantAddress(data);
        String name = join(field(applicant, "firstName"), field(applicant, "middleName1"), field(applicant, "lastName"));
        row("Name", name);
        row("Date Of Birth", date(field(applicant, "dateOfBirthApplicant")));
        row("Gender", gender(field(applicant, "genderCode")));
        String pan = field(applicant, "incomeTaxPan");
        row("PAN Number", maskedOnly(missing(pan) ? firstAccountId(data) : pan));
        row("Mobile Number", maskedOnly(field(applicant, "mobilePhoneNumber")));
        row("Email", maskedOnly(field(applicant, "emailId")));
        String state = field(address, "state");
        row("State", "27".equals(state) ? "Maharashtra" : state);
        row("PIN Code", field(address, PIN_CODE));
    }

    private void renderCreditLimits(List<JsonNode> creditAccounts) throws IOException {
        y -= 32;
        section("Credit Accounts", "Reported credit limits by account");
        long maximum = maximumCreditLimit(creditAccounts);
        for (JsonNode account : creditAccounts) {
            String limit = field(account, CREDIT_LIMIT_AMOUNT);
            if (missing(limit)) {
                continue;
            }
            ensure(36);
            text(50, y, shorten(field(account, "subscriberName"), 23), 10, regular, DARK);
            rect(185, y - 3, 270, 8, new Color(234, 234, 234));
            float fraction = maximum == 0 ? 0 : (float) amount(limit) / maximum;
            rect(185, y - 3, 270 * fraction, 8, ORANGE);
            text(468, y, money(limit), 10, bold, DARK);
            y -= 27;
        }
    }

    private static long maximumCreditLimit(List<JsonNode> creditAccounts) {
        long maximum = 0;
        for (JsonNode account : creditAccounts) {
            maximum = Math.max(maximum, amount(field(account, CREDIT_LIMIT_AMOUNT)));
        }
        return maximum;
    }

    private void renderAccountDetails(List<JsonNode> creditAccounts) throws IOException {
        newPage();
        section("Account Detail", "Reported credit facilities");
        for (JsonNode account : creditAccounts) {
            account(account);
        }
    }

    private void renderPaymentHistory(List<JsonNode> creditAccounts) throws IOException {
        TreeMap<String, String> periods = reportedPeriods(creditAccounts);
        ensure(205);
        y -= 24;
        section("Payment History", "Reported monthly days past due");
        if (periods.isEmpty()) {
            row("Payment history", NOT_REPORTED);
            return;
        }
        List<Map.Entry<String, String>> latest = new ArrayList<>();
        for (Map.Entry<String, String> entry : periods.descendingMap().entrySet()) {
            if (latest.size() == 5) break;
            latest.add(entry);
        }
        java.util.Collections.reverse(latest);
        long onTime = latest.stream().filter(entry -> "0".equals(entry.getValue())).count();
        text(60, y, onTime + "/" + latest.size() + " reported periods on time", 10, regular, onTime == latest.size() ? GREEN : RED);
        y -= 83;
        for (int index = 0; index < latest.size(); index++) {
            Map.Entry<String, String> entry = latest.get(index);
            float x = 92.0f + index * 105.0f;
            boolean timely = "0".equals(entry.getValue());
            circle(x, y + 28, 23, timely ? GREEN : RED);
            if (timely) {
                cs.setStrokingColor(Color.WHITE); cs.setLineWidth(3);
                cs.moveTo(x - 10, y + 28); cs.lineTo(x - 3, y + 20); cs.lineTo(x + 12, y + 37); cs.stroke();
            } else text(x - 5, y + 22, "X", 14, bold, Color.WHITE);
            String monthLabel = entry.getKey().substring(5) + "/" + entry.getKey().substring(2, 4);
            text(x - 18, y - 9, monthLabel, 10, bold, DARK);
            text(x - 27, y - 25, entry.getValue() + " Past Due", 8, regular, MUTED);
        }
        y -= 80;
    }

    private static TreeMap<String, String> reportedPeriods(List<JsonNode> creditAccounts) {
        TreeMap<String, String> periods = new TreeMap<>();
        for (JsonNode account : creditAccounts) {
            JsonNode history = account.path(ACCOUNT_HISTORY);
            if (!history.isArray()) {
                continue;
            }
            for (JsonNode period : history) {
                addPeriod(periods, period);
            }
        }
        return periods;
    }

    private static void addPeriod(Map<String, String> periods, JsonNode history) {
        String year = field(history, "year");
        String month = field(history, "month");
        String overdue = field(history, "daysPastDue");
        if (missing(year) || missing(month) || missing(overdue)
                || !year.matches("\\d{4}") || !month.matches("\\d{1,2}")) {
            return;
        }
        int monthNumber = Integer.parseInt(month);
        if (monthNumber >= 1 && monthNumber <= 12) {
            periods.put(year + "-" + String.format(Locale.ROOT, "%02d", monthNumber), overdue);
        }
    }

    private void renderEnquiries(SubmitOtpResponse data) throws IOException {
        y -= 25;
        ensure(145);
        section("Credit Enquiries", "Reported enquiry counts");
        BoltPeCreditReport.EnquirySummary enquiries = data.creditReport() == null
                ? null : data.creditReport().enquirySummary();
        cards(new String[]{"Last 7 days", "Last 30 days", "Last 90 days", "Last 180 days"},
                new String[]{num(enquiries == null ? null : enquiries.last7Days()),
                        num(enquiries == null ? null : enquiries.last30Days()),
                        num(enquiries == null ? null : enquiries.last90Days()),
                        num(enquiries == null ? null : enquiries.last180Days())});
    }

    private void renderAddresses(SubmitOtpResponse data, List<JsonNode> creditAccounts) throws IOException {
        y -= 50;
        ensure(130);
        section("Address Information", "Reported addresses");
        Map<String, String> addresses = collectPostalAddresses(creditAccounts);
        String applicantPinCode = field(applicantAddress(data), PIN_CODE);
        if (addresses.isEmpty() && !missing(applicantPinCode)) {
            addresses.put(applicantPinCode, applicantPinCode);
        }
        for (Map.Entry<String, String> address : addresses.entrySet()) {
            addressRow(address.getValue(), address.getKey());
        }
    }

    private static Map<String, String> collectPostalAddresses(List<JsonNode> creditAccounts) {
        Map<String, String> addresses = new java.util.LinkedHashMap<>();
        for (JsonNode account : creditAccounts) {
            JsonNode details = account.path(ACCOUNT_ADDRESS);
            if (!details.isArray()) continue;
            for (JsonNode address : details) {
                String pin = field(address, "zipPostalCodeNonNormalized");
                if (missing(pin)) continue;
                String location = join(field(address, "firstLineOfAddressNonNormalized"),
                        field(address, "secondLineOfAddressNonNormalized"),
                        field(address, "thirdLineOfAddressNonNormalized"),
                        field(address, "cityNonNormalized"));
                addresses.putIfAbsent(pin, missing(location) ? pin : location);
            }
        }
        return addresses;
    }

    private static JsonNode applicantAddress(SubmitOtpResponse data) {
        return path(data, APPLICATION, "currentApplicationDetails", "currentApplicantAddressDetails");
    }

    private void addPageFooters() throws IOException {
        int count = doc.getNumberOfPages();
        for (int index = 0; index < count; index++) {
            try (PDPageContentStream footer = new PDPageContentStream(
                    doc, doc.getPage(index), PDPageContentStream.AppendMode.APPEND, true, true)) {
                line(footer, 50, 55, 545, 55, LINE);
                draw(footer, 50, 40, REPORT_TITLE, 8, regular, MUTED);
                draw(footer, 50, 27, "Generated on (IST): " + generatedAt.atZone(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), 7, regular, MUTED);
                draw(footer, 50, 17, DISCLAIMER, 6, regular, MUTED);
                draw(footer, 487, 40, "Page " + (index + 1) + " of " + count, 8, regular, MUTED);
            }
        }
    }

    private void newPage() throws IOException {
        closePage();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        cs = new PDPageContentStream(doc, page);
        y = 760;
        if (boltPeLogo != null) {
            float maxWidth = 80f;
            float height = maxWidth * boltPeLogo.getHeight() / boltPeLogo.getWidth();
            cs.drawImage(boltPeLogo, 467, 788, maxWidth, height);
        } else {
            text(475, 800, "BoltPe", 16, bold, Color.BLACK);
        }
    }

    private void closePage() throws IOException {
        if (cs != null) {
            cs.close();
            cs = null;
        }
    }

    private void ensure(float height) throws IOException {
        if (y - height < 78) {
            newPage();
        }
    }

    private void scorePanel(SubmitOtpResponse data) throws IOException {
        float top = y;
        roundedRect(50, top - 230, 495, 230, 13, new Color(247, 247, 247), new Color(216, 216, 216));
        float cx = 167;
        float cy = top - 104;
        ring(cx, cy, 68, 14, new Color(227, 227, 227));
        // The score is a partial circular progress arc, not a solid disc.
        int score = data.score() == null ? 300 : data.score();
        float fraction = Math.max(0, Math.min(1, (score - 300f) / 600f));
        arc(cx, cy, 68, 14, 155, -320 * fraction, GREEN);
        centeredAt(cx, cy - 8, num(data.score()), 27, bold, DARK);
        centeredUnderlinedAt(cx, cy - 28, safe(data.band()), 11, regular, GREEN);
        centeredAt(cx, top - 202, "FCIREX Score", 12, regular, DARK);
        text(284, top - 42, "Your Credit Score", 16, bold, DARK);
        text(284, top - 60, "A snapshot of your reported credit profile", 10, regular, DARK);
        line(cs, 284, top - 73, 515, top - 73, new Color(120,120,120));
        text(284, top - 96, "Match Status :", 11, regular, DARK);
        text(284, top - 112, matchStatus(data), 10, regular, DARK);
        text(284, top - 143, "Report details :", 11, regular, DARK);
        text(284, top - 159, date(data.report() == null ? null : data.report().reportDate()), 10, regular, DARK);
        text(284, top - 190, "Report no :", 11, regular, DARK);
        text(284, top - 206, safe(data.report() == null ? null : data.report().reportNumber()), 10, regular, DARK);
        y -= 230;
    }

    private void centered(String value, float atY, int size, PDFont font, Color color) throws IOException {
        centeredAt(297.5f, atY, value, size, font, color);
    }

    private void centeredAt(float cx, float atY, String value, int size, PDFont font, Color color) throws IOException {
        text(cx - textWidth(value, size, font) / 2, atY, value, size, font, color);
    }

    private void centeredUnderlinedAt(float cx, float atY, String value, int size, PDFont font, Color color) throws IOException {
        underlinedText(cx - textWidth(value, size, font) / 2, atY, value, size, font, color);
    }

    private void underlinedText(float x, float atY, String value, int size, PDFont font, Color color) throws IOException {
        text(x, atY, value, size, font, color);
        line(cs, x, atY - 2, x + textWidth(value, size, font), atY - 2, color);
    }

    private static float textWidth(String value, int size, PDFont font) throws IOException {
        String v = value == null ? "" : value;
        float width = 0;
        for (String part : v.split("₹", -1)) width += font.getStringWidth(part) / 1000f * (size + 1);
        width += (v.length() - v.replace("₹", "").length()) * (size + 1) * 0.67f;
        return width;
    }

    private void roundedRect(float x, float bottom, float w, float h, float r, Color fill, Color border) throws IOException {
        float k = .55228475f * r;
        cs.setNonStrokingColor(fill);
        cs.setStrokingColor(border);
        cs.setLineWidth(.8f);
        cs.moveTo(x+r,bottom);
        cs.lineTo(x+w-r,bottom); cs.curveTo(x+w-r+k,bottom,x+w,bottom+r-k,x+w,bottom+r);
        cs.lineTo(x+w,bottom+h-r); cs.curveTo(x+w,bottom+h-r+k,x+w-r+k,bottom+h,x+w-r,bottom+h);
        cs.lineTo(x+r,bottom+h); cs.curveTo(x+r-k,bottom+h,x,bottom+h-r+k,x,bottom+h-r);
        cs.lineTo(x,bottom+r); cs.curveTo(x,bottom+r-k,x+r-k,bottom,x+r,bottom);
        cs.closePath(); cs.fillAndStroke();
    }

    private void ring(float cx,float cy,float radius,float thickness,Color color) throws IOException {
        arc(cx,cy,radius,thickness,0,360,color);
    }

    private void arc(float cx,float cy,float radius,float thickness,float start,float sweep,Color color) throws IOException {
        cs.setStrokingColor(color); cs.setLineWidth(thickness);
        cs.setLineCapStyle(1);
        int steps=Math.max(4,(int)Math.ceil(Math.abs(sweep)/5));
        for(int i=0;i<=steps;i++) {
            double theta=Math.toRadians(start+sweep*i/steps);
            float x=cx+(float)Math.cos(theta)*radius;
            float yy=cy+(float)Math.sin(theta)*radius;
            if(i==0) cs.moveTo(x,yy); else cs.lineTo(x,yy);
        }
        cs.stroke();
    }

    private static String matchStatus(SubmitOtpResponse data) {
        if (data.creditReport() == null || data.creditReport().matchInformation() == null) {
            return "Not Confirmed";
        }
        return "Y".equalsIgnoreCase(data.creditReport().matchInformation().exactMatch())
                ? "Exact Match" : "Not Confirmed";
    }

    private void section(String heading, String subtitle) throws IOException {
        ensure(60);
        text(50, y, "-  " + heading, 15, bold, DARK);
        y -= 17;
        text(65, y, subtitle, 10, regular, DARK);
        y -= 30;
    }

    private void cards(String[] labels, String[] values) throws IOException {
        ensure(83);
        float width = 495f / labels.length;
        Color[] backgrounds = {new Color(216,249,253), new Color(211,255,210),
                new Color(255,221,221), new Color(255,239,221)};
        Color[] foregrounds = {new Color(0,104,155), new Color(0,129,19),
                new Color(148,0,0), new Color(147,72,0)};
        for (int i=0;i<labels.length;i++) {
            float x=50+i*width;
            float w=width-8;
            roundedRect(x,y-68,w,68,7,Color.WHITE,new Color(220,220,220));
            String label=shorten(labels[i],23);
            float badgeW = Math.min(w - 12,
                    textWidth(label, 10, regular) + 19);
            roundedRect(x+7,y-30,badgeW,24,5,backgrounds[i%4],backgrounds[i%4]);
            text(x+15,y-22,label,10,regular,foregrounds[i%4]);
            text(x+15,y-55,shorten(values[i],21),13,bold,DARK);
        }
        y-=73;
    }

    private void account(JsonNode account) throws IOException {
        ensure(80);
        String status = "13".equals(field(account, "accountStatus")) ? "Closed" : "Active";
        String accountHeading = shorten(field(account, "subscriberName"), 42) + " - ";
        Color statusColor = "Closed".equals(status) ? RED : GREEN;
        text(50, y, accountHeading, 13, bold, DARK);
        float statusX = 50 + textWidth(accountHeading, 13, bold);
        underlinedText(statusX, y, status, 13, regular, statusColor);
        y -= 25;
        row("Account Number", maskedOnly(field(account, "accountNumber")));
        row("Credit Limit", money(field(account, CREDIT_LIMIT_AMOUNT)));
        row("Highest Credit/Loan", money(field(account, "highestCreditOrOriginalLoanAmount")));
        row("Current Balance", money(field(account, "currentBalance")));
        row("Amount Past Due", money(field(account, "amountPastDue")));
        String rate = field(account, "rateOfInterest");
        row("Interest Rate", missing(rate) ? NOT_REPORTED : rate + "%");
        row("Currency", field(account, "currencyCode"));
        row("Open Date", date(field(account, "openDate")));
        row("Last Reported", date(field(account, "dateReported")));
        y -= 28;
    }

    /** Wrap long postal addresses without truncation; reserve the right column for PIN. */
    private void addressRow(String address, String pin) throws IOException {
        final float left = 60f;
        final float maxWidth = 365f;
        final int fontSize = 10;
        String fullAddress = safe(address).replaceAll("\\s+", " ").trim();
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : fullAddress.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && textWidth(candidate, fontSize, regular) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            for (char ch : word.toCharArray()) {
                if (!current.isEmpty() && textWidth(current.toString() + ch, fontSize, regular) > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(ch);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        if (lines.isEmpty()) lines.add(NOT_REPORTED);
        float height = Math.max(29f, lines.size() * 16f + 13f);
        ensure(height);
        for (int i = 0; i < lines.size(); i++) {
            text(left, y - i * 16f, lines.get(i), fontSize, regular, MUTED);
        }
        String pinText = safe(pin);
        text(535 - textWidth(pinText, fontSize, bold), y, pinText, fontSize, bold, DARK);
        line(cs, left, y - height + 19f, 535, y - height + 19f, LINE);
        y -= height;
    }

    private void row(String key, String value) throws IOException {
        ensure(29);
        text(60, y, shorten(key, 44), 10, regular, MUTED);
        String display = shorten(safe(value), 57);
        float width = textWidth(display, 10, bold);
        text(Math.max(290, 535 - width), y, display, 10, bold, DARK);
        line(cs, 60, y - 10, 535, y - 10, LINE);
        y -= 29;
    }

    private void text(float x, float positionY, String value, int size, PDFont font, Color color)
            throws IOException {
        draw(cs, x, positionY, value, size, font, color);
    }

    private static void draw(PDPageContentStream stream, float x, float positionY,
                             String value, int size, PDFont font, Color color) throws IOException {
        String raw = value == null ? "" : value;
        String[] parts = raw.split("₹", -1);
        float cursor = x;
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                stream.beginText();
                stream.setNonStrokingColor(color);
                stream.setFont(font, size + 1);
                stream.newLineAtOffset(cursor, positionY);
                stream.showText(parts[i]);
                stream.endText();
                cursor += font.getStringWidth(parts[i]) / 1000f * (size + 1);
            }
            if (i < parts.length - 1) {
                drawRupee(stream, cursor, positionY, size + 1, color);
                cursor += (size + 1) * .67f;
            }
        }
    }

    /** Vector rupee symbol: independent of installed/bundled font glyph coverage. */
    private static void drawRupee(PDPageContentStream stream, float x, float baseline, float size, Color color)
            throws IOException {
        float w = size * .63f, h = size * .75f;
        stream.setStrokingColor(color);
        stream.setLineWidth(Math.max(.65f, size * .075f));
        stream.moveTo(x, baseline + h); stream.lineTo(x + w, baseline + h);
        stream.moveTo(x, baseline + h * .76f); stream.lineTo(x + w, baseline + h * .76f);
        stream.moveTo(x + w * .06f, baseline + h); stream.lineTo(x + w * .40f, baseline + h);
        stream.curveTo(x + w * .98f, baseline + h, x + w * .89f, baseline + h * .42f, x + w * .09f, baseline + h * .42f);
        stream.lineTo(x + w * .77f, baseline);
        stream.stroke();
    }

    private void rect(float x, float positionY, float width, float height, Color color)
            throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, positionY, width, height);
        cs.fill();
    }

    private void circle(float centerX, float centerY, float radius, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        float factor = .55228475f * radius;
        cs.moveTo(centerX + radius, centerY);
        cs.curveTo(centerX + radius, centerY + factor, centerX + factor, centerY + radius,
                centerX, centerY + radius);
        cs.curveTo(centerX - factor, centerY + radius, centerX - radius, centerY + factor,
                centerX - radius, centerY);
        cs.curveTo(centerX - radius, centerY - factor, centerX - factor, centerY - radius,
                centerX, centerY - radius);
        cs.curveTo(centerX + factor, centerY - radius, centerX + radius, centerY - factor,
                centerX + radius, centerY);
        cs.fill();
    }

    private static void line(PDPageContentStream stream, float x, float y, float x2,
                             float y2, Color color) throws IOException {
        stream.setStrokingColor(color);
        stream.setLineWidth(.6f);
        stream.moveTo(x, y);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    private static String safe(String value) {
        return missing(value) ? NOT_REPORTED : value;
    }

    private static boolean missing(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value)
                || NOT_REPORTED.equalsIgnoreCase(value);
    }

    private static String shorten(String value, int length) {
        String text = safe(value);
        return text.length() <= length ? text : text.substring(0, length - 3) + "...";
    }

    private static String field(JsonNode node, String key) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(key);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static JsonNode path(SubmitOtpResponse data, String section, String... keys) {
        if (data.creditReport() == null) {
            return null;
        }
        JsonNode node = switch (section) {
            case APPLICATION -> data.creditReport().application() == null
                    ? null : data.creditReport().application().details();
            case CREDIT_ACCOUNTS -> data.creditReport().creditAccounts() == null
                    ? null : data.creditReport().creditAccounts().summary();
            default -> null;
        };
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            node = node.path(key);
        }
        return node;
    }

    private static List<JsonNode> accounts(SubmitOtpResponse data) {
        List<JsonNode> result = new ArrayList<>();
        if (data.creditReport() == null || data.creditReport().creditAccounts() == null) {
            return result;
        }
        JsonNode accountNodes = data.creditReport().creditAccounts().accounts();
        if (accountNodes != null && accountNodes.isArray()) {
            accountNodes.forEach(result::add);
        }
        return result;
    }

    private static String firstAccountId(SubmitOtpResponse data) {
        for (JsonNode account : accounts(data)) {
            JsonNode identifiers = account.path("caisHolderIdDetails");
            if (!identifiers.isArray()) {
                continue;
            }
            for (JsonNode identifier : identifiers) {
                String pan = field(identifier, "incomeTaxPan");
                if (!missing(pan)) {
                    return pan;
                }
            }
        }
        return null;
    }

    private static String maskedOnly(String value) {
        if (missing(value)) {
            return NOT_REPORTED;
        }
        if (value.contains("@")) {
            String local = value.substring(0, value.indexOf('@'));
            String domain = value.substring(value.indexOf('@') + 1);
            int lastDot = domain.lastIndexOf('.');
            String suffix = lastDot >= 0 ? domain.substring(lastDot) : "";
            return (local.isEmpty() ? "*" : local.substring(0, 1)) + "****@****" + suffix;
        }
        if (value.indexOf('*') >= 0 || value.matches("(?s).*[Xx]{2,}.*")) {
            return value;
        }
        return "****" + value.substring(Math.max(0, value.length() - 4));
    }

    private static String join(String... parts) {
        StringJoiner result = new StringJoiner(" ");
        for (String part : parts) {
            if (!missing(part)) {
                result.add(part);
            }
        }
        return result.toString();
    }

    private static String num(Number value) {
        return value == null ? NOT_REPORTED : value.toString();
    }

    private static long amount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String money(Object value) {
        if (value == null || missing(value.toString())) {
            return NOT_REPORTED;
        }
        try {
            return "₹" + String.format(Locale.US, "%,d", Long.parseLong(value.toString()));
        } catch (NumberFormatException exception) {
            return NOT_REPORTED;
        }
    }

    private static String date(String value) {
        if (missing(value)) {
            return NOT_REPORTED;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        } catch (java.time.format.DateTimeParseException exception) {
            return NOT_REPORTED;
        }
    }

    private static String gender(String value) {
        return switch (safe(value)) {
            case "1" -> "Male";
            case "2" -> "Female";
            default -> NOT_REPORTED;
        };
    }

    private static String defaults(SubmitOtpResponse data) {
        JsonNode value = path(data, CREDIT_ACCOUNTS, "creditAccount");
        return safe(field(value, "creditAccountDefault"));
    }
}

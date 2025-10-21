package app.reader;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Класс обработки файла эксель. Перебирает строки и ячейки эксель,
 * проверяет на типы данных в ячейке, и возвращает список строк
 * excelPath - путь к эксель файлу.
 */
public class ExcelReader {
    private final String excelFile;
    public ExcelReader(String excelFile) {
        this.excelFile = excelFile;
    }

    public List<Map<String, String>> readAllRows() throws IOException {
        List<Map<String,String>> list = new ArrayList<>();
        try (InputStream stream = Files.newInputStream(Paths.get(excelFile));
             Workbook workbook = new XSSFWorkbook(stream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();
            if (!rows.hasNext()) return list;
            Row header = rows.next();
            List<String> cols = new ArrayList<>();
            for(Cell cell: header) cols.add(cell.getStringCellValue().trim());

            //Бежим строкам
            while (rows.hasNext()) {
                Row row = rows.next();
                if (isRowEmpty(row)) continue;
                Map<String, String> map = new HashMap<>();

                //Достаем каждую ячейку и обрабатываем ее
                for (int i = 0; i < cols.size(); i++) {
                    Cell cell = row.getCell(i);
                    String value = "";

                    // Проверка на типы данных в ячейке
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING -> {
                                value = cell.getStringCellValue().trim();
                            }
                            case NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    // Если это дата — сохраняем в ISO-формате (YYYY-MM-DD)
                                    value = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                                } else {
                                    // Число — сохраняем без экспоненциальной записи
                                    BigDecimal bd = BigDecimal.valueOf(cell.getNumericCellValue());
                                    value = bd.stripTrailingZeros().toPlainString();
                                }
                            }
                            case BOOLEAN -> {
                                value = Boolean.toString(cell.getBooleanCellValue());
                            }
                            default -> {
                                // Принудительно как текст
                                cell.setCellType(CellType.STRING);
                                value = cell.getStringCellValue().trim();
                            }
                        }
                    }
                    map.put(cols.get(i), value != null ? value.trim() : "");
                }
                list.add(map);
            }
        }
        return list;
    }
    //Проверка ячеек входящей строки на наличие текста или чисел
    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String res = "";
                if (cell.getCellType() == CellType.STRING) {
                    res = cell.getStringCellValue().trim();
                } else if (cell.getCellType() == CellType.NUMERIC) {
                    res = Double.toString(cell.getNumericCellValue());
                }
                if (!res.isEmpty()) return false;
            }
        }
        return true;
    }
}

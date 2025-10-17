package app.core;


import app.reader.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Основной класс запуска приложения
 * Created by Aleksey Selikhov 17.10.2025
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception{

        //Достаем конфиг и пробуем его считать
        Properties properties = new Properties();
        Path cfgFile = Paths.get("application.properties");
        if (!Files.exists(cfgFile)) {
            System.out.println("Не найден конфигурационный файл");
            System.exit(1);
        }

        try (InputStream stream = Files.newInputStream(cfgFile)) {
            properties.load(stream);
        }
        //Достанем данные для работы
        String publicId = properties.getProperty("publicId");
        String apiSecret = properties.getProperty("apiSecret");
        String url = properties.getProperty("apiUrl");
        String excelFile = properties.getProperty("excelFile");
        int threads = Integer.parseInt(properties.getProperty("threads"));
        int requestPerSeconds = Integer.parseInt(properties.getProperty("requestPerSeconds"));
        int maxRetries = Integer.parseInt(properties.getProperty("maxRetries"));
        int initialRetryMillis = Integer.parseInt(properties.getProperty("initialRetryMillis"));
        //Файлы логов
        String successLog = properties.getProperty("successLog");
        String failedLog = properties.getProperty("failedLog");
        //Достаем список
        ExcelReader reader = new ExcelReader(excelFile);
        List<Map<String,String>> rows = reader.readAllRows();
        log.info("найдено {} строк", rows);
    }

}

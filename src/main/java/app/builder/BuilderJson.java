package app.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Класс формирует json на отправку из файла эксель
 * row - входящая строка со всеми данными на один чек
 * Возвращает готовый json шаблон
 * Created by Aleksey Selikhov
 */
public class BuilderJson {
    private final ObjectMapper mapper;

    public BuilderJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String buildJSON(Map<String,String> row) throws Exception{
        ObjectNode root = mapper.createObjectNode();
        ObjectNode data = mapper.createObjectNode();

        data.put("OrganizationInn", getData(row, "OrganizationInn"));
        data.put("TaxationSystem", getData(row, "TaxationSystem"));
        data.put("CorrectionReceiptType", getData(row, "CorrectionReceiptType"));

        ObjectNode cause = mapper.createObjectNode();
        cause.put("CorrectionDate", getData(row, "CorrectionDate"));
        cause.put("CorrectionDate", getData(row, "CorrectionDate"));
        data.set("CauseCorrection", cause);

        ObjectNode amounts = mapper.createObjectNode();
        double total = parseDouble(row.get("Total"), 0.0);
        amounts.put("Total", total);
        data.set("Amounts", amounts);

        //Дополнительные данные
        putIfExist(data, row, "PaymentPlace");
        putIfExist(data, row, "PaymentAddress");
        if (row.containsKey("IsInternetPayment")) {
            data.put("IsInternetPayment", Boolean.parseBoolean(getData(row, "IsInternetPayment")));
        }
        //Опциональные поля
        if (row.containsKey("Items") && !getData(row, "Items").isEmpty()) {
            try {
                var itemsNode = mapper.readTree(getData(row, "Items"));
                data.set("Items", itemsNode);
            } catch (Exception ignored) {}
        }

        root.set("CorrectionReceiptData", data);
        return mapper.writeValueAsString(root);
    }

    //Достаем данные из строки по ключу
    private static String getData(Map<String, String> row, String key) {
        return row.getOrDefault(key, "");
    }
    //Парсим данные о ценах
    private static double parseDouble(String data, double defaultValue) {
        if (data == null || data.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(data.replace(",", "."));
        } catch (Exception ex) {
            return defaultValue;

        }
    }
    //Метод добавления опциональных полей, если есть.
    private static void putIfExist(ObjectNode node, Map<String,String> row, String key) {
        if (row.containsKey(key)) node.put(key, getData(row, key));
    }
}

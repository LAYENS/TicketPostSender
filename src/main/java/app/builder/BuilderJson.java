package app.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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


        data.put("organizationInn", getData(row, "OrganizationInn"));
        data.put("taxationSystem", (int) Double.parseDouble(getData(row, "TaxationSystem")));
        data.put("correctionReceiptType", (int) Double.parseDouble(getData(row, "CorrectionReceiptType")));

        ObjectNode cause = mapper.createObjectNode();
        cause.put("correctionDate", getData(row, "CorrectionDate"));
        cause.put("correctionNumber", getData(row, "CorrectionNumber"));
        data.set("CauseCorrection", cause);

        ObjectNode amounts = mapper.createObjectNode();
        double total = parseDouble(row.get("Amounts"), 0.0);
        amounts.put("electronic", total);
        data.set("amounts", amounts);

        ArrayNode itemsArray = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("label", getData(row, "Label"));
        item.put("price", getData(row, "Price"));
        item.put("quantity", getData(row, "Quantity"));
        item.put("amount", getData(row, "Amount"));
        item.put("correctionType", (int) Double.parseDouble(getData(row, "CorrectionType")));
        item.put("paymentPlace", getData(row, "PaymentPlace"));
        item.put("paymentAddress", getData(row, "PaymentAddress"));
        itemsArray.add(item);
        data.set("items", itemsArray);

        root.set("correctionReceiptData", data);
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

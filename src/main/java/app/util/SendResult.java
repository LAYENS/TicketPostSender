package app.util;

public class SendResult {
    public final boolean success;
    public final int httpCode;
    public final String responseBody;

    public SendResult(boolean success, int httpCode, String responseBody) {
        this.success = success;
        this.httpCode = httpCode;
        this.responseBody = responseBody;
    }

}

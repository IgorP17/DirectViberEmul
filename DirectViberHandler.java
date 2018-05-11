package direct_viber_emul;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import utils.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Хендлер запросов
 */
class DirectViberHandler implements HttpHandler {

    private int replyDelay = DirectViberEmul.getMtReplyDelay();

    private static Pattern patternDest = Pattern.compile("\"dest\":\"(\\d+)\",");
    private static Pattern patternSeq = Pattern.compile("\"seq\":(\\d+),");

    public void handle(HttpExchange exchange) {
        try {
            String response;
            long threadId = Thread.currentThread().getId();
            Log.println("Thread id = " + threadId + "; Reply delay = " + replyDelay);

            // прочитаем POST запрос
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            String requestBodyLine = br.readLine();
            StringBuilder requestBodyBuilder = new StringBuilder();
            while (requestBodyLine != null) {
                requestBodyBuilder.append(requestBodyLine);
                requestBodyLine = br.readLine();
            }
            String requestBody = requestBodyBuilder.toString();

            // надо обработать запрос
            String dest, seq;
            //TODO , type= "", ttl = "";

            // генерим токен
            int messageToken = DirectViberEmul.getMessageToken();
            String badData = "{\"status\":03," +
                    "\"seq\": 0," +
                    "\"message_token\":\"" + messageToken + "\"}";

            Matcher matcher;
            matcher = patternDest.matcher(requestBody);

            if (matcher.find()) {
                // нашли msisdn
                dest = matcher.group(1);
                // генерим код ответа
                int respCode = Integer.parseInt(getResponseCode(dest));
                // ищем seq
                matcher = patternSeq.matcher(requestBody);
                if (matcher.find()) {
                    // нашли seq
                    seq = matcher.group(1);
                    // хороший ответ
                    response = "{\"status\":" + respCode + "," +
                            "\"seq\":" + seq + "," +
                            "\"message_token\":\"" + messageToken + "\"}";
                } else {
                    // не найден seq, вернем SRVC_BAD_DATA = 03
                    response = badData;

                }

                // надо обработать надо ли статусы и тп
                // ТОЛЬКО если ответ 0! принято к обработке
                if (respCode == 0) {
                    manageStatus(messageToken, dest);
                }
            } else {
                // не найден msisdn, вернем SRVC_BAD_DATA = 03
                response = badData;
            }

            // надо подождать
            if (replyDelay > 0) Thread.sleep(replyDelay);

            sendResponse(exchange, response);


        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void sendResponse(HttpExchange exchange, String response) throws IOException {
        // Ответ
        Headers h = exchange.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");

        byte[] responseBytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.flush();
        os.close();    // 100% нету keep-alive
    }

    // Отдаем код ответа
    private String getResponseCode(String s) {
        String result = "0";
        String code = DirectViberEmul.getMtStatesMode();
        // если не надо по последней цифре(т.е. жостко режим номер 13 например), отдатим соотв цифру
        if (!code.equalsIgnoreCase("15")) {
            if (code.startsWith("0")) {
                result = code.substring(1, 2);
            } else {
                result = code;
            }
        } else { //а иначе надо отдать соотв статус
            // берем последние 2 цифры номера (второй параметр это endIndex - 1, поэтому 2 символа получится)
            // Если 00 - 14, то отдадим их
            // А если нет, то пусть останется 0 - OK
            String las = s.length() > 1 ? s.substring(s.length() - 2, s.length()) : "00";
            Integer lastDigits = Integer.parseInt(las);
            if (lastDigits < 15) {
                result = lastDigits.toString();
            }
        }

        return result;
    }

    // делаем статусы
    private void manageStatus(int messageToken, String msisdn) {
        switch (DirectViberEmul.getStatusMode()) {
            case "NO_STATUS":
                break;
            case "ALLDELIVERED":
                putToDeliveredQueue(messageToken);
                break;
            case "ALLREAD":
                putToDeliveredQueue(messageToken);
                putToReadQueue(messageToken);
                break;
            case "LASTDIGIT":
                int lastDigit = Integer.parseInt(msisdn.substring(msisdn.length() - 1, msisdn.length()));
                switch (lastDigit) {
                    case 0:
                        break;
                    case 1:
                    case 2:
                        putToDeliveredQueue(messageToken);
                        break;
                    default:
                        putToDeliveredQueue(messageToken);
                        putToReadQueue(messageToken);
                        break;
                }
                break;
            default:
                break;
        }
    }

    private void putToReadQueue(int messageToken) {
        DirectViberEmul.statusReadQueue.put(messageToken, System.currentTimeMillis() + DirectViberEmul.getStatusReadInterval() * 1000L);
    }

    private void putToDeliveredQueue(int messageToken) {
        DirectViberEmul.statusDeliveredQueue.put(messageToken, System.currentTimeMillis() + DirectViberEmul.getStatusDeliveredInterval() * 1000L);
    }
}

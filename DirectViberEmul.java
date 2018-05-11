package direct_viber_emul;

import com.sun.net.httpserver.HttpServer;
import utils.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Эмулятор прямого вайбера
 */
public class DirectViberEmul implements Runnable {

    private static DirectViberStatusCallback directViberStatusCallback;
    private static DirectViberMOCallback directViberMOCallback;
    private String filesLocation = null;

    private int PORT = 9097;
    private String PATH = "/direct_viber_emulator";
    private int nThreads = 20;
    private HttpServer httpServer;

    // настройки МТ
    private static int MT_REPLY_DELAY = 0; // задержка ответа на МТ сообщение
    private static String MT_STATES_MODE = "15"; // по умолчанию LASTDIGIT

    static String getMtStatesMode() {
        return MT_STATES_MODE;
    }

    static int getMtReplyDelay() {
        return MT_REPLY_DELAY;
    }

    // map ы с токенами и "unix" временем когда отправить статус
    static ConcurrentHashMap<Integer, Long> statusDeliveredQueue = new ConcurrentHashMap<>();
    static ConcurrentHashMap<Integer, Long> statusReadQueue = new ConcurrentHashMap<>();

    // настройки статусов
    private static String STATUS_HOST = "localhost";
    private static String STATUS_URL = "/status";
    private static int STATUS_PORT = 8080;
    private static String STATUS_MODE = "NO_STATUS";
    private static int STATUS_DELIVERED_INTERVAL = 60;//сек
    private static int STATUS_READ_INTERVAL = 120;//сек
    private static int STATUS_QUEUE_CHECK_INTERVAL = 10;//сек

    static String getStatusHost() {
        return STATUS_HOST;
    }

    static String getStatusUrl() {
        return STATUS_URL;
    }

    static int getStatusPort() {
        return STATUS_PORT;
    }

    static String getStatusMode() {
        return STATUS_MODE;
    }

    static int getStatusDeliveredInterval() {
        return STATUS_DELIVERED_INTERVAL;
    }

    static int getStatusReadInterval() {
        return STATUS_READ_INTERVAL;
    }

    static int getStatusQueueCheckInterval() {
        return STATUS_QUEUE_CHECK_INTERVAL;
    }

    // настройки МО
    private static String MO_HOST = "localhost";
    private static String MO_URL = "/mo";
    private static int MO_PORT = 8080;
    private static String MO_MODE = "NO_MO";
    private static int MO_REPEAT_INTERVAL = 300;//сек
    private static String MO_MESSAGE = "hello";
    private static String MO_MSISDN = "79263731118";
    private static String MO_TRACKING_ID = "123";
    private static String MO_TOKEN = "777";
    private static String MO_TIME = "-10000";

    static String getMoHost() {
        return MO_HOST;
    }

    static String getMoUrl() {
        return MO_URL;
    }

    static int getMoPort() {
        return MO_PORT;
    }

    static String getMoMode() {
        return MO_MODE;
    }

    static int getMoRepeatInterval() {
        return MO_REPEAT_INTERVAL;
    }

    static String getMoMessage() {
        return MO_MESSAGE;
    }

    static String getMoMSISDN() {
        return MO_MSISDN;
    }

    static String getMoTrakingID() {
        return MO_TRACKING_ID;
    }

    static String getMoToken() {
        return MO_TOKEN;
    }

    static String getMoTime() {
        return MO_TIME;
    }

    // message token
    private static Random randomGenerator = new Random();

    static int getMessageToken() {
        return randomGenerator.nextInt(Integer.MAX_VALUE);
    }

    private DirectViberEmul(String sDirLocation) {
        // обработаем конф файл запуска
        if (sDirLocation == null || sDirLocation.length() == 0) {
            Log.println("Параметр рабочей директории не задан!");
            System.exit(1);
        } else {
            filesLocation = sDirLocation;
        }
        // читаем конфиг
        readEmulatorSettings();
    }

    @Override
    public void run() {
        try {
            Log.println("========== START PROGRAM ==========");
            ExecutorService executor = Executors.newFixedThreadPool(nThreads);

            httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
            httpServer.createContext(PATH, new DirectViberHandler());
            httpServer.setExecutor(executor);
            httpServer.start();
            Log.println("");
            Log.println("===");
            Log.println("Started Direct Viber Server at port " + PORT);
            Log.println("Path = " + PATH);
            Log.println("Threads = " + nThreads);
            Log.println("===");
            Log.println("MT Reply Delay = " + MT_REPLY_DELAY);
            Log.println("MT Responses = " + MT_STATES_MODE);
            Log.println("===");
            Log.println("STATUS host = " + STATUS_HOST);
            Log.println("STATUS port = " + STATUS_PORT);
            Log.println("STATUS URL = " + STATUS_URL);
            Log.println("STATUS MODE = " + STATUS_MODE);
            Log.println("STATUS DELIVERED INTERVAL sec = " + STATUS_DELIVERED_INTERVAL);
            Log.println("STATUS READ INTERVAL sec = " + STATUS_READ_INTERVAL);
            Log.println("STATUS QUEUE CHECK INTERVAL sec = " + STATUS_QUEUE_CHECK_INTERVAL);
            Log.println("===");
            Log.println("MO host = " + MO_HOST);
            Log.println("MO port = " + MO_PORT);
            Log.println("MO URL = " + MO_URL);
            Log.println("MO MODE = " + MO_MODE);
            Log.println("MO REPEAT INTERVAL = " + MO_REPEAT_INTERVAL);
            Log.println("MO MESSAGE = " + MO_MESSAGE);
            Log.println("MO MSISDN = " + MO_MSISDN);
            Log.println("MO TRACKING ID = " + MO_TRACKING_ID);
            Log.println("MO TOKEN = " + MO_TOKEN);
            Log.println("MO TIME = " + MO_TIME);
            Log.println("===");

            // Wait here until notified of shutdown.
            synchronized (this) {
                try {
                    this.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


    public static void main(String[] args) throws Exception {
        Log.println("");
        Log.println("");

        DirectViberEmul serverInstance = new DirectViberEmul(args == null || args.length == 0 ? "" : args[0]);

        Thread serverThread = new Thread(serverInstance);
        serverThread.start();

        Runtime.getRuntime().addShutdownHook(new OnShutdown(serverInstance));

        // подождем сек 30 прежде чем стартовать статусы и МО, на всякий
        Thread.sleep(30000);

        directViberStatusCallback = new DirectViberStatusCallback("Direct Status Callback");
        directViberStatusCallback.start();

        directViberMOCallback = new DirectViberMOCallback("Direct MO Callback");
        directViberMOCallback.start();

        try {
            serverThread.join();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    void shutdown() {
        try {
            if (directViberStatusCallback != null) {
                Log.println("Shutting down Direct Viber Status Callback.");
                directViberStatusCallback.cancel();
            }

            if (directViberMOCallback != null) {
                Log.println("Shutting down Direct Viber MO Callback.");
                directViberMOCallback.cancel();
            }

            Log.println("Shutting down Direct Viber Emulator.");
//            httpServer.removeContext(PATH);
            httpServer.stop(0);

        } catch (Exception e) {
            Log.printStackTrace(e);
        }

        synchronized (this) {
            notifyAll();
        }

    }

    //читаем настройки
    private void readEmulatorSettings() {
        try {
            File fileDir = new File(filesLocation + "direct_viber.conf");
            String paramName, paramValue;

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(fileDir), "UTF8"));

            String str;
            // читаем файл
            while ((str = in.readLine()) != null) {
                if (!str.startsWith("#") && !str.trim().equals("")) { // это не комментарий и не пустая строка
                    str = str.trim();
                    if (str.contains("=")) {
                        paramName = str.split("=")[0].trim();
                        paramValue = str.split("=")[1].trim();

                        switch (paramName) {
                            case "PORT":
                                PORT = Integer.parseInt(paramValue);
                                break;
                            case "PATH":
                                PATH = paramValue;
                                break;
                            case "THREADS":
                                nThreads = Integer.parseInt(paramValue);
                                break;

                            case "STATUS.HOST":
                                STATUS_HOST = paramValue;
                                break;
                            case "STATUS.PORT":
                                STATUS_PORT = Integer.parseInt(paramValue);
                                break;
                            case "STATUS.URL":
                                STATUS_URL = paramValue;
                                break;
                            case "MO.HOST":
                                MO_HOST = paramValue;
                                break;
                            case "MO.PORT":
                                MO_PORT = Integer.parseInt(paramValue);
                                break;
                            case "MO.URL":
                                MO_URL = paramValue;
                                break;

                            case "MT.STATES.MODE":
                                MT_STATES_MODE = paramValue;
                                break;
                            case "MT.REPLY.DELAY":
                                MT_REPLY_DELAY = Integer.parseInt(paramValue);
                                break;

                            case "STATUS.MODE":
                                STATUS_MODE = paramValue;
                                break;
                            case "STATUS.DELIVERED.INTERVAL":
                                STATUS_DELIVERED_INTERVAL = Integer.parseInt(paramValue);
                                break;
                            case "STATUS.READ.INTERVAL":
                                STATUS_READ_INTERVAL = Integer.parseInt(paramValue);
                                break;
                            case "STATUS.QUEUE.CHECK.INTERVAL":
                                STATUS_QUEUE_CHECK_INTERVAL = Integer.parseInt(paramValue);
                                break;

                            case "MO.MODE":
                                MO_MODE = paramValue;
                                break;
                            case "MO.REPEAT.INTERVAL":
                                MO_REPEAT_INTERVAL = Integer.parseInt(paramValue);
                                break;
                            case "MO.MESSAGE":
                                MO_MESSAGE = paramValue;
                                break;
                            case "MO.TOKEN":
                                MO_TOKEN = paramValue;
                                break;
                            case "MO.MSISDN":
                                MO_MSISDN = paramValue;
                                break;
                            case "MO.TRACKING_ID":
                                MO_TRACKING_ID = paramValue;
                                break;
                            case "MO.TIME":
                                MO_TIME = paramValue;
                                break;

                            default:
                                Log.println("Неизвестный параметр " + paramName + " - игнорируем");
                        }
                    }

                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
            System.exit(1);
        }
    }

}

/* Responds to a JVM shutdown by stopping the server. */
class OnShutdown extends Thread {
    private DirectViberEmul serverInstance;

    OnShutdown(DirectViberEmul serverInstance) {
        this.serverInstance = serverInstance;
    }

    public void run() {
        serverInstance.shutdown();
    }
}

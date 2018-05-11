package direct_viber_emul;

import http.HttpSender;
import utils.Log;
import utils.Utils;

/**
 * Входящие от прямого вайбера
 */
class DirectViberMOCallback extends Thread {

    private String threadName;
    private static final long sleepTime = DirectViberEmul.getMoRepeatInterval() * 1000L; // мсек
    private static final String url = "http://"
                                + DirectViberEmul.getMoHost() + ":"
                                + DirectViberEmul.getMoPort()
                                + DirectViberEmul.getMoUrl();
    private static boolean isSendOccurs = false;

    DirectViberMOCallback(String name) {
        threadName = name;
        Log.println("Creating " + threadName);
    }

    void cancel() {
        interrupt();
    }

    public void run() {
        try {
            Log.println("Running " + threadName);

            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(sleepTime);
                Log.println("--- Run MO callback");
                switch (DirectViberEmul.getMoMode()) {
                    case "NO_MO":
                        break;
                    case "ONCE":
                        if (!isSendOccurs) {
                            // надо послать
                            injectMO();
                            isSendOccurs = true;
                        }
                        break;
                    case "REPEAT":
                        injectMO();
                        break;

                }
            }
        } catch (InterruptedException e) {
            Log.println("Thread " + threadName + " interrupted.");
        }
        Log.println("Thread " + threadName + " exiting.");
    }


    private static void injectMO() {
        try {
            // особо не заморачиваемся на правильность параметров конфига
            String[] msisdn = DirectViberEmul.getMoMSISDN().split(";");
            String[] token = DirectViberEmul.getMoToken().split(";");
            String[] time = DirectViberEmul.getMoTime().split(";");
            String[] tracking = DirectViberEmul.getMoTrakingID().split(";");
            String message = DirectViberEmul.getMoMessage();
            String params, sendTime;

            for (int i = 0; i < msisdn.length; i++) {
                if (msisdn[i] != null) {
                    String messageReplaced = message.replace("%RND%", Utils.getRandomSpecialString());
                    // надо оставить 10 знаков во времени, а не 13
                    sendTime = String.valueOf(System.currentTimeMillis() + Long.parseLong(time[i])).substring(0, 10);
                    params = "{\"message_token\": " + token[i] + "," +
                            "\"phone_number\": \"" + msisdn[i] + "\"," +
                            "\"time\": " + sendTime + "," +
                            "\"message\":{\"text\":\"" + messageReplaced + "\"," +
                            "\"tracking_data\": \"" + tracking[i] + "\"}}";

                    HttpSender.sendPost(url, null, null, null, params);
                }
            }


        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

}

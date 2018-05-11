package direct_viber_emul;

import http.HttpSender;
import utils.Log;

import java.util.ArrayList;
import java.util.Map;


/**
 * Отдаем статусы
 */
class DirectViberStatusCallback extends Thread {

    private String threadName;
    private static final long sleepTime = DirectViberEmul.getStatusQueueCheckInterval() * 1000L; // мсек
    private static final String url = "http://"
                                        + DirectViberEmul.getStatusHost() + ":"
                                        + DirectViberEmul.getStatusPort()
                                        + DirectViberEmul.getStatusUrl();


    DirectViberStatusCallback(String name) {
        threadName = name;
        Log.println("Creating " + threadName);
    }

    public void run() {
        try {
            Log.println("Running " + threadName);
            // Раз в sleepTime просыпаемся и смотрим надо ли осчастливить статусами
            while (!Thread.currentThread().isInterrupted()) {
                checkQueue(DirectViberEmul.statusDeliveredQueue, "Deliv Queue", 0);  // доставленные
                checkQueue(DirectViberEmul.statusReadQueue, "Read Queue", 1);  // прочитаные
                Thread.sleep(sleepTime);
            }
        } catch (InterruptedException e) {
            Log.println("Thread " + threadName + " interrupted.");
        }
        Log.println("Thread " + threadName + " exiting.");
    }

    void cancel() {
        interrupt();
    }


    // надо ли чего доставить/прочитать
    private void checkQueue(Map<Integer, Long> queue, String queueLogName, int msgStatus) {
        ArrayList<Integer> deleteEntries = new ArrayList<>();

        Log.println("--- Run status callback " + queueLogName + " = " + queue.size());
        Log.println("--- Run status callback Checking what to send...");

        for (Map.Entry<Integer, Long> pair : queue.entrySet()) {
            //  pair.getKey()
            //  pair.getValue());
            int messageToken = pair.getKey();
            long time = pair.getValue();

            if (time <= System.currentTimeMillis()) {//пришло время отправить
                // Отправить
                // sendPost(messageToken, q); // 0 - доставлено, 1 - прочитано
                HttpSender.sendPost(url,
                        null, null, null,
                        "{\"message_token\":" + messageToken + ",\"message_status\":" + msgStatus + "}");
                // запись нам больше ну нужна, запомним
                deleteEntries.add(messageToken);
            }
//            it.remove(); // avoids a ConcurrentModificationException
        }
        // почистим ненужное
        for (int i : deleteEntries) {
            queue.remove(i);
        }
    }
}


import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Main {
    private static final String CONFIG_FILE = "config.json";
    private static Map<String, Map<String, Object>> sensorData = new HashMap<>();
    private static final Random random = new Random();
    private static InetAddress serverAddress;
    private static int serverPort;
    private static int sendIntervalSeconds;

    private static void loadConfig(JSONObject config) throws Exception {
        String host = config.getString("HOST_UDP");
        serverAddress = InetAddress.getByName(host);
        serverPort = config.getInt("PORT_UDP");
        sendIntervalSeconds = config.getInt("SEND_INTERVAL");
        JSONArray sensors = config.getJSONArray("SENSORS");

        initializeSensorData(sensors);
    }

    public static void main(String[] args) {
        String configFilePath = "config.json";
        JSONObject config = readConfig(configFilePath);

        if(config == null) {
            System.out.println("Failed to read Configuration.");
            return;
        }

        try {
            loadConfig(config);
            new Thread(Main::startUdpReceiver).start();
            startUdpSender();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startUdpSender() {
        try(DatagramSocket socket = new DatagramSocket()) {
            while(true) {
                for(Map<String, Object> mapobject : sensorData.values()) {
                    // 미세한 변동 적용
                    applyMinorFluctuations(mapobject);

                    JSONObject json = new JSONObject(mapobject);
                    byte[] buffer = json.toString().getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
                    socket.send(packet);
                }
                Thread.sleep(sendIntervalSeconds * 1000);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void startUdpReceiver() {
        try (DatagramSocket socket = new DatagramSocket(8888)) {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                JSONObject json = new JSONObject(message);
                updateSensorData(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject readConfig(String filePath) {
        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new JSONObject(content);
    }

    private static void initializeSensorData(JSONArray sensors) {
        for (int i = 0; i < sensors.length(); i++) {
            JSONObject sensor = sensors.getJSONObject(i);
            String location = sensor.getString("location");

            sensorData.put(location, new HashMap<String, Object>());

            sensorData.get(location).put("location", location);
            sensorData.get(location).put("sensorId", sensor.getInt("sensorId"));
            sensorData.get(location).put("temperature", random.nextDouble() * 35);
            sensorData.get(location).put("humidity", random.nextDouble() * 100);
            sensorData.get(location).put("co2", random.nextInt(2000));
            sensorData.get(location).put("light", random.nextDouble() * 10000);
            sensorData.get(location).put("pm2_5", random.nextInt(300));
        }
    }

    private static void updateSensorData(JSONObject json) {
        String location = json.getString("location");
        Double temperature = json.has("temperature") ? json.getDouble("temperature") : null;
        Double humidity = json.has("humidity") ? json.getDouble("humidity") : null;
        Double light = json.has("light") ? json.getDouble("light") : null;

        Map<String, Object> sensor = sensorData.get(location);
        if (sensor != null) {
            if (temperature != null) {
                sensor.put("temperature", temperature);
            }
            if (humidity != null) {
                sensor.put("humidity", humidity);
            }
            if (light != null) {
                sensor.put("light", light);
            }
        }
    }

    private static void applyMinorFluctuations(Map<String, Object> sensor) {
        // co2와 pm2_5 값을 미세하게 변동시킴
        sensor.put("co2", applyFluctuation((int) sensor.get("co2"), 10, 2000));
        sensor.put("pm2_5", applyFluctuation((int) sensor.get("pm2_5"), 5, 300));
    }

    private static int applyFluctuation(int value, int fluctuation, int maxValue) {
        int change = random.nextInt(fluctuation * 2 + 1) - fluctuation;
        value += change;
        if (value < 0) value = 0;
        if (value > maxValue) value = maxValue;
        return value;
    }
}

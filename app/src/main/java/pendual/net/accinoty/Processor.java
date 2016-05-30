package pendual.net.accinoty;

import android.location.Location;
import android.os.Environment;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;

/**
 * Created by EleMas on 16. 5. 29..
 */
public class Processor {

    public Processor(int carIndex) {
        this.carIndex = carIndex;
        sender = new Sender();
        sender.start();
    }


    public void updateLocation(Location location) {
        this.location = location;
    }

    public void updateVideoCount(int count) {
        currentVideoCount= count;
    }

    public void sendAccident() {
        try {
            sender.sendAccident();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // data sending thread
    Sender sender;

    // Socket
    Socket socket;
    String socketAddr= "accinoty.pendual.net";
    int socketPort = 8088;
    DataInputStream dataInputStream;
    DataOutputStream dataOutputStream;
    FileInputStream fileInputStream;
    BufferedInputStream bufferedInputStream;

    // Location
    Location location;

    // Current video count
    int currentVideoCount= 0;

    // sending info
    final int carIndex;


    /** Sender class */
    public class Sender extends Thread {
        //DateFormat dateFormat= new SimpleDateFormat("yyyy/MM/dd HH:mm:SS");
        //Date today= Calendar.getInstance().getTime();
        //String reportDate= dateFormat.format(today);

        double latitude = 0.0;
        double longitude = 0.0;

        // TODO: 16. 5. 29. 초기값 0.0 인 경우에는 송신하지 않기[완료], socket이 끊어졌을 경우 재연결 시도

         /* synchronized */
         public void sendFile() throws IOException {
            // directory path
            File dirPath= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

            // 파일 내용을 읽으면서 전송
            // 직전 파일
            File file = new File(dirPath.getPath() + File.separator +
                    "Video_"+ currentVideoCount + ".jpg");
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);

            int len;
            int size = 4096;
            byte[] data = new byte[size];
            while ((len = bufferedInputStream.read(data)) != -1) {
                dataOutputStream.write(data, 0, len);
            }
            dataOutputStream.flush();

            // 직전 파일의 하나 이전 파일
            file = new File(dirPath.getPath() + File.separator +
                    "Video_"+ (currentVideoCount-1) + ".jpg");
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);

            while ((len = bufferedInputStream.read(data)) != -1) {
                dataOutputStream.write(data, 0, len);
            }

            dataOutputStream.flush();
            dataOutputStream.close();
            bufferedInputStream.close();
            fileInputStream.close();
            //System.out.println("파일 전송 작업을 완료하였습니다.");
            //System.out.println("보낸 파일의 사이즈 : " + file.length());
        }

        /** returned other value, accident around */
        synchronized public void sendAccidentAround (int receivedIndex) throws IOException {
            // gps info, sender info, time stamp
            System.out.println("gps send (accident_Around)");

            //String sData = reportDate + "|" + carIndex + "|" + latitude + "|" + longitude;
            // date update
            //today= Calendar.getInstance().getTime();
            //reportDate= dateFormat.format(today);

            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                JSONObject obj = new JSONObject();
                obj.put("type", "accident_around");
                obj.put("car_index", carIndex);
                obj.put("accident_around", receivedIndex);
                obj.put("latitude", latitude);
                obj.put("longitude", longitude);
                //obj.put("date", reportDate);


                dataOutputStream.writeUTF(obj.toJSONString());
                // TODO: 16. 5. 29. >>>file send<<<
                sendFile();
                System.out.println(latitude);
                System.out.println(longitude);
                dataOutputStream.flush();
            }


        }

        /** GPS 0.0인 경우 Location 업데이트를 받아들이기 위해 synchronized 키워드를 제거 */
        public void sendAccident() throws IOException {
            System.out.println("gps send (accident)");
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                JSONObject obj = new JSONObject();
                obj.put("type", "GPS");
                obj.put("car_index", carIndex);
                obj.put("latitude", latitude);
                obj.put("longitude", longitude);
                //obj.put("date", reportDate);


                dataOutputStream.writeUTF(obj.toJSONString());
                System.out.println(latitude);
                System.out.println(longitude);
                dataOutputStream.flush();
            }

        }

        /** returned 0, normal */
        synchronized public void sendCycle() throws IOException {


            // gps info, sender info, time stamp
            System.out.println("gps send (periodical)");

            //String sData = reportDate + "|" + carIndex + "|" + latitude + "|" + longitude;
            // date update
            //today= Calendar.getInstance().getTime();
            //reportDate= dateFormat.format(today);
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                JSONObject obj = new JSONObject();
                obj.put("type", "GPS");
                obj.put("car_index", carIndex);
                obj.put("latitude", latitude);
                obj.put("longitude", longitude);
                //obj.put("date", reportDate);


                dataOutputStream.writeUTF(obj.toJSONString());
                System.out.println(latitude);
                System.out.println(longitude);
                dataOutputStream.flush();
            }


        }

        @Override
        public void run() {
            try {
                socket = new Socket(socketAddr, socketPort);
                dataInputStream = new DataInputStream(socket.getInputStream());
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                Random random = new Random();
                random.setSeed(random.nextLong());
                String returnValue;
                int returnValueParsed= 0;

                // 0: 정상
                // other: 사고

                try {
                    while (true) {
                        if (returnValueParsed == 0) {
                            sleep(4700 + (random.nextInt() % 50) * 10);
                            sendCycle();
                        } else {
                            sendAccidentAround(returnValueParsed);
                        }
                        returnValue = dataInputStream.readUTF();
                        JSONObject obj=(JSONObject)new JSONParser().parse(returnValue.trim());
                        returnValueParsed = Integer.parseInt(obj.get("ack").toString());
                        System.out.println(returnValueParsed);
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

}

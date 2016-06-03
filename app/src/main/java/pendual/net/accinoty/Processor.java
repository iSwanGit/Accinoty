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
import java.net.SocketException;
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

    public void updateVideoCount(int count, boolean isLooping) {
        currentVideoCount = count;
        // 1번 파일 이전의 5번이 없는 경우 처리
        if (!isLooped)
            isLooped= isLooping;
    }

    public void sendAccident() {
        // update된 currentVideoCount와 그 다음 파일을 전송하게 할것
        accidentOccured= true;
        setTriggeredCount();
        /*
        try {
            sender.sendAccidentOccur();
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
    }

    /**
     * 사고났을 경우 sendAccident를 호출받고 그 안에서 이 메서드가 호출됨.
     * 보낼 동영상, 이어서 보낼 동영상 두 인덱스를 세팅
     * 1번 파일 이전의 5번 파일이 없는 경우 1번 파일을 재전송하는 것으로.
     */
    public void setTriggeredCount() {
        triggeredCount= currentVideoCount;
        if (currentVideoCount == 0)
            try {
                Thread.sleep(sleepTime);
                triggeredCount++;
                //triggeredCount= currentVideoCount;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        if (currentVideoCount == 5)
            triggeredNextCount= 1;
        else
            triggeredNextCount= currentVideoCount +1;
    }


    // data sending thread
    Sender sender;

    // Socket
    Socket socket;
    String socketAddr = "accinoty.pendual.net";
    int socketPort = 8088;
    DataInputStream dataInputStream;
    DataOutputStream dataOutputStream;
    FileInputStream fileInputStream;
    BufferedInputStream bufferedInputStream;
    Socket fileSocket;
    DataOutputStream fileDOS;

    // Location
    Location location;

    // Current video count
    int currentVideoCount = 0;
    boolean isLooped= false;

    // sending info
    final int carIndex;

    // send accident trigger
    boolean accidentOccured = false;
    int triggeredCount;
    int triggeredNextCount;

    final int sleepTime= 20000;


    /**
     * Sender class
     */
    public class Sender extends Thread {
        //DateFormat dateFormat= new SimpleDateFormat("yyyy/MM/dd HH:mm:SS");
        //Date today= Calendar.getInstance().getTime();
        //String reportDate= dateFormat.format(today);

        double latitude = 0.0;
        double longitude = 0.0;

        // TODO: 16. 5. 29. 초기값 0.0 인 경우에는 송신하지 않기[완료], socket이 끊어졌을 경우 재연결 시도

        /* synchronized */
        synchronized public void sendFile(int count) throws IOException {
            // directory path
            File dirPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyCameraApp");
            //String dirPath = "/storage/emulated/legacy/Pictures";
            File file_current = new File(dirPath.getPath() + File.separator +
                    "Video_" + count + ".mp4");


            // file buffer size
            int len;
            int size= 4096;
            byte[] data;

            try {
                fileSocket = new Socket("accinoty.pendual.net", 8000);
                fileDOS= new DataOutputStream(fileSocket.getOutputStream());


                // 파일 내용을 읽으면서 전송
                // 직전 파일

                fileInputStream = new FileInputStream(file_current);
                bufferedInputStream = new BufferedInputStream(fileInputStream);
                //size= (int)file_current.length();
                data = new byte[size];
                System.out.println("file sending...");
                System.out.println(file_current.getPath());

                //fileDOS.writeInt(size);
                //fileDOS.write(bufferedInputStream.read(data));

                while ((len = bufferedInputStream.read(data)) != -1) {
                    fileDOS.write(data, 0, len);
                }


                fileDOS.flush();
                fileDOS.close();
                bufferedInputStream.close();
                fileInputStream.close();
                fileSocket.close();

            } catch (SocketException e) {
                e.printStackTrace();
            }
            System.out.println("file sent.");
            System.out.println(file_current.length());



        }
        /**
         * returned other value, accident around
         */
        synchronized public void sendAccidentAround(int receivedIndex) throws IOException {

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
                if (triggeredCount != 0) {
                    try {
                        // 다음 파일 녹화 완료까지 대기
                        sleep(sleepTime);
                        sendFile(triggeredNextCount);   // accidentOccured 이후에 호출될 때 처음에 보낸 동영상 다음 파일을 보냄
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        triggeredCount = 0;
                        triggeredNextCount = 0;
                    }
                }
                else {
                    if (currentVideoCount == 0) {
                        // accidentaround 실행시, 아직비디오가 녹화 안된 경우
                        try {
                            sleep(sleepTime);
                            sendFile(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    else sendFile(currentVideoCount);    // cycle 도는 중에 around호출받았을 떄 최근에 찍은거 보내기

                }
                System.out.println(latitude);
                System.out.println(longitude);
                dataOutputStream.flush();


            }


        }

        /**
         * 대부분 그럴리는 없겠지만(?) GPS 0.0인 경우 Location 업데이트를 받아들이기 위해 synchronized 키워드를 제거
         */
        public void sendAccidentOccur() throws IOException {
            System.out.println("gps send (accident)");
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                JSONObject obj = new JSONObject();
                obj.put("type", "accident_occur");
                obj.put("car_index", carIndex);
                obj.put("latitude", latitude);
                obj.put("longitude", longitude);
                //obj.put("date", reportDate);


                dataOutputStream.writeUTF(obj.toJSONString());
                sendFile(triggeredCount);
                System.out.println(latitude);
                System.out.println(longitude);
                dataOutputStream.flush();

                accidentOccured = false;
            }

        }

        /**
         * returned 0, normal
         */
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
                int returnValueParsed = 0;

                // 0: 정상
                // other: 사고

                try {
                    while (true) {
                        if (returnValueParsed == 0) {
                            sleep(4700 + random.nextInt(50) * 10);
                            sendCycle();
                        } else if (accidentOccured) {
                            setTriggeredCount();
                            sendAccidentOccur();
                        } else {
                            sendAccidentAround(returnValueParsed);
                        }
                        returnValue = dataInputStream.readUTF();
                        JSONObject obj = (JSONObject) new JSONParser().parse(returnValue.trim());
                        returnValueParsed = Integer.parseInt(obj.get("ack").toString());
                        System.out.println(returnValueParsed);
                        System.out.println(accidentOccured);
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
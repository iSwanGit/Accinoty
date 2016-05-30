package pendual.net.accinoty;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {

    /** //////////////      EGUNI CAMERA MEM.VAR            ////////////////////*/
    public static final String TAG = "EGUNICAMERA";
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mMediaRecorder;
    private TimerTask startTask;
    private TimerTask stopTask;
    private Timer startTimer;
    private Timer stopTimer;

    private boolean isRecording = false;
    private int videoCount = 1;
    //////////////////      EGUNI CAMERA MEM.VAR END        //////////////////////


    /** ///////////////      ELEMAS ACC/GYRO MEM.VAR          /////////////////////*/
    int accelX, accelY, accelZ;
    int gyroX, gyroY, gyroZ;

    private SensorManager mSensorManager;
    private Sensor mGyroscope;
    private Sensor accSensor;
    //////////////////      ELEMAS ACC/GYRO MEM.VAR END          //////////////////////

    /** ///////////////      ELEMAS GPS MEM.VAR          /////////////////////*/
    double latPoint = 0.0;
    double lngPoint = 0.0;
    double speed = 0.0;

    private LocationManager locManager;
    private Location location;

    boolean GPS_Enabled = false;
    boolean Network_Enabled = false;

    Geocoder geocoder;
    //////////////////      ELEMAS GPS MEM.VAR END      //////////////////////

    /** ///////////////      ELEMAS SOCKET MEM.VAR           /////////////////////*/
    Processor sendProcessor;

    Thread sendThread;
    //String socketAddr = "127.0.0.1";
    //String socketAddr= "163.180.116.78";
    //String socketAddr= "accinoty.pendual.net";
    //int socketPort = 8088;
    //Socket mSocket;
    /** socket은 sender 객체 안에 선언 */

    ///// car info
    int carIndex = 3810;

    // viewer
    //StatusViewer statusViewer;
    boolean firstPassed = false;
    //LayoutInflater inflater;
    //View viewLayout;
    //Toast toast;

    // TODO: 16. 5. 25. 권한요구 + GPS랑 데이터 꺼져있을 경우 키는 것 요구하기

    //////////////////      ELEMAS SOCKET MEM.VAR END       //////////////////////


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ////////      EGUNI CAMERA METHOD       ////////
        if (checkCameraHardware(this)) {

            mCamera = getCameraInstance();
            mPreview = new CameraPreview(this, mCamera);
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);

            // operate camera timer : 120000ms
            startTask = new TimerTask() {
                @Override
                public void run() {
                    prepareVideoRecorder();
                    startCamera();
                }
            };
            stopTask = new TimerTask() {
                @Override
                public void run() {
                    stopCamera();
                }
            };
            startTimer = new Timer();
            stopTimer = new Timer();
            startTimer.scheduleAtFixedRate(startTask, 1000, 14000);
            stopTimer.scheduleAtFixedRate(stopTask, 11000, 14000);
        }
        ////////        EGUNI CAMERA METHOD END         ////////


        ////////        ELEMAS ACC/GYRO METHOD         ////////
        mSensorManager= (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mGyroscope= mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accSensor= mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        ////////        ELEMAS ACC/GYRO METHOD END        ////////

        ////////      ELEMAS GPS METHOD       ////////
        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
        geocoder= new Geocoder(this, Locale.KOREA);
/*
        ////////        ELEMAS SOCKET METHOD        ////////
        try {
            mSocket= new Socket(socketAddr, socketPort);
        } catch (IOException e) {
            System.out.println("socket connection failed: exception");
            e.printStackTrace();
        }
*/

    }

    // Register Listener
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_GAME);

        /** 주기 설명
         * SENSOR_DELAY_UI 갱신에 필요한 정도의 주기
         * SENSOR_DELAY_NORMAL 화면 방향 전환 등의 일상적인 주기
         * SENSOR_DELAY_GAME 게임에 적합한 주기
         * SENSOR_DELAY_FASTEST 최대한의 빠른 주기
         */
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
        mSensorManager.unregisterListener(this);        // unregister sensor listener
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private boolean prepareVideoRecorder(){
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());

        // Step 5: Set the preview output
        // Create our Preview view and set it as the content of our activity.
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }


    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Pass videoCount value to Processor object.
        sendProcessor.updateVideoCount(videoCount);

        // Create a media file name
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ videoCount + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "Video_"+ videoCount + ".mp4");
        } else {
            return null;
        }
        if (mediaFile.exists())
            mediaFile.delete();


        // If videoCount is more than 6 then delete video 1
        if (videoCount == 5)
            videoCount = 1;
        else
            videoCount++;

        return mediaFile;
    }
    private void startCamera() {
        // Camera is available and unlocked, MediaRecorder is prepared,
        // now you can start recording
        mMediaRecorder.start();

        // inform the user that recording has started
        isRecording = true;
    }

    private void stopCamera() {
        // stop recording and release camera
        mMediaRecorder.stop();  // stop the recording
        releaseMediaRecorder(); // release the MediaRecorder object
        mCamera.lock();         // take camera access back from MediaRecorder

        // inform the user that recording has stopped
        isRecording = false;
    }


    //////////////////      ELEMAS ACC/GYRO METHOD OVERRIDE          //////////////////////
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor= event.sensor;

        if (sensor.getType() == sensor.TYPE_GYROSCOPE) {
            gyroX= Math.round(event.values[0]*1000);
            gyroY= Math.round(event.values[1]*1000);
            gyroZ= Math.round(event.values[2]*1000);
        }

        if (sensor.getType() == sensor.TYPE_ACCELEROMETER) {
            accelX= (int) event.values[0];
            accelY= (int) event.values[1];
            accelZ= (int) event.values[2];

        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    //////////////////      ELEMAS ACC/GYRO METHOD OVERRIDE END          //////////////////////


    //////////////////      ELEMAS GPS METHOD OVERRIDE          //////////////////////
    @Override
    public void onLocationChanged(Location location) {
        latPoint= location.getLatitude();
        lngPoint= location.getLongitude();
        this.location = location;
        if (sendProcessor!= null)
            sendProcessor.updateLocation(location);
    }
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

        if (!firstPassed && status != 0) {
            CharSequence text= "GPS connected";
            int duration= Toast.LENGTH_SHORT;
            Toast toast= Toast.makeText(this, text, duration);
            toast.show();
            firstPassed= true;

            /////////       ELEMAS SOCKET CONNECTION METHOD         ////////
            sendProcessor = new Processor(carIndex);

        }

        if (firstPassed && status == 0) {
            CharSequence text= "GPS disconnected";
            int duration= Toast.LENGTH_SHORT;
            Toast toast= Toast.makeText(this, text, duration);
            toast.show();
            firstPassed= false;

        }


    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
    //////////////////      ELEMAS GPS METHOD OVERRIDE END       //////////////////////


}
